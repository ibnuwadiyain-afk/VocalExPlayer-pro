package com.example.dubbing.translation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.whisper.TranscriptionSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object DubbingTranslationManager {

    private const val TAG = "DubbingTranslation"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Checks if the device runs Android 12+ (API 31) where TranslationManager exists.
     */
    fun isSystemTranslationSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Checks if on-device system translation service is available on this device.
     */
    fun isSystemTranslationAvailable(context: Context): Boolean {
        if (!isSystemTranslationSupported()) return false
        return try {
            val tm = context.getSystemService(android.view.translation.TranslationManager::class.java)
            tm != null
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Translates a single text string using the specified service.
     * Falls back to Free Online Translation if Android system service is requested but unavailable.
     */
    suspend fun translateText(
        context: Context,
        text: String,
        sourceLang: String = "auto",
        targetLang: String,
        service: TranslationServiceType = TranslationServiceType.ONLINE_FREE
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(text)

        when (service) {
            TranslationServiceType.NONE -> Result.success(text)

            TranslationServiceType.ANDROID_SYSTEM -> {
                if (isSystemTranslationSupported() && isSystemTranslationAvailable(context)) {
                    val systemResult = translateWithAndroidSystem(context, trimmed, sourceLang, targetLang)
                    if (systemResult.isSuccess) {
                        systemResult
                    } else {
                        Log.w(
                            TAG,
                            "Android System translation failed (${systemResult.exceptionOrNull()?.message}). Falling back to Free Online service."
                        )
                        translateWithFreeOnline(trimmed, sourceLang, targetLang)
                    }
                } else {
                    Log.i(TAG, "Android System translation not available on this OS version. Falling back to Free Online service.")
                    translateWithFreeOnline(trimmed, sourceLang, targetLang)
                }
            }

            TranslationServiceType.ONLINE_FREE -> {
                translateWithFreeOnline(trimmed, sourceLang, targetLang)
            }
        }
    }

    /**
     * Translates a list of transcription subtitle segments with progress reporting.
     * Preserves exact cue timestamps and indexes.
     */
    suspend fun translateSegments(
        context: Context,
        segments: List<TranscriptionSegment>,
        sourceLang: String = "auto",
        targetLang: String,
        service: TranslationServiceType = TranslationServiceType.ONLINE_FREE,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<List<TranscriptionSegment>> = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext Result.success(emptyList())
        if (service == TranslationServiceType.NONE) return@withContext Result.success(segments)

        val totalCues = segments.size
        val translatedList = ArrayList<TranscriptionSegment>(totalCues)
        var failureCount = 0

        for ((index, segment) in segments.withIndex()) {
            val progressVal = (index.toFloat() / totalCues).coerceIn(0f, 1f)
            onProgress(
                progressVal,
                "Translating cue ${index + 1}/$totalCues (${targetLang.uppercase()})..."
            )

            if (segment.text.isBlank()) {
                translatedList.add(segment)
                continue
            }

            val result = translateText(
                context = context,
                text = segment.text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                service = service
            )

            if (result.isSuccess) {
                val translatedText = result.getOrNull()?.trim() ?: segment.text
                translatedList.add(segment.copy(text = translatedText))
            } else {
                failureCount++
                Log.w(TAG, "Failed translating cue #${segment.index}: '${segment.text}'")
                translatedList.add(segment) // Keep original text on individual cue failure
            }

            // Slight throttle for online translation to be polite and avoid bursts
            if (service == TranslationServiceType.ONLINE_FREE && totalCues > 5) {
                delay(30)
            }
        }

        onProgress(1.0f, "Translation completed ($totalCues cues)")

        if (failureCount == totalCues && totalCues > 0) {
            Result.failure(IllegalStateException("Translation service failed for all subtitle cues."))
        } else {
            Result.success(translatedList)
        }
    }

    /**
     * Translates text via Android 12+ On-Device TranslationManager.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun translateWithAndroidSystem(
        context: Context,
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> {
        val tm = try {
            context.getSystemService(android.view.translation.TranslationManager::class.java)
        } catch (e: Exception) {
            return Result.failure(e)
        } ?: return Result.failure(IllegalStateException("TranslationManager service null"))

        return suspendCancellableCoroutine { continuation ->
            try {
                val cleanSource = if (sourceLang == "auto" || sourceLang.isBlank()) "en" else sourceLang
                val sourceULocale = android.icu.util.ULocale.forLanguageTag(cleanSource)
                val targetULocale = android.icu.util.ULocale.forLanguageTag(targetLang)

                val sourceSpec = android.view.translation.TranslationSpec(
                    sourceULocale,
                    android.view.translation.TranslationSpec.DATA_FORMAT_TEXT
                )
                val targetSpec = android.view.translation.TranslationSpec(
                    targetULocale,
                    android.view.translation.TranslationSpec.DATA_FORMAT_TEXT
                )

                val translationContext = android.view.translation.TranslationContext.Builder(sourceSpec, targetSpec).build()
                val executor = ContextCompat.getMainExecutor(context)

                tm.createOnDeviceTranslator(translationContext, executor) { translator ->
                    if (translator == null) {
                        if (continuation.isActive) {
                            continuation.resumeWith(
                                Result.success(
                                    Result.failure(IllegalStateException("On-device translator not created (models may not be installed)"))
                                )
                            )
                        }
                        return@createOnDeviceTranslator
                    }

                    try {
                        val request = android.view.translation.TranslationRequest.Builder()
                            .setTranslationRequestValues(
                                listOf(android.view.translation.TranslationRequestValue.forText(text))
                            )
                            .build()

                        translator.translate(request, null, executor) { response ->
                            try {
                                if (response.translationStatus == android.view.translation.TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                                    val values = response.translationResponseValues
                                    val translated = values.get(0)?.text?.toString() ?: text
                                    if (continuation.isActive) {
                                        continuation.resumeWith(Result.success(Result.success(translated)))
                                    }
                                } else {
                                    if (continuation.isActive) {
                                        continuation.resumeWith(
                                            Result.success(
                                                Result.failure(IllegalStateException("System translation status code: ${response.translationStatus}"))
                                            )
                                        )
                                    }
                                }
                            } finally {
                                try {
                                    translator.destroy()
                                } catch (_: Throwable) {}
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            translator.destroy()
                        } catch (_: Throwable) {}
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Result.failure(e)))
                        }
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(Result.failure(e)))
                }
            }
        }
    }

    /**
     * Translates text via free online translation endpoints (Google GTX with MyMemory fallback).
     */
    private suspend fun translateWithFreeOnline(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // Attempt 1: Google Translate GTX endpoint (free, neural, zero-api-key)
        try {
            val src = if (sourceLang.isBlank() || sourceLang == "auto") "auto" else sourceLang
            val encodedQuery = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$src&tl=$targetLang&dt=t&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
                .header("Accept", "*/*")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isNotBlank()) {
                    val jsonArray = JSONArray(bodyStr)
                    val sentences = jsonArray.optJSONArray(0)
                    if (sentences != null && sentences.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until sentences.length()) {
                            val sentence = sentences.optJSONArray(i)
                            val part = sentence?.optString(0)
                            if (!part.isNullOrEmpty()) {
                                sb.append(part)
                            }
                        }
                        val finalTrans = unescapeHtml(sb.toString().trim())
                        if (finalTrans.isNotBlank()) {
                            return@withContext Result.success(finalTrans)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google GTX translation attempt failed: ${e.message}")
        }

        // Attempt 2: MyMemory Translation API fallback (free, public)
        try {
            val src = if (sourceLang.isBlank() || sourceLang == "auto") "en" else sourceLang
            val encodedQuery = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encodedQuery&langpair=$src|$targetLang"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isNotBlank()) {
                    val jsonObj = JSONObject(bodyStr)
                    val responseData = jsonObj.optJSONObject("responseData")
                    val translatedText = responseData?.optString("translatedText")
                    if (!translatedText.isNullOrBlank()) {
                        val finalTrans = unescapeHtml(translatedText.trim())
                        return@withContext Result.success(finalTrans)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MyMemory fallback failed: ${e.message}")
        }

        // Fallback: Return original text if network was unavailable
        Result.failure(IllegalStateException("Free translation services unavailable"))
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
