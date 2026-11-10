# ---------------------------------------------------------------------------
# SPEC §15.4 / §16: R8 on the release build is the known failure point. These
# three - kotlinx.serialization, Room and Retrofit models - are exactly what it
# tends to break, so the release APK is built and run from phase 1 onward.
# ---------------------------------------------------------------------------

# --- kotlinx.serialization ---------------------------------------------------
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **.*$$serializer {
    *** descriptor;
}
-dontnote kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Navigation Compose builds type-safe routes out of @Serializable objects.
-keep,includedescriptorclasses class com.zeroindex.intake.ui.navigation.Route { *; }
-keep,includedescriptorclasses class com.zeroindex.intake.ui.navigation.Route$* { *; }

# Our own serializable payloads (export/import, AI responses).
-keep,includedescriptorclasses class com.zeroindex.intake.data.remote.** { *; }
-keep,includedescriptorclasses class com.zeroindex.intake.data.backup.** { *; }

# --- Room --------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keep class androidx.room.paging.** { *; }
-dontwarn androidx.room.paging.**
-keep class **_Impl { <init>(...); }

# --- Retrofit + OkHttp -------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Hilt / WorkManager ------------------------------------------------------
-dontwarn dagger.hilt.**
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# --- Health Connect is optional at runtime ----------------------------------
-dontwarn androidx.health.connect.**

# --- Keep our own line numbers readable in a crash ---------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- ML Kit bundled barcode scanning (barcode fix) ----------------------------
# The decoder is native code (libbarhopper_v3.so) that reads Java option fields and
# creates response objects by name, and the scanner is reached through generated
# dispatch classes. ML Kit's own consumer rules keep only part of that. Keeping the
# whole barcode path costs a little size and removes R8 as a way for release builds to
# differ from debug.
-keep class com.google.android.libraries.barhopper.** { *; }
-keep class com.google.photos.vision.barhopper.** { *; }
-keep class com.google.barhopper.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-dontwarn com.google.mlkit.vision.barcode.**

# --- Reference food database (Room, pre-packaged asset) ----------------------
-keep class com.zeroindex.intake.data.foodref.RefFoodEntity { *; }
