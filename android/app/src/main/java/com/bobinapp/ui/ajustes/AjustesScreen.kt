package com.bobinapp.ui.ajustes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    fun cambiarTema(tema: Tema) = ajustes.cambiarTema(tema)
    fun revisarAlertas() { WorkScheduler.revisarAlertasAhora(contexto); mensaje = "Revisión de alertas en marcha." }
}

@Composable
fun AjustesScreen(onPedirPermisoNotificaciones: () -> Unit) {
    val vm = appViewModel { c, _ -> AjustesViewModel(c.contexto, c.hato, c.ajustes, c.apiProvider, c.conectividad) }
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
        Seccion("Alertas") {
            if (!NotificationHelper.puedeNotificar(contexto)) {
                Text("Las notificaciones están desactivadas.")
                Button(onClick = onPedirPermisoNotificaciones) { Text("Permitir notificaciones") }
            } else {
                Text("Las alertas urgentes llegan como notificación. Se revisan cada 6 horas en segundo plano.")
            }
            OutlinedButton(onClick = vm::revisarAlertas) { Text("Revisar alertas ahora") }
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
