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
     * Parses a list of lines from an SRT file/stream into TranscriptionSegments.
     * Supports UTF-8 with optional BOM and standard timestamp formats.
     */
    fun parseLines(lines: List<String>): List<TranscriptionSegment> {
        val segments = mutableListOf<TranscriptionSegment>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            // Remove any UTF-8 BOM if present on first line
            val cleanLine = line.removePrefix("\uFEFF")
            val index = cleanLine.toIntOrNull()
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

    /**
     * Parses an existing SRT file back into a list of TranscriptionSegments.
     */
    fun parseSrtFile(file: File): List<TranscriptionSegment> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return parseLines(file.readLines(StandardCharsets.UTF_8))
    }

    /**
     * Parses an InputStream containing SRT data into TranscriptionSegments.
     */
    fun parseSrtStream(inputStream: java.io.InputStream): List<TranscriptionSegment> {
        return parseLines(inputStream.bufferedReader(StandardCharsets.UTF_8).readLines())
    }

    /**
     * Parses raw SRT content string into TranscriptionSegments.
     */
    fun parseSrtContent(content: String): List<TranscriptionSegment> {
        return parseLines(content.lines())
    }

    /**
     * Writes SRT content directly to an OutputStream (e.g. for SAF CreateDocument).
     */
    fun writeSrtToOutputStream(segments: List<TranscriptionSegment>, outputStream: java.io.OutputStream) {
        val srtContent = generateSrtContent(segments)
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(srtContent)
            writer.flush()
        }
    }

    /**
     * Exports translated segments to an .srt file in the subtitles folder.
     */
    fun exportTranslatedSrtFile(
        context: android.content.Context,
        videoTitle: String,
        targetLanguageCode: String,
        segments: List<TranscriptionSegment>
    ): File {
        val sanitized = com.example.export.ExportNameSanitizer.sanitizeTitle(videoTitle)
        val exportDir = File(context.filesDir, "subtitles")
        if (!exportDir.exists()) exportDir.mkdirs()
        val langSuffix = if (targetLanguageCode.isNotBlank()) "_$targetLanguageCode" else "_translated"
        val targetFile = File(exportDir, "${sanitized}${langSuffix}.srt")
        return writeSrtFile(segments, targetFile)
    }

    /**
     * Opens the Android share sheet for an SRT file using FileProvider.
     */
    fun shareSrtFile(
        context: android.content.Context,
        file: File,
        chooserTitle: String = "Export Subtitles (.srt)"
    ) {
        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle))
        } catch (_: Exception) {
            val textIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, file.readText(StandardCharsets.UTF_8))
                putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
            }
            context.startActivity(android.content.Intent.createChooser(textIntent, chooserTitle))
        }
    }

    /**
     * Reads and parses an SRT file from a content Uri, normalizing timestamps and indexes.
     */
    fun parseAndNormalizeSrt(
        context: android.content.Context,
        uri: android.net.Uri
    ): Result<List<TranscriptionSegment>> {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalStateException("Unable to open subtitle file"))
            val parsed = stream.use { parseSrtStream(it) }
            if (parsed.isEmpty()) {
                Result.failure(IllegalArgumentException("No valid subtitle cues found in file"))
            } else {
                val normalized = normalizeSegments(parsed)
                Result.success(normalized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Detects probable locale from subtitle text (Arabic, Japanese, Chinese, Russian, etc.).
     */
    fun detectScriptLocale(segments: List<TranscriptionSegment>): java.util.Locale? {
        val sample = segments.take(25).joinToString(" ") { it.text }
        if (sample.isBlank()) return null

        for (ch in sample) {
            val code = ch.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF) {
                return java.util.Locale("ar", "SA")
            }
        }
        for (ch in sample) {
            val code = ch.code
            if (code in 0x3040..0x309F || code in 0x30A0..0x30FF) {
                return java.util.Locale.JAPANESE
            }
        }
        for (ch in sample) {
            val code = ch.code
            if (code in 0x4E00..0x9FFF) {
                return java.util.Locale.CHINESE
            }
        }
        for (ch in sample) {
            val code = ch.code
            if (code in 0x0400..0x04FF) {
                return java.util.Locale("ru", "RU")
            }
        }
        return null
    }

    /**
     * Checks if a string contains Arabic characters.
     */
    fun containsArabicCharacters(text: String): Boolean {
        for (char in text) {
            val code = char.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF) {
                return true
            }
        }
        return false
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

