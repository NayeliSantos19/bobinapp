package com.bobinapp.domain

import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Reglas zootécnicas puras (sin Android), fáciles de probar con JUnit. */
object HatoCalculos {
    const val GESTACION_DIAS = 283L
    const val CICLO_ESTRAL_DIAS = 21L
    const val DIAGNOSTICO_PRENEZ_DIAS = 35L
    const val SECADO_ANTES_PARTO_DIAS = 60L
    const val DESTETE_DIAS = 240L

    fun dias(desde: LocalDate, hasta: LocalDate): Long = ChronoUnit.DAYS.between(desde, hasta)

    fun categoria(a: AnimalEntity, eventos: List<EventoEntity>, hoy: LocalDate): String {
        val edad = dias(a.nacimiento, hoy)
        val haParido = eventos.any { it.tipo == TipoEvento.PARTO }
        return if (a.sexo == Sexo.H) {
            when {
                edad < DESTETE_DIAS -> "Ternera"
                !haParido && edad < 1100 -> "Novilla"
                else -> "Vaca"
            }
        } else {
            when {
                edad < DESTETE_DIAS -> "Ternero"
                a.castrado -> "Novillo"
                edad < 730 -> "Torete"
                else -> "Toro"
            }
        }
    }

    fun edadTexto(nacimiento: LocalDate, hoy: LocalDate): String {
        val d = dias(nacimiento, hoy)
        if (d < 0) return "—"
        if (d < 60) return "$d días"
        val meses = (d / 30.44).toInt()
        if (meses < 24) return "$meses meses"
        val anios = meses / 12
        val resto = meses % 12
        return if (resto == 0) "$anios años" else "$anios años $resto m"
    }

    sealed interface EstadoReproductivo {
        val etiqueta: String
        data class Prenada(val meses: Int, val partoEstimado: LocalDate) : EstadoReproductivo {
            override val etiqueta: String get() = "Preñada · $meses m"
        }
        data object Inseminada : EstadoReproductivo { override val etiqueta = "Inseminada" }
        data object EnCelo : EstadoReproductivo { override val etiqueta = "En celo" }
        data object Posparto : EstadoReproductivo { override val etiqueta = "Posparto" }
        data object Vacia : EstadoReproductivo { override val etiqueta = "Vacía" }
    }

    private fun ultimo(eventos: List<EventoEntity>, tipo: TipoEvento) =
        eventos.filter { it.tipo == tipo && !it.eliminado }.maxByOrNull { it.fecha }

    fun estadoReproductivo(a: AnimalEntity, eventos: List<EventoEntity>, hoy: LocalDate): EstadoReproductivo? {
        if (a.sexo != Sexo.H || dias(a.nacimiento, hoy) < 300) return null
        val ins = ultimo(eventos, TipoEvento.INSEMINACION)
        val pal = ultimo(eventos, TipoEvento.PALPACION)
        val par = ultimo(eventos, TipoEvento.PARTO)
        val celo = ultimo(eventos, TipoEvento.CELO)
        if (ins != null && (par == null || par.fecha < ins.fecha)) {
            if (pal != null && pal.fecha >= ins.fecha) {
                return if (pal.resultado == ResultadoPalpacion.PRENADA) {
                    EstadoReproductivo.Prenada((dias(ins.fecha, hoy) / 30.4).toInt(), ins.fecha.plusDays(GESTACION_DIAS))
                } else EstadoReproductivo.Vacia
            }
            return EstadoReproductivo.Inseminada
        }
        if (celo != null && dias(celo.fecha, hoy) <= 1 && (ins == null || ins.fecha < celo.fecha)) return EstadoReproductivo.EnCelo
        if (par != null && dias(par.fecha, hoy) < 60) return EstadoReproductivo.Posparto
        return EstadoReproductivo.Vacia
    }

    /** Ganancia diaria de peso (kg/día) entre el primer y último pesaje de la ventana. */
    fun gananciaDiaria(eventos: List<EventoEntity>, hoy: LocalDate, ventanaDias: Long = 120): Double? {
        val p = eventos.filter { it.tipo == TipoEvento.PESO && it.kg != null && dias(it.fecha, hoy) <= ventanaDias }
            .sortedBy { it.fecha }
        if (p.size < 2) return null
        val d = dias(p.first().fecha, p.last().fecha)
        if (d < 14) return null
        return (p.last().kg!! - p.first().kg!!) / d
    }

    fun ultimoPeso(eventos: List<EventoEntity>): EventoEntity? = ultimo(eventos, TipoEvento.PESO)

    fun estaLactando(eventos: List<EventoEntity>, hoy: LocalDate): Boolean =
        ultimo(eventos, TipoEvento.LECHE)?.let { dias(it.fecha, hoy) <= 3 } ?: false
}
