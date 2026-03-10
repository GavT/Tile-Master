package com.example.tilemaster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sin

class SoundManager {

    private val executor: ExecutorService = Executors.newFixedThreadPool(2)

    // Pre-generate short sounds to avoid repeated allocation
    private val clickSamples: ShortArray by lazy { generateClick() }
    private val correctSamples: ShortArray by lazy { generateCorrectPing() }
    private val wrongSamples: ShortArray by lazy { generateWrongBuzz() }

    fun playClick() {
        playOnExecutor(clickSamples)
    }

    fun playCorrect() {
        playOnExecutor(correctSamples)
    }

    fun playWrong() {
        playOnExecutor(wrongSamples)
    }

    fun playFanfare() {
        // Fanfare is infrequent; generate on demand
        playOnExecutor(generateFanfare())
    }

    fun release() {
        executor.shutdown()
    }

    private fun playOnExecutor(samples: ShortArray) {
        executor.execute {
            val audioTrack = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (_: Exception) {
                return@execute
            }

            try {
                audioTrack.write(samples, 0, samples.size)
                audioTrack.setNotificationMarkerPosition(samples.size)
                audioTrack.setPlaybackPositionUpdateListener(
                    object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack) {
                            track.release()
                        }
                        override fun onPeriodicNotification(track: AudioTrack) {}
                    }
                )
                audioTrack.play()
            } catch (_: Exception) {
                // Ensure release on any playback failure to prevent AudioTrack leak
                try { audioTrack.release() } catch (_: Exception) {}
            }
        }
    }

    private fun generateClick(): ShortArray {
        val numSamples = SAMPLE_RATE * 30 / 1000 // 30ms
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val envelope = 1.0 - progress
            (Short.MAX_VALUE * 0.5 * envelope * sin(TWO_PI * 3000.0 * i / SAMPLE_RATE))
                .toInt().toShort()
        }
    }

    private fun generateCorrectPing(): ShortArray {
        val numSamples = SAMPLE_RATE * 200 / 1000 // 200ms
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val freq = 880.0 + 440.0 * progress
            val envelope = (1.0 - progress) * (1.0 - progress)
            (Short.MAX_VALUE * 0.5 * envelope * sin(TWO_PI * freq * i / SAMPLE_RATE))
                .toInt().toShort()
        }
    }

    private fun generateWrongBuzz(): ShortArray {
        val numSamples = SAMPLE_RATE * 250 / 1000 // 250ms
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val freq = 350.0 - 150.0 * progress
            val envelope = 1.0 - progress
            (Short.MAX_VALUE * 0.4 * envelope * sin(TWO_PI * freq * i / SAMPLE_RATE))
                .toInt().toShort()
        }
    }

    private fun generateFanfare(): ShortArray {
        // Celebratory arpeggio: C5 -> E5 -> G5 -> C6 with overlap
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6
        val noteMs = 150
        val totalMs = noteMs * notes.size + 300 // extra sustain on last note
        val numSamples = SAMPLE_RATE * totalMs / 1000
        val samples = ShortArray(numSamples)

        for ((noteIndex, freq) in notes.withIndex()) {
            val noteStart = noteIndex * noteMs * SAMPLE_RATE / 1000
            val noteDuration = if (noteIndex == notes.lastIndex) {
                (noteMs + 300) * SAMPLE_RATE / 1000
            } else {
                noteMs * 2 * SAMPLE_RATE / 1000 // overlap with next note
            }
            for (j in 0 until noteDuration) {
                val sampleIndex = noteStart + j
                if (sampleIndex >= numSamples) break
                val progress = j.toDouble() / noteDuration
                val envelope = if (progress < 0.05) progress / 0.05 else (1.0 - progress)
                val sample = (Short.MAX_VALUE * 0.35 * envelope * sin(TWO_PI * freq * j / SAMPLE_RATE))
                    .toInt()
                samples[sampleIndex] = (samples[sampleIndex] + sample)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return samples
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TWO_PI = 2.0 * Math.PI
    }
}
