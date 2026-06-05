package com.ntanhprt.videoresizer.ui.config

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.ntanhprt.videoresizer.App
import com.ntanhprt.videoresizer.domain.model.*
import com.ntanhprt.videoresizer.work.VideoCompressionWork

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    selectedVideoIds: String,
    onNavigateToProgress: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ConfigViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as App
                return ConfigViewModel(app.videoRepository, app.sizeEstimationEngine, selectedVideoIds) as T
            }
        }
    )
    val state by vm.uiState.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.path?.let { vm.setOutputFolderPath(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình nén") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.totalSavedBytes > 0) {
                        Text(
                            "Dự kiến tiết kiệm: ${formatBytes(state.totalSavedBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            val workId = startCompression(context, selectedVideoIds, vm)
                            onNavigateToProgress(workId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selectedVideos.isNotEmpty()
                    ) {
                        Text("Bắt đầu nén ${state.selectedVideos.size} video")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ConfigSection("Độ phân giải") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetResolution.entries.forEach { res ->
                            FilterChip(
                                selected = state.config.targetResolution == res,
                                onClick = { vm.setResolution(res) },
                                label = { Text(res.label) }
                            )
                        }
                    }
                }
            }
            item {
                ConfigSection("Codec video") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoCodec.entries.forEach { codec ->
                            FilterChip(
                                selected = state.config.videoCodec == codec,
                                onClick = { vm.setCodec(codec) },
                                label = { Text(codec.label) }
                            )
                        }
                    }
                }
            }
            item {
                ConfigSection("Bitrate audio") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioBitrate.entries.forEach { br ->
                            FilterChip(
                                selected = state.config.audioBitrate == br,
                                onClick = { vm.setAudioBitrate(br) },
                                label = { Text(br.label) }
                            )
                        }
                    }
                }
            }
            item {
                ConfigSection("Lưu file") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutputMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.config.outputMode == mode,
                                onClick = { vm.setOutputMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    if (state.config.outputMode == OutputMode.NEW_FOLDER) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (state.outputFolderPath.isBlank()) "Chọn thư mục lưu..."
                                else state.outputFolderPath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (state.estimates.isNotEmpty()) {
                item {
                    Text("Ước tính từng file", style = MaterialTheme.typography.titleSmall)
                }
                items(state.estimates) { est ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            est.video.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${formatBytes(est.originalBytes)} → ${
                                if (est.estimatedBytes < 0) "?" else formatBytes(est.estimatedBytes)
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(6.dp))
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1_024.0)
}

private fun startCompression(
    context: android.content.Context,
    selectedVideoIds: String,
    vm: ConfigViewModel
): String {
    val ids = selectedVideoIds.split(",").mapNotNull { it.toLongOrNull() }.toLongArray()
    val request = OneTimeWorkRequestBuilder<VideoCompressionWork>()
        .setInputData(workDataOf(
            VideoCompressionWork.KEY_VIDEO_IDS to ids,
            VideoCompressionWork.KEY_CONFIG_JSON to vm.buildConfigJson()
        ))
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    WorkManager.getInstance(context).enqueue(request)
    return request.id.toString()
}
