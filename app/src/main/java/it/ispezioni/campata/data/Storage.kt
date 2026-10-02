package it.ispezioni.campata.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object Storage {

    val root: File get() = Environment.getExternalStorageDirectory()

    /** Accesso completo alla memoria condivisa (Android 11+: "Accesso a tutti i file"). */
    fun hasFullAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    data class Entry(
        val file: File,
        val isDir: Boolean,
        val name: String,
        val size: Long,
        val modified: Long,
        val childCount: Int,
        val isImage: Boolean,
    )

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

    fun isImage(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in imageExt

    fun list(dir: File, showHidden: Boolean): List<Entry> {
        val files = dir.listFiles() ?: return emptyList()
        return files.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                val d = f.isDirectory
                Entry(
                    file = f,
                    isDir = d,
                    name = f.name,
                    size = if (d) 0L else f.length(),
                    modified = f.lastModified(),
                    childCount = if (d) (f.list()?.count { showHidden || !it.startsWith(".") } ?: 0) else 0,
                    isImage = !d && isImage(f.name),
                )
            }
            .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()
    }

    fun countImages(dir: File): Int = dir.listFiles()?.count { it.isFile && isImage(it.name) } ?: 0

    fun latestImage(dir: File): File? =
        dir.listFiles()?.filter { it.isFile && isImage(it.name) }?.maxByOrNull { it.lastModified() }

    /** Restituisce il messaggio d'errore, oppure null se la cartella è stata creata. */
    fun createFolder(parent: File, rawName: String): Pair<File?, String?> {
        val name = rawName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (name.isEmpty() || name == "." || name == "..") return null to "Scrivi un nome per la cartella."
        val f = File(parent, name)
        if (f.exists()) return null to "Esiste già un elemento chiamato \"$name\" in questa cartella."
        return if (f.mkdirs()) f to null else null to "Android non ha permesso di creare la cartella qui. Prova in un'altra posizione."
    }

    fun delete(ctx: Context, f: File): Boolean {
        val paths = if (f.isDirectory) f.walkBottomUp().map { it.absolutePath }.toList() else listOf(f.absolutePath)
        val ok = f.deleteRecursively()
        MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
        return ok
    }

    fun uriFor(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun mimeOf(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "*/*"

    fun openExternal(ctx: Context, f: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(ctx, f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(intent, "Apri con").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Nessuna app installata apre questo tipo di file.", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(ctx, "Questo percorso non può essere condiviso con altre app.", Toast.LENGTH_LONG).show()
        }
    }

    fun share(ctx: Context, f: File) {
        try {
            val intent = Intent(Intent.ACTION_SEND)
                .setType(mimeOf(f))
                .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Condivisione non disponibile per questo file.", Toast.LENGTH_LONG).show()
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ITALY, "%.0f kB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.ITALY, "%.1f MB", bytes / 1048576.0)
        else -> String.format(Locale.ITALY, "%.2f GB", bytes / 1073741824.0)
    }
}
