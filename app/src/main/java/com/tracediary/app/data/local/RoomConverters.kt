package com.tracediary.app.data.local

import androidx.room.TypeConverter
import com.tracediary.app.domain.model.SyncState
import java.time.Instant
import java.time.LocalDate

class RoomConverters {
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun syncStateToName(value: SyncState?): String? = value?.name

    @TypeConverter
    fun nameToSyncState(value: String?): SyncState? = value?.let(SyncState::valueOf)
}
