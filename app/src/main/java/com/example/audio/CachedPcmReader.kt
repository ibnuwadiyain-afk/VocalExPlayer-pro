package com.example.audio

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Memory-safe reader for disk-backed 16-bit PCM stereo audio (WAV or raw PCM).
 * Never loads full multi-megabyte audio files into heap memory.
 * Reads small sample slices on demand via RandomAccessFile.seek() with a pre-allocated small buffer.
 */
class CachedPcmReader(
    val file: File,
    val headerOffset: Long = 44L // Standard 44-byte WAV header
) : AutoCloseable {

    companion object {
        private const val TAG = "CachedPcmReader"
        private const val BUFFER_CAPACITY_BYTES = 16 * 1024 // 16 KB pre-allocated buffer
    }

    private var raf: RandomAccessFile? = null
    private val buffer = ByteBuffer.allocate(BUFFER_CAPACITY_BYTES).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Reads stereo Float32 samples [-1.0f, 1.0f] into destL and destR.
     * @param startFrame The stereo frame index to begin reading from.
     * @param frameCount Number of stereo frames (2 samples: L, R) to read.
     * @param destL Destination array for left channel samples.
     * @param destR Destination array for right channel samples.
     * @param destOffset Offset into destL and destR.
     * @return The number of valid stereo frames actually read from disk.
     */
    @Synchronized
    fun readStereoFrames(
        startFrame: Long,
        frameCount: Int,
        destL: FloatArray,
        destR: FloatArray,
        destOffset: Int = 0
    ): Int {
        if (frameCount <= 0 || !file.exists()) return 0
        val byteOffset = headerOffset + (startFrame * 4L)
        val fileLen = file.length()
        if (byteOffset >= fileLen) return 0

        val maxAvailableFrames = ((fileLen - byteOffset) / 4L).coerceAtLeast(0L).toInt()
        val framesToRead = minOf(frameCount, maxAvailableFrames)
        if (framesToRead <= 0) return 0

        try {
            if (raf == null) {
                raf = RandomAccessFile(file, "r")
            }
            val curRaf = raf ?: return 0

            curRaf.seek(byteOffset)
            var framesLoaded = 0
            while (framesLoaded < framesToRead) {
                val batchFrames = minOf(framesToRead - framesLoaded, buffer.capacity() / 4)
                val bytesNeeded = batchFrames * 4
                buffer.clear()
                val bytesRead = curRaf.read(buffer.array(), 0, bytesNeeded)
                if (bytesRead <= 0) break

                val validBatchFrames = bytesRead / 4
                buffer.position(0)
                buffer.limit(bytesRead)
                val sBuf = buffer.asShortBuffer()
                for (f in 0 until validBatchFrames) {
                    val outIdx = destOffset + framesLoaded + f
                    if (outIdx < destL.size && outIdx < destR.size) {
                        destL[outIdx] = sBuf.get() / 32768.0f
                        destR[outIdx] = sBuf.get() / 32768.0f
                    } else {
                        sBuf.get()
                        sBuf.get()
                    }
                }
                framesLoaded += validBatchFrames
            }
            return framesLoaded
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached PCM at frame $startFrame: ${e.message}")
            return 0
        }
    }

    /**
     * Checks if the file on disk currently has frames available up to the given endFrame.
     */
    fun hasFramesAvailable(endFrame: Long): Boolean {
        if (!file.exists()) return false
        val requiredBytes = headerOffset + (endFrame * 4L)
        return file.length() >= requiredBytes
    }

    override fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {}
        raf = null
    }
}
