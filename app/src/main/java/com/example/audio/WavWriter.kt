package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {

    /**
     * Creates a streaming WAV writer that streams audio chunks directly to disk.
     * Prevents heap OutOfMemoryError on long audio files.
     */
    fun createStreamingWriter(
        outputFile: File,
        sampleRate: Int = 44100,
        numChannels: Short = 2
    ): StreamingWavWriter {
        return StreamingWavWriter(outputFile, sampleRate, numChannels)
    }

    /**
     * Writes 2-channel (stereo) Float32 audio samples [-1.0f, 1.0f] into a 16-bit PCM WAV file.
     * @param leftChannel Float array for left channel samples
     * @param rightChannel Float array for right channel samples
     * @param sampleRate Sampling rate (default 44100 Hz)
     * @param outputFile Target file to write WAV into
     */
    fun writeWavFile(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int = 44100,
        outputFile: File
    ) {
        val numSamples = minOf(leftChannel.size, rightChannel.size)
        createStreamingWriter(outputFile, sampleRate, 2).use { writer ->
            writer.writeStereoChunk(leftChannel, rightChannel, 0, numSamples)
        }
    }
}

/**
 * Streams audio samples directly to disk as a standard 16-bit PCM WAV file.
 * Uses a fixed 64KB intermediate buffer so memory usage remains strictly O(1)
 * regardless of file duration (from seconds to hours).
 */
class StreamingWavWriter(
    val outputFile: File,
    val sampleRate: Int = 44100,
    val numChannels: Short = 2
) : AutoCloseable {

    private val bitsPerSample: Short = 16
    private val bytesPerSample = bitsPerSample / 8
    private val byteRate = sampleRate * numChannels * bytesPerSample
    private val blockAlign = (numChannels * bytesPerSample).toShort()

    private val fos: FileOutputStream
    private var totalFramesWritten: Long = 0L
    private val buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
    private val zerosBuffer = ByteArray(64 * 1024)

    init {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()
        fos = FileOutputStream(outputFile)

        // Write placeholder 44-byte WAV header (finalized in close())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(0) // placeholder totalSize
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(numChannels)
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample)
        header.put("data".toByteArray())
        header.putInt(0) // placeholder dataSize
        fos.write(header.array())
    }

    fun writeStereoChunk(left: FloatArray, right: FloatArray, offset: Int, count: Int) {
        if (count <= 0) return
        var i = 0
        while (i < count) {
            if (buffer.remaining() < 4) {
                fos.write(buffer.array(), 0, buffer.position())
                buffer.clear()
            }
            val framesToBuffer = minOf(count - i, buffer.remaining() / 4)
            for (f in 0 until framesToBuffer) {
                val idx = offset + i + f
                val lSample = (left[idx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                val rSample = (right[idx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                buffer.putShort(lSample)
                buffer.putShort(rSample)
            }
            i += framesToBuffer
        }
        totalFramesWritten += count
    }

    fun writeSilence(count: Int) {
        if (count <= 0) return
        if (buffer.position() > 0) {
            fos.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        }
        val bytesPerFrame = numChannels * bytesPerSample
        var remainingBytes = count.toLong() * bytesPerFrame
        while (remainingBytes > 0) {
            val toWrite = minOf(remainingBytes, zerosBuffer.size.toLong()).toInt()
            fos.write(zerosBuffer, 0, toWrite)
            remainingBytes -= toWrite
        }
        totalFramesWritten += count
    }

    /**
     * Flushes internal memory buffer to underlying file output stream.
     * Enables concurrent streaming readers to immediately read up to [framesWritten].
     */
    fun flush() {
        if (buffer.position() > 0) {
            fos.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        }
        fos.flush()
    }

    val framesWritten: Long
        get() = totalFramesWritten

    override fun close() {
        try {
            if (buffer.position() > 0) {
                fos.write(buffer.array(), 0, buffer.position())
                buffer.clear()
            }
            fos.flush()
            fos.close()

            // Seek back to header to write accurate dataSize and totalSize
            RandomAccessFile(outputFile, "rw").use { raf ->
                val dataBytes = totalFramesWritten * numChannels * bytesPerSample
                val clampedDataSize = dataBytes.coerceAtMost(0x7FFFFFFFL).toInt()
                val totalSize = (36L + dataBytes).coerceAtMost(0x7FFFFFFFL).toInt()

                raf.seek(4)
                val totalSizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalSize)
                raf.write(totalSizeBuf.array())

                raf.seek(40)
                val dataSizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(clampedDataSize)
                raf.write(dataSizeBuf.array())
            }
        } catch (e: Exception) {
            android.util.Log.w("StreamingWavWriter", "Error finalizing WAV header: ${e.message}")
        }
    }
}
