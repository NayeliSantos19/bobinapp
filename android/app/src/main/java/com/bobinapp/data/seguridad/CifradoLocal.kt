package com.bobinapp.data.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra textos pequeños (el token de la finca) con una llave AES-256 que vive en el Android Keystore.
 * La llave nunca sale del hardware seguro del teléfono: aunque alguien copie el archivo de preferencias
 * (por ejemplo, desde una copia de seguridad), sin el teléfono no puede leer el token.
 *
 * Formato guardado: Base64( IV de 12 bytes + texto cifrado con etiqueta GCM ).
 */
object CifradoLocal {
    private const val ALMACEN = "AndroidKeyStore"
    private const val ALIAS = "bobinapp_llave_token"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val ETIQUETA_BITS = 128

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance(ALMACEN).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ALMACEN)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    fun cifrar(texto: String): String {
        val c = Cipher.getInstance(TRANSFORMACION)
        c.init(Cipher.ENCRYPT_MODE, llave())
        val cifrado = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + cifrado, Base64.NO_WRAP)
    }

    /** Devuelve null si el dato está dañado o se cifró con una llave que ya no existe (app reinstalada, copia restaurada). */
    fun descifrar(guardado: String): String? = try {
        val bytes = Base64.decode(guardado, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORMACION)
        c.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(ETIQUETA_BITS, bytes, 0, IV_BYTES))
        String(c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
