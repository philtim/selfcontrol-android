package com.t7lab.focustime.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBlockedItemType(value: BlockedItemType): String = value.name

    @TypeConverter
    fun toBlockedItemType(value: String): BlockedItemType = BlockedItemType.valueOf(value)
}
