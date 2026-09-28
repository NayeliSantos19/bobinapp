package com.bobinapp.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Guarda la foto de cada animal como archivo JPEG en el almacenamiento privado de la app
 * (filesDir/fotos/<id>.jpg). La base de datos solo guarda la versión, no los bytes.
 */
class FotosAnimales(context: Context) {
    private val carpeta = File(context.filesDir, "fotos").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun archivo(animalId: String) = File(carpeta, "$animalId.jpg")

    /** Reduce a 1280 px de lado mayor y guarda como JPEG 85 %. Escritura atómica (temporal + renombrar). */
    suspend fun guardar(animalId: String, original: Bitmap) = withContext(Dispatchers.IO) {
        val escala = 1280f / maxOf(original.width, original.height)
        val b = if (escala < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * escala).toInt(), (original.height * escala).toInt(), true)
        } else original
        val salida = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        escribir(animalId, salida.toByteArray())
    }

    suspend fun escribir(animalId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val tmp = File(carpeta, "$animalId.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(archivo(animalId))) {
            archivo(animalId).writeBytes(bytes)
            tmp.delete()
        }
        synchronized(cache) { cache.snapshot().keys.filter { it.startsWith(animalId) }.forEach { cache.remove(it) } }
    }

    suspend fun leerBytes(animalId: String): ByteArray? = withContext(Dispatchers.IO) {
        archivo(animalId).takeIf { it.exists() }?.readBytes()
    }

    fun borrar(animalId: String) {
        archivo(animalId).delete()
        synchronized(cache) { cache.snapshot().keys.filter { it.startsWith(animalId) }.forEach { cache.remove(it) } }
    }

    /** Miniatura decodificada con inSampleSize para no cargar la foto completa en memoria. */
    suspend fun miniatura(animalId: String, ladoMaximo: Int): Bitmap? = withContext(Dispatchers.IO) {
        val clave = "$animalId@$ladoMaximo"
        synchronized(cache) { cache.get(clave) }?.let { return@withContext it }
        val f = archivo(animalId)
        if (!f.exists()) return@withContext null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, limites)
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = muestra })
        bmp?.also { synchronized(cache) { cache.put(clave, it) } }
    }
}
