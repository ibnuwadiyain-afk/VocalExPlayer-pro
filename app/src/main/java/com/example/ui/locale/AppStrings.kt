package com.example.ui.locale

/**
 * Localized UI strings for VocalExtractor supporting:
 * English, Arabic (العربية), Spanish (Español), French (Français),
 * German (Deutsch), Japanese (日本語), Chinese (中文), Russian (Русский), Portuguese (Português).
 */
object AppStrings {

    fun appName(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مستخرج الأصوات"
        AppLanguage.SPANISH -> "Extractor de Voces"
        AppLanguage.FRENCH -> "Extracteur Vocal"
        AppLanguage.GERMAN -> "Stimmen-Extraktor"
        AppLanguage.JAPANESE -> "ボーカル抽出"
        AppLanguage.CHINESE -> "人声分离器"
        AppLanguage.RUSSIAN -> "Экстрактор Вокала"
        AppLanguage.PORTUGUESE -> "Extrator de Vozes"
        else -> "VocalExtractor"
    }

    fun appTagline(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عزل الأصوات والموسيقى بنموذج سبلتر الثنائي"
        AppLanguage.SPANISH -> "Separador de voces y música con Spleeter"
        AppLanguage.FRENCH -> "Séparation vocale & musicale avec Spleeter"
        AppLanguage.GERMAN -> "KI-Stimmisolierung mit Spleeter 2-Stem"
        AppLanguage.JAPANESE -> "Spleeter 2-Stem AI 音声分離プレーヤー"
        AppLanguage.CHINESE -> "基于 Spleeter 2-Stem 的 AI 伴奏人声提取"
        AppLanguage.RUSSIAN -> "Разделение вокала и музыки с нейросетью Spleeter"
        AppLanguage.PORTUGUESE -> "Separador de vocais e música com Spleeter"
        else -> "AI Vocal & Instrumental Separator (Spleeter 2-Stem)"
    }

    // Top Bar & Navigation
    fun storageFolders(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مجلدات التخزين"
        AppLanguage.SPANISH -> "Carpetas de almacenamiento"
        AppLanguage.FRENCH -> "Dossiers de stockage"
        AppLanguage.GERMAN -> "Speicherordner"
        AppLanguage.JAPANESE -> "ストレージフォルダ"
        AppLanguage.CHINESE -> "存储文件夹"
        AppLanguage.RUSSIAN -> "Папки хранилища"
        AppLanguage.PORTUGUESE -> "Pastas de armazenamento"
        else -> "Storage Folders"
    }

    fun importUrl(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد من الرابط"
        AppLanguage.SPANISH -> "Importar desde enlace"
        AppLanguage.FRENCH -> "Importer depuis un lien"
        AppLanguage.GERMAN -> "Aus Link importieren"
        AppLanguage.JAPANESE -> "リンクからインポート"
        AppLanguage.CHINESE -> "从链接导入"
        AppLanguage.RUSSIAN -> "Импорт по ссылке"
        AppLanguage.PORTUGUESE -> "Importar por link"
        else -> "Import from Link"
    }

    fun aiModelSettings(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعدادات محرك الذكاء الاصطناعي"
        AppLanguage.SPANISH -> "Ajustes de motor IA"
        AppLanguage.FRENCH -> "Paramètres du moteur IA"
        AppLanguage.GERMAN -> "KI-Engine-Einstellungen"
        AppLanguage.JAPANESE -> "AIエンジン設定"
        AppLanguage.CHINESE -> "AI 引擎设置"
        AppLanguage.RUSSIAN -> "Настройки ИИ движка"
        AppLanguage.PORTUGUESE -> "Configurações do motor de IA"
        else -> "AI Engine Settings"
    }

    fun languagePreferences(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تفضيلات اللغة"
        AppLanguage.SPANISH -> "Preferencias de idioma"
        AppLanguage.FRENCH -> "Préférences de langue"
        AppLanguage.GERMAN -> "Spracheinstellungen"
        AppLanguage.JAPANESE -> "言語設定"
        AppLanguage.CHINESE -> "语言首选项"
        AppLanguage.RUSSIAN -> "Языковые настройки"
        AppLanguage.PORTUGUESE -> "Preferências de idioma"
        else -> "Language Preferences"
    }

    fun selectLanguage(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اختر لغة الواجهة"
        AppLanguage.SPANISH -> "Seleccionar idioma de la interfaz"
        AppLanguage.FRENCH -> "Sélectionner la langue de l'interface"
        AppLanguage.GERMAN -> "Oberflächensprache wählen"
        AppLanguage.JAPANESE -> "UIの言語を選択"
        AppLanguage.CHINESE -> "选择界面语言"
        AppLanguage.RUSSIAN -> "Выберите язык интерфейса"
        AppLanguage.PORTUGUESE -> "Selecione o idioma da interface"
        else -> "Select UI Language"
    }

    // FAB Buttons
    fun fabFolders(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مجلدات"
        AppLanguage.SPANISH -> "Carpetas"
        AppLanguage.FRENCH -> "Dossiers"
        AppLanguage.GERMAN -> "Ordner"
        AppLanguage.JAPANESE -> "フォルダ"
        AppLanguage.CHINESE -> "文件夹"
        AppLanguage.RUSSIAN -> "Папки"
        AppLanguage.PORTUGUESE -> "Pastas"
        else -> "Folders"
    }

    fun fabLink(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "رابط"
        AppLanguage.SPANISH -> "Enlace"
        AppLanguage.FRENCH -> "Lien"
        AppLanguage.GERMAN -> "Link"
        AppLanguage.JAPANESE -> "リンク"
        AppLanguage.CHINESE -> "链接"
        AppLanguage.RUSSIAN -> "Ссылка"
        AppLanguage.PORTUGUESE -> "Link"
        else -> "Link"
    }

    fun fabImport(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد"
        AppLanguage.SPANISH -> "Importar"
        AppLanguage.FRENCH -> "Importer"
        AppLanguage.GERMAN -> "Importieren"
        AppLanguage.JAPANESE -> "インポート"
        AppLanguage.CHINESE -> "导入"
        AppLanguage.RUSSIAN -> "Импорт"
        AppLanguage.PORTUGUESE -> "Importar"
        else -> "Import"
    }

    // Engine Card
    fun aiSeparationEngine(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "محرك عزل الصوت بالذكاء الاصطناعي"
        AppLanguage.SPANISH -> "MOTOR DE SEPARACIÓN IA"
        AppLanguage.FRENCH -> "MOTEUR DE SÉPARATION IA"
        AppLanguage.GERMAN -> "KI-SEPARATIONSMOTOR"
        AppLanguage.JAPANESE -> "AI音声分離エンジン"
        AppLanguage.CHINESE -> "AI 分离引擎"
        AppLanguage.RUSSIAN -> "ДВИЖОК РАЗДЕЛЕНИЯ ИИ"
        AppLanguage.PORTUGUESE -> "MOTOR DE SEPARAÇÃO IA"
        else -> "AI SEPARATION ENGINE"
    }

