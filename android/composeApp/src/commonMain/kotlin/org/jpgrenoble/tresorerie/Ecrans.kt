package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// =====================================================================
// Accueil : bannière (photo de l'association, trésorerie), comptes,
// puis rubriques dépliables (à traiter, chiffres, évolution, répartition,
// anniversaires). L'état des rubriques est gardé pendant la session.
// =====================================================================
object EtatAccueil { val ouvertes = mutableStateMapOf<String, Boolean>() }

@Composable
fun EcranAccueil(d: Donnees, onAller: (String) -> Unit = {}) {
    var soldes by remember { mutableStateOf<List<Solde>?>(null) }
    var anniv by remember { mutableStateOf<List<Anniversaire>>(emptyList()) }
    var retards by remember { mutableStateOf<List<Retard>>(emptyList()) }
    var demandes by remember { mutableStateOf<List<Demande>>(emptyList()) }
    var alertes by remember { mutableStateOf<List<LigneBudget>>(emptyList()) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var operations by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var comptesTous by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var cotis by remember { mutableStateOf<List<Cotisation>>(emptyList()) }
    val voitSoldes = d.peut("consulter_finances")
    var nbComptes by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        if (d.peut("administrer")) try { nbComptes = Repo.profilsComplets().size + Repo.invitations().size } catch (_: Exception) { }
    }
    LaunchedEffect(Unit) {
        try {
            anniv = Repo.anniversaires()
            if (voitSoldes) {
                soldes = Repo.soldes(); retards = Repo.retards()
                operations = Repo.toutesEcritures(); comptesTous = try { Repo.tousLesComptes() } catch (_: Exception) { d.comptes }
                cotis = try { Repo.cotisations(aujourdhui().year) } catch (_: Exception) { emptyList() }
            }
            if (d.peut("demander_depenses", "valider_depenses", "payer_depenses", "consulter_finances")) demandes = Repo.demandes()
            if (d.peut("consulter_finances", "gerer_budget")) alertes = Repo.budget(aujourdhui().year).filter { it.alerte }
        } catch (e: Exception) { erreur = traduireErreur(e) }
    }
    // Paiement neutralisé par contre-passation : plus de justificatif attendu ; refus après paiement : à régulariser
    val etats = remember(demandes, operations) { EtatsDemandes(demandes, operations) }
    val retardsActifs = retards.filter { it.id !in etats.annulees }
    val aRegul = demandes.filter { etats.aRegulariser(it) }
    val aValider = demandes.filter { it.statut == "soumise" }
    val aPayer = demandes.filter { it.statut == "validee" && it.valideePar != d.profil.id }
    val aJustifier = demandes.filter { it.statut == "payee" && it.id !in etats.annulees && (d.peut("saisir_ecritures", "payer_depenses") || it.demandeur == d.profil.id) }
    fun s(n: Int) = if (n > 1) "s" else ""
    // (titre, détail, destination, alerte)
    val taches = buildList {
        if (d.peut("saisir_ecritures") && aRegul.isNotEmpty()) add(listOf("${aRegul.size} dépense${s(aRegul.size)} refusée${s(aRegul.size)} après paiement",
            "À régulariser$NBSP: ${euros(aRegul.sumOf { it.montant })}", "depenses", "alerte"))
        if (retardsActifs.isNotEmpty()) add(listOf("${retardsActifs.size} justificatif${s(retardsActifs.size)} en retard",
            retardsActifs.joinToString(", ") { "${it.objet} (${euros(it.montant)}, ${it.jours}$NBSP" + "jours)" }, "depenses", "alerte"))
        if (d.peut("valider_depenses") && aValider.isNotEmpty()) add(listOf("${aValider.size} demande${s(aValider.size)} à valider", euros(aValider.sumOf { it.montant }), "depenses", ""))
        if (d.peut("payer_depenses") && aPayer.isNotEmpty()) add(listOf("${aPayer.size} demande${s(aPayer.size)} à payer", euros(aPayer.sumOf { it.montant }), "depenses", ""))
        if (aJustifier.isNotEmpty()) add(listOf("${aJustifier.size} justificatif${s(aJustifier.size)} à joindre", euros(aJustifier.sumOf { it.montant }), "depenses", ""))
        if (alertes.isNotEmpty()) add(listOf("${alertes.size} poste${s(alertes.size)} de budget en alerte", alertes.joinToString(", ") { it.categorie }, "budget", ""))
    }
    val auj = aujourdhui(); val jour = auj.toString(); val an = auj.year; val mois = auj.monthNumber
    val debut12 = LocalDate(an, mois, 1).minus(DatePeriod(months = 11)).toString()
    val serie = remember(operations, comptesTous) { serieMensuelle(operations, comptesTous, debut12, jour) }
    val ytd = operations.filter { it.date >= "$an-01-01" && it.date <= jour }
    val n1 = operations.filter { it.date >= "${an - 1}-01-01" && it.date <= "${an - 1}${jour.drop(4)}" }
    fun flux(l: List<Ecriture>, sens: String) = l.filter { it.sens == sens && it.estFlux }.sumOf { it.montant }
    val rec = flux(ytd, "recette"); val dep = flux(ytd, "depense"); val resultat = rec - dep
    val total = soldes?.sumOf { it.solde } ?: 0.0
    val reserve = reserveEnMois(total, serie)
    val exigible = cotis.sumOf { it.exigible ?: 0.0 }; val encaisse = cotis.sumOf { it.paye }
    fun signe(v: Double) = (if (v >= 0) "+$NBSP" else "−$NBSP") + euros0(kotlin.math.abs(v))
    fun variation(a: Double, b: Double) = if (b > 0) kotlin.math.round(100 * (a - b) / b).toInt().let { (if (it > 0) "+" else if (it < 0) "−" else "") + "${kotlin.math.abs(it)}$NBSP% sur un an" } else "Pas de comparaison"
    fun repartition(sens: String) = ytd.filter { it.sens == sens && it.estFlux }.groupBy { it.categorieId }
        .map { (id, l) -> Element(d.nomCategorie(id), l.sumOf { it.montant }) }.sortedByDescending { it.valeur }
    val reserveTxt = reserve?.let { "${(kotlin.math.round(it * 10) / 10).toString().replace('.', ',').removeSuffix(",0")}$NBSP" + "mois" } ?: "Non définie"
    // Opérations saisies puis toutes annulées : rien à représenter sur 12 mois
    val sansMouvementNet = operations.isNotEmpty() && serie.all { kotlin.math.abs(it.rec) < 0.005 && kotlin.math.abs(it.dep) < 0.005 }
    val ecart12 = serie.firstOrNull()?.let { serie.last().solde - (it.solde - it.rec + it.dep) } ?: 0.0
    // Bien démarrer : étapes de mise en route, cochées automatiquement
    val etapesDemarrage = if (d.peut("administrer")) listOf(
        "Créer votre fiche de membre" to (d.profil.memberId != null),
        "Renseigner l’association : nom, logo, photo" to (d.organisation.logo != null || d.organisation.banniere != null),
        "Saisir les soldes de départ des comptes" to comptesTous.ifEmpty { d.comptes }.any { it.soldeInitial != 0.0 },
        "Ajouter les membres" to (d.membres.size > 1),
        "Désigner le bureau : président, secrétaire…" to (nbComptes > 1),
        "Générer les cotisations de l’année" to cotis.isNotEmpty(),
    ) else emptyList()
    val faites = etapesDemarrage.count { it.second }
    val rubriques = buildList {
        if (etapesDemarrage.isNotEmpty() && faites < etapesDemarrage.size) add("demarrer")
        if (taches.isNotEmpty()) add("traiter")
        if (voitSoldes && operations.isNotEmpty()) addAll(listOf("chiffres", "evolution", "repartition"))
        add("anniversaires")
    }
    val toutOuvert = rubriques.all { EtatAccueil.ouvertes[it] ?: true }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        erreur?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Banniere(d) {
                if (voitSoldes) {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onAller("operations:") }) {
                        Text("Trésorerie au ${dateFr(jour)}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (soldes == null) LinearProgressIndicator(Modifier.width(160.dp).padding(vertical = 14.dp), color = Color.White)
                        else Text(euros(total), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                    if (operations.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PuceBanniere("Résultat $an", signe(resultat), Modifier.weight(1f))
                        PuceBanniere("Réserve", reserveTxt, Modifier.weight(1f))
                        if (exigible > 0) PuceBanniere("Cotisations", "${kotlin.math.round(100 * encaisse / exigible).toInt()}$NBSP%", Modifier.weight(1f))
                    }
                }
            }
        }
        soldes?.takeIf { it.isNotEmpty() }?.chunked(2)?.forEach { paire ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    paire.forEach { c -> CarteCompte(c, Modifier.weight(1f)) { onAller("operations:${c.id}") } }
                    if (paire.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { val v = !toutOuvert; rubriques.forEach { EtatAccueil.ouvertes[it] = v } }) {
                    Text(if (toutOuvert) "Tout replier" else "Tout déplier")
                }
            }
        }
        if (etapesDemarrage.isNotEmpty() && faites < etapesDemarrage.size) item {
            Rubrique("demarrer", "Bien démarrer", "$faites sur ${etapesDemarrage.size}") {
                etapesDemarrage.forEachIndexed { i, (titre, fait) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        onAller(when (i) { 0, 3, 4 -> "membres"; 5 -> "cotisations"; else -> "parametres" })
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(26.dp).background(if (fait) Couleurs.Bleu else Color.Transparent, CircleShape)
                            .then(if (fait) Modifier else Modifier.border(2.dp, Color(0xFFD9D3D0), CircleShape)), contentAlignment = Alignment.Center) {
                            if (fait) Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Text(titre, fontWeight = FontWeight.SemiBold, color = if (fait) Couleurs.Texte2 else Couleurs.Texte,
                            textDecoration = if (fait) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    }
                }
            }
        }
        if (taches.isNotEmpty()) item {
            Rubrique("traiter", "À traiter", "${taches.size} action${s(taches.size)}", alerte = retardsActifs.isNotEmpty() || aRegul.isNotEmpty()) {
                taches.forEach { (titre, detail, cible, nature) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onAller(cible) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(titre, fontWeight = FontWeight.SemiBold, color = if (nature == "alerte") Couleurs.Erreur else Couleurs.Texte)
                            Text(detail, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("Voir", color = Couleurs.Orange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (voitSoldes && operations.isNotEmpty()) {
            item {
                Rubrique("chiffres", "Chiffres $an", "Recettes ${euros0(rec)} · Dépenses ${euros0(dep)}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tuile("Recettes $an", euros0(rec), variation(rec, flux(n1, "recette")), CouleursGraph.Recette, Modifier.weight(1f))
                        Tuile("Dépenses $an", euros0(dep), variation(dep, flux(n1, "depense")), CouleursGraph.Depense, Modifier.weight(1f))
                    }
                    if (exigible > 0) Surface(color = Couleurs.Fond, shape = RoundedCornerShape(20.dp)) {
                        Jauge("Cotisations encaissées", encaisse, exigible,
                            "${euros0(encaisse)} sur ${euros0(exigible)} · ${cotis.count { it.statut == "a_jour" }} membres à jour sur ${cotis.size}", Modifier.padding(14.dp).fillMaxWidth())
                    }
                }
            }
            if (sansMouvementNet) item {
                Rubrique("evolution", "Évolution sur 12 mois", "Aucun mouvement net") {
                    Text("Aucune opération nette sur les 12 derniers mois$NBSP: les opérations saisies ont été annulées par contre-passation. Elles restent visibles dans Opérations.", color = Couleurs.Texte2)
                }
            }
            if (!sansMouvementNet) item {
                Rubrique("evolution", "Évolution sur 12 mois", "Trésorerie ${signe(ecart12)}") {
                    Text("Recettes et dépenses par mois", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    GraphColonnes(serie)
                    Text("Trésorerie en fin de mois", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    GraphSolde(serie)
                }
            }
            if (!sansMouvementNet) item {
                val depCat = repartition("depense")
                Rubrique("repartition", "Répartition $an", depCat.firstOrNull()?.let { "Premier poste de dépense$NBSP: ${it.nom}" } ?: "") {
                    Text("Origine des recettes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    GraphAnneau(repartition("recette"), "recette")
                    Text("Destination des dépenses", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    GraphAnneau(depCat, "depense")
                }
            }
        }
        item {
            Rubrique("anniversaires", "Anniversaires ${deMois(mois)}", if (anniv.isEmpty()) "Aucun" else "${anniv.size} personne${s(anniv.size)}") {
                if (anniv.isEmpty()) Text("Aucun anniversaire ce mois-ci", color = Couleurs.Texte2)
                anniv.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Avatar(a.prenom, a.nom)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${a.prenom} ${a.nom}", fontWeight = FontWeight.SemiBold)
                            Text("${a.jour} ${MOIS[mois - 1]}" + (a.profession?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                        if (a.jour == auj.dayOfMonth) Pastille("Aujourd’hui", Couleurs.JauneClair, Couleurs.SurJaune)
                    }
                }
            }
        }
    }
}

// Bannière : photo de l'association si elle existe, sinon aplat neutre ; voile sombre pour la lisibilité
@Composable
private fun Banniere(d: Donnees, contenu: @Composable ColumnScope.() -> Unit) {
    val octets by Repo.banniere.collectAsState()
    val photo = remember(octets) { octets?.let { imageDepuisOctets(it) } }
    val forme = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(forme)
        .background(Brush.radialGradient(listOf(Color(0xFF5A5350), Color(0xFF2B2826), Color(0xFF1C1B1A)), center = Offset(2000f, 0f), radius = 2200f))) {
        if (photo != null) {
            Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x40141211), Color(0x8C141211), Color(0xD1141211)))))
        }
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White)) { LogoAsso(Modifier.fillMaxSize()) }
                Column(Modifier.weight(1f)) {
                    Text(d.organisation.nom, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Bonjour ${d.membres.firstOrNull { it.id == d.profil.memberId }?.prenom ?: d.profil.nom.substringBefore('@').substringBefore(' ')} · ${dateFr(aujourdhui().toString())}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            contenu()
        }
    }
}

