package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin

object SampleVideoGenerator {
    private const val TAG = "SampleVideoGenerator"

    /**
     * Creates a lightweight sample MP4 video with a musical audio tone so the emulator user can
     * immediately test the UVR MDX-Net ONNX separation pipeline without manual file transfers.
     */
    fun getOrCreateSampleVideo(context: Context): File {
        val sampleFile = File(context.filesDir, "sample_vocal_track.mp4")
        if (sampleFile.exists() && sampleFile.length() > 5000) {
            return sampleFile
        }

        try {
            val width = 320
            val height = 240
            val frameRate = 24
            val durationSec = 4
            val totalFrames = frameRate * durationSec
            val sampleRate = 44100

            val muxer = MediaMuxer(sampleFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Video Track Format
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 250_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoCodec.start()

            // Audio Track Format (AAC)
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }
            val audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec.start()

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            // Generate YUV frame (dark teal gradient)
            val yuvSize = width * height * 3 / 2
            val frameBytes = ByteArray(yuvSize) { i ->
                if (i < width * height) (40 + (i % 60)).toByte() else 128.toByte()
            }

            // Encode Video
            for (frame in 0 until totalFrames) {
                val inputIndex = videoCodec.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = videoCodec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(frameBytes)
                    val pts = frame * 1_000_000L / frameRate
                    val flags = if (frame == totalFrames - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    videoCodec.queueInputBuffer(inputIndex, 0, yuvSize, pts, flags)
                }
            }

            // Encode Audio (harmonics and vocal-like sine chords)
            val totalAudioSamples = sampleRate * durationSec
            val audioChunkSamples = 1024
            var audioSampleIndex = 0

            while (audioSampleIndex < totalAudioSamples) {
                val inputIndex = audioCodec.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = audioCodec.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    val samplesToWrite = minOf(audioChunkSamples, totalAudioSamples - audioSampleIndex)
                    val pcmBytes = ByteBuffer.allocate(samplesToWrite * 4) // stereo 16-bit
                    for (i in 0 until samplesToWrite) {
                        val t = (audioSampleIndex + i).toDouble() / sampleRate
                        // Vocal formant approximation + music backing chord
                        val vocalMelody = sin(2.0 * Math.PI * 440.0 * t) * 0.4 + sin(2.0 * Math.PI * 880.0 * t) * 0.2
                        val bass = sin(2.0 * Math.PI * 110.0 * t) * 0.3
                        val sampleVal = ((vocalMelody + bass).coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                        pcmBytes.putShort(sampleVal)
                        pcmBytes.putShort(sampleVal)
                    }
                    val pts = audioSampleIndex * 1_000_000L / sampleRate
                    val isLast = (audioSampleIndex + samplesToWrite >= totalAudioSamples)
                    val flags = if (isLast) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    inputBuffer?.put(pcmBytes.array())
                    audioCodec.queueInputBuffer(inputIndex, 0, samplesToWrite * 4, pts, flags)
                    audioSampleIndex += samplesToWrite
                }
            }

            // Drain Encoders and write to Muxer
            var videoDone = false
            var audioDone = false

            while (!videoDone || !audioDone) {
                if (!videoDone) {
                    val outIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, 5000L)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        videoTrackIndex = muxer.addTrack(videoCodec.outputFormat)
                        if (audioTrackIndex != -1 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (outIndex >= 0) {
                        val outBuffer = videoCodec.getOutputBuffer(outIndex)
                        if (muxerStarted && outBuffer != null && videoBufferInfo.size > 0) {
                            muxer.writeSampleData(videoTrackIndex, outBuffer, videoBufferInfo)
                        }
                        videoCodec.releaseOutputBuffer(outIndex, false)
                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            videoDone = true
                        }
                    }
                }

                if (!audioDone) {
                    val outIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, 5000L)
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioTrackIndex = muxer.addTrack(audioCodec.outputFormat)
                        if (videoTrackIndex != -1 && !muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (outIndex >= 0) {
                        val outBuffer = audioCodec.getOutputBuffer(outIndex)
                        if (muxerStarted && outBuffer != null && audioBufferInfo.size > 0) {
                            muxer.writeSampleData(audioTrackIndex, outBuffer, audioBufferInfo)
                        }
                        audioCodec.releaseOutputBuffer(outIndex, false)
                        if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            audioDone = true
                        }
                    }
                }
            }

            videoCodec.stop(); videoCodec.release()
            audioCodec.stop(); audioCodec.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            Log.i(TAG, "Successfully generated sample video at: ${sampleFile.absolutePath}")
            return sampleFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample MP4", e)
            return sampleFile
        }
    }
}
