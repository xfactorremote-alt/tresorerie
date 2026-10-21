package org.jpgrenoble.tresorerie

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.datetime.LocalDate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// =====================================================================
// Annonces de la bannière des membres : prochain rendez-vous, relances (cotisation, participation), communiqués.
// Même calcul que annoncesBanniere / reglesBanniere du site : priorité de chaque type (1 = d'abord), fréquence
// (toujours, une fois par jour sur l'appareil, jamais), durée de chaque annonce ; un communiqué « haute » passe en premier.
// =====================================================================
const val REGLES_BANNIERE_DEFAUT = """{"rotation":8,"rdv":{"priorite":1,"frequence":"toujours"},"cotisation":{"priorite":2,"frequence":"toujours"},"participation":{"priorite":2,"frequence":"toujours"},"communique":{"priorite":1,"frequence":"toujours"}}"""

data class RegleAnnonce(val priorite: Int, val frequence: String)
data class ReglesBanniere(val rotation: Int, val types: Map<String, RegleAnnonce>)

fun reglesBanniere(texte: String?): ReglesBanniere {
    val defaut = Json.parseToJsonElement(REGLES_BANNIERE_DEFAUT).jsonObject
    val r = try { texte?.let { Json.parseToJsonElement(it).jsonObject } } catch (_: Exception) { null } ?: JsonObject(emptyMap())
    fun type(k: String): RegleAnnonce {
        val o = (r[k] as? JsonObject) ?: (defaut[k] as JsonObject)
        val od = defaut[k] as JsonObject
        return RegleAnnonce(o["priorite"]?.jsonPrimitive?.intOrNull ?: od["priorite"]!!.jsonPrimitive.intOrNull!!,
            o["frequence"]?.jsonPrimitive?.contentOrNull ?: od["frequence"]!!.jsonPrimitive.contentOrNull!!)
    }
    return ReglesBanniere(r["rotation"]?.jsonPrimitive?.intOrNull ?: 8, listOf("rdv", "cotisation", "participation", "communique").associateWith { type(it) })
}

fun texteReglesBanniere(r: ReglesBanniere): String = buildJsonObject {
    put("rotation", r.rotation)
    r.types.forEach { (k, v) -> putJsonObject(k) { put("priorite", v.priorite); put("frequence", v.frequence) } }
}.toString()

sealed class Annonce(val cle: String, val prio: Double) {
    class Rdv(val e: Projet, p: Double) : Annonce("rdv:" + e.id, p)
    class Cotisation(val montant: Double, p: Double) : Annonce("cotisation", p)
    class Participations(val dues: List<Participation>, p: Double) : Annonce("participation", p)
    class Info(val c: Communique, p: Double) : Annonce("communique:" + c.id, p)
}

private fun vuAujourdhui(cle: String) = lirePreference("banniere.vu.$cle") == aujourdhui().toString()
fun noterAnnonceVue(cle: String) = garderPreference("banniere.vu.$cle", aujourdhui().toString())

fun annoncesBanniere(planning: List<Projet>, cot: List<PeriodeCotisation>, parts: List<Participation>, communiques: List<Communique>, regles: String?): Pair<List<Annonce>, Int> {
    val R = reglesBanniere(regles); val l = mutableListOf<Annonce>(); val auj = aujourdhui().toString()
    fun garder(type: String, cle: String) = R.types[type]!!.frequence != "jamais" && (R.types[type]!!.frequence != "jour" || !vuAujourdhui(cle))
    planning.firstOrNull()?.let { e -> if (garder("rdv", "rdv:" + e.id)) l += Annonce.Rdv(e, R.types["rdv"]!!.priorite.toDouble()) }
    val retard = retardDe(cot)
    if (retard > 0.005 && garder("cotisation", "cotisation")) l += Annonce.Cotisation(retard, R.types["cotisation"]!!.priorite.toDouble())
    val dues = parts.filter { (it.montantAttendu ?: 0.0) > 0 && it.donne < it.montantAttendu!! && !it.cloturee }
    if (dues.isNotEmpty() && garder("participation", "participation")) l += Annonce.Participations(dues, R.types["participation"]!!.priorite.toDouble())
    communiques.filter { it.debut <= auj && (it.fin == null || it.fin >= auj) }.forEach { c ->
        if (garder("communique", "communique:" + c.id))
            l += Annonce.Info(c, if (c.priorite == "haute") 0.0 else R.types["communique"]!!.priorite + if (c.priorite == "basse") 0.5 else 0.0)
    }
    return l.sortedBy { it.prio } to R.rotation
}

