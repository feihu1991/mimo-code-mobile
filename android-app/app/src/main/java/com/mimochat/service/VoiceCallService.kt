package com.mimochat.service
import com.mimochat.data.*

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class VoiceCallConfig(
    val sessionId: String,
    val character: Character,
    val baseUrl: String,
    val apiKey: String
)

sealed class VoiceCallState {
    object Disconnected : VoiceCallState()
    object Connecting : VoiceCallState()
    object Connected : VoiceCallState()
    data class Error(val message: String) : VoiceCallState()
}

interface VoiceCallCallback {
    fun onStateChanged(state: VoiceCallState)
    fun onAudioLevelChanged(level: Float)
}

class VoiceCallService(private val context: Context) {

    companion object {
        private const val TAG = "VoiceCallService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SEND_INTERVAL_MS = 100L
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    private var isSpeakerOn = true

    private var recordingJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var callback: VoiceCallCallback? = null
    private var config: VoiceCallConfig? = null

    var state: VoiceCallState = VoiceCallState.Disconnected
        private set

    fun setCallback(callback: VoiceCallCallback) {
        this.callback = callback
    }

    fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCall(config: VoiceCallConfig) {
        this.config = config
        updateState(VoiceCallState.Connecting)

        val wsUrl = config.baseUrl.replace("https://", "wss://")
            .replace("http://", "ws://") + "/voice"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                updateState(VoiceCallState.Connected)

                val sessionMsg = gson.toJson(mapOf(
                    "type" to "session.create",
                    "session_id" to config.sessionId,
                    "character_id" to config.character.id,
                    "system_prompt" to config.character.systemPrompt
                ))
                webSocket.send(sessionMsg)

                startAudioCapture()
                startAudioPlayback()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text: $text")
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleAudioData(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                stopCall()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                updateState(VoiceCallState.Error(t.message ?: "Connection failed"))
                stopCall()
            }
        })
    }

    fun stopCall() {
        recordingJob?.cancel()
        recordingJob = null

        stopAudioCapture()
        stopAudioPlayback()

        webSocket?.close(1000, "Call ended")
        webSocket = null

        config = null
        updateState(VoiceCallState.Disconnected)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun setSpeaker(enabled: Boolean) {
        isSpeakerOn = enabled
        audioTrack?.let { track ->
            val streamType = if (enabled) {
                android.media.AudioManager.STREAM_MUSIC
            } else {
                android.media.AudioManager.STREAM_VOICE_CALL
            }
            // Note: AudioTrack stream type cannot be changed after creation
            // This is handled in startAudioPlayback
        }
    }

    fun isSpeakerEnabled(): Boolean = isSpeakerOn

    private fun startAudioCapture() {
        if (!hasPermission()) {
            Log.e(TAG, "No audio recording permission")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val audioStream = ByteArrayOutputStream()

            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0 && !isMuted) {
                    audioStream.write(buffer, 0, bytesRead)

                    if (audioStream.size() >= SAMPLE_RATE * 2 * AUDIO_SEND_INTERVAL_MS / 1000) {
                        val audioData = audioStream.toByteArray()
                        audioStream.reset()

                        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
                        val message = gson.toJson(mapOf(
                            "type" to "audio.data",
                            "data" to base64Audio,
                            "format" to "pcm_s16le",
                            "sample_rate" to SAMPLE_RATE,
                            "channels" to 1
                        ))
                        webSocket?.send(message)

                        val level = calculateAudioLevel(audioData)
                        withContext(Dispatchers.Main) {
                            callback?.onAudioLevelChanged(level)
                        }
                    }
                } else if (bytesRead > 0) {
                    audioStream.reset()
                }
            }
        }
    }

    private fun stopAudioCapture() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        }
        audioRecord = null
    }

    private fun startAudioPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)

        val streamType = if (isSpeakerOn) {
            android.media.AudioManager.STREAM_MUSIC
        } else {
            android.media.AudioManager.STREAM_VOICE_CALL
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (isSpeakerOn) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
    }

    private fun stopAudioPlayback() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        }
        audioTrack = null
    }

    private fun handleAudioData(audioData: ByteArray) {
        if (isPlaying) {
            audioTrack?.write(audioData, 0, audioData.size)
        }
    }

    private fun handleTextMessage(message: String) {
        try {
            val json = gson.fromJson(message, Map::class.java)
            when (json["type"]) {
                "session.created" -> {
                    Log.d(TAG, "Session created: ${json["session_id"]}")
                }
                "error" -> {
                    val errorMsg = json["message"] as? String ?: "Unknown error"
                    Log.e(TAG, "Server error: $errorMsg")
                    updateState(VoiceCallState.Error(errorMsg))
                }
                "audio.level" -> {
                    val level = (json["level"] as? Number)?.toFloat() ?: 0f
                    scope.launch(Dispatchers.Main) {
                        callback?.onAudioLevelChanged(level)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $message", e)
        }
    }

    private fun calculateAudioLevel(audioData: ByteArray): Float {
        var sum = 0L
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = (audioData[i].toInt() and 0xFF) or (audioData[i + 1].toInt() shl 8)
                sum += kotlin.math.abs(sample.toLong())
            }
        }
        val average = sum / (audioData.size / 2)
        return (average / 32768.0f).coerceIn(0f, 1f)
    }

    private fun updateState(newState: VoiceCallState) {
        state = newState
        scope.launch(Dispatchers.Main) {
            callback?.onStateChanged(newState)
        }
    }

    fun release() {
        stopCall()
        scope.cancel()
    }
}
