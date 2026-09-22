package com.example.ui.locale

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the current UI language preference for the app.
 */
object LanguageManager {

    private const val PREFS_NAME = "vocal_extractor_locale_prefs"
    private const val KEY_LANGUAGE = "selected_ui_language"

    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedCode = prefs?.getString(KEY_LANGUAGE, null)
            val initial = if (savedCode != null) {
                AppLanguage.fromCode(savedCode)
            } else {
                // Check device default language
                val systemLang = java.util.Locale.getDefault().language
                AppLanguage.fromCode(systemLang)
            }
            _currentLanguage.value = initial
        }
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
    }
}
