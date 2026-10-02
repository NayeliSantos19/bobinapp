package com.bobinapp.ui.seguridad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bobinapp.R

/**
 * Pide confirmar la identidad (huella, rostro o PIN) y ejecuta [onExito] si sale bien.
 * La provee MainActivity; en vistas previas no hace nada.
 */
val LocalPedirIdentidad = compositionLocalOf<(titulo: String, onExito: () -> Unit) -> Unit> { { _, _ -> } }

/** Tapa toda la app hasta que el usuario se identifica. Surface de Material 3 bloquea los toques de abajo. */
@Composable
fun PantallaBloqueo(onDesbloquear: () -> Unit) {
    // Muestra el diálogo de huella apenas aparece la pantalla, sin hacer tocar un botón primero.
    LaunchedEffect(Unit) { onDesbloquear() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_logo_vaca_ui), contentDescription = null, modifier = Modifier.size(88.dp))
            Spacer(Modifier.height(16.dp))
            Text("Bobinapp está bloqueada", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Usa tu huella, tu rostro o el PIN del teléfono para ver los datos de tu hato.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDesbloquear) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Desbloquear")
            }
        }
    }
}
