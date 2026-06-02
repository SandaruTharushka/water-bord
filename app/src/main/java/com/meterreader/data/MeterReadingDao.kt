package com.meterreader.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MeterReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: MeterReading): Long

    @Query("SELECT * FROM meter_readings ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<MeterReading>>

    @Query("SELECT * FROM meter_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): MeterReading?

    @Query("SELECT * FROM meter_readings ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<MeterReading>

    @Query("DELETE FROM meter_readings WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
