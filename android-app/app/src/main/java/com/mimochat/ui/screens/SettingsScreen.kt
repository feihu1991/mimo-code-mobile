package com.mimochat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mimochat.data.MiMoConfig
import com.mimochat.data.MiMoConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val existingConfig = remember { MiMoConfigManager.getConfig(context) }
    var baseUrl by remember { mutableStateOf(existingConfig?.baseUrl ?: "https://api.mimo.xiaomi.com") }
    var apiKey by remember { mutableStateOf(existingConfig?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiMo API 设置") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(Icons.Default.RemoveRedEye, if (showKey) "隐藏" else "显示")
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text("API Key 将使用 AES-256 加密存储", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when {
                        baseUrl.isBlank() -> Toast.makeText(context, "请输入 API 地址", Toast.LENGTH_SHORT).show()
                        apiKey.isBlank() -> Toast.makeText(context, "请输入 API Key", Toast.LENGTH_SHORT).show()
                        !baseUrl.startsWith("http") -> Toast.makeText(context, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
                        else -> {
                            MiMoConfigManager.saveConfig(context, MiMoConfig(baseUrl.removeSuffix("/"), apiKey))
                            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存配置") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    MiMoConfigManager.clearConfig(context)
                    baseUrl = "https://api.mimo.xiaomi.com"; apiKey = ""
                    Toast.makeText(context, "配置已清除", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("清除配置", color = MaterialTheme.colorScheme.error) }
        }
    }
}
