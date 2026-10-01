package com.bobinapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.bobinapp.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Apariencia elegida por el usuario. */
enum class Tema(val etiqueta: String) { SISTEMA("Automático"), CLARO("Claro"), OSCURO("Oscuro") }

data class Ajustes(
    val servidorUrl: String,
    val token: String?,
    val fincaNombre: String?,
    val cursor: Long,
    val ultimaSync: Long?,
    val descartadas: Set<String>,
    val tema: Tema = Tema.SISTEMA,
) {
    val conectadoANube: Boolean get() = token != null
}

/** Preferencias pequeñas de la app, expuestas como StateFlow para que la UI reaccione. */
class AjustesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bobinapp_ajustes", Context.MODE_PRIVATE)
    private val _estado = MutableStateFlow(leer())
    val estado: StateFlow<Ajustes> = _estado.asStateFlow()
    val actual: Ajustes get() = _estado.value

    private fun leer() = Ajustes(
        servidorUrl = prefs.getString(K_URL, null) ?: BuildConfig.API_URL_POR_DEFECTO,
        token = prefs.getString(K_TOKEN, null),
        fincaNombre = prefs.getString(K_FINCA, null),
        cursor = prefs.getLong(K_CURSOR, 0L),
        ultimaSync = prefs.getLong(K_ULTIMA, 0L).takeIf { it > 0 },
        descartadas = prefs.getStringSet(K_DESCARTADAS, emptySet())?.toSet() ?: emptySet(),
        tema = prefs.getString(K_TEMA, null)?.let { n -> Tema.entries.firstOrNull { it.name == n } } ?: Tema.SISTEMA,
    )

    @Synchronized
    private fun editar(bloque: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(bloque).apply()
        _estado.value = leer()
    }

    /** Devuelve false si la URL no es válida. */
    fun guardarServidor(url: String): Boolean {
        val limpia = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        if (limpia.toHttpUrlOrNull() == null) return false
        editar { putString(K_URL, limpia); putLong(K_CURSOR, 0L) }
        return true
    }

    fun conectarFinca(nombre: String, token: String) = editar {
        putString(K_FINCA, nombre); putString(K_TOKEN, token); putLong(K_CURSOR, 0L); remove(K_ULTIMA)
    }

    fun desconectar() = editar { remove(K_TOKEN); remove(K_FINCA); putLong(K_CURSOR, 0L); remove(K_ULTIMA) }
    fun guardarCursor(cursor: Long) = editar { putLong(K_CURSOR, cursor) }
    fun marcarSincronizado(momento: Long) = editar { putLong(K_ULTIMA, momento) }
    fun descartarAlerta(clave: String) = editar { putStringSet(K_DESCARTADAS, actual.descartadas + clave) }
    fun restaurarAlertas() = editar { remove(K_DESCARTADAS) }
    fun cambiarTema(tema: Tema) = editar { putString(K_TEMA, tema.name) }

    var demoSembrado: Boolean
        get() = prefs.getBoolean(K_DEMO, false)
        set(v) = prefs.edit().putBoolean(K_DEMO, v).apply()

    /** Claves de alertas ya notificadas, para no repetir la misma notificación. */
    var alertasNotificadas: Set<String>
        get() = prefs.getStringSet(K_NOTIFICADAS, emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet(K_NOTIFICADAS, v).apply()

    private companion object {
        const val K_URL = "servidorUrl"
        const val K_TOKEN = "token"
        const val K_FINCA = "fincaNombre"
        const val K_CURSOR = "cursor"
        const val K_ULTIMA = "ultimaSync"
        const val K_DESCARTADAS = "alertasDescartadas"
        const val K_NOTIFICADAS = "alertasNotificadas"
        const val K_DEMO = "demoSembrado"
        const val K_TEMA = "tema"
    }
}
