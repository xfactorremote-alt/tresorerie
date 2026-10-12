package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// =====================================================================
// Partages avec les membres : annonce, sondage (questionnaire), photos, lien (Drive, formulaire, vidéo).
// Même liste et mêmes règles que la base (communiques.genre, repondre_sondage, calcul_resultats) et que le site
// (GENRES_COMMUNIQUE, libelleLien, calculResultats, blocResultats, rendreSondage, visionneuse).
// =====================================================================
data class GenreCommunique(val libelle: String, val sur: String, val action: String, val icone: ImageVector,
                           val rubrique: String, val fort: Color, val clair: Color)

// Une rubrique par genre sur la page des membres, avec sa couleur (mêmes couleurs que les groupes des Paramètres et le site)
val GENRES_COMMUNIQUE = linkedMapOf(
    "annonce" to GenreCommunique("Annonce", "Communiqué", "", Icons.Outlined.Campaign, "Communiqués", Couleurs.Bleu, Color(0xFFE3F1FB)),
    "sondage" to GenreCommunique("Sondage", "Votre avis", "Touchez pour répondre", Icons.Outlined.Poll, "Sondages", Couleurs.Orange, Color(0xFFFFEDE5)),
    "photos" to GenreCommunique("Photos", "Photos", "Touchez pour voir", Icons.Outlined.Image, "Photos", Couleurs.Vert, Color(0xFFE4F5E8)),
    "lien" to GenreCommunique("Lien", "À voir", "Touchez pour ouvrir", Icons.Outlined.Link, "Liens", Color(0xFF7048B8), Color(0xFFEFE9FA)),
)
fun genreDe(c: Communique) = GENRES_COMMUNIQUE[c.genre] ?: GENRES_COMMUNIQUE.getValue("annonce")

fun hoteDe(u: String?): String = Regex("^https?://([^/:?#]+)", RegexOption.IGNORE_CASE).find(u ?: "")?.groupValues?.get(1)?.lowercase()?.removePrefix("www.") ?: ""

/** Bouton d'un lien selon le site visé (même règle que libelleLien du site). */
fun libelleLien(u: String?): String {
    val h = hoteDe(u)
    val chemin = Regex("^https?://[^/]+(/[^?#]*)", RegexOption.IGNORE_CASE).find(u ?: "")?.groupValues?.get(1) ?: ""
    return when {
        h == "forms.gle" || (h == "docs.google.com" && chemin.startsWith("/forms")) || h.endsWith("forms.office.com") -> "Répondre au formulaire"
        h == "drive.google.com" || h == "docs.google.com" -> "Ouvrir le Drive"
        h.contains("photos.google") || h == "photos.app.goo.gl" -> "Voir l’album"
        h.contains("youtube.com") || h == "youtu.be" -> "Voir la vidéo"
        else -> "Ouvrir le lien"
    }
}

/** Choix donnés à une question (un seul, plusieurs, ou le texte libre). */
fun JsonObject.reponseA(q: String): List<String> = when (val v = this[q]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(v.contentOrNull)
    else -> emptyList()
}

