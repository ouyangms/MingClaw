package com.loy.mingclaw.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun stringMap_roundTrip() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val json = converters.fromStringMap(map)
        val result = converters.toStringMap(json)
        assertEquals(map, result)
    }

    @Test
    fun stringList_roundTrip() {
        val list = listOf("item1", "item2", "item3")
        val json = converters.fromStringList(list)
        val result = converters.toStringList(json)
        assertEquals(list, result)
    }

    @Test
    fun emptyMap_roundTrip() {
        val map = emptyMap<String, String>()
        val json = converters.fromStringMap(map)
        val result = converters.toStringMap(json)
        assertEquals(map, result)
    }
}
