package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dubbing.translation.DubbingTranslationManager
import com.example.dubbing.translation.TranslationServiceType
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.whisper.SrtGenerator
import com.example.whisper.TranscriptionSegment
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TranslationConfigSection(
    enableTranslation: Boolean,
    onEnableTranslationChange: (Boolean) -> Unit,
    translationService: TranslationServiceType,
    onTranslationServiceChange: (TranslationServiceType) -> Unit,
    targetLocale: Locale,
    onTargetLocaleChange: ((Locale) -> Unit)? = null,
    videoTitle: String = "video",
    segments: List<TranscriptionSegment>,
    translatedSegments: List<TranscriptionSegment>?,
    onTranslatedSegmentsChange: (List<TranscriptionSegment>?) -> Unit,
    isTranslating: Boolean,
    onIsTranslatingChange: (Boolean) -> Unit,
    translationProgressText: String,
    onTranslationProgressTextChange: (String) -> Unit,
    translationError: String?,
    onTranslationErrorChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val coroutineScope = rememberCoroutineScope()
    var isPreviewExpanded by remember { mutableStateOf(false) }

    val isSystemSupported = remember {
        DubbingTranslationManager.isSystemTranslationSupported()
    }
    val isSystemAvailable = remember {
        DubbingTranslationManager.isSystemTranslationAvailable(context)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header & Toggle Switch
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (enableTranslation) DarkSurfaceVariant else DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEnableTranslationChange(!enableTranslation) }
                .then(
                    if (enableTranslation) Modifier.border(1.dp, AccentTeal.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    else Modifier
                )
                .testTag("translation_toggle_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (enableTranslation) AccentTeal.copy(alpha = 0.2f) else DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = if (enableTranslation) AccentTeal else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Translate Transcript",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            if (enableTranslation) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentTeal.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = targetLocale.displayLanguage.uppercase(),
                                        color = AccentTeal,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (enableTranslation) "Translate subtitles to speak in ${targetLocale.displayLanguage}"
                            else "Dub directly in original transcription language",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Switch(
                    checked = enableTranslation,
                    onCheckedChange = { onEnableTranslationChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentTeal,
                        checkedTrackColor = AccentTeal.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkBackground
                    ),
                    modifier = Modifier.testTag("translation_switch")
                )
            }
        }

        // Expandable Translation Settings
        AnimatedVisibility(
            visible = enableTranslation,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                // Translation Service Selector
                Text(
                    text = "TRANSLATION ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Service Option 1: Free Online
                TranslationServiceCard(
                    title = "Free Online Translation",
                    description = "Instant neural translation across 100+ languages (no setup required)",
                    badge = "Universal • 100+ Langs",
                    isSelected = translationService == TranslationServiceType.ONLINE_FREE,
                    icon = Icons.Default.Public,
                    onSelect = { onTranslationServiceChange(TranslationServiceType.ONLINE_FREE) },
                    testTag = "service_online_free"
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Service Option 2: Android System Service
                val systemDescription = when {
                    isSystemAvailable -> "Android On-Device TranslationManager active"
                    isSystemSupported -> "Android 12+ service (auto-falls back if language pack not downloaded)"
                    else -> "Requires Android 12+ (auto-falls back to Free Online)"
                }
                val systemBadge = if (isSystemAvailable) "Android 12+ On-Device" else "System Service"

                TranslationServiceCard(
                    title = "Built-in Android System Service",
                    description = systemDescription,
                    badge = systemBadge,
                    isSelected = translationService == TranslationServiceType.ANDROID_SYSTEM,
                    icon = Icons.Default.PhoneAndroid,
                    onSelect = { onTranslationServiceChange(TranslationServiceType.ANDROID_SYSTEM) },
                    testTag = "service_android_system"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Target Language Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Target: ${targetLocale.displayLanguage} (${targetLocale.language.uppercase()})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Matches TTS Voice",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SAF Launchers for Import and Export of Translated SRT
                val srtImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val result = SrtGenerator.parseAndNormalizeSrt(context, uri)
                        if (result.isSuccess) {
                            val cues = result.getOrThrow()
                            onTranslatedSegmentsChange(cues)
                            onEnableTranslationChange(true)
                            onTranslationErrorChange(null)
                            onTranslationProgressTextChange("Imported ${cues.size} translated subtitle cues from file")
                            val detectedLocale = SrtGenerator.detectScriptLocale(cues)
                            if (detectedLocale != null && onTargetLocaleChange != null) {
                                onTargetLocaleChange(detectedLocale)
                            }
                            Toast.makeText(
                                context,
                                AppStrings.importedSrtSuccess(lang, cues.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val err = result.exceptionOrNull()?.message ?: AppStrings.noCuesFoundInSrt(lang)
                            onTranslationErrorChange(err)
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val sanitizedBaseTitle = remember(videoTitle) {
                    com.example.export.ExportNameSanitizer.sanitizeTitle(videoTitle)
                }

                val srtExportDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri: Uri? ->
                    if (uri != null && !translatedSegments.isNullOrEmpty()) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                SrtGenerator.writeSrtToOutputStream(translatedSegments, os)
                            }
                            Toast.makeText(context, "Saved translated SRT to file", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                fun exportAndShareTranslatedSrt() {
                    if (translatedSegments.isNullOrEmpty()) return
                    try {
                        val file = SrtGenerator.exportTranslatedSrtFile(
                            context = context,
                            videoTitle = videoTitle,
                            targetLanguageCode = targetLocale.language,
                            segments = translatedSegments
                        )
                        SrtGenerator.shareSrtFile(
                            context = context,
                            file = file,
                            chooserTitle = "${AppStrings.exportTranslatedSrt(lang)} (${file.name})"
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                // Translate & Preview Action Button
                if (isTranslating) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentTeal
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = translationProgressText.ifBlank { "Translating subtitle cues..." },
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (segments.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            onIsTranslatingChange(true)
                                            onTranslationErrorChange(null)
                                            onTranslationProgressTextChange("Starting translation...")

                                            val result = DubbingTranslationManager.translateSegments(
                                                context = context,
                                                segments = segments,
                                                sourceLang = "auto",
                                                targetLang = targetLocale.language,
                                                service = translationService,
                                                onProgress = { _, msg ->
                                                    onTranslationProgressTextChange(msg)
                                                }
                                            )

                                            onIsTranslatingChange(false)
                                            if (result.isSuccess) {
                                                onTranslatedSegmentsChange(result.getOrNull())
                                            } else {
                                                onTranslationErrorChange(
                                                    result.exceptionOrNull()?.message ?: "Translation failed"
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_translate_preview")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (translatedSegments != null) "Re-translate (${targetLocale.displayLanguage})"
                                        else "Translate Cues",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Import Translated SRT Button
                            OutlinedButton(
                                onClick = {
                                    srtImportLauncher.launch(arrayOf("*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_translated_srt")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppStrings.importTranslatedSrt(lang),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (translatedSegments != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Export Translated SRT Button
                                Button(
                                    onClick = { exportAndShareTranslatedSrt() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_export_translated_srt")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AppStrings.exportTranslatedSrt(lang),
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = { isPreviewExpanded = !isPreviewExpanded },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    modifier = Modifier.testTag("btn_toggle_cue_preview")
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isPreviewExpanded) "Hide" else "View Cues",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Message if any
                if (!translationError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF3E1C1C),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = translationError,
                                color = Color(0xFFFF6B6B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Translation Status Banner
                if (translatedSegments != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentTeal.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ready: ${translatedSegments.size} cues for ${targetLocale.displayLanguage} dubbing",
                                        color = AccentTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        onTranslatedSegmentsChange(null)
                                        onTranslationProgressTextChange("")
                                    },
                                    modifier = Modifier.testTag("btn_reset_translation")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Reset", color = TextSecondary, fontSize = 10.sp)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { exportAndShareTranslatedSrt() },
                                    modifier = Modifier.testTag("banner_share_translated_srt")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share .srt", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = {
                                        val fileName = "${sanitizedBaseTitle}_translated_${targetLocale.language}.srt"
                                        srtExportDocumentLauncher.launch(fileName)
                                    },
                                    modifier = Modifier.testTag("banner_save_translated_srt")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save .srt to device", color = AccentTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Translated Cues Comparison List
                    AnimatedVisibility(
                        visible = isPreviewExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "TRANSLATED CUES COMPARISON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .padding(6.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(translatedSegments) { cue ->
                                        val originalCue = segments.firstOrNull { it.index == cue.index }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "#${cue.index} (${cue.startFormatted})",
                                                    fontSize = 9.sp,
                                                    color = AccentTeal,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (originalCue != null && originalCue.text != cue.text) {
                                                Text(
                                                    text = "Original: ${originalCue.text}",
                                                    fontSize = 10.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                text = "Translated: ${cue.text}",
                                                fontSize = 11.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationServiceCard(
    title: String,
    description: String,
    badge: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) DarkSurfaceVariant else DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .then(
                if (isSelected) Modifier.border(1.5.dp, AccentTeal, RoundedCornerShape(10.dp))
                else Modifier
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentTeal.copy(alpha = 0.2f) else DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) AccentTeal else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.85f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) AccentTeal.copy(alpha = 0.15f) else DarkBackground
                    ) {
                        Text(
                            text = badge,
                            fontSize = 8.sp,
                            color = if (isSelected) AccentTeal else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
