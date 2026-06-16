package com.mimochat.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mimochat.data.CharacterManager
import com.mimochat.data.MiMoConfigManager
import com.mimochat.service.VoiceCallConfig
import com.mimochat.service.VoiceCallCallback
import com.mimochat.service.VoiceCallService
import com.mimochat.service.VoiceCallState

@Composable
fun VoiceCallScreen(navController: NavController, sessionId: String) {
    val context = LocalContext.current
    val voiceService = remember { VoiceCallService(context) }
    var callState by remember { mutableStateOf<VoiceCallState>(VoiceCallState.Disconnected) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    val character = remember { CharacterManager.getSelectedCharacter(context) }

    DisposableEffect(Unit) {
        voiceService.setCallback(object : VoiceCallCallback {
            override fun onStateChanged(state: VoiceCallState) { callState = state }
            override fun onAudioLevelChanged(level: Float) {}
        })
        onDispose { voiceService.release() }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val config = MiMoConfigManager.getConfig(context)
            if (config != null) {
                voiceService.startCall(VoiceCallConfig(sessionId, character, config.baseUrl, config.apiKey))
            } else {
                Toast.makeText(context, "请先配置 API", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        } else {
            Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        if (voiceService.hasPermission()) {
            val config = MiMoConfigManager.getConfig(context)
            if (config != null) voiceService.startCall(VoiceCallConfig(sessionId, character, config.baseUrl, config.apiKey))
            else { Toast.makeText(context, "请先配置 API", Toast.LENGTH_SHORT).show(); navController.popBackStack() }
        } else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(character.avatar, fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(character.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            when (callState) {
                is VoiceCallState.Disconnected -> "已断开"
                is VoiceCallState.Connecting -> "正在连接..."
                is VoiceCallState.Connected -> "通话中"
                is VoiceCallState.Error -> "连接失败"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            // Mute
            IconButton(
                onClick = { isMuted = !isMuted; voiceService.setMuted(isMuted) },
                modifier = Modifier.size(64.dp).clip(CircleShape).background(if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, "静音", modifier = Modifier.size(32.dp))
            }
            // End call
            IconButton(
                onClick = { voiceService.stopCall(); navController.popBackStack() },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.CallEnd, "挂断", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onError)
            }
            // Speaker
            IconButton(
                onClick = { isSpeakerOn = !isSpeakerOn; voiceService.setSpeaker(isSpeakerOn) },
                modifier = Modifier.size(64.dp).clip(CircleShape).background(if (isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "扬声器", modifier = Modifier.size(32.dp))
            }
        }
    }
}
