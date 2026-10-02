package it.ispezioni.campata.data

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class InspectionTags(
    val opera: String = "",
    val campata: String = "",
    val elemento: String = "",
    val difetto: String = "",
) {
    fun describe(): String =
        "Opera: ${opera.ifBlank { "-" }} | Campata: ${campata.ifBlank { "-" }} | " +
            "Elemento: ${elemento.ifBlank { "-" }} | Difetto: ${difetto.ifBlank { "-" }}"
}

/** Elementi e difetti ricorrenti nelle schede di ispezione dei ponti. */
val ELEMENTI = listOf(
    "Soletta", "Trave", "Traverso", "Impalcato", "Pila", "Pulvino", "Spalla", "Baggiolo",
    "Appoggio", "Giunto", "Cordolo", "Sbalzo", "Arco", "Fondazione", "Barriera", "Smaltimento acque",
)
val DIFETTI = listOf(
    "Fessurazioni", "Distacco copriferro", "Armatura scoperta/ossidata", "Macchie di umidità",
    "Efflorescenze", "Vespai", "Dilavamento", "Ammaloramento cls", "Corrosione", "Deformazione",
    "Spostamento appoggio", "Erosione/scalzamento", "Vegetazione", "Nessun difetto",
)

/** Ricorda i dati di ispezione per ogni cartella. */
class TagStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("dati_ispezione", Context.MODE_PRIVATE)

    fun load(dir: File): InspectionTags {
        val k = dir.absolutePath
        return InspectionTags(
            opera = p.getString("$k|opera", null) ?: dir.name,
            campata = p.getString("$k|campata", null) ?: "",
            elemento = p.getString("$k|elemento", null) ?: "",
            difetto = p.getString("$k|difetto", null) ?: "",
        )
    }

    fun save(dir: File, t: InspectionTags) {
        val k = dir.absolutePath
        p.edit()
            .putString("$k|opera", t.opera)
            .putString("$k|campata", t.campata)
            .putString("$k|elemento", t.elemento)
            .putString("$k|difetto", t.difetto)
            .apply()
    }
}

object Naming {
    /** 20261001_143205_C3_Trave.jpg: ordinabile per data, leggibile a colpo d'occhio. */
    fun newPhotoFile(dir: File, t: InspectionTags): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val parts = listOf(t.campata, t.elemento).map(::slug).filter { it.isNotEmpty() }
        val base = (listOf(stamp) + parts).joinToString("_")
        var f = File(dir, "$base.jpg")
        var i = 1
        while (f.exists()) { f = File(dir, "${base}_$i.jpg"); i++ }
        return f
    }

    private fun slug(s: String): String =
        Exif.ascii(s).trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(24)
}

object Exif {

    /** I campi testo EXIF sono ASCII: togliamo accenti e caratteri non stampabili. */
    fun ascii(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\x20-\\x7E]"), "?")

