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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var availableLocales by remember { mutableStateOf<List<Locale>>(emptyList()) }
    var selectedLocale by remember { mutableStateOf(Locale.getDefault()) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var mixMode by remember { mutableStateOf(DubbingMixMode.VIDEO_DUBBED_WITH_MUSIC) }
    var duckingVolume by remember { mutableFloatStateOf(0.20f) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Translation State
    var enableTranslation by remember { mutableStateOf(false) }
    var translationService by remember { mutableStateOf(TranslationServiceType.ONLINE_FREE) }
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

    // Initialize TTS engine for preview and available voice queries
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInstance = tts
                    tts?.let { validTts ->
                        val locales = TtsDubbingEngine.getAvailableLocales(validTts)
                        availableLocales = locales
                        // Match selectedLocale to available if possible
                        locales.firstOrNull { it.language == selectedLocale.language }?.let {
                            selectedLocale = it
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = "Dub Video with Android TTS",
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("tts_dubbing_dialog_content")
            ) {
                // Info Banner
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
                                text = "SUBTITLES DETECTED",
                                color = AccentTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${segments.size} cues",
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
                            text = "Uses on-device Android TextToSpeech engine to generate synchronized dubbed audio.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
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
                    segments = segments,
                    translatedSegments = translatedSegments,
                    onTranslatedSegmentsChange = { translatedSegments = it },
                    isTranslating = isTranslating,
                    onIsTranslatingChange = { isTranslating = it },
                    translationProgressText = translationProgressText,
                    onTranslationProgressTextChange = { translationProgressText = it },
                    translationError = translationError,
                    onTranslationErrorChange = { translationError = it }
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
                                pitch = pitch
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
            Button(
                onClick = {
                    val config = DubbingConfig(
                        locale = selectedLocale,
                        speechRate = speechRate,
                        pitch = pitch,
                        mixMode = mixMode,
                        backgroundDuckingVolume = duckingVolume,
                        enableTranslation = enableTranslation,
                        translationService = translationService,
                        targetLanguageCode = selectedLocale.language,
                        sourceLanguageCode = "auto",
                        preTranslatedSegments = translatedSegments
                    )
                    onConfirmDubbing(config)
                },
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
