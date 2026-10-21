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
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Info
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
// expressif : page des membres (Material 3 Expressive) — mois en pilules, « réglé » en vert, comme .vue-membre .case-p du site
fun GrillePeriodes(cases: List<Pair<String, String?>>, pas: Int, modifier: Modifier = Modifier, uneLigne: Boolean = false, expressif: Boolean = false) {
    // Douze mois : deux lignes de six sur téléphone pour que les noms restent lisibles ; une ligne sur grand écran (comme le site)
    val ecart = if (expressif) 4.dp else 3.dp
    val forme = if (expressif) RoundedCornerShape(50) else RoundedCornerShape(5.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ecart)) { cases.chunked(if (cases.size == 12 && !uneLigne) 6 else cases.size.coerceAtLeast(1)).forEach { rangee ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ecart)) {
        rangee.forEach { (p, st) ->
            val (fond, texte) = when (st) {
                "regle" -> (if (expressif) Couleurs.M3Vert else Couleurs.Bleu) to Color.White
                "partiel" -> Couleurs.Jaune to Couleurs.SurJaune
                "impaye" -> Couleurs.ErreurClair to Couleurs.SurErreurClair
                null -> Color.Transparent to Couleurs.Texte2
                else -> Couleurs.Surface2 to Couleurs.Texte2
            }
            val etat = when (st) { "regle" -> "réglé"; "partiel" -> "partiel"; "impaye" -> "impayé"; "a_venir" -> "à venir"; "dispense" -> "dispensé"; null -> "non dû"; else -> st }
            Box(Modifier.weight(1f).height(if (expressif) 26.dp else 22.dp).clip(forme).background(fond)
                .semantics { contentDescription = "${nomPeriode(p, pas)} : $etat" }
                .then(if (st == "impaye") Modifier.border(1.dp, Couleurs.Erreur, forme) else if (st == null) Modifier.border(1.dp, Couleurs.Bord, forme) else Modifier),
                contentAlignment = Alignment.Center) {
                if (pas == 1) Text(MOIS_COURTS[p.substring(5, 7).toInt() - 1], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = texte, maxLines = 1)
            }
        }
    }
    } }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun Legende() {
    // Petits carrés de 14 dp et libellé, sur plusieurs lignes au besoin (.legende du site)
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("regle", "partiel", "impaye", "a_venir", "dispense").forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val fond = when (st) { "regle" -> Couleurs.Bleu; "partiel" -> Couleurs.Jaune; "impaye" -> Couleurs.ErreurClair; else -> Couleurs.Surface2 }
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(fond)
                    .then(if (st == "impaye") Modifier.border(1.dp, Couleurs.Erreur, RoundedCornerShape(3.dp)) else Modifier))
                Text(STATUTS_PERIODE[st] ?: "", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
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
            if (!libelleSaisi) libelle = if (tiersNom.isBlank()) "Cotisation" else "Cotisation : ${tiersNom.trim()}"
        } else if (rubrique.isNotEmpty()) {
            val co = collectes.firstOrNull { it.id == rubrique } ?: return@LaunchedEffect
            cat("Activités / événements")?.let { categorie = it }
            co.projetId?.let { id -> projets.firstOrNull { it.id == id }?.let { projet = it } }
            if (!libelleSaisi) libelle = if (tiersNom.isBlank()) co.nom else "${co.nom} : ${tiersNom.trim()}"
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
            val choix = listOf("depense" to "Dépense", "recette" to "Recette") + if (onVirement != null && pre.rubrique == null) listOf("virement" to "Virement interne") else emptyList()
            choix.forEachIndexed { i, (k, v) ->
                SegmentedButton(selected = sens == k, onClick = { if (k == "virement") onVirement?.invoke() else sens = k }, shape = SegmentedButtonDefaults.itemShape(i, choix.size)) { Text(v) }
            }
        }
        OutlinedTextField(montant, { montant = it }, label = { Text("Montant") }, singleLine = true, suffix = { Text("€") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChampTiers(tiersNom, { tiersNom = it }, suggestions, if (sens == "recette") "Tiers : membre ou donateur" else "Tiers : fournisseur")
        if (sens == "recette") ChoixListe("Rubrique", rubriques.firstOrNull { it.first == rubrique }?.second ?: "Aucune", rubriques.map { it.second }) { rubrique = rubriques[it].first }
        info?.let {
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
        OutlinedTextField(libelle, { libelle = it.take(120); libelleSaisi = it.isNotEmpty() }, label = { Text("Libellé") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChampDate(date, { date = it }, label = { Text("Date") }, singleLine = true, placeholder = { Text("JJ/MM/AAAA") },
            isError = dateValide == null, modifier = Modifier.fillMaxWidth())
        ChoixListe("Catégorie", categorie?.nom ?: "", categories.map { it.nom }) { i -> categorie = categories[i] }
        ChoixListe("Mode de paiement", MODES[mode] ?: mode, MODES.values.toList()) { mode = MODES.keys.toList()[it] }
        ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it] }
        if (alerteMode.isNotEmpty()) Surface(color = Couleurs.JauneClair, contentColor = Couleurs.SurJaune, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
            Text(alerteMode, Modifier.padding(12.dp), fontSize = 14.sp)
        }
        ChoixListe("Activité", projet?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { i -> projet = if (i == 0) null else projets[i - 1] }
        if (sens == "depense") {
            Text("Justificatif", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            ChoixPiece(piece, message, choix) { piece = it }
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
                            // Saisie libre (écran Opérations) : même message que le site ; les encaissements gardent celui de leur écran
                            else if (pre.rubrique == null) message(if (rub.isNotEmpty()) "Encaissement enregistré" else "Opération enregistrée")
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
// Mémoire de l'écran pendant la session (comme S.ongletCotis et S.filtreCotis du site)
object EtatCotisations { var exporter by mutableStateOf(false) }
object PreferencesCotisations { var onglet = 0; var filtre = "tous" }

@Composable
fun EcranCotisations(d: Donnees, message: (String) -> Unit) {
    var onglet by remember { mutableStateOf(PreferencesCotisations.onglet) }
    Column(Modifier.fillMaxSize()) {
        // En-tête commun : « Exporter » dans l'en-tête pour l'onglet Cotisations (le bouton + pour l'onglet Participations)
        Box(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)) {
            Titre("Cotisations") { if (onglet == 0) OutlinedButton(onClick = { EtatCotisations.exporter = true }) { Text("Exporter") } }
        }
        TabRow(selectedTabIndex = onglet, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Cotisations", "Participations").forEachIndexed { i, t -> Tab(selected = onglet == i, onClick = { onglet = i; PreferencesCotisations.onglet = i }, text = { Text(t) }) }
        }
        Box(Modifier.weight(1f)) { if (onglet == 0) OngletCotisations(d, message) else OngletParticipations(d, message) }
    }
}

// Choix du format d'export : même feuille que choisirFormat du site
@Composable
internal fun ChoixFormat(titre: String, onPdf: () -> Unit, onExcel: () -> Unit, onFermer: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("PDF", "Mise en page pour imprimer, archiver ou transmettre", onPdf), Triple("Excel", "Tableau modifiable (CSV)", onExcel)).forEach { (t, s, action) ->
                    Surface(onClick = { onFermer(); action() }, shape = Formes.XGrand, color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0DCDA)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(t, fontWeight = FontWeight.Bold)
                            Text(s, fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OngletCotisations(d: Donnees, message: (String) -> Unit) {
    val an = aujourdhui().year
    var annee by remember { mutableStateOf(an) }
    var synth by remember { mutableStateOf<List<Cotisation>?>(null) }
    var periodes by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var pas by remember { mutableStateOf(1) }
    var montantPeriode by remember { mutableStateOf(0.0) }
    var version by remember { mutableStateOf(0) }
    var filtre by remember { mutableStateOf(PreferencesCotisations.filtre) }
    var encaisser by remember { mutableStateOf<PreEcriture?>(null) }
    var rappel by remember { mutableStateOf<DemandeRappel?>(null) }
    var rappelGroupe by remember { mutableStateOf<List<CibleRappel>?>(null) }
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
    val nomOrg = d.organisation.nom.trim()
    var exporterCotis by EtatCotisations::exporter
    val imprimerCotis = rememberImpression()
    val enregistrerCotis = rememberEnregistrer { it?.let(message) }
    // Export : PDF (même document que pdfCotisations) ou tableau des cases de la grille (mêmes colonnes que le site)
    if (exporterCotis) ChoixFormat("Cotisations $annee", onFermer = { exporterCotis = false },
        onPdf = { scope.launch { try { val (t, h) = documentHtml(d, "cotisations", annee); imprimerCotis(t, h) } catch (e: Exception) { message(traduireErreur(e)) } } },
        onExcel = {
            enregistrerCotis("cotisations-$annee.csv", "text/csv", csv(listOf("Prénom", "Nom") + cases.map { nomPeriode(it, pas) } + listOf("Dû", "Réglé", "Exigible", "Retard", "Réglé jusqu’à"),
                lignes.map { (m, c) -> listOf<Any?>(m.prenom, m.nom) + cases.map { p -> parCase[m.id to p]?.statut?.let { STATUTS_PERIODE[it] ?: it } ?: "" } +
                    listOf<Any?>(c.du, c.paye, c.exigible ?: 0.0, c.retard, c.regleJusqua?.let { nomPeriode(it, pas) } ?: "") }).encodeToByteArray())
        })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= 840.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PuceMenu(annee.toString(), true, listOf(an + 1, an, an - 1, an - 2).map { it.toString() }) { annee = an + 1 - it }
                    Text("${PERIODICITES[pas] ?: ""}, ${euros(montantPeriode)} par période", fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (gere && nbRetard > 0) FilledTonalButton(onClick = { rappelGroupe = lignes.filter { it.second.retard > 0.005 }.map { (m, c) -> CibleRappel(m, c.retard) } }) { Text("Petit rappel groupé") }
                    }
                }
            }
            if (gere && sansCotis > 0 && synth != null) item {
                Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.XGrand) {
                    FlowRow(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val s = if (sansCotis > 1) "s" else ""
                        Text("$sansCotis membre$s actif$s sans cotisation en $annee", Modifier.weight(1f).align(Alignment.CenterVertically), fontSize = 14.sp)
                        Button(onClick = { scope.launch { try { val n = Repo.genererCotisations(annee); val p = if (n > 1) "s" else ""; message("$n période$p créée$p"); version++ } catch (e: Exception) { message(traduireErreur(e)) } } }) {
                            Text("Générer les cotisations $annee")
                        }
                    }
                }
            }
            if (synth == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                if (lignes.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Indicateur("Encaissé", euros((synth ?: emptyList()).sumOf { it.paye }), Couleurs.Bleu, Modifier.weight(1f))
                            Indicateur("En retard", euros((synth ?: emptyList()).sumOf { it.retard }), Couleurs.Orange, Modifier.weight(1f))
                            Indicateur("À jour", "${lignes.size - nbRetard}$NBSP/$NBSP${lignes.size}", Couleurs.Texte, Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Triple("tous", "Tous", lignes.size), Triple("retard", "En retard", nbRetard), Triple("ajour", "À jour", lignes.size - nbRetard)).forEach { (k, l, n) ->
                                FilterChip(selected = filtre == k, onClick = { filtre = k; PreferencesCotisations.filtre = k }, label = { Text("$l ($n)") })
                            }
                        }
                    }
                }
                // Une seule carte : légende puis une ligne par membre (comme la section .carte du site)
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            if (vues.isEmpty()) Text(if (lignes.isNotEmpty()) "Aucune cotisation" else "Aucune cotisation pour $annee", color = Couleurs.Texte2,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                            else {
                                Legende()
                                vues.forEachIndexed { i, (m, c) ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                    val puces: @Composable () -> Unit = {
                                        if (c.retard > 0.005) Puce("Retard ${euros(c.retard)}", Couleurs.ErreurClair, Couleurs.SurErreurClair) else Puce("À jour", Couleurs.BleuClair, Couleurs.SurBleuClair)
                                        if (c.avance > 0.005) Puce("Avance ${euros(c.avance)}", Couleurs.Surface2, Couleurs.Texte2)
                                    }
                                    val actions: @Composable () -> Unit = {
                                        if (gere) {
                                            OutlinedButton(onClick = { encaisser = PreEcriture("recette", m.id, "cotisation", if (c.retard > 0.005) c.retard else montantPeriode) }) { Text("Encaisser") }
                                            if (c.retard > 0.005) TextButton(onClick = { rappel = DemandeRappel(m, "cotisation", c.retard) }) { Text("Petit rappel") }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Avatar(m.prenom, m.nom, 40)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.clickable { fiche = m }.semantics { contentDescription = "Fiche cotisation de ${m.nomComplet}" })
                                            GrillePeriodes(cases.map { it to parCase[m.id to it]?.statut }, pas, if (large) Modifier.widthIn(max = 420.dp) else Modifier, uneLigne = large)
                                            if (!large) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(6.dp)) { puces() }
                                                actions()
                                            }
                                        }
                                        if (large) { puces(); actions() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    encaisser?.let { pre ->
        ModalBottomSheet(onDismissRequest = { encaisser = null }) {
            FormulaireEcriture(d, pre, onFini = { ok -> encaisser = null; if (ok) { message("Encaissement enregistré"); version++ } }, message)
        }
    }
    rappel?.let { r ->
        ModalBottomSheet(onDismissRequest = { rappel = null }) { FeuilleRappel(d, r, message) { rappel = null } }
    }
    rappelGroupe?.let { l ->
        ModalBottomSheet(onDismissRequest = { rappelGroupe = null }) { FeuilleRappelGroupe(d, l, "cotisation", "", null, message) { rappelGroupe = null } }
    }
    fiche?.let { m ->
        ModalBottomSheet(onDismissRequest = { fiche = null }) {
            FicheCotisation(d, m, annee, pas, message, onEncaisser = { pre -> fiche = null; encaisser = pre }, onChange = { version++ }, onFermer = { fiche = null })
        }
    }
}

// Fiche cotisation d'un membre (ficheCotisation du site) : périodes de l'année, montant dû modifiable (dispense), versements
@Composable
private fun FicheCotisation(d: Donnees, m: Membre, annee: Int, pas: Int, message: (String) -> Unit, onEncaisser: (PreEcriture) -> Unit, onChange: () -> Unit, onFermer: () -> Unit = {}) {
    var periodes by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var operations by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var collectes by remember { mutableStateOf<List<Collecte>>(emptyList()) }
    var montantPeriode by remember { mutableStateOf(0.0) }
    var version by remember { mutableStateOf(0) }
    var edition by remember { mutableStateOf<PeriodeCotisation?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version, Synchro.version) {
        try {
            periodes = Repo.periodesMembre(m.id); operations = Repo.toutesEcritures().filter { it.membreId == m.id }.sortedByDescending { it.date }
            collectes = Repo.collectes()
            montantPeriode = Repo.reglages()["cotisation_montant"] ?: 0.0
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val gere = d.peut("gerer_cotisations")
    val retard = retardDe(periodes)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(m.prenom, m.nom)
            Text(m.nomComplet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Row { if (retard > 0.005) Puce("Retard ${euros(retard)}", Couleurs.ErreurClair, Couleurs.SurErreurClair) else Puce("À jour", Couleurs.BleuClair, Couleurs.SurBleuClair) }
        TitreFiche("$annee")
        val deLAnnee = periodes.filter { it.annee == annee }.sortedBy { it.periode }
        if (deLAnnee.isEmpty()) Text("Aucune période", color = Couleurs.Texte2)
        deLAnnee.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(nomPeriode(p.periode, pas), fontWeight = FontWeight.SemiBold)
                    Text("${euros(p.regle)} sur ${euros(p.du)}", fontSize = 13.sp, color = Couleurs.Texte2)
                }
                val (f, c) = when (p.statut) { "regle" -> Couleurs.BleuClair to Couleurs.SurBleuClair; "partiel" -> Couleurs.JauneClair to Couleurs.SurJaune
                    "impaye" -> Couleurs.ErreurClair to Couleurs.SurErreurClair; else -> Couleurs.Surface2 to Couleurs.Texte2 }
                Puce(STATUTS_PERIODE[p.statut] ?: p.statut, f, c)
                if (gere) TextButton(onClick = { edition = p }) { Text("Montant dû") }
            }
        }
        TitreFiche("Versements")
        val versements = operations.filter { it.estCotisation }
        if (versements.isEmpty()) Text("Aucun versement", color = Couleurs.Texte2)
        versements.forEach { e -> LigneSimple(dateFr(e.date), MODES[e.mode] ?: e.mode, e.signe) }
        val parts = operations.filter { it.collecteId != null }
        if (parts.isNotEmpty()) {
            TitreFiche("Participations")
            parts.forEach { e -> LigneSimple(nomRubrique(e, collectes), dateFr(e.date), e.signe) }
        }
        Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (gere) OutlinedButton(onClick = { onEncaisser(PreEcriture("recette", m.id, "cotisation", retard.takeIf { it > 0.005 } ?: montantPeriode)) }) { Text("Encaisser") }
            Button(onClick = onFermer) { Text("Fermer") }
        }
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

// Intertitre d'une fiche (h3 du site)
@Composable
private fun TitreFiche(texte: String) = Text(texte, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun LigneSimple(titre: String, sous: String, montant: Double, signe: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titre, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sous.isNotBlank()) Text(sous, fontSize = 13.sp, color = Couleurs.Texte2)
        }
        Text((if (!signe) "" else if (montant >= 0) "+ " else "− ") + euros(kotlin.math.abs(montant)), color = if (montant >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
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
    // Suppression gardée ici (et non dans la feuille, qui se ferme) pour que « Annuler » reste possible
    val supprimerCollecte = rememberSuppression(message) { version++ }
    val gere = d.peut("gerer_activites", "gerer_cotisations")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= 840.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val l = liste
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (l.isEmpty()) item {
                CarteBlanche { Text("Aucune collecte", color = Couleurs.Texte2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) }
            }
            // Deux colonnes sur grand écran (grille-2 du site)
            else items(l.chunked(if (large) 2 else 1), key = { r -> r.joinToString { it.id } }) { rang ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rang.forEach { c -> Box(Modifier.weight(1f)) { CarteCollecte(c, projets) { detail = c } } }
                    if (large && rang.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (gere) BoutonPlus("Nouvelle collecte", Modifier.align(Alignment.BottomEnd)) { formulaire = null to null }
    }
    detail?.let { c ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailCollecte(d, c, projets, message, onModifier = { detail = null; formulaire = c to c.projetId }, onChange = { version++ }, onFermer = { detail = null },
                onSupprimer = { detail = null; supprimerCollecte("collectes", c.id, c.nom) })
        }
    }
    formulaire?.let { (c, p) ->
        ModalBottomSheet(onDismissRequest = { formulaire = null }) {
            FormulaireCollecte(d, c, p, projets, message) { ok -> formulaire = null; if (ok) version++ }
        }
    }
}

@Composable
private fun CarteCollecte(c: Collecte, projets: List<Projet>, onClick: () -> Unit) {
    val vise = (c.objectif?.takeIf { it > 0 } ?: c.montantAttendu?.let { it * c.nbConcernes })?.takeIf { it > 0 }
    CarteBlanche(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (c.cloturee) Puce("Clôturée", Couleurs.Surface2, Couleurs.Texte2)
        }
        Text(listOfNotNull(projets.firstOrNull { it.id == c.projetId }?.nom, c.montantAttendu?.takeIf { it > 0 }?.let { "${euros(it)} par personne" } ?: "Montant libre",
            c.dateLimite?.let { "avant le ${dateFr(it)}" }).joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)) { append(euros(c.totalRecu)) }
            vise?.let { append(" sur ${euros(it)}") }
        }, fontSize = 15.sp)
        if (vise != null) LinearProgressIndicator(progress = { (c.totalRecu / vise).toFloat().coerceIn(0f, 1f) }, color = Couleurs.Bleu,
            trackColor = Couleurs.Surface2, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        Text("${c.nbContributeurs} contributeur${if (c.nbContributeurs > 1) "s" else ""} · ${c.nbConcernes} membre${if (c.nbConcernes > 1) "s" else ""} concerné${if (c.nbConcernes > 1) "s" else ""}",
            fontSize = 13.sp, color = Couleurs.Texte2)
    }
}

// Qui a donné quoi pour une collecte (detailCollecte du site).
// onSupprimer : suppression confiée à l'écran appelant (la feuille se ferme, « Annuler » doit rester possible).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailCollecte(d: Donnees, c0: Collecte, projets: List<Projet>, message: (String) -> Unit, onModifier: () -> Unit, onChange: () -> Unit, onFermer: () -> Unit,
                   onSupprimer: (() -> Unit)? = null) {
    var c by remember { mutableStateOf(c0) }
    var operations by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var choisis by remember { mutableStateOf<List<String>>(emptyList()) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var filtre by remember { mutableStateOf("tous") }
    var version by remember { mutableStateOf(0) }
    var encaisser by remember { mutableStateOf<PreEcriture?>(null) }
    var rappel by remember { mutableStateOf<DemandeRappel?>(null) }
    var rappelGroupe by remember { mutableStateOf<List<CibleRappel>?>(null) }
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
            if (c.cloturee) Puce("Clôturée", Couleurs.Surface2, Couleurs.Texte2)
        }
        Text(listOfNotNull(projets.firstOrNull { it.id == c.projetId }?.nom, if (att > 0) "${euros(att)} par personne" else "Montant libre",
            c.dateLimite?.let { "avant le ${dateFr(it)}" }).joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Indicateur("Reçu", euros(operations.sumOf { it.montant }), Couleurs.Bleu, Modifier.weight(1f))
            Indicateur("Ont donné", "${lignes.count { it.second > 0 }}$NBSP/$NBSP${lignes.size}", Couleurs.Texte, Modifier.weight(1f))
            Indicateur(if (att > 0) "Reste attendu" else "Objectif", if (att > 0) euros(lignes.sumOf { maxOf(0.0, att - it.second) }) else c.objectif?.let { euros(it) } ?: "–", Couleurs.Texte, Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("tous" to "Tous", "donne" to "Ont donné", "pas" to "N’ont pas donné").forEach { (k, l) -> FilterChip(selected = filtre == k, onClick = { filtre = k }, label = { Text(l) }) }
        }
        val vusCollecte = lignes.filter { (_, v) -> filtre == "tous" || (filtre == "donne") == (v > 0) }
        if (vusCollecte.isEmpty()) Text("Personne", color = Couleurs.Texte2)
        vusCollecte.forEachIndexed { i, (m, v) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(m.prenom, m.nom, 40)
                    Column(Modifier.weight(1f)) {
                        Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(euros(v) + if (att > 0) " sur ${euros(att)}" else "", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    // Situation toujours affichée (comme sur le site)
                    if (att > 0) {
                        if (v >= att) Puce("Réglé", Couleurs.BleuClair, Couleurs.SurBleuClair) else if (v > 0) Puce("Partiel", Couleurs.JauneClair, Couleurs.SurJaune) else Puce("À régler", Couleurs.ErreurClair, Couleurs.SurErreurClair)
                    } else if (v > 0) Puce("Donné", Couleurs.BleuClair, Couleurs.SurBleuClair)
                }
                // Actions sous la ligne, alignées sur le nom (actions-ligne du site)
                if (gere && !c.cloturee) Row(Modifier.padding(start = 50.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { encaisser = PreEcriture("recette", m.id, c.id, if (att > 0) maxOf(att - v, 0.0).takeIf { it > 0 } ?: att else null) }) { Text("Encaisser") }
                    if (att == 0.0 || v < att) TextButton(onClick = { rappel = DemandeRappel(m, "participation", if (att > 0) att - v else 0.0, c.nom, c.id) }) { Text("Petit rappel") }
                }
            }
        }
        if (autres.isNotEmpty()) {
            TitreFiche("Autres contributions")
            autres.forEach { e -> LigneSimple(nomTiers(e, d.membres, tiers).ifBlank { e.libelle }, dateFr(e.date), e.signe) }
        }
        FlowRowActions {
            OutlinedButton(onClick = {
                val statut = { v: Double -> if (att > 0) (if (v >= att) "Réglé" else if (v > 0) "Partiel" else "À régler") else if (v > 0) "Donné" else "" }
                enregistrer("participations-${sansAccentsCode(c.nom).replace("_", "-")}.csv", "text/csv", csv(listOf("Prénom", "Nom", "Donné", "Attendu", "Statut"),
                    lignes.map { (m, v) -> listOf<Any?>(m.prenom, m.nom, v, if (att > 0) att else "", statut(v)) } +
                        autres.map { e -> listOf<Any?>("", nomTiers(e, d.membres, tiers).ifBlank { e.libelle }, e.montant, "", "Autre contribution") }).encodeToByteArray())
            }) { Text("Exporter") }
            if (d.peut("gerer_activites", "gerer_cotisations")) {
                TextButton(onClick = onModifier) { Text("Modifier") }
                TextButton(onClick = { scope.launch { try { Repo.cloturerCollecte(c.id, !c.cloturee); message(if (c.cloturee) "Collecte rouverte" else "Collecte clôturée"); onChange(); onFermer() } catch (e: Exception) { message(traduireErreur(e)) } } }) {
                    Text(if (c.cloturee) "Rouvrir" else "Clôturer")
                }
            }
            if (gere && !c.cloturee) OutlinedButton(onClick = { encaisser = PreEcriture("recette", null, c.id) }) { Text("Autre encaissement") }
            // Petit rappel groupé : montant proposé non atteint, ou montant libre et rien donné (comme aRappeler du site)
            val aRappeler = lignes.filter { (_, v) -> if (att > 0) v < att else v <= 0 }.map { (m, v) -> CibleRappel(m, if (att > 0) att - v else 0.0) }
            if (gere && !c.cloturee && aRappeler.isNotEmpty()) TextButton(onClick = { rappelGroupe = aRappeler }) { Text("Petit rappel groupé") }
            if (d.peut("gerer_activites", "gerer_cotisations") && operations.isEmpty()) BoutonSupprimer({ if (onSupprimer != null) onSupprimer() else supprimerCollecte("collectes", c.id, c.nom) })
            Button(onClick = onFermer) { Text("Fermer") }
        }
    }
    encaisser?.let { pre ->
        ModalBottomSheet(onDismissRequest = { encaisser = null }) {
            FormulaireEcriture(d, pre, onFini = { ok -> encaisser = null; if (ok) { message("Encaissement enregistré"); version++; onChange() } }, message)
        }
    }
    rappel?.let { r ->
        ModalBottomSheet(onDismissRequest = { rappel = null }) { FeuilleRappel(d, r, message) { rappel = null } }
    }
    rappelGroupe?.let { l ->
        ModalBottomSheet(onDismissRequest = { rappelGroupe = null }) { FeuilleRappelGroupe(d, l, "participation", c.nom, c.id, message) { rappelGroupe = null } }
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
        (limite.isBlank() || dLimite != null)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (c == null) "Nouvelle collecte" else "Modifier la collecte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom *") }, placeholder = { Text("Participation à la sortie") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoixListe("Activité", projets.firstOrNull { it.id == projet }?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { i ->
            projet = if (i == 0) null else projets[i - 1].id
            if (nom.isBlank() && i > 0) nom = "Participation$NBSP: ${projets[i - 1].nom}"
        }
        OutlinedTextField(attendu, { attendu = it }, label = { Text("Montant par personne (€)") }, placeholder = { Text("Libre") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(objectif, { objectif = it }, label = { Text("Objectif total (€)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChampDate(limite, { limite = it }, label = { Text("Date limite") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
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
                if (!tous && choisis.isEmpty()) message("Cochez au moins un membre, ou choisissez «${NBSP}Tous les membres${NBSP}»")
                else {
                    enCours = true
                    scope.launch {
                        try {
                            Repo.enregistrerCollecte(c?.id, NouvelleCollecte(nom.trim(), projet, lireMontant(attendu), lireMontant(objectif), dLimite?.toString(), tous), choisis.toList())
                            message(if (c == null) "Collecte créée" else "Collecte modifiée"); onFini(true)
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }
            }) { Text("Enregistrer") }
        }
    }
}

// =====================================================================
// Planning : mois, semaine, agenda (et suivi des activités pour le bureau)
// =====================================================================
// Choix gardés sur le téléphone, comme sur le site (localStorage « vuePlanning2 » et « affichagePlanning »)
object PreferencesPlanning {
    var vue: String
        get() = (try { lirePreference("vuePlanning2") } catch (_: Exception) { null }) ?: "calendrier"
        set(v) { try { garderPreference("vuePlanning2", v) } catch (_: Exception) { } }
    var affichage: String
        get() = (try { lirePreference("affichagePlanning") } catch (_: Exception) { null }) ?: "mois"
        set(v) { try { garderPreference("affichagePlanning", v) } catch (_: Exception) { } }
}

// Jetons du site (styles.css, Fluent 2) repris pour le Planning : coins 4 / 8, surfaces, traits
private object CouleursPlanning {
    val Surface2 = Couleurs.Surface2; val BordDoux = Couleurs.BordDoux; val Bord = Couleurs.Bord
    val Texte2 = Couleurs.TexteFort2; val Texte3 = Couleurs.Texte2
    val JauneTrait = Couleurs.JauneTrait; val BleuTrait = Couleurs.BleuTrait; val Info = Color(0xFFEAF4FB)
    val JourFond = Color(0xFFFAFAF9); val JourWe = Color(0xFFFFF7F3); val JourAuj = Color(0xFFFFF4EF); val DateWe = Color(0xFFFFEDE5)
    val PrimaireClair = Couleurs.OrangeClair
}
private val FormeP4 = Formes.Moyen
private val FormeP8 = Formes.XGrand

// Planning : même conception que le site (pageActivites). Deux onglets, « Calendrier » (bandeau du mois, affichage Mois
// ou Semaine, Aujourd'hui) et « À venir » (rendez-vous des douze prochains mois, par mois). Un appui sur un jour ouvre
// le détail du jour ; sur un rendez-vous, son détail. Téléphone (< 600 dp) : rendez-vous en barres de couleur dans les
// cases, comme le site ; au-delà, leur nom. À partir de 640 dp, « Nouveau rendez-vous » est dans l'en-tête (pas de +).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranPlanning(d: Donnees, message: (String) -> Unit, onBudget: (() -> Unit)? = null) {
    EtatPlanning.gere = d.peut("gerer_activites")
    var vue by remember { mutableStateOf(PreferencesPlanning.vue.takeIf { it in listOf("calendrier", "avenir") } ?: "calendrier") }
    var affichage by remember { mutableStateOf(PreferencesPlanning.affichage.takeIf { it in listOf("mois", "semaine") } ?: "mois") }
    var ref by remember { mutableStateOf(aujourdhui()) }
    var evts by remember { mutableStateOf<List<Projet>?>(null) }
    var anniv by remember { mutableStateOf<Map<Int, List<Anniversaire>>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Projet?>(null) }
    var jour by remember { mutableStateOf<String?>(null) }
    var nouveau by remember { mutableStateOf<String?>(null) }
    var sens by remember { mutableStateOf(0) }   // 1 : le suivant arrive par la droite ; -1 : par la gauche ; 0 : sans glissement
    val (debut, fin) = when {
        vue == "avenir" -> aujourdhui() to aujourdhui().plus(DatePeriod(days = 365))
        affichage == "mois" -> { val p = LocalDate(ref.year, ref.monthNumber, 1); val dern = p.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
            p.minus(DatePeriod(days = p.dayOfWeek.isoDayNumber - 1)) to dern.plus(DatePeriod(days = 7 - dern.dayOfWeek.isoDayNumber)) }
        else -> { val l = ref.minus(DatePeriod(days = ref.dayOfWeek.isoDayNumber - 1)); l to l.plus(DatePeriod(days = 6)) }
    }
    LaunchedEffect(vue, debut, fin, version, Synchro.version) {
        try {
            evts = Repo.planning(debut.toString(), fin.toString())
            val mois = if (vue == "avenir") emptySet() else generateSequence(debut) { it.plus(DatePeriod(days = 7)) }.takeWhile { it <= fin }.map { it.monthNumber }.toSet() + fin.monthNumber + ref.monthNumber
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
    // Suppression tenue par l'écran (et non par la feuille du détail, qui se ferme) : « Annuler » reste possible
    val supprimerEvt = rememberSuppression(message) { version++ }
    fun decaler(n: Int) {
        sens = n
        ref = if (affichage == "mois") { val p = LocalDate(ref.year, ref.monthNumber, 1); if (n > 0) p.plus(DatePeriod(months = n)) else p.minus(DatePeriod(months = -n)) }
              else if (n > 0) ref.plus(DatePeriod(days = 7 * n)) else ref.minus(DatePeriod(days = -7 * n))
    }
    // Comme le site : le premier du mois affiché s'il n'est pas le mois en cours, sinon aujourd'hui
    fun dateNouveau() = if (vue == "calendrier" && affichage == "mois" && ref.monthNumber != aujourdhui().monthNumber) LocalDate(ref.year, ref.monthNumber, 1).toString() else auj
    val decalage = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.roundToPx() }
    val seuilGlisser = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp          // site : @media (max-width:600px)
        val enTete = maxWidth >= 640.dp          // site : le bouton principal est dans l'en-tête au-delà de 639 px
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, if (compact) 8.dp else 16.dp, 16.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            item {
                val actionTitre: (@Composable () -> Unit)? = if (gere && enTete) { {
                    Button(onClick = { nouveau = dateNouveau() }, shape = FormeP4) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nouveau rendez-vous")
                    }
                } } else null
                Titre("Planning", action = actionTitre)
            }
            item { OngletsPlanning(vue) { vue = it; PreferencesPlanning.vue = it; sens = 0 } }
            if (vue == "calendrier") {
                // Bandeau coloré du mois (une couleur par mois), flèches rondes
                item {
                    val titre = if (affichage == "mois") "${MOIS[ref.monthNumber - 1].replaceFirstChar { it.uppercase() }} ${ref.year}" else "Semaine du ${debut.dayOfMonth} ${MOIS[debut.monthNumber - 1]}"
                    BandeauMois(titre, EVT_PALETTE[(ref.monthNumber - 1) % EVT_PALETTE.size].third, compact, onPrec = { decaler(-1) }, onSuiv = { decaler(1) })
                }
                item {
                    val choix: (String) -> Unit = { affichage = it; PreferencesPlanning.affichage = it; sens = 0 }
                    val boutonAuj = @Composable {
                        Surface(onClick = { sens = 0; ref = aujourdhui() }, shape = FormeP4, color = Color.White, contentColor = Couleurs.Texte,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CouleursPlanning.Bord)) {
                            Text("Aujourd’hui", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // Mois / Semaine et Aujourd'hui sur une seule ligne, téléphone compris (tout le mois tient dans l'écran)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupeAffichage(affichage, plein = false, onChoix = choix)
                        Spacer(Modifier.weight(1f))
                        boutonAuj()
                    }
                }
                if (evts == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    // Changement de mois (ou de semaine) : le nouveau glisse depuis le côté vers lequel on va ; glisser du doigt aussi
                    AnimatedContent(affichage to debut, transitionSpec = {
                        if (sens != 0) (slideInHorizontally(androidx.compose.animation.core.tween(300)) { sens * decalage } +
                            fadeIn(androidx.compose.animation.core.tween(300), initialAlpha = 0.3f)) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
                        else fadeIn(androidx.compose.animation.core.tween(200)) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
                    }, modifier = Modifier.pointerInput(affichage) {
                        var cumul = 0f
                        detectHorizontalDragGestures(onDragEnd = {
                            if (cumul < -seuilGlisser) decaler(1) else if (cumul > seuilGlisser) decaler(-1)
                            cumul = 0f
                        }, onDragCancel = { cumul = 0f }) { _, dx -> cumul += dx }
                    }, label = "planning") { (aff, deb) ->
                        if (aff == "mois") {
                            val nb = (evts ?: emptyList()).count { LocalDate.parse(it.debut!!.take(10)).monthNumber == ref.monthNumber }
                            CalendrierMois(deb, parJour, ::annivDe, anniv[ref.monthNumber].orEmpty().size, nb, compact, onJour = { jour = it }, onEvt = { detail = it })
                        } else {
                            // Semaine : sept jours, logo en filigrane comme le calendrier du mois
                            CartePlanning(Modifier.fillMaxWidth()) { Box {
                                Column(Modifier.padding(16.dp)) {
                                    generateSequence(deb) { it.plus(DatePeriod(days = 1)) }.take(7).map { it.toString() }.forEachIndexed { i, s ->
                                        LigneAgenda(s, s == auj, i > 4, parJour[s].orEmpty(), annivDe(s), onJour = { jour = s }) { detail = it }
                                        HorizontalDivider(color = CouleursPlanning.BordDoux)
                                    }
                                }
                                Filigrane(Modifier.align(Alignment.Center), compact)
                            } }
                        }
                    }
                }
                if (affichage == "mois") item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.size(width = 24.dp, height = 4.dp).clip(RoundedCornerShape(50))) { EVT_PALETTE.take(4).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it.third)) } }
                            Text("Rendez-vous (une couleur chacun)", fontSize = 12.sp, color = CouleursPlanning.Texte3)
                        }
                        Legende(Couleurs.Jaune, "Anniversaire")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.size(12.dp).background(Couleurs.Orange, CircleShape)); Text("Aujourd’hui", fontSize = 12.sp, color = CouleursPlanning.Texte3) }
                        Text("Touchez un jour pour le détail", fontSize = 12.sp, color = CouleursPlanning.Texte3)
                    }
                }
            } else {
                item { Text("Les rendez-vous des douze prochains mois.", fontSize = 14.sp, color = CouleursPlanning.Texte3) }
                val l = (evts ?: emptyList()).filter { (it.fin ?: it.debut ?: "") >= auj }.sortedWith(compareBy({ it.debut }, { it.heureDebut ?: "" }))
                if (evts != null && l.isEmpty()) item {
                    CartePlanning(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Aucun rendez-vous prévu.", color = CouleursPlanning.Texte3, textAlign = TextAlign.Center)
                        }
                    }
                }
                l.groupBy { it.debut!!.take(7) }.forEach { (m, liste) ->
                    item(key = m) {
                        val mo = m.substring(5, 7).toInt()
                        CartePlanning(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Titre du mois précédé d'un filet de la couleur du mois (comme .carte-mois)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(width = 10.dp, height = 24.dp).background(EVT_PALETTE[(mo - 1) % EVT_PALETTE.size].third, RoundedCornerShape(50)))
                                    Text("${MOIS[mo - 1].replaceFirstChar { it.uppercase() }} ${m.take(4)}", fontSize = if (compact) 18.sp else 20.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    liste.forEach { e ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            DateAgenda(e.debut!!.take(10), e.debut.take(10) == auj, false, null)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                CarteEvenement(e) { detail = e }
                                                if (e.fin != null && e.fin.take(10) != e.debut.take(10)) Text("Jusqu’au ${jourLong(e.fin.take(10))}", fontSize = 14.sp, color = CouleursPlanning.Texte3)
                                            }
                                        }
                                        HorizontalDivider(color = CouleursPlanning.BordDoux)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (gere && !enTete) BoutonPlus("Nouveau rendez-vous", Modifier.align(Alignment.BottomEnd)) { nouveau = dateNouveau() }
    }
    // Détail du jour (feuilleJour du site) : liste sobre, anniversaires, ajout
    jour?.let { s ->
        ModalBottomSheet(onDismissRequest = { jour = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(jourLong(s), fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                val l = parJour[s].orEmpty()
                if (l.isEmpty()) Text("Rien de prévu", fontSize = 14.sp, color = CouleursPlanning.Texte3)
                else Column {
                    l.forEach { e ->
                        Row(Modifier.fillMaxWidth().clickable { jour = null; detail = e }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(e.nom, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(e.heureDebut?.let { heureFr(it) } ?: "Journée", e.lieu).joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = CouleursPlanning.Texte3)
                            }
                            if (e.type == "activite") PucePlanning("Budget suivi", CouleursPlanning.Surface2, CouleursPlanning.Bord, CouleursPlanning.Texte2)
                        }
                        HorizontalDivider(color = CouleursPlanning.BordDoux)
                    }
                }
                annivDe(s).forEach { PuceAnniversaire(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (gere) OutlinedButton(onClick = { jour = null; nouveau = s }, shape = FormeP4) { Text("Ajouter un rendez-vous") }
                    Button(onClick = { jour = null }, shape = FormeP4) { Text("Fermer") }
                }
            }
        }
    }
    detail?.let { e ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailEvenement(d, e, message, onChange = { version++ }, onFermer = { detail = null }, onBudget = onBudget,
                onSupprimer = { p -> detail = null; supprimerEvt("projects", p.id, p.nom) })
        }
    }
    nouveau?.let { s ->
        FormulaireActivite(null, onFini = { ok -> nouveau = null; if (ok) { message("Ajouté au planning"); version++ } }, message, dateInitiale = s)
    }
}

// Onglets à la manière du site : soulignés, alignés à gauche, filet sous la rangée
@Composable
private fun OngletsPlanning(vue: String, onChoix: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row {
            listOf("calendrier" to "Calendrier", "avenir" to "À venir").forEach { (k, l) ->
                val choisi = vue == k
                // Largeur = celle du texte (IntrinsicSize) : avant, le trait « fillMaxWidth » prenait toute la ligne et chassait « À venir »
                Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onChoix(k) }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, Modifier.padding(vertical = 11.dp), fontSize = 14.sp, maxLines = 1, fontWeight = if (choisi) FontWeight.SemiBold else FontWeight.Normal, color = if (choisi) Couleurs.Texte else CouleursPlanning.Texte2)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (choisi) Couleurs.Orange else Color.Transparent, RoundedCornerShape(50)))
                }
            }
        }
        HorizontalDivider(color = CouleursPlanning.BordDoux)
    }
}

