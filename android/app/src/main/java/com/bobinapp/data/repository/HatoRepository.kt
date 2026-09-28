package com.bobinapp.data.repository

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.bobinapp.data.demo.DemoSeeder
import com.bobinapp.data.fotos.FotosAnimales
import com.bobinapp.data.local.BobinappDatabase
import com.bobinapp.data.local.entity.AnimalConEventos
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.SyncEstado
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.TotalDiario
import com.bobinapp.data.local.entity.TotalMensual
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Único punto de escritura del hato. Toda escritura:
 *  1. se guarda en Room (funciona sin internet),
 *  2. queda marcada como PENDIENTE (cola de subida),
 *  3. avisa a [alCambiar] para programar la sincronización.
 */
class HatoRepository(
    private val db: BobinappDatabase,
    private val fotos: FotosAnimales,
    private val alCambiar: () -> Unit,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val animales = db.animalDao()
    private val eventos = db.eventoDao()

    fun observarHato(): Flow<List<AnimalConEventos>> = animales.observarConEventos()
    fun observarAnimal(id: String): Flow<AnimalEntity?> = animales.observar(id)
    fun observarEventos(animalId: String): Flow<List<EventoEntity>> = eventos.observarDeAnimal(animalId)
    fun observarCrias(id: String): Flow<List<AnimalEntity>> = animales.observarCrias(id)
    fun lecheDiaria(desde: LocalDate): Flow<List<TotalDiario>> = eventos.lecheDiaria(desde)
    fun gastoFarmaciaMensual(desde: LocalDate): Flow<List<TotalMensual>> = eventos.gastoFarmaciaMensual(desde)
    fun observarPendientes(): Flow<Int> =
        combine(animales.observarPendientes(), eventos.observarPendientes()) { a, e -> a + e }

    suspend fun todosConEventos(): List<AnimalConEventos> = animales.todosConEventos()
    suspend fun obtenerAnimal(id: String): AnimalEntity? = animales.obtener(id)
    suspend fun ancestros(id: String, generaciones: Int = 2): List<AnimalEntity> = animales.ancestros(id, generaciones)
    suspend fun areteDisponible(arete: String, excluirId: String = ""): Boolean =
        animales.contarArete(arete.trim(), excluirId) == 0

    suspend fun guardarAnimal(animal: AnimalEntity) {
        animales.upsert(animal.copy(actualizadoEn = reloj(), syncEstado = SyncEstado.PENDIENTE))
        alCambiar()
    }

    suspend fun registrarEventos(lista: List<EventoEntity>) {
        if (lista.isEmpty()) return
        val ahora = reloj()
        eventos.upsertTodos(lista.map { it.copy(actualizadoEn = ahora, syncEstado = SyncEstado.PENDIENTE) })
        alCambiar()
    }

    /** Borrado lógico: el evento se marca como eliminado para que el borrado también se sincronice. */
    suspend fun borrarEvento(evento: EventoEntity) =
        registrarEventos(listOf(evento.copy(eliminado = true)))

    /** Registra el parto en la madre y crea la cría en una sola transacción. */
    suspend fun registrarParto(madre: AnimalEntity, cria: AnimalEntity, fecha: LocalDate, pesoAlNacer: Double?) {
        val ahora = reloj()
        db.withTransaction {
            animales.upsert(cria.copy(actualizadoEn = ahora, syncEstado = SyncEstado.PENDIENTE, esDemo = madre.esDemo))
            val nuevos = buildList {
                add(EventoEntity(id = nuevoId(), animalId = madre.id, tipo = TipoEvento.PARTO, fecha = fecha, criaId = cria.id, esDemo = madre.esDemo))
                if (pesoAlNacer != null) {
                    add(EventoEntity(id = nuevoId(), animalId = cria.id, tipo = TipoEvento.PESO, fecha = fecha, kg = pesoAlNacer, esDemo = madre.esDemo))
                }
            }
            eventos.upsertTodos(nuevos.map { it.copy(actualizadoEn = ahora, syncEstado = SyncEstado.PENDIENTE) })
        }
        alCambiar()
    }

    suspend fun darDeBaja(animal: AnimalEntity, estado: EstadoAnimal, fecha: LocalDate, detalle: String?) {
        val ahora = reloj()
        db.withTransaction {
            animales.upsert(animal.copy(estado = estado, actualizadoEn = ahora, syncEstado = SyncEstado.PENDIENTE))
            eventos.upsert(
                EventoEntity(
                    id = nuevoId(), animalId = animal.id, tipo = TipoEvento.BAJA, fecha = fecha,
                    nota = listOfNotNull(estado.etiqueta, detalle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    actualizadoEn = ahora, esDemo = animal.esDemo,
                )
            )
        }
        alCambiar()
    }

    /** Guarda la foto en el teléfono y deja la fila y la foto en cola para subir. */
    suspend fun cambiarFoto(animal: AnimalEntity, foto: Bitmap) {
        fotos.guardar(animal.id, foto)
        val version = reloj()
        guardarAnimal(animal.copy(fotoActualizadaEn = version, fotoLocalEn = version, fotoPendiente = true))
    }

    suspend fun quitarFoto(animal: AnimalEntity) {
        fotos.borrar(animal.id)
        guardarAnimal(animal.copy(fotoActualizadaEn = null, fotoLocalEn = null, fotoPendiente = false))
    }

    suspend fun eliminarAnimal(animal: AnimalEntity) = guardarAnimal(animal.copy(eliminado = true))

    suspend fun borrarDemo() = db.withTransaction {
        animales.borrarDemo()
        eventos.borrarDemo()
    }

    suspend fun cargarDemo() = DemoSeeder.sembrar(db)

    /** Al conectar una finca nueva, todo lo local debe subirse a ella. */
    suspend fun marcarTodoPendiente() {
        db.withTransaction {
            animales.marcarTodoPendiente()
            eventos.marcarTodoPendiente()
        }
        alCambiar()
    }

    companion object {
        fun nuevoId(): String = UUID.randomUUID().toString()
    }
}
