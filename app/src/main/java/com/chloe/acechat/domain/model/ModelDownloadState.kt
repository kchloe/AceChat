package com.chloe.acechat.domain.model

/**
 * Represents the state of the model file download.
 */
sealed class ModelDownloadState {
    /**
     * Checking whether the model file already exists on device (initial app launch).
     */
    object Checking : ModelDownloadState()

    /**
     * Model file not found. Download is required.
     */
    object NotDownloaded : ModelDownloadState()

    /**
     * Model file is being downloaded.
     * @param progress Download progress (0-100).
     */
    data class Downloading(val progress: Int) : ModelDownloadState()

    /**
     * Model file has been successfully downloaded and is ready to use.
     */
    object Downloaded : ModelDownloadState()

    /**
     * Model download failed.
     * @param message Error description.
     */
    data class Failed(val message: String) : ModelDownloadState()
}
