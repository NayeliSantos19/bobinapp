package com.bobinapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.bobinapp.data.local.entity.AnimalConEventos
import com.bobinapp.data.local.entity.AnimalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimalDao {

    @Transaction
    @Query("SELECT * FROM animales WHERE eliminado = 0 ORDER BY arete")
    fun observarConEventos(): Flow<List<AnimalConEventos>>

    @Transaction
    @Query("SELECT * FROM animales WHERE eliminado = 0")
    suspend fun todosConEventos(): List<AnimalConEventos>

    @Query("SELECT * FROM animales WHERE id = :id")
    fun observar(id: String): Flow<AnimalEntity?>

    @Query("SELECT * FROM animales WHERE id = :id")
    suspend fun obtener(id: String): AnimalEntity?

    @Query("SELECT * FROM animales WHERE eliminado = 0 AND (madreId = :id OR padreId = :id) ORDER BY nacimiento")
    fun observarCrias(id: String): Flow<List<AnimalEntity>>

    /**
     * Genealogía con una CTE recursiva: sube por madre y padre hasta [generaciones] niveles.
     * Una sola consulta en lugar de N consultas encadenadas.
     */
    @Query(
        """
        WITH RECURSIVE ancestros(id, generacion) AS (
            SELECT :id, 0
            UNION
            SELECT p.id, a.generacion + 1
            FROM ancestros a
            JOIN animales h ON h.id = a.id
            JOIN animales p ON (p.id = h.madreId OR p.id = h.padreId)
            WHERE a.generacion < :generaciones
        )
        SELECT animales.* FROM animales
        JOIN ancestros ON animales.id = ancestros.id
        WHERE ancestros.generacion > 0
        """
    )
    suspend fun ancestros(id: String, generaciones: Int): List<AnimalEntity>

    @Query("SELECT COUNT(*) FROM animales WHERE eliminado = 0 AND lower(arete) = lower(:arete) AND id != :excluirId")
    suspend fun contarArete(arete: String, excluirId: String): Int

    @Query("SELECT COUNT(*) FROM animales")
    suspend fun contar(): Int

    @Upsert
    suspend fun upsert(animal: AnimalEntity)

    @Upsert
    suspend fun upsertTodos(animales: List<AnimalEntity>)

    // ---- Cola de sincronización ----

    @Query("SELECT * FROM animales WHERE syncEstado = 'PENDIENTE' AND esDemo = 0 LIMIT :limite")
    suspend fun pendientes(limite: Int = 500): List<AnimalEntity>

    /** Cuenta filas por subir y también fotos por subir. */
    @Query("SELECT COUNT(*) FROM animales WHERE (syncEstado = 'PENDIENTE' OR fotoPendiente = 1) AND esDemo = 0")
    fun observarPendientes(): Flow<Int>

    /** Solo marca si nadie editó la fila mientras se subía (compara actualizadoEn). */
    @Query("UPDATE animales SET syncEstado = 'SINCRONIZADO' WHERE id = :id AND actualizadoEn = :actualizadoEn")
    suspend fun marcarSincronizado(id: String, actualizadoEn: Long)

    // ---- Fotos ----

    @Query("SELECT * FROM animales WHERE fotoPendiente = 1 AND esDemo = 0 AND fotoActualizadaEn IS NOT NULL")
    suspend fun fotosPorSubir(): List<AnimalEntity>

    @Query("UPDATE animales SET fotoPendiente = 0 WHERE id = :id AND fotoActualizadaEn = :version")
    suspend fun marcarFotoSubida(id: String, version: Long)

    @Query(
        """
        SELECT * FROM animales
        WHERE esDemo = 0 AND eliminado = 0 AND fotoActualizadaEn IS NOT NULL
          AND (fotoLocalEn IS NULL OR fotoLocalEn < fotoActualizadaEn)
        """
    )
    suspend fun fotosPorDescargar(): List<AnimalEntity>

    @Query("UPDATE animales SET fotoLocalEn = :version WHERE id = :id")
    suspend fun marcarFotoLocal(id: String, version: Long)

    @Query("UPDATE animales SET syncEstado = 'PENDIENTE', fotoPendiente = (fotoActualizadaEn IS NOT NULL) WHERE esDemo = 0")
    suspend fun marcarTodoPendiente()

    @Query("DELETE FROM animales WHERE esDemo = 1")
    suspend fun borrarDemo()
}
