package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Native Android Audio Recorder Helper using MediaRecorder.
 * Captures real on-device ambient audio evidence during emergency incidents.
 * Fully native, free, and operates on-device without any payment or external subscriptions.
 */
class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording: Boolean = false
        private set

    /**
     * Starts native microphone audio recording.
     * Saves audio stream to application cache in AAC / MPEG-4 format.
     */
    fun startRecording(): File? {
        if (isRecording) return currentOutputFile

        return try {
            val audioDir = File(context.cacheDir, "evidence_audio").apply { if (!exists()) mkdirs() }
            val outputFile = File(audioDir, "audio_evidence_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

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
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            Log.d(TAG, "Native audio recording started: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start native audio recording", e)
            cleanup()
            null
        }
    }

    /**
     * Stops audio recording and returns the recorded audio file.
     */
    fun stopRecording(): File? {
        if (!isRecording) return null

        return try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d(TAG, "Audio recording finished: ${currentOutputFile?.absolutePath}")
            currentOutputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recorder", e)
            cleanup()
            null
        }
    }

    /**
     * Cleans up resources in case of cancellation or errors.
     */
    fun cleanup() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
    }

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }
}
