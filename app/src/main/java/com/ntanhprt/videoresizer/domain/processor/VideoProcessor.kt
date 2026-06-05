package com.ntanhprt.videoresizer.domain.processor

import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult

interface VideoProcessor {
    suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult
}

class UnsupportedCodecException(message: String) : Exception(message)
