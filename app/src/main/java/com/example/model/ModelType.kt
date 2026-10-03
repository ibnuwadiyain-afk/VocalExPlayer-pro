package com.example.model

enum class ModelType {
    VOCAL_SEPARATION,
    WHISPER_TRANSCRIPTION;

    val displayName: String
        get() = when (this) {
            VOCAL_SEPARATION -> "Vocal Separation"
            WHISPER_TRANSCRIPTION -> "Speech Recognition (Whisper)"
        }
}
