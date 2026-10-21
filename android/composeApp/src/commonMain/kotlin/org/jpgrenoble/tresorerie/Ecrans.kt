package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
    var maCot by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var mesParts by remember { mutableStateOf<List<Participation>>(emptyList()) }
    var pas by remember { mutableStateOf(1) }
    var delai by remember { mutableStateOf(7) }
    val voitSoldes = d.peut("consulter_finances")
    var nbComptes by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        if (d.peut("administrer")) try { nbComptes = Repo.profilsComplets().size + Repo.invitations().size } catch (_: Exception) { }
    }
    LaunchedEffect(Synchro.version) {
        try {
            anniv = Repo.anniversaires()
            val reglages = try { Repo.reglages() } catch (_: Exception) { emptyMap() }
            pas = (reglages["cotisation_periode_mois"] ?: 1.0).toInt()
            delai = (reglages["delai_justificatif_jours"] ?: 7.0).toInt()
            if (d.profil.memberId != null) {
                maCot = try { Repo.maCotisation() } catch (_: Exception) { emptyList() }
                mesParts = try { Repo.mesParticipations() } catch (_: Exception) { emptyList() }
            }
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
    // Mêmes lignes et mêmes boutons que le site : Voir (régulariser, retard), Valider, Payer, Joindre, Voir le budget
    val taches = buildList {
        if (d.peut("saisir_ecritures") && aRegul.isNotEmpty()) add(TacheAccueil("${aRegul.size} dépense${s(aRegul.size)} refusée${s(aRegul.size)} après paiement",
            "À régulariser$NBSP: ${euros(aRegul.sumOf { it.montant })}", "depenses", true, "Voir", filtre = "a_regulariser"))
        if (retardsActifs.isNotEmpty()) add(TacheAccueil("${retardsActifs.size} justificatif${s(retardsActifs.size)} en retard",
            retardsActifs.joinToString(", ") { "${it.objet} (${euros(it.montant)}, ${it.jours}$NBSP" + "jours)" }, "depenses", true, "Voir"))
        if (d.peut("valider_depenses") && aValider.isNotEmpty()) add(TacheAccueil("${aValider.size} demande${s(aValider.size)} à valider", euros(aValider.sumOf { it.montant }), "depenses", false, "Valider", primaire = true))
        if (d.peut("payer_depenses") && aPayer.isNotEmpty()) add(TacheAccueil("${aPayer.size} demande${s(aPayer.size)} à payer", euros(aPayer.sumOf { it.montant }), "depenses", false, "Payer", primaire = true))
        if (aJustifier.isNotEmpty()) add(TacheAccueil("${aJustifier.size} justificatif${s(aJustifier.size)} à joindre", "Délai de $delai${NBSP}jours après paiement", "depenses", false, "Joindre"))
        if (alertes.isNotEmpty()) add(TacheAccueil("${alertes.size} poste${s(alertes.size)} de budget en alerte", alertes.joinToString(", ") { it.categorie }, "budget", false, "Voir le budget"))
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
    val rubriques = buildList {
        if (d.profil.memberId != null) add("masituation")
        if (taches.isNotEmpty()) add("traiter")
        if (voitSoldes) add("indicateurs")
        if (voitSoldes && operations.isNotEmpty()) addAll(listOf("evolution", "repartition"))
        add("anniversaires")
        if (voitSoldes) add("operations")
    }
    val toutOuvert = rubriques.all { EtatAccueil.ouvertes[it] ?: true }

    // Grand écran (comme le site au-delà de 900 px) : chiffres sur trois colonnes, graphiques et dernières rubriques sur deux
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val large = maxWidth >= 840.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        erreur?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Banniere(d, onReglages = { onAller("parametres") }, onCloche = { onAller("nouveautes") }) {
                if (voitSoldes) {
                    Column(Modifier.clip(Formes.Moyen).clickable { onAller("operations:") }) {
                        Text("Trésorerie au ${dateFr(jour)}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (soldes == null) LinearProgressIndicator(Modifier.width(160.dp).padding(vertical = 14.dp), color = Color.White)
                        else {
                            // Le solde « monte » jusqu'à sa valeur à l'ouverture
                            val anime = remember { androidx.compose.animation.core.Animatable(0f) }
                            LaunchedEffect(total) { anime.animateTo(total.toFloat(), androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                            Text(euros(anime.value.toDouble()), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                    // Résultat, réserve et cotisations : toujours affichés, comme sur le site (« Non définie » sans dépenses)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val m = if (large) Modifier else Modifier.weight(1f)
                        PuceBanniere("Résultat $an", signe(resultat), m)
                        PuceBanniere("Réserve", reserveTxt, m)
                        if (exigible > 0) PuceBanniere("Cotisations", "${kotlin.math.round(100 * encaisse / exigible).toInt()}$NBSP%", m)
                    }
                }
            }
        }
        soldes?.takeIf { it.isNotEmpty() }?.chunked(if (large) 4 else 2)?.forEach { rang ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rang.forEach { c -> CarteCompte(c, Modifier.weight(1f)) { onAller("operations:${c.id}") } }
                    if (!large && rang.size == 1) Spacer(Modifier.weight(1f))
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
        if (d.profil.memberId != null) item {
            val retardMoi = retardDe(maCot)
            // Ouverte d'office seulement quand la cotisation est en retard (site : ouverte: retard > 0)
            Rubrique("masituation", "Ma situation", if (retardMoi > 0.005) "Cotisation : ${euros(retardMoi)} en retard" else "Cotisation à jour", alerte = false, ouverteParDefaut = retardMoi > 0.005) {
                BlocMaCotisation(maCot, pas)
                if (mesParts.isNotEmpty()) { Text("Mes participations", fontWeight = FontWeight.Bold); ListeParticipations(mesParts) }
            }
        }
        if (taches.isNotEmpty()) item {
            Rubrique("traiter", "À traiter", "${taches.size} action${s(taches.size)}", alerte = retardsActifs.isNotEmpty() || aRegul.isNotEmpty()) {
                taches.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.titre, fontWeight = FontWeight.SemiBold, color = if (t.alerte) Couleurs.Erreur else Couleurs.Texte)
                            Text(t.detail, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val aller = { t.filtre?.let { FiltreDemandesAccueil.cible = it }; onAller(t.cible) }
                        if (t.primaire) Button(onClick = aller, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(t.bouton) }
                        else OutlinedButton(onClick = aller, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(t.bouton) }
                    }
                }
            }
        }
        if (voitSoldes && operations.isEmpty()) item {
            Rubrique("indicateurs", "Chiffres $an", "Aucune opération") {
                Text("Les chiffres et les graphiques apparaissent dès la première opération.", color = Couleurs.Texte2)
                if (d.peut("saisir_ecritures")) Button(onClick = { onAller("operations:") }) { Text("Nouvelle opération") }
            }
        }
        if (voitSoldes && operations.isNotEmpty()) {
            item {
                Rubrique("indicateurs", "Chiffres $an", "Recettes ${euros0(rec)} · Dépenses ${euros0(dep)}") {
                    val carteCotis: @Composable (Modifier) -> Unit = { m -> Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BORD_CARTE), modifier = m) {
                        if (exigible > 0) Jauge("Cotisations encaissées", encaisse, exigible,
                            "${euros0(encaisse)} sur ${euros0(exigible)} · ${cotis.count { it.statut == "a_jour" }} membres à jour sur ${cotis.size}", Modifier.padding(14.dp).fillMaxWidth())
                        else Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text("Cotisations", fontSize = 12.sp, color = Couleurs.Texte2); Text("–", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        Tuile("Recettes $an", euros0(rec), variation(rec, flux(n1, "recette")), CouleursGraph.Recette, Modifier.weight(1f).fillMaxHeight())
                        Tuile("Dépenses $an", euros0(dep), variation(dep, flux(n1, "depense")), CouleursGraph.Depense, Modifier.weight(1f).fillMaxHeight())
                        if (large) carteCotis(Modifier.weight(1f).fillMaxHeight())
                    }
                    if (!large) carteCotis(Modifier.fillMaxWidth())
                }
            }
            if (sansMouvementNet) item {
                Rubrique("evolution", "Évolution sur 12 mois", "Aucun mouvement net") {
                    Text("Aucune opération nette sur les 12 derniers mois$NBSP: les opérations saisies ont été annulées par contre-passation. Elles restent visibles dans Opérations.", color = Couleurs.Texte2)
                }
            }
            if (!sansMouvementNet) item {
                Rubrique("evolution", "Évolution sur 12 mois", "Trésorerie ${signe(ecart12)}") {
                    DeuxColonnes(large, {
                        Text("Recettes et dépenses par mois", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        GraphColonnes(serie)
                    }) {
                        Text("Trésorerie en fin de mois", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        GraphSolde(serie)
                    }
                }
            }
            if (!sansMouvementNet) item {
                val depCat = repartition("depense")
                Rubrique("repartition", "Répartition $an", depCat.firstOrNull()?.let { "Premier poste de dépense$NBSP: ${it.nom}" } ?: "") {
                    DeuxColonnes(large, {
                        Text("Origine des recettes", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        GraphAnneau(repartition("recette"), "recette")
                    }) {
                        Text("Destination des dépenses", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        GraphAnneau(depCat, "depense")
                    }
                }
            }
        }
        item {
            val anniversaires: @Composable () -> Unit = { Rubrique("anniversaires", "Anniversaires ${deMois(mois)}", if (anniv.isEmpty()) "Aucun" else "${anniv.size} personne${s(anniv.size)}") {
                ListeAnniversaires(anniv, mois)
            } }
            val dernieresOperations: @Composable () -> Unit = { Rubrique("operations", "Dernières opérations", "") {
                val dernieres = operations.sortedWith(compareByDescending<Ecriture> { it.date }.thenByDescending { it.creeLe ?: "" }).take(5)
                if (dernieres.isEmpty()) Text("Aucune opération.", color = Couleurs.Texte2)
                dernieres.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(e.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${dateFr(e.date)} · ${d.nomCategorie(e.categorieId)}", fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                        val v = e.signe
                        Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)), color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (dernieres.isNotEmpty()) TextButton(onClick = { onAller("operations:") }) { Text("Toutes les opérations") }
            } }
            if (large && voitSoldes) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { anniversaires() }
                Box(Modifier.weight(1f)) { dernieresOperations() }
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                anniversaires()
                if (voitSoldes) dernieresOperations()
            }
        }
    }
    }
}

// Ligne de la rubrique « À traiter » : bouton du site (Voir, Valider, Payer, Joindre, Voir le budget)
private data class TacheAccueil(val titre: String, val detail: String, val cible: String, val alerte: Boolean, val bouton: String,
                                val primaire: Boolean = false, val filtre: String? = null)

/** Filtre de la page Demandes demandé depuis l'accueil (« À régulariser ») : lu une fois à l'ouverture des Demandes. */
object FiltreDemandesAccueil {
    var cible: String? = null
    fun prendre(): String? = cible.also { cible = null }
}

internal val BORD_CARTE = Color(0xFFE6E1DE)

// Deux graphiques côte à côte sur grand écran, l'un sous l'autre sur téléphone (grille-2 du site)
@Composable
private fun DeuxColonnes(large: Boolean, gauche: @Composable ColumnScope.() -> Unit, droite: @Composable ColumnScope.() -> Unit) {
    if (large) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = gauche)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = droite)
    } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = gauche)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = droite)
    }
}

