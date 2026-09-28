package com.bobinapp.data.reportes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.bobinapp.R
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity
import com.bobinapp.data.local.entity.Sexo
import com.bobinapp.data.local.entity.TipoEvento
import com.bobinapp.data.local.entity.nombreVisible
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Genera la ficha de trazabilidad de un animal en PDF (tamaño A4) con las APIs nativas de Android,
 * sin librerías externas. Sirve para ventas, ferias y certificaciones.
 */
object ReporteTrazabilidad {

    data class Datos(
        val animal: AnimalEntity,
        val raza: String,
        val categoria: String,
        val edad: String,
        val estadoReproductivo: String?,
        val genealogia: List<Pair<String, String>>,
        val crias: List<String>,
        val eventos: List<EventoEntity>,
        val foto: Bitmap?,
        val finca: String?,
    )

    private const val ANCHO = 595f
    private const val ALTO = 842f
    private const val MARGEN = 40f
    private val VERDE = Color.rgb(35, 64, 47)
    private val TINTA = Color.rgb(24, 33, 28)
    private val TENUE = Color.rgb(88, 101, 93)
    private val LINEA = Color.rgb(211, 218, 210)
    private val ARETE = Color.rgb(242, 196, 20)
    private val ES: Locale = Locale.forLanguageTag("es")
    private val FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
    private fun f(d: LocalDate) = d.format(FECHA)
    private fun n(v: Double, dec: Int = 0) = String.format(ES, "%,.${dec}f", v)

