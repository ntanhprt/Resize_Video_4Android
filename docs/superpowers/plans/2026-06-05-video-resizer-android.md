# Video Resizer Android — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app (API 26+, tiếng Việt) tìm, xem trước và nén hàng loạt video để giải phóng bộ nhớ.

**Architecture:** Single Activity + Jetpack Compose + Navigation Compose. VideoProcessor interface (MediaCodec ưu tiên, FFmpegKit fallback). WorkManager chạy nén background.

**Tech Stack:** Kotlin, Jetpack Compose BOM 2024.06, Navigation Compose 2.7.7, Media3 ExoPlayer 1.3.1, WorkManager 2.9.0, FFmpegKit-android 6.0-2, JUnit4, MockK, Turbine

---

## File Map

```
app/build.gradle.kts
settings.gradle.kts
build.gradle.kts (project)
.github/workflows/build.yml

app/src/main/AndroidManifest.xml
app/src/main/java/com/ntanhprt/videoresizer/
  App.kt
  MainActivity.kt
  data/
    model/VideoFile.kt
    source/MediaStoreDataSource.kt
    repository/VideoRepository.kt
  domain/
    model/CompressionConfig.kt
    model/CompressionResult.kt
    processor/VideoProcessor.kt          ← interface
    processor/MediaCodecProcessor.kt
    processor/FFmpegProcessor.kt
    processor/HybridVideoProcessor.kt
    estimation/SizeEstimationEngine.kt
  work/
    VideoCompressionWork.kt
  ui/
    theme/Color.kt
    theme/Type.kt
    theme/Theme.kt
    navigation/AppNavigation.kt
    browse/BrowseViewModel.kt
    browse/BrowseScreen.kt
    config/ConfigViewModel.kt
    config/ConfigScreen.kt
    progress/ProgressViewModel.kt
    progress/ProgressScreen.kt
    result/ResultViewModel.kt
    result/ResultScreen.kt

app/src/test/java/com/ntanhprt/videoresizer/
  domain/estimation/SizeEstimationEngineTest.kt
  ui/browse/BrowseViewModelTest.kt
  ui/config/ConfigViewModelTest.kt
```

---

## Task 1: Project Scaffold — Gradle + Manifest + App class

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (project)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/ntanhprt/videoresizer/App.kt`
- Create: `app/src/main/java/com/ntanhprt/videoresizer/MainActivity.kt`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VideoResizer"
include(":app")
```

- [ ] **Step 2: Create project build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 3: Create gradle/libs.versions.toml**

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
coreKtx = "1.13.1"
lifecycleRuntime = "2.8.2"
activityCompose = "1.9.0"
composeBom = "2024.06.00"
navigationCompose = "2.7.7"
media3 = "1.3.1"
workManager = "2.9.0"
ffmpegKit = "6.0-2"
coroutines = "1.8.0"
junit = "4.13.2"
mockk = "1.13.11"
turbine = "1.1.0"
coroutinesTest = "1.8.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntime" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntime" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
ffmpeg-kit-android = { group = "com.arthenica", name = "ffmpeg-kit-android", version.ref = "ffmpegKit" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 4: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ntanhprt.videoresizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ntanhprt.videoresizer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.work.runtime.ktx)
    implementation(libs.ffmpeg.kit.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"
        android:minSdkVersion="33" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.VideoResizer"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
```

- [ ] **Step 6: Create res/values/strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Video Resizer</string>
</resources>
```

- [ ] **Step 7: Create res/values/themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.VideoResizer" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 8: Create App.kt**

```kotlin
package com.ntanhprt.videoresizer

import android.app.Application
import com.ntanhprt.videoresizer.data.source.MediaStoreDataSource
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import com.ntanhprt.videoresizer.domain.estimation.SizeEstimationEngine
import com.ntanhprt.videoresizer.domain.processor.HybridVideoProcessor

class App : Application() {
    val mediaStoreDataSource by lazy { MediaStoreDataSource(this) }
    val videoRepository by lazy { VideoRepository(mediaStoreDataSource) }
    val sizeEstimationEngine by lazy { SizeEstimationEngine() }
    val hybridVideoProcessor by lazy { HybridVideoProcessor(this) }
}
```

- [ ] **Step 9: Create MainActivity.kt**

```kotlin
package com.ntanhprt.videoresizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ntanhprt.videoresizer.ui.navigation.AppNavigation
import com.ntanhprt.videoresizer.ui.theme.VideoResizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoResizerTheme {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 10: Sync project, verify it compiles**

```bash
cd /home/javis-ai/TA/Resize_Video_4Android
./gradlew assembleDebug --stacktrace 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "chore: initial Android project scaffold"
```

---

## Task 2: Data Models

**Files:**
- Create: `app/src/main/java/com/ntanhprt/videoresizer/data/model/VideoFile.kt`
- Create: `app/src/main/java/com/ntanhprt/videoresizer/domain/model/CompressionConfig.kt`
- Create: `app/src/main/java/com/ntanhprt/videoresizer/domain/model/CompressionResult.kt`

- [ ] **Step 1: Create VideoFile.kt**

```kotlin
package com.ntanhprt.videoresizer.data.model

import android.net.Uri

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val path: String
) {
    val resolution: String get() = "${height}p"
    val sizeFormatted: String get() = formatSize(sizeBytes)
    val durationFormatted: String get() = formatDuration(durationMs)

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        else -> "%.0f KB".format(bytes / 1_024.0)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
```

- [ ] **Step 2: Create CompressionConfig.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.model

data class CompressionConfig(
    val targetResolution: TargetResolution = TargetResolution.P720,
    val videoCodec: VideoCodec = VideoCodec.H265,
    val audioBitrate: AudioBitrate = AudioBitrate.KBPS_64,
    val outputMode: OutputMode = OutputMode.NEW_FOLDER,
    val outputFolderPath: String = ""
)

enum class TargetResolution(val label: String, val height: Int, val videoBitrateKbps: Int) {
    ORIGINAL("Giữ nguyên", -1, -1),
    P360("360p", 360, 500),
    P480("480p", 480, 1000),
    P720("720p", 720, 2500),
    P1080("1080p", 1080, 5000)
}

enum class VideoCodec(val label: String, val qualityMultiplier: Double) {
    H264("H.264", 1.0),
    H265("H.265 (HEVC)", 0.5),
    VP9("VP9", 0.6)
}

enum class AudioBitrate(val label: String, val kbps: Int) {
    KBPS_32("32 kbps", 32),
    KBPS_64("64 kbps", 64),
    KBPS_128("128 kbps", 128)
}

enum class OutputMode(val label: String) {
    OVERWRITE("Ghi đè file gốc"),
    NEW_FOLDER("Lưu vào thư mục mới")
}
```

- [ ] **Step 3: Create CompressionResult.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.model

import com.ntanhprt.videoresizer.data.model.VideoFile

sealed class CompressionResult {
    data class Success(
        val inputFile: VideoFile,
        val outputPath: String,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long
    ) : CompressionResult() {
        val savedBytes: Long get() = originalSizeBytes - compressedSizeBytes
        val savingPercent: Int get() = ((savedBytes.toDouble() / originalSizeBytes) * 100).toInt()
    }

    data class Failure(
        val inputFile: VideoFile,
        val error: String
    ) : CompressionResult()
}
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add data models VideoFile, CompressionConfig, CompressionResult"
```

---

## Task 3: MediaStore Data Source + Repository

**Files:**
- Create: `app/src/main/java/com/ntanhprt/videoresizer/data/source/MediaStoreDataSource.kt`
- Create: `app/src/main/java/com/ntanhprt/videoresizer/data/repository/VideoRepository.kt`

- [ ] **Step 1: Create MediaStoreDataSource.kt**

```kotlin
package com.ntanhprt.videoresizer.data.source

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import com.ntanhprt.videoresizer.data.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreDataSource(private val context: Context) {

    suspend fun queryAllVideos(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.SIZE} DESC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                videos.add(
                    VideoFile(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "unknown.mp4",
                        sizeBytes = cursor.getLong(sizeCol),
                        durationMs = cursor.getLong(durationCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        path = cursor.getString(dataCol) ?: ""
                    )
                )
            }
        }
        videos
    }
}
```

- [ ] **Step 2: Create VideoRepository.kt**

```kotlin
package com.ntanhprt.videoresizer.data.repository

