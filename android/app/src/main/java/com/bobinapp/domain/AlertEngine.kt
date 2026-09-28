package com.bobinapp.domain

import com.bobinapp.data.local.entity.AnimalConEventos
import com.bobinapp.data.local.entity.EstadoAnimal
import com.bobinapp.data.local.entity.ResultadoPalpacion
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.eventos
import com.bobinapp.data.local.entity.nombreVisible
import com.bobinapp.domain.HatoCalculos.dias
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Severidad(val etiqueta: String, val orden: Int) {
    VENCIDA("Vencida", 0), HOY("Hoy", 1), PROXIMA("Próxima", 2), AVISO("Aviso", 3)
}

data class Alerta(
    /** Clave estable: la misma situación produce siempre la misma clave (para descartar y no repetir notificaciones). */
    val clave: String,
    val severidad: Severidad,
    val fecha: LocalDate,
    val dias: Long,
    val titulo: String,
    val detalle: String,
    val categoria: String,
    val animalIds: List<String>,
    /** Evento sugerido para resolver la alerta (null = solo informativa). */
    val accion: TipoEvento? = null,
    val productoSugerido: String? = null,
    val dosisSugerida: String? = null,
)

/**
 * Motor de reglas de alertas. Es código puro: recibe el hato y la fecha, devuelve alertas.
 * Lo usan la pantalla de Alertas y el AlertWorker en segundo plano.
 */
object AlertEngine {
    private val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es"))
    private fun f(d: LocalDate) = d.format(fmt)
    private fun sev(d: Long) = when {
        d < 0 -> Severidad.VENCIDA
        d == 0L -> Severidad.HOY
        d <= 14 -> Severidad.PROXIMA
        else -> Severidad.AVISO
    }

