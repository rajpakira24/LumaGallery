# LumaGallery ProGuard rules

# Preserve line numbers for stack traces, hide original source file name
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin ---
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlin.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Jetpack Compose ---
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }
-dontwarn androidx.compose.**

# --- Navigation Compose ---
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# --- Lifecycle / ViewModel ---
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# --- App data classes (Photo, FolderGroup, DateGroup, RecycleBinItem, sealed result types) ---
-keep class com.webstudio.lumagallery.data.** { *; }
-keepclassmembers class com.webstudio.lumagallery.data.** {
    <init>(...);
}

# --- Coil (image loader + video frame decoder) ---
-keep class coil.** { *; }
-keep interface coil.** { *; }
-keep class coil.decode.VideoFrameDecoder { *; }
-dontwarn coil.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Telephoto (zoomable image) ---
-keep class me.saket.telephoto.** { *; }
-dontwarn me.saket.telephoto.**

# --- Lottie ---
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# --- Accompanist ---
-keep class com.google.accompanist.** { *; }
-dontwarn com.google.accompanist.**

# --- IronSource / Unity LevelPlay ---
-keep class com.ironsource.** { *; }
-keep interface com.ironsource.** { *; }
-keep class com.unity3d.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.ironsource.**
-dontwarn com.unity3d.**
-dontwarn com.google.android.gms.**

# --- ML Kit (Subject Segmentation) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.mlkit.**

# --- Retrofit / OkHttp ---
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.webstudio.lumagallery.**$$serializer { *; }
-keepclassmembers class com.webstudio.lumagallery.** {
    *** Companion;
}
-keepclasseswithmembers class com.webstudio.lumagallery.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- uCrop ---
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# Strip verbose log calls from release builds (paths and queries must not leak)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# --- Generic Android ---
-keepattributes *Annotation*,Signature,Exception,InnerClasses,EnclosingMethod
-keep public class * extends android.app.Activity
-keep public class * extends androidx.activity.ComponentActivity
