package com.meterreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.meterreader.databinding.FragmentHistoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private val adapter = ReadingAdapter()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
        }

        viewModel.allReadings.observe(viewLifecycleOwner) { readings ->
            adapter.submitList(readings)
            binding.textEmptyState.visibility =
                if (readings.isNullOrEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnExportCsv.setOnClickListener { exportCsv() }
    }

    private fun exportCsv() {
        CoroutineScope(Dispatchers.IO).launch {
            val readings = viewModel.getAllReadingsSync()
            if (readings.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "No readings to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val csv = buildString {
                appendLine("id,timestamp,datetime,raw_value,unit,is_valid,ocr_text")
                readings.forEach { r ->
                    val dt = dateFormat.format(Date(r.timestamp))
                    val ocr = r.rawOcrText?.replace(",", ";")?.replace("\n", " ") ?: ""
                    appendLine("${r.id},${r.timestamp},\"$dt\",${r.rawValue ?: ""},${r.unit},${r.isValid},\"$ocr\"")
                }
            }

            val file = File(requireContext().cacheDir, "meter_readings_${System.currentTimeMillis()}.csv")
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Export CSV"))
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
