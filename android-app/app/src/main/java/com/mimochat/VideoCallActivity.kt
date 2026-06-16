package com.mimochat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class VideoCallActivity : AppCompatActivity(), VideoCallCallback {
    companion object { private const val TAG = "VideoCallActivity"; private const val PERM_REQ = 1002 }

    private lateinit var characterNameText: TextView
    private lateinit var characterAvatarText: TextView
    private lateinit var statusText: TextView
    private lateinit var endCallButton: ImageButton
    private lateinit var muteButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var localPreview: PreviewView
    private lateinit var remoteVideoView: ImageView
    private lateinit var videoCallService: VideoCallService
    private var isMuted = false; private var isFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_call)
        videoCallService = VideoCallService(this); videoCallService.setCallback(this)
        initViews(); setupClickListeners()
        if (checkPermissions()) startVideoCall() else requestPermissions()
    }

    private fun initViews() {
        characterNameText = findViewById(R.id.characterNameText); characterAvatarText = findViewById(R.id.characterAvatarText)
        statusText = findViewById(R.id.statusText); endCallButton = findViewById(R.id.endCallButton)
        muteButton = findViewById(R.id.muteButton); switchCameraButton = findViewById(R.id.switchCameraButton)
        localPreview = findViewById(R.id.localPreview); remoteVideoView = findViewById(R.id.remoteVideoView)
        val character = CharacterManager.getSelectedCharacter(this)
        characterNameText.text = character.name; characterAvatarText.text = character.avatar; statusText.text = "正在连接..."
        videoCallService.onVideoFrame = { jpegData ->
            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            remoteVideoView.setImageBitmap(bitmap); remoteVideoView.visibility = View.VISIBLE; characterAvatarText.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        endCallButton.setOnClickListener { videoCallService.stopCall(); finish() }
        muteButton.setOnClickListener { isMuted = !isMuted; videoCallService.setMuted(isMuted); muteButton.alpha = if (isMuted) 0.5f else 1.0f }
        switchCameraButton.setOnClickListener { isFrontCamera = !isFrontCamera; bindCamera() }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), PERM_REQ) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ) { if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startVideoCall() else { Toast.makeText(this, "需要麦克风和相机权限", Toast.LENGTH_SHORT).show(); finish() } }
    }

    private fun startVideoCall() {
        val config = MiMoConfigManager.getConfig(this) ?: run { Toast.makeText(this, "请先配置 MiMo API", Toast.LENGTH_SHORT).show(); finish(); return }
        val character = CharacterManager.getSelectedCharacter(this)
        videoCallService.startCall(VoiceCallConfig(intent.getStringExtra("SESSION_ID") ?: "", character, config.baseUrl, config.apiKey))
        bindCamera()
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(localPreview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy -> processFrame(imageProxy) }
            try { cameraProvider?.unbindAll(); cameraProvider?.bindToLifecycle(this, selector, preview, analysis) }
            catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val yBuffer = imageProxy.planes[0].buffer; val uBuffer = imageProxy.planes[1].buffer; val vBuffer = imageProxy.planes[2].buffer
            val ySize = yBuffer.remaining(); val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uBuffer.remaining() + vSize)
            yBuffer.get(nv21, 0, ySize); vBuffer.get(nv21, ySize, vSize); uBuffer.get(nv21, ySize + vSize, uBuffer.remaining())
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream(); yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 60, out)
            val jpeg = if (isFrontCamera) { val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return; val m = Matrix().apply { preScale(-1f, 1f) }; val o = ByteArrayOutputStream(); Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).compress(Bitmap.CompressFormat.JPEG, 60, o); o.toByteArray() } else out.toByteArray()
            videoCallService.sendVideoFrame(jpeg)
        } catch (e: Exception) { Log.e(TAG, "Frame error", e) } finally { imageProxy.close() }
    }

    override fun onStateChanged(state: VoiceCallState) {
        runOnUiThread { when (state) {
            is VoiceCallState.Disconnected -> statusText.text = "已断开"
            is VoiceCallState.Connecting -> statusText.text = "正在连接..."
            is VoiceCallState.Connected -> statusText.text = "视频通话中"
            is VoiceCallState.Error -> { statusText.text = "连接失败"; Toast.makeText(this, "错误: ${state.message}", Toast.LENGTH_SHORT).show() }
        }}
    }

    override fun onDestroy() { super.onDestroy(); videoCallService.release(); cameraProvider?.unbindAll(); analysisExecutor.shutdown() }
}
