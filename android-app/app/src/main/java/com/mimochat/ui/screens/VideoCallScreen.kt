package com.mimochat.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.mimochat.data.CharacterManager
import com.mimochat.data.MiMoConfigManager
import com.mimochat.service.VoiceCallConfig
import com.mimochat.service.VideoCallCallback
import com.mimochat.service.VideoCallService
import com.mimochat.service.VoiceCallState

@Composable
fun VideoCallScreen(navController: NavController, sessionId: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoService = remember { VideoCallService(context) }
    var callState by remember { mutableStateOf<VoiceCallState>(VoiceCallState.Disconnected) }
    var isMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var remoteFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val character = remember { CharacterManager.getSelectedCharacter(context) }

    DisposableEffect(Unit) {
        videoService.setCallback(object : VideoCallCallback {
            override fun onStateChanged(state: VoiceCallState) { callState = state }
        })
        videoService.onVideoFrame = { jpeg ->
            remoteFrame = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        }
        onDispose { videoService.release() }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            val config = MiMoConfigManager.getConfig(context)
            if (config != null) videoService.startCall(VoiceCallConfig(sessionId, character, config.baseUrl, config.apiKey))
            else { Toast.makeText(context, "请先配置 API", Toast.LENGTH_SHORT).show(); navController.popBackStack() }
        } else { Toast.makeText(context, "需要麦克风和相机权限", Toast.LENGTH_SHORT).show(); navController.popBackStack() }
    }

    LaunchedEffect(Unit) { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Remote video or avatar
        if (remoteFrame != null) {
            Image(bitmap = remoteFrame!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(character.avatar, fontSize = 72.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(character.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // Status
        Text(
            when (callState) {
                is VoiceCallState.Connecting -> "正在连接..."
                is VoiceCallState.Connected -> "视频通话中"
                is VoiceCallState.Disconnected -> "已断开"
                is VoiceCallState.Error -> "连接失败"
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )

        // Local preview (CameraX)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview) } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(width = 120.dp, height = 160.dp)
        )

        // Controls
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(
                onClick = { isMuted = !isMuted; videoService.setMuted(isMuted) },
                modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)
            ) { Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, "静音", modifier = Modifier.size(28.dp)) }

            IconButton(
                onClick = { videoService.stopCall(); navController.popBackStack() },
                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Default.CallEnd, "挂断", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onError) }

            IconButton(
                onClick = { isFrontCamera = !isFrontCamera },
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            ) { Icon(Icons.Default.Cameraswitch, "切换", modifier = Modifier.size(28.dp)) }
        }
    }
}
