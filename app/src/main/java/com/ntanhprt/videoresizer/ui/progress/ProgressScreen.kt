package com.ntanhprt.videoresizer.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    workId: String,
    onDone: () -> Unit,
    vm: ProgressViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(workId) { vm.observeWork(workId) }
    LaunchedEffect(state.isFinished) { if (state.isFinished) onDone() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Đang nén video") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.fileProgresses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(state.fileProgresses, key = { index, _ -> index }) { index, fp ->
                    FileProgressCard(
                        fileProgress = fp,
                        isActive = index == state.currentFileIndex && !state.isFinished,
                        isDone = fp.isDone || index < state.currentFileIndex
                    )
                }
            }

            OutlinedButton(
                onClick = { vm.cancelWork() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isFinished
            ) {
                Text("Dừng lại")
            }
        }
    }
}

@Composable
fun FileProgressCard(fileProgress: FileProgress, isActive: Boolean, isDone: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    fileProgress.name.ifBlank { "Đang chờ..." },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isDone -> "✓ Xong"
                        isActive -> "${fileProgress.percent}%"
                        else -> "Chờ..."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 52.dp),
                    textAlign = TextAlign.End,
                    color = when {
                        isDone -> MaterialTheme.colorScheme.tertiary
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    }
                )
            }
            if (isActive || isDone) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (isDone) 1f else fileProgress.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
