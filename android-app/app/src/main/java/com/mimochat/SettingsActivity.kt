package com.mimochat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var baseUrlInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        initViews()
        loadConfig()
        setupClickListeners()
    }
    
    private fun initViews() {
        baseUrlInput = findViewById(R.id.baseUrlInput)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        
        title = "MiMo API 设置"
    }
    
    private fun loadConfig() {
        val config = MiMoConfigManager.getConfig(this)
        if (config != null) {
            baseUrlInput.setText(config.baseUrl)
            apiKeyInput.setText(config.apiKey)
        } else {
            baseUrlInput.setText("https://api.mimo.xiaomi.com")
        }
    }
    
    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            val baseUrl = baseUrlInput.text.toString().trim()
            val apiKey = apiKeyInput.text.toString().trim()
            
            if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val config = MiMoConfig(baseUrl = baseUrl, apiKey = apiKey)
            MiMoConfigManager.saveConfig(this, config)
            
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