// Bannière : photo de l'association si elle existe, sinon aplat neutre ; voile sombre pour la lisibilité
@Composable
// expressif : page des membres (Material 3 Expressive) — grands arrondis, aplat vivant aux couleurs de l'association sans photo
internal fun Banniere(d: Donnees, onReglages: (() -> Unit)? = null, onCloche: (() -> Unit)? = null, compacte: Boolean = false, expressif: Boolean = true, pageMembre: Boolean = false, contenu: @Composable ColumnScope.() -> Unit) {
    val octets by Repo.banniere.collectAsState()
    val photo = remember(octets) { octets?.let { imageDepuisOctets(it) } }
    val forme = if (expressif) Formes.M3XL else Formes.XGrand
    // Page des membres avec photo : bannière plus haute (80 % de la largeur, entre 320 et 440, 420 sur grand écran),
    // cadrée sur les visages, annonces en bas ; sans photo : hauteur de son contenu (comme le site)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Accueil du bureau : 200 au moins (comme .banniere du site)
    val hauteur = when { compacte -> 0.dp; pageMembre && photo != null -> if (maxWidth >= 700.dp) 420.dp else (maxWidth * 0.8f).coerceIn(320.dp, 440.dp)
        pageMembre -> 0.dp; else -> 200.dp }
    Box(Modifier.fillMaxWidth().heightIn(min = hauteur).then(if (expressif) Modifier.shadow(8.dp, forme, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.3f)) else Modifier).clip(forme)
        .background(if (expressif) FondBanniereMembre else Brush.radialGradient(listOf(Couleurs.Texte2, Color(0xFF2B2826), Couleurs.Texte), center = Offset(2000f, 0f), radius = 2200f))) {
        // Formes douces en fond (comme les dégradés ronds du site)
        if (expressif && photo == null) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-50).dp).size(220.dp).background(Color(0x8CFFB394), CircleShape))
            Box(Modifier.align(Alignment.BottomStart).offset(x = (-40).dp, y = 60.dp).size(170.dp).background(Color(0x61FF8A5B), CircleShape))
        }
        if (photo != null) {
            Image(photo, null, contentScale = ContentScale.Crop, alignment = if (expressif) androidx.compose.ui.BiasAlignment(0f, -0.24f) else Alignment.Center, modifier = Modifier.matchParentSize())
            // Voile neutre seulement en haut (titre) et en bas (annonces), la photo reste claire au milieu
            Box(Modifier.matchParentSize().background(if (expressif) Brush.verticalGradient(0f to Color(0x80000000), 0.3f to Color.Transparent, 0.52f to Color.Transparent, 1f to Color(0x9E000000))
                else Brush.verticalGradient(listOf(Color(0x40141211), Color(0x8C141211), Color(0xD1141211)))))
        }
        Column(Modifier.heightIn(min = hauteur).padding(20.dp).fillMaxWidth(), verticalArrangement = if (photo != null || hauteur > 0.dp) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White)) { LogoAsso(Modifier.fillMaxSize()) }
                Column(Modifier.weight(1f)) {
                    Text(d.organisation.nom, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Bonjour ${d.membres.firstOrNull { it.id == d.profil.memberId }?.prenom ?: d.profil.nom.substringBefore('@').substringBefore(' ')} · ${dateFr(aujourdhui().toString())}", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Cloche des nouveautés et roue dentée des Paramètres, posées sur la photo (l'accueil n'a pas de barre du haut)
                if (onCloche != null) BoutonCloche(onCloche, clair = true)
                if (onReglages != null) Surface(onClick = onReglages, color = Color.White.copy(alpha = 0.16f), contentColor = Color.White, shape = Formes.Moyen,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Settings, contentDescription = "Paramètres") }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { contenu() }
        }
    }
    }
}

