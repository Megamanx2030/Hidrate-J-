package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

class SoundAndVibrationManager(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playJob: Job? = null

    fun playGentleBellAndVibrate(
        durationSeconds: Int = 5,
        chimeType: String = "Sino Suave",
        onFinished: (() -> Unit)? = null
    ) {
        stopAlert()
        isPlaying = true

        // 1. Trigger Vibration
        vibrateOnly(durationSeconds)

        // 2. Play Gentle Bell Synthesized Audio based on chimeType
        playJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val sampleRate = 44100
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val startTime = System.currentTimeMillis()
                val endTime = startTime + (durationSeconds * 1000L)

                val numSamplesInRepeat = sampleRate * 2 // 2-second repeat cycle
                val samples = ShortArray(numSamplesInRepeat)

                for (i in 0 until numSamplesInRepeat) {
                    val time = i.toDouble() / sampleRate
                    val sampleValue: Double = when {
                        chimeType.contains("Gota", ignoreCase = true) -> {
                            // Water drop sound effect: frequency sweeps from 500Hz to 1300Hz quickly
                            val subTime = time % 0.8
                            if (subTime < 0.25) {
                                val freq = 500.0 + (subTime / 0.25) * 800.0
                                val decay = exp(-12.0 * subTime)
                                sin(2 * Math.PI * freq * subTime) * decay
                            } else 0.0
                        }
                        chimeType.contains("Harpa", ignoreCase = true) -> {
                            // Harpa Melódica: 3-note ascending arpeggio C5, E5, G5
                            val subTime = time % 1.2
                            when {
                                subTime < 0.3 -> sin(2 * Math.PI * 523.25 * subTime) * exp(-5.0 * subTime)
                                subTime < 0.6 -> sin(2 * Math.PI * 659.25 * (subTime - 0.3)) * exp(-5.0 * (subTime - 0.3))
                                subTime < 1.0 -> sin(2 * Math.PI * 783.99 * (subTime - 0.6)) * exp(-4.0 * (subTime - 0.6))
                                else -> 0.0
                            }
                        }
                        chimeType.contains("Cristal", ignoreCase = true) -> {
                            // Sino de Cristal: High crystal tones G5 & C6
                            val subTime = time % 1.5
                            val freq1 = 783.99
                            val freq2 = 1046.50
                            val decay = exp(-3.0 * subTime)
                            (sin(2 * Math.PI * freq1 * subTime) * 0.4 + sin(2 * Math.PI * freq2 * subTime) * 0.6) * decay
                        }
                        else -> {
                            // Sino Suave (Clássico): Dual chime C5 & E5
                            val freq1 = 523.25
                            val freq2 = 659.25
                            val decay = exp(-2.5 * (time % 1.0))
                            (sin(2 * Math.PI * freq1 * time) * 0.5 + sin(2 * Math.PI * freq2 * time) * 0.5) * decay
                        }
                    }

                    samples[i] = (sampleValue * Short.MAX_VALUE * 0.45).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                while (isPlaying && System.currentTimeMillis() < endTime) {
                    audioTrack?.write(samples, 0, samples.size)
                    delay(50)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopAlert()
                onFinished?.invoke()
            }
        }
    }

    fun vibrateOnly(durationSeconds: Int, onFinished: (() -> Unit)? = null) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 400, 400, 400, 400) // Vibrate 400ms, pause 400ms
            val attrs = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs) // Repeat pattern
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0, attrs)
            }

            // Stop vibration after durationSeconds
            CoroutineScope(Dispatchers.Main).launch {
                delay(durationSeconds * 1000L)
                vibrator.cancel()
                onFinished?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFinished?.invoke()
        }
    }

    fun stopAlert() {
        isPlaying = false
        playJob?.cancel()
        playJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
