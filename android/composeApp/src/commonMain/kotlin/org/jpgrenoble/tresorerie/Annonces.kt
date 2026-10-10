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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
const val REGLES_BANNIERE_DEFAUT = """{"rotation":8,"rdv":{"priorite":1,"frequence":"toujours"},"cotisation":{"priorite":2,"frequence":"jour"},"participation":{"priorite":2,"frequence":"jour"},"communique":{"priorite":1,"frequence":"toujours"}}"""

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
    val panneau = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x6B141211)).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
    if (annonces.isEmpty()) {
        Column(panneau.padding(14.dp)) {
            Text("Prochain rendez-vous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
            Text("Rien de prévu pour l’instant", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        return
    }
    var i by remember(annonces.size) { mutableStateOf(0) }
    var pause by remember { mutableStateOf(false) }
    LaunchedEffect(annonces.getOrNull(i)?.cle) { annonces.getOrNull(i)?.let { noterAnnonceVue(it.cle) } }
    LaunchedEffect(annonces.size, rotation, pause) {
        if (annonces.size > 1 && rotation > 0 && !pause) while (true) { kotlinx.coroutines.delay(rotation * 1000L); i = (i + 1) % annonces.size }
    }
    Box(panneau.pointerInput(annonces.size) {
        var total = 0f
        detectHorizontalDragGestures(onDragStart = { pause = true; total = 0f }, onDragEnd = {
            if (kotlin.math.abs(total) > 60) i = (i + (if (total < 0) 1 else -1) + annonces.size) % annonces.size
            pause = false
        }) { _, d -> total += d }
    }) {
        androidx.compose.animation.AnimatedContent(i.coerceIn(0, annonces.size - 1), transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))
        }, label = "annonce") { k ->
            val a = annonces[k]
            val (accent, puce) = when (a) {
                is Annonce.Rdv -> couleurEvt(a.e.id).third to couleurEvt(a.e.id).third
                is Annonce.Cotisation -> Color(0xFFFF8A80) to Color(0xBFBA1A1A)
                is Annonce.Participations -> Color(0xFFFFD54F) to Color(0xCCC58A00)
                is Annonce.Info -> (if (a.c.priorite == "haute") Color.White else Color(0xFF8EC9F0)) to Color(0xCC1B77B0)
            }
            Row(Modifier.fillMaxWidth().clickable {
                when (a) { is Annonce.Rdv -> onEvt(a.e); is Annonce.Cotisation, is Annonce.Participations -> onRegler(); is Annonce.Info -> onCommunique(a.c) }
            }.drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(puce), contentAlignment = Alignment.Center) {
                    when (a) {
                        is Annonce.Rdv -> { val j = LocalDate.parse(a.e.debut!!.take(10))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(MOIS_COURTS[j.monthNumber - 1].uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 11.sp)
                                Text("${j.dayOfMonth}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 21.sp) } }
                        is Annonce.Cotisation -> Icon(Icons.Outlined.Payments, null, tint = Color.White)
                        is Annonce.Participations -> Icon(Icons.Outlined.Event, null, tint = Color.White)
                        is Annonce.Info -> Icon(Icons.Outlined.Campaign, null, tint = Color.White)
                    }
                }
                Column(Modifier.weight(1f)) {
                    val (sur, titre, sous) = when (a) {
                        is Annonce.Rdv -> Triple("Prochain rendez-vous · ${dansJours(a.e.debut!!)}", a.e.nom,
                            listOfNotNull(if (a.e.heureDebut != null) heureFr(a.e.heureDebut) + (a.e.heureFin?.let { " – " + heureFr(it) } ?: "") else "Toute la journée", a.e.lieu).joinToString(" · "))
                        is Annonce.Cotisation -> Triple("Rappel · cotisation", "${euros(a.montant)} en retard", "Touchez pour voir comment régler")
                        is Annonce.Participations -> Triple("Rappel · participation", "${euros(a.dues.sumOf { it.montantAttendu!! - it.donne })} à régler", a.dues.joinToString(", ") { it.nom })
                        is Annonce.Info -> Triple((if (a.c.priorite == "haute") "Important · " else "") + "Communiqué", a.c.titre, a.c.texte?.lineSequence()?.firstOrNull() ?: "")
                    }
                    Text(sur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(titre, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sous.isNotEmpty()) Text(sous, fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (a is Annonce.Rdv) Surface(onClick = { onAgenda(a.e) }, shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.08f), contentColor = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.EditCalendar, contentDescription = "Ajouter à mon agenda", modifier = Modifier.size(20.dp)) }
                } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
        if (annonces.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            annonces.indices.forEach { k ->
                val large by androidx.compose.animation.core.animateDpAsState(if (k == i) 16.dp else 6.dp, label = "point")
                Box(Modifier.height(6.dp).width(large).clip(RoundedCornerShape(3.dp)).background(if (k == i) Color.White else Color.White.copy(alpha = 0.45f)).clickable { i = k })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleCommunique(c: Communique, onFermer: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (c.priorite == "haute") Puce("Important", Couleurs.ErreurClair, Color(0xFF410002))
            }
            Text("Publié le ${dateFr(c.debut)}" + (c.fin?.let { " · jusqu’au ${dateFr(it)}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
            c.texte?.let { Text(it, fontSize = 15.sp) }
            Button(onClick = onFermer, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
        }
    }
}

// =====================================================================
// Paramètres › Bannière et communiqués (même section que paramBanniere du site)
// =====================================================================
@Composable
fun ParamBanniere(d: Donnees, message: (String) -> Unit) {
    val gere = d.peut("gerer_activites", "administrer")
    var version by remember { mutableStateOf(0) }
    var liste by remember { mutableStateOf<List<Communique>>(emptyList()) }
    var regles by remember { mutableStateOf(reglesBanniere(null)) }
    var edition by remember { mutableStateOf<Communique?>(null) }
    var nouveau by remember { mutableStateOf(false) }
    var lu by remember { mutableStateOf<Communique?>(null) }
    val scope = rememberCoroutineScope()
    val supprimer = rememberSuppression(message) { version++ }
    LaunchedEffect(version, Synchro.version) {
        try { liste = Repo.communiques(); regles = reglesBanniere(Repo.texteReglage("banniere")) } catch (e: Exception) { message(traduireErreur(e)) }
        EtatNouveautes.vu("communiques")
    }
    val auj = aujourdhui().toString()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Communiqués", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (gere) Button(onClick = { nouveau = true }) { Text("Nouveau") }
                }
                Text("Un communiqué s’affiche sur la bannière des membres pendant sa période, et chacun reçoit une notification.", fontSize = 13.sp, color = Couleurs.Texte2)
                if (liste.isEmpty()) Text("Aucun communiqué pour l’instant.", color = Couleurs.Texte2)
                liste.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (gere) edition = c else lu = c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(Color(0xFFE3F1FB), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Campaign, null, tint = Color(0xFF1B77B0)) }
                        Column(Modifier.weight(1f)) {
                            Text(c.titre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((when { c.debut > auj -> "À partir du ${dateFr(c.debut)}"; c.fin != null && c.fin < auj -> "Terminé"; c.fin != null -> "Jusqu’au ${dateFr(c.fin)}"; else -> "En cours" }) +
                                (if (c.visible) "" else " · bureau seulement"), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                        when (c.priorite) { "haute" -> Puce("Important", Couleurs.ErreurClair, Color(0xFF410002)); "basse" -> Puce("Discret", Color(0xFFEFEDEC), Couleurs.Texte2); else -> Puce("Normal", Color(0xFFEFEDEC), Couleurs.Texte2) }
                    }
                }
            }
        }
        if (d.peut("administrer")) item {
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
    }
    if (nouveau || edition != null) EditionCommunique(edition, onSupprimer = edition?.let { c -> { edition = null; supprimer("communiques", c.id, c.titre) } }, message = message,
        onFini = { nouveau = false; edition = null; version++ }) { nouveau = false; edition = null }
    lu?.let { FeuilleCommunique(it) { lu = null } }
}

@Composable
private fun EditionCommunique(c: Communique?, onSupprimer: (() -> Unit)?, message: (String) -> Unit, onFini: () -> Unit, onAnnuler: () -> Unit) {
    var titre by remember { mutableStateOf(c?.titre ?: "") }
    var texte by remember { mutableStateOf(c?.texte ?: "") }
    var debut by remember { mutableStateOf(dateFr(c?.debut ?: aujourdhui().toString())) }
    var fin by remember { mutableStateOf(c?.fin?.let { dateFr(it) } ?: "") }
    var priorite by remember { mutableStateOf(c?.priorite ?: "normale") }
    var visible by remember { mutableStateOf(c?.visible ?: true) }
    val scope = rememberCoroutineScope()
    val dDebut = dateDepuisFr(debut); val dFin = if (fin.isBlank()) null else dateDepuisFr(fin)
    val valide = titre.trim().length >= 2 && dDebut != null && (fin.isBlank() || (dFin != null && dFin >= dDebut))
    DialogueSimple(if (c == null) "Nouveau communiqué" else "Modifier le communiqué", if (c == null) "Publier" else "Enregistrer", valide, onAnnuler, {
        scope.launch {
            try { Repo.enregistrerCommunique(c?.id, NouveauCommunique(titre.trim(), texte.trim().ifBlank { null }, dDebut!!.toString(), dFin?.toString(), priorite, visible)); message(if (c == null) "Communiqué publié" else "Communiqué modifié"); onFini() }
            catch (e: Exception) { message(traduireErreur(e)) }
        }
    }) {
        OutlinedTextField(titre, { titre = it.take(120) }, label = { Text("Titre *") }, placeholder = { Text("Assemblée générale le 15 novembre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(texte, { texte = it.take(1500) }, label = { Text("Texte") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(debut, { debut = it }, label = { Text("À partir du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dDebut == null, modifier = Modifier.weight(1f))
            OutlinedTextField(fin, { fin = it }, label = { Text("Jusqu’au") }, placeholder = { Text("facultatif") }, singleLine = true, isError = fin.isNotBlank() && (dFin == null || (dDebut != null && dFin < dDebut)), modifier = Modifier.weight(1f))
        }
        val P = listOf("haute" to "Important (en premier)", "normale" to "Normal", "basse" to "Discret")
        Text("Priorité", fontWeight = FontWeight.SemiBold)
        PuceMenu(P.first { it.first == priorite }.second, false, P.map { it.second }) { priorite = P[it].first }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(visible, { visible = it }); Text("Visible par tous les membres (sinon bureau seulement)", fontSize = 14.sp) }
        onSupprimer?.let { BoutonSupprimer(it) }
    }
}
