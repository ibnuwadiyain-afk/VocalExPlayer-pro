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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
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
    onTranslationErrorChange: (String?) -> Unit,
    localServerHost: String = "127.0.0.1",
    onLocalServerHostChange: (String) -> Unit = {},
    localServerPort: Int = 5000,
    onLocalServerPortChange: (Int) -> Unit = {},
    localServerEndpoint: String = "/translate",
    onLocalServerEndpointChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val coroutineScope = rememberCoroutineScope()
    var isPreviewExpanded by remember { mutableStateOf(false) }

    // Local server connectivity test state
    var isTestingServer by remember { mutableStateOf(false) }
    var testServerResult by remember { mutableStateOf<Result<String>?>(null) }

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

                // Service Option 2: Local Translation Server (App installed on this phone)
                TranslationServiceCard(
                    title = "Local Translation Server App",
                    description = "Request translation from local server app installed on this phone via localhost & port",
                    badge = "Localhost • Private",
                    isSelected = translationService == TranslationServiceType.LOCAL_SERVER,
                    icon = Icons.Default.Dns,
                    onSelect = {
                        onTranslationServiceChange(TranslationServiceType.LOCAL_SERVER)
                        testServerResult = null
                    },
                    testTag = "service_local_server"
                )

                if (translationService == TranslationServiceType.LOCAL_SERVER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LocalServerConfigCard(
                        hostOrUrl = localServerHost,
                        onHostOrUrlChange = {
                            onLocalServerHostChange(it)
                            testServerResult = null
                        },
                        port = localServerPort,
                        onPortChange = {
                            onLocalServerPortChange(it)
                            testServerResult = null
                        },
                        endpoint = localServerEndpoint,
                        onEndpointChange = {
                            onLocalServerEndpointChange(it)
                            testServerResult = null
                        },
                        targetLanguage = targetLocale.language,
                        isTesting = isTestingServer,
                        testResult = testServerResult,
                        onTestConnection = {
                            coroutineScope.launch {
                                isTestingServer = true
                                testServerResult = null
                                val res = DubbingTranslationManager.testLocalServerConnection(
                                    hostOrUrl = localServerHost,
                                    port = localServerPort,
                                    endpoint = localServerEndpoint,
                                    targetLang = targetLocale.language
                                )
                                isTestingServer = false
                                testServerResult = res
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Service Option 3: Android System Service
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
                                                localServerHost = localServerHost,
                                                localServerPort = localServerPort,
                                                localServerEndpoint = localServerEndpoint,
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

@Composable
private fun LocalServerConfigCard(
    hostOrUrl: String,
    onHostOrUrlChange: (String) -> Unit,
    port: Int,
    onPortChange: (Int) -> Unit,
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    targetLanguage: String,
    isTesting: Boolean,
    testResult: Result<String>?,
    onTestConnection: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_server_config_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Localhost Server Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Port $port",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connects to a translation server app running on this phone (e.g. LibreTranslate, Argos, Ollama, Termux).",
                fontSize = 10.sp,
                color = TextSecondary,
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick App Presets
            Text(
                text = "POPULAR APP PRESETS",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = AccentTeal,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetChip(
                    label = "LibreTranslate",
                    sub = ":5000",
                    isSelected = port == 5000 && endpoint == "/translate",
                    onClick = {
                        onHostOrUrlChange("127.0.0.1")
                        onPortChange(5000)
                        onEndpointChange("/translate")
                    },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    label = "LocalAI / Custom",
                    sub = ":8080",
                    isSelected = port == 8080 && endpoint == "/translate",
                    onClick = {
                        onHostOrUrlChange("127.0.0.1")
                        onPortChange(8080)
                        onEndpointChange("/translate")
                    },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    label = "Ollama LLM",
                    sub = ":11434",
                    isSelected = port == 11434 && endpoint == "/api/generate",
                    onClick = {
                        onHostOrUrlChange("127.0.0.1")
                        onPortChange(11434)
                        onEndpointChange("/api/generate")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Host / URL Input
            Text(
                text = "Server Host / Localhost URL",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            OutlinedTextField(
                value = hostOrUrl,
                onValueChange = onHostOrUrlChange,
                placeholder = { Text("127.0.0.1 or localhost", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_server_host_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.35f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Fast host quick chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("127.0.0.1", "localhost", "10.0.2.2").forEach { h ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (hostOrUrl == h) AccentTeal.copy(alpha = 0.25f) else DarkSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onHostOrUrlChange(h) }
                    ) {
                        Text(
                            text = h,
                            fontSize = 9.sp,
                            color = if (hostOrUrl == h) AccentTeal else TextSecondary,
                            fontWeight = if (hostOrUrl == h) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Port and Endpoint Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Port
                Column(modifier = Modifier.weight(0.42f)) {
                    Text(
                        text = "Port Number",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    OutlinedTextField(
                        value = if (port == 0) "" else port.toString(),
                        onValueChange = { newVal ->
                            val clean = newVal.filter { it.isDigit() }.take(5)
                            onPortChange(clean.toIntOrNull() ?: 0)
                        },
                        placeholder = { Text("5000", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_server_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.35f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Endpoint Path
                Column(modifier = Modifier.weight(0.58f)) {
                    Text(
                        text = "Endpoint Path",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = onEndpointChange,
                        placeholder = { Text("/translate", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_server_endpoint_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.35f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Target URL preview
            val resolvedUrl = remember(hostOrUrl, port, endpoint) {
                DubbingTranslationManager.buildLocalServerUrl(hostOrUrl, port, endpoint)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "URL: $resolvedUrl",
                        fontSize = 9.5.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test Connection Button & Result
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isTesting && port > 0,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_test_local_server_connection")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentTeal
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Testing Connection...", fontSize = 11.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Test Local Server Connection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(6.dp))
                if (testResult.isSuccess) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentTeal.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = testResult.getOrNull() ?: "Connected to local translation server!",
                                color = AccentTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
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
                                text = testResult.exceptionOrNull()?.message ?: "Could not connect to local server",
                                color = Color(0xFFFF6B6B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    sub: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) AccentTeal.copy(alpha = 0.20f) else DarkSurface,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(1.dp, AccentTeal, RoundedCornerShape(6.dp))
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AccentTeal else TextPrimary,
                maxLines = 1
            )
            Text(
                text = sub,
                fontSize = 8.5.sp,
                color = if (isSelected) AccentTeal else TextSecondary
            )
        }
    }
}
