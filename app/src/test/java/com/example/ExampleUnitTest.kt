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
    fun testWhisperTokenizerAndSrtGeneration() {
        // Test Whisper Tokenizer
        assertTrue(com.example.whisper.WhisperTokenizer.isTimestampToken(50364)) // 0.00s
        assertEquals(0.0f, com.example.whisper.WhisperTokenizer.timestampTokenToSeconds(50364), 0.001f)
        assertEquals(1.0f, com.example.whisper.WhisperTokenizer.timestampTokenToSeconds(50364 + 50), 0.001f) // 50 * 0.02 = 1.0s

        // Test SRT Time Formatting
        assertEquals("00:00:01,200", com.example.whisper.SrtGenerator.formatSrtTime(1200L))
        assertEquals("00:01:05,050", com.example.whisper.SrtGenerator.formatSrtTime(65050L))
        assertEquals("01:00:00,000", com.example.whisper.SrtGenerator.formatSrtTime(3600000L))

        // Test Arabic text preservation in SRT
        val arabicSegments = listOf(
            com.example.whisper.TranscriptionSegment(1, 1000L, 3500L, "مرحبا بكم في هذا الفيديو"),
            com.example.whisper.TranscriptionSegment(2, 3600L, 6000L, "هذا اختبار للترجمة التلقائية")
        )
        val srtContent = com.example.whisper.SrtGenerator.generateSrtContent(arabicSegments)
        assertTrue(srtContent.contains("00:00:01,000 --> 00:00:03,500"))
        assertTrue(srtContent.contains("مرحبا بكم في هذا الفيديو"))
        assertTrue(srtContent.contains("00:00:03,600 --> 00:00:06,000"))
        assertTrue(srtContent.contains("هذا اختبار للترجمة التلقائية"))

        // Test overlap prevention in normalizeSegments
        val overlappingSegments = listOf(
            com.example.whisper.TranscriptionSegment(1, 1000L, 4000L, "First line"),
            com.example.whisper.TranscriptionSegment(2, 3000L, 5000L, "Second line")
        )
        val normalized = com.example.whisper.SrtGenerator.normalizeSegments(overlappingSegments)
        assertEquals(2, normalized.size)
        assertTrue(normalized[1].startMs >= normalized[0].endMs)
    }

    @Test
    fun testVocalProcessingManagerStateResetGuarantee() {
        com.example.service.VocalProcessingManager.resetState(videoId = "test_vid_123", videoTitle = "Test Song")
        assertEquals(0f, com.example.service.VocalProcessingManager.progress.value, 0.001f)
        assertEquals(0L, com.example.service.VocalProcessingManager.elapsedSeconds.value)
        assertEquals("00:00", com.example.service.VocalProcessingManager.elapsedTimeFormatted.value)
        assertNull(com.example.service.VocalProcessingManager.estimatedRemainingFormatted.value)
        assertEquals("test_vid_123", com.example.service.VocalProcessingManager.activeVideoId.value)
        assertEquals("Test Song", com.example.service.VocalProcessingManager.activeVideoTitle.value)
    }

    @Test
    fun testDubbingTranslationConfiguration() {
        val defaultConfig = com.example.dubbing.DubbingConfig()
        assertFalse(defaultConfig.enableTranslation)
        assertEquals(com.example.dubbing.translation.TranslationServiceType.ONLINE_FREE, defaultConfig.translationService)
        assertEquals("auto", defaultConfig.sourceLanguageCode)

        val spanishConfig = com.example.dubbing.DubbingConfig(
            locale = java.util.Locale("es", "ES"),
            enableTranslation = true,
            translationService = com.example.dubbing.translation.TranslationServiceType.ONLINE_FREE,
            targetLanguageCode = "es",
            sourceLanguageCode = "en",
            preTranslatedSegments = listOf(
                com.example.whisper.TranscriptionSegment(1, 0L, 2000L, "Hola mundo")
            )
        )
        assertTrue(spanishConfig.enableTranslation)
        assertEquals("es", spanishConfig.targetLanguageCode)
        assertEquals("en", spanishConfig.sourceLanguageCode)
        assertEquals(1, spanishConfig.preTranslatedSegments?.size)
        assertEquals("Hola mundo", spanishConfig.preTranslatedSegments?.first()?.text)
    }

    @Test
    fun testTranslatedSrtExportAndImportRoundTrip() {
        val translatedCues = listOf(
            com.example.whisper.TranscriptionSegment(
                index = 1,
                startMs = 500L,
                endMs = 2500L,
                text = "مرحباً بكم في الدبلجة الصوتية الذكية"
            ),
            com.example.whisper.TranscriptionSegment(
                index = 2,
                startMs = 2600L,
                endMs = 5000L,
                text = "هذا ملف SRT مترجم مخصص للدبلجة"
            )
        )

        val srtContent = com.example.whisper.SrtGenerator.generateSrtContent(translatedCues)
        assertTrue(srtContent.contains("00:00:00,500 --> 00:00:02,500"))
        assertTrue(srtContent.contains("مرحباً بكم في الدبلجة الصوتية الذكية"))
        assertTrue(srtContent.contains("00:00:02,600 --> 00:00:05,000"))
        assertTrue(srtContent.contains("هذا ملف SRT مترجم مخصص للدبلجة"))

        // Test stream parsing (Import simulation)
        val inputStream = java.io.ByteArrayInputStream(srtContent.toByteArray(Charsets.UTF_8))
        val parsed = com.example.whisper.SrtGenerator.parseSrtStream(inputStream)

        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].index)
        assertEquals(500L, parsed[0].startMs)
        assertEquals(2500L, parsed[0].endMs)
        assertEquals("مرحباً بكم في الدبلجة الصوتية الذكية", parsed[0].text)

        assertEquals(2, parsed[1].index)
        assertEquals(2600L, parsed[1].startMs)
        assertEquals(5000L, parsed[1].endMs)
        assertEquals("هذا ملف SRT مترجم مخصص للدبلجة", parsed[1].text)
    }

    @Test
    fun testDetectScriptLocaleFromTranslatedSegments() {
        val arabicSegments = listOf(
            com.example.whisper.TranscriptionSegment(1, 0L, 1000L, "صباح الخير")
        )
        val arabicLocale = com.example.whisper.SrtGenerator.detectScriptLocale(arabicSegments)
        assertNotNull(arabicLocale)
        assertEquals("ar", arabicLocale?.language)

        val japaneseSegments = listOf(
            com.example.whisper.TranscriptionSegment(1, 0L, 1000L, "こんにちは世界")
        )
        val japaneseLocale = com.example.whisper.SrtGenerator.detectScriptLocale(japaneseSegments)
        assertNotNull(japaneseLocale)
        assertEquals("ja", japaneseLocale?.language)

        val russianSegments = listOf(
            com.example.whisper.TranscriptionSegment(1, 0L, 1000L, "Привет, мир")
        )
        val russianLocale = com.example.whisper.SrtGenerator.detectScriptLocale(russianSegments)
        assertNotNull(russianLocale)
        assertEquals("ru", russianLocale?.language)
    }

    @Test
    fun testAudioTimeFitterCompressAndFade() {
        val sampleRate = 44100
        // Generate 1.0 second test tone (44100 samples)
        val sourceFrames = 44100
        val left = FloatArray(sourceFrames) { i -> kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate).toFloat() }
        val right = left.clone()

        // Request compression to 0.75 seconds (33075 samples)
        val targetFrames = 33075
        val (compL, compR) = com.example.dubbing.AudioTimeFitter.compressAudio(
            samplesLeft = left,
            samplesRight = right,
            targetFrames = targetFrames,
            sampleRate = sampleRate,
            maxSpeedUp = 1.45f
        )

        assertTrue(compL.size <= sourceFrames)
        assertTrue(compL.size in (targetFrames - 500)..(targetFrames + 500))
        assertEquals(compL.size, compR.size)

        // Verify all samples are within valid amplitude range [-1.0, 1.0]
        for (i in compL.indices) {
            assertTrue(compL[i] in -1.01f..1.01f)
            assertTrue(compR[i] in -1.01f..1.01f)
        }

        // Test smooth trimming
        val (trimmedL, trimmedR) = com.example.dubbing.AudioTimeFitter.trimWithFade(
            samplesLeft = left,
            samplesRight = right,
            targetFrames = 20000,
            fadeOutFrames = 1000
        )
        assertEquals(20000, trimmedL.size)
        assertEquals(20000, trimmedR.size)
        assertTrue(kotlin.math.abs(trimmedL.last()) < 0.05f) // Should fade smoothly to ~0
    }

    @Test
    fun testDubbingConfigOverlapDefaults() {
        val config = com.example.dubbing.DubbingConfig()
        assertTrue(config.preventOverlap)
        assertTrue(config.autoFitCues)
        assertEquals(80L, config.minGapBetweenCuesMs)
        assertEquals(com.example.dubbing.OverlapResolution.AUTO_PACE, config.overlapResolution)
        assertNull(config.ttsEnginePackage)
        assertNull(config.ttsEngineLabel)
    }

    @Test
    fun testDubbingConfigCustomTtsEngine() {
        val rhvoiceConfig = com.example.dubbing.DubbingConfig(
            ttsEnginePackage = "com.github.olga_yakovleva.rhvoice.android",
            ttsEngineLabel = "RHVoice"
        )
        assertEquals("com.github.olga_yakovleva.rhvoice.android", rhvoiceConfig.ttsEnginePackage)
        assertEquals("RHVoice", rhvoiceConfig.ttsEngineLabel)

        val engineInfo = com.example.dubbing.TtsDubbingEngine.TtsEngineInfo(
            packageName = "org.nvda.espeak",
            label = "eSpeak NG",
            isDefault = false
        )
        assertEquals("org.nvda.espeak", engineInfo.packageName)
        assertEquals("eSpeak NG", engineInfo.label)
        assertFalse(engineInfo.isDefault)
    }
}



