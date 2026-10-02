package com.bobinapp

import com.bobinapp.domain.Alerta
import com.bobinapp.domain.PoliticaNotificaciones
import com.bobinapp.domain.Severidad
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaNotificacionesTest {
    private val hoy = LocalDate.of(2026, 10, 1)
    private fun alerta(clave: String, categoria: String, sev: Severidad = Severidad.HOY) =
        Alerta(clave, sev, hoy, 0, "t", "d", categoria, listOf("a"))

    private val hato = listOf(
        alerta("insem:1", "Reproducción"),
        alerta("dosis:2", "Salud", Severidad.VENCIDA),
        alerta("pesaje:3", "Manejo"),
        alerta("parto:4", "Reproducción", Severidad.PROXIMA),
    )

    @Test
    fun notificaSoloUrgentesNuevas() {
        val d = PoliticaNotificaciones.decidir(hato, yaNotificadas = setOf("dosis:2"), categoriasApagadas = emptySet(), silencioNocturno = true, hora = 10)
        assertEquals(listOf("insem:1", "pesaje:3"), d.notificar.map { it.clave })
        assertEquals(setOf("insem:1", "dosis:2", "pesaje:3"), d.recordar)
    }

    @Test
    fun respetaCategoriasApagadas() {
        val d = PoliticaNotificaciones.decidir(hato, emptySet(), categoriasApagadas = setOf("Manejo", "Salud"), silencioNocturno = false, hora = 10)
        assertEquals(listOf("insem:1"), d.notificar.map { it.clave })
        // Las apagadas no se recuerdan: si el usuario las vuelve a activar, sí llegan.
        assertEquals(setOf("insem:1"), d.recordar)
    }

    @Test
    fun silencioNocturnoGuardaParaDespues() {
        val noche = PoliticaNotificaciones.decidir(hato, setOf("insem:1", "vieja:9"), emptySet(), silencioNocturno = true, hora = 23)
        assertTrue(noche.notificar.isEmpty())
        assertEquals(setOf("insem:1"), noche.recordar)
        val manana = PoliticaNotificaciones.decidir(hato, noche.recordar, emptySet(), silencioNocturno = true, hora = 6)
        assertEquals(listOf("dosis:2", "pesaje:3"), manana.notificar.map { it.clave })
    }

    @Test
    fun sinSilencioNotificaDeNoche() {
        val d = PoliticaNotificaciones.decidir(hato, emptySet(), emptySet(), silencioNocturno = false, hora = 2)
        assertEquals(3, d.notificar.size)
    }
}
