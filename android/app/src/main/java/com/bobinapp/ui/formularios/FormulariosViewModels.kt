package com.bobinapp.ui.formularios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.eventos
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.domain.HatoCalculos
import java.time.LocalDate
import kotlinx.coroutines.launch

private fun String.aDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

// ---------------------------------------------------------------- Evento

data class FormEvento(
    val tipo: TipoEvento = TipoEvento.PESO,
    val fecha: LocalDate = LocalDate.now(),
    val kg: String = "",
    val litros: String = "",
    val producto: String = "",
    val dosis: String = "",
    val diagnostico: String = "",
    val retiroDias: String = "",
    val proxima: LocalDate? = null,
    val costo: String = "",
    val toro: String = "",
    val toroId: String? = null,
    val tecnico: String = "",
    val resultado: ResultadoPalpacion = ResultadoPalpacion.PRENADA,
    val nota: String = "",
)

class EventoFormViewModel(guardado: SavedStateHandle, private val hato: HatoRepository) : ViewModel() {
    private val ids: List<String> = (guardado.get<String>("ids") ?: "").split(',').filter { it.isNotBlank() }
    var animales by mutableStateOf<List<AnimalEntity>>(emptyList()); private set
    var toros by mutableStateOf<List<AnimalEntity>>(emptyList()); private set
    var form by mutableStateOf(
        FormEvento(
            tipo = guardado.get<String>("tipo")?.takeIf { it.isNotBlank() }?.let(TipoEvento::valueOf) ?: TipoEvento.PESO,
            producto = guardado.get<String>("producto").orEmpty(),
            dosis = guardado.get<String>("dosis").orEmpty(),
        )
    )
    var error by mutableStateOf<String?>(null); private set
    var listo by mutableStateOf(false); private set

    val tiposDisponibles: List<TipoEvento>
        get() = TipoEvento.entries.filter { t ->
            t != TipoEvento.PARTO && t != TipoEvento.BAJA && (!t.soloHembras || animales.all { it.sexo == Sexo.H })
        }

    init {
        viewModelScope.launch {
            animales = ids.mapNotNull { hato.obtenerAnimal(it) }
            val hoy = LocalDate.now()
            toros = hato.todosConEventos().map { it.animal }
                .filter { it.sexo == Sexo.M && !it.castrado && HatoCalculos.dias(it.nacimiento, hoy) >= 540 }
            if (form.tipo !in tiposDisponibles) form = form.copy(tipo = TipoEvento.PESO)
        }
    }

    fun actualizar(f: FormEvento) { form = f; error = null }

    fun guardar() {
        val f = form
        val hoy = LocalDate.now()
        fun falla(m: String) { error = m }
        if (animales.isEmpty()) return falla("No se encontró el animal.")
        if (f.fecha.isAfter(hoy)) return falla("La fecha no puede ser futura.")
        animales.firstOrNull { f.fecha.isBefore(it.nacimiento) }?.let { return falla("La fecha es anterior al nacimiento de ${it.arete}.") }
        if (f.proxima != null && !f.proxima.isAfter(f.fecha)) return falla("La próxima fecha debe ser posterior al evento.")
        val base = when (f.tipo) {
            TipoEvento.PESO -> {
                val kg = f.kg.aDecimal()
                if (kg == null || kg <= 0 || kg > 2000) return falla("Indica un peso entre 1 y 2000 kg.")
                EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, kg = kg)
            }
            TipoEvento.LECHE -> {
                val l = f.litros.aDecimal()
                if (l == null || l < 0 || l > 80) return falla("Indica los litros del día (0 a 80).")
                EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, litros = l)
            }
            TipoEvento.VACUNA, TipoEvento.TRATAMIENTO -> {
                if (f.producto.isBlank()) return falla("Escribe el nombre del producto.")
                if (f.tipo == TipoEvento.TRATAMIENTO && f.diagnostico.isBlank()) return falla("Escribe el diagnóstico.")
                val costo = f.costo.takeIf { it.isNotBlank() }?.let { it.aDecimal() ?: return falla("El costo no es un número válido.") }
                val retiro = f.retiroDias.takeIf { it.isNotBlank() }?.let { it.trim().toIntOrNull()?.takeIf { d -> d in 0..365 } ?: return falla("Los días de retiro deben estar entre 0 y 365.") }
                EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, producto = f.producto.trim(),
                    dosis = f.dosis.trim().ifBlank { null }, diagnostico = f.diagnostico.trim().ifBlank { null },
                    retiroDias = retiro, proximaFecha = f.proxima, costo = costo)
            }
            TipoEvento.INSEMINACION -> {
                if (f.toro.isBlank()) return falla("Indica el toro o la pajilla de semen.")
                EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, toro = f.toro.trim(), toroId = f.toroId,
                    tecnico = f.tecnico.trim().ifBlank { null })
            }
            TipoEvento.PALPACION -> EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, resultado = f.resultado)
            TipoEvento.NOTA -> {
                if (f.nota.isBlank()) return falla("Escribe la nota.")
                EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, nota = f.nota.trim())
            }
            else -> EventoEntity(id = "", animalId = "", tipo = f.tipo, fecha = f.fecha, nota = f.nota.trim().ifBlank { null })
        }
        viewModelScope.launch {
            hato.registrarEventos(animales.map { a -> base.copy(id = HatoRepository.nuevoId(), animalId = a.id, esDemo = a.esDemo) })
            listo = true
        }
    }
}

