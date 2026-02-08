package com.t7lab.focustime.data.repository

import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.Session
import com.t7lab.focustime.data.db.SessionDao
import com.t7lab.focustime.data.db.SessionItem
import com.t7lab.focustime.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val preferencesManager: PreferencesManager
) {
    fun getActiveSession(): Flow<Session?> = sessionDao.getActiveSession()

    fun getActiveBlockedItems(): Flow<List<BlockedItem>> = sessionDao.getActiveBlockedItemsFlow()

    suspend fun startSession(durationMs: Long, blockedItemIds: List<Long>): Long {
        // Deactivate any existing sessions
        sessionDao.deactivateAllSessions()

        val now = System.currentTimeMillis()
        val endTime = now + durationMs

        val session = Session(
            startTime = now,
            durationMs = durationMs,
            endTime = endTime,
            isActive = true
        )

        val sessionId = sessionDao.insert(session)

        val sessionItems = blockedItemIds.map { itemId ->
            SessionItem(sessionId = sessionId, blockedItemId = itemId)
        }
        sessionDao.insertSessionItems(sessionItems)

        // Also persist to DataStore for quick service access
        preferencesManager.startSession(sessionId, endTime)

        return sessionId
    }

    suspend fun endSession() {
        val session = sessionDao.getActiveSessionOnce()
        if (session != null) {
            sessionDao.deactivateSession(session.id)
        }
        preferencesManager.endSession()
    }

    suspend fun getActiveBlockedItemsOnce(): List<BlockedItem> {
        return sessionDao.getActiveBlockedItems()
    }

    suspend fun isSessionActive(): Boolean {
        return preferencesManager.isSessionActiveOnce()
    }

    suspend fun getSessionEndTime(): Long {
        return preferencesManager.getSessionEndTimeOnce()
    }
}
