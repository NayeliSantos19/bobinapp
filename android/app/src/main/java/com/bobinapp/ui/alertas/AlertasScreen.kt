@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.alertas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.Alerta
import com.bobinapp.domain.Severidad
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.components.diasRelativo
import com.bobinapp.ui.theme.LocalSemanticos
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class AlertasEstado(
    val alertas: List<Alerta> = emptyList(),
    val conteo: Map<Severidad, Int> = emptyMap(),
    val nombres: Map<String, AnimalEntity> = emptyMap(),
    val descartadas: Int = 0,
    val filtro: Severidad? = null,
)

class AlertasViewModel(hato: HatoRepository, private val ajustes: AjustesStore) : ViewModel() {
    private val filtro = MutableStateFlow<Severidad?>(null)

    val estado = combine(hato.observarHato(), ajustes.estado, filtro) { lista, aj, f ->
        val vigentes = AlertEngine.calcular(lista, LocalDate.now()).filter { it.clave !in aj.descartadas }
        AlertasEstado(
            alertas = if (f == null) vigentes else vigentes.filter { it.severidad == f },
            conteo = vigentes.groupingBy { it.severidad }.eachCount(),
            nombres = lista.associate { it.animal.id to it.animal },
            descartadas = aj.descartadas.size,
            filtro = f,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertasEstado())

    fun filtrar(s: Severidad?) { filtro.value = s }
    fun descartar(clave: String) = ajustes.descartarAlerta(clave)
    fun restaurar() = ajustes.restaurarAlertas()
}

@Composable
fun AlertasScreen(
    onAbrirAnimal: (String) -> Unit,
    onRegistrar: (List<String>, TipoEvento, String?, String?) -> Unit,
    onRegistrarParto: (String) -> Unit,
) {
    val vm = appViewModel { c, _ -> AlertasViewModel(c.hato, c.ajustes) }
    val e by vm.estado.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Calculadas en el dispositivo y revisadas en segundo plano cada 6 horas. Las urgentes llegan como notificación.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = e.filtro == null, onClick = { vm.filtrar(null) }, label = { Text("Todas · ${e.conteo.values.sum()}") })
                Severidad.entries.forEach { s ->
                    FilterChip(selected = e.filtro == s, onClick = { vm.filtrar(s) }, label = { Text("${s.etiqueta} · ${e.conteo[s] ?: 0}") })
                }
            }
        }
        if (e.alertas.isEmpty()) item { Text("No hay alertas en esta categoría.", modifier = Modifier.padding(vertical = 24.dp)) }
        items(e.alertas, key = { it.clave }) { al ->
            TarjetaAlerta(
                alerta = al,
                nombres = e.nombres,
                onAbrirAnimal = onAbrirAnimal,
                onRegistrar = al.accion?.let { t ->
                    {
                        if (t == TipoEvento.PARTO) onRegistrarParto(al.animalIds.first())
                        else onRegistrar(al.animalIds, t, al.productoSugerido, al.dosisSugerida)
                    }
                },
                onDescartar = { vm.descartar(al.clave) },
            )
        }
        if (e.descartadas > 0) item {
            TextButton(onClick = vm::restaurar) { Text("Restaurar ${e.descartadas} alertas descartadas") }
        }
    }
}

@Composable
fun colorSeveridad(s: Severidad): Color {
    val sem = LocalSemanticos.current
    return when (s) {
        Severidad.VENCIDA -> sem.vencida
        Severidad.HOY -> sem.hoy
        Severidad.PROXIMA -> sem.proxima
        Severidad.AVISO -> sem.aviso
    }
}

@Composable
fun TarjetaAlerta(
    alerta: Alerta,
    nombres: Map<String, AnimalEntity>,
    onAbrirAnimal: ((String) -> Unit)?,
    onRegistrar: (() -> Unit)?,
    onDescartar: () -> Unit,
) {
    val color = colorSeveridad(alerta.severidad)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${alerta.severidad.etiqueta} · ${alerta.categoria} · ${diasRelativo(alerta.dias)}".uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(alerta.titulo, fontWeight = FontWeight.Bold)
                Text(alerta.detalle, style = MaterialTheme.typography.bodySmall)
                if (onAbrirAnimal != null && alerta.animalIds.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        alerta.animalIds.mapNotNull { nombres[it] }.forEach { a ->
                            AssistChip(onClick = { onAbrirAnimal(a.id) }, label = { Text("${a.arete} ${a.nombre.orEmpty()}") })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onAbrirAnimal != null && alerta.animalIds.size == 1) {
                        OutlinedButton(onClick = { onAbrirAnimal(alerta.animalIds.first()) }) { Text("Ver animal") }
                    }
                    if (onRegistrar != null) {
                        OutlinedButton(onClick = onRegistrar) { Text(if (alerta.animalIds.size > 1) "Registrar a todos" else "Registrar") }
                    }
                    TextButton(onClick = onDescartar) { Text("Descartar") }
                }
            }
        }
    }
}
