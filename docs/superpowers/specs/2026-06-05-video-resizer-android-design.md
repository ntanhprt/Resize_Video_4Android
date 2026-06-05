# Video Resizer for Android — Design Spec
**Date:** 2026-06-05
**Status:** Approved

## Overview

Android app (API 26+, tiếng Việt) để tìm, xem trước, chọn và nén hàng loạt video nhằm giải phóng bộ nhớ. Hỗ trợ resize độ phân giải, đổi codec (H.265/H.264/VP9), nén audio, ước tính dung lượng trước/sau, ghi đè hoặc lưu thư mục mới.

---

## Architecture

**Single Activity + Jetpack Compose** + Navigation Compose.

```
BrowseScreen → ConfigScreen → ProgressScreen → ResultScreen
```

### Layers

```
UI Layer         Compose Screens + ViewModels (StateFlow/SharedFlow)
Domain Layer     VideoRepository, EstimationEngine
Data Layer       MediaStoreDataSource, VideoProcessor (interface)
                 ├── MediaCodecProcessor   (hardware, ưu tiên)
                 └── FFmpegProcessor       (fallback qua FFmpegKit)
Background       WorkManager – VideoCompressionWork (CoroutineWorker)
```

---

## Màn hình 1: BrowseScreen

### Data source
- Query `MediaStore.Video.Media` với projection: `_ID, DISPLAY_NAME, SIZE, DURATION, RESOLUTION, MIME_TYPE`
- Sort: `SIZE DESC`
- Permission: `READ_MEDIA_VIDEO` (API 33+) / `READ_EXTERNAL_STORAGE` (API 26-32)

### UI Layout — Split Screen
- Nửa trên (default 50%): `VideoPlayerView` dùng ExoPlayer
- Divider: kéo được, giới hạn 30%-70%
- Nửa dưới: `LazyColumn` danh sách video

### Item interactions
| Gesture | Action |
|---------|--------|
| Tap item row | Load video vào player (preview) |
| Tap checkbox | Toggle chọn/bỏ chọn để nén |
| Long-press | `Intent.ACTION_VIEW` → mở bằng app video ngoài |
| Nút ⛶ (top-right player) | ExoPlayer fullscreen, back để thu lại |
| Kéo divider | Thay đổi tỉ lệ split (30%-70%) |

### Bottom bar
- Hiển thị: "Đã chọn: N file · Tổng: X GB"
- Nút "Cấu hình nén ›" — enable khi ≥1 file được chọn

---

## Màn hình 2: ConfigScreen

Config áp dụng cho toàn bộ batch.

| Nhóm | Tùy chọn | Default |
|------|-----------|---------|
| Độ phân giải | 360p / 480p / 720p / 1080p / Giữ nguyên | 720p |
| Codec video | H.265 (HEVC) / H.264 / VP9 | H.265 |
| Bitrate audio | 32 / 64 / 128 kbps | 64 kbps |
| Lưu file | Ghi đè gốc / Thư mục mới (folder picker) | Thư mục mới |

### Ước tính dung lượng (realtime)
- Tính khi user thay đổi bất kỳ setting
- Công thức: `est_size = (video_bitrate_kbps + audio_bitrate_kbps) × duration_sec / 8 / 1024` (MB)
- Video bitrate ước tính theo resolution target: 360p≈500kbps, 480p≈1000kbps, 720p≈2500kbps, 1080p≈5000kbps; H.265 × 0.5
- Hiển thị từng file: `4.2 GB → ~1.1 GB`, và tổng tiết kiệm

---

## Màn hình 3: ProgressScreen

### WorkManager
- `VideoCompressionWork` extends `CoroutineWorker`
- Chạy tuần tự từng file trong coroutine
- `setProgress(workDataOf("file" to name, "percent" to n, "fileIndex" to i))`
- UI observe `WorkManager.getWorkInfoByIdLiveData()` → convert to StateFlow

### UI
- File đang xử lý: tên + thanh progress % + dung lượng ước tính
- File chờ: mờ, hiển thị "Chờ..."
- File xong: ✓ + dung lượng thực tế
- Thời gian còn lại (ước tính dựa trên tốc độ encode hiện tại)
- Nút "Dừng lại" → `WorkManager.cancelWorkById()`

### Hybrid processor logic
```kotlin
suspend fun compress(input: VideoFile, config: Config): Result {
    return try {
        MediaCodecProcessor.compress(input, config)
    } catch (e: UnsupportedCodecException) {
        FFmpegProcessor.compress(input, config)
    }
}
```

---

## Màn hình 4: ResultScreen

- Danh sách từng file: tên, dung lượng trước → sau, % tiết kiệm
- Tổng tiết kiệm highlight (màu xanh)
- Nút "Xử lý thêm" (navigate back to Browse, giữ selection)
- Nút "Xong" (finish Activity)

---

## GitHub Actions CI

File: `.github/workflows/build.yml`

```yaml
trigger: push to main, pull_request to main
steps:
  - checkout
  - setup-java JDK 17 (temurin)
  - cache gradle
  - ./gradlew assembleDebug
  - upload-artifact: app-debug.apk
```

Release build (manual trigger): `./gradlew assembleRelease` với signing config từ GitHub Secrets.

---

## Dependencies chính

| Library | Mục đích |
|---------|----------|
| `androidx.compose.*` BOM 2024.x | UI |
| `androidx.navigation:navigation-compose` | Navigation |
| `androidx.media3:media3-exoplayer` | Video player |
| `androidx.work:work-runtime-ktx` | Background processing |
| `com.arthenica:ffmpeg-kit-android` | FFmpeg fallback |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel |

---

## Permissions (AndroidManifest)

```xml
READ_MEDIA_VIDEO          (API 33+)
READ_EXTERNAL_STORAGE     (API 26-32, maxSdkVersion=32)
WRITE_EXTERNAL_STORAGE    (API 26-28, maxSdkVersion=28)
FOREGROUND_SERVICE        (WorkManager notification)
```

---

## Out of scope (v1)

- Trim video (cắt thời gian)
- Per-file config khác nhau
- Upload/share sau khi nén
- Batch undo (restore file gốc sau ghi đè)