/** Contrôle d'une réponse : questions obligatoires, choix proposés (même règle que repondre_sondage). */
fun controlerReponses(c: Communique, r: JsonObject): JsonObject {
    val propre = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
    c.questions.forEach { q ->
        val v = r[q.id]
        when (q.type) {
            "texte" -> (v as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { propre[q.id] = JsonPrimitive(it.take(1000)) }
            "multiple" -> (v as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { a ->
                if (a.any { (it as? JsonPrimitive)?.contentOrNull !in q.choix }) throw IllegalStateException("Choix inconnu"); propre[q.id] = a }
            else -> (v as? JsonPrimitive)?.contentOrNull?.let { x -> if (x !in q.choix) throw IllegalStateException("Choix inconnu"); propre[q.id] = JsonPrimitive(x) }
        }
        if (q.id !in propre && q.obligatoire) throw IllegalStateException("Merci de répondre à : ${q.intitule}")
    }
    return JsonObject(propre)
}

/** Résultats d'un sondage (même nom et même calcul que calcul_resultats de la base et calculResultats du site). */
fun calculResultats(c: Communique, toutes: List<ReponseSondage>, bureau: Boolean, membres: List<Membre>): ResultatsSondage {
    val rep = toutes.filter { it.communiqueId == c.id }
    return ResultatsSondage(rep.size, c.questions.map { q ->
        ResultatQuestion(q.id, q.choix.associateWith { ch -> rep.count { ch in it.reponses.reponseA(q.id) } },
            if (bureau && q.type == "texte") rep.mapNotNull { it.reponses.reponseA(q.id).firstOrNull() } else null)
    }, if (bureau && !c.anonyme) rep.map { r -> val m = membres.firstOrNull { it.id == r.memberId }; Repondant(m?.prenom, m?.nom, r.reponses, r.modifieLe) }
        .sortedWith(compareBy({ it.nom }, { it.prenom })) else null)
}

fun aRepondre(c: Communique, mesReponses: Map<String, JsonObject>) = c.genre == "sondage" && c.id !in mesReponses

// ---------- Photos ----------
/** Image de démonstration (dégradé coloré) pour les photos « demo/… » de la démo. */
private val cacheDemo = mutableMapOf<String, ImageBitmap>()
fun imageDemo(chemin: String): ImageBitmap = cacheDemo.getOrPut(chemin) {
    val n = chemin.filter { it.isDigit() }.toIntOrNull() ?: 1
    val (a, b) = listOf(Color(0xFFF2994A) to Couleurs.Orange, Couleurs.Bleu to Color(0xFF78BEE6), Couleurs.Vert to Color(0xFFC8E6A0), Color(0xFF783CA0) to Color(0xFFF0B478))[(n - 1).mod(4)]
    val img = ImageBitmap(600, 400)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(600f, 400f)) {
        drawRect(Brush.verticalGradient(listOf(a, b)))
        repeat(9) { k -> drawCircle(b.copy(alpha = 0.35f), radius = 30f + (k * 37 % 90), center = Offset((k * 131 % 600).toFloat(), (k * 97 % 400).toFloat())) }
    }
    img
}

private object CachePhotos { val images = mutableMapOf<String, ImageBitmap>(); val octets = mutableMapOf<String, ByteArray>() }

@Composable
fun PhotoPartage(chemin: String, modifier: Modifier = Modifier, ajustement: ContentScale = ContentScale.Crop) {
    var image by remember(chemin) { mutableStateOf(if (chemin.startsWith("demo/")) imageDemo(chemin) else CachePhotos.images[chemin]) }
    LaunchedEffect(chemin) {
        if (image == null) try {
            val o = Repo.telecharger("partages", chemin)
            if (o != null) imageDepuisOctets(o)?.let { CachePhotos.images[chemin] = it; CachePhotos.octets[chemin] = o; if (CachePhotos.images.size > 60) CachePhotos.images.remove(CachePhotos.images.keys.first()); image = it }
        } catch (_: Exception) { }
    }
    Box(modifier.background(Couleurs.Surface2), contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, contentScale = ajustement, modifier = Modifier.fillMaxSize()) }
            ?: Icon(Icons.Outlined.Image, null, tint = Couleurs.Texte2)
    }
}

