package com.ntanhprt.videoresizer.domain.processor

import android.content.Context
import android.media.*
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

        val codecMime = when (config.videoCodec) {
            VideoCodec.H264 -> "video/avc"
            VideoCodec.H265 -> "video/hevc"
            VideoCodec.VP9  -> "video/x-vnd.on2.vp9"
        }

        findEncoderForMime(codecMime)
            ?: throw UnsupportedCodecException("No encoder for $codecMime on this device")

        try {
            transcode(input, config, outputPath, codecMime, onProgress)
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

    private fun findEncoderForMime(mimeType: String): MediaCodecInfo? =
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.contains(mimeType)
        }

    private fun transcode(
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
                val w = (srcWidth * scale).toInt().let { if (it % 2 != 0) it + 1 else it }
                w to targetH
            }
        }

        val bitrate = if (config.targetResolution == TargetResolution.ORIGINAL) {
            try { inputVideoFormat.getInteger(MediaFormat.KEY_BIT_RATE) } catch (_: Exception) { 2_000_000 }
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

        if (audioTrackIndex != null) {
            muxerAudioTrack = muxer.addTrack(extractor.getTrackFormat(audioTrackIndex))
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        var decoderDone = false
        var encoderDone = false
        val durationUs = input.durationMs * 1000L

        extractor.selectTrack(videoTrackIndex)

        while (!encoderDone) {
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
                        if (durationUs > 0) {
                            val pct = ((extractor.sampleTime.toDouble() / durationUs) * 50).toInt().coerceIn(0, 50)
                            onProgress(pct)
                        }
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outIdx >= 0) {
                decoder.releaseOutputBuffer(outIdx, bufferInfo.size > 0)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    encoder.signalEndOfInputStream()
                }
            }

            val encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Can fire multiple times — only start muxer once
                    if (!muxerStarted) {
                        muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                encOutIdx >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && muxerStarted) {
                        val buf = encoder.getOutputBuffer(encOutIdx)!!
                        muxer.writeSampleData(muxerVideoTrack, buf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (durationUs > 0) {
                        val pct = 50 + ((bufferInfo.presentationTimeUs.toDouble() / durationUs) * 50).toInt().coerceIn(0, 50)
                        onProgress(pct)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) encoderDone = true
                }
            }
        }

        if (audioTrackIndex != null && muxerStarted) {
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(context, input.uri, null)
            audioExtractor.selectTrack(audioTrackIndex)
            val audioBuf = ByteBuffer.allocate(512 * 1024)
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