fun communiquesActifs(l: List<Communique>): List<Communique> { val auj = aujourdhui().toString(); return l.filter { it.debut <= auj && (it.fin == null || it.fin >= auj) } }

/** Une annonce à la fois, en fondu, posée sur la photo ; défile seule, se met en pause quand on la touche ; glisser pour changer. */
@Composable
fun BanniereAnnonces(annonces: List<Annonce>, rotation: Int, onEvt: (Projet) -> Unit, onAgenda: (Projet) -> Unit, onRegler: () -> Unit, onCommunique: (Communique) -> Unit) {
    // Verre clair à grands arrondis (Material 3 Expressive, comme .accueil-membre .annonces du site)
    val panneau = Modifier.fillMaxWidth().clip(Formes.M3L).background(Color.White.copy(alpha = 0.16f)).border(1.dp, Color.White.copy(alpha = 0.32f), Formes.M3L)
    if (annonces.isEmpty()) {
        Column(panneau.padding(14.dp)) {
            Text("Prochain rendez-vous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
            Text("Rien de prévu pour l’instant", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        return
    }
    var i by remember(annonces.size) { mutableStateOf(0) }
    var sens by remember { mutableStateOf(1) }
    var pause by remember { mutableStateOf(false) }
    val portee = rememberCoroutineScope()
    var reprise by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val aller = { n: Int, s: Int -> sens = s; i = (n + annonces.size) % annonces.size }
    // Le temps est porté par la barre du point actif (comme le site) : en pause, la barre s'arrête et l'annonce reste
    val progres = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(annonces.getOrNull(i)?.cle) { annonces.getOrNull(i)?.let { noterAnnonceVue(it.cle) }; progres.snapTo(0f) }
    LaunchedEffect(i, annonces.size, rotation, pause) {
        if (annonces.size > 1 && rotation > 0 && !pause) {
            // Pas à pas plutôt qu'une animation : « Supprimer les animations » d'Android ramènerait sa durée à zéro
            while (progres.value < 1f) { kotlinx.coroutines.delay(50); progres.snapTo((progres.value + 50f / (rotation * 1000f)).coerceAtMost(1f)) }
            aller(i + 1, 1)
        }
    }
    // Liseré qui s'illumine à chaque nouvelle annonce
    val eclat = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(i) { eclat.snapTo(1f); eclat.animateTo(0f, androidx.compose.animation.core.tween(900)) }
    Box(panneau.drawBehind {
        if (eclat.value > 0f) drawRoundRect(Color.White.copy(alpha = 0.55f * eclat.value), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (2.dp + 6.dp * (1f - eclat.value)).toPx()))
    }.pointerInput(Unit) {
        // Pause au toucher, reprise 4 secondes après (comme le site : touchstart / touchend)
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pause = true; reprise?.cancel()
            waitForUpOrCancellation()
            reprise = portee.launch { kotlinx.coroutines.delay(4000); pause = false }
        }
    }.pointerInput(annonces.size) {
        var total = 0f
        detectHorizontalDragGestures(onDragStart = { pause = true; total = 0f }, onDragEnd = {
            if (kotlin.math.abs(total) > 60) aller(i + (if (total < 0) 1 else -1), if (total < 0) 1 else -1)
        }) { _, d -> total += d }
    }) {
        // Passage d'une annonce à l'autre, fait pour attirer l'œil : glissement avec léger rebond, icône qui saute,
        // reflet lumineux qui balaie l'annonce (même effet que le site)
        androidx.compose.animation.AnimatedContent(i.coerceIn(0, annonces.size - 1), transitionSpec = {
            val rebond = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(620, easing = rebond)) { it * sens / 7 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(380)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(620, easing = rebond), initialScale = 0.94f)) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(380)) { -it * sens / 8 } +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300)) +
                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(380), targetScale = 0.96f))
        }, label = "annonce") { k ->
            val saut = remember { androidx.compose.animation.core.Animatable(0.4f) }
            val reflet = remember { androidx.compose.animation.core.Animatable(-0.4f) }
            LaunchedEffect(Unit) {
                launch { kotlinx.coroutines.delay(120); saut.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 380f)) }
                launch { kotlinx.coroutines.delay(250); reflet.animateTo(1.4f, androidx.compose.animation.core.tween(1000)) }
            }
            val a = annonces[k]
            // Pastille en galet : fond clair, icône colorée (date blanche à la couleur du rendez-vous, comme le site)
            val (puce, encrePuce) = when (a) {
                is Annonce.Rdv -> Color.White to couleurEvt(a.e.id).third
                is Annonce.Cotisation -> Couleurs.ErreurClair to Couleurs.Erreur
                is Annonce.Participations -> Couleurs.M3OrangeConteneur to Couleurs.M3Orange
                is Annonce.Info -> if (a.c.priorite == "haute") Couleurs.M3Drapeau to Couleurs.M3SurDrapeau else Color.White to Couleurs.M3Bleu
            }
            Row(Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                if (reflet.value in -0.4f..1.4f) {
                    val x = size.width * reflet.value; val l = size.width * 0.35f
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0f)),
                        start = androidx.compose.ui.geometry.Offset(x - l / 2, 0f), end = androidx.compose.ui.geometry.Offset(x + l / 2, size.height)))
                }
            }.clickable {
                when (a) { is Annonce.Rdv -> onEvt(a.e); is Annonce.Cotisation, is Annonce.Participations -> onRegler(); is Annonce.Info -> onCommunique(a.c) }
            }.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).graphicsLayer { scaleX = saut.value; scaleY = saut.value; rotationZ = (1f - saut.value) * -24f }
                    .clip(Formes.M3M).background(puce), contentAlignment = Alignment.Center) {
                    when (a) {
                        is Annonce.Rdv -> { val j = LocalDate.parse(a.e.debut!!.take(10))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(MOIS_COURTS[j.monthNumber - 1].uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = encrePuce, lineHeight = 11.sp)
                                Text("${j.dayOfMonth}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = encrePuce, lineHeight = 21.sp) } }
                        is Annonce.Cotisation -> Icon(Icons.Outlined.Payments, null, tint = encrePuce)
                        is Annonce.Participations -> Icon(Icons.Outlined.Event, null, tint = encrePuce)
                        is Annonce.Info -> Icon(genreDe(a.c).icone, null, tint = encrePuce)
                    }
                }
                Column(Modifier.weight(1f)) {
                    val (sur, titre, sous) = when (a) {
                        is Annonce.Rdv -> Triple("Prochain rendez-vous · ${dansJours(a.e.debut!!)}", a.e.nom,
                            listOfNotNull(if (a.e.heureDebut != null) heureFr(a.e.heureDebut) + (a.e.heureFin?.let { " – " + heureFr(it) } ?: "") else "Toute la journée", a.e.lieu).joinToString(" · "))
                        is Annonce.Cotisation -> Triple("Rappel · cotisation", "${euros(a.montant)} en retard", "Touchez pour voir comment régler")
                        is Annonce.Participations -> Triple("Rappel · participation", "${euros(a.dues.sumOf { it.montantAttendu!! - it.donne })} à régler", a.dues.joinToString(", ") { it.nom })
                        is Annonce.Info -> Triple(genreDe(a.c).sur, a.c.titre,
                            a.c.texte?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: genreDe(a.c).action)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // « Important » en drapeau lisible (comme .ann-drapeau du site)
                        if (a is Annonce.Info && a.c.priorite == "haute") Text("IMPORTANT", fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, color = Couleurs.M3SurDrapeau,
                            modifier = Modifier.background(Couleurs.M3Drapeau, Formes.Rond).padding(horizontal = 8.dp))
                        Text(sur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(titre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sous.isNotEmpty()) Text(sous, fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (a is Annonce.Rdv) Surface(onClick = { onAgenda(a.e) }, shape = Formes.Grand, color = Color.White.copy(alpha = 0.08f), contentColor = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.EditCalendar, contentDescription = "Ajouter à mon agenda", modifier = Modifier.size(20.dp)) }
                } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
        // Zone tactile de 24 dp autour de chaque point (comme le site, WCAG 2.5.8) ; le point visible reste de 6 dp
        if (annonces.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = 1.dp).offset(y = 3.dp)) {
            annonces.indices.forEach { k ->
                val large by androidx.compose.animation.core.animateDpAsState(if (k == i) 22.dp else 6.dp, label = "point")
                Box(Modifier.height(24.dp).width(large + 18.dp).clickable { if (k != i) aller(k, if (k < i) -1 else 1) }
                    .semantics { contentDescription = "Annonce ${k + 1} sur ${annonces.size}" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.height(6.dp).width(large).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.45f))) {
                        // Le point actif se remplit pendant que l'annonce est affichée
                        if (k == i) Box(Modifier.fillMaxHeight().fillMaxWidth(if (rotation > 0) progres.value.coerceAtLeast(0.01f) else 1f).background(Color.White))
                    }
                }
            }
        }
    }
}

