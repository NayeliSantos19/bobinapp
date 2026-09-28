package com.bobinapp.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bobinapp.BobinappApp
import com.bobinapp.di.AppContainer
import com.bobinapp.ui.theme.Arete
import com.bobinapp.ui.theme.AreteTinta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Crea un ViewModel con acceso al contenedor de dependencias y a los argumentos de navegación. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline crear: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as BobinappApp
    return viewModel(factory = viewModelFactory { initializer { crear(app.container, createSavedStateHandle()) } })
}

private val FORMATO_FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
private val FORMATO_CORTO = DateTimeFormatter.ofPattern("d MMM", Locale("es"))
fun LocalDate.texto(): String = format(FORMATO_FECHA)
fun LocalDate.corto(): String = format(FORMATO_CORTO)
fun diasRelativo(d: Long): String = when {
    d == 0L -> "hoy"
    d == 1L -> "mañana"
    d == -1L -> "ayer"
    d > 0 -> "en $d días"
    else -> "hace ${-d} días"
}
fun Double.fmt(dec: Int = 0): String = String.format(Locale("es"), "%,.${dec}f", this)

@Composable
fun AreteChip(arete: String, modifier: Modifier = Modifier, grande: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 9.dp, bottomEnd = 9.dp))
            .background(Arete)
            .padding(horizontal = if (grande) 10.dp else 7.dp, vertical = if (grande) 4.dp else 2.dp),
    ) {
        Text(arete, color = AreteTinta, fontFamily = FontFamily.Monospace, fontSize = if (grande) 15.sp else 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Etiqueta(texto: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color),
    ) {
        Text(texto.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, accion: @Composable (() -> Unit)? = null, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                accion?.invoke()
            }
            contenido()
        }
    }
}

@Composable
fun Punto(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

/** Campo de fecha que abre el selector nativo de Android. */
@Composable
fun CampoFecha(
    etiqueta: String,
    valor: LocalDate?,
    onCambio: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    maxima: LocalDate? = null,
    permitirVacio: Boolean = false,
) {
    val contexto = LocalContext.current
    Box(modifier) {
        OutlinedTextField(
            value = valor?.texto() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable {
                    val base = valor ?: LocalDate.now()
                    DatePickerDialog(contexto, { _, y, m, d -> onCambio(LocalDate.of(y, m + 1, d)) }, base.year, base.monthValue - 1, base.dayOfMonth)
                        .apply {
                            maxima?.let { datePicker.maxDate = it.toEpochDay() * 86_400_000L + 86_399_000L }
                            if (permitirVacio) setButton(DatePickerDialog.BUTTON_NEUTRAL, "Quitar") { _, _ -> onCambio(null) }
                        }
                        .show()
                },
        )
    }
}

/** Selector desplegable simple (evita APIs experimentales de Material 3). */
@Composable
fun <T> Selector(
    etiqueta: String,
    opciones: List<T>,
    seleccionado: T,
    texto: (T) -> String,
    onCambio: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = texto(seleccionado),
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { abierto = true })
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { op ->
                DropdownMenuItem(text = { Text(texto(op)) }, onClick = { onCambio(op); abierto = false })
            }
        }
    }
}
