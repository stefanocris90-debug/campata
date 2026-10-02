package it.ispezioni.campata.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ispezioni.campata.ui.theme.BarlowCondensed
import it.ispezioni.campata.ui.theme.C
import java.io.File

/**
 * Il percorso della cartella disegnato come un prospetto di ponte:
 * ogni cartella è una campata fra due pile. L'ultima (dove ti trovi) è evidenziata.
 */
@Composable
fun SpanPath(dir: File, root: File, onNavigate: (File) -> Unit) {
    val segments = remember(dir.absolutePath) {
        val out = ArrayList<File>()
        var f: File? = dir
        while (f != null && f.absolutePath != root.absolutePath) { out += f; f = f.parentFile }
        out += root
        out.reversed()
    }
    val state = rememberLazyListState()
    LaunchedEffect(segments.size, dir.absolutePath) { state.animateScrollToItem(segments.lastIndex) }

    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        itemsIndexed(segments) { i, f ->
            val current = i == segments.lastIndex
            val label = if (i == 0) "Memoria" else f.name
            Box(
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .height(58.dp)
                    .clickable(enabled = !current) { onNavigate(f) }
                    .drawBehind {
                        val deckY = 14.dp.toPx()
                        val deckH = 5.dp.toPx()
                        val pierW = 6.dp.toPx()
                        val pierTop = deckY + deckH
                        // impalcato
                        drawRect(
                            color = if (current) C.HiVis else C.Ink,
                            topLeft = Offset(0f, deckY),
                            size = Size(size.width, deckH),
                        )
                        // pila sinistra (la prima campata parte dalla spalla)
                        drawRect(C.Ink, Offset(0f, pierTop), Size(pierW, size.height - pierTop - 6.dp.toPx()))
                        if (current) {
                            drawRect(C.Ink, Offset(size.width - pierW, pierTop), Size(pierW, size.height - pierTop - 6.dp.toPx()))
                        }
                        // linea del terreno
                        drawLine(
                            color = C.InkSoft,
                            start = Offset(0f, size.height - 6.dp.toPx()),
                            end = Offset(size.width, size.height - 6.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                    .padding(start = 12.dp, end = 12.dp, top = 22.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) C.Ink else C.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
    }
}

/** Marchio: arco con pendini. */
@Composable
fun BridgeMark(modifier: Modifier, color: Color = C.HiVis) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = w * 0.09f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.72f)
            quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.92f, h * 0.72f)
        }
        drawPath(path, color, style = Stroke(width = s, cap = StrokeCap.Round))
        drawRect(color, Offset(0f, h * 0.70f), Size(w, s))
        for (x in listOf(0.3f, 0.5f, 0.7f)) {
            val yTop = if (x == 0.5f) h * 0.37f else h * 0.48f
            drawLine(color, Offset(w * x, yTop), Offset(w * x, h * 0.70f), strokeWidth = s * 0.5f)
        }
    }
}

/** Disegno per la cartella vuota: travata su tre pile, con quota della campata. */
@Composable
fun EmptyBridge(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = C.Ink
        val deckY = h * 0.38f
        drawRect(ink, Offset(0f, deckY), Size(w, h * 0.07f))
        listOf(0.04f, 0.5f, 0.96f).forEach { x ->
            drawRect(ink, Offset(w * x - 7.dp.toPx(), deckY + h * 0.07f), Size(14.dp.toPx(), h * 0.42f))
        }
        drawLine(C.InkSoft, Offset(0f, h * 0.87f), Offset(w, h * 0.87f), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // quota L
        val qy = h * 0.18f
        drawLine(C.Corten, Offset(w * 0.04f, qy), Offset(w * 0.5f, qy), strokeWidth = 2f)
        listOf(0.04f, 0.5f).forEach { x ->
            drawLine(C.Corten, Offset(w * x, qy - 8.dp.toPx()), Offset(w * x, qy + 8.dp.toPx()), strokeWidth = 2f)
        }
        // campata vuota tratteggiata in giallo
        drawRect(
            C.HiVis, Offset(w * 0.5f + 7.dp.toPx(), deckY - 4.dp.toPx()),
            Size(w * 0.46f - 14.dp.toPx(), h * 0.07f + 8.dp.toPx()),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
        )
    }
}

@Composable
fun FieldText(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    dark: Boolean = false,
) {
    val text = if (dark) Color.White else C.Ink
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Ink,
            unfocusedBorderColor = C.Grid,
            focusedLabelColor = C.Ink,
            unfocusedLabelColor = C.InkSoft,
            cursorColor = C.Ink,
            focusedTextColor = text,
            unfocusedTextColor = text,
        ),
    )
}

@Composable
fun QuickPicks(options: List<String>, selected: String, onPick: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(options) { o ->
            val on = o.equals(selected, ignoreCase = true)
            Text(
                text = o,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) C.Ink else C.InkSoft,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (on) C.HiVis else Color.Transparent)
                    .border(1.dp, if (on) C.HiVis else C.Grid, MaterialTheme.shapes.extraSmall)
                    .clickable { onPick(o) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, color: Color = C.Ink) {
    Text(
        text = text,
        fontFamily = BarlowCondensed,
        fontSize = 19.sp,
        color = color,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
fun DataRow(label: String, value: String, labelColor: Color = C.InkSoft, valueColor: Color = C.Ink) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}
