package com.example.onnx

import com.example.audio.DecodedAudio
import java.io.File

data class SeparationResult(
    val vocalWavFile: File,
    val instrumentalWavFile: File? = null
)

enum class SeparationEngineType(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val badge: String,
    val producesTwoStems: Boolean
) {
    DEEZER_SPLEETER(
        id = "deezer_spleeter",
        displayName = "Deezer Spleeter (2-Stem Engine)",
        subtitle = "Original Deezer 2-stem model • Vocals + Accompaniment",
        description = "Genuine Deezer Spleeter neural source separation engine. Generates both isolated vocals and studio karaoke accompaniment.",
        badge = "2-Stem Neural",
        producesTwoStems = true
    ),
    UVR_MDXNET(
        id = "uvr_mdxnet",
        displayName = "UVR-MDX-Net 1 (Vocals Engine)",
        subtitle = "Built-in 29.7MB MDX-Net 1 • Studio Vocal Extraction",
        description = "4-channel complex STFT architecture fine-tuned for high-fidelity vocal isolation.",
        badge = "UVR Studio",
        producesTwoStems = false
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom ONNX Model",
        subtitle = "User-imported ONNX model",
        description = "User-supplied ONNX separation model running on native ONNX Runtime.",
        badge = "Custom",
        producesTwoStems = false
    );

    companion object {
        fun fromId(id: String?): SeparationEngineType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEEZER_SPLEETER
        }
    }
}

interface SeparationEngine {
    val engineType: SeparationEngineType
    val activeHardwareBackend: String

    fun separate(
        decodedAudio: DecodedAudio,
        outputVocalFile: File,
        outputInstrumentalFile: File?,
        customModelPath: String? = null,
        accelerationMode: AccelerationMode = AccelerationMode.TURBO,
        onProgress: (Float) -> Unit = {}
    ): SeparationResult
}