@Composable
private fun PuceBanniere(titre: String, valeur: String, modifier: Modifier = Modifier) =
    Surface(color = Color.White.copy(alpha = 0.14f), contentColor = Color.White, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(titre, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }

@Composable
private fun CarteCompte(c: Solde, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9D3D0)), modifier = modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Color(0xFFEFEDEC), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (c.type == "caisse") Icons.Outlined.Payments else Icons.Outlined.AccountBalance, null, tint = Couleurs.Texte2, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(c.nom, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(euros(c.solde), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, color = if (c.solde < 0) Couleurs.Erreur else Couleurs.Texte)
            }
        }
    }

@Composable
private fun Tuile(titre: String, valeur: String, detail: String, couleur: Color, modifier: Modifier = Modifier) =
    Surface(color = Couleurs.Fond, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(titre, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = couleur, maxLines = 1)
            Text(detail, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

// Rubrique dépliable : un appui sur l'en-tête ouvre ou ferme ; repliée, elle montre un résumé
@Composable
internal fun Rubrique(id: String, titre: String, resume: String, alerte: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ouverte = EtatAccueil.ouvertes[id] ?: true
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (ouverte) 180f else 0f)
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        border = if (alerte) androidx.compose.foundation.BorderStroke(2.dp, Couleurs.Erreur.copy(alpha = 0.5f)) else null) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (ouverte) "Replier" else "Déplier") { EtatAccueil.ouvertes[id] = !ouverte }
                .padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!ouverte && resume.isNotBlank()) Text(resume, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ExpandMore, if (ouverte) "Replier" else "Déplier", tint = Couleurs.Texte2, modifier = Modifier.graphicsLayer { rotationZ = rotation })
            }
            androidx.compose.animation.AnimatedVisibility(ouverte) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

