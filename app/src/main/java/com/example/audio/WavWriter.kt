package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, streaming 16-bit PCM WAV writer.
 * Writes directly to disk in chunks to avoid any large in-memory heap allocations.
 */
class WavWriter(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val numChannels: Int = 2
) : AutoCloseable {

    private val fos = FileOutputStream(outputFile).buffered(64 * 1024)
    private var totalBytesWritten = 0L

    init {
        // Write placeholder 44-byte WAV header; updated on close()
        val emptyHeader = ByteArray(44)
        fos.write(emptyHeader)
    }

    /**
     * Write raw 16-bit Little-Endian PCM byte chunk
     */
    fun writePcmBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (length > 0) {
            fos.write(bytes, offset, length)
            totalBytesWritten += length
        }
    }

    /**
     * Write ShortArray chunk (converts directly to Little-Endian bytes)
     */
    fun writePcmShorts(shorts: ShortArray, offset: Int = 0, length: Int = shorts.size) {
        if (length <= 0) return
        val byteBuf = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in offset until (offset + length)) {
            byteBuf.putShort(shorts[i])
        }
        val bytes = byteBuf.array()
        fos.write(bytes, 0, bytes.size)
        totalBytesWritten += bytes.size
    }

    /**
     * Write FloatArray chunk in [-1.0f, 1.0f] range, normalized and clamped to 16-bit PCM
     */
    fun writeNormalizedFloats(floats: FloatArray, offset: Int = 0, length: Int = floats.size) {
        if (length <= 0) return
        val byteBuf = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in offset until (offset + length)) {
            val sample = floats[i].coerceIn(-1.0f, 1.0f)
            val shortVal = (sample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            byteBuf.putShort(shortVal)
        }
        val bytes = byteBuf.array()
        fos.write(bytes, 0, bytes.size)
        totalBytesWritten += bytes.size
    }

    override fun close() {
        fos.flush()
        fos.close()
        // Seek back and rewrite standard RIFF/WAVE header with exact byte sizes
        updateWavHeader(outputFile, totalBytesWritten, sampleRate, numChannels)
    }

    companion object {
        fun updateWavHeader(file: File, pcmDataLength: Long, sampleRate: Int, numChannels: Int) {
            val totalDataLen = pcmDataLength + 36
            val byteRate = sampleRate * numChannels * 2
            val blockAlign = numChannels * 2

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                val header = ByteArray(44)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

                // RIFF chunk descriptor
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                bb.position(4)
                bb.putInt((totalDataLen and 0xFFFFFFFFL).toInt())
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()

                // "fmt " sub-chunk
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                bb.position(16)
                bb.putInt(16) // SubChunk1Size for PCM
                bb.putShort(1) // AudioFormat: 1 = PCM
                bb.putShort(numChannels.toShort())
                bb.putInt(sampleRate)
                bb.putInt(byteRate)
                bb.putShort(blockAlign.toShort())
                bb.putShort(16) // BitsPerSample: 16-bit

                // "data" sub-chunk
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                bb.position(40)
                bb.putInt((pcmDataLength and 0xFFFFFFFFL).toInt())

                raf.write(header)
            }
        }

        fun writeCompleteWav(
            outputFile: File,
            samplesLeft: FloatArray,
            samplesRight: FloatArray,
            sampleRate: Int = 44100
        ) {
            val length = minOf(samplesLeft.size, samplesRight.size)
            WavWriter(outputFile, sampleRate, 2).use { writer ->
                val bufferSize = 8192
                val interleaved = FloatArray(bufferSize * 2)
                var i = 0
                while (i < length) {
                    val count = minOf(bufferSize, length - i)
                    for (c in 0 until count) {
                        interleaved[c * 2] = samplesLeft[i + c]
                        interleaved[c * 2 + 1] = samplesRight[i + c]
                    }
                    writer.writeNormalizedFloats(interleaved, 0, count * 2)
                    i += count
                }
            }
        }
    }
}
