package com.mimochat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ASRService(private val context: Context) {
    companion object { private const val TAG = "ASRService" }

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startRecording(): Result<File> {
        if (isRecording) return Result.failure(IllegalStateException("Already recording"))
        outputFile = File(context.cacheDir, "asr_${System.currentTimeMillis()}.m4a")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(outputFile?.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        return Result.success(outputFile!!)
    }

    fun stopRecording(): Result<File> {
        if (!isRecording) return Result.failure(IllegalStateException("Not recording"))
        return try {
            mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null; isRecording = false
            Result.success(outputFile!!)
        } catch (e: Exception) { mediaRecorder?.release(); mediaRecorder = null; isRecording = false; Result.failure(e) }
    }

    fun cancelRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null; isRecording = false; outputFile?.delete(); outputFile = null
    }

    fun isRecording(): Boolean = isRecording

    suspend fun transcribe(audioFile: File, mimoClient: MiMoClient): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = mimoClient.transcribe(audioFile)
            if (text.isBlank()) Result.failure(Exception("未识别到语音内容")) else Result.success(text)
        } catch (e: Exception) { Result.failure(e) }
        finally { audioFile.delete() }
    }
}
