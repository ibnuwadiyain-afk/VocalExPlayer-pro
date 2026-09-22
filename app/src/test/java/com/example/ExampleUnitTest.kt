package com.example

import com.example.media.MediaResolutionOption
import com.example.media.SocialMediaVideoProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDirectVideoResolutionProbing() = runBlocking {
        val testUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val options = SocialMediaVideoProcessor.fetchAvailableResolutions(testUrl)

        assertNotNull(options)
        assertTrue(options.isNotEmpty())

        // Should include recommended default and audio only option
        val hasRecommended = options.any { it.isRecommended }
        val hasAudio = options.any { it.id == "audio" }

        assertTrue(hasRecommended)
        assertTrue(hasAudio)
    }

    @Test
    fun testResolutionOptionSorting() {
        val options = listOf(
            MediaResolutionOption(id = "360p", label = "360p (Fast)", height = 360, format = "mp4"),
            MediaResolutionOption(id = "1080p", label = "1080p (Full HD)", height = 1080, format = "mp4"),
            MediaResolutionOption(id = "audio", label = "Audio Only", height = 0, format = "mp3"),
            MediaResolutionOption(id = "720p", label = "720p (HD)", height = 720, format = "mp4", isRecommended = true)
        )

        val bestVideo = options.filter { it.height > 0 }.maxByOrNull { it.height }
        assertEquals(1080, bestVideo?.height)

        val audioOption = options.firstOrNull { it.id == "audio" }
        assertNotNull(audioOption)
        assertEquals(0, audioOption?.height)
    }

    @Test
    fun testStreamingWavWriterAndChunkReading() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_audio_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val wavFile = java.io.File(tempDir, "test_out.wav")

        try {
            val sampleRate = 44100
            val totalFrames = 1000
            val left = FloatArray(totalFrames) { i -> i / 1000.0f }
            val right = FloatArray(totalFrames) { i -> -i / 1000.0f }

            com.example.audio.WavWriter.createStreamingWriter(wavFile, sampleRate, 2).use { writer ->
                writer.writeStereoChunk(left, right, 0, 500)
                writer.writeSilence(200)
                writer.writeStereoChunk(left, right, 500, 300)
            }

            assertTrue(wavFile.exists())
            // 44-byte header + 1000 frames * 4 bytes/frame = 4044 bytes
            assertEquals(4044L, wavFile.length())

            // Test DecodedAudio reading from memory and chunks
            val decoded = com.example.audio.DecodedAudio(
                leftChannel = left,
                rightChannel = right,
                sampleRate = sampleRate,
                durationMs = 1000L
            )

            val destL = FloatArray(100)
            val destR = FloatArray(100)
            val readCount = decoded.readStereoChunk(100L, 100, destL, destR, 0)
            assertEquals(100, readCount)
            assertEquals(0.10f, destL[0], 0.01f)
            assertEquals(-0.10f, destR[0], 0.01f)

            decoded.close()
        } finally {
            wavFile.delete()
            tempDir.delete()
        }
    }
}