// ---------------------------------------------------------------- Animal

data class FormAnimal(
    val arete: String = "",
    val nombre: String = "",
    val sexo: Sexo = Sexo.H,
    val nacimiento: LocalDate? = null,
    val razaId: String? = null,
    val razaTexto: String = "",
    val madreId: String? = null,
    val padreId: String? = null,
    val padreExterno: String = "",
    val castrado: Boolean = false,
    val notas: String = "",
)

const val RAZA_CRUCE = "cruce"

class AnimalFormViewModel(guardado: SavedStateHandle, private val hato: HatoRepository, val razas: RazasRepository) : ViewModel() {
    private val idEditar: String? = guardado.get<String>("id")?.takeIf { it.isNotBlank() }
    val esNuevo = idEditar == null
    private var original: AnimalEntity? = null
    var form by mutableStateOf(FormAnimal(razaId = guardado.get<String>("raza")?.takeIf { it.isNotBlank() }))
    var madres by mutableStateOf<List<AnimalEntity>>(emptyList()); private set
    var padres by mutableStateOf<List<AnimalEntity>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var guardadoId by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            val todos = hato.todosConEventos().map { it.animal }.filter { it.id != idEditar }
            madres = todos.filter { it.sexo == Sexo.H }.sortedBy { it.arete }
            padres = todos.filter { it.sexo == Sexo.M && !it.castrado }.sortedBy { it.arete }
            idEditar?.let { hato.obtenerAnimal(it) }?.let { a ->
                original = a
                form = FormAnimal(
                    arete = a.arete, nombre = a.nombre.orEmpty(), sexo = a.sexo, nacimiento = a.nacimiento,
                    razaId = a.razaId ?: if (!a.razaTexto.isNullOrBlank()) RAZA_CRUCE else null, razaTexto = a.razaTexto.orEmpty(),
                    madreId = a.madreId, padreId = a.padreId, padreExterno = a.padreExterno.orEmpty(),
                    castrado = a.castrado, notas = a.notas.orEmpty(),
                )
            }
        }
    }

    fun actualizar(f: FormAnimal) { form = f; error = null }

    fun guardar() {
        val f = form
        viewModelScope.launch {
            val arete = f.arete.trim()
            val nac = f.nacimiento
            error = when {
                arete.isEmpty() -> "El número de arete es obligatorio."
                !hato.areteDisponible(arete, idEditar ?: "") -> "Ya existe un animal con el arete $arete."
                nac == null -> "Indica la fecha de nacimiento."
                nac.isAfter(LocalDate.now()) -> "La fecha de nacimiento no puede ser futura."
                else -> listOfNotNull(f.madreId, f.padreId).mapNotNull { id -> (madres + padres).firstOrNull { it.id == id } }
                    .firstOrNull { !it.nacimiento.isBefore(nac) }?.let { "${it.arete} no puede ser progenitor: no es mayor que este animal." }
            }
            if (error != null || nac == null) return@launch
            val esCruce = f.razaId == RAZA_CRUCE
            val base = original ?: AnimalEntity(id = HatoRepository.nuevoId(), arete = arete, sexo = f.sexo, nacimiento = nac)
            val animal = base.copy(
                arete = arete, nombre = f.nombre.trim().ifBlank { null }, sexo = f.sexo, nacimiento = nac,
                razaId = if (esCruce) null else f.razaId, razaTexto = if (esCruce) f.razaTexto.trim().ifBlank { null } else null,
                madreId = f.madreId, padreId = f.padreId, padreExterno = if (f.padreId == null) f.padreExterno.trim().ifBlank { null } else null,
                castrado = f.sexo == Sexo.M && f.castrado, notas = f.notas.trim().ifBlank { null },
            )
            hato.guardarAnimal(animal)
            guardadoId = animal.id
        }
    }
}

