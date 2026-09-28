package com.bobinapp.ui.components

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bobinapp.BobinappApp
import com.bobinapp.data.fotos.ImagenUtil
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.nombreVisible
import kotlinx.coroutines.launch

/**
 * Foto del animal guardada en el teléfono. Si no tiene foto (o aún no se descarga),
 * muestra la inicial de su nombre sobre un fondo neutro. El tamaño lo decide [modifier].
 */
@Composable
fun FotoAnimal(animal: AnimalEntity, forma: Shape, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val fotos = remember { (contexto.applicationContext as BobinappApp).container.fotos }
    BoxWithConstraints(
        modifier.clip(forma).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val lado = maxOf(maxWidth, maxHeight)
        val px = with(LocalDensity.current) { lado.roundToPx() }.coerceIn(64, 1280)
        val bitmap by produceState<Bitmap?>(null, animal.id, animal.fotoLocalEn, animal.fotoActualizadaEn) {
            value = if (animal.fotoActualizadaEn != null && animal.fotoLocalEn != null) fotos.miniatura(animal.id, px) else null
        }
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = "Foto de ${animal.nombreVisible}", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        } else {
            Text(
                animal.nombreVisible.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (minOf(maxWidth, maxHeight).value * 0.42f).coerceIn(12f, 56f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Menú para tomar una foto con la cámara (resolución completa vía FileProvider),
 * elegirla de la galería o quitarla. Devuelve el Bitmap ya corregido de rotación.
 */
@Composable
fun MenuFoto(
    abierto: Boolean,
    onCerrar: () -> Unit,
    tieneFoto: Boolean,
    onFoto: (Bitmap) -> Unit,
    onQuitar: () -> Unit,
    onError: (String) -> Unit,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var uriCamara by rememberSaveable { mutableStateOf<Uri?>(null) }
    val procesar: (Uri) -> Unit = { uri ->
        alcance.launch {
            val bmp = ImagenUtil.decodificar(contexto, uri)
            if (bmp != null) onFoto(bmp) else onError("No se pudo leer la imagen.")
        }
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = uriCamara
        if (ok && u != null) procesar(u)
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(procesar) }

    DropdownMenu(expanded = abierto, onDismissRequest = onCerrar) {
        DropdownMenuItem(text = { Text("Tomar foto") }, onClick = {
            onCerrar()
            try {
                val u = ImagenUtil.uriParaCamara(contexto)
                uriCamara = u
                camara.launch(u)
            } catch (e: ActivityNotFoundException) {
                onError("No hay una app de cámara disponible.")
            }
        })
        DropdownMenuItem(text = { Text("Elegir de la galería") }, onClick = {
            onCerrar()
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        if (tieneFoto) DropdownMenuItem(text = { Text("Quitar foto") }, onClick = { onCerrar(); onQuitar() })
    }
}
