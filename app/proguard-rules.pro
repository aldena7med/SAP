# Separate App Sound ProGuard rules
-keep class com.separateappsound.model.** { *; }
-keep class com.separateappsound.service.** { *; }
-keep class com.separateappsound.tile.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
