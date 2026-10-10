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

// =====================================================================
// Couleurs des rendez-vous : même palette et même calcul que le site (somme des codes de l'identifiant)
// =====================================================================
val EVT_PALETTE = listOf(
    Triple(Color(0xFFFFE1D6), Color(0xFF8A2A06), Color(0xFFC23E10)), Triple(Color(0xFFD7ECFA), Color(0xFF0B4A73), Color(0xFF1B77B0)),
    Triple(Color(0xFFDDF3E2), Color(0xFF145C2C), Color(0xFF2E8B4E)), Triple(Color(0xFFEBE3FA), Color(0xFF4A2C86), Color(0xFF7048B8)),
    Triple(Color(0xFFFCE0EC), Color(0xFF86184A), Color(0xFFC2367A)), Triple(Color(0xFFD4F1F0), Color(0xFF0D5653), Color(0xFF1B8A85)),
    Triple(Color(0xFFFFF0C7), Color(0xFF6B4A00), Color(0xFFC58A00)),
)
fun indiceCouleur(id: String) = id.sumOf { it.code } % EVT_PALETTE.size
fun couleurEvt(id: String) = EVT_PALETTE[indiceCouleur(id)]

// « Dans 3 jours », « Demain », « Aujourd'hui »
fun dansJours(date: String): String {
    val j = kotlinx.datetime.LocalDate.parse(date.take(10)).toEpochDays() - aujourdhui().toEpochDays()
    return when { j < 0 -> "En cours"; j == 0 -> "Aujourd’hui"; j == 1 -> "Demain"; else -> "Dans $j jours" }
}

// =====================================================================
// Nouveautés : pastilles sur les onglets, cloche, notification du téléphone
// =====================================================================
val SECTIONS_NOUVEAUTES = listOf("ecritures", "depenses", "cotisations", "activites", "membres")
val LIBELLES_SECTIONS = mapOf("ecritures" to "Opérations", "depenses" to "Demandes", "cotisations" to "Cotisations", "activites" to "Planning", "membres" to "Membres")

object EtatNouveautes {
    val n = mutableStateOf(Nouveautes())
    private var dernier: String? = null
    // Recharge ; prévient par une notification du téléphone si un élément plus récent est arrivé
    suspend fun charger() {
        try {
            val r = Repo.nouveautes()
            val recent = r.elements.firstOrNull()
            if (dernier != null && recent != null && recent.quand > dernier!!) notifierSysteme(recent.titre, recent.detail)
            dernier = recent?.quand ?: dernier ?: ""
            n.value = r
        } catch (_: Exception) { }
    }
    suspend fun vu(section: String) {
        if (section !in SECTIONS_NOUVEAUTES) return
        try { Repo.marquerVu(section); charger() } catch (_: Exception) { }
    }
    fun compte(section: String) = n.value.compteurs[section] ?: 0
}

@Composable
fun IconeAvecPastille(icone: ImageVector, nombre: Int, description: String?) {
    BadgedBox(badge = { if (nombre > 0) Badge(containerColor = Couleurs.Erreur, contentColor = Color.White) { Text(if (nombre > 99) "99+" else "$nombre") } }) {
        Icon(icone, contentDescription = description)
    }
}

// Cloche : sa pastille compte toutes les nouveautés ; elle bouge un instant quand il y en a
@Composable
fun BoutonCloche(onClick: () -> Unit, clair: Boolean = false) {
    val total = EtatNouveautes.n.value.total
    var secoue by remember { mutableStateOf(false) }
    LaunchedEffect(total) { if (total > 0) { secoue = true; kotlinx.coroutines.delay(600); secoue = false } }
    val echelle by animateFloatAsState(if (secoue) 1.18f else 1f)
    IconButton(onClick = onClick, colors = if (clair) IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White) else IconButtonDefaults.iconButtonColors()) {
        Box(Modifier.scale(echelle)) { IconeAvecPastille(Icons.Outlined.Notifications, total, "Nouveautés") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNouveautes(onAller: (String) -> Unit, onFermer: () -> Unit) {
    val els = EtatNouveautes.n.value.elements
    val scope = rememberCoroutineScope()
    var autorise by remember { mutableStateOf(notificationsPermises()) }
    val demander = rememberDemandeNotifications { autorise = it }
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nouveautés", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (els.isEmpty()) Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2E8B4E), modifier = Modifier.size(36.dp))
                Text("Rien de nouveau. Vous êtes à jour.", color = Couleurs.Texte2)
            }
            els.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAller(e.section); onFermer() }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (fond, encre) = couleursSection(e.section)
                    Box(Modifier.size(40.dp).clip(CircleShape).background(fond), contentAlignment = Alignment.Center) { Icon(iconeSection(e.section), null, tint = encre) }
                    Column(Modifier.weight(1f)) {
                        Text(e.titre, fontWeight = FontWeight.SemiBold)
                        Text(e.detail, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(ilYaTexte(e.quand), fontSize = 12.sp, color = Couleurs.Texte2)
                }
            }
            if (!autorise) Text("Pour recevoir une notification du téléphone : Paramètres › Notifications.", fontSize = 13.sp, color = Couleurs.Texte2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (els.isNotEmpty()) TextButton(onClick = { scope.launch { SECTIONS_NOUVEAUTES.forEach { try { Repo.marquerVu(it) } catch (_: Exception) { } }; EtatNouveautes.charger(); onFermer() } }) { Text("Tout marquer comme vu") }
                Button(onClick = onFermer) { Text("Fermer") }
            }
        }
    }
}

