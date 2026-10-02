package it.ispezioni.campata.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.ispezioni.campata.data.Storage
import it.ispezioni.campata.ui.theme.BarlowCondensed
import it.ispezioni.campata.ui.theme.C
import it.ispezioni.campata.ui.theme.TagShape
import it.ispezioni.campata.ui.theme.millimetrata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FOLDER_PRESETS = listOf("Campata ", "Pila ", "Spalla ", "Impalcato ", "Appoggi ", "Giunti ", "Sopralluogo ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    dir: File,
    root: File,
    refreshKey: Int,
    onNavigate: (File) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenPhoto: (File) -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Storage.Entry?>(null) }
    var deleteFor by remember { mutableStateOf<Storage.Entry?>(null) }

    val entries by produceState<List<Storage.Entry>?>(null, dir.absolutePath, refreshKey, showHidden) {
        value = withContext(Dispatchers.IO) { Storage.list(dir, showHidden) }
    }

    val isRoot = dir.absolutePath == root.absolutePath
    BackHandler(enabled = !isRoot) { dir.parentFile?.let(onNavigate) }

    val list = entries
    val dirs = list?.count { it.isDir } ?: 0
    val photos = list?.count { it.isImage } ?: 0
    val others = (list?.size ?: 0) - dirs - photos

    Scaffold(
        containerColor = C.Paper,
        topBar = {
            Column(Modifier.fillMaxWidth().background(C.PaperHi)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BridgeMark(Modifier.size(30.dp), color = C.Ink)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isRoot) "Memoria del telefono" else dir.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = C.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = summary(dirs, photos, others),
                            style = MaterialTheme.typography.bodySmall,
                            color = C.InkSoft,
                        )
                    }
                    IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(
                            if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (showHidden) "Nascondi file nascosti" else "Mostra file nascosti",
                            tint = C.InkSoft,
                        )
                    }
                    IconButton(onClick = { onNavigate(root) }, enabled = !isRoot) {
                        Icon(Icons.Outlined.Home, contentDescription = "Vai alla memoria principale", tint = if (isRoot) C.Grid else C.InkSoft)
                    }
                }
                SpanPath(dir = dir, root = root, onNavigate = onNavigate)
                HorizontalDivider(color = C.Grid)
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { newFolderOpen = true },
                    containerColor = C.PaperHi,
                    contentColor = C.Ink,
                    shape = TagShape,
                ) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Nuova cartella") }
                ExtendedFloatingActionButton(
                    onClick = onOpenCamera,
                    containerColor = C.HiVis,
                    contentColor = C.Ink,
                    shape = TagShape,
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = { Text("Scatta qui", style = MaterialTheme.typography.titleMedium) },
                )
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).millimetrata()) {
            when {
                list == null -> Text(
                    "Lettura della cartella…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = C.InkSoft,
                    modifier = Modifier.align(Alignment.Center),
                )
                list.isEmpty() -> EmptyFolder(Modifier.align(Alignment.Center), dir.canWrite())
                else -> LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 150.dp)) {
                    items(list, key = { it.file.absolutePath }) { e ->
                        EntryRow(
                            e = e,
                            onClick = {
                                when {
                                    e.isDir -> onNavigate(e.file)
                                    e.isImage -> onOpenPhoto(e.file)
                                    else -> Storage.openExternal(ctx, e.file)
                                }
                            },
                            onLongClick = { actionsFor = e },
                        )
                    }
                }
            }
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onCreate = { name ->
                val (created, err) = Storage.createFolder(dir, name)
                if (created != null) {
                    newFolderOpen = false
                    onChanged()
                    onNavigate(created)
                }
                err
            },
        )
    }

    actionsFor?.let { e ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = C.PaperHi,
            title = { Text(e.name, style = MaterialTheme.typography.titleLarge, color = C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    DataRow("Percorso", e.file.absolutePath)
                    DataRow("Modificato", dateLabel(e.modified))
                    if (e.isDir) DataRow("Contenuto", "${e.childCount} elementi") else DataRow("Dimensione", Storage.formatSize(e.size))
                }
            },
            confirmButton = {
                TextButton(onClick = { deleteFor = e; actionsFor = null }) { Text("Elimina", color = C.Corten) }
            },
            dismissButton = {
                Row {
                    if (!e.isDir) {
                        TextButton(onClick = { Storage.share(ctx, e.file); actionsFor = null }) { Text("Condividi", color = C.Ink) }
                    }
                    TextButton(onClick = { actionsFor = null }) { Text("Chiudi", color = C.InkSoft) }
                }
            },
        )
    }

    deleteFor?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = C.PaperHi,
            title = { Text("Eliminare ${e.name}?", style = MaterialTheme.typography.titleLarge, color = C.Ink) },
            text = {
                Text(
                    if (e.isDir) "La cartella e i suoi ${e.childCount} elementi verranno eliminati dal telefono. Non è possibile annullare."
                    else "Il file verrà eliminato dal telefono. Non è possibile annullare.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = C.Ink,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = e.file
                    deleteFor = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Storage.delete(ctx, target) }
                        onChanged()
                    }
                }) { Text("Elimina definitivamente", color = C.Corten) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Annulla", color = C.InkSoft) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(e: Storage.Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    val accent = when {
        e.isDir -> C.Ink
        e.isImage -> C.Ok
        else -> C.Grid
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Paper.copy(alpha = 0.82f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.padding(vertical = 10.dp).size(52.dp).clip(RoundedCornerShape(3.dp)).background(C.PaperHi),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    e.isImage -> AsyncImage(
                        model = ImageRequest.Builder(ctx).data(e.file).size(160).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    e.isDir -> Icon(Icons.Outlined.Folder, contentDescription = null, tint = C.Ink, modifier = Modifier.size(28.dp))
                    else -> Icon(Icons.Outlined.Description, contentDescription = null, tint = C.InkSoft, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = C.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (e.isDir) "${e.childCount} elementi, ${dateLabel(e.modified)}"
                    else "${Storage.formatSize(e.size)}, ${dateLabel(e.modified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = C.InkSoft,
                )
            }
            if (e.isDir) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.InkSoft)
            }
        }
        HorizontalDivider(color = C.Grid.copy(alpha = 0.7f))
    }
}

