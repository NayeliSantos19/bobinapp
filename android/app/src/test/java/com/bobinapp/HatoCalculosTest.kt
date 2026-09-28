package com.bobinapp

import com.bobinapp.data.demo.DemoSeeder
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.SyncEstado
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.repository.ReglasSync
import com.bobinapp.domain.HatoCalculos
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HatoCalculosTest {
    private val hoy = LocalDate.of(2026, 9, 25)
    private fun animal(sexo: Sexo, dias: Long, castrado: Boolean = false) =
        AnimalEntity(id = "x", arete = "SV-1", sexo = sexo, nacimiento = hoy.minusDays(dias), castrado = castrado)

    @Test
    fun `categoria depende de sexo, edad, partos y castracion`() {
        assertEquals("Ternera", HatoCalculos.categoria(animal(Sexo.H, 100), emptyList(), hoy))
        assertEquals("Novilla", HatoCalculos.categoria(animal(Sexo.H, 600), emptyList(), hoy))
        val parto = EventoEntity(id = "p", animalId = "x", tipo = TipoEvento.PARTO, fecha = hoy)
        assertEquals("Vaca", HatoCalculos.categoria(animal(Sexo.H, 900), listOf(parto), hoy))
        assertEquals("Torete", HatoCalculos.categoria(animal(Sexo.M, 400), emptyList(), hoy))
        assertEquals("Toro", HatoCalculos.categoria(animal(Sexo.M, 1000), emptyList(), hoy))
        assertEquals("Novillo", HatoCalculos.categoria(animal(Sexo.M, 600, castrado = true), emptyList(), hoy))
    }

    @Test
    fun `ganancia diaria usa primer y ultimo pesaje de la ventana`() {
        val pesos = listOf(
            EventoEntity(id = "1", animalId = "x", tipo = TipoEvento.PESO, fecha = hoy.minusDays(100), kg = 200.0),
            EventoEntity(id = "2", animalId = "x", tipo = TipoEvento.PESO, fecha = hoy, kg = 250.0),
        )
        assertEquals(0.5, HatoCalculos.gananciaDiaria(pesos, hoy)!!, 1e-9)
        assertNull(HatoCalculos.gananciaDiaria(pesos.take(1), hoy))
    }

    @Test
    fun `edad en texto legible`() {
        assertEquals("30 días", HatoCalculos.edadTexto(hoy.minusDays(30), hoy))
        assertEquals("2 años", HatoCalculos.edadTexto(hoy.minusDays(731), hoy))
    }

    @Test
    fun `regla last-writer-wins de la sincronizacion`() {
        assertTrue(ReglasSync.debeAplicarRemoto(null, 100))
        assertTrue(ReglasSync.debeAplicarRemoto(100, 200))
        assertTrue(ReglasSync.debeAplicarRemoto(100, 100))
        assertFalse(ReglasSync.debeAplicarRemoto(300, 200))
    }

    @Test
    fun `al aplicar un cambio remoto se conservan los datos locales de la foto`() {
        val local = animal(Sexo.H, 900).copy(actualizadoEn = 100, fotoActualizadaEn = 50, fotoLocalEn = 50)
        val remoto = local.copy(nombre = "Nuevo", actualizadoEn = 200, fotoLocalEn = null)
        val r = ReglasSync.fusionarAnimal(local, remoto)!!
        assertEquals("Nuevo", r.nombre)
        assertEquals(50L, r.fotoLocalEn)
        assertFalse(r.fotoPendiente)
    }

    @Test
    fun `una foto nueva tomada sin senal no se pierde si llega un cambio remoto`() {
        val local = animal(Sexo.H, 900).copy(actualizadoEn = 100, fotoActualizadaEn = 150, fotoLocalEn = 150, fotoPendiente = true)
        val remoto = local.copy(nombre = "Editado en otro telefono", actualizadoEn = 200, fotoActualizadaEn = 80, fotoPendiente = false)
        val r = ReglasSync.fusionarAnimal(local, remoto)!!
        assertEquals("Editado en otro telefono", r.nombre)
        assertEquals(150L, r.fotoActualizadaEn)
        assertTrue(r.fotoPendiente)
        assertEquals(SyncEstado.PENDIENTE, r.syncEstado)
    }

    @Test
    fun `un cambio remoto mas viejo se ignora`() {
        val local = animal(Sexo.H, 900).copy(actualizadoEn = 300)
        assertNull(ReglasSync.fusionarAnimal(local, local.copy(actualizadoEn = 200)))
    }

    @Test
    fun `datos de ejemplo son consistentes`() {
        val (animales, eventos) = DemoSeeder.generar(hoy)
        val ids = animales.map { it.id }.toSet()
        assertEquals(13, animales.size)
        assertTrue(eventos.all { it.animalId in ids })
        assertTrue(animales.all { it.madreId == null || it.madreId in ids })
        assertTrue(animales.all { it.esDemo })
        val lucero = animales.first { it.nombre == "Lucero" }
        assertNotNull(HatoCalculos.estadoReproductivo(lucero, eventos.filter { it.animalId == lucero.id }, hoy)
            as? HatoCalculos.EstadoReproductivo.Prenada)
    }
}
