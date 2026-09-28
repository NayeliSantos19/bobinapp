package com.bobinapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobinapp.data.local.BobinappDatabase
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.SyncEstado
import com.bobinapp.data.local.entity.TipoEvento
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de las consultas SQL reales de Room sobre una base en memoria. */
@RunWith(AndroidJUnit4::class)
class HatoDaoTest {
    private lateinit var db: BobinappDatabase
    private val hoy = LocalDate.of(2026, 9, 25)

    @Before
    fun crear() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BobinappDatabase::class.java).build()
    }

    @After
    fun cerrar() = db.close()

    private fun animal(id: String, madre: String? = null, padre: String? = null, sync: SyncEstado = SyncEstado.SINCRONIZADO) =
        AnimalEntity(id = id, arete = id, sexo = Sexo.H, nacimiento = hoy.minusYears(3), madreId = madre, padreId = padre, syncEstado = sync)

    @Test
    fun ancestrosConCteRecursiva() = runTest {
        val dao = db.animalDao()
        dao.upsertTodos(listOf(
            animal("abuela"), animal("abuelo"), animal("madre", "abuela", "abuelo"), animal("padre"),
            animal("cria", "madre", "padre"), animal("bisabuela"),
        ))
        dao.upsert(animal("abuela", madre = "bisabuela"))
        val dosGeneraciones = dao.ancestros("cria", 2).map { it.id }.toSet()
        assertEquals(setOf("madre", "padre", "abuela", "abuelo"), dosGeneraciones)
        assertEquals(5, dao.ancestros("cria", 3).size)
    }

    @Test
    fun colaDePendientesExcluyeDemoYSeVacia() = runTest {
        val dao = db.animalDao()
        dao.upsert(animal("a", sync = SyncEstado.PENDIENTE))
        dao.upsert(animal("b", sync = SyncEstado.PENDIENTE).copy(esDemo = true))
        val pendientes = dao.pendientes()
        assertEquals(listOf("a"), pendientes.map { it.id })
        dao.marcarSincronizado("a", pendientes.first().actualizadoEn)
        assertEquals(0, dao.observarPendientes().first())
    }

    @Test
    fun lecheDiariaAgregaPorDia() = runTest {
        db.animalDao().upsertTodos(listOf(animal("a"), animal("b")))
        db.eventoDao().upsertTodos(listOf(
            EventoEntity(id = "1", animalId = "a", tipo = TipoEvento.LECHE, fecha = hoy, litros = 10.0),
            EventoEntity(id = "2", animalId = "b", tipo = TipoEvento.LECHE, fecha = hoy, litros = 12.5),
            EventoEntity(id = "3", animalId = "a", tipo = TipoEvento.LECHE, fecha = hoy.minusDays(1), litros = 9.0),
        ))
        val serie = db.eventoDao().lecheDiaria(hoy.minusDays(7)).first()
        assertEquals(2, serie.size)
        assertEquals(22.5, serie.last().total, 1e-9)
    }

    @Test
    fun fotosPorDescargarSoloSiLaRemotaEsMasNueva() = runTest {
        val dao = db.animalDao()
        dao.upsertTodos(listOf(
            animal("vieja").copy(fotoActualizadaEn = 200, fotoLocalEn = 100),
            animal("al_dia").copy(fotoActualizadaEn = 200, fotoLocalEn = 200),
            animal("nunca_bajada").copy(fotoActualizadaEn = 300),
            animal("sin_foto"),
        ))
        assertEquals(setOf("vieja", "nunca_bajada"), dao.fotosPorDescargar().map { it.id }.toSet())
    }
}
