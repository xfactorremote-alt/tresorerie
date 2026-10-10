package org.jpgrenoble.tresorerie

import kotlinx.datetime.toInstant
import io.github.jan.supabase.auth.auth
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

actual fun sha256(texte: String): String =
    MessageDigest.getInstance("SHA-256").digest(texte.toByteArray()).joinToString("") { "%02x".format(it) }

actual fun signatureEnPng(traits: List<List<Offset>>, largeur: Int, hauteur: Int): ByteArray {
    val bmp = Bitmap.createBitmap(largeur.coerceAtLeast(1), hauteur.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    val p = Paint().apply {
        color = Color.rgb(28, 27, 26); strokeWidth = 6f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    traits.filter { it.size > 1 }.forEach { t ->
        val chemin = Path().apply { moveTo(t[0].x, t[0].y); t.drop(1).forEach { lineTo(it.x, it.y) } }
        c.drawPath(chemin, p)
    }
    return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
}

private fun reduirePhoto(octets: ByteArray, max: Int = 1600): ByteArray {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
    var echantillon = 1
    while (bornes.outWidth / (echantillon * 2) >= max || bornes.outHeight / (echantillon * 2) >= max) echantillon *= 2
    val bmp = BitmapFactory.decodeByteArray(octets, 0, octets.size, BitmapFactory.Options().apply { inSampleSize = echantillon })
        ?: throw IllegalArgumentException("Image illisible")
    val r = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
    val finale = if (r < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true) else bmp
    return ByteArrayOutputStream().use { finale.compress(Bitmap.CompressFormat.JPEG, 75, it); it.toByteArray() }
}

@Composable
actual fun rememberChoixFichier(pdfAccepte: Boolean, quandChoisi: (Fichier?, String?) -> Unit): () -> Unit {
    val contexte = LocalContext.current
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { quandChoisi(null, null); return@rememberLauncherForActivityResult }
        try {
            val mime = contexte.contentResolver.getType(uri) ?: ""
            val octets = contexte.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Fichier illisible")
            val nom = try {
                contexte.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } catch (_: Exception) { null }
            when {
                mime == "application/pdf" -> {
                    if (octets.size > 3 * 1024 * 1024) quandChoisi(null, "Ce PDF dépasse 3 Mo. Photographiez plutôt la page.")
                    else quandChoisi(Fichier(octets, "application/pdf", "pdf", nom), null)
                }
                mime.startsWith("image/") -> quandChoisi(Fichier(reduirePhoto(octets), "image/jpeg", "jpg", nom), null)
                else -> quandChoisi(null, "Format accepté : photo ou PDF")
            }
        } catch (e: Exception) { quandChoisi(null, e.message ?: "Fichier illisible") }
    }
    return { lanceur.launch(if (pdfAccepte) arrayOf("image/*", "application/pdf") else arrayOf("image/*")) }
}

actual fun imageDepuisOctets(octets: ByteArray): ImageBitmap? =
    try { BitmapFactory.decodeByteArray(octets, 0, octets.size)?.asImageBitmap() } catch (_: Exception) { null }

@Composable
actual fun rememberChoixTexte(quandChoisi: (String?) -> Unit): () -> Unit {
    val contexte = LocalContext.current
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { quandChoisi(null); return@rememberLauncherForActivityResult }
        val octets = contexte.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (octets == null) { quandChoisi(null); return@rememberLauncherForActivityResult }
        // UTF-8, sinon encodage Windows des anciens CSV Excel
        val texte = String(octets, Charsets.UTF_8).let { if (it.contains('�')) String(octets, charset("windows-1252")) else it }
        quandChoisi(texte.removePrefix("﻿"))
    }
    return { lanceur.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel")) }
}

@Composable
actual fun rememberEnregistrer(quandFini: (String?) -> Unit): (String, String, ByteArray) -> Unit {
    val contexte = LocalContext.current
    var enAttente by remember { mutableStateOf<ByteArray?>(null) }
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val octets = enAttente
        enAttente = null
        if (uri == null || octets == null) { quandFini(null); return@rememberLauncherForActivityResult }
        try {
            contexte.contentResolver.openOutputStream(uri)?.use { it.write(octets) }
            quandFini("Fichier enregistré")
        } catch (e: Exception) { quandFini(e.message ?: "Enregistrement impossible") }
    }
    return { nom, _, octets -> enAttente = octets; lanceur.launch(nom) }
}

