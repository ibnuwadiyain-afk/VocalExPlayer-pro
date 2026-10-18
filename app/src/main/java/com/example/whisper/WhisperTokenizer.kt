package com.example.whisper

import android.content.Context
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

    // Standard Whisper non-speech symbols to suppress in tiny and base models
    val NON_SPEECH_SUPPRESS_TOKENS: Set<Int> = hashSetOf(
        1, 2, 7, 8, 9, 10, 14, 25, 26, 27, 28, 29, 31, 58, 59, 60, 61, 62, 63,
        90, 91, 92, 93, 359, 503, 522, 542, 873, 893, 902, 918, 922, 936, 950,
        985, 1007, 1030, 1060, 1061, 1071, 1072, 1073, 1078, 1079, 1080
    )

    // Common hallucinated subtitles that small models produce on silence, music or noise
    private val HALLUCINATION_PHRASES = setOf(
        "thank you",
        "thank you.",
        "thank you for watching",
        "thank you for watching.",
        "thanks for watching",
        "thanks for watching.",
        "please subscribe",
        "please subscribe.",
        "subscribe to my channel",
        "subtitles by",
        "transcribed by",
        "sous-titres réalisés par",
        "شكرا للمشاهدة",
        "شكراً للمشاهدة",
        "شكرا لكم على المشاهدة",
        "شكراً لكم على المشاهدة",
        "اشترك في القناة",
        "لا تنسى الاشتراك",
        "you",
        "bye",
        "amara.org",
        "translated by"
    )

    fun isHallucination(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return true
        val normalized = trimmed.lowercase().replace(Regex("[.!?,،؛:\"'\\[\\]()]"), "").trim()
        if (normalized.isEmpty()) return true
        if (normalized in HALLUCINATION_PHRASES) return true

        // Detect single repeated character (e.g. "aaaaa" or ".....")
        if (normalized.length >= 4 && normalized.all { it == normalized[0] }) return true

        // Detect repeated 1-word or 2-word loop in string (e.g. "you you you you" or "and the and the and the")
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 3) {
            val allSame = words.all { it == words[0] }
            if (allSame) return true
        }
        if (words.size >= 4 && words.size % 2 == 0) {
            val isTwoWordLoop = (0 until words.size step 2).all {
                words[it] == words[0] && words[it + 1] == words[1]
            }
            if (isTwoWordLoop) return true
        }

        return false
    }

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

    private var vocabulary: Array<String>? = null

    // GPT-2 byte-to-unicode decoder table mapping the 256 BPE Unicode chars to bytes
    private val byteDecoder: Map<Char, Byte> by lazy {
        val bs = mutableListOf<Int>()
        for (i in '!'.code..'~'.code) bs.add(i)
        for (i in '¡'.code..'¬'.code) bs.add(i)
        for (i in '®'.code..'ÿ'.code) bs.add(i)

        val cs = bs.toMutableList()
        var n = 0
        for (b in 0 until 256) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        val map = HashMap<Char, Byte>(256)
        for (idx in 0 until cs.size) {
            map[cs[idx].toChar()] = bs[idx].toByte()
        }
        map
    }

    @Synchronized
    fun loadVocabulary(context: Context) {
        if (vocabulary != null) return
        try {
            context.assets.open("models/whisper_vocab.txt").bufferedReader(StandardCharsets.UTF_8).use { reader ->
                val list = ArrayList<String>(51865)
                var line = reader.readLine()
                while (line != null) {
                    list.add(line)
                    line = reader.readLine()
                }
                vocabulary = list.toTypedArray()
            }
        } catch (e: Exception) {
            android.util.Log.e("WhisperTokenizer", "Failed to load vocabulary: ${e.message}")
        }
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
        val vocab = vocabulary
        val byteBuffer = java.io.ByteArrayOutputStream()

        for (token in tokenIds) {
            if (token >= START_OF_TRANSCRIPT || isTimestampToken(token)) {
                continue
            }
            if (vocab != null && token in vocab.indices) {
                val tokenStr = vocab[token]
                for (ch in tokenStr) {
                    val b = byteDecoder[ch]
                    if (b != null) {
                        byteBuffer.write(b.toInt())
                    } else {
                        val utf8 = ch.toString().toByteArray(StandardCharsets.UTF_8)
                        byteBuffer.write(utf8)
                    }
                }
            } else if (token in 32..126) {
                byteBuffer.write(token)
            }
        }

        val decoded = String(byteBuffer.toByteArray(), StandardCharsets.UTF_8).trim()
        return decoded.replace(Regex("\\s+"), " ")
    }
}
