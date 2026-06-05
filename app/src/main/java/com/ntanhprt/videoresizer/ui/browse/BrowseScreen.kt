package com.ntanhprt.videoresizer.ui.browse

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ntanhprt.videoresizer.App
import com.ntanhprt.videoresizer.data.model.VideoFile

@Composable
fun BrowseScreen(
    onNavigateToConfig: (String) -> Unit
) {
    val context = LocalContext.current
    val vm: BrowseViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as App
                return BrowseViewModel(app.videoRepository) as T
            }
        }
    )

    val state by vm.uiState.collectAsState()
    val density = LocalDensity.current

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.onPermissionGranted() }

    LaunchedEffect(Unit) { permLauncher.launch(permission) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var totalHeightPx by remember { mutableIntStateOf(1) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { totalHeightPx = it.height }
    ) {
        // ── Top: Video Preview ──
        val topHeightDp = with(density) { (totalHeightPx * splitRatio).toDp() }
        Box(modifier = Modifier.fillMaxWidth().height(topHeightDp).statusBarsPadding()) {
            VideoPlayerSection(uri = state.previewUri, modifier = Modifier.fillMaxSize())
            if (state.previewUri == null) {
                Text(
                    "Chọn video để xem trước",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            IconButton(
                onClick = { /* fullscreen — PlayerView handles via its own fullscreen button */ },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Toàn màn hình",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ── Draggable divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val newRatio = splitRatio + delta / totalHeightPx
                        splitRatio = newRatio.coerceIn(0.3f, 0.7f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Box(
                modifier = Modifier
                    .size(40.dp, 4.dp)
                    .padding(0.dp)
            )
        }

        // ── Video List ──
        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.videos.isEmpty() && state.permissionGranted) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy video nào", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.videos, key = { it.id }) { video ->
                    VideoListItem(
                        video = video,
                        isSelected = video.id in state.selectedIds,
                        isPreviewActive = state.previewUri == video.uri,
                        onTap = { vm.previewVideo(video) },
                        onToggleSelect = { vm.toggleSelection(video.id) },
                        onOpenExternal = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(video.uri, video.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Mở bằng"))
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }

        // ── Bottom Bar ──
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Đã chọn: ${state.selectedIds.size} file",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { onNavigateToConfig(vm.selectedIdsParam()) },
                    enabled = state.selectedIds.isNotEmpty()
                ) {
                    Text("Cấu hình nén")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoFile,
    isSelected: Boolean,
    isPreviewActive: Boolean,
    onTap: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val bgColor = if (isPreviewActive)
        MaterialTheme.colorScheme.surfaceVariant
    else MaterialTheme.colorScheme.surface

    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onTap, onLongClick = onOpenExternal)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${video.sizeFormatted} · ${video.resolution} · ${video.durationFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isPreviewActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Đang xem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
