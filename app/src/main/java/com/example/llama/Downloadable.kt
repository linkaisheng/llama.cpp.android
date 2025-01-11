package com.example.llama

import android.app.DownloadManager
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class Downloadable(
    val name: String,
    val uri: Uri,
    val file: File,
) {
    sealed interface Status {
        data object Ready : Status
        data class Downloading(val progress: Float, val speed: String = "", val eta: String = "") : Status
        data object Downloaded : Status
        data class Error(val message: String) : Status
    }

    companion object {
        private const val TAG = "Downloadable"
        private const val BUFFER_SIZE = 8192
        private const val TIMEOUT_MILLIS = 300000L // 5 minutes

        private val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .addInterceptor { chain ->
                try {
                    val directClient = chain.proceed(chain.request())
                    if (directClient.isSuccessful) {
                        return@addInterceptor directClient
                    }
                    directClient.close()
                    
                    val proxyRequest = chain.request().newBuilder().build()
                    val proxyClient = OkHttpClient.Builder()
                        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 7890)))
                        .build()
                    return@addInterceptor proxyClient.newCall(proxyRequest).execute()
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed", e)
                    throw e
                }
            }
            .build()

        @Composable
        fun Button(
            llama: Llama,
            dm: DownloadManager,
            item: Downloadable,
            onLoaded: () -> Unit = {}
        ) {
            val context = LocalContext.current
            var status by remember {
                mutableStateOf<Status>(
                    if (item.file.exists()) Status.Downloaded
                    else Status.Ready
                )
            }
            
            var showSnackbar by remember { mutableStateOf(false) }
            var snackbarMessage by remember { mutableStateOf("") }
            
            val coroutineScope = rememberCoroutineScope()
            val ioScope = CoroutineScope(Dispatchers.IO + Job())

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val currentStatus = status) {
                    is Status.Downloaded -> {
                        ElevatedButton(
                            onClick = { 
                                Log.d(TAG, "Loading model: ${item.name}")
                                coroutineScope.launch {
                                    try {
                                        llama.load(item.file.absolutePath)
                                        snackbarMessage = "模型加载成功"
                                        showSnackbar = true
                                        onLoaded()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error loading model", e)
                                        snackbarMessage = "模型加载失败: ${e.message}"
                                        showSnackbar = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("加载")
                        }
                    }
                    is Status.Ready -> {
                        ElevatedButton(
                            onClick = { 
                                status = Status.Downloading(0f)
                                snackbarMessage = "开始下载..."
                                showSnackbar = true
                                
                                ioScope.launch {
                                    try {
                                        item.file.parentFile?.mkdirs()
                                        if (item.file.exists()) {
                                            item.file.delete()
                                        }

                                        val request = Request.Builder()
                                            .url(item.uri.toString())
                                            .build()

                                        Log.d(TAG, "Starting download from: ${item.uri}")
                                        client.newCall(request).execute().use { response ->
                                            if (!response.isSuccessful) {
                                                throw Exception("服务器返回错误: ${response.code}")
                                            }

                                            val body = response.body ?: throw Exception("Empty response")
                                            val contentLength = body.contentLength().toFloat()
                                            var downloadedBytes = 0f
                                            var lastUpdateTime = System.currentTimeMillis()
                                            var lastDownloadedBytes = 0f

                                            FileOutputStream(item.file).use { output ->
                                                body.byteStream().use { input ->
                                                    val buffer = ByteArray(BUFFER_SIZE)
                                                    var bytes = input.read(buffer)
                                                    while (bytes >= 0) {
                                                        output.write(buffer, 0, bytes)
                                                        downloadedBytes += bytes
                                                        
                                                        val currentTime = System.currentTimeMillis()
                                                        val timeDiff = (currentTime - lastUpdateTime) / 1000f
                                                        if (timeDiff >= 0.1f || downloadedBytes == bytes.toFloat()) {
                                                            val bytesDiff = downloadedBytes - lastDownloadedBytes
                                                            val speed = formatBytes((bytesDiff / timeDiff).toLong()) + "/s"
                                                            
                                                            val remainingBytes = contentLength - downloadedBytes
                                                            val eta = if (bytesDiff > 0) {
                                                                val seconds = (remainingBytes / (bytesDiff / timeDiff)).toLong()
                                                                formatTime(seconds)
                                                            } else ""
                                                            
                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                status = Status.Downloading(
                                                                    progress = downloadedBytes / contentLength,
                                                                    speed = speed,
                                                                    eta = eta
                                                                )
                                                            }
                                                            
                                                            lastUpdateTime = currentTime
                                                            lastDownloadedBytes = downloadedBytes
                                                            
                                                            Log.d(TAG, "下载进度: ${formatBytes(downloadedBytes.toLong())} / ${formatBytes(contentLength.toLong())} ($speed)")
                                                        }
                                                        
                                                        bytes = input.read(buffer)
                                                    }
                                                }
                                            }
                                            
                                            coroutineScope.launch(Dispatchers.Main) {
                                                status = Status.Downloaded
                                                snackbarMessage = "下载完成!"
                                                showSnackbar = true
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Download failed", e)
                                        coroutineScope.launch(Dispatchers.Main) {
                                            Log.e(TAG, "下载失败: ${e.message}")
                                            status = Status.Error(e.message ?: "未知错误")
                                            snackbarMessage = "下载失败: ${e.message}"
                                            showSnackbar = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("下载")
                        }
                    }
                    is Status.Downloading -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "正在下载: ${(currentStatus.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (currentStatus.speed.isNotEmpty()) {
                                    Text(
                                        text = "速度: ${currentStatus.speed}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                if (currentStatus.eta.isNotEmpty()) {
                                    Text(
                                        text = "预计剩余时间: ${currentStatus.eta}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = currentStatus.progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )
                            }
                        }
                    }
                    is Status.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentStatus.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            ElevatedButton(
                                onClick = { status = Status.Ready },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("重试")
                            }
                        }
                    }
                }
            }

            if (showSnackbar) {
                LaunchedEffect(snackbarMessage) {
                    delay(2000)
                    showSnackbar = false
                }
            }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return String.format("%.1f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
        }

        private fun formatTime(seconds: Long): String {
            return when {
                seconds < 60 -> "${seconds}秒"
                seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
                else -> "${seconds / 3600}时${(seconds % 3600) / 60}分${seconds % 60}秒"
            }
        }
    }
}
