@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.hato

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.eventos
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.domain.HatoCalculos
import com.bobinapp.ui.components.AreteChip
import com.bobinapp.ui.components.Etiqueta
import com.bobinapp.ui.components.FotoAnimal
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.components.fmt
import com.bobinapp.ui.theme.LocalSemanticos
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

enum class FiltroHato(val etiqueta: String) {
    TODOS("Todos"), HEMBRAS("Vacas y novillas"), CRIAS("Terneros"), MACHOS("Toros y novillos"), BAJAS("Bajas")
}

data class FilaAnimal(
    val animal: AnimalEntity,
    val categoria: String,
    val edad: String,
    val raza: String,
    val estadoRepro: HatoCalculos.EstadoReproductivo?,
    val ultimoPesoKg: Double?,
)

class HatoViewModel(hato: HatoRepository, private val razas: RazasRepository) : ViewModel() {
    val busqueda = MutableStateFlow("")
    val filtro = MutableStateFlow(FiltroHato.TODOS)

    val filas = combine(hato.observarHato(), busqueda, filtro) { lista, q, f ->
        val hoy = LocalDate.now()
        lista.map { ae ->
            val a = ae.animal
            FilaAnimal(
                animal = a,
                categoria = HatoCalculos.categoria(a, ae.eventos, hoy),
                edad = HatoCalculos.edadTexto(a.nacimiento, hoy),
                raza = razas.buscar(a.razaId)?.nombre ?: a.razaTexto ?: "Sin raza definida",
                estadoRepro = HatoCalculos.estadoReproductivo(a, ae.eventos, hoy),
                ultimoPesoKg = HatoCalculos.ultimoPeso(ae.eventos)?.kg,
            )
        }.filter { fila ->
            val activa = fila.animal.estado == EstadoAnimal.ACTIVA
            val pasaFiltro = when (f) {
                FiltroHato.TODOS -> activa
                FiltroHato.HEMBRAS -> activa && fila.categoria in setOf("Vaca", "Novilla")
                FiltroHato.CRIAS -> activa && fila.categoria in setOf("Ternera", "Ternero")
                FiltroHato.MACHOS -> activa && fila.categoria in setOf("Toro", "Torete", "Novillo")
                FiltroHato.BAJAS -> !activa
            }
            val texto = listOfNotNull(fila.animal.arete, fila.animal.nombre, fila.raza, fila.categoria).joinToString(" ").lowercase()
            pasaFiltro && (q.isBlank() || q.trim().lowercase() in texto)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HatoScreen(onAbrirAnimal: (String) -> Unit, onNuevoAnimal: () -> Unit) {
    val vm = appViewModel { c, _ -> HatoViewModel(c.hato, c.razas) }
    val filas by vm.filas.collectAsStateWithLifecycle()
    val q by vm.busqueda.collectAsStateWithLifecycle()
    val filtro by vm.filtro.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { vm.busqueda.value = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Buscar por arete, nombre o raza") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiltroHato.entries.forEach { f ->
                        FilterChip(selected = filtro == f, onClick = { vm.filtro.value = f }, label = { Text(f.etiqueta) })
                    }
                }
            }
            val lista = filas
            if (lista != null) {
                item { Text("${lista.size} animales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(lista, key = { it.animal.id }) { fila -> FilaHato(fila) { onAbrirAnimal(fila.animal.id) } }
                if (lista.isEmpty()) item { Text("No hay animales con ese filtro.", modifier = Modifier.padding(vertical = 24.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNuevoAnimal,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Animal") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun FilaHato(f: FilaAnimal, onClick: () -> Unit) {
    val sem = LocalSemanticos.current
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FotoAnimal(f.animal, CircleShape, Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.animal.nombreVisible, fontWeight = FontWeight.Bold)
                    AreteChip(f.animal.arete)
                }
                Text("${f.categoria} · ${f.edad} · ${f.raza}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                f.estadoRepro?.let {
                    val color = when (it) {
                        is HatoCalculos.EstadoReproductivo.Prenada -> sem.doble
                        HatoCalculos.EstadoReproductivo.Inseminada -> sem.leche
                        HatoCalculos.EstadoReproductivo.EnCelo -> sem.carne
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Etiqueta(it.etiqueta, color, Modifier.padding(top = 4.dp))
                }
                if (f.animal.estado != EstadoAnimal.ACTIVA) Etiqueta(f.animal.estado.etiqueta, sem.carne, Modifier.padding(top = 4.dp))
            }
            Text(f.ultimoPesoKg?.let { "${it.fmt()} kg" } ?: "—", fontFamily = FontFamily.Monospace)
        }
    }
}