    fun spleeterBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "سبيليتر 2-STEM"
        AppLanguage.SPANISH -> "SPLEETER 2-STEM"
        AppLanguage.FRENCH -> "SPLEETER 2-STEM"
        AppLanguage.GERMAN -> "SPLEETER 2-STEM"
        AppLanguage.JAPANESE -> "SPLEETER 2-STEM"
        AppLanguage.CHINESE -> "SPLEETER 双音轨"
        AppLanguage.RUSSIAN -> "SPLEETER 2-STEM"
        AppLanguage.PORTUGUESE -> "SPLEETER 2-STEM"
        else -> "SPLEETER 2-STEM"
    }

    fun changeModel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تغيير المحرك أو السرعة"
        AppLanguage.SPANISH -> "Cambiar modelo o velocidad"
        AppLanguage.FRENCH -> "Changer le modèle ou la vitesse"
        AppLanguage.GERMAN -> "Modell oder Geschwindigkeit ändern"
        AppLanguage.JAPANESE -> "モデルまたは速度を変更"
        AppLanguage.CHINESE -> "更改模型或速度"
        AppLanguage.RUSSIAN -> "Изменить модель или скорость"
        AppLanguage.PORTUGUESE -> "Mudar modelo ou velocidade"
        else -> "Change Model"
    }

    // Empty State
    fun emptyStateTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "لم تتم إضافة أي وسائط حتى الآن"
        AppLanguage.SPANISH -> "Aún no se ha añadido audio ni vídeo"
        AppLanguage.FRENCH -> "Aucun fichier audio ou vidéo ajouté"
        AppLanguage.GERMAN -> "Noch kein Audio oder Video hinzugefügt"
        AppLanguage.JAPANESE -> "メディアファイルがまだありません"
        AppLanguage.CHINESE -> "尚未添加音频或视频"
        AppLanguage.RUSSIAN -> "Аудио или видео еще не добавлены"
        AppLanguage.PORTUGUESE -> "Nenhum áudio ou vídeo adicionado ainda"
        else -> "No Audio or Video Added Yet"
    }

    fun emptyStateDescription(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استورد مقاطع صوتية (MP3، WAV، FLAC، M4A) أو فيديوهات لعزل صوت المغني أو استخراج الموسيقى التصويرية باستخدام محرك سبلتر الثنائي."
        AppLanguage.SPANISH -> "Importa canciones (MP3, WAV, FLAC, M4A) o vídeos para aislar voces o extraer pistas instrumentales usando el motor neuronal Spleeter."
        AppLanguage.FRENCH -> "Importez des pistes audio (MP3, WAV, FLAC, M4A) ou des vidéos pour isoler la voix ou la musique avec le moteur Spleeter 2-Stem."
        AppLanguage.GERMAN -> "Importieren Sie Audiodateien (MP3, WAV, FLAC, M4A) oder Videos, um Gesang oder Instrumentalmusik mit Spleeter 2-Stem zu trennen."
        AppLanguage.JAPANESE -> "音声ファイル (MP3, WAV, FLAC, M4A) または動画をインポートして、Spleeter 2-Stem エンジンでボーカルや伴奏を分離します。"
        AppLanguage.CHINESE -> "导入音频曲目 (MP3, WAV, FLAC, M4A) 或音乐视频，通过 Spleeter 双音轨神经网络精确提取人声或伴奏。"
        AppLanguage.RUSSIAN -> "Импортируйте аудио (MP3, WAV, FLAC, M4A) или видео, чтобы изолировать вокал или инструментал с помощью нейросети Spleeter 2-Stem."
        AppLanguage.PORTUGUESE -> "Importe faixas de áudio (MP3, WAV, FLAC, M4A) ou vídeos para isolar vozes ou faixas instrumentais usando o Spleeter 2-Stem."
        else -> "Import audio tracks (MP3, WAV, FLAC, M4A) or music videos to isolate vocals or instrumental backings using the Spleeter 2-Stem engine."
    }

    fun browseStorageFolders(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصفح مجلدات التخزين"
        AppLanguage.SPANISH -> "Explorar carpetas de almacenamiento"
        AppLanguage.FRENCH -> "Parcourir les dossiers de stockage"
        AppLanguage.GERMAN -> "Speicherordner durchsuchen"
        AppLanguage.JAPANESE -> "ストレージフォルダを参照"
        AppLanguage.CHINESE -> "浏览存储文件夹"
        AppLanguage.RUSSIAN -> "Обзор папок хранилища"
        AppLanguage.PORTUGUESE -> "Navegar nas pastas"
        else -> "Browse Storage Folders"
    }

    fun importAudioOrVideoFile(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد ملف صوت أو فيديو"
        AppLanguage.SPANISH -> "Importar archivo de audio o vídeo"
        AppLanguage.FRENCH -> "Importer un fichier audio ou vidéo"
        AppLanguage.GERMAN -> "Audio- oder Videodatei importieren"
        AppLanguage.JAPANESE -> "音声・動画ファイルをインポート"
        AppLanguage.CHINESE -> "导入音频或视频文件"
        AppLanguage.RUSSIAN -> "Импорт аудио или видео файла"
        AppLanguage.PORTUGUESE -> "Importar arquivo de áudio ou vídeo"
        else -> "Import Audio or Video File"
    }

    fun fromYoutubeOrLink(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "من رابط يوتيوب أو مواقع التواصل"
        AppLanguage.SPANISH -> "Desde YouTube o enlace web"
        AppLanguage.FRENCH -> "Depuis un lien YouTube ou réseau social"
        AppLanguage.GERMAN -> "Von YouTube oder Social-Media-Link"
        AppLanguage.JAPANESE -> "YouTube / SNSリンクから"
        AppLanguage.CHINESE -> "从 YouTube / 社交链接导入"
        AppLanguage.RUSSIAN -> "Из YouTube или интернет-ссылки"
        AppLanguage.PORTUGUESE -> "Do YouTube ou link da web"
        else -> "From YouTube / Social Link"
    }

    fun loadDemoMusicVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تحميل فيديو موسيقي تجريبي"
        AppLanguage.SPANISH -> "Cargar vídeo musical de prueba"
        AppLanguage.FRENCH -> "Charger une vidéo musicale démo"
        AppLanguage.GERMAN -> "Demo-Musikvideo laden"
        AppLanguage.JAPANESE -> "デモ音楽動画を読み込む"
        AppLanguage.CHINESE -> "加载示范音乐视频"
        AppLanguage.RUSSIAN -> "Загрузить демо-видео"
        AppLanguage.PORTUGUESE -> "Carregar vídeo demo"
        else -> "Load Demo Music Video"
    }

    fun listTip(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اضغط للتشغيل مع عازل الصوت. اضغط مطولاً للخيارات وإعادة المعالجة."
        AppLanguage.SPANISH -> "Toca para reproducir. Mantén presionado para opciones y reextracción."
        AppLanguage.FRENCH -> "Appuyez pour lire. Appui long pour les options et la réextraction."
        AppLanguage.GERMAN -> "Tippen zum Abspielen. Gedrückt halten für Optionen & erneutes Trennen."
        AppLanguage.JAPANESE -> "タップで再生。長押しでオプションと再抽出。"
        AppLanguage.CHINESE -> "点击播放人声提取音频。长按打开选项与重新提取。"
        AppLanguage.RUSSIAN -> "Нажмите для воспроизведения. Удерживайте для параметров и повторной обработки."
        AppLanguage.PORTUGUESE -> "Toque para reproduzir. Segure para opções e re-extração."
        else -> "Tap to play with vocal extractor. Long-press for options & re-extraction."
    }

    // Status Badges
    fun badgeReady(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جاهز"
        AppLanguage.SPANISH -> "Listo"
        AppLanguage.FRENCH -> "Prêt"
        AppLanguage.GERMAN -> "Bereit"
        AppLanguage.JAPANESE -> "完了"
        AppLanguage.CHINESE -> "就绪"
        AppLanguage.RUSSIAN -> "Готово"
        AppLanguage.PORTUGUESE -> "Pronto"
        else -> "Ready"
    }

    fun badgeProcessing(lang: AppLanguage, elapsed: String = "") = when (lang) {
        AppLanguage.ARABIC -> if (elapsed.isNotBlank()) "عزل $elapsed" else "معالجة…"
        AppLanguage.SPANISH -> if (elapsed.isNotBlank()) "Extrayendo $elapsed" else "Procesando…"
        AppLanguage.FRENCH -> if (elapsed.isNotBlank()) "Extraction $elapsed" else "Traitement…"
        AppLanguage.GERMAN -> if (elapsed.isNotBlank()) "Trennen $elapsed" else "Verarbeiten…"
        AppLanguage.JAPANESE -> if (elapsed.isNotBlank()) "抽出中 $elapsed" else "処理中…"
        AppLanguage.CHINESE -> if (elapsed.isNotBlank()) "正在提取 $elapsed" else "处理中…"
        AppLanguage.RUSSIAN -> if (elapsed.isNotBlank()) "Извлечение $elapsed" else "Обработка…"
        AppLanguage.PORTUGUESE -> if (elapsed.isNotBlank()) "Extraindo $elapsed" else "Processando…"
        else -> if (elapsed.isNotBlank()) "Extracting $elapsed" else "Processing…"
    }

    fun badgeError(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "خطأ"
        AppLanguage.SPANISH -> "Error"
        AppLanguage.FRENCH -> "Erreur"
        AppLanguage.GERMAN -> "Fehler"
        AppLanguage.JAPANESE -> "エラー"
        AppLanguage.CHINESE -> "错误"
        AppLanguage.RUSSIAN -> "Ошибка"
        AppLanguage.PORTUGUESE -> "Erro"
        else -> "Error"
    }

    fun badgeNew(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "في الانتظار"
        AppLanguage.SPANISH -> "En espera"
        AppLanguage.FRENCH -> "En attente"
        AppLanguage.GERMAN -> "Wartend"
        AppLanguage.JAPANESE -> "待機中"
        AppLanguage.CHINESE -> "等待处理"
        AppLanguage.RUSSIAN -> "В очереди"
        AppLanguage.PORTUGUESE -> "Aguardando"
        else -> "Waiting"
    }

    fun badgeAudio(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "صوت"
        AppLanguage.SPANISH -> "AUDIO"
        AppLanguage.FRENCH -> "AUDIO"
        AppLanguage.GERMAN -> "AUDIO"
        AppLanguage.JAPANESE -> "音声"
        AppLanguage.CHINESE -> "音频"
        AppLanguage.RUSSIAN -> "АУДИО"
        AppLanguage.PORTUGUESE -> "ÁUDIO"
        else -> "AUDIO"
    }

    fun badgeVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "فيديو"
        AppLanguage.SPANISH -> "VÍDEO"
        AppLanguage.FRENCH -> "VIDÉO"
        AppLanguage.GERMAN -> "VIDEO"
        AppLanguage.JAPANESE -> "動画"
        AppLanguage.CHINESE -> "视频"
        AppLanguage.RUSSIAN -> "ВИДЕО"
        AppLanguage.PORTUGUESE -> "VÍDEO"
        else -> "VIDEO"
    }

    // Video Action Options Dialog
    fun chooseActionTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اختر الإجراء لهذا المقطع:"
        AppLanguage.SPANISH -> "Elige una acción para esta pista:"
        AppLanguage.FRENCH -> "Choisissez une action pour ce fichier :"
        AppLanguage.GERMAN -> "Aktion für diesen Titel wählen:"
        AppLanguage.JAPANESE -> "このトラックのアクションを選択:"
        AppLanguage.CHINESE -> "选择此曲目的操作:"
        AppLanguage.RUSSIAN -> "Выберите действие для этого трека:"
        AppLanguage.PORTUGUESE -> "Escolha uma ação para esta faixa:"
        else -> "Choose an action for this video track:"
    }

    fun exportVideoVocalsOnly(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير الفيديو (الصوت المعزول فقط)"
        AppLanguage.SPANISH -> "Exportar vídeo (solo voces)"
        AppLanguage.FRENCH -> "Exporter la vidéo (voix uniquement)"
        AppLanguage.GERMAN -> "Video exportieren (nur Gesang)"
        AppLanguage.JAPANESE -> "動画を書き出し（ボーカルのみ）"
        AppLanguage.CHINESE -> "导出视频 (仅人声音轨)"
        AppLanguage.RUSSIAN -> "Экспорт видео (только вокал)"
        AppLanguage.PORTUGUESE -> "Exportar vídeo (apenas vocais)"
        else -> "Export Video (Vocals Only)"
    }

    fun reextractVocalsWithAi(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة عزل الأصوات بـ Spleeter"
        AppLanguage.SPANISH -> "Reextraer voces con Spleeter"
        AppLanguage.FRENCH -> "Réextraire les voix avec Spleeter"
        AppLanguage.GERMAN -> "Gesang mit Spleeter erneut trennen"
        AppLanguage.JAPANESE -> "Spleeter AI でボーカルを再抽出"
        AppLanguage.CHINESE -> "使用 Spleeter 重新提取人声"
        AppLanguage.RUSSIAN -> "Повторно выделить вокал через Spleeter"
        AppLanguage.PORTUGUESE -> "Re-extrair vocais com Spleeter"
        else -> "Re-extract Vocals with Spleeter AI"
    }

    fun deleteVideo(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف المقطع"
        AppLanguage.SPANISH -> "Eliminar pista"
        AppLanguage.FRENCH -> "Supprimer le fichier"
        AppLanguage.GERMAN -> "Datei löschen"
        AppLanguage.JAPANESE -> "トラックを削除"
        AppLanguage.CHINESE -> "删除视频/音频"
        AppLanguage.RUSSIAN -> "Удалить трек"
        AppLanguage.PORTUGUESE -> "Excluir faixa"
        else -> "Delete Video"
    }

    fun cancel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إلغاء"
        AppLanguage.SPANISH -> "Cancelar"
        AppLanguage.FRENCH -> "Annuler"
        AppLanguage.GERMAN -> "Abbrechen"
        AppLanguage.JAPANESE -> "キャンセル"
        AppLanguage.CHINESE -> "取消"
        AppLanguage.RUSSIAN -> "Отмена"
        AppLanguage.PORTUGUESE -> "Cancelar"
        else -> "Cancel"
    }

    fun delete(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف"
        AppLanguage.SPANISH -> "Eliminar"
        AppLanguage.FRENCH -> "Supprimer"
        AppLanguage.GERMAN -> "Löschen"
        AppLanguage.JAPANESE -> "削除"
        AppLanguage.CHINESE -> "删除"
        AppLanguage.RUSSIAN -> "Удалить"
        AppLanguage.PORTUGUESE -> "Excluir"
        else -> "Delete"
    }

    fun retry(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة المحاولة"
        AppLanguage.SPANISH -> "Reintentar"
        AppLanguage.FRENCH -> "Réessayer"
        AppLanguage.GERMAN -> "Wiederholen"
        AppLanguage.JAPANESE -> "再試行"
        AppLanguage.CHINESE -> "重试"
        AppLanguage.RUSSIAN -> "Повторить"
        AppLanguage.PORTUGUESE -> "Repetir"
        else -> "Retry"
    }

    fun reprocess(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة المعالجة"
        AppLanguage.SPANISH -> "Reprocesar"
        AppLanguage.FRENCH -> "Retraiter"
        AppLanguage.GERMAN -> "Erneut verarbeiten"
        AppLanguage.JAPANESE -> "再処理"
        AppLanguage.CHINESE -> "重新处理"
        AppLanguage.RUSSIAN -> "Переработать"
        AppLanguage.PORTUGUESE -> "Reprocessar"
        else -> "Reprocess"
    }

    fun export(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير"
        AppLanguage.SPANISH -> "Exportar"
        AppLanguage.FRENCH -> "Exporter"
        AppLanguage.GERMAN -> "Exportieren"
        AppLanguage.JAPANESE -> "書き出し"
        AppLanguage.CHINESE -> "导出"
        AppLanguage.RUSSIAN -> "Экспорт"
        AppLanguage.PORTUGUESE -> "Exportar"
        else -> "Export"
    }

    fun exportMedia(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير الملفات"
        AppLanguage.SPANISH -> "Exportar medios"
        AppLanguage.FRENCH -> "Exporter les médias"
        AppLanguage.GERMAN -> "Medien exportieren"
        AppLanguage.JAPANESE -> "メディアを書き出し"
        AppLanguage.CHINESE -> "导出媒体"
        AppLanguage.RUSSIAN -> "Экспорт медиа"
        AppLanguage.PORTUGUESE -> "Exportar mídia"
        else -> "Export Media"
    }

    fun exportVideoWithVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير الفيديو مع الصوت المعزول"
        AppLanguage.SPANISH -> "Exportar vídeo con voces"
        AppLanguage.FRENCH -> "Exporter vidéo avec voix"
        AppLanguage.GERMAN -> "Video mit Stimmen exportieren"
        AppLanguage.JAPANESE -> "ボーカル付き動画を書き出し"
        AppLanguage.CHINESE -> "导出含人声的视频"
        AppLanguage.RUSSIAN -> "Экспорт видео с изолированным вокалом"
        AppLanguage.PORTUGUESE -> "Exportar vídeo com vocais"
        else -> "Export Video with Vocals"
    }

    // Active Download Banner
    fun downloadingWebMedia(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ تنزيل الوسائط من الويب"
        AppLanguage.SPANISH -> "Descargando medios de la web"
        AppLanguage.FRENCH -> "Téléchargement de média Web"
        AppLanguage.GERMAN -> "Web-Medien werden heruntergeladen"
        AppLanguage.JAPANESE -> "ウェブメディアをダウンロード中"
        AppLanguage.CHINESE -> "正在下载网络媒体"
        AppLanguage.RUSSIAN -> "Загрузка медиа из интернета"
        AppLanguage.PORTUGUESE -> "Baixando mídia da web"
        else -> "Downloading Web Media"
    }

    fun downloadingInBackground(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التنزيل قيد التشغيل في الخلفية…"
        AppLanguage.SPANISH -> "Descargando en segundo plano…"
        AppLanguage.FRENCH -> "Téléchargement en arrière-plan…"
        AppLanguage.GERMAN -> "Download läuft im Hintergrund…"
        AppLanguage.JAPANESE -> "バックグラウンドでダウンロード中…"
        AppLanguage.CHINESE -> "正在后台下载…"
        AppLanguage.RUSSIAN -> "Загрузка в фоновом режиме…"
        AppLanguage.PORTUGUESE -> "Baixando em segundo plano…"
        else -> "Downloading in Background…"
    }

    fun tapToViewDetails(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "اضغط لعرض التفاصيل"
        AppLanguage.SPANISH -> "Toca para ver detalles"
        AppLanguage.FRENCH -> "Appuyez pour voir les détails"
        AppLanguage.GERMAN -> "Tippen für Details"
        AppLanguage.JAPANESE -> "タップして詳細を表示"
        AppLanguage.CHINESE -> "点击查看详情"
        AppLanguage.RUSSIAN -> "Нажмите для подробностей"
        AppLanguage.PORTUGUESE -> "Toque para ver detalhes"
        else -> "Tap to view details"
    }

    fun viewButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عرض"
        AppLanguage.SPANISH -> "Ver"
        AppLanguage.FRENCH -> "Voir"
        AppLanguage.GERMAN -> "Ansicht"
        AppLanguage.JAPANESE -> "表示"
        AppLanguage.CHINESE -> "查看"
        AppLanguage.RUSSIAN -> "Открыть"
        AppLanguage.PORTUGUESE -> "Ver"
        else -> "View"
    }

    fun cancelDownload(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إلغاء التنزيل"
        AppLanguage.SPANISH -> "Cancelar descarga"
        AppLanguage.FRENCH -> "Annuler le téléchargement"
        AppLanguage.GERMAN -> "Download abbrechen"
        AppLanguage.JAPANESE -> "ダウンロードをキャンセル"
        AppLanguage.CHINESE -> "取消下载"
        AppLanguage.RUSSIAN -> "Отменить загрузку"
        AppLanguage.PORTUGUESE -> "Cancelar download"
        else -> "Cancel Download"
    }

    // Active Processing Banner
    fun extractingVocalsBanner(lang: AppLanguage, title: String) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ عزل: $title"
        AppLanguage.SPANISH -> "Extrayendo: $title"
        AppLanguage.FRENCH -> "Extraction : $title"
        AppLanguage.GERMAN -> "Trennen: $title"
        AppLanguage.JAPANESE -> "分離中: $title"
        AppLanguage.CHINESE -> "正在提取: $title"
        AppLanguage.RUSSIAN -> "Извлечение: $title"
        AppLanguage.PORTUGUESE -> "Extraindo: $title"
        else -> "Extracting: $title"
    }

    fun aiVocalExtractionActive(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عزل Spleeter الثنائي نشط"
        AppLanguage.SPANISH -> "Separación Spleeter 2-Stem activa"
        AppLanguage.FRENCH -> "Séparation Spleeter 2-Stem active"
        AppLanguage.GERMAN -> "Spleeter 2-Stem Trennung aktiv"
        AppLanguage.JAPANESE -> "Spleeter 2-Stem AI 分離実行中"
        AppLanguage.CHINESE -> "Spleeter 双音轨 AI 提取中"
        AppLanguage.RUSSIAN -> "Spleeter 2-Stem разделение активно"
        AppLanguage.PORTUGUESE -> "Separação Spleeter 2-Stem ativa"
        else -> "Spleeter 2-Stem AI Active"
    }

    fun backgroundSafe(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "آمن في الخلفية"
        AppLanguage.SPANISH -> "Seguro en segundo plano"
        AppLanguage.FRENCH -> "Sûr en arrière-plan"
        AppLanguage.GERMAN -> "Hintergrund-geschützt"
        AppLanguage.JAPANESE -> "バックグラウンド実行中"
        AppLanguage.CHINESE -> "后台受保护运行"
        AppLanguage.RUSSIAN -> "Работает в фоне"
        AppLanguage.PORTUGUESE -> "Protegido em 2º plano"
        else -> "Background safe"
    }

    // Player Screen
    fun playingIsolatedVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تشغيل: الصوت البشري المعزول"
        AppLanguage.SPANISH -> "Reproduciendo: Voces aisladas por IA"
        AppLanguage.FRENCH -> "Lecture : Voix isolées par l'IA"
        AppLanguage.GERMAN -> "Wiedergabe: KI-isolierter Gesang"
        AppLanguage.JAPANESE -> "再生中: AI分離ボーカル"
        AppLanguage.CHINESE -> "正在播放: AI 分离人声"
        AppLanguage.RUSSIAN -> "Воспроизведение: Изолированный вокал"
        AppLanguage.PORTUGUESE -> "Reproduzindo: Vocais isolados por IA"
        else -> "Playing: AI Isolated Vocals"
    }

    fun playingOriginalAudio(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تشغيل: الصوت الأصلي الكامل"
        AppLanguage.SPANISH -> "Reproduciendo: Audio original"
        AppLanguage.FRENCH -> "Lecture : Audio original"
        AppLanguage.GERMAN -> "Wiedergabe: Original-Audio"
        AppLanguage.JAPANESE -> "再生中: オリジナル音声"
        AppLanguage.CHINESE -> "正在播放: 原始完整音频"
        AppLanguage.RUSSIAN -> "Воспроизведение: Исходное аудио"
        AppLanguage.PORTUGUESE -> "Reproduzindo: Áudio original"
        else -> "Playing: Original Audio"
    }

    fun chipAiVocalsOnly(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول فقط"
        AppLanguage.SPANISH -> "Solo voces IA"
        AppLanguage.FRENCH -> "Voix IA uniquement"
        AppLanguage.GERMAN -> "Nur KI-Gesang"
        AppLanguage.JAPANESE -> "AIボーカルのみ"
        AppLanguage.CHINESE -> "仅 AI 人声"
        AppLanguage.RUSSIAN -> "Только вокал ИИ"
        AppLanguage.PORTUGUESE -> "Apenas vocais IA"
        else -> "AI Vocals Only"
    }

    fun chipOriginalAudio(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت الأصلي"
        AppLanguage.SPANISH -> "Audio original"
        AppLanguage.FRENCH -> "Audio original"
        AppLanguage.GERMAN -> "Original-Audio"
        AppLanguage.JAPANESE -> "オリジナル音声"
        AppLanguage.CHINESE -> "原始音频"
        AppLanguage.RUSSIAN -> "Исходное аудио"
        AppLanguage.PORTUGUESE -> "Áudio original"
        else -> "Original Audio"
    }

    fun playbackUnavailable(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التشغيل غير متاح"
        AppLanguage.SPANISH -> "Reproducción no disponible"
        AppLanguage.FRENCH -> "Lecture non disponible"
        AppLanguage.GERMAN -> "Wiedergabe nicht verfügbar"
        AppLanguage.JAPANESE -> "再生できません"
        AppLanguage.CHINESE -> "暂无法播放"
        AppLanguage.RUSSIAN -> "Воспроизведение недоступно"
        AppLanguage.PORTUGUESE -> "Reprodução indisponível"
        else -> "Playback unavailable"
    }

    fun preparingMedia(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ تجهيز الوسائط…"
        AppLanguage.SPANISH -> "Preparando medios…"
        AppLanguage.FRENCH -> "Préparation du média…"
        AppLanguage.GERMAN -> "Medien werden vorbereitet…"
        AppLanguage.JAPANESE -> "メディアを準備中…"
        AppLanguage.CHINESE -> "正在准备媒体…"
        AppLanguage.RUSSIAN -> "Подготовка медиа…"
        AppLanguage.PORTUGUESE -> "Preparando mídia…"
        else -> "Preparing media…"
    }

    fun vocalExtractionIssue(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حدث خطأ أثناء عزل الصوت"
        AppLanguage.SPANISH -> "Problema al extraer la pista vocal"
        AppLanguage.FRENCH -> "Problème lors de l'extraction vocale"
        AppLanguage.GERMAN -> "Problem bei der Stimmentrennung"
        AppLanguage.JAPANESE -> "ボーカル抽出中に問題が発生しました"
        AppLanguage.CHINESE -> "人声提取过程中遇到问题"
        AppLanguage.RUSSIAN -> "Возникла ошибка при извлечении вокала"
        AppLanguage.PORTUGUESE -> "Problema durante a extração vocal"
        else -> "Vocal extraction encountered an issue"
    }

    fun retryExtraction(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة محاولة العزل"
        AppLanguage.SPANISH -> "Reintentar extracción"
        AppLanguage.FRENCH -> "Réessayer l'extraction"
        AppLanguage.GERMAN -> "Trennung wiederholen"
        AppLanguage.JAPANESE -> "抽出を再試行"
        AppLanguage.CHINESE -> "重试提取"
        AppLanguage.RUSSIAN -> "Повторить извлечение"
        AppLanguage.PORTUGUESE -> "Repetir extração"
        else -> "Retry Extraction"
    }

    fun reextractVocals(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة عزل الأصوات"
        AppLanguage.SPANISH -> "Reextraer voces"
        AppLanguage.FRENCH -> "Réextraire les voix"
        AppLanguage.GERMAN -> "Gesang erneut trennen"
        AppLanguage.JAPANESE -> "ボーカルを再抽出"
        AppLanguage.CHINESE -> "重新提取人声"
        AppLanguage.RUSSIAN -> "Повторно выделить вокал"
        AppLanguage.PORTUGUESE -> "Re-extrair vocais"
        else -> "Re-extract Vocals"
    }

    // Export Options Dialog
    fun exportDialogTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "خيارات التصدير وحفظ الملفات"
        AppLanguage.SPANISH -> "Opciones de exportación"
        AppLanguage.FRENCH -> "Options d'exportation"
        AppLanguage.GERMAN -> "Exportoptionen"
        AppLanguage.JAPANESE -> "エクスポート設定"
        AppLanguage.CHINESE -> "媒体导出选项"
        AppLanguage.RUSSIAN -> "Параметры экспорта"
        AppLanguage.PORTUGUESE -> "Opções de exportação"
        else -> "Export Media"
    }

    fun exportDialogSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات الصوت والفيديو المدعومة"
        AppLanguage.SPANISH -> "Formatos de extracción de audio y vídeo"
        AppLanguage.FRENCH -> "Formats d'extraction audio et vidéo"
        AppLanguage.GERMAN -> "Audio- und Video-Exportformate"
        AppLanguage.JAPANESE -> "対応音声・動画フォーマット"
        AppLanguage.CHINESE -> "支持的音频与视频输出格式"
        AppLanguage.RUSSIAN -> "Форматы экспорта аудио и видео"
        AppLanguage.PORTUGUESE -> "Formatos de extração de áudio e vídeo"
        else -> "Audio & Video Extraction Formats"
    }

    fun audioExportFormats(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات تصدير الصوت"
        AppLanguage.SPANISH -> "FORMATOS DE EXPORTACIÓN DE AUDIO"
        AppLanguage.FRENCH -> "FORMATS D'EXPORTATION AUDIO"
        AppLanguage.GERMAN -> "AUDIO-EXPORTFORMATE"
        AppLanguage.JAPANESE -> "音声エクスポート形式"
        AppLanguage.CHINESE -> "音频导出格式"
        AppLanguage.RUSSIAN -> "ФОРМАТЫ ЭКСПОРТА АУДИО"
        AppLanguage.PORTUGUESE -> "FORMATOS DE EXPORTAÇÃO DE ÁUDIO"
        else -> "AUDIO EXPORT FORMATS"
    }

    fun videoExportFormats(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنسيقات تصدير الفيديو"
        AppLanguage.SPANISH -> "FORMATOS DE EXPORTACIÓN DE VÍDEO"
        AppLanguage.FRENCH -> "FORMATS D'EXPORTATION VIDÉO"
        AppLanguage.GERMAN -> "VIDEO-EXPORTFORMATE"
        AppLanguage.JAPANESE -> "動画エクスポート形式"
        AppLanguage.CHINESE -> "视频导出格式"
        AppLanguage.RUSSIAN -> "ФОРМАТЫ ЭКСПОРТА ВИДЕО"
        AppLanguage.PORTUGUESE -> "FORMATOS DE EXPORTAÇÃO DE VÍDEO"
        else -> "VIDEO EXPORT FORMATS"
    }

    fun fastSave(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حفظ سريع"
        AppLanguage.SPANISH -> "Guardado rápido"
        AppLanguage.FRENCH -> "Sauvegarde rapide"
        AppLanguage.GERMAN -> "Schnellspeichern"
        AppLanguage.JAPANESE -> "高速保存"
        AppLanguage.CHINESE -> "快速保存"
        AppLanguage.RUSSIAN -> "Быстрое сохранение"
        AppLanguage.PORTUGUESE -> "Salvamento rápido"
        else -> "Fast Save"
    }

    fun deleteOriginal(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف الفيديو الأصلي بعد نجاح التصدير"
        AppLanguage.SPANISH -> "Eliminar el vídeo original tras exportar"
        AppLanguage.FRENCH -> "Supprimer la vidéo d'origine après l'export"
        AppLanguage.GERMAN -> "Originalvideo nach erfolgreichem Export löschen"
        AppLanguage.JAPANESE -> "書き出し完了後に元の動画を削除"
        AppLanguage.CHINESE -> "导出成功后删除原始媒体"
        AppLanguage.RUSSIAN -> "Удалить исходное видео после экспорта"
        AppLanguage.PORTUGUESE -> "Excluir o vídeo original após exportar"
        else -> "Delete original video after export"
    }

    fun deleteOriginalSubtitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "يوفر مساحة تخزين الجهاز"
        AppLanguage.SPANISH -> "Ahorra espacio en el dispositivo"
        AppLanguage.FRENCH -> "Économise l'espace de stockage de l'appareil"
        AppLanguage.GERMAN -> "Spart Speicherplatz auf dem Gerät"
        AppLanguage.JAPANESE -> "端末のストレージ容量を節約します"
        AppLanguage.CHINESE -> "释放设备存储空间"
        AppLanguage.RUSSIAN -> "Освобождает место на устройстве"
        AppLanguage.PORTUGUESE -> "Economiza espaço de armazenamento"
        else -> "Saves device storage space"
    }

    fun startExport(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "بدء التصدير والحفظ"
        AppLanguage.SPANISH -> "Comenzar exportación"
        AppLanguage.FRENCH -> "Lancer l'exportation"
        AppLanguage.GERMAN -> "Export starten"
        AppLanguage.JAPANESE -> "エクスポートを開始"
        AppLanguage.CHINESE -> "开始导出并保存"
        AppLanguage.RUSSIAN -> "Начать экспорт"
        AppLanguage.PORTUGUESE -> "Iniciar exportação"
        else -> "Start Export"
    }

    fun formatVocalWavTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول فقط (WAV عالي الدقة)"
        AppLanguage.SPANISH -> "Pista vocal aislada (WAV sin pérdidas)"
        AppLanguage.FRENCH -> "Piste vocale isolée (WAV sans perte)"
        AppLanguage.GERMAN -> "Isolierte Gesangsspur (Verlustfreies WAV)"
        AppLanguage.JAPANESE -> "分離ボーカル（ロスレス WAV）"
        AppLanguage.CHINESE -> "独立人声音轨 (无损 WAV)"
        AppLanguage.RUSSIAN -> "Изолированный вокал (Lossless WAV)"
        AppLanguage.PORTUGUESE -> "Faixa vocal isolada (WAV lossless)"
        else -> "Isolated Vocal Track (Lossless WAV)"
    }

    fun formatVocalWavSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "صوت بشري نقي 16-bit بجودة الاستوديو بدون موسيقى"
        AppLanguage.SPANISH -> "Audio 16-bit de estudio sin música de fondo"
        AppLanguage.FRENCH -> "Audio vocal pur 16-bit qualité studio"
        AppLanguage.GERMAN -> "Reiner 16-Bit Studio-Gesang ohne Musik"
        AppLanguage.JAPANESE -> "伴奏なしの 16-bit スタジオ品質ボーカル"
        AppLanguage.CHINESE -> "16-bit 录音室级纯净人声，无伴奏干扰"
        AppLanguage.RUSSIAN -> "Чистый 16-бит студийный вокал без фоновой музыки"
        AppLanguage.PORTUGUESE -> "Voz limpa em 16-bit com qualidade de estúdio"
        else -> "Pure vocal audio in 16-bit studio quality with no music"
    }

    fun formatVocalM4aTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الصوت المعزول مضغوط (M4A / AAC)"
        AppLanguage.SPANISH -> "Voz aislada compacta (M4A / AAC)"
        AppLanguage.FRENCH -> "Voix isolée compacte (M4A / AAC)"
        AppLanguage.GERMAN -> "Kompakter Gesang (M4A / AAC)"
        AppLanguage.JAPANESE -> "分離ボーカル（M4A / AAC）"
        AppLanguage.CHINESE -> "压缩人声音轨 (M4A / AAC)"
        AppLanguage.RUSSIAN -> "Компактный вокал (M4A / AAC)"
        AppLanguage.PORTUGUESE -> "Voz isolada compacta (M4A / AAC)"
        else -> "Isolated Vocal Track (M4A / AAC)"
    }

    fun formatVocalM4aSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حجم ملف صغير وجودة صوت ممتازة للمشاركة السريعة"
        AppLanguage.SPANISH -> "Tamaño reducido y gran fidelidad para compartir"
        AppLanguage.FRENCH -> "Taille légère et haute fidélité pour le partage"
        AppLanguage.GERMAN -> "Kleine Dateigröße bei hoher Audioqualität"
        AppLanguage.JAPANESE -> "共有しやすい軽量かつ高音質な AAC 形式"
        AppLanguage.CHINESE -> "文件轻巧且保真度高，适合快速分享"
        AppLanguage.RUSSIAN -> "Малый размер файла при высоком качестве"
        AppLanguage.PORTUGUESE -> "Arquivo leve com alta fidelidade para compartilhar"
        else -> "Lightweight file size with high fidelity for sharing"
    }

    fun formatInstWavTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الموسيقى والخلفية / كاريوكي (WAV)"
        AppLanguage.SPANISH -> "Pista instrumental / Karaoke (WAV)"
        AppLanguage.FRENCH -> "Piste instrumentale / Karaoké (WAV)"
        AppLanguage.GERMAN -> "Instrumental / Karaoke (WAV)"
        AppLanguage.JAPANESE -> "伴奏・カラオケ音源（WAV）"
        AppLanguage.CHINESE -> "伴奏 / 卡拉OK音轨 (无损 WAV)"
        AppLanguage.RUSSIAN -> "Инструментал / Караоке (WAV)"
        AppLanguage.PORTUGUESE -> "Faixa instrumental / Karaokê (WAV)"
        else -> "Instrumental / Karaoke Track (WAV)"
    }

    fun formatInstWavSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تمت إزالة الصوت البشري • خلفية موسيقية كاملة 16-bit"
        AppLanguage.SPANISH -> "Voces eliminadas • Pista instrumental completa en WAV"
        AppLanguage.FRENCH -> "Voix supprimées • Musique d'accompagnement complète en WAV"
        AppLanguage.GERMAN -> "Gesang entfernt • Vollständige Begleitung in 16-Bit WAV"
        AppLanguage.JAPANESE -> "ボーカル消去済み • 完全な 16-bit 伴奏トラック"
        AppLanguage.CHINESE -> "人声已消除 • 完整 16-bit 伴奏背景音乐"
        AppLanguage.RUSSIAN -> "Вокал удален • Полный инструментал в 16-бит WAV"
        AppLanguage.PORTUGUESE -> "Vocais removidos • Música de fundo completa em WAV"
        else -> "Acapella removed • Full backing music in 16-bit studio WAV"
    }

    fun formatInstM4aTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الموسيقى والخلفية / كاريوكي (M4A / AAC)"
        AppLanguage.SPANISH -> "Instrumental / Karaoke (M4A / AAC)"
        AppLanguage.FRENCH -> "Instrumental / Karaoké (M4A / AAC)"
        AppLanguage.GERMAN -> "Instrumental / Karaoke (M4A / AAC)"
        AppLanguage.JAPANESE -> "伴奏・カラオケ（M4A / AAC）"
        AppLanguage.CHINESE -> "伴奏 / 卡拉OK (M4A / AAC)"
        AppLanguage.RUSSIAN -> "Инструментал / Караоке (M4A / AAC)"
        AppLanguage.PORTUGUESE -> "Instrumental / Karaokê (M4A / AAC)"
        else -> "Instrumental / Karaoke (M4A / AAC)"
    }

    fun formatInstM4aSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "موسيقى بدون أصوات بحجم خفيف وتنسيق AAC"
        AppLanguage.SPANISH -> "Música de fondo en formato AAC compacto"
        AppLanguage.FRENCH -> "Musique d'accompagnement en AAC compact"
        AppLanguage.GERMAN -> "Kompaktes AAC-Begleit-Audio"
        AppLanguage.JAPANESE -> "軽量な AAC フォーマットの伴奏音源"
        AppLanguage.CHINESE -> "小体积 AAC 伴奏音轨"
        AppLanguage.RUSSIAN -> "Компактный AAC инструментал"
        AppLanguage.PORTUGUESE -> "Música de fundo em AAC compacto"
        else -> "Backing accompaniment track in compact high-quality AAC"
    }

    fun formatVideoVocalsTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "فيديو مع الصوت المعزول فقط (MP4)"
        AppLanguage.SPANISH -> "Vídeo solo con voces (MP4)"
        AppLanguage.FRENCH -> "Vidéo avec voix uniquement (MP4)"
        AppLanguage.GERMAN -> "Video nur mit Stimmen (MP4)"
        AppLanguage.JAPANESE -> "ボーカルのみ動画（MP4）"
        AppLanguage.CHINESE -> "仅保留人声的视频 (MP4)"
        AppLanguage.RUSSIAN -> "Видео только с вокалом (MP4)"
        AppLanguage.PORTUGUESE -> "Vídeo apenas com vocais (MP4)"
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
        AppLanguage.JAPANESE -> if (isCacheReady)
            "⚡ キャッシュから瞬時にエクスポート • 待ち時間ゼロ！"
        else
            "元動画と AI で抽出したボーカルを合成"
        AppLanguage.CHINESE -> if (isCacheReady)
            "⚡ 实时缓存即时导出 • 无需任何合成等待！"
        else
            "将原视频与 AI 分离的人声进行合成"
        AppLanguage.RUSSIAN -> if (isCacheReady)
            "⚡ Мгновенный экспорт из кэша • Без ожидания сведения!"
        else
            "Объединяет видеопоток с выделенным вокалом"
        AppLanguage.PORTUGUESE -> if (isCacheReady)
            "⚡ Exportação instantânea do cache • Zero tempo de espera!"
        else
            "Combina o vídeo original com os vocais isolados"
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
        AppLanguage.JAPANESE -> "カラオケ動画（MP4）"
        AppLanguage.CHINESE -> "伴奏/卡拉OK视频 (MP4)"
        AppLanguage.RUSSIAN -> "Караоке-видео (MP4)"
        AppLanguage.PORTUGUESE -> "Vídeo karaokê (MP4)"
        else -> "Video with Karaoke Track (MP4)"
    }

    fun formatVideoInstSub(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "دمج فيديو المصدر مع الموسيقى التصويرية دون الصوت البشري"
        AppLanguage.SPANISH -> "Combina el vídeo original con audio instrumental"
        AppLanguage.FRENCH -> "Combine la vidéo originale avec la musique instrumentale"
        AppLanguage.GERMAN -> "Kombiniert Original-Videostream mit Instrumental-Audio"
        AppLanguage.JAPANESE -> "元動画と伴奏トラックを合成"
        AppLanguage.CHINESE -> "将原视频与纯伴奏音轨合成"
        AppLanguage.RUSSIAN -> "Объединяет видеопоток с инструментальной музыкой"
        AppLanguage.PORTUGUESE -> "Combina o vídeo original com música instrumental"
        else -> "Combines original video stream with instrumental audio"
    }

    // Model Settings Dialog
    fun modelSettingsTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "محرك الذكاء الاصطناعي وتسريع العتاد"
        AppLanguage.SPANISH -> "Motor IA y aceleración de hardware"
        AppLanguage.FRENCH -> "Moteur IA et accélération matérielle"
        AppLanguage.GERMAN -> "KI-Engine & Hardware-Beschleunigung"
        AppLanguage.JAPANESE -> "AIエンジンとハードウェアアクセラレーション"
        AppLanguage.CHINESE -> "AI 引擎与硬件加速"
        AppLanguage.RUSSIAN -> "Движок ИИ и аппаратное ускорение"
        AppLanguage.PORTUGUESE -> "Motor de IA e aceleração de hardware"
        else -> "AI Engine & Acceleration"
    }

    fun activeHardwareAcceleration(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تسريع العتاد النشط"
        AppLanguage.SPANISH -> "ACELERACIÓN DE HARDWARE ACTIVA"
        AppLanguage.FRENCH -> "ACCÉLÉRATION MATÉRIELLE ACTIVE"
        AppLanguage.GERMAN -> "AKTIVE HARDWARE-BESCHLEUNIGUNG"
        AppLanguage.JAPANESE -> "アクティブなハードウェアアクセラレーション"
        AppLanguage.CHINESE -> "当前硬件加速状态"
        AppLanguage.RUSSIAN -> "АКТИВНОЕ АППАРАТНОЕ УСКОРЕНИЕ"
        AppLanguage.PORTUGUESE -> "ACELERAÇÃO DE HARDWARE ATIVA"
        else -> "ACTIVE HARDWARE ACCELERATION"
    }

    fun speedProfileHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نمط سرعة العزل بالذكاء الاصطناعي"
        AppLanguage.SPANISH -> "PERFIL DE VELOCIDAD DE SEPARACIÓN"
        AppLanguage.FRENCH -> "PROFIL DE VITESSE DE SÉPARATION"
        AppLanguage.GERMAN -> "GESCHWINDIGKEITSPROFIL"
        AppLanguage.JAPANESE -> "分離処理スピードプロファイル"
        AppLanguage.CHINESE -> "分离处理速度配置"
        AppLanguage.RUSSIAN -> "ПРОФИЛЬ СКОРОСТИ РАЗДЕЛЕНИЯ"
        AppLanguage.PORTUGUESE -> "PERFIL DE VELOCIDADE DE SEPARAÇÃO"
        else -> "SEPARATION SPEED PROFILE"
    }

    fun customModelBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نموذج مخصص"
        AppLanguage.SPANISH -> "MODELO PERSONALIZADO"
        AppLanguage.FRENCH -> "MODÈLE PERSONNALISÉ"
        AppLanguage.GERMAN -> "BENUTZERDEFINIERTES MODELL"
        AppLanguage.JAPANESE -> "カスタムモデル"
        AppLanguage.CHINESE -> "自定义模型"
        AppLanguage.RUSSIAN -> "ПОЛЬЗОВАТЕЛЬСКАЯ МОДЕЛЬ"
        AppLanguage.PORTUGUESE -> "MODELO PERSONALIZADO"
        else -> "CUSTOM MODEL"
    }

    fun builtInModelBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "شبكة سبلتر العصبية المدمجة"
        AppLanguage.SPANISH -> "RED NEURONAL SPLEETER INTEGRADA"
        AppLanguage.FRENCH -> "RÉSEAU NEURONAL SPLEETER INTÉGRÉ"
        AppLanguage.GERMAN -> "INTEGRIERTES SPLEETER NEURONALES NETZ"
        AppLanguage.JAPANESE -> "内蔵 SPLEETER ニューラルネットワーク"
        AppLanguage.CHINESE -> "内置 SPLEETER 神经网络"
        AppLanguage.RUSSIAN -> "ВСТРОЕННАЯ НЕЙРОСЕТЬ SPLEETER"
        AppLanguage.PORTUGUESE -> "REDE NEURAL SPLEETER INTEGRADA"
        else -> "BUILT-IN SPLEETER NEURAL NETWORK"
    }

    fun importCustomModelButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استيراد نموذج سبلتر مخصص (.onnx)"
        AppLanguage.SPANISH -> "Importar modelo Spleeter (.onnx)"
        AppLanguage.FRENCH -> "Importer modèle Spleeter (.onnx)"
        AppLanguage.GERMAN -> "Eigenes Spleeter-Modell importieren (.onnx)"
        AppLanguage.JAPANESE -> "カスタム Spleeter モデルをインポート (.onnx)"
        AppLanguage.CHINESE -> "导入自定义 Spleeter 模型 (.onnx)"
        AppLanguage.RUSSIAN -> "Импортировать Spleeter модель (.onnx)"
        AppLanguage.PORTUGUESE -> "Importar modelo Spleeter (.onnx)"
        else -> "Import Custom Spleeter Model (.onnx)"
    }

    fun resetToBuiltInModelButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة التعيين إلى محرك Spleeter 2-Stem المدمج"
        AppLanguage.SPANISH -> "Restablecer a Spleeter 2-Stem integrado"
        AppLanguage.FRENCH -> "Réinitialiser vers Spleeter 2-Stem intégré"
        AppLanguage.GERMAN -> "Auf integriertes Spleeter 2-Stem zurücksetzen"
        AppLanguage.JAPANESE -> "内蔵 Spleeter 2-Stem にリセット"
        AppLanguage.CHINESE -> "重置为内置 Spleeter 双音轨引擎"
        AppLanguage.RUSSIAN -> "Сброс к встроенному Spleeter 2-Stem"
        AppLanguage.PORTUGUESE -> "Restaurar para Spleeter 2-Stem integrado"
        else -> "Reset to Built-in Spleeter 2-Stem"
    }

    fun closeButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إغلاق"
        AppLanguage.SPANISH -> "Cerrar"
        AppLanguage.FRENCH -> "Fermer"
        AppLanguage.GERMAN -> "Schließen"
        AppLanguage.JAPANESE -> "閉じる"
        AppLanguage.CHINESE -> "关闭"
        AppLanguage.RUSSIAN -> "Закрыть"
        AppLanguage.PORTUGUESE -> "Fechar"
        else -> "Close"
    }

    // Processing Overlay
    fun extractingVocalsWithSpleeter(lang: AppLanguage, modelName: String): String {
        val cleanModel = if (modelName.contains("Spleeter", ignoreCase = true)) {
            modelName
        } else {
            "Spleeter ($modelName)"
        }
        return when (lang) {
            AppLanguage.ARABIC -> "جارٍ عزل الأصوات بمحرك $cleanModel…"
            AppLanguage.SPANISH -> "Extrayendo voces con $cleanModel…"
            AppLanguage.FRENCH -> "Extraction des voix avec $cleanModel…"
            AppLanguage.GERMAN -> "Gesang wird mit $cleanModel extrahiert…"
            AppLanguage.JAPANESE -> "$cleanModel でボーカルを抽出中…"
            AppLanguage.CHINESE -> "正在使用 $cleanModel 提取人声…"
            AppLanguage.RUSSIAN -> "Извлечение вокала с $cleanModel…"
            AppLanguage.PORTUGUESE -> "Extraindo vocais com $cleanModel…"
            else -> "Extracting vocals with $cleanModel…"
        }
    }

    fun progressLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التقدم"
        AppLanguage.SPANISH -> "Progreso"
        AppLanguage.FRENCH -> "Progression"
        AppLanguage.GERMAN -> "Fortschritt"
        AppLanguage.JAPANESE -> "進捗"
        AppLanguage.CHINESE -> "进度"
        AppLanguage.RUSSIAN -> "Прогресс"
        AppLanguage.PORTUGUESE -> "Progresso"
        else -> "Progress"
    }

    fun remainingTime(lang: AppLanguage, formattedTime: String) = when (lang) {
        AppLanguage.ARABIC -> "المتبقي: $formattedTime"
        AppLanguage.SPANISH -> "Restante: ~$formattedTime"
        AppLanguage.FRENCH -> "Restant : ~$formattedTime"
        AppLanguage.GERMAN -> "Verbleibend: ~$formattedTime"
        AppLanguage.JAPANESE -> "残り ~$formattedTime"
        AppLanguage.CHINESE -> "剩余 ~$formattedTime"
        AppLanguage.RUSSIAN -> "Осталось: ~$formattedTime"
        AppLanguage.PORTUGUESE -> "Restante: ~$formattedTime"
        else -> "~$formattedTime left"
    }

    fun finishingUp(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جاري الإنهاء…"
        AppLanguage.SPANISH -> "Finalizando…"
        AppLanguage.FRENCH -> "Finalisation…"
        AppLanguage.GERMAN -> "Wird abgeschlossen…"
        AppLanguage.JAPANESE -> "仕上げ中…"
        AppLanguage.CHINESE -> "正在完成…"
        AppLanguage.RUSSIAN -> "Завершение…"
        AppLanguage.PORTUGUESE -> "Finalizando…"
        else -> "Finishing up…"
    }

    fun runningAiSeparation(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جاري العزل بالذكاء الاصطناعي"
        AppLanguage.SPANISH -> "Ejecutando separación por IA"
        AppLanguage.FRENCH -> "Séparation IA en cours"
        AppLanguage.GERMAN -> "KI-Trennung läuft"
        AppLanguage.JAPANESE -> "AI音源分離を実行中"
        AppLanguage.CHINESE -> "正在进行 AI 音频分离"
        AppLanguage.RUSSIAN -> "Идет разделение ИИ"
        AppLanguage.PORTUGUESE -> "Executando separação por IA"
        else -> "Running AI separation"
    }

    fun elapsedLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الوقت المنقضي: "
        AppLanguage.SPANISH -> "Transcurrido: "
        AppLanguage.FRENCH -> "Écoulé : "
        AppLanguage.GERMAN -> "Vergangen: "
        AppLanguage.JAPANESE -> "経過時間: "
        AppLanguage.CHINESE -> "已用时间: "
        AppLanguage.RUSSIAN -> "Прошло: "
        AppLanguage.PORTUGUESE -> "Decorrido: "
        else -> "Elapsed: "
    }

    fun backgroundProtectedNotice(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "محمي في الخلفية • يمكنك التبديل بين التطبيقات بأمان"
        AppLanguage.SPANISH -> "Protegido en segundo plano • Seguro cambiar de app"
        AppLanguage.FRENCH -> "Protégé en arrière-plan • Sûr de changer d'application"
        AppLanguage.GERMAN -> "Im Hintergrund geschützt • App-Wechsel sicher möglich"
        AppLanguage.JAPANESE -> "バックグラウンド保護中 • アプリを切り替えても安全です"
        AppLanguage.CHINESE -> "后台受保护运行 • 可安全切换到其他应用"
        AppLanguage.RUSSIAN -> "Защищено в фоне • Можно безопасно свернуть приложение"
        AppLanguage.PORTUGUESE -> "Protegido em segundo plano • Seguro alternar de app"
        else -> "Protected in background • Safe to switch apps"
    }

    fun cancelExtraction(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إلغاء العزل"
        AppLanguage.SPANISH -> "Cancelar extracción"
        AppLanguage.FRENCH -> "Annuler l'extraction"
        AppLanguage.GERMAN -> "Extraktion abbrechen"
        AppLanguage.JAPANESE -> "抽出を中止"
        AppLanguage.CHINESE -> "取消提取"
        AppLanguage.RUSSIAN -> "Отменить извлечение"
        AppLanguage.PORTUGUESE -> "Cancelar extração"
        else -> "Cancel Extraction"
    }

    fun cancelTranscription(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إلغاء الترجمة"
        AppLanguage.SPANISH -> "Cancelar transcripción"
        AppLanguage.FRENCH -> "Annuler la transcription"
        AppLanguage.GERMAN -> "Transkription abbrechen"
        AppLanguage.JAPANESE -> "文字起こしを中止"
        AppLanguage.CHINESE -> "取消转录"
        AppLanguage.RUSSIAN -> "Отменить транскрипцию"
        AppLanguage.PORTUGUESE -> "Cancelar transcrição"
        else -> "Cancel Transcription"
    }

    fun arabicNameSupportBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "دعم كامل للغة العربية وتخطيط RTL وتصدير مقاطع الفيديو بأسماء عربية"
        AppLanguage.SPANISH -> "Compatibilidad total con RTL y exportación de video"
        AppLanguage.FRENCH -> "Prise en charge complète du RTL et de l'exportation vidéo"
        AppLanguage.GERMAN -> "Volle Unterstützung für RTL und Videoexport"
        AppLanguage.JAPANESE -> "RTLおよびビデオ書き出しの完全サポート"
        AppLanguage.CHINESE -> "完整支持 RTL 排版及多语言视频导出"
        AppLanguage.RUSSIAN -> "Полная поддержка RTL и экспорта видео"
        AppLanguage.PORTUGUESE -> "Suporte completo a RTL e exportação de vídeo"
        else -> "Full RTL layout and multi-language video export support"
    }

    fun modelLabel(lang: AppLanguage, modelName: String) = when (lang) {
        AppLanguage.ARABIC -> "النموذج: $modelName"
        AppLanguage.SPANISH -> "Modelo: $modelName"
        AppLanguage.FRENCH -> "Modèle : $modelName"
        AppLanguage.GERMAN -> "Modell: $modelName"
        AppLanguage.JAPANESE -> "モデル: $modelName"
        AppLanguage.CHINESE -> "模型: $modelName"
        AppLanguage.RUSSIAN -> "Модель: $modelName"
        AppLanguage.PORTUGUESE -> "Modelo: $modelName"
        else -> "Model: $modelName"
    }

    fun whisperSpeechRecognition(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "التعرف على الصوت (Whisper)"
        AppLanguage.SPANISH -> "Reconocimiento de voz (Whisper)"
        AppLanguage.FRENCH -> "Reconnaissance vocale (Whisper)"
        AppLanguage.GERMAN -> "Spracherkennung (Whisper)"
        AppLanguage.JAPANESE -> "音声認識 (Whisper)"
        AppLanguage.CHINESE -> "语音识别 (Whisper)"
        AppLanguage.RUSSIAN -> "Распознавание речи (Whisper)"
        AppLanguage.PORTUGUESE -> "Reconhecimento de voz (Whisper)"
        else -> "Speech Recognition (Whisper)"
    }

    fun vocalSeparationModels(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نماذج عزل الصوت"
        AppLanguage.SPANISH -> "Modelos de separación vocal"
        AppLanguage.FRENCH -> "Modèles de séparation vocale"
        AppLanguage.GERMAN -> "Vokalisationsmodelle"
        AppLanguage.JAPANESE -> "ボーカル分離モデル"
        AppLanguage.CHINESE -> "人声分离模型"
        AppLanguage.RUSSIAN -> "Модели разделения вокала"
        AppLanguage.PORTUGUESE -> "Modelos de separação vocal"
        else -> "Vocal Separation Models"
    }

    fun whisperModels(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نماذج Whisper"
        AppLanguage.SPANISH -> "Modelos de Whisper"
        AppLanguage.FRENCH -> "Modèles Whisper"
        AppLanguage.GERMAN -> "Whisper-Modelle"
        AppLanguage.JAPANESE -> "Whisperモデル"
        AppLanguage.CHINESE -> "Whisper 模型"
        AppLanguage.RUSSIAN -> "Модели Whisper"
        AppLanguage.PORTUGUESE -> "Modelos Whisper"
        else -> "Whisper Models"
    }

    fun transcribeVocalsToSubtitles(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تحويل الصوت المعزول إلى ترجمة نصية"
        AppLanguage.SPANISH -> "Transcribir voces a subtítulos"
        AppLanguage.FRENCH -> "Transcrire la voix en sous-titres"
        AppLanguage.GERMAN -> "Gesang in Untertitel transkribieren"
        AppLanguage.JAPANESE -> "ボーカルを字幕に書き起こす"
        AppLanguage.CHINESE -> "将人声转录为字幕"
        AppLanguage.RUSSIAN -> "Транскрибировать вокал в субтитры"
        AppLanguage.PORTUGUESE -> "Transcrever vocais para legendas"
        else -> "Transcribe vocals to subtitles"
    }

    fun whisperModelNotInstalled(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نموذج Whisper غير مثبت."
        AppLanguage.SPANISH -> "Modelo Whisper no instalado."
        AppLanguage.FRENCH -> "Modèle Whisper non installé."
        AppLanguage.GERMAN -> "Whisper-Modell nicht installiert."
        AppLanguage.JAPANESE -> "Whisperモデルがインストールされていません。"
        AppLanguage.CHINESE -> "未安装 Whisper 模型。"
        AppLanguage.RUSSIAN -> "Модель Whisper не установлена."
        AppLanguage.PORTUGUESE -> "Modelo Whisper não instalado."
        else -> "Whisper model not installed."
    }

    fun downloadWhisperModel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تنزيل نموذج Whisper"
        AppLanguage.SPANISH -> "Descargar modelo Whisper"
        AppLanguage.FRENCH -> "Télécharger le modèle Whisper"
        AppLanguage.GERMAN -> "Whisper-Modell herunterladen"
        AppLanguage.JAPANESE -> "Whisperモデルをダウンロード"
        AppLanguage.CHINESE -> "下载 Whisper 模型"
        AppLanguage.RUSSIAN -> "Скачать модель Whisper"
        AppLanguage.PORTUGUESE -> "Baixar modelo Whisper"
        else -> "Download Whisper Model"
    }

    fun generateSubtitles(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "توليد الترجمة (Whisper)"
        AppLanguage.SPANISH -> "Generar subtítulos (Whisper)"
        AppLanguage.FRENCH -> "Générer les sous-titres (Whisper)"
        AppLanguage.GERMAN -> "Untertitel generieren (Whisper)"
        AppLanguage.JAPANESE -> "字幕を生成 (Whisper)"
        AppLanguage.CHINESE -> "生成字幕 (Whisper)"
        AppLanguage.RUSSIAN -> "Сгенерировать субтитры (Whisper)"
        AppLanguage.PORTUGUESE -> "Gerar legendas (Whisper)"
        else -> "Generate Subtitles (Whisper)"
    }

    fun subtitlesLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "الترجمة"
        AppLanguage.SPANISH -> "Subtítulos"
        AppLanguage.FRENCH -> "Sous-titres"
        AppLanguage.GERMAN -> "Untertitel"
        AppLanguage.JAPANESE -> "字幕"
        AppLanguage.CHINESE -> "字幕"
        AppLanguage.RUSSIAN -> "Субтитры"
        AppLanguage.PORTUGUESE -> "Legendas"
        else -> "Subtitles"
    }

    fun exportSrtButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير ملف SRT"
        AppLanguage.SPANISH -> "Exportar archivo SRT"
        AppLanguage.FRENCH -> "Exporter le fichier SRT"
        AppLanguage.GERMAN -> "SRT-Datei exportieren"
        AppLanguage.JAPANESE -> "SRTファイルを書き出す"
        AppLanguage.CHINESE -> "导出 SRT 字幕文件"
        AppLanguage.RUSSIAN -> "Экспортировать файл SRT"
        AppLanguage.PORTUGUESE -> "Exportar arquivo SRT"
        else -> "Export SRT Subtitles"
    }

    fun transcriptionLanguage(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "لغة الترجمة"
        AppLanguage.SPANISH -> "Idioma de transcripción"
        AppLanguage.FRENCH -> "Langue de transcription"
        AppLanguage.GERMAN -> "Transkriptionssprache"
        AppLanguage.JAPANESE -> "文字起こし言語"
        AppLanguage.CHINESE -> "转录语言"
        AppLanguage.RUSSIAN -> "Язык транскрипции"
        AppLanguage.PORTUGUESE -> "Idioma de transcrição"
        else -> "Transcription Language"
    }

    fun installedBadge(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مثبت"
        AppLanguage.SPANISH -> "Instalado"
        AppLanguage.FRENCH -> "Installé"
        AppLanguage.GERMAN -> "Installiert"
        AppLanguage.JAPANESE -> "インストール済み"
        AppLanguage.CHINESE -> "已安装"
        AppLanguage.RUSSIAN -> "Установлено"
        AppLanguage.PORTUGUESE -> "Instalado"
        else -> "Installed"
    }

    fun deleteModelButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "حذف النموذج"
        AppLanguage.SPANISH -> "Eliminar modelo"
        AppLanguage.FRENCH -> "Supprimer le modèle"
        AppLanguage.GERMAN -> "Modell löschen"
        AppLanguage.JAPANESE -> "モデルを削除"
        AppLanguage.CHINESE -> "删除模型"
        AppLanguage.RUSSIAN -> "Удалить модель"
        AppLanguage.PORTUGUESE -> "Excluir modelo"
        else -> "Delete Model"
    }

    fun viewTranscript(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "عرض النص الكامل"
        AppLanguage.SPANISH -> "Ver transcripción completa"
        AppLanguage.FRENCH -> "Voir la transcription complète"
        AppLanguage.GERMAN -> "Vollständige Transkription anzeigen"
        AppLanguage.JAPANESE -> "全体の文字起こしを表示"
        AppLanguage.CHINESE -> "查看完整文字记录"
        AppLanguage.RUSSIAN -> "Просмотреть полный текст"
        AppLanguage.PORTUGUESE -> "Ver transcrição completa"
        else -> "View Full Transcript"
    }

    fun retryTranscription(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "إعادة محاولة الترجمة"
        AppLanguage.SPANISH -> "Reintentar transcripción"
        AppLanguage.FRENCH -> "Réessayer la transcription"
        AppLanguage.GERMAN -> "Transkription wiederholen"
        AppLanguage.JAPANESE -> "文字起こしを再試行"
        AppLanguage.CHINESE -> "重试转录"
        AppLanguage.RUSSIAN -> "Повторить транскрипцию"
        AppLanguage.PORTUGUESE -> "Tentar transcrição novamente"
        else -> "Retry Transcription"
    }

    fun noSubtitlesGenerated(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "لم يتم إنشاء ترجمة بعد"
        AppLanguage.SPANISH -> "Aún no se han generado subtítulos"
        AppLanguage.FRENCH -> "Aucun sous-titre généré pour le moment"
        AppLanguage.GERMAN -> "Noch keine Untertitel generiert"
        AppLanguage.JAPANESE -> "字幕はまだ生成されていません"
        AppLanguage.CHINESE -> "尚未生成字幕"
        AppLanguage.RUSSIAN -> "Субтитры еще не созданы"
        AppLanguage.PORTUGUESE -> "Nenhuma legenda gerada ainda"
        else -> "No subtitles generated yet"
    }

    fun modelsBackupAndExport(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "النسخ الاحتياطي وتصدير النماذج"
        AppLanguage.SPANISH -> "Copia de seguridad y exportación de modelos"
        AppLanguage.FRENCH -> "Sauvegarde et exportation des modèles"
        AppLanguage.GERMAN -> "Modellsicherung & Export"
        AppLanguage.JAPANESE -> "モデルのバックアップと書き出し"
        AppLanguage.CHINESE -> "模型备份与导出"
        AppLanguage.RUSSIAN -> "Резервное копирование и экспорт моделей"
        AppLanguage.PORTUGUESE -> "Backup e exportação de modelos"
        else -> "Models Backup & Export"
    }

    fun backupAllModelsButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "نسخ احتياطي لكافة النماذج (ZIP)"
        AppLanguage.SPANISH -> "Copia de seguridad completa (ZIP)"
        AppLanguage.FRENCH -> "Sauvegarder tous les modèles (ZIP)"
        AppLanguage.GERMAN -> "Alle Modelle sichern (ZIP)"
        AppLanguage.JAPANESE -> "全モデルを一括バックアップ (ZIP)"
        AppLanguage.CHINESE -> "备份所有模型 (ZIP)"
        AppLanguage.RUSSIAN -> "Создать резервную копию всех моделей (ZIP)"
        AppLanguage.PORTUGUESE -> "Fazer backup de todos os modelos (ZIP)"
        else -> "Backup All Models (ZIP)"
    }

    fun restoreModelsBackupButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "استعادة النماذج من نسخة احتياطية"
        AppLanguage.SPANISH -> "Restaurar modelos desde copia"
        AppLanguage.FRENCH -> "Restaurer les modèles"
        AppLanguage.GERMAN -> "Modelle wiederherstellen"
        AppLanguage.JAPANESE -> "バックアップから復元"
        AppLanguage.CHINESE -> "从备份恢复模型"
        AppLanguage.RUSSIAN -> "Восстановить модели из копии"
        AppLanguage.PORTUGUESE -> "Restaurar modelos do backup"
        else -> "Restore Models from Backup"
    }

    fun exportModelButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "تصدير النموذج"
        AppLanguage.SPANISH -> "Exportar modelo"
        AppLanguage.FRENCH -> "Exporter le modèle"
        AppLanguage.GERMAN -> "Modell exportieren"
        AppLanguage.JAPANESE -> "モデルを書き出す"
        AppLanguage.CHINESE -> "导出模型"
        AppLanguage.RUSSIAN -> "Экспортировать модель"
        AppLanguage.PORTUGUESE -> "Exportar modelo"
        else -> "Export Model"
    }

    fun shareModelButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مشاركة ملف النموذج"
        AppLanguage.SPANISH -> "Compartir archivo de modelo"
        AppLanguage.FRENCH -> "Partager le modèle"
        AppLanguage.GERMAN -> "Modell teilen"
        AppLanguage.JAPANESE -> "モデルファイルを共有"
        AppLanguage.CHINESE -> "分享模型文件"
        AppLanguage.RUSSIAN -> "Поделиться файлом модели"
        AppLanguage.PORTUGUESE -> "Compartilhar modelo"
        else -> "Share Model File"
    }

    fun shareBackupButton(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "مشاركة النسخة الاحتياطية"
        AppLanguage.SPANISH -> "Compartir copia de seguridad"
        AppLanguage.FRENCH -> "Partager la sauvegarde"
        AppLanguage.GERMAN -> "Sicherung teilen"
        AppLanguage.JAPANESE -> "バックアップを共有"
        AppLanguage.CHINESE -> "分享备份压缩包"
        AppLanguage.RUSSIAN -> "Поделиться архивом резервной копии"
        AppLanguage.PORTUGUESE -> "Compartilhar backup"
        else -> "Share Backup Archive"
    }

    fun installedModelsHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "النماذج المثبتة"
        AppLanguage.SPANISH -> "Modelos instalados"
        AppLanguage.FRENCH -> "Modèles installés"
        AppLanguage.GERMAN -> "Installierte Modelle"
        AppLanguage.JAPANESE -> "インストール済みモデル"
        AppLanguage.CHINESE -> "已安装模型"
        AppLanguage.RUSSIAN -> "Установленные модели"
        AppLanguage.PORTUGUESE -> "Modelos instalados"
        else -> "Installed Models"
    }

    fun backupExportSummary(count: Int, sizeMb: Long, lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "$count نماذج متوفرة محلياً ($sizeMb ميغابايت)"
        AppLanguage.SPANISH -> "$count modelos disponibles localmente ($sizeMb MB)"
        AppLanguage.FRENCH -> "$count modèle(s) disponible(s) localement ($sizeMb Mo)"
        AppLanguage.GERMAN -> "$count Modelle lokal verfügbar ($sizeMb MB)"
        AppLanguage.JAPANESE -> "利用可能なモデル: $count 件 ($sizeMb MB)"
        AppLanguage.CHINESE -> "本地可用模型: $count 个 ($sizeMb MB)"
        AppLanguage.RUSSIAN -> "Доступно локально моделей: $count ($sizeMb МБ)"
        AppLanguage.PORTUGUESE -> "$count modelos disponíveis localmente ($sizeMb MB)"
        else -> "$count model(s) available locally ($sizeMb MB)"
    }

    fun backupExportInProgress(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "جارٍ تجهيز النسخ الاحتياطي / التصدير…"
        AppLanguage.SPANISH -> "Copia / Exportación en curso…"
        AppLanguage.FRENCH -> "Sauvegarde / Exportation en cours…"
        AppLanguage.GERMAN -> "Sicherung / Export läuft…"
        AppLanguage.JAPANESE -> "バックアップ／エクスポート処理中…"
        AppLanguage.CHINESE -> "备份/导出处理中…"
        AppLanguage.RUSSIAN -> "Выполняется резервное копирование / экспорт…"
        AppLanguage.PORTUGUESE -> "Backup / Exportação em andamento…"
        else -> "Backup / Export in progress…"
    }

    fun noInstalledModels(lang: AppLanguage) = when (lang) {
        AppLanguage.ARABIC -> "لا توجد نماذج مثبتة حالياً للتصدير"
        AppLanguage.SPANISH -> "No hay modelos instalados para exportar"
        AppLanguage.FRENCH -> "Aucun modèle installé à exporter"
        AppLanguage.GERMAN -> "Keine installierten Modelle zum Exportieren vorhanden"
        AppLanguage.JAPANESE -> "エクスポート可能なモデルがありません"
        AppLanguage.CHINESE -> "暂无已安装模型可导出"
        AppLanguage.RUSSIAN -> "Нет установленных моделей для экспорта"
        AppLanguage.PORTUGUESE -> "Nenhum modelo instalado para exportação"
        else -> "No installed models available to export"
    }
}
