package com.mimochat

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VideoCallActivity : AppCompatActivity() {
    
    private lateinit var characterNameText: TextView
    private lateinit var characterAvatarText: TextView
    private lateinit var endCallButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    
    private var isMuted = false
    private var isFrontCamera = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)
        
        initViews()
        setupClickListeners()
        startVideoCall()
    }
    
    private fun initViews() {
        characterNameText = findViewById(R.id.characterNameText)
        characterAvatarText = findViewById(R.id.characterAvatarText)
        endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        
        val character = CharacterManager.getSelectedCharacter(this)
        characterNameText.text = character.name
        characterAvatarText.text = character.avatar
    }
    
    private fun setupClickListeners() {
        endCallButton.setOnClickListener {
            endVideoCall()
        }
        
        muteButton.setOnClickListener {
            isMuted = !isMuted
            // TODO: Implement mute functionality
        }
        
        switchCameraButton.setOnClickListener {
            isFrontCamera = !isFrontCamera
            // TODO: Switch camera
        }
    }
    
    private fun startVideoCall() {
        // TODO: Start video call with MiMo
    }
    
    private fun endVideoCall() {
        // TODO: End video call
        finish()
    }
}
