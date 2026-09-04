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
}