// ---------------------------------------------------------------- Parto

data class FormParto(
    val fecha: LocalDate = LocalDate.now(),
    val sexo: Sexo = Sexo.H,
    val arete: String = "",
    val nombre: String = "",
    val peso: String = "",
    val razaId: String? = null,
    val razaTexto: String = "",
)

class PartoFormViewModel(guardado: SavedStateHandle, private val hato: HatoRepository, val razas: RazasRepository) : ViewModel() {
    private val madreId: String = checkNotNull(guardado["madreId"])
    var madre by mutableStateOf<AnimalEntity?>(null); private set
    var padreDescripcion by mutableStateOf<String?>(null); private set
    private var padreId: String? = null
    private var padreExterno: String? = null
    var form by mutableStateOf(FormParto())
    var error by mutableStateOf<String?>(null); private set
    var guardadoId by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            val hatoCompleto = hato.todosConEventos()
            val m = hatoCompleto.firstOrNull { it.animal.id == madreId } ?: return@launch
            madre = m.animal
            val ins = m.eventos.lastOrNull { it.tipo == TipoEvento.INSEMINACION }
            val par = m.eventos.lastOrNull { it.tipo == TipoEvento.PARTO }
            if (ins != null && (par == null || par.fecha < ins.fecha)) {
                val toro = ins.toroId?.let { id -> hatoCompleto.firstOrNull { it.animal.id == id }?.animal }
                padreId = toro?.id
                padreExterno = if (toro == null) ins.toro else null
                padreDescripcion = (toro?.let { "${it.arete} · ${it.nombreVisible}" } ?: ins.toro.orEmpty()) + " (inseminación del ${ins.fecha})"
                val mismaRaza = toro != null && toro.razaId != null && toro.razaId == m.animal.razaId
                val nombreMadre = razas.buscar(m.animal.razaId)?.nombre ?: m.animal.razaTexto ?: "?"
                val nombrePadre = toro?.let { razas.buscar(it.razaId)?.nombre ?: it.razaTexto } ?: ins.toro ?: "?"
                form = form.copy(
                    razaId = if (mismaRaza) m.animal.razaId else RAZA_CRUCE,
                    razaTexto = if (mismaRaza) "" else "$nombreMadre × $nombrePadre",
                )
            } else {
                form = form.copy(razaId = m.animal.razaId)
            }
        }
    }

    fun actualizar(f: FormParto) { form = f; error = null }

    fun guardar() {
        val m = madre ?: return
        val f = form
        viewModelScope.launch {
            val arete = f.arete.trim()
            val peso = f.peso.takeIf { it.isNotBlank() }?.aDecimal()
            error = when {
                f.fecha.isAfter(LocalDate.now()) -> "La fecha no puede ser futura."
                arete.isEmpty() -> "Asigna un número de arete a la cría."
                !hato.areteDisponible(arete) -> "Ya existe un animal con el arete $arete."
                f.peso.isNotBlank() && (peso == null || peso < 5 || peso > 90) -> "El peso al nacer debe estar entre 5 y 90 kg."
                else -> null
            }
            if (error != null) return@launch
            val esCruce = f.razaId == RAZA_CRUCE
            val cria = AnimalEntity(
                id = HatoRepository.nuevoId(), arete = arete, nombre = f.nombre.trim().ifBlank { null }, sexo = f.sexo,
                razaId = if (esCruce) null else f.razaId, razaTexto = if (esCruce) f.razaTexto.trim().ifBlank { null } else null,
                nacimiento = f.fecha, madreId = m.id, padreId = padreId, padreExterno = padreExterno,
            )
            hato.registrarParto(m, cria, f.fecha, peso)
            guardadoId = cria.id
        }
    }
}