private fun ilYaTexte(quand: String): String {
    val j = try { aujourdhui().toEpochDays() - kotlinx.datetime.LocalDate.parse(quand.take(10)).toEpochDays() } catch (_: Exception) { 0 }
    return when { j <= 0 -> "aujourd’hui"; j == 1 -> "hier"; else -> "il y a $j j" }
}
fun iconeSection(s: String): ImageVector = when (s) {
    "depenses" -> Icons.Outlined.Euro; "ecritures" -> Icons.Outlined.ReceiptLong; "activites" -> Icons.Outlined.Event
    "membres" -> Icons.Outlined.Groups; else -> Icons.Outlined.Payments
}
fun couleursSection(s: String): Pair<Color, Color> = when (s) {
    "depenses" -> Couleurs.OrangeClair to Couleurs.Orange; "ecritures" -> Couleurs.BleuClair to Couleurs.Bleu
    "activites" -> Color(0xFFE3F4E6) to Color(0xFF1E7A3A); "membres" -> Couleurs.JauneClair to Couleurs.SurJaune
    else -> Color(0xFFEDE4FA) to Color(0xFF5B3A9E)
}

// =====================================================================
// Suppression réversible : motif, corbeille, « Annuler » aussitôt
// =====================================================================
object Annulation { var hote: SnackbarHostState? = null }

