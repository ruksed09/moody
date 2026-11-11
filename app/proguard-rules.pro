# libVLC uses JNI callbacks into these classes; keep them intact.
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# jaudiotagger builds tag frames by class name, and has desktop-only image code Moody never calls.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.moody.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
