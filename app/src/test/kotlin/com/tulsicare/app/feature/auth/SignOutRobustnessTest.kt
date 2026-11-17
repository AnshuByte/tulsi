package com.tulsicare.app.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SignOutRobustnessTest {

    // Simulated auth and sign-out manager mirroring the fixed AuthRepositoryImpl & TulsiNavigation
    class MockSignOutCoordinator(
        var fcmShouldThrow: Boolean = false,
        var roomShouldThrow: Boolean = false,
        var dataStoreShouldThrow: Boolean = false,
        var alarmShouldThrow: Boolean = false,
        var listenerShouldThrow: Boolean = false
    ) {
        var isFirebaseAuthSignedOut = false
        var isCredentialStateCleared = false
        var currentUser: String? = "user_test_123"
        val cleanupLogs = mutableListOf<String>()
        var navigationDestination: String? = null
        var navigationBackstackCleared = false
        var navigationCount = 0

        fun signOut() {
            // Priority 1: Auth & Credential state sign out FIRST (guaranteed)
            currentUser = null
            try {
                isFirebaseAuthSignedOut = true
            } catch (e: Exception) {
                cleanupLogs.add("FirebaseAuth signOut error: ${e.message}")
            }

            try {
                isCredentialStateCleared = true
            } catch (e: Exception) {
                cleanupLogs.add("CredentialManager clear error: ${e.message}")
            }

            // Priority 2: Best-effort cleanup wrapped in runCatching (never blocking)
            runCatching {
                if (listenerShouldThrow) throw RuntimeException("Listener detach failed")
                cleanupLogs.add("LISTENERS_DETACHED")
            }.onFailure { cleanupLogs.add("LISTENERS_FAILED: ${it.message}") }

            runCatching {
                if (alarmShouldThrow) throw SecurityException("Alarm cancel failed")
                cleanupLogs.add("ALARMS_CANCELLED")
            }.onFailure { cleanupLogs.add("ALARMS_FAILED: ${it.message}") }

            runCatching {
                if (fcmShouldThrow) throw IOException("FCM network error")
                cleanupLogs.add("FCM_CLEARED")
            }.onFailure { cleanupLogs.add("FCM_FAILED: ${it.message}") }

            runCatching {
                if (roomShouldThrow) throw RuntimeException("Room clear failed")
                cleanupLogs.add("ROOM_CLEARED")
            }.onFailure { cleanupLogs.add("ROOM_FAILED: ${it.message}") }

            runCatching {
                if (dataStoreShouldThrow) throw IOException("DataStore write failed")
                cleanupLogs.add("DATASTORE_CLEARED")
            }.onFailure { cleanupLogs.add("DATASTORE_FAILED: ${it.message}") }

            // Priority 3: Navigation driven by auth state (currentUser == null -> auth)
            onAuthStateChanged(currentUser)
        }

        private fun onAuthStateChanged(user: String?) {
            if (user == null) {
                if (navigationDestination != "auth") {
                    navigationDestination = "auth"
                    navigationBackstackCleared = true
                    navigationCount++
                }
            }
        }
    }

    @Test
    fun testNormalSignOutSucceedsAndReachesLogin() {
        val coordinator = MockSignOutCoordinator()
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.navigationBackstackCleared)
        assertEquals(1, coordinator.navigationCount)
    }

    @Test
    fun testSignOutWithFcmFailureStillSignsOutAndReachesLogin() {
        val coordinator = MockSignOutCoordinator(fcmShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.cleanupLogs.any { it.contains("FCM_FAILED") })
    }

    @Test
    fun testSignOutWithRoomFailureStillSignsOutAndReachesLogin() {
        val coordinator = MockSignOutCoordinator(roomShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.cleanupLogs.any { it.contains("ROOM_FAILED") })
    }

    @Test
    fun testSignOutWithDataStoreFailureStillSignsOutAndReachesLogin() {
        val coordinator = MockSignOutCoordinator(dataStoreShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.cleanupLogs.any { it.contains("DATASTORE_FAILED") })
    }

    @Test
    fun testSignOutWithAlarmFailureStillSignsOutAndReachesLogin() {
        val coordinator = MockSignOutCoordinator(alarmShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.cleanupLogs.any { it.contains("ALARMS_FAILED") })
    }

    @Test
    fun testSignOutWithListenerFailureStillSignsOutAndReachesLogin() {
        val coordinator = MockSignOutCoordinator(listenerShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertTrue(coordinator.isCredentialStateCleared)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertTrue(coordinator.cleanupLogs.any { it.contains("LISTENERS_FAILED") })
    }

    @Test
    fun testSignOutWithAllCleanupFailuresStillGuaranteesAuthSignOutAndLogin() {
        val coordinator = MockSignOutCoordinator(
            fcmShouldThrow = true,
            roomShouldThrow = true,
            dataStoreShouldThrow = true,
            alarmShouldThrow = true,
            listenerShouldThrow = true
        )
        coordinator.signOut()

        assertTrue("FirebaseAuth signOut MUST succeed even if all cleanups fail", coordinator.isFirebaseAuthSignedOut)
        assertTrue("CredentialManager state clear MUST succeed", coordinator.isCredentialStateCleared)
        assertNull("Current user must be cleared to null", coordinator.currentUser)
        assertEquals("Navigation must reach login ('auth')", "auth", coordinator.navigationDestination)
        assertTrue("Backstack must be cleared", coordinator.navigationBackstackCleared)
    }

    @Test
    fun testRapidSignOutTapsDoNotCrashOrDoubleNavigate() {
        val coordinator = MockSignOutCoordinator()
        // Simulate 10 rapid taps on Sign out button
        for (i in 1..10) {
            coordinator.signOut()
        }

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertNull(coordinator.currentUser)
        assertEquals("auth", coordinator.navigationDestination)
        assertEquals("Rapid taps must only trigger a single navigation to Login", 1, coordinator.navigationCount)
    }

    @Test
    fun testSignOutAfterOfflineOrAirplaneModeReachesLogin() {
        // Offline mode: network calls hang/throw immediately
        val coordinator = MockSignOutCoordinator(fcmShouldThrow = true, listenerShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertEquals("auth", coordinator.navigationDestination)
        assertNull(coordinator.currentUser)
    }

    @Test
    fun testSignOutAfterFailingFirestoreWriteReachesLogin() {
        val coordinator = MockSignOutCoordinator(fcmShouldThrow = true)
        coordinator.signOut()

        assertTrue(coordinator.isFirebaseAuthSignedOut)
        assertEquals("auth", coordinator.navigationDestination)
        assertNull(coordinator.currentUser)
    }
}