// Choix Mois / Semaine (groupe du site : fond gris, bouton choisi blanc avec un petit trait orange)
@Composable
private fun GroupeAffichage(affichage: String, plein: Boolean, onChoix: (String) -> Unit) {
    Row((if (plein) Modifier.fillMaxWidth() else Modifier).clip(FormeP4).background(CouleursPlanning.Surface2).border(1.dp, CouleursPlanning.BordDoux, FormeP4).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("mois" to "Mois", "semaine" to "Semaine").forEach { (k, l) ->
            val choisi = affichage == k
            Box((if (plein) Modifier.weight(1f) else Modifier.widthIn(min = 72.dp)).heightIn(min = 36.dp)
                .then(if (choisi) Modifier.shadow(1.dp, FormeP4).background(Color.White, FormeP4) else Modifier).clip(FormeP4)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onChoix(k) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(l, fontSize = 14.sp, fontWeight = if (choisi) FontWeight.SemiBold else FontWeight.Normal, color = if (choisi) Couleurs.Texte else CouleursPlanning.Texte2)
                if (choisi) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).size(width = 16.dp, height = 3.dp).background(Couleurs.Orange, RoundedCornerShape(50)))
            }
        }
    }
}

// Bandeau du mois : dégradé de la couleur du mois, deux cercles clairs, titre qui entre en montant
@Composable
private fun BandeauMois(titre: String, couleur: Color, compact: Boolean, onPrec: () -> Unit, onSuiv: () -> Unit) {
    val sombre = Color(couleur.red * 0.7f, couleur.green * 0.7f, couleur.blue * 0.7f)
    Row(Modifier.fillMaxWidth().shadow(4.dp, FormeP8).clip(FormeP8)
        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(couleur, sombre)))
        .drawBehind {
            drawCircle(Color.White.copy(alpha = 0.1f), radius = 100.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width - 40.dp.toPx(), 10.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.1f), radius = 60.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.3f + 60.dp.toPx(), size.height + 20.dp.toPx()))
        }
        .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlecheMois(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Précédent", if (compact) 36.dp else 44.dp, onPrec)
        AnimatedContent(titre, Modifier.weight(1f), transitionSpec = {
            (fadeIn(androidx.compose.animation.core.tween(250)) + slideInVertically(androidx.compose.animation.core.tween(250)) { it / 4 }) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
        }, label = "titre-mois") { t ->
            Text(t, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 18.sp else 24.sp, lineHeight = if (compact) 24.sp else 32.sp, color = Color.White)
        }
        FlecheMois(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Suivant", if (compact) 36.dp else 44.dp, onSuiv)
    }
}

