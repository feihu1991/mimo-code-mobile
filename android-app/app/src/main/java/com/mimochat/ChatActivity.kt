package com.mimochat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var voiceButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var messageAdapter: MessageAdapter
    
    private var sessionId: String? = null
    private var sessionTitle: String? = null
    private val messages = mutableListOf<Message>()
    
    private lateinit var mimoClient: MiMoClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        sessionId = intent.getStringExtra("SESSION_ID")
        sessionTitle = intent.getStringExtra("SESSION_TITLE")
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        initMiMoClient()
        loadMessages()
    }
    
    private fun initViews() {
        messageRecyclerView = findViewById(R.id.messageRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        voiceButton = findViewById(R.id.voiceButton)
        progressBar = findViewById(R.id.progressBar)
        
        title = sessionTitle ?: "新对话"
    }
    
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages)
        messageRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messageRecyclerView.adapter = messageAdapter
    }
    
    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                messageInput.text.clear()
            }
        }
        
        attachButton.setOnClickListener {
            // TODO: Open image picker
        }
        
        voiceButton.setOnClickListener {
            // TODO: Start voice recording
        }
    }
    
    private fun initMiMoClient() {
        val config = MiMoConfigManager.getConfig(this)
        if (config != null) {
            mimoClient = MiMoClient(config)
        }
    }
    
    private fun loadMessages() {
        sessionId?.let { id ->
            lifecycleScope.launch {
                try {
                    val sessionMessages = mimoClient.getMessages(id)
                    messages.clear()
                    messages.addAll(sessionMessages)
                    messageAdapter.notifyDataSetChanged()
                    scrollToBottom()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun sendMessage(text: String) {
        val userMessage = Message(
            id = "msg_${System.currentTimeMillis()}",
            sessionId = sessionId ?: "",
            role = "user",
            content = text,
            createdAt = System.currentTimeMillis()
        )
        
        messages.add(userMessage)
        messageAdapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val response = mimoClient.sendMessage(
                    sessionId = sessionId ?: "",
                    content = text,
                    system = CharacterManager.getSelectedCharacter(this@ChatActivity).systemPrompt
                )
                messages.add(response)
                messageAdapter.notifyItemInserted(messages.size - 1)
                scrollToBottom()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            messageRecyclerView.smoothScrollToPosition(messages.size - 1)
        }
    }
}
