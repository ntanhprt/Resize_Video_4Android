package com.ntanhprt.videoresizer.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.ntanhprt.videoresizer.App
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult
import com.ntanhprt.videoresizer.domain.model.OutputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class VideoCompressionWork(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_VIDEO_IDS = "video_ids"
        const val KEY_CONFIG_JSON = "config_json"
        const val KEY_PROGRESS_FILE_INDEX = "file_index"
        const val KEY_PROGRESS_FILE_NAME = "file_name"
        const val KEY_PROGRESS_PERCENT = "percent"
        const val KEY_RESULTS_JSON = "results_json"
        const val CHANNEL_ID = "compression_channel"
        const val NOTIF_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext as App
        val videoIds = inputData.getLongArray(KEY_VIDEO_IDS)?.toList()
            ?: return@withContext Result.failure()
        val configJson = inputData.getString(KEY_CONFIG_JSON)
            ?: return@withContext Result.failure()
        val config = Json.decodeFromString<CompressionConfig>(configJson)

        createNotificationChannel()
        setForeground(createForegroundInfo("Đang chuẩn bị..."))

        val allVideos = mutableListOf<com.ntanhprt.videoresizer.data.model.VideoFile>()
        app.videoRepository.getVideos().collect { allVideos.addAll(it) }
        val selectedVideos = allVideos.filter { it.id in videoIds }

        val results = mutableListOf<String>()

        selectedVideos.forEachIndexed { index, video ->
            setProgress(workDataOf(
                KEY_PROGRESS_FILE_INDEX to index,
                KEY_PROGRESS_FILE_NAME to video.name,
                KEY_PROGRESS_PERCENT to 0
            ))
            setForeground(createForegroundInfo("${index + 1}/${selectedVideos.size}: ${video.name}"))

            val outputPath = resolveOutputPath(video, config)
            File(outputPath).parentFile?.mkdirs()

            val result = app.hybridVideoProcessor.compress(video, config, outputPath) { pct ->
                setProgressAsync(workDataOf(
                    KEY_PROGRESS_FILE_INDEX to index,
                    KEY_PROGRESS_FILE_NAME to video.name,
                    KEY_PROGRESS_PERCENT to pct
                ))
            }

            if (result is CompressionResult.Success && config.outputMode == OutputMode.OVERWRITE) {
                File(video.path).delete()
                File(outputPath).renameTo(File(video.path))
            }

            results.add(serializeResult(result))
        }

        Result.success(workDataOf(KEY_RESULTS_JSON to results.joinToString("|")))
    }

    private fun resolveOutputPath(
        video: com.ntanhprt.videoresizer.data.model.VideoFile,
        config: CompressionConfig
    ): String = when (config.outputMode) {
        OutputMode.OVERWRITE -> {
            val dir = File(video.path).parent ?: ctx.filesDir.path
            "$dir/_tmp_${video.name}"
        }
        OutputMode.NEW_FOLDER -> {
            val dir = config.outputFolderPath.ifBlank {
                "${ctx.getExternalFilesDir(null)?.path}/VideoResizer"
            }
            "$dir/${video.name}"
        }
    }

    private fun serializeResult(result: CompressionResult): String = when (result) {
        is CompressionResult.Success ->
            "ok|${result.inputFile.name}|${result.originalSizeBytes}|${result.compressedSizeBytes}"
        is CompressionResult.Failure ->
            "fail|${result.inputFile.name}|${result.error}"
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Video Resizer")
            .setContentText(title)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Nén video", NotificationManager.IMPORTANCE_LOW
            )
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
