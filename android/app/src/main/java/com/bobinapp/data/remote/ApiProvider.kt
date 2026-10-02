package com.bobinapp.data.remote

import com.bobinapp.BuildConfig
import com.bobinapp.data.repository.AjustesStore
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Crea el cliente Retrofit. La URL del servidor se puede cambiar desde Ajustes,
 * por eso el cliente se reconstruye cuando cambia.
 */
class ApiProvider(private val ajustes: AjustesStore) {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = ajustes.actual.token
            // La analítica es anónima: nunca se le adjunta el token de la finca.
            val anonima = chain.request().url.encodedPath.contains("/api/telemetria")
            val peticion = if (token != null && !anonima) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(peticion)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private var cache: Pair<String, BobinappApi>? = null

    @Synchronized
    fun api(): BobinappApi {
        val url = ajustes.actual.servidorUrl
        cache?.let { (u, api) -> if (u == url) return api }
        val api = Retrofit.Builder()
            .baseUrl(url)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BobinappApi::class.java)
        cache = url to api
        return api
    }
}
