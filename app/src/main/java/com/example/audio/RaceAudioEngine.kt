package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AudioSettings
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real-time procedural PCM audio engine:
 *  - Random hard rock guitar riffs + driving rock drum groove
 *  - Lower-volume per-car RC electric motor sound modulated by speed & proximity
 */
class RaceAudioEngine(
    private val scope: CoroutineScope
) {
    private val sampleRate = 22050
    private val chunkSamples = 512

    private var audioJob: Job? = null
    @Volatile private var currentSnapshot: RaceSnapshot? = null
    @Volatile private var localPlayerId: String = ""
    @Volatile private var settings: AudioSettings = AudioSettings()

    private class CarVoiceState(
        var phaseMain: Double = 0.0,
        var phaseWhine: Double = 0.0,
        var targetFreq: Double = 115.0,
        var targetGain: Double = 0.0
    )

    private val carVoices = Array(4) { CarVoiceState() }

    fun updateSnapshot(snapshot: RaceSnapshot?, playerId: String) {
        currentSnapshot = snapshot
        localPlayerId = playerId
    }

    fun updateAudioSettings(audioSettings: AudioSettings) {
        settings = audioSettings
    }

    fun start(seed: Long = System.currentTimeMillis()) {
        if (audioJob?.isActive == true) return
        val rng = Random(seed)

        // Build randomized hard rock guitar riff pattern (E/D/G/A minor pentatonic power chords)
        val scaleFreqs = doubleArrayOf(82.41, 92.50, 98.00, 110.00, 123.47, 130.81, 146.83, 164.81)
        val riffPool = Array(4) { bar ->
            IntArray(16) { step ->
                when {
                    step % 4 == 0 -> if (bar == 3 && step >= 12) rng.nextInt(3, 7) else 0
                    step % 2 == 0 -> rng.nextInt(scaleFreqs.size)
                    rng.nextFloat() < 0.58f -> rng.nextInt(scaleFreqs.size)
                    else -> -1 // Palm-muted chug rest
                }
            }
        }

        audioJob = scope.launch(Dispatchers.Default) {
            val minBufBytes = runCatching {
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            }.getOrDefault(-1)
            if (minBufBytes <= 0) return@launch

            val bufferBytes = maxOf(minBufBytes, chunkSamples * 2 * 2)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            runCatching { track.play() }.onFailure {
                runCatching { track.release() }
                return@launch
            }

            val pcm = ShortArray(chunkSamples)
            var guitarPhaseRoot = 0.0
            var guitarPhaseFifth = 0.0
            var guitarPhaseOct = 0.0
            var kickPhase = 0.0
            var noiseState = 1337
            var totalSamples = 0L

            val stepDurationSamples = (sampleRate * 0.108).toInt() // ~138 BPM 16th notes
            var activeBar = 0

            try {
                while (isActive) {
                    val cfg = settings
                    val snap = currentSnapshot
                    val racing = snap != null && snap.phase != RacePhase.FINISHED.name

                    // Refresh per-car RC motor target frequencies & gains
                    refreshCarVoices(snap)

                    for (i in 0 until chunkSamples) {
                        var mix = 0.0
                        val sampleIdx = totalSamples + i

                        // 1. Hard Rock Guitar Riff + Drums BGM
                        if (cfg.musicEnabled && racing) {
                            val stepIndexTotal = (sampleIdx / stepDurationSamples).toInt()
                            val stepInBar = stepIndexTotal % 16
                            if (stepInBar == 0 && (sampleIdx % stepDurationSamples) == 0L) {
                                activeBar = ((stepIndexTotal / 16) + rng.nextInt(0, 2)) % riffPool.size
                            }
                            val stepSample = (sampleIdx % stepDurationSamples).toDouble()
                            val stepProgress = stepSample / stepDurationSamples

                            val noteIdx = riffPool[activeBar][stepInBar]
                            val rootFreq = if (noteIdx >= 0) scaleFreqs[noteIdx] else scaleFreqs[0]
                            val env = if (noteIdx >= 0) {
                                exp(-stepProgress * 3.1)
                            } else {
                                exp(-stepProgress * 12.0) * 0.45 // Tight palm mute chug
                            }

                            guitarPhaseRoot = (guitarPhaseRoot + rootFreq / sampleRate) % 1.0
                            guitarPhaseFifth = (guitarPhaseFifth + (rootFreq * 1.498) / sampleRate) % 1.0
                            guitarPhaseOct = (guitarPhaseOct + (rootFreq * 2.0) / sampleRate) % 1.0

                            // Saw-rich string excitation fed into hard guitar overdrive distortion
                            val rawString =
                                (guitarPhaseRoot * 2.0 - 1.0) * 0.55 +
                                    (guitarPhaseFifth * 2.0 - 1.0) * 0.35 +
                                    sin(2.0 * PI * guitarPhaseOct) * 0.25
                            val driven = tanh(rawString * 4.4 * env)
                            // Bitey cab filter approximation
                            val guitarSample = driven * 0.26

                            // Punchy rock kick (steps 0, 6, 8, 10) & snare (steps 4, 12) + hihat
                            var drumSample = 0.0
                            if (stepInBar == 0 || stepInBar == 6 || stepInBar == 8 || stepInBar == 10) {
                                val kEnv = exp(-stepProgress * 9.5)
                                val kFreq = 52.0 + 95.0 * exp(-stepProgress * 18.0)
                                kickPhase = (kickPhase + kFreq / sampleRate) % 1.0
                                drumSample += sin(2.0 * PI * kickPhase) * kEnv * 0.22
                            }
                            noiseState = noiseState * 1664525 + 1013904223
                            val white = ((noiseState ushr 16) and 0xFFFF) / 32768.0 - 1.0
                            if (stepInBar == 4 || stepInBar == 12) {
                                val sEnv = exp(-stepProgress * 10.0)
                                drumSample += (white * 0.7 + sin(2.0 * PI * stepProgress * 190.0) * 0.3) * sEnv * 0.16
                            } else if (stepInBar % 2 == 0) {
                                drumSample += white * exp(-stepProgress * 22.0) * 0.04
                            }

                            mix += guitarSample + drumSample
                        }

                        // 2. Per-car lower-volume RC electric motor sounds
                        if (cfg.carSfxEnabled && racing) {
                            val racersCount = snap?.racers?.size?.coerceAtMost(4) ?: 0
                            for (c in 0 until racersCount) {
                                val v = carVoices[c]
                                if (v.targetGain <= 0.002) continue
                                v.phaseMain = (v.phaseMain + v.targetFreq / sampleRate) % 1.0
                                v.phaseWhine = (v.phaseWhine + (v.targetFreq * 2.02) / sampleRate) % 1.0
                                // Brushed/brushless RC gear hum: subtle triangle + high whine overtone
                                val tri = 1.0 - 4.0 * abs(v.phaseMain - 0.5)
                                val whine = sin(2.0 * PI * v.phaseWhine)
                                mix += (tri * 0.62 + whine * 0.38) * v.targetGain
                            }
                        }

                        val clamped = (mix.coerceIn(-0.92, 0.92) * 28000.0).toInt().toShort()
                        pcm[i] = clamped
                    }

                    totalSamples += chunkSamples
                    val written = track.write(pcm, 0, pcm.size)
                    if (written < 0) break
                }
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    private fun refreshCarVoices(snap: RaceSnapshot?) {
        if (snap == null || snap.racers.isEmpty()) {
            carVoices.forEach { it.targetGain = 0.0 }
            return
        }
        val localRacer = snap.racers.find { it.playerId == localPlayerId } ?: snap.racers.firstOrNull()
        val count = snap.racers.size.coerceAtMost(4)
        for (i in 0 until 4) {
            val v = carVoices[i]
            if (i >= count) {
                v.targetGain = 0.0
                continue
            }
            val r = snap.racers[i]
            val speedNorm = (r.speed / 580.0).coerceIn(0.0, 1.35)
            val airBoost = if (r.jumpZ > 0.05f) 45.0 else 0.0
            val nitroBoost = if (r.boostMs > 0L) 65.0 else 0.0
            // Each car gets slight harmonic offset so multi-car packs sound distinct
            val carOffsetHz = i * 9.5
            v.targetFreq = 98.0 + carOffsetHz + speedNorm * 245.0 + airBoost + nitroBoost

            val isLocal = r.playerId == localPlayerId
            val dist = if (localRacer != null) {
                hypot((r.x - localRacer.x).toDouble(), (r.y - localRacer.y).toDouble())
            } else {
                0.0
            }
            val proxFactor = if (isLocal) {
                1.0
            } else {
                (1.0 - (dist / 1200.0)).coerceIn(0.18, 0.68)
            }
            val idleBase = if (snap.phase == RacePhase.COUNTDOWN.name) 0.025 else 0.045
            val speedFactor = idleBase + speedNorm * 0.095
            // Intentionally much quieter (~0.09 max vs ~0.35 BGM) per request
            v.targetGain = (speedFactor * proxFactor * 0.68).coerceIn(0.0, 0.11)
        }
    }

    fun stop() {
        audioJob?.cancel()
        audioJob = null
    }
}
