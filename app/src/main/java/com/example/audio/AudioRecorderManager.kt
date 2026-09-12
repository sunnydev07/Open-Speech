package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages microphone audio recording using Android's MediaRecorder,
 * providing real-time audio amplitude updates for visual waveform animations
 * and outputting recorded AAC/M4A audio files for Gemini AI speech analysis.
 */
class AudioRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var amplitudeJob: Job? = null

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts recording microphone audio to a local cache file.
     * Starts polling amplitude for live audio wave visualization.
     */
    fun startRecording(): Result<File> {
        // F7: never silently discard an in-progress recording. The caller must
        // stop the active session explicitly before starting a new one.
        if (_isRecording.value) {
            return Result.failure(IllegalStateException("Already recording"))
        }
        return try {
            val audioFile = File(
                context.cacheDir,
                "speech_recording_${System.currentTimeMillis()}.m4a"
            )
            currentAudioFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true

            // Poll amplitude for real-time visualizer
            amplitudeJob = scope.launch {
                while (isActive && _isRecording.value) {
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        // Normalize 0..32767 to 0.0..1.0 with logarithmic dampening
                        val normalized = (maxAmp / 28000f).coerceIn(0.05f, 1f)
                        _amplitude.value = normalized
                    } catch (e: Exception) {
                        _amplitude.value = 0.1f
                    }
                    delay(80)
                }
            }

            Result.success(audioFile)
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to start audio recording", e)
            _isRecording.value = false
            Result.failure(e)
        }
    }

    /**
     * Stops the active recording and returns the recorded File.
     */
    fun stopRecording(): File? {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _amplitude.value = 0f
        _isRecording.value = false

        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.w("AudioRecorderManager", "Error stopping recorder (maybe empty/short): ${e.message}")
                }
                release()
            }
            mediaRecorder = null
            currentAudioFile
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error releasing recorder", e)
            mediaRecorder = null
            null
        }
    }

    fun release() {
        stopRecording()
    }
}
