package com.bobinapp.data.repository

import androidx.room.withTransaction
import com.bobinapp.data.fotos.FotosAnimales
import com.bobinapp.data.local.BobinappDatabase
import com.bobinapp.data.remote.ApiProvider
import com.bobinapp.data.remote.PushRequest
import com.bobinapp.data.remote.aDto
import com.bobinapp.data.remote.aEntidad
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ResultadoSync {
    data object SinFinca : ResultadoSync
    data class Ok(val subidos: Int, val recibidos: Int, val conflictos: Int, val fotosSubidas: Int, val fotosBajadas: Int) : ResultadoSync
}

/**
 * Protocolo de sincronización (ver docs/ARQUITECTURA.md):
 *  1. PUSH: sube por lotes todo lo marcado PENDIENTE.
 *  2. FOTOS ↑: sube las fotos tomadas en este teléfono.
 *  3. PULL: descarga lo que cambió en el servidor desde el último cursor, página por página.
 *  4. FOTOS ↓: descarga las fotos cuya versión remota es más nueva que la local.
 *  Conflictos: gana el cambio con actualizadoEn más reciente (last-writer-wins), ver [ReglasSync].
 */
class SyncRepository(
    private val db: BobinappDatabase,
    private val apiProvider: ApiProvider,
    private val ajustes: AjustesStore,
    private val fotos: FotosAnimales,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val animales = db.animalDao()
    private val eventos = db.eventoDao()

    suspend fun sincronizar(): ResultadoSync {
        if (!ajustes.actual.conectadoANube) return ResultadoSync.SinFinca
        val api = apiProvider.api()

        // 1. PUSH por lotes
        var subidos = 0
        var conflictos = 0
        while (true) {
            val a = animales.pendientes(LOTE_ANIMALES)
            val e = eventos.pendientes(LOTE_EVENTOS)
            if (a.isEmpty() && e.isEmpty()) break
            val r = api.push(PushRequest(a.map { it.aDto() }, e.map { it.aDto() }))
            // Aplicado o rechazado por conflicto: en ambos casos sale de la cola.
            // Si hubo conflicto, el servidor tiene una versión más nueva que llegará en el PULL.
            db.withTransaction {
                a.forEach { animales.marcarSincronizado(it.id, it.actualizadoEn) }
                e.forEach { eventos.marcarSincronizado(it.id, it.actualizadoEn) }
            }
            subidos += r.aplicados.animales + r.aplicados.eventos
            conflictos += r.conflictos.animales.size + r.conflictos.eventos.size
            if (a.size < LOTE_ANIMALES && e.size < LOTE_EVENTOS) break
        }

        // 2. Fotos por subir (después del push, para que el animal ya exista en el servidor)
        var fotosSubidas = 0
        for (a in animales.fotosPorSubir()) {
            val version = a.fotoActualizadaEn ?: continue
            val bytes = fotos.leerBytes(a.id)
            if (bytes == null) { animales.marcarFotoSubida(a.id, version); continue }
            val r = api.subirFoto(a.id, version, bytes.toRequestBody(JPEG))
            when (r.code()) {
                204 -> { animales.marcarFotoSubida(a.id, version); fotosSubidas++ }
                409 -> animales.marcarFotoSubida(a.id, version) // el servidor tiene una más nueva
                // 404: el animal aún no llega al servidor; se reintenta en la próxima sincronización
            }
        }

        // 3. PULL paginado
        var recibidos = 0
        var cursor = ajustes.actual.cursor
        do {
            val pagina = api.cambios(desde = cursor)
            db.withTransaction {
                for (dto in pagina.animales) {
                    ReglasSync.fusionarAnimal(animales.obtener(dto.id), dto.aEntidad())?.let {
                        animales.upsert(it); recibidos++
                    }
                }
                for (dto in pagina.eventos) {
                    val local = eventos.obtener(dto.id)
                    if (ReglasSync.debeAplicarRemoto(local?.actualizadoEn, dto.actualizadoEn)) {
                        eventos.upsert(dto.aEntidad()); recibidos++
                    }
                }
            }
            cursor = pagina.cursor
            ajustes.guardarCursor(cursor)
        } while (pagina.hayMas)

        // 4. Fotos por descargar
        var fotosBajadas = 0
        for (a in animales.fotosPorDescargar()) {
            val r = api.bajarFoto(a.id)
            val cuerpo = r.body()
            if (r.isSuccessful && cuerpo != null) {
                fotos.escribir(a.id, cuerpo.use { it.bytes() })
                val version = r.headers()["X-Foto-Actualizada-En"]?.toLongOrNull() ?: a.fotoActualizadaEn ?: continue
                animales.marcarFotoLocal(a.id, maxOf(version, a.fotoActualizadaEn ?: 0L))
                fotosBajadas++
            }
        }

        ajustes.marcarSincronizado(reloj())
        return ResultadoSync.Ok(subidos, recibidos, conflictos, fotosSubidas, fotosBajadas)
    }

    companion object {
        private const val LOTE_ANIMALES = 200
        private const val LOTE_EVENTOS = 1000
        private val JPEG = "image/jpeg".toMediaType()
    }
}
