@file:OptIn(ExperimentalMaterial3Api::class)

package com.bobinapp.ui.formularios

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.ui.components.AreteChip
import com.bobinapp.ui.components.CampoFecha
import com.bobinapp.ui.components.Selector
import com.bobinapp.ui.components.appViewModel
import java.time.LocalDate

private val VACUNAS = listOf("Triple bovina", "Rabia paralítica bovina", "Brucelosis RB51", "Leptospirosis", "Clostridiales 8 vías", "Ivermectina 1%", "Vitaminas ADE")
private val MEDICAMENTOS = listOf("Oxitetraciclina LA", "Penicilina + estreptomicina", "Cefquinoma intramamaria", "Flunixin meglumine", "Enrofloxacina")

@Composable
private fun PantallaFormulario(titulo: String, onAtras: () -> Unit, error: String?, textoGuardar: String, onGuardar: () -> Unit, contenido: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = { IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            contenido()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onGuardar, modifier = Modifier.fillMaxWidth()) { Text(textoGuardar) }
        }
    }
}

@Composable
private fun CampoTexto(etiqueta: String, valor: String, onCambio: (String) -> Unit, numerico: Boolean = false) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio, label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Sugerencias(opciones: List<String>, onElegir: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { AssistChip(onClick = { onElegir(it) }, label = { Text(it) }) }
    }
}

@Composable
fun EventoFormScreen(onAtras: () -> Unit) {
    val vm = appViewModel { c, s -> EventoFormViewModel(s, c.hato) }
    LaunchedEffect(vm.listo) { if (vm.listo) onAtras() }
    val f = vm.form
    val titulo = if (vm.animales.size == 1) "Registrar · ${vm.animales.first().nombreVisible}" else "Registrar · ${vm.animales.size} animales"
    PantallaFormulario(titulo, onAtras, vm.error, "Guardar", vm::guardar) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.animales.forEach { AreteChip(it.arete) }
        }
        Selector("Tipo de evento", vm.tiposDisponibles, f.tipo, { it.etiqueta }, { vm.actualizar(f.copy(tipo = it)) })
        CampoFecha("Fecha", f.fecha, { vm.actualizar(f.copy(fecha = it ?: LocalDate.now())) }, maxima = LocalDate.now())
        when (f.tipo) {
            TipoEvento.PESO -> CampoTexto("Peso (kg)", f.kg, { vm.actualizar(f.copy(kg = it)) }, numerico = true)
            TipoEvento.LECHE -> CampoTexto("Litros del día", f.litros, { vm.actualizar(f.copy(litros = it)) }, numerico = true)
            TipoEvento.VACUNA, TipoEvento.TRATAMIENTO -> {
                if (f.tipo == TipoEvento.TRATAMIENTO) CampoTexto("Diagnóstico", f.diagnostico, { vm.actualizar(f.copy(diagnostico = it)) })
                CampoTexto(if (f.tipo == TipoEvento.VACUNA) "Vacuna o producto" else "Medicamento", f.producto, { vm.actualizar(f.copy(producto = it)) })
                Sugerencias(if (f.tipo == TipoEvento.VACUNA) VACUNAS else MEDICAMENTOS) { vm.actualizar(f.copy(producto = it)) }
                CampoTexto("Dosis", f.dosis, { vm.actualizar(f.copy(dosis = it)) })
                if (f.tipo == TipoEvento.TRATAMIENTO) CampoTexto("Días de retiro (leche y carne)", f.retiroDias, { vm.actualizar(f.copy(retiroDias = it)) }, numerico = true)
                CampoFecha("Próxima dosis (opcional)", f.proxima, { vm.actualizar(f.copy(proxima = it)) }, permitirVacio = true)
                CampoTexto("Costo por animal (USD)", f.costo, { vm.actualizar(f.copy(costo = it)) }, numerico = true)
            }
            TipoEvento.INSEMINACION -> {
                if (vm.toros.isNotEmpty()) {
                    Text("Toros del hato", style = MaterialTheme.typography.labelSmall)
                    Sugerencias(vm.toros.map { "${it.arete} · ${it.nombreVisible}" }) { t ->
                        vm.actualizar(f.copy(toro = t, toroId = vm.toros.firstOrNull { t.startsWith(it.arete) }?.id))
                    }
                }
                CampoTexto("Toro o pajilla de semen", f.toro, { vm.actualizar(f.copy(toro = it, toroId = null)) })
                CampoTexto("Técnico", f.tecnico, { vm.actualizar(f.copy(tecnico = it)) })
            }
            TipoEvento.PALPACION -> Selector("Resultado", ResultadoPalpacion.entries.toList(), f.resultado, { it.etiqueta }, { vm.actualizar(f.copy(resultado = it)) })
            else -> CampoTexto(if (f.tipo == TipoEvento.NOTA) "Nota" else "Observaciones (opcional)", f.nota, { vm.actualizar(f.copy(nota = it)) })
        }
    }
}

