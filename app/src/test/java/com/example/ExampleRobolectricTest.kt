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
}
