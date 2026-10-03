package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelEntity
import com.example.model.ModelType
import com.example.onnx.AccelerationMode
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.whisper.WhisperTokenizer

@Composable
fun ModelSettingsDialog(
    activeModelName: String,
    customModelPath: String? = null,
    accelerationMode: AccelerationMode = AccelerationMode.TURBO,
    hardwareBackend: String = "Multi-Core CPU",
    models: List<ModelEntity> = emptyList(),
    selectedVocalModelId: String = "spleeter_2stems",
    selectedWhisperModelId: String? = null,
    whisperLanguage: String = "auto",
    initialTabIndex: Int = 0,
    onDismiss: () -> Unit,
    onSelectAccelerationMode: (AccelerationMode) -> Unit = {},
    onSelectModel: (modelId: String, type: ModelType) -> Unit = { _, _ -> },
    onDownloadModel: (modelId: String) -> Unit = {},
    onDeleteModel: (modelId: String) -> Unit = {},
    onSetWhisperLanguage: (langCode: String) -> Unit = {},
    onImportCustomModel: (Uri) -> Unit = {},
    onImportCustomModelWithType: (Uri, ModelType) -> Unit = { uri, _ -> onImportCustomModel(uri) },
    onResetToBuiltIn: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    var selectedTab by remember { mutableIntStateOf(initialTabIndex) }

    var targetImportType by remember { mutableStateOf(ModelType.VOCAL_SEPARATION) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            onImportCustomModelWithType(it, targetImportType)
        }
    }

    val isCustom = !customModelPath.isNullOrBlank()
    val vocalModels = models.filter { it.type == ModelType.VOCAL_SEPARATION }
    val whisperModels = models.filter { it.type == ModelType.WHISPER_TRANSCRIPTION }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.modelSettingsTitle(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("model_settings_dialog")
            ) {
                // Tab Row: Vocal Separation vs Speech Recognition (Whisper)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = AccentTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentTeal
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_vocal_models"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 0) AccentTeal else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.vocalSeparationModels(lang),
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) AccentTeal else TextSecondary
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_whisper_models"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == 1) AccentTeal else TextSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.whisperModels(lang),
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) AccentTeal else TextSecondary
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TAB 0: VOCAL SEPARATION
                    VocalSeparationSettings(
                        activeModelName = activeModelName,
                        customModelPath = customModelPath,
                        accelerationMode = accelerationMode,
                        hardwareBackend = hardwareBackend,
                        models = vocalModels,
                        selectedModelId = selectedVocalModelId,
                        onSelectModel = { onSelectModel(it, ModelType.VOCAL_SEPARATION) },
                        onDownloadModel = onDownloadModel,
                        onSelectAccelerationMode = onSelectAccelerationMode,
                        onImportCustomModel = {
                            targetImportType = ModelType.VOCAL_SEPARATION
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onResetToBuiltIn = onResetToBuiltIn
                    )
                } else {
                    // TAB 1: WHISPER SPEECH RECOGNITION
                    WhisperModelsSettings(
                        models = whisperModels,
                        selectedWhisperModelId = selectedWhisperModelId,
                        whisperLanguage = whisperLanguage,
                        onSelectModel = { onSelectModel(it, ModelType.WHISPER_TRANSCRIPTION) },
                        onDownloadModel = onDownloadModel,
                        onDeleteModel = onDeleteModel,
                        onSetLanguage = onSetWhisperLanguage,
                        onImportCustomModel = {
                            targetImportType = ModelType.WHISPER_TRANSCRIPTION
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_model_dialog_button")
            ) {
                Text(AppStrings.closeButton(lang), color = AccentTeal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun VocalSeparationSettings(
    activeModelName: String,
    customModelPath: String?,
    accelerationMode: AccelerationMode,
    hardwareBackend: String,
    models: List<ModelEntity>,
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onSelectAccelerationMode: (AccelerationMode) -> Unit,
    onImportCustomModel: () -> Unit,
    onResetToBuiltIn: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val isCustom = !customModelPath.isNullOrBlank()

    // Hardware Acceleration Status Badge
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = AppStrings.activeHardwareAcceleration(lang),
                    color = AccentTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = hardwareBackend,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Processing Acceleration Modes
    Text(
        text = AppStrings.speedProfileHeader(lang),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    AccelerationMode.entries.forEach { mode ->
        val isSelected = mode == accelerationMode
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) DarkSurfaceVariant else DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(1.dp, AccentTeal, RoundedCornerShape(12.dp))
                    } else Modifier
                )
                .clickable { onSelectAccelerationMode(mode) }
                .testTag("accel_mode_${mode.id}")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) AccentTeal else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mode.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (mode == AccelerationMode.TURBO) AccentTeal.copy(alpha = 0.2f) else DarkBackground
                        ) {
                            Text(
                                text = mode.speedBadge,
                                color = if (mode == AccelerationMode.TURBO) AccentTeal else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = mode.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Active Model Box
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCustom) AppStrings.customModelBadge(lang) else AppStrings.builtInModelBadge(lang),
                    color = AccentTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeModelName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Import Custom Model Button
    Button(
        onClick = onImportCustomModel,
        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_custom_model_button")
    ) {
        Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = AppStrings.importCustomModelButton(lang),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    if (isCustom) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onResetToBuiltIn,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentTeal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_model_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = AppStrings.resetToBuiltInModelButton(lang),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WhisperModelsSettings(
    models: List<ModelEntity>,
    selectedWhisperModelId: String?,
    whisperLanguage: String,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSetLanguage: (String) -> Unit,
    onImportCustomModel: () -> Unit
) {
    val lang = LocalAppLanguage.current
    var languageMenuExpanded by remember { mutableStateOf(false) }

    // Language Selector Section
    Text(
        text = AppStrings.transcriptionLanguage(lang),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(6.dp))

    val currentLangObj = WhisperTokenizer.AVAILABLE_LANGUAGES.firstOrNull { it.code == whisperLanguage }
        ?: WhisperTokenizer.AVAILABLE_LANGUAGES.first()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { languageMenuExpanded = true }
            .testTag("whisper_language_selector")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentLangObj.displayName} (${currentLangObj.nativeName})",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = TextSecondary
            )
        }

        DropdownMenu(
            expanded = languageMenuExpanded,
            onDismissRequest = { languageMenuExpanded = false },
            modifier = Modifier.background(DarkSurfaceVariant)
        ) {
            WhisperTokenizer.AVAILABLE_LANGUAGES.forEach { langItem ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = langItem.displayName,
                                color = if (langItem.code == whisperLanguage) AccentTeal else TextPrimary,
                                fontWeight = if (langItem.code == whisperLanguage) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${langItem.nativeName})",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    },
                    onClick = {
                        onSetLanguage(langItem.code)
                        languageMenuExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Whisper Models Catalog Section
    Text(
        text = AppStrings.whisperModels(lang),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (models.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = AppStrings.whisperModelNotInstalled(lang),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        models.forEach { model ->
            val isSelected = model.id == selectedWhisperModelId && model.isInstalled

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) DarkSurfaceVariant else DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, AccentTeal, RoundedCornerShape(12.dp))
                        } else Modifier
                    )
                    .clickable(enabled = model.isInstalled) {
                        onSelectModel(model.id)
                    }
                    .testTag("whisper_model_${model.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) AccentTeal else if (model.isInstalled) TextSecondary else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model.displayName,
                                    color = if (model.isInstalled) TextPrimary else TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (model.isInstalled) AccentTeal.copy(alpha = 0.2f) else DarkBackground
                                ) {
                                    Text(
                                        text = if (model.isInstalled) AppStrings.installedBadge(lang) else model.sizeDisplay,
                                        color = if (model.isInstalled) AccentTeal else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = model.description,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }

                        // Action Button: Download, Downloading Progress, or Delete
                        if (model.isDownloading) {
                            val pct = ((model.downloadProgress ?: 0f) * 100).toInt()
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$pct%",
                                    color = AccentTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (model.downloadSpeedKbps > 0) {
                                    Text(
                                        text = "${model.downloadSpeedKbps} KB/s",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else if (!model.isInstalled) {
                            Button(
                                onClick = { onDownloadModel(model.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .testTag("download_model_${model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = model.sizeDisplay,
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!model.isBundled) {
                            IconButton(
                                onClick = { onDeleteModel(model.id) },
                                modifier = Modifier.testTag("delete_model_${model.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = AppStrings.deleteModelButton(lang),
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (model.isDownloading && model.downloadProgress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { model.downloadProgress },
                            color = AccentTeal,
                            trackColor = DarkBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }

                    if (!model.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = model.errorMessage,
                            color = StatusError,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Import Custom Whisper Model Button
    Button(
        onClick = onImportCustomModel,
        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_custom_whisper_button")
    ) {
        Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Import Custom Whisper Model (.onnx)",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
