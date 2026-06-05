package com.ntanhprt.videoresizer.domain.processor

import android.content.Context
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult

class HybridVideoProcessor(context: Context) : VideoProcessor {

    private val mediaCodecProcessor = MediaCodecProcessor(context)
    private val ffmpegProcessor = FFmpegProcessor()

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult {
        return try {
            mediaCodecProcessor.compress(input, config, outputPath, onProgress)
        } catch (e: UnsupportedCodecException) {
            ffmpegProcessor.compress(input, config, outputPath, onProgress)
        }
    }
}