    fun generar(context: Context, d: Datos): File {
        val titulo = fuente(context, R.font.zilla_slab_bold, Typeface.DEFAULT_BOLD)
        val texto = fuente(context, R.font.karla_regular, Typeface.DEFAULT)
        val negrita = fuente(context, R.font.karla_bold, Typeface.DEFAULT_BOLD)
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_logo_vaca_ui)
        val doc = PdfDocument()
        val l = Lienzo(doc, texto, negrita, titulo, d.animal.arete)
        try {
            l.nuevaPagina()
            encabezado(l, logo, d)
            ficha(l, d)
            seccionTabla(l, "Genealogía", listOf(160f, 355f), listOf("Parentesco", "Animal"),
                d.genealogia.map { listOf(it.first, it.second) } +
                    listOf(listOf("Descendencia", if (d.crias.isEmpty()) "Sin crías registradas" else d.crias.joinToString(", "))))

            val ev = d.eventos.sortedBy { it.fecha }
            val pesos = ev.filter { it.tipo == TipoEvento.PESO && it.kg != null }
            seccionTabla(l, "Pesajes", listOf(130f, 110f, 275f), listOf("Fecha", "Peso", "Ganancia desde el anterior"),
                pesos.takeLast(15).map { p ->
                    val previo = pesos.lastOrNull { it.fecha < p.fecha }
                    val g = previo?.let { (p.kg!! - it.kg!!) / maxOf(1L, java.time.temporal.ChronoUnit.DAYS.between(it.fecha, p.fecha)) }
                    listOf(f(p.fecha), "${n(p.kg!!)} kg", g?.let { "${n(it, 2)} kg/día" } ?: "—")
                }, vacio = "Sin pesajes registrados.")

            val salud = ev.filter { it.tipo == TipoEvento.VACUNA || it.tipo == TipoEvento.TRATAMIENTO }
            seccionTabla(l, "Sanidad: vacunas y tratamientos", listOf(80f, 185f, 90f, 90f, 70f),
                listOf("Fecha", "Producto", "Dosis", "Próxima", "Costo"),
                salud.reversed().take(25).map {
                    listOf(f(it.fecha), listOfNotNull(it.diagnostico, it.producto).joinToString(": "), it.dosis ?: "—",
                        it.proximaFecha?.let(::f) ?: "—", it.costo?.let { c -> "$" + n(c, 2) } ?: "—")
                }, vacio = "Sin vacunas ni tratamientos registrados.")

            if (d.animal.sexo == Sexo.H) {
                val tipos = setOf(TipoEvento.CELO, TipoEvento.INSEMINACION, TipoEvento.PALPACION, TipoEvento.PARTO, TipoEvento.DESTETE)
                seccionTabla(l, "Reproducción", listOf(100f, 140f, 275f), listOf("Fecha", "Evento", "Detalle"),
                    ev.filter { it.tipo in tipos }.reversed().take(20).map {
                        listOf(f(it.fecha), it.tipo.etiqueta, listOfNotNull(it.toro, it.resultado?.etiqueta, it.nota).joinToString(" · ").ifBlank { "—" })
                    }, vacio = "Sin eventos reproductivos.")
                val leche = ev.filter { it.tipo == TipoEvento.LECHE && it.litros != null }
                if (leche.isNotEmpty()) {
                    val ult = leche.takeLast(30)
                    l.seccion("Producción de leche")
                    l.parrafo("${leche.size} registros diarios. Promedio de los últimos ${ult.size} días: " +
                        "${n(ult.sumOf { it.litros!! } / ult.size, 1)} L/día. Último registro: ${f(leche.last().fecha)}.")
                }
            }
            val bajas = ev.filter { it.tipo == TipoEvento.BAJA }
            if (bajas.isNotEmpty()) {
                l.seccion("Baja del inventario")
                bajas.forEach { l.parrafo("${f(it.fecha)} · ${it.nota ?: ""}") }
            }
            l.terminar()
            val carpeta = File(context.cacheDir, "reportes").apply { mkdirs() }
            val archivo = File(carpeta, "trazabilidad_${d.animal.arete.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf")
            archivo.outputStream().use { doc.writeTo(it) }
            return archivo
        } finally {
            doc.close()
        }
    }

    private fun fuente(context: Context, id: Int, respaldo: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: respaldo } catch (e: Exception) { respaldo }

    private fun encabezado(l: Lienzo, logo: Bitmap?, d: Datos) {
        val c = l.canvas
        c.drawRect(0f, 0f, ANCHO, 74f, Paint().apply { color = VERDE })
        logo?.let { c.drawBitmap(it, null, RectF(MARGEN, 13f, MARGEN + 48f, 61f), Paint(Paint.FILTER_BITMAP_FLAG)) }
        c.drawText("BOBINAPP", MARGEN + 60f, 38f, l.pintura(l.titulo, 20f, Color.WHITE))
        c.drawText("Ficha de trazabilidad", MARGEN + 60f, 56f, l.pintura(l.texto, 11f, Color.rgb(220, 230, 222)))
        val der = l.pintura(l.texto, 9.5f, Color.rgb(220, 230, 222)).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Emitida el ${f(LocalDate.now())}", ANCHO - MARGEN, 38f, der)
        d.finca?.let { c.drawText("Finca: $it", ANCHO - MARGEN, 54f, der) }
        l.y = 100f
    }

    private fun ficha(l: Lienzo, d: Datos) {
        val c = l.canvas
        val a = d.animal
        val arriba = l.y
        val anchoFoto = 190f
        val altoFoto = 142f
        val xFoto = ANCHO - MARGEN - anchoFoto
        val marco = RectF(xFoto, arriba, xFoto + anchoFoto, arriba + altoFoto)
        if (d.foto != null) {
            c.drawBitmap(d.foto, recorteCentrado(d.foto, anchoFoto / altoFoto), marco, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawRect(marco, Paint().apply { color = Color.rgb(244, 246, 242) })
            c.drawText("Sin foto", marco.centerX(), marco.centerY(), l.pintura(l.texto, 10f, TENUE).apply { textAlign = Paint.Align.CENTER })
        }
        c.drawText(a.nombreVisible.uppercase(), MARGEN, arriba + 24f, l.pintura(l.titulo, 24f, TINTA))
        // Arete amarillo
        val pa = l.pintura(l.negrita, 11f, Color.rgb(28, 26, 12))
        val w = pa.measureText(a.arete) + 14f
        c.drawRoundRect(RectF(MARGEN, arriba + 34f, MARGEN + w, arriba + 52f), 5f, 5f, Paint().apply { color = ARETE })
        c.drawText(a.arete, MARGEN + 7f, arriba + 47f, pa)
        l.y = arriba + 72f
        val campos = listOfNotNull(
            "Raza" to d.raza,
            "Sexo" to (if (a.sexo == Sexo.H) "Hembra" else "Macho") + if (a.castrado) " (castrado)" else "",
            "Categoría" to d.categoria,
            "Nacimiento" to "${f(a.nacimiento)} (${d.edad})",
            "Estado" to a.estado.etiqueta,
            d.estadoReproductivo?.let { "Reproducción" to it },
        )
        campos.forEach { (k, v) -> l.fila(k, v, anchoMax = xFoto - MARGEN - 16f) }
        l.y = maxOf(l.y, arriba + altoFoto) + 18f
    }

    private fun seccionTabla(l: Lienzo, titulo: String, anchos: List<Float>, cabecera: List<String>, filas: List<List<String>>, vacio: String = "") {
        l.seccion(titulo)
        if (filas.isEmpty()) { l.parrafo(vacio); return }
        l.filaTabla(cabecera, anchos, cabecera = true)
        filas.forEach { l.filaTabla(it, anchos) }
        l.y += 6f
    }

    private fun recorteCentrado(b: Bitmap, proporcion: Float): Rect {
        val actual = b.width.toFloat() / b.height
        return if (actual > proporcion) {
            val w = (b.height * proporcion).toInt(); val x = (b.width - w) / 2; Rect(x, 0, x + w, b.height)
        } else {
            val h = (b.width / proporcion).toInt(); val y = (b.height - h) / 2; Rect(0, y, b.width, y + h)
        }
    }

    /** Maneja páginas, posición vertical y ajuste de líneas. */
    private class Lienzo(
        val doc: PdfDocument,
        val texto: Typeface,
        val negrita: Typeface,
        val titulo: Typeface,
        val arete: String,
    ) {
        lateinit var pagina: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = MARGEN
        var numero = 0

        fun pintura(t: Typeface, tam: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = t; textSize = tam; color = col }

        fun nuevaPagina() {
            if (numero > 0) terminar()
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO.toInt(), ALTO.toInt(), numero).create())
            canvas = pagina.canvas
            y = MARGEN
            val pie = pintura(texto, 8.5f, TENUE)
            canvas.drawLine(MARGEN, ALTO - 34f, ANCHO - MARGEN, ALTO - 34f, Paint().apply { color = LINEA })
            canvas.drawText("Bobinapp · Ficha de trazabilidad del animal $arete", MARGEN, ALTO - 20f, pie)
            canvas.drawText("Página $numero", ANCHO - MARGEN, ALTO - 20f, pie.apply { textAlign = Paint.Align.RIGHT })
        }

        fun terminar() = doc.finishPage(pagina)

        fun asegurar(alto: Float) { if (y + alto > ALTO - 48f) nuevaPagina() }

        fun seccion(t: String) {
            asegurar(44f)
            y += 8f
            canvas.drawText(t.uppercase(), MARGEN, y + 12f, pintura(titulo, 13f, VERDE))
            y += 20f
            canvas.drawLine(MARGEN, y, ANCHO - MARGEN, y, Paint().apply { color = VERDE; strokeWidth = 1.2f })
            y += 8f
        }

        fun fila(k: String, v: String, anchoMax: Float) {
            val pk = pintura(negrita, 9.5f, TENUE)
            val pv = pintura(texto, 10.5f, TINTA)
            val lineas = partir(v, pv, anchoMax - 95f)
            asegurar(14f * lineas.size)
            canvas.drawText(k, MARGEN, y + 10f, pk)
            lineas.forEachIndexed { i, s -> canvas.drawText(s, MARGEN + 95f, y + 10f + i * 13f, pv) }
            y += 13f * lineas.size + 3f
        }

        fun parrafo(t: String) {
            val p = pintura(texto, 10f, TINTA)
            partir(t, p, ANCHO - 2 * MARGEN).forEach { s -> asegurar(14f); canvas.drawText(s, MARGEN, y + 10f, p); y += 13.5f }
            y += 4f
        }

        fun filaTabla(celdas: List<String>, anchos: List<Float>, cabecera: Boolean = false) {
            val p = if (cabecera) pintura(negrita, 8.5f, TENUE) else pintura(texto, 9.5f, TINTA)
            val partes = celdas.mapIndexed { i, c -> partir(if (cabecera) c.uppercase() else c, p, anchos[i] - 8f) }
            val alto = partes.maxOf { it.size } * 12f + 8f
            asegurar(alto)
            var x = MARGEN
            partes.forEachIndexed { i, lineas ->
                lineas.forEachIndexed { j, s -> canvas.drawText(s, x + 2f, y + 12f + j * 12f, p) }
                x += anchos[i]
            }
            y += alto
            canvas.drawLine(MARGEN, y - 2f, ANCHO - MARGEN, y - 2f, Paint().apply { color = LINEA; strokeWidth = if (cabecera) 1f else 0.6f })
        }

        /** Divide un texto en líneas que caben en [ancho]. */
        fun partir(t: String, p: Paint, ancho: Float): List<String> {
            if (t.isEmpty()) return listOf("")
            val out = mutableListOf<String>()
            var actual = ""
            for (palabra in t.split(" ")) {
                val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (p.measureText(prueba) <= ancho) actual = prueba
                else {
                    if (actual.isNotEmpty()) out += actual
                    actual = palabra
                    while (p.measureText(actual) > ancho && actual.length > 1) {
                        val n = p.breakText(actual, true, ancho, null).coerceAtLeast(1)
                        out += actual.substring(0, n); actual = actual.substring(n)
                    }
                }
            }
            if (actual.isNotEmpty()) out += actual
            return out
        }
    }
}
