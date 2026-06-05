package com.ntanhprt.videoresizer.domain.processor

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class FFmpegProcessor : VideoProcessor {

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult = suspendCancellableCoroutine { cont ->

        val videoCodecStr = when (config.videoCodec) {
            VideoCodec.H264 -> "libx264"
            VideoCodec.H265 -> "libx265"
            VideoCodec.VP9  -> "libvpx-vp9"
        }

        val scaleFilter = if (config.targetResolution == TargetResolution.ORIGINAL) ""
        else "-vf scale=-2:${config.targetResolution.height}"

        val videoBitrate = if (config.targetResolution == TargetResolution.ORIGINAL) ""
        else "-b:v ${(config.targetResolution.videoBitrateKbps * config.videoCodec.qualityMultiplier).toInt()}k"

        val cmd = buildString {
            append("-i \"${input.path}\" ")
            append("-c:v $videoCodecStr ")
            if (videoBitrate.isNotEmpty()) append("$videoBitrate ")
            if (scaleFilter.isNotEmpty()) append("$scaleFilter ")
            append("-c:a aac -b:a ${config.audioBitrate.kbps}k ")
            append("-y \"$outputPath\"")
        }

        FFmpegKitConfig.enableStatisticsCallback { stats ->
            val durationMs = input.durationMs.takeIf { it > 0 } ?: 1L
            val processedMs = stats.time.toLong()
            val progress = ((processedMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 100)
            onProgress(progress)
        }

        val session = FFmpegKit.executeAsync(cmd) { session ->
            FFmpegKitConfig.disableStatisticsCallback()
            if (ReturnCode.isSuccess(session.returnCode)) {
                val outFile = File(outputPath)
                cont.resume(
                    CompressionResult.Success(
                        inputFile = input,
                        outputPath = outputPath,
                        originalSizeBytes = input.sizeBytes,
                        compressedSizeBytes = outFile.length()
                    )
                )
            } else {
                cont.resume(
                    CompressionResult.Failure(
                        input,
                        "FFmpeg error: ${session.failStackTrace ?: "unknown"}"
                    )
                )
            }
        }

        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
}
