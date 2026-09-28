package com.bobinapp.ui.hato

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.local.entity.AnimalConEventos
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.eventos
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.data.fotos.FotosAnimales
import com.bobinapp.data.reportes.ReporteTrazabilidad
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.data.repository.Raza
import com.bobinapp.data.repository.RazasRepository
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.Alerta
import com.bobinapp.domain.HatoCalculos
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Nodo {
    data class EnHato(val animal: AnimalEntity) : Nodo
    data class Externo(val descripcion: String) : Nodo
    data object Desconocido : Nodo
}

data class Arbol(val madre: Nodo, val padre: Nodo, val abuelaMaterna: Nodo, val abueloMaterno: Nodo, val abuelaPaterna: Nodo, val abueloPaterno: Nodo)

data class DetalleEstado(
    val cargando: Boolean = true,
    val animal: AnimalEntity? = null,
    val eventos: List<EventoEntity> = emptyList(),
    val crias: List<AnimalEntity> = emptyList(),
    val arbol: Arbol? = null,
    val raza: Raza? = null,
    val categoria: String = "",
    val edad: String = "",
    val repro: HatoCalculos.EstadoReproductivo? = null,
    val ganancia: Double? = null,
    val ultimoPeso: EventoEntity? = null,
    val lechePromedio7: Double? = null,
    val alertas: List<Alerta> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnimalDetalleViewModel(
    guardado: SavedStateHandle,
    private val hato: HatoRepository,
    private val razas: RazasRepository,
    private val ajustes: AjustesStore,
    private val fotos: FotosAnimales,
) : ViewModel() {
    val id: String = checkNotNull(guardado["id"])

    val estado: StateFlow<DetalleEstado> = combine(
        hato.observarAnimal(id), hato.observarEventos(id), hato.observarCrias(id), ajustes.estado,
    ) { a, ev, crias, aj -> Quad(a, ev, crias, aj.descartadas) }
        .mapLatest { (a, ev, crias, descartadas) ->
            if (a == null) return@mapLatest DetalleEstado(cargando = false)
            val hoy = LocalDate.now()
            val asc = ev.sortedBy { it.fecha }
            val ancestros = hato.ancestros(a.id, 2).associateBy { it.id }
            fun nodo(id: String?, externo: String?): Nodo =
                id?.let { ancestros[it] }?.let { Nodo.EnHato(it) }
                    ?: externo?.takeIf { it.isNotBlank() }?.let { Nodo.Externo(it) } ?: Nodo.Desconocido
            val madre = a.madreId?.let { ancestros[it] }
            val padre = a.padreId?.let { ancestros[it] }
            val leche7 = asc.filter { it.tipo == TipoEvento.LECHE && HatoCalculos.dias(it.fecha, hoy) < 7 && it.litros != null }
            DetalleEstado(
                cargando = false,
                animal = a,
                eventos = ev,
                crias = crias,
                arbol = Arbol(
                    madre = nodo(a.madreId, null), padre = nodo(a.padreId, a.padreExterno),
                    abuelaMaterna = nodo(madre?.madreId, null), abueloMaterno = nodo(madre?.padreId, madre?.padreExterno),
                    abuelaPaterna = nodo(padre?.madreId, null), abueloPaterno = nodo(padre?.padreId, padre?.padreExterno),
                ),
                raza = razas.buscar(a.razaId),
                categoria = HatoCalculos.categoria(a, asc, hoy),
                edad = HatoCalculos.edadTexto(a.nacimiento, hoy),
                repro = HatoCalculos.estadoReproductivo(a, asc, hoy),
                ganancia = HatoCalculos.gananciaDiaria(asc, hoy),
                ultimoPeso = HatoCalculos.ultimoPeso(asc),
                lechePromedio7 = leche7.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.litros!! } / l.size },
                alertas = AlertEngine.calcular(listOf(AnimalConEventos(a, ev)), hoy).filter { it.clave !in descartadas },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleEstado())

    fun borrarEvento(e: EventoEntity) = viewModelScope.launch { hato.borrarEvento(e) }

    fun darDeBaja(estado: EstadoAnimal, fecha: LocalDate, detalle: String) = viewModelScope.launch {
        this@AnimalDetalleViewModel.estado.value.animal?.let { hato.darDeBaja(it, estado, fecha, detalle) }
    }

    fun cambiarFoto(foto: Bitmap) = viewModelScope.launch {
        estado.value.animal?.let { hato.cambiarFoto(it, foto) }
    }

    fun quitarFoto() = viewModelScope.launch {
        estado.value.animal?.let { hato.quitarFoto(it) }
    }

    /** Genera la ficha de trazabilidad en PDF; devuelve null si el animal aún no cargó. */
    suspend fun crearReporte(context: Context): File? = withContext(Dispatchers.IO) {
        val e = estado.value
        val a = e.animal ?: return@withContext null
        fun texto(n: Nodo) = when (n) {
            is Nodo.EnHato -> "${n.animal.arete} · ${n.animal.nombreVisible}"
            is Nodo.Externo -> "${n.descripcion} (externo)"
            Nodo.Desconocido -> "Sin registro"
        }
        val genealogia = e.arbol?.let { t ->
            listOf(
                "Madre" to texto(t.madre), "Padre" to texto(t.padre),
                "Abuela materna" to texto(t.abuelaMaterna), "Abuelo materno" to texto(t.abueloMaterno),
                "Abuela paterna" to texto(t.abuelaPaterna), "Abuelo paterno" to texto(t.abueloPaterno),
            )
        }.orEmpty()
        ReporteTrazabilidad.generar(
            context,
            ReporteTrazabilidad.Datos(
                animal = a,
                raza = e.raza?.nombre ?: "Sin raza definida",
                categoria = e.categoria,
                edad = e.edad,
                estadoReproductivo = e.repro?.etiqueta,
                genealogia = genealogia,
                crias = e.crias.map { "${it.arete} · ${it.nombreVisible}" },
                eventos = e.eventos,
                foto = if (a.fotoActualizadaEn != null) fotos.miniatura(a.id, 800) else null,
                finca = ajustes.actual.fincaNombre,
            ),
        )
    }

    fun eliminar(alTerminar: () -> Unit) = viewModelScope.launch {
        estado.value.animal?.let { hato.eliminarAnimal(it) }
        alTerminar()
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
