package com.ntanhprt.videoresizer.domain.estimation

import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.TargetResolution

class SizeEstimationEngine {

    /**
     * Returns estimated output size in bytes.
     * Returns -1 if resolution is ORIGINAL (can't estimate without transcoding).
     */
    fun estimateOutputBytes(durationMs: Long, config: CompressionConfig): Long {
        if (config.targetResolution == TargetResolution.ORIGINAL) return -1L

        val durationSec = durationMs / 1000.0
        val videoBitrateKbps = config.targetResolution.videoBitrateKbps *
                config.videoCodec.qualityMultiplier
        val audioBitrateKbps = if (config.audioBitrate.kbps < 0) 128.0 else config.audioBitrate.kbps.toDouble()
        val totalBitrateKbps = videoBitrateKbps + audioBitrateKbps

        return (totalBitrateKbps * 1000.0 * durationSec / 8.0).toLong()
    }

    fun estimateSavingPercent(originalBytes: Long, durationMs: Long, config: CompressionConfig): Int {
        val estimated = estimateOutputBytes(durationMs, config)
        if (estimated < 0L || originalBytes <= 0L) return 0
        val saving = ((originalBytes - estimated).toDouble() / originalBytes * 100).toInt()
        return saving.coerceIn(0, 99)
    }
}