    fun calcular(hato: List<AnimalConEventos>, hoy: LocalDate): List<Alerta> {
        val out = mutableListOf<Alerta>()
        data class Grupo(val tipo: TipoEvento, val producto: String, val dosis: String?, val fecha: LocalDate, val ids: MutableList<String>)
        val grupos = linkedMapOf<String, Grupo>()

        for (ae in hato) {
            val a = ae.animal
            if (a.eliminado || a.estado != EstadoAnimal.ACTIVA) continue
            val ev = ae.eventos
            fun ultimo(t: TipoEvento) = ev.lastOrNull { it.tipo == t }
            val nombre = "${a.nombreVisible} (${a.arete})"
            val edad = dias(a.nacimiento, hoy)

            // --- Sanidad: dosis programadas, agrupadas por producto y fecha ---
            for (e in ev) {
                if ((e.tipo == TipoEvento.VACUNA || e.tipo == TipoEvento.TRATAMIENTO) && e.proximaFecha != null) {
                    val prod = e.producto.orEmpty()
                    val cubierta = ev.any { it !== e && it.tipo == e.tipo && it.producto.equals(prod, ignoreCase = true) && it.fecha > e.fecha }
                    val d = dias(hoy, e.proximaFecha)
                    if (!cubierta && d <= 14) {
                        val k = "dosis:${prod.lowercase()}:${e.proximaFecha}"
                        grupos.getOrPut(k) { Grupo(e.tipo, prod, e.dosis, e.proximaFecha, mutableListOf()) }.ids += a.id
                    }
                }
                if (e.tipo == TipoEvento.TRATAMIENTO && (e.retiroDias ?: 0) > 0) {
                    val fin = e.fecha.plusDays(e.retiroDias!!.toLong())
                    val d = dias(hoy, fin)
                    if (d >= 0) out += Alerta("retiro:${e.id}", Severidad.HOY, fin, d, "Leche y carne en retiro",
                        "$nombre: no vender leche ni carne hasta el ${f(fin)} por ${e.producto}.", "Salud", listOf(a.id))
                }
            }

            // --- Reproducción ---
            if (a.sexo == Sexo.H && edad >= 300) {
                val ins = ultimo(TipoEvento.INSEMINACION)
                val pal = ultimo(TipoEvento.PALPACION)
                val par = ultimo(TipoEvento.PARTO)
                val celo = ultimo(TipoEvento.CELO)
                val cicloAbierto = ins != null && (par == null || par.fecha < ins.fecha)
                val diagnosticada = cicloAbierto && pal != null && pal.fecha >= ins!!.fecha

                if (diagnosticada && pal!!.resultado == ResultadoPalpacion.PRENADA) {
                    val parto = ins!!.fecha.plusDays(HatoCalculos.GESTACION_DIAS)
                    val d = dias(hoy, parto)
                    if (d <= 30) out += Alerta("parto:${ins.id}", if (d < 0) Severidad.VENCIDA else if (d <= 7) Severidad.HOY else Severidad.PROXIMA,
                        parto, d, if (d < 0) "Parto atrasado" else "Parto próximo",
                        "$nombre: parto estimado el ${f(parto)} (283 días desde la inseminación). Preparar potrero de maternidad.",
                        "Reproducción", listOf(a.id), accion = TipoEvento.PARTO)
                    val secado = parto.minusDays(HatoCalculos.SECADO_ANTES_PARTO_DIAS)
                    val ds = dias(hoy, secado)
                    if (HatoCalculos.estaLactando(ev, hoy) && ds <= 7) out += Alerta("secado:${ins.id}", sev(ds), secado, ds,
                        "Secar vaca", "$nombre: secar 60 días antes del parto para que la ubre descanse.", "Reproducción", listOf(a.id))
                } else if (cicloAbierto && !diagnosticada) {
                    val palp = ins!!.fecha.plusDays(HatoCalculos.DIAGNOSTICO_PRENEZ_DIAS)
                    val d = dias(hoy, palp)
                    if (d <= 7) out += Alerta("palp:${ins.id}", sev(d), palp, d, "Diagnóstico de preñez",
                        "$nombre: palpar o hacer ecografía a los 35 días de la inseminación del ${f(ins.fecha)}.",
                        "Reproducción", listOf(a.id), accion = TipoEvento.PALPACION)
                    val retorno = ins.fecha.plusDays(HatoCalculos.CICLO_ESTRAL_DIAS)
                    val dr = dias(hoy, retorno)
                    if (dr in -2L..3L) out += Alerta("retorno:${ins.id}", if (dr <= 0) Severidad.HOY else Severidad.PROXIMA, retorno, dr,
                        "Observar retorno a celo", "$nombre: si repite celo a los 21 días, la inseminación no pegó.",
                        "Reproducción", listOf(a.id), accion = TipoEvento.CELO)
                } else if (celo != null && (ins == null || ins.fecha < celo.fecha)) {
                    val desde = dias(celo.fecha, hoy)
                    if (desde <= 1) {
                        out += Alerta("insem:${celo.id}", Severidad.HOY, celo.fecha, 0, "Inseminar hoy",
                            "$nombre entró en celo el ${f(celo.fecha)}. Regla AM/PM: inseminar unas 12 horas después de detectado.",
                            "Reproducción", listOf(a.id), accion = TipoEvento.INSEMINACION)
                    } else {
                        val ciclos = (desde + HatoCalculos.CICLO_ESTRAL_DIAS - 1) / HatoCalculos.CICLO_ESTRAL_DIAS
                        val proximo = celo.fecha.plusDays(ciclos * HatoCalculos.CICLO_ESTRAL_DIAS)
                        val d = dias(hoy, proximo)
                        if (d <= 3) out += Alerta("celo:${a.id}:$proximo", if (d == 0L) Severidad.HOY else Severidad.PROXIMA, proximo, d,
                            "Celo esperado", "$nombre: próximo celo estimado por ciclo de 21 días. Observar montas en la mañana y en la tarde.",
                            "Reproducción", listOf(a.id), accion = TipoEvento.CELO)
                    }
                } else if (par != null && dias(par.fecha, hoy) > 70 && !(celo != null && celo.fecha > par.fecha)) {
                    val limite = par.fecha.plusDays(70)
                    out += Alerta("anestro:${par.id}", Severidad.AVISO, limite, dias(hoy, limite), "Sin celo desde el parto",
                        "$nombre: ${dias(par.fecha, hoy)} días posparto sin celo registrado. Revisar condición corporal.",
                        "Reproducción", listOf(a.id), accion = TipoEvento.CELO)
                }
            }

            // --- Manejo ---
            if (edad in 220L..300L && ultimo(TipoEvento.DESTETE) == null) {
                val fd = a.nacimiento.plusDays(HatoCalculos.DESTETE_DIAS)
                val d = dias(hoy, fd)
                out += Alerta("destete:${a.id}", if (d > 14) Severidad.PROXIMA else sev(d), fd, d, "Destete",
                    "$nombre cumple 8 meses el ${f(fd)}.", "Manejo", listOf(a.id), accion = TipoEvento.DESTETE)
            }
            if (edad < 900) {
                val lp = ultimo(TipoEvento.PESO)
                val desde = if (lp != null) dias(lp.fecha, hoy) else edad
                if (desde > 45) {
                    val due = lp?.fecha?.plusDays(45) ?: hoy
                    out += Alerta("peso:${a.id}:${lp?.id ?: "0"}", Severidad.AVISO, due, dias(hoy, due), "Pesaje pendiente",
                        "$nombre: ${if (lp != null) "último pesaje hace $desde días" else "sin pesajes"}. Pesar cada 30 a 45 días para medir la ganancia.",
                        "Manejo", listOf(a.id), accion = TipoEvento.PESO)
                }
            }
        }

        for ((k, g) in grupos) {
            val d = dias(hoy, g.fecha)
            val n = g.ids.size
            out += Alerta(k, sev(d), g.fecha, d,
                (if (g.tipo == TipoEvento.VACUNA) "Vacuna: " else "Medicamento: ") + g.producto,
                (if (n > 1) "$n animales" else "1 animal") + (g.dosis?.let { " · dosis $it" } ?: "") + " · programada para el ${f(g.fecha)}.",
                "Salud", g.ids.toList(), accion = g.tipo, productoSugerido = g.producto, dosisSugerida = g.dosis)
        }
        return out.sortedWith(compareBy<Alerta> { it.severidad.orden }.thenBy { it.dias })
    }
}
