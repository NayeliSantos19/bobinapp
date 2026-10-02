package com.bobinapp.data.analitica

import android.content.Context
import android.os.Build
import com.bobinapp.BuildConfig
import com.bobinapp.data.remote.ApiProvider
import com.bobinapp.data.remote.EventoTelemetriaDto
import com.bobinapp.data.remote.LoteTelemetriaRequest
import com.bobinapp.data.repository.AjustesStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Analítica de uso propia y anónima (sin Google ni terceros).
 *
 * Qué se registra: pantallas abiertas, tiempo de arranque, acciones clave (reporte PDF, escaneo…)
 * y el tipo y lugar de los errores. Nunca nombres, aretes, notas, fotos ni el token de la finca.
 *
 * Los eventos se guardan en un archivo (una línea JSON por evento) y TelemetriaWorker los envía en lotes
 * cuando hay red. Si el usuario desactiva las estadísticas en Ajustes, no se registra nada y se borra la cola.
 */
class Analitica(context: Context, private val ajustes: AjustesStore, private val api: ApiProvider) {
    private val archivo = File(context.filesDir, "telemetria.jsonl")
    private val json = Json { encodeDefaults = false }

    /** Última pantalla abierta: da contexto a los errores. */
    @Volatile var pantallaActual: String? = null

    fun pantalla(nombre: String) {
        pantallaActual = nombre
        registrar("pantalla", nombre)
    }

    /** Acción importante del usuario (reporte PDF, escaneo, registro de evento…). */
    fun accion(nombre: String, datos: Map<String, Any> = emptyMap()) = registrar(nombre, pantallaActual, datos)

    @Synchronized
    fun registrar(evento: String, pantalla: String? = null, datos: Map<String, Any> = emptyMap()) {
        if (!ajustes.actual.estadisticas) return
        val dto = EventoTelemetriaDto(
            evento = evento,
            pantalla = pantalla,
            datos = datos.mapValues { (_, v) ->
                when (v) {
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString().take(200))
                }
            },
            ocurridoEn = System.currentTimeMillis(),
        )
        try {
            archivo.appendText(json.encodeToString(dto) + "\n")
            recortarSiCrecio()
        } catch (_: Exception) {
            // La analítica nunca debe romper la app.
        }
    }

    /**
     * Registra un error que no se atrapó. Se llama desde el manejador global antes de que la app se cierre,
     * así que escribe en el mismo hilo. Solo guarda el tipo de excepción y la primera línea de nuestro código.
     */
    fun registrarCierreInesperado(error: Throwable, pantalla: String? = pantallaActual) {
        val marco = error.stackTrace.firstOrNull { it.className.startsWith("com.bobinapp") } ?: error.stackTrace.firstOrNull()
        val lugar = marco?.let { "${it.fileName}:${it.lineNumber}" } ?: "desconocido"
        registrar("error", pantalla, mapOf("tipo" to (error::class.simpleName ?: "Error"), "lugar" to lugar, "fatal" to true))
    }

    /** Envía lo pendiente. Devuelve true si ya no queda nada por enviar. */
    suspend fun enviar(): Boolean = withContext(Dispatchers.IO) {
        if (!ajustes.actual.estadisticas) { borrarCola(); return@withContext true }
        val lineas = synchronized(this@Analitica) { if (archivo.exists()) archivo.readLines() else emptyList() }
        if (lineas.isEmpty()) return@withContext true
        val eventos = lineas.mapNotNull { runCatching { json.decodeFromString<EventoTelemetriaDto>(it) }.getOrNull() }
        for (lote in eventos.chunked(LOTE)) {
            val r = api.api().telemetria(
                LoteTelemetriaRequest(ajustes.instalacionId, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, lote),
            )
            // 400 = el servidor rechazó el lote (formato viejo); no tiene sentido reintentarlo para siempre.
            if (!r.isSuccessful && r.code() != 400) return@withContext false
        }
        synchronized(this@Analitica) {
            // Conserva lo que se haya registrado mientras se enviaba.
            val ahora = if (archivo.exists()) archivo.readLines() else emptyList()
            archivo.writeText(ahora.drop(lineas.size).joinToString("") { it + "\n" })
        }
        true
    }

    @Synchronized
    fun borrarCola() { archivo.delete() }

    /** Borra en el servidor lo enviado por esta instalación y empieza con un id nuevo. */
    suspend fun borrarMisDatos(): Boolean = withContext(Dispatchers.IO) {
        borrarCola()
        val ok = runCatching { api.api().borrarTelemetria(ajustes.instalacionId).isSuccessful }.getOrDefault(false)
        if (ok) ajustes.reiniciarInstalacionId()
        ok
    }

    private fun recortarSiCrecio() {
        if (archivo.length() < 200_000) return
        val lineas = archivo.readLines()
        archivo.writeText(lineas.takeLast(MAX_EN_COLA).joinToString("") { it + "\n" })
    }

    private companion object {
        const val LOTE = 200
        const val MAX_EN_COLA = 1_000
    }
}
