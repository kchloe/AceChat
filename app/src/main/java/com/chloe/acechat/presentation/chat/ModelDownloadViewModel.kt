package com.chloe.acechat.presentation.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chloe.acechat.data.llm.KEY_MODEL_DOWNLOAD_ERROR
import com.chloe.acechat.data.llm.KEY_MODEL_DOWNLOAD_PROGRESS
import com.chloe.acechat.data.llm.MODEL_FILE_NAME
import com.chloe.acechat.data.llm.ModelDownloadWorker
import com.chloe.acechat.domain.model.ModelDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "ModelDownloadViewModel"
private const val WORK_NAME = "model_download"

class ModelDownloadViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _downloadState =
        MutableStateFlow<ModelDownloadState>(ModelDownloadState.Checking)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    val modelPath: String
        get() {
            val modelDir =
                File(getApplication<Application>().getExternalFilesDir(null), "models")
            return File(modelDir, MODEL_FILE_NAME).absolutePath
        }

    init {
        checkModelExists()
    }

    /**
     * Checks if the model file already exists on the device.
     * Transitions from Checking → Downloaded or NotDownloaded.
     */
    private fun checkModelExists() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelFile = File(modelPath)
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model file found: $modelPath (${modelFile.length()} bytes)")
                _downloadState.update { ModelDownloadState.Downloaded }
            } else {
                Log.d(TAG, "Model file not found: $modelPath")
                _downloadState.update { ModelDownloadState.NotDownloaded }
            }
        }
    }

    /**
     * Starts downloading the model file using WorkManager.
     *
     * Uses callbackFlow + awaitClose to bridge the LiveData observer into a Flow.
     * When viewModelScope is cancelled (ViewModel cleared), awaitClose removes the
     * observer automatically — no manual cleanup needed. (이슈 1 수정)
     */
    fun startDownload() {
        Log.d(TAG, "Starting model download")
        _downloadState.update { ModelDownloadState.Downloading(0) }

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>().build()
        val workManager = WorkManager.getInstance(getApplication())

        viewModelScope.launch {
            callbackFlow<WorkInfo?> {
                val liveData = workManager.getWorkInfoByIdLiveData(workRequest.id)
                val observer = Observer<WorkInfo?> { workInfo -> trySend(workInfo) }
                liveData.observeForever(observer)
                // awaitClose runs when the coroutine (viewModelScope) is cancelled,
                // ensuring the observer is always removed and no leak occurs.
                awaitClose { liveData.removeObserver(observer) }
            }.collect { workInfo ->
                if (workInfo == null) return@collect

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress =
                            workInfo.progress.getInt(KEY_MODEL_DOWNLOAD_PROGRESS, 0)
                        _downloadState.update { ModelDownloadState.Downloading(progress) }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Model download succeeded")
                        _downloadState.update { ModelDownloadState.Downloaded }
                    }

                    WorkInfo.State.FAILED -> {
                        val error =
                            workInfo.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR)
                                ?: "Unknown error"
                        Log.e(TAG, "Model download failed: $error")
                        _downloadState.update { ModelDownloadState.Failed(error) }
                    }

                    WorkInfo.State.CANCELLED -> {
                        Log.d(TAG, "Model download cancelled")
                        _downloadState.update { ModelDownloadState.NotDownloaded }
                    }

                    else -> {} // ENQUEUED / BLOCKED — no state change needed
                }
            }
        }

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
    }

    /**
     * Cancels the ongoing model download.
     */
    fun cancelDownload() {
        Log.d(TAG, "Cancelling model download")
        WorkManager.getInstance(getApplication()).cancelUniqueWork(WORK_NAME)
        _downloadState.update { ModelDownloadState.NotDownloaded }
    }
}
