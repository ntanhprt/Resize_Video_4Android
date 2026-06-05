package com.ntanhprt.videoresizer.data.model

import android.net.Uri

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val path: String
) {
    val resolution: String get() = "${height}p"
    val sizeFormatted: String get() = formatSize(sizeBytes)
    val durationFormatted: String get() = formatDuration(durationMs)

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        else -> "%.0f KB".format(bytes / 1_024.0)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