// Carte sobre du site (.carte / .cal) : blanche, coins 8, ombre légère
@Composable
private fun CartePlanning(modifier: Modifier, contenu: @Composable () -> Unit) =
    Surface(modifier, color = Color.White, shape = FormeP8, shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(1.dp, CouleursPlanning.BordDoux)) { contenu() }

@Composable
private fun Legende(couleur: Color, texte: String) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(Modifier.size(width = 12.dp, height = 4.dp).background(couleur, RoundedCornerShape(50))); Text(texte, fontSize = 12.sp, color = CouleursPlanning.Texte3)
}

// Puce du site (.puce) : arrondie, filet, 12 px
@Composable
private fun PucePlanning(texte: String, fond: Color, bord: Color, couleur: Color, icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
                         taille: androidx.compose.ui.unit.TextUnit = 12.sp, marge: androidx.compose.ui.unit.Dp = 8.dp) =
    Row(Modifier.heightIn(min = 20.dp).clip(RoundedCornerShape(50)).background(fond).border(1.dp, bord, RoundedCornerShape(50)).padding(horizontal = marge),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        icone?.let { Icon(it, null, Modifier.size(12.dp), tint = couleur) }
        Text(texte, fontSize = taille, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = couleur, maxLines = 1)
    }

// Date d'une ligne d'agenda (.agenda-date) : 48 × 48, coins 4 ; aujourd'hui en orange ; week-end teinté (semaine)
@Composable
private fun DateAgenda(s: String, estAuj: Boolean, weekEnd: Boolean, onClick: (() -> Unit)?) {
    val fond = if (estAuj) Couleurs.Orange else if (weekEnd) CouleursPlanning.DateWe else CouleursPlanning.Surface2
    Column(Modifier.size(48.dp).clip(FormeP4).background(fond).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(s.takeLast(2).trimStart('0'), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, color = if (estAuj) Color.White else Couleurs.Texte)
        Text(JOURS_COURTS[LocalDate.parse(s).dayOfWeek.isoDayNumber - 1], fontSize = 11.sp, lineHeight = 14.sp, color = if (estAuj) Color.White else CouleursPlanning.Texte3)
    }
}

