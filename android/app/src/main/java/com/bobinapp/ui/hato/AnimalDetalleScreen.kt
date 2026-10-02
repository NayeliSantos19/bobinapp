@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.hato

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobinapp.BobinappApp
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.domain.HatoCalculos
import com.bobinapp.ui.alertas.TarjetaAlerta
import com.bobinapp.ui.components.AreteChip
import com.bobinapp.ui.components.CampoFecha
import com.bobinapp.ui.components.Etiqueta
import com.bobinapp.ui.components.FotoAnimal
import com.bobinapp.ui.components.GraficoLinea
import com.bobinapp.ui.components.MenuFoto
import com.bobinapp.ui.components.Seccion
import com.bobinapp.ui.components.Selector
import com.bobinapp.ui.components.appViewModel
import com.bobinapp.ui.components.fmt
import com.bobinapp.ui.components.texto
import com.bobinapp.ui.theme.LocalSemanticos
import java.time.LocalDate

@Composable
fun AnimalDetalleScreen(
    onAtras: () -> Unit,
    onAbrirAnimal: (String) -> Unit,
    onRegistrarEvento: (List<String>, TipoEvento?) -> Unit,
    onRegistrarParto: (String) -> Unit,
    onEditar: (String) -> Unit,
    onVerRaza: (String) -> Unit,
    onDescartarAlerta: (String) -> Unit,
) {
    val vm = appViewModel { c, s -> AnimalDetalleViewModel(s, c.hato, c.razas, c.ajustes, c.fotos) }
    val e by vm.estado.collectAsStateWithLifecycle()
    var borrar by remember { mutableStateOf<EventoEntity?>(null) }
    var mostrarBaja by remember { mutableStateOf(false) }
    var menuFoto by remember { mutableStateOf(false) }
    var avisoFoto by remember { mutableStateOf<String?>(null) }
    val a = e.animal
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var generandoPdf by remember { mutableStateOf(false) }
    fun compartirReporte() {
        if (generandoPdf) return
        generandoPdf = true
        alcance.launch {
            val archivo = runCatching { vm.crearReporte(ctx) }.getOrNull()
            generandoPdf = false
            if (archivo == null) { avisoFoto = "No se pudo generar el reporte."; return@launch }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fotos", archivo)
            val envio = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ficha de trazabilidad · ${archivo.nameWithoutExtension}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            (ctx.applicationContext as BobinappApp).container.analitica.accion("reporte_pdf")
            ctx.startActivity(Intent.createChooser(envio, "Compartir reporte"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a?.nombreVisible ?: "Animal") },
                navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { padding ->
        if (a == null) {
            Text(if (e.cargando) "Cargando…" else "Este animal ya no existe.", modifier = Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val sem = LocalSemanticos.current
        val activo = a.estado == EstadoAnimal.ACTIVA
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(padding)) {
            item {
                Box {
                    FotoAnimal(a, RoundedCornerShape(16.dp), Modifier.fillMaxWidth().height(220.dp).clickable { menuFoto = true })
                    Surface(
                        onClick = { menuFoto = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    ) {
                        Text(if (a.fotoActualizadaEn != null) "Cambiar foto" else "Agregar foto",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                    MenuFoto(
                        abierto = menuFoto, onCerrar = { menuFoto = false }, tieneFoto = a.fotoActualizadaEn != null,
                        onFoto = { vm.cambiarFoto(it); avisoFoto = null }, onQuitar = { vm.quitarFoto() }, onError = { avisoFoto = it },
                    )
                }
                avisoFoto?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AreteChip(a.arete, grande = true)
                        Text(a.nombreVisible.uppercase(), style = MaterialTheme.typography.headlineMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Etiqueta(e.categoria)
                        e.repro?.let { Etiqueta(it.etiqueta, sem.doble) }
                        if (!activo) Etiqueta(a.estado.etiqueta, sem.carne)
                        if (a.esDemo) Etiqueta("Ejemplo")
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activo) {
                            Button(onClick = { onRegistrarEvento(listOf(a.id), null) }) { Text("Registrar evento") }
                            if (a.sexo == Sexo.H && HatoCalculos.dias(a.nacimiento, LocalDate.now()) >= 300) {
                                OutlinedButton(onClick = { onRegistrarParto(a.id) }) { Text("Registrar parto") }
                            }
                        }
                        OutlinedButton(onClick = { onEditar(a.id) }) { Text("Editar") }
                        OutlinedButton(onClick = { compartirReporte() }, enabled = !generandoPdf) {
                            Text(if (generandoPdf) "Generando…" else "Reporte PDF")
                        }
                        if (activo) OutlinedButton(onClick = { mostrarBaja = true }) { Text("Dar de baja") }
                    }
                }
            }
            if (e.alertas.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.alertas.forEach { al ->
                        TarjetaAlerta(al, nombres = emptyMap(), onAbrirAnimal = null,
                            onRegistrar = al.accion?.let { t -> { if (t == TipoEvento.PARTO) onRegistrarParto(a.id) else onRegistrarEvento(listOf(a.id), t) } },
                            onDescartar = { onDescartarAlerta(al.clave) })
                    }
                }
            }
            item {
                Seccion("Datos") {
                    Dato("Raza", e.raza?.nombre ?: a.razaTexto ?: "Sin definir", e.raza?.let { r -> { onVerRaza(r.id) } })
                    Dato("Nacimiento", "${a.nacimiento.texto()} · ${e.edad}")
                    Dato("Último peso", e.ultimoPeso?.let { "${it.kg?.fmt()} kg · ${it.fecha.texto()}" } ?: "—")
                    Dato("Ganancia diaria", e.ganancia?.let { "${it.fmt(2)} kg/día" } ?: "—")
                    (e.repro as? HatoCalculos.EstadoReproductivo.Prenada)?.let { Dato("Parto estimado", it.partoEstimado.texto()) }
                    e.lechePromedio7?.let { Dato("Leche promedio 7 días", "${it.fmt(1)} L/día") }
                    a.notas?.takeIf { it.isNotBlank() }?.let { Dato("Notas", it) }
                }
            }
            item {
                Seccion("Genealogía") {
                    e.arbol?.let { ArbolGenealogico(a, it, onAbrirAnimal) }
                    Text("DESCENDENCIA (${e.crias.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.crias.isEmpty()) Text("Sin crías registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    e.crias.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { onAbrirAnimal(c.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AreteChip(c.arete); Text(c.nombreVisible); Text(HatoCalculos.edadTexto(c.nacimiento, LocalDate.now()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val asc = e.eventos.sortedBy { it.fecha }
            item {
                Seccion("Evolución de peso") {
                    GraficoLinea(asc.filter { it.tipo == TipoEvento.PESO && it.kg != null }.map { it.fecha to it.kg!! }, sem.doble, "kg", "Peso de ${a.nombreVisible}")
                }
            }
            val leche = asc.filter { it.tipo == TipoEvento.LECHE && it.litros != null && HatoCalculos.dias(it.fecha, LocalDate.now()) < 60 }
            if (leche.size > 1) item {
                Seccion("Leche · últimos 60 días") { GraficoLinea(leche.map { it.fecha to it.litros!! }, sem.leche, "L", "Leche diaria", decimales = 1) }
            }
            item {
                Seccion("Salud y vacunas") {
                    ListaEventos(e.eventos.filter { it.tipo == TipoEvento.VACUNA || it.tipo == TipoEvento.TRATAMIENTO }, "Sin vacunas ni tratamientos.") { borrar = it }
                }
            }
            if (a.sexo == Sexo.H) item {
                Seccion("Reproducción") {
                    val tipos = setOf(TipoEvento.CELO, TipoEvento.INSEMINACION, TipoEvento.PALPACION, TipoEvento.PARTO, TipoEvento.DESTETE)
                    ListaEventos(e.eventos.filter { it.tipo in tipos }, "Sin eventos reproductivos.") { borrar = it }
                }
            }
            item {
                val otros = e.eventos.filter { it.tipo != TipoEvento.LECHE }
                val nLeche = e.eventos.size - otros.size
                Seccion("Historial completo") {
                    Text("${otros.size} eventos" + if (nLeche > 0) " + $nLeche registros de leche" else "", style = MaterialTheme.typography.bodySmall)
                    ListaEventos(otros.take(60), "Sin eventos.") { borrar = it }
                }
            }
        }
    }

    borrar?.let { ev ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar este registro?") },
            text = { Text("${ev.tipo.etiqueta} del ${ev.fecha.texto()}. El borrado también se sincroniza con la nube.") },
            confirmButton = { TextButton(onClick = { vm.borrarEvento(ev); borrar = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar") } },
        )
    }
    if (mostrarBaja) DialogoBaja(
        onCancelar = { mostrarBaja = false },
        onConfirmar = { estado, fecha, detalle -> vm.darDeBaja(estado, fecha, detalle); mostrarBaja = false },
        onEliminar = { vm.eliminar(onAtras); mostrarBaja = false },
    )
}

@Composable
private fun Dato(titulo: String, valor: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(valor, fontWeight = FontWeight.Medium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

fun describirEvento(e: EventoEntity): String = when (e.tipo) {
    TipoEvento.PESO -> "${e.kg?.fmt(1)} kg"
    TipoEvento.LECHE -> "${e.litros?.fmt(1)} L"
    TipoEvento.VACUNA -> listOfNotNull(e.producto, e.dosis, e.proximaFecha?.let { "próxima ${it.texto()}" }, e.costo?.let { "$" + it.fmt(2) }).joinToString(" · ")
    TipoEvento.TRATAMIENTO -> listOfNotNull(
        listOfNotNull(e.diagnostico, e.producto).joinToString(": "), e.dosis, e.retiroDias?.let { "retiro $it días" },
        e.proximaFecha?.let { "próxima ${it.texto()}" }, e.costo?.let { "$" + it.fmt(2) },
    ).joinToString(" · ")
    TipoEvento.INSEMINACION -> listOfNotNull(e.toro, e.tecnico).joinToString(" · ")
    TipoEvento.PALPACION -> e.resultado?.etiqueta ?: ""
    else -> e.nota ?: ""
}

@Composable
private fun ListaEventos(eventos: List<EventoEntity>, vacio: String, onBorrar: (EventoEntity) -> Unit) {
    if (eventos.isEmpty()) { Text(vacio, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    eventos.forEachIndexed { i, e ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.Top) {
            Text(e.fecha.texto(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f)) {
                Text(e.tipo.etiqueta, fontWeight = FontWeight.Bold)
                val d = describirEvento(e)
                if (d.isNotBlank()) Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onBorrar(e) }) { Icon(Icons.Default.Delete, contentDescription = "Borrar registro") }
        }
    }
}

@Composable
private fun ArbolGenealogico(a: com.bobinapp.data.local.entity.AnimalEntity, arbol: Arbol, onAbrir: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        NodoArbol("Animal", Nodo.EnHato(a), null, Modifier.weight(1f), destacado = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(36.dp)) {
            NodoArbol("Madre", arbol.madre, onAbrir)
            NodoArbol("Padre", arbol.padre, onAbrir)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NodoArbol("Abuela materna", arbol.abuelaMaterna, onAbrir)
            NodoArbol("Abuelo materno", arbol.abueloMaterno, onAbrir)
            NodoArbol("Abuela paterna", arbol.abuelaPaterna, onAbrir)
            NodoArbol("Abuelo paterno", arbol.abueloPaterno, onAbrir)
        }
    }
}

@Composable
private fun NodoArbol(rol: String, nodo: Nodo, onAbrir: ((String) -> Unit)?, modifier: Modifier = Modifier, destacado: Boolean = false) {
    val clic = (nodo as? Nodo.EnHato)?.let { n -> onAbrir?.let { f -> { f(n.animal.id) } } }
    Surface(
        modifier = modifier.fillMaxWidth().then(if (clic != null) Modifier.clickable(onClick = clic) else Modifier),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (destacado) 2.dp else 1.dp, if (destacado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(rol.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            when (nodo) {
                is Nodo.EnHato -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FotoAnimal(nodo.animal, CircleShape, Modifier.size(28.dp))
                        Text(nodo.animal.nombreVisible, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                    AreteChip(nodo.animal.arete)
                }
                is Nodo.Externo -> { Text(nodo.descripcion, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall); Text("Externo", style = MaterialTheme.typography.bodySmall) }
                Nodo.Desconocido -> Text("Sin registro", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DialogoBaja(onCancelar: () -> Unit, onConfirmar: (EstadoAnimal, LocalDate, String) -> Unit, onEliminar: () -> Unit) {
    var estado by remember { mutableStateOf(EstadoAnimal.VENDIDA) }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var detalle by remember { mutableStateOf("") }
    var confirmarEliminar by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Dar de baja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sale del inventario activo, pero su historial y genealogía se conservan.")
                Selector("Motivo", listOf(EstadoAnimal.VENDIDA, EstadoAnimal.MUERTA, EstadoAnimal.DESCARTADA), estado, { it.etiqueta }, { estado = it })
                CampoFecha("Fecha", fecha, { fecha = it ?: LocalDate.now() }, maxima = LocalDate.now())
                OutlinedTextField(detalle, { detalle = it }, label = { Text("Detalle (comprador, precio, causa)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { if (confirmarEliminar) onEliminar() else confirmarEliminar = true }) {
                    Text(if (confirmarEliminar) "Toca otra vez para eliminar el registro" else "Eliminar registro por error",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(estado, fecha, detalle) }) { Text("Confirmar baja") } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
