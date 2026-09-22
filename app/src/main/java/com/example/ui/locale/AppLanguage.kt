package com.example.ui.locale

import androidx.compose.ui.unit.LayoutDirection

/**
 * Supported UI languages for VocalExtractor.
 */
enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean = false
) {
    ENGLISH(
        code = "en",
        nativeName = "English",
        englishName = "English",
        isRtl = false
    ),
    ARABIC(
        code = "ar",
        nativeName = "العربية",
        englishName = "Arabic",
        isRtl = true
    ),
    SPANISH(
        code = "es",
        nativeName = "Español",
        englishName = "Spanish",
        isRtl = false
    ),
    FRENCH(
        code = "fr",
        nativeName = "Français",
        englishName = "French",
        isRtl = false
    ),
    GERMAN(
        code = "de",
        nativeName = "Deutsch",
        englishName = "German",
        isRtl = false
    );

    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
