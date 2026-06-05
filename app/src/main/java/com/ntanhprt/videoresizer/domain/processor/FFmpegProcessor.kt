package com.ntanhprt.videoresizer.domain.processor

import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult

class FFmpegProcessor : VideoProcessor {

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult = CompressionResult.Failure(
        input,
        "Codec ${config.videoCodec.name} không được hỗ trợ trên thiết bị này"
    )
}
