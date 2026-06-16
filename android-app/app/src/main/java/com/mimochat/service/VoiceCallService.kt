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
import com.google.gson.Gson
import com.mimochat.data.Character
import com.mimochat.data.ChatMessage
import com.mimochat.data.MiMoClient
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    object Listening : VoiceCallState()      // 正在听用户说话
    object Processing : VoiceCallState()     // 正在处理 (ASR→Chat→TTS)
    object Speaking : VoiceCallState()       // 正在播放 AI 回复
    object Reconnecting : VoiceCallState()
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
        // 静音检测：连续 N 毫秒静音则认为说完一句话
        private const val SILENCE_THRESHOLD = 500       // 音量阈值
        private const val SILENCE_DURATION_MS = 1500L   // 静音持续时间
        private const val MIN_SPEECH_DURATION_MS = 500L // 最短语音时长
    }

    private val gson = Gson()
    private var mimoClient: MiMoClient? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    private var isSpeakerOn = true

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var callback: VoiceCallCallback? = null
    private var config: VoiceCallConfig? = null
    private var conversationHistory = mutableListOf<ChatMessage>()

    var state: VoiceCallState = VoiceCallState.Disconnected
        private set

    fun setCallback(callback: VoiceCallCallback) {
        this.callback = callback
    }

    fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCall(config: VoiceCallConfig) {
        this.config = config
        this.conversationHistory.clear()
        mimoClient = MiMoClient(com.mimochat.data.MiMoConfig(config.baseUrl, config.apiKey))
        updateState(VoiceCallState.Connecting)

        // 模拟连接建立，直接进入 Connected 状态
        scope.launch {
            delay(500)
            updateState(VoiceCallState.Connected)
            delay(300)
            startListening()
        }
    }

    fun stopCall() {
        recordingJob?.cancel()
        playbackJob?.cancel()
        stopAudioCapture()
        stopAudioPlayback()
        config = null
        mimoClient = null
        conversationHistory.clear()
        updateState(VoiceCallState.Disconnected)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun setSpeaker(enabled: Boolean) {
        if (isSpeakerOn == enabled) return
        isSpeakerOn = enabled
        if (isPlaying) {
            stopAudioPlayback()
            // Playback will restart on next TTS
        }
    }

    fun isSpeakerEnabled(): Boolean = isSpeakerOn

    // ---- 核心语音对话流水线 ----

    private fun startListening() {
        if (state is VoiceCallState.Disconnected || state is VoiceCallState.Error) return
        updateState(VoiceCallState.Listening)
        startAudioCapture()
    }

    private fun startAudioCapture() {
        if (!hasPermission()) {
            Log.e(TAG, "No audio recording permission")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            val speechBuffer = ByteArrayOutputStream()
            var lastSpeechTime = System.currentTimeMillis()
            var speechStartTime = 0L

            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0 && !isMuted) {
                    val level = calculateAudioLevel(buffer, bytesRead)
                    withContext(Dispatchers.Main) {
                        callback?.onAudioLevelChanged(level)
                    }

                    if (level * 32768 > SILENCE_THRESHOLD) {
                        // 有声音
                        if (speechStartTime == 0L) {
                            speechStartTime = System.currentTimeMillis()
                        }
                        lastSpeechTime = System.currentTimeMillis()
                        speechBuffer.write(buffer, 0, bytesRead)
                    } else if (speechStartTime > 0) {
                        // 静音中，但之前有声音
                        speechBuffer.write(buffer, 0, bytesRead)
                        val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                        val speechDuration = lastSpeechTime - speechStartTime

                        if (silenceDuration >= SILENCE_DURATION_MS && speechDuration >= MIN_SPEECH_DURATION_MS) {
                            // 一句话说完了
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
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        }
        audioRecord = null
    }

    private suspend fun processAudio(audioData: ByteArray) {
        val cfg = config ?: return
        val client = mimoClient ?: return

        updateState(VoiceCallState.Processing)

        try {
            // Step 1: ASR - 语音转文字
            val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            val userText = client.transcribe(audioBase64 = audioBase64, mimeType = "audio/m4a", language = "zh")

            if (userText.isBlank()) {
                Log.d(TAG, "ASR returned empty, resuming listen")
                startListening()
                return
            }

            Log.d(TAG, "ASR result: $userText")

            // Step 2: Chat - 发送文字获取回复
            conversationHistory.add(ChatMessage(role = "user", content = userText))
            val response = client.sendMessage(
                sessionId = cfg.sessionId,
                history = conversationHistory.toList(),
                userText = userText,
                systemPrompt = cfg.character.systemPrompt
            )
            val assistantText = response.choices?.firstOrNull()?.message?.content?.toString() ?: "抱歉，我没有理解"
            conversationHistory.add(ChatMessage(role = "assistant", content = assistantText))

            Log.d(TAG, "Chat response: ${assistantText.take(100)}...")

            // Step 3: TTS - 文字转语音并播放
            updateState(VoiceCallState.Speaking)
            val audioBytes = client.synthesizeSpeech(text = assistantText)
            playAudio(audioBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Voice pipeline error", e)
            // 出错后继续监听
            delay(1000)
            startListening()
        }
    }

    private suspend fun playAudio(audioData: ByteArray) {
        val tempFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.mp3")
        withContext(Dispatchers.IO) {
            FileOutputStream(tempFile).use { it.write(audioData) }
        }

        try {
            stopAudioPlayback()
            val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
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

            // 读取并播放 (这里简化处理，实际应该解码 mp3)
            // 对于 mp3 格式，使用 MediaPlayer 更合适
            stopAudioPlayback()
            isPlaying = false

            // 使用 MediaPlayer 播放 mp3
            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(if (isSpeakerOn) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    it.release()
                    tempFile.delete()
                    // 播放完后继续监听
                    scope.launch {
                        delay(300)
                        startListening()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    tempFile.delete()
                    scope.launch {
                        delay(300)
                        startListening()
                    }
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error", e)
            tempFile.delete()
            startListening()
        }
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

    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0L
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                sum += kotlin.math.abs(sample.toLong())
            }
        }
        val average = sum / (length / 2)
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
