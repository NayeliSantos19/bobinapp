package com.bobinapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Marcas "bonitas" del eje Y (1, 2, 5 × 10^n). */
fun marcasEje(min: Double, max: Double, n: Int = 4): List<Double> {
    val hi = if (max <= min) min + 1 else max
    val paso0 = (hi - min) / n
    val mag = 10.0.pow(floor(log10(paso0)))
    val r = paso0 / mag
    val paso = (if (r < 1.5) 1.0 else if (r < 3) 2.0 else if (r < 7) 5.0 else 10.0) * mag
    val inicio = floor(min / paso) * paso
    val fin = ceil(hi / paso) * paso
    val marcas = mutableListOf<Double>()
    var v = inicio
    while (v <= fin + paso * 1e-6) { marcas += v; v += paso }
    return marcas
}

private const val M_IZQ = 44f
private const val M_DER = 12f
private const val M_SUP = 14f
private const val M_INF = 24f

/** Gráfico de línea con área, rejilla, último valor destacado y selección por toque/arrastre. */
@Composable
fun GraficoLinea(
    puntos: List<Pair<LocalDate, Double>>,
    color: Color,
    unidad: String,
    descripcion: String,
    modifier: Modifier = Modifier,
    decimales: Int = 0,
    desdeCero: Boolean = false,
) {
    if (puntos.size < 2) {
        Text("Aún no hay suficientes registros para graficar.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 12.dp))
        return
    }
    val medidor = rememberTextMeasurer()
    val tinta = MaterialTheme.colorScheme.onSurface
    val tenue = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outline
    val fondo = MaterialTheme.colorScheme.surface
    var seleccion by remember(puntos) { mutableStateOf<Int?>(null) }

    val x0 = puntos.first().first.toEpochDay().toDouble()
    val x1 = puntos.last().first.toEpochDay().toDouble()
    var lo = puntos.minOf { it.second }
    var hi = puntos.maxOf { it.second }
    if (desdeCero) lo = 0.0 else {
        val margen = ((hi - lo) * 0.2).takeIf { it > 0 } ?: (hi * 0.05).coerceAtLeast(1.0)
        lo = (lo - margen).coerceAtLeast(0.0); hi += margen
    }
    val marcas = marcasEje(lo, hi)
    lo = marcas.first(); hi = marcas.last()
    val estiloEje = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = tenue)

    fun DrawScope.px(i: Int): Offset {
        val (f, v) = puntos[i]
        val x = M_IZQ + ((f.toEpochDay() - x0) / (x1 - x0).coerceAtLeast(1.0)).toFloat() * (size.width - M_IZQ - M_DER)
        val y = M_SUP + (1 - ((v - lo) / (hi - lo))).toFloat() * (size.height - M_SUP - M_INF)
        return Offset(x, y)
    }
    fun cercano(x: Float, ancho: Float): Int {
        val rel = ((x - M_IZQ) / (ancho - M_IZQ - M_DER)).coerceIn(0f, 1f)
        val objetivo = x0 + rel * (x1 - x0)
        return puntos.indices.minBy { abs(puntos[it].first.toEpochDay() - objetivo) }
    }

    Box(modifier.fillMaxWidth().height(210.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .semantics { contentDescription = descripcion }
                .pointerInput(puntos) { detectTapGestures { seleccion = cercano(it.x, size.width.toFloat()) } }
                .pointerInput(puntos) {
                    detectDragGestures(onDragEnd = {}) { cambio, _ -> seleccion = cercano(cambio.position.x, size.width.toFloat()) }
                },
        ) {
            val base = M_SUP + (size.height - M_SUP - M_INF)
            marcas.forEach { m ->
                val y = M_SUP + (1 - ((m - lo) / (hi - lo))).toFloat() * (size.height - M_SUP - M_INF)
                drawLine(rejilla, Offset(M_IZQ, y), Offset(size.width - M_DER, y), strokeWidth = 1f)
                val t = medidor.measure(m.fmt(if (m % 1.0 != 0.0) 1 else 0), estiloEje)
                drawText(t, topLeft = Offset(M_IZQ - 6f - t.size.width, y - t.size.height / 2f))
            }
            listOf(0, puntos.size / 2, puntos.lastIndex).distinct().forEach { i ->
                val p = px(i)
                val t = medidor.measure(puntos[i].first.corto(), estiloEje)
                val x = when (i) { 0 -> p.x; puntos.lastIndex -> p.x - t.size.width; else -> p.x - t.size.width / 2f }
                drawText(t, topLeft = Offset(x, size.height - t.size.height.toFloat()))
            }
            val linea = Path()
            puntos.indices.forEach { i -> val p = px(i); if (i == 0) linea.moveTo(p.x, p.y) else linea.lineTo(p.x, p.y) }
            val area = Path().apply {
                addPath(linea)
                lineTo(px(puntos.lastIndex).x, base); lineTo(px(0).x, base); close()
            }
            drawPath(area, color.copy(alpha = 0.13f))
            drawPath(linea, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val ultimo = px(puntos.lastIndex)
            drawCircle(fondo, 6.dp.toPx(), ultimo)
            drawCircle(color, 4.dp.toPx(), ultimo)

            val sel = seleccion
            val (i, destacado) = if (sel != null) sel to px(sel) else puntos.lastIndex to ultimo
            if (sel != null) {
                drawLine(tenue, Offset(destacado.x, M_SUP), Offset(destacado.x, base), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(fondo, 6.dp.toPx(), destacado); drawCircle(color, 4.5.dp.toPx(), destacado)
            }
            val etiqueta = (if (sel != null) "${puntos[i].first.texto()} · " else "") + "${puntos[i].second.fmt(decimales)} $unidad"
            val t = medidor.measure(etiqueta, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel != null) fondo else tinta))
            val ancho = t.size.width + 16f
            val xCaja = (destacado.x - ancho / 2).coerceIn(0f, size.width - ancho)
            val yCaja = (destacado.y - t.size.height - 18f).coerceAtLeast(0f)
            if (sel != null) drawRoundRect(tinta, Offset(xCaja, yCaja), Size(ancho, t.size.height + 8f), CornerRadius(8f, 8f))
            drawText(t, topLeft = Offset(xCaja + 8f, yCaja + 4f))
        }
    }
}

