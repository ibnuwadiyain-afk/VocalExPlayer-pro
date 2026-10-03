package com.example.whisper

import java.nio.charset.StandardCharsets

object WhisperTokenizer {

    // Special Token Constants
    const val END_OF_TEXT = 50257
    const val START_OF_TRANSCRIPT = 50258
    const val TRANSCRIBE = 50359
    const val TRANSLATE = 50358
    const val NO_SPEECH = 50362
    const val NO_TIMESTAMPS = 50363
    const val TIMESTAMP_BEGIN = 50364 // <|0.00|>
    const val TIMESTAMP_END = 51864   // <|30.00|> (50364 + 1500 tokens = 51864)

    // Language tokens mapping (ISO 639-1 code to token ID)
    val LANGUAGE_TOKENS: Map<String, Int> = mapOf(
        "en" to 50259, // English
        "zh" to 50260, // Chinese
        "de" to 50261, // German
        "es" to 50262, // Spanish
        "ru" to 50263, // Russian
        "ko" to 50264, // Korean
        "fr" to 50265, // French
        "ja" to 50266, // Japanese
        "pt" to 50267, // Portuguese
        "tr" to 50268, // Turkish
        "pl" to 50269, // Polish
        "ca" to 50270, // Catalan
        "nl" to 50271, // Dutch
        "ar" to 50272, // Arabic
        "sv" to 50273, // Swedish
        "it" to 50274, // Italian
        "id" to 50275, // Indonesian
        "hi" to 50276, // Hindi
        "fi" to 50277, // Finnish
        "vi" to 50278, // Vietnamese
        "he" to 50279, // Hebrew
        "uk" to 50280, // Ukrainian
        "el" to 50281, // Greek
        "ms" to 50282, // Malay
        "cs" to 50283, // Czech
        "ro" to 50284, // Romanian
        "da" to 50285, // Danish
        "hu" to 50286, // Hungarian
        "ta" to 50287, // Tamil
        "no" to 50288, // Norwegian
        "th" to 50289, // Thai
        "ur" to 50290, // Urdu
        "hr" to 50291, // Croatian
        "bg" to 50292, // Bulgarian
        "lt" to 50293, // Lithuanian
        "fa" to 50300  // Persian
    )

    data class SupportedLanguage(
        val code: String,
        val displayName: String,
        val nativeName: String,
        val tokenId: Int?
    )

    val AVAILABLE_LANGUAGES: List<SupportedLanguage> = listOf(
        SupportedLanguage("auto", "Auto Detect", "تلقائي", null),
        SupportedLanguage("ar", "Arabic", "العربية", 50272),
        SupportedLanguage("en", "English", "English", 50259),
        SupportedLanguage("es", "Spanish", "Español", 50262),
        SupportedLanguage("fr", "French", "Français", 50265),
        SupportedLanguage("de", "German", "Deutsch", 50261),
        SupportedLanguage("it", "Italian", "Italiano", 50274),
        SupportedLanguage("pt", "Portuguese", "Português", 50267),
        SupportedLanguage("ru", "Russian", "Русский", 50263),
        SupportedLanguage("tr", "Turkish", "Türkçe", 50268),
        SupportedLanguage("ja", "Japanese", "日本語", 50266),
        SupportedLanguage("zh", "Chinese", "中文", 50260)
    )

    fun getLanguageToken(langCode: String): Int? {
        if (langCode == "auto") return null
        return LANGUAGE_TOKENS[langCode.lowercase()]
    }

    fun isTimestampToken(tokenId: Int): Boolean {
        return tokenId in TIMESTAMP_BEGIN..TIMESTAMP_END
    }

    /**
     * Converts a Whisper timestamp token ID to seconds offset from start of 30-sec window.
     * Each timestamp token represents 0.02 seconds (20ms) increments.
     */
    fun timestampTokenToSeconds(tokenId: Int): Float {
        return (tokenId - TIMESTAMP_BEGIN) * 0.02f
    }

    /**
     * Converts seconds within a 30s window to the nearest Whisper timestamp token ID.
     */
    fun secondsToTimestampToken(seconds: Float): Int {
        val clampedSec = seconds.coerceIn(0f, 30.0f)
        val tokenOffset = (clampedSec / 0.02f).toInt()
        return (TIMESTAMP_BEGIN + tokenOffset).coerceIn(TIMESTAMP_BEGIN, TIMESTAMP_END)
    }

    /**
     * Decodes a list of Byte-Level BPE or UTF-8 token IDs into a clean readable string.
     */
    fun decodeTokens(tokenIds: List<Int>): String {
        val byteArray = mutableListOf<Byte>()

        for (token in tokenIds) {
            if (token < 256) {
                byteArray.add(token.toByte())
            } else if (!isTimestampToken(token) && token < START_OF_TRANSCRIPT) {
                // If the token corresponds to individual bytes or standard ASCII ranges
                val lowByte = (token and 0xFF).toByte()
                byteArray.add(lowByte)
            }
        }

        return String(byteArray.toByteArray(), StandardCharsets.UTF_8).trim()
    }
}
