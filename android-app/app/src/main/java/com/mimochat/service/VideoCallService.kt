package com.mimochat.service

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
import com.mimochat.data.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VideoCallService(private val context: Context) {
    companion object {
        private const val TAG = "VideoCallService"
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_THRESHOLD = 500
        private const val SILENCE_DURATION_MS = 1500L
        private const val MIN_SPEECH_DURATION_MS = 500L
    }

    private var mimoClient: MiMoClient? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isMuted = false
    private var recordingJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var callback: VideoCallCallback? = null
    private var config: VoiceCallConfig? = null
    private var conversationHistory = mutableListOf<ChatMessage>()

    var state: VoiceCallState = VoiceCallState.Disconnected
        private set
    var onVideoFrame: ((ByteArray) -> Unit)? = null

    fun setCallback(callback: VideoCallCallback) { this.callback = callback }

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startCall(config: VoiceCallConfig) {
        this.config = config
        this.conversationHistory.clear()
        mimoClient = MiMoClient(MiMoConfig(config.baseUrl, config.apiKey))
        updateState(VoiceCallState.Connecting)

        scope.launch {
            delay(500)
            updateState(VoiceCallState.Connected)
            delay(300)
            startListening()
        }
    }

    fun stopCall() {
        recordingJob?.cancel()
        stopAudioCapture()
        config = null
        mimoClient = null
        conversationHistory.clear()
        updateState(VoiceCallState.Disconnected)
    }

    fun setMuted(muted: Boolean) { isMuted = muted }
    fun isMuted(): Boolean = isMuted

    // ---- 发送视频帧给 Vision 模型 ----

    fun sendVideoFrame(jpegData: ByteArray) {
        val client = mimoClient ?: return
        val cfg = config ?: return

        scope.launch {
            try {
                val base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP)
                val response = client.describeImage(
                    imageBase64 = base64,
                    mimeType = "image/jpeg",
                    prompt = "简要描述摄像头画面中的内容，一句话概括"
                )
                Log.d(TAG, "Vision frame: $response")
            } catch (e: Exception) {
                Log.e(TAG, "Vision frame error", e)
            }
        }
    }

    // ---- 语音对话流水线 (同 VoiceCallService) ----

    private fun startListening() {
        if (state is VoiceCallState.Disconnected || state is VoiceCallState.Error) return
        updateState(VoiceCallState.Listening)
        startAudioCapture()
    }

    private fun startAudioCapture() {
        if (!hasPermission()) return
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord?.startRecording()
        isRecording = true

        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val speechBuffer = ByteArrayOutputStream()
            var lastSpeechTime = System.currentTimeMillis()
            var speechStartTime = 0L

            while (isActive && isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && !isMuted) {
                    val level = calculateAudioLevel(buffer, read)
                    if (level * 32768 > SILENCE_THRESHOLD) {
                        if (speechStartTime == 0L) speechStartTime = System.currentTimeMillis()
                        lastSpeechTime = System.currentTimeMillis()
                        speechBuffer.write(buffer, 0, read)
                    } else if (speechStartTime > 0) {
                        speechBuffer.write(buffer, 0, read)
                        if (System.currentTimeMillis() - lastSpeechTime >= SILENCE_DURATION_MS
                            && lastSpeechTime - speechStartTime >= MIN_SPEECH_DURATION_MS) {
                            val audioData = speechBuffer.toByteArray()
                            speechBuffer.reset()
                            speechStartTime = 0L
                            stopAudioCapture()
                            processAudio(audioData)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun stopAudioCapture() {
        isRecording = false
        recordingJob?.cancel()
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private suspend fun processAudio(audioData: ByteArray) {
        val cfg = config ?: return
        val client = mimoClient ?: return
        updateState(VoiceCallState.Processing)

        try {
            val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            val userText = client.transcribe(audioBase64 = audioBase64, mimeType = "audio/m4a", language = "zh")
            if (userText.isBlank()) { startListening(); return }

            conversationHistory.add(ChatMessage(role = "user", content = userText))
            val response = client.sendMessage(
                sessionId = cfg.sessionId,
                history = conversationHistory.toList(),
                userText = userText,
                systemPrompt = cfg.character.systemPrompt
            )
            val assistantText = response.choices?.firstOrNull()?.message?.content?.toString() ?: "抱歉，我没有理解"
            conversationHistory.add(ChatMessage(role = "assistant", content = assistantText))

            updateState(VoiceCallState.Speaking)
            val ttsAudio = client.synthesizeSpeech(text = assistantText)
            playAudioAndResume(ttsAudio)

        } catch (e: Exception) {
            Log.e(TAG, "Voice pipeline error", e)
            delay(1000)
            startListening()
        }
    }

    private suspend fun playAudioAndResume(audioData: ByteArray) {
        val tempFile = File(context.cacheDir, "video_tts_${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) { FileOutputStream(tempFile).use { it.write(audioData) } }

        try {
            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { it.release(); tempFile.delete(); scope.launch { delay(300); startListening() } }
                setOnErrorListener { mp, _, _ -> mp.release(); tempFile.delete(); scope.launch { delay(300); startListening() }; true }
                prepare()
                start()
            }
        } catch (e: Exception) { tempFile.delete(); startListening() }
    }

    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0L
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                sum += kotlin.math.abs(sample.toLong())
            }
        }
        return ((sum / (length / 2)) / 32768.0f).coerceIn(0f, 1f)
    }

    private fun updateState(newState: VoiceCallState) {
        state = newState
        scope.launch(Dispatchers.Main) { callback?.onStateChanged(newState) }
    }

    fun release() { stopCall(); scope.cancel() }
}

interface VideoCallCallback { fun onStateChanged(state: VoiceCallState) }
