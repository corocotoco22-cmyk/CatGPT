package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.model.RelaxSoundPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class FelineAudioEngine {

    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var currentVolume: Float = 0.7f

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(currentVolume)
        } catch (_: Exception) {
        }
    }

    fun startRelaxSound(
        scope: CoroutineScope,
        preset: RelaxSoundPreset,
        volume: Float
    ) {
        stopRelaxSound()
        currentVolume = volume.coerceIn(0f, 1f)

        playbackJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val minBufBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.setVolume(currentVolume)
            track.play()

            val chunkSamples = 1024
            val buffer = ShortArray(chunkSamples)
            var sampleIndex = 0L
            var pinkB0 = 0.0
            var pinkB1 = 0.0
            var pinkB2 = 0.0
            val harpScale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.00, 1046.50)

            try {
                while (isActive) {
                    for (i in 0 until chunkSamples) {
                        val t = sampleIndex / sampleRate.toDouble()
                        val sampleDouble = when (preset) {
                            RelaxSoundPreset.PURR_THERAPY -> {
                                // Realistic 26 Hz + 52 Hz feline purr with ~0.35 Hz breathing cycle
                                val breathEnv = 0.45 + 0.55 * (0.5 + 0.5 * sin(2.0 * PI * 0.32 * t))
                                val fundamental = sin(2.0 * PI * 26.0 * t)
                                val harmonic2 = 0.65 * sin(2.0 * PI * 52.0 * t)
                                val harmonic3 = 0.30 * sin(2.0 * PI * 78.0 * t)
                                val warmth = 0.15 * sin(2.0 * PI * 130.0 * t)
                                (fundamental + harmonic2 + harmonic3 + warmth) * 0.40 * breathEnv
                            }

                            RelaxSoundPreset.FELINE_HARP -> {
                                val notePeriod = 1.6
                                val noteIdx = ((t / notePeriod).toInt()) % harpScale.size
                                val noteLocalT = t % notePeriod
                                val freq = harpScale[noteIdx]
                                val pluckEnv = kotlin.math.exp(-noteLocalT * 2.2)
                                val pad = 0.18 * sin(2.0 * PI * 261.63 * t) + 0.14 * sin(2.0 * PI * 392.0 * t)
                                val chime = (sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * freq * 2.0 * t)) * 0.35 * pluckEnv
                                pad + chime
                            }

                            RelaxSoundPreset.WINDOW_RAIN -> {
                                val white = Random.nextDouble() * 2.0 - 1.0
                                pinkB0 = 0.997 * pinkB0 + white * 0.029
                                pinkB1 = 0.985 * pinkB1 + white * 0.032
                                pinkB2 = 0.950 * pinkB2 + white * 0.048
                                val swish = 0.75 + 0.25 * sin(2.0 * PI * 0.15 * t)
                                ((pinkB0 + pinkB1 + pinkB2) * 0.55) * swish
                            }

                            RelaxSoundPreset.SOLAR_NAP -> {
                                val swell = 0.6 + 0.4 * sin(2.0 * PI * 0.12 * t)
                                val chord = sin(2.0 * PI * 220.0 * t) * 0.28 +
                                    sin(2.0 * PI * 277.18 * t) * 0.20 +
                                    sin(2.0 * PI * 329.63 * t) * 0.20 +
                                    sin(2.0 * PI * 440.0 * t) * 0.10
                                chord * swell
                            }
                        }
                        buffer[i] = (sampleDouble.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                        sampleIndex++
                    }
                    track.write(buffer, 0, chunkSamples)
                }
            } finally {
                try {
                    track.pause()
                    track.flush()
                    track.release()
                } catch (_: Exception) {
                }
                if (audioTrack === track) {
                    audioTrack = null
                }
            }
        }
    }

    fun stopRelaxSound() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    @SuppressLint("MissingPermission")
    suspend fun recordMeowWav(
        durationMs: Long = 4500L,
        onAmplitude: (Float) -> Unit
    ): ByteArray {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(2048)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            minBufSize * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microfone indisponível para gravação.")
        }

        val pcmStream = ByteArrayOutputStream()
        val shortBuf = ShortArray(minBufSize / 2)
        val startTime = System.currentTimeMillis()

        try {
            recorder.startRecording()
            while (System.currentTimeMillis() - startTime < durationMs) {
                val read = recorder.read(shortBuf, 0, shortBuf.size)
                if (read > 0) {
                    var sumSquares = 0.0
                    val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        val s = shortBuf[i]
                        byteBuf.putShort(s)
                        sumSquares += (s * s).toDouble()
                    }
                    pcmStream.write(byteBuf.array())
                    val rms = sqrt(sumSquares / read) / Short.MAX_VALUE.toDouble()
                    onAmplitude((rms * 4.5).toFloat().coerceIn(0.05f, 1f))
                }
                delay(40L)
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            recorder.release()
            onAmplitude(0f)
        }

        val pcmBytes = pcmStream.toByteArray()
        return wrapWavHeader(pcmBytes, sampleRate, 1, 16)
    }

    private fun wrapWavHeader(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // AudioFormat PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(pcmData.size)
        }.array()
        return header + pcmData
    }
}
