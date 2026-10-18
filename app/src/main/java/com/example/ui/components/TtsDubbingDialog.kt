package com.example.ui.components

import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoItemEntity
import com.example.dubbing.DubbingConfig
import com.example.dubbing.DubbingMixMode
import com.example.dubbing.OverlapResolution
import com.example.dubbing.TtsDubbingEngine
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsDubbingDialog(
    video: VideoItemEntity,
    segments: List<TranscriptionSegment>,
    onDismiss: () -> Unit,
    onConfirmDubbing: (DubbingConfig) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scrollState = rememberScrollState()

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // 3rd-Party & System TTS Engines State
    var installedEngines by remember { mutableStateOf<List<TtsDubbingEngine.TtsEngineInfo>>(emptyList()) }
    var selectedEngine by remember { mutableStateOf<TtsDubbingEngine.TtsEngineInfo?>(null) }
    var isEngineDropdownExpanded by remember { mutableStateOf(false) }
    var isSwitchingEngine by remember { mutableStateOf(false) }

    var availableLocales by remember { mutableStateOf<List<Locale>>(emptyList()) }
    var selectedLocale by remember { mutableStateOf(Locale.getDefault()) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var mixMode by remember { mutableStateOf(DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC) }
    var duckingVolume by remember { mutableFloatStateOf(0.20f) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Overlap Prevention & Pacing State
    var preventOverlap by remember { mutableStateOf(true) }
    var autoFitCues by remember { mutableStateOf(true) }
    var minGapBetweenCuesMs by remember { mutableStateOf(80L) }
    var overlapResolution by remember { mutableStateOf(OverlapResolution.AUTO_PACE) }

    // Translation State
    var enableTranslation by remember { mutableStateOf(false) }
    var translationService by remember { mutableStateOf(TranslationServiceType.ONLINE_FREE) }
    var localServerHost by remember { mutableStateOf("127.0.0.1") }
    var localServerPort by remember { mutableStateOf(5000) }
    var localServerEndpoint by remember { mutableStateOf("/translate") }
    var translatedSegments by remember { mutableStateOf<List<TranscriptionSegment>?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationProgressText by remember { mutableStateOf("") }
    var translationError by remember { mutableStateOf<String?>(null) }

    // Detect initial locale from first subtitle text
    LaunchedEffect(segments) {
        val firstText = segments.firstOrNull { it.text.isNotBlank() }?.text ?: ""
        if (containsArabicCharacters(firstText)) {
            selectedLocale = Locale("ar", "SA")
        }
    }

    // Initialize TTS engine (discovering all 3rd-party installed engines and system default)
    LaunchedEffect(Unit) {
        val engines = TtsDubbingEngine.getInstalledTtsEngines(context)
        installedEngines = engines
        val defaultEng = engines.firstOrNull { it.isDefault } ?: engines.firstOrNull()
        selectedEngine = defaultEng

        val tts = TtsDubbingEngine.createTts(context, defaultEng?.packageName?.ifBlank { null })
        if (tts != null) {
            ttsInstance = tts
            val refreshed = TtsDubbingEngine.getInstalledTtsEngines(context, tts)
            if (refreshed.isNotEmpty()) {
                installedEngines = refreshed
            }
            val locales = TtsDubbingEngine.getAvailableLocales(tts)
            availableLocales = locales
            locales.firstOrNull { it.language == selectedLocale.language }?.let {
                selectedLocale = it
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (_: Exception) {}
        }
    }

    val samplePreviewText = remember(segments, selectedLocale, enableTranslation, translatedSegments) {
        if (enableTranslation && !translatedSegments.isNullOrEmpty()) {
            translatedSegments?.firstOrNull { it.text.isNotBlank() }?.text
                ?: if (selectedLocale.language == "ar") "مرحباً بكم في الدبلجة الصوتية الذكية"
                else "Welcome to the AI voice dubbing preview."
        } else {
            segments.firstOrNull { it.text.isNotBlank() }?.text
                ?: if (selectedLocale.language == "ar") "مرحباً بكم في الدبلجة الصوتية الذكية"
                else "Welcome to the AI voice dubbing preview."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Dub Video with TTS Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Synchronized voiceover with video alignment",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (!translatedSegments.isNullOrEmpty()) {
                    IconButton(
                        onClick = {
                            val file = SrtGenerator.exportTranslatedSrtFile(
                                context = context,
                                videoTitle = video.title,
                                targetLanguageCode = selectedLocale.language,
                                segments = translatedSegments!!
                            )
                            SrtGenerator.shareSrtFile(
                                context = context,
                                file = file,
                                chooserTitle = "Export Translated Subtitles (${file.name})"
                            )
                        },
                        modifier = Modifier.testTag("dialog_top_export_translated_srt")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Translated SRT",
                            tint = AccentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("tts_dubbing_dialog_content")
            ) {
                // Info Banner
                val activeCount = if (!translatedSegments.isNullOrEmpty()) translatedSegments!!.size else segments.size
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!translatedSegments.isNullOrEmpty()) "TRANSLATED CUES ACTIVE"
                                else if (segments.isNotEmpty()) "SUBTITLES DETECTED"
                                else "NO SUBTITLES YET",
                                color = AccentTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "$activeCount cues",
                                color = AccentTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = video.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!translatedSegments.isNullOrEmpty())
                                "Ready to dub with ${translatedSegments!!.size} translated cues in ${selectedLocale.displayLanguage}."
                            else if (segments.isNotEmpty())
                                "Uses on-device Android TextToSpeech engine to generate synchronized dubbed audio."
                            else
                                "Import a translated .srt file below to dub this video directly with TTS without running offline transcription.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TTS Engine Provider Section (3rd-Party & System Engines)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TTS ENGINE / SERVICE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentTeal,
                        letterSpacing = 0.5.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val refreshed = TtsDubbingEngine.getInstalledTtsEngines(context, ttsInstance)
                                installedEngines = refreshed
                                android.widget.Toast.makeText(
                                    context,
                                    "Found ${refreshed.size} TTS engines",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("refresh_tts_engines_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh TTS Engines",
                                tint = AccentTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Refresh",
                                fontSize = 10.sp,
                                color = AccentTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        TextButton(
                            onClick = { TtsDubbingEngine.openSystemTtsSettings(context) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("manage_tts_engines_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Manage TTS Engines",
                                tint = AccentTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Manage Engines",
                                fontSize = 10.sp,
                                color = AccentTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isEngineDropdownExpanded,
                    onExpandedChange = { if (!isSwitchingEngine) isEngineDropdownExpanded = !isEngineDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (isSwitchingEngine) "Switching engine..." else (selectedEngine?.label ?: "System Default Engine"),
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            if (isSwitchingEngine) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentTeal
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isEngineDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("tts_engine_selector"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = isEngineDropdownExpanded,
                        onDismissRequest = { isEngineDropdownExpanded = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        val displayEngines = if (installedEngines.isNotEmpty()) installedEngines else listOf(
                            TtsDubbingEngine.TtsEngineInfo("", "System Default Engine", true)
                        )
                        displayEngines.forEach { eng ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = eng.label,
                                                color = if (eng == selectedEngine) AccentTeal else TextPrimary,
                                                fontWeight = if (eng == selectedEngine) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            if (eng.isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AccentTeal.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Default",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AccentTeal,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (eng.packageName.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = DarkSurfaceVariant
                                                ) {
                                                    Text(
                                                        text = "3rd-Party",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextSecondary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (eng.packageName.isNotBlank()) {
                                            Text(
                                                text = eng.packageName,
                                                fontSize = 9.5.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    isEngineDropdownExpanded = false
                                    if (selectedEngine != eng) {
                                        selectedEngine = eng
                                        coroutineScope.launch {
                                            isSwitchingEngine = true
                                            try {
                                                ttsInstance?.stop()
                                                ttsInstance?.shutdown()
                                            } catch (_: Exception) {}

                                            val newTts = TtsDubbingEngine.createTts(context, eng.packageName.ifBlank { null })
                                            ttsInstance = newTts
                                            if (newTts != null) {
                                                val locales = TtsDubbingEngine.getAvailableLocales(newTts)
                                                availableLocales = locales
                                                if (locales.isNotEmpty() && locales.none { it.language == selectedLocale.language }) {
                                                    selectedLocale = locales.first()
                                                }
                                            }
                                            isSwitchingEngine = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Voice Language Picker
                Text(
                    text = "TTS VOICE LANGUAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${selectedLocale.displayLanguage} (${selectedLocale.displayCountry.ifBlank { selectedLocale.language }})",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("tts_language_selector"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        val displayList = if (availableLocales.isNotEmpty()) availableLocales else listOf(selectedLocale)
                        displayList.forEach { loc ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${loc.displayLanguage} (${loc.displayCountry.ifBlank { loc.language }})",
                                        color = if (loc == selectedLocale) AccentTeal else TextPrimary,
                                        fontWeight = if (loc == selectedLocale) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    if (selectedLocale != loc) {
                                        selectedLocale = loc
                                        translatedSegments = null
                                    }
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Translation Configuration Section
                TranslationConfigSection(
                    enableTranslation = enableTranslation,
                    onEnableTranslationChange = { enableTranslation = it },
                    translationService = translationService,
                    onTranslationServiceChange = { translationService = it },
                    targetLocale = selectedLocale,
                    onTargetLocaleChange = { newLoc ->
                        selectedLocale = newLoc
                    },
                    videoTitle = video.title,
                    segments = segments,
                    translatedSegments = translatedSegments,
                    onTranslatedSegmentsChange = { translatedSegments = it },
                    isTranslating = isTranslating,
                    onIsTranslatingChange = { isTranslating = it },
                    translationProgressText = translationProgressText,
                    onTranslationProgressTextChange = { translationProgressText = it },
                    translationError = translationError,
                    onTranslationErrorChange = { translationError = it },
                    localServerHost = localServerHost,
                    onLocalServerHostChange = { localServerHost = it },
                    localServerPort = localServerPort,
                    onLocalServerPortChange = { localServerPort = it },
                    localServerEndpoint = localServerEndpoint,
                    onLocalServerEndpointChange = { localServerEndpoint = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sliders: Speech Rate & Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Speech Rate: ${String.format(Locale.US, "%.2fx", speechRate)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.70f..1.50f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentTeal,
                        activeTrackColor = AccentTeal,
                        inactiveTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tts_speech_rate_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice Pitch: ${String.format(Locale.US, "%.2fx", pitch)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.75f..1.40f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentTeal,
                        activeTrackColor = AccentTeal,
                        inactiveTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tts_pitch_slider")
                )

                // Voice Preview Button
                OutlinedButton(
                    onClick = {
                        ttsInstance?.let { tts ->
                            val cfg = DubbingConfig(
                                locale = selectedLocale,
                                speechRate = speechRate,
                                pitch = pitch,
                                ttsEnginePackage = selectedEngine?.packageName?.ifBlank { null },
                                ttsEngineLabel = selectedEngine?.label
                            )
                            TtsDubbingEngine.previewSpeech(tts, samplePreviewText, cfg)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tts_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Voice Preview",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overlap Prevention & Speech Pacing Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overlap_prevention_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Prevent Voice Overlap",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Guarantees cues never speak simultaneously",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = preventOverlap,
                                onCheckedChange = { preventOverlap = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentTeal,
                                    checkedTrackColor = AccentTeal.copy(alpha = 0.4f),
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurface
                                ),
                                modifier = Modifier.testTag("prevent_overlap_switch")
                            )
                        }

                        if (preventOverlap) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Auto-Pacing Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Smart Speech Auto-Pacing (SOLA)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Speeds up long translated cues to fit subtitle window",
                                        fontSize = 9.5.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = autoFitCues,
                                    onCheckedChange = { autoFitCues = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AccentTeal,
                                        checkedTrackColor = AccentTeal.copy(alpha = 0.4f),
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkSurface
                                    ),
                                    modifier = Modifier.testTag("auto_fit_cues_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Cue Gap slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pause Between Cues",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${minGapBetweenCuesMs} ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                            }
                            Slider(
                                value = minGapBetweenCuesMs.toFloat(),
                                onValueChange = { minGapBetweenCuesMs = it.toLong() },
                                valueRange = 40f..160f,
                                steps = 5,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentTeal,
                                    activeTrackColor = AccentTeal,
                                    inactiveTrackColor = DarkSurface
                                ),
                                modifier = Modifier.height(26.dp).testTag("cue_gap_slider")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Strategy Radio Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { overlapResolution = OverlapResolution.AUTO_PACE }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = overlapResolution == OverlapResolution.AUTO_PACE,
                                    onClick = { overlapResolution = OverlapResolution.AUTO_PACE },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentTeal),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = OverlapResolution.AUTO_PACE.title,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = OverlapResolution.AUTO_PACE.description,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { overlapResolution = OverlapResolution.SMOOTH_TRIM }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = overlapResolution == OverlapResolution.SMOOTH_TRIM,
                                    onClick = { overlapResolution = OverlapResolution.SMOOTH_TRIM },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentTeal),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = OverlapResolution.SMOOTH_TRIM.title,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = OverlapResolution.SMOOTH_TRIM.description,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mix & Export Options
                Text(
                    text = "MIX & EXPORT MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                DubbingOptionCard(
                    mode = DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC,
                    isSelected = mixMode == DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC,
                    onSelect = { mixMode = DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC },
                    icon = Icons.Default.MusicNote
                )

                if (mixMode == DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Background Music Ducking",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                                Text(
                                    text = "${(duckingVolume * 100).toInt()}% Vol",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Slider(
                                value = duckingVolume,
                                onValueChange = { duckingVolume = it },
                                valueRange = 0.10f..0.45f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentTeal,
                                    activeTrackColor = AccentTeal,
                                    inactiveTrackColor = DarkSurface
                                ),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                DubbingOptionCard(
                    mode = DubbingMixMode.VIDEO_DUBBED_ONLY,
                    isSelected = mixMode == DubbingMixMode.VIDEO_DUBBED_ONLY,
                    onSelect = { mixMode = DubbingMixMode.VIDEO_DUBBED_ONLY },
                    icon = Icons.Default.Movie
                )

                Spacer(modifier = Modifier.height(6.dp))

                DubbingOptionCard(
                    mode = DubbingMixMode.AUDIO_DUBBED_WAV,
                    isSelected = mixMode == DubbingMixMode.AUDIO_DUBBED_WAV,
                    onSelect = { mixMode = DubbingMixMode.AUDIO_DUBBED_WAV },
                    icon = Icons.Default.Mic
                )

                Spacer(modifier = Modifier.height(6.dp))

                DubbingOptionCard(
                    mode = DubbingMixMode.AUDIO_DUBBED_M4A,
                    isSelected = mixMode == DubbingMixMode.AUDIO_DUBBED_M4A,
                    onSelect = { mixMode = DubbingMixMode.AUDIO_DUBBED_M4A },
                    icon = Icons.Default.Audiotrack
                )
            }
        },
        confirmButton = {
            val hasCues = segments.isNotEmpty() || !translatedSegments.isNullOrEmpty()
            Button(
                onClick = {
                    val config = DubbingConfig(
                        locale = selectedLocale,
                        speechRate = speechRate,
                        pitch = pitch,
                        mixMode = mixMode,
                        backgroundDuckingVolume = duckingVolume,
                        enableTranslation = enableTranslation || !translatedSegments.isNullOrEmpty(),
                        translationService = translationService,
                        targetLanguageCode = selectedLocale.language,
                        sourceLanguageCode = "auto",
                        preTranslatedSegments = translatedSegments,
                        preventOverlap = preventOverlap,
                        autoFitCues = autoFitCues,
                        minGapBetweenCuesMs = minGapBetweenCuesMs,
                        overlapResolution = overlapResolution,
                        ttsEnginePackage = selectedEngine?.packageName?.ifBlank { null },
                        ttsEngineLabel = selectedEngine?.label,
                        localServerHost = localServerHost,
                        localServerPort = localServerPort,
                        localServerEndpoint = localServerEndpoint
                    )
                    onConfirmDubbing(config)
                },
                enabled = hasCues && !isTranslating,
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_dubbing_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (mixMode.isVideo) "Export Dubbed Video" else "Export Dubbed Audio",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_dubbing_button")
            ) {
                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DubbingOptionCard(
    mode: DubbingMixMode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    icon: ImageVector
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
            .testTag("dubbing_mode_${mode.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = AccentTeal,
                    unselectedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.85f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
                Text(
                    text = mode.subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun containsArabicCharacters(text: String): Boolean {
    for (char in text) {
        val code = char.code
        if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF) {
            return true
        }
    }
    return false
}
