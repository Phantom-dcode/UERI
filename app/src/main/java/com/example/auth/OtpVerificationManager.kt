package com.example.auth

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.util.EmailValidator
import com.example.util.EmailValidationResult
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Supported channels for delivering One-Time Passwords in UERI.
 */
enum class OtpChannel(val displayName: String, val channelLabel: String) {
    EMAIL("Mail ID (Email)", "Email"),
    SMS("Phone Number SMS", "SMS Message"),
    WHATSAPP("Phone Number WhatsApp", "WhatsApp")
}

/**
 * Purpose of the requested OTP.
 */
enum class OtpPurpose {
    REGISTRATION,
    FORGOT_PASSWORD
}

enum class OtpFlowState {
    IDLE,
    SENDING,
    OTP_REQUEST_ACCEPTED,
    VERIFICATION_PENDING,
    VERIFIED,
    SEND_FAILED,
    DELIVERY_STATUS_UNKNOWN
}

/**
 * Secure data class representing an active OTP verification session.
 * Plaintext code is NEVER retained in memory or disk — only the cryptographic salted SHA-256 hash.
 */
data class SecureOtpSession(
    val target: String,
    val channel: OtpChannel,
    val purpose: OtpPurpose,
    val codeHash: String,
    val salt: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000L), // 10 minutes expiry
    var attemptsRemaining: Int = 3,
    var lastSentAt: Long = System.currentTimeMillis(),
    var isVerified: Boolean = false,
    val serverManaged: Boolean = false
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

/**
 * Safe client-facing result of requesting an OTP dispatch.
 * Plaintext code is NEVER included.
 */
data class OtpDispatchResult(
    val formattedTarget: String,
    val deliveryMessage: String,
    val channel: OtpChannel,
    val typoSuggestion: String? = null,
    val state: OtpFlowState = OtpFlowState.OTP_REQUEST_ACCEPTED
)

/**
 * Pluggable email delivery interface for testing provider failures and quota limits.
 */
interface EmailDeliveryProvider {
    fun deliver(email: String, code: String): Result<Unit>
}

/**
 * Manages OTP lifecycle with zero-trust security:
 * - Cryptographically secure 6-digit generation (SecureRandom)
 * - Salted SHA-256 hash verification (never logs or returns plaintext OTP)
 * - Rate limiting (60-second cooldown)
 * - Short expiration (10 minutes)
 * - Maximum attempt enforcement (3 attempts lockout)
 * - Single-use consumption
 * - Production delivery is email-only through the Cloud Function; SMS/WhatsApp require a provider integration.
 */
