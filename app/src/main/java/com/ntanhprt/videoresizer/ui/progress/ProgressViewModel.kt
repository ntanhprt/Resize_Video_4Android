package com.ntanhprt.videoresizer.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ntanhprt.videoresizer.work.VideoCompressionWork
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class FileProgress(
    val name: String,
    val percent: Int,
    val isDone: Boolean = false
)

data class ProgressUiState(
    val fileProgresses: List<FileProgress> = emptyList(),
    val currentFileIndex: Int = 0,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false
)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var workId: UUID? = null

    fun observeWork(workIdStr: String) {
        val id = runCatching { UUID.fromString(workIdStr) }.getOrNull() ?: return
        workId = id
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                info ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val fileIndex = info.progress.getInt(VideoCompressionWork.KEY_PROGRESS_FILE_INDEX, 0)
                        val fileName = info.progress.getString(VideoCompressionWork.KEY_PROGRESS_FILE_NAME) ?: ""
                        val percent = info.progress.getInt(VideoCompressionWork.KEY_PROGRESS_PERCENT, 0)
                        _uiState.update { state ->
                            val list = state.fileProgresses.toMutableList()
                            repeat((fileIndex + 1) - list.size) { list.add(FileProgress("", 0)) }
                            // mark previous files as done
                            if (fileIndex > 0 && list.size > fileIndex - 1) {
                                list[fileIndex - 1] = list[fileIndex - 1].copy(isDone = true)
                            }
                            list[fileIndex] = FileProgress(fileName, percent)
                            state.copy(fileProgresses = list, currentFileIndex = fileIndex)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> _uiState.update {
                        it.copy(isFinished = true, fileProgresses = it.fileProgresses.map { fp -> fp.copy(isDone = true) })
                    }
                    WorkInfo.State.CANCELLED -> _uiState.update { it.copy(isFinished = true) }
                    WorkInfo.State.FAILED -> _uiState.update { it.copy(isFinished = true, isFailed = true) }
                    else -> {}
                }
            }
        }
    }

    fun cancelWork() {
        workId?.let { workManager.cancelWorkById(it) }
    }
}