@Composable
private fun LigneAgenda(s: String, estAuj: Boolean, weekEnd: Boolean, evts: List<Projet>, anniv: List<Anniversaire>, onJour: () -> Unit, onEvt: (Projet) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        DateAgenda(s, estAuj, weekEnd, onJour)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            evts.forEach { e -> CarteEvenement(e) { onEvt(e) } }
            anniv.forEach { PuceAnniversaire(it) }
            if (evts.isEmpty() && anniv.isEmpty()) Text("–", fontSize = 14.sp, color = CouleursPlanning.Texte3)
        }
    }
}

// Logo de l'association en filigrane : très discret, laisse passer les appuis (55 % au plus 340 dp ; 70 % sur téléphone)
@Composable
private fun Filigrane(modifier: Modifier, compact: Boolean) =
    LogoAsso(modifier.fillMaxWidth(if (compact) 0.7f else 0.55f).widthIn(max = 340.dp).aspectRatio(1f)
        .graphicsLayer { alpha = if (compact) 0.07f else 0.08f }.clip(CircleShape).clearAndSetSemantics { })

@Composable
private fun CalendrierMois(debut: LocalDate, parJour: Map<String, List<Projet>>,
                           annivDe: (String) -> List<Anniversaire>, nbAnniv: Int, nbEvts: Int, compact: Boolean,
                           onJour: (String) -> Unit, onEvt: (Projet) -> Unit) {
    // Mois affiché déduit du premier lundi de la grille (la première semaine contient toujours le 1er)
    val ref = debut.plus(DatePeriod(days = 7)).let { LocalDate(it.year, it.monthNumber, 1) }
    val dern = ref.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    val fin = dern.plus(DatePeriod(days = 7 - dern.dayOfWeek.isoDayNumber))
    val jours = generateSequence(debut) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= fin }.toList()
    val cle = ref.year * 12 + ref.monthNumber
    CartePlanning(Modifier.fillMaxWidth()) { Box {
        Column(Modifier.padding(if (compact) 4.dp else 8.dp)) {
            // Résumé dans la carte, comme .cal-resume
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Couleurs.Texte)) { append("$nbEvts") }; append(" rendez-vous ce mois")
                if (nbAnniv > 0) { append(" · "); withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Couleurs.Texte)) { append("$nbAnniv") }; append(" anniversaire${if (nbAnniv > 1) "s" else ""}") }
            }, fontSize = 13.sp, color = CouleursPlanning.Texte3, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                JOURS_COURTS.forEachIndexed { i, t ->
                    Text(t, Modifier.weight(1f).padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold, color = if (i >= 5) Couleurs.Orange else CouleursPlanning.Texte3)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                jours.chunked(7).forEachIndexed { n, semaine ->
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        semaine.forEachIndexed { k, j ->
                            val s = j.toString()
                            CaseJour(j, n * 7 + k, cle, j.monthNumber != ref.monthNumber, k >= 5, parJour[s].orEmpty(), annivDe(s), compact,
                                Modifier.weight(1f).fillMaxHeight(), onJour, onEvt)
                        }
                    }
                }
            }
        }
        Filigrane(Modifier.align(Alignment.Center), compact)
    } }
}

