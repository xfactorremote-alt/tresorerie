package org.jpgrenoble.tresorerie

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// =====================================================================
// Outils : tiers, rubriques, périodes de cotisation, heures
// =====================================================================
val TYPES_TIERS = mapOf("donateur" to "Donateur", "fournisseur" to "Fournisseur", "partenaire" to "Partenaire", "autre" to "Autre")
val PERIODICITES = mapOf(1 to "Mensuelle", 3 to "Trimestrielle", 6 to "Semestrielle", 12 to "Annuelle")
private val STATUTS_PERIODE = mapOf("regle" to "Réglé", "partiel" to "Partiel", "impaye" to "Impayé", "a_venir" to "À venir", "dispense" to "Dispensé")
internal val JOURS_COURTS = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
private val JOURS_LONGS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

internal fun lireMontant(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").replace(NBSP_FINE.toString(), "").toDoubleOrNull()
private fun norm(s: String) = sansAccentsCode(s)

// « 2026-03-01 » -> « mars 2026 » (mensuel), « mars–mai 2026 » (trimestriel), « 2026 » (annuel)
fun nomPeriode(p: String, pas: Int, court: Boolean = false): String {
    val m = p.substring(5, 7).toInt() - 1; val a = p.take(4)
    return when (pas) {
        12 -> a
        1 -> if (court) MOIS[m].take(3) else "${MOIS[m]} $a"
        else -> "${MOIS[m].take(3)}.–${MOIS[(m + pas - 1) % 12].take(3)}." + if (court) "" else " $a"
    }
}

// 09:30 -> « 9 h 30 », 18:00 -> « 18 h »
fun heureFr(h: String?): String {
    if (h.isNullOrBlank()) return ""
    val (hh, mm) = h.split(":").let { it[0] to it.getOrElse(1) { "00" } }
    return "${hh.toInt()}${NBSP}h" + if (mm != "00") "$NBSP$mm" else ""
}

// « 9h30 », « 9:30 », « 9 » -> « 09:30 » ; null si invalide
fun heureDepuisSaisie(s: String): String? {
    val r = Regex("^\\s*([01]?\\d|2[0-3])\\s*(?:[:hH.]\\s*([0-5]\\d)?)?\\s*$").find(s) ?: return null
    return "${r.groupValues[1].padStart(2, '0')}:${r.groupValues[2].ifBlank { "00" }}"
}

fun jourLong(s: String): String {
    val d = LocalDate.parse(s.take(10))
    return "${JOURS_LONGS[d.dayOfWeek.isoDayNumber - 1]} ${d.dayOfMonth} ${MOIS[d.monthNumber - 1]}"
}

// Tiers saisi : membre, tiers enregistré, ou nouveau nom
data class TiersTrouve(val membreId: String? = null, val tiersId: String? = null, val nouveau: String? = null)
fun trouverTiers(nom: String, membres: List<Membre>, tiers: List<Tiers>): TiersTrouve {
    val n = norm(nom)
    if (nom.isBlank()) return TiersTrouve()
    membres.firstOrNull { norm(it.nomComplet) == n }?.let { return TiersTrouve(membreId = it.id) }
    tiers.firstOrNull { norm(it.nom) == n }?.let { return TiersTrouve(tiersId = it.id) }
    return TiersTrouve(nouveau = nom.trim())
}
fun nomTiers(e: Ecriture, membres: List<Membre>, tiers: List<Tiers>) =
    e.membreId?.let { id -> membres.firstOrNull { it.id == id }?.nomComplet } ?: e.tiersId?.let { id -> tiers.firstOrNull { it.id == id }?.nom } ?: ""
fun nomRubrique(e: Ecriture, collectes: List<Collecte>) =
    if (e.estCotisation) "Cotisation" else e.collecteId?.let { id -> collectes.firstOrNull { it.id == id }?.nom ?: "Participation" } ?: ""

// Imputation d'un versement sur les périodes les plus anciennes non réglées
data class Couverture(val jusqua: String?, val partiel: String?, val avance: Double)
fun couverture(periodes: List<PeriodeCotisation>, montant: Double): Couverture {
    var reste = montant; var jusqua: String? = null; var partiel: String? = null
    for (p in periodes.sortedBy { it.periode }) {
        val du = p.du - p.regle
        if (du <= 0.005) { jusqua = p.periode; continue }
        if (reste >= du - 0.005) { reste -= du; jusqua = p.periode } else { if (reste > 0) partiel = p.periode; reste = 0.0; break }
    }
    return Couverture(jusqua, partiel, kotlin.math.round(reste * 100) / 100)
}
fun retardDe(periodes: List<PeriodeCotisation>): Double {
    val auj = aujourdhui().toString()
    return periodes.filter { it.periode <= auj }.sumOf { it.du - it.regle }
}

// Champ de saisie du tiers avec suggestions (membres et tiers enregistrés)
@Composable
fun ChampTiers(valeur: String, onChange: (String) -> Unit, suggestions: List<Pair<String, String>>, libelle: String) {
    var ouvert by remember { mutableStateOf(false) }
    val proposes = if (valeur.isBlank()) emptyList() else suggestions.filter { norm(it.first).contains(norm(valeur)) && it.first != valeur }.take(6)
    Box {
        OutlinedTextField(valeur, { onChange(it.take(80)); ouvert = true }, label = { Text(libelle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded = ouvert && proposes.isNotEmpty(), onDismissRequest = { ouvert = false }, properties = PopupProperties(focusable = false)) {
            proposes.forEach { (nom, type) ->
                DropdownMenuItem(text = { Column { Text(nom); Text(type, fontSize = 12.sp, color = Couleurs.Texte2) } }, onClick = { onChange(nom); ouvert = false })
            }
        }
    }
}

// Une case par période, colorée selon l'état
@Composable
fun GrillePeriodes(cases: List<Pair<String, String?>>, pas: Int, modifier: Modifier = Modifier) {
    // Douze mois : deux lignes de six pour que les noms restent lisibles
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) { cases.chunked(if (cases.size == 12) 6 else cases.size.coerceAtLeast(1)).forEach { rangee ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        rangee.forEach { (p, st) ->
            val (fond, texte) = when (st) {
                "regle" -> Couleurs.Bleu to Color.White
                "partiel" -> Couleurs.Jaune to Couleurs.SurJaune
                "impaye" -> Couleurs.ErreurClair to Color(0xFF410002)
                null -> Color.Transparent to Couleurs.Texte2
                else -> Color(0xFFEFEDEC) to Couleurs.Texte2
            }
            val etat = when (st) { "regle" -> "réglé"; "partiel" -> "partiel"; "impaye" -> "impayé"; "a_venir" -> "à venir"; "dispense" -> "dispensé"; null -> "non dû"; else -> st }
            Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(fond)
                .semantics { contentDescription = "${nomPeriode(p, pas)} : $etat" }
                .then(if (st == "impaye") Modifier.border(1.dp, Couleurs.Erreur, RoundedCornerShape(5.dp)) else if (st == null) Modifier.border(1.dp, Color(0xFFD9D3D0), RoundedCornerShape(5.dp)) else Modifier),
                contentAlignment = Alignment.Center) {
                if (pas == 1) Text(MOIS_COURTS[p.substring(5, 7).toInt() - 1], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = texte, maxLines = 1)
            }
        }
    }
    } }
}

@Composable
private fun Legende() {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("regle", "partiel", "impaye", "a_venir", "dispense").forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(36.dp)) { GrillePeriodes(listOf("2026-01-01" to st), 12) }
                Text(STATUTS_PERIODE[st] ?: "", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
    }
}

// Relance WhatsApp : même texte que lienRelance du site (comment régler, lien vers l'espace membre)
private val porteeRelance = kotlinx.coroutines.MainScope()
private fun lienRelance(uri: androidx.compose.ui.platform.UriHandler, m: Membre, objet: String) {
    val num = numeroWa(m.whatsapp) ?: return
    porteeRelance.launch {
        val infos = try { Repo.texteReglage("infos_paiement") } catch (_: Exception) { null }
        val lien = try { Repo.liens().firstOrNull { it.membreId == m.id } } catch (_: Exception) { null }
        val texte = "Bonjour ${m.prenom}, $objet. " +
            (infos?.takeIf { it.isNotBlank() }?.let { "Pour régler : " + it.trim().replace(Regex("\\s*\n\\s*"), " ; ") + "." } ?: "Vous pouvez régler en espèces auprès du trésorier ou par virement.") +
            (lien?.let { " Votre espace membre : ${Repo.urlLien(it, m)}" } ?: "") + " Merci."
        uri.openUri("https://wa.me/$num?text=${encoderUrl(texte)}")
    }
}

