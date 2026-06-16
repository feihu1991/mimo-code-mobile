package com.mimochat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ChatActivity : AppCompatActivity() {
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var attachButton: ImageButton
    private lateinit var voiceButton: ImageButton
    private lateinit var ttsButton: ImageButton
    private lateinit var progressBar: View
    private lateinit var recordingIndicator: View
    private lateinit var messageAdapter: MessageAdapter
    private var sessionId: String? = null
    private var sessionTitle: String? = null
    private val messages = mutableListOf<Message>()
    private var mimoClient: MiMoClient? = null
    private lateinit var asrService: ASRService
    private lateinit var ttsService: TTSService
    private var isRecording = false
    private var recordingStartTime = 0L
    private var autoTtsEnabled = false

    companion object { private const val PERMISSION_REQUEST_AUDIO = 1001 }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { handleSelectedImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        sessionId = intent.getStringExtra("SESSION_ID")
        sessionTitle = intent.getStringExtra("SESSION_TITLE")
        asrService = ASRService(this)
        ttsService = TTSService(this)
        initViews(); setupRecyclerView(); setupClickListeners(); initMiMoClient(); loadMessages()
    }

    private fun initViews() {
        messageRecyclerView = findViewById(R.id.messageRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        voiceButton = findViewById(R.id.voiceButton)
        ttsButton = findViewById(R.id.ttsButton)
        progressBar = findViewById(R.id.progressBar)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        title = sessionTitle ?: "新对话"
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages)
        messageRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messageRecyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener { val text = messageInput.text.toString().trim(); if (text.isNotEmpty()) { sendMessage(text); messageInput.text?.clear() } }
        attachButton.setOnClickListener { imagePickerLauncher.launch("image/*") }
        voiceButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopRecordingAndTranscribe(); true }
                else -> false
            }
        }
        ttsButton.setOnClickListener {
            autoTtsEnabled = !autoTtsEnabled
            ttsButton.alpha = if (autoTtsEnabled) 1.0f else 0.4f
            Toast.makeText(this, if (autoTtsEnabled) "语音朗读已开启" else "语音朗读已关闭", Toast.LENGTH_SHORT).show()
            if (!autoTtsEnabled) ttsService.stop()
        }
    }

    private fun initMiMoClient() {
        val config = MiMoConfigManager.getConfig(this)
        if (config != null) mimoClient = MiMoClient(config)
        else { Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_LONG).show(); startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun startRecording() {
        if (!asrService.hasPermission()) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_AUDIO); return }
        asrService.startRecording().onSuccess { isRecording = true; recordingStartTime = System.currentTimeMillis(); recordingIndicator.visibility = View.VISIBLE; voiceButton.alpha = 0.5f; Toast.makeText(this, "正在录音...", Toast.LENGTH_SHORT).show() }
            .onFailure { e -> Toast.makeText(this, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        isRecording = false; voiceButton.alpha = 1.0f
        if (System.currentTimeMillis() - recordingStartTime < 500) { asrService.cancelRecording(); recordingIndicator.visibility = View.GONE; Toast.makeText(this, "录音太短", Toast.LENGTH_SHORT).show(); return }
        asrService.stopRecording().onSuccess { audioFile ->
            val client = mimoClient ?: run { Toast.makeText(this, "请先配置 API", Toast.LENGTH_SHORT).show(); return }
            recordingIndicator.visibility = View.GONE; Toast.makeText(this, "正在识别...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                asrService.transcribe(audioFile, client).onSuccess { text -> messageInput.setText(text); Toast.makeText(this@ChatActivity, "识别完成", Toast.LENGTH_SHORT).show() }
                    .onFailure { e -> Toast.makeText(this@ChatActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.onFailure { e -> recordingIndicator.visibility = View.GONE; Toast.makeText(this, "录音停止失败: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun handleSelectedImage(uri: Uri) {
        val client = mimoClient ?: run { Toast.makeText(this, "请先配置 API", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val ratio = 1024f / maxOf(bitmap.width, bitmap.height)
                val scaled = if (ratio < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
                val out = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                progressBar.visibility = View.VISIBLE
                val text = messageInput.text?.toString()?.trim(); messageInput.text?.clear()
                val response = client.sendImage(sessionId ?: "", base64, contentResolver.getType(uri) ?: "image/jpeg", text?.ifEmpty { null })
                messages.add(Message("msg_${System.currentTimeMillis()}", sessionId ?: "", "user", text ?: "[图片]", System.currentTimeMillis()))
                messages.add(response); messageAdapter.notifyItemRangeInserted(messages.size - 2, 2); scrollToBottom()
                if (autoTtsEnabled) speakText(response.content)
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            finally { progressBar.visibility = View.GONE }
        }
    }

    private fun loadMessages() {
        val client = mimoClient ?: return
        sessionId?.let { id -> lifecycleScope.launch {
            try { messages.clear(); messages.addAll(client.getMessages(id)); messageAdapter.notifyDataSetChanged(); scrollToBottom() }
            catch (e: Exception) { Toast.makeText(this@ChatActivity, "加载消息失败: ${e.message}", Toast.LENGTH_SHORT).show() }
        }}
    }

    private fun sendMessage(text: String) {
        val client = mimoClient ?: run { Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_SHORT).show(); return }
        val userMessage = Message("msg_${System.currentTimeMillis()}", sessionId ?: "", "user", text, System.currentTimeMillis())
        messages.add(userMessage); messageAdapter.notifyItemInserted(messages.size - 1); scrollToBottom()
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE; sendButton.isEnabled = false
            try {
                val response = client.sendMessage(sessionId ?: "", text, CharacterManager.getSelectedCharacter(this@ChatActivity).systemPrompt)
                messages.add(response); messageAdapter.notifyItemInserted(messages.size - 1); scrollToBottom()
                if (autoTtsEnabled) speakText(response.content)
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            finally { progressBar.visibility = View.GONE; sendButton.isEnabled = true }
        }
    }

    private fun speakText(text: String) {
        val client = mimoClient ?: return
        val character = CharacterManager.getSelectedCharacter(this)
        lifecycleScope.launch { try { ttsService.speak(text, client, character.voiceId) } catch (_: Exception) {} }
    }

    private fun scrollToBottom() { if (messages.isNotEmpty()) messageRecyclerView.smoothScrollToPosition(messages.size - 1) }

    override fun onDestroy() { super.onDestroy(); ttsService.stop(); if (isRecording) asrService.cancelRecording() }
}