// Case d'un jour (.cal-jour) : fond selon le jour, aujourd'hui entouré, anniversaire en dégradé jaune en bas ;
// entrée en fondu léger, en cascade, une seule fois par mois affiché (pas à chaque mise à jour en temps réel)
@Composable
private fun CaseJour(j: LocalDate, rang: Int, cle: Int, hors: Boolean, weekEnd: Boolean, e: List<Projet>, a: List<Anniversaire>, compact: Boolean,
                     modifier: Modifier, onJour: (String) -> Unit, onEvt: (Projet) -> Unit) {
    val s = j.toString()
    val estAuj = s == aujourdhui().toString()
    val entree = remember(cle) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(cle) { kotlinx.coroutines.delay(rang * 6L); entree.animateTo(1f, androidx.compose.animation.core.tween(250)) }
    val fond = when { estAuj -> CouleursPlanning.JourAuj; e.isNotEmpty() -> Color.White; weekEnd -> CouleursPlanning.JourWe; else -> CouleursPlanning.JourFond }
    val degradeAnniv = androidx.compose.ui.graphics.Brush.verticalGradient(0.7f to Color.Transparent, 1f to Couleurs.Jaune.copy(alpha = 0.18f))
    Column(modifier.heightIn(min = if (compact) 48.dp else 88.dp)
        .graphicsLayer { alpha = entree.value; val sc = 0.96f + 0.04f * entree.value; scaleX = sc; scaleY = sc }
        .clip(FormeP4).background(fond)
        .then(if (a.isNotEmpty()) Modifier.drawBehind { drawRect(degradeAnniv) } else Modifier)
        .then(if (estAuj) Modifier.border(2.dp, Couleurs.Orange, FormeP4) else if (e.isNotEmpty()) Modifier.border(1.dp, CouleursPlanning.BordDoux, FormeP4) else Modifier)
        .clickable { onJour(s) }
        .semantics { contentDescription = jourLong(s) + if (e.isNotEmpty()) ", ${e.size} rendez-vous" else "" }
        .padding(if (compact) 2.dp else 4.dp)) {
        // Hors du mois : le contenu s'efface (.45), la case garde son fond
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (hors) 0.45f else 1f }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NumeroJour(j.dayOfMonth, estAuj)
            e.take(3).forEach { ev ->
                val (fondEvt, encre, accent) = couleurEvt(ev.id)
                // Téléphone : une barre de la couleur du rendez-vous ; ailleurs, l'heure et le nom
                if (compact) Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent).clickable { onEvt(ev) })
                else Text(buildAnnotatedString {
                    ev.heureDebut?.let { withStyle(SpanStyle(color = encre.copy(alpha = 0.8f))) { append(it.take(5)) }; append(" ") }
                    append(ev.nom)
                }, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = encre, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(FormeP4).background(fondEvt)
                        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                        .clickable { onEvt(ev) }.padding(horizontal = 6.dp, vertical = 1.dp))
            }
            if (e.size > 3) Text("+${e.size - 3}", fontSize = 11.sp, lineHeight = 14.sp, color = CouleursPlanning.Texte3)
            if (a.isNotEmpty()) {
                if (compact) Box(Modifier.fillMaxWidth().height(4.dp).background(Couleurs.Jaune, Formes.Petit))
                else Text(a.joinToString(", ") { it.prenom }, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Couleurs.SurJaune, modifier = Modifier.fillMaxWidth().background(Couleurs.JauneClair, Formes.Petit).padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
    }
}

