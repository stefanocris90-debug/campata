package it.ispezioni.campata

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.ispezioni.campata.data.Storage
import it.ispezioni.campata.ui.BridgeMark
import it.ispezioni.campata.ui.BrowserScreen
import it.ispezioni.campata.ui.CameraScreen
import it.ispezioni.campata.ui.EmptyBridge
import it.ispezioni.campata.ui.PhotoScreen
import it.ispezioni.campata.ui.theme.C
import it.ispezioni.campata.ui.theme.CampataTheme
import it.ispezioni.campata.ui.theme.TagShape
import it.ispezioni.campata.ui.theme.millimetrata
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CampataTheme { CampataApp() } }
    }
}

@Composable
fun CampataApp() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var storageOk by remember { mutableStateOf(Storage.hasFullAccess(ctx)) }

    // Al ritorno dalle Impostazioni di Android ricontrolla il permesso.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) storageOk = Storage.hasFullAccess(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Surface(Modifier.fillMaxSize(), color = C.Paper) {
        if (!storageOk) {
            AccessScreen(onChecked = { storageOk = Storage.hasFullAccess(ctx) })
        } else {
            val rootPath = remember { Storage.root.absolutePath }
            var currentPath by rememberSaveable { mutableStateOf(rootPath) }
            var cameraOpen by rememberSaveable { mutableStateOf(false) }
            var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
            var refresh by remember { mutableIntStateOf(0) }

            val photo = photoPath
            when {
                photo != null -> PhotoScreen(
                    file = File(photo),
                    onBack = { photoPath = null; refresh++ },
                )
                cameraOpen -> CameraScreen(
                    dir = File(currentPath),
                    onClose = { cameraOpen = false; refresh++ },
                    onOpenPhoto = { photoPath = it.absolutePath },
                )
                else -> BrowserScreen(
                    dir = File(currentPath),
                    root = File(rootPath),
                    refreshKey = refresh,
                    onNavigate = { currentPath = it.absolutePath },
                    onOpenCamera = { cameraOpen = true },
                    onOpenPhoto = { photoPath = it.absolutePath },
                    onChanged = { refresh++ },
                )
            }
        }
    }
}

@Composable
private fun AccessScreen(onChecked: () -> Unit) {
    val ctx = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChecked() }

    Column(
        Modifier.fillMaxSize().millimetrata().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BridgeMark(Modifier.size(64.dp), color = C.Ink)
        Spacer(Modifier.height(18.dp))
        Text("Campata", style = MaterialTheme.typography.headlineMedium, color = C.Ink)
        Text("File manager per l'ispezione delle opere", style = MaterialTheme.typography.bodyLarge, color = C.InkSoft)
        Spacer(Modifier.height(28.dp))
        EmptyBridge(Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            "Per mostrarti tutte le cartelle del telefono e salvare le foto dove scegli, " +
                "Campata ha bisogno dell'accesso a tutti i file. Le foto restano sul telefono: " +
                "l'app non usa internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = C.Ink,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            },
            shape = TagShape,
            colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = C.HiVis),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text("Consenti accesso ai file", style = MaterialTheme.typography.titleMedium)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Nelle Impostazioni attiva l'interruttore accanto a Campata, poi torna qui.",
                style = MaterialTheme.typography.bodyMedium,
                color = C.InkSoft,
            )
        }
    }
}
