package com.bobinapp.ui.theme

import com.bobinapp.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta inspirada en el potrero y el arete ganadero amarillo. */
val Potrero = Color(0xFF23402F)
val PotreroClaro = Color(0xFF2F5A42)
val Arete = Color(0xFFF2C414)
val AreteTinta = Color(0xFF1C1A0C)

@Immutable
data class ColoresSemanticos(
    val carne: Color,
    val leche: Color,
    val doble: Color,
    val vencida: Color,
    val hoy: Color,
    val proxima: Color,
    val aviso: Color,
)

private val SemanticosClaros = ColoresSemanticos(
    carne = Color(0xFFA2462A), leche = Color(0xFF2A6598), doble = Color(0xFF3A764A),
    vencida = Color(0xFFA2462A), hoy = Color(0xFF9A6A00), proxima = Arete, aviso = Color(0xFF8A968E),
)
private val SemanticosOscuros = ColoresSemanticos(
    carne = Color(0xFFE38D6B), leche = Color(0xFF80B5E2), doble = Color(0xFF8AC698),
    vencida = Color(0xFFE38D6B), hoy = Color(0xFFE6B94A), proxima = Arete, aviso = Color(0xFF6F7C74),
)

val LocalSemanticos = staticCompositionLocalOf { SemanticosClaros }

private val Claro = lightColorScheme(
    primary = Potrero, onPrimary = Color.White,
    secondary = Arete, onSecondary = AreteTinta,
    secondaryContainer = Color(0xFFFBEBB0), onSecondaryContainer = AreteTinta,
    background = Color(0xFFECEFEA), onBackground = Color(0xFF18211C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF18211C),
    surfaceVariant = Color(0xFFF4F6F2), onSurfaceVariant = Color(0xFF58655D),
    outline = Color(0xFFD3DAD2), error = Color(0xFFA2462A),
)
private val Oscuro = darkColorScheme(
    primary = Color(0xFF8AC698), onPrimary = Color(0xFF0F1A13),
    secondary = Arete, onSecondary = AreteTinta,
    secondaryContainer = Color(0xFF4A3F10), onSecondaryContainer = Color(0xFFFBEBB0),
    background = Color(0xFF101512), onBackground = Color(0xFFE5EBE6),
    surface = Color(0xFF18201B), onSurface = Color(0xFFE5EBE6),
    surfaceVariant = Color(0xFF1F2823), onSurfaceVariant = Color(0xFF9AA79F),
    outline = Color(0xFF2E3933), error = Color(0xFFE38D6B),
)

/** Zilla Slab (títulos): letra con remates rectos, como la de los sacos de alimento y los hierros de marcar. */
val ZillaSlab = FontFamily(
    Font(R.font.zilla_slab_semibold, FontWeight.SemiBold),
    Font(R.font.zilla_slab_bold, FontWeight.Bold),
)

/** Karla (texto): sans serif con carácter y muy legible en pantallas pequeñas. */
val Karla = FontFamily(
    Font(R.font.karla_regular, FontWeight.Normal),
    Font(R.font.karla_bold, FontWeight.Bold),
)

private val base = Typography()
private val Tipografia = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.3.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontFamily = ZillaSlab, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Karla, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Karla),
    bodyMedium = base.bodyMedium.copy(fontFamily = Karla),
    bodySmall = base.bodySmall.copy(fontFamily = Karla),
    labelLarge = base.labelLarge.copy(fontFamily = Karla, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = Karla),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun BobinappTheme(oscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSemanticos provides if (oscuro) SemanticosOscuros else SemanticosClaros,
    ) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, content = content)
    }
}
