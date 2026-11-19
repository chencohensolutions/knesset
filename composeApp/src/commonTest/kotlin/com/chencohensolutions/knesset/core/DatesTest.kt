package com.chencohensolutions.knesset.core

import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataInstant
import com.chencohensolutions.knesset.core.odata.parseODataInstant
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DatesTest {
    @Test
    fun parsesZeroToThreeFractionalDigits() {
        assertEquals(Instant.parse("2026-11-16T11:01:33Z"), parseODataInstant("2026-11-16T13:01:33+02:00"))
        assertEquals(Instant.parse("2026-11-16T11:01:33.700Z"), parseODataInstant("2026-11-16T13:01:33.7+02:00"))
        assertEquals(Instant.parse("2026-11-16T11:01:33.370Z"), parseODataInstant("2026-11-16T13:01:33.37+02:00"))
        assertEquals(Instant.parse("2026-11-16T11:01:33.717Z"), parseODataInstant("2026-11-16T13:01:33.717+02:00"))
    }

    @Test
    fun parsesBothIsraelOffsets() {
        assertEquals(Instant.parse("2026-07-27T21:00:00Z"), parseODataInstant("2026-07-28T00:00:00+03:00"))
        assertEquals(Instant.parse("2026-11-09T22:00:00Z"), parseODataInstant("2026-11-10T00:00:00+02:00"))
    }

    @Test
    fun valueWithoutOffsetIsIsraelLocalTime() {
        assertEquals(Instant.parse("2026-07-28T07:30:00Z"), parseODataInstant("2026-07-28T10:30:00"))
    }

    @Serializable
    private class Row(val d: ODataInstant? = null)

    @Test
    fun serializerHandlesNullAndValues() {
        val json = ODataClient.json
        assertNull(json.decodeFromString(Row.serializer(), """{"d":null}""").d)
        assertEquals(
            Instant.parse("2026-11-16T11:01:33.717Z"),
            json.decodeFromString(Row.serializer(), """{"d":"2026-11-16T13:01:33.717+02:00"}""").d,
        )
    }

    @Test
    fun displaysInIsraelTime() {
        val t = parseODataInstant("2026-11-23T10:00:00+02:00")
        assertEquals("23/11/2026", IsraelTime.date(t))
        assertEquals("10:00", IsraelTime.time(t))
        assertEquals("23/11/2026 10:00", IsraelTime.dateTime(t))
        assertEquals("28/07/2026", IsraelTime.dateTime(parseODataInstant("2026-07-28T00:00:00+03:00")))
    }
}
