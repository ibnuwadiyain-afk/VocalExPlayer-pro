package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {

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
        val numChannels: Short = 2
        val bitsPerSample: Short = 16
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * numChannels * bytesPerSample
        val blockAlign = (numChannels * bytesPerSample).toShort()
        val dataSize = numSamples * numChannels * bytesPerSample
        val totalSize = 36 + dataSize

        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())

            // fmt sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1) // AudioFormat 1 = PCM
            header.putShort(numChannels)
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign)
            header.putShort(bitsPerSample)

            // data sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Buffer for writing interleaved 16-bit PCM samples
            val chunkSize = 4096
            val buffer = ByteBuffer.allocate(chunkSize * numChannels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)

            var sampleIdx = 0
            while (sampleIdx < numSamples) {
                buffer.clear()
                val currentChunk = minOf(chunkSize, numSamples - sampleIdx)
                for (i in 0 until currentChunk) {
                    val idx = sampleIdx + i
                    val lSample = (leftChannel[idx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    val rSample = (rightChannel[idx].coerceIn(-1.0f, 1.0f) * 32767f).toInt().toShort()
                    buffer.putShort(lSample)
                    buffer.putShort(rSample)
                }
                fos.write(buffer.array(), 0, buffer.position())
                sampleIdx += currentChunk
            }
            fos.flush()
        }
    }
}
