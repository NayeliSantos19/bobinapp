@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.escaner

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.analitica.Analitica
import com.bobinapp.data.remote.ApiProvider
import com.bobinapp.data.remote.IdentificacionDto
import com.bobinapp.data.remote.IdentificarRequest
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.ui.components.Seccion
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.razas.FotoRaza
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed interface EstadoEscaner {
    data object Inicial : EstadoEscaner
    data object Analizando : EstadoEscaner
    data class Resultado(val r: IdentificacionDto) : EstadoEscaner
    data class Error(val mensaje: String) : EstadoEscaner
}

class EscanerViewModel(
    private val api: ApiProvider,
    private val ajustes: AjustesStore,
    val razas: RazasRepository,
    private val analitica: Analitica,
) : ViewModel() {
    var foto by mutableStateOf<Bitmap?>(null); private set
    var estado by mutableStateOf<EstadoEscaner>(EstadoEscaner.Inicial); private set

    fun ponerFoto(b: Bitmap) { foto = b; estado = EstadoEscaner.Inicial }

    fun identificar() {
        val b = foto ?: return
        if (!ajustes.actual.conectadoANube) {
            estado = EstadoEscaner.Error("Conecta tu finca al servidor en Ajustes. El escáner usa la API para no guardar llaves en el teléfono.")
            return
        }
        estado = EstadoEscaner.Analizando
        viewModelScope.launch {
            estado = try {
                val base64 = withContext(Dispatchers.Default) { comprimir(b) }
                val r = api.api().identificar(IdentificarRequest(base64, "image/jpeg"))
                // Solo qué tan seguro estuvo el modelo y si era un bovino; nunca la foto ni la raza del animal del usuario.
                analitica.accion("escaneo", mapOf("confianza" to r.confianza, "bovino" to r.esBovino))
                EstadoEscaner.Resultado(r)
            } catch (e: IOException) {
                EstadoEscaner.Error("Sin conexión con el servidor. El escáner necesita internet; el resto de la app funciona sin señal.")
            } catch (e: HttpException) {
                EstadoEscaner.Error(if (e.code() == 503) "El servidor no tiene configurada la llave de visión (ANTHROPIC_API_KEY)." else "El servidor respondió con error ${e.code()}.")
            }
        }
    }

    /** Reduce a 1280 px de lado mayor y JPEG 85 %: suficiente para identificar y ligero para datos móviles. */
    private fun comprimir(original: Bitmap): String {
        val escala = 1280f / maxOf(original.width, original.height)
        val b = if (escala < 1f) Bitmap.createScaledBitmap(original, (original.width * escala).toInt(), (original.height * escala).toInt(), true) else original
        val salida = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
    }
}

@Composable
fun EscanerScreen(onAtras: () -> Unit, onVerRaza: (String) -> Unit, onAgregarAlHato: (String) -> Unit) {
    val vm = appViewModel { c, _ -> EscanerViewModel(c.apiProvider, c.ajustes, c.razas, c.analitica) }
    val contexto = LocalContext.current
    var aviso by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { it?.let(vm::ponerFoto) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            try {
                val bmp = contexto.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream)
                if (bmp != null) vm.ponerFoto(bmp) else aviso = "No se pudo abrir la imagen."
            } catch (e: IOException) {
                aviso = "No se pudo abrir la imagen."
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Identificar raza") },
            navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Toma la foto de lado, con el animal completo y buena luz. Cabeza, giba, orejas y patas ayudan a distinguir la raza.",
                style = MaterialTheme.typography.bodyMedium)
            vm.foto?.let {
                Image(it.asImageBitmap(), contentDescription = "Foto del animal", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try { camara.launch(null) } catch (e: ActivityNotFoundException) { aviso = "No hay una app de cámara disponible." }
                }) { Text("Tomar foto") }
                OutlinedButton(onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galería") }
                Button(onClick = vm::identificar, enabled = vm.foto != null && vm.estado != EstadoEscaner.Analizando) { Text("Identificar") }
            }
            aviso?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when (val s = vm.estado) {
                EstadoEscaner.Inicial -> {}
                EstadoEscaner.Analizando -> { Text("Analizando la foto…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is EstadoEscaner.Error -> Text(s.mensaje, color = MaterialTheme.colorScheme.error)
                is EstadoEscaner.Resultado -> Resultado(s.r, vm.razas, onVerRaza, onAgregarAlHato)
            }
        }
    }
}

@Composable
private fun Resultado(r: IdentificacionDto, razas: RazasRepository, onVerRaza: (String) -> Unit, onAgregarAlHato: (String) -> Unit) {
    if (!r.esBovino) {
        Seccion("No se detectó un bovino") { Text(r.nota.ifBlank { "Prueba con otra foto donde el animal se vea completo." }) }
        return
    }
    val raza = razas.buscar(r.razaId)
    Seccion("Resultado") {
        Text((raza?.nombre ?: r.razaNombre.ifBlank { "Raza no determinada" }).uppercase(), style = MaterialTheme.typography.headlineMedium)
        if (raza != null && razas.tieneFoto(raza.id)) {
            FotoRaza(raza, RoundedCornerShape(12.dp), Modifier.fillMaxWidth().height(170.dp))
            Text("Ejemplar típico de ${raza.nombre}, para comparar con tu foto.", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { r.confianza / 100f }, modifier = Modifier.fillMaxWidth())
        Text("Confianza ${r.confianza}%", style = MaterialTheme.typography.labelSmall)
        if (r.esCruce && r.cruceDe.isNotEmpty()) Text("Posible cruce: ${r.cruceDe.joinToString(" × ")}", fontWeight = FontWeight.SemiBold)
        if (r.rasgos.isNotEmpty()) {
            Text("Rasgos observados", fontWeight = FontWeight.Bold)
            r.rasgos.forEach { Text("• $it") }
        }
        if (r.alternativas.isNotEmpty()) {
            Text("También podría ser", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.alternativas.forEach { alt ->
                    AssistChip(onClick = { alt.razaId?.let(onVerRaza) }, label = { Text("${razas.buscar(alt.razaId)?.nombre ?: alt.razaNombre} · ${alt.confianza}%") })
                }
            }
        }
        if (r.nota.isNotBlank()) Text(r.nota, style = MaterialTheme.typography.bodySmall)
        if (raza != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onVerRaza(raza.id) }) { Text("Ver ficha") }
            OutlinedButton(onClick = { onAgregarAlHato(raza.id) }) { Text("Agregar al hato") }
        }
    }
}