// Numéro du jour ; aujourd'hui : rond orange avec un halo qui respire (pulsation du site)
@Composable
private fun NumeroJour(n: Int, estAuj: Boolean) {
    val halo = if (estAuj) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "halo")
        t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "halo").value
    } else 0f
    Box(Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
        .then(if (estAuj) Modifier.drawBehind {
            val r = size.minDimension / 2
            drawCircle(CouleursPlanning.PrimaireClair.copy(alpha = 1f - 0.6f * halo), radius = r + (3f + 3f * halo).dp.toPx())
        }.background(Couleurs.Orange, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        Text(n.toString(), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = if (estAuj) Color.White else Couleurs.Texte,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
}

object EtatPlanning { var gere = false }

// Rendez-vous dans l'agenda (.agenda-evt) : fond de sa couleur, filet de 4 à gauche, nom, heure ou « Toute la journée » · lieu,
// repère « Bureau » pour ceux qui gèrent le planning, participation demandée dessous
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteEvenement(e: Projet, onClick: () -> Unit) {
    val gere = EtatPlanning.gere
    val (fond, texte, accent) = couleurEvt(e.id)
    Column(Modifier.fillMaxWidth().clip(FormeP4).background(fond).drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.nom, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = texte)
            if (gere && !e.visible) PucePlanning("Bureau", CouleursPlanning.Surface2, CouleursPlanning.Bord, CouleursPlanning.Texte2,
                Icons.Outlined.VisibilityOff, 11.sp, 6.dp)
        }
        val l = listOfNotNull(if (e.heureDebut != null) heureFr(e.heureDebut) + (e.heureFin?.let { " – " + heureFr(it) } ?: "") else "Toute la journée", e.lieu)
        Text(l.joinToString(" · "), fontSize = 12.sp, lineHeight = 16.sp, color = texte.copy(alpha = 0.85f))
        e.participation?.let { PucePlanning("Participation ${euros(it)}", Couleurs.JauneClair, CouleursPlanning.JauneTrait, Couleurs.SurJaune) }
    }
}

@Composable
private fun PuceAnniversaire(a: Anniversaire) =
    Text("Anniversaire de ${a.prenom} ${a.nom}", fontSize = 13.sp, color = Couleurs.SurJaune,
        modifier = Modifier.background(Couleurs.JauneClair, FormeP4).padding(horizontal = 8.dp, vertical = 4.dp))

// Ligne du détail (.ligne-detail) : intitulé à gauche, valeur à droite, filet dessous
@Composable
private fun LigneDetailEvt(titre: String, contenu: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(titre, fontSize = 14.sp, color = CouleursPlanning.Texte3)
            Spacer(Modifier.weight(1f))
            contenu()
        }
        HorizontalDivider(color = CouleursPlanning.BordDoux)
    }
}