import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.source.MediaStoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VideoRepository(private val dataSource: MediaStoreDataSource) {

    fun getVideos(): Flow<List<VideoFile>> = flow {
        emit(dataSource.queryAllVideos())
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add MediaStoreDataSource and VideoRepository"
```

---

## Task 4: SizeEstimationEngine + Tests

**Files:**
- Create: `app/src/main/java/com/ntanhprt/videoresizer/domain/estimation/SizeEstimationEngine.kt`
- Create: `app/src/test/java/com/ntanhprt/videoresizer/domain/estimation/SizeEstimationEngineTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.ntanhprt.videoresizer.domain.estimation

import com.ntanhprt.videoresizer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SizeEstimationEngineTest {

    private val engine = SizeEstimationEngine()

    @Test
    fun `estimate returns smaller size for H265 vs H264`() {
        val durationMs = 60_000L // 1 minute
        val config264 = CompressionConfig(
            targetResolution = TargetResolution.P720,
            videoCodec = VideoCodec.H264,
            audioBitrate = AudioBitrate.KBPS_64
        )
        val config265 = CompressionConfig(
            targetResolution = TargetResolution.P720,
            videoCodec = VideoCodec.H265,
            audioBitrate = AudioBitrate.KBPS_64
        )

        val size264 = engine.estimateOutputBytes(durationMs, config264)
        val size265 = engine.estimateOutputBytes(durationMs, config265)

        assertTrue("H265 should be smaller than H264", size265 < size264)
    }

    @Test
    fun `estimate returns -1 for ORIGINAL resolution`() {
        val config = CompressionConfig(targetResolution = TargetResolution.ORIGINAL)
        val result = engine.estimateOutputBytes(60_000L, config)
        assertEquals(-1L, result)
    }

    @Test
    fun `estimate 1080p H264 128kbps 1min is around 39MB`() {
        val config = CompressionConfig(
            targetResolution = TargetResolution.P1080,
            videoCodec = VideoCodec.H264,
            audioBitrate = AudioBitrate.KBPS_128
        )
        val bytes = engine.estimateOutputBytes(60_000L, config)
        val mb = bytes / 1_048_576.0
        // 5000 kbps video + 128 kbps audio = 5128 kbps × 60s / 8 = ~38.46 MB
        assertTrue("Expected ~38-40 MB, got $mb", mb in 38.0..40.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "*.SizeEstimationEngineTest" 2>&1 | tail -20
```
Expected: FAILED — class not found

- [ ] **Step 3: Create SizeEstimationEngine.kt**

```kotlin
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
        val audioBitrateKbps = config.audioBitrate.kbps.toDouble()
        val totalBitrateKbps = videoBitrateKbps + audioBitrateKbps

        // bits = kbps * 1000 * seconds; bytes = bits / 8
        return (totalBitrateKbps * 1000.0 * durationSec / 8.0).toLong()
    }

    fun estimateSavingPercent(originalBytes: Long, durationMs: Long, config: CompressionConfig): Int {
        val estimated = estimateOutputBytes(durationMs, config)
        if (estimated < 0L || originalBytes <= 0L) return 0
        val saving = ((originalBytes - estimated).toDouble() / originalBytes * 100).toInt()
        return saving.coerceIn(0, 99)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:test --tests "*.SizeEstimationEngineTest" 2>&1 | tail -20
```
Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add SizeEstimationEngine with tests"
```

---

## Task 5: VideoProcessor Interface + FFmpegProcessor

**Files:**
- Create: `domain/processor/VideoProcessor.kt`
- Create: `domain/processor/FFmpegProcessor.kt`

- [ ] **Step 1: Create VideoProcessor.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.processor

import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult

interface VideoProcessor {
    /**
     * Compress a single video file.
     * [onProgress] is called with values 0-100.
     * Throws [UnsupportedOperationException] if codec/format not supported.
     */
    suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult
}

class UnsupportedCodecException(message: String) : Exception(message)
```

- [ ] **Step 2: Create FFmpegProcessor.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.processor

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class FFmpegProcessor : VideoProcessor {

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult = suspendCancellableCoroutine { cont ->

        val videoCodec = when (config.videoCodec) {
            VideoCodec.H264 -> "libx264"
            VideoCodec.H265 -> "libx265"
            VideoCodec.VP9  -> "libvpx-vp9"
        }

        val scaleFilter = if (config.targetResolution == TargetResolution.ORIGINAL) ""
        else "-vf scale=-2:${config.targetResolution.height}"

        val videoBitrate = if (config.targetResolution == TargetResolution.ORIGINAL) ""
        else "-b:v ${(config.targetResolution.videoBitrateKbps * config.videoCodec.qualityMultiplier).toInt()}k"

        val cmd = buildString {
            append("-i \"${input.path}\" ")
            append("-c:v $videoCodec ")
            if (videoBitrate.isNotEmpty()) append("$videoBitrate ")
            if (scaleFilter.isNotEmpty()) append("$scaleFilter ")
            append("-c:a aac -b:a ${config.audioBitrate.kbps}k ")
            append("-y \"$outputPath\"")
        }

        // Track progress via statistics
        FFmpegKitConfig.enableStatisticsCallback { stats ->
            val durationMs = input.durationMs.takeIf { it > 0 } ?: 1L
            val processedMs = stats.time.toLong()
            val progress = ((processedMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 100)
            onProgress(progress)
        }

        val session = FFmpegKit.executeAsync(cmd) { session ->
            FFmpegKitConfig.disableStatisticsCallback()
            if (ReturnCode.isSuccess(session.returnCode)) {
                val outFile = File(outputPath)
                cont.resume(
                    CompressionResult.Success(
                        inputFile = input,
                        outputPath = outputPath,
                        originalSizeBytes = input.sizeBytes,
                        compressedSizeBytes = outFile.length()
                    )
                )
            } else {
                cont.resume(
                    CompressionResult.Failure(input, "FFmpeg error: ${session.failStackTrace ?: "unknown"}")
                )
            }
        }

        cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add VideoProcessor interface and FFmpegProcessor"
```

---

## Task 6: MediaCodecProcessor + HybridVideoProcessor

**Files:**
- Create: `domain/processor/MediaCodecProcessor.kt`
- Create: `domain/processor/HybridVideoProcessor.kt`

- [ ] **Step 1: Create MediaCodecProcessor.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.processor

import android.content.Context
import android.media.*
import android.net.Uri
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class MediaCodecProcessor(private val context: Context) : VideoProcessor {

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult = withContext(Dispatchers.IO) {

        val codecName = when (config.videoCodec) {
            VideoCodec.H264 -> "video/avc"
            VideoCodec.H265 -> "video/hevc"
            VideoCodec.VP9  -> "video/x-vnd.on2.vp9"
        }

        // Verify codec is available before starting
        val codecInfo = findEncoderForMime(codecName)
            ?: throw UnsupportedCodecException("No encoder for $codecName on this device")

        try {
            transcode(context, input, config, outputPath, codecName, onProgress)
            val outFile = File(outputPath)
            CompressionResult.Success(
                inputFile = input,
                outputPath = outputPath,
                originalSizeBytes = input.sizeBytes,
                compressedSizeBytes = outFile.length()
            )
        } catch (e: UnsupportedCodecException) {
            throw e
        } catch (e: Exception) {
            CompressionResult.Failure(input, e.message ?: "MediaCodec error")
        }
    }

    private fun findEncoderForMime(mimeType: String): MediaCodecInfo? {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        return list.codecInfos.firstOrNull { info ->
            !info.isEncoder.not() && info.isEncoder && info.supportedTypes.contains(mimeType)
        }
    }

    private fun transcode(
        context: Context,
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        videoMime: String,
        onProgress: (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, input.uri, null)

        val videoTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: throw UnsupportedCodecException("No video track found")

        val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

        val inputVideoFormat = extractor.getTrackFormat(videoTrackIndex)
        val srcWidth = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val srcHeight = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)

        val (outWidth, outHeight) = if (config.targetResolution == TargetResolution.ORIGINAL) {
            srcWidth to srcHeight
        } else {
            val targetH = config.targetResolution.height
            if (srcHeight <= targetH) srcWidth to srcHeight
            else {
                val scale = targetH.toFloat() / srcHeight
                ((srcWidth * scale).toInt().let { if (it % 2 != 0) it + 1 else it }) to targetH
            }
        }

        val bitrate = if (config.targetResolution == TargetResolution.ORIGINAL) {
            inputVideoFormat.getInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
        } else {
            (config.targetResolution.videoBitrateKbps * config.videoCodec.qualityMultiplier * 1000).toInt()
        }

        val outputVideoFormat = MediaFormat.createVideoFormat(videoMime, outWidth, outHeight).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        val encoder = MediaCodec.createEncoderByType(videoMime)
        encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()

        val decoder = MediaCodec.createDecoderByType(
            inputVideoFormat.getString(MediaFormat.KEY_MIME)!!
        )
        decoder.configure(inputVideoFormat, surface, null, 0)
        decoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerVideoTrack = -1
        var muxerAudioTrack = -1
        var muxerStarted = false

        // Copy audio track directly
        if (audioTrackIndex != null) {
            val audioFmt = extractor.getTrackFormat(audioTrackIndex)
            muxerAudioTrack = muxer.addTrack(audioFmt)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        var decoderDone = false
        var encoderDone = false
        val durationUs = input.durationMs * 1000

        extractor.selectTrack(videoTrackIndex)

        while (!encoderDone) {
            // Feed decoder
            if (!decoderDone) {
                val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        decoderDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                        val pct = ((extractor.sampleTime.toDouble() / durationUs) * 50).toInt().coerceIn(0, 50)
                        onProgress(pct)
                    }
                }
            }

            // Drain decoder output → surface → encoder automatically
            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outIdx >= 0) {
                val render = bufferInfo.size > 0
                decoder.releaseOutputBuffer(outIdx, render)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    encoder.signalEndOfInputStream()
                }
            }

            // Drain encoder
            val encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                    if (!muxerStarted) { muxer.start(); muxerStarted = true }
                }
                encOutIdx >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && muxerStarted) {
                        val buf = encoder.getOutputBuffer(encOutIdx)!!
                        muxer.writeSampleData(muxerVideoTrack, buf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    val pct = 50 + ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * 50).toInt().coerceIn(0, 50)
                    onProgress(pct)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                }
            }
        }

        // Copy audio samples
        if (audioTrackIndex != null && muxerStarted) {
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(context, input.uri, null)
            audioExtractor.selectTrack(audioTrackIndex)
            val audioBuf = ByteBuffer.allocate(1024 * 1024)
            val audioBufInfo = MediaCodec.BufferInfo()
            while (true) {
                val sz = audioExtractor.readSampleData(audioBuf, 0)
                if (sz < 0) break
                audioBufInfo.set(0, sz, audioExtractor.sampleTime, audioExtractor.sampleFlags)
                muxer.writeSampleData(muxerAudioTrack, audioBuf, audioBufInfo)
                audioExtractor.advance()
            }
            audioExtractor.release()
        }

        decoder.stop(); decoder.release()
        encoder.stop(); encoder.release()
        extractor.release()
        muxer.stop(); muxer.release()
        onProgress(100)
    }
}
```

- [ ] **Step 2: Create HybridVideoProcessor.kt**

```kotlin
package com.ntanhprt.videoresizer.domain.processor

