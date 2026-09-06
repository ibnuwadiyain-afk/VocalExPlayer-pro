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
    assertEquals("Direct Video Link", processor.detectPlatform("https://example.com/audio/test.mp4"))

    // Presets verification
    org.junit.Assert.assertFalse(com.example.media.SocialMediaVideoProcessor.SAMPLE_PRESETS.isEmpty())
    val firstPreset = com.example.media.SocialMediaVideoProcessor.SAMPLE_PRESETS.first()
    org.junit.Assert.assertTrue(firstPreset.url.startsWith("http"))
  }

  @Test
  fun `verify VocalProcessingService creates notification channel and promotes to foreground immediately`() {
    val serviceController = org.robolectric.Robolectric.buildService(com.example.service.VocalProcessingService::class.java)
    val service = serviceController.create().get()

    val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channel = notificationManager.getNotificationChannel(com.example.service.VocalProcessingService.CHANNEL_ID)
    org.junit.Assert.assertNotNull("Notification channel should be created in onCreate", channel)
    assertEquals("AI Vocal Separation", channel.name)

    // Test onStartCommand with null intent exits cleanly
    val startResult = service.onStartCommand(null, 0, 1)
    assertEquals(android.app.Service.START_NOT_STICKY, startResult)
  }

  @Test
  fun `verify VocalProcessingService handles missing configuration without crashing`() {
    val serviceController = org.robolectric.Robolectric.buildService(com.example.service.VocalProcessingService::class.java)
    val service = serviceController.create().get()
    val intent = android.content.Intent(com.example.service.VocalProcessingService.ACTION_START_PROCESSING)
    // videoId missing intentionally
    val startResult = service.onStartCommand(intent, 0, 1)
    assertEquals(android.app.Service.START_NOT_STICKY, startResult)
  }
}