/** Visionneuse plein écran : glisser pour passer d'une photo à l'autre, enregistrer, fermer (comme la visionneuse du site). */
@Composable
fun Visionneuse(photos: List<String>, depart: Int, onFermer: () -> Unit) {
    val etat = rememberPagerState(depart) { photos.size }
    val enregistrer = rememberEnregistrer { }
    val scope = rememberCoroutineScope()
    Dialog(onFermer, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0A0A))) {
            HorizontalPager(etat, Modifier.fillMaxSize()) { p -> PhotoPartage(photos[p], Modifier.fillMaxSize().background(Color(0xFF0B0A0A)), ContentScale.Fit) }
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${etat.currentPage + 1} / ${photos.size}", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { val ch = photos[etat.currentPage]; (CachePhotos.octets[ch] ?: try { Repo.telecharger("partages", ch) } catch (_: Exception) { null })
                    ?.let { enregistrer("photo-${etat.currentPage + 1}.jpg", "image/jpeg", it) } } }) { Icon(Icons.Outlined.Download, "Enregistrer la photo", tint = Color.White) }
                IconButton(onClick = onFermer) { Icon(Icons.Outlined.Close, "Fermer", tint = Color.White) }
            }
        }
    }
}

@Composable
fun GaleriePhotos(photos: List<String>, colonnes: Int = 3) {
    var ouverte by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.chunked(colonnes).forEachIndexed { ligne, l ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                l.forEachIndexed { k, ch ->
                    PhotoPartage(ch, Modifier.weight(1f).aspectRatio(1f).clip(Formes.Grand).clickable { ouverte = ligne * colonnes + k })
                }
                repeat(colonnes - l.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    ouverte?.let { Visionneuse(photos, it) { ouverte = null } }
}

// ---------- Contenu d'une publication selon son genre (dans la fiche) ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenuCommunique(c: Communique, maReponse: JsonObject?, onRepondu: (JsonObject) -> Unit, onFermer: () -> Unit, message: (String) -> Unit = {}) {
    when (c.genre) {
        "photos" -> GaleriePhotos(c.photos)
        "lien" -> {
            val uri = LocalUriHandler.current
            val presse = LocalClipboardManager.current
            // Comme feuilleCommunique du site : Copier le lien, le bouton du lien (Drive, formulaire…), Fermer
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { presse.setText(AnnotatedString(c.lien ?: "")); message("Lien copié") }) { Text("Copier le lien") }
                Button(onClick = { c.lien?.let { runCatching { uri.openUri(it) } } }) {
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(libelleLien(c.lien))
                }
                FilledTonalButton(onClick = onFermer) { Text("Fermer") }
            }
        }
        "sondage" -> SondageMembre(c, maReponse, onRepondu, onFermer)
    }
}

