@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.navegacion

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bobinapp.BobinappApp
import com.bobinapp.R
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.Severidad
import com.bobinapp.ui.ajustes.AjustesScreen
import com.bobinapp.ui.ajustes.EstadoSync
import com.bobinapp.ui.ajustes.observarEstadoSync
import com.bobinapp.ui.alertas.AlertasScreen
import com.bobinapp.ui.ayuda.AyudaScreen
import com.bobinapp.ui.components.Punto
import com.bobinapp.ui.escaner.EscanerScreen
import com.bobinapp.ui.formularios.AnimalFormScreen
import com.bobinapp.ui.formularios.EventoFormScreen
import com.bobinapp.ui.formularios.PartoFormScreen
import com.bobinapp.ui.hato.AnimalDetalleScreen
import com.bobinapp.ui.hato.HatoScreen
import com.bobinapp.ui.panel.PanelScreen
import com.bobinapp.ui.razas.RazaDetalleScreen
import com.bobinapp.ui.razas.RazasScreen
import com.bobinapp.ui.theme.LocalSemanticos
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

object Rutas {
    const val PANEL = "panel"
    const val HATO = "hato"
    const val RAZAS = "razas"
    const val ALERTAS = "alertas"
    const val AJUSTES = "ajustes"
    const val ESCANER = "escaner"
    const val AYUDA = "ayuda"
    const val ANIMAL = "animal/{id}"
    const val ANIMAL_FORM = "animal_form?id={id}&raza={raza}"
    const val EVENTO = "evento?ids={ids}&tipo={tipo}&producto={producto}&dosis={dosis}"
    const val PARTO = "parto/{madreId}"
    const val RAZA = "raza/{id}"

    fun animal(id: String) = "animal/$id"
    fun animalForm(id: String? = null, raza: String? = null) = "animal_form?id=${id.orEmpty()}&raza=${raza.orEmpty()}"
    fun evento(ids: List<String>, tipo: TipoEvento? = null, producto: String? = null, dosis: String? = null) =
        "evento?ids=${ids.joinToString(",")}&tipo=${tipo?.name.orEmpty()}&producto=${Uri.encode(producto.orEmpty())}&dosis=${Uri.encode(dosis.orEmpty())}"
    fun parto(madreId: String) = "parto/$madreId"
    fun raza(id: String) = "raza/$id"
}

private data class Pestana(val ruta: String, val etiqueta: String, val icono: ImageVector)

private val PESTANAS = listOf(
    Pestana(Rutas.PANEL, "Panel", Icons.Default.Home),
    Pestana(Rutas.HATO, "Mi hato", Icons.AutoMirrored.Filled.List),
    Pestana(Rutas.RAZAS, "Razas", Icons.Default.Star),
    Pestana(Rutas.ALERTAS, "Alertas", Icons.Default.Notifications),
    Pestana(Rutas.AJUSTES, "Ajustes", Icons.Default.Settings),
)

private fun argTexto(nombre: String) = navArgument(nombre) { type = NavType.StringType; defaultValue = "" }

