package com.example.desktop.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

/**
 * Desktop audio manager. Plays the same MP3 assets the Android build uses
 * (bundled in `src/main/resources/sfx/` and `/music/`). MP3 decoding is provided
 * by the mp3spi service-provider registered with `javax.sound.sampled.AudioSystem`,
 * so the standard `AudioInputStream` + `SourceDataLine` pipeline transparently
 * accepts `.mp3` input.
 *
 * SFX play once on a fire-and-forget background thread; music streams in a loop
 * on its own coroutine and supports pause/resume/volume.
 */
object DurakAudioManager {
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var musicVolume: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume(musicLine, field)
        }
    var sfxVolume: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    @Volatile private var musicLine: SourceDataLine? = null
    @Volatile private var musicJob: Job? = null
    @Volatile private var isPlayingMusic = false
    @Volatile private var isPaused = false
    @Volatile private var currentMusicType = 0

    fun initialize() { /* no-op on desktop */ }

    // ---- SFX -------------------------------------------------------------
    fun playSFX(type: Int) {
        if (sfxVolume <= 0.01f) return
        val path = when (type) {
            1 -> "/sfx/playing_cards_shuffle.mp3"
            2 -> "/sfx/you_play.mp3"
            3 -> "/sfx/opponent.mp3"
            4 -> "/sfx/playing_cards_transfer.mp3"
            else -> return
        }
        audioScope.launch { streamResource(path, loop = false, volumeProvider = { sfxVolume }) }
    }

    // ---- Music -----------------------------------------------------------
    fun startMusic(type: Int) {
        if (isPlayingMusic && currentMusicType == type) {
            applyVolume(musicLine, musicVolume)
            return
        }
        stopMusic()
        val path = when (type) {
            5 -> "/music/bah_shutka.mp3"
            6 -> "/music/lunnaya_sonata.mp3"
            else -> return
        }
        currentMusicType = type
        isPlayingMusic = true
        isPaused = false
        musicJob = audioScope.launch {
            try {
                while (isActive && isPlayingMusic) {
                    streamResource(path, loop = true, volumeProvider = { musicVolume }, registerMusicLine = true)
                    if (!isPlayingMusic) break
                }
            } catch (_: Exception) { }
        }
    }

    fun stopMusic() {
        isPlayingMusic = false
        isPaused = false
        currentMusicType = 0
        try { musicLine?.stop(); musicLine?.flush(); musicLine?.close() } catch (_: Exception) {}
        musicLine = null
        musicJob?.cancel(); musicJob = null
    }

    fun pauseMusic() {
        if (isPlayingMusic && !isPaused) {
            isPaused = true
            try { musicLine?.stop() } catch (_: Exception) {}
        }
    }

    fun resumeMusic() {
        if (isPlayingMusic && isPaused) {
            isPaused = false
            try { musicLine?.start() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------------
    private fun resourceStream(path: String): InputStream? {
        val raw = DurakAudioManager::class.java.getResourceAsStream(path) ?: return null
        // AudioSystem requires mark-supporting streams to sniff the format.
        return BufferedInputStream(raw)
    }

    /**
     * Decodes an MP3 (or WAV) resource to PCM and writes to a SourceDataLine
     * until the stream ends or `isPlayingMusic` flips to false.
     */
    private fun streamResource(
        path: String,
        loop: Boolean,
        volumeProvider: () -> Float,
        registerMusicLine: Boolean = false
    ) {
        val stream = resourceStream(path) ?: return
        var inAudio: AudioInputStream? = null
        var decoded: AudioInputStream? = null
        var line: SourceDataLine? = null
        try {
            inAudio = AudioSystem.getAudioInputStream(stream)
            val baseFmt = inAudio.format
            // Convert to signed 16-bit PCM that any SourceDataLine can speak.
            val pcmFmt = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFmt.sampleRate,
                16,
                baseFmt.channels,
                baseFmt.channels * 2,
                baseFmt.sampleRate,
                false
            )
            decoded = AudioSystem.getAudioInputStream(pcmFmt, inAudio)

            val info = DataLine.Info(SourceDataLine::class.java, pcmFmt)
            line = AudioSystem.getLine(info) as SourceDataLine
            line.open(pcmFmt)
            applyVolume(line, volumeProvider())
            line.start()
            if (registerMusicLine) musicLine = line

            val buf = ByteArray(8 * 1024)
            while (true) {
                if (registerMusicLine && !isPlayingMusic) break
                if (registerMusicLine && isPaused) { Thread.sleep(80); continue }
                val n = decoded.read(buf, 0, buf.size)
                if (n <= 0) break
                line.write(buf, 0, n)
                // Re-apply volume each cycle so live slider changes take effect.
                applyVolume(line, volumeProvider())
            }
            if (!loop || (registerMusicLine && !isPlayingMusic)) {
                line.drain()
            }
        } catch (_: Exception) {
            // Resource missing or codec issue — fail silently so gameplay continues.
        } finally {
            if (registerMusicLine && musicLine === line) musicLine = null
            try { line?.stop(); line?.close() } catch (_: Exception) {}
            try { decoded?.close() } catch (_: Exception) {}
            try { inAudio?.close() } catch (_: Exception) {}
            try { stream.close() } catch (_: Exception) {}
        }
    }

    /**
     * Volume control: prefer MASTER_GAIN (dB), fall back to VOLUME (linear).
     * Linear 0..1 → dB via 20*log10(v) (clipped at -60 dB / silence).
     */
    private fun applyVolume(line: SourceDataLine?, linear: Float) {
        val l = line ?: return
        val v = linear.coerceIn(0f, 1f)
        try {
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gain = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val db = if (v <= 0.0001f) gain.minimum else (20.0 * Math.log10(v.toDouble())).toFloat()
                gain.value = db.coerceIn(gain.minimum, gain.maximum)
                return
            }
            if (l.isControlSupported(FloatControl.Type.VOLUME)) {
                val vol = l.getControl(FloatControl.Type.VOLUME) as FloatControl
                vol.value = (vol.minimum + (vol.maximum - vol.minimum) * v)
                    .coerceIn(vol.minimum, vol.maximum)
            }
        } catch (_: Exception) { }
    }
}
