# kotlinx.serialization : garde les sérialiseurs générés des DTO ESPN.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.leaguehub.android.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.leaguehub.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit : les interfaces de service sont lues par réflexion.
-keep,allowobfuscation interface com.leaguehub.android.data.espn.EspnApi
-keepattributes Signature, Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Glance repère chaque widget par le nom de sa classe : R8 ne doit ni les renommer ni les fusionner,
# sinon « Mon club » et « Matchs du jour » partagent une classe et Glance dessine l'un dans la case de l'autre.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