@Composable
private fun PuceBanniere(titre: String, valeur: String, modifier: Modifier = Modifier) =
    Surface(color = Color.White.copy(alpha = 0.14f), contentColor = Color.White, shape = Formes.Moyen,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(titre, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }

@Composable
private fun CarteCompte(c: Solde, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand,
        border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), modifier = modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Couleurs.Surface2, CircleShape), contentAlignment = Alignment.Center) {
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
    Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand, border = androidx.compose.foundation.BorderStroke(1.dp, BORD_CARTE), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titre, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = couleur, maxLines = 1)
            Text(detail, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

// Anniversaires du mois (même liste que listeAnniversaires() du site) : Aujourd'hui, Passé
@Composable
internal fun ListeAnniversaires(anniv: List<Anniversaire>, mois: Int) {
    val jour = aujourdhui().dayOfMonth
    if (anniv.isEmpty()) Text("Aucun anniversaire ce mois-ci.", color = Couleurs.Texte2)
    anniv.forEach { a ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            Avatar(a.prenom, a.nom)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${a.prenom} ${a.nom}", fontWeight = FontWeight.SemiBold)
                Text("${a.jour} ${MOIS[mois - 1]}", fontSize = 13.sp, color = Couleurs.Texte2)
            }
            if (a.jour == jour) Pastille("Aujourd’hui", Couleurs.JauneClair, Couleurs.SurJaune)
            else if (a.jour < jour) Pastille("Passé", Couleurs.Surface2, Couleurs.Texte2)
        }
    }
}

