package com.t7lab.focustime.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockedItemType {
    APP,
    URL
}

@Entity(tableName = "blocked_items")
data class BlockedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockedItemType,
    val value: String, // package name for apps, domain pattern for URLs
    val displayName: String,
    val isWildcard: Boolean = false // for URLs: *.domain.com
)

@Entity(
    tableName = "sessions",
    indices = [Index("isActive")]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val durationMs: Long,
    val endTime: Long,
    val isActive: Boolean
)

@Entity(
    tableName = "session_items",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BlockedItem::class,
            parentColumns = ["id"],
            childColumns = ["blockedItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("blockedItemId")
    ]
)
data class SessionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val blockedItemId: Long
)