    /**
     * Scrive data/ora, fuso, dispositivo, GPS (WGS84) e dati di ispezione.
     * ImageDescription: testo leggibile da Windows, exiftool, Lightroom.
     * UserComment: JSON completo per elaborazioni successive.
     */
    fun write(file: File, loc: Location?, fixAgeS: Long?, t: InspectionTags) {
        val exif = ExifInterface(file.absolutePath)
        val now = Date()
        val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(now)
        val offset = SimpleDateFormat("XXX", Locale.US).format(now)

        if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isNullOrBlank())
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        if (exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED).isNullOrBlank())
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        if (exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank())
            exif.setAttribute(ExifInterface.TAG_MAKE, ascii(Build.MANUFACTURER))
        if (exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank())
            exif.setAttribute(ExifInterface.TAG_MODEL, ascii(Build.MODEL))
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Campata 1.0.0")

        if (loc != null) {
            exif.setGpsInfo(loc)
            exif.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, "WGS-84")
            if (loc.hasAccuracy()) {
                exif.setAttribute("GPSHPositioningError", "${(loc.accuracy * 100).roundToInt()}/100")
            }
        }

        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, ascii(t.describe()))

        val json = JSONObject().apply {
            put("app", "Campata")
            put("opera", t.opera)
            put("campata", t.campata)
            put("elemento", t.elemento)
            put("difetto", t.difetto)
            put("scatto", stamp + offset)
            put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            if (loc != null) {
                put("datum", "WGS84")
                put("lat", loc.latitude)
                put("lon", loc.longitude)
                if (loc.hasAltitude()) put("h_ellissoidica_m", (loc.altitude * 10).roundToInt() / 10.0)
                if (loc.hasAccuracy()) put("accuratezza_m", (loc.accuracy * 10).roundToInt() / 10.0)
                put("provider", loc.provider ?: "")
                put("eta_fix_s", fixAgeS ?: -1L)
            } else {
                put("gps", "assente")
            }
        }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, ascii(json.toString()))
        exif.saveAttributes()
    }

    data class Section(val title: String, val rows: List<Pair<String, String>>)
    data class PhotoMeta(val sections: List<Section>, val lat: Double?, val lon: Double?)

    fun read(f: File): PhotoMeta {
        val exif = try {
            ExifInterface(f.absolutePath)
        } catch (e: Exception) {
            return PhotoMeta(listOf(Section("Errore", listOf("EXIF" to (e.message ?: "file illeggibile")))), null, null)
        }

        val desc = parseDescription(exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))
        val json = parseJson(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))

        val ispezione = listOf("Opera", "Campata", "Elemento", "Difetto").map { k ->
            k to (desc[k]?.takeIf { it != "-" } ?: json?.optString(k.lowercase(Locale.ROOT))?.takeIf { it.isNotBlank() } ?: "non indicato")
        }

        val ll = exif.latLong
        val posizione = mutableListOf<Pair<String, String>>()
        if (ll != null) {
            posizione += "Latitudine" to String.format(Locale.US, "%.7f°", ll[0])
            posizione += "Longitudine" to String.format(Locale.US, "%.7f°", ll[1])
            posizione += "Sessagesimali" to "${dms(ll[0], true)}  ${dms(ll[1], false)}"
            val alt = exif.getAltitude(Double.NaN)
            if (!alt.isNaN()) posizione += "Quota ellissoidica" to String.format(Locale.ITALY, "%.1f m", alt)
            val acc = rational(exif.getAttribute("GPSHPositioningError"))
                ?: json?.optDouble("accuratezza_m")?.takeIf { !it.isNaN() }
            if (acc != null) posizione += "Precisione orizzontale" to String.format(Locale.ITALY, "± %.1f m", acc)
            posizione += "Datum" to (exif.getAttribute(ExifInterface.TAG_GPS_MAP_DATUM) ?: "WGS-84")
            json?.optString("provider")?.takeIf { it.isNotBlank() }?.let { posizione += "Sorgente" to it.uppercase(Locale.ROOT) }
        } else {
            posizione += "Coordinate" to "assenti: scatto senza fix GNSS"
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        val make = listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
            .joinToString(" ").ifBlank { "non registrato" }
        val scatto = listOf(
            "Data e ora" to formatExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)),
            "Dispositivo" to make,
            "Risoluzione" to if (opts.outWidth > 0) "${opts.outWidth} × ${opts.outHeight} px" else "n.d.",
            "Dimensione" to Storage.formatSize(f.length()),
            "Percorso" to f.absolutePath,
        )

        return PhotoMeta(
            listOf(Section("Ispezione", ispezione), Section("Posizione", posizione), Section("Scatto", scatto)),
            ll?.get(0), ll?.get(1),
        )
    }

    private fun parseDescription(s: String?): Map<String, String> {
        if (s.isNullOrBlank()) return emptyMap()
        return s.split("|").mapNotNull { part ->
            val i = part.indexOf(':')
            if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }.toMap()
    }

    private fun parseJson(s: String?): JSONObject? {
        if (s.isNullOrBlank()) return null
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(s.substring(a, b + 1)) } catch (_: Exception) { null }
    }

    private fun rational(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        val p = s.split("/")
        return try {
            if (p.size == 2) p[0].toDouble() / p[1].toDouble() else s.toDouble()
        } catch (_: Exception) { null }
    }

    private fun formatExifDate(s: String?, offset: String?): String {
        if (s.isNullOrBlank()) return "non registrata"
        return try {
            val d = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(s)
            SimpleDateFormat("d MMMM yyyy, HH:mm:ss", Locale.ITALY).format(d!!) + (offset?.let { " (UTC$it)" } ?: "")
        } catch (_: Exception) { s }
    }

    fun dms(v: Double, isLat: Boolean): String {
        val emi = if (isLat) (if (v >= 0) "N" else "S") else (if (v >= 0) "E" else "O")
        val a = abs(v)
        val g = a.toInt()
        val mf = (a - g) * 60
        val m = mf.toInt()
        val s = (mf - m) * 60
        return String.format(Locale.US, "%d°%02d'%05.2f\"%s", g, m, s, emi)
    }
}
