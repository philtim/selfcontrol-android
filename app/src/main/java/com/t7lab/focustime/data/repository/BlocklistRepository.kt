package com.t7lab.focustime.data.repository

import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemDao
import com.t7lab.focustime.data.db.BlockedItemType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlocklistRepository @Inject constructor(
    private val blockedItemDao: BlockedItemDao
) {
    fun getAllItems(): Flow<List<BlockedItem>> = blockedItemDao.getAllItems()

    fun getApps(): Flow<List<BlockedItem>> = blockedItemDao.getItemsByType(BlockedItemType.APP)

    fun getUrls(): Flow<List<BlockedItem>> = blockedItemDao.getItemsByType(BlockedItemType.URL)

    suspend fun addApp(packageName: String, displayName: String) {
        if (!blockedItemDao.exists(BlockedItemType.APP, packageName)) {
            blockedItemDao.insert(
                BlockedItem(
                    type = BlockedItemType.APP,
                    value = packageName,
                    displayName = displayName
                )
            )
        }
    }

    suspend fun addUrl(domain: String) {
        val isWildcard = domain.startsWith("*.")
        val displayName = domain
        val normalizedDomain = domain.lowercase().trim()

        if (!blockedItemDao.exists(BlockedItemType.URL, normalizedDomain)) {
            blockedItemDao.insert(
                BlockedItem(
                    type = BlockedItemType.URL,
                    value = normalizedDomain,
                    displayName = displayName,
                    isWildcard = isWildcard
                )
            )
        }
    }

    suspend fun removeItem(item: BlockedItem) {
        blockedItemDao.delete(item)
    }

    suspend fun removeById(id: Long) {
        blockedItemDao.deleteById(id)
    }

    suspend fun getBlockedApps(): List<BlockedItem> = blockedItemDao.getBlockedApps()

    suspend fun getBlockedUrls(): List<BlockedItem> = blockedItemDao.getBlockedUrls()

    suspend fun getItemCount(): Int = blockedItemDao.getCount()

    suspend fun getAllItemsList(): List<BlockedItem> {
        return blockedItemDao.getAllItemsSuspend()
    }
}
