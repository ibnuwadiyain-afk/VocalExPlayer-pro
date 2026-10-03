package com.example.whisper

import java.io.File

data class TranscriptionSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val startFormatted: String
        get() = formatTimestamp(startMs)

    val endFormatted: String
        get() = formatTimestamp(endMs)

    companion object {
        fun formatTimestamp(ms: Long): String {
            val totalSec = ms / 1000
            val millis = ms % 1000
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val secs = totalSec % 60
            return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
        }
    }
}

data class TranscriptionResult(
    val language: String,
    val segments: List<TranscriptionSegment>,
    val srtFile: File,
    val fullText: String
)