@Composable
private fun CamposRaza(razas: RazasRepository, razaId: String?, razaTexto: String, onRaza: (String?) -> Unit, onTexto: (String) -> Unit) {
    val opciones: List<String?> = listOf<String?>(null) + razas.todas.map { it.id } + RAZA_CRUCE
    Selector("Raza", opciones, razaId, { id ->
        when (id) { null -> "Sin definir"; RAZA_CRUCE -> "Cruce u otra raza"; else -> razas.buscar(id)?.nombre ?: id }
    }, onRaza)
    if (razaId == RAZA_CRUCE) CampoTexto("Describe el cruce (ej.: Brahman × Pardo Suizo)", razaTexto, onTexto)
}

@Composable
fun AnimalFormScreen(onAtras: () -> Unit, onGuardado: (String) -> Unit) {
    val vm = appViewModel { c, s -> AnimalFormViewModel(s, c.hato, c.razas) }
    LaunchedEffect(vm.guardadoId) { vm.guardadoId?.let(onGuardado) }
    val f = vm.form
    PantallaFormulario(if (vm.esNuevo) "Nuevo animal" else "Editar animal", onAtras, vm.error, "Guardar", vm::guardar) {
        CampoTexto("Número de arete *", f.arete, { vm.actualizar(f.copy(arete = it)) })
        CampoTexto("Nombre", f.nombre, { vm.actualizar(f.copy(nombre = it)) })
        Selector("Sexo", Sexo.entries.toList(), f.sexo, { if (it == Sexo.H) "Hembra" else "Macho" }, { vm.actualizar(f.copy(sexo = it)) })
        CampoFecha("Fecha de nacimiento *", f.nacimiento, { vm.actualizar(f.copy(nacimiento = it)) }, maxima = LocalDate.now())
        CamposRaza(vm.razas, f.razaId, f.razaTexto, { vm.actualizar(f.copy(razaId = it)) }, { vm.actualizar(f.copy(razaTexto = it)) })
        Selector("Madre", listOf<String?>(null) + vm.madres.map { it.id }, f.madreId,
            { id -> vm.madres.firstOrNull { it.id == id }?.let { "${it.arete} · ${it.nombreVisible}" } ?: "Sin registro" },
            { vm.actualizar(f.copy(madreId = it)) })
        Selector("Padre", listOf<String?>(null) + vm.padres.map { it.id }, f.padreId,
            { id -> vm.padres.firstOrNull { it.id == id }?.let { "${it.arete} · ${it.nombreVisible}" } ?: "Externo o sin registro" },
            { vm.actualizar(f.copy(padreId = it)) })
        if (f.padreId == null) CampoTexto("Padre externo o pajilla de semen", f.padreExterno, { vm.actualizar(f.copy(padreExterno = it)) })
        if (f.sexo == Sexo.M) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = f.castrado, onCheckedChange = { vm.actualizar(f.copy(castrado = it)) })
            Text("Castrado (novillo)")
        }
        OutlinedTextField(f.notas, { vm.actualizar(f.copy(notas = it)) }, label = { Text("Notas") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PartoFormScreen(onAtras: () -> Unit, onGuardado: (String) -> Unit) {
    val vm = appViewModel { c, s -> PartoFormViewModel(s, c.hato, c.razas) }
    LaunchedEffect(vm.guardadoId) { vm.guardadoId?.let(onGuardado) }
    val f = vm.form
    PantallaFormulario("Parto · ${vm.madre?.nombreVisible ?: ""}", onAtras, vm.error, "Guardar parto", vm::guardar) {
        Text("Padre: " + (vm.padreDescripcion ?: "sin inseminación registrada; puedes indicarlo después editando la cría."),
            style = MaterialTheme.typography.bodyMedium)
        CampoFecha("Fecha del parto", f.fecha, { vm.actualizar(f.copy(fecha = it ?: LocalDate.now())) }, maxima = LocalDate.now())
        Selector("Sexo de la cría", Sexo.entries.toList(), f.sexo, { if (it == Sexo.H) "Hembra" else "Macho" }, { vm.actualizar(f.copy(sexo = it)) })
        CampoTexto("Arete de la cría *", f.arete, { vm.actualizar(f.copy(arete = it)) })
        CampoTexto("Nombre", f.nombre, { vm.actualizar(f.copy(nombre = it)) })
        CampoTexto("Peso al nacer (kg)", f.peso, { vm.actualizar(f.copy(peso = it)) }, numerico = true)
        CamposRaza(vm.razas, f.razaId, f.razaTexto, { vm.actualizar(f.copy(razaId = it)) }, { vm.actualizar(f.copy(razaTexto = it)) })
    }
}
