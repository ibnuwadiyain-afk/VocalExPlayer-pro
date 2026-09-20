package com.example.onnx

/**
 * User-configurable vocal and instrumental separation methods.
 * Keeps UVR MDX-Net 1 as an optional high-quality batch method while providing
 * real-time streaming neural separation and instant 0ms phase cancellation.
 */
enum class SeparationMethod(
    val id: String,
    val displayName: String,
    val speedBadge: String,
    val description: String
) {
    STREAMING_NEURAL(
        id = "streaming_neural",
        displayName = "Streaming Neural Engine (Zero-Wait)",
        speedBadge = "RECOMMENDED",
        description = "Non-blocking chunk-by-chunk ONNX neural separation with persistent PCM cache, real-time playback & simultaneous muxing."
    ),
    INSTANT_PHASE_CANCELLATION(
        id = "instant_phase",
        displayName = "Instant Phase Cancellation (0ms)",
        speedBadge = "INSTANT",
        description = "Center-channel stereo mid/side matrixing with <120Hz bass retention. 0ms wait time for instant playback & scrubbing."
    ),
    UVR_MDX_FULL(
        id = "uvr_mdx",
        displayName = "UVR MDX-Net 1 (High Quality Batch)",
        speedBadge = "STUDIO",
        description = "Full spectrogram UVR MDX-Net 1 batch separation. Studio-grade quality with full vocal stem isolation."
    );

    companion object {
        fun fromId(id: String?): SeparationMethod {
            return entries.firstOrNull { it.id == id } ?: STREAMING_NEURAL
        }
    }
}
