package it.ispezioni.campata.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaScannerConnection
import android.os.Build
import android.os.SystemClock
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.ispezioni.campata.data.DIFETTI
import it.ispezioni.campata.data.ELEMENTI
import it.ispezioni.campata.data.Exif
import it.ispezioni.campata.data.GpsTracker
import it.ispezioni.campata.data.InspectionTags
import it.ispezioni.campata.data.Naming
import it.ispezioni.campata.data.Storage
import it.ispezioni.campata.data.TagStore
import it.ispezioni.campata.ui.theme.BarlowCondensed
import it.ispezioni.campata.ui.theme.C
import it.ispezioni.campata.ui.theme.DataStyle
import it.ispezioni.campata.ui.theme.HazardBand
import it.ispezioni.campata.ui.theme.TagShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Oltre questa età il fix non viene scritto nella foto: meglio nessuna coordinata che una sbagliata. */
private const val MAX_FIX_AGE_S = 120L

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun CameraScreen(dir: File, onClose: () -> Unit, onOpenPhoto: (File) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var camOk by remember { mutableStateOf(granted(ctx, Manifest.permission.CAMERA)) }
    var locOk by remember {
        mutableStateOf(granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camOk = granted(ctx, Manifest.permission.CAMERA)
        locOk = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    fun askPermissions() {
        val need = buildList {
            if (!granted(ctx, Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
            if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !granted(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION))
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        if (need.isNotEmpty()) launcher.launch(need.toTypedArray())
    }
    LaunchedEffect(Unit) { askPermissions() }
    BackHandler(onBack = onClose)

    // GNSS
    val gps = remember { GpsTracker(ctx) }
    DisposableEffect(locOk) {
        if (locOk) gps.start()
        onDispose { gps.stop() }
    }
    val loc by gps.location.collectAsState()
    val sats by gps.satellites.collectAsState()
    var nowNanos by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nowNanos = SystemClock.elapsedRealtimeNanos() } }

    // Dati ispezione per cartella
    val store = remember { TagStore(ctx) }
    var tags by remember(dir.absolutePath) { mutableStateOf(store.load(dir)) }
    var tagsOpen by remember { mutableStateOf(false) }

    // Fotocamera
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()
    }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var camError by remember { mutableStateOf<String?>(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var lastPhoto by remember { mutableStateOf<File?>(null) }
    var count by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Pair<String, Color>?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }
    val shutterFlash = remember { Animatable(0f) }

    LaunchedEffect(dir.absolutePath) {
        val (n, last) = withContext(Dispatchers.IO) { Storage.countImages(dir) to Storage.latestImage(dir) }
        count = n; lastPhoto = last
    }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(message) { if (message != null) { delay(3200); message = null } }
    LaunchedEffect(focusKey) { if (focusAt != null) { delay(1400); focusAt = null } }

    // Rotazione EXIF corretta anche con schermata bloccata in verticale
    DisposableEffect(Unit) {
        val l = object : OrientationEventListener(ctx) {
            override fun onOrientationChanged(o: Int) {
                if (o == OrientationEventListener.ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        l.enable()
        onDispose { l.disable() }
    }

    DisposableEffect(lifecycleOwner, camOk) {
        var provider: ProcessCameraProvider? = null
        if (camOk) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    camError = null
                } catch (e: Exception) {
                    camError = "Fotocamera non disponibile: ${e.message ?: "errore sconosciuto"}"
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            try { provider?.unbindAll() } catch (_: Exception) { }
            camera = null
        }
    }

    fun capture() {
        if (busy || camera == null) return
        busy = true
        val current = loc
        val age = current?.let { GpsTracker.ageSeconds(it, SystemClock.elapsedRealtimeNanos()) }
        val fix: Location? = current?.takeIf { age != null && age <= MAX_FIX_AGE_S && locOk }
        val snapTags = tags
        store.save(dir, snapTags)
        val target = Naming.newPhotoFile(dir, snapTags)
        scope.launch { shutterFlash.snapTo(0.75f); shutterFlash.animateTo(0f, tween(320)) }

        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(target).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val err = withContext(Dispatchers.IO) {
                            try {
                                Exif.write(target, fix, age, snapTags)
                                MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
                                null
                            } catch (e: Exception) { e.message ?: "errore EXIF" }
                        }
                        busy = false
                        lastPhoto = target
                        count++
                        message = when {
                            err != null -> "Foto salvata, ma i metadati non sono stati scritti: $err" to C.Corten
                            fix == null -> "Foto salvata senza coordinate: nessun fix GNSS valido" to C.Corten
                            else -> String.format(Locale.ITALY, "Foto salvata con coordinate, precisione ± %.0f m", fix.accuracy) to C.Ok
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    message = "Scatto non riuscito: ${exception.message ?: "errore fotocamera"}" to C.Corten
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(C.Night)) {
        if (camOk) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Reticle(Modifier.fillMaxSize().pointerInput(camera) {
                detectTapGestures { off ->
                    val cam = camera ?: return@detectTapGestures
                    val pt = previewView.meteringPointFactory.createPoint(off.x, off.y)
                    cam.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(pt).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                    )
                    focusAt = off
                    focusKey++
                }
            })
            focusAt?.let { FocusMark(it) }
            Box(Modifier.fillMaxSize().alpha(shutterFlash.value).background(Color.White))
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Serve il permesso fotocamera", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Senza questo permesso Campata non può scattare. Se l'hai negato più volte, abilitalo dalle Impostazioni di Android.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Button(
                    onClick = { askPermissions() },
                    shape = TagShape,
                    colors = ButtonDefaults.buttonColors(containerColor = C.HiVis, contentColor = C.Ink),
                ) { Text("Consenti fotocamera e posizione") }
            }
        }

        // Testata: cartella, flash, stato GNSS
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(C.NightPanel)) {
            HazardBand()
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla cartella", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(dir.name.ifBlank { "Memoria" }, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (count == 1) "1 foto in questa cartella" else "$count foto in questa cartella",
                        color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }) {
                    Icon(
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                            else -> Icons.Outlined.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White.copy(alpha = 0.7f) else C.HiVis,
                    )
                }
            }
            GnssPanel(loc = loc, sats = sats, nowNanos = nowNanos, permitted = locOk, gpsOn = gps.isGpsEnabled())
        }

        // Fascia inferiore: messaggi, dati ispezione, zoom, scatto
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            message?.let { (text, color) ->
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(TagShape)
                        .background(color)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Column(Modifier.fillMaxWidth().background(C.NightPanel).padding(bottom = 18.dp)) {
                TagStrip(tags = tags, onEdit = { tagsOpen = true })
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(Locale.ITALY, "Zoom %d%%", (zoom * 100).toInt()),
                        style = DataStyle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.width(76.dp),
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it; camera?.cameraControl?.setLinearZoom(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = C.HiVis, activeTrackColor = C.HiVis, inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(4.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable(enabled = lastPhoto != null) { lastPhoto?.let(onOpenPhoto) },
                    ) {
                        lastPhoto?.let { f ->
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(f).memoryCacheKey(f.absolutePath + f.lastModified()).size(200).build(),
                                contentDescription = "Ultima foto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Shutter(busy = busy || camera == null, onClick = ::capture)
                    Spacer(Modifier.size(56.dp))
                }
            }
        }

        camError?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp).background(C.Corten).padding(16.dp))
        }
    }

    if (tagsOpen) {
        TagsDialog(
            initial = tags,
            onDismiss = { tagsOpen = false },
            onApply = { t -> tags = t; store.save(dir, t); tagsOpen = false },
        )
    }
}

