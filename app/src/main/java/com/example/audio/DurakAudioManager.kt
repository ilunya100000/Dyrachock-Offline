package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*

object DurakAudioManager {
    private var appContext: Context? = null
    private val audioScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var musicVolume = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyMusicVolume()
        }
        
    var sfxVolume = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private var musicPlayer: MediaPlayer? = null
    private var isPlayingMusic = false
    private var isPaused = false
    private var currentMusicType = 0 // 5 for standard, 6 for endgame

    fun initialize(context: Context) {
        appContext = context.applicationContext
        applyMusicVolume()
    }

    private fun applyMusicVolume() {
        try {
            musicPlayer?.setVolume(musicVolume, musicVolume)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun playSFX(type: Int) {
        val context = appContext ?: return
        if (sfxVolume <= 0.01f) return

        val resId = when (type) {
            1 -> com.example.R.raw.playing_cards_shuffle
            2 -> com.example.R.raw.you_play
            3 -> com.example.R.raw.opponent
            4 -> com.example.R.raw.playing_cards_transfer
            else -> return
        }

        audioScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    val sfxPlayer = MediaPlayer.create(context, resId)
                    if (sfxPlayer != null) {
                        sfxPlayer.setVolume(sfxVolume, sfxVolume)
                        sfxPlayer.start()
                        sfxPlayer.setOnCompletionListener { mp ->
                            mp.release()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DurakAudio", "Failed to play SFX $type", e)
            }
        }
    }

    fun startMusic(type: Int) {
        val context = appContext ?: return
        if (isPlayingMusic && currentMusicType == type) {
            applyMusicVolume()
            return
        }
        
        stopMusic()
        currentMusicType = type
        isPlayingMusic = true

        val resId = when (type) {
            5 -> com.example.R.raw.bah_shutka
            6 -> com.example.R.raw.lunnaya_sonata
            else -> return
        }

        try {
            val mp = MediaPlayer.create(context, resId)
            if (mp != null) {
                musicPlayer = mp
                mp.isLooping = true
                mp.setVolume(musicVolume, musicVolume)
                mp.start()
            }
        } catch (e: Exception) {
            Log.e("DurakAudio", "Failed to start music type $type", e)
        }
    }

    fun stopMusic() {
        isPlayingMusic = false
        isPaused = false
        try {
            musicPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        musicPlayer = null
        currentMusicType = 0
    }

    fun pauseMusic() {
        if (isPlayingMusic) {
            try {
                musicPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.pause()
                        isPaused = true
                    }
                }
            } catch (e: Exception) {
                Log.e("DurakAudio", "Failed to pause music", e)
            }
        }
    }

    fun resumeMusic() {
        if (isPlayingMusic && isPaused) {
            try {
                musicPlayer?.let { mp ->
                    mp.start()
                    isPaused = false
                }
            } catch (e: Exception) {
                Log.e("DurakAudio", "Failed to resume music", e)
            }
        }
    }
}
