package com.example.ui.locale

/**
 * Localized UI strings for VocalExtractor.
 */
object AppStrings {

    fun appName(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مستخرج الأصوات"
        AppLanguage.SPANISH -> "Extractor de Voces"
        AppLanguage.FRENCH -> "Extracteur Vocal"
        AppLanguage.GERMAN -> "Stimmen-Extraktor"
        else -> "VocalExtractor"
    }

    fun appTagline(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عزل الأصوات والموسيقى بالذكاء الاصطناعي"
        AppLanguage.SPANISH -> "Separador de voces y reproductor con IA"
        AppLanguage.FRENCH -> "Séparation vocale IA & lecteur vidéo"
        AppLanguage.GERMAN -> "KI-Stimmisolierung & Videoplayer"
        else -> "AI Vocal Separator & Video Player"
    }

    // Top Bar & Navigation
    fun storageFolders(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مجلدات التخزين"
        AppLanguage.SPANISH -> "Carpetas de almacenamiento"
        AppLanguage.FRENCH -> "Dossiers de stockage"
        AppLanguage.GERMAN -> "Speicherordner"
        else -> "Storage Folders"
    }

    fun importUrl(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد من الرابط"
        AppLanguage.SPANISH -> "Importar desde enlace"
        AppLanguage.FRENCH -> "Importer depuis un lien"
        AppLanguage.GERMAN -> "Aus Link importieren"
        else -> "Import from Link"
    }

    fun aiModelSettings(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعدادات نموذج الذكاء الاصطناعي"
        AppLanguage.SPANISH -> "Ajustes de modelo IA"
        AppLanguage.FRENCH -> "Paramètres du modèle IA"
        AppLanguage.GERMAN -> "KI-Modell-Einstellungen"
        else -> "AI Model Settings"
    }

    fun languagePreferences(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تفضيلات اللغة"
        AppLanguage.SPANISH -> "Preferencias de idioma"
        AppLanguage.FRENCH -> "Préférences de langue"
        AppLanguage.GERMAN -> "Spracheinstellungen"
        else -> "Language Preferences"
    }

    fun selectLanguage(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اختر لغة الواجهة"
        AppLanguage.SPANISH -> "Seleccionar idioma de la interfaz"
        AppLanguage.FRENCH -> "Sélectionner la langue de l'interface"
        AppLanguage.GERMAN -> "Oberflächensprache wählen"
        else -> "Select UI Language"
    }

