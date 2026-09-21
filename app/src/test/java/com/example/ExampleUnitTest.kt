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

    @Test
    fun testSpleeterSpectrogramProcessorRoundtrip() {
        val processor = com.example.onnx.SpleeterSpectrogramProcessor(
            nFft = 4096,
            hopLength = 1024,
            dimF = 1024,
            dimT = 512
        )

        // Generate test signal (440 Hz sine wave for 0.5s = 22050 samples)
        val sampleRate = 44100
        val numSamples = 22050
        val original = FloatArray(numSamples) { i ->
            kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / sampleRate).toFloat() * 0.8f
        }

        val stft = processor.computeStft(original)
        assertTrue(stft.numFrames > 0)
        assertTrue(stft.paddedFrames >= stft.numFrames)
        assertEquals(0, stft.paddedFrames % 512)

        val reconstructed = processor.computeIstft(stft.real, stft.imag, stft.numFrames, numSamples)
        assertEquals(numSamples, reconstructed.size)

        // Verify middle region (away from boundaries) has excellent reconstruction fidelity
        val testStart = 4096
        val testEnd = numSamples - 4096
        var maxDiff = 0f
        for (i in testStart until testEnd) {
            val diff = kotlin.math.abs(original[i] - reconstructed[i])
            if (diff > maxDiff) maxDiff = diff
        }

        assertTrue("Max reconstruction error should be < 0.05 but was $maxDiff", maxDiff < 0.05f)
    }

    @Test
    fun testSeparationEngineEnumAndMaskRatio() {
        val spleeterType = com.example.onnx.SeparationEngineType.fromId("deezer_spleeter")
        assertEquals(com.example.onnx.SeparationEngineType.DEEZER_SPLEETER, spleeterType)
        assertTrue(spleeterType.producesTwoStems)

        val uvrType = com.example.onnx.SeparationEngineType.fromId("uvr_mdxnet")
        assertEquals(com.example.onnx.SeparationEngineType.UVR_MDXNET, uvrType)
        assertFalse(uvrType.producesTwoStems)

        // Verify soft ratio masking logic: mask_vocal = Mag_v / (Mag_v + Mag_acc + eps)
        val vMag = 3.0f
        val accMag = 1.0f
        val eps = 1e-10f
        val vMask = vMag / (vMag + accMag + eps)
        val accMask = accMag / (vMag + accMag + eps)

        assertEquals(0.75f, vMask, 0.001f)
        assertEquals(0.25f, accMask, 0.001f)
        assertEquals(1.0f, vMask + accMask, 0.001f)
    }
}
