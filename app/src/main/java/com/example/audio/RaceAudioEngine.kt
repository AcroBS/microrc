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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real-time procedural studio synthesizer & SFX engine:
 *  - Calm semi-electronic ambient menu background music (warm Rhodes/pad 9th chords,
 *    soft sub-bass, sparse echoed electronic bell plucks).
 *  - Realistic, smooth, low-stress race soundtracks that change randomly on every run
 *    (Karplus-Strong physical plucked strings, warm filtered electric piano/polysynth chords,
 *    damped stereo-style delay echo, and relaxed brushed percussion).
 *  - Smooth slewed per-car RC electric motor sound modulated by speed & proximity.
 */
class RaceAudioEngine(
    private val scope: CoroutineScope
) {
    enum class AudioMode {
        MENU,
        RACE
    }

    data class RaceTrackTheme(
        val index: Int,
        val name: String,
        val bpm: Int,
        val swing: Double,
        // 4-bar chord progression in MIDI note numbers (4-note 7th/9th voicings)
        val chordMidi: Array<IntArray>,
        // Bass root MIDI notes per bar
        val bassRootsMidi: IntArray,
        // Melodic scale MIDI notes (pentatonic / Dorian / Lydian color tones)
        val leadScaleMidi: IntArray,
        val padCutoff: Double,
        val bassBrightness: Double,
        val kickPatternMask: Int,
        val rimPatternMask: Int
    )

    class RaceRunArrangement(
        val theme: RaceTrackTheme,
        val bassSteps: Array<IntArray>, // 8 bars x 16 steps (-1 = sustain/rest, >=0 MIDI note)
        val leadSteps: Array<IntArray>  // 8 bars x 16 steps (-1 = rest, >=0 MIDI note)
    )

    private val sampleRate = 32000
    private val chunkSamples = 1024

    private var audioJob: Job? = null
    @Volatile private var currentSnapshot: RaceSnapshot? = null
    @Volatile private var localPlayerId: String = ""
    @Volatile private var settings: AudioSettings = AudioSettings()
    @Volatile private var mode: AudioMode = AudioMode.MENU
    @Volatile private var appInForeground: Boolean = true
    @Volatile private var currentRunArrangement: RaceRunArrangement = buildArrangement(THEMES[0], Random(1L))
    @Volatile private var runChangeCounter: Int = 0
    private var lastThemeIndex: Int = -1

    val activeThemeIndex: Int
        get() = currentRunArrangement.theme.index

    val activeThemeName: String
        get() = currentRunArrangement.theme.name

    /**
     * Karplus-Strong physical waveguide plucked-string voice for realistic bass & guitar plucks.
     */
    private class PluckedStringVoice(maxDelaySamples: Int = 1024) {
        private val buffer = DoubleArray(maxDelaySamples)
        private var length = 200
        private var idx = 0
        private var prevOut = 0.0
        private var damping = 0.994
        private var brightness = 0.45
        private var active = false

        fun pluck(freqHz: Double, sampleRate: Int, velocity: Double, toneBrightness: Double, sustain: Double, rng: Random) {
            val len = (sampleRate / freqHz.coerceIn(36.0, 1400.0)).toInt().coerceIn(24, buffer.size - 1)
            length = len
            idx = 0
            prevOut = 0.0
            brightness = toneBrightness.coerceIn(0.18, 0.72)
            damping = sustain.coerceIn(0.982, 0.9985)
            // Smoothly filtered pick impulse so string attack sounds warm and realistic, never harsh
            var lp = 0.0
            for (i in 0 until len) {
                val window = sin(PI * (i.toDouble() / len))
                val raw = (rng.nextDouble() * 2.0 - 1.0) * velocity * window
                lp += (raw - lp) * brightness
                buffer[i] = lp
            }
            active = true
        }

        fun nextSample(): Double {
            if (!active) return 0.0
            val cur = buffer[idx]
            val nextIdx = if (idx + 1 >= length) 0 else idx + 1
            val nxt = buffer[nextIdx]
            // Low-pass averaging waveguide feedback
            val averaged = (cur * (1.0 - 0.42 * (1.0 - brightness)) + nxt * (0.42 * (1.0 - brightness))) * damping
            prevOut = averaged
            buffer[idx] = averaged
            idx = nextIdx
            if (abs(cur) < 1e-5 && abs(nxt) < 1e-5) {
                // Check if energy has decayed
                active = true
            }
            return cur
        }
    }

    /**
     * Warm damped feedback echo line for spacious electronic depth without clutter.
     */
    private class WarmEchoDelay(delaySamples: Int) {
        private val buf = DoubleArray(delaySamples.coerceAtLeast(64))
        private var pos = 0
        private var lpState = 0.0

        fun process(input: Double, feedback: Double, dampCoeff: Double, wetMix: Double): Double {
            val delayed = buf[pos]
            lpState += (delayed - lpState) * dampCoeff
            buf[pos] = (input + lpState * feedback).coerceIn(-1.2, 1.2)
            pos++
            if (pos >= buf.size) pos = 0
            return lpState * wetMix
        }

        fun clear() {
            buf.fill(0.0)
            lpState = 0.0
        }
    }

    private class CarVoiceState(
        var phaseMain: Double = 0.0,
        var phaseWhine: Double = 0.0,
        var currentFreq: Double = 110.0,
        var currentGain: Double = 0.0,
        var targetFreq: Double = 110.0,
        var targetGain: Double = 0.0
    )

    private val carVoices = Array(4) { CarVoiceState() }

    fun updateSnapshot(snapshot: RaceSnapshot?, playerId: String) {
        currentSnapshot = snapshot
        localPlayerId = playerId
    }

    fun updateAudioSettings(audioSettings: AudioSettings) {
        settings = audioSettings
        syncEngineLoop()
    }

    fun setAppForeground(inForeground: Boolean) {
        appInForeground = inForeground
        if (!inForeground) {
            stop()
        } else {
            syncEngineLoop()
        }
    }

    /**
     * Switches to calm semi-electronic menu background music when outside a race.
     */
    fun setMenuMode() {
        currentSnapshot = null
        mode = AudioMode.MENU
        syncEngineLoop()
    }

    /**
     * Randomizes a brand-new race soundtrack arrangement for this run (guaranteeing no immediate
     * theme repeat across consecutive runs) and starts/continues playback in RACE mode.
     */
    fun startRaceRun(seed: Long = System.nanoTime() xor System.currentTimeMillis()): RaceTrackTheme {
        val theme = selectNextRunArrangement(seed)
        mode = AudioMode.RACE
        syncEngineLoop()
        return theme
    }

    /**
     * Pure JVM-safe arrangement selector (also exercised directly in unit tests).
     */
    @Synchronized
    fun selectNextRunArrangement(seed: Long = System.nanoTime() xor System.currentTimeMillis()): RaceTrackTheme {
        val rng = Random(seed)
        val candidates = if (THEMES.size > 1 && lastThemeIndex in THEMES.indices) {
            THEMES.filter { it.index != lastThemeIndex }
        } else {
            THEMES.toList()
        }
        val chosen = candidates[rng.nextInt(candidates.size)]
        lastThemeIndex = chosen.index
        currentRunArrangement = buildArrangement(chosen, rng)
        runChangeCounter += 1
        return chosen
    }

    fun start(seed: Long = System.nanoTime() xor System.currentTimeMillis()) {
        startRaceRun(seed)
    }

    private fun syncEngineLoop() {
        val cfg = settings
        val wantLoop = appInForeground && (cfg.musicEnabled || (mode == AudioMode.RACE && cfg.carSfxEnabled))
        if (wantLoop) {
            ensureLoopStarted()
        } else {
            stop()
        }
    }

    private fun ensureLoopStarted() {
        if (audioJob?.isActive == true) return

        audioJob = scope.launch(Dispatchers.Default) {
            val minBufBytes = runCatching {
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            }.getOrDefault(-1)
            if (minBufBytes <= 0) return@launch

            val bufferBytes = maxOf(minBufBytes, chunkSamples * 2 * 3)
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
            val voiceRng = Random(System.nanoTime())

            // Plucked string voices (realistic acoustic/electric bass & melodic guitar plucks)
            val bassString = PluckedStringVoice(1024)
            val leadString = PluckedStringVoice(1024)
            val echo = WarmEchoDelay((sampleRate * 0.335).toInt())

            // Polyphonic chord oscillator phases (4 chord tones + sub bass)
            val chordPhases = DoubleArray(4)
            val chordDetunePhases = DoubleArray(4)
            val chordFreqs = DoubleArray(4) { 220.0 }
            var subPhase = 0.0
            var subFreq = 65.41

            // Bell / soft lead voice state
            var bellPhase = 0.0
            var bellOvertonePhase = 0.0
            var bellFreq = 440.0
            var bellEnv = 0.0

            // Warm 2-pole low-pass filter states for chords
            var padLp1 = 0.0
            var padLp2 = 0.0
            var chordStrikeEnv = 0.0

            // Percussion synthesis state
            var kickPhase = 0.0
            var kickEnv = 0.0
            var kickPitchEnv = 0.0
            var rimEnv = 0.0
            var rimPhase = 0.0
            var hatEnv = 0.0
            var hatBpState = 0.0
            var noiseSeed = 0x5A17C3

            // Sequencer tracking
            var stepSamplePos = 0.0
            var stepIndex = -1
            var observedMode = mode
            var observedRunId = runChangeCounter

            // Menu sequencer patterns (calm semi-electronic: Cmaj9 -> Am9 -> Fmaj9 -> G6sus4)
            val menuChords = arrayOf(
                intArrayOf(60, 64, 67, 74), // Cmaj9
                intArrayOf(57, 60, 64, 71), // Am9
                intArrayOf(53, 57, 60, 67), // Fmaj9
                intArrayOf(55, 60, 62, 67)  // G6sus4
            )
            val menuBassMidi = intArrayOf(36, 33, 29, 31)
            val menuBellScale = intArrayOf(72, 74, 76, 79, 81, 84, 86)
            val menuBellPattern = intArrayOf(
                0, -1, -1, 2, -1, -1, 4, -1, -1, -1, 3, -1, -1, 1, -1, -1,
                2, -1, -1, -1, 5, -1, -1, 4, -1, -1, 2, -1, -1, -1, 0, -1
            )

            var streamStartNano = System.nanoTime()
            var renderedFrames = 0L

            try {
                while (isActive && appInForeground) {
                    val cfg = settings
                    val activeMode = mode
                    val snap = currentSnapshot
                    val arr = currentRunArrangement

                    if (activeMode != observedMode || observedRunId != runChangeCounter) {
                        observedMode = activeMode
                        observedRunId = runChangeCounter
                        stepIndex = -1
                        stepSamplePos = 0.0
                        if (activeMode == AudioMode.MENU) {
                            echo.clear()
                        }
                    }

                    val inActiveRace = activeMode == AudioMode.RACE &&
                        snap != null &&
                        snap.phase != RacePhase.FINISHED.name

                    refreshCarVoices(if (inActiveRace) snap else null)

                    val bpm = if (activeMode == AudioMode.MENU) 88 else arr.theme.bpm
                    val baseStepSamples = (sampleRate * 60.0) / (bpm * 4.0)

                    for (i in 0 until chunkSamples) {
                        var mix = 0.0

                        if (cfg.musicEnabled) {
                            // Trigger next 16th-note step when needed
                            val currentStepOdd = (stepIndex and 1) == 1
                            val swingFactor = if (activeMode == AudioMode.MENU) 0.03 else arr.theme.swing
                            val curStepLen = baseStepSamples * (if (currentStepOdd) (1.0 - swingFactor) else (1.0 + swingFactor))

                            if (stepIndex < 0 || stepSamplePos >= curStepLen) {
                                stepSamplePos = if (stepIndex < 0) 0.0 else (stepSamplePos - curStepLen).coerceAtLeast(0.0)
                                stepIndex = (stepIndex + 1) and 0x7FFFFFFF
                                val stepInBar = stepIndex % 16

                                if (activeMode == AudioMode.MENU) {
                                    // Calm semi-electronic menu music triggers
                                    val bar = (stepIndex / 16) % menuChords.size
                                    if (stepInBar == 0 || stepInBar == 10) {
                                        val chord = menuChords[bar]
                                        for (v in 0 until 4) {
                                            chordFreqs[v] = midiToHz(chord[v])
                                        }
                                        subFreq = midiToHz(menuBassMidi[bar])
                                        chordStrikeEnv = if (stepInBar == 0) 0.85 else 0.42
                                    }
                                    // Soft sub heartbeat on step 0 and gentle pulse on step 10
                                    if (stepInBar == 0) {
                                        kickEnv = 0.34
                                        kickPitchEnv = 0.35
                                    } else if (stepInBar == 10) {
                                        kickEnv = 0.18
                                        kickPitchEnv = 0.22
                                    }
                                    if (stepInBar == 4 || stepInBar == 12) {
                                        hatEnv = 0.14
                                    }
                                    // Sparse crystalline electronic bell plucks
                                    val bellIdx = menuBellPattern[stepIndex % menuBellPattern.size]
                                    if (bellIdx >= 0 && voiceRng.nextFloat() < 0.82f) {
                                        bellFreq = midiToHz(menuBellScale[bellIdx % menuBellScale.size])
                                        bellEnv = 0.55
                                    }
                                } else if (snap == null || snap.phase != RacePhase.FINISHED.name) {
                                    // Realistic, smooth race soundtrack triggers (unique per run)
                                    val theme = arr.theme
                                    val bar4 = (stepIndex / 16) % 4
                                    val bar8 = (stepIndex / 16) % 8

                                    // Warm electric-piano / synth chord comping
                                    if (stepInBar == 0 || stepInBar == 6 || stepInBar == 12) {
                                        val chord = theme.chordMidi[bar4]
                                        for (v in 0 until 4) {
                                            chordFreqs[v] = midiToHz(chord[v])
                                        }
                                        subFreq = midiToHz(theme.bassRootsMidi[bar4])
                                        chordStrikeEnv = when (stepInBar) {
                                            0 -> 0.92
                                            6 -> 0.58
                                            else -> 0.45
                                        }
                                    }

                                    // Plucked acoustic/electric bassline
                                    val bassMidi = arr.bassSteps[bar8][stepInBar]
                                    if (bassMidi > 0) {
                                        bassString.pluck(
                                            freqHz = midiToHz(bassMidi),
                                            sampleRate = sampleRate,
                                            velocity = if (stepInBar % 4 == 0) 0.72 else 0.54,
                                            toneBrightness = theme.bassBrightness,
                                            sustain = 0.9955,
                                            rng = voiceRng
                                        )
                                    }

                                    // Melodic plucked guitar / bell synth voice
                                    val leadMidi = arr.leadSteps[bar8][stepInBar]
                                    if (leadMidi > 0) {
                                        leadString.pluck(
                                            freqHz = midiToHz(leadMidi),
                                            sampleRate = sampleRate,
                                            velocity = 0.48,
                                            toneBrightness = 0.52,
                                            sustain = 0.9965,
                                            rng = voiceRng
                                        )
                                        bellFreq = midiToHz(leadMidi)
                                        bellEnv = 0.34
                                    }

                                    // Smooth, relaxed drum groove
                                    val bit = 1 shl stepInBar
                                    if ((theme.kickPatternMask and bit) != 0) {
                                        kickEnv = if (stepInBar == 0 || stepInBar == 8) 0.75 else 0.52
                                        kickPitchEnv = 1.0
                                    }
                                    if ((theme.rimPatternMask and bit) != 0) {
                                        rimEnv = 0.62
                                    }
                                    if (stepInBar % 2 == 0) {
                                        hatEnv = if (stepInBar % 4 == 2) 0.34 else 0.18
                                    } else if (stepInBar == 7 || stepInBar == 15) {
                                        hatEnv = 0.12
                                    }
                                }
                            }
                            stepSamplePos += 1.0

                            // Synthesize warm polyphonic chords (Rhodes + analog pad)
                            var rawChord = 0.0
                            for (v in 0 until 4) {
                                val f = chordFreqs[v]
                                chordPhases[v] = (chordPhases[v] + f / sampleRate) % 1.0
                                chordDetunePhases[v] = (chordDetunePhases[v] + (f * 1.0035) / sampleRate) % 1.0
                                val fundamental = sin(2.0 * PI * chordPhases[v])
                                val detunedTri = 1.0 - 4.0 * abs(chordDetunePhases[v] - 0.5)
                                val tine = sin(4.0 * PI * chordPhases[v]) * chordStrikeEnv * 0.24
                                rawChord += (fundamental * 0.64 + detunedTri * 0.28 + tine) * 0.25
                            }
                            chordStrikeEnv *= 0.99945

                            // 2-pole warm low-pass filter on chords
                            val cutoff = if (activeMode == AudioMode.MENU) {
                                0.055 + 0.025 * sin(2.0 * PI * (renderedFrames + i) / (sampleRate * 6.5))
                            } else {
                                arr.theme.padCutoff + chordStrikeEnv * 0.045
                            }
                            padLp1 += (rawChord - padLp1) * cutoff
                            padLp2 += (padLp1 - padLp2) * cutoff

                            // Sub-bass warmth
                            subPhase = (subPhase + subFreq / sampleRate) % 1.0
                            val subSample = sin(2.0 * PI * subPhase) *
                                (if (activeMode == AudioMode.MENU) 0.11 else 0.09)

                            // Crystalline electronic bell / lead shimmer + Karplus-Strong strings
                            var melodicDry = 0.0
                            if (bellEnv > 0.0005) {
                                bellPhase = (bellPhase + bellFreq / sampleRate) % 1.0
                                bellOvertonePhase = (bellOvertonePhase + (bellFreq * 2.0) / sampleRate) % 1.0
                                val bellTone = sin(2.0 * PI * bellPhase) * 0.78 +
                                    sin(2.0 * PI * bellOvertonePhase) * 0.22 * bellEnv
                                melodicDry += bellTone * bellEnv * (if (activeMode == AudioMode.MENU) 0.15 else 0.11)
                                bellEnv *= if (activeMode == AudioMode.MENU) 0.99955 else 0.99925
                            }

                            val bassPluck = if (activeMode == AudioMode.RACE) bassString.nextSample() * 0.30 else 0.0
                            val leadPluck = if (activeMode == AudioMode.RACE) leadString.nextSample() * 0.22 else 0.0
                            melodicDry += leadPluck

                            val echoedMelodic = echo.process(
                                input = melodicDry,
                                feedback = if (activeMode == AudioMode.MENU) 0.46 else 0.34,
                                dampCoeff = 0.22,
                                wetMix = if (activeMode == AudioMode.MENU) 0.42 else 0.28
                            )

                            // Smooth studio percussion
                            var drumMix = 0.0
                            if (kickEnv > 0.001) {
                                val kFreq = 49.0 + 78.0 * kickPitchEnv
                                kickPhase = (kickPhase + kFreq / sampleRate) % 1.0
                                drumMix += sin(2.0 * PI * kickPhase) * kickEnv * 0.23
                                kickEnv *= 0.9968
                                kickPitchEnv *= 0.991
                            }

                            noiseSeed = noiseSeed * 1664525 + 1013904223
                            val white = ((noiseSeed ushr 16) and 0xFFFF) / 32768.0 - 1.0
                            hatBpState += (white - hatBpState) * 0.36
                            val airyNoise = white - hatBpState

                            if (rimEnv > 0.001) {
                                rimPhase = (rimPhase + 188.0 / sampleRate) % 1.0
                                val woodBody = sin(2.0 * PI * rimPhase) * 0.55
                                val brushSnap = airyNoise * 0.45
                                drumMix += (woodBody + brushSnap) * rimEnv * 0.14
                                rimEnv *= 0.9952
                            }
                            if (hatEnv > 0.001) {
                                drumMix += airyNoise * hatEnv * 0.075
                                hatEnv *= 0.9925
                            }

                            val padGain = if (activeMode == AudioMode.MENU) 0.34 else 0.26
                            mix += padLp2 * padGain + subSample + bassPluck + melodicDry + echoedMelodic + drumMix
                        }

                        // Per-car smooth slewed RC electric motor sounds during active races
                        if (cfg.carSfxEnabled && inActiveRace) {
                            val racersCount = snap?.racers?.size?.coerceAtMost(4) ?: 0
                            for (c in 0 until racersCount) {
                                val v = carVoices[c]
                                v.currentFreq += (v.targetFreq - v.currentFreq) * 0.0035
                                v.currentGain += (v.targetGain - v.currentGain) * 0.0035
                                if (v.currentGain <= 0.0015) continue
                                v.phaseMain = (v.phaseMain + v.currentFreq / sampleRate) % 1.0
                                v.phaseWhine = (v.phaseWhine + (v.currentFreq * 2.01) / sampleRate) % 1.0
                                val tri = 1.0 - 4.0 * abs(v.phaseMain - 0.5)
                                val whine = sin(2.0 * PI * v.phaseWhine)
                                mix += (tri * 0.65 + whine * 0.35) * v.currentGain
                            }
                        }

                        // Gentle studio soft-saturation limiter so peaks never clip harshly
                        val softLimited = tanh(mix * 0.95) * 0.86
                        pcm[i] = (softLimited * 30000.0).toInt().coerceIn(-32760, 32760).toShort()
                    }

                    renderedFrames += chunkSamples
                    val written = track.write(pcm, 0, pcm.size)
                    if (written < 0) break

                    // On real Android hardware AudioTrack.write() blocks naturally at DAC rate;
                    // under JVM/Robolectric ShadowAudioTrack returns immediately, so pace safely.
                    val expectedElapsedMs = (renderedFrames * 1000L) / sampleRate
                    val actualElapsedMs = (System.nanoTime() - streamStartNano) / 1_000_000L
                    val aheadMs = expectedElapsedMs - actualElapsedMs
                    if (aheadMs > 120L) {
                        delay((aheadMs - 80L).coerceAtMost(250L))
                    } else if (actualElapsedMs - expectedElapsedMs > 1000L) {
                        streamStartNano = System.nanoTime() - expectedElapsedMs * 1_000_000L
                    }
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
            val airBoost = if (r.jumpZ > 0.05f) 42.0 else 0.0
            val nitroBoost = if (r.boostMs > 0L) 58.0 else 0.0
            val carOffsetHz = i * 9.0
            v.targetFreq = 96.0 + carOffsetHz + speedNorm * 230.0 + airBoost + nitroBoost

            val isLocal = r.playerId == localPlayerId
            val dist = if (localRacer != null) {
                hypot((r.x - localRacer.x).toDouble(), (r.y - localRacer.y).toDouble())
            } else {
                0.0
            }
            val proxFactor = if (isLocal) {
                1.0
            } else {
                (1.0 - (dist / 1200.0)).coerceIn(0.16, 0.64)
            }
            val idleBase = if (snap.phase == RacePhase.COUNTDOWN.name) 0.020 else 0.038
            val speedFactor = idleBase + speedNorm * 0.082
            v.targetGain = (speedFactor * proxFactor * 0.65).coerceIn(0.0, 0.095)
        }
    }

    fun stop() {
        audioJob?.cancel()
        audioJob = null
    }

    companion object {
        private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        val THEMES: List<RaceTrackTheme> = listOf(
            // 0: Sunset Coast Groove — Warm Rhodes & acoustic funk bass (Fmaj7 - Em7 - Dm9 - Cmaj7)
            RaceTrackTheme(
                index = 0,
                name = "Sunset Coast Groove",
                bpm = 106,
                swing = 0.07,
                chordMidi = arrayOf(
                    intArrayOf(53, 57, 60, 64), // Fmaj7
                    intArrayOf(52, 55, 59, 62), // Em7
                    intArrayOf(50, 53, 57, 64), // Dm9
                    intArrayOf(48, 52, 55, 59)  // Cmaj7
                ),
                bassRootsMidi = intArrayOf(41, 40, 38, 36),
                leadScaleMidi = intArrayOf(64, 67, 69, 72, 74, 76, 79),
                padCutoff = 0.11,
                bassBrightness = 0.44,
                kickPatternMask = 0b0000_0101_0010_0001, // steps 0, 5, 8, 10
                rimPatternMask = 0b0001_0000_0001_0000   // steps 4, 12
            ),
            // 1: Midnight Neon Cruise — Smooth synthwave groove (Am9 - Fmaj7 - Cmaj7 - G6)
            RaceTrackTheme(
                index = 1,
                name = "Midnight Neon Cruise",
                bpm = 112,
                swing = 0.03,
                chordMidi = arrayOf(
                    intArrayOf(57, 60, 64, 71), // Am9
                    intArrayOf(53, 57, 60, 64), // Fmaj7
                    intArrayOf(48, 52, 55, 59), // Cmaj7
                    intArrayOf(55, 59, 62, 64)  // G6
                ),
                bassRootsMidi = intArrayOf(33, 41, 36, 43),
                leadScaleMidi = intArrayOf(64, 67, 69, 71, 72, 76, 79),
                padCutoff = 0.13,
                bassBrightness = 0.50,
                kickPatternMask = 0b0001_0001_0001_0001, // relaxed four-on-the-floor pulse
                rimPatternMask = 0b0001_0000_0001_0000
            ),
            // 2: Paddock Lounge Funk — Syncopated warm clav/Rhodes & walking bass (Dm9 - G13 - Cmaj9 - A7sus4)
            RaceTrackTheme(
                index = 2,
                name = "Paddock Lounge Funk",
                bpm = 108,
                swing = 0.08,
                chordMidi = arrayOf(
                    intArrayOf(50, 53, 57, 64), // Dm9
                    intArrayOf(55, 59, 62, 64), // G13 color
                    intArrayOf(48, 52, 55, 62), // Cmaj9
                    intArrayOf(57, 62, 64, 67)  // A7sus4
                ),
                bassRootsMidi = intArrayOf(38, 43, 36, 33),
                leadScaleMidi = intArrayOf(62, 65, 67, 69, 72, 74, 76),
                padCutoff = 0.12,
                bassBrightness = 0.48,
                kickPatternMask = 0b0000_1001_0100_0001, // steps 0, 6, 8, 11
                rimPatternMask = 0b0001_0000_0001_0000
            ),
            // 3: Horizon Electro-Jazz — Chill liquid groove (Em9 - Cmaj7 - Am9 - B7sus4)
            RaceTrackTheme(
                index = 3,
                name = "Horizon Electro-Jazz",
                bpm = 104,
                swing = 0.06,
                chordMidi = arrayOf(
                    intArrayOf(52, 55, 59, 66), // Em9
                    intArrayOf(48, 52, 55, 59), // Cmaj7
                    intArrayOf(57, 60, 64, 71), // Am9
                    intArrayOf(59, 64, 66, 69)  // B7sus4
                ),
                bassRootsMidi = intArrayOf(40, 36, 33, 35),
                leadScaleMidi = intArrayOf(64, 66, 67, 71, 74, 76, 78),
                padCutoff = 0.10,
                bassBrightness = 0.40,
                kickPatternMask = 0b0010_0000_0100_1001, // airy broken-beat groove
                rimPatternMask = 0b0001_0000_0001_0000
            ),
            // 4: Velvet Drift Circuit — Warm melodic cruiser (Gm9 - Ebmaj7 - Bbmaj7 - F6)
            RaceTrackTheme(
                index = 4,
                name = "Velvet Drift Circuit",
                bpm = 114,
                swing = 0.04,
                chordMidi = arrayOf(
                    intArrayOf(55, 58, 62, 69), // Gm9
                    intArrayOf(51, 55, 58, 62), // Ebmaj7
                    intArrayOf(46, 50, 53, 57), // Bbmaj7
                    intArrayOf(53, 57, 60, 62)  // F6
                ),
                bassRootsMidi = intArrayOf(43, 39, 34, 41),
                leadScaleMidi = intArrayOf(62, 65, 67, 70, 72, 74, 77),
                padCutoff = 0.125,
                bassBrightness = 0.46,
                kickPatternMask = 0b0000_0101_0000_1001, // steps 0, 3, 8, 10
                rimPatternMask = 0b0001_0000_0001_0000
            )
        )

        private fun buildArrangement(theme: RaceTrackTheme, rng: Random): RaceRunArrangement {
            val bassSteps = Array(8) { bar ->
                val root = theme.bassRootsMidi[bar % 4]
                val fifth = root + 7
                val octave = root + 12
                IntArray(16) { step ->
                    when {
                        step == 0 -> root
                        step == 6 && rng.nextFloat() < 0.75f -> fifth
                        step == 8 -> if (rng.nextFloat() < 0.65f) root else octave
                        step == 10 && rng.nextFloat() < 0.55f -> octave
                        step == 14 && rng.nextFloat() < 0.65f -> fifth
                        step == 3 && rng.nextFloat() < 0.35f -> root
                        else -> -1
                    }
                }
            }

            // Build tasteful, breathing melodic phrases (plenty of rests so it's never stressful)
            var motifDeg = rng.nextInt(theme.leadScaleMidi.size)
            val leadSteps = Array(8) { bar ->
                IntArray(16) { step ->
                    val onMusicalSlot = step == 0 || step == 3 || step == 6 || step == 8 || step == 10 || step == 12 || step == 14
                    val density = if (bar % 2 == 0) 0.44f else 0.36f
                    if (onMusicalSlot && rng.nextFloat() < density) {
                        val delta = rng.nextInt(-2, 3)
                        motifDeg = (motifDeg + delta).coerceIn(0, theme.leadScaleMidi.size - 1)
                        theme.leadScaleMidi[motifDeg]
                    } else {
                        -1
                    }
                }
            }

            return RaceRunArrangement(
                theme = theme,
                bassSteps = bassSteps,
                leadSteps = leadSteps
            )
        }
    }
}
