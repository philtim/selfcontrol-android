package com.t7lab.focustime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BlockedItem::class, Session::class, SessionItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FocusTimeDatabase : RoomDatabase() {
    abstract fun blockedItemDao(): BlockedItemDao
    abstract fun sessionDao(): SessionDao
}
