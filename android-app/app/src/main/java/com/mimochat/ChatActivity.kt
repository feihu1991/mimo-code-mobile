package com.mimochat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var progressBar: View
    private lateinit var messageAdapter: MessageAdapter
    
    private var sessionId: String? = null
    private var sessionTitle: String? = null
    private val messages = mutableListOf<Message>()
    
    private var mimoClient: MiMoClient? = null
    
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
                messageInput.text?.clear()
            }
        }
    }
    
    private fun initMiMoClient() {
        val config = MiMoConfigManager.getConfig(this)
        if (config != null) {
            mimoClient = MiMoClient(config)
        } else {
            Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun loadMessages() {
        val client = mimoClient ?: return
        sessionId?.let { id ->
            lifecycleScope.launch {
                try {
                    val sessionMessages = client.getMessages(id)
                    messages.clear()
                    messages.addAll(sessionMessages)
                    messageAdapter.notifyDataSetChanged()
                    scrollToBottom()
                } catch (e: Exception) {
                    Toast.makeText(this@ChatActivity, "加载消息失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun sendMessage(text: String) {
        val client = mimoClient
        if (client == null) {
            Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_SHORT).show()
            return
        }
        
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
            sendButton.isEnabled = false
            try {
                val response = client.sendMessage(
                    sessionId = sessionId ?: "",
                    content = text,
                    system = CharacterManager.getSelectedCharacter(this@ChatActivity).systemPrompt
                )
                messages.add(response)
                messageAdapter.notifyItemInserted(messages.size - 1)
                scrollToBottom()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
            }
        }
    }
    
    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            messageRecyclerView.smoothScrollToPosition(messages.size - 1)
        }
    }
}
