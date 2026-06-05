package com.meterreader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.meterreader.data.AppDatabase
import com.meterreader.data.MeterReading

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).meterReadingDao()

    val allReadings: LiveData<List<MeterReading>> = dao.getAll()

    // Latest/previous are DERIVED from allReadings via map(), so they are
    // garbage-collected with the ViewModel. The previous code used
    // allReadings.observeForever {} in init and never removed the observer,
    // leaking the ViewModel (and the DB LiveData) for the process lifetime.
    val latestReading: LiveData<MeterReading?> = allReadings.map { it.getOrNull(0) }
    val previousReading: LiveData<MeterReading?> = allReadings.map { it.getOrNull(1) }

    suspend fun getAllReadingsSync(): List<MeterReading> = dao.getAllSync()
}
