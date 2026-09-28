@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.razas

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobinapp.BobinappApp
import com.bobinapp.data.repository.Raza
import com.bobinapp.data.repository.aptitudEtiqueta
import com.bobinapp.ui.components.Etiqueta
import com.bobinapp.ui.components.Seccion
import com.bobinapp.ui.theme.LocalSemanticos

fun colorHex(hex: String?): Color = try {
    Color(android.graphics.Color.parseColor(hex ?: "#999999"))
} catch (e: IllegalArgumentException) {
    Color.Gray
}

@Composable
fun MuestraPelaje(r: Raza, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(CircleShape).background(colorHex(r.colorPrincipal))) {
        r.colorSecundario?.let {
            Box(Modifier.size(22.dp).align(Alignment.BottomEnd).clip(CircleShape).background(colorHex(it)))
        }
    }
}

/** Foto de referencia de la raza; si no está en assets, muestra sus colores de pelaje. */
@Composable
fun FotoRaza(r: Raza, forma: Shape, modifier: Modifier = Modifier) {
    val repo = (LocalContext.current.applicationContext as BobinappApp).container.razas
    BoxWithConstraints(modifier.clip(forma).background(colorHex(r.colorPrincipal)), contentAlignment = Alignment.Center) {
        val px = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceIn(64, 1280)
        val foto by produceState<Bitmap?>(null, r.id, px) { value = repo.foto(r.id, px) }
        val b = foto
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = "Ejemplar de raza ${r.nombre}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            r.colorSecundario?.let {
                Box(Modifier.fillMaxSize(0.5f).align(Alignment.BottomEnd).clip(CircleShape).background(colorHex(it)))
            }
        }
    }
}

@Composable
fun AptitudesFila(r: Raza) {
    val sem = LocalSemanticos.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        r.aptitudes.forEach { a ->
            Etiqueta(aptitudEtiqueta(a), when (a) { "carne" -> sem.carne; "leche" -> sem.leche; else -> sem.doble })
        }
        Etiqueta(r.grupoEtiqueta)
    }
}

@Composable
fun RazasScreen(onAbrirRaza: (String) -> Unit, onEscanear: () -> Unit) {
    val razas = (LocalContext.current.applicationContext as BobinappApp).container.razas.todas
    var q by rememberSaveable { mutableStateOf("") }
    var aptitud by rememberSaveable { mutableStateOf<String?>(null) }
    var grupo by rememberSaveable { mutableStateOf<String?>(null) }
    val lista = remember(q, aptitud, grupo) {
        razas.filter { r ->
            (aptitud == null || aptitud in r.aptitudes) && (grupo == null || r.grupo == grupo) &&
                (q.isBlank() || listOf(r.nombre, r.otrosNombres, r.origen, r.pelaje).joinToString(" ").contains(q.trim(), ignoreCase = true))
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Buscar raza, color o país") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Todas", "carne" to "Carne", "leche" to "Leche", "doble" to "Doble propósito").forEach { (k, t) ->
                        FilterChip(selected = aptitud == k, onClick = { aptitud = k }, label = { Text(t) })
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Todo origen", "indicus" to "Cebú", "taurus" to "Europea", "sintetica" to "Sintética", "criolla" to "Criolla").forEach { (k, t) ->
                        FilterChip(selected = grupo == k, onClick = { grupo = k }, label = { Text(t) })
                    }
                }
            }
            item { Text("${lista.size} de ${razas.size} razas", style = MaterialTheme.typography.labelSmall) }
            items(lista, key = { it.id }) { r ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { onAbrirRaza(r.id) }) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FotoRaza(r, RoundedCornerShape(10.dp), Modifier.size(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.nombre, style = MaterialTheme.typography.titleMedium)
                            Text(r.origen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            AptitudesFila(r)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onEscanear,
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            text = { Text("Identificar por foto") },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun RazaDetalleScreen(id: String, onAtras: () -> Unit, onAgregarAlHato: (String) -> Unit) {
    val r = (LocalContext.current.applicationContext as BobinappApp).container.razas.buscar(id)
    Scaffold(topBar = {
        TopAppBar(title = { Text(r?.nombre ?: "Raza") },
            navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } })
    }) { padding ->
        if (r == null) { Text("Raza no encontrada", Modifier.padding(padding).padding(24.dp)); return@Scaffold }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoRaza(r, RoundedCornerShape(16.dp), Modifier.fillMaxWidth().height(220.dp))
            val repo = (LocalContext.current.applicationContext as BobinappApp).container.razas
            repo.credito(r.id)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!repo.tieneFoto(r.id)) Text("Foto de referencia pendiente: se muestran los colores típicos del pelaje.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(r.nombre.uppercase(), style = MaterialTheme.typography.headlineMedium)
                    if (r.otrosNombres.isNotBlank()) Text("También: ${r.otrosNombres}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AptitudesFila(r)
            Text(r.descripcion)
            Seccion("Ficha") {
                listOf("Origen" to r.origen, "Peso toro" to "${r.pesoMachoKg} kg", "Peso vaca" to "${r.pesoHembraKg} kg",
                    "Clima ideal" to r.clima, "Pelaje" to r.pelaje, "Cuernos" to r.cuernos).forEach { (k, v) ->
                    Row { Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f)); Text(v, modifier = Modifier.weight(0.6f)) }
                }
            }
            Seccion("Cómo reconocerla") { r.rasgos.forEach { Text("• $it") } }
            Seccion("Ventajas") { r.ventajas.forEach { Text("• $it") } }
            Seccion("Desventajas") { r.desventajas.forEach { Text("• $it") } }
            OutlinedCard(shape = RoundedCornerShape(10.dp)) { Text("Dato: ${r.dato}", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium) }
            androidx.compose.material3.Button(onClick = { onAgregarAlHato(r.id) }, modifier = Modifier.fillMaxWidth()) { Text("Agregar un animal de esta raza") }
        }
    }
}