@Composable
fun BobinappNavHost(destinoInicial: String?, onDestinoConsumido: () -> Unit, onPedirPermisoNotificaciones: () -> Unit) {
    val nav = rememberNavController()
    val contexto = LocalContext.current
    val c = (contexto.applicationContext as BobinappApp).container
    val sync by remember { observarEstadoSync(contexto, c.hato, c.ajustes, c.conectividad) }
        .collectAsStateWithLifecycle(EstadoSync())
    val urgentes by remember {
        combine(c.hato.observarHato(), c.ajustes.estado) { h, aj ->
            AlertEngine.calcular(h, LocalDate.now()).count {
                it.clave !in aj.descartadas && (it.severidad == Severidad.VENCIDA || it.severidad == Severidad.HOY)
            }
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(0)

    LaunchedEffect(destinoInicial) {
        if (destinoInicial != null) { irAPestana(nav, destinoInicial); onDestinoConsumido() }
    }

    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val esPestana = PESTANAS.any { it.ruta == rutaActual }

    // Analítica anónima: solo el nombre de la pantalla ("animal", no el id del animal).
    LaunchedEffect(rutaActual) {
        rutaActual?.let { r -> withContext(Dispatchers.IO) { c.analitica.pantalla(r.substringBefore('/').substringBefore('?')) } }
    }

    Scaffold(
        topBar = {
            if (esPestana) TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(R.drawable.ic_logo_vaca_ui), contentDescription = "Bobinapp", modifier = Modifier.size(30.dp))
                        Text(PESTANAS.first { it.ruta == rutaActual }.etiqueta, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IndicadorSync(sync) { irAPestana(nav, Rutas.AJUSTES) }
                    IconButton(onClick = { nav.navigate(Rutas.AYUDA) }) { Icon(Icons.Outlined.Info, contentDescription = "Ayuda") }
                },
            )
        },
        bottomBar = {
            if (esPestana) NavigationBar {
                PESTANAS.forEach { p ->
                    NavigationBarItem(
                        selected = rutaActual == p.ruta,
                        onClick = { irAPestana(nav, p.ruta) },
                        icon = {
                            if (p.ruta == Rutas.ALERTAS && urgentes > 0) BadgedBox(badge = { Badge { Text("$urgentes") } }) { Icon(p.icono, null) }
                            else Icon(p.icono, null)
                        },
                        label = { Text(p.etiqueta) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Rutas.PANEL, modifier = Modifier.padding(padding)) {
            composable(Rutas.PANEL) {
                PanelScreen(onAbrirAnimal = { nav.navigate(Rutas.animal(it)) }, onVerAlertas = { irAPestana(nav, Rutas.ALERTAS) })
            }
            composable(Rutas.HATO) {
                HatoScreen(onAbrirAnimal = { nav.navigate(Rutas.animal(it)) }, onNuevoAnimal = { nav.navigate(Rutas.animalForm()) })
            }
            composable(Rutas.RAZAS) {
                RazasScreen(onAbrirRaza = { nav.navigate(Rutas.raza(it)) }, onEscanear = { nav.navigate(Rutas.ESCANER) })
            }
            composable(Rutas.ALERTAS) {
                AlertasScreen(
                    onAbrirAnimal = { nav.navigate(Rutas.animal(it)) },
                    onRegistrar = { ids, tipo, producto, dosis -> nav.navigate(Rutas.evento(ids, tipo, producto, dosis)) },
                    onRegistrarParto = { nav.navigate(Rutas.parto(it)) },
                )
            }
            composable(Rutas.AJUSTES) { AjustesScreen(onPedirPermisoNotificaciones, onAbrirAyuda = { nav.navigate(Rutas.AYUDA) }) }
            composable(Rutas.AYUDA) { AyudaScreen(onAtras = { nav.popBackStack() }) }
            composable(Rutas.ESCANER) {
                EscanerScreen(onAtras = { nav.popBackStack() }, onVerRaza = { nav.navigate(Rutas.raza(it)) },
                    onAgregarAlHato = { nav.navigate(Rutas.animalForm(raza = it)) })
            }
            composable(Rutas.ANIMAL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                AnimalDetalleScreen(
                    onAtras = { nav.popBackStack() },
                    onAbrirAnimal = { nav.navigate(Rutas.animal(it)) },
                    onRegistrarEvento = { ids, tipo -> nav.navigate(Rutas.evento(ids, tipo)) },
                    onRegistrarParto = { nav.navigate(Rutas.parto(it)) },
                    onEditar = { nav.navigate(Rutas.animalForm(id = it)) },
                    onVerRaza = { nav.navigate(Rutas.raza(it)) },
                    onDescartarAlerta = { c.ajustes.descartarAlerta(it) },
                )
            }
            composable(Rutas.ANIMAL_FORM, arguments = listOf(argTexto("id"), argTexto("raza"))) {
                AnimalFormScreen(onAtras = { nav.popBackStack() }, onGuardado = { id ->
                    nav.popBackStack()
                    if (nav.currentDestination?.route != Rutas.ANIMAL) nav.navigate(Rutas.animal(id))
                })
            }
            composable(Rutas.EVENTO, arguments = listOf(argTexto("ids"), argTexto("tipo"), argTexto("producto"), argTexto("dosis"))) {
                EventoFormScreen(onAtras = { nav.popBackStack() })
            }
            composable(Rutas.PARTO, arguments = listOf(navArgument("madreId") { type = NavType.StringType })) {
                PartoFormScreen(onAtras = { nav.popBackStack() }, onGuardado = { id -> nav.popBackStack(); nav.navigate(Rutas.animal(id)) })
            }
            composable(Rutas.RAZA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                RazaDetalleScreen(entry.arguments?.getString("id").orEmpty(), onAtras = { nav.popBackStack() },
                    onAgregarAlHato = { nav.navigate(Rutas.animalForm(raza = it)) })
            }
        }
    }
}

private fun irAPestana(nav: NavHostController, ruta: String) {
    nav.navigate(ruta) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun IndicadorSync(s: EstadoSync, onClick: () -> Unit) {
    val sem = LocalSemanticos.current
    AssistChip(
        onClick = onClick,
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Punto(
                    when {
                        !s.conectado -> sem.aviso
                        !s.enLinea -> sem.carne
                        s.sincronizando || s.pendientes > 0 -> sem.hoy
                        else -> sem.doble
                    }
                )
                Text(s.texto)
            }
        },
        modifier = Modifier.padding(end = 4.dp),
    )
}
