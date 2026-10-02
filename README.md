# Campata — file manager per l'ispezione delle opere

App Android (8.0 e successivi) che sfoglia tutta la memoria del telefono, crea cartelle
e scatta foto georeferenziate con i dati di ispezione scritti dentro il file.
Funziona interamente offline.

## Ottenere l'APK (senza installare nulla sul PC)

1. Crea un repository **privato** su github.com (privato perché contiene il keystore di firma).
2. Nella pagina del repository: *Add file → Upload files*, trascina **tutto il contenuto**
   della cartella `Campata` (non la cartella stessa) e conferma con *Commit changes*.
3. Controlla che esista il file `.github/workflows/build-apk.yml`. Alcuni sistemi non caricano
   le cartelle che iniziano con il punto: in quel caso usa *Add file → Create new file*,
   scrivi come nome `.github/workflows/build-apk.yml` e incolla il contenuto del file.
4. Apri la scheda **Actions**: la compilazione parte da sola e dura circa 6–10 minuti.
5. A fine build trovi l'APK in **Releases** (colonna di destra del repository): aprila dal
   telefono e scarica `Campata-1.0.0-buildN.apk`.
6. Installa consentendo "Installa app sconosciute" per il browser. Se Play Protect avvisa
   che lo sviluppatore non è noto, scegli *Installa comunque*.

Ogni nuovo commit genera una nuova build. Tutte le build hanno la stessa firma, quindi si
installano sopra la precedente senza perdere i dati.

Se una build fallisce, apri il run in *Actions*, copia le ultime righe rosse del log e incollale
in chat: si corregge in un passaggio.

## Primo avvio

- **Accesso a tutti i file** (Android 11+): l'app apre le Impostazioni, attiva l'interruttore
  e torna indietro. Serve per leggere e scrivere in qualunque cartella.
- **Fotocamera e posizione**: richiesti al primo ingresso nel mirino.

## Uso

- Tocca una cartella per entrarci; il percorso in alto è disegnato come un impalcato,
  ogni campata è una cartella: toccala per risalire.
- Pressione lunga su un elemento: dettagli, condivisione, eliminazione (con conferma).
- *Nuova cartella*: nomi rapidi (Pila, Spalla, Campata…), poi l'app entra nella cartella creata.
- *Scatta qui*: apre il mirino nella cartella corrente.
  - Tocca la fascia in basso per impostare **Opera, Campata, Elemento, Difetto**: restano
    memorizzati per quella cartella.
  - Tocca il mirino per mettere a fuoco, usa lo zoom per i dettagli lontani, il flash per
    l'intradosso.
  - Il pallino GNSS indica la qualità del fix: verde ≤ 5 m, giallo ≤ 15 m, rosso oltre.
- Tocca una foto per la scheda completa: dati di ispezione, coordinate decimali e
  sessagesimali, quota, precisione, copia coordinate, apertura in un'app di mappe.

Nome file: `AAAAMMGG_hhmmss_<campata>_<elemento>.jpg`, ordinabile per data.

## Metadati scritti in ogni foto

| Campo EXIF | Contenuto |
|---|---|
| DateTimeOriginal, OffsetTimeOriginal | data e ora di scatto con fuso orario |
| GPSLatitude/Longitude, GPSAltitude | posizione WGS84 dal ricevitore del telefono |
| GPSHPositioningError | precisione orizzontale stimata dal telefono (m) |
| GPSMapDatum | `WGS-84` |
| Make, Model, Software | dispositivo e `Campata 1.0.0` |
| ImageDescription | `Opera: … \| Campata: … \| Elemento: … \| Difetto: …` (visibile in Esplora risorse, exiftool, Lightroom) |
| UserComment | JSON con tutti i campi, età del fix e sorgente, per elaborazioni automatiche |

I campi testo EXIF sono ASCII: le lettere accentate vengono scritte senza accento (es. "umidita").

Se non c'è un fix valido (assente o più vecchio di 2 minuti) la foto viene salvata **senza
coordinate** e l'app lo segnala: si evita di registrare una posizione sbagliata.

## Note tecniche da tenere presenti

- **Offline**: si usa il solo GPS del telefono, senza Google Play Services. Senza rete il primo
  fix (avvio a freddo, senza dati di assistenza A-GPS) può richiedere da 30 secondi a qualche
  minuto all'aperto. Sotto l'impalcato la precisione peggiora: conviene attendere il fix fuori.
- **Quota**: è l'altezza **ellissoidica** WGS84 fornita da Android, non la quota ortometrica.
  In Italia la differenza (ondulazione del geoide) è dell'ordine di 40–50 m: non usarla come
  quota s.l.m. senza conversione.
- **WGS84 e RDN2008**: le coordinate sono nel sistema del ricevitore (WGS84, realizzazione
  corrente). La differenza rispetto a RDN2008/ETRF2000 è stimabile in qualche decimetro,
  quindi trascurabile rispetto alla precisione di un telefono (alcuni metri). Il dato è una
  stima di ordine di grandezza, non una trasformazione rigorosa.
- La cartella `Android/data` e quelle di sistema non sono accessibili per scelta di Android.

## Struttura del progetto

```
app/src/main/java/it/ispezioni/campata/
  MainActivity.kt        navigazione e richiesta accesso ai file
  data/Storage.kt        elenco, creazione, eliminazione, apertura file
  data/GpsTracker.kt     GNSS offline con conteggio satelliti
  data/Inspection.kt     dati di ispezione, scrittura/lettura EXIF, nomi file
  ui/BrowserScreen.kt    esploratore
  ui/CameraScreen.kt     mirino, HUD GNSS, scatto
  ui/PhotoScreen.kt      scheda foto
  ui/Components.kt       percorso a campate e componenti grafici
  ui/theme/Theme.kt      palette, font Barlow, carta millimetrata
.github/workflows/build-apk.yml   compilazione automatica
```

Compilazione locale (alternativa): apri la cartella con Android Studio e usa
*Build → Build APK(s)*.

Firma: keystore `app/campata-release.jks`, password `campata2026`. Per usarne uno tuo,
imposta i secret `CAMPATA_KEYSTORE`, `CAMPATA_KEYSTORE_PASS`, `CAMPATA_KEY_ALIAS`.

Font Barlow © The Barlow Project Authors, licenza SIL OFL 1.1 (`FONT_LICENSE_OFL.txt`).
