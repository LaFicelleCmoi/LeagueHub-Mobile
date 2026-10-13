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
