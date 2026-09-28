package com.bobinapp.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.eventos
import com.bobinapp.data.repository.AjustesStore
import com.bobinapp.data.repository.HatoRepository
import com.bobinapp.domain.AlertEngine
import com.bobinapp.domain.HatoCalculos
import com.bobinapp.domain.Severidad
import com.bobinapp.ui.components.Barra
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Productora(val animal: AnimalEntity, val promedio: Double, val dias: Int)

data class PanelEstado(
    val cargando: Boolean = true,
    val periodo: Int = 90,
    val activos: Int = 0,
    val hembras: Int = 0,
    val lechePromedio: Double = 0.0,
    val lecheCambio: Double? = null,
    val gananciaPromedio: Double? = null,
    val gananciaN: Int = 0,
    val gasto: Double = 0.0,
    val gastoCambio: Double? = null,
    val urgentes: Int = 0,
    val lecheSerie: List<Pair<LocalDate, Double>> = emptyList(),
    val opcionesPeso: List<AnimalEntity> = emptyList(),
    val animalPeso: AnimalEntity? = null,
    val seriePeso: List<Pair<LocalDate, Double>> = emptyList(),
    val gananciaAnimal: Double? = null,
    val gastoMensual: List<Barra> = emptyList(),
    val composicion: List<Pair<String, Int>> = emptyList(),
    val productoras: List<Productora> = emptyList(),
    val animalesDemo: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(private val hato: HatoRepository, private val ajustes: AjustesStore) : ViewModel() {
    val periodo = MutableStateFlow(90)
    private val pesoSeleccionado = MutableStateFlow<String?>(null)

    fun cambiarPeriodo(dias: Int) { periodo.value = dias }
    fun seleccionarPeso(id: String) { pesoSeleccionado.value = id }

    private val base = combine(hato.observarHato(), periodo, pesoSeleccionado) { h, p, s -> Triple(h, p, s) }
    private val leche = periodo.flatMapLatest { p -> hato.lecheDiaria(LocalDate.now().minusDays(2L * p)) }
    private val gastoMes = hato.gastoFarmaciaMensual(YearMonth.now().minusMonths(11).atDay(1))

    val estado: StateFlow<PanelEstado> = combine(base, leche, gastoMes, ajustes.estado) { (lista, p, sel), lecheDias, gastos, aj ->
        val hoy = LocalDate.now()
        val desde = hoy.minusDays(p - 1L)
        val desdeAnterior = desde.minusDays(p.toLong())
        val activos = lista.filter { it.animal.estado == EstadoAnimal.ACTIVA }

        val actual = lecheDias.filter { it.dia >= desde }
        val previo = lecheDias.filter { it.dia < desde && it.dia >= desdeAnterior }
        val promLeche = actual.map { it.total }.average().takeIf { !it.isNaN() } ?: 0.0
        val promPrevio = previo.map { it.total }.average().takeIf { !it.isNaN() }

        fun costo(d1: LocalDate, d2: LocalDate) = lista.sumOf { ae ->
            ae.eventos.filter { (it.tipo == TipoEvento.VACUNA || it.tipo == TipoEvento.TRATAMIENTO) && it.fecha in d1..d2 }
                .sumOf { it.costo ?: 0.0 }
        }
        val gasto = costo(desde, hoy)
        val gastoPrevio = costo(desdeAnterior, desde.minusDays(1))

        val ganancias = activos.mapNotNull { HatoCalculos.gananciaDiaria(it.eventos, hoy, p.toLong()) }.filter { it > 0 }

        val conPeso = lista.filter { ae -> ae.eventos.count { it.tipo == TipoEvento.PESO } >= 2 }
            .sortedByDescending { it.animal.nacimiento }
        val elegido = conPeso.firstOrNull { it.animal.id == sel }
            ?: conPeso.firstOrNull { HatoCalculos.dias(it.animal.nacimiento, hoy) < 900 } ?: conPeso.firstOrNull()

        val meses = (11 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) }
        val porMes = gastos.associate { it.mes to it.total }
        val barras = meses.map { m ->
            val nombre = m.month.getDisplayName(TextStyle.SHORT, Locale("es")).trimEnd('.')
            Barra(nombre, "$nombre ${m.year}", porMes[m.toString()] ?: 0.0)
        }

        val orden = listOf("Vaca", "Novilla", "Ternera", "Toro", "Torete", "Novillo", "Ternero")
        val composicion = activos.groupingBy { HatoCalculos.categoria(it.animal, it.eventos, hoy) }.eachCount()
            .toList().sortedBy { orden.indexOf(it.first) }

        val productoras = activos.filter { it.animal.sexo == Sexo.H }.mapNotNull { ae ->
            val l = ae.eventos.filter { it.tipo == TipoEvento.LECHE && it.fecha >= desde && it.litros != null }
            if (l.isEmpty()) null else Productora(ae.animal, l.sumOf { it.litros!! } / l.size, l.size)
        }.sortedByDescending { it.promedio }.take(5)

        val urgentes = AlertEngine.calcular(lista, hoy).count {
            it.clave !in aj.descartadas && (it.severidad == Severidad.VENCIDA || it.severidad == Severidad.HOY)
        }

        PanelEstado(
            cargando = false,
            periodo = p,
            activos = activos.size,
            hembras = activos.count { it.animal.sexo == Sexo.H },
            lechePromedio = promLeche,
            lecheCambio = promPrevio?.takeIf { it > 0 }?.let { (promLeche - it) / it * 100 },
            gananciaPromedio = ganancias.takeIf { it.isNotEmpty() }?.average(),
            gananciaN = ganancias.size,
            gasto = gasto,
            gastoCambio = gastoPrevio.takeIf { it > 0 }?.let { (gasto - it) / it * 100 },
            urgentes = urgentes,
            lecheSerie = actual.map { it.dia to it.total },
            opcionesPeso = conPeso.map { it.animal },
            animalPeso = elegido?.animal,
            seriePeso = elegido?.eventos?.filter { it.tipo == TipoEvento.PESO && it.kg != null }?.map { it.fecha to it.kg!! } ?: emptyList(),
            gananciaAnimal = elegido?.let { HatoCalculos.gananciaDiaria(it.eventos, hoy, p.toLong()) },
            gastoMensual = barras,
            composicion = composicion,
            productoras = productoras,
            animalesDemo = lista.count { it.animal.esDemo },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelEstado())

    fun borrarDemo() {
        viewModelScope.launch { hato.borrarDemo() }
    }
}