// Barre d'information du site (.info) : fond bleu pâle, filet, icône
@Composable
private fun InfoPlanning(texte: androidx.compose.ui.text.AnnotatedString) =
    Row(Modifier.fillMaxWidth().clip(FormeP4).background(CouleursPlanning.Info).border(1.dp, CouleursPlanning.BleuTrait, FormeP4).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = Couleurs.Texte)
        Text(texte, fontSize = 14.sp, lineHeight = 20.sp, color = Couleurs.Texte)
    }

// Détail d'un rendez-vous (detailEvenement du site). onSupprimer : l'écran appelant tient la suppression (et son « Annuler »)
@Composable
fun DetailEvenement(d: Donnees, e: Projet, message: (String) -> Unit, onChange: () -> Unit, onFermer: () -> Unit, onBudget: (() -> Unit)? = null,
                    onSupprimer: ((Projet) -> Unit)? = null) {
    val supprimerEvt = rememberSuppression(message) { onChange(); onFermer() }
    var collecte by remember { mutableStateOf<Collecte?>(null) }
    var maPart by remember { mutableStateOf<Participation?>(null) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var modifier by remember { mutableStateOf<Projet?>(null) }
    var demander by remember { mutableStateOf(false) }
    var voir by remember { mutableStateOf(false) }
    var modifierCollecte by remember { mutableStateOf(false) }
    val finances = d.peut("consulter_finances", "gerer_cotisations", "gerer_activites")
    val gere = d.peut("gerer_activites")
    LaunchedEffect(e.id) {
        try {
            if (e.collecteId != null) {
                if (finances) collecte = Repo.collectes().firstOrNull { it.id == e.collecteId }
                else maPart = Repo.mesParticipations().firstOrNull { it.collecteId == e.collecteId }
            }
            if (gere) projets = Repo.projets()
        } catch (_: Exception) { }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.nom, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // Comme le site : l'activité suivie au budget est signalée à tous ; « Voir le budget » selon les droits
            if (e.type == "activite") PucePlanning("Budget suivi", CouleursPlanning.Surface2, CouleursPlanning.Bord, CouleursPlanning.Texte2)
        }
        Column {
            val quand = e.debut?.let { jourLong(it) + if (e.fin != null && e.fin != e.debut) " au " + jourLong(e.fin) else "" } ?: "Date à fixer"
            LigneDetailEvt("Date") { Text(quand, fontSize = 14.sp, textAlign = TextAlign.End) }
            e.heureDebut?.let { h -> LigneDetailEvt("Heure") { Text(heureFr(h) + (e.heureFin?.let { f -> " – " + heureFr(f) } ?: ""), fontSize = 14.sp, textAlign = TextAlign.End) } }
            e.lieu?.takeIf { it.isNotBlank() }?.let { l -> LigneDetailEvt("Lieu") { Text(l, fontSize = 14.sp, textAlign = TextAlign.End) } }
            // Visibilité changée d'un geste, sans ouvrir le formulaire (interrupteur du site)
            if (gere) {
                var visible by remember(e.id) { mutableStateOf(e.visible) }
                val portee = rememberCoroutineScope()
                LigneDetailEvt("Visibilité") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(visible, { v -> visible = v; portee.launch {
                            try { Repo.majVisibilite(e.id, v); message(if (v) "Visible par tous les membres" else "Réservé au bureau"); onChange() }
                            catch (ex: Exception) { visible = !v; message(traduireErreur(ex)) } } })
                        Text(if (visible) "Tous les membres" else "Bureau seulement", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        e.description?.let { Text(it, fontSize = 14.sp) }
        collecte?.let { c ->
            InfoPlanning(buildAnnotatedString {
                append("Participation${c.montantAttendu?.let { " de ${euros(it)} par personne" } ?: ""}$NBSP: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(euros(c.totalRecu)) }
                append(" reçus de ${c.nbContributeurs} contributeur${if (c.nbContributeurs > 1) "s" else ""}")
            })
        }
        maPart?.let { p ->
            InfoPlanning(buildAnnotatedString {
                append("Participation demandée$NBSP: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p.montantAttendu?.let { euros(it) } ?: "libre") }
                append(" · vous avez donné ${euros(p.donne)}")
            })
        }
        FlowRowActions {
            if (collecte != null && d.peut("consulter_finances", "gerer_cotisations")) TextButton(onClick = { voir = true }) { Text("Voir les participations") }
            if (e.type == "activite" && d.peut("consulter_finances", "gerer_budget") && onBudget != null) TextButton(onClick = { onFermer(); onBudget() }) { Text("Voir le budget") }
            if (e.collecteId == null && gere) TextButton(onClick = { demander = true }) { Text("Demander une participation") }
            if (gere) BoutonSupprimer({ if (onSupprimer != null) onSupprimer(e) else supprimerEvt("projects", e.id, e.nom) })
            if (gere) OutlinedButton(onClick = { modifier = projets.firstOrNull { it.id == e.id } ?: e }, shape = FormeP4) { Text("Modifier") }
            Button(onClick = onFermer, shape = FormeP4) { Text("Fermer") }
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
    var type by remember { mutableStateOf(PreferencesTiers.type) }
    var recherche by remember { mutableStateOf(PreferencesTiers.texte) }
    val an = aujourdhui().year
    var annee by remember { mutableStateOf<Int?>(when (val a = PreferencesTiers.annee) { null -> an; 0 -> null; else -> a }) }
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
                    listOf("tous" to "Tous", "membres" to "Membres", "autres" to "Autres tiers").forEach { (k, l) -> FilterChip(selected = type == k, onClick = { type = k; PreferencesTiers.type = k }, label = { Text(l) }) }
                }
            }
            // Année et recherche sur une ligne, comme les filtres du site
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PuceMenu(annee?.toString() ?: "Toutes les années", true, listOf(an, an - 1, an - 2).map { it.toString() } + "Toutes les années") { i ->
                        annee = if (i < 3) an - i else null; PreferencesTiers.annee = annee ?: 0
                    }
                    OutlinedTextField(recherche, { recherche = it; PreferencesTiers.texte = it }, placeholder = { Text("Rechercher") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            if (operations == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            // Une seule carte qui contient la liste (section .carte du site)
            else item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (lignes.isEmpty()) Text("Aucun tiers", color = Couleurs.Texte2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                        lignes.forEachIndexed { i, l ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Row(Modifier.fillMaxWidth().clickable { fiche = l.m to l.t }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Initiales : prénom et nom d'un membre, deux premières lettres d'un tiers (comme le site)
                                if (l.m != null) Avatar(l.m.prenom, l.m.nom, 40) else Avatar(l.nom.take(1), l.nom.drop(1).take(1), 40)
                                Column(Modifier.weight(1f)) {
                                    Text(l.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(l.type + if (l.n > 0) " · ${l.n} opération${if (l.n > 1) "s" else ""}" else "", fontSize = 13.sp, color = Couleurs.Texte2)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (l.rec > 0) Text("+ ${euros(l.rec)}", color = Couleurs.Bleu, fontWeight = FontWeight.SemiBold)
                                    if (l.dep > 0) Text("− ${euros(l.dep)}", color = Couleurs.Orange, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (d.peut("saisir_ecritures", "gerer_cotisations")) BoutonPlus("Nouveau tiers", Modifier.align(Alignment.BottomEnd)) { edition = true to null }
    }
    val supprimerTiers = rememberSuppression(message) { version++ }
    fiche?.let { (m, t) ->
        ModalBottomSheet(onDismissRequest = { fiche = null }) {
            val liste = (operations ?: emptyList()).filter { if (m != null) it.membreId == m.id else it.tiersId == t?.id }.sortedByDescending { it.date }
            val parRubrique = liste.filter { it.sens == "recette" }.groupBy { nomRubrique(it, collectes).ifBlank { d.categories.firstOrNull { c -> c.id == it.categorieId }?.nom ?: "" } }
                .mapValues { e -> e.value.sumOf { it.montant } }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (m != null) Avatar(m.prenom, m.nom)
                    Text(m?.nomComplet ?: t?.nom ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Puce(if (m != null) "Membre" else TYPES_TIERS[t?.type] ?: "", Couleurs.Surface2, Couleurs.Texte2)
                }
                t?.let { listOfNotNull(it.telephone, it.email, it.notes).filter { s -> s.isNotBlank() }.takeIf { l -> l.isNotEmpty() }?.let { l -> Text(l.joinToString(" · "), color = Couleurs.Texte2, fontSize = 14.sp) } }
                if (parRubrique.isNotEmpty()) {
                    TitreFiche("Ce qu’il a donné")
                    parRubrique.forEach { (k, v) -> LigneSimple(k, "", v, signe = false) }
                }
                TitreFiche("Opérations")
                if (liste.isEmpty()) Text("Aucune opération", color = Couleurs.Texte2)
                liste.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    LigneSimple(e.libelle, dateFr(e.date) + nomRubrique(e, collectes).let { if (it.isBlank()) "" else " · $it" }, e.signe)
                }
                // Mêmes boutons, dans le même ordre, que la fiche du site : Exporter, Modifier, Supprimer, Cotisation, Fermer
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val nom = sansAccentsCode(m?.nomComplet ?: t?.nom ?: "tiers").replace("_", "-")
                        enregistrerCsv("tiers-$nom.csv", "text/csv", csv(listOf("Date", "Libellé", "Rubrique", "Catégorie", "Montant"),
                            liste.map { listOf(dateFr(it.date), it.libelle, nomRubrique(it, collectes), d.nomCategorie(it.categorieId), it.signe) }).encodeToByteArray())
                    }) { Text("Exporter") }
                    if (t != null && d.peut("saisir_ecritures", "gerer_cotisations")) TextButton(onClick = { fiche = null; edition = false to t }) { Text("Modifier") }
                    if (t != null && d.peut("saisir_ecritures", "gerer_cotisations") && liste.isEmpty()) BoutonSupprimer({ fiche = null; supprimerTiers("tiers", t.id, t.nom) })
                    if (m != null && d.peut("gerer_cotisations")) TextButton(onClick = { fiche = null; cotisMembre = m }) { Text("Cotisation") }
                    Button(onClick = { fiche = null }) { Text("Fermer") }
                }
            }
        }
    }
    cotisMembre?.let { m ->
        ModalBottomSheet(onDismissRequest = { cotisMembre = null }) {
            FicheCotisation(d, m, an, pas, message, onEncaisser = { pre -> cotisMembre = null; encaisser = pre }, onChange = { version++ }, onFermer = { cotisMembre = null })
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
            OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom *") }, singleLine = true)
            ChoixListe("Type", TYPES_TIERS[typeT] ?: "", TYPES_TIERS.values.toList()) { typeT = TYPES_TIERS.keys.toList()[it] }
            OutlinedTextField(tel, { tel = it }, label = { Text("Téléphone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(mail, { mail = it.trim() }, label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes") })
            if (t != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(actif, { actif = it }); Text("Actif") }
        }
    }
}

// Mémoire des filtres de la page Tiers pendant la session (S.filtreTiers du site) ; annee 0 = toutes les années
object PreferencesTiers { var type = "tous"; var texte = ""; var annee: Int? = null }

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
// tuiles : page des membres (Material 3 Expressive), chaque participation dans une tuile douce
fun ListeParticipations(parts: List<Participation>, tuiles: Boolean = false) {
    if (parts.isEmpty()) { Text("Aucune participation demandée", color = Couleurs.Texte2); return }
    parts.forEachIndexed { k, p ->
        Row(Modifier.fillMaxWidth().then(if (tuiles) Modifier.clip(formeSegment(k, parts.size)).background(Couleurs.M3SurfaceBasse).padding(horizontal = 12.dp, vertical = 10.dp) else Modifier.padding(vertical = 4.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nom, fontWeight = FontWeight.SemiBold)
                Text("Donné$NBSP: ${euros(p.donne)}" + (p.dateLimite?.let { " · avant le ${dateFr(it)}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
            }
            val att = p.montantAttendu ?: 0.0
            when {
                att > 0 && p.donne >= att -> Puce("Réglé", Couleurs.BleuClair, Couleurs.SurBleuClair)
                att > 0 && p.donne > 0 -> Puce("Reste ${euros(att - p.donne)}", Couleurs.JauneClair, Couleurs.SurJaune)
                att > 0 -> Puce("${euros(att)} attendus", Couleurs.ErreurClair, Couleurs.SurErreurClair)
                p.donne > 0 -> Puce("Merci", Couleurs.BleuClair, Couleurs.SurBleuClair)
                else -> Puce("Libre", Couleurs.Surface2, Couleurs.Texte2)
            }
        }
    }
}

@Composable
private fun FlecheMois(icone: androidx.compose.ui.graphics.vector.ImageVector, description: String, taille: androidx.compose.ui.unit.Dp = 44.dp, onClick: () -> Unit) =
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.14f), contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), modifier = Modifier.size(taille)) {
        Box(contentAlignment = Alignment.Center) { Icon(icone, description) }
    }
