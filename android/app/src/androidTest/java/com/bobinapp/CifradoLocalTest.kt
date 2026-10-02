package com.bobinapp

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobinapp.data.seguridad.CifradoLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Corre en un dispositivo real o emulador porque usa el Android Keystore. */
@RunWith(AndroidJUnit4::class)
class CifradoLocalTest {
    private val token = "a3f9c2e1d4b5a6978877665544332211ffeeddccbbaa99887766554433221100"

    @Test
    fun cifraYDescifra() {
        val guardado = CifradoLocal.cifrar(token)
        assertNotEquals(token, guardado)
        assertEquals(token, CifradoLocal.descifrar(guardado))
    }

    @Test
    fun cadaCifradoUsaUnIvDistinto() {
        assertNotEquals(CifradoLocal.cifrar(token), CifradoLocal.cifrar(token))
    }

    @Test
    fun unDatoAlteradoNoSeAcepta() {
        val bytes = Base64.decode(CifradoLocal.cifrar(token), Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        assertNull(CifradoLocal.descifrar(Base64.encodeToString(bytes, Base64.NO_WRAP)))
        assertNull(CifradoLocal.descifrar("esto no es base64 válido"))
    }
}