// Communiqués lus sur cet appareil : les autres portent « Nouveau » (même règle que communiquesVus du site)
object CommuniquesVus {
    var version by mutableStateOf(0)
    private fun liste() = (lirePreference("communiques.vus") ?: "").split(',').filter { it.isNotBlank() }
    fun vu(id: String): Boolean { version; return id in liste() }
    fun marquer(id: String) { val l = liste(); if (id in l) return; garderPreference("communiques.vus", (l + id).takeLast(200).joinToString(",")); version++ }
}
fun ordreCommuniques(l: List<Communique>) = l.sortedWith(compareBy<Communique> { if (it.priorite == "haute") 0 else 1 }.thenByDescending { it.debut })

/** Rubrique « Communiqués » de la page des membres : au-dessus du programme, accent bleu, absente sans communiqué (comme rubriqueCommuniques du site). */
@Composable
fun RubriqueCommuniques(liste: List<Communique>, mesReponses: Map<String, JsonObject> = emptyMap(), onOuvrir: (Communique) -> Unit) {
    // Une rubrique par genre (Communiqués, Sondages, Photos, Liens), chacune absente si vide, comme rubriqueCommuniques du site
    val tous = ordreCommuniques(communiquesActifs(liste))
    if (tous.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GENRES_COMMUNIQUE.forEach { (genre, g) ->
            val l = tous.filter { it.genre == genre || (genre == "annonce" && it.genre !in GENRES_COMMUNIQUE) }
            if (l.isNotEmpty()) RubriqueGenre("m-$genre", g, l, mesReponses, onOuvrir)
        }
    }
}

