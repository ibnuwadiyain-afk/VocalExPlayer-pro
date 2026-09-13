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
}