@Composable
actual fun rememberImpression(): (String, String) -> Unit {
    val contexte = LocalContext.current
    val vues = remember { mutableListOf<android.webkit.WebView>() }   // garde la page en mémoire pendant l'impression
    return { titre, html ->
        val vue = android.webkit.WebView(contexte)
        vues.add(vue)
        vue.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                val impression = contexte.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                impression.print(titre, view.createPrintDocumentAdapter(titre), android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        vue.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}

@Composable
actual fun rememberOuvrirFichier(): (String, String, ByteArray) -> Unit {
    val contexte = LocalContext.current
    return { nom, mime, octets ->
        val dossier = java.io.File(contexte.cacheDir, "pieces").apply { mkdirs() }
        val fichier = java.io.File(dossier, nom.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        fichier.writeBytes(octets)
        val uri = androidx.core.content.FileProvider.getUriForFile(contexte, contexte.packageName + ".fichiers", fichier)
        val intention = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { contexte.startActivity(android.content.Intent.createChooser(intention, nom).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { android.widget.Toast.makeText(contexte, "Aucune application pour ouvrir ce fichier", android.widget.Toast.LENGTH_LONG).show() }
    }
}

@Composable
actual fun RetourSysteme(actif: Boolean, onRetour: () -> Unit) = androidx.activity.compose.BackHandler(actif, onRetour)

actual fun zipper(fichiers: List<Pair<String, ByteArray>>): ByteArray {
    val sortie = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(sortie).use { zip ->
        fichiers.forEach { (chemin, octets) ->
            zip.putNextEntry(java.util.zip.ZipEntry(chemin)); zip.write(octets); zip.closeEntry()
        }
    }
    return sortie.toByteArray()
}

// Contexte de l'application, fixé au démarrage (MainActivity)
object ContexteApp { lateinit var contexte: android.content.Context }

private fun prefs() = ContexteApp.contexte.getSharedPreferences("tresorerie", android.content.Context.MODE_PRIVATE)
actual fun lirePreference(cle: String): String? = try { prefs().getString(cle, null) } catch (_: Exception) { null }
actual fun garderPreference(cle: String, valeur: String?) { try { prefs().edit().apply { if (valeur == null) remove(cle) else putString(cle, valeur) }.apply() } catch (_: Exception) { } }

actual fun notificationsPermises(): Boolean = try {
    androidx.core.app.NotificationManagerCompat.from(ContexteApp.contexte).areNotificationsEnabled() &&
        (android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(ContexteApp.contexte, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
} catch (_: Exception) { false }

// Un canal par réglage (le son et la vibration d'un canal ne se changent plus après sa création) ;
// importance haute : la notification sonne, vibre et s'affiche en haut de l'écran
private fun canalNotifications(ctx: android.content.Context): String {
    val son = lirePreference("notif.son") != "0"; val vibre = lirePreference("notif.vibration") != "0"
    val id = "nouveautes_" + (if (son) "son" else "muet") + "_" + (if (vibre) "vibre" else "fixe")
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        val g = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (g.getNotificationChannel(id) == null) g.createNotificationChannel(android.app.NotificationChannel(id,
            "Nouveautés" + when { son && vibre -> ""; son -> " (sans vibration)"; vibre -> " (sans son)"; else -> " (silencieuses)" },
            if (son) android.app.NotificationManager.IMPORTANCE_HIGH else android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Demandes à valider ou à payer, rendez-vous, participations, communiqués"
            enableVibration(vibre); if (vibre) vibrationPattern = longArrayOf(0, 180, 90, 180)
            if (!son) setSound(null, null)
            setShowBadge(true)
        })
    }
    return id
}

actual fun notifierSysteme(titre: String, texte: String, nombre: Int) {
    try {
        if (!notificationsPermises()) return
        val ctx = ContexteApp.contexte
        val ouvrir = android.app.PendingIntent.getActivity(ctx, 0, android.content.Intent(ctx, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, canalNotifications(ctx)).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(titre).setContentText(texte)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(texte)).setAutoCancel(true).setContentIntent(ouvrir)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(nombre)   // compteur sur l'icône de l'application (lanceurs qui l'affichent)
            .setBadgeIconType(androidx.core.app.NotificationCompat.BADGE_ICON_SMALL)
            .setDefaults((if (lirePreference("notif.son") != "0") androidx.core.app.NotificationCompat.DEFAULT_SOUND else 0) or
                (if (lirePreference("notif.vibration") != "0") androidx.core.app.NotificationCompat.DEFAULT_VIBRATE else 0))
            .build()
        androidx.core.app.NotificationManagerCompat.from(ctx).notify(1, n)
    } catch (_: SecurityException) { } catch (_: Exception) { }
}

// Tout est vu : la notification et le compteur de l'icône disparaissent
actual fun effacerNotifications() { try { androidx.core.app.NotificationManagerCompat.from(ContexteApp.contexte).cancel(1) } catch (_: Exception) { } }

// Vérification en arrière-plan (application fermée) : toutes les 15 minutes quand le téléphone a du réseau
actual fun planifierVerification() {
    try {
        val demande = androidx.work.PeriodicWorkRequestBuilder<VerificationNouveautes>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()
        androidx.work.WorkManager.getInstance(ContexteApp.contexte).enqueueUniquePeriodicWork("nouveautes", androidx.work.ExistingPeriodicWorkPolicy.KEEP, demande)
    } catch (_: Exception) { }
}

class VerificationNouveautes(ctx: android.content.Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ContexteApp.contexte = applicationContext
        if (Repo.demo) return Result.success()
        return try {
            Repo.client.auth.awaitInitialization()
            if (Repo.client.auth.currentSessionOrNull() == null) return Result.success()
            EtatNouveautes.charger()
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

@Composable
actual fun rememberDemandeNotifications(quandFini: (Boolean) -> Unit): () -> Unit {
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { quandFini(it) }
    return { if (android.os.Build.VERSION.SDK_INT >= 33) lanceur.launch(android.Manifest.permission.POST_NOTIFICATIONS) else quandFini(notificationsPermises()) }
}

actual fun ajouterAgenda(nom: String, date: String, heureDebut: String?, dateFin: String?, heureFin: String?, lieu: String?, description: String?): Boolean = try {
    val zone = kotlinx.datetime.TimeZone.of("Europe/Paris")
    val jour = kotlinx.datetime.LocalDate.parse(date.take(10))
    val fin = dateFin?.let { kotlinx.datetime.LocalDate.parse(it.take(10)) } ?: jour
    fun instant(j: kotlinx.datetime.LocalDate, h: String) = kotlinx.datetime.LocalDateTime(j.year, j.monthNumber, j.dayOfMonth, h.take(2).toInt(), h.drop(3).take(2).toInt())
        .toInstant(zone).toEpochMilliseconds()
    val debutMs = if (heureDebut != null) instant(jour, heureDebut) else kotlinx.datetime.LocalDateTime(jour.year, jour.monthNumber, jour.dayOfMonth, 0, 0).toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()
    val finMs = when {
        heureDebut != null && heureFin != null -> instant(fin, heureFin)
        heureDebut != null -> debutMs + 2 * 3600_000L
        else -> kotlinx.datetime.LocalDateTime(fin.year, fin.monthNumber, fin.dayOfMonth, 0, 0).toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds() + 86_400_000L
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, nom)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, debutMs)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, finMs)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, heureDebut == null)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    lieu?.let { intent.putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, it) }
    description?.let { intent.putExtra(android.provider.CalendarContract.Events.DESCRIPTION, it) }
    ContexteApp.contexte.startActivity(intent); true
} catch (_: android.content.ActivityNotFoundException) { false } catch (_: Exception) { false }
