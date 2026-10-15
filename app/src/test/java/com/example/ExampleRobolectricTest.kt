package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VocalExtractor", appName)
  }

  @Test
  fun `verify AppStrings translations for multiple languages`() {
    val en = com.example.ui.locale.AppLanguage.ENGLISH
    val ar = com.example.ui.locale.AppLanguage.ARABIC
    val es = com.example.ui.locale.AppLanguage.SPANISH

    assertEquals("VocalExtractor", com.example.ui.locale.AppStrings.appName(en))
    assertEquals("مستخرج الأصوات", com.example.ui.locale.AppStrings.appName(ar))
    assertEquals("Extractor de Voces", com.example.ui.locale.AppStrings.appName(es))

    assertEquals("SPLEETER 2-STEM", com.example.ui.locale.AppStrings.spleeterBadge(en))
    assertEquals("سبيليتر 2-STEM", com.example.ui.locale.AppStrings.spleeterBadge(ar))

    assertEquals("No Audio or Video Added Yet", com.example.ui.locale.AppStrings.emptyStateTitle(en))
    assertEquals("لم تتم إضافة أي وسائط حتى الآن", com.example.ui.locale.AppStrings.emptyStateTitle(ar))
  }

  @Test
  fun `verify SpleeterInferenceEngine initialization and model extraction`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.onnx.SpleeterInferenceEngine(context)
    val modelFile = engine.getOrExtractModelFile()
    org.junit.Assert.assertNotNull("Model file should not be null", modelFile)
    org.junit.Assert.assertTrue("Model file should exist", modelFile!!.exists())
    org.junit.Assert.assertEquals("Model file should be genuine Deezer Spleeter 2-stem model (19.68MB)", 19_681_017L, modelFile.length())
  }

  @Test
  fun `verify SpleeterSpectrogramProcessor forward and inverse transforms`() {
    val processor = com.example.onnx.SpleeterSpectrogramProcessor()
    val signal = FloatArray(processor.chunkSize) { i ->
      kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / 44100.0).toFloat()
    }
    val scratch = processor.createScratchpad()
    val byteBuf = java.nio.ByteBuffer.allocateDirect(2 * 1 * processor.dimT * processor.dimF * 4)
      .order(java.nio.ByteOrder.nativeOrder())
      .asFloatBuffer()

    processor.computeSpleeterInput(
      pcmLeft = signal,
      pcmRight = signal,
      pcmOffset = 0,
      pcmLength = processor.chunkSize,
      scratch = scratch,
      onnxInputBuffer = byteBuf
    )

    // Check that 440Hz has peak energy in the corresponding bin
    // Bin resolution: 44100 / 4096 ≈ 10.766 Hz/bin => 440Hz ≈ bin 41
    var maxBin = 0
    var maxMag = 0f
    for (f in 0 until processor.dimF) {
      val mag = byteBuf.get(f) // First frame of channel 0
      if (mag > maxMag) {
        maxMag = mag
        maxBin = f
      }
    }
    org.junit.Assert.assertTrue("Peak bin ($maxBin) should be near 41 for 440Hz", kotlin.math.abs(maxBin - 41) <= 2)
  }

  @Test
  fun `verify AccelerationMode properties and lookup`() {
    val turbo = com.example.onnx.AccelerationMode.fromId("turbo")
    val studio = com.example.onnx.AccelerationMode.fromId("studio")
    val defaultMode = com.example.onnx.AccelerationMode.fromId("invalid")

    assertEquals(com.example.onnx.AccelerationMode.TURBO, turbo)
    assertEquals(com.example.onnx.AccelerationMode.STUDIO, studio)
    assertEquals(com.example.onnx.AccelerationMode.TURBO, defaultMode)
    org.junit.Assert.assertTrue(turbo.silenceSkipThreshold > 0f)
    assertEquals(0.0f, studio.silenceSkipThreshold, 0.0001f)
  }

  @Test
  fun `verify VideoVocalsExporter handles invalid files gracefully`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val result = com.example.export.VideoVocalsExporter.exportVideoWithVocals(
      context = context,
      videoUriString = "file:///non/existent/video.mp4",
      vocalWavPath = "/non/existent/vocals.wav",
      outputTitle = "Test"
    )
    org.junit.Assert.assertTrue("Export should fail for non-existent vocal file", result.isFailure)
  }

  @Test
  fun `verify social media url extraction and youtube id parsing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val processor = com.example.media.SocialMediaVideoProcessor(context)

    // YouTube share message
    val shareText = "Look at this song! https://youtu.be/dQw4w9WgXcQ sent from YouTube"
    val extracted = processor.extractUrlFromText(shareText)
    assertEquals("https://youtu.be/dQw4w9WgXcQ", extracted)

    // YouTube ID parsing
    val id1 = processor.extractYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    val id2 = processor.extractYouTubeId("https://youtu.be/dQw4w9WgXcQ")
    val id3 = processor.extractYouTubeId("https://www.youtube.com/shorts/AbCdEfGhIjK")

    assertEquals("dQw4w9WgXcQ", id1)
    assertEquals("dQw4w9WgXcQ", id2)
    assertEquals("AbCdEfGhIjK", id3)

    // TikTok and Instagram extraction
    val tiktokText = "Check this dance https://www.tiktok.com/@artist/video/9876543210 cool"
    assertEquals("https://www.tiktok.com/@artist/video/9876543210", processor.extractUrlFromText(tiktokText))

    val instaText = "Reel: https://www.instagram.com/reel/DA12345678/ #music"
    assertEquals("https://www.instagram.com/reel/DA12345678/", processor.extractUrlFromText(instaText))
  }

  @Test
  fun `verify SocialMediaVideoProcessor platform detection and presets`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val processor = com.example.media.SocialMediaVideoProcessor(context)

    assertEquals("YouTube", processor.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("YouTube Shorts", processor.detectPlatform("https://www.youtube.com/shorts/AbCdEfGhIjK"))
    assertEquals("TikTok", processor.detectPlatform("https://www.tiktok.com/@user/video/123456"))
    assertEquals("Instagram Reel", processor.detectPlatform("https://www.instagram.com/reel/12345/"))
    assertEquals("X (Twitter)", processor.detectPlatform("https://twitter.com/user/status/12345"))
    assertEquals("VidLii", processor.detectPlatform("https://www.vidlii.com/watch?v=abcdef123"))
    assertEquals("Dailymotion", processor.detectPlatform("https://www.dailymotion.com/video/x89123"))
    assertEquals("Vimeo", processor.detectPlatform("https://vimeo.com/12345678"))
    assertEquals("Direct Video Link", processor.detectPlatform("https://example.com/audio/test.mp4"))
    assertEquals("Example", processor.detectPlatform("https://example.com/watch?v=999"))

    // Presets verification
    org.junit.Assert.assertFalse(com.example.media.SocialMediaVideoProcessor.SAMPLE_PRESETS.isEmpty())
    val firstPreset = com.example.media.SocialMediaVideoProcessor.SAMPLE_PRESETS.first()
    org.junit.Assert.assertTrue(firstPreset.url.startsWith("http"))
  }

  @Test
  fun `verify ProgressiveVideoMerger feedSeparatedChunk does not crash on empty arrays or silence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempOut = java.io.File(context.cacheDir, "test_merger_out.mp4")
    val merger = com.example.export.ProgressiveVideoMerger(
      context = context,
      videoUriString = "file:///non/existent.mp4",
      sampleRate = 44100,
      channels = 2,
      outputCacheFile = tempOut
    )

    // Verify it handles empty arrays, silence chunks, and EOS safely without throwing ArrayIndexOutOfBoundsException
    merger.feedSeparatedChunk(FloatArray(0), FloatArray(0), 0, 1024, false)
    merger.feedSeparatedChunk(FloatArray(512), FloatArray(512), 0, 1024, false)
    merger.feedSeparatedChunk(FloatArray(0), FloatArray(0), 0, 0, true)
    merger.cancel()
  }

  @Test
  fun `verify VocalProcessingManager state reset guarantee clears progress elapsed time and buffers`() {
    val manager = com.example.service.VocalProcessingManager

    // Trigger resetState with mock video information
    manager.resetState(videoId = "test_vid_123", videoTitle = "Test Song")

    assertEquals(0.0f, manager.progress.value, 0.0001f)
    assertEquals(0L, manager.elapsedSeconds.value)
    assertEquals("00:00", manager.elapsedTimeFormatted.value)
    assertEquals(null, manager.estimatedRemainingFormatted.value)
    assertEquals("test_vid_123", manager.activeVideoId.value)
    assertEquals("Test Song", manager.activeVideoTitle.value)
    org.junit.Assert.assertFalse(manager.isProcessing.value)
    org.junit.Assert.assertFalse(manager.isBackgroundProtected.value)

    // Trigger resetState with null parameters (e.g. before initial import inspection)
    manager.resetState()
    assertEquals(0.0f, manager.progress.value, 0.0001f)
    assertEquals(0L, manager.elapsedSeconds.value)
    assertEquals("00:00", manager.elapsedTimeFormatted.value)
    assertEquals(null, manager.estimatedRemainingFormatted.value)
    assertEquals(null, manager.activeVideoId.value)
    assertEquals(null, manager.activeVideoTitle.value)
  }

  @Test
  fun `verify WhisperTokenizer timestamp and language tokens`() {
    val enToken = com.example.whisper.WhisperTokenizer.getLanguageToken("en")
    val arToken = com.example.whisper.WhisperTokenizer.getLanguageToken("ar")
    val esToken = com.example.whisper.WhisperTokenizer.getLanguageToken("es")

    assertEquals(50259, enToken)
    assertEquals(50272, arToken)
    assertEquals(50262, esToken)

    // Timestamp tokens
    org.junit.Assert.assertTrue(com.example.whisper.WhisperTokenizer.isTimestampToken(50364)) // <|0.00|>
    org.junit.Assert.assertTrue(com.example.whisper.WhisperTokenizer.isTimestampToken(51864)) // <|30.00|>
    org.junit.Assert.assertFalse(com.example.whisper.WhisperTokenizer.isTimestampToken(50258))

    assertEquals(0.0f, com.example.whisper.WhisperTokenizer.timestampTokenToSeconds(50364), 0.0001f)
    assertEquals(1.0f, com.example.whisper.WhisperTokenizer.timestampTokenToSeconds(50414), 0.0001f)
    assertEquals(30.0f, com.example.whisper.WhisperTokenizer.timestampTokenToSeconds(51864), 0.0001f)
  }

  @Test
  fun `verify SrtGenerator generates valid UTF-8 SRT with chronological order and Arabic preservation`() {
    val rawSegments = listOf(
      com.example.whisper.TranscriptionSegment(
        index = 1,
        startMs = 1200L,
        endMs = 4350L,
        text = "مرحبا بكم في هذا الفيديو"
      ),
      com.example.whisper.TranscriptionSegment(
        index = 2,
        startMs = 4600L,
        endMs = 7900L,
        text = "Welcome to the video."
      )
    )

    val srtContent = com.example.whisper.SrtGenerator.generateSrtContent(rawSegments)

    org.junit.Assert.assertTrue("SRT should contain 1", srtContent.contains("1\r\n00:00:01,200 --> 00:00:04,350"))
    org.junit.Assert.assertTrue("SRT should contain Arabic text preserved", srtContent.contains("مرحبا بكم في هذا الفيديو"))
    org.junit.Assert.assertTrue("SRT should contain 2", srtContent.contains("2\r\n00:00:04,600 --> 00:00:07,900"))
    org.junit.Assert.assertTrue("SRT should contain English text", srtContent.contains("Welcome to the video."))

    // Test writing and reading back
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempSrt = java.io.File(context.cacheDir, "test_subtitles.srt")
    com.example.whisper.SrtGenerator.writeSrtFile(rawSegments, tempSrt)

    org.junit.Assert.assertTrue("SRT file should exist", tempSrt.exists())
    val parsed = com.example.whisper.SrtGenerator.parseSrtFile(tempSrt)
    assertEquals(2, parsed.size)
    assertEquals("مرحبا بكم في هذا الفيديو", parsed[0].text)
    assertEquals(1200L, parsed[0].startMs)
    assertEquals(4350L, parsed[0].endMs)
  }

  @Test
  fun `verify AppModelManager registers Spleeter and Whisper models`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.model.AppModelManager.getInstance(context)

    val allModels = manager.models.value
    org.junit.Assert.assertTrue("Models catalog should not be empty", allModels.isNotEmpty())

    val spleeterModel = allModels.firstOrNull { it.id == "spleeter_2stems" }
    org.junit.Assert.assertNotNull("Spleeter model should exist", spleeterModel)
    org.junit.Assert.assertTrue("Spleeter model should be bundled", spleeterModel!!.isBundled)
    assertEquals(com.example.model.ModelType.VOCAL_SEPARATION, spleeterModel.type)

    val whisperTiny = allModels.firstOrNull { it.id == "whisper_tiny" }
    org.junit.Assert.assertNotNull("Whisper Tiny should exist", whisperTiny)
    assertEquals(com.example.model.ModelType.WHISPER_TRANSCRIPTION, whisperTiny!!.type)

    val whisperBase = allModels.firstOrNull { it.id == "whisper_base" }
    org.junit.Assert.assertNotNull("Whisper Base should exist", whisperBase)

    val whisperSmall = allModels.firstOrNull { it.id == "whisper_small" }
    org.junit.Assert.assertNotNull("Whisper Small should exist", whisperSmall)
  }

  @Test
  fun `verify Model Catalog true sizes and display formatting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.model.AppModelManager.getInstance(context)

    val spleeter = manager.getModelById("spleeter_2stems")!!
    assertEquals(19_681_017L, spleeter.sizeBytes)
    assertEquals(com.example.model.AppModelManager.formatFileSize(19_681_017L), spleeter.sizeDisplay)

    val uvr = manager.getModelById("uvr_mdxnet_voc")!!
    assertEquals(29_704_436L, uvr.sizeBytes)
    assertEquals(com.example.model.AppModelManager.formatFileSize(29_704_436L), uvr.sizeDisplay)
    org.junit.Assert.assertTrue("UVR downloadUrl should be valid huggingface URL", uvr.downloadUrl.contains("seanghay"))

    val whisperTiny = manager.getModelById("whisper_tiny")!!
    assertEquals(40_586_061L, whisperTiny.sizeBytes)
    assertEquals(com.example.model.AppModelManager.formatFileSize(40_586_061L), whisperTiny.sizeDisplay)
    org.junit.Assert.assertNotNull("Whisper Tiny should have companion decoder URL", manager.getDecoderUrl("whisper_tiny"))

    assertEquals("10.0 MB", com.example.model.AppModelManager.formatFileSize(10_485_760L))
    assertEquals("28.3 MB", com.example.model.AppModelManager.formatFileSize(29_704_436L))
    assertEquals("512.0 KB", com.example.model.AppModelManager.formatFileSize(524_288L))
  }

  @Test
  fun `verify WhisperTokenizer vocabulary loading and token decoding`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.whisper.WhisperTokenizer.loadVocabulary(context)

    val decoded = com.example.whisper.WhisperTokenizer.decodeTokens(listOf(7751, 1002))
    org.junit.Assert.assertTrue("Decoded text should not be empty", decoded.isNotBlank())
    assertEquals("hello world", decoded)
  }

  @Test
  fun `verify WhisperAudioPreprocessor mel spectrogram computation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val preprocessor = com.example.whisper.WhisperAudioPreprocessor(context)
    val dummyAudio = FloatArray(480000)
    for (i in 0 until 64000) {
      dummyAudio[i] = kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / 16000.0).toFloat()
    }
    val mel = preprocessor.computeMelSpectrogram(dummyAudio)
    assertEquals(80, mel.size)
    assertEquals(3000, mel[0].size)
  }

  @Test
  fun `verify Model Backup Zip Creation and Manifest`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val manager = com.example.model.AppModelManager.getInstance(context)

      // Ensure models list is refreshed
      manager.refreshModelsList()

      // Test creating temporary backup ZIP archive
      var progressCalled = false
      val zipResult = manager.createTemporaryBackupZipFile { pct, msg ->
        progressCalled = true
        org.junit.Assert.assertNotNull(msg)
      }

      org.junit.Assert.assertTrue("Backup ZIP should succeed", zipResult.isSuccess)
      val zipFile = zipResult.getOrThrow()
      org.junit.Assert.assertTrue("Backup ZIP file should exist on disk", zipFile.exists())
      org.junit.Assert.assertTrue("Backup ZIP file should not be empty", zipFile.length() > 0)
      org.junit.Assert.assertTrue("Progress callback should have been invoked", progressCalled)

      // Verify ZIP contains backup_manifest.json
      var hasManifest = false
      val zipInputStream = java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile))
      var entry = zipInputStream.nextEntry
      while (entry != null) {
        if (entry.name == "backup_manifest.json") {
          hasManifest = true
          val content = zipInputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).readText()
          val json = org.json.JSONObject(content)
          org.junit.Assert.assertEquals("VocalExtractor", json.getString("app"))
          org.junit.Assert.assertTrue(json.getInt("modelCount") >= 1)
        }
        entry = zipInputStream.nextEntry
      }
      zipInputStream.close()
      org.junit.Assert.assertTrue("Backup ZIP must contain backup_manifest.json", hasManifest)

      // Verify share intent creation
      val spleeterModel = manager.models.value.first { it.id == "spleeter_2stems" }
      val shareIntent = manager.createShareIntentForModel(spleeterModel.id)
      org.junit.Assert.assertNotNull("Share intent should be generated", shareIntent)
      org.junit.Assert.assertEquals(android.content.Intent.ACTION_SEND, shareIntent!!.action)
      org.junit.Assert.assertEquals("application/octet-stream", shareIntent.type)

      // Clean up
      zipFile.delete()
    }
  }
}