@Composable
private fun GnssPanel(loc: Location?, sats: Int, nowNanos: Long, permitted: Boolean, gpsOn: Boolean) {
    val age = loc?.let { GpsTracker.ageSeconds(it, nowNanos) }
    val (color, label) = when {
        !permitted -> C.Corten to "Posizione non consentita: le foto non avranno coordinate"
        !gpsOn -> C.Corten to "GPS spento: attivalo dalle impostazioni rapide"
        loc == null -> C.Corten to "Ricerca satelliti in corso"
        age != null && age > MAX_FIX_AGE_S -> C.Corten to "Ultimo fix di ${age / 60} min fa: attendi un fix nuovo"
        age != null && age > 15 -> C.HiVis to "Fix di ${age} s fa"
        (loc?.accuracy ?: 999f) <= 5f -> C.Ok to "Fix ottimo"
        (loc?.accuracy ?: 999f) <= 15f -> C.HiVis to "Fix buono"
        else -> C.Corten to "Fix debole: spostati lontano dall'impalcato"
    }
    val pulse by rememberInfiniteTransition(label = "gnss").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(9.dp).alpha(if (color == C.Ok) 1f else pulse).clip(CircleShape).background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loc != null) {
            Text(
                String.format(Locale.US, "%.6f   %.6f", loc.latitude, loc.longitude),
                style = DataStyle, fontSize = 22.sp, color = Color.White,
                modifier = Modifier.padding(top = 2.dp),
            )
            val parts = buildList {
                if (loc.hasAccuracy()) add(String.format(Locale.ITALY, "± %.1f m", loc.accuracy))
                if (loc.hasAltitude()) add(String.format(Locale.ITALY, "h %.1f m", loc.altitude))
                add("$sats satelliti")
                add((loc.provider ?: "").uppercase(Locale.ROOT))
            }
            Text(parts.joinToString("   "), style = DataStyle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun TagStrip(tags: InspectionTags, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tags.opera.ifBlank { "Opera non indicata" },
                fontFamily = BarlowCondensed, fontSize = 20.sp, color = C.HiVis,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(tags.campata, tags.elemento, tags.difetto).filter { it.isNotBlank() }
            Text(
                if (sub.isEmpty()) "Tocca per indicare campata, elemento e difetto" else sub.joinToString("  /  "),
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.Edit, contentDescription = "Modifica dati ispezione", tint = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Shutter(busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(82.dp).border(4.dp, if (busy) Color.White.copy(alpha = 0.4f) else C.HiVis, CircleShape).padding(8.dp)
            .clip(CircleShape).background(if (busy) Color.White.copy(alpha = 0.3f) else C.HiVis)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = "Scatta", tint = C.Ink, modifier = Modifier.size(30.dp))
    }
}

/** Mirino da rilievo: squadre d'angolo, terzi, croce centrale con scala graduata. */
@Composable
private fun Reticle(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val y0 = h * 0.22f
        val y1 = h * 0.70f
        val m = 22.dp.toPx()
        val l = 34.dp.toPx()
        val sw = 2.5.dp.toPx()
        val y = C.HiVis
        // squadre
        drawLine(y, Offset(m, y0), Offset(m + l, y0), sw); drawLine(y, Offset(m, y0), Offset(m, y0 + l), sw)
        drawLine(y, Offset(w - m, y0), Offset(w - m - l, y0), sw); drawLine(y, Offset(w - m, y0), Offset(w - m, y0 + l), sw)
        drawLine(y, Offset(m, y1), Offset(m + l, y1), sw); drawLine(y, Offset(m, y1), Offset(m, y1 - l), sw)
        drawLine(y, Offset(w - m, y1), Offset(w - m - l, y1), sw); drawLine(y, Offset(w - m, y1), Offset(w - m, y1 - l), sw)
        // terzi
        val faint = Color.White.copy(alpha = 0.18f)
        for (k in 1..2) {
            drawLine(faint, Offset(w * k / 3f, y0), Offset(w * k / 3f, y1), 1f)
            drawLine(faint, Offset(m, y0 + (y1 - y0) * k / 3f), Offset(w - m, y0 + (y1 - y0) * k / 3f), 1f)
        }
        // croce e scala
        val cx = w / 2f
        val cy = (y0 + y1) / 2f
        val gap = 10.dp.toPx()
        val arm = 26.dp.toPx()
        drawLine(Color.White, Offset(cx - arm, cy), Offset(cx - gap, cy), 2f)
        drawLine(Color.White, Offset(cx + gap, cy), Offset(cx + arm, cy), 2f)
        drawLine(Color.White, Offset(cx, cy - arm), Offset(cx, cy - gap), 2f)
        drawLine(Color.White, Offset(cx, cy + gap), Offset(cx, cy + arm), 2f)
        val tick = 8.dp.toPx()
        for (i in -6..6) {
            if (i == 0) continue
            val x = cx + i * tick * 2
            val len = if (i % 3 == 0) 9.dp.toPx() else 4.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.7f), Offset(x, cy + arm + 8.dp.toPx()), Offset(x, cy + arm + 8.dp.toPx() + len), 1.5f)
        }
    }
}

