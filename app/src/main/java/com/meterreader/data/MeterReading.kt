package com.meterreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meter_readings")
data class MeterReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,           // epoch millis
    val rawValue: Double?,         // parsed totalizer value
    val unit: String = "m³",
    val rawOcrText: String?,       // full OCR text for debugging
    val isValid: Boolean           // false if OCR returned no recognisable number
)
