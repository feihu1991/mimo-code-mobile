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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class VideoCallService(private val context: Context) {
    companion object {
        private const val TAG = "VideoCallService"
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BASE_RECONNECT_DELAY_MS = 1000L
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false; private var isPlaying = false; private var isMuted = false; private var isSpeakerOn = true
    private var recordingJob: Job? = null; private var reconnectJob: Job? = null; private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callback: VideoCallCallback? = null; private var config: VoiceCallConfig? = null
    private var intentionalDisconnect = false; private var reconnectAttempts = 0
    var state: VoiceCallState = VoiceCallState.Disconnected; private set
    var onVideoFrame: ((ByteArray) -> Unit)? = null

    fun setCallback(callback: VideoCallCallback) { this.callback = callback }

    fun hasPermission(): Boolean = ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startCall(config: VoiceCallConfig) {
        this.config = config; this.intentionalDisconnect = false; this.reconnectAttempts = 0
        connectWebSocket(config)
    }

    private fun connectWebSocket(config: VoiceCallConfig) {
        updateState(VoiceCallState.Connecting)
        val wsUrl = config.baseUrl.replace("https://", "wss://").replace("http://", "ws://") + "/video"
        val request = Request.Builder().url(wsUrl).addHeader("Authorization", "Bearer ${config.apiKey}").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0; updateState(VoiceCallState.Connected)
                webSocket.send(gson.toJson(mapOf("type" to "session.create", "session_id" to config.sessionId, "character_id" to config.character.id, "system_prompt" to config.character.systemPrompt, "mode" to "video")))
                startAudioCapture(); startAudioPlayback()
            }
            override fun onMessage(webSocket: WebSocket, text: String) { handleTextMessage(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { if (isPlaying) audioTrack?.write(bytes.toByteArray(), 0, bytes.size) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null); handleDisconnection() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { handleDisconnection(t.message) }
        })
    }

    private fun handleDisconnection(errorMsg: String? = null) {
        stopAudioCapture(); stopAudioPlayback(); webSocket = null
        if (intentionalDisconnect) { config = null; updateState(VoiceCallState.Disconnected); return }
        val cfg = config
        if (cfg != null && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            val delayMs = BASE_RECONNECT_DELAY_MS * (1L shl (reconnectAttempts - 1)).coerceAtMost(16)
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
            updateState(VoiceCallState.Reconnecting)
            reconnectJob = scope.launch {
                delay(delayMs)
                if (!intentionalDisconnect && config != null) connectWebSocket(cfg)
            }
        } else { config = null; updateState(VoiceCallState.Error(errorMsg ?: "Connection lost")) }
    }

    fun sendVideoFrame(jpegData: ByteArray) {
        webSocket?.send(gson.toJson(mapOf("type" to "video.frame", "data" to Base64.encodeToString(jpegData, Base64.NO_WRAP), "format" to "jpeg")))
    }

    fun stopCall() { intentionalDisconnect = true; reconnectJob?.cancel(); recordingJob?.cancel(); stopAudioCapture(); stopAudioPlayback(); webSocket?.close(1000, "Call ended"); webSocket = null; config = null; updateState(VoiceCallState.Disconnected) }
    fun setMuted(muted: Boolean) { isMuted = muted }
    fun isMuted(): Boolean = isMuted

    private fun startAudioCapture() {
        if (!hasPermission()) return
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord?.startRecording(); isRecording = true
        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize); val stream = ByteArrayOutputStream()
            while (isActive && isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && !isMuted) {
                    stream.write(buffer, 0, read)
                    if (stream.size() >= SAMPLE_RATE * 2 * 100 / 1000) {
                        webSocket?.send(gson.toJson(mapOf("type" to "audio.data", "data" to Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP), "format" to "pcm_s16le", "sample_rate" to SAMPLE_RATE, "channels" to 1)))
                        stream.reset()
                    }
                } else if (read > 0) stream.reset()
            }
        }
    }

    private fun stopAudioCapture() { isRecording = false; recordingJob?.cancel(); try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}; audioRecord = null }

    private fun startAudioPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
        audioTrack?.play(); isPlaying = true
    }

    private fun stopAudioPlayback() { isPlaying = false; try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}; audioTrack = null }

    private fun handleTextMessage(message: String) {
        try {
            val json = gson.fromJson(message, Map::class.java)
            when (json["type"]) {
                "session.created" -> Log.d(TAG, "Session created")
                "error" -> updateState(VoiceCallState.Error(json["message"] as? String ?: "Unknown error"))
                "video.frame" -> (json["data"] as? String)?.let { data -> val bytes = Base64.decode(data, Base64.DEFAULT); scope.launch(Dispatchers.Main) { onVideoFrame?.invoke(bytes) } }
            }
        } catch (e: Exception) { Log.e(TAG, "Parse error", e) }
    }

    private fun updateState(newState: VoiceCallState) { state = newState; scope.launch(Dispatchers.Main) { callback?.onStateChanged(newState) } }
    fun release() { stopCall(); scope.cancel() }
}

interface VideoCallCallback { fun onStateChanged(state: VoiceCallState) }
