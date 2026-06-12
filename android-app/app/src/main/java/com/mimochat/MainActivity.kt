package com.mimochat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    
    private lateinit var sessionRecyclerView: RecyclerView
    private lateinit var newChatButton: FloatingActionButton
    private lateinit var sessionAdapter: SessionAdapter
    private val sessions = mutableListOf<Session>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadSessions()
    }
    
    private fun initViews() {
        sessionRecyclerView = findViewById(R.id.sessionRecyclerView)
        newChatButton = findViewById(R.id.newChatButton)
    }
    
    private fun setupRecyclerView() {
        sessionAdapter = SessionAdapter(sessions) { session ->
            openChat(session)
        }
        sessionRecyclerView.layoutManager = LinearLayoutManager(this)
        sessionRecyclerView.adapter = sessionAdapter
    }
    
    private fun setupClickListeners() {
        newChatButton.setOnClickListener {
            openChat(null)
        }
    }
    
    private fun loadSessions() {
        // Load sessions from storage
        sessions.clear()
        sessions.addAll(SessionManager.getSessions(this))
        sessionAdapter.notifyDataSetChanged()
    }
    
    private fun openChat(session: Session?) {
        val intent = Intent(this, ChatActivity::class.java)
        session?.let {
            intent.putExtra("SESSION_ID", it.id)
            intent.putExtra("SESSION_TITLE", it.title)
        }
        startActivity(intent)
    }
}