class OtpVerificationManager(
    private var context: Context? = null
) {
    companion object {
        private const val TAG = "OtpVerificationMgr"
        private const val PREFS_NAME = "ueri_secure_otp_sessions"
        private const val FUNCTIONS_REGION = "us-central1"
        private const val RESEND_COOLDOWN_MS = 60_000L // 60 seconds
        private val random = SecureRandom()

        @Volatile
        private var instance: OtpVerificationManager? = null

        @Volatile
        private var testEmailDeliveryProvider: EmailDeliveryProvider? = null

        fun getInstance(context: Context? = null): OtpVerificationManager {
            val inst = instance
            if (inst != null) {
                if (context != null && inst.context == null) {
                    inst.attachContext(context.applicationContext)
                }
                return inst
            }
            return synchronized(this) {
                instance ?: OtpVerificationManager(context?.applicationContext).also { instance = it }
            }
        }

        @VisibleForTesting
        fun setEmailDeliveryProviderForTest(provider: EmailDeliveryProvider?) {
            testEmailDeliveryProvider = provider
        }

        // Test-only memory store for Robolectric JVM unit tests
        private val testOnlyPlaintextCodes = ConcurrentHashMap<String, String>()
    }

    private val sessions = ConcurrentHashMap<String, SecureOtpSession>()
    private val flowStates = ConcurrentHashMap<String, OtpFlowState>()

    private val functions: FirebaseFunctions? by lazy {
        try {
            FirebaseFunctions.getInstance(FUNCTIONS_REGION)
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseFunctions instance unavailable: ${e.message}")
            null
        }
    }

    fun attachContext(ctx: Context) {
        this.context = ctx.applicationContext
    }

    private fun normalizeKey(target: String, purpose: OtpPurpose): String {
        return "${purpose.name}_${target.trim().lowercase(Locale.ROOT)}"
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun computeSha256(code: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$code".toByteArray(Charsets.UTF_8)
        val hash = md.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }

    @VisibleForTesting
    internal fun emailFunctionError(error: Throwable): Throwable {
        val functionsError = error as? FirebaseFunctionsException ?: return error
        return mapEmailFunctionError(functionsError.code, functionsError.message)
    }

    @VisibleForTesting
    internal fun mapEmailFunctionError(
        code: FirebaseFunctionsException.Code,
        message: String?
    ): Throwable {
        return when (code) {
            FirebaseFunctionsException.Code.NOT_FOUND ->
                IllegalStateException(
                    "The email verification service is not deployed for this Firebase project and region. " +
                        "Deploy the sendEmailOtp Cloud Function to the configured project in us-central1."
                )
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                IllegalStateException(
                    message
                        ?: "The email verification service is not configured. Check the backend SMTP settings."
                )
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                IllegalStateException(
                    "Firebase rejected the email verification request. Check the app's Firebase project and Cloud Functions access configuration."
                )
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                IllegalStateException(
                    message ?: "Too many verification requests. Please wait before trying again."
                )
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                IllegalArgumentException(
                    message ?: "The email verification request is invalid."
                )
            FirebaseFunctionsException.Code.INTERNAL ->
                IllegalStateException(
                    message ?: "The email provider could not process the verification request."
                )
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.UNKNOWN ->
                IllegalStateException("The email request could not be confirmed. Delivery status is unknown; check your inbox before retrying.")
            else -> IllegalStateException(
                message ?: "Failed to request an email verification code."
            )
        }
    }

    /**
     * Dispatches a cryptographically secure 6-digit OTP.
     * Enforces strict rate limits, input validation, and zero plaintext exposure.
     */
    suspend fun sendOtp(
        target: String,
        channel: OtpChannel,
        purpose: OtpPurpose,
        displayName: String? = null
    ): Result<OtpDispatchResult> {
        val trimmedTarget = target.trim()

        // 1. Validate email syntax before sending
        var typoSuggestion: String? = null
        val normalizedTarget = if (channel == OtpChannel.EMAIL) {
            val validation = EmailValidator.validate(trimmedTarget)
            if (validation is EmailValidationResult.Failure) {
                flowStates[normalizeKey(trimmedTarget, purpose)] = OtpFlowState.SEND_FAILED
                return Result.failure(IllegalArgumentException(validation.errorMessage))
            }
            val success = validation as EmailValidationResult.Success
            typoSuggestion = success.typoSuggestion
            success.normalizedEmail
        } else {
            if (!trimmedTarget.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
                flowStates[normalizeKey(trimmedTarget, purpose)] = OtpFlowState.SEND_FAILED
                return Result.failure(IllegalArgumentException("Please enter a valid phone number."))
            }
            val channelName = if (channel == OtpChannel.SMS) "SMS" else "WhatsApp"
            flowStates[normalizeKey(trimmedTarget, purpose)] = OtpFlowState.SEND_FAILED
            return Result.failure(
                IllegalStateException("$channelName verification is not configured. Choose email verification.")
            )
        }

        val key = normalizeKey(normalizedTarget, purpose)
        flowStates[key] = OtpFlowState.SENDING

        // Enforce a client-side resend cooldown in addition to the server-side limit.
        val existingSession = sessions[key] ?: loadSessionFromPrefs(key, normalizedTarget, purpose)
        val now = System.currentTimeMillis()
        if (existingSession != null && !existingSession.isExpired) {
            val elapsed = now - existingSession.lastSentAt
            if (elapsed < RESEND_COOLDOWN_MS) {
                val remainingSec = ceil((RESEND_COOLDOWN_MS - elapsed) / 1000.0).toInt().coerceAtLeast(1)
                flowStates[key] = if (existingSession.isVerified) {
                    OtpFlowState.VERIFIED
                } else {
                    OtpFlowState.OTP_REQUEST_ACCEPTED
                }
                return Result.failure(IllegalStateException("Please wait $remainingSec seconds before requesting a new code."))
            }
        }

        return try {
            val testProvider = testEmailDeliveryProvider
            val session = if (testProvider != null) {
                val code = (100000 + random.nextInt(900000)).toString()
                val salt = generateSalt()
                val delivery = testProvider.deliver(normalizedTarget, code)
                if (delivery.isFailure) {
                    throw delivery.exceptionOrNull()
                        ?: IllegalStateException("Email delivery provider rejected request.")
                }
                testOnlyPlaintextCodes[key] = code
                SecureOtpSession(
                    target = normalizedTarget,
                    channel = channel,
                    purpose = purpose,
                    codeHash = computeSha256(code, salt),
                    salt = salt,
                    createdAt = now,
                    lastSentAt = now,
                    expiresAt = now + (10 * 60 * 1000L)
                )
            } else {
                val fn = functions
                    ?: throw IllegalStateException("Email verification service is unavailable. Please try again later.")
                val result = fn.getHttpsCallable("sendEmailOtp")
                    .call(mapOf("email" to normalizedTarget, "purpose" to purpose.name))
                    .await()
                val data = result.data as? Map<*, *>
                if (data?.get("accepted") != true) {
                    flowStates[key] = OtpFlowState.DELIVERY_STATUS_UNKNOWN
                    throw IllegalStateException(
                        "The email provider did not confirm acceptance. Delivery status is unknown; please check your inbox before retrying."
                    )
                }
                SecureOtpSession(
                    target = normalizedTarget,
                    channel = channel,
                    purpose = purpose,
                    codeHash = "",
                    salt = "",
                    createdAt = now,
                    lastSentAt = now,
                    expiresAt = now + (10 * 60 * 1000L),
                    serverManaged = true
                )
            }

            sessions[key] = session
            saveSessionToPrefs(key, session)
            flowStates[key] = OtpFlowState.OTP_REQUEST_ACCEPTED
            Log.i(TAG, "Email OTP request accepted for [${normalizedTarget.take(3)}***]. Inbox delivery is not confirmed.")
            Result.success(
                OtpDispatchResult(
                    formattedTarget = normalizedTarget,
                    deliveryMessage = "The email provider accepted the verification request. Delivery to your inbox is not confirmed.",
                    channel = channel,
                    typoSuggestion = typoSuggestion
                )
            )
        } catch (error: Exception) {
            val mappedError = emailFunctionError(error)
            if (flowStates[key] != OtpFlowState.DELIVERY_STATUS_UNKNOWN) {
                flowStates[key] = if (mappedError.message?.contains("status is unknown", ignoreCase = true) == true) {
                    OtpFlowState.DELIVERY_STATUS_UNKNOWN
                } else {
                    OtpFlowState.SEND_FAILED
                }
            }
            Log.e(TAG, "Email OTP request failed (${flowStates[key]}): ${mappedError.message}", error)
            Result.failure(mappedError)
        }
    }

    /**
     * Verifies the entered 6-digit OTP against the salted hash with constant-time comparison.
     */
    suspend fun verifyOtp(target: String, code: String, purpose: OtpPurpose): Result<Boolean> {
        val trimmedTarget = target.trim()
        val trimmedCode = code.trim()

        if (trimmedCode.length != 6 || !trimmedCode.all { it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Invalid or expired verification code. Please try again."))
        }

        val normalizedTarget = if (trimmedTarget.contains("@")) {
            EmailValidator.normalize(trimmedTarget) ?: trimmedTarget
        } else {
            trimmedTarget
        }

        val key = normalizeKey(normalizedTarget, purpose)
        val session = sessions[key] ?: loadSessionFromPrefs(key, normalizedTarget, purpose)
        if (session?.isExpired == true) {
            consumeVerification(normalizedTarget, purpose)
            return Result.failure(IllegalArgumentException("Invalid or expired verification code. Please try again."))
        }
        if (session?.isVerified == true) {
            flowStates[key] = OtpFlowState.VERIFIED
            return Result.failure(IllegalArgumentException("This verification code has already been used."))
        }

        if (session != null && session.attemptsRemaining <= 0) {
            consumeVerification(normalizedTarget, purpose)
            return Result.failure(IllegalArgumentException("Too many failed attempts. Please request a new code."))
        }

        flowStates[key] = OtpFlowState.VERIFICATION_PENDING
        if (testEmailDeliveryProvider != null) {
            if (session == null || session.serverManaged) {
                return Result.failure(IllegalArgumentException("Invalid or expired verification code. Please try again."))
            }
            val expectedHash = computeSha256(trimmedCode, session.salt)
            val isMatch = MessageDigest.isEqual(
                expectedHash.toByteArray(Charsets.UTF_8),
                session.codeHash.toByteArray(Charsets.UTF_8)
            )
            if (!isMatch) {
                session.attemptsRemaining -= 1
                saveSessionToPrefs(key, session)
                if (session.attemptsRemaining <= 0) {
                    consumeVerification(normalizedTarget, purpose)
                    flowStates[key] = OtpFlowState.SEND_FAILED
                    return Result.failure(IllegalArgumentException("Too many failed attempts. Please request a new code."))
                }
                return Result.failure(IllegalArgumentException("Invalid or expired verification code. Please try again."))
            }
        } else {
            if (!normalizedTarget.contains("@")) {
                return Result.failure(IllegalStateException("Phone verification is not configured. Choose email verification."))
            }
            try {
                val fn = functions
                    ?: throw IllegalStateException("Email verification service is unavailable. Please try again later.")
                val result = fn.getHttpsCallable("verifyEmailOtp")
                    .call(
                        mapOf(
                            "email" to normalizedTarget,
                            "code" to trimmedCode,
                            "purpose" to purpose.name
                        )
                    )
                    .await()
                val data = result.data as? Map<*, *>
                if (data?.get("verified") != true) {
                    return Result.failure(IllegalArgumentException("Invalid or expired verification code. Please try again."))
                }
            } catch (error: Exception) {
                val mappedError = emailFunctionError(error)
                Log.w(TAG, "Email OTP verification failed: ${mappedError.message}")
                return Result.failure(mappedError)
            }
        }

        val verifiedSession = session ?: SecureOtpSession(
            target = normalizedTarget,
            channel = OtpChannel.EMAIL,
            purpose = purpose,
            codeHash = "",
            salt = "",
            serverManaged = true
        )
        verifiedSession.isVerified = true
        sessions[key] = verifiedSession
        saveSessionToPrefs(key, verifiedSession)
        flowStates[key] = OtpFlowState.VERIFIED
        Log.i(TAG, "OTP verification successful for [${normalizedTarget.take(3)}***]")
        return Result.success(true)
    }

    /**
     * Checks if the target has successfully verified their OTP.
     */
    fun isVerified(target: String, purpose: OtpPurpose): Boolean {
        val normalized = if (target.contains("@")) EmailValidator.normalize(target) ?: target.trim() else target.trim()
        val key = normalizeKey(normalized, purpose)
        val session = sessions[key] ?: loadSessionFromPrefs(key, normalized, purpose)
        return session != null && session.channel == OtpChannel.EMAIL && session.isVerified && !session.isExpired
    }

    fun state(target: String, purpose: OtpPurpose): OtpFlowState {
        val normalized = if (target.contains("@")) EmailValidator.normalize(target) ?: target.trim() else target.trim()
        return flowStates[normalizeKey(normalized, purpose)] ?: OtpFlowState.IDLE
    }

    /**
     * Consumes and clears the OTP verification session (Single-use enforcement).
     */
    fun consumeVerification(target: String, purpose: OtpPurpose) {
        val normalized = if (target.contains("@")) EmailValidator.normalize(target) ?: target.trim() else target.trim()
        val key = normalizeKey(normalized, purpose)
        sessions.remove(key)
        testOnlyPlaintextCodes.remove(key)
        flowStates[key] = OtpFlowState.IDLE
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.remove("hash_$key")
            ?.remove("salt_$key")
            ?.remove("expires_$key")
            ?.remove("attempts_$key")
            ?.remove("last_sent_$key")
            ?.remove("channel_$key")
            ?.remove("verified_$key")
            ?.apply()
    }

    private fun saveSessionToPrefs(key: String, session: SecureOtpSession) {
        val editor = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit() ?: return
        editor.putLong("expires_$key", session.expiresAt)
            .putInt("attempts_$key", session.attemptsRemaining)
            .putLong("last_sent_$key", session.lastSentAt)
            .putString("channel_$key", session.channel.name)
            .putBoolean("verified_$key", session.isVerified)
            .putBoolean("server_managed_$key", session.serverManaged)
        if (session.serverManaged) {
            editor.remove("hash_$key").remove("salt_$key")
        } else {
            editor.putString("hash_$key", session.codeHash).putString("salt_$key", session.salt)
        }
        editor.apply()
    }

    private fun loadSessionFromPrefs(key: String, target: String, purpose: OtpPurpose): SecureOtpSession? {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val serverManaged = prefs.getBoolean("server_managed_$key", false)
        if (!serverManaged) {
            prefs.edit()
                .remove("hash_$key")
                .remove("salt_$key")
                .remove("expires_$key")
                .remove("attempts_$key")
                .remove("last_sent_$key")
                .remove("channel_$key")
                .remove("verified_$key")
                .apply()
            return null
        }
        val expiresAt = prefs.getLong("expires_$key", 0L)
        val attempts = prefs.getInt("attempts_$key", 3)
        val lastSent = prefs.getLong("last_sent_$key", 0L)
        val channelName = prefs.getString("channel_$key", OtpChannel.EMAIL.name) ?: OtpChannel.EMAIL.name
        val channel = try { OtpChannel.valueOf(channelName) } catch (_: Exception) { OtpChannel.EMAIL }
        val verified = prefs.getBoolean("verified_$key", false)

        return SecureOtpSession(
            target = target,
            channel = channel,
            purpose = purpose,
            codeHash = "",
            salt = "",
            expiresAt = expiresAt,
            attemptsRemaining = attempts,
            lastSentAt = lastSent,
            isVerified = verified,
            serverManaged = true
        )
    }

    @VisibleForTesting
    fun getPendingCodeForTest(target: String, purpose: OtpPurpose): String? {
        val normalized = if (target.contains("@")) EmailValidator.normalize(target) ?: target.trim() else target.trim()
        val key = normalizeKey(normalized, purpose)
        return testOnlyPlaintextCodes[key]
    }

    @VisibleForTesting
    fun clearAllForTest() {
        sessions.clear()
        flowStates.clear()
        testOnlyPlaintextCodes.clear()
        testEmailDeliveryProvider = null
    }
}
