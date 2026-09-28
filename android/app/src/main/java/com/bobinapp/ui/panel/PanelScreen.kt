@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.ui.components.AreteChip
import com.bobinapp.ui.components.GraficoBarras
import com.bobinapp.ui.components.GraficoLinea
import com.bobinapp.ui.components.Seccion
import com.bobinapp.ui.components.Selector
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.components.fmt
import com.bobinapp.ui.theme.LocalSemanticos
import kotlin.math.abs

@Composable
fun PanelScreen(onAbrirAnimal: (String) -> Unit, onVerAlertas: () -> Unit) {
    val vm = appViewModel { c, _ -> PanelViewModel(c.hato, c.ajustes) }
    val e by vm.estado.collectAsStateWithLifecycle()
    val sem = LocalSemanticos.current

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (e.animalesDemo > 0) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Estás viendo ${e.animalesDemo} animales de ejemplo. No se suben a la nube.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::borrarDemo) { Text("Borrar ejemplo") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 días", 90 to "90 días", 365 to "12 meses").forEach { (d, t) ->
                    FilterChip(selected = e.periodo == d, onClick = { vm.cambiarPeriodo(d) }, label = { Text(t) })
                }
            }
        }
        item {
            val cambio: (Double?, Boolean) -> Pair<String, Color>? = { c, menosEsMejor ->
                c?.let {
                    val bueno = if (menosEsMejor) it < 0 else it > 0
                    "${if (it > 0) "▲" else "▼"} ${abs(it).fmt()}% vs periodo anterior" to (if (bueno) sem.doble else sem.carne)
                }
            }
            val kpis = listOf(
                Kpi("Animales activos", "${e.activos}", "", "${e.hembras} hembras · ${e.activos - e.hembras} machos", null),
                Kpi("Leche del hato", e.lechePromedio.fmt(1), "L/día", "promedio del periodo", cambio(e.lecheCambio, false)),
                Kpi("Ganancia de peso", e.gananciaPromedio?.fmt(2) ?: "—", "kg/día", "promedio de ${e.gananciaN} animales en crecimiento", null),
                Kpi("Gasto en farmacia", "$" + e.gasto.fmt(2), "", "vacunas y tratamientos", cambio(e.gastoCambio, true)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kpis.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        fila.forEach { TarjetaKpi(it, Modifier.weight(1f)) }
                    }
                }
                Card(
                    onClick = onVerAlertas,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (e.urgentes > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${e.urgentes}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("  alertas para hoy o vencidas", modifier = Modifier.weight(1f))
                        Text("Ver →", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Seccion("Producción de leche") {
                Text("Litros por día, todo el hato", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GraficoLinea(e.lecheSerie, sem.leche, "L", "Producción diaria de leche del hato", decimales = 1)
            }
        }
        item {
            Seccion("Evolución de peso") {
                val elegido = e.animalPeso
                if (elegido != null) {
                    Selector("Animal", e.opcionesPeso, elegido, { "${it.arete} · ${it.nombreVisible}" }, { vm.seleccionarPeso(it.id) })
                    GraficoLinea(e.seriePeso, sem.doble, "kg", "Peso de ${elegido.nombreVisible}")
                    e.gananciaAnimal?.let { Text("Ganancia en el periodo: ${it.fmt(2)} kg/día", style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("Registra al menos dos pesajes de un animal para ver su curva.")
                }
            }
        }
        item {
            Seccion("Gastos de farmacia") {
                Text("USD por mes, últimos 12 meses. Toca una barra para ver el valor.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                GraficoBarras(e.gastoMensual, MaterialTheme.colorScheme.primary, "$", "Gasto mensual en farmacia")
            }
        }
        item {
            Seccion("Composición del hato") {
                val max = (e.composicion.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                e.composicion.forEach { (cat, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, modifier = Modifier.width(80.dp))
                        Box(Modifier.weight(1f).height(14.dp)) {
                            Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                        }
                        Text("$n", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp).padding(start = 8.dp))
                    }
                }
            }
        }
        if (e.productoras.isNotEmpty()) item {
            Seccion("Mejores productoras") {
                e.productoras.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { onAbrirAnimal(p.animal.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AreteChip(p.animal.arete)
                        Text("  ${p.animal.nombreVisible}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${p.promedio.fmt(1)} L/día", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private data class Kpi(val titulo: String, val valor: String, val unidad: String, val sub: String, val cambio: Pair<String, Color>?)

@Composable
private fun TarjetaKpi(k: Kpi, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(k.titulo.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(k.valor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                if (k.unidad.isNotEmpty()) Text(" ${k.unidad}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 5.dp))
            }
            if (k.cambio != null) Text(k.cambio.first, color = k.cambio.second, style = MaterialTheme.typography.bodySmall)
            else Text(k.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
