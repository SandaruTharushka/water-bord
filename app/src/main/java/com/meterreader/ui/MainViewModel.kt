package com.meterreader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.meterreader.data.AppDatabase
import com.meterreader.data.MeterReading

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).meterReadingDao()

    val allReadings: LiveData<List<MeterReading>> = dao.getAll()

    private val _latestReading = MutableLiveData<MeterReading?>()
    val latestReading: LiveData<MeterReading?> = _latestReading

    private val _previousReading = MutableLiveData<MeterReading?>()
    val previousReading: LiveData<MeterReading?> = _previousReading

    init {
        // Keep latest/previous in sync whenever the full list updates
        allReadings.observeForever { readings ->
            _latestReading.value  = readings?.getOrNull(0)
            _previousReading.value = readings?.getOrNull(1)
        }
    }

    suspend fun getAllReadingsSync(): List<MeterReading> = dao.getAllSync()
}
