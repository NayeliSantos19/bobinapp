package com.bobinapp.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImagenUtil {

    /** Archivo temporal donde la app de cámara escribe la foto a resolución completa. */
    fun uriParaCamara(context: Context): Uri {
        val archivo = File(File(context.cacheDir, "camara").apply { mkdirs() }, "captura.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fotos", archivo)
    }

    /**
     * Decodifica una imagen de la cámara o la galería:
     * - con inSampleSize para no cargar 12 MP en memoria,
     * - corrigiendo la rotación EXIF (las cámaras guardan la foto "acostada" y marcan el giro en EXIF).
     */
    suspend fun decodificar(context: Context, uri: Uri, ladoMaximo: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) } ?: return@withContext null
        if (limites.outWidth <= 0) return@withContext null
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
        } ?: return@withContext null
        val grados = cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (grados == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(grados) }, true)
    }
}
