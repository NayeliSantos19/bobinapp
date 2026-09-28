# kotlinx.serialization: conservar serializadores generados de los DTO
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.bobinapp.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.bobinapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit usa reflexión sobre la interfaz de la API
-keep,allowobfuscation interface com.bobinapp.data.remote.BobinappApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
