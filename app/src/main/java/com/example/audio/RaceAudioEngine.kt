package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.example.R
import com.example.model.AudioSettings
import com.example.model.RacePhase
import com.example.model.RaceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Hybrid studio audio & SFX engine:
 *  - Calm semi-electronic ambient menu background music (warm Rhodes/pad 9th chords,
 *    soft sub-bass, sparse echoed electronic bell plucks via AudioTrack).
 *  - Bundled studio race soundtracks (.m4a in res/raw) where one track is randomly selected
 *    per race run (without immediate back-to-back repeats) and played via MediaPlayer.
 *  - Smooth slewed per-car RC electric motor sound modulated by speed & proximity.
 */
class RaceAudioEngine(
    private val scope: CoroutineScope,
    private val context: Context? = null
) {
    enum class AudioMode {
        MENU,
        RACE
    }

    data class RaceTrackTheme(
        val index: Int,
        val name: String,
        @RawRes val rawResId: Int
    )

    private val sampleRate = 32000
    private val chunkSamples = 1024

    private var audioJob: Job? = null
    private val mediaMutex = Mutex()
    private var raceMediaPlayer: MediaPlayer? = null
    private var preparedThemeIndex: Int = -1
    private var preparedRunCounter: Int = -1

    @Volatile private var currentSnapshot: RaceSnapshot? = null
    @Volatile private var localPlayerId: String = ""
    @Volatile private var settings: AudioSettings = AudioSettings()
    @Volatile private var mode: AudioMode = AudioMode.MENU
    @Volatile private var appInForeground: Boolean = true
    @Volatile private var currentRunTheme: RaceTrackTheme = THEMES[0]
    @Volatile private var runChangeCounter: Int = 0
    @Volatile private var lastSnapshotFinished: Boolean = false
    @Volatile private var cueTriggerSeq: Int = 0
    @Volatile private var cueIsGoChime: Boolean = false
    private var lastThemeIndex: Int = -1

    val activeThemeIndex: Int
        get() = currentRunTheme.index

    val activeThemeName: String
        get() = currentRunTheme.name

    /**
     * Warm damped feedback echo line for spacious electronic menu depth without clutter.
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
        val isFinished = snapshot?.phase == RacePhase.FINISHED.name
        val finishChanged = isFinished != lastSnapshotFinished
        currentSnapshot = snapshot
        localPlayerId = playerId
        if (finishChanged) {
            lastSnapshotFinished = isFinished
            syncRaceMediaPlayer()
        }
    }

    /**
     * Triggers a crisp synthesized countdown pip (3, 2, 1) or bright dual-harmonic start chime ("GO!").
     */
    fun playCountdownCue(isGo: Boolean) {
        cueIsGoChime = isGo
        cueTriggerSeq += 1
    }

    fun updateAudioSettings(audioSettings: AudioSettings) {
        settings = audioSettings
        syncEngineLoop()
    }

    fun setAppForeground(inForeground: Boolean) {
        appInForeground = inForeground
        if (!inForeground) {
            audioJob?.cancel()
            audioJob = null
            syncRaceMediaPlayer()
        } else {
            syncEngineLoop()
        }
    }

    /**
     * Switches to calm semi-electronic menu background music when outside a race.
     */
    fun setMenuMode() {
        currentSnapshot = null
        lastSnapshotFinished = false
        mode = AudioMode.MENU
        syncEngineLoop()
    }

    /**
     * Randomly selects one bundled race soundtrack for this run (guaranteeing no immediate
     * repeat across consecutive runs) and starts playback in RACE mode.
     */
    fun startRaceRun(seed: Long = System.nanoTime() xor System.currentTimeMillis()): RaceTrackTheme {
        val theme = selectNextRunArrangement(seed)
        lastSnapshotFinished = currentSnapshot?.phase == RacePhase.FINISHED.name
        mode = AudioMode.RACE
        syncEngineLoop()
        return theme
    }

    /**
     * Pure JVM-safe soundtrack selector (also exercised directly in unit tests).
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
        currentRunTheme = chosen
        runChangeCounter += 1
        return chosen
    }

    fun start(seed: Long = System.nanoTime() xor System.currentTimeMillis()) {
        startRaceRun(seed)
    }

    private fun syncEngineLoop() {
        syncRaceMediaPlayer()
        val cfg = settings
        val wantAudioTrackLoop = appInForeground && when (mode) {
            AudioMode.MENU -> cfg.musicEnabled
            AudioMode.RACE -> cfg.carSfxEnabled
        }
        if (wantAudioTrackLoop) {
            ensureLoopStarted()
        } else {
            audioJob?.cancel()
            audioJob = null
        }
    }

    private fun syncRaceMediaPlayer() {
        val ctx = context ?: return
        scope.launch(Dispatchers.IO) {
            mediaMutex.withLock {
                reconcileMediaPlayerLocked(ctx)
            }
        }
    }

    private fun reconcileMediaPlayerLocked(ctx: Context) {
        val activeMode = mode
        val cfg = settings
        val foreground = appInForeground
        val finished = lastSnapshotFinished || currentSnapshot?.phase == RacePhase.FINISHED.name

        if (activeMode != AudioMode.RACE) {
            releaseMediaPlayerLocked()
            return
        }

        val wantPlaying = foreground && cfg.musicEnabled && !finished
        if (!wantPlaying) {
            val mp = raceMediaPlayer
            if (mp != null) {
                runCatching {
                    if (mp.isPlaying) {
                        mp.pause()
                    }
                    if (finished) {
                        mp.seekTo(0)
                    }
                }
            }
            return
        }

        val targetMusicGain = computeMusicGain(cfg.musicEnabled, cfg.musicVolume)
        val mpVol = targetMusicGain * 0.76f
        val targetTheme = currentRunTheme
        val targetRunId = runChangeCounter
        val needsNewPlayer = raceMediaPlayer == null ||
            preparedThemeIndex != targetTheme.index ||
            preparedRunCounter != targetRunId

        if (needsNewPlayer) {
            releaseMediaPlayerLocked()
            val created = runCatching {
                val afd = runCatching { ctx.resources.openRawResourceFd(targetTheme.rawResId) }.getOrNull()
                if (afd != null) {
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        try {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        } finally {
                            runCatching { afd.close() }
                        }
                        isLooping = true
                        setVolume(mpVol, mpVol)
                        prepare()
                    }
                } else {
                    MediaPlayer.create(ctx, targetTheme.rawResId)?.apply {
                        isLooping = true
                        setVolume(mpVol, mpVol)
                    }
                }
            }.getOrNull()

            if (created != null) {
                // Re-verify state in case mode/settings changed while preparing
                val stillWanted = appInForeground &&
                    mode == AudioMode.RACE &&
                    settings.musicEnabled &&
                    !lastSnapshotFinished &&
                    currentSnapshot?.phase != RacePhase.FINISHED.name &&
                    currentRunTheme.index == targetTheme.index &&
                    runChangeCounter == targetRunId
                if (stillWanted) {
                    runCatching { created.start() }
                        .onSuccess {
                            raceMediaPlayer = created
                            preparedThemeIndex = targetTheme.index
                            preparedRunCounter = targetRunId
                        }
                        .onFailure {
                            runCatching { created.release() }
                        }
                } else {
                    runCatching { created.release() }
                }
            }
        } else {
            val mp = raceMediaPlayer
            if (mp != null) {
                runCatching {
                    mp.setVolume(mpVol, mpVol)
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                }.onFailure {
                    releaseMediaPlayerLocked()
                }
            }
        }
    }

    private fun releaseMediaPlayerLocked() {
        val mp = raceMediaPlayer
        raceMediaPlayer = null
        preparedThemeIndex = -1
        preparedRunCounter = -1
        if (mp != null) {
            runCatching {
                if (mp.isPlaying) {
                    mp.stop()
                }
            }
            runCatching { mp.reset() }
            runCatching { mp.release() }
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
            val echo = WarmEchoDelay((sampleRate * 0.335).toInt())

            // Polyphonic chord oscillator phases (4 chord tones + sub bass)
            val chordPhases = DoubleArray(4)
            val chordDetunePhases = DoubleArray(4)
            val chordFreqs = DoubleArray(4) { 220.0 }
            var subPhase = 0.0
            var subFreq = 65.41

            // Bell voice state
            var bellPhase = 0.0
            var bellOvertonePhase = 0.0
            var bellFreq = 440.0
            var bellEnv = 0.0

            // Warm 2-pole low-pass filter states for chords
            var padLp1 = 0.0
            var padLp2 = 0.0
            var chordStrikeEnv = 0.0

            // Soft menu percussion synthesis state
            var kickPhase = 0.0
            var kickEnv = 0.0
            var kickPitchEnv = 0.0
            var hatEnv = 0.0
            var hatBpState = 0.0
            var noiseSeed = 0x5A17C3

            // Sequencer tracking for menu music
            var stepSamplePos = 0.0
            var stepIndex = -1
            var observedMode = mode

            // One-shot countdown pip & GO! start chime synthesizer state
            var lastCueSeq = cueTriggerSeq
            var cueRemainingSamples = 0
            var cueTotalSamples = 1
            var cueBaseFreq = 523.25
            var cueHarmonicFreq = 1046.5
            var cuePhase1 = 0.0
            var cuePhase2 = 0.0
            var cueDualChord = false

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

                    if (activeMode != observedMode) {
                        observedMode = activeMode
                        stepIndex = -1
                        stepSamplePos = 0.0
                        echo.clear()
                    }

                    val inActiveRace = activeMode == AudioMode.RACE &&
                        snap != null &&
                        snap.phase != RacePhase.FINISHED.name

                    refreshCarVoices(if (inActiveRace) snap else null)

                    val curCueSeq = cueTriggerSeq
                    if (curCueSeq != lastCueSeq) {
                        lastCueSeq = curCueSeq
                        val goChime = cueIsGoChime
                        cueDualChord = goChime
                        cueBaseFreq = if (goChime) 880.0 else 523.25
                        cueHarmonicFreq = if (goChime) 1318.51 else 1046.5
                        cueTotalSamples = if (goChime) (sampleRate * 0.34).toInt() else (sampleRate * 0.12).toInt()
                        cueRemainingSamples = cueTotalSamples
                        cuePhase1 = 0.0
                        cuePhase2 = 0.0
                    }

                    val bpm = 88
                    val baseStepSamples = (sampleRate * 60.0) / (bpm * 4.0)

                    for (i in 0 until chunkSamples) {
                        var mix = 0.0

                        // Keep calm semi-electronic menu music in MENU mode
                        if (activeMode == AudioMode.MENU && cfg.musicEnabled) {
                            val currentStepOdd = (stepIndex and 1) == 1
                            val swingFactor = 0.03
                            val curStepLen = baseStepSamples * (if (currentStepOdd) (1.0 - swingFactor) else (1.0 + swingFactor))

                            if (stepIndex < 0 || stepSamplePos >= curStepLen) {
                                stepSamplePos = if (stepIndex < 0) 0.0 else (stepSamplePos - curStepLen).coerceAtLeast(0.0)
                                stepIndex = (stepIndex + 1) and 0x7FFFFFFF
                                val stepInBar = stepIndex % 16

                                val bar = (stepIndex / 16) % menuChords.size
                                if (stepInBar == 0 || stepInBar == 10) {
                                    val chord = menuChords[bar]
                                    for (v in 0 until 4) {
                                        chordFreqs[v] = midiToHz(chord[v])
                                    }
                                    subFreq = midiToHz(menuBassMidi[bar])
                                    chordStrikeEnv = if (stepInBar == 0) 0.85 else 0.42
                                }
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
                                val bellIdx = menuBellPattern[stepIndex % menuBellPattern.size]
                                if (bellIdx >= 0 && voiceRng.nextFloat() < 0.82f) {
                                    bellFreq = midiToHz(menuBellScale[bellIdx % menuBellScale.size])
                                    bellEnv = 0.55
                                }
                            }
                            stepSamplePos += 1.0

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

                            val cutoff = 0.055 + 0.025 * sin(2.0 * PI * (renderedFrames + i) / (sampleRate * 6.5))
                            padLp1 += (rawChord - padLp1) * cutoff
                            padLp2 += (padLp1 - padLp2) * cutoff

                            subPhase = (subPhase + subFreq / sampleRate) % 1.0
                            val subSample = sin(2.0 * PI * subPhase) * 0.11

                            var melodicDry = 0.0
                            if (bellEnv > 0.0005) {
                                bellPhase = (bellPhase + bellFreq / sampleRate) % 1.0
                                bellOvertonePhase = (bellOvertonePhase + (bellFreq * 2.0) / sampleRate) % 1.0
                                val bellTone = sin(2.0 * PI * bellPhase) * 0.78 +
                                    sin(2.0 * PI * bellOvertonePhase) * 0.22 * bellEnv
                                melodicDry += bellTone * bellEnv * 0.15
                                bellEnv *= 0.99955
                            }

                            val echoedMelodic = echo.process(
                                input = melodicDry,
                                feedback = 0.46,
                                dampCoeff = 0.22,
                                wetMix = 0.42
                            )

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

                            if (hatEnv > 0.001) {
                                drumMix += airyNoise * hatEnv * 0.075
                                hatEnv *= 0.9925
                            }

                            val musicGain = computeMusicGain(cfg.musicEnabled, cfg.musicVolume).toDouble()
                            mix += (padLp2 * 0.34 + subSample + melodicDry + echoedMelodic + drumMix) * musicGain
                        }

                        // Per-car smooth slewed RC electric motor sounds during active races
                        val sfxGain = computeSfxGain(cfg.carSfxEnabled, cfg.sfxVolume).toDouble()
                        if (sfxGain > 0.0001 && inActiveRace) {
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
                                mix += (tri * 0.65 + whine * 0.35) * v.currentGain * sfxGain
                            }
                        }

                        // Crisp 3-2-1 pip and bright GO! start chime
                        if (cueRemainingSamples > 0) {
                            if (sfxGain > 0.0001 && activeMode == AudioMode.RACE) {
                                val elapsed = cueTotalSamples - cueRemainingSamples
                                val progress = elapsed.toDouble() / cueTotalSamples.coerceAtLeast(1)
                                val attack = (elapsed / (sampleRate * 0.006)).coerceIn(0.0, 1.0)
                                val decay = (1.0 - progress).pow(if (cueDualChord) 1.65 else 2.1)
                                cuePhase1 = (cuePhase1 + cueBaseFreq / sampleRate) % 1.0
                                cuePhase2 = (cuePhase2 + cueHarmonicFreq / sampleRate) % 1.0
                                val tone = if (cueDualChord) {
                                    sin(2.0 * PI * cuePhase1) * 0.62 +
                                        sin(2.0 * PI * cuePhase2) * 0.38
                                } else {
                                    sin(2.0 * PI * cuePhase1) * 0.82 +
                                        sin(2.0 * PI * cuePhase2) * 0.18
                                }
                                val chimeAmp = if (cueDualChord) 0.30 else 0.20
                                mix += tone * attack * decay * chimeAmp * sfxGain
                            }
                            cueRemainingSamples--
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
            val revBoost = if (r.isCountdownRevving) 48.0 else 0.0
            val carOffsetHz = i * 9.0
            v.targetFreq = 96.0 + carOffsetHz + speedNorm * 230.0 + airBoost + nitroBoost + revBoost

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
        CoroutineScope(Dispatchers.IO).launch {
            mediaMutex.withLock {
                releaseMediaPlayerLocked()
            }
        }
    }

    companion object {
        fun computeMusicGain(musicEnabled: Boolean, musicVolume: Float): Float =
            if (musicEnabled) musicVolume.coerceIn(0f, 1f) else 0f

        fun computeSfxGain(carSfxEnabled: Boolean, sfxVolume: Float): Float =
            if (carSfxEnabled) sfxVolume.coerceIn(0f, 1f) else 0f

        private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        val THEMES: List<RaceTrackTheme> = listOf(
            RaceTrackTheme(
                index = 0,
                name = "Apex Glide DnB",
                rawResId = R.raw.apex_glide_dnb
            ),
            RaceTrackTheme(
                index = 1,
                name = "Cardboard Circuit Chaos",
                rawResId = R.raw.cardboard_circuit_chaos
            ),
            RaceTrackTheme(
                index = 2,
                name = "MicroRC Circuit Dash",
                rawResId = R.raw.microrc_circuit_dash
            ),
            RaceTrackTheme(
                index = 3,
                name = "Neon Arena Drift",
                rawResId = R.raw.neon_arena_drift
            ),
            RaceTrackTheme(
                index = 4,
                name = "Pocket Pitstop Funk",
                rawResId = R.raw.pocket_pitstop_funk
            ),
            RaceTrackTheme(
                index = 5,
                name = "Volt Buggy Drift",
                rawResId = R.raw.volt_buggy_drift
            )
        )
    }
}
