package com.ntanhprt.videoresizer.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import com.ntanhprt.videoresizer.domain.estimation.SizeEstimationEngine
import com.ntanhprt.videoresizer.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class VideoEstimate(
    val video: VideoFile,
    val originalBytes: Long,
    val estimatedBytes: Long
) {
    val savingPercent: Int get() {
        if (estimatedBytes < 0 || originalBytes <= 0) return 0
        return ((originalBytes - estimatedBytes).toDouble() / originalBytes * 100).toInt().coerceIn(0, 99)
    }
    val totalSavedBytes: Long get() = (originalBytes - estimatedBytes).coerceAtLeast(0)
}

data class ConfigUiState(
    val selectedVideos: List<VideoFile> = emptyList(),
    val config: CompressionConfig = CompressionConfig(),
    val estimates: List<VideoEstimate> = emptyList(),
    val totalSavedBytes: Long = 0L,
    val outputFolderPath: String = ""
)

class ConfigViewModel(
    private val repository: VideoRepository,
    private val estimationEngine: SizeEstimationEngine,
    private val selectedIdsParam: String
) : ViewModel() {

    private val selectedIds: Set<Long> = selectedIdsParam
        .split(",").mapNotNull { it.toLongOrNull() }.toSet()

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getVideos().collect { all ->
                val selected = all.filter { it.id in selectedIds }
                _uiState.update { it.copy(selectedVideos = selected) }
                recomputeEstimates()
            }
        }
    }

    fun setResolution(res: TargetResolution) {
        _uiState.update { it.copy(config = it.config.copy(targetResolution = res)) }
        recomputeEstimates()
    }

    fun setCodec(codec: VideoCodec) {
        _uiState.update { it.copy(config = it.config.copy(videoCodec = codec)) }
        recomputeEstimates()
    }

    fun setAudioBitrate(bitrate: AudioBitrate) {
        _uiState.update { it.copy(config = it.config.copy(audioBitrate = bitrate)) }
        recomputeEstimates()
    }

    fun setOutputMode(mode: OutputMode) {
        _uiState.update { it.copy(config = it.config.copy(outputMode = mode)) }
    }

    fun setOutputFolderPath(path: String) {
        _uiState.update { it.copy(
            outputFolderPath = path,
            config = it.config.copy(outputFolderPath = path)
        )}
    }

    private fun recomputeEstimates() {
        val state = _uiState.value
        val estimates = state.selectedVideos.map { video ->
            val estimated = estimationEngine.estimateOutputBytes(video.durationMs, state.config)
            VideoEstimate(video, video.sizeBytes, estimated)
        }
        _uiState.update { it.copy(estimates = estimates, totalSavedBytes = estimates.sumOf { e -> e.totalSavedBytes }) }
    }

    fun buildConfigJson(): String =
        Json.encodeToString(CompressionConfig.serializer(), _uiState.value.config)
}
