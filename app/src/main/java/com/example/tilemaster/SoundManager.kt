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
    private val suckHomeSamples: ShortArray by lazy { generateSuckHome() }
    private val wrongSamples: ShortArray by lazy { generateWrongBuzz() }
    private val explosionSamples: ShortArray by lazy { generateExplosion() }
    private val thunkCompleteSamples: ShortArray by lazy { generateThunkComplete() }
    private val countdownBeepSamples: ShortArray by lazy { generateCountdownBeep() }
    private val timeUpSamples: ShortArray by lazy { generateTimeUp() }

    fun playClick() {
        playOnExecutor(clickSamples)
    }

    fun playSuckHome() {
        playOnExecutor(suckHomeSamples)
    }

    fun playWrong() {
        playOnExecutor(wrongSamples)
    }

    fun playExplosion() {
        playOnExecutor(explosionSamples)
    }

    fun playThunkComplete() {
        playOnExecutor(thunkCompleteSamples)
    }

    fun playFanfare() {
        // Fanfare is infrequent; generate on demand
        playOnExecutor(generateFanfare())
    }

    fun playCountdownBeep() {
        playOnExecutor(countdownBeepSamples)
    }

    fun playTimeUp() {
        playOnExecutor(timeUpSamples)
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

    // Satisfying short click — two-tone percussive pop, ~40ms
    private fun generateClick(): ShortArray {
        val numSamples = SAMPLE_RATE * 40 / 1000
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val envelope = (1.0 - progress) * (1.0 - progress)
            val tone = sin(TWO_PI * 1800.0 * i / SAMPLE_RATE) * 0.6 +
                    sin(TWO_PI * 3200.0 * i / SAMPLE_RATE) * 0.4
            (Short.MAX_VALUE * 0.3 * envelope * tone).toInt().toShort()
        }
    }

    // Short suck sound for homing a tile — quick descending sweep, ~80ms
    private fun generateSuckHome(): ShortArray {
        val numSamples = SAMPLE_RATE * 80 / 1000
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val freq = 2000.0 - 1600.0 * progress // sweep down from 2000 to 400 Hz
            val envelope = if (progress < 0.1) progress / 0.1 else (1.0 - progress)
            (Short.MAX_VALUE * 0.35 * envelope * sin(TWO_PI * freq * i / SAMPLE_RATE))
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

    // Small explosion — white noise burst with low-freq rumble, ~200ms
    private fun generateExplosion(): ShortArray {
        val numSamples = SAMPLE_RATE * 200 / 1000
        val rng = java.util.Random(42)
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val envelope = (1.0 - progress) * (1.0 - progress)
            val noise = (rng.nextDouble() * 2.0 - 1.0) * 0.5
            val rumble = sin(TWO_PI * 80.0 * i / SAMPLE_RATE) * 0.5
            (Short.MAX_VALUE * 0.35 * envelope * (noise + rumble)).toInt().toShort()
        }
    }

    // Dull thunk for completing all tiles of one color — low impact, ~120ms
    private fun generateThunkComplete(): ShortArray {
        val numSamples = SAMPLE_RATE * 120 / 1000
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val envelope = (1.0 - progress) * (1.0 - progress) * (1.0 - progress)
            val tone = sin(TWO_PI * 120.0 * i / SAMPLE_RATE) * 0.7 +
                    sin(TWO_PI * 80.0 * i / SAMPLE_RATE) * 0.3
            (Short.MAX_VALUE * 0.5 * envelope * tone).toInt().toShort()
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

    private fun generateCountdownBeep(): ShortArray {
        val numSamples = SAMPLE_RATE * 80 / 1000 // 80ms sharp beep
        return ShortArray(numSamples) { i ->
            val progress = i.toDouble() / numSamples
            val envelope = if (progress < 0.05) progress / 0.05 else (1.0 - progress)
            (Short.MAX_VALUE * 0.45 * envelope * sin(TWO_PI * 1200.0 * i / SAMPLE_RATE))
                .toInt().toShort()
        }
    }

    private fun generateTimeUp(): ShortArray {
        // Descending trombone "wah wah wah wahhh"
        val notes = doubleArrayOf(392.0, 369.99, 349.23, 261.63) // G4, F#4, F4, C4
        val durations = intArrayOf(200, 200, 200, 500)
        val totalMs = durations.sum()
        val numSamples = SAMPLE_RATE * totalMs / 1000
        val samples = ShortArray(numSamples)

        var offset = 0
        for ((noteIndex, freq) in notes.withIndex()) {
            val noteStart = offset
            val noteSamples = SAMPLE_RATE * durations[noteIndex] / 1000
            for (j in 0 until noteSamples) {
                val sampleIndex = noteStart + j
                if (sampleIndex >= numSamples) break
                val progress = j.toDouble() / noteSamples
                val envelope = if (progress < 0.05) progress / 0.05
                else if (noteIndex == notes.lastIndex) (1.0 - progress) * (1.0 - progress)
                else 1.0 - progress * 0.3
                val sample = (Short.MAX_VALUE * 0.4 * envelope * sin(TWO_PI * freq * j / SAMPLE_RATE))
                    .toInt()
                samples[sampleIndex] = (samples[sampleIndex] + sample)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            offset += noteSamples
        }
        return samples
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TWO_PI = 2.0 * Math.PI
    }
}
