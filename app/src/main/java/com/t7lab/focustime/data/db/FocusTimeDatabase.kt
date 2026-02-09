package com.t7lab.focustime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BlockedItem::class, Session::class, SessionItem::class],
    version = 2,
    exportSchema = true
)
abstract class FocusTimeDatabase : RoomDatabase() {
    abstract fun blockedItemDao(): BlockedItemDao
    abstract fun sessionDao(): SessionDao
}
