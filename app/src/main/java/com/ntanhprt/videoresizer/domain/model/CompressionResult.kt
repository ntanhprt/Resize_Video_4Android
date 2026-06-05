package com.ntanhprt.videoresizer.domain.model

import com.ntanhprt.videoresizer.data.model.VideoFile

sealed class CompressionResult {
    data class Success(
        val inputFile: VideoFile,
        val outputPath: String,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long
    ) : CompressionResult() {
        val savedBytes: Long get() = originalSizeBytes - compressedSizeBytes
        val savingPercent: Int get() = ((savedBytes.toDouble() / originalSizeBytes) * 100).toInt()
    }

    data class Failure(
        val inputFile: VideoFile,
        val error: String
    ) : CompressionResult()
}
