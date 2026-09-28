package com.bobinapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.TotalDiario
import com.bobinapp.data.local.entity.TotalMensual
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface EventoDao {

    @Query("SELECT * FROM eventos WHERE animalId = :animalId AND eliminado = 0 ORDER BY fecha DESC")
    fun observarDeAnimal(animalId: String): Flow<List<EventoEntity>>

    @Query("SELECT * FROM eventos WHERE id = :id")
    suspend fun obtener(id: String): EventoEntity?

    @Upsert
    suspend fun upsert(evento: EventoEntity)

    @Upsert
    suspend fun upsertTodos(eventos: List<EventoEntity>)

    // ---- Analíticas: la base hace la agregación ----

    @Query(
        """
        SELECT fecha AS dia, SUM(litros) AS total FROM eventos
        WHERE tipo = 'LECHE' AND eliminado = 0 AND fecha >= :desde
        GROUP BY fecha ORDER BY fecha
        """
    )
    fun lecheDiaria(desde: LocalDate): Flow<List<TotalDiario>>

    @Query(
        """
        SELECT substr(fecha, 1, 7) AS mes, SUM(costo) AS total FROM eventos
        WHERE tipo IN ('VACUNA', 'TRATAMIENTO') AND costo IS NOT NULL AND eliminado = 0 AND fecha >= :desde
        GROUP BY mes ORDER BY mes
        """
    )
    fun gastoFarmaciaMensual(desde: LocalDate): Flow<List<TotalMensual>>

    // ---- Cola de sincronización ----

    @Query("SELECT * FROM eventos WHERE syncEstado = 'PENDIENTE' AND esDemo = 0 LIMIT :limite")
    suspend fun pendientes(limite: Int = 2000): List<EventoEntity>

    @Query("SELECT COUNT(*) FROM eventos WHERE syncEstado = 'PENDIENTE' AND esDemo = 0")
    fun observarPendientes(): Flow<Int>

    @Query("UPDATE eventos SET syncEstado = 'SINCRONIZADO' WHERE id = :id AND actualizadoEn = :actualizadoEn")
    suspend fun marcarSincronizado(id: String, actualizadoEn: Long)

    @Query("UPDATE eventos SET syncEstado = 'PENDIENTE' WHERE esDemo = 0")
    suspend fun marcarTodoPendiente()

    @Query("DELETE FROM eventos WHERE esDemo = 1")
    suspend fun borrarDemo()
}