// =====================================================================
// Saisie d'une opération : tiers et rubrique (cotisation ou participation)
// =====================================================================
data class PreEcriture(val sens: String = "depense", val membreId: String? = null, val rubrique: String? = null, val montant: Double? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaireEcriture(d: Donnees, pre: PreEcriture = PreEcriture(), onFini: (Boolean) -> Unit, message: (String) -> Unit, onVirement: (() -> Unit)? = null) {
    val limite = !d.peut("saisir_ecritures")     // droit « cotisations » seul : encaissement rattaché à une rubrique
    var sens by remember { mutableStateOf(if (limite || pre.rubrique != null) "recette" else pre.sens) }
    var montant by remember { mutableStateOf(pre.montant?.takeIf { it > 0 }?.let { montantSaisie(it) } ?: "") }
    var libelle by remember { mutableStateOf("") }
    var libelleSaisi by remember { mutableStateOf(false) }
    var tiersNom by remember { mutableStateOf(pre.membreId?.let { id -> d.membres.firstOrNull { it.id == id }?.nomComplet } ?: "") }
    var rubrique by remember { mutableStateOf(pre.rubrique ?: if (limite) "cotisation" else "") }
    var date by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var mode by remember { mutableStateOf("especes") }
    val categories = d.categories.filter { it.sens == sens }
    var categorie by remember(sens) { mutableStateOf(categories.firstOrNull()) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var projet by remember { mutableStateOf<Projet?>(null) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var collectes by remember { mutableStateOf<List<Collecte>>(emptyList()) }
    var pas by remember { mutableStateOf(1) }
    var periodesMembre by remember { mutableStateOf<Pair<String, List<PeriodeCotisation>>?>(null) }
    var compte by remember(mode) { mutableStateOf(d.comptes.firstOrNull { it.type == if (mode == "especes") "caisse" else "banque" } ?: d.comptes.firstOrNull()) }
    var piece by remember { mutableStateOf<Fichier?>(null) }
    var faireValider by remember { mutableStateOf(true) }
    var enCours by remember { mutableStateOf(false) }
    // Combinaison compte / mode inhabituelle : signalée, puis confirmée par un second appui
    val alerteMode = incoherenceMode(compte, mode)
    var modeConfirme by remember(compte, mode) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val choix = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) piece = f }
    LaunchedEffect(Unit) {
        try { projets = Repo.projets() } catch (_: Exception) { }
        try { tiers = Repo.tiers() } catch (_: Exception) { }
        try { collectes = Repo.collectes() } catch (_: Exception) { }
        try { pas = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt() } catch (_: Exception) { }
    }
    val trouve = trouverTiers(tiersNom, d.membres, tiers)
    LaunchedEffect(trouve.membreId, rubrique) {
        val mid = trouve.membreId
        if (rubrique == "cotisation" && mid != null && periodesMembre?.first != mid) try { periodesMembre = mid to Repo.periodesMembre(mid) } catch (_: Exception) { }
    }
    // La rubrique choisit la catégorie, l'activité et propose le libellé
    LaunchedEffect(rubrique, tiersNom, sens, collectes, projets) {
        if (sens != "recette") return@LaunchedEffect
        fun cat(nom: String) = d.categories.firstOrNull { it.sens == "recette" && it.nom == nom }
        if (rubrique == "cotisation") {
            cat("Cotisations")?.let { categorie = it }
            if (!libelleSaisi) libelle = if (tiersNom.isBlank()) "Cotisation" else "Cotisation$NBSP: ${tiersNom.trim()}"
        } else if (rubrique.isNotEmpty()) {
            val co = collectes.firstOrNull { it.id == rubrique } ?: return@LaunchedEffect
            cat("Activités / événements")?.let { categorie = it }
            co.projetId?.let { id -> projets.firstOrNull { it.id == id }?.let { projet = it } }
            if (!libelleSaisi) libelle = if (tiersNom.isBlank()) co.nom else "${co.nom}$NBSP: ${tiersNom.trim()}"
        }
    }
    val valeur = lireMontant(montant)?.takeIf { it > 0 }
    val info: String? = when {
        sens != "recette" -> null
        rubrique == "cotisation" -> if (trouve.membreId == null) "Choisissez un membre dans la liste" else periodesMembre?.takeIf { it.first == trouve.membreId }?.second?.let { p ->
            val r = retardDe(p); val c = couverture(p, valeur ?: 0.0)
            (if (r > 0.005) "En retard$NBSP: ${euros(r)}" else "À jour") + if (valeur != null)
                " · Ce versement règle jusqu’à ${c.jusqua?.let { nomPeriode(it, pas) } ?: "–"}" + (c.partiel?.let { ", ${nomPeriode(it, pas)} en partie" } ?: "") +
                    (if (c.avance > 0) ", avance de ${euros(c.avance)}" else "") else ""
        }
        rubrique.isNotEmpty() -> collectes.firstOrNull { it.id == rubrique }?.montantAttendu?.let { "Attendu$NBSP: ${euros(it)} par personne" }
        else -> null
    }
    val ouvertes = collectes.filter { !it.cloturee || it.id == pre.rubrique }
    val rubriques = (if (limite) emptyList() else listOf("" to "Aucune")) + listOf("cotisation" to "Cotisation") + ouvertes.map { it.id to it.nom }
    val suggestions = d.membres.filter { it.actif }.map { it.nomComplet to "Membre" } + tiers.filter { it.actif }.map { it.nom to (TYPES_TIERS[it.type] ?: "") }
    val dateValide = dateDepuisFr(date)

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (pre.rubrique != null || limite) "Encaissement" else "Nouvelle opération", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!limite) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val choix = listOf("depense" to "Dépense", "recette" to "Recette") + if (onVirement != null && pre.rubrique == null) listOf("virement" to "Virement") else emptyList()
            choix.forEachIndexed { i, (k, v) ->
                SegmentedButton(selected = sens == k, onClick = { if (k == "virement") onVirement?.invoke() else sens = k }, shape = SegmentedButtonDefaults.itemShape(i, choix.size)) { Text(v) }
            }
        }
        OutlinedTextField(montant, { montant = it }, label = { Text("Montant") }, singleLine = true, suffix = { Text("€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChampTiers(tiersNom, { tiersNom = it }, suggestions, if (sens == "recette") "Tiers : membre ou donateur" else "Tiers : fournisseur")
        if (sens == "recette") ChoixListe("Rubrique", rubriques.firstOrNull { it.first == rubrique }?.second ?: "Aucune", rubriques.map { it.second }) { rubrique = rubriques[it].first }
        info?.let {
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
        OutlinedTextField(libelle, { libelle = it.take(120); libelleSaisi = it.isNotEmpty() }, label = { Text("Libellé") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date") }, singleLine = true, placeholder = { Text("JJ/MM/AAAA") },
            isError = dateValide == null, modifier = Modifier.fillMaxWidth())
        ChoixListe("Catégorie", categorie?.nom ?: "", categories.map { it.nom }) { i -> categorie = categories[i] }
        ChoixListe("Mode", MODES[mode] ?: mode, MODES.values.toList()) { mode = MODES.keys.toList()[it] }
        ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it] }
        if (alerteMode.isNotEmpty()) Surface(color = Couleurs.JauneClair, contentColor = Couleurs.SurJaune, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(alerteMode, Modifier.padding(12.dp), fontSize = 14.sp)
        }
        ChoixListe("Activité", projet?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { i -> projet = if (i == 0) null else projets[i - 1] }
        if (sens == "depense") OutlinedButton(onClick = choix, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(piece?.let { "Pièce jointe (${it.ko}$NBSP" + "Ko)" } ?: "Joindre une pièce")
        }
        if (sens == "depense") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { faireValider = !faireValider }) {
            Checkbox(faireValider, { faireValider = it }); Text("Faire valider par le président", fontSize = 15.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(
                enabled = !enCours && valeur != null && libelle.isNotBlank() && dateValide != null && categorie != null && compte != null,
                onClick = {
                    val rub = if (sens == "recette") rubrique else ""
                    if (rub == "cotisation" && trouve.membreId == null) { message("Une cotisation se rattache à un membre : choisissez-le dans la liste"); return@Button }
                    if (alerteMode.isNotEmpty() && !modeConfirme) { modeConfirme = true; message("Compte et mode inhabituels : vérifiez, puis enregistrez à nouveau pour confirmer"); return@Button }
                    enCours = true
                    scope.launch {
                        try {
                            val tiersId = trouve.tiersId ?: trouve.nouveau?.let { Repo.ajouterTiers(NouveauTiers(it, if (sens == "recette") "donateur" else "fournisseur")).id }
                            val id = Repo.ajouter(NouvelleEcriture(dateValide!!.toString(), compte!!.id, sens, valeur!!, categorie!!.id, libelle.trim(), mode, d.profil.id,
                                projet?.id, null, trouve.membreId, tiersId, rub == "cotisation", rub.takeIf { it.isNotEmpty() && it != "cotisation" }))
                            piece?.let { f -> Repo.joindrePiece(id, f, d.profil.id) }
                            if (sens == "depense" && faireValider) { Repo.demanderValidation(id); message("Dépense enregistrée et envoyée au président") }
                            onFini(true)
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                },
            ) { Text("Enregistrer", fontWeight = FontWeight.Bold) }
        }
    }
}

// =====================================================================
// Cotisations (par période) et participations aux activités
// =====================================================================
@Composable
fun EcranCotisations(d: Donnees, message: (String) -> Unit) {
    var onglet by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Cotisations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        TabRow(selectedTabIndex = onglet, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Cotisations", "Participations").forEachIndexed { i, t -> Tab(selected = onglet == i, onClick = { onglet = i }, text = { Text(t) }) }
        }
        Box(Modifier.weight(1f)) { if (onglet == 0) OngletCotisations(d, message) else OngletParticipations(d, message) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OngletCotisations(d: Donnees, message: (String) -> Unit) {
    val an = aujourdhui().year
    var annee by remember { mutableStateOf(an) }
    var synth by remember { mutableStateOf<List<Cotisation>?>(null) }
    var periodes by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var pas by remember { mutableStateOf(1) }
    var montantPeriode by remember { mutableStateOf(0.0) }
    var version by remember { mutableStateOf(0) }
    var filtre by remember { mutableStateOf("tous") }
    var encaisser by remember { mutableStateOf<PreEcriture?>(null) }
    var fiche by remember { mutableStateOf<Membre?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val gere = d.peut("gerer_cotisations")
    LaunchedEffect(annee, version, Synchro.version) {
        try {
            synth = Repo.cotisations(annee); periodes = Repo.periodes(annee)
            val r = Repo.reglages(); pas = (r["cotisation_periode_mois"] ?: 1.0).toInt(); montantPeriode = r["cotisation_montant"] ?: 0.0
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val parMembre = (synth ?: emptyList()).associateBy { it.membreId }
    val cases = periodes.map { it.periode }.distinct().sorted()
    val parCase = periodes.associateBy { it.membreId to it.periode }
    val lignes = d.membres.mapNotNull { m -> parMembre[m.id]?.let { m to it } }.sortedWith(compareBy({ -it.second.retard }, { it.first.nom }))
    val nbRetard = lignes.count { it.second.retard > 0.005 }
    val vues = lignes.filter { (_, c) -> filtre == "tous" || (filtre == "retard") == (c.retard > 0.005) }
    val sansCotis = d.membres.count { it.actif && parMembre[it.id] == null }
    var exporterCotis by remember { mutableStateOf(false) }
    val scopeExp = rememberCoroutineScope()
    val imprimerCotis = rememberImpression()
    val enregistrerCotis = rememberEnregistrer { it?.let(message) }
    if (exporterCotis) AlertDialog(
        onDismissRequest = { exporterCotis = false },
        title = { Text("Exporter les cotisations $annee") },
        text = { Text("PDF : état des cotisations mis en page. Excel : tableau modifiable (CSV).") },
        confirmButton = { Button(onClick = { exporterCotis = false; scopeExp.launch { try { val (t, h) = documentHtml(d, "cotisations", annee); imprimerCotis(t, h) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("PDF") } },
        dismissButton = { OutlinedButton(onClick = { exporterCotis = false; scopeExp.launch { try { enregistrerCotis("cotisations-$annee.csv", "text/csv", exportCsv(d, "cotisations", annee).encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Excel") } },
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuceMenu(annee.toString(), true, listOf(an + 1, an, an - 1, an - 2).map { it.toString() }) { annee = an + 1 - it }
                Text("${PERIODICITES[pas] ?: ""}, ${euros(montantPeriode)} par période", fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { exporterCotis = true }) { Text("Exporter") }
            }
        }
        if (gere && sansCotis > 0 && synth != null) item {
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$sansCotis membre${if (sansCotis > 1) "s" else ""} sans cotisation en $annee", Modifier.weight(1f), fontSize = 14.sp)
                    Button(onClick = { scope.launch { try { message("${Repo.genererCotisations(annee)} périodes créées"); version++ } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Générer") }
                }
            }
        }
        if (synth == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (lignes.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Indicateur("Encaissé", euros(lignes.sumOf { it.second.paye }), Couleurs.Bleu, Modifier.weight(1f))
                    Indicateur("En retard", euros(lignes.sumOf { it.second.retard }), Couleurs.Orange, Modifier.weight(1f))
                    Indicateur("À jour", "${lignes.size - nbRetard}$NBSP/$NBSP${lignes.size}", Couleurs.Texte, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("tous", "Tous", lignes.size), Triple("retard", "En retard", nbRetard), Triple("ajour", "À jour", lignes.size - nbRetard)).forEach { (k, l, n) ->
                        FilterChip(selected = filtre == k, onClick = { filtre = k }, label = { Text("$l ($n)") })
                    }
                }
            }
            item { Legende() }
            items(vues, key = { it.first.id }) { (m, c) ->
                CarteBlanche(onClick = { fiche = m }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(m.prenom, m.nom, 40)
                        Text(m.nomComplet, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.retard > 0.005) Puce("Retard ${euros(c.retard)}", Couleurs.ErreurClair, Color(0xFF410002)) else Puce("À jour", Couleurs.BleuClair, Couleurs.SurBleuClair)
                    }
                    GrillePeriodes(cases.map { it to parCase[m.id to it]?.statut }, pas)
                    if (c.avance > 0) Text("Avance$NBSP: ${euros(c.avance)}", fontSize = 13.sp, color = Couleurs.Texte2)
                    if (gere) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { encaisser = PreEcriture("recette", m.id, "cotisation", if (c.retard > 0.005) c.retard else montantPeriode) }) { Text("Encaisser") }
                        if (c.retard > 0.005 && numeroWa(m.whatsapp) != null) TextButton(onClick = {
                            lienRelance(uri, m, "votre cotisation à ${d.organisation.nom} présente un retard de ${euros(c.retard)}")
                        }) { Text("Relancer") }
                    }
                }
            }
        } else item { Text("Aucune cotisation pour $annee", color = Couleurs.Texte2) }
    }
    encaisser?.let { pre ->
        ModalBottomSheet(onDismissRequest = { encaisser = null }) {
            FormulaireEcriture(d, pre, onFini = { ok -> encaisser = null; if (ok) { message("Encaissement enregistré"); version++ } }, message)
        }
    }
    fiche?.let { m ->
        ModalBottomSheet(onDismissRequest = { fiche = null }) {
            FicheCotisation(d, m, annee, pas, message, onEncaisser = { pre -> fiche = null; encaisser = pre }, onChange = { version++ })
        }
    }
}

@Composable
private fun FicheCotisation(d: Donnees, m: Membre, annee: Int, pas: Int, message: (String) -> Unit, onEncaisser: (PreEcriture) -> Unit, onChange: () -> Unit) {
    var periodes by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var operations by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var collectes by remember { mutableStateOf<List<Collecte>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var edition by remember { mutableStateOf<PeriodeCotisation?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version, Synchro.version) {
        try {
            periodes = Repo.periodesMembre(m.id); operations = Repo.toutesEcritures().filter { it.membreId == m.id }.sortedByDescending { it.date }
            collectes = Repo.collectes()
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val gere = d.peut("gerer_cotisations")
    val retard = retardDe(periodes)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(m.prenom, m.nom)
            Text(m.nomComplet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (retard > 0.005) Puce("Retard ${euros(retard)}", Couleurs.ErreurClair, Color(0xFF410002)) else Puce("À jour", Couleurs.BleuClair, Couleurs.SurBleuClair)
        }
        Text("$annee", fontWeight = FontWeight.Bold, color = Couleurs.Texte2)
        periodes.filter { it.annee == annee }.sortedBy { it.periode }.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable(enabled = gere) { edition = p }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nomPeriode(p.periode, pas), fontWeight = FontWeight.SemiBold)
                    Text("${euros(p.regle)} sur ${euros(p.du)}", fontSize = 13.sp, color = Couleurs.Texte2)
                }
                val (f, c) = when (p.statut) { "regle" -> Couleurs.BleuClair to Couleurs.SurBleuClair; "partiel" -> Couleurs.JauneClair to Couleurs.SurJaune
                    "impaye" -> Couleurs.ErreurClair to Color(0xFF410002); else -> Color(0xFFEFEDEC) to Couleurs.Texte2 }
                Puce(STATUTS_PERIODE[p.statut] ?: p.statut, f, c)
            }
        }
        Text("Versements", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, modifier = Modifier.padding(top = 8.dp))
        val versements = operations.filter { it.estCotisation }
        if (versements.isEmpty()) Text("Aucun versement", color = Couleurs.Texte2)
        versements.forEach { e -> LigneSimple(dateFr(e.date), MODES[e.mode] ?: e.mode, e.signe) }
        val parts = operations.filter { it.collecteId != null }
        if (parts.isNotEmpty()) {
            Text("Participations", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, modifier = Modifier.padding(top = 8.dp))
            parts.forEach { e -> LigneSimple(nomRubrique(e, collectes), dateFr(e.date), e.signe) }
        }
        if (gere) Button(onClick = { onEncaisser(PreEcriture("recette", m.id, "cotisation", retard.takeIf { it > 0.005 })) }, modifier = Modifier.align(Alignment.End)) { Text("Encaisser") }
    }
    edition?.let { p ->
        var v by remember(p.periode) { mutableStateOf(montantSaisie(p.du)) }
        DialogueSimple(nomPeriode(p.periode, pas), "Enregistrer", lireMontant(v)?.let { it >= 0 } == true, { edition = null }, {
            scope.launch {
                try { Repo.majDu(m.id, p.periode, lireMontant(v)!!); message(if (lireMontant(v) == 0.0) "Période dispensée" else "Montant dû modifié"); version++; onChange() }
                catch (e: Exception) { message(traduireErreur(e)) }
                edition = null
            }
        }) {
            OutlinedTextField(v, { v = it }, label = { Text("Montant dû") }, suffix = { Text("€") }, singleLine = true,
                supportingText = { Text("0 pour dispenser") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}

@Composable
private fun LigneSimple(titre: String, sous: String, montant: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sous.isNotBlank()) Text(sous, fontSize = 13.sp, color = Couleurs.Texte2)
        }
        Text((if (montant >= 0) "+ " else "− ") + euros(kotlin.math.abs(montant)), color = if (montant >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Participations ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OngletParticipations(d: Donnees, message: (String) -> Unit) {
    var liste by remember { mutableStateOf<List<Collecte>?>(null) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Collecte?>(null) }
    var formulaire by remember { mutableStateOf<Pair<Collecte?, String?>?>(null) }
    LaunchedEffect(version, Synchro.version) {
        try { liste = Repo.collectes(); projets = Repo.projets() } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val gere = d.peut("gerer_activites", "gerer_cotisations")
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val l = liste
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (l.isEmpty()) item { Text("Aucune collecte", color = Couleurs.Texte2) }
            else items(l, key = { it.id }) { c ->
                val vise = c.objectif ?: (c.montantAttendu?.let { it * c.nbConcernes })
                CarteBlanche(onClick = { detail = c }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (c.cloturee) Puce("Clôturée", Color(0xFFEFEDEC), Couleurs.Texte2)
                    }
                    Text(listOfNotNull(projets.firstOrNull { it.id == c.projetId }?.nom, c.montantAttendu?.let { "${euros(it)} par personne" } ?: "Montant libre",
                        c.dateLimite?.let { "avant le ${dateFr(it)}" }).joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
                    Text(euros(c.totalRecu) + (vise?.let { " sur ${euros(it)}" } ?: ""), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    if (vise != null && vise > 0) LinearProgressIndicator(progress = { (c.totalRecu / vise).toFloat().coerceIn(0f, 1f) }, color = Couleurs.Bleu,
                        trackColor = Color(0xFFEFEDEC), modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text("${c.nbContributeurs} contributeur${if (c.nbContributeurs > 1) "s" else ""} · ${c.nbConcernes} membre${if (c.nbConcernes > 1) "s" else ""} concerné${if (c.nbConcernes > 1) "s" else ""}",
                        fontSize = 13.sp, color = Couleurs.Texte2)
                }
            }
        }
        if (gere) ExtendedFloatingActionButton(onClick = { formulaire = null to null }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nouvelle collecte") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
    detail?.let { c ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailCollecte(d, c, projets, message, onModifier = { detail = null; formulaire = c to c.projetId }, onChange = { version++ }, onFermer = { detail = null })
        }
    }
    formulaire?.let { (c, p) ->
        ModalBottomSheet(onDismissRequest = { formulaire = null }) {
            FormulaireCollecte(d, c, p, projets, message) { ok -> formulaire = null; if (ok) version++ }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCollecte(d: Donnees, c0: Collecte, projets: List<Projet>, message: (String) -> Unit, onModifier: () -> Unit, onChange: () -> Unit, onFermer: () -> Unit) {
    var c by remember { mutableStateOf(c0) }
    var operations by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var choisis by remember { mutableStateOf<List<String>>(emptyList()) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var filtre by remember { mutableStateOf("tous") }
    var version by remember { mutableStateOf(0) }
    var encaisser by remember { mutableStateOf<PreEcriture?>(null) }
    val supprimerCollecte = rememberSuppression(message) { onChange(); onFermer() }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val enregistrer = rememberEnregistrer { it?.let(message) }
    LaunchedEffect(version, Synchro.version) {
        try {
            operations = Repo.toutesEcritures().filter { it.collecteId == c.id }
            if (!c.tousMembres) choisis = Repo.collecteMembres(c.id)
            tiers = Repo.tiers()
            Repo.collectes().firstOrNull { it.id == c.id }?.let { c = it }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val concernes = if (c.tousMembres) d.membres.filter { it.actif } else d.membres.filter { it.id in choisis }
    fun donne(id: String) = operations.filter { it.membreId == id }.sumOf { it.montant }
    val att = c.montantAttendu ?: 0.0
    val lignes = concernes.map { it to donne(it.id) }.sortedWith(compareBy({ it.second }, { it.first.nom }))
    val autres = operations.filter { e -> concernes.none { it.id == e.membreId } }
    val gere = d.peut("gerer_cotisations")
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(c.nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (c.cloturee) Puce("Clôturée", Color(0xFFEFEDEC), Couleurs.Texte2)
        }
        Text(listOfNotNull(projets.firstOrNull { it.id == c.projetId }?.nom, if (att > 0) "${euros(att)} par personne" else "Montant libre",
            c.dateLimite?.let { "avant le ${dateFr(it)}" }).joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Indicateur("Reçu", euros(operations.sumOf { it.montant }), Couleurs.Bleu, Modifier.weight(1f))
            Indicateur("Ont donné", "${lignes.count { it.second > 0 }}$NBSP/$NBSP${lignes.size}", Couleurs.Texte, Modifier.weight(1f))
            Indicateur(if (att > 0) "Reste attendu" else "Objectif", if (att > 0) euros(lignes.sumOf { maxOf(0.0, att - it.second) }) else c.objectif?.let { euros(it) } ?: "–", Couleurs.Orange, Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("tous" to "Tous", "donne" to "Ont donné", "pas" to "N’ont pas donné").forEach { (k, l) -> FilterChip(selected = filtre == k, onClick = { filtre = k }, label = { Text(l) }) }
        }
        val vusCollecte = lignes.filter { (_, v) -> filtre == "tous" || (filtre == "donne") == (v > 0) }
        if (vusCollecte.isEmpty()) Text("Personne", color = Couleurs.Texte2)
        vusCollecte.forEach { (m, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(m.prenom, m.nom, 40)
                Column(Modifier.weight(1f)) {
                    Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(euros(v) + if (att > 0) " sur ${euros(att)}" else "", fontSize = 13.sp, color = Couleurs.Texte2)
                }
                // Situation toujours affichée (comme sur le site), boutons en plus pour le gestionnaire
                if (att > 0) {
                    if (v >= att) Puce("Réglé", Couleurs.BleuClair, Couleurs.SurBleuClair) else if (v > 0) Puce("Partiel", Couleurs.JauneClair, Couleurs.SurJaune) else Puce("À régler", Couleurs.ErreurClair, Color(0xFF410002))
                } else if (v > 0) Puce("Donné", Couleurs.BleuClair, Couleurs.SurBleuClair)
                if (gere && !c.cloturee) {
                    if ((att == 0.0 || v < att) && numeroWa(m.whatsapp) != null) TextButton(onClick = {
                        lienRelance(uri, m, "pour « ${c.nom} », la participation demandée est de ${if (att > 0) euros(att - v) else "votre choix"}")
                    }) { Text("Relancer") }
                    FilledTonalButton(onClick = { encaisser = PreEcriture("recette", m.id, c.id, if (att > 0) maxOf(att - v, 0.0).takeIf { it > 0 } ?: att else null) }) { Text("Encaisser") }
                }
            }
        }
        if (autres.isNotEmpty()) {
            Text("Autres contributions", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, modifier = Modifier.padding(top = 8.dp))
            autres.forEach { e -> LigneSimple(nomTiers(e, d.membres, tiers).ifBlank { e.libelle }, dateFr(e.date), e.signe) }
        }
        FlowRowActions {
            OutlinedButton(onClick = {
                val statut = { v: Double -> if (att > 0) (if (v >= att) "Réglé" else if (v > 0) "Partiel" else "À régler") else if (v > 0) "Donné" else "" }
                val csv = "﻿" + (listOf("Prénom;Nom;Donné;Attendu;Statut") + lignes.map { (m, v) -> "${m.prenom};${m.nom};${montantSaisie(v)};${if (att > 0) montantSaisie(att) else ""};${statut(v)}" } +
                    autres.map { e -> ";${nomTiers(e, d.membres, tiers).ifBlank { e.libelle }};${montantSaisie(e.montant)};;Autre contribution" }).joinToString("\r\n")
                enregistrer("participations-${sansAccentsCode(c.nom)}.csv", "text/csv", csv.encodeToByteArray())
            }) { Text("Exporter") }
            if (d.peut("gerer_activites", "gerer_cotisations")) {
                TextButton(onClick = onModifier) { Text("Modifier") }
                TextButton(onClick = { scope.launch { try { Repo.cloturerCollecte(c.id, !c.cloturee); message(if (c.cloturee) "Collecte rouverte" else "Collecte clôturée"); version++; onChange() } catch (e: Exception) { message(traduireErreur(e)) } } }) {
                    Text(if (c.cloturee) "Rouvrir" else "Clôturer")
                }
            }
            if (gere && !c.cloturee) Button(onClick = { encaisser = PreEcriture("recette", null, c.id) }) { Text("Autre encaissement") }
            if (d.peut("gerer_activites", "gerer_cotisations") && operations.none { it.collecteId == c.id }) BoutonSupprimer({ supprimerCollecte("collectes", c.id, c.nom) })
        }
    }
    encaisser?.let { pre ->
        ModalBottomSheet(onDismissRequest = { encaisser = null }) {
            FormulaireEcriture(d, pre, onFini = { ok -> encaisser = null; if (ok) { message("Encaissement enregistré"); version++; onChange() } }, message)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowActions(content: @Composable () -> Unit) =
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
fun FormulaireCollecte(d: Donnees, c: Collecte?, preProjet: String?, projets: List<Projet>, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    var nom by remember { mutableStateOf(c?.nom ?: preProjet?.let { id -> projets.firstOrNull { it.id == id }?.let { "Participation$NBSP: ${it.nom}" } } ?: "") }
    var projet by remember { mutableStateOf(c?.projetId ?: preProjet) }
    var attendu by remember { mutableStateOf(c?.montantAttendu?.let { montantSaisie(it) } ?: "") }
    var objectif by remember { mutableStateOf(c?.objectif?.let { montantSaisie(it) } ?: "") }
    var limite by remember { mutableStateOf(c?.dateLimite?.let { dateFr(it) } ?: "") }
    var tous by remember { mutableStateOf(c?.tousMembres ?: true) }
    val choisis = remember { mutableStateListOf<String>() }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(c?.id) { if (c != null && !c.tousMembres) try { choisis.addAll(Repo.collecteMembres(c.id)) } catch (_: Exception) { } }
    val dLimite = if (limite.isBlank()) null else dateDepuisFr(limite)
    val valide = nom.isNotBlank() && (attendu.isBlank() || lireMontant(attendu)?.let { it > 0 } == true) && (objectif.isBlank() || lireMontant(objectif)?.let { it > 0 } == true) &&
        (limite.isBlank() || dLimite != null) && (tous || choisis.isNotEmpty())
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (c == null) "Nouvelle collecte" else "Modifier la collecte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoixListe("Activité", projets.firstOrNull { it.id == projet }?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { i ->
            projet = if (i == 0) null else projets[i - 1].id
            if (nom.isBlank() && i > 0) nom = "Participation$NBSP: ${projets[i - 1].nom}"
        }
        OutlinedTextField(attendu, { attendu = it }, label = { Text("Montant par personne") }, placeholder = { Text("Libre") }, suffix = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(objectif, { objectif = it }, label = { Text("Objectif total") }, suffix = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(limite, { limite = it }, label = { Text("Date limite") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
            isError = limite.isNotBlank() && dLimite == null, modifier = Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(true to "Tous les membres", false to "Choisir").forEachIndexed { i, (k, v) ->
                SegmentedButton(selected = tous == k, onClick = { tous = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
            }
        }
        if (!tous) d.membres.filter { it.actif }.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { if (m.id in choisis) choisis.remove(m.id) else choisis.add(m.id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(m.id in choisis, { if (it) choisis.add(m.id) else choisis.remove(m.id) }); Text(m.nomComplet)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = valide && !enCours, onClick = {
                enCours = true
                scope.launch {
                    try {
                        Repo.enregistrerCollecte(c?.id, NouvelleCollecte(nom.trim(), projet, lireMontant(attendu), lireMontant(objectif), dLimite?.toString(), tous), choisis.toList())
                        message(if (c == null) "Collecte créée" else "Collecte modifiée"); onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
    }
}

// =====================================================================
// Planning : mois, semaine, agenda (et suivi des activités pour le bureau)
// =====================================================================
object PreferencesPlanning { var vue = "calendrier"; var affichage = "mois" }

// Planning : même conception que le site. Deux onglets, « Calendrier » (affichage Mois ou Semaine,
// navigation, Aujourd'hui) et « À venir » (rendez-vous des douze prochains mois, par mois).
// Un appui sur un jour ouvre le détail du jour.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPlanning(d: Donnees, message: (String) -> Unit, onBudget: (() -> Unit)? = null) {
    var vue by remember { mutableStateOf(PreferencesPlanning.vue.takeIf { it in listOf("calendrier", "avenir") } ?: "calendrier") }
    var affichage by remember { mutableStateOf(PreferencesPlanning.affichage.takeIf { it in listOf("mois", "semaine") } ?: "mois") }
    var ref by remember { mutableStateOf(aujourdhui()) }
    var evts by remember { mutableStateOf<List<Projet>?>(null) }
    var anniv by remember { mutableStateOf<Map<Int, List<Anniversaire>>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Projet?>(null) }
    var jour by remember { mutableStateOf<String?>(null) }
    var nouveau by remember { mutableStateOf<String?>(null) }
    var sens by remember { mutableStateOf(1) }   // le mois suivant arrive par la droite
    val (debut, fin) = when {
        vue == "avenir" -> aujourdhui() to aujourdhui().plus(DatePeriod(days = 365))
        affichage == "mois" -> { val p = LocalDate(ref.year, ref.monthNumber, 1); val dern = p.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            p.minus(DatePeriod(days = p.dayOfWeek.isoDayNumber - 1)) to dern.plus(DatePeriod(days = 7 - dern.dayOfWeek.isoDayNumber)) }
        else -> { val l = ref.minus(DatePeriod(days = ref.dayOfWeek.isoDayNumber - 1)); l to l.plus(DatePeriod(days = 6)) }
    }
    LaunchedEffect(vue, debut, fin, version, Synchro.version) {
        try {
            evts = Repo.planning(debut.toString(), fin.toString())
            val mois = if (vue == "avenir") emptySet() else generateSequence(debut) { it.plus(DatePeriod(days = 7)) }.takeWhile { it <= fin }.map { it.monthNumber }.toSet() + fin.monthNumber
            anniv = mois.associateWith { m -> try { Repo.anniversaires(m) } catch (_: Exception) { emptyList() } }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val parJour = remember(evts) {
        val m = mutableMapOf<String, MutableList<Projet>>()
        (evts ?: emptyList()).forEach { e ->
            var j = LocalDate.parse(e.debut!!.take(10)); val f = LocalDate.parse((e.fin ?: e.debut).take(10)); var i = 0
            while (j <= f && i < 62) { m.getOrPut(j.toString()) { mutableListOf() } += e; j = j.plus(DatePeriod(days = 1)); i++ }
        }
        m
    }
    fun annivDe(s: String): List<Anniversaire> { val j = LocalDate.parse(s); return anniv[j.monthNumber].orEmpty().filter { it.jour == j.dayOfMonth } }
    val gere = d.peut("gerer_activites")
    val auj = aujourdhui().toString()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Titre("Planning") }
            item {
                TabRow(selectedTabIndex = if (vue == "calendrier") 0 else 1, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("calendrier" to "Calendrier", "avenir" to "À venir").forEach { (k, l) ->
                        Tab(selected = vue == k, onClick = { vue = k; PreferencesPlanning.vue = k }, text = { Text(l) })
                    }
                }
            }
            if (vue == "calendrier") {
                // Bandeau coloré du mois (une couleur par mois), flèches rondes ; glisser pour changer de mois
                item {
                    val couleurMois = EVT_PALETTE[(ref.monthNumber - 1) % EVT_PALETTE.size].third
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(couleurMois, couleurMois.copy(red = couleurMois.red * 0.7f, green = couleurMois.green * 0.7f, blue = couleurMois.blue * 0.7f))))
                        .padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FlecheMois(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Précédent") { sens = -1; ref = if (affichage == "mois") LocalDate(ref.year, ref.monthNumber, 1).minus(DatePeriod(months = 1)) else ref.minus(DatePeriod(days = 7)) }
                        AnimatedContent(if (affichage == "mois") "${MOIS[ref.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${ref.year}" else "Semaine du ${debut.dayOfMonth} ${MOIS[debut.monthNumber - 1]}",
                            Modifier.weight(1f), transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) }) { t ->
                            Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                        }
                        FlecheMois(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Suivant") { sens = 1; ref = if (affichage == "mois") LocalDate(ref.year, ref.monthNumber, 1).plus(DatePeriod(months = 1)) else ref.plus(DatePeriod(days = 7)) }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                            listOf("mois" to "Mois", "semaine" to "Semaine").forEachIndexed { i, (k, l) ->
                                SegmentedButton(selected = affichage == k, onClick = { affichage = k; PreferencesPlanning.affichage = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                            }
                        }
                        OutlinedButton(onClick = { sens = if (aujourdhui() > ref) 1 else -1; ref = aujourdhui() }) { Text("Aujourd’hui") }
                    }
                }
                if (evts == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (affichage == "mois") {
                    item {
                        val nb = (evts ?: emptyList()).count { LocalDate.parse(it.debut!!.take(10)).monthNumber == ref.monthNumber }
                        val na = anniv[ref.monthNumber].orEmpty().size
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Couleurs.Texte)) { append("$nb") }; append(" rendez-vous ce mois")
                            if (na > 0) { append(" · "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Couleurs.Texte)) { append("$na") }; append(" anniversaire${if (na > 1) "s" else ""}") }
                        }, fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    item {
                        AnimatedContent(ref.year * 12 + ref.monthNumber, transitionSpec = {
                            (slideInHorizontally { w -> sens * w / 3 } + fadeIn()) togetherWith (slideOutHorizontally { w -> -sens * w / 3 } + fadeOut())
                        }, modifier = Modifier.pointerInput(affichage) {
                            var cumul = 0f
                            detectHorizontalDragGestures(onDragEnd = {
                                if (cumul < -120) { sens = 1; ref = LocalDate(ref.year, ref.monthNumber, 1).plus(DatePeriod(months = 1)) }
                                else if (cumul > 120) { sens = -1; ref = LocalDate(ref.year, ref.monthNumber, 1).minus(DatePeriod(months = 1)) }
                                cumul = 0f
                            }) { _, dx -> cumul += dx }
                        }) { _ -> CalendrierMois(ref, debut, fin, parJour, ::annivDe) { jour = it } }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { EVT_PALETTE.take(4).forEach { Box(Modifier.size(width = 6.dp, height = 4.dp).background(it.third)) } }
                                Text("Rendez-vous (une couleur chacun)", fontSize = 12.sp, color = Couleurs.Texte2)
                            }
                            Legende(Couleurs.Jaune, "Anniversaire")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(10.dp).background(Couleurs.Orange, CircleShape)); Text("Aujourd’hui", fontSize = 12.sp, color = Couleurs.Texte2) }
                            Text("Touchez un jour pour le détail", fontSize = 12.sp, color = Couleurs.Texte2)
                        }
                    }
                } else item {
                    // Semaine : sept jours, logo en filigrane comme le calendrier du mois
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) { Box {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            generateSequence(debut) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= fin }.map { it.toString() }.forEach { s ->
                                LigneJour(s, s == auj, parJour[s].orEmpty(), annivDe(s), onJour = { jour = s }) { detail = it }
                            }
                        }
                        Filigrane(Modifier.align(Alignment.Center))
                    } }
                }
            } else {
                item { Text("Les rendez-vous des douze prochains mois.", color = Couleurs.Texte2) }
                val l = (evts ?: emptyList()).filter { (it.fin ?: it.debut ?: "") >= auj }.sortedBy { it.debut }
                if (evts != null && l.isEmpty()) item {
                    CarteBlanche {
                        Text("Aucun rendez-vous prévu.", color = Couleurs.Texte2)
                        if (gere) Button(onClick = { nouveau = auj }) { Text("Ajouter un rendez-vous") }
                    }
                }
                l.groupBy { it.debut!!.take(7) }.forEach { (m, liste) ->
                    item(key = m) {
                        CarteBlanche {
                            Text("${MOIS[m.substring(5, 7).toInt() - 1].replaceFirstChar { it.uppercase() }} ${m.take(4)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            liste.forEach { e ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PastilleDate(e.debut!!.take(10), e.debut.take(10) == auj) {}
                                    Column(Modifier.weight(1f)) {
                                        CarteEvenement(e) { detail = e }
                                        if (e.fin != null && e.fin.take(10) != e.debut.take(10)) Text("Jusqu’au ${jourLong(e.fin.take(10))}", fontSize = 12.sp, color = Couleurs.Texte2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (gere) LargeFloatingActionButton(onClick = { nouveau = if (vue == "calendrier" && affichage == "mois" && ref.monthNumber != aujourdhui().monthNumber) LocalDate(ref.year, ref.monthNumber, 1).toString() else auj },
            containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Nouveau rendez-vous")
        }
    }
    // Détail du jour (comme sur le site) : rendez-vous, anniversaires, ajout
    jour?.let { s ->
        ModalBottomSheet(onDismissRequest = { jour = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(jourLong(s).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val l = parJour[s].orEmpty()
                if (l.isEmpty()) Text("Rien de prévu", color = Couleurs.Texte2)
                l.forEach { e -> CarteEvenement(e, jourDetail = true) { jour = null; detail = e } }
                annivDe(s).forEach { PuceAnniversaire(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (gere) FilledTonalButton(onClick = { jour = null; nouveau = s }) { Text("Ajouter un rendez-vous") }
                    Button(onClick = { jour = null }) { Text("Fermer") }
                }
            }
        }
    }
    detail?.let { e ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailEvenement(d, e, message, onChange = { version++ }, onFermer = { detail = null }, onBudget = onBudget)
        }
    }
    nouveau?.let { s ->
        FormulaireActivite(null, onFini = { ok -> nouveau = null; if (ok) { message("Ajouté au planning"); version++ } }, message, dateInitiale = s)
    }
}

@Composable
private fun Legende(couleur: Color, texte: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.size(width = 14.dp, height = 4.dp).background(couleur, RoundedCornerShape(2.dp))); Text(texte, fontSize = 12.sp, color = Couleurs.Texte2)
}

@Composable
private fun PastilleDate(s: String, aujourdhuiOui: Boolean, onClick: () -> Unit) =
    Surface(onClick = onClick, color = if (aujourdhuiOui) Couleurs.Orange else if (LocalDate.parse(s).dayOfWeek.isoDayNumber >= 6) Color(0xFFFFEDE5) else Color(0xFFEFEDEC), contentColor = if (aujourdhuiOui) Color.White else Couleurs.Texte,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(s.takeLast(2).trimStart('0'), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(JOURS_COURTS[LocalDate.parse(s).dayOfWeek.isoDayNumber - 1], fontSize = 11.sp)
        }
    }

@Composable
private fun LigneJour(s: String, estAuj: Boolean, evts: List<Projet>, anniv: List<Anniversaire>, onJour: () -> Unit, onEvt: (Projet) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PastilleDate(s, estAuj, onJour)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            evts.forEach { e -> CarteEvenement(e) { onEvt(e) } }
            anniv.forEach { PuceAnniversaire(it) }
            if (evts.isEmpty() && anniv.isEmpty()) Text("–", color = Couleurs.Texte2, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// Logo de l'association en filigrane : très discret, laisse passer les appuis
@Composable
private fun Filigrane(modifier: Modifier) =
    LogoAsso(modifier.fillMaxWidth(0.6f).aspectRatio(1f).graphicsLayer { alpha = 0.08f }.clip(CircleShape).clearAndSetSemantics { })

@Composable
private fun CalendrierMois(ref: LocalDate, debut: LocalDate, fin: LocalDate, parJour: Map<String, List<Projet>>,
                           annivDe: (String) -> List<Anniversaire>, onJour: (String) -> Unit) {
    val jours = generateSequence(debut) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= fin }.toList()
    val auj = aujourdhui().toString()
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) { Box {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row { JOURS_COURTS.forEachIndexed { i, t -> Text(t, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = if (i >= 5) Couleurs.Orange else Couleurs.Texte2, fontWeight = FontWeight.Bold) } }
            jours.chunked(7).forEach { semaine ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    semaine.forEachIndexed { k, j ->
                        val s = j.toString()
                        val e = parJour[s].orEmpty(); val a = annivDe(s)
                        val hors = j.monthNumber != ref.monthNumber
                        val weekEnd = k >= 5
                        val fondJour = when { s == auj -> Color(0xFFFFF4EF); e.isNotEmpty() -> Color.White; weekEnd -> Color(0xFFFFF7F3); else -> Color(0xFFFAFAF9) }
                        Column(Modifier.weight(1f).height(88.dp).graphicsLayer { alpha = if (hors) 0.5f else 1f }.clip(RoundedCornerShape(12.dp))
                            .background(fondJour)
                            .then(if (s == auj) Modifier.border(2.dp, Couleurs.Orange, RoundedCornerShape(12.dp)) else if (e.isNotEmpty()) Modifier.border(1.dp, Color(0xFFE8E3E1), RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onJour(s) }.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.size(22.dp).background(if (s == auj) Couleurs.Orange else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text(j.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (s == auj) Color.White else if (hors) Color(0xFFB0A9A6) else Couleurs.Texte)
                            }
                            // Comme le site : nom des rendez-vous (3 au plus, avec l'heure), « +N », prénoms des anniversaires
                            e.take(3).forEach { ev ->
                                val (fondEvt, encre, accent) = couleurEvt(ev.id)
                                Text((ev.heureDebut?.let { it.take(5) + " " } ?: "") + ev.nom, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = encre, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(fondEvt)
                                        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }.padding(start = 4.dp, end = 2.dp))
                            }
                            if (e.size > 3) Text("+${e.size - 3}", fontSize = 9.sp, lineHeight = 10.sp, color = Couleurs.Texte2)
                            if (a.isNotEmpty()) Text(a.joinToString(", ") { it.prenom }, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = Couleurs.SurJaune, modifier = Modifier.fillMaxWidth().background(Couleurs.JauneClair, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp))
                        }
                    }
                }
            }
        }
        // Logo de l'association en filigrane : très discret, laisse passer les appuis sur les jours
        Filigrane(Modifier.align(Alignment.Center))
    } }
}

@Composable
private fun CarteEvenement(e: Projet, jourDetail: Boolean = false, onClick: () -> Unit) {
    val (fond, texte, accent) = couleurEvt(e.id)
    Surface(onClick = onClick, color = fond, contentColor = texte, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.nom, fontWeight = FontWeight.Bold)
                val l = listOfNotNull(if (e.heureDebut != null) heureFr(e.heureDebut) + (e.heureFin?.let { " – " + heureFr(it) } ?: "") else "Journée", e.lieu)
                Text(l.joinToString(" · "), fontSize = 13.sp)
            }
            // Comme le site : dans le détail du jour, l'activité suivie au budget est signalée ; ailleurs, la participation
            if (jourDetail) { if (e.type == "activite") Puce("Budget suivi", Color(0xFFEFEDEC), Couleurs.Texte2) }
            else e.participation?.let { Puce("Participation ${euros(it)}", Couleurs.JauneClair, Couleurs.SurJaune) }
        }
    }
}

@Composable
private fun PuceAnniversaire(a: Anniversaire) = Puce("Anniversaire de ${a.prenom} ${a.nom}", Couleurs.JauneClair, Couleurs.SurJaune)

@Composable
fun DetailEvenement(d: Donnees, e: Projet, message: (String) -> Unit, onChange: () -> Unit, onFermer: () -> Unit, onBudget: (() -> Unit)? = null) {
    val supprimerEvt = rememberSuppression(message) { onChange(); onFermer() }
    var collecte by remember { mutableStateOf<Collecte?>(null) }
    var maPart by remember { mutableStateOf<Participation?>(null) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var modifier by remember { mutableStateOf<Projet?>(null) }
    var demander by remember { mutableStateOf(false) }
    var voir by remember { mutableStateOf(false) }
    var modifierCollecte by remember { mutableStateOf(false) }
    val finances = d.peut("consulter_finances", "gerer_cotisations", "gerer_activites")
    val budgetSuivi = e.type == "activite" && d.peut("consulter_finances", "gerer_budget")
    LaunchedEffect(e.id) {
        try {
            if (e.collecteId != null) {
                if (finances) collecte = Repo.collectes().firstOrNull { it.id == e.collecteId }
                else maPart = Repo.mesParticipations().firstOrNull { it.collecteId == e.collecteId }
            }
            if (d.peut("gerer_activites")) projets = Repo.projets()
        } catch (_: Exception) { }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(e.nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (budgetSuivi) Puce("Budget suivi", Color(0xFFEFEDEC), Couleurs.Texte2)
        }
        LigneInfo("Date", e.debut?.let { jourLong(it) + if (e.fin != null && e.fin != e.debut) " au " + jourLong(e.fin) else "" } ?: "Date à fixer")
        LigneInfo("Heure", e.heureDebut?.let { heureFr(it) + (e.heureFin?.let { f -> " – " + heureFr(f) } ?: "") })
        LigneInfo("Lieu", e.lieu)
        if (d.peut("gerer_activites")) LigneInfo("Visibilité", if (e.visible) "Tous les membres" else "Bureau seulement")
        e.description?.let { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
        collecte?.let { c ->
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Participation${c.montantAttendu?.let { " de ${euros(it)} par personne" } ?: ""}$NBSP: ${euros(c.totalRecu)} reçus de ${c.nbContributeurs} contributeur${if (c.nbContributeurs > 1) "s" else ""}",
                    Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
        maPart?.let { p ->
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Participation demandée$NBSP: ${p.montantAttendu?.let { euros(it) } ?: "libre"} · vous avez donné ${euros(p.donne)}", Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
        FlowRowActions {
            if (collecte != null && d.peut("consulter_finances", "gerer_cotisations")) TextButton(onClick = { voir = true }) { Text("Voir les participations") }
            if (budgetSuivi && onBudget != null) TextButton(onClick = { onFermer(); onBudget() }) { Text("Voir le budget") }
            if (e.collecteId == null && d.peut("gerer_activites")) TextButton(onClick = { demander = true }) { Text("Demander une participation") }
            if (d.peut("gerer_activites")) BoutonSupprimer({ supprimerEvt("projects", e.id, e.nom) })
            if (d.peut("gerer_activites")) FilledTonalButton(onClick = { modifier = projets.firstOrNull { it.id == e.id } ?: e }) { Text("Modifier") }
            Button(onClick = onFermer) { Text("Fermer") }
        }
    }
    modifier?.let { p -> FormulaireActivite(p, onFini = { ok -> modifier = null; if (ok) { message("Modifications enregistrées"); onChange(); onFermer() } }, message) }
    if (demander) Dialogue({ demander = false }) { FormulaireCollecte(d, null, e.id, projets.ifEmpty { listOf(e) }, message) { ok -> demander = false; if (ok) { onChange(); onFermer() } } }
    if (voir) collecte?.let { c -> Dialogue({ voir = false }) { DetailCollecte(d, c, projets, message, onModifier = { voir = false; modifierCollecte = true }, onChange = onChange, onFermer = { voir = false }) } }
    if (modifierCollecte) collecte?.let { c -> Dialogue({ modifierCollecte = false }) { FormulaireCollecte(d, c, e.id, projets.ifEmpty { listOf(e) }, message) { ok -> modifierCollecte = false; if (ok) { onChange(); onFermer() } } } }
}

// Fenêtre plein écran simple pour empiler un formulaire au-dessus d'une feuille
@Composable
private fun Dialogue(onFermer: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onFermer, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.statusBarsPadding().padding(top = 16.dp)) { content() } }
    }
}

// =====================================================================
// Tiers : membres et autres tiers, ce que chacun a donné ou reçu
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranTiers(d: Donnees, message: (String) -> Unit) {
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var operations by remember { mutableStateOf<List<Ecriture>?>(null) }
    var collectes by remember { mutableStateOf<List<Collecte>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf("tous") }
    var recherche by remember { mutableStateOf("") }
    val an = aujourdhui().year
    var annee by remember { mutableStateOf<Int?>(an) }
    var fiche by remember { mutableStateOf<Pair<Membre?, Tiers?>?>(null) }
    var edition by remember { mutableStateOf<Pair<Boolean, Tiers?>?>(null) }
    var cotisMembre by remember { mutableStateOf<Membre?>(null) }
    var encaisser by remember { mutableStateOf<PreEcriture?>(null) }
    var pas by remember { mutableStateOf(1) }
    val enregistrerCsv = rememberEnregistrer { it?.let(message) }
    LaunchedEffect(version, Synchro.version) {
        try { tiers = Repo.tiers(); operations = Repo.toutesEcritures(); collectes = try { Repo.collectes() } catch (_: Exception) { emptyList() }
            pas = try { (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt() } catch (_: Exception) { 1 } }
        catch (e: Exception) { message(traduireErreur(e)) }
    }
    val ops = (operations ?: emptyList()).filter { annee == null || it.date.startsWith(annee.toString()) }
    data class LigneTiers(val nom: String, val type: String, val m: Membre?, val t: Tiers?, val rec: Double, val dep: Double, val n: Int)
    fun cumul(f: (Ecriture) -> Boolean): Triple<Double, Double, Int> { val l = ops.filter(f); return Triple(l.filter { it.sens == "recette" }.sumOf { it.montant }, l.filter { it.sens == "depense" }.sumOf { it.montant }, l.size) }
    val lignes = (d.membres.map { m -> cumul { it.membreId == m.id }.let { (r, dd, n) -> LigneTiers(m.nomComplet, "Membre", m, null, r, dd, n) } } +
        tiers.map { t -> cumul { it.tiersId == t.id }.let { (r, dd, n) -> LigneTiers(t.nom, TYPES_TIERS[t.type] ?: "", null, t, r, dd, n) } })
        .filter { (type == "tous" || (type == "membres") == (it.m != null)) && (recherche.isBlank() || norm(it.nom).contains(norm(recherche))) }
        .sortedWith(compareBy({ -(it.rec + it.dep) }, { it.nom }))
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item { Titre("Tiers") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tous" to "Tous", "membres" to "Membres", "autres" to "Autres tiers").forEach { (k, l) -> FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) }) }
                    PuceMenu(annee?.toString() ?: "Toutes les années", true, listOf(an, an - 1, an - 2).map { it.toString() } + "Toutes les années") { i -> annee = if (i < 3) an - i else null }
                }
            }
            item { OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
            if (operations == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(lignes, key = { (it.m?.id ?: it.t?.id) ?: it.nom }) { l ->
                Row(Modifier.fillMaxWidth().clickable { fiche = l.m to l.t }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(l.nom.substringBefore(' '), l.nom.substringAfter(' ', ""), 40)
                    Column(Modifier.weight(1f)) {
                        Text(l.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(l.type + if (l.n > 0) " · ${l.n} opération${if (l.n > 1) "s" else ""}" else "", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (l.rec > 0) Text("+ ${euros(l.rec)}", color = Couleurs.Bleu, fontWeight = FontWeight.SemiBold)
                        if (l.dep > 0) Text("− ${euros(l.dep)}", color = Couleurs.Orange, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (d.peut("saisir_ecritures", "gerer_cotisations")) ExtendedFloatingActionButton(onClick = { edition = true to null }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nouveau tiers") }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
    val supprimerTiers = rememberSuppression(message) { version++ }
    fiche?.let { (m, t) ->
        ModalBottomSheet(onDismissRequest = { fiche = null }) {
            val liste = (operations ?: emptyList()).filter { if (m != null) it.membreId == m.id else it.tiersId == t?.id }.sortedByDescending { it.date }
            val parRubrique = liste.filter { it.sens == "recette" }.groupBy { nomRubrique(it, collectes).ifBlank { d.categories.firstOrNull { c -> c.id == it.categorieId }?.nom ?: "" } }
                .mapValues { e -> e.value.sumOf { it.montant } }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m?.nomComplet ?: t?.nom ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Puce(if (m != null) "Membre" else TYPES_TIERS[t?.type] ?: "", Color(0xFFEFEDEC), Couleurs.Texte2)
                }
                t?.let { listOfNotNull(it.telephone, it.email, it.notes).takeIf { l -> l.isNotEmpty() }?.let { l -> Text(l.joinToString(" · "), color = Couleurs.Texte2, fontSize = 14.sp) } }
                if (parRubrique.isNotEmpty()) {
                    Text("Ce qu’il a donné", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, modifier = Modifier.padding(top = 8.dp))
                    parRubrique.forEach { (k, v) -> LigneSimple(k, "", v) }
                }
                Text("Opérations", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, modifier = Modifier.padding(top = 8.dp))
                if (liste.isEmpty()) Text("Aucune opération", color = Couleurs.Texte2)
                liste.forEach { e -> LigneSimple(e.libelle, dateFr(e.date) + nomRubrique(e, collectes).let { if (it.isBlank()) "" else " · $it" }, e.signe) }
                // Mêmes boutons que la fiche du site : Exporter, Modifier (tiers), Cotisation (membre)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    Button(onClick = {
                        val nom = sansAccentsCode(m?.nomComplet ?: t?.nom ?: "tiers").replace("_", "-")
                        enregistrerCsv("tiers-$nom.csv", "text/csv", csv(listOf("Date", "Libellé", "Rubrique", "Catégorie", "Montant"),
                            liste.map { listOf(dateFr(it.date), it.libelle, nomRubrique(it, collectes), d.nomCategorie(it.categorieId), it.signe) }).encodeToByteArray())
                    }, colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Bleu)) { Text("Exporter") }
                    if (t != null && d.peut("saisir_ecritures", "gerer_cotisations")) TextButton(onClick = { fiche = null; edition = false to t }) { Text("Modifier") }
                    if (t != null && d.peut("saisir_ecritures", "gerer_cotisations") && liste.isEmpty()) BoutonSupprimer({ fiche = null; supprimerTiers("tiers", t.id, t.nom) })
                    if (m != null && d.peut("gerer_cotisations")) TextButton(onClick = { fiche = null; cotisMembre = m }) { Text("Cotisation") }
                }
            }
        }
    }
    cotisMembre?.let { m ->
        ModalBottomSheet(onDismissRequest = { cotisMembre = null }) {
            FicheCotisation(d, m, an, pas, message, onEncaisser = { pre -> cotisMembre = null; encaisser = pre }, onChange = { version++ })
        }
    }
    encaisser?.let { pre ->
        ModalBottomSheet(onDismissRequest = { encaisser = null }) {
            FormulaireEcriture(d, pre, onFini = { ok -> encaisser = null; if (ok) { message("Encaissement enregistré"); version++ } }, message)
        }
    }
    edition?.let { (_, t) ->
        var nom by remember(t?.id) { mutableStateOf(t?.nom ?: "") }
        var typeT by remember(t?.id) { mutableStateOf(t?.type ?: "donateur") }
        var tel by remember(t?.id) { mutableStateOf(t?.telephone ?: "") }
        var mail by remember(t?.id) { mutableStateOf(t?.email ?: "") }
        var notes by remember(t?.id) { mutableStateOf(t?.notes ?: "") }
        var actif by remember(t?.id) { mutableStateOf(t?.actif ?: true) }
        val scope = rememberCoroutineScope()
        DialogueSimple(if (t == null) "Nouveau tiers" else "Modifier le tiers", "Enregistrer", nom.isNotBlank(), { edition = null }, {
            scope.launch {
                try {
                    val n = NouveauTiers(nom.trim(), typeT, tel.trim().ifBlank { null }, mail.trim().ifBlank { null }, notes.trim().ifBlank { null }, actif)
                    if (t == null) Repo.ajouterTiers(n) else Repo.majTiers(t.id, n)
                    message(if (t == null) "Tiers ajouté" else "Tiers modifié"); version++
                } catch (e: Exception) { message(traduireErreur(e)) }
                edition = null
            }
        }) {
            OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom") }, singleLine = true)
            ChoixListe("Type", TYPES_TIERS[typeT] ?: "", TYPES_TIERS.values.toList()) { typeT = TYPES_TIERS.keys.toList()[it] }
            OutlinedTextField(tel, { tel = it }, label = { Text("Téléphone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(mail, { mail = it.trim() }, label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes") })
            if (t != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(actif, { actif = it }); Text("Actif") }
        }
    }
}

// Cotisation et participations de la personne connectée
@Composable
fun BlocMaCotisation(periodes: List<PeriodeCotisation>, pas: Int) {
    val an = aujourdhui().year
    val l = periodes.filter { it.annee == an }.sortedBy { it.periode }
    val retard = retardDe(periodes)
    val regles = periodes.filter { it.statut == "regle" || it.statut == "dispense" }.maxOfOrNull { it.periode }
    if (periodes.isEmpty()) { Text("Aucune cotisation enregistrée", color = Couleurs.Texte2); return }
    Text("$an", color = Couleurs.Texte2, fontSize = 13.sp)
    if (retard > 0.005) Text("${euros(retard)} en retard", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Couleurs.Erreur)
    else Text("À jour" + (regles?.let { " jusqu’à ${nomPeriode(it, pas)}" } ?: ""), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    if (l.isNotEmpty()) GrillePeriodes(l.map { it.periode to it.statut }, pas)
}

@Composable
fun ListeParticipations(parts: List<Participation>) {
    if (parts.isEmpty()) { Text("Aucune participation demandée", color = Couleurs.Texte2); return }
    parts.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nom, fontWeight = FontWeight.SemiBold)
                Text("Donné$NBSP: ${euros(p.donne)}" + (p.dateLimite?.let { " · avant le ${dateFr(it)}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
            }
            val att = p.montantAttendu ?: 0.0
            when {
                att > 0 && p.donne >= att -> Puce("Réglé", Couleurs.BleuClair, Couleurs.SurBleuClair)
                att > 0 && p.donne > 0 -> Puce("Reste ${euros(att - p.donne)}", Couleurs.JauneClair, Couleurs.SurJaune)
                att > 0 -> Puce("${euros(att)} attendus", Couleurs.ErreurClair, Color(0xFF410002))
                p.donne > 0 -> Puce("Merci", Couleurs.BleuClair, Couleurs.SurBleuClair)
                else -> Puce("Libre", Color(0xFFEFEDEC), Couleurs.Texte2)
            }
        }
    }
}

@Composable
private fun FlecheMois(icone: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) =
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.16f), contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icone, description) }
    }
