# Règles ProGuard/R8 du projet FloraPin (NODE-119).
# Minification + shrinking activés en release : on conserve ce que les
# bibliothèques à réflexion (Retrofit, Moshi, Room, MapLibre, Firebase)
# et nos DTO sérialisés exigent.

# --- Attributs génériques nécessaires à la réflexion / aux génériques ---
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# --- Retrofit ---
# Les interfaces d'API sont appelées par réflexion via leurs annotations HTTP.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclasseswithmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# --- OkHttp / Okio (rules consumer fournies, on neutralise juste les warnings) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Moshi (codegen) ---
# Les adaptateurs générés (<Dto>JsonAdapter) sont résolus par nom ; on garde
# donc les classes annotées @JsonClass et leurs adaptateurs (package dto).
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keep class com.florapin.app.network.dto.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-dontwarn com.squareup.moshi.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- MapLibre GL ---
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# --- Firebase Cloud Messaging ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Crashlytics (NODE-148) ---
# Conserver fichier source + numéros de ligne pour désobfusquer les crashs.
# Le mapping R8 est uploadé par le plugin ; ces attributs rendent les
# stacktraces lisibles dans la console Firebase.
-keepattributes SourceFile, LineNumberTable
# Ne pas renommer les exceptions custom (lisibilité des rapports).
-keep public class * extends java.lang.Exception

# --- Modèles applicatifs sérialisés / persistés (réflexion Moshi + Room) ---
-keep class com.florapin.app.data.** { *; }
