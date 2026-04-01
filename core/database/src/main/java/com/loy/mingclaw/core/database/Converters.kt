package com.loy.mingclaw.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>): String = json.encodeToString(
        kotlinx.serialization.serializer(),
        map,
    )

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = json.decodeFromString(
        kotlinx.serialization.serializer(),
        value,
    )

    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(
        kotlinx.serialization.serializer(),
        list,
    )

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(
        kotlinx.serialization.serializer(),
        value,
    )
}
