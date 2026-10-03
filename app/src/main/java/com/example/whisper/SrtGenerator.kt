package com.example.whisper

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object SrtGenerator {

    /**
     * Formats milliseconds to standard SRT timestamp format: HH:MM:SS,mmm
     */
    fun formatSrtTime(ms: Long): String {
        val nonNegativeMs = ms.coerceAtLeast(0L)
        val totalSec = nonNegativeMs / 1000
        val millis = nonNegativeMs % 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }

    /**
     * Normalizes and cleans segment timestamps:
     * - Ensures sequential 1-based indexing
     * - Guarantees non-negative timestamps
     * - Prevents negative durations or overlapping entries
     * - Normalizes readable line breaks (1-2 lines per subtitle block)
     * - Strictly preserves Arabic Unicode text, punctuation, and numerals without reversal
     */
    fun normalizeSegments(rawSegments: List<TranscriptionSegment>): List<TranscriptionSegment> {
        if (rawSegments.isEmpty()) return emptyList()

        val cleanSegments = mutableListOf<TranscriptionSegment>()
        var lastEndTimeMs = 0L
        var currentIndex = 1

        for (seg in rawSegments) {
            val text = seg.text.trim()
            if (text.isBlank()) continue

            // Ensure start time is at least the end time of previous segment to prevent overlaps
            val startTimeMs = seg.startMs.coerceAtLeast(lastEndTimeMs).coerceAtLeast(0L)
            // Ensure minimum display duration of 800ms for readability if Whisper end time is too short
            val minDurationMs = 800L
            val endTimeMs = (seg.endMs.coerceAtLeast(startTimeMs + minDurationMs))

            // Subtitle formatting: limit to maximum 2 lines with natural word wrapping
            val formattedText = formatSubtitleTextLines(text)

            cleanSegments.add(
                TranscriptionSegment(
                    index = currentIndex++,
                    startMs = startTimeMs,
                    endMs = endTimeMs,
                    text = formattedText
                )
            )
            lastEndTimeMs = endTimeMs
        }

        return cleanSegments
    }

    /**
     * Formats subtitle text into at most 2 balanced lines without breaking words.
     * Preserves Arabic characters and punctuation.
     */
    private fun formatSubtitleTextLines(raw: String): String {
        val singleSpaced = raw.replace(Regex("\\s+"), " ").trim()
        if (singleSpaced.length <= 42) return singleSpaced

        val words = singleSpaced.split(" ")
        if (words.size <= 4) return singleSpaced

        val mid = words.size / 2
        val line1 = words.subList(0, mid).joinToString(" ")
        val line2 = words.subList(mid, words.size).joinToString(" ")
        return "$line1\n$line2"
    }

    /**
     * Generates a complete, valid SubRip (.srt) UTF-8 string from segments.
     */
    fun generateSrtContent(segments: List<TranscriptionSegment>): String {
        val normalized = normalizeSegments(segments)
        val sb = StringBuilder()

        for (seg in normalized) {
            sb.append(seg.index).append("\r\n")
            sb.append(formatSrtTime(seg.startMs))
                .append(" --> ")
                .append(formatSrtTime(seg.endMs))
                .append("\r\n")
            sb.append(seg.text).append("\r\n")
            sb.append("\r\n")
        }

        return sb.toString()
    }

    /**
     * Writes SRT content to the specified target File using pure UTF-8 encoding.
     */
    fun writeSrtFile(segments: List<TranscriptionSegment>, targetFile: File): File {
        if (targetFile.parentFile?.exists() == false) {
            targetFile.parentFile?.mkdirs()
        }

        val srtContent = generateSrtContent(segments)
        FileOutputStream(targetFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(srtContent)
                writer.flush()
            }
        }

        return targetFile
    }

    /**
     * Parses an existing SRT file back into a list of TranscriptionSegments.
     */
    fun parseSrtFile(file: File): List<TranscriptionSegment> {
        if (!file.exists() || file.length() == 0L) return emptyList()

        val segments = mutableListOf<TranscriptionSegment>()
        val lines = file.readLines(StandardCharsets.UTF_8)
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            val index = line.toIntOrNull()
            if (index != null && i + 1 < lines.size) {
                val timeLine = lines[++i].trim()
                if (timeLine.contains("-->")) {
                    val parts = timeLine.split("-->")
                    val startMs = parseSrtTimestamp(parts[0].trim())
                    val endMs = parseSrtTimestamp(parts[1].trim())

                    val textLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i].trim())
                        i++
                    }

                    segments.add(
                        TranscriptionSegment(
                            index = index,
                            startMs = startMs,
                            endMs = endMs,
                            text = textLines.joinToString("\n")
                        )
                    )
                }
            }
            i++
        }

        return segments
    }

    private fun parseSrtTimestamp(timeStr: String): Long {
        try {
            val clean = timeStr.replace(',', '.')
            val parts = clean.split(":")
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val secParts = parts[2].split(".")
            val secs = secParts[0].toLong()
            val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
            return (hours * 3600 + minutes * 60 + secs) * 1000 + millis
        } catch (_: Exception) {
            return 0L
        }
    }
}
