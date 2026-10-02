package it.ispezioni.campata.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.ispezioni.campata.data.Exif
import it.ispezioni.campata.data.Storage
import it.ispezioni.campata.ui.theme.C
import it.ispezioni.campata.ui.theme.TagShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PhotoScreen(file: File, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    BackHandler(onBack = onBack)

    val meta by produceState<Exif.PhotoMeta?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { Exif.read(file) }
    }

    Column(Modifier.fillMaxSize().background(C.Night)) {
        Row(
            Modifier.fillMaxWidth().background(C.Ink).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
            }
            Text(
                file.name, color = Color.White, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { Storage.share(ctx, file) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Condividi foto", tint = Color.White)
            }
        }

        AsyncImage(
            model = ImageRequest.Builder(ctx).data(file).memoryCacheKey(file.absolutePath + file.lastModified()).build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .background(C.PaperHi)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            val m = meta
            if (m == null) {
                Text("Lettura dei metadati…", color = C.InkSoft, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp))
            } else {
                m.sections.forEach { s ->
                    SectionTitle(s.title, color = if (s.title == "Ispezione") C.Corten else C.Ink)
                    s.rows.forEach { (k, v) -> DataRow(k, v) }
                }
                if (m.lat != null && m.lon != null) {
                    val lat = m.lat
                    val lon = m.lon
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(String.format(Locale.US, "%.7f, %.7f", lat, lon)))
                                Toast.makeText(ctx, "Coordinate copiate", Toast.LENGTH_SHORT).show()
                            },
                            shape = TagShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink),
                        ) { Text("Copia coordinate") }
                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse(String.format(Locale.US, "geo:%.7f,%.7f?q=%.7f,%.7f", lat, lon, lat, lon))
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(ctx, "Nessuna app di mappe installata", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = TagShape,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink),
                        ) { Text("Apri in mappa") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