@Composable
private fun FocusMark(at: Offset) {
    Canvas(Modifier.fillMaxSize()) {
        val s = 64.dp.toPx()
        drawRect(C.HiVis, Offset(at.x - s / 2, at.y - s / 2), Size(s, s), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun TagsDialog(initial: InspectionTags, onDismiss: () -> Unit, onApply: (InspectionTags) -> Unit) {
    var opera by remember { mutableStateOf(initial.opera) }
    var campata by remember { mutableStateOf(initial.campata) }
    var elemento by remember { mutableStateOf(initial.elemento) }
    var difetto by remember { mutableStateOf(initial.difetto) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.PaperHi,
        title = { Text("Dati di ispezione", style = MaterialTheme.typography.titleLarge, color = C.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Vengono scritti in ogni foto scattata in questa cartella finché non li cambi.",
                    style = MaterialTheme.typography.bodyMedium, color = C.InkSoft,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FieldText("Opera", opera, { opera = it }, placeholder = "es. Viadotto sul fiume Neto")
                Spacer(Modifier.height(8.dp))
                FieldText("Campata", campata, { campata = it }, placeholder = "es. C3, P2, S1")
                Spacer(Modifier.height(8.dp))
                FieldText("Elemento", elemento, { elemento = it })
                QuickPicks(ELEMENTI, selected = elemento, onPick = { elemento = it })
                FieldText("Difetto", difetto, { difetto = it })
                QuickPicks(DIFETTI, selected = difetto, onPick = { difetto = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(InspectionTags(opera.trim(), campata.trim(), elemento.trim(), difetto.trim()))
            }) { Text("Applica", color = C.Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = C.InkSoft) } },
    )
}
