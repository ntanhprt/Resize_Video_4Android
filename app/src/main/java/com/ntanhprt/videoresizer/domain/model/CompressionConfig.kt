package com.ntanhprt.videoresizer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CompressionConfig(
    val targetResolution: TargetResolution = TargetResolution.P720,
    val videoCodec: VideoCodec = VideoCodec.H265,
    val audioBitrate: AudioBitrate = AudioBitrate.KBPS_64,
    val outputMode: OutputMode = OutputMode.NEW_FOLDER,
    val outputFolderPath: String = ""
)

@Serializable
enum class TargetResolution(val label: String, val height: Int, val videoBitrateKbps: Int) {
    ORIGINAL("Giữ nguyên", -1, -1),
    P360("360p", 360, 500),
    P480("480p", 480, 1000),
    P720("720p", 720, 2500),
    P1080("1080p", 1080, 5000)
}

@Serializable
enum class VideoCodec(val label: String, val qualityMultiplier: Double) {
    H264("H.264", 1.0),
    H265("H.265 (HEVC)", 0.5),
    VP9("VP9", 0.6)
}

@Serializable
enum class AudioBitrate(val label: String, val kbps: Int) {
    KEEP_ORIGINAL("Giữ nguyên", -1),
    KBPS_32("32 kbps", 32),
    KBPS_64("64 kbps", 64),
    KBPS_128("128 kbps", 128)
}

@Serializable
enum class OutputMode(val label: String) {
    OVERWRITE("Ghi đè file gốc"),
    NEW_FOLDER("Lưu vào thư mục mới")
}