    // Actions
    fun importVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد فيديو"
        AppLanguage.SPANISH -> "Importar vídeo"
        AppLanguage.FRENCH -> "Importer une vidéo"
        AppLanguage.GERMAN -> "Video importieren"
        else -> "Import Video"
    }

    fun importAudioOrVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد صوت أو فيديو"
        AppLanguage.SPANISH -> "Importar audio o vídeo"
        AppLanguage.FRENCH -> "Importer audio ou vidéo"
        AppLanguage.GERMAN -> "Audio oder Video importieren"
        else -> "Import Audio or Video"
    }

    fun cancel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إلغاء"
        AppLanguage.SPANISH -> "Cancelar"
        AppLanguage.FRENCH -> "Annuler"
        AppLanguage.GERMAN -> "Abbrechen"
        else -> "Cancel"
    }

    fun delete(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف"
        AppLanguage.SPANISH -> "Eliminar"
        AppLanguage.FRENCH -> "Supprimer"
        AppLanguage.GERMAN -> "Löschen"
        else -> "Delete"
    }

    fun retry(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة المحاولة"
        AppLanguage.SPANISH -> "Reintentar"
        AppLanguage.FRENCH -> "Réessayer"
        AppLanguage.GERMAN -> "Wiederholen"
        else -> "Retry"
    }

    fun reprocess(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة المعالجة"
        AppLanguage.SPANISH -> "Reprocesar"
        AppLanguage.FRENCH -> "Retraiter"
        AppLanguage.GERMAN -> "Erneut verarbeiten"
        else -> "Reprocess"
    }

    fun export(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير"
        AppLanguage.SPANISH -> "Exportar"
        AppLanguage.FRENCH -> "Exporter"
        AppLanguage.GERMAN -> "Exportieren"
        else -> "Export"
    }

    fun exportMedia(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير الملفات"
        AppLanguage.SPANISH -> "Exportar medios"
        AppLanguage.FRENCH -> "Exporter les médias"
        AppLanguage.GERMAN -> "Medien exportieren"
        else -> "Export Media"
    }

    fun exportVideoWithVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير الفيديو مع الصوت المعزول"
        AppLanguage.SPANISH -> "Exportar vídeo con voces"
        AppLanguage.FRENCH -> "Exporter vidéo avec voix"
        AppLanguage.GERMAN -> "Video mit Stimmen exportieren"
        else -> "Export Video with Vocals"
    }

    // Export Options Dialog
    fun exportDialogTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "خيارات التصدير وحفظ الملفات"
        AppLanguage.SPANISH -> "Opciones de exportación"
        AppLanguage.FRENCH -> "Options d'exportation"
        AppLanguage.GERMAN -> "Exportoptionen"
        else -> "Export Media"
    }

    fun exportDialogSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات الصوت والفيديو المدعومة"
        AppLanguage.SPANISH -> "Formatos de extracción de audio y vídeo"
        AppLanguage.FRENCH -> "Formats d'extraction audio et vidéo"
        AppLanguage.GERMAN -> "Audio- und Video-Exportformate"
        else -> "Audio & Video Extraction Formats"
    }

    fun audioExportFormats(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات تصدير الصوت"
        AppLanguage.SPANISH -> "FORMATOS DE EXPORTACIÓN DE AUDIO"
        AppLanguage.FRENCH -> "FORMATS D'EXPORTATION AUDIO"
        AppLanguage.GERMAN -> "AUDIO-EXPORTFORMATE"
        else -> "AUDIO EXPORT FORMATS"
    }

    fun videoExportFormats(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات تصدير الفيديو"
        AppLanguage.SPANISH -> "FORMATOS DE EXPORTACIÓN DE VÍDEO"
        AppLanguage.FRENCH -> "FORMATS D'EXPORTATION VIDÉO"
        AppLanguage.GERMAN -> "VIDEO-EXPORTFORMATE"
        else -> "VIDEO EXPORT FORMATS"
    }

    fun deleteOriginalAfterExport(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف الملف الأصلي بعد نجاح التصدير"
        AppLanguage.SPANISH -> "Eliminar original tras exportar"
        AppLanguage.FRENCH -> "Supprimer l'original après l'exportation"
        AppLanguage.GERMAN -> "Original nach Export löschen"
        else -> "Delete original after export"
    }

    fun deleteOriginalSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "يتم الحذف بأمان فقط بعد إتمام التصدير بنجاح."
        AppLanguage.SPANISH -> "Elimina de forma segura el origen solo tras éxito."
        AppLanguage.FRENCH -> "Supprime la source uniquement après le succès de l'export."
        AppLanguage.GERMAN -> "Löscht das Original erst nach erfolgreichem Export."
        else -> "Safely deletes source only after export succeeds."
    }

    fun arabicNameSupportBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "✓ دعم كامل للأسماء العربية والرموز عند التصدير"
        AppLanguage.SPANISH -> "✓ Compatibilidad completa con nombres en árabe y caracteres Unicode"
        AppLanguage.FRENCH -> "✓ Prise en charge complète des noms arabes et Unicode"
        AppLanguage.GERMAN -> "✓ Volle Unterstützung arabischer Namen & Unicode"
        else -> "✓ Full support for Arabic names & Unicode on export"
    }

    fun instantVideoCacheReady(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "⚡ جاهز للتصدير الفوري (تم الدمج المسبق أثناء الفصل)"
        AppLanguage.SPANISH -> "⚡ Listo para exportación instantánea (prefusionado)"
        AppLanguage.FRENCH -> "⚡ Prêt pour export immédiat (pré-fusionné en cache)"
        AppLanguage.GERMAN -> "⚡ Sofortiger Export bereit (vorausgeführt im Cache)"
        else -> "⚡ Instant Export Ready (Pre-merged live cache)"
    }

    fun instantExportBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير فوري بدون انتظار"
        AppLanguage.SPANISH -> "Exportación inmediata"
        AppLanguage.FRENCH -> "Exportation immédiate"
        AppLanguage.GERMAN -> "Sofortiger Export"
        else -> "Instant Export"
    }

    fun exportingMedia(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ تصدير الملف…"
        AppLanguage.SPANISH -> "Exportando medios…"
        AppLanguage.FRENCH -> "Exportation des médias…"
        AppLanguage.GERMAN -> "Medien werden exportiert…"
        else -> "Exporting Media…"
    }

    fun exportComplete(lang: AppLanguage, isAudio: Boolean) = when (lang) {
        AppLanguage.ARABIC -> if (isAudio) "اكتمل تصدير الصوت بنجاح!" else "اكتمل تصدير الفيديو بنجاح!"
        AppLanguage.SPANISH -> if (isAudio) "¡Exportación de audio completada!" else "¡Exportación de vídeo completada!"
        AppLanguage.FRENCH -> if (isAudio) "Export audio terminé !" else "Export vidéo terminé !"
        AppLanguage.GERMAN -> if (isAudio) "Audio-Export abgeschlossen!" else "Video-Export abgeschlossen!"
        else -> if (isAudio) "Audio Export Complete!" else "Video Export Complete!"
    }

    fun playVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تشغيل الفيديو"
        AppLanguage.SPANISH -> "Reproducir vídeo"
        AppLanguage.FRENCH -> "Lire la vidéo"
        AppLanguage.GERMAN -> "Video abspielen"
        else -> "Play Video"
    }

    fun playAudio(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تشغيل الصوت"
        AppLanguage.SPANISH -> "Reproducir audio"
        AppLanguage.FRENCH -> "Lire l'audio"
        AppLanguage.GERMAN -> "Audio abspielen"
        else -> "Play Audio"
    }

    fun share(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مشاركة"
        AppLanguage.SPANISH -> "Compartir"
        AppLanguage.FRENCH -> "Partager"
        AppLanguage.GERMAN -> "Teilen"
        else -> "Share"
    }

    fun done(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تم"
        AppLanguage.SPANISH -> "Listo"
        AppLanguage.FRENCH -> "Terminé"
        AppLanguage.GERMAN -> "Fertig"
        else -> "Done"
    }

    // Audio Formats
    fun formatVocalsWavTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول (WAV فائق الجودة)"
        AppLanguage.SPANISH -> "Voces aisladas (WAV sin pérdidas)"
        AppLanguage.FRENCH -> "Voix isolées (WAV sans perte)"
        AppLanguage.GERMAN -> "Isolierte Stimmen (Lossless WAV)"
        else -> "Isolated Vocals (Lossless WAV)"
    }

    fun formatVocalsWavSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "صوت استوديو غير مضغوط 16-bit 44.1kHz • الأفضل للمونتاج والتعديل"
        AppLanguage.SPANISH -> "Audio 16-bit 44.1kHz sin comprimir • Ideal para edición"
        AppLanguage.FRENCH -> "Audio 16-bit 44.1kHz non compressé • Idéal pour le montage"
        AppLanguage.GERMAN -> "Unkomprimiertes 16-Bit 44.1kHz Studio-Audio • Bestens für Bearbeitung"
        else -> "Studio 16-bit 44.1kHz uncompressed audio • Best for DAWs & editing"
    }

    fun formatVocalsM4aTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول (M4A / AAC عالي الجودة)"
        AppLanguage.SPANISH -> "Voces aisladas (M4A / AAC)"
        AppLanguage.FRENCH -> "Voix isolées (M4A / AAC)"
        AppLanguage.GERMAN -> "Isolierte Stimmen (M4A / AAC)"
        else -> "Isolated Vocals (M4A / AAC)"
    }

    fun formatVocalsM4aSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "صوت نقي 256 kbps AAC • تشغيل فوري وحجم صغير"
        AppLanguage.SPANISH -> "Audio AAC 256 kbps de alta calidad • Tamaño compacto"
        AppLanguage.FRENCH -> "Audio AAC 256 kbps haute qualité • Petite taille"
        AppLanguage.GERMAN -> "Hochwertiges 256 kbps AAC-Audio • Kompakte Dateigröße"
        else -> "High-quality 256 kbps AAC audio • Universal playback & small file size"
    }

    fun formatInstWavTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الموسيقى والخلفية / كاريوكي (WAV)"
        AppLanguage.SPANISH -> "Instrumental / Karaoke (WAV sin pérdidas)"
        AppLanguage.FRENCH -> "Instrumental / Karaoké (WAV sans perte)"
        AppLanguage.GERMAN -> "Instrumental / Karaoke (Lossless WAV)"
        else -> "Instrumental / Karaoke (Lossless WAV)"
    }

    fun formatInstWavSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تمت إزالة الصوت البشري • خلفية موسيقية كاملة 16-bit"
        AppLanguage.SPANISH -> "Voces eliminadas • Pista instrumental completa en WAV"
        AppLanguage.FRENCH -> "Voix supprimées • Musique d'accompagnement complète en WAV"
        AppLanguage.GERMAN -> "Gesang entfernt • Vollständige Begleitung in 16-Bit WAV"
        else -> "Acapella removed • Full backing music in 16-bit studio WAV"
    }

    fun formatInstM4aTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الموسيقى والخلفية / كاريوكي (M4A / AAC)"
        AppLanguage.SPANISH -> "Instrumental / Karaoke (M4A / AAC)"
        AppLanguage.FRENCH -> "Instrumental / Karaoké (M4A / AAC)"
        AppLanguage.GERMAN -> "Instrumental / Karaoke (M4A / AAC)"
        else -> "Instrumental / Karaoke (M4A / AAC)"
    }

    fun formatInstM4aSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "موسيقى بدون أصوات بحجم خفيف وتنسيق AAC"
        AppLanguage.SPANISH -> "Música de fondo en formato AAC compacto"
        AppLanguage.FRENCH -> "Musique d'accompagnement en AAC compact"
        AppLanguage.GERMAN -> "Kompaktes AAC-Begleit-Audio"
        else -> "Backing accompaniment track in compact high-quality AAC"
    }

    fun formatVideoVocalsTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "فيديو مع الصوت المعزول فقط (MP4)"
        AppLanguage.SPANISH -> "Vídeo solo con voces (MP4)"
        AppLanguage.FRENCH -> "Vidéo avec voix uniquement (MP4)"
        AppLanguage.GERMAN -> "Video nur mit Stimmen (MP4)"
        else -> "Video with Vocals Only (MP4)"
    }

    fun formatVideoVocalsSub(lang: AppLanguage, isCacheReady: Boolean) = when (lang) {
        AppLanguage.ARABIC -> if (isCacheReady)
            "⚡ دمج فوري من الذاكرة المؤقتة • بدون أي وقت انتظار للدمج!"
        else
            "دمج فيديو المصدر مع الصوت البشري المعزول بالذكاء الاصطناعي"
        AppLanguage.SPANISH -> if (isCacheReady)
            "⚡ Exportación instantánea desde caché • ¡Sin espera de mezcla!"
        else
            "Combina el vídeo original con la pista vocal aislada"
        AppLanguage.FRENCH -> if (isCacheReady)
            "⚡ Export immédiat depuis le cache • Sans temps d'attente !"
        else
            "Combine le flux vidéo original avec la voix isolée par l'IA"
        AppLanguage.GERMAN -> if (isCacheReady)
            "⚡ Sofortiger Export aus Cache • Keine Wartezeit beim Mischen!"
        else
            "Kombiniert Original-Videostream mit KI-isoliertem Gesang"
        else -> if (isCacheReady)
            "⚡ Instant Export from live cache • Zero merging wait time!"
        else
            "Combines original video stream with AI-isolated vocal audio"
    }

    fun formatVideoInstTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "فيديو كاريوكي / موسيقى بدون أصوات (MP4)"
        AppLanguage.SPANISH -> "Vídeo con pista de karaoke (MP4)"
        AppLanguage.FRENCH -> "Vidéo avec piste karaoké (MP4)"
        AppLanguage.GERMAN -> "Video mit Karaoke-Spur (MP4)"
        else -> "Video with Karaoke Track (MP4)"
    }

    fun formatVideoInstSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "دمج فيديو المصدر مع الموسيقى التصويرية دون الصوت البشري"
        AppLanguage.SPANISH -> "Combina el vídeo original con audio instrumental"
        AppLanguage.FRENCH -> "Combine la vidéo originale avec la musique instrumentale"
        AppLanguage.GERMAN -> "Kombiniert Original-Videostream mit Instrumental-Audio"
        else -> "Combines original video stream with instrumental audio"
    }

    // Player controls
    fun originalAudio(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت الأصلي"
        AppLanguage.SPANISH -> "Audio original"
        AppLanguage.FRENCH -> "Audio original"
        AppLanguage.GERMAN -> "Original-Audio"
        else -> "Original Audio"
    }

    fun isolatedVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول (ذكاء اصطناعي)"
        AppLanguage.SPANISH -> "Voces aisladas (IA)"
        AppLanguage.FRENCH -> "Voix isolées (IA)"
        AppLanguage.GERMAN -> "Isolierte Stimmen (KI)"
        else -> "Isolated Vocals (AI)"
    }

    fun audioMixer(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "موازن الصوت المزدوج"
        AppLanguage.SPANISH -> "Mezclador de audio"
        AppLanguage.FRENCH -> "Mixeur audio"
        AppLanguage.GERMAN -> "Audio-Mixer"
        else -> "Dual Audio Balance"
    }

    fun processingVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ عزل الأصوات بالذكاء الاصطناعي…"
        AppLanguage.SPANISH -> "Extrayendo voces con IA…"
        AppLanguage.FRENCH -> "Extraction des voix avec l'IA…"
        AppLanguage.GERMAN -> "Stimmen werden mit KI extrahiert…"
        else -> "Extracting vocals with AI…"
    }

    fun noVideosFound(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "لا توجد ملفات بعد"
        AppLanguage.SPANISH -> "Aún no hay vídeos importados"
        AppLanguage.FRENCH -> "Aucune vidéo importée pour l'instant"
        AppLanguage.GERMAN -> "Noch keine Videos importiert"
        else -> "No videos imported yet"
    }

    fun tapToImport(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اضغط على الزر أدناه لاستيراد مقطع فيديو أو ملف صوتي"
        AppLanguage.SPANISH -> "Toca el botón inferior para importar un vídeo o audio"
        AppLanguage.FRENCH -> "Appuyez sur le bouton ci-dessous pour importer un média"
        AppLanguage.GERMAN -> "Tippen Sie unten, um ein Video oder Audio zu importieren"
        else -> "Tap the button below or import a media file to begin"
    }
}
