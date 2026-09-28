package com.bobinapp.data.repository

import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.SyncEstado

/** Reglas puras de resolución de conflictos. Sin Android ni red, para probarlas con JUnit. */
object ReglasSync {

    /** Last-writer-wins. Un empate se resuelve a favor del servidor para que todos converjan. */
    fun debeAplicarRemoto(localActualizadoEn: Long?, remotoActualizadoEn: Long): Boolean =
        localActualizadoEn == null || remotoActualizadoEn >= localActualizadoEn

    /**
     * Combina la versión remota de un animal con la local.
     * - Si gana la local, devuelve null (no hay nada que aplicar).
     * - Si gana la remota, se conservan los campos que solo existen en este teléfono (fotoLocalEn, fotoPendiente).
     * - Si aquí se tomó una foto más nueva que aún no se sube, esa foto se respeta aunque el resto de la fila
     *   venga del servidor, y la fila queda pendiente para volver a subir la referencia correcta.
     */
    fun fusionarAnimal(local: AnimalEntity?, remoto: AnimalEntity): AnimalEntity? {
        if (local == null) return remoto
        if (!debeAplicarRemoto(local.actualizadoEn, remoto.actualizadoEn)) return null
        val fotoLocalMasNueva = local.fotoPendiente &&
            (local.fotoActualizadaEn ?: 0L) > (remoto.fotoActualizadaEn ?: 0L)
        return if (fotoLocalMasNueva) {
            remoto.copy(
                fotoActualizadaEn = local.fotoActualizadaEn,
                fotoLocalEn = local.fotoLocalEn,
                fotoPendiente = true,
                syncEstado = SyncEstado.PENDIENTE,
            )
        } else {
            remoto.copy(fotoLocalEn = local.fotoLocalEn, fotoPendiente = false)
        }
    }
}
