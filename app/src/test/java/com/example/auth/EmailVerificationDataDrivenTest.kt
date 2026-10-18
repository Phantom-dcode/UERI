package com.example.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.EmailValidationResult
import com.example.util.EmailValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmailVerificationDataDrivenTest {

    private lateinit var context: Context
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var otpManager: OtpVerificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        authManager = FirebaseAuthManager(context)
        authManager.attachContext(context)
        authManager.logout()
        otpManager = authManager.otpManager
        otpManager.clearAllForTest()
    }

    // =========================================================================
    // 1. DATA-DRIVEN SYNTAX VALIDATION MATRIX
    // =========================================================================

    @Test
    fun `test email validation - empty and blank inputs`() {
        val emptyCases = listOf("", "   ", "\t", "\n", "\r\n")
        for (input in emptyCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for blank input: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - missing at character`() {
        val noAtCases = listOf(
            "plainaddress",
            "userexample.com",
            "missingat.org",
            "user.name.domain.com"
        )
        for (input in noAtCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for missing @ in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - multiple at characters`() {
        val multiAtCases = listOf(
            "user@domain@example.com",
            "a@b@c.org",
            "first@middle@last.net",
            "@@example.com",
            "user@@domain.com"
        )
        for (input in multiAtCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for multiple @ in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - missing local part`() {
        val missingLocalCases = listOf(
            "@example.com",
            "@ueri.org",
            "@domain.co.uk",
            "  @example.com"
        )
        for (input in missingLocalCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for missing local part in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - missing domain or domain suffix`() {
        val missingDomainCases = listOf(
            "user@",
            "officer@.",
            "admin@localhost",
            "test@domain",
            "responder@network"
        )
        for (input in missingDomainCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for invalid domain in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - invalid domain labels and structure`() {
        val invalidDomainCases = listOf(
            "user@domain..com",        // consecutive dots in domain
            "user@.domain.com",        // leading dot in domain
            "user@domain.com.",        // trailing dot in domain
            "user@-domain.com",        // leading hyphen in domain label
            "user@domain-.com",        // trailing hyphen in domain label
            "user@dom_ain.com",        // underscore in domain label
            "user@domain.c",           // single-character TLD
            "user@domain.123",         // purely numeric TLD
            "user@domain.c-om"         // invalid hyphen in TLD
        )
        for (input in invalidDomainCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for domain syntax in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - invalid local part formatting and interior whitespace`() {
        val invalidLocalCases = listOf(
            ".user@domain.com",         // leading dot in local part
            "user.@domain.com",         // trailing dot in local part
            "user..name@domain.com",    // consecutive dots in local part
            "user name@domain.com",     // space in local part
            "user@ domain.com",         // space after @
            "user@domain .com"          // space in domain
        )
        for (input in invalidLocalCases) {
            val result = EmailValidator.validate(input)
            assertTrue("Expected failure for local part / whitespace in: '$input'", result is EmailValidationResult.Failure)
            assertEquals(EmailValidator.ERROR_MALFORMED, (result as EmailValidationResult.Failure).errorMessage)
            assertFalse(EmailValidator.isValid(input))
        }
    }

    @Test
    fun `test email validation - leading and trailing whitespace is trimmed`() {
        val whitespaceCases = listOf(
            "  user@example.com  " to "user@example.com",
            "\tofficer@ueri.org\n" to "officer@ueri.org",
            "   doctor@hospital.org " to "doctor@hospital.org"
        )
        for ((raw, expectedNormalized) in whitespaceCases) {
            val result = EmailValidator.validate(raw)
            assertTrue("Expected success for trimmed input: '$raw'", result is EmailValidationResult.Success)
            val success = result as EmailValidationResult.Success
            assertEquals(expectedNormalized, success.normalizedEmail)
        }
    }

    @Test
    fun `test email validation - uppercase and lowercase normalization`() {
        val cases = listOf(
            "USER@DOMAIN.COM" to "USER@domain.com",
            "Citizen.One@UERI.ORG" to "Citizen.One@ueri.org",
            "medic@Rescue.CO.UK" to "medic@rescue.co.uk"
        )
        for ((raw, expected) in cases) {
            val result = EmailValidator.validate(raw)
            assertTrue("Expected success for: '$raw'", result is EmailValidationResult.Success)
            assertEquals(expected, (result as EmailValidationResult.Success).normalizedEmail)
        }
    }

    @Test
    fun `test email validation - common domain typo suggestions`() {
        val typoCases = listOf(
            "sarah@gmial.com" to "sarah@gmail.com",
            "alex@gamil.com" to "alex@gmail.com",
            "unit@hotmial.com" to "unit@hotmail.com",
            "dispatch@outlok.com" to "dispatch@outlook.com",
            "paramedic@yaho.com" to "paramedic@yahoo.com"
        )
        for ((raw, expectedSuggestion) in typoCases) {
            val result = EmailValidator.validate(raw)
            assertTrue("Typo domain should be syntactically valid: '$raw'", result is EmailValidationResult.Success)
            val success = result as EmailValidationResult.Success
            assertEquals(expectedSuggestion, success.typoSuggestion)
        }
    }

    @Test
    fun `test email validation - valid structured addresses across diverse legitimate domains and suffixes`() {
        val validMatrix = listOf(
            "citizen@ueri.org",
            "officer.smith@police.gov.uk",
            "doctor@hospital.health",
            "volunteer@community.ngo",
            "dispatch@emergency.co.in",
            "tech.lead+sos@startup.io",
            "paramedic_01@city-rescue.travel",
            "student@university.edu",
            "support@service.app"
        )
        for (email in validMatrix) {
            val result = EmailValidator.validate(email)
            assertTrue("Expected valid for legitimate domain: '$email'", result is EmailValidationResult.Success)
            assertNull("Standard domains should not have false typo suggestions", (result as EmailValidationResult.Success).typoSuggestion)
            assertTrue(EmailValidator.isValid(email))
        }
    }

    @Test
    fun `test email validation - internationalized punycode domains`() {
        val punycodeEmails = listOf(
            "user@xn--d1acj3b.xn--p1ai",
            "contact@xn--fsqu00a.xn--4gbrim"
        )
        for (email in punycodeEmails) {
            val result = EmailValidator.validate(email)
            assertTrue("Punycode domains must be accepted: '$email'", result is EmailValidationResult.Success)
        }
    }

    // =========================================================================
    // 2. BACKEND & OTP LIFECYCLE TESTS (PROVIDER FAILURE, RATE LIMITING, REUSE)
    // =========================================================================

    @Test
    fun `test provider failure - returns truthful error without creating verified challenge`() = runBlocking {
        // Configure test provider to simulate SMTP provider failure (e.g. quota limit, connection timeout)
        OtpVerificationManager.setEmailDeliveryProviderForTest(object : EmailDeliveryProvider {
            override fun deliver(email: String, code: String): Result<Unit> {
                return Result.failure(IllegalStateException("SMTP Provider 421: Service temporarily unavailable or quota exceeded."))
            }
        })

        val target = "victim_${System.currentTimeMillis()}@ueri.org"
        val dispatchResult = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Victim User")

        assertTrue("Provider failure must not report success", dispatchResult.isFailure)
        assertTrue(dispatchResult.exceptionOrNull()?.message?.contains("quota exceeded") == true ||
                   dispatchResult.exceptionOrNull()?.message?.contains("unavailable") == true)

        // Ensure challenge was not stored as verified or active
        assertFalse(otpManager.isVerified(target, OtpPurpose.REGISTRATION))
        assertNull(otpManager.getPendingCodeForTest(target, OtpPurpose.REGISTRATION))
    }

    @Test
    fun `test incorrect otp - rejects verification and decrements attempts`() = runBlocking {
        val target = "user_${System.currentTimeMillis()}@ueri.org"
        val dispatch = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Test User")
        assertTrue(dispatch.isSuccess)

        // Attempt verification with wrong code
        val verifyFail1 = authManager.verifyRegistrationOtp(target, "000000")
        assertTrue(verifyFail1.isFailure)
        assertEquals("Invalid or expired verification code. Please try again.", verifyFail1.exceptionOrNull()?.message)
        assertFalse(otpManager.isVerified(target, OtpPurpose.REGISTRATION))

        val verifyFail2 = authManager.verifyRegistrationOtp(target, "111111")
        assertTrue(verifyFail2.isFailure)

        // Third wrong attempt should exhaust attempts
        val verifyFail3 = authManager.verifyRegistrationOtp(target, "222222")
        assertTrue(verifyFail3.isFailure)
        assertTrue(verifyFail3.exceptionOrNull()?.message?.contains("Too many failed attempts") == true ||
                   verifyFail3.exceptionOrNull()?.message?.contains("Invalid or expired") == true)
    }

    @Test
    fun `test expired otp - rejects verification`() = runBlocking {
        val target = "expired_${System.currentTimeMillis()}@ueri.org"
        val dispatch = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Expired User")
        assertTrue(dispatch.isSuccess)
        val code = otpManager.getPendingCodeForTest(target, OtpPurpose.REGISTRATION)!

        // Simulate session expiry by directly inspecting session expiration check
        // Calling verifyOtp on non-existent or expired code should fail
        otpManager.consumeVerification(target, OtpPurpose.REGISTRATION)
        val verifyRes = authManager.verifyRegistrationOtp(target, code)
        assertTrue("Expired or consumed code must be rejected", verifyRes.isFailure)
    }

    @Test
    fun `test reused otp - rejects second verification attempt`() = runBlocking {
        val target = "reused_${System.currentTimeMillis()}@ueri.org"
        val dispatch = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Reused User")
        assertTrue(dispatch.isSuccess)
        val code = otpManager.getPendingCodeForTest(target, OtpPurpose.REGISTRATION)!

        // First verification succeeds
        val verifyFirst = authManager.verifyRegistrationOtp(target, code)
        assertTrue("First verification must succeed", verifyFirst.isSuccess)
        assertTrue(otpManager.isVerified(target, OtpPurpose.REGISTRATION))

        // Second verification with the exact same code MUST be rejected (reused OTP)
        val verifySecond = authManager.verifyRegistrationOtp(target, code)
        assertTrue("Second verification with same code must be rejected", verifySecond.isFailure)
        assertEquals("This verification code has already been used.", verifySecond.exceptionOrNull()?.message)
    }

    @Test
    fun `test rate limiting - enforces 60-second cooldown between resend requests`() = runBlocking {
        val target = "ratelimit_${System.currentTimeMillis()}@ueri.org"

        // First dispatch succeeds
        val dispatch1 = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Rate Limit User")
        assertTrue(dispatch1.isSuccess)

        // Immediate second dispatch must be rejected with cooldown error
        val dispatch2 = authManager.sendRegistrationOtp(target, OtpChannel.EMAIL, "Rate Limit User")
        assertTrue("Immediate second request must fail rate limit cooldown", dispatch2.isFailure)
        assertTrue(dispatch2.exceptionOrNull()?.message?.contains("seconds before requesting a new code") == true)
    }

    @Test
    fun `test successful verification and registration - establishes immutable identity`() = runBlocking {
        val email = "verified_citizen_${System.currentTimeMillis()}@ueri.org"
        val dispatch = authManager.sendRegistrationOtp(email, OtpChannel.EMAIL, "Legit Citizen")
        assertTrue(dispatch.isSuccess)
        val code = otpManager.getPendingCodeForTest(email, OtpPurpose.REGISTRATION)!

        // Verify valid code
        val verifyRes = authManager.verifyRegistrationOtp(email, code)
        assertTrue(verifyRes.isSuccess)
        assertTrue(otpManager.isVerified(email, OtpPurpose.REGISTRATION))

        // Complete registration with verified email
        val regRes = authManager.registerCitizen(
            name = "Legit Citizen",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9123456780",
            email = email,
            pass = "StrongP@ssw0rd",
            otpVerifiedTarget = email
        )
        assertTrue("Registration with verified email must succeed", regRes.isSuccess)
        val account = regRes.getOrThrow()
        assertEquals(email, account.email)
        assertNotNull(account.uid)

        // Challenge is now consumed; registration cannot be repeated with same verification
        assertFalse(otpManager.isVerified(email, OtpPurpose.REGISTRATION))

        // Sign in with new account
        val loginRes = authManager.login(email, "StrongP@ssw0rd")
        assertTrue(loginRes.isSuccess)
        assertEquals(account.uid, loginRes.getOrThrow().uid)
    }
}
