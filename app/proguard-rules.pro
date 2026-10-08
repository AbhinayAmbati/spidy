# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.example.agent.**$$serializer { *; }
-keepclassmembers class com.example.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# --- androidx.security / Tink ------------------------------------------------
# Tink references Error Prone (and similar) annotations that are compile-time
# only and absent from the runtime classpath. R8 in full mode treats those
# unresolved references as errors; the annotations are never needed on device.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.j2objc.annotations.**
