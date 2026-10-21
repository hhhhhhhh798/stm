# StreamMaster — R8 / Retrofit / Gson release rules
#
# Goal:
# - Keep Retrofit generic signatures intact
# - Keep Gson DTO fields/classes available for reflection.
# - Keep JNI entry points stable.
# - Keep Media3/J2V8 integration conservative.
#
# IMPORTANT:
# R8/ProGuard is obfuscation + shrinking + optimization, NOT encryption.
# Do not treat a client-side secret as unextractable.


# ============================================================
# 1) Required class-file metadata
# ============================================================

-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes MethodParameters
-keepattributes *Annotation*


# ============================================================
# 2) Retrofit — CRITICAL
# ============================================================
#
# Retrofit reads generic return types through reflection.
#
# Keep the API interface and its method signatures intact.
# This is especially important for:
#
# Call<TokenResponse>
# Call<SubtitleSearchResponse>
# Call<StreamResponse>
#
# etc.

-keep interface com.stream.master.ApiService { *; }

-keepclassmembers interface com.stream.master.ApiService {
    @retrofit2.http.* <methods>;
}

-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification interface retrofit2.Call
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification interface retrofit2.CallAdapter
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification interface retrofit2.Converter

-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification class retrofit2.Response
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification class retrofit2.Response$*
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification class retrofit2.OkHttpCall
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification class retrofit2.DefaultCallAdapterFactory
-keep,allowoptimization,allowshrinking,allowobfuscation,allowaccessmodification class retrofit2.BuiltInConverters

# Keep Retrofit annotations available at runtime.
-keep @interface retrofit2.http.* { *; }


# ============================================================
# 3) Gson / API DTOs
# ============================================================
#
# These classes are populated through Gson reflection.
# Keep them to avoid release-only deserialization failures.

-keep class com.stream.master.HomeResponse { *; }

-keep class com.stream.master.MovieResponse { *; }

-keep class com.stream.master.Movie { *; }
-keep class com.stream.master.Cast { *; }
-keep class com.stream.master.Review { *; }
-keep class com.stream.master.DetailResponse { *; }
-keep class com.stream.master.DetailResponse$* { *; }

-keep class com.stream.master.StreamResponse { *; }
-keep class com.stream.master.StreamResponse$* { *; }

-keep class com.stream.master.TokenResponse { *; }

-keep class com.stream.master.UpdateInfo { *; }
-keepclassmembers class com.stream.master.UpdateInfo { *; }
-keep class com.stream.master.**Update** { *; }


# ============================================================
# 3.1) SubDL subtitle response
# ============================================================
#
# Added for the SubDL subtitle API integration.
#
# SubtitleSearchResponse is deserialized by Gson and therefore
# must remain available in release builds.
#
# The $* rule also preserves nested subtitle model classes.

-keep class com.stream.master.SubtitleSearchResponse { *; }
-keep class com.stream.master.SubtitleSearchResponse$* { *; }


# ============================================================
# 3.2) Series (TV shows) - list + detail + seasons/episodes
# ============================================================
#
# GET api/series (list)   -> SeriesResponse { List<Series> }
# GET api/series?vid=     -> SeriesDetailResponse { List<Season>, List<Episode> }
# Same reflection risk as every other Gson DTO above: keep the
# classes explicitly instead of relying only on the generic
# @SerializedName field-keeper further down, matching how every
# other response model in this file is treated.

-keep class com.stream.master.Series { *; }
-keep class com.stream.master.SeriesResponse { *; }
-keep class com.stream.master.SeriesDetailResponse { *; }
-keep class com.stream.master.SeriesDetailResponse$* { *; }


# Keep any field explicitly mapped by Gson.
-keepclassmembers,allowoptimization,allowobfuscation class com.stream.master.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson's annotations are runtime metadata.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations


# ============================================================
# 4) JNI / native secure-lib
# ============================================================
#
# Native symbols are resolved against this exact Java/Kotlin
# class and its native method names.
#
# Do NOT rename or remove this class/methods.

-keep class com.stream.master.SecureNetworkClient { *; }

# Explicitly retain all native methods in case the native bridge
# is resolved reflectively on a device.

-keepclassmembers class com.stream.master.SecureNetworkClient {
    native <methods>;
}


# ============================================================
# 5) Secure storage
# ============================================================

-keep class com.stream.master.SecureStorageManager { *; }


# ============================================================
# 6) App components referenced by AndroidManifest / reflection
# ============================================================

-keep public class com.stream.master.SplashActivity { *; }

-keep public class com.stream.master.MainActivity { *; }

-keep public class com.stream.master.PlayerActivity { *; }

-keep public class com.stream.master.FavoritesActivity { *; }

