package com.example.data

import com.example.model.EmergencySession
import com.example.model.EmergencyStatus
import com.example.model.Responder
import com.example.model.ResponderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirestoreEmergencyRepositoryTest {

    @Test
    fun repository_initializesWithoutCrash() {
        val repo = FirestoreEmergencyRepository()
        assertNotNull(repo)
        assertEquals("emergencies", FirestoreEmergencyRepository.COLLECTION_EMERGENCIES)
        assertEquals("responders", FirestoreEmergencyRepository.COLLECTION_RESPONDERS)
    }

    @Test
    fun repository_handlesUninitializedFirebaseGracefully() = runBlocking {
        // Without an active Firebase project in local unit test, isAvailable returns false
        // and flows return empty or safe fallbacks without throwing uncaught exceptions.
        val repo = FirestoreEmergencyRepository(null)
        assertEquals(false, repo.isAvailable())

        val activeList = repo.observeActiveEmergencies()
        assertNotNull(activeList)

        val respondersList = repo.observeResponders()
        assertNotNull(respondersList)

        val sampleSession = EmergencySession(
            id = "test-session-1",
            emergencyId = "UERI-999999",
            status = EmergencyStatus.ACTIVE,
            latitude = 37.7749,
            longitude = -122.4194
        )
        val publishResult = repo.publishEmergencyEvent(sampleSession)
        assertTrue(publishResult.isFailure)

        val sampleResponder = Responder(
            id = "resp-1",
            name = "Medic Unit Alpha",
            role = "Paramedic",
            latitude = 37.7750,
            longitude = -122.4200,
            status = ResponderStatus.ALERTED
        )
        val responderResult = repo.updateResponderLocation(sampleResponder)
        assertTrue(responderResult.isFailure)
    }
}