// Rubrique dépliable : un appui sur l'en-tête ouvre ou ferme ; repliée, elle montre un résumé
@Composable
internal fun Rubrique(id: String, titre: String, resume: String, alerte: Boolean = false, ouverteParDefaut: Boolean = true,
                      accent: Color? = null, icone: androidx.compose.ui.graphics.vector.ImageVector? = null, forcee: Boolean = false, halo: Boolean = false,
                      teinteIcone: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val ouverte = forcee || (EtatAccueil.ouvertes[id] ?: ouverteParDefaut)
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (ouverte) 180f else 0f, glisse())
    // Halo à l'arrivée d'une nouveauté : deux pulsations douces autour de la carte (comme le site)
    val pulsation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(halo) { if (halo) repeat(2) { kotlinx.coroutines.delay(400); pulsation.animateTo(1f, androidx.compose.animation.core.tween(640)); pulsation.animateTo(0f, androidx.compose.animation.core.tween(960)) } }
    // Carte blanche à grands arrondis cernée d'un filet doux (comme details.rubrique du site)
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, shadowElevation = 1.dp,
        border = if (accent != null && pulsation.value > 0f) androidx.compose.foundation.BorderStroke(1.dp + 4.dp * pulsation.value, accent.copy(alpha = 0.35f * pulsation.value))
            else androidx.compose.foundation.BorderStroke(1.dp, Couleurs.BordDoux)) {
        // Alerte : filet rouge à gauche (rub-alerte du site), comme le filet de couleur d'une rubrique à accent
        val filet = if (alerte) Couleurs.Erreur else accent
        Column(Modifier.fillMaxWidth().then(if (filet != null) Modifier.drawBehind { drawRect(filet, size = androidx.compose.ui.geometry.Size((if (alerte) 3 else 4).dp.toPx(), size.height)) } else Modifier)) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (ouverte) "Replier" else "Déplier") { EtatAccueil.ouvertes[id] = !ouverte }
                .padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icone != null) { Icon(icone, null, tint = teinteIcone ?: accent ?: Couleurs.Texte2, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(titre, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    if (!ouverte && resume.isNotBlank()) Text(resume, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(32.dp).background(Couleurs.Surface2, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ExpandMore, if (ouverte) "Replier" else "Déplier", tint = Couleurs.Texte2, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
                }
            }
            androidx.compose.animation.AnimatedVisibility(ouverte, enter = androidx.compose.animation.expandVertically(glisse()) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
                exit = androidx.compose.animation.shrinkVertically(glisse()) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180, delayMillis = 100))) {
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
    Surface(color = fond, contentColor = couleur, shape = Formes.Rond) {
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
    var exporter by remember { mutableStateOf(false) }
    val scopeOps = rememberCoroutineScope()
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    // Suppression portée par l'écran (et non par la fiche qui se ferme) : la ligne disparaît aussitôt de la liste,
    // la liste est rechargée, et « Annuler » reste proposé après la fermeture de la fiche
    val supprimerOp = rememberSuppression(message) {
        detail?.let { x -> liste = liste?.filterNot { it.id == x.id || (x.virement != null && it.virement == x.virement) } }
        detail = null; version++
    }
    LaunchedEffect(version, Synchro.version) {
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
    // Recherche sans accents sur le libellé, le tiers, la catégorie et la rubrique (comme le site), et sur le montant
    val q = sansAccentsRecherche(recherche)
    // Filtres hors sens : servent aux totaux (recettes et dépenses restent visibles)
    val base = tous.filter { e ->
        e.date in debut..fin && (compte == null || e.compteId == compte) && (categorie == null || e.categorieId == categorie) &&
            (rubrique == null || (if (rubrique == "cotisation") e.estCotisation else e.collecteId == rubrique)) &&
            (!sansPiece || (e.sens == "depense" && e.estFlux && e.montant > 0 && !aPiece(e) && e.contrepasseDe == null && e.id !in contrepassees)) &&
            (q.isEmpty() || sansAccentsRecherche("${e.libelle} ${nomTiers(e, d.membres, tiers)} ${d.nomCategorie(e.categorieId)} ${nomRubrique(e, collectes)}").contains(q) || montantSaisie(e.montant).contains(q))
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
    val toutEffacer = { sens = "tout"; periode = "annee"; perso = null; compte = null; categorie = null; rubrique = null; sansPiece = false; recherche = "" }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Site : bouton « Nouvelle opération » dans l'en-tête dès 640 px, bouton flottant en dessous ; totaux sur 4 colonnes dès 900 px
        val enTete = maxWidth >= 640.dp
        val quatre = maxWidth >= 840.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp)) {
            item {
                Titre("Opérations") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { exporter = true }) { Text("Exporter") }
                        if (enTete && d.peut("saisir_ecritures")) Button(onClick = { saisie = true }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nouvelle opération")
                        }
                    }
                }
            }
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
                    TextButton(onClick = toutEffacer) { Text("Tout effacer") }
                }
            }
            item {
                val resultat = recettes - depenses
                // « Solde au … » : fin de la période, jamais après aujourd'hui ; « Solde » seul pour « Tout »
                val titreSolde = if (periode == "tout") "Solde" else "Solde au ${dateFr(finSolde)}"
                val k1: @Composable RowScope.() -> Unit = { Indicateur("Recettes", euros(recettes), Couleurs.Bleu, Modifier.weight(1f)) { sens = "recette"; categorie = null } }
                val k2: @Composable RowScope.() -> Unit = { Indicateur("Dépenses", euros(depenses), Couleurs.Orange, Modifier.weight(1f)) { sens = "depense"; categorie = null } }
                val k3: @Composable RowScope.() -> Unit = { Indicateur("Résultat", euros(resultat), if (resultat < 0) Couleurs.Erreur else Couleurs.Texte, Modifier.weight(1f)) }
                val k4: @Composable RowScope.() -> Unit = { Indicateur(titreSolde, euros(solde), if (solde < 0) Couleurs.Erreur else Couleurs.Texte, Modifier.weight(1f)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    if (quatre) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { k1(); k2(); k3(); k4() }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { k1(); k2() }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { k3(); k4() }
                    }
                }
            }
            val l = liste
            val filtresActifs = pastilles.isNotEmpty() || q.isNotEmpty()
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (vues.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand, modifier = Modifier.fillMaxWidth()) {
                    Text(if (filtresActifs) "Aucune opération pour ces filtres." else "Aucune opération.", color = Couleurs.Texte2, modifier = Modifier.padding(20.dp))
                }
            }
            else {
                item { Text("${vues.size} opération${if (vues.size > 1) "s" else ""}", fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.padding(bottom = 6.dp)) }
                // La liste tient dans une carte, comme sur le site (section.carte)
                itemsIndexed(vues, key = { _, e -> e.id }) { i, e ->
                    val r = 20.dp
                    val forme = when {
                        vues.size == 1 -> RoundedCornerShape(r)
                        i == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
                        i == vues.size - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Column(Modifier.fillMaxWidth().clip(forme).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp)) {
                        LigneOperation(d, e, aPiece(e), e.id in contrepassees, nomTiers(e, d.membres, tiers), nomRubrique(e, collectes),
                            demandes.firstOrNull { it.id == e.demandeId }) { detail = e }
                        if (i < vues.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
        if (d.peut("saisir_ecritures") && !enTete) {
            BoutonPlus("Nouvelle opération", Modifier.align(Alignment.BottomEnd)) { saisie = true }
        }
    }
    if (saisie) {
        ModalBottomSheet(onDismissRequest = { saisie = false }) {
            // Le message (« Opération enregistrée », « Dépense enregistrée et envoyée au président »…) vient du formulaire
            FormulaireEcriture(d, PreEcriture(), onFini = { ok -> saisie = false; if (ok) version++ }, message,
                onVirement = if (d.comptes.size > 1) ({ saisie = false; virement = true }) else null)
        }
    }
    if (virement) {
        ModalBottomSheet(onDismissRequest = { virement = false }) {
            FormulaireVirement(d, message) { ok -> virement = false; if (ok) { message("Virement enregistré"); version++ } }
        }
    }
    // Exporter les opérations affichées : la période et les filtres actifs sont repris (choisirFormat du site)
    if (exporter) ModalBottomSheet(onDismissRequest = { exporter = false }) {
        FormatExportOperations("Exporter les opérations affichées", onPdf = {
            exporter = false
            scopeOps.launch { try {
                val (t, h) = documentHtml(d, "journal", debut.take(4).toIntOrNull()?.takeIf { it > 1000 } ?: aujourdhui().year, debut.takeIf { it > "0000" }, minOf(fin, "9999-12-31"),
                    null, compte, sens.takeIf { it != "tout" }, categorie)
                imprimer(t, h)
            } catch (e: Exception) { message(traduireErreur(e)) } }
        }) {
            exporter = false
            scopeOps.launch { try {
                // Même cellule que telechargerCsv du site : nombre sans « ,0 » inutile, virgule décimale
                fun cel(v: Any?): String { val s2 = when (v) { is Double -> nombreCsv(v); null -> ""; else -> v.toString() }; return if (s2.any { it == ';' || it == '"' || it == '\n' }) "\"" + s2.replace("\"", "\"\"") + "\"" else s2 }
                val entetes = listOf("Date", "Sens", "Libellé", "Tiers", "Rubrique", "Catégorie", "Activité", "Compte", "Mode", "Montant", "Pièce", "Rapprochée")
                val lignesCsv = vues.map { e -> listOf(dateFr(e.date),
                    if (e.virement != null) (if (e.sens == "recette") "Virement interne (entrée)" else "Virement interne (sortie)") else if (e.sens == "recette") "Recette" else "Dépense", e.libelle,
                    nomTiers(e, d.membres, tiers), nomRubrique(e, collectes), d.nomCategorie(e.categorieId), projets.firstOrNull { it.id == e.projetId }?.nom,
                    d.comptes.firstOrNull { it.id == e.compteId }?.nom, MODES[e.mode], e.signe, if (aPiece(e)) "Oui" else "Non", if (e.rapproche) "Oui" else "Non") }
                enregistrer("operations-${debut.takeIf { it > "0000" } ?: "debut"}-${minOf(fin, aujourdhui().toString())}.csv", "text/csv",
                    ("﻿" + (listOf(entetes) + lignesCsv).joinToString("\r\n") { l -> l.joinToString(";") { cel(it) } }).encodeToByteArray())
            } catch (e: Exception) { message(traduireErreur(e)) } }
        }
    }
    if (filtres) {
        ModalBottomSheet(onDismissRequest = { filtres = false }) {
            FeuilleFiltres(d, collectes, sens, periode, perso, compte, categorie, rubrique, sansPiece, onToutEffacer = { toutEffacer(); filtres = false }) { s2, p2, perso2, c2, cat2, r2, sp2 ->
                sens = s2; periode = p2; perso = perso2; compte = c2; categorie = cat2; rubrique = r2; sansPiece = sp2; filtres = false
            }
        }
    }
    detail?.let { e ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailOperation(d, e, tous, pieces, demandes, projets, profils, nomTiers(e, d.membres, tiers), nomRubrique(e, collectes), message,
                onVoir = { detail = it }, supprimerOp = supprimerOp) { change -> detail = null; if (change) version++ }
        }
    }
}

// Recherche sans accents ni majuscules (sansAccents du site)
private fun sansAccentsRecherche(s: String): String {
    val de = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿœæ"; val vers = "aaaaaaceeeeiiiinooooouuuuyyoa"
    return s.lowercase().trim().map { c -> val i = de.indexOf(c); if (i >= 0) vers[i] else c }.joinToString("")
}

// Nombre d'une cellule CSV : 15 -> « 15 », 15.5 -> « 15,5 » (String(Number) du site, virgule décimale)
private fun nombreCsv(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    return if (r == kotlin.math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString().replace('.', ',')
}

// Choix du format d'export (choisirFormat du site) : deux cartes, PDF puis Excel
@Composable
private fun FormatExportOperations(titre: String, onPdf: () -> Unit, onCsv: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        listOf(Triple("PDF", "Mise en page pour imprimer, archiver ou transmettre", onPdf), Triple("Excel", "Tableau modifiable (CSV)", onCsv)).forEach { (t, s, action) ->
            Surface(onClick = action, color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand,
                border = androidx.compose.foundation.BorderStroke(1.dp, BORD_CARTE), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t, fontWeight = FontWeight.Bold)
                    Text(s, fontSize = 14.sp, color = Couleurs.Texte2)
                }
            }
        }
    }
}

