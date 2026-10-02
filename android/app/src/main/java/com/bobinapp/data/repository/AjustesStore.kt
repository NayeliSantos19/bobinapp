package com.bobinapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.bobinapp.BuildConfig
import com.bobinapp.data.seguridad.CifradoLocal
import java.util.UUID
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
    /** Pedir huella, rostro o PIN al abrir la app. */
    val bloqueo: Boolean = false,
    /** Categorías de alertas (como las nombra AlertEngine) que NO deben notificarse. */
    val notifApagadas: Set<String> = emptySet(),
    /** No mandar notificaciones entre las 21:00 y las 6:00; se envían en la siguiente revisión. */
    val silencioNocturno: Boolean = true,
    /** Enviar estadísticas de uso anónimas (pantallas, tiempos de inicio y errores). */
    val estadisticas: Boolean = true,
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
        token = leerToken(),
        fincaNombre = prefs.getString(K_FINCA, null),
        cursor = prefs.getLong(K_CURSOR, 0L),
        ultimaSync = prefs.getLong(K_ULTIMA, 0L).takeIf { it > 0 },
        descartadas = prefs.getStringSet(K_DESCARTADAS, emptySet())?.toSet() ?: emptySet(),
        tema = prefs.getString(K_TEMA, null)?.let { n -> Tema.entries.firstOrNull { it.name == n } } ?: Tema.SISTEMA,
        bloqueo = prefs.getBoolean(K_BLOQUEO, false),
        notifApagadas = prefs.getStringSet(K_NOTIF_APAGADAS, emptySet())?.toSet() ?: emptySet(),
        silencioNocturno = prefs.getBoolean(K_SILENCIO, true),
        estadisticas = prefs.getBoolean(K_ESTADISTICAS, true),
    )

    /**
     * El token se guarda cifrado con una llave del Android Keystore (ver CifradoLocal).
     * Si viene de una versión anterior que lo guardaba en texto plano, se cifra la primera vez que se lee.
     */
    private fun leerToken(): String? {
        prefs.getString(K_TOKEN_CIFRADO, null)?.let { return CifradoLocal.descifrar(it) }
        val plano = prefs.getString(K_TOKEN, null) ?: return null
        prefs.edit().putString(K_TOKEN_CIFRADO, CifradoLocal.cifrar(plano)).remove(K_TOKEN).apply()
        return plano
    }

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
        putString(K_FINCA, nombre); putString(K_TOKEN_CIFRADO, CifradoLocal.cifrar(token)); remove(K_TOKEN)
        putLong(K_CURSOR, 0L); remove(K_ULTIMA)
    }

    fun desconectar() = editar { remove(K_TOKEN); remove(K_TOKEN_CIFRADO); remove(K_FINCA); putLong(K_CURSOR, 0L); remove(K_ULTIMA) }
    fun guardarCursor(cursor: Long) = editar { putLong(K_CURSOR, cursor) }
    fun marcarSincronizado(momento: Long) = editar { putLong(K_ULTIMA, momento) }
    fun descartarAlerta(clave: String) = editar { putStringSet(K_DESCARTADAS, actual.descartadas + clave) }
    fun restaurarAlertas() = editar { remove(K_DESCARTADAS) }
    fun cambiarTema(tema: Tema) = editar { putString(K_TEMA, tema.name) }
    fun cambiarBloqueo(activo: Boolean) = editar { putBoolean(K_BLOQUEO, activo) }
    fun cambiarNotificacion(categoria: String, activa: Boolean) = editar {
        putStringSet(K_NOTIF_APAGADAS, if (activa) actual.notifApagadas - categoria else actual.notifApagadas + categoria)
    }
    fun cambiarSilencioNocturno(activo: Boolean) = editar { putBoolean(K_SILENCIO, activo) }
    fun cambiarEstadisticas(activas: Boolean) = editar { putBoolean(K_ESTADISTICAS, activas) }

    /** Id aleatorio de esta instalación, solo para la analítica anónima. No se relaciona con la finca. */
    val instalacionId: String
        @Synchronized get() = prefs.getString(K_INSTALACION, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(K_INSTALACION, it).apply() }

    /** Genera un id nuevo: lo enviado antes ya no se puede relacionar con esta instalación. */
    @Synchronized
    fun reiniciarInstalacionId() = prefs.edit().putString(K_INSTALACION, UUID.randomUUID().toString()).apply()

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
        const val K_TOKEN_CIFRADO = "tokenCifrado"
        const val K_BLOQUEO = "bloqueo"
        const val K_NOTIF_APAGADAS = "notificacionesApagadas"
        const val K_SILENCIO = "silencioNocturno"
        const val K_ESTADISTICAS = "estadisticasAnonimas"
        const val K_INSTALACION = "instalacionId"
    }
}
