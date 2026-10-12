package com.example.dubbing

import java.util.Locale

enum class DubbingMixMode(
    val title: String,
    val subtitle: String,
    val isVideo: Boolean
) {
    VIDEO_DUBBED_ONLY(
        title = "Dubbed Video (Speech Only)",
        subtitle = "Replaces original voice with synchronized TTS speech over video",
        isVideo = true
    ),
    VIDEO_DUBBED_WITH_MUSIC(
        title = "Dubbed Video + Background Music",
        subtitle = "Dubbed voice mixed with backing accompaniment (with audio ducking)",
        isVideo = true
    ),
    AUDIO_DUBBED_WAV(
        title = "Dubbed Audio (Lossless WAV)",
        subtitle = "Synchronized speech timeline in studio 16-bit 44.1kHz WAV",
        isVideo = false
    ),
    AUDIO_DUBBED_M4A(
        title = "Dubbed Audio (AAC / M4A)",
        subtitle = "High-quality 256 kbps AAC audio • Universal playback",
        isVideo = false
    )
}

data class DubbingConfig(
    val locale: Locale = Locale.getDefault(),
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val mixMode: DubbingMixMode = DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC,
    val backgroundDuckingVolume: Float = 0.20f
)
