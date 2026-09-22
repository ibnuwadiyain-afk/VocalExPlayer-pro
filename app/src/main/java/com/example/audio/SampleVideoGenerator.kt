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
     * immediately test the Spleeter 2-stem separation pipeline without manual file transfers.
     */
    fun getOrCreateSampleVideo(context: Context): File {
        val sampleFile = File(context.filesDir, "sample_vocal_track_v2.mp4")
        if (sampleFile.exists() && sampleFile.length() > 5000) {
            return sampleFile
        }

        // Try extracting pre-bundled clean demo video from assets first
        try {
            context.assets.open("samples/demo_sample.mp4").use { input ->
                java.io.FileOutputStream(sampleFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (sampleFile.exists() && sampleFile.length() > 5000) {
                Log.i(TAG, "Copied bundled demo video from assets: ${sampleFile.length()} bytes")
                return sampleFile
            }
        } catch (e: Exception) {
            Log.d(TAG, "Assets demo sample not found or failed, falling back to synthesis: ${e.message}")
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

            // Encode Audio (vocal lead in center, stereo instruments + bass panned)
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
                        
                        // 1. Vocal lead (Center panned, 440Hz / 554Hz notes with vibrato & formants)
                        val noteFreq = if ((t % 2.0) < 1.0) 440.0 else 554.37 // A4 -> C#5 melody
                        val vibrato = sin(2.0 * Math.PI * 5.0 * t) * 6.0
                        val vocalPhase = 2.0 * Math.PI * (noteFreq + vibrato) * t
                        val vocalFormant = sin(vocalPhase) * 0.45 +
                                sin(vocalPhase * 2.0) * 0.22 +
                                sin(vocalPhase * 3.0) * 0.10

                        // 2. Heavy sub-bass (65 Hz kick/bass - filtered out by vocal separation)
                        val bass = sin(2.0 * Math.PI * 65.0 * t) * 0.35

                        // 3. Stereo accompaniment synth chord (panned out-of-phase L vs R)
                        val chordL = sin(2.0 * Math.PI * 261.63 * t) * 0.25 // C4
                        val chordR = -sin(2.0 * Math.PI * 261.63 * t) * 0.25 // Inverted C4 (Side channel)

                        // 4. Percussion hi-hat click at 6500 Hz every 0.5s (filtered out)
                        val clickTime = t % 0.5
                        val hihat = if (clickTime < 0.04) {
                            sin(2.0 * Math.PI * 6500.0 * t) * (1.0 - clickTime / 0.04) * 0.2
                        } else {
                            0.0
                        }

                        val leftMix = (vocalFormant + bass + chordL + hihat).coerceIn(-1.0, 1.0)
                        val rightMix = (vocalFormant + bass + chordR + hihat).coerceIn(-1.0, 1.0)

                        pcmBytes.putShort((leftMix * 32767.0).toInt().toShort())
                        pcmBytes.putShort((rightMix * 32767.0).toInt().toShort())
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