data class Barra(val etiqueta: String, val etiquetaLarga: String, val valor: Double)

/** Gráfico de barras vertical con esquinas superiores redondeadas y selección por toque. */
@Composable
fun GraficoBarras(barras: List<Barra>, color: Color, prefijo: String, descripcion: String, modifier: Modifier = Modifier) {
    if (barras.none { it.valor > 0 }) {
        Text("Sin gastos registrados en este periodo.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 12.dp))
        return
    }
    val medidor = rememberTextMeasurer()
    val tinta = MaterialTheme.colorScheme.onSurface
    val tenue = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outline
    val fondo = MaterialTheme.colorScheme.surface
    var seleccion by remember(barras) { mutableStateOf<Int?>(null) }
    val marcas = marcasEje(0.0, barras.maxOf { it.valor })
    val hi = marcas.last()
    val estiloEje = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = tenue)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(210.dp)
            .semantics { contentDescription = descripcion }
            .pointerInput(barras) {
                detectTapGestures { o ->
                    val ancho = (size.width - M_IZQ - M_DER) / barras.size
                    val i = ((o.x - M_IZQ) / ancho).toInt()
                    seleccion = if (i in barras.indices && seleccion != i) i else null
                }
            },
    ) {
        val alto = size.height - M_SUP - M_INF
        val base = M_SUP + alto
        fun y(v: Double) = M_SUP + (1 - (v / hi)).toFloat() * alto
        marcas.forEach { m ->
            drawLine(rejilla, Offset(M_IZQ, y(m)), Offset(size.width - M_DER, y(m)), strokeWidth = 1f)
            val t = medidor.measure("$prefijo${m.fmt()}", estiloEje)
            drawText(t, topLeft = Offset(M_IZQ - 6f - t.size.width, y(m) - t.size.height / 2f))
        }
        val ancho = (size.width - M_IZQ - M_DER) / barras.size
        barras.forEachIndexed { i, b ->
            val x = M_IZQ + i * ancho + 2f
            val w = ancho - 4f
            val top = y(b.valor)
            if (b.valor > 0) {
                val r = minOf(4.dp.toPx(), w / 2, base - top)
                val path = Path().apply {
                    moveTo(x, base); lineTo(x, top + r)
                    quadraticBezierTo(x, top, x + r, top); lineTo(x + w - r, top)
                    quadraticBezierTo(x + w, top, x + w, top + r); lineTo(x + w, base); close()
                }
                drawPath(path, if (seleccion == null || seleccion == i) color else color.copy(alpha = 0.35f))
            }
            if (barras.size <= 12 || i % 2 == 0) {
                val t = medidor.measure(b.etiqueta, estiloEje)
                drawText(t, topLeft = Offset(x + w / 2 - t.size.width / 2f, size.height - t.size.height.toFloat()))
            }
        }
        seleccion?.let { i ->
            val b = barras[i]
            val t = medidor.measure("${b.etiquetaLarga} · $prefijo${b.valor.fmt(2)}", TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fondo))
            val w = t.size.width + 16f
            val cx = M_IZQ + i * ancho + ancho / 2
            val xCaja = (cx - w / 2).coerceIn(0f, size.width - w)
            val yCaja = (y(b.valor) - t.size.height - 16f).coerceAtLeast(0f)
            drawRoundRect(tinta, Offset(xCaja, yCaja), Size(w, t.size.height + 8f), CornerRadius(8f, 8f))
            drawText(t, topLeft = Offset(xCaja + 8f, yCaja + 4f))
        }
    }
}
