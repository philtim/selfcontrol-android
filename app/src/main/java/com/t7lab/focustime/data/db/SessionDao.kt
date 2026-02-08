package com.t7lab.focustime.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSession(): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSessionOnce(): Session?

    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: Long)

    @Query("UPDATE sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllSessions()

    @Insert
    suspend fun insertSessionItem(sessionItem: SessionItem)

    @Insert
    suspend fun insertSessionItems(sessionItems: List<SessionItem>)

    @Query("""
        SELECT bi.* FROM blocked_items bi
        INNER JOIN session_items si ON bi.id = si.blockedItemId
        WHERE si.sessionId = :sessionId
    """)
    suspend fun getBlockedItemsForSession(sessionId: Long): List<BlockedItem>

    @Query("""
        SELECT bi.* FROM blocked_items bi
        INNER JOIN session_items si ON bi.id = si.blockedItemId
        INNER JOIN sessions s ON si.sessionId = s.id
        WHERE s.isActive = 1
    """)
    suspend fun getActiveBlockedItems(): List<BlockedItem>

    @Query("""
        SELECT bi.* FROM blocked_items bi
        INNER JOIN session_items si ON bi.id = si.blockedItemId
        INNER JOIN sessions s ON si.sessionId = s.id
        WHERE s.isActive = 1
    """)
    fun getActiveBlockedItemsFlow(): Flow<List<BlockedItem>>
}
