package com.mimochat.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.mimochat.data.MiMoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TTSService(private val context: Context) {
    companion object {
        private const val TAG = "TTSService"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isSpeakingFlag = false
    private var onCompletionListener: (() -> Unit)? = null

    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    suspend fun speak(text: String, mimoClient: MiMoClient, voice: String? = null, style: String? = null) {
        stop()
        try {
            val audioData = mimoClient.synthesizeSpeech(
                text = text,
                voice = voice ?: "mimo_default",
                style = style
            )
            val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { it.write(audioData) }
            }
            playAudioFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
            isSpeakingFlag = false
            onCompletionListener?.invoke()
        }
    }

    fun speakFromFile(audioFile: String) {
        stop()
        playAudioFile(File(audioFile))
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    start()
                    isSpeakingFlag = true
                }
                setOnCompletionListener {
                    isSpeakingFlag = false
                    release()
                    mediaPlayer = null
                    file.delete()
                    onCompletionListener?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    isSpeakingFlag = false
                    release()
                    mediaPlayer = null
                    file.delete()
                    onCompletionListener?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            isSpeakingFlag = false
            file.delete()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop() }
        } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        isSpeakingFlag = false
    }

    fun isSpeaking(): Boolean = isSpeakingFlag
}