-keep public class com.stream.master.DownloadsActivity { *; }

-keep public class com.stream.master.CategoryActivity { *; }

-keep public class com.stream.master.DetailsActivity { *; }

-keep public class com.stream.master.SearchActivity { *; }

-keep public class com.stream.master.SettingsActivity { *; }

-keep public class com.stream.master.SeriesDetailsActivity { *; }


# ============================================================
# 7) Enums used by serializers/reflection
# ============================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ============================================================
# 8) Media3 / ExoPlayer
# ============================================================
#
# Media3 ships its own consumer rules.
# Keep only metadata required by reflective integrations.

-keepattributes EnclosingMethod,InnerClasses


# ============================================================
# 9) J2V8 / native JavaScript bridge
# ============================================================
#
# J2V8 contains native integration.
# Keep the bridge conservative because it is used by the
# application to execute Node.js/JavaScript functionality.

-keep class com.eclipsesource.v8.** { *; }


# ============================================================
# 10) Optional crypto providers
# ============================================================

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**


# ============================================================
# 11) SecurityGuard
# ============================================================
#
# SecurityGuard is called by name from the application entry point.

-keep class com.stream.master.SecurityGuard { public *; }


# ============================================================
# IMPORTANT — DO NOT ADD
# ============================================================
#
# Do NOT add:
#
# -dontobfuscate
# -dontoptimize
# -dontshrink
#
# Do NOT add:
#
# -keep class com.stream.master.** { *; }
#
# Do NOT add:
#
# -repackageclasses
#
# These rules either weaken release hardening or can interfere
# with reflection/JNI and unnecessarily increase APK size.
# Remote configuration models / manager
-keep class com.stream.master.RemoteConfigResponse { *; }
-keep class com.stream.master.RemoteConfigManager { *; }


# ============================================================
# 12) Unity LevelPlay / mediation SDK
# ============================================================
#
# Required by Unity's LevelPlay Android integration when R8/ProGuard
# is enabled. Keep mediation reflection, WebView JS bridges, and
# Parcelable creators intact.

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class com.ironsource.sdk.controller.IronSourceWebView$JSInterface {
    public *;
}

-keep public class com.google.android.gms.ads.** {
    public *;
}

-keep class com.google.android.gms.appset.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

-keep class com.ironsource.adapters.** { *; }
-keep class com.ironsource.unity.androidbridge.** { *; }
-keep public class com.ironsource.** { *; }
-keepclassmembers class com.ironsource.** {
    public *;
}

-dontwarn com.ironsource.**
-dontwarn com.ironsource.adapters.**

-keep class com.iab.omid.** { *; }

# Unity Ads / Unity Services WebView and runtime bridges.
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.services.**
-dontwarn com.google.ar.core.**
-dontwarn com.ironsource.adapters.unityads.**

-keep class android.webkit.JavascriptInterface { *; }
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Retain source/line metadata for ad SDK diagnostics.
-keepattributes SourceFile,LineNumberTable

# ---- Release: strip all android.util.Log calls (verbose/debug/info/warn/error) ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}


# ============================================================
# 13) WebRTC (Watch Party voice chat) — JNI-critical
# ============================================================
#
# libwebrtc's native side binds to org.webrtc.* Java classes/methods via
# explicit RegisterNatives calls at init time (NOT the Java_pkg_Class_method
# naming convention), which means the exact class and method names must
# survive R8, or every native->Java callback breaks at runtime with an
# UnsatisfiedLinkError / NoSuchMethodError — this would surface as a crash
# the moment voice chat starts (first PeerConnection created), not at build
# time, so it's easy to miss in testing that never exercises the mic.
#
# "allowshrinking" (no allowobfuscation, no allowoptimization) is
# deliberate: it still lets R8 delete the video/camera classes this app
# never references (we only use the audio path), which is most of this
# library's size — it only forbids renaming/altering whatever ends up
# actually reachable. Same allow-pattern already used above for Retrofit.

-keep,allowshrinking class org.webrtc.** { *; }
-keepclassmembers,allowshrinking class org.webrtc.** {
    native <methods>;
}
-dontwarn org.webrtc.**


# ============================================================
# 14) shrinkResources safety net — dynamic resource lookup
# ============================================================
#
# ShelfAdapter.getLocalizedCategoryTitle() resolves R.string.continue_watching
# via Resources.getIdentifier("continue_watching", "string", ...) instead of
# a compile-time R.string reference. AAPT2's resource shrinker has a
# best-effort heuristic for literal-string getIdentifier() calls like this
# one, but it's not a hard guarantee across AGP versions — an explicit
# tools:keep in res/raw/keep.xml removes the ambiguity so this string can
# never be stripped as "unused" and return a silent blank label in release.
