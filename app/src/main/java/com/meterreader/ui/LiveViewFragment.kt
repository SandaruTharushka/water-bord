package com.meterreader.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.meterreader.R
import com.meterreader.databinding.FragmentLiveViewBinding
import com.meterreader.service.ACTION_MANUAL_CAPTURE
import com.meterreader.service.CAPTURE_INTERVAL_MS
import com.meterreader.service.MeterReaderService
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class LiveViewFragment : Fragment() {

    private var _binding: FragmentLiveViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var countDownTimer: CountDownTimer? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnManualCapture.setOnClickListener {
            triggerManualCapture()
            resetCountdown()
        }

        observeLatestReading()
        startCountdown()
        startCameraPreview()
    }

    private fun observeLatestReading() {
        viewModel.latestReading.observe(viewLifecycleOwner) { reading ->
            if (reading == null) {
                binding.textLastValue.text = getString(R.string.no_readings_yet)
                binding.textLastTimestamp.text = ""
                setConfidenceColor(R.color.confidence_green)
                return@observe
            }

            binding.textLastTimestamp.text = dateFormat.format(Date(reading.timestamp))
            binding.textLastValue.text = if (reading.isValid && reading.rawValue != null)
                "${"%.2f".format(reading.rawValue)} ${reading.unit}"
            else
                getString(R.string.ocr_failed)

            val previous = viewModel.previousReading.value
            val colorRes = when {
                !reading.isValid -> R.color.confidence_red
                previous?.rawValue != null && reading.rawValue != null -> {
                    val pct = abs(reading.rawValue - previous.rawValue) / previous.rawValue * 100
                    if (pct > 5) R.color.confidence_yellow else R.color.confidence_green
                }
                else -> R.color.confidence_green
            }
            setConfidenceColor(colorRes)
        }
    }

    private fun setConfidenceColor(colorRes: Int) {
        binding.viewConfidence.setBackgroundColor(
            ContextCompat.getColor(requireContext(), colorRes)
        )
    }

    private fun startCameraPreview() {
        val ctx = requireContext()
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                // Preview not critical — swallow silently
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun triggerManualCapture() {
        val ctx = requireContext()
        val intent = Intent(ctx, MeterReaderService::class.java).apply {
            action = ACTION_MANUAL_CAPTURE
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(CAPTURE_INTERVAL_MS, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60_000
                val seconds = (millisUntilFinished % 60_000) / 1000
                binding.textNextCapture.text =
                    getString(R.string.next_capture_in, minutes, seconds)
            }

            override fun onFinish() {
                binding.textNextCapture.text = getString(R.string.capturing_now)
                startCountdown()
            }
        }.start()
    }

    private fun resetCountdown() = startCountdown()

    override fun onDestroyView() {
        countDownTimer?.cancel()
        cameraProvider?.unbindAll()
        _binding = null
        super.onDestroyView()
    }
}