import android.content.Context
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.domain.model.CompressionConfig
import com.ntanhprt.videoresizer.domain.model.CompressionResult

class HybridVideoProcessor(context: Context) : VideoProcessor {

    private val mediaCodecProcessor = MediaCodecProcessor(context)
    private val ffmpegProcessor = FFmpegProcessor()

    override suspend fun compress(
        input: VideoFile,
        config: CompressionConfig,
        outputPath: String,
        onProgress: (Int) -> Unit
    ): CompressionResult {
        return try {
            mediaCodecProcessor.compress(input, config, outputPath, onProgress)
        } catch (e: UnsupportedCodecException) {
            // MediaCodec doesn't support this codec on this device → fall back to FFmpeg
            ffmpegProcessor.compress(input, config, outputPath, onProgress)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add MediaCodecProcessor and HybridVideoProcessor"
```

---

## Task 7: WorkManager — VideoCompressionWork

**Files:**
- Create: `work/VideoCompressionWork.kt`

- [ ] **Step 1: Create VideoCompressionWork.kt**

```kotlin
package com.ntanhprt.videoresizer.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
        val videoIds = inputData.getLongArray(KEY_VIDEO_IDS)?.toList() ?: return@withContext Result.failure()
        val configJson = inputData.getString(KEY_CONFIG_JSON) ?: return@withContext Result.failure()
        val config = Json.decodeFromString<CompressionConfig>(configJson)

        createNotificationChannel()
        setForeground(createForegroundInfo("Đang chuẩn bị..."))

        val allVideos = app.videoRepository.getVideos().let { flow ->
            val list = mutableListOf<com.ntanhprt.videoresizer.data.model.VideoFile>()
            flow.collect { list.addAll(it) }
            list
        }
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
    ): String {
        return when (config.outputMode) {
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
            ForegroundInfo(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nén video", NotificationManager.IMPORTANCE_LOW)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 2: Add kotlinx.serialization to gradle (needed for Json.decodeFromString)**

In `gradle/libs.versions.toml`, add:
```toml
[versions]
kotlinxSerialization = "1.7.1"

[libraries]
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

In `app/build.gradle.kts`, add plugin and dependency:
```kotlin
plugins {
    // ...existing...
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // ...existing...
    implementation(libs.kotlinx.serialization.json)
}
```

Add `@Serializable` to `CompressionConfig` and all its enums in `CompressionConfig.kt`:
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class CompressionConfig(...)

@Serializable
enum class TargetResolution(...)

@Serializable
enum class VideoCodec(...)

@Serializable
enum class AudioBitrate(...)

@Serializable
enum class OutputMode(...)
```

- [ ] **Step 3: Sync and compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add VideoCompressionWork with WorkManager + serialization"
```

---

## Task 8: Theme + Navigation skeleton

**Files:**
- Create: `ui/theme/Color.kt`
- Create: `ui/theme/Theme.kt`
- Create: `ui/theme/Type.kt`
- Create: `ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

val DarkBackground = Color(0xFF1E1E2E)
val DarkSurface = Color(0xFF313244)
val AccentPurple = Color(0xFFCBA6F7)
val AccentBlue = Color(0xFF89B4FA)
val AccentGreen = Color(0xFFA6E3A1)
val AccentRed = Color(0xFFF38BA8)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    secondary = AccentBlue,
    tertiary = AccentGreen,
    background = DarkBackground,
    surface = DarkSurface,
    error = AccentRed
)

@Composable
fun VideoResizerTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```

- [ ] **Step 4: Create AppNavigation.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ntanhprt.videoresizer.ui.browse.BrowseScreen
import com.ntanhprt.videoresizer.ui.config.ConfigScreen
import com.ntanhprt.videoresizer.ui.progress.ProgressScreen
import com.ntanhprt.videoresizer.ui.result.ResultScreen

object Routes {
    const val BROWSE = "browse"
    const val CONFIG = "config/{selectedIds}"
    const val PROGRESS = "progress/{workId}"
    const val RESULT = "result/{workId}"

    fun config(selectedIds: String) = "config/$selectedIds"
    fun progress(workId: String) = "progress/$workId"
    fun result(workId: String) = "result/$workId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) {
            BrowseScreen(onNavigateToConfig = { ids ->
                navController.navigate(Routes.config(ids))
            })
        }
        composable(
            Routes.CONFIG,
            arguments = listOf(navArgument("selectedIds") { type = NavType.StringType })
        ) { back ->
            val ids = back.arguments?.getString("selectedIds") ?: ""
            ConfigScreen(
                selectedVideoIds = ids,
                onNavigateToProgress = { workId ->
                    navController.navigate(Routes.progress(workId)) {
                        popUpTo(Routes.BROWSE)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.PROGRESS,
            arguments = listOf(navArgument("workId") { type = NavType.StringType })
        ) { back ->
            val workId = back.arguments?.getString("workId") ?: ""
            ProgressScreen(
                workId = workId,
                onDone = { navController.navigate(Routes.result(workId)) { popUpTo(Routes.BROWSE) } }
            )
        }
        composable(
            Routes.RESULT,
            arguments = listOf(navArgument("workId") { type = NavType.StringType })
        ) { back ->
            val workId = back.arguments?.getString("workId") ?: ""
            ResultScreen(
                workId = workId,
                onProcessMore = { navController.navigate(Routes.BROWSE) { popUpTo(Routes.BROWSE) { inclusive = true } } },
                onDone = { navController.navigate(Routes.BROWSE) { popUpTo(Routes.BROWSE) { inclusive = true } } }
            )
        }
    }
}
```

- [ ] **Step 5: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD" | tail -20
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add theme and navigation skeleton"
```

---

## Task 9: BrowseViewModel

**Files:**
- Create: `ui/browse/BrowseViewModel.kt`
- Create: `app/src/test/java/com/ntanhprt/videoresizer/ui/browse/BrowseViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.ntanhprt.videoresizer.ui.browse

import android.net.Uri
import app.cash.turbine.test
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BrowseViewModelTest {

    private val repo = mockk<VideoRepository>()

    private fun fakeVideo(id: Long, sizeBytes: Long) = VideoFile(
        id = id, uri = Uri.EMPTY, name = "video$id.mp4",
        sizeBytes = sizeBytes, durationMs = 60_000L,
        width = 1920, height = 1080, mimeType = "video/mp4", path = "/fake/video$id.mp4"
    )

    @Test
    fun `initial state has empty selection and video list`() = runTest {
        coEvery { repo.getVideos() } returns flowOf(emptyList())
        val vm = BrowseViewModel(repo)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.videos.isEmpty())
            assertTrue(state.selectedIds.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleSelection adds and removes video id`() = runTest {
        val videos = listOf(fakeVideo(1L, 1_000_000L))
        coEvery { repo.getVideos() } returns flowOf(videos)
        val vm = BrowseViewModel(repo)

        vm.toggleSelection(1L)
        assertTrue(vm.uiState.value.selectedIds.contains(1L))

        vm.toggleSelection(1L)
        assertFalse(vm.uiState.value.selectedIds.contains(1L))
    }
}
```

- [ ] **Step 2: Run to verify fails**

```bash
./gradlew :app:test --tests "*.BrowseViewModelTest" 2>&1 | tail -10
```
Expected: FAILED

- [ ] **Step 3: Create BrowseViewModel.kt**

```kotlin
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
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:test --tests "*.BrowseViewModelTest" 2>&1 | tail -10
```
Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add BrowseViewModel with tests"
```

---

## Task 10: BrowseScreen

**Files:**
- Create: `ui/browse/BrowseScreen.kt`

- [ ] **Step 1: Create BrowseScreen.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.browse

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ntanhprt.videoresizer.App
import com.ntanhprt.videoresizer.data.model.VideoFile

@Composable
fun BrowseScreen(
    onNavigateToConfig: (String) -> Unit,
    vm: BrowseViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.Factory {
            val app = LocalContext.current.applicationContext as App
            BrowseViewModel(app.videoRepository)
        }
    )
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.onPermissionGranted() }

    LaunchedEffect(Unit) { permLauncher.launch(permission) }

    var totalHeightPx by remember { mutableIntStateOf(1) }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }

    Column(modifier = Modifier.fillMaxSize().onSizeChanged { totalHeightPx = it.height }) {

        // ── Top: Video Preview ──────────────────────────────────────
        val topHeightDp = with(density) { (totalHeightPx * splitRatio).toDp() }
        Box(modifier = Modifier.fillMaxWidth().height(topHeightDp)) {
            VideoPlayerSection(uri = state.previewUri, modifier = Modifier.fillMaxSize())
            if (state.previewUri == null) {
                Text(
                    "Chọn video để xem trước",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            IconButton(
                onClick = { /* TODO: fullscreen */ },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Toàn màn hình",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // ── Divider (draggable) ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
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
                modifier = Modifier.size(40.dp, 4.dp)
                    .padding(0.dp)
                    .let { it },
            )
        }

        // ── Bottom: Video List ──────────────────────────────────────
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

        // ── Bottom Bar ──────────────────────────────────────────────
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoFile,
    isSelected: Boolean,
    isPreviewActive: Boolean,
    onTap: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenExternal: () -> Unit
) {
    androidx.compose.foundation.combinedClickable(
        onClick = onTap,
        onLongClick = onOpenExternal
    )
    Surface(
        color = if (isPreviewActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (isPreviewActive) mod.padding(start = 3.dp)
                    .then(Modifier) else mod
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { mod ->
                    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                    mod.androidx.compose.foundation.combinedClickable(
                        onClick = onTap,
                        onLongClick = onOpenExternal
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.name, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "${video.sizeFormatted} · ${video.resolution} · ${video.durationFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isPreviewActive) {
                Text("Đang xem", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
```

- [ ] **Step 2: Create VideoPlayerSection.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.browse

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayerSection(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
        } else {
            player.stop()
            player.clearMediaItems()
        }
        onDispose { }
    }

    DisposableEffect(Unit) { onDispose { player.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier
    )
}
```

- [ ] **Step 3: Fix Compose combinedClickable usage (simplify VideoListItem)**

The inline lambda approach above is messy. Replace `VideoListItem` body with cleaner version:

```kotlin
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surface

    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .androidx.compose.foundation.combinedClickable(
                    onClick = onTap,
                    onLongClick = onOpenExternal
                )
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${video.sizeFormatted} · ${video.resolution} · ${video.durationFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (isPreviewActive) {
                Text(
                    "Đang xem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD" | tail -20
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add BrowseScreen with split preview and video list"
```

---

## Task 11: ConfigViewModel + ConfigScreen

**Files:**
- Create: `ui/config/ConfigViewModel.kt`
- Create: `ui/config/ConfigScreen.kt`
- Create: `app/src/test/java/.../ui/config/ConfigViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.ntanhprt.videoresizer.ui.config

import android.net.Uri
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import com.ntanhprt.videoresizer.domain.estimation.SizeEstimationEngine
import com.ntanhprt.videoresizer.domain.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfigViewModelTest {

    private val repo = mockk<VideoRepository>()
    private val engine = SizeEstimationEngine()

    private fun fakeVideo(id: Long) = VideoFile(
        id = id, uri = Uri.EMPTY, name = "v$id.mp4",
        sizeBytes = 1_073_741_824L, durationMs = 120_000L,
        width = 1920, height = 1080, mimeType = "video/mp4", path = "/v$id.mp4"
    )

    @Test
    fun `changing resolution updates estimates`() = runTest {
        coEvery { repo.getVideos() } returns flowOf(listOf(fakeVideo(1L)))
        val vm = ConfigViewModel(repo, engine, "1")

        vm.setResolution(TargetResolution.P480)

        val estimates = vm.uiState.value.estimates
        assertTrue(estimates.isNotEmpty())
        val est = estimates.first()
        assertTrue("Estimate should be less than original", est.estimatedBytes < est.originalBytes)
    }

    @Test
    fun `default config is 720p H265 64kbps`() = runTest {
        coEvery { repo.getVideos() } returns flowOf(emptyList())
        val vm = ConfigViewModel(repo, engine, "")
        val config = vm.uiState.value.config
        assertEquals(TargetResolution.P720, config.targetResolution)
        assertEquals(VideoCodec.H265, config.videoCodec)
        assertEquals(AudioBitrate.KBPS_64, config.audioBitrate)
    }
}
```

- [ ] **Step 2: Run to verify fails**

```bash
./gradlew :app:test --tests "*.ConfigViewModelTest" 2>&1 | tail -10
```

- [ ] **Step 3: Create ConfigViewModel.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import com.ntanhprt.videoresizer.domain.estimation.SizeEstimationEngine
import com.ntanhprt.videoresizer.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        val totalSaved = estimates.sumOf { it.totalSavedBytes }
        _uiState.update { it.copy(estimates = estimates, totalSavedBytes = totalSaved) }
    }

    fun buildConfigJson(): String {
        val config = _uiState.value.config
        return kotlinx.serialization.json.Json.encodeToString(CompressionConfig.serializer(), config)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:test --tests "*.ConfigViewModelTest" 2>&1 | tail -10
```
Expected: 2 tests PASSED

- [ ] **Step 5: Create ConfigScreen.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.config

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.ntanhprt.videoresizer.App
import com.ntanhprt.videoresizer.domain.model.*
import com.ntanhprt.videoresizer.work.VideoCompressionWork
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    selectedVideoIds: String,
    onNavigateToProgress: (String) -> Unit,
    onBack: () -> Unit,
    vm: ConfigViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.Factory {
            val app = LocalContext.current.applicationContext as App
            ConfigViewModel(app.videoRepository, app.sizeEstimationEngine, selectedVideoIds)
        }
    )
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { vm.setOutputFolderPath(it.path ?: "") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình nén") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
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
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val workId = startCompression(context, selectedVideoIds, vm)
                            onNavigateToProgress(workId)
                        },
                        modifier = Modifier.fillMaxWidth()
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
            item { ResolutionSection(state.config.targetResolution, vm::setResolution) }
            item { CodecSection(state.config.videoCodec, vm::setCodec) }
            item { AudioSection(state.config.audioBitrate, vm::setAudioBitrate) }
            item {
                OutputModeSection(
                    mode = state.config.outputMode,
                    folderPath = state.outputFolderPath,
                    onModeChange = vm::setOutputMode,
                    onPickFolder = { folderPicker.launch(null) }
                )
            }
            if (state.estimates.isNotEmpty()) {
                item { Text("Ước tính từng file", style = MaterialTheme.typography.titleSmall) }
                items(state.estimates) { est ->
                    EstimateRow(est)
                }
            }
        }
    }
}

@Composable
fun ResolutionSection(current: TargetResolution, onSelect: (TargetResolution) -> Unit) {
    ConfigSection(title = "Độ phân giải") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetResolution.entries.forEach { res ->
                FilterChip(selected = current == res, onClick = { onSelect(res) }, label = { Text(res.label) })
            }
        }
    }
}

@Composable
fun CodecSection(current: VideoCodec, onSelect: (VideoCodec) -> Unit) {
    ConfigSection(title = "Codec video") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodec.entries.forEach { codec ->
                FilterChip(selected = current == codec, onClick = { onSelect(codec) }, label = { Text(codec.label) })
            }
        }
    }
}

