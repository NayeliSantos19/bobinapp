package com.bobinapp.ui.ajustes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import com.bobinapp.data.analitica.Analitica
import com.bobinapp.data.seguridad.Biometria
import com.bobinapp.domain.PoliticaNotificaciones
import com.bobinapp.ui.seguridad.LocalPedirIdentidad
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.bobinapp.data.ConnectivityObserver
import com.bobinapp.data.remote.ApiProvider
import com.bobinapp.data.remote.CrearFincaRequest
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.Tema
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.ui.components.Punto
import com.bobinapp.ui.components.Seccion
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.theme.LocalSemanticos
import com.bobinapp.work.NotificationHelper
import com.bobinapp.work.WorkScheduler
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Estado de sincronización que se muestra en la barra superior y en Ajustes. */
data class EstadoSync(
    val enLinea: Boolean = true,
    val pendientes: Int = 0,
    val sincronizando: Boolean = false,
    val conectado: Boolean = false,
    val ultimaSync: Long? = null,
) {
    val texto: String
        get() = when {
            !conectado -> "Solo en este dispositivo"
            !enLinea -> if (pendientes > 0) "Sin señal · $pendientes en cola" else "Sin señal"
            sincronizando -> "Sincronizando…"
            pendientes > 0 -> "$pendientes por subir"
            else -> "Sincronizado"
        }
}

fun observarEstadoSync(contexto: Context, hato: HatoRepository, ajustes: AjustesStore, red: ConnectivityObserver): Flow<EstadoSync> {
    val trabajando = WorkScheduler.observarSync(contexto).map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
    return combine(red.enLinea, hato.observarPendientes(), trabajando, ajustes.estado) { online, pend, corriendo, aj ->
        EstadoSync(online, pend, corriendo, aj.conectadoANube, aj.ultimaSync)
    }
}

class AjustesViewModel(
    private val contexto: Context,
    private val hato: HatoRepository,
    private val ajustes: AjustesStore,
    private val api: ApiProvider,
    red: ConnectivityObserver,
    private val analitica: Analitica,
) : ViewModel() {
    val ajustesEstado = ajustes.estado
    val sync = observarEstadoSync(contexto, hato, ajustes, red)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSync())
    var mensaje by mutableStateOf<String?>(null); private set
    var ocupado by mutableStateOf(false); private set

    fun guardarServidor(url: String) {
        mensaje = if (ajustes.guardarServidor(url)) "Servidor guardado." else "La dirección no es válida. Ejemplo: http://192.168.1.20:3000/"
    }

    fun crearFinca(nombre: String) {
        if (nombre.isBlank()) { mensaje = "Escribe el nombre de la finca."; return }
        ocupado = true
        viewModelScope.launch {
            mensaje = try {
                val r = api.api().crearFinca(CrearFincaRequest(nombre.trim()))
                ajustes.conectarFinca(r.finca.nombre, r.token)
                hato.marcarTodoPendiente()
                "Finca «${r.finca.nombre}» conectada. Subiendo tus datos…"
            } catch (e: IOException) {
                "No se pudo contactar al servidor. Revisa la dirección y que la API esté corriendo."
            } catch (e: HttpException) {
                "El servidor respondió con error ${e.code()}."
            } finally {
                ocupado = false
            }
        }
    }

    fun sincronizarAhora() { WorkScheduler.sincronizarAhora(contexto); mensaje = "Sincronización en cola. Corre en cuanto haya señal." }
    fun desconectar() { ajustes.desconectar(); mensaje = "Finca desconectada. Tus datos siguen en el teléfono." }
    fun borrarDemo() = viewModelScope.launch { hato.borrarDemo(); mensaje = "Datos de ejemplo borrados." }
    fun cargarDemo() = viewModelScope.launch { hato.cargarDemo(); mensaje = "Datos de ejemplo cargados." }
    fun cambiarTema(tema: Tema) { ajustes.cambiarTema(tema); analitica.accion("tema", mapOf("modo" to tema.name.lowercase())) }
    fun cambiarBloqueo(activo: Boolean) {
        ajustes.cambiarBloqueo(activo)
        analitica.accion("bloqueo", mapOf("activo" to activo))
        mensaje = if (activo) "Bloqueo activado. Se pedirá al abrir la app y tras 1 minuto fuera." else "Bloqueo desactivado."
    }
    fun cambiarNotificacion(categoria: String, activa: Boolean) = ajustes.cambiarNotificacion(categoria, activa)
    fun cambiarSilencioNocturno(activo: Boolean) = ajustes.cambiarSilencioNocturno(activo)
    fun cambiarEstadisticas(activas: Boolean) {
        if (!activas) analitica.borrarCola()
        ajustes.cambiarEstadisticas(activas)
    }
    fun borrarMisEstadisticas() = viewModelScope.launch {
        mensaje = if (analitica.borrarMisDatos()) "Se borraron tus estadísticas del servidor." else
            "No se pudo contactar al servidor. Lo pendiente en el teléfono sí se borró."
    }
    fun revisarAlertas() { WorkScheduler.revisarAlertasAhora(contexto); mensaje = "Revisión de alertas en marcha." }
}

