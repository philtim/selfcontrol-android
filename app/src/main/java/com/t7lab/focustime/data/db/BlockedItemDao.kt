package com.t7lab.focustime.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedItemDao {
    @Query("SELECT * FROM blocked_items ORDER BY type, displayName")
    fun getAllItems(): Flow<List<BlockedItem>>

    @Query("SELECT * FROM blocked_items WHERE type = :type ORDER BY displayName")
    fun getItemsByType(type: BlockedItemType): Flow<List<BlockedItem>>

    @Query("SELECT * FROM blocked_items WHERE type = 'APP'")
    suspend fun getBlockedApps(): List<BlockedItem>

    @Query("SELECT * FROM blocked_items WHERE type = 'URL'")
    suspend fun getBlockedUrls(): List<BlockedItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlockedItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BlockedItem>)

    @Delete
    suspend fun delete(item: BlockedItem)

    @Query("DELETE FROM blocked_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM blocked_items")
    suspend fun getCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_items WHERE type = :type AND value = :value)")
    suspend fun exists(type: BlockedItemType, value: String): Boolean
}
