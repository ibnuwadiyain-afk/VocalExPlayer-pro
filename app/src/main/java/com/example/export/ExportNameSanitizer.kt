package com.example.export

/**
 * Sanitizes exported filenames while providing full native support for Arabic characters,
 * Unicode letters, numerals, and valid punctuation.
 */
object ExportNameSanitizer {

    /**
     * Sanitizes export file titles by removing only illegal filesystem characters:
     * / \ ? % * : | " < > and ASCII control characters (0x00-0x1F),
     * while fully preserving Arabic letters, Latin letters, numbers, spaces, dots, and hyphens.
     */
    fun sanitizeTitle(rawTitle: String, defaultTitle: String = "vocal_export"): String {
        if (rawTitle.isBlank()) return defaultTitle

        // Replace illegal filesystem characters: / \ ? % * : | " < > and control chars
        val cleaned = rawTitle
            .replace(Regex("[/\\\\?%*:|\"<>\u0000-\u001F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Restrict to max 80 characters to prevent filesystem truncation on multi-byte UTF-8
        val truncated = if (cleaned.length > 80) cleaned.substring(0, 80).trim() else cleaned
        return if (truncated.isNotBlank()) truncated else defaultTitle
    }
}