@Composable
internal fun Carte(content: @Composable ColumnScope.() -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }

@Composable
internal fun Avatar(prenom: String, nom: String, taille: Int = 44) =
    Box(Modifier.size(taille.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) {
        Text("${prenom.take(1)}${nom.take(1)}".uppercase(), color = Couleurs.SurOrangeClair, fontWeight = FontWeight.Bold)
    }

@Composable
private fun Pastille(texte: String, fond: Color, couleur: Color) =
    Surface(color = fond, contentColor = couleur, shape = RoundedCornerShape(13.dp)) {
        Text(texte, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

// =====================================================================
// Opérations : filtres (sens, période, compte, catégorie, pièce, recherche),
// totaux, solde, détail avec pièces jointes
// =====================================================================
private val PERIODES = listOf("mois" to "Ce mois", "mois_prec" to "Mois précédent", "annee" to "Cette année", "tout" to "Tout", "perso" to "Du… au…")

private fun bornes(periode: String, perso: Pair<LocalDate, LocalDate>?): Pair<String, String> {
    val t = aujourdhui()
    val debutMois = LocalDate(t.year, t.monthNumber, 1)
    return when (periode) {
        "mois" -> debutMois.toString() to debutMois.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).toString()
        "mois_prec" -> debutMois.minus(DatePeriod(months = 1)).toString() to debutMois.minus(DatePeriod(days = 1)).toString()
        "annee" -> "${t.year}-01-01" to "${t.year}-12-31"
        "perso" -> (perso?.first?.toString() ?: "0000-01-01") to (perso?.second?.toString() ?: "9999-12-31")
        else -> "0000-01-01" to "9999-12-31"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranOperations(d: Donnees, message: (String) -> Unit, compteInitial: String? = null) {
    var liste by remember { mutableStateOf<List<Ecriture>?>(null) }
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    var demandes by remember { mutableStateOf<List<Demande>>(emptyList()) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var profils by remember { mutableStateOf<List<ProfilCourt>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var sens by remember { mutableStateOf("tout") }
    var periode by remember { mutableStateOf("annee") }
    var perso by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var compte by remember(compteInitial) { mutableStateOf(compteInitial?.ifBlank { null }) }
    var categorie by remember { mutableStateOf<String?>(null) }
    var sansPiece by remember { mutableStateOf(false) }
    var recherche by remember { mutableStateOf("") }
    var saisie by remember { mutableStateOf(false) }
    var virement by remember { mutableStateOf(false) }
    var filtres by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Ecriture?>(null) }
    var tiers by remember { mutableStateOf<List<Tiers>>(emptyList()) }
    var collectes by remember { mutableStateOf<List<Collecte>>(emptyList()) }
    var rubrique by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) {
        try { tiers = Repo.tiers() } catch (_: Exception) { }
        try { collectes = Repo.collectes() } catch (_: Exception) { }
        try { profils = Repo.profils() } catch (_: Exception) { }
        try {
            liste = Repo.toutesEcritures().sortedWith(compareByDescending<Ecriture> { it.date }.thenByDescending { it.creeLe ?: it.id })
            pieces = Repo.pieces()
            demandes = try { Repo.demandes() } catch (_: Exception) { emptyList() }
            projets = try { Repo.projets() } catch (_: Exception) { emptyList() }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val (debut, fin) = bornes(periode, perso)
    val tous = liste ?: emptyList()
    val contrepassees = tous.mapNotNull { it.contrepasseDe }.toSet()
    fun aPiece(e: Ecriture) = pieces.any { it.nature != "devis" && (it.transactionId == e.id || (e.demandeId != null && it.demandeId == e.demandeId)) }
    val q = recherche.trim().lowercase()
    // Filtres hors sens : servent aux totaux (recettes et dépenses restent visibles)
    val base = tous.filter { e ->
        e.date in debut..fin && (compte == null || e.compteId == compte) && (categorie == null || e.categorieId == categorie) &&
            (rubrique == null || (if (rubrique == "cotisation") e.estCotisation else e.collecteId == rubrique)) &&
            (!sansPiece || (e.sens == "depense" && e.estFlux && e.montant > 0 && !aPiece(e) && e.contrepasseDe == null && e.id !in contrepassees)) &&
            (q.isEmpty() || e.libelle.lowercase().contains(q) || nomTiers(e, d.membres, tiers).lowercase().contains(q) || nomRubrique(e, collectes).lowercase().contains(q) || montantSaisie(e.montant).contains(q))
    }
    val vues = base.filter { sens == "tout" || it.sens == sens }
    // Un virement interne n'est ni une recette ni une dépense
    val recettes = base.filter { it.sens == "recette" && it.estFlux }.sumOf { it.montant }
    val depenses = base.filter { it.sens == "depense" && it.estFlux }.sumOf { it.montant }
    val finSolde = minOf(fin, aujourdhui().toString())
    val comptesVus = d.comptes.filter { compte == null || it.id == compte }
    val solde = comptesVus.sumOf { it.soldeInitial } + tous.filter { e -> comptesVus.any { it.id == e.compteId } && e.date <= finSolde }.sumOf { it.signe }
    // Filtres actifs en pastilles : un appui retire le filtre
    val pastilles = buildList<Pair<String, String>> {
        if (sens != "tout") add("sens" to if (sens == "recette") "Recettes" else "Dépenses")
        if (periode != "annee") add("periode" to (perso?.takeIf { periode == "perso" }?.let { "Du ${dateFr(it.first.toString())} au ${dateFr(it.second.toString())}" } ?: PERIODES.first { it.first == periode }.second))
        compte?.let { c -> add("compte" to (d.comptes.firstOrNull { it.id == c }?.nom ?: "Compte")) }
        categorie?.let { c -> add("categorie" to d.nomCategorie(c)) }
        rubrique?.let { r -> add("rubrique" to (if (r == "cotisation") "Cotisations" else collectes.firstOrNull { it.id == r }?.nom ?: "Rubrique")) }
        if (sansPiece) add("piece" to "Sans pièce")
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp)) {
            item { Titre("Opérations") }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (recherche.isNotEmpty()) IconButton(onClick = { recherche = "" }) { Icon(Icons.Outlined.Close, "Effacer la recherche") } },
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { filtres = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (pastilles.isEmpty()) "Filtres" else "Filtres (${pastilles.size})")
                    }
                }
            }
            if (pastilles.isNotEmpty()) item {
                FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    pastilles.forEach { (cle, libelle) ->
                        InputChip(selected = false, onClick = {
                            when (cle) { "sens" -> { sens = "tout"; categorie = null }; "periode" -> periode = "annee"; "compte" -> compte = null
                                "categorie" -> categorie = null; "rubrique" -> rubrique = null; "piece" -> sansPiece = false }
                        }, label = { Text(libelle, maxLines = 1) }, trailingIcon = { Icon(Icons.Outlined.Close, "Retirer le filtre $libelle", Modifier.size(16.dp)) })
                    }
                    TextButton(onClick = { sens = "tout"; periode = "annee"; compte = null; categorie = null; rubrique = null; sansPiece = false }) { Text("Tout effacer") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Indicateur("Recettes", euros(recettes), Couleurs.Bleu, Modifier.weight(1f)) { sens = "recette" }
                        Indicateur("Dépenses", euros(depenses), Couleurs.Orange, Modifier.weight(1f)) { sens = "depense" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Indicateur("Résultat", euros(recettes - depenses), if (recettes >= depenses) Couleurs.Bleu else Couleurs.Erreur, Modifier.weight(1f))
                        Indicateur("Solde au ${dateFr(finSolde)}", euros(solde), Couleurs.Texte, Modifier.weight(1f))
                    }
                }
            }
            val l = liste
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (vues.isEmpty()) item { Text(if (pastilles.isEmpty() && q.isEmpty()) "Aucune opération" else "Aucune opération pour ces filtres", color = Couleurs.Texte2, modifier = Modifier.padding(vertical = 16.dp)) }
            else {
                item { Text("${vues.size} opération${if (vues.size > 1) "s" else ""}", fontSize = 13.sp, color = Couleurs.Texte2) }
                items(vues, key = { it.id }) { e ->
                    LigneOperation(d, e, aPiece(e), e.id in contrepassees, nomTiers(e, d.membres, tiers), nomRubrique(e, collectes),
                        demandes.firstOrNull { it.id == e.demandeId }) { detail = e }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
        if (d.peut("saisir_ecritures")) {
            LargeFloatingActionButton(
                onClick = { saisie = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Nouvelle opération") }
        }
    }
    if (saisie) {
        ModalBottomSheet(onDismissRequest = { saisie = false }) {
            FormulaireEcriture(d, PreEcriture(), onFini = { ok -> saisie = false; if (ok) { message("Opération enregistrée"); version++ } }, message,
                onVirement = if (d.comptes.size > 1) ({ saisie = false; virement = true }) else null)
        }
    }
    if (virement) {
        ModalBottomSheet(onDismissRequest = { virement = false }) {
            FormulaireVirement(d, message) { ok -> virement = false; if (ok) { message("Virement enregistré"); version++ } }
        }
    }
    if (filtres) {
        ModalBottomSheet(onDismissRequest = { filtres = false }) {
            FeuilleFiltres(d, collectes, sens, periode, perso, compte, categorie, rubrique, sansPiece) { s2, p2, perso2, c2, cat2, r2, sp2 ->
                sens = s2; periode = p2; perso = perso2; compte = c2; categorie = cat2; rubrique = r2; sansPiece = sp2; filtres = false
            }
        }
    }
    detail?.let { e ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailOperation(d, e, tous, pieces, demandes, projets, profils, nomTiers(e, d.membres, tiers), nomRubrique(e, collectes), message,
                onVoir = { detail = it }) { change -> detail = null; if (change) version++ }
        }
    }
}

// Tous les filtres des opérations dans une seule feuille, appliqués d'un coup
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleFiltres(d: Donnees, collectes: List<Collecte>, sens0: String, periode0: String, perso0: Pair<LocalDate, LocalDate>?, compte0: String?,
                           categorie0: String?, rubrique0: String?, sansPiece0: Boolean,
                           onAppliquer: (String, String, Pair<LocalDate, LocalDate>?, String?, String?, String?, Boolean) -> Unit) {
    var sens by remember { mutableStateOf(sens0) }
    var periode by remember { mutableStateOf(periode0) }
    var du by remember { mutableStateOf(perso0?.first?.let { dateFr(it.toString()) } ?: "01/${aujourdhui().monthNumber.toString().padStart(2, '0')}/${aujourdhui().year}") }
    var au by remember { mutableStateOf(perso0?.second?.let { dateFr(it.toString()) } ?: dateFr(aujourdhui().toString())) }
    var compte by remember { mutableStateOf(compte0) }
    var categorie by remember { mutableStateOf(categorie0) }
    var rubrique by remember { mutableStateOf(rubrique0) }
    var sansPiece by remember { mutableStateOf(sansPiece0) }
    val categoriesVues = d.categories.filter { sens == "tout" || it.sens == sens }
    val dDu = dateDepuisFr(du); val dAu = dateDepuisFr(au)
    val persoValide = periode != "perso" || (dDu != null && dAu != null && dAu >= dDu)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Filtrer les opérations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("tout" to "Tout", "recette" to "Recettes", "depense" to "Dépenses").forEachIndexed { i, (k, v) ->
                SegmentedButton(selected = sens == k, onClick = {
                    sens = k
                    val sc = d.categories.firstOrNull { it.id == categorie }?.sens
                    if (sc != null && k != "tout" && sc != k) categorie = null
                }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(v) }
            }
        }
        ChoixListe("Période", PERIODES.first { it.first == periode }.second, PERIODES.map { it.second }) { periode = PERIODES[it].first }
        if (periode == "perso") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(du, { du = it }, label = { Text("Du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dDu == null, modifier = Modifier.weight(1f))
            OutlinedTextField(au, { au = it }, label = { Text("Au") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
                isError = dAu == null || (dDu != null && dAu < dDu), modifier = Modifier.weight(1f))
        }
        ChoixListe("Compte", d.comptes.firstOrNull { it.id == compte }?.nom ?: "Tous les comptes", listOf("Tous les comptes") + d.comptes.map { it.nom }) {
            compte = if (it == 0) null else d.comptes[it - 1].id
        }
        ChoixListe("Catégorie", categoriesVues.firstOrNull { it.id == categorie }?.nom ?: "Toutes les catégories", listOf("Toutes les catégories") + categoriesVues.map { it.nom }) {
            categorie = if (it == 0) null else categoriesVues[it - 1].id
        }
        val rubs = listOf(null to "Toutes les rubriques", "cotisation" to "Cotisations") + collectes.map { it.id to it.nom }
        ChoixListe("Rubrique", rubs.firstOrNull { it.first == rubrique }?.second ?: "Toutes les rubriques", rubs.map { it.second }) { rubrique = rubs[it].first }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { sansPiece = !sansPiece }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(sansPiece, { sansPiece = it }); Text("Seulement les dépenses sans pièce justificative")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onAppliquer("tout", "annee", null, null, null, null, false) }) { Text("Tout effacer") }
            Button(enabled = persoValide, onClick = {
                onAppliquer(sens, periode, if (periode == "perso") dDu!! to dAu!! else perso0, compte, categorie, rubrique, sansPiece)
            }) { Text("Afficher") }
        }
    }
}

@Composable
private fun LigneOperation(d: Donnees, e: Ecriture, aPiece: Boolean, contrepassee: Boolean, tiers: String, rubrique: String, demande: Demande?, onClick: () -> Unit) {
    val cat = d.nomCategorie(e.categorieId)
    val compte = d.comptes.firstOrNull { it.id == e.compteId }?.nom ?: ""
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (contrepassee || e.contrepasseDe != null) Couleurs.Texte2 else Color.Unspecified)
            Text(listOf(dateFr(e.date), tiers, rubrique.ifBlank { cat }, compte).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            demande?.statut == "soumise" -> Pastille("À valider", Couleurs.JauneClair, Couleurs.SurJaune)
            demande?.statut == "refusee" && !contrepassee -> Pastille(if (demande.regularisation) "Refusée · à régulariser" else "Refusée", Couleurs.ErreurClair, Color(0xFF410002))
            e.rapproche -> Pastille("Rapprochée", Couleurs.BleuClair, Couleurs.SurBleuClair)
            e.contrepasseDe != null -> Pastille("Correction", Color(0xFFEFEDEC), Couleurs.Texte2)
            contrepassee -> Pastille("Annulée", Color(0xFFEFEDEC), Couleurs.Texte2)
            e.virement != null -> Pastille("Virement interne", Color(0xFFEFEDEC), Couleurs.Texte)
            e.sens == "depense" && e.estFlux && e.montant > 0 && !aPiece -> Pastille("Sans pièce", Couleurs.ErreurClair, Color(0xFF410002))
        }
        if (aPiece) Icon(Icons.Outlined.AttachFile, contentDescription = "Pièce jointe", tint = Couleurs.Texte2, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        Spacer(Modifier.width(8.dp))
        val v = e.signe
        Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)),
            color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Indicateur(titre: String, valeur: String, couleur: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else Modifier)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(titre, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = couleur, maxLines = 1)
        }
    }
}

