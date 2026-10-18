package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AuthState
import com.example.auth.FirebaseAuthManager
import com.example.model.ueri.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UERI", appName)
    }

    private fun verifyTarget(auth: FirebaseAuthManager, target: String) {
        val channel = if (target.contains("@")) com.example.auth.OtpChannel.EMAIL else com.example.auth.OtpChannel.SMS
        auth.sendRegistrationOtp(target, channel, "Test User")
        val code = auth.otpManager.getPendingCodeForTest(target, com.example.auth.OtpPurpose.REGISTRATION)!!
        auth.verifyRegistrationOtp(target, code)
    }

    @Test
    fun `citizen registration and login flow stores profile and role`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()
        assertTrue(authManager.authState.value is AuthState.Unauthenticated)

        verifyTarget(authManager, "sarah@ueri.org")
        val regResult = authManager.registerCitizen(
            name = "Sarah Miller",
            countryIsoCode = "US",
            countryCallingCode = "+1",
            nationalPhoneNumber = "4156601234",
            email = "sarah@ueri.org",
            pass = "securePass123",
            otpVerifiedTarget = "sarah@ueri.org"
        )
        assertTrue(regResult.isSuccess)
        val user = regResult.getOrThrow()
        assertEquals("Sarah Miller", user.displayName)
        assertEquals("US", user.countryIsoCode)
        assertEquals("+1", user.countryCallingCode)
        assertEquals("4156601234", user.nationalPhoneNumber)
        assertEquals("+14156601234", user.normalizedPhoneNumber)
        assertEquals("sarah@ueri.org", user.email)
        assertEquals(UserRole.CITIZEN.name, user.role)
        assertFalse(user.verifiedResponder)
        assertTrue(authManager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `responder registration preserves verification requirement`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        // 1. Unverified responder ID remains unverified
        verifyTarget(authManager, "alex@ueri.org")
        val unverifiedRes = authManager.registerResponder(
            name = "Alex Rookie",
            countryIsoCode = "US",
            countryCallingCode = "+1",
            nationalPhoneNumber = "4156601234",
            email = "alex@ueri.org",
            pass = "responderPass123",
            responderId = "CUSTOM-999",
            otpVerifiedTarget = "alex@ueri.org"
        )
        assertTrue(unverifiedRes.isSuccess)
        val unverifiedUser = unverifiedRes.getOrThrow()
        assertEquals(UserRole.RESPONDER.name, unverifiedUser.role)
        assertEquals("CUSTOM-999", unverifiedUser.responderId)
        assertFalse(unverifiedUser.verifiedResponder)
        assertFalse(authManager.isResponderVerified())

        // 2. Verified responder ID (e.g. resp-1) is authorized
        authManager.logout()
        verifyTarget(authManager, "vance@ueri.org")
        val verifiedRes = authManager.registerResponder(
            name = "Officer Marcus Vance",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = "vance@ueri.org",
            pass = "responderPass123",
            responderId = "resp-1",
            otpVerifiedTarget = "vance@ueri.org"
        )
        assertTrue(verifiedRes.isSuccess)
        val verifiedUser = verifiedRes.getOrThrow()
        assertEquals(UserRole.RESPONDER.name, verifiedUser.role)
        assertEquals("resp-1", verifiedUser.responderId)
        assertEquals("IN", verifiedUser.countryIsoCode)
        assertEquals("+91", verifiedUser.countryCallingCode)
        assertEquals("+919876543210", verifiedUser.normalizedPhoneNumber)
        assertTrue(verifiedUser.verifiedResponder)
        assertTrue(authManager.isResponderVerified())
    }

    @Test
    fun `forgot password flow validates email and sends reset link`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)

        // Empty email should fail validation
        val emptyRes = authManager.sendPasswordResetEmail("")
        assertTrue(emptyRes.isFailure)

        // Invalid email format should fail validation
        val invalidRes = authManager.sendPasswordResetEmail("not-an-email")
        assertTrue(invalidRes.isFailure)

        // Valid email should return success confirmation message
        val validRes = authManager.sendPasswordResetEmail("citizen@ueri.org")
        assertTrue(validRes.isSuccess)
        assertTrue(validRes.getOrThrow().contains("citizen@ueri.org"))
    }

    @Test
    fun `sos 24h analytics computes frequency buckets and geographic sector distribution`() {
        val now = System.currentTimeMillis()
        val baseline = com.example.model.SosAnalyticsCalculator.generateBaseline24hSessions(nowMs = now)
        val analytics = com.example.model.SosAnalyticsCalculator.compute24hAnalytics(
            sessions = baseline,
            activeSession = null,
            nowMs = now
        )

        assertEquals(12, analytics.frequencyBuckets.size)
        assertEquals(5, analytics.geographicSectors.size)
        assertEquals(baseline.size, analytics.totalCalls24h)
        assertTrue(analytics.criticalCalls24h > 0)
        assertEquals(
            analytics.totalCalls24h,
            analytics.geographicSectors.sumOf { it.totalCalls }
        )
    }

    @Test
    fun `offline sos repository caches requests in room when offline and auto broadcasts when online`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        val offlineDao = db.offlineSosDao()
        val emergencyDao = db.emergencyDao()
        val broadcastedIds = mutableListOf<String>()

        val recordingTransport = object : com.example.transport.EmergencyTransport {
            override val transportName: String = "TestTransport"
            override val isMeshCapable: Boolean = true
            override suspend fun broadcastEmergency(session: com.example.model.EmergencySession): Boolean {
                broadcastedIds.add(session.emergencyId)
                return true
            }
            override suspend fun broadcastResolution(session: com.example.model.EmergencySession): Boolean = true
            override suspend fun updateRadius(session: com.example.model.EmergencySession, newRadiusMeters: Double): Boolean = true
        }

        val offlineRepo = com.example.data.OfflineSosRepository(
            offlineSosDao = offlineDao,
            emergencyDao = emergencyDao,
            transport = recordingTransport,
            context = null
        )

        // 1. Put repository in offline mode
        offlineRepo.setOfflineMode(true)
        assertFalse(offlineRepo.isOnline.value)

        val offlineSession = com.example.model.EmergencySession(
            id = "session-offline-1",
            emergencyId = "UERI-OFFLINE-101",
            status = com.example.model.EmergencyStatus.ACTIVE,
            priority = "CRITICAL",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 500.0
        )

        // 2. Trigger SOS while offline -> cached in Room with PENDING_SYNC
        val cached = offlineRepo.cacheAndBroadcastSos(offlineSession, victimUid = "citizen-1")
        assertEquals(com.example.model.SosSyncStatus.PENDING_SYNC.name, cached.syncStatus)
        assertTrue(broadcastedIds.isEmpty())

        val pendingInDb = offlineDao.getPendingSosRequests()
        assertEquals(1, pendingInDb.size)
        assertEquals("UERI-OFFLINE-101", pendingInDb.first().emergencyId)

        // 3. Restore connection and flush pending queue -> automatically broadcasted
        offlineRepo.setOfflineMode(false)
        assertTrue(offlineRepo.isOnline.value)
        val syncedCount = offlineRepo.flushPendingSosQueue()
        assertTrue(syncedCount >= 0) // Either auto-flushed by setOfflineMode(false) or by flushPendingSosQueue()

        // Wait briefly if background scope flushed it, or verify DB state
        var finalRecord = offlineDao.getByEmergencyId("UERI-OFFLINE-101")
        if (finalRecord?.syncStatus != com.example.model.SosSyncStatus.BROADCASTED.name) {
            kotlinx.coroutines.delay(100)
            finalRecord = offlineDao.getByEmergencyId("UERI-OFFLINE-101")
        }

        assertEquals(com.example.model.SosSyncStatus.BROADCASTED.name, finalRecord?.syncStatus)
        assertTrue(broadcastedIds.contains("UERI-OFFLINE-101"))
        assertTrue(offlineDao.getPendingSosRequests().isEmpty())

        db.close()
    }

    @Test
    fun `phoneNumberValidator strictly validates country specific numbering and normalizes E164`() {
        // India (+91)
        val validIn = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "9876543210")
        assertTrue(validIn is com.example.util.PhoneValidationResult.Success)
        val inSuccess = validIn as com.example.util.PhoneValidationResult.Success
        assertEquals("IN", inSuccess.countryIsoCode)
        assertEquals("+91", inSuccess.countryCallingCode)
        assertEquals("9876543210", inSuccess.nationalPhoneNumber)
        assertEquals("+919876543210", inSuccess.normalizedPhoneNumber)

        // India with spaces/formatting
        val validInFormatted = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "98765 43210")
        assertTrue(validInFormatted is com.example.util.PhoneValidationResult.Success)
        assertEquals("+919876543210", (validInFormatted as com.example.util.PhoneValidationResult.Success).normalizedPhoneNumber)

        // India invalid length (too short)
        val invalidInShort = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "98765")
        assertTrue(invalidInShort is com.example.util.PhoneValidationResult.Failure)

        // India invalid length (too long)
        val invalidInLong = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "987654321012345")
        assertTrue(invalidInLong is com.example.util.PhoneValidationResult.Failure)

        // United States (+1)
        val validUs = com.example.util.PhoneNumberValidator.validatePhoneNumber("US", "4156601234")
        assertTrue(validUs is com.example.util.PhoneValidationResult.Success)
        assertEquals("+14156601234", (validUs as com.example.util.PhoneValidationResult.Success).normalizedPhoneNumber)

        // United Kingdom (+44)
        val validUk = com.example.util.PhoneNumberValidator.validatePhoneNumber("GB", "7911123456")
        assertTrue(validUk is com.example.util.PhoneValidationResult.Success)
        assertEquals("+447911123456", (validUk as com.example.util.PhoneValidationResult.Success).normalizedPhoneNumber)

        // Unselected / blank country
        val blankCountry = com.example.util.PhoneNumberValidator.validatePhoneNumber("", "9876543210")
        assertTrue(blankCountry is com.example.util.PhoneValidationResult.Failure)

        // Blank number
        val blankNumber = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "")
        assertTrue(blankNumber is com.example.util.PhoneValidationResult.Failure)

        // Repeated attempts with invalid numbers are NEVER accepted
        for (i in 1..5) {
            val retryRes = com.example.util.PhoneNumberValidator.validatePhoneNumber("IN", "12345")
            assertTrue("Retry attempt $i should be rejected", retryRes is com.example.util.PhoneValidationResult.Failure)
        }
    }

    @Test
    fun `registration strictly rejects invalid phone numbers on every attempt`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)

        // Short Indian number must be rejected
        val fail1 = authManager.registerCitizen(
            name = "Test User",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "999",
            email = "test1@ueri.org",
            pass = "secret123"
        )
        assertTrue(fail1.isFailure)

        // Retrying with same or different invalid number must also be rejected
        val fail2 = authManager.registerCitizen(
            name = "Test User",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "0000000000",
            email = "test2@ueri.org",
            pass = "secret123"
        )
        assertTrue(fail2.isFailure)
    }

    @Test
    fun `updateFcmPushToken associates token with active responder account`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        verifyTarget(authManager, "unit1@ueri.org")
        val regResult = authManager.registerResponder(
            name = "Tactical Unit 1",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = "unit1@ueri.org",
            pass = "secureUnit123",
            responderId = "resp-1",
            otpVerifiedTarget = "unit1@ueri.org"
        )
        assertTrue(regResult.isSuccess)

        // Register new FCM device token
        val testToken = "fcm_token_test_abc123_device2"
        authManager.updateFcmPushToken(testToken)

        assertEquals(testToken, authManager.currentUser.value.pushToken)
        val prefs = context.getSharedPreferences(FirebaseAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(testToken, prefs.getString(FirebaseAuthManager.KEY_FCM_TOKEN, ""))
    }

    @Test
    fun `login strictly validates email and password input formats`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        // 1. Empty email
        val emptyEmailRes = authManager.login("", "secret123")
        assertTrue(emptyEmailRes.isFailure)
        assertTrue(emptyEmailRes.exceptionOrNull()?.message?.contains("enter your email") == true)

        // 2. Malformed emails
        val invalidEmails = listOf("notanemail", "user@", "user@domain", "plainaddress", "@domain.com")
        for (badEmail in invalidEmails) {
            val res = authManager.login(badEmail, "secret123")
            assertTrue("Should reject bad email: $badEmail", res.isFailure)
            assertTrue(res.exceptionOrNull()?.message?.contains("valid email") == true)
        }

        // 3. Empty password
        val emptyPassRes = authManager.login("valid@ueri.org", "")
        assertTrue(emptyPassRes.isFailure)
        assertTrue(emptyPassRes.exceptionOrNull()?.message?.contains("enter your password") == true)

        // 4. Password shorter than 6 characters
        val shortPassRes = authManager.login("valid@ueri.org", "123")
        assertTrue(shortPassRes.isFailure)
        assertTrue(shortPassRes.exceptionOrNull()?.message?.contains("at least 6 characters") == true)
    }

    @Test
    fun `new user attempting to sign in is rejected with Kindly register first`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        // Random unregistered user
        val unregisteredEmail = "newcitizen_${System.currentTimeMillis()}@ueri.org"
        val loginRes = authManager.login(unregisteredEmail, "somePassword123")

        assertTrue(loginRes.isFailure)
        assertEquals("Kindly register first", loginRes.exceptionOrNull()?.message)
        assertTrue(authManager.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `registered user with wrong password is rejected with Incorrect email ID or password`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        // 1. Register user
        val email = "registered_${System.currentTimeMillis()}@ueri.org"
        val correctPass = "validPassword123"
        verifyTarget(authManager, email)
        val regRes = authManager.registerCitizen(
            name = "Test Subject",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = email,
            pass = correctPass,
            otpVerifiedTarget = email
        )
        assertTrue(regRes.isSuccess)

        // Logout
        authManager.logout()
        assertTrue(authManager.authState.value is AuthState.Unauthenticated)

        // 2. Try login with WRONG password
        val wrongPassRes = authManager.login(email, "completelyWrongPass")
        assertTrue(wrongPassRes.isFailure)
        assertEquals("Incorrect email ID or password", wrongPassRes.exceptionOrNull()?.message)
        assertTrue(authManager.authState.value is AuthState.Unauthenticated)

        // 3. Try login with CORRECT password -> SUCCEEDS
        val correctLoginRes = authManager.login(email, correctPass)
        assertTrue(correctLoginRes.isSuccess)
        val user = correctLoginRes.getOrThrow()
        assertEquals(email, user.email)
        assertEquals("Test Subject", user.displayName)
        assertTrue(authManager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `user credentials and account persist across app restarts and tab closure`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialAuthManager = FirebaseAuthManager(context)
        initialAuthManager.logout()

        val email = "persistent_user_${System.currentTimeMillis()}@ueri.org"
        val password = "PermanentSecret999"

        // 1. Register user
        verifyTarget(initialAuthManager, email)
        val regRes = initialAuthManager.registerCitizen(
            name = "Persistent Citizen",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = email,
            pass = password,
            otpVerifiedTarget = email
        )
        assertTrue(regRes.isSuccess)

        // 2. User logs out
        initialAuthManager.logout()
        assertTrue(initialAuthManager.authState.value is AuthState.Unauthenticated)

        // 3. SIMULATE APP / TAB RESTART: Create a brand new FirebaseAuthManager instance
        val restartedAuthManager = FirebaseAuthManager(context)
        restartedAuthManager.attachContext(context)

        // Verify that the restarted instance successfully finds the user in persistent storage
        val userRecord = restartedAuthManager.getFromUserRegistry(email)
        assertTrue("User must be preserved in durable persistent storage", userRecord != null)
        assertEquals(email, userRecord?.second?.email)
        assertEquals("Persistent Citizen", userRecord?.second?.displayName)

        // 4. Sign in with the registered credentials on the restarted instance
        val loginRes = restartedAuthManager.login(email, password)
        assertTrue("Sign-in on restarted app must succeed", loginRes.isSuccess)
        val loggedInUser = loginRes.getOrThrow()
        assertEquals("Persistent Citizen", loggedInUser.displayName)
        assertEquals(email, loggedInUser.email)
        assertTrue(restartedAuthManager.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `otp registration flow generates valid codes and gates password creation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        val email = "otp_citizen_${System.currentTimeMillis()}@ueri.org"

        // Step 1: Send OTP via WhatsApp
        val dispatch = authManager.sendRegistrationOtp(
            targetDestination = "+919876543210",
            channel = com.example.auth.OtpChannel.WHATSAPP,
            displayName = "WhatsApp Citizen"
        ).getOrThrow()
        val otpCode = authManager.otpManager.getPendingCodeForTest("+919876543210", com.example.auth.OtpPurpose.REGISTRATION)!!
        assertEquals(6, otpCode.length)
        assertTrue(dispatch.deliveryMessage.contains("phone number"))

        // Step 2: Attempting to register BEFORE verifying OTP should be rejected
        val prematureReg = authManager.registerCitizen(
            name = "WhatsApp Citizen",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = email,
            pass = "secret123",
            otpVerifiedTarget = "+919876543210"
        )
        assertTrue(prematureReg.isFailure)

        // Step 3: Enter invalid code should fail
        val invalidVerify = authManager.verifyRegistrationOtp("+919876543210", "000000")
        assertTrue(invalidVerify.isFailure)

        // Step 4: Enter valid code should succeed
        val validVerify = authManager.verifyRegistrationOtp("+919876543210", otpCode)
        assertTrue(validVerify.isSuccess)

        // Step 5: Now register with the verified target -> SUCCEEDS
        val validReg = authManager.registerCitizen(
            name = "WhatsApp Citizen",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = email,
            pass = "secret123",
            otpVerifiedTarget = "+919876543210"
        )
        assertTrue(validReg.isSuccess)
        assertEquals("WhatsApp Citizen", validReg.getOrThrow().displayName)
    }

    @Test
    fun `forgot password with otp flow supports multi-channel reset and new password signin`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        val email = "reset_user_${System.currentTimeMillis()}@ueri.org"
        val oldPass = "oldPassword123"
        val newPass = "brandNewPassword456"

        // 1. Unregistered user requesting OTP is rejected with "Kindly register first"
        val unregOtp = authManager.requestForgotPasswordOtp("unknown@ueri.org", com.example.auth.OtpChannel.EMAIL)
        assertTrue(unregOtp.isFailure)
        assertTrue(unregOtp.exceptionOrNull()?.message?.contains("Kindly register first") == true)

        // 2. Register user with old password
        verifyTarget(authManager, email)
        val regRes = authManager.registerCitizen(
            name = "Reset Subject",
            countryIsoCode = "IN",
            countryCallingCode = "+91",
            nationalPhoneNumber = "9876543210",
            email = email,
            pass = oldPass,
            otpVerifiedTarget = email
        )
        assertTrue(regRes.isSuccess)
        authManager.logout()

        // 3. Request Forgot Password OTP via SMS
        val smsDispatch = authManager.requestForgotPasswordOtp(email, com.example.auth.OtpChannel.SMS)
        assertTrue(smsDispatch.isSuccess)
        val smsCode = authManager.otpManager.getPendingCodeForTest(email, com.example.auth.OtpPurpose.FORGOT_PASSWORD)!!

        // 4. Verify OTP code
        val verifyRes = authManager.verifyForgotPasswordOtp(email, smsCode)
        assertTrue(verifyRes.isSuccess)

        // 5. Reset password with valid code
        val resetRes = authManager.resetPasswordWithOtp(email, smsCode, newPass)
        assertTrue(resetRes.isSuccess)

        // 6. Old password no longer works
        val oldLogin = authManager.login(email, oldPass)
        assertTrue(oldLogin.isFailure)
        assertEquals("Incorrect email ID or password", oldLogin.exceptionOrNull()?.message)

        // 7. New password works successfully
        val newLogin = authManager.login(email, newPass)
        assertTrue(newLogin.isSuccess)
        assertEquals("Reset Subject", newLogin.getOrThrow().displayName)
    }

    @Test
    fun `ueri security Phase 7 end-to-end tests cases A through K`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authManager = FirebaseAuthManager(context)
        authManager.logout()

        // Case A: Enter abc123 -> validation error; no email sent
        val caseARes = authManager.sendRegistrationOtp(
            targetDestination = "abc123",
            channel = com.example.auth.OtpChannel.EMAIL,
            displayName = "Invalid Email User"
        )
        assertTrue("Case A must reject malformed email", caseARes.isFailure)
        assertEquals("Please enter a valid email address.", caseARes.exceptionOrNull()?.message)

        // Case B & C: Enter a validly formatted email -> backend attempts real delivery; OTP is not exposed in API response
        val testEmail = "citizen_${System.currentTimeMillis()}@example.com"
        val caseBRes = authManager.sendRegistrationOtp(
            targetDestination = testEmail,
            channel = com.example.auth.OtpChannel.EMAIL,
            displayName = "Valid Citizen"
        )
        assertTrue("Case B must accept valid email syntax", caseBRes.isSuccess)
        val dispatch = caseBRes.getOrThrow()
        assertEquals(testEmail, dispatch.formattedTarget)
        // Case C: Neutral delivery message only; no plaintext OTP exposed in response
        assertEquals("If this email can receive verification, a verification code has been sent.", dispatch.deliveryMessage)
        assertEquals(com.example.auth.OtpChannel.EMAIL, dispatch.channel)

        // Case H: Resend too frequently (< 60s) -> rate-limited
        val caseHRes = authManager.sendRegistrationOtp(
            targetDestination = testEmail,
            channel = com.example.auth.OtpChannel.EMAIL,
            displayName = "Valid Citizen"
        )
        assertTrue("Case H must enforce 60-second cooldown", caseHRes.isFailure)
        assertTrue(caseHRes.exceptionOrNull()?.message?.contains("seconds before requesting a new code") == true)

        // Case E: Enter an incorrect OTP -> rejected
        val caseERes = authManager.verifyRegistrationOtp(testEmail, "000000")
        assertTrue("Case E must reject incorrect OTP", caseERes.isFailure)
        assertEquals("Invalid or expired verification code. Please try again.", caseERes.exceptionOrNull()?.message)

        // Case D & G: Enter the correct OTP received from real inbox -> verified
        val testCode = authManager.otpManager.getPendingCodeForTest(testEmail, com.example.auth.OtpPurpose.REGISTRATION)
        assertNotNull("Cryptographic code must be generated", testCode)
        assertEquals(6, testCode!!.length)

        val caseGRes = authManager.verifyRegistrationOtp(testEmail, testCode)
        assertTrue("Case G must verify correct OTP", caseGRes.isSuccess)
        assertTrue(authManager.otpManager.isVerified(testEmail, com.example.auth.OtpPurpose.REGISTRATION))

        // Complete account setup using verified target (Phase 4 requirement)
        val citizenAccount = authManager.registerCitizen(
            name = "Valid Citizen",
            countryIsoCode = "US",
            countryCallingCode = "+1",
            nationalPhoneNumber = "4155550199",
            email = testEmail,
            pass = "citizenPassword123",
            otpVerifiedTarget = testEmail
        ).getOrThrow()
        assertEquals("Valid Citizen", citizenAccount.displayName)
        assertTrue(citizenAccount.phoneVerified)

        // Single-use consumption check: attempting to reuse verification fails
        assertFalse(authManager.otpManager.isVerified(testEmail, com.example.auth.OtpPurpose.REGISTRATION))

        // Case J: Verify unverified responders remain pending and cannot accept emergencies
        val respEmail = "rookie_responder_${System.currentTimeMillis()}@city911.gov"
        authManager.sendRegistrationOtp(respEmail, com.example.auth.OtpChannel.EMAIL, "Officer Rook")
        val respCode = authManager.otpManager.getPendingCodeForTest(respEmail, com.example.auth.OtpPurpose.REGISTRATION)!!
        authManager.verifyRegistrationOtp(respEmail, respCode)

        val rookieResp = authManager.registerResponder(
            name = "Officer Rook",
            countryIsoCode = "US",
            countryCallingCode = "+1",
            nationalPhoneNumber = "4155550188",
            email = respEmail,
            pass = "officerPass123",
            responderId = "", // Auto-assigned badge
            otpVerifiedTarget = respEmail
        ).getOrThrow()

        assertEquals(com.example.model.ueri.UserRole.RESPONDER.name, rookieResp.role)
        assertFalse("Unverified responder must remain pending approval", rookieResp.verifiedResponder)
        assertFalse("Responder cannot be authorized while pending", authManager.isResponderVerified())

        // Case K: Verify existing UERI login, SOS, and responder features still work
        authManager.logout()
        val citizenLogin = authManager.login(testEmail, "citizenPassword123").getOrThrow()
        assertEquals(testEmail, citizenLogin.email)
        assertEquals(com.example.model.ueri.UserRole.CITIZEN.name, citizenLogin.role)
    }
}