@Composable
fun AudioSection(current: AudioBitrate, onSelect: (AudioBitrate) -> Unit) {
    ConfigSection(title = "Bitrate audio") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioBitrate.entries.forEach { br ->
                FilterChip(selected = current == br, onClick = { onSelect(br) }, label = { Text(br.label) })
            }
        }
    }
}

@Composable
fun OutputModeSection(
    mode: OutputMode,
    folderPath: String,
    onModeChange: (OutputMode) -> Unit,
    onPickFolder: () -> Unit
) {
    ConfigSection(title = "Lưu file") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { onModeChange(m) }, label = { Text(m.label) })
            }
        }
        if (mode == OutputMode.NEW_FOLDER) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
                Text(if (folderPath.isBlank()) "Chọn thư mục lưu..." else folderPath)
            }
        }
    }
}

@Composable
fun EstimateRow(estimate: VideoEstimate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            estimate.video.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatBytes(estimate.originalBytes)} → ${if (estimate.estimatedBytes < 0) "?" else formatBytes(estimate.estimatedBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun ConfigSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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
    val inputData = workDataOf(
        VideoCompressionWork.KEY_VIDEO_IDS to ids,
        VideoCompressionWork.KEY_CONFIG_JSON to vm.buildConfigJson()
    )
    val request = OneTimeWorkRequestBuilder<VideoCompressionWork>()
        .setInputData(inputData)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    WorkManager.getInstance(context).enqueue(request)
    return request.id.toString()
}
```

- [ ] **Step 6: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD" | tail -20
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add ConfigViewModel, ConfigScreen with estimates"
```

---

## Task 12: ProgressScreen + ProgressViewModel

**Files:**
- Create: `ui/progress/ProgressViewModel.kt`
- Create: `ui/progress/ProgressScreen.kt`

- [ ] **Step 1: Create ProgressViewModel.kt**

```kotlin
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
    val isCancelled: Boolean = false
)

class ProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var workId: UUID? = null

    fun observeWork(workIdStr: String) {
        val id = UUID.fromString(workIdStr)
        workId = id
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val fileIndex = info.progress.getInt(VideoCompressionWork.KEY_PROGRESS_FILE_INDEX, 0)
                        val fileName = info.progress.getString(VideoCompressionWork.KEY_PROGRESS_FILE_NAME) ?: ""
                        val percent = info.progress.getInt(VideoCompressionWork.KEY_PROGRESS_PERCENT, 0)

                        _uiState.update { state ->
                            val list = state.fileProgresses.toMutableList()
                            while (list.size <= fileIndex) list.add(FileProgress("", 0))
                            list[fileIndex] = FileProgress(fileName, percent)
                            state.copy(fileProgresses = list, currentFileIndex = fileIndex)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> _uiState.update { it.copy(isFinished = true) }
                    WorkInfo.State.CANCELLED -> _uiState.update { it.copy(isCancelled = true, isFinished = true) }
                    WorkInfo.State.FAILED -> _uiState.update { it.copy(isFinished = true) }
                    else -> {}
                }
            }
        }
    }

    fun cancelWork() {
        workId?.let { workManager.cancelWorkById(it) }
    }
}
```

- [ ] **Step 2: Create ProgressScreen.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
        topBar = { @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Đang nén video") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.fileProgresses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.fileProgresses) { index, fp ->
                    FileProgressCard(
                        fileProgress = fp,
                        isActive = index == state.currentFileIndex && !state.isFinished,
                        isDone = index < state.currentFileIndex
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    when {
                        isDone -> "✓ Xong"
                        isActive -> "${fileProgress.percent}%"
                        else -> "Chờ..."
                    },
                    style = MaterialTheme.typography.labelMedium,
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
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add ProgressViewModel and ProgressScreen"
```

---

## Task 13: ResultScreen + ResultViewModel

**Files:**
- Create: `ui/result/ResultViewModel.kt`
- Create: `ui/result/ResultScreen.kt`

- [ ] **Step 1: Create ResultViewModel.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.result

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
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
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(UUID.fromString(workIdStr)).collect { info ->
                if (info == null) return@collect
                val raw = info.outputData.getString(VideoCompressionWork.KEY_RESULTS_JSON) ?: return@collect
                val results = raw.split("|").chunked(1).mapNotNull { parseResult(it.first()) }
                val totalSaved = results.sumOf { it.savedBytes }
                _uiState.update { ResultUiState(results, totalSaved, false) }
            }
        }
    }

    private fun parseResult(raw: String): FileResult? {
        val parts = raw.split("|")
        return when (parts.getOrNull(0)) {
            "ok" -> FileResult(
                name = parts.getOrElse(1) { "" },
                originalBytes = parts.getOrElse(2) { "0" }.toLongOrNull() ?: 0L,
                compressedBytes = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L,
                isSuccess = true
            )
            "fail" -> FileResult(
                name = parts.getOrElse(1) { "" },
                originalBytes = 0L, compressedBytes = 0L,
                isSuccess = false, error = parts.getOrElse(2) { "Lỗi không xác định" }
            )
            else -> null
        }
    }
}
```

- [ ] **Step 2: Create ResultScreen.kt**

```kotlin
package com.ntanhprt.videoresizer.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    workId: String,
    onProcessMore: () -> Unit,
    onDone: () -> Unit,
    vm: ResultViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    LaunchedEffect(workId) { vm.loadResults(workId) }

    Scaffold(topBar = { TopAppBar(title = { Text("Kết quả") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                if (state.totalSavedBytes > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Tổng tiết kiệm", style = MaterialTheme.typography.labelMedium)
                            Text(
                                formatBytes(state.totalSavedBytes),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results) { result ->
                        ResultRow(result)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onProcessMore, modifier = Modifier.weight(1f)) {
                        Text("Xử lý thêm")
                    }
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text("Xong")
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(result: FileResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (result.isSuccess) {
                    Text(
                        "${formatBytes(result.originalBytes)} → ${formatBytes(result.compressedBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Text(result.error, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (result.isSuccess) {
                Text(
                    "-${result.savingPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1_024.0)
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: add ResultViewModel and ResultScreen"
```

---

## Task 14: GitHub Actions CI

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.gitignore`

- [ ] **Step 1: Create .gitignore**

```
.gradle/
build/
*.apk
*.aab
.idea/
local.properties
*.jks
*.keystore
.superpowers/
```

- [ ] **Step 2: Create .github/workflows/build.yml**

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', '**/libs.versions.toml') }}
          restore-keys: ${{ runner.os }}-gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run unit tests
        run: ./gradlew :app:test --stacktrace

      - name: Build Debug APK
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-${{ github.run_number }}
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

- [ ] **Step 3: Commit and push**

```bash
git add .github/ .gitignore
git commit -m "ci: add GitHub Actions workflow to build and test APK"
git push -u origin main
```

- [ ] **Step 4: Verify GitHub Actions triggered**

Go to `https://github.com/ntanhprt/Resize_Video_4Android/actions` and confirm the workflow starts running.

---

## Task 15: Final compile + integration verification

- [ ] **Step 1: Full build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew :app:test 2>&1 | tail -20
```
Expected: All tests PASSED

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "chore: final integration — all screens and tests complete"
git push
```

---

## Self-Review Checklist

| Spec requirement | Covered in |
|---|---|
| Query videos sorted by size DESC | Task 3 (MediaStoreDataSource) |
| Multi-select | Task 9 (BrowseViewModel.toggleSelection) |
| Split-screen preview (tap=preview, checkbox=select, long-press=external, drag divider) | Task 10 (BrowseScreen) |
| ExoPlayer fullscreen button | Task 10 (VideoPlayerSection + ⛶ button) |
| Configure resolution / codec / audio bitrate | Task 11 (ConfigScreen) |
| Realtime size estimation | Task 4 + Task 11 |
| Choose overwrite or new folder | Task 11 (OutputModeSection) |
| Sequential background processing | Task 7 (VideoCompressionWork) |
| Progress screen with cancel | Task 12 |
| Result screen with savings | Task 13 |
| MediaCodec ưu tiên, FFmpeg fallback | Task 6 (HybridVideoProcessor) |
| Vietnamese UI | All screens use Vietnamese strings |
| GitHub Actions CI | Task 14 |
| Android 8+ (API 26) minSdk | Task 1 (build.gradle.kts) |
