package com.tracediary.app.data.local

import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun localDate_roundTripsThroughEpochDay() {
        val date = LocalDate.parse("2026-05-17")

        val encoded = converters.localDateToEpochDay(date)

        assertEquals(date, converters.epochDayToLocalDate(encoded))
    }

    @Test
    fun instant_roundTripsThroughEpochMilli() {
        val instant = Instant.parse("2026-05-17T08:30:12.123Z")

        val encoded = converters.instantToEpochMilli(instant)

        assertEquals(instant, converters.epochMilliToInstant(encoded))
    }

    @Test
    fun syncState_roundTripsThroughName() {
        val encoded = converters.syncStateToName(SyncState.Pending)

        assertEquals(SyncState.Pending, converters.nameToSyncState(encoded))
    }

    @Test
    fun nullableValues_remainNull() {
        assertNull(converters.localDateToEpochDay(null))
        assertNull(converters.epochDayToLocalDate(null))
        assertNull(converters.instantToEpochMilli(null))
        assertNull(converters.epochMilliToInstant(null))
        assertNull(converters.syncStateToName(null))
        assertNull(converters.nameToSyncState(null))
    }
}
