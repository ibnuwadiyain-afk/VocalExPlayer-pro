package com.example.onnx

enum class AccelerationMode(
    val id: String,
    val displayName: String,
    val speedBadge: String,
    val description: String,
    val silenceSkipThreshold: Float
) {
    TURBO(
        id = "turbo",
        displayName = "Turbo Mode",
        speedBadge = "2x – 3x Faster",
        description = "Uses NNAPI hardware acceleration, all CPU cores, zero-allocation buffers, and smart silence skipping.",
        silenceSkipThreshold = 0.00005f
    ),
    STUDIO(
        id = "studio",
        displayName = "Studio Precision",
        speedBadge = "Original",
        description = "Full neural pass across all audio frames without silence skipping for maximum isolation precision.",
        silenceSkipThreshold = 0.0f
    );

    companion object {
        fun fromId(id: String?): AccelerationMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TURBO
        }
    }
}
