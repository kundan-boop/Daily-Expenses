package com.example.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room TypeConverters for managing dates, lists, and custom types locally in Room.
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString("|")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        return data?.split("|")?.filter { it.isNotBlank() }
    }
}
