# Project-specific ProGuard / R8 rules
# Line number & stack trace preservation
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Keep native methods and JNI bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# ONNX Runtime (Native JNI libraries & session bindings)
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# AndroidX Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Moshi & JSON Data Models
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.example.model.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.media.** { *; }

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 ExoPlayer (reflection-based decoders, extractors, renderers)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