@Composable
fun AjustesScreen(onPedirPermisoNotificaciones: () -> Unit, onAbrirAyuda: () -> Unit) {
    val vm = appViewModel { c, _ -> AjustesViewModel(c.contexto, c.hato, c.ajustes, c.apiProvider, c.conectividad, c.analitica) }
    val pedirIdentidad = LocalPedirIdentidad.current
    val aj by vm.ajustesEstado.collectAsStateWithLifecycle()
    val s by vm.sync.collectAsStateWithLifecycle()
    val sem = LocalSemanticos.current
    val contexto = LocalContext.current
    var url by rememberSaveable(aj.servidorUrl) { mutableStateOf(aj.servidorUrl) }
    var finca by rememberSaveable { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        vm.mensaje?.let { Text(it, fontWeight = FontWeight.SemiBold) }
        Seccion("Apariencia") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tema.entries.forEach { t ->
                    FilterChip(
                        selected = aj.tema == t,
                        onClick = { vm.cambiarTema(t) },
                        label = { Text(t.etiqueta) },
                    )
                }
            }
            Text(
                if (aj.tema == Tema.SISTEMA) "Sigue el modo claro u oscuro de tu teléfono." else "El modo oscuro ahorra batería en pantallas OLED y cansa menos la vista de noche.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Seccion("Seguridad") {
            val disponible = remember { Biometria.disponibilidad(contexto) }
            FilaInterruptor(
                titulo = "Bloquear con huella o rostro",
                detalle = when (disponible) {
                    Biometria.Disponibilidad.LISTA -> "Pide tu huella, rostro o PIN al abrir la app."
                    Biometria.Disponibilidad.SIN_CONFIGURAR -> "Primero configura una huella o un PIN en los ajustes del teléfono."
                    Biometria.Disponibilidad.NO_SOPORTADA -> "Este teléfono no tiene huella ni bloqueo de pantalla."
                },
                activo = aj.bloqueo,
                habilitado = disponible == Biometria.Disponibilidad.LISTA || aj.bloqueo,
                // Para activarlo o quitarlo hay que confirmar que es el dueño del teléfono.
                onCambio = { nuevo ->
                    // Sin huella ni PIN en el teléfono no hay con qué confirmar: solo se permite quitarlo.
                    if (disponible != Biometria.Disponibilidad.LISTA) { if (!nuevo) vm.cambiarBloqueo(false) }
                    else pedirIdentidad(if (nuevo) "Activar bloqueo" else "Quitar bloqueo") { vm.cambiarBloqueo(nuevo) }
                },
            )
            Text(
                "El token de tu finca se guarda cifrado con una llave del Android Keystore y la app solo se conecta al servidor por HTTPS.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Seccion("Sincronización") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Punto(if (!s.enLinea) sem.carne else if (s.pendientes > 0 || s.sincronizando) sem.hoy else sem.doble)
                Text(s.texto, fontWeight = FontWeight.Bold)
            }
            Text("Cada registro se guarda primero en el teléfono (Room/SQLite) y entra a una cola. WorkManager la sube sola al recuperar señal, aunque la app esté cerrada.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Última sincronización: " + (s.ultimaSync?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "nunca"))
            if (aj.conectadoANube) {
                Text("Finca: ${aj.fincaNombre}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::sincronizarAhora) { Text("Sincronizar ahora") }
                    OutlinedButton(onClick = vm::desconectar) { Text("Desconectar") }
                }
            }
        }
        Seccion("Servidor") {
            OutlinedTextField(url, { url = it }, label = { Text("Dirección de la API") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Emulador: http://10.0.2.2:3000/ · Teléfono real: la IP de tu computadora en la misma red wifi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { vm.guardarServidor(url) }) { Text("Guardar dirección") }
            if (!aj.conectadoANube) {
                OutlinedTextField(finca, { finca = it }, label = { Text("Nombre de tu finca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.crearFinca(finca) }, enabled = !vm.ocupado) { Text(if (vm.ocupado) "Conectando…" else "Crear finca y conectar") }
            }
        }
        Seccion("Notificaciones") {
            if (!NotificationHelper.puedeNotificar(contexto)) {
                Text("Las notificaciones están desactivadas para Bobinapp.")
                Button(onClick = onPedirPermisoNotificaciones) { Text("Permitir notificaciones") }
            } else {
                Text("Solo avisan lo vencido o para hoy, y nunca dos veces por lo mismo. Se revisan cada 6 horas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PoliticaNotificaciones.CATEGORIAS.forEach { cat ->
                FilaInterruptor(
                    titulo = cat,
                    detalle = when (cat) {
                        "Reproducción" -> "Celos, inseminación, preñez, partos y secado"
                        "Salud" -> "Dosis de vacunas y tratamientos, retiro de leche"
                        else -> "Destetes y pesajes pendientes"
                    },
                    activo = cat !in aj.notifApagadas,
                    onCambio = { vm.cambiarNotificacion(cat, it) },
                )
            }
            FilaInterruptor(
                titulo = "Silencio de noche",
                detalle = "Nada entre las 9 p. m. y las 6 a. m.; los avisos llegan en la mañana.",
                activo = aj.silencioNocturno,
                onCambio = vm::cambiarSilencioNocturno,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::revisarAlertas) { Text("Revisar ahora") }
                TextButton(onClick = { NotificationHelper.abrirAjustesDelSistema(contexto) }) { Text("Ajustes de Android") }
            }
        }
        Seccion("Privacidad") {
            FilaInterruptor(
                titulo = "Enviar estadísticas anónimas",
                detalle = "Qué pantallas se usan, cuánto tarda en abrir y qué errores ocurren. Nunca datos de tus animales ni de tu finca.",
                activo = aj.estadisticas,
                onCambio = vm::cambiarEstadisticas,
            )
            TextButton(onClick = { vm.borrarMisEstadisticas() }) { Text("Borrar mis estadísticas del servidor") }
        }
        Seccion("Ayuda") {
            Text("Guía rápida, preguntas frecuentes, privacidad y reporte de problemas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAbrirAyuda) { Text("Abrir ayuda y soporte") }
        }
        Seccion("Datos de ejemplo") {
            Text("Los animales de ejemplo nunca se suben a la nube.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.borrarDemo() }) { Text("Borrar ejemplo") }
                OutlinedButton(onClick = { vm.cargarDemo() }) { Text("Cargar ejemplo") }
            }
        }
    }
}

/** Fila con título, explicación y un interruptor a la derecha. Toda la fila es tocable. */
@Composable
private fun FilaInterruptor(
    titulo: String,
    detalle: String,
    activo: Boolean,
    onCambio: (Boolean) -> Unit,
    habilitado: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = activo, enabled = habilitado, role = Role.Switch, onValueChange = onCambio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // onCheckedChange = null: el toque lo maneja la fila completa (mejor para accesibilidad).
        Switch(checked = activo, onCheckedChange = null, enabled = habilitado)
    }
}