@Composable
private fun RubriqueGenre(id: String, g: GenreCommunique, l: List<Communique>, mesReponses: Map<String, JsonObject>, onOuvrir: (Communique) -> Unit) {
    val nouveaux = l.count { !CommuniquesVus.vu(it.id) && !aRepondre(it, mesReponses) }
    val aRep = l.count { aRepondre(it, mesReponses) }
    val resume = when { aRep > 0 -> "$aRep à répondre"; nouveaux > 0 -> "$nouveaux nouveau" + (if (nouveaux > 1) "x" else ""); else -> "${l.size}" }
    // Page des membres en Material 3 Expressive : rubrique teintée de son genre, badge des nouveautés, tuiles segmentées (comme le site)
    RubriqueMembre(id, g.rubrique, resume, g.teinte, g.icone, forcee = nouveaux + aRep > 0, badge = if (nouveaux + aRep > 0) resume else null, fondTeinte = true) {
        l.forEachIndexed { k, c -> TuileCommunique(c, g, k, l.size, nonLu = !CommuniquesVus.vu(c.id), nouveau = !CommuniquesVus.vu(c.id) && !aRepondre(c, mesReponses), mesReponses) { onOuvrir(c) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleCommunique(c: Communique, maReponse: JsonObject? = null, onRepondu: (JsonObject) -> Unit = {}, message: (String) -> Unit = {}, onFermer: () -> Unit) {
    LaunchedEffect(c.id) { CommuniquesVus.marquer(c.id) }
    val g = genreDe(c)
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Couleurs.Surface2) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(g.icone, null, Modifier.size(14.dp), tint = Couleurs.Texte2); Text(g.libelle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
                    }
                }
                if (c.priorite == "haute") Puce("Important", Couleurs.ErreurClair, Couleurs.SurErreurClair)
            }
            Text(c.titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Publié le ${dateFr(c.debut)}" + (c.fin?.let { (if (c.genre == "sondage") " · répondre avant le " else " · jusqu’au ") + dateFr(it) } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
            c.texte?.let { Text(it, fontSize = 15.sp) }
            ContenuCommunique(c, maReponse, onRepondu, onFermer, message)
            // Le lien porte son propre « Fermer » (tonal, à côté du bouton principal), comme le site
            if (c.genre != "sondage" && c.genre != "lien") Button(onClick = onFermer, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
        }
    }
}

// =====================================================================
// Paramètres › Bannière et communiqués (même section que paramBanniere du site)
// =====================================================================
@Composable
fun ParamBanniere(d: Donnees, message: (String) -> Unit, enPage: Boolean = false) {
    val gere = d.peut("gerer_activites", "administrer")
    var version by remember { mutableStateOf(0) }
    var liste by remember { mutableStateOf<List<Communique>>(emptyList()) }
    var regles by remember { mutableStateOf(reglesBanniere(null)) }
    var edition by remember { mutableStateOf<Communique?>(null) }
    var nouveau by remember { mutableStateOf(false) }
    var lu by remember { mutableStateOf<Communique?>(null) }
    var resultats by remember { mutableStateOf<Communique?>(null) }
    var nbRep by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val supprimer = rememberSuppression(message) { version++ }
    LaunchedEffect(version, Synchro.version) {
        try { liste = Repo.communiques(); regles = reglesBanniere(Repo.texteReglage("banniere")) } catch (e: Exception) { message(traduireErreur(e)) }
        if (gere) nbRep = try { Repo.nbReponses() } catch (_: Exception) { emptyMap() }
        EtatNouveautes.vu("communiques")
    }
    val auj = aujourdhui().toString()
    @Composable fun CartePublications() {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enPage) "Publications" else "Communiqués et partages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (gere && !enPage) Button(onClick = { nouveau = true }) { Text("Publier") }
                }
                Text("Annonce, sondage ou questionnaire, photos, lien (Drive, formulaire, vidéo) : chaque publication s’affiche sur la page des membres et sur leur bannière pendant sa période, et chacun reçoit une notification.", fontSize = 13.sp, color = Couleurs.Texte2)
                if (liste.isEmpty()) Text("Aucun communiqué pour l’instant.", color = Couleurs.Texte2)
                liste.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA))
                    Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable { if (gere && c.genre == "sondage") resultats = c else if (gere) edition = c else lu = c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) { Icon(genreDe(c).icone, null, tint = Couleurs.SurOrangeClair, modifier = Modifier.size(18.dp)) }   // .avatar du site
                        Column(Modifier.weight(1f)) {
                            Text(c.titre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(genreDe(c).libelle + " · " + (when { c.debut > auj -> "À partir du ${dateFr(c.debut)}"; c.fin != null && c.fin < auj -> "Terminé"; c.fin != null -> "Jusqu’au ${dateFr(c.fin)}"; else -> "En cours" }) +
                                (if (c.genre == "sondage") " · ${nbRep[c.id] ?: 0} réponse" + (if ((nbRep[c.id] ?: 0) > 1) "s" else "") else "") +
                                (if (c.visible) "" else " · bureau seulement"), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                        when (c.priorite) { "haute" -> Puce("Important", Couleurs.ErreurClair, Couleurs.SurErreurClair); "basse" -> Puce("Discret", Couleurs.Surface2, Couleurs.Texte2); else -> Puce("Normal", Couleurs.Surface2, Couleurs.Texte2) }
                    }
                }
            }
    }
    @Composable fun CarteOrdre() {
            CarteBlanche {
                Text("Ordre et fréquence des annonces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("La bannière des membres montre une annonce à la fois. Choisissez l’ordre et la fréquence de chaque type ; un communiqué « Important » passe toujours en premier.", fontSize = 13.sp, color = Couleurs.Texte2)
                val ORDRES = listOf(1 to "En premier", 2 to "Ensuite", 3 to "En dernier")
                val FREQ = listOf("toujours" to "À chaque visite", "jour" to "Une fois par jour", "jamais" to "Jamais")
                listOf("rdv" to "Prochain rendez-vous", "communique" to "Communiqués", "cotisation" to "Rappel de cotisation en retard", "participation" to "Rappel de participation à régler").forEach { (k, l) ->
                    val r = regles.types[k]!!
                    Text(l, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PuceMenu(ORDRES.first { it.first == r.priorite }.second, false, ORDRES.map { it.second }) { n -> regles = regles.copy(types = regles.types + (k to r.copy(priorite = ORDRES[n].first))) }
                        PuceMenu(FREQ.first { it.first == r.frequence }.second, false, FREQ.map { it.second }) { n -> regles = regles.copy(types = regles.types + (k to r.copy(frequence = FREQ[n].first))) }
                    }
                }
                val DUREES = listOf(5 to "5 secondes", 8 to "8 secondes", 12 to "12 secondes", 20 to "20 secondes", 0 to "Ne pas faire défiler")
                Text("Durée de chaque annonce", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                PuceMenu(DUREES.firstOrNull { it.first == regles.rotation }?.second ?: "${regles.rotation} s", false, DUREES.map { it.second }) { n -> regles = regles.copy(rotation = DUREES[n].first) }
                Button(onClick = { scope.launch { try { Repo.majTexteReglage("banniere", texteReglesBanniere(regles)); message("Réglages de la bannière enregistrés") } catch (e: Exception) { message(traduireErreur(e)) } } },
                    modifier = Modifier.align(Alignment.End)) { Text("Enregistrer") }
            }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val large = maxWidth >= 840.dp
    val admin = d.peut("administrer")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Page Plus › Communiqués : en-tête commun ; « Publier » = bouton + flottant au téléphone, bouton texte dans l'en-tête sur grand écran (règle 9 bis)
        if (enPage) item {
            Titre("Communiqués") { if (gere && large) Button(onClick = { nouveau = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Publier") } }
        }
        // Deux colonnes au-delà de 840 dp (grille-2 du site) : publications, ordre et fréquence
        if (large && admin) item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { CartePublications() }
                Box(Modifier.weight(1f)) { CarteOrdre() }
            }
        } else {
            item { CartePublications() }
            if (admin) item { CarteOrdre() }
        }
    }
    if (enPage && gere && !large) BoutonPlus("Publier", Modifier.align(Alignment.BottomEnd)) { nouveau = true }
    }
    if (nouveau || edition != null) EditionCommunique(edition, onSupprimer = edition?.let { c -> { edition = null; supprimer("communiques", c.id, c.titre) } }, message = message,
        onFini = { nouveau = false; edition = null; version++ }) { nouveau = false; edition = null }
    lu?.let { FeuilleCommunique(it) { lu = null } }
    resultats?.let { c -> FeuilleResultatsSondage(c, message, onModifier = { resultats = null; edition = c }) { resultats = null } }
}

@Composable
private fun EditionCommunique(c: Communique?, onSupprimer: (() -> Unit)?, message: (String) -> Unit, onFini: () -> Unit, onAnnuler: () -> Unit) {
    var genre by remember { mutableStateOf(c?.genre ?: "annonce") }
    var titre by remember { mutableStateOf(c?.titre ?: "") }
    var texte by remember { mutableStateOf(c?.texte ?: "") }
    var debut by remember { mutableStateOf(dateFr(c?.debut ?: aujourdhui().toString())) }
    var fin by remember { mutableStateOf(c?.fin?.let { dateFr(it) } ?: "") }
    var priorite by remember { mutableStateOf(c?.priorite ?: "normale") }
    var visible by remember { mutableStateOf(c?.visible ?: true) }
    // Sondage : questions (intitulé, type, choix un par ligne, obligatoire)
    data class QEd(val id: String, val intitule: String, val type: String, val choix: String, val obligatoire: Boolean)
    val questions = remember { mutableStateListOf<QEd>().apply {
        addAll((c?.questions ?: emptyList()).map { QEd(it.id, it.intitule, it.type, it.choix.joinToString("\n"), it.obligatoire) })
        if (isEmpty()) add(QEd("q1", "", "unique", "", true)) } }
    var anonyme by remember { mutableStateOf(c?.anonyme ?: false) }
    var resultatsVisibles by remember { mutableStateOf(c?.resultatsVisibles ?: true) }
    // Photos : déjà enregistrées (chemins) et nouvelles (à envoyer)
    val photos = remember { mutableStateListOf<String>().apply { addAll(c?.photos ?: emptyList()) } }
    val nouvelles = remember { mutableStateListOf<Fichier>() }
    var lien by remember { mutableStateOf(c?.lien ?: "") }
    var envoi by remember { mutableStateOf<String?>(null) }
    val choixPhotos = rememberChoixPhotos { l, err -> err?.let(message); nouvelles.addAll(l.take(30 - photos.size - nouvelles.size)) }
    val scope = rememberCoroutineScope()
    val dDebut = dateDepuisFr(debut); val dFin = if (fin.isBlank()) null else dateDepuisFr(fin)
    val qsPropres = questions.map { q -> QuestionSondage(q.id, q.intitule.trim(), q.type, if (q.type == "texte") emptyList() else q.choix.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct(), q.obligatoire) }
    val contenuValide = when (genre) {
        "sondage" -> qsPropres.isNotEmpty() && qsPropres.all { it.intitule.isNotEmpty() && (it.type == "texte" || it.choix.size >= 2) }
        "photos" -> photos.size + nouvelles.size >= 1
        "lien" -> Regex("^https?://\\S+\\.\\S+", RegexOption.IGNORE_CASE).containsMatchIn(lien.trim())
        else -> true
    }
    val valide = envoi == null && titre.trim().length >= 2 && dDebut != null && (fin.isBlank() || (dFin != null && dFin >= dDebut)) && contenuValide
    val titres = mapOf("annonce" to "une annonce", "sondage" to "un sondage", "photos" to "des photos", "lien" to "un lien")
    DialogueSimple((if (c == null) "Publier " else "Modifier ") + titres[genre], if (c == null) "Publier" else "Enregistrer", valide, onAnnuler, {
        scope.launch {
            try {
                val chemins = photos.toMutableList()
                if (genre == "photos") nouvelles.forEachIndexed { k, f -> envoi = "Envoi des photos ${k + 1} / ${nouvelles.size}…"; chemins += Repo.envoyerPhotoPartage(f) }
                Repo.enregistrerCommunique(c?.id, NouveauCommunique(titre.trim(), texte.trim().ifBlank { null }, dDebut!!.toString(), dFin?.toString(), priorite, visible,
                    genre = genre, lien = if (genre == "lien") lien.trim() else null, photos = if (genre == "photos") chemins else emptyList(),
                    questions = if (genre == "sondage") qsPropres else emptyList(), anonyme = genre == "sondage" && anonyme, resultatsVisibles = resultatsVisibles))
                message(if (c == null) "Publié : les membres sont prévenus" else "Publication modifiée"); onFini()
            } catch (e: Exception) { envoi = null; message(traduireErreur(e)) }
        }
    }) {
        if (c == null) {
            @OptIn(ExperimentalMaterial3Api::class)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GENRES_COMMUNIQUE.entries.forEachIndexed { k, (cle, g) ->
                    SegmentedButton(selected = genre == cle, onClick = { genre = cle }, shape = SegmentedButtonDefaults.itemShape(k, GENRES_COMMUNIQUE.size),
                        icon = { Icon(g.icone, null, Modifier.size(16.dp)) }) { Text(g.libelle, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
        OutlinedTextField(titre, { titre = it.take(120) }, label = { Text("Titre *") }, placeholder = { Text("Assemblée générale le 15 novembre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(texte, { texte = it.take(1500) }, label = { Text("Texte") }, placeholder = { Text("Ce que les membres doivent savoir") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        when (genre) {
            "sondage" -> {
                questions.forEachIndexed { k, q ->
                    Surface(shape = Formes.XGrand, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.BordDoux), color = Color.Transparent) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Question ${k + 1}", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(q.intitule, { questions[k] = q.copy(intitule = it.take(200)) }, placeholder = { Text("Quel samedi vous convient ?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            val T = listOf("unique" to "Un seul choix", "multiple" to "Plusieurs choix", "texte" to "Réponse libre")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PuceMenu(T.first { it.first == q.type }.second, false, T.map { it.second }) { n -> questions[k] = q.copy(type = T[n].first) }
                                Checkbox(q.obligatoire, { questions[k] = q.copy(obligatoire = it) }); Text("Réponse obligatoire", fontSize = 14.sp)
                            }
                            if (q.type != "texte") OutlinedTextField(q.choix, { questions[k] = q.copy(choix = it) }, placeholder = { Text("Un choix par ligne") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            if (questions.size > 1) TextButton(onClick = { questions.removeAt(k) }) { Text("Retirer la question") }
                        }
                    }
                }
                if (questions.size < 20) FilledTonalButton(onClick = { questions += QEd("q" + ((questions.maxOfOrNull { it.id.drop(1).toIntOrNull() ?: 0 } ?: 0) + 1), "", "unique", "", true) }) { Text("Ajouter une question") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(anonyme, { anonyme = it }); Text("Réponses anonymes (le bureau voit les totaux, pas les noms)", fontSize = 14.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(resultatsVisibles, { resultatsVisibles = it }); Text("Montrer les résultats aux membres après leur réponse", fontSize = 14.sp) }
            }
            "photos" -> {
                val toutes = photos.map { it to null as Fichier? } + nouvelles.map { "" to it }
                toutes.chunked(3).forEachIndexed { ligne, l ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        l.forEachIndexed { k, (ch, f) ->
                            val index = ligne * 3 + k
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(Formes.Grand)) {
                                if (f == null) PhotoPartage(ch, Modifier.fillMaxSize())
                                else imageDepuisOctets(f.octets)?.let { androidx.compose.foundation.Image(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                IconButton(onClick = { if (index < photos.size) photos.removeAt(index) else nouvelles.removeAt(index - photos.size) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)) {
                                    Icon(Icons.Outlined.Close, "Retirer la photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        repeat(3 - l.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (photos.size + nouvelles.size < 30) FilledTonalButton(onClick = choixPhotos) { Icon(Icons.Outlined.AddAPhoto, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ajouter des photos") }
                Text("${photos.size + nouvelles.size} / 30 photos · réduites automatiquement pour les téléphones. Pour un grand album, publiez plutôt un lien vers le Drive.", fontSize = 13.sp, color = Couleurs.Texte2)
            }
            "lien" -> {
                OutlinedTextField(lien, { lien = it.take(1000) }, label = { Text("Adresse du lien *") }, placeholder = { Text("https://drive.google.com/…") }, singleLine = true,
                    isError = lien.isNotBlank() && !contenuValide, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                Text("Dossier Drive, album photo, formulaire, vidéo… Vérifiez que le partage est ouvert « à toute personne disposant du lien ».", fontSize = 13.sp, color = Couleurs.Texte2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampDate(debut, { debut = it }, label = { Text("Afficher à partir du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dDebut == null, modifier = Modifier.weight(1f))
            ChampDate(fin, { fin = it }, label = { Text(if (genre == "sondage") "Réponses jusqu’au (facultatif)" else "Jusqu’au (facultatif)") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = fin.isNotBlank() && (dFin == null || (dDebut != null && dFin < dDebut)), modifier = Modifier.weight(1f))
        }
        val P = listOf("haute" to "Important (en premier sur la bannière)", "normale" to "Normal", "basse" to "Discret")
        Text("Priorité", fontWeight = FontWeight.SemiBold)
        PuceMenu(P.first { it.first == priorite }.second, false, P.map { it.second }) { priorite = P[it].first }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(visible, { visible = it }); Text("Visible par tous les membres (sinon bureau seulement)", fontSize = 14.sp) }
        envoi?.let { Text(it, fontSize = 13.sp, color = Couleurs.Texte2) }
        onSupprimer?.let { BoutonSupprimer(it) }
    }
}
