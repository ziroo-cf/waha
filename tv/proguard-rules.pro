# R8 rules for the TV release build.
#
# The TV build is the one that ships to 1GB set-top boxes, so it is the build that
# runs shrunk (see `optimization { enable = true }` in build.gradle.kts). Shrinking
# is also where the risky parts of this app live: a stream extractor that talks to
# YouTube's JS, a serialized Supabase client, and Room's generated code. The rules
# below keep exactly those seams and nothing else — no blanket `-keep class **`.

# --- Reflection-visible members ------------------------------------------------
# kotlinx.serialization, Room and Supabase all look members up by name; without
# these attributes R8 strips the annotations they read.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

# --- kotlinx.serialization -----------------------------------------------------
# Serializers for this app's own models. `@Serializable` classes get a generated
# `$serializer` and a `Companion.serializer()`, both resolved reflectively by
# `serializer<T>()` and by Supabase's `Postgrest` body encoding.
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class com.waha.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.waha.** {
    *** Companion;
}
# The runtime's built-in serializers (String, List, Map…) are looked up by name.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- NewPipeExtractor ----------------------------------------------------------
# The extractor resolves YouTube's ciphers through Rhino, and Rhino instantiates
# interpreter classes reflectively (`ContextFactory`, the class-shutter tables),
# so its package has to survive intact or stream resolution fails at runtime with
# a NoClassDefFoundError rather than a compile error.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
# NewPipe's services and info classes are reached by name from `ServiceList` and
# from the JSON layer (nanojson maps fields reflectively onto some models).
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
# jsoup ships optional integrations the extractor never uses.
-dontwarn org.jsoup.**

# --- Supabase / Ktor -----------------------------------------------------------
# Ktor's Android engine and the Supabase plugins log through SLF4J, which is not on
# the classpath in an app build; the warnings are noise, the classes are optional.
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
# Ktor picks engines and content converters by name.
-keep class io.ktor.client.engine.android.** { *; }
-keep class io.github.jan.supabase.** { *; }

# --- Room ----------------------------------------------------------------------
# Room's generated `_Impl` classes are loaded by name from the database builder.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Media3 / Compose / Coil ---------------------------------------------------
# These ship their own consumer rules; these two are the parts those rules assume
# the app keeps: the player's renderer classes are named in Media3's extension
# loader, and Coil resolves its decoders through ServiceLoader.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class * implements coil.decode.Decoder { *; }
