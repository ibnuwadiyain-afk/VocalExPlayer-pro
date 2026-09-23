package com.example.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Progressively and simultaneously merges separated vocal audio chunks with the original video track
 * in a decoupled background coroutine during neural audio separation.
 *
 * This ensures that when audio separation finishes, the video cache is already merged and ready
 * for instantaneous export without waiting time, while strictly preventing any slowdown or
 * blocking of the neural inference engine.
 */
class ProgressiveVideoMerger(
    private val context: Context,
    private val videoUriString: String,
    private val sampleRate: Int = 44100,
    private val channels: Int = 2,
    val outputCacheFile: File
) {
    companion object {
        private const val TAG = "ProgressiveVideoMerger"
        private const val TIMEOUT_USEC = 10_000L

        fun getCacheFileForVideo(context: Context, videoId: String): File {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            return File(cacheDir, "premerged_vocals_${videoId}.mp4")
        }

        fun isCacheReadyForVideo(context: Context, videoId: String): Boolean {
            val file = getCacheFileForVideo(context, videoId)
            return file.exists() && file.length() > 50_000L
        }
    }

    private class AudioChunk(
        val pcmBytes: ByteArray,
        val isEos: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunkChannel = Channel<AudioChunk>(capacity = 64)
    private var workerJob: Job? = null

    @Volatile
    var isCacheReady: Boolean = false
        private set

    @Volatile
    var hasVideoTrack: Boolean = false
        private set

    @Volatile
    private var isCancelled: Boolean = false

    private val tempOutputFile = File(outputCacheFile.parentFile, "${outputCacheFile.name}.tmp")

    fun start() {
        if (outputCacheFile.exists()) {
            outputCacheFile.delete()
        }
        if (tempOutputFile.exists()) {
            tempOutputFile.delete()
        }

        workerJob = scope.launch {
            // Set thread priority to background to never compete with ONNX neural inference
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Exception) {}

            runMergerLoop()
        }
    }

    /**
     * Non-blocking audio chunk consumer invoked directly by the separation engine.
     * Converts Float32 samples to 16-bit PCM and queues them for the background muxer.
     * Guaranteed never to block or slow down the caller.
     */
    fun feedSeparatedChunk(
        left: FloatArray,
        right: FloatArray,
        offset: Int,
        count: Int,
        isEos: Boolean
    ) {
        if (isCancelled) return

        if (count <= 0 && !isEos) return

        val pcmBytes: ByteArray = if (count > 0) {
            val bytes = ByteArray(count * channels * 2)
            // If left channel contains valid samples, convert them; otherwise bytes remain 0 (silence)
            if (left.isNotEmpty() && offset in left.indices) {
                var byteIdx = 0
                val hasRight = right.isNotEmpty() && channels == 2

                for (i in 0 until count) {
                    val srcIdx = offset + i
                    val sL = if (srcIdx in left.indices) {
                        (left[srcIdx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    } else {
                        0.toShort()
                    }
                    bytes[byteIdx++] = (sL.toInt() and 0xFF).toByte()
                    bytes[byteIdx++] = ((sL.toInt() shr 8) and 0xFF).toByte()

                    val sR = if (hasRight && srcIdx in right.indices) {
                        (right[srcIdx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    } else {
                        sL
                    }
                    bytes[byteIdx++] = (sR.toInt() and 0xFF).toByte()
                    bytes[byteIdx++] = ((sR.toInt() shr 8) and 0xFF).toByte()
                }
            }
            bytes
        } else {
            ByteArray(0)
        }

        // Send non-blocking. If buffer is saturated, drop non-critical or buffer up to capacity
        val chunk = AudioChunk(pcmBytes, isEos)
        val result = chunkChannel.trySend(chunk)
        if (!result.isSuccess && !isEos) {
            Log.w(TAG, "Progressive muxer channel full, chunk bypassed to protect realtime separation")
        }
    }

    /**
     * Awaits completion of the progressive merger up to timeoutMs (typically < 500ms since
     * muxing runs concurrently with separation).
     */
    suspend fun awaitFinalization(timeoutMs: Long = 3000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            workerJob?.join()
            isCacheReady
        } ?: isCacheReady
    }

    fun cancel() {
        isCancelled = true
        workerJob?.cancel()
        chunkChannel.close()
        try {
            if (tempOutputFile.exists()) tempOutputFile.delete()
        } catch (_: Exception) {}
    }

    private fun runMergerLoop() {
        val videoExtractor = MediaExtractor()
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            // 1. Inspect source video track
            setExtractorDataSource(context, videoExtractor, videoUriString)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    videoExtractor.selectTrack(i)
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.i(TAG, "Source media is audio-only. Progressive video muxing not required.")
                hasVideoTrack = false
                // Drain channel to allow caller to finish
                while (scope.isActive && !isCancelled) {
                    val chunk = chunkChannel.tryReceive().getOrNull()
                    if (chunk == null || chunk.isEos) break
                }
                return
            }

            hasVideoTrack = true

            // Read video orientation
            var rotation = 0
            val retriever = MediaMetadataRetriever()
            try {
                setRetrieverDataSource(context, retriever, videoUriString)
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                rotation = rotStr?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract video rotation: ${e.message}")
            } finally {
                retriever.release()
            }

            // 2. Configure AAC audio encoder
            val aacFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channels
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            // 3. Configure MediaMuxer
            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            var muxerAudioTrack = -1
            var muxerStarted = false

            val encoderBufferInfo = MediaCodec.BufferInfo()
            val videoBuffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val videoBufferInfo = MediaCodec.BufferInfo()

            var totalPcmBytesQueued = 0L
            val bytesPerSec = sampleRate * channels * 2
            var sawEosChunk = false
            var sawEncoderEos = false
            var sawVideoEos = false

            // Process incoming chunks in real-time
            while ((!sawEncoderEos || !sawVideoEos) && !isCancelled) {
                // Fetch next chunk if not finished
                if (!sawEosChunk) {
                    val chunk = chunkChannel.tryReceive().getOrNull()
                    if (chunk != null) {
                        if (chunk.isEos) {
                            sawEosChunk = true
                            // Queue EOS to audio encoder
                            val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                            if (inIdx >= 0) {
                                val pts = (totalPcmBytesQueued * 1_000_000L) / bytesPerSec
                                audioEncoder.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        } else if (chunk.pcmBytes.isNotEmpty()) {
                            // Feed PCM to encoder in slices if needed
                            var pcmOffset = 0
                            val pcmLen = chunk.pcmBytes.size

                            while (pcmOffset < pcmLen && !isCancelled) {
                                val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                                if (inIdx >= 0) {
                                    val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                                    inBuf.clear()
                                    val toWrite = minOf(pcmLen - pcmOffset, inBuf.remaining())
                                    inBuf.put(chunk.pcmBytes, pcmOffset, toWrite)
                                    val pts = (totalPcmBytesQueued * 1_000_000L) / bytesPerSec
                                    audioEncoder.queueInputBuffer(inIdx, 0, toWrite, pts, 0)
                                    pcmOffset += toWrite
                                    totalPcmBytesQueued += toWrite
                                } else {
                                    // Dequeue output to make space in encoder
                                    drainEncoderAndMux(
                                        audioEncoder = audioEncoder,
                                        encoderBufferInfo = encoderBufferInfo,
                                        muxer = muxer,
                                        muxerVideoTrack = muxerVideoTrack,
                                        getMuxerAudioTrack = { muxerAudioTrack },
                                        setMuxerAudioTrack = { muxerAudioTrack = it },
                                        isMuxerStarted = { muxerStarted },
                                        setMuxerStarted = { muxerStarted = it },
                                        videoExtractor = videoExtractor,
                                        videoBuffer = videoBuffer,
                                        videoBufferInfo = videoBufferInfo,
                                        onVideoEos = { sawVideoEos = true },
                                        onEncoderEos = { sawEncoderEos = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Drain output from audio encoder and interleave video frames
                drainEncoderAndMux(
                    audioEncoder = audioEncoder,
                    encoderBufferInfo = encoderBufferInfo,
                    muxer = muxer,
                    muxerVideoTrack = muxerVideoTrack,
                    getMuxerAudioTrack = { muxerAudioTrack },
                    setMuxerAudioTrack = { muxerAudioTrack = it },
                    isMuxerStarted = { muxerStarted },
                    setMuxerStarted = { muxerStarted = it },
                    videoExtractor = videoExtractor,
                    videoBuffer = videoBuffer,
                    videoBufferInfo = videoBufferInfo,
                    onVideoEos = { sawVideoEos = true },
                    onEncoderEos = { sawEncoderEos = true }
                )

                if (sawEosChunk && !sawEncoderEos) {
                    // Pump encoder EOS until completion
                    val inIdx = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                    if (inIdx >= 0) {
                        val pts = (totalPcmBytesQueued * 1_000_000L) / bytesPerSec
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                // If audio finished, drain remaining video frames
                if (sawEncoderEos && !sawVideoEos && muxerStarted) {
                    while (!sawVideoEos && !isCancelled) {
                        videoBuffer.clear()
                        val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                        if (sampleSize < 0) {
                            sawVideoEos = true
                            break
                        }
                        videoBufferInfo.offset = 0
                        videoBufferInfo.size = sampleSize
                        videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                        videoBufferInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                        videoExtractor.advance()
                    }
                }

                // If channel is idle and EOS hasn't arrived, sleep briefly
                if (!sawEosChunk) {
                    Thread.sleep(10)
                }
            }

            // Finalize muxer
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Muxer stop exception: ${e.message}")
                }
            }

            // Move temp file to finalized cache file
            if (!isCancelled && tempOutputFile.exists() && tempOutputFile.length() > 50_000L) {
                if (outputCacheFile.exists()) outputCacheFile.delete()
                val renamed = tempOutputFile.renameTo(outputCacheFile)
                if (renamed && outputCacheFile.exists()) {
                    isCacheReady = true
                    Log.i(TAG, "⚡ Pre-merged video cache successfully ready! File: ${outputCacheFile.absolutePath} (${outputCacheFile.length() / 1024L} KB)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Progressive video merging encountered non-fatal error: ${e.message}")
            try {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            } catch (_: Exception) {}
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioEncoder?.stop(); audioEncoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private inline fun drainEncoderAndMux(
        audioEncoder: MediaCodec,
        encoderBufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        muxerVideoTrack: Int,
        getMuxerAudioTrack: () -> Int,
        setMuxerAudioTrack: (Int) -> Unit,
        isMuxerStarted: () -> Boolean,
        setMuxerStarted: (Boolean) -> Unit,
        videoExtractor: MediaExtractor,
        videoBuffer: ByteBuffer,
        videoBufferInfo: MediaCodec.BufferInfo,
        onVideoEos: () -> Unit,
        onEncoderEos: () -> Unit
    ) {
        val outIdx = audioEncoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_USEC)
        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat = audioEncoder.outputFormat
            setMuxerAudioTrack(muxer.addTrack(newFormat))
            muxer.start()
            setMuxerStarted(true)
        } else if (outIdx >= 0) {
            val muxAudioTrack = getMuxerAudioTrack()
            val muxerStarted = isMuxerStarted()

            if (muxerStarted && muxAudioTrack != -1) {
                val currentAudioPts = encoderBufferInfo.presentationTimeUs

                // Interleave video frames up to current audio timestamp (+100ms tolerance)
                while (videoExtractor.sampleTime in 0L..(currentAudioPts + 100_000L)) {
                    videoBuffer.clear()
                    val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                    if (sampleSize < 0) {
                        onVideoEos()
                        break
                    }
                    videoBufferInfo.offset = 0
                    videoBufferInfo.size = sampleSize
                    videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                    videoBufferInfo.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoBufferInfo)
                    videoExtractor.advance()
                }

                // Write encoded audio sample
                val encodedBuf = audioEncoder.getOutputBuffer(outIdx)!!
                if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    encoderBufferInfo.size = 0
                }
                if (encoderBufferInfo.size > 0) {
                    encodedBuf.position(encoderBufferInfo.offset)
                    encodedBuf.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                    muxer.writeSampleData(muxAudioTrack, encodedBuf, encoderBufferInfo)
                }
            }

            audioEncoder.releaseOutputBuffer(outIdx, false)
            if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                onEncoderEos()
            }
        }
    }

    private fun setExtractorDataSource(context: Context, extractor: MediaExtractor, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            extractor.setDataSource(context, uri, null)
        } else {
            val file = if (uriString.startsWith("file://")) File(uri.path ?: uriString) else File(uriString)
            if (file.exists()) {
                extractor.setDataSource(file.absolutePath)
            } else {
                extractor.setDataSource(context, uri, null)
            }
        }
    }

    private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            retriever.setDataSource(context, uri)
        } else {
            val file = if (uriString.startsWith("file://")) File(uri.path ?: uriString) else File(uriString)
            if (file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }
        }
    }
}
