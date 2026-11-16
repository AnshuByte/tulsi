package com.tulsicare.app.core.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreListenerManager @Inject constructor() {

    private data class ListenerKey(
        val userId: String,
        val groupId: String,
        val tag: String
    )

    private val registrations = ConcurrentHashMap<ListenerKey, ListenerRegistration>()

    fun register(
        userId: String,
        groupId: String,
        tag: String,
        registration: ListenerRegistration
    ) {
        val key = ListenerKey(userId, groupId, tag)
        registrations.remove(key)?.remove()
        registrations[key] = registration
    }

    fun removeListener(userId: String, groupId: String, tag: String) {
        val key = ListenerKey(userId, groupId, tag)
        registrations.remove(key)?.remove()
    }

    fun removeListenersForGroup(groupId: String) {
        val iterator = registrations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.groupId == groupId) {
                runCatching { entry.value.remove() }
                    .onFailure { Log.e("FirestoreListenerMgr", "Error removing group listener", it) }
                iterator.remove()
            }
        }
    }

    fun removeListenersForUser(userId: String) {
        val iterator = registrations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.userId == userId) {
                runCatching { entry.value.remove() }
                    .onFailure { Log.e("FirestoreListenerMgr", "Error removing user listener", it) }
                iterator.remove()
            }
        }
    }

    fun clearAllListeners() {
        for ((_, reg) in registrations) {
            runCatching { reg.remove() }
                .onFailure { Log.e("FirestoreListenerMgr", "Error removing listener", it) }
        }
        registrations.clear()
    }

    fun activeListenerCount(): Int = registrations.size
}
