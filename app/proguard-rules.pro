# libVLC uses JNI callbacks into these classes; keep them intact.
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.moody.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