/** Renvoie la fonction qui demande confirmation puis supprime ; « Annuler » dans le message restaure. */
@Composable
fun rememberSuppression(message: (String) -> Unit, apres: () -> Unit): (table: String, id: String, libelle: String) -> Unit {
    var cible by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val scope = rememberCoroutineScope()
    cible?.let { (table, id, libelle) ->
        var motif by remember(id) { mutableStateOf("") }
        var enCours by remember(id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cible = null },
            icon = { Icon(Icons.Outlined.Delete, null, tint = Couleurs.Erreur) },
            title = { Text("Supprimer «$NBSP$libelle$NBSP»$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("L’élément part dans la corbeille (Paramètres, Corbeille) avec ses pièces jointes ; il peut être restauré à tout moment.", fontSize = 14.sp, color = Couleurs.Texte2)
                    OutlinedTextField(motif, { motif = it.take(120) }, label = { Text("Motif (facultatif)") }, placeholder = { Text("Doublon, erreur de saisie…") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = !enCours, colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Erreur), onClick = {
                    enCours = true
                    scope.launch {
                        try {
                            val idCorbeille = Repo.supprimer(table, id, motif.trim().ifBlank { null })
                            cible = null; apres(); EtatNouveautes.charger()
                            val hote = Annulation.hote
                            if (hote != null) {
                                val r = hote.showSnackbar("Supprimé, placé dans la corbeille", actionLabel = "Annuler", duration = SnackbarDuration.Long)
                                if (r == SnackbarResult.ActionPerformed) try { Repo.restaurer(idCorbeille); apres(); message("Restauré") } catch (e: Exception) { message(traduireErreur(e)) }
                            } else message("Supprimé, placé dans la corbeille")
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { cible = null }) { Text("Annuler") } },
        )
    }
    return { table, id, libelle -> cible = Triple(table, id, libelle) }
}

@Composable
fun BoutonSupprimer(onClick: () -> Unit, texte: String = "Supprimer") =
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = Couleurs.Erreur)) {
        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(texte)
    }

// =====================================================================
// Arrivée d'un nouveau membre : il remplit sa fiche
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleMaFiche(d: Donnees, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val morceaux = if (d.profil.nom.contains('@')) listOf("") else d.profil.nom.split(' ')
    var prenom by remember { mutableStateOf(morceaux.first()) }
    var nom by remember { mutableStateOf(morceaux.drop(1).joinToString(" ")) }
    var jour by remember { mutableStateOf<Int?>(null) }
    var mois by remember { mutableStateOf<Int?>(null) }
    var whatsapp by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var accord by remember { mutableStateOf(true) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { onFini(false) }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Couleurs.Orange, modifier = Modifier.size(30.dp))
                Column {
                    Text("Bienvenue${if (prenom.isNotBlank()) " $prenom" else ""}$NBSP!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Complétez votre fiche de membre : elle permet au trésorier de suivre votre cotisation et à l’association de fêter votre anniversaire.", color = Couleurs.Texte2, fontSize = 14.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom *") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("Anniversaire (jour et mois, sans l’année) *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ChoixListe("Jour", jour?.toString() ?: "", (1..31).map { "$it" }) { jour = it + 1 } }
                Box(Modifier.weight(1f)) { ChoixListe("Mois", mois?.let { MOIS[it - 1] } ?: "", MOIS) { mois = it + 1 } }
            }
            OutlinedTextField(whatsapp, { whatsapp = it.take(20) }, label = { Text("WhatsApp") }, placeholder = { Text("06 12 34 56 78") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(profession, { profession = it.take(80) }, label = { Text("Profession (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { accord = !accord }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accord, { accord = it }); Text("J’accepte que mon anniversaire soit affiché aux autres membres", fontSize = 14.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onFini(false) }) { Text("Plus tard") }
                Button(enabled = !enCours && prenom.isNotBlank() && nom.isNotBlank() && jour != null && mois != null, onClick = {
                    enCours = true
                    scope.launch {
                        try { Repo.enregistrerMaFiche(prenom.trim(), nom.trim(), jour!!, mois!!, whatsapp.ifBlank { null }, profession.ifBlank { null }, accord); message("Fiche enregistrée, merci$NBSP!"); onFini(true) }
                        catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }) { Text("Enregistrer ma fiche") }
            }
        }
    }
}

// =====================================================================
// Assistant de configuration (administrateur, première connexion ; relançable dans Paramètres)
// =====================================================================
private val ETAPES = listOf("Association", "Coordonnées", "Exercice", "Comptes", "Cotisation", "Terminé")

fun exerciceDe(debut: String): NouvelExercice {
    val d = kotlinx.datetime.LocalDate.parse(debut)
    val fin = d.plus(kotlinx.datetime.DatePeriod(years = 1)).minus(kotlinx.datetime.DatePeriod(days = 1))
    return NouvelExercice(if (d.monthNumber == 1) "Exercice ${d.year}" else "Exercice ${d.year}-${d.year + 1}", debut, fin.toString())
}
@Composable
fun AssistantConfiguration(d: Donnees, onFini: () -> Unit, onPlusTard: () -> Unit) {
    val messages = remember { SnackbarHostState() }
    val scopeMessages = rememberCoroutineScope()
    val message: (String) -> Unit = { m -> scopeMessages.launch { messages.showSnackbar(m) } }
    var etape by remember { mutableStateOf(0) }
    var o by remember { mutableStateOf(d.organisation) }
    var exercices by remember { mutableStateOf<List<Exercice>>(emptyList()) }
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var reglages by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var infos by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(0) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version) {
        try { o = Repo.organisation(); exercices = Repo.exercices(); comptes = Repo.tousLesComptes(); reglages = Repo.reglages(); infos = Repo.texteReglage("infos_paiement") ?: "" }
        catch (e: Exception) { message(traduireErreur(e)) }
    }
    val an = aujourdhui().year
    val ex = exercices.firstOrNull { !it.cloture }
    // Champs des étapes
    val champs = remember(o) { mutableStateMapOf("nom" to o.nom, "sigle" to (o.sigle ?: ""), "objet" to (o.objet ?: ""), "date_creation" to (o.dateCreation?.let(::dateFr) ?: ""),
        "adresse" to (o.adresse ?: ""), "code_postal" to (o.codePostal ?: ""), "ville" to (o.ville ?: ""), "email" to (o.email ?: ""), "telephone" to (o.telephone ?: ""),
        "rna" to (o.rna ?: ""), "siret" to (o.siret ?: ""), "site_web" to (o.siteWeb ?: "")) }
    var exDebut by remember(ex) { mutableStateOf(ex?.debut ?: "$an-01-01") }
    var exFin by remember(ex) { mutableStateOf(ex?.fin ?: "$an-12-31") }
    var exLibelle by remember(ex) { mutableStateOf(ex?.libelle ?: "Exercice $an") }
    val soldes = remember(comptes) { mutableStateMapOf<String, String>().apply { comptes.forEach { put(it.id, montantSaisie(it.soldeInitial)) } } }
    var nouveauCompte by remember { mutableStateOf("") }
    var nouveauType by remember { mutableStateOf("banque") }
    var cotisation by remember(reglages) { mutableStateOf(montantSaisie(reglages["cotisation_montant"] ?: 0.0)) }
    var pas by remember(reglages) { mutableStateOf((reglages["cotisation_periode_mois"] ?: 1.0).toInt()) }

    fun champ(k: String, l: String, clavier: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) =
        @Composable { OutlinedTextField(champs[k] ?: "", { champs[k] = it }, label = { Text(l) }, singleLine = k != "objet", keyboardOptions = KeyboardOptions(keyboardType = clavier), modifier = modifier) }

    fun suivant() {
        enCours = true
        scope.launch {
            try {
                val v = { k: String -> champs[k]?.trim()?.ifBlank { null } }
                when (etape) {
                    0 -> Repo.majIdentite(mapOf("nom" to (v("nom") ?: o.nom), "sigle" to v("sigle"), "objet" to v("objet"), "date_creation" to v("date_creation")?.let { dateDepuisFr(it)?.toString() }))
                    1 -> Repo.majIdentite(listOf("adresse", "code_postal", "ville", "email", "telephone", "rna", "siret", "site_web").associateWith { v(it) })
                    2 -> Repo.enregistrerExercice(ex?.id, NouvelExercice(exLibelle.trim(), exDebut, exFin))
                    3 -> {
                        comptes.forEach { c -> nombre2(soldes[c.id] ?: "")?.let { s -> if (s != c.soldeInitial) Repo.majCompte(c.id, s, c.actif) } }
                        if (nouveauCompte.isNotBlank()) { Repo.ajouterCompte(NouveauCompte(nouveauCompte.trim(), nouveauType, 0.0)); nouveauCompte = ""; version++; enCours = false; return@launch }
                    }
                    4 -> { nombre2(cotisation)?.let { Repo.majReglage("cotisation_montant", it) }; Repo.majReglage("cotisation_periode_mois", pas.toDouble()); Repo.majTexteReglage("infos_paiement", infos.trim().ifBlank { null }) }
                    5 -> { Repo.terminerConfiguration(); message("Configuration enregistrée"); onFini(); return@launch }
                }
                version++; etape++
            } catch (e: Exception) { message(traduireErreur(e)) }
            enCours = false
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Couleurs.Fond).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp)
        .fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoAsso(Modifier.size(40.dp).clip(CircleShape))
            Text("Configuration de l’association", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (etape < 5) TextButton(onClick = onPlusTard) { Text("Plus tard") }
        }
        // Étapes : faites, en cours, à venir
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ETAPES.forEachIndexed { i, t ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val echelle by animateFloatAsState(if (i == etape) 1.15f else 1f)
                    Box(Modifier.size(28.dp).scale(echelle).clip(CircleShape).background(when { i < etape -> Couleurs.Bleu; i == etape -> Couleurs.Orange; else -> Color(0xFFEFEDEC) }), contentAlignment = Alignment.Center) {
                        Text(if (i < etape) "✓" else "${i + 1}", color = if (i <= etape) Color.White else Couleurs.Texte2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (i == etape) Text(t, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        AnimatedContent(etape, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }) { e ->
            CarteBlanche {
                when (e) {
                    0 -> {
                        Text("Votre association", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Ces informations apparaissent sur les documents (rapports, PDF). Le logo se change dans Paramètres, Logo et bannière.", color = Couleurs.Texte2, fontSize = 14.sp)
                        champ("nom", "Nom de l’association *")()
                        champ("sigle", "Sigle")()
                        champ("objet", "Objet (but de l’association)")()
                        champ("date_creation", "Date de création (JJ/MM/AAAA)", KeyboardType.Number)()
                    }
                    1 -> {
                        Text("Coordonnées", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Le numéro RNA (W…) figure sur le récépissé de la préfecture ; le SIRET seulement si l’association en a un.", color = Couleurs.Texte2, fontSize = 14.sp)
                        champ("adresse", "Adresse")()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { champ("code_postal", "Code postal", KeyboardType.Number, Modifier.weight(1f))(); champ("ville", "Ville", modifier = Modifier.weight(2f))() }
                        champ("email", "E-mail", KeyboardType.Email)(); champ("telephone", "Téléphone", KeyboardType.Phone)()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { champ("rna", "N° RNA", modifier = Modifier.weight(1f))(); champ("siret", "SIRET", KeyboardType.Number, Modifier.weight(1f))() }
                        champ("site_web", "Site internet", KeyboardType.Uri)()
                    }
                    2 -> {
                        Text("Exercice comptable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Période de 12 mois sur laquelle les comptes sont arrêtés et présentés à l’assemblée générale.", color = Couleurs.Texte2, fontSize = 14.sp)
                        listOf(Triple("Année civile", "1er janvier – 31 décembre $an", "$an-01-01"), Triple("Année scolaire", "1er septembre $an – 31 août ${an + 1}", "$an-09-01")).forEach { (t, s, deb) ->
                            val choisi = exDebut == deb
                            Surface(onClick = { val x = exerciceDe(deb); exDebut = x.debut; exFin = x.fin; exLibelle = x.libelle }, shape = RoundedCornerShape(14.dp),
                                color = if (choisi) Couleurs.OrangeClair else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(if (choisi) 2.dp else 1.dp, if (choisi) Couleurs.Orange else Color(0xFFD9D3D0))) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(t, fontWeight = FontWeight.Bold); Text(s, fontSize = 13.sp, color = Couleurs.Texte2) }
                            }
                        }
                        var debutSaisi by remember(exDebut) { mutableStateOf(dateFr(exDebut)) }
                        var finSaisie by remember(exFin) { mutableStateOf(dateFr(exFin)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(debutSaisi, { debutSaisi = it; dateDepuisFr(it)?.let { dt -> val x = exerciceDe(dt.toString()); exDebut = x.debut; exFin = x.fin; exLibelle = x.libelle } },
                                label = { Text("Début") }, singleLine = true, isError = dateDepuisFr(debutSaisi) == null, modifier = Modifier.weight(1f))
                            OutlinedTextField(finSaisie, { finSaisie = it; dateDepuisFr(it)?.let { dt -> exFin = dt.toString() } }, label = { Text("Fin") }, singleLine = true,
                                isError = dateDepuisFr(finSaisie) == null, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(exLibelle, { exLibelle = it.take(40) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    3 -> {
                        Text("Comptes et soldes de départ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Le solde de chaque compte au début de l’exercice : relevé de banque et comptage de la caisse.", color = Couleurs.Texte2, fontSize = 14.sp)
                        comptes.forEach { c ->
                            OutlinedTextField(soldes[c.id] ?: "", { soldes[c.id] = it }, label = { Text("${c.nom} (${if (c.type == "caisse") "caisse" else "banque"})") }, suffix = { Text("€") },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        }
                        Text("Ajouter un compte", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(nouveauCompte, { nouveauCompte = it.take(60) }, label = { Text("Nom") }, placeholder = { Text("Livret A, Caisse des jeunes…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("banque" to "Banque", "caisse" to "Caisse").forEachIndexed { i, (k, l) -> SegmentedButton(selected = nouveauType == k, onClick = { nouveauType = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) } }
                        }
                    }
                    4 -> {
                        Text("Cotisation des membres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(cotisation, { cotisation = it }, label = { Text("Montant par période") }, suffix = { Text("€") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        ChoixListe("Périodicité", PERIODICITES[pas] ?: "", PERIODICITES.values.toList()) { pas = PERIODICITES.keys.toList()[it] }
                        OutlinedTextField(infos, { infos = it.take(400) }, label = { Text("Comment régler (affiché aux membres)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2E8B4E), modifier = Modifier.size(36.dp))
                            Column { Text("Tout est prêt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Modifiable à tout moment dans Paramètres.", color = Couleurs.Texte2, fontSize = 14.sp) }
                        }
                        LigneInfo("Association", o.nom + listOfNotNull(o.ville).joinToString("") { " · $it" })
                        LigneInfo("Exercice", ex?.let { "${it.libelle} (${dateFr(it.debut)} au ${dateFr(it.fin)})" })
                        LigneInfo("Comptes", comptes.joinToString(" · ") { "${it.nom} ${euros(it.soldeInitial)}" })
                        LigneInfo("Cotisation", "${euros(reglages["cotisation_montant"] ?: 0.0)} · ${PERIODICITES[(reglages["cotisation_periode_mois"] ?: 1.0).toInt()] ?: ""}")
                        Text("Étapes suivantes conseillées : ajouter les membres (Membres, Importer), inviter le président et le bureau (Paramètres, Personnes).", color = Couleurs.Texte2, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (etape > 0) TextButton(onClick = { etape-- }) { Text("Précédent") } else Spacer(Modifier)
            Button(enabled = !enCours && (etape != 0 || (champs["nom"] ?: "").isNotBlank()), onClick = { suivant() }, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(if (etape == 5) "Accéder à la trésorerie" else if (etape == 3 && nouveauCompte.isNotBlank()) "Ajouter le compte" else "Suivant", fontWeight = FontWeight.Bold)
            }
        }
    }
    SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private fun nombre2(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").toDoubleOrNull()

// =====================================================================
// Paramètres : sections nouvelles (exercices, corbeille, identité, catégories, sécurité, notifications)
// =====================================================================
fun statutExercice(e: Exercice): Triple<String, Color, Color> {
    val auj = aujourdhui().toString()
    return when {
        e.cloture -> Triple("Clôturé", Color(0xFFEFEDEC), Couleurs.Texte2)
        e.debut > auj -> Triple("À venir", Color(0xFFEFEDEC), Couleurs.Texte2)
        e.fin < auj -> Triple("À clôturer", Couleurs.JauneClair, Couleurs.SurJaune)
        else -> Triple("En cours", Color(0xFFDDF3E2), Color(0xFF145C2C))
    }
}

@Composable
fun ParamExercices(d: Donnees, message: (String) -> Unit) {
    var liste by remember { mutableStateOf<List<Exercice>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var edition by remember { mutableStateOf<Pair<Boolean, Exercice?>?>(null) }
    var aCloturer by remember { mutableStateOf<Exercice?>(null) }
    var aRouvrir by remember { mutableStateOf<Exercice?>(null) }
    val scope = rememberCoroutineScope()
    val supprimer = rememberSuppression(message) { version++ }
    LaunchedEffect(version) { try { liste = Repo.exercices() } catch (e: Exception) { message(traduireErreur(e)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("L’exercice regroupe 12 mois de comptes. Une fois clôturé, ses opérations ne peuvent plus être ajoutées, modifiées ni supprimées (il peut être rouvert).", color = Couleurs.Texte2, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { edition = true to null }) { Text("Nouvel exercice") }
        }
        items(liste, key = { it.id }) { e ->
            val (lib, fond, encre) = statutExercice(e)
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = Couleurs.Orange)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.libelle, fontWeight = FontWeight.Bold)
                        Text("${dateFr(e.debut)} au ${dateFr(e.fin)}" + (e.clotureLe?.let { " · clôturé le ${dateFr(it.take(10))}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    Puce(lib, fond, encre)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                    if (e.cloture) TextButton(onClick = { aRouvrir = e }) { Text("Rouvrir") }
                    else {
                        BoutonSupprimer({ supprimer("exercices", e.id, e.libelle) })
                        TextButton(onClick = { edition = false to e }) { Text("Modifier") }
                        FilledTonalButton(onClick = { aCloturer = e }) { Text("Clôturer") }
                    }
                }
            }
        }
    }
    edition?.let { (_, e) ->
        val suivant = liste.firstOrNull()?.let { exerciceDe(kotlinx.datetime.LocalDate.fromEpochDays(kotlinx.datetime.LocalDate.parse(it.fin).toEpochDays() + 1).toString()) } ?: exerciceDe("${aujourdhui().year}-01-01")
        var debut by remember(e) { mutableStateOf(dateFr(e?.debut ?: suivant.debut)) }
        var fin by remember(e) { mutableStateOf(dateFr(e?.fin ?: suivant.fin)) }
        var libelle by remember(e) { mutableStateOf(e?.libelle ?: suivant.libelle) }
        val dD = dateDepuisFr(debut); val dF = dateDepuisFr(fin)
        DialogueSimple(if (e == null) "Nouvel exercice" else "Modifier l’exercice", "Enregistrer", dD != null && dF != null && dF > dD && libelle.isNotBlank(), { edition = null }, {
            scope.launch {
                try { Repo.enregistrerExercice(e?.id, NouvelExercice(libelle.trim(), dD.toString(), dF.toString())); message(if (e == null) "Exercice créé" else "Exercice modifié"); version++ }
                catch (ex: Exception) { message(traduireErreur(ex)) }
                edition = null
            }
        }) {
            OutlinedTextField(debut, { debut = it; if (e == null) dateDepuisFr(it)?.let { dt -> val x = exerciceDe(dt.toString()); fin = dateFr(x.fin); libelle = x.libelle } },
                label = { Text("Début") }, singleLine = true, isError = dD == null)
            OutlinedTextField(fin, { fin = it }, label = { Text("Fin") }, singleLine = true, isError = dF == null || (dD != null && dF <= dD))
            OutlinedTextField(libelle, { libelle = it.take(40) }, label = { Text("Nom") }, singleLine = true)
            Text("En général 12 mois ; le premier exercice peut être plus court ou plus long.", fontSize = 13.sp, color = Couleurs.Texte2)
        }
    }
    aCloturer?.let { e ->
        // Points de contrôle avant clôture
        var points by remember(e.id) { mutableStateOf<List<Pair<Boolean, String>>?>(null) }
        LaunchedEffect(e.id) {
            try {
                val banques = d.comptes.filter { it.type == "banque" }.map { it.id }.toSet()
                val nonRappr = Repo.toutesEcritures().count { it.date in e.debut..e.fin && it.compteId in banques && it.rapprochementId == null }
                val ouvertes = Repo.demandes().count { it.statut in listOf("soumise", "validee", "payee") && it.creeLe.take(10) <= e.fin }
                points = listOf(
                    (nonRappr == 0) to (if (nonRappr > 0) "$nonRappr opération${if (nonRappr > 1) "s" else ""} bancaire${if (nonRappr > 1) "s" else ""} non rapprochée${if (nonRappr > 1) "s" else ""}" else "Opérations bancaires rapprochées"),
                    (ouvertes == 0) to (if (ouvertes > 0) "$ouvertes demande${if (ouvertes > 1) "s" else ""} de dépense non soldée${if (ouvertes > 1) "s" else ""}" else "Demandes de dépense soldées"),
                    (e.fin < aujourdhui().toString()) to (if (e.fin < aujourdhui().toString()) "Période terminée" else "L’exercice n’est pas terminé (fin le ${dateFr(e.fin)})"))
            } catch (ex: Exception) { message(traduireErreur(ex)) }
        }
        AlertDialog(onDismissRequest = { aCloturer = null }, title = { Text("Clôturer ${e.libelle}$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    points?.forEach { (ok, t) -> Row(verticalAlignment = Alignment.CenterVertically) { Puce(if (ok) "OK" else "À voir", if (ok) Color(0xFFDDF3E2) else Couleurs.JauneClair, if (ok) Color(0xFF145C2C) else Couleurs.SurJaune); Spacer(Modifier.width(8.dp)); Text(t, fontSize = 14.sp) } }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Pensez à imprimer le rapport annuel (Rapports) avant de clôturer. L’exercice pourra être rouvert si besoin.", fontSize = 13.sp, color = Couleurs.Texte2)
                }
            },
            confirmButton = { Button(onClick = { scope.launch { try { Repo.cloturerExercice(e.id, true); message("Exercice clôturé"); version++ } catch (ex: Exception) { message(traduireErreur(ex)) }; aCloturer = null } }) { Text("Clôturer") } },
            dismissButton = { TextButton(onClick = { aCloturer = null }) { Text("Annuler") } })
    }
    aRouvrir?.let { e ->
        AlertDialog(onDismissRequest = { aRouvrir = null }, title = { Text("Rouvrir ${e.libelle}$NBSP?") },
            text = { Text("Les opérations de la période pourront de nouveau être modifiées. La réouverture est tracée.") },
            confirmButton = { Button(onClick = { scope.launch { try { Repo.cloturerExercice(e.id, false); message("Exercice rouvert"); version++ } catch (ex: Exception) { message(traduireErreur(ex)) }; aRouvrir = null } }) { Text("Rouvrir") } },
            dismissButton = { TextButton(onClick = { aRouvrir = null }) { Text("Annuler") } })
    }
}

val TYPES_CORBEILLE = mapOf("transactions" to "Opération", "members" to "Membre", "tiers" to "Tiers", "projects" to "Rendez-vous", "collectes" to "Collecte",
    "materiel" to "Matériel", "categories" to "Catégorie", "accounts" to "Compte", "budgets" to "Ligne de budget", "expense_requests" to "Demande", "exercices" to "Exercice")

@Composable
fun ParamCorbeille(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var liste by remember { mutableStateOf<List<ElementCorbeille>?>(null) }
    var profils by remember { mutableStateOf<List<ProfilCourt>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var voirRestaures by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version) { try { liste = Repo.corbeille(); profils = try { Repo.profils() } catch (_: Exception) { emptyList() } } catch (e: Exception) { message(traduireErreur(e)) } }
    fun nom(id: String?) = if (id == d.profil.id) "vous" else profils.firstOrNull { it.id == id }?.nom ?: "un membre du bureau"
    val l = liste
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Un élément supprimé n’est jamais perdu : il reste ici avec ses pièces jointes et ses liens, et « Restaurer » le remet exactement en place. Chaque suppression et chaque restauration est tracée.", color = Couleurs.Texte2, fontSize = 14.sp) }
        if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else {
            val attente = l.filter { it.restaureLe == null }; val faits = l.filter { it.restaureLe != null }
            if (attente.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Delete, null, tint = Couleurs.Texte2, modifier = Modifier.size(36.dp)); Text("La corbeille est vide.", color = Couleurs.Texte2)
                }
            }
            items(attente, key = { it.id }) { x ->
                CarteBlanche {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Puce(TYPES_CORBEILLE[x.table] ?: x.table, Color(0xFFEFEDEC), Couleurs.Texte2)
                        Column(Modifier.weight(1f)) {
                            Text(x.libelle, fontWeight = FontWeight.SemiBold)
                            Text("Supprimé le ${dateFr(x.supprimeLe.take(10))} par ${nom(x.supprimePar)}" + (x.motif?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                    FilledTonalButton(modifier = Modifier.align(Alignment.End), onClick = {
                        scope.launch { try { Repo.restaurer(x.id); message("Élément restauré"); version++; recharger(); EtatNouveautes.charger() } catch (e: Exception) { message(traduireErreur(e)) } }
                    }) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restaurer") }
                }
            }
            if (faits.isNotEmpty()) item { TextButton(onClick = { voirRestaures = !voirRestaures }) { Text(if (voirRestaures) "Masquer les éléments restaurés" else "Déjà restaurés (${faits.size})") } }
            if (voirRestaures) items(faits, key = { it.id }) { x ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(x.libelle); Text("Restauré le ${dateFr(x.restaureLe!!.take(10))}", fontSize = 12.sp, color = Couleurs.Texte2) }
                    Puce("Restauré", Color(0xFFDDF3E2), Color(0xFF145C2C))
                }
            }
        }
    }
}

// Identité et coordonnées (reprises sur les documents)
@Composable
fun ParamIdentite(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    val o = d.organisation
    val c = remember { mutableStateMapOf("nom" to o.nom, "sigle" to (o.sigle ?: ""), "objet" to (o.objet ?: ""), "date_creation" to (o.dateCreation?.let(::dateFr) ?: ""),
        "rna" to (o.rna ?: ""), "siret" to (o.siret ?: ""), "site_web" to (o.siteWeb ?: ""), "adresse" to (o.adresse ?: ""), "code_postal" to (o.codePostal ?: ""),
        "ville" to (o.ville ?: ""), "email" to (o.email ?: ""), "telephone" to (o.telephone ?: "")) }
    val scope = rememberCoroutineScope()
    val libelles = listOf("nom" to "Nom *", "sigle" to "Sigle", "objet" to "Objet", "date_creation" to "Date de création (JJ/MM/AAAA)", "rna" to "N° RNA", "siret" to "SIRET",
        "site_web" to "Site internet", "adresse" to "Adresse", "code_postal" to "Code postal", "ville" to "Ville", "email" to "E-mail", "telephone" to "Téléphone")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CarteBlanche {
                libelles.forEach { (k, l) ->
                    if (k == "adresse") Text("Coordonnées", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(c[k] ?: "", { c[k] = it }, label = { Text(l) }, singleLine = k != "objet", modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = when (k) { "email" -> KeyboardType.Email; "telephone" -> KeyboardType.Phone; "code_postal", "siret" -> KeyboardType.Number; "site_web" -> KeyboardType.Uri; else -> KeyboardType.Text }))
                }
                Button(enabled = (c["nom"] ?: "").isNotBlank(), modifier = Modifier.align(Alignment.End), onClick = {
                    scope.launch {
                        try {
                            Repo.majIdentite(c.toMap().mapValues { (k, v) -> v.trim().ifBlank { null }?.let { if (k == "date_creation") dateDepuisFr(it)?.toString() else it } })
                            message("Association enregistrée"); recharger()
                        } catch (e: Exception) { message(traduireErreur(e)) }
                    }
                }) { Text("Enregistrer") }
            }
        }
    }
}

// Catégories : ajout, renommage, suppression réversible si jamais utilisée
@Composable
fun ParamCategories(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var categories by remember { mutableStateOf(d.categories) }
    var version by remember { mutableStateOf(0) }
    var renommer by remember { mutableStateOf<Categorie?>(null) }
    var ajouter by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val supprimer = rememberSuppression(message) { version++; recharger() }
    LaunchedEffect(version) { try { categories = Repo.categories().filter { !it.interne } } catch (e: Exception) { message(traduireErreur(e)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("recette" to "Recettes (ressources)", "depense" to "Dépenses (emplois)").forEach { (sens, titre) ->
            item(key = sens) {
                CarteBlanche {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ajouter = sens }) { Text("Ajouter") }
                    }
                    categories.filter { it.sens == sens }.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.nom, Modifier.weight(1f))
                            IconButton(onClick = { renommer = c }) { Icon(Icons.Outlined.Edit, contentDescription = "Renommer ${c.nom}", tint = Couleurs.Texte2) }
                            IconButton(onClick = { supprimer("categories", c.id, c.nom) }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer ${c.nom}", tint = Couleurs.Erreur) }
                        }
                    }
                }
            }
        }
        item { Text("Une catégorie déjà utilisée par une opération ou une demande ne se supprime pas ; renommez-la si besoin.", color = Couleurs.Texte2, fontSize = 13.sp) }
    }
    renommer?.let { c ->
        var nom by remember(c.id) { mutableStateOf(c.nom) }
        DialogueSimple("Renommer la catégorie", "Enregistrer", nom.isNotBlank(), { renommer = null }, {
            scope.launch { try { Repo.renommerCategorie(c.id, nom.trim()); message("Catégorie renommée"); version++; recharger() } catch (e: Exception) { message(traduireErreur(e)) }; renommer = null }
        }) { OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom") }, singleLine = true) }
    }
    ajouter?.let { sens ->
        var nom by remember(sens) { mutableStateOf("") }
        DialogueSimple(if (sens == "recette") "Nouvelle catégorie de recette" else "Nouvelle catégorie de dépense", "Ajouter", nom.isNotBlank(), { ajouter = null }, {
            scope.launch { try { Repo.ajouterCategorie(NouvelleCategorie(nom.trim(), sens)); message("Catégorie ajoutée"); version++; recharger() } catch (e: Exception) { message(traduireErreur(e)) }; ajouter = null }
        }) { OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom") }, singleLine = true) }
    }
}

// Mot de passe et session
@Composable
fun ParamSecurite(message: (String) -> Unit) {
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val email = remember { Repo.emailConnecte() }
    val autofill = androidx.compose.ui.platform.LocalAutofillManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Text("Changer le mot de passe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ChampMotDePasse(mdp, { mdp = it }, "Nouveau mot de passe (8 caractères minimum)", androidx.compose.ui.autofill.ContentType.NewPassword, androidx.compose.ui.text.input.ImeAction.Next)
                ChampMotDePasse(mdp2, { mdp2 = it }, "Confirmez", androidx.compose.ui.autofill.ContentType.NewPassword, erreur = mdp2.isNotEmpty() && mdp2 != mdp)
                Button(enabled = mdp.length >= 8 && mdp == mdp2, modifier = Modifier.align(Alignment.End), onClick = {
                    scope.launch { try { Repo.changerMotDePasse(mdp); autofill?.commit(); mdp = ""; mdp2 = ""; message("Mot de passe modifié") } catch (e: Exception) { message(traduireErreur(e)) } }
                }) { Text("Enregistrer") }
            }
        }
        item {
            CarteBlanche {
                Text("Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Connecté avec $email" + if (lirePreference("resterConnecte") == "0") ", session fermée à la fermeture de l’application." else ", session gardée sur ce téléphone.", color = Couleurs.Texte2, fontSize = 14.sp)
                Button(onClick = { scope.launch { Repo.deconnecter() } }, colors = ButtonDefaults.buttonColors(containerColor = Couleurs.ErreurClair, contentColor = Color(0xFF410002))) { Text("Se déconnecter", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ParamNotifications() {
    var autorise by remember { mutableStateOf(notificationsPermises()) }
    val demander = rememberDemandeNotifications { autorise = it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Text("Pastilles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Un chiffre apparaît sur un onglet quand quelque chose vous attend ou est nouveau depuis votre dernière visite : demande à valider ou à payer, opération saisie par un autre, rendez-vous ajouté, participation demandée, nouveau membre. La cloche les rassemble.", color = Couleurs.Texte2, fontSize = 14.sp)
            }
        }
        item {
            CarteBlanche {
                Text("Notifications du téléphone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Une notification s’affiche quand une nouveauté arrive pendant que l’application est ouverte ou en arrière-plan. Actuellement : ${if (autorise) "activées" else "désactivées"}.", color = Couleurs.Texte2, fontSize = 14.sp)
                if (!autorise) Button(onClick = demander) { Text("Activer les notifications") }
            }
        }
    }
}

// Menu des Paramètres : un thème par groupe, une ligne par section (icône, titre, description)
data class SectionParam(val cle: String, val titre: String, val description: String, val icone: ImageVector)

fun sectionsParametres(d: Donnees): List<Pair<String, List<SectionParam>>> {
    val admin = d.peut("administrer")
    val corbeille = admin || d.peut("consulter_finances", "saisir_ecritures", "gerer_membres", "gerer_activites", "gerer_materiel", "demander_depenses")
    return listOf(
        "Mon compte" to listOf(SectionParam("compte", "Profil et fiche de membre", "Nom affiché, ma fiche de membre", Icons.Outlined.Person),
            SectionParam("securite", "Mot de passe et session", "Changer le mot de passe, se déconnecter", Icons.Outlined.Lock),
            SectionParam("notifications", "Notifications", "Pastilles et notifications du téléphone", Icons.Outlined.Notifications)),
        "Association" to if (admin) listOf(SectionParam("association", "Identité et coordonnées", "Nom, sigle, objet, adresse, RNA, SIRET", Icons.Outlined.Business),
            SectionParam("apparence", "Logo et bannière", "Logo, photo de l’accueil", Icons.Outlined.Image),
            SectionParam("exercices", "Exercices", "Créer, clôturer ou rouvrir un exercice", Icons.Outlined.CalendarMonth)) else emptyList(),
        "Finances" to if (admin) listOf(SectionParam("comptes", "Comptes et soldes de départ", "Banque, caisse, livret", Icons.Outlined.AccountBalanceWallet),
            SectionParam("categories", "Catégories", "Recettes et dépenses", Icons.Outlined.Sell),
            SectionParam("regles", "Cotisations et dépenses", "Montant, périodicité, délais, seuils", Icons.Outlined.Payments)) else emptyList(),
        "Accès" to if (admin) listOf(SectionParam("personnes", "Comptes et accès", "Vue d’ensemble : qui a un compte, actif ou non", Icons.Outlined.Groups),
            SectionParam("roles", "Rôles et droits", "Ce que chaque rôle peut faire", Icons.Outlined.Shield)) else emptyList(),
        "Données" to listOfNotNull(if (corbeille) SectionParam("corbeille", "Corbeille", "Éléments supprimés, à restaurer", Icons.Outlined.Delete) else null,
            if (admin) SectionParam("sauvegarde", "Sauvegarde", "Télécharger toutes les données", Icons.Outlined.Storage) else null,
            if (admin && !d.organisation.configuree) SectionParam("assistant", "Assistant de configuration", "Terminer la configuration pas à pas", Icons.Outlined.AutoFixHigh) else null),
    ).filter { it.second.isNotEmpty() }
}

// Couleur de chaque groupe du menu (mêmes valeurs que .c-bleu, .c-orange… du site)
private val COULEURS_GROUPES = mapOf("Mon compte" to (Color(0xFF1B77B0) to Color(0xFFE3F1FB)), "Association" to (Color(0xFFC23E10) to Color(0xFFFFEDE5)),
    "Finances" to (Color(0xFF2E8B4E) to Color(0xFFE4F5E8)), "Accès" to (Color(0xFF7048B8) to Color(0xFFEFE9FA)), "Données" to (Color(0xFF5A5350) to Color(0xFFEEECEB)))

@Composable
fun MenuParametres(d: Donnees, choisie: String?, onChoix: (String) -> Unit, modifier: Modifier = Modifier) {
    // Menu en cartes groupées et colorées, distinct de la navigation de l'application (comme le site)
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Couleurs.Orange, Color(0xFFE8743B)))).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text(d.profil.nom.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, fontWeight = FontWeight.Bold, color = Couleurs.Orange)
                }
                Column { Text(d.profil.nom, fontWeight = FontWeight.Bold, color = Color.White); Text(d.roles.firstOrNull { it.code == d.profil.role }?.nom ?: d.profil.role, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)) }
            }
        }
        sectionsParametres(d).forEach { (groupe, sections) ->
            val (fort, clair) = COULEURS_GROUPES[groupe] ?: (Couleurs.Orange to Couleurs.OrangeClair)
            item(key = groupe) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text(groupe.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Couleurs.Texte2, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                        Column {
                            sections.forEachIndexed { i, sec ->
                                if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA))
                                val active = sec.cle == choisie
                                Row(Modifier.fillMaxWidth().background(if (active) clair else Color.Transparent).clickable { onChoix(sec.cle) }
                                    .drawBehind { if (active) drawRect(fort, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (active) fort else clair), contentAlignment = Alignment.Center) {
                                        Icon(sec.icone, null, tint = if (active) Color.White else fort, modifier = Modifier.size(20.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(sec.titre, fontWeight = FontWeight.SemiBold)
                                        Text(sec.description, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Barre propre aux Paramètres : retour au menu (téléphone, dans une section), titre, « Fermer » pour revenir à l'application
@Composable
fun BarreParametres(d: Donnees, titre: String, retour: (() -> Unit)?, onFermer: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (retour != null) IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour aux paramètres") }
            else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Couleurs.Orange), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Settings, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(d.organisation.nom, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 1)
            }
            OutlinedButton(onClick = onFermer, contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Fermer") }
        }
    }
}

@Composable
fun EnteteSection(s: SectionParam) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Text(s.titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(s.description, color = Couleurs.Texte2, fontSize = 14.sp)
    }
}
