package com.bobinapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

enum class Sexo { H, M }

enum class EstadoAnimal(val etiqueta: String) {
    ACTIVA("Activa"), VENDIDA("Vendida"), MUERTA("Muerta"), DESCARTADA("Descartada")
}

enum class TipoEvento(val etiqueta: String) {
    PESO("Pesaje"),
    LECHE("Leche"),
    VACUNA("Vacuna"),
    TRATAMIENTO("Tratamiento"),
    CELO("Celo"),
    INSEMINACION("Inseminación"),
    PALPACION("Diagnóstico de preñez"),
    PARTO("Parto"),
    DESTETE("Destete"),
    BAJA("Baja"),
    NOTA("Nota");

    val soloHembras: Boolean get() = this in setOf(LECHE, CELO, INSEMINACION, PALPACION)
}

enum class ResultadoPalpacion(val etiqueta: String) { PRENADA("Preñada"), VACIA("Vacía") }

/** Estado de cada fila frente a la nube: PENDIENTE = está en la cola de subida. */
enum class SyncEstado { SINCRONIZADO, PENDIENTE }

/**
 * Una res del hato. Las relaciones madre/padre apuntan a otras filas de esta misma tabla.
 * No se declaran llaves foráneas a propósito: durante la sincronización una cría puede
 * llegar antes que su madre, y SQLite rechazaría la inserción.
 */
@Entity(
    tableName = "animales",
    indices = [Index("madreId"), Index("padreId"), Index("arete"), Index("syncEstado")],
)
data class AnimalEntity(
    @PrimaryKey val id: String,
    val arete: String,
    val nombre: String? = null,
    val sexo: Sexo,
    val razaId: String? = null,
    val razaTexto: String? = null,
    val nacimiento: LocalDate,
    val madreId: String? = null,
    val padreId: String? = null,
    val padreExterno: String? = null,
    val castrado: Boolean = false,
    val estado: EstadoAnimal = EstadoAnimal.ACTIVA,
    val notas: String? = null,
    /** Versión (epoch ms) de la foto vigente del animal. Se sincroniza con la fila. */
    val fotoActualizadaEn: Long? = null,
    /** Versión de la foto guardada en ESTE teléfono. Solo local: si es menor que fotoActualizadaEn, hay que descargarla. */
    val fotoLocalEn: Long? = null,
    /** La foto se tomó aquí y falta subirla. Solo local. */
    @ColumnInfo(defaultValue = "0")
    val fotoPendiente: Boolean = false,
    val eliminado: Boolean = false,
    /** Epoch ms del último cambio en el dispositivo. Decide conflictos (gana el más reciente). */
    val actualizadoEn: Long = System.currentTimeMillis(),
    val syncEstado: SyncEstado = SyncEstado.PENDIENTE,
    /** Datos de ejemplo: nunca se suben a la nube. */
    val esDemo: Boolean = false,
)

/** Nombre para mostrar (extensión fuera de la entidad para que Room no la trate como columna). */
val AnimalEntity.nombreVisible: String get() = nombre?.takeIf { it.isNotBlank() } ?: arete

/** Cualquier cosa que le pasa a un animal: pesaje, vacuna, celo, parto... Los campos dependen del tipo. */
@Entity(
    tableName = "eventos",
    indices = [Index(value = ["animalId", "fecha"]), Index("tipo"), Index("syncEstado")],
)
data class EventoEntity(
    @PrimaryKey val id: String,
    val animalId: String,
    val tipo: TipoEvento,
    val fecha: LocalDate,
    val kg: Double? = null,
    val litros: Double? = null,
    val producto: String? = null,
    val dosis: String? = null,
    val diagnostico: String? = null,
    val proximaFecha: LocalDate? = null,
    val retiroDias: Int? = null,
    val costo: Double? = null,
    val toro: String? = null,
    val toroId: String? = null,
    val tecnico: String? = null,
    val resultado: ResultadoPalpacion? = null,
    val criaId: String? = null,
    val nota: String? = null,
    val eliminado: Boolean = false,
    val actualizadoEn: Long = System.currentTimeMillis(),
    val syncEstado: SyncEstado = SyncEstado.PENDIENTE,
    val esDemo: Boolean = false,
)

/** Animal con todo su historial, cargado en una sola consulta con @Relation. */
data class AnimalConEventos(
    @Embedded val animal: AnimalEntity,
    @Relation(parentColumn = "id", entityColumn = "animalId")
    val todosLosEventos: List<EventoEntity>,
)

/** Eventos vigentes (sin borrados), en orden cronológico. */
val AnimalConEventos.eventos: List<EventoEntity> get() = todosLosEventos.filterNot { it.eliminado }.sortedBy { it.fecha }

/** Resultados de consultas de agregación para el panel. */
data class TotalDiario(val dia: LocalDate, val total: Double)
data class TotalMensual(val mes: String, val total: Double)
