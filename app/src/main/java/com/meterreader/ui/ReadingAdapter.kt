package com.meterreader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meterreader.R
import com.meterreader.data.MeterReading
import com.meterreader.databinding.ItemReadingBinding
import java.text.SimpleDateFormat
import java.util.*

class ReadingAdapter : ListAdapter<MeterReading, ReadingAdapter.ViewHolder>(DIFF) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReadingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val current = getItem(position)
        val previous = if (position + 1 < itemCount) getItem(position + 1) else null
        holder.bind(current, previous)
    }

    inner class ViewHolder(private val b: ItemReadingBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(reading: MeterReading, previous: MeterReading?) {
            b.textTimestamp.text = dateFormat.format(Date(reading.timestamp))
            b.textValue.text = if (reading.isValid && reading.rawValue != null)
                "${"%.2f".format(reading.rawValue)} ${reading.unit}"
            else
                "OCR failed"

            val indicatorColor = when {
                !reading.isValid -> R.color.confidence_red
                previous?.rawValue != null && reading.rawValue != null -> {
                    val delta = kotlin.math.abs(reading.rawValue - previous.rawValue) / previous.rawValue * 100
                    if (delta > 5) R.color.confidence_yellow else R.color.confidence_green
                }
                else -> R.color.confidence_green
            }
            b.viewConfidenceIndicator.setBackgroundColor(
                ContextCompat.getColor(b.root.context, indicatorColor)
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MeterReading>() {
            override fun areItemsTheSame(a: MeterReading, b: MeterReading) = a.id == b.id
            override fun areContentsTheSame(a: MeterReading, b: MeterReading) = a == b
        }
    }
}
