package com.mimochat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class VoiceCallActivity : AppCompatActivity(), VoiceCallCallback {
    
    private lateinit var characterNameText: TextView
    private lateinit var characterAvatarText: TextView
    private lateinit var statusText: TextView
    private lateinit var endCallButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var speakerButton: ImageButton
    
    private lateinit var voiceCallService: VoiceCallService
    private var isMuted = false
    private var isSpeakerOn = true
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)
        
        voiceCallService = VoiceCallService(this)
        voiceCallService.setCallback(this)
        
        initViews()
        setupClickListeners()
        
        if (checkPermissions()) {
            startVoiceCall()
        } else {
            requestPermissions()
        }
    }
    
    private fun initViews() {
        characterNameText = findViewById(R.id.characterNameText)
        characterAvatarText = findViewById(R.id.characterAvatarText)
        statusText = findViewById(R.id.statusText)
        endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton)
        speakerButton = findViewById(R.id.speakerButton)
        
        val character = CharacterManager.getSelectedCharacter(this)
        characterNameText.text = character.name
        characterAvatarText.text = character.avatar
        statusText.text = "正在连接..."
    }
    
    private fun setupClickListeners() {
        endCallButton.setOnClickListener {
            endVoiceCall()
        }
        
        muteButton.setOnClickListener {
            isMuted = !isMuted
            voiceCallService.setMuted(isMuted)
            updateMuteButton()
        }
        
        speakerButton.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            voiceCallService.setSpeaker(isSpeakerOn)
            updateSpeakerButton()
        }
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceCall()
            } else {
                Toast.makeText(this, "需要麦克风权限才能进行语音通话", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun startVoiceCall() {
        val config = MiMoConfigManager.getConfig(this)
        if (config == null) {
            Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val character = CharacterManager.getSelectedCharacter(this)
        val sessionId = intent.getStringExtra("SESSION_ID") ?: ""
        
        val voiceConfig = VoiceCallConfig(
            sessionId = sessionId,
            character = character,
            baseUrl = config.baseUrl,
            apiKey = config.apiKey
        )
        
        voiceCallService.startCall(voiceConfig)
    }
    
    private fun endVoiceCall() {
        voiceCallService.stopCall()
        finish()
    }
    
    private fun updateMuteButton() {
        val icon = if (isMuted) {
            R.drawable.ic_mic_off
        } else {
            R.drawable.ic_mic_on
        }
        muteButton.setImageResource(icon)
    }
    
    private fun updateSpeakerButton() {
        val icon = if (isSpeakerOn) {
            R.drawable.ic_speaker_on
        } else {
            R.drawable.ic_speaker_off
        }
        speakerButton.setImageResource(icon)
    }
    
    override fun onStateChanged(state: VoiceCallState) {
        runOnUiThread {
            when (state) {
                is VoiceCallState.Disconnected -> {
                    statusText.text = "已断开"
                }
                is VoiceCallState.Connecting -> {
                    statusText.text = "正在连接..."
                }
                is VoiceCallState.Connected -> {
                    statusText.text = "通话中"
                }
                is VoiceCallState.Error -> {
                    statusText.text = "连接失败"
                    Toast.makeText(this, "错误: ${state.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onAudioLevelChanged(level: Float) {
        // Could be used to show audio level indicator
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceCallService.release()
    }
}
