package com.bobinapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class Raza(
    val id: String,
    val nombre: String,
    val otrosNombres: String = "",
    val grupo: String,
    val aptitudes: List<String>,
    val origen: String,
    val pelaje: String,
    val cuernos: String,
    val pesoMachoKg: String,
    val pesoHembraKg: String,
    val clima: String,
    val descripcion: String,
    val rasgos: List<String>,
    val ventajas: List<String>,
    val desventajas: List<String>,
    val dato: String,
    val colorPrincipal: String,
    val colorSecundario: String? = null,
    val giba: Boolean = false,
) {
    val grupoEtiqueta: String
        get() = when (grupo) {
            "indicus" -> "Cebú"
            "taurus" -> "Europea"
            "sintetica" -> "Sintética"
            else -> "Criolla"
        }
}

fun aptitudEtiqueta(a: String) = when (a) {
    "carne" -> "Carne"
    "leche" -> "Leche"
    else -> "Doble propósito"
}

/**
 * Catálogo de razas empaquetado en assets/razas.json (el mismo que usa el backend).
 * Las fotos de referencia van en assets/razas/<id>.jpg y sus créditos en assets/razas/creditos.json.
 * Si falta la foto de una raza, la interfaz muestra sus colores de pelaje.
 */
class RazasRepository(private val context: Context) {
    val todas: List<Raza> by lazy {
        val texto = context.assets.open("razas.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<List<Raza>>(texto)
    }
    private val porId by lazy { todas.associateBy { it.id } }

    fun buscar(id: String?): Raza? = id?.let { porId[it] }

    private val fotosDisponibles: Set<String> by lazy {
        context.assets.list("razas")?.filter { it.endsWith(".jpg") }?.map { it.removeSuffix(".jpg") }?.toSet() ?: emptySet()
    }

    private val creditos: Map<String, String> by lazy {
        try {
            val texto = context.assets.open("razas/creditos.json").bufferedReader().use { it.readText() }
            Json.decodeFromString<Map<String, String>>(texto)
        } catch (e: IOException) {
            emptyMap()
        }
    }

    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun tieneFoto(id: String) = id in fotosDisponibles
    fun credito(id: String): String? = creditos[id]

    /** Decodifica la foto de referencia con inSampleSize según el tamaño en pantalla. */
    suspend fun foto(id: String, ladoMaximo: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!tieneFoto(id)) return@withContext null
        val clave = "$id@$ladoMaximo"
        synchronized(cache) { cache.get(clave) }?.let { return@withContext it }
        val ruta = "razas/$id.jpg"
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(ruta).use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        val bmp = context.assets.open(ruta).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) }
        bmp?.also { synchronized(cache) { cache.put(clave, it) } }
    }
}