@Composable
private fun EmptyFolder(modifier: Modifier, writable: Boolean) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyBridge(Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            if (writable) "Cartella vuota" else "Cartella non accessibile",
            fontFamily = BarlowCondensed,
            style = MaterialTheme.typography.titleLarge,
            color = C.Ink,
        )
        Text(
            if (writable) "Crea una sottocartella per l'elemento da ispezionare, oppure scatta direttamente qui."
            else "Android riserva questa cartella al sistema o a un'altra app. Torna indietro e scegli un'altra posizione.",
            style = MaterialTheme.typography.bodyLarge,
            color = C.InkSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> String?) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.PaperHi,
        title = { Text("Nuova cartella", style = MaterialTheme.typography.titleLarge, color = C.Ink) },
        text = {
            Column {
                FieldText("Nome", name, { name = it; error = null }, placeholder = "es. Pila 3 lato monte")
                QuickPicks(FOLDER_PRESETS.map { it.trim() }, selected = "", onPick = { name = "$it " })
                error?.let { Text(it, color = C.Corten, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onCreate(name) }) { Text("Crea e apri", color = C.Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = C.InkSoft) } },
    )
}

private fun summary(dirs: Int, photos: Int, others: Int): String {
    val parts = buildList {
        add(if (dirs == 1) "1 cartella" else "$dirs cartelle")
        add(if (photos == 1) "1 foto" else "$photos foto")
        if (others > 0) add(if (others == 1) "1 altro file" else "$others altri file")
    }
    return parts.joinToString(", ")
}

private fun dateLabel(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALY).format(Date(ms))