// Tous les filtres des opérations dans une seule feuille, appliqués d'un coup
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleFiltres(d: Donnees, collectes: List<Collecte>, sens0: String, periode0: String, perso0: Pair<LocalDate, LocalDate>?, compte0: String?,
                           categorie0: String?, rubrique0: String?, sansPiece0: Boolean, onToutEffacer: () -> Unit,
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
    // Sans type choisi, chaque catégorie porte son sens : « Dons (recette) »
    fun nomCat(c: Categorie) = c.nom + if (sens != "tout") "" else if (c.sens == "recette") " (recette)" else " (dépense)"
    val dDu = dateDepuisFr(du); val dAu = dateDepuisFr(au)
    val persoValide = periode != "perso" || (dDu != null && dAu != null && dAu >= dDu)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Filtrer les opérations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("tout" to "Tout", "recette" to "Recettes", "depense" to "Dépenses").forEachIndexed { i, (k, v) ->
                // Changer de type remet la catégorie à « Toutes » (comme le site)
                SegmentedButton(selected = sens == k, onClick = { sens = k; categorie = null }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(v) }
            }
        }
        ChoixListe("Période", PERIODES.first { it.first == periode }.second, PERIODES.map { it.second }) { periode = PERIODES[it].first }
        ChoixListe("Compte", d.comptes.firstOrNull { it.id == compte }?.nom ?: "Tous les comptes", listOf("Tous les comptes") + d.comptes.map { it.nom }) {
            compte = if (it == 0) null else d.comptes[it - 1].id
        }
        if (periode == "perso") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChampDate(du, { du = it }, label = { Text("Du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dDu == null, modifier = Modifier.weight(1f))
            ChampDate(au, { au = it }, label = { Text("Au") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
                isError = dAu == null || (dDu != null && dAu < dDu), modifier = Modifier.weight(1f))
        }
        ChoixListe("Catégorie", categoriesVues.firstOrNull { it.id == categorie }?.let { nomCat(it) } ?: "Toutes les catégories", listOf("Toutes les catégories") + categoriesVues.map { nomCat(it) }) {
            categorie = if (it == 0) null else categoriesVues[it - 1].id
        }
        val rubs = listOf(null to "Toutes les rubriques", "cotisation" to "Cotisations") + collectes.map { it.id to it.nom }
        ChoixListe("Rubrique", rubs.firstOrNull { it.first == rubrique }?.second ?: "Toutes les rubriques", rubs.map { it.second }) { rubrique = rubs[it].first }
        Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable { sansPiece = !sansPiece }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(sansPiece, { sansPiece = it }); Text("Seulement les dépenses sans pièce justificative")
        }
        if (!persoValide) Text("Période invalide : la fin doit suivre le début", color = Couleurs.Erreur, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onToutEffacer) { Text("Tout effacer") }
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
    Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(e.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (contrepassee || e.contrepasseDe != null) Couleurs.Texte2 else Color.Unspecified)
            Text(listOf(dateFr(e.date), tiers, rubrique.ifBlank { cat }, compte).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // État (puceEtat du site) puis pièce (trombone ou « Sans pièce ») : les deux peuvent se suivre
        when {
            demande?.statut == "soumise" -> Pastille("À valider", Couleurs.JauneClair, Couleurs.SurJaune)
            demande?.statut == "refusee" && !contrepassee -> Pastille(if (demande.regularisation) "Refusée · à régulariser" else "Refusée", Couleurs.ErreurClair, Couleurs.SurErreurClair)
            e.rapproche -> Pastille("Rapprochée", Couleurs.BleuClair, Couleurs.SurBleuClair)
            e.contrepasseDe != null -> Pastille("Correction", Couleurs.Surface2, Couleurs.Texte2)
            contrepassee -> Pastille("Annulée", Couleurs.Surface2, Couleurs.Texte2)
            e.virement != null -> Pastille("Virement interne", Couleurs.BleuClair, Couleurs.SurBleuClair)
        }
        if (aPiece) Icon(Icons.Outlined.AttachFile, contentDescription = "Pièce jointe", tint = Couleurs.Texte2, modifier = Modifier.size(18.dp))
        else if (e.sens == "depense" && e.estFlux && e.montant > 0 && e.contrepasseDe == null && !contrepassee) Pastille("Sans pièce", Couleurs.ErreurClair, Couleurs.SurErreurClair)
        Spacer(Modifier.width(2.dp))
        val v = e.signe
        Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)),
            color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Indicateur(titre: String, valeur: String, couleur: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand,
        modifier = modifier.then(if (onClick != null) Modifier.clip(Formes.XGrand).clickable(onClick = onClick) else Modifier)) {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DetailOperation(d: Donnees, e: Ecriture, toutes: List<Ecriture>, pieces: List<Piece>, demandes: List<Demande>, projets: List<Projet>,
                             profils: List<ProfilCourt>, tiers: String, rubrique: String, message: (String) -> Unit,
                             onVoir: (Ecriture) -> Unit = {}, supprimerOp: ((String, String, String) -> Unit)? = null, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmer by remember { mutableStateOf(false) }
    var annulerVir by remember { mutableStateOf(false) }
    var inventaire by remember { mutableStateOf(false) }
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
    // Suppression : de préférence portée par l'écran appelant (supprimerOp), pour que « Annuler » survive à la fermeture de la fiche
    val supprimerLocal = rememberSuppression(message) { onFini(true) }
    val supprimer = supprimerOp ?: supprimerLocal
    fun auteur(id: String?) = when (id) { null -> ""; d.profil.id -> "vous"; else -> profils.firstOrNull { it.id == id }?.nom ?: "une personne du bureau" }
    fun joindre(f: Fichier) = scope.launch {
        try { Repo.joindrePiece(e.id, f, d.profil.id); message("Pièce jointe (${f.ko}$NBSP" + "Ko)"); onFini(true) }
        catch (x: Exception) { message(traduireErreur(x)) }
    }
    val choix = rememberChoixFichier { f, err ->
        if (err != null) message(err)
        if (f != null) joindre(f)
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // En-tête : libellé et montant sur la même ligne (comme la fiche du site)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(e.libelle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val v = e.signe
            Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange)
        }
        Spacer(Modifier.height(4.dp))
        LigneInfo("Date", dateFr(e.date))
        LigneInfo("Catégorie", d.nomCategorie(e.categorieId))
        LigneInfo("Compte", compte?.nom)
        LigneInfo("Mode", (MODES[e.mode] ?: e.mode) + if (alerteMode.isNotEmpty()) " · À vérifier" else "")
        LigneInfo("Activité", projets.firstOrNull { it.id == e.projetId }?.nom)
        LigneInfo("Tiers", tiers)
        LigneInfo("Rubrique", rubrique)
        if (e.virement != null) LigneInfo("Virement interne", "${d.comptes.firstOrNull { it.id == jambes.firstOrNull { j -> j.sens == "depense" }?.compteId }?.nom ?: "?"} → " +
            "${d.comptes.firstOrNull { it.id == jambes.firstOrNull { j -> j.sens == "recette" }?.compteId }?.nom ?: "?"}, ni recette ni dépense")
        LigneInfo("Validation", demande?.let { x -> when (x.statut) {
            "soumise" -> "À valider par le président"
            "refusee" -> "Refusée" + (x.motifRefus?.let { " · $it" } ?: "")
            "annulee" -> "Demande annulée"
            else -> "Validée" + (x.valideeLe?.let { " le " + dateFr(it.take(10)) } ?: "") + if (x.regularisation) " (après paiement)" else ""
        } })
        LigneInfo("État", when { e.rapproche -> "Rapprochée, verrouillée"; e.contrepasseDe != null -> "Correction d’une autre écriture"; correction != null -> "Annulée par contre-passation"; else -> "Active" })
        LigneInfo("Référence", refOperation(e.id))
        if (alerteMode.isNotEmpty()) Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.Moyen) {
            Text(alerteMode, Modifier.padding(12.dp), fontSize = 14.sp)
        }
        // Historique : qui a fait quoi, et le lien vers l'écriture liée
        Text("Historique", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.padding(start = 4.dp).border(width = 0.dp, color = Color.Transparent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EvenementHistorique("${dateFr((e.creeLe ?: e.date).take(10))}${e.creeLe?.let { " " + it.drop(11).take(5) } ?: ""}",
                (if (e.contrepasseDe != null) "Correction saisie" else "Saisie") + (e.creePar?.let { " par ${auteur(it)}" } ?: ""))
            origine?.let { o -> EvenementHistorique(dateFr(o.date), "Corrige «$NBSP${o.libelle}$NBSP» (${refOperation(o.id)})" + motifDe(e.libelle).let { m -> if (m.isNotEmpty()) ", motif$NBSP: $m" else "" }) { onVoir(o) } }
            demande?.let { x -> EvenementHistorique(dateFr(x.creeLe.take(10)), "Demande de validation" + (x.valideeLe?.let { ", validée le ${dateFr(it.take(10))}" } ?: if (x.statut == "refusee") ", refusée" else "")) }
            // Préfixe de la correction (« contre-passation », « erreur de saisie »…) : « Préfixe : libellé » comme la base et le site
            correction?.let { c -> EvenementHistorique(dateFr(c.date), "Annulée (${c.libelle.substringBefore(SEP_MOTIF).substringBefore(" : ").substringBefore("$NBSP:").lowercase()})" +
                (c.creePar?.let { ", saisie par ${auteur(it)}" } ?: "") + motifDe(c.libelle).let { m -> if (m.isNotEmpty()) ", motif$NBSP: $m" else "" } + " (${refOperation(c.id)})") { onVoir(c) } }
            if (e.rapproche) EvenementHistorique(e.dateRapprochement?.let { dateFr(it) } ?: "", "Rapprochée avec le relevé, verrouillée")
        }
        // Pièces : seulement s'il y en a (le site n'affiche rien sinon)
        if (siennes.isNotEmpty() || demande?.signature != null) { Spacer(Modifier.height(8.dp)); PiecesVue(siennes, demande?.signature) }
        Spacer(Modifier.height(8.dp))
        // Mêmes actions, dans le même ordre que la fiche du site ; « Fermer » en dernier
        val peutCorriger = d.peut("saisir_ecritures") && !e.rapproche && e.contrepasseDe == null && correction == null
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (siennes.none { it.transactionId == e.id } && correction == null && e.sens == "depense" && e.montant > 0 && d.peut("saisir_ecritures"))
                FilledTonalButton(onClick = choix) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Joindre une pièce") }
            if (siennes.none { it.transactionId == e.id } && correction == null && e.sens == "depense" && e.montant > 0 && d.peut("saisir_ecritures"))
                BoutonScanner(message, libelle = "Scanner") { joindre(it) }
            if (demande == null && e.virement == null && peutCorriger && e.sens == "depense" && e.montant > 0)
                FilledTonalButton(onClick = {
                    scope.launch { try { Repo.demanderValidation(e.id); message("Dépense envoyée au président pour validation"); onFini(true) } catch (x: Exception) { message(traduireErreur(x)) } }
                }) { Text("Faire valider par le président") }
            if (d.peut("gerer_materiel") && e.sens == "depense" && e.estFlux && e.montant > 0 && Regex("mat[ée]riel|instrument|[ée]quipement", RegexOption.IGNORE_CASE).containsMatchIn(d.nomCategorie(e.categorieId)))
                TextButton(onClick = { inventaire = true }) { Text("Inscrire à l’inventaire") }
            if (peutCorriger && e.virement == null)
                TextButton(onClick = { motif = ""; confirmer = true }) { Text("Contre-passer") }
            if (d.peut("saisir_ecritures") && e.virement != null && !virementAnnule && e.contrepasseDe == null && jambes.none { it.rapproche })
                TextButton(onClick = { motif = ""; annulerVir = true }) { Text("Annuler le virement") }
            // Supprimer (réversible, corbeille) : saisie par erreur, ni rapprochée, ni corrigée, ni dans un exercice clôturé
            if (d.peut("saisir_ecritures") && !e.rapproche && correction == null && !(e.demandeId != null && e.contrepasseDe == null) && !d.exerciceClos(e.date) &&
                !(e.virement != null && (virementAnnule || jambes.any { it.rapproche })))
                BoutonSupprimer({ supprimer("transactions", e.id, e.libelle) })
            Button(onClick = { onFini(false) }) { Text("Fermer") }
        }
    }
    if (inventaire) ModalBottomSheet(onDismissRequest = { inventaire = false }) {
        FormulaireMateriel(null, NouveauMateriel(e.libelle, "instrument", valeurAcquisition = e.montant, valeurActuelle = e.montant, dateAcquisition = e.date, etat = "neuf", transactionId = e.id), message) { ok ->
            inventaire = false; if (ok) message("Article inscrit à l’inventaire")
        }
    }
    if (confirmer || annulerVir) AlertDialog(
        onDismissRequest = { confirmer = false; annulerVir = false },
        title = { Text(if (annulerVir) "Annuler ce virement$NBSP?" else "Contre-passer cette opération$NBSP?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (annulerVir) "Les deux écritures de «$NBSP${e.libelle}$NBSP» (${euros(e.montant)}) sont contre-passées à la date du jour. Elles restent dans l’historique."
                     else "Une opération de −${euros(e.montant)} annule «$NBSP${e.libelle}$NBSP» à la date du jour. L’opération d’origine reste dans l’historique, reliée à sa correction.")
                OutlinedTextField(motif, { motif = it.take(if (annulerVir) 60 else 100) }, label = { Text("Motif (obligatoire)") },
                    placeholder = { Text(if (annulerVir) "Erreur de montant, doublon…" else "Erreur de montant, doublon, dépense refusée…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
        dismissButton = { TextButton(onClick = { confirmer = false; annulerVir = false }) { Text(if (annulerVir) "Garder" else "Annuler") } },
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
            // Date jamais dans le futur (max du site)
            ChampDate(date, { date = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dt == null || dt > aujourdhui(), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(libelle, { libelle = it.take(120) }, label = { Text("Libellé") }, placeholder = { Text(nature) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Bordereau ou ticket", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        ChoixPiece(bordereau, message, choix) { bordereau = it }
        Surface(color = Couleurs.Surface2, shape = Formes.Moyen) {
            Text(if (source == dest) "Choisissez deux comptes différents." else "$nature$NBSP: −${euros(m ?: 0.0)} sur « ${s.nom} », +${euros(m ?: 0.0)} sur « ${dd.nom} ».",
                Modifier.padding(12.dp), fontSize = 14.sp, color = if (source == dest) Couleurs.Erreur else Couleurs.Texte)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && source != dest && m != null && m > 0 && dt != null && dt <= aujourdhui(), onClick = {
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
        Surface(color = Couleurs.Surface2, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
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
            modifier = Modifier.fillMaxWidth().height(hauteur.dp).clip(Formes.Moyen).background(Color.White)
                .clickable { octets?.let { ouvrir(nom, mime, it) } })
        else -> LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

