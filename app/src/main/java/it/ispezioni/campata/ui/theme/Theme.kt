package it.ispezioni.campata.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ispezioni.campata.R

/**
 * Palette ispirata al cantiere di ispezione:
 * calcestruzzo chiaro come supporto (leggibile al sole), carta millimetrata,
 * inchiostro da tavola tecnica, acciaio Corten per gli avvisi,
 * giallo alta visibilità riservato allo scatto e allo stato attivo.
 */
object C {
    val Paper = Color(0xFFDCDFDD)
    val PaperHi = Color(0xFFEEF0EE)
    val Grid = Color(0xFFB9C8CE)
    val Ink = Color(0xFF1F3A4D)
    val InkSoft = Color(0xFF5C7180)
    val Corten = Color(0xFF8C3A1E)
    val HiVis = Color(0xFFF5C400)
    val Ok = Color(0xFF2F7A4E)
    val Night = Color(0xFF0D1418)
    val NightPanel = Color(0xE6101A20)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)
val BarlowCondensed = FontFamily(
    Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
)

/** Numeri tabellari per coordinate e misure: le cifre non "ballano". */
val DataStyle = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")

/** Forma "cartellino di rilievo": un angolo smussato. */
val TagShape = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Barlow, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun CampataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Ink,
            onPrimary = Color.White,
            secondary = C.Corten,
            onSecondary = Color.White,
            tertiary = C.HiVis,
            background = C.Paper,
            onBackground = C.Ink,
            surface = C.PaperHi,
            onSurface = C.Ink,
            surfaceVariant = C.Paper,
            onSurfaceVariant = C.InkSoft,
            surfaceContainerHigh = C.PaperHi,
            surfaceContainerHighest = C.PaperHi,
            outline = C.Grid,
            error = C.Corten,
        ),
        typography = typography,
        content = content,
    )
}

/** Sfondo a carta millimetrata: maglia fine ogni 4 mm visivi, maglia grossa ogni 5. */
fun Modifier.millimetrata(): Modifier = this.drawBehind {
    val step = 20.dp.toPx()
    val fine = C.Grid.copy(alpha = 0.35f)
    val grossa = C.Grid.copy(alpha = 0.9f)
    var i = 0
    var x = 0f
    while (x <= size.width) {
        drawLine(color = if (i % 5 == 0) grossa else fine, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
        x += step; i++
    }
    i = 0
    var y = 0f
    while (y <= size.height) {
        drawLine(color = if (i % 5 == 0) grossa else fine, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
        y += step; i++
    }
}

/** Banda zebrata da cantiere, usata solo come bordo superiore del mirino. */
@Composable
fun HazardBand(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(5.dp).clipToBounds()) {
        drawRect(Color.Black)
        val h = size.height
        val passo = h * 3f
        var x = -h * 2
        while (x < size.width + h) {
            val p = Path().apply {
                moveTo(x, h); lineTo(x + h, 0f); lineTo(x + h * 2.5f, 0f); lineTo(x + h * 1.5f, h); close()
            }
            drawPath(p, C.HiVis)
            x += passo
        }
    }
}
