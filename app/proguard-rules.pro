# R8 / ProGuard rules for release builds (isMinifyEnabled + isShrinkResources).
#
# Principle: libraries that ship their own consumer rules (Room, Hilt/Dagger, AndroidX, OkHttp,
# kotlinx.serialization, Tink) are trusted for those; the rules below cover what they do not
# (JNI, our own serialised types) plus a few cheap safety nets, each with its reason.
# Manifest components (Activity, Service, Application, ...) are kept by AGP-generated rules.

# --- Attributes ----------------------------------------------------------------------------
# Annotations, generics signatures and inner-class info are read at runtime by
# kotlinx.serialization, Room and Hilt; removing them breaks (de)serialisation, not the build.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# Readable crash traces: keep file names and line numbers, hide the real file name. The release
# workflow uploads mapping.txt so these can be de-obfuscated.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- JNI: the on-device engine --------------------------------------------------------------
# The native library binds these methods by symbol name, Java_com_example_agent_core_ai_local_
# SpideyNative_nativeXxx. If R8 renamed the class or a method, System.loadLibrary would succeed
# but the first call would throw UnsatisfiedLinkError - and only in release builds. The class
# is also kept whole because its Kotlin `object` generates INSTANCE / lazy fields that the
# external functions sit beside; keeping everything is a few bytes and removes any doubt.
# The C++ side calls back into no Java class (no FindClass/GetMethodID), so nothing else is needed.
-keep class com.example.agent.core.ai.local.SpideyNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- kotlinx.serialization -----------------------------------------------------------------
# The library's consumer rules keep Companion.serializer() for classes that carry
# @Serializable; these are the app-scoped belt and braces for the generated $$serializer
# classes and the Companion lookup that polymorphic / reflective serializer(...) uses.
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

# --- Room ----------------------------------------------------------------------------------
# Room instantiates the generated <Database>_Impl by name via reflection, and reads entity
# and DAO structure through annotations. Obfuscating the database class name breaks that
# lookup. Room's own consumer rules also do this; stated here so it survives a Room upgrade.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger -------------------------------------------------------------------------
# Hilt generates its component wiring at compile time and ships consumer rules. The entry
# point annotations are matched at runtime by the generated Hilt_* base classes, so their
# annotated classes keep their names.
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.InstallIn class *
-dontwarn dagger.hilt.**

# --- androidx.security (EncryptedSharedPreferences / MasterKey) / Tink ---------------------
# Tink stores its keysets as protobuf-lite messages whose fields are located by reflection;
# if R8 strips or renames them, decrypting stored credentials fails at runtime in release
# builds only. This is the widely used rule for the shaded protobuf bundled in Tink.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
# Tink references compile-time-only annotations (Error Prone, javax.annotation, j2objc) that are
# absent on device. R8 full mode treats unresolved references as errors; they are never needed.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.j2objc.annotations.**

# --- OkHttp / Okio -------------------------------------------------------------------------
# OkHttp ships consumer rules. These silence warnings for its optional TLS providers
# (Conscrypt, BouncyCastle, OpenJSSE), which are not on the classpath.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Logging -------------------------------------------------------------------------------
# Debug and verbose logging is removed from release builds, so diagnostic detail never reaches
# logcat on a user's device. Log.i/w/e are kept for real problems. Safe only because no
# Log.d/v call has side effects in its arguments.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