/** Questionnaire du membre, puis sa réponse et les résultats (comme rendreSondage du site). */
@Composable
fun SondageMembre(c: Communique, maReponse: JsonObject?, onRepondu: (JsonObject) -> Unit, onFermer: () -> Unit) {
    var modifier by remember(c.id) { mutableStateOf(false) }
    var resultats by remember(c.id) { mutableStateOf<ResultatsSondage?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ouvert = c.fin == null || c.fin >= aujourdhui().toString()
    LaunchedEffect(c.id, maReponse) { if (maReponse != null && c.resultatsVisibles && resultats == null) resultats = try { Repo.resultatsSondage(c.id) } catch (_: Exception) { null } }
    if (maReponse != null && !modifier) {
        Surface(shape = Formes.XGrand, color = Couleurs.Surface2, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.BleuTrait)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Couleurs.Bleu)
                Column {
                    Text("Votre réponse est enregistrée", fontWeight = FontWeight.SemiBold)
                    Text((if (c.anonyme) "Sondage anonyme : le bureau voit les totaux, pas qui a répondu quoi." else "Le bureau voit votre réponse.") +
                        (if (ouvert && c.fin != null) " Modifiable jusqu’au ${dateFr(c.fin)}." else ""), fontSize = 13.sp, color = Couleurs.Texte2)
                }
            }
        }
        val r = resultats
        if (r != null) BlocResultats(c, r, maReponse)
        else if (!c.resultatsVisibles) Text("Les résultats sont réservés au bureau.", color = Couleurs.Texte2, fontSize = 14.sp)
        else Text("Chargement des résultats…", color = Couleurs.Texte2, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (ouvert) TextButton(onClick = { modifier = true }) { Text("Modifier ma réponse") }
            Button(onClick = onFermer) { Text("Fermer") }
        }
        return
    }
    val choix = remember(c.id) { mutableStateMapOf<String, List<String>>().apply { c.questions.forEach { q -> put(q.id, maReponse?.reponseA(q.id) ?: emptyList()) } } }
    c.questions.forEachIndexed { i, q ->
        Surface(shape = Formes.XGrand, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.BordDoux), color = Color.Transparent) {
            Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${i + 1}. ${q.intitule}" + if (!q.obligatoire) " (facultatif)" else "", fontWeight = FontWeight.SemiBold)
                when (q.type) {
                    "texte" -> OutlinedTextField(choix[q.id]?.firstOrNull() ?: "", { choix[q.id] = listOf(it.take(1000)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    else -> q.choix.forEach { ch ->
                        val coche = ch in (choix[q.id] ?: emptyList())
                        Row(Modifier.fillMaxWidth().clip(Formes.Grand)
                            .background(if (coche) Couleurs.OrangeClair else Color.Transparent)
                            .border(1.dp, if (coche) Couleurs.Orange else Couleurs.BordDoux, Formes.Grand)
                            .clickable { choix[q.id] = if (q.type == "multiple") (if (coche) choix[q.id]!! - ch else choix[q.id]!! + ch) else listOf(ch) }
                            .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (q.type == "multiple") Checkbox(coche, null) else RadioButton(coche, null)
                            Text(ch, fontSize = 15.sp)
                        }
                    }
                }
                if (q.type == "multiple") Text("Plusieurs réponses possibles", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
    }
    Text(if (c.anonyme) "Sondage anonyme : le bureau voit les totaux, pas qui a répondu quoi." else "Le bureau verra votre réponse.", fontSize = 13.sp, color = Couleurs.Texte2)
    erreur?.let { Text(it, color = Couleurs.Erreur, fontSize = 14.sp) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = onFermer) { Text("Plus tard") }
        Button(enabled = !envoi, onClick = {
            val rep = JsonObject(c.questions.mapNotNull { q ->
                val v = choix[q.id].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                if (v.isEmpty()) null else q.id to (if (q.type == "multiple") JsonArray(v.map { JsonPrimitive(it) }) else JsonPrimitive(v.first()))
            }.toMap())
            val manque = c.questions.firstOrNull { it.obligatoire && it.id !in rep }
            if (manque != null) { erreur = "Merci de répondre à : ${manque.intitule}"; return@Button }
            envoi = true; erreur = null
            scope.launch {
                try { resultats = Repo.repondreSondage(c.id, rep); modifier = false; onRepondu(rep) }
                catch (e: Exception) { erreur = traduireErreur(e) }
                envoi = false
            }
        }) { Text("Envoyer ma réponse") }
    }
}

/** Barres de résultats par choix ; « votre choix » en orange (comme blocResultats du site). */
@Composable
fun BlocResultats(c: Communique, r: ResultatsSondage, mien: JsonObject?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Résultats", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("${r.nb} réponse" + if (r.nb > 1) "s" else "", fontSize = 13.sp, color = Couleurs.Texte2)
        }
        c.questions.forEachIndexed { i, q ->
            val rq = r.questions.firstOrNull { it.id == q.id }
            Text("${i + 1}. ${q.intitule}", fontWeight = FontWeight.SemiBold)
            if (q.type == "texte") {
                val t = rq?.textes
                if (t == null) Text("Les réponses écrites sont lues par le bureau.", fontSize = 13.sp, color = Couleurs.Texte2)
                else if (t.isEmpty()) Text("Aucune réponse écrite.", fontSize = 13.sp, color = Couleurs.Texte2)
                else t.forEach { Text("• $it", fontSize = 14.sp) }
            } else {
                val comptes = rq?.comptes ?: emptyMap()
                val base = if (q.type == "multiple") r.nb else comptes.values.sum()
                q.choix.forEach { ch ->
                    val n = comptes[ch] ?: 0
                    val pct = if (base > 0) (n * 100 + base / 2) / base else 0
                    val moi = mien?.reponseA(q.id)?.contains(ch) == true
                    Box(Modifier.fillMaxWidth().clip(Formes.Moyen).background(Couleurs.Surface2)) {
                        Box(Modifier.matchParentSize().fillMaxWidth(pct / 100f).background(if (moi) Couleurs.OrangeClair else Couleurs.BleuClair))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(ch + if (moi) " · votre choix" else "", fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("$pct % ($n)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Étiquette d'une publication dans la rubrique : ce qu'elle demande ou contient (comme etiquetteCommunique du site). */
@Composable
fun EtiquetteCommunique(c: Communique, mesReponses: Map<String, JsonObject>) {
    when (c.genre) {
        "sondage" -> if (c.id in mesReponses) Puce("Répondu", Couleurs.BleuClair, Couleurs.SurBleuClair) else Puce("À répondre", Couleurs.OrangeClair, Couleurs.SurOrangeClair)
        "photos" -> Puce("${c.photos.size} photo" + if (c.photos.size > 1) "s" else "", Couleurs.Surface2, Couleurs.Texte2)
    }
}

// ---------- Bureau : résultats d'un sondage, export ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleResultatsSondage(c: Communique, message: (String) -> Unit, onModifier: () -> Unit, onFermer: () -> Unit) {
    var r by remember(c.id) { mutableStateOf<ResultatsSondage?>(null) }
    LaunchedEffect(c.id) { r = try { Repo.resultatsSondage(c.id) } catch (e: Exception) { message(traduireErreur(e)); null } }
    val enregistrer = rememberEnregistrer { it?.let(message) }
    fun lisible(v: JsonObject, q: QuestionSondage) = v.reponseA(q.id).joinToString(" ; ")
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Puce("Sondage", Couleurs.Surface2, Couleurs.Texte2); if (c.anonyme) Puce("Anonyme", Couleurs.Surface2, Couleurs.Texte2) }
            Text(c.titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text((c.fin?.let { "Réponses jusqu’au ${dateFr(it)}" } ?: "Sans date de fin") + " · " +
                (if (c.resultatsVisibles) "résultats montrés aux membres après leur réponse" else "résultats réservés au bureau"), fontSize = 13.sp, color = Couleurs.Texte2)
            val res = r
            if (res == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                BlocResultats(c, res, null)
                res.repondants?.takeIf { it.isNotEmpty() }?.let { l ->
                    Text("Qui a répondu", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    l.forEach { x ->
                        Column {
                            Text("${x.prenom ?: ""} ${x.nom ?: ""}", fontWeight = FontWeight.SemiBold)
                            Text(c.questions.map { lisible(x.reponses, it) }.filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onModifier) { Text("Modifier") }
                OutlinedButton(enabled = res != null, onClick = {
                    val nom = "sondage-" + c.titre.lowercase().map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-') + ".csv"
                    val texte = res!!.repondants?.let { l -> csv(listOf("Prénom", "Nom", "Date") + c.questions.map { it.intitule },
                        l.map { x -> listOf(x.prenom, x.nom, x.le?.take(10)) + c.questions.map { lisible(x.reponses, it) } }) }
                        ?: csv(listOf("Question", "Choix", "Réponses"), c.questions.flatMap { q ->
                            val rq = res.questions.firstOrNull { it.id == q.id }
                            if (q.type == "texte") (rq?.textes ?: emptyList()).map { listOf(q.intitule, it, "") } else q.choix.map { listOf(q.intitule, it, rq?.comptes?.get(it) ?: 0) } })
                    enregistrer(nom, "text/csv", texte.encodeToByteArray())
                }) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Exporter") }
                Button(onClick = onFermer) { Text("Fermer") }
            }
        }
    }
}
