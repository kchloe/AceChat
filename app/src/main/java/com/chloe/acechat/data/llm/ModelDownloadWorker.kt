package com.chloe.acechat.data.llm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

private const val TAG = "ModelDownloadWorker"
private const val NOTIFICATION_CHANNEL_ID = "model_download_channel"
private const val NOTIFICATION_ID = 1001
private const val PROGRESS_UPDATE_INTERVAL = 500 // ms

const val KEY_MODEL_DOWNLOAD_PROGRESS = "model_download_progress"
const val KEY_MODEL_DOWNLOAD_ERROR = "model_download_error"

// Model file details
const val MODEL_FILE_NAME = "gemma-3n-e4b-it-int4.litertlm"
const val MODEL_DOWNLOAD_URL = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm"
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val okHttpClient = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createNotificationChannel()
            val notification = buildNotification(0)
            setForeground(
                ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            )

            val modelDir =
                File(applicationContext.getExternalFilesDir(null), "models").apply { mkdirs() }
            val modelFile = File(modelDir, MODEL_FILE_NAME)
            val tmpFile = File(modelDir, "$MODEL_FILE_NAME.tmp")

            // Delete existing temp file if any
            tmpFile.delete()

            Log.d(TAG, "Starting download from: $MODEL_DOWNLOAD_URL")
            Log.d(TAG, "Target location: ${modelFile.absolutePath}")

            downloadFile(MODEL_DOWNLOAD_URL, tmpFile) { progress ->
                updateProgress(progress)
            }

            // Verify file integrity
            if (!tmpFile.exists() || tmpFile.length() == 0L) {
                tmpFile.delete()
                Log.e(TAG, "Downloaded file is empty or doesn't exist")
                return@withContext Result.failure(
                    workDataOf(KEY_MODEL_DOWNLOAD_ERROR to "Downloaded file is invalid")
                )
            }

            // Move temp file to final location
            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (!tmpFile.renameTo(modelFile)) {
                tmpFile.delete()
                Log.e(TAG, "Failed to move downloaded file to target location")
                return@withContext Result.failure(
                    workDataOf(KEY_MODEL_DOWNLOAD_ERROR to "Failed to save model file")
                )
            }

            Log.d(TAG, "Model downloaded successfully: ${modelFile.absolutePath}")
            Log.d(TAG, "File size: ${modelFile.length()} bytes")

            updateProgress(100)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(
                workDataOf(KEY_MODEL_DOWNLOAD_ERROR to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * Downloads a file from the given URL with progress tracking.
     *
     * @param url File URL to download
     * @param outputFile Destination file
     * @param onProgressUpdate Callback for progress updates (0-100)
     */
    private fun downloadFile(
        url: String,
        outputFile: File,
        onProgressUpdate: (Int) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .apply {
                val token = com.chloe.acechat.BuildConfig.HF_TOKEN
                if (token.isNotEmpty()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Failed to download: ${response.code}")
        }

        val responseBody = response.body ?: throw IOException("Empty response body")
        val totalBytes = responseBody.contentLength()

        if (totalBytes <= 0) {
            throw IOException("Invalid content length: $totalBytes")
        }

        var lastUpdateTime = System.currentTimeMillis()
        var downloadedBytes = 0L

        responseBody.byteStream().use { inputStream ->
            outputFile.outputStream().use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        onProgressUpdate(progress)
                        lastUpdateTime = currentTime

                        Log.d(TAG, "Download progress: $progress% ($downloadedBytes / $totalBytes bytes)")
                    }
                }
            }
        }
    }

    private fun updateProgress(progress: Int) {
        setProgressAsync(workDataOf(KEY_MODEL_DOWNLOAD_PROGRESS to progress))

        val notification = buildNotification(progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(progress: Int): Notification =
        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Downloading AceChat Model")
            .setContentText("$progress% completed")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
    }
}
