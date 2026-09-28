package com.bobinapp

import com.bobinapp.data.demo.DemoSeeder
import com.bobinapp.data.local.entity.AnimalConEventos
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.Severidad
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val hoy = LocalDate.of(2026, 9, 25)
    private var n = 0
    private fun vaca(dias: Int = -1500) = AnimalEntity(id = "a${n++}", arete = "SV-$n", sexo = Sexo.H, nacimiento = hoy.plusDays(dias.toLong()))
    private fun ev(a: AnimalEntity, tipo: TipoEvento, dia: Int, b: EventoEntity.() -> EventoEntity = { this }) =
        EventoEntity(id = "e${n++}", animalId = a.id, tipo = tipo, fecha = hoy.plusDays(dia.toLong())).b()
    private fun alertas(vararg pares: Pair<AnimalEntity, List<EventoEntity>>) =
        AlertEngine.calcular(pares.map { (a, e) -> AnimalConEventos(a, e) }, hoy)

    @Test
    fun `celo detectado hoy genera alerta de inseminar hoy`() {
        val a = vaca()
        val r = alertas(a to listOf(ev(a, TipoEvento.CELO, 0)))
        assertEquals("Inseminar hoy", r.single().titulo)
        assertEquals(Severidad.HOY, r.single().severidad)
        assertEquals(TipoEvento.INSEMINACION, r.single().accion)
    }

    @Test
    fun `celo hace 19 dias predice el siguiente en 2 dias`() {
        val a = vaca()
        val r = alertas(a to listOf(ev(a, TipoEvento.CELO, -19)))
        val celo = r.single { it.titulo == "Celo esperado" }
        assertEquals(2L, celo.dias)
        assertEquals(hoy.plusDays(2), celo.fecha)
    }

    @Test
    fun `inseminada sin diagnostico a los 38 dias tiene palpacion vencida`() {
        val a = vaca()
        val r = alertas(a to listOf(ev(a, TipoEvento.INSEMINACION, -38) { copy(toro = "X") }))
        val palp = r.single { it.titulo == "Diagnóstico de preñez" }
        assertEquals(Severidad.VENCIDA, palp.severidad)
        assertEquals(-3L, palp.dias)
    }

    @Test
    fun `preñada confirmada calcula parto a 283 dias`() {
        val a = vaca()
        val r = alertas(a to listOf(
            ev(a, TipoEvento.INSEMINACION, -265) { copy(toro = "X") },
            ev(a, TipoEvento.PALPACION, -220) { copy(resultado = ResultadoPalpacion.PRENADA) },
        ))
        val parto = r.single { it.titulo == "Parto próximo" }
        assertEquals(hoy.plusDays(18), parto.fecha)
        assertEquals(Severidad.PROXIMA, parto.severidad)
    }

    @Test
    fun `palpacion vacia no genera alerta de parto`() {
        val a = vaca()
        val r = alertas(a to listOf(
            ev(a, TipoEvento.INSEMINACION, -265) { copy(toro = "X") },
            ev(a, TipoEvento.PALPACION, -220) { copy(resultado = ResultadoPalpacion.VACIA) },
        ))
        assertTrue(r.none { it.titulo.startsWith("Parto") })
    }

    @Test
    fun `dosis programadas del mismo producto y fecha se agrupan`() {
        val a = vaca(); val b = vaca()
        val vac: EventoEntity.() -> EventoEntity = { copy(producto = "Ivermectina 1%", proximaFecha = hoy.plusDays(5)) }
        val r = alertas(a to listOf(ev(a, TipoEvento.VACUNA, -85, vac)), b to listOf(ev(b, TipoEvento.VACUNA, -85, vac)))
        val grupo = r.single { it.titulo == "Vacuna: Ivermectina 1%" }
        assertEquals(2, grupo.animalIds.size)
    }

    @Test
    fun `una dosis ya aplicada despues cancela la alerta`() {
        val a = vaca()
        val r = alertas(a to listOf(
            ev(a, TipoEvento.VACUNA, -85) { copy(producto = "Triple bovina", proximaFecha = hoy.plusDays(5)) },
            ev(a, TipoEvento.VACUNA, -1) { copy(producto = "triple bovina") },
        ))
        assertTrue(r.none { it.titulo.startsWith("Vacuna") })
    }

    @Test
    fun `retiro de leche activo genera alerta`() {
        val a = vaca()
        val r = alertas(a to listOf(ev(a, TipoEvento.TRATAMIENTO, -3) { copy(producto = "Cefquinoma", diagnostico = "Mastitis", retiroDias = 5) }))
        val retiro = r.single { it.titulo == "Leche y carne en retiro" }
        assertEquals(hoy.plusDays(2), retiro.fecha)
    }

    @Test
    fun `animales dados de baja no generan alertas`() {
        val a = vaca().copy(estado = EstadoAnimal.VENDIDA)
        assertTrue(alertas(a to listOf(ev(a, TipoEvento.CELO, 0))).isEmpty())
    }

    @Test
    fun `las alertas salen ordenadas de mas a menos urgente`() {
        val (animales, eventos) = DemoSeeder.generar(hoy)
        val r = AlertEngine.calcular(animales.map { a -> AnimalConEventos(a, eventos.filter { it.animalId == a.id }) }, hoy)
        assertTrue(r.isNotEmpty())
        assertEquals(r.sortedWith(compareBy({ it.severidad.orden }, { it.dias })), r)
    }
}
