package com.ntanhprt.videoresizer.ui.browse

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowseUiState(
    val videos: List<VideoFile> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val previewUri: Uri? = null,
    val previewVideoName: String = "",
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false
)

class BrowseViewModel(private val repository: VideoRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getVideos().collect { videos ->
                _uiState.update { it.copy(videos = videos, isLoading = false) }
            }
        }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val newSet = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(selectedIds = newSet)
        }
    }

    fun previewVideo(video: VideoFile) {
        _uiState.update { it.copy(previewUri = video.uri, previewVideoName = video.name) }
    }

    fun onPermissionGranted() {
        _uiState.update { it.copy(permissionGranted = true) }
        loadVideos()
    }

    fun selectedIdsParam(): String = _uiState.value.selectedIds.joinToString(",")
}
