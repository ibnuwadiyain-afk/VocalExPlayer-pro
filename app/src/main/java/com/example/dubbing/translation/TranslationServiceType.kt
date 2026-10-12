package com.example.dubbing.translation

enum class TranslationServiceType(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    ONLINE_FREE(
        title = "Free Online Translation",
        subtitle = "Instant neural translation across 100+ languages (zero setup required)",
        badge = "Fast & Universal"
    ),
    ANDROID_SYSTEM(
        title = "Built-in Android System Service",
        subtitle = "On-device system translation via Android TranslationManager (Android 12+)",
        badge = "On-Device"
    ),
    NONE(
        title = "No Translation",
        subtitle = "Synthesize original subtitle wording without language change",
        badge = "Original"
    )
}
