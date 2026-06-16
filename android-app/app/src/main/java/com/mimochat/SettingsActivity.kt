package com.mimochat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    
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
        clearButton = findViewById(R.id.clearButton)
        
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
            
            if (baseUrl.isEmpty()) {
                baseUrlInput.error = "请输入 API 地址"
                return@setOnClickListener
            }
            if (apiKey.isEmpty()) {
                apiKeyInput.error = "请输入 API Key"
                return@setOnClickListener
            }
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrlInput.error = "请输入有效的 URL"
                return@setOnClickListener
            }
            
            val config = MiMoConfig(
                baseUrl = baseUrl.removeSuffix("/"),
                apiKey = apiKey
            )
            MiMoConfigManager.saveConfig(this, config)
            
            Toast.makeText(this, "配置已保存（已加密存储）", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        clearButton.setOnClickListener {
            MiMoConfigManager.clearConfig(this)
            baseUrlInput.setText("https://api.mimo.xiaomi.com")
            apiKeyInput.text?.clear()
            Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show()
        }
    }
}
