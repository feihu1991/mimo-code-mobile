package com.mimochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.mimochat.data.CharacterManager
import com.mimochat.data.Message
import com.mimochat.data.MiMoClient
import com.mimochat.data.MiMoConfigManager
import com.mimochat.ui.navigation.Routes
import com.mimochat.service.ASRService
import com.mimochat.service.TTSService
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, sessionId: String, sessionTitle: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val mimoClient = remember {
        MiMoConfigManager.getConfig(context)?.let { MiMoClient(it) }
    }
    val asrService = remember { ASRService(context) }
    val ttsService = remember { TTSService(context) }

    var messages by remember { mutableStateOf(listOf<Message>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var autoTts by remember { mutableStateOf(false) }
    val character = remember { CharacterManager.getSelectedCharacter(context) }

    // Load messages
    LaunchedEffect(sessionId) {
        mimoClient?.let { client ->
            try { messages = client.getMessages(sessionId) } catch (_: Exception) {}
        }
    }

    // Auto scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() } ?: return@launch
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val ratio = 1024f / maxOf(bitmap.width, bitmap.height)
                    val scaled = if (ratio < 1) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
                    val out = ByteArrayOutputStream(); scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
                    val text = inputText.ifBlank { null }
                    inputText = ""
                    val response = mimoClient!!.sendImage(sessionId, base64, mimeType, text)
                    messages = messages + Message("msg_${System.currentTimeMillis()}", sessionId, "user", text ?: "[图片]", System.currentTimeMillis()) + response
                    if (autoTts) ttsService.speak(response.content, mimoClient)
                } catch (e: Exception) {
                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally { isLoading = false }
            }
        }
    }

    // ASR permission
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isRecording = true
            asrService.startRecording()
        } else Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) { onDispose { ttsService.stop(); if (asrService.isRecording()) asrService.cancelRecording() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sessionTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.voiceCall(sessionId)) }) {
                        Icon(Icons.Default.Phone, "语音通话")
                    }
                    IconButton(onClick = { navController.navigate(Routes.videoCall(sessionId)) }) {
                        Icon(Icons.Default.Videocam, "视频通话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                if (isLoading) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("思考中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Recording indicator
            if (isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在录音...", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            // Input bar
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, "图片")
                }
                IconButton(onClick = {
                    if (isRecording) {
                        isRecording = false
                        val result = asrService.stopRecording()
                        result.onSuccess { file ->
                            Toast.makeText(context, "正在识别...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                asrService.transcribe(file, mimoClient!!).onSuccess { text ->
                                    inputText = text
                                    Toast.makeText(context, "识别完成", Toast.LENGTH_SHORT).show()
                                }.onFailure { e ->
                                    Toast.makeText(context, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        if (asrService.hasPermission()) {
                            isRecording = true; asrService.startRecording()
                        } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(Icons.Default.Mic, "语音", tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = {
                    autoTts = !autoTts
                    Toast.makeText(context, if (autoTts) "语音朗读已开启" else "语音朗读已关闭", Toast.LENGTH_SHORT).show()
                    if (!autoTts) ttsService.stop()
                }) {
                    Icon(Icons.Default.VolumeUp, "TTS", modifier = Modifier.alpha(if (autoTts) 1f else 0.4f))
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    placeholder = { Text("输入消息...") },
                    maxLines = 4
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val text = inputText; inputText = ""
                            scope.launch {
                                isLoading = true
                                try {
                                    val response = mimoClient!!.sendMessage(sessionId, text, character.systemPrompt)
                                    messages = messages + Message("msg_${System.currentTimeMillis()}", sessionId, "user", text, System.currentTimeMillis()) + response
                                    if (autoTts) ttsService.speak(response.content, mimoClient)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally { isLoading = false }
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", fontSize = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 16.sp)
            }
        }
    }
}
