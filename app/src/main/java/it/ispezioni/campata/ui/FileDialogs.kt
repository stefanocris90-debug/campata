package it.ispezioni.campata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.ispezioni.campata.data.Storage
import it.ispezioni.campata.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val FOLDER_NAME_PRESETS = listOf("Campata", "Pila", "Spalla", "Impalcato", "Appoggi", "Giunti", "Sopralluogo")

/**
 * Selettore della cartella di destinazione: si naviga fra le cartelle come nell'esploratore
 * e si conferma con "Sposta qui". Permette di creare al volo la cartella di destinazione.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    start: File,
    root: File,
    source: File?,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit,
) {
    var cur by remember { mutableStateOf(start) }
    var reload by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val atRoot = cur.absolutePath == root.absolutePath
    val isSource = source != null && cur.absolutePath == source.absolutePath

    val folders by produceState<List<File>?>(null, cur.absolutePath, reload) {
        value = withContext(Dispatchers.IO) { Storage.subfolders(cur) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.PaperHi,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = C.Ink) },
        text = {
            Column {
                Text(
                    if (atRoot) "Memoria del telefono" else cur.absolutePath.removePrefix(root.absolutePath).trimStart('/'),
                    style = MaterialTheme.typography.bodyMedium, color = C.InkSoft,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.padding(top = 6.dp))
                HorizontalDivider(color = C.Grid)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (!atRoot) {
                        item {
                            PickerRow(Icons.Outlined.ArrowUpward, "Cartella superiore") {
                                cur.parentFile?.let { cur = it; error = null }
                            }
                        }
                    }
                    val list = folders
                    when {
                        list == null -> item { Text("Lettura…", color = C.InkSoft, modifier = Modifier.padding(12.dp)) }
                        list.isEmpty() -> item {
                            Text("Nessuna sottocartella", color = C.InkSoft, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp))
                        }
                        else -> items(list, key = { it.absolutePath }) { f ->
                            PickerRow(Icons.Outlined.Folder, f.name, chevron = true) { cur = f; error = null }
                        }
                    }
                }
                HorizontalDivider(color = C.Grid)
                if (creating) {
                    Spacer(Modifier.padding(top = 6.dp))
                    FieldText("Nuova cartella qui", newName, { newName = it; error = null }, placeholder = "es. Pila 3")
                    QuickPicks(FOLDER_NAME_PRESETS, selected = "", onPick = { newName = "$it " })
                    TextButton(onClick = {
                        val (created, err) = Storage.createFolder(cur, newName)
                        if (created != null) { cur = created; creating = false; newName = ""; reload++ } else error = err
                    }) { Text("Crea ed entra", color = C.Ink) }
                } else {
                    PickerRow(Icons.Outlined.CreateNewFolder, "Nuova cartella qui") { creating = true }
                }
                if (isSource) {
                    Text("È la cartella in cui si trovano già i file.", color = C.InkSoft,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                error?.let { Text(it, color = C.Corten, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(cur) }, enabled = !isSource && cur.canWrite()) {
                Text("Sposta qui", color = if (!isSource && cur.canWrite()) C.Ink else C.Grid)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = C.InkSoft) } },
    )
}

@Composable
private fun PickerRow(icon: ImageVector, label: String, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = C.Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = C.Ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = C.InkSoft)
    }
}

/** Rinomina cartella o file. Per i file si modifica il nome senza estensione, che viene conservata. */
@Composable
fun RenameDialog(file: File, onDismiss: () -> Unit, onRename: (String) -> String?) {
    val isDir = file.isDirectory
    var name by remember { mutableStateOf(if (isDir) file.name else file.nameWithoutExtension) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.PaperHi,
        title = { Text(if (isDir) "Rinomina cartella" else "Rinomina file", style = MaterialTheme.typography.titleLarge, color = C.Ink) },
        text = {
            Column {
                FieldText("Nuovo nome", name, { name = it; error = null })
                if (isDir) {
                    QuickPicks(FOLDER_NAME_PRESETS, selected = "", onPick = { name = "$it " })
                    Text(
                        "I dati di ispezione memorizzati per la cartella la seguono. Le foto già scattate non cambiano.",
                        style = MaterialTheme.typography.bodySmall, color = C.InkSoft,
                    )
                } else if (file.extension.isNotEmpty()) {
                    Text("L'estensione .${file.extension} viene mantenuta.", style = MaterialTheme.typography.bodySmall, color = C.InkSoft)
                }
                error?.let { Text(it, color = C.Corten, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onRename(name) }) { Text("Rinomina", color = C.Ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = C.InkSoft) } },
    )
}

/** Riga di azione nei menu a pressione lunga. */
@Composable
fun ActionRow(icon: ImageVector, label: String, color: androidx.compose.ui.graphics.Color = C.Ink, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.PaperHi).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