// Puce de filtre qui ouvre une liste de choix
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PuceMenu(libelle: String, actif: Boolean, options: List<String>, onChoix: (Int) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = actif, onClick = { ouvert = true }, label = { Text(libelle, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) })
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChoix(i); ouvert = false }) }
        }
    }
}

@Composable
internal fun LigneInfo(titre: String, valeur: String?) {
    if (valeur.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(titre, color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.width(120.dp))
        Text(valeur, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailOperation(d: Donnees, e: Ecriture, toutes: List<Ecriture>, pieces: List<Piece>, demandes: List<Demande>, projets: List<Projet>,
                             profils: List<ProfilCourt>, tiers: String, rubrique: String, message: (String) -> Unit,
                             onVoir: (Ecriture) -> Unit = {}, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmer by remember { mutableStateOf(false) }
    var annulerVir by remember { mutableStateOf(false) }
    var motif by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    val siennes = pieces.filter { it.transactionId == e.id || (e.demandeId != null && it.demandeId == e.demandeId) }.distinctBy { it.id }
    val demande = demandes.firstOrNull { it.id == e.demandeId }
    val correction = toutes.firstOrNull { it.contrepasseDe == e.id }
    val origine = toutes.firstOrNull { it.id == e.contrepasseDe }
    val jambes = if (e.virement != null) toutes.filter { it.virement == e.virement && it.contrepasseDe == null } else emptyList()
    val virementAnnule = e.virement != null && toutes.any { it.virement == e.virement && it.contrepasseDe != null }
    val compte = d.comptes.firstOrNull { it.id == e.compteId }
    val alerteMode = if (e.virement != null) "" else incoherenceMode(compte, e.mode)
    fun auteur(id: String?) = when (id) { null -> ""; d.profil.id -> "vous"; else -> profils.firstOrNull { it.id == id }?.nom ?: "une personne du bureau" }
    val choix = rememberChoixFichier { f, err ->
        if (err != null) message(err)
        if (f != null) scope.launch {
            try { Repo.joindrePiece(e.id, f, d.profil.id); message("Pièce jointe (${f.ko}$NBSP" + "Ko)"); onFini(true) }
            catch (x: Exception) { message(traduireErreur(x)) }
        }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(e.libelle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val v = e.signe
        Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
            color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange)
        Spacer(Modifier.height(4.dp))
        LigneInfo("Date", dateFr(e.date))
        LigneInfo("Compte", compte?.nom)
        LigneInfo("Catégorie", d.nomCategorie(e.categorieId))
        LigneInfo("Mode", (MODES[e.mode] ?: e.mode) + if (alerteMode.isNotEmpty()) " · à vérifier" else "")
        LigneInfo("Activité", projets.firstOrNull { it.id == e.projetId }?.nom)
        LigneInfo("Tiers", tiers)
        LigneInfo("Rubrique", rubrique)
        if (e.virement != null) LigneInfo("Virement interne", "${d.comptes.firstOrNull { it.id == jambes.firstOrNull { j -> j.sens == "depense" }?.compteId }?.nom ?: "?"} → " +
            "${d.comptes.firstOrNull { it.id == jambes.firstOrNull { j -> j.sens == "recette" }?.compteId }?.nom ?: "?"}, ni recette ni dépense")
        LigneInfo("Validation", demande?.let { x -> when (x.statut) {
            "soumise" -> "À valider par le président"
            "refusee" -> "Refusée" + (x.motifRefus?.let { " : $it" } ?: "")
            "annulee" -> "Demande annulée"
            else -> "Validée" + (x.valideeLe?.let { " le " + dateFr(it.take(10)) } ?: "") + if (x.regularisation) " (après paiement)" else ""
        } })
        LigneInfo("État", when { e.rapproche -> "Rapprochée, verrouillée"; e.contrepasseDe != null -> "Correction d’une autre écriture"; correction != null -> "Annulée par contre-passation"; else -> "Active" })
        LigneInfo("Référence", refOperation(e.id))
        if (alerteMode.isNotEmpty()) Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(12.dp)) {
            Text(alerteMode, Modifier.padding(12.dp), fontSize = 14.sp)
        }
        // Historique : qui a fait quoi, et le lien vers l'écriture liée
        Text("Historique", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(start = 4.dp).border(width = 0.dp, color = Color.Transparent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EvenementHistorique("${dateFr((e.creeLe ?: e.date).take(10))}${e.creeLe?.let { " " + it.drop(11).take(5) } ?: ""}",
                (if (e.contrepasseDe != null) "Correction saisie" else "Saisie") + (e.creePar?.let { " par ${auteur(it)}" } ?: ""))
            origine?.let { o -> EvenementHistorique(dateFr(o.date), "Corrige « ${o.libelle} » (${refOperation(o.id)})" + motifDe(e.libelle).let { m -> if (m.isNotEmpty()) ", motif : $m" else "" }) { onVoir(o) } }
            demande?.let { x -> EvenementHistorique(dateFr(x.creeLe.take(10)), "Demande de validation" + (x.valideeLe?.let { ", validée le ${dateFr(it.take(10))}" } ?: if (x.statut == "refusee") ", refusée" else "")) }
            correction?.let { c -> EvenementHistorique(dateFr(c.date), "Annulée (${c.libelle.substringBefore(SEP_MOTIF).substringBefore(NBSP + ":").lowercase()})" +
                (c.creePar?.let { ", saisie par ${auteur(it)}" } ?: "") + motifDe(c.libelle).let { m -> if (m.isNotEmpty()) ", motif : $m" else "" } + " (${refOperation(c.id)})") { onVoir(c) } }
            if (e.rapproche) EvenementHistorique(e.dateRapprochement?.let { dateFr(it) } ?: "", "Rapprochée avec le relevé, verrouillée")
        }
        Spacer(Modifier.height(8.dp))
        PiecesVue(siennes, demande?.signature)
        Spacer(Modifier.height(8.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (d.peut("saisir_ecritures") && e.virement == null && e.contrepasseDe == null && correction == null && !e.rapproche)
                TextButton(onClick = { motif = ""; confirmer = true }) { Text("Contre-passer", color = Couleurs.Erreur) }
            if (d.peut("saisir_ecritures") && e.virement != null && !virementAnnule && e.contrepasseDe == null && jambes.none { it.rapproche })
                TextButton(onClick = { motif = ""; annulerVir = true }) { Text("Annuler le virement", color = Couleurs.Erreur) }
            if (d.peut("saisir_ecritures") && demande == null && e.virement == null && e.sens == "depense" && e.montant > 0 && e.contrepasseDe == null && correction == null && !e.rapproche)
                FilledTonalButton(onClick = {
                    scope.launch { try { Repo.demanderValidation(e.id); message("Dépense envoyée au président pour validation"); onFini(true) } catch (x: Exception) { message(traduireErreur(x)) } }
                }) { Text("Faire valider") }
            if (d.peut("saisir_ecritures", "payer_depenses") && correction == null && e.contrepasseDe == null)
                FilledTonalButton(onClick = choix) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Joindre une pièce") }
        }
    }
    if (confirmer || annulerVir) AlertDialog(
        onDismissRequest = { confirmer = false; annulerVir = false },
        title = { Text(if (annulerVir) "Annuler ce virement$NBSP?" else "Contre-passer cette opération$NBSP?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (annulerVir) "Les deux écritures de « ${e.libelle} » (${euros(e.montant)}) sont contre-passées à la date du jour. Elles restent dans l’historique."
                     else "${e.libelle}, ${euros(e.montant)}. Une opération de ${euros(-e.montant)} est créée à la date du jour ; l’originale reste dans l’historique, reliée à sa correction.")
                OutlinedTextField(motif, { motif = it.take(if (annulerVir) 60 else 100) }, label = { Text("Motif (obligatoire)") },
                    placeholder = { Text("Erreur de montant, doublon…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !enCours && motif.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Erreur), onClick = {
                enCours = true
                scope.launch {
                    try {
                        if (annulerVir) { Repo.annulerVirement(e.virement!!, motif.trim()); message("Virement annulé") }
                        else { Repo.contrePasser(e, d.profil.id, motif.trim()); message("Opération contre-passée") }
                        confirmer = false; annulerVir = false; onFini(true)
                    } catch (x: Exception) { enCours = false; message(traduireErreur(x)) }
                }
            }) { Text(if (annulerVir) "Annuler le virement" else "Contre-passer") }
        },
        dismissButton = { TextButton(onClick = { confirmer = false; annulerVir = false }) { Text("Garder") } },
    )
}

@Composable
private fun EvenementHistorique(quand: String, quoi: String, onVoir: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Couleurs.Texte2, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (quand.isNotBlank()) Text(quand, fontSize = 12.sp, color = Couleurs.Texte2)
            Text(quoi, fontSize = 14.sp)
        }
        if (onVoir != null) TextButton(onClick = onVoir) { Text("Voir") }
    }
}

// Virement interne : dépôt d'espèces à la banque, retrait pour la caisse, virement entre deux comptes.
// Deux écritures liées : les soldes bougent, les recettes et dépenses non.
@Composable
fun FormulaireVirement(d: Donnees, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf((d.comptes.firstOrNull { it.type == "caisse" } ?: d.comptes.first()).id) }
    var dest by remember { mutableStateOf((d.comptes.firstOrNull { it.type == "banque" } ?: d.comptes.last()).id) }
    var montant by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var libelle by remember { mutableStateOf("") }
    var bordereau by remember { mutableStateOf<Fichier?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val s = d.comptes.first { it.id == source }; val dd = d.comptes.first { it.id == dest }
    val nature = when { s.type == "caisse" && dd.type == "banque" -> "Dépôt d’espèces à la banque"; s.type == "banque" && dd.type == "caisse" -> "Retrait d’espèces pour la caisse"; else -> "Virement entre comptes" }
    val m = lireMontant(montant); val dt = dateDepuisFr(date)
    val choix = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) bordereau = f }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Virement interne", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Pour un dépôt d’espèces à la banque, un retrait pour la caisse ou un virement entre deux comptes de l’association. Ce n’est ni une recette ni une dépense$NBSP: seuls les soldes changent.",
            color = Couleurs.Texte2, fontSize = 14.sp)
        ChoixListe("De", s.nom, d.comptes.map { it.nom }) { source = d.comptes[it].id }
        ChoixListe("Vers", dd.nom, d.comptes.map { it.nom }) { dest = d.comptes[it].id }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(montant, { montant = it }, label = { Text("Montant (€)") }, singleLine = true, isError = montant.isNotEmpty() && (m == null || m <= 0),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(date, { date = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dt == null, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(libelle, { libelle = it.take(120) }, label = { Text("Libellé") }, placeholder = { Text(nature) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = choix, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AttachFile, null); Spacer(Modifier.width(8.dp)); Text(bordereau?.let { "Bordereau joint (${it.ko}$NBSP" + "Ko)" } ?: "Joindre le bordereau ou le ticket")
        }
        Surface(color = Color(0xFFEFEDEC), shape = RoundedCornerShape(12.dp)) {
            Text(if (source == dest) "Choisissez deux comptes différents." else "$nature$NBSP: −${euros(m ?: 0.0)} sur « ${s.nom} », +${euros(m ?: 0.0)} sur « ${dd.nom} ».",
                Modifier.padding(12.dp), fontSize = 14.sp, color = if (source == dest) Couleurs.Erreur else Couleurs.Texte)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && source != dest && m != null && m > 0 && dt != null, onClick = {
                enCours = true
                scope.launch {
                    try { Repo.virementInterne(dt.toString(), source, dest, m!!, libelle.trim().ifBlank { null }, bordereau, d.profil.id); onFini(true) }
                    catch (x: Exception) { enCours = false; message(traduireErreur(x)) }
                }
            }) { Text("Enregistrer le virement") }
        }
    }
}

val MODES = mapOf("especes" to "Espèces", "virement" to "Virement", "autre" to "Chèque ou carte")

// Aperçu des pièces jointes et de la signature ; touchez une pièce pour l'ouvrir en grand
@Composable
internal fun PiecesVue(pieces: List<Piece>, signature: String?) {
    val ouvrir = rememberOuvrirFichier()
    if (pieces.isEmpty()) {
        Surface(color = Color(0xFFEFEDEC), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Aucune pièce jointe", Modifier.padding(14.dp), color = Couleurs.Texte2, fontSize = 14.sp)
        }
    }
    pieces.forEachIndexed { i, p ->
        ApercuFichier(if (pieces.size > 1) "Pièce ${i + 1}" else "Pièce jointe", "justificatifs", p.chemin, p.mime ?: "image/jpeg", ouvrir)
    }
    signature?.let { ApercuFichier("Signature de validation", "signatures", it, "image/png", ouvrir, hauteur = 110) }
}

@Composable
private fun ApercuFichier(titre: String, bucket: String, chemin: String, mime: String, ouvrir: (String, String, ByteArray) -> Unit, hauteur: Int = 240) {
    var octets by remember(chemin) { mutableStateOf<ByteArray?>(null) }
    var image by remember(chemin) { mutableStateOf<ImageBitmap?>(null) }
    var echec by remember(chemin) { mutableStateOf(false) }
    LaunchedEffect(chemin) {
        try {
            val o = Repo.telecharger(bucket, chemin)
            octets = o
            if (o == null) echec = true
            else if (!mime.contains("pdf")) { image = imageDepuisOctets(o); if (image == null) echec = true }
        } catch (_: Exception) { echec = true }
    }
    val nom = chemin.substringAfterLast('/')
    val img = image
    Text(titre, fontSize = 13.sp, color = Couleurs.Texte2)
    when {
        echec -> Text("Fichier indisponible", color = Couleurs.Erreur, fontSize = 14.sp)
        mime.contains("pdf") -> OutlinedButton(enabled = octets != null, onClick = { octets?.let { ouvrir(nom, "application/pdf", it) } },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Ouvrir le PDF")
        }
        img != null -> Image(img, titre, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(hauteur.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                .clickable { octets?.let { ouvrir(nom, mime, it) } })
        else -> LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

