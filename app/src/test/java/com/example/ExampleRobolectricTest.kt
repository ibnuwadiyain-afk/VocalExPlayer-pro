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
    org.junit.Assert.assertTrue("Model file should exist", modelFile.exists())
    org.junit.Assert.assertTrue("Model file should have valid weights (> 20MB)", modelFile.length() > 20_000_000L)
  }

  @Test
  fun `verify MdxSpectrogramProcessor forward and inverse transforms`() {
    val processor = com.example.onnx.MdxSpectrogramProcessor()
    val signal = FloatArray(processor.chunkSize + processor.nFft) { i ->
      kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / 44100.0).toFloat()
    }
    val specRe = Array(processor.dimF) { FloatArray(processor.dimT) }
    val specIm = Array(processor.dimF) { FloatArray(processor.dimT) }

    processor.computeChannelStft(signal, specRe, specIm)

    // Check that 440Hz has energy in the corresponding bin
    // Bin resolution: 44100 / 6144 ≈ 7.177 Hz/bin => 440Hz ≈ bin 61
    var maxBin = 0
    var maxMag = 0f
    for (f in 0 until processor.dimF) {
      val mag = specRe[f][10] * specRe[f][10] + specIm[f][10] * specIm[f][10]
      if (mag > maxMag) {
        maxMag = mag
        maxBin = f
      }
    }
    org.junit.Assert.assertTrue("Peak bin ($maxBin) should be near 61 for 440Hz", kotlin.math.abs(maxBin - 61) <= 2)
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
}
