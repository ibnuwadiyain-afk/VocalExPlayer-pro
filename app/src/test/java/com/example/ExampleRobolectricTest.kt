package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.WavWriter
import com.example.data.db.AppDatabase
import com.example.data.model.VideoItemEntity
import com.example.media.SocialMediaVideoProcessor
import com.example.onnx.MdxSpectrogramProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifyDatabaseOperations() = runBlocking {
        val dao = database.videoDao()
        val video = VideoItemEntity(
            title = "Test Music Video",
            videoUri = "file:///storage/video.mp4",
            durationMs = 180000L,
            sourcePlatform = "TikTok"
        )
        val id = dao.insertVideo(video)
        assertTrue(id > 0)

        val retrieved = dao.getVideoById(id)
        assertNotNull(retrieved)
        assertEquals("Test Music Video", retrieved?.title)
        assertEquals("TikTok", retrieved?.sourcePlatform)

        val all = dao.getAllVideos().first()
        assertEquals(1, all.size)
    }

    @Test
    fun verifyWavWriterStreaming() {
        val tempFile = File(context.cacheDir, "test_audio.wav")
        if (tempFile.exists()) tempFile.delete()

        val sampleRate = 44100
        val dummyFloats = FloatArray(4410 * 2) { 0.5f } // 0.1s stereo
        WavWriter.writeCompleteWav(
            outputFile = tempFile,
            samplesLeft = FloatArray(4410) { 0.5f },
            samplesRight = FloatArray(4410) { -0.5f },
            sampleRate = sampleRate
        )

        assertTrue(tempFile.exists())
        assertEquals(44L + (4410 * 2 * 2), tempFile.length())
        tempFile.delete()
    }

    @Test
    fun verifySocialMediaVideoProcessorPlatformDetection() {
        val processor = SocialMediaVideoProcessor(context)

        assertEquals("Web Video", processor.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("Web Video", processor.detectPlatform("https://www.youtube.com/shorts/AbCdEfGhIjK"))
        assertEquals("TikTok", processor.detectPlatform("https://www.tiktok.com/@user/video/123456"))
        assertEquals("Instagram Reel", processor.detectPlatform("https://www.instagram.com/reel/12345/"))
        assertEquals("X (Twitter)", processor.detectPlatform("https://twitter.com/user/status/12345"))
        assertEquals("Direct Video Link", processor.detectPlatform("https://example.com/audio/test.mp4"))
    }

    @Test
    fun verifySpectrogramProcessorFFT() {
        val proc = MdxSpectrogramProcessor(nFft = 256, hopLength = 128)
        val real = FloatArray(256) { 1.0f }
        val imag = FloatArray(256) { 0.0f }

        val originalReal = real.clone()
        proc.fft(real, imag)
        proc.ifft(real, imag)

        for (i in 0 until 256) {
            assertTrue(abs(originalReal[i] - real[i]) < 1e-4)
        }
    }

    @Test
    fun verifyUvrMdxModelAssetPresent() {
        val assetList = context.assets.list("models")
        assertNotNull(assetList)
        assertTrue(assetList!!.contains("UVR_MDXNET_1_9703.onnx"))
    }
}
