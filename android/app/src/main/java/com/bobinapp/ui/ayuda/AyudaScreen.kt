@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.ayuda

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobinapp.BobinappApp
import com.bobinapp.BuildConfig
import com.bobinapp.ui.components.Seccion

private data class Pregunta(val pregunta: String, val respuesta: String)

private val GUIA = listOf(
    "Registra tus animales" to "En Mi hato toca ＋. Arete, sexo, raza y nacimiento son lo único obligatorio; madre y padre arman el árbol genealógico solos.",
    "Anota lo que pasa" to "Desde la ficha del animal: Registrar evento para pesajes, leche, vacunas, tratamientos, celos, inseminaciones y partos.",
    "Revisa las alertas" to "La pestaña Alertas te dice qué toca hoy: inseminar, palpar, secar, vacunar o pesar. Desde ahí mismo lo registras.",
    "Mira cómo va el hato" to "El Panel resume leche, ganancia de peso y gasto en farmacia, y compara con el periodo anterior.",
)

private val PREGUNTAS = listOf(
    Pregunta("¿Funciona sin señal?",
        "Sí. Todo se guarda primero en el teléfono. Cuando vuelve la señal, la app sube los cambios sola, aunque esté cerrada. Arriba ves cuántos cambios quedan en cola."),
    Pregunta("¿Cómo uso la app con varios teléfonos?",
        "En Ajustes crea la finca en un teléfono y conéctala. Los cambios de cada teléfono se combinan: si dos personas editan lo mismo, gana el cambio más reciente."),
    Pregunta("¿Por qué no me llegan las notificaciones?",
        "Revisa en Ajustes → Notificaciones que estén permitidas y que la categoría esté encendida. Con el silencio de noche activo, lo de 9 p. m. a 6 a. m. llega en la mañana. Algunos teléfonos cierran las apps para ahorrar batería: quita a Bobinapp del ahorro de batería."),
    Pregunta("¿Qué tan exacto es el escáner de razas?",
        "Funciona mejor con el animal de lado, completo y con buena luz. Muestra qué tan seguro está y otras razas posibles. En cruces, toma el resultado como orientación."),
    Pregunta("¿Cómo comparto la ficha de un animal?",
        "En la ficha toca Reporte PDF. Se genera un documento con foto, genealogía, pesajes, sanidad y reproducción que puedes mandar por WhatsApp o correo."),
    Pregunta("Borré un registro por error, ¿lo recupero?",
        "Por ahora no hay papelera. Vuelve a registrarlo con la fecha original y los cálculos se ajustan solos."),
    Pregunta("¿Quién puede ver mis datos?",
        "Solo los teléfonos conectados a tu finca. El servidor guarda tus datos separados de los de otras fincas, y el token de acceso va cifrado en tu teléfono."),
    Pregunta("¿Cómo protejo la app si presto el teléfono?",
        "En Ajustes → Seguridad activa el bloqueo con huella o rostro. Se pide al abrir la app y cuando vuelves después de un minuto."),
)

private val PRIVACIDAD = listOf(
    "En tu teléfono" to "Tus animales, eventos y fotos. Si conectas una finca, el token de acceso se guarda cifrado.",
    "En el servidor de tu finca" to "Los mismos datos, solo si conectaste una finca, para sincronizar tus teléfonos. Viajan siempre por HTTPS.",
    "Estadísticas anónimas" to "Qué pantallas se usan, cuánto tarda en abrir y qué errores ocurren, con un número al azar que no te identifica. Puedes apagarlas y borrarlas en Ajustes → Privacidad.",
    "Lo que nunca sale" to "Nombres, aretes, notas y fotos nunca van a las estadísticas. No usamos publicidad ni vendemos datos.",
)

@Composable
fun AyudaScreen(onAtras: () -> Unit) {
    val contexto = LocalContext.current
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayuda y soporte") },
                navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Seccion("Guía rápida") {
                    GUIA.forEachIndexed { i, (titulo, texto) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(titulo, fontWeight = FontWeight.Bold)
                                Text(texto, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item { Text("PREGUNTAS FRECUENTES", style = MaterialTheme.typography.titleMedium) }
            items(PREGUNTAS) { PreguntaPlegable(it) }
            item {
                Seccion("Tus datos y tu privacidad") {
                    PRIVACIDAD.forEach { (titulo, texto) ->
                        Column {
                            Text(titulo, fontWeight = FontWeight.Bold)
                            Text(texto, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Seccion("¿Algo no funciona?") {
                    Text("Cuéntanos qué pasó. El correo incluye la versión de la app y del teléfono para ayudarte más rápido; nada de tus animales.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { aviso = escribirASoporte(contexto, "Problema en Bobinapp", "Describe qué estabas haciendo y qué pasó:") }) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Text("  Reportar un problema")
                    }
                    OutlinedButton(onClick = { aviso = escribirASoporte(contexto, "Sugerencia para Bobinapp", "¿Qué te gustaría que hiciera la app?") }) {
                        Text("Sugerir una mejora")
                    }
                    aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            item {
                Text(
                    "Bobinapp ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PreguntaPlegable(p: Pregunta) {
    var abierta by rememberSaveable(p.pregunta) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (abierta) "Cerrar" else "Ver respuesta") { abierta = !abierta }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.pregunta, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (abierta) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (abierta) Text(p.respuesta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
        HorizontalDivider()
    }
}

/** Abre la app de correo con los datos técnicos ya escritos. Devuelve un aviso si no hay app de correo. */
private fun escribirASoporte(contexto: Context, asunto: String, indicacion: String): String? {
    val c = (contexto.applicationContext as BobinappApp).container
    val aj = c.ajustes.actual
    val cuerpo = buildString {
        appendLine(indicacion)
        appendLine(); appendLine(); appendLine()
        appendLine("— Datos técnicos (no borres esta parte) —")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Teléfono: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Finca conectada: ${if (aj.conectadoANube) "sí" else "no"}")
        c.analitica.pantallaActual?.let { appendLine("Última pantalla: $it") }
    }
    val intento = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.SOPORTE_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, asunto)
        putExtra(Intent.EXTRA_TEXT, cuerpo)
    }
    return try {
        contexto.startActivity(intento)
        c.analitica.accion("soporte_contacto")
        null
    } catch (e: ActivityNotFoundException) {
        "No encontramos una app de correo. Escríbenos a ${BuildConfig.SOPORTE_EMAIL}."
    }
}
