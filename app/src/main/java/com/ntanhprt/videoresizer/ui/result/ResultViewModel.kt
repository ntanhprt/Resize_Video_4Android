package com.ntanhprt.videoresizer.ui.result

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.getWorkInfoByIdFlow
import com.ntanhprt.videoresizer.work.VideoCompressionWork
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class FileResult(
    val name: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val isSuccess: Boolean,
    val error: String = ""
) {
    val savedBytes: Long get() = (originalBytes - compressedBytes).coerceAtLeast(0)
    val savingPercent: Int get() = if (originalBytes > 0)
        ((savedBytes.toDouble() / originalBytes) * 100).toInt() else 0
}

data class ResultUiState(
    val results: List<FileResult> = emptyList(),
    val totalSavedBytes: Long = 0L,
    val isLoading: Boolean = true
)

class ResultViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun loadResults(workIdStr: String) {
        val id = runCatching { UUID.fromString(workIdStr) }.getOrNull() ?: return
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                info ?: return@collect
                if (info.state != WorkInfo.State.SUCCEEDED) return@collect
                val raw = info.outputData.getString(VideoCompressionWork.KEY_RESULTS_JSON) ?: return@collect
                val results = raw.split("|").chunked(4).mapNotNull { parts ->
                    when (parts.getOrNull(0)) {
                        "ok" -> FileResult(
                            name = parts.getOrElse(1) { "" },
                            originalBytes = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0L,
                            compressedBytes = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L,
                            isSuccess = true
                        )
                        "fail" -> FileResult(
                            name = parts.getOrElse(1) { "" },
                            originalBytes = 0L,
                            compressedBytes = 0L,
                            isSuccess = false,
                            error = parts.getOrElse(2) { "Lỗi không xác định" }
                        )
                        else -> null
                    }
                }
                _uiState.update { ResultUiState(results, results.sumOf { it.savedBytes }, false) }
            }
        }
    }
}
