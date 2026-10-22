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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.focus.onFocusChanged
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
    Triple(Color(0xFFFFE1D6), Color(0xFF8A2A06), Couleurs.Orange), Triple(Color(0xFFD7ECFA), Color(0xFF0B4A73), Couleurs.Bleu),
    Triple(Color(0xFFDDF3E2), Color(0xFF145C2C), Couleurs.Vert), Triple(Color(0xFFEBE3FA), Color(0xFF4A2C86), Color(0xFF7048B8)),
    Triple(Color(0xFFFCE0EC), Color(0xFF86184A), Color(0xFFC2367A)), Triple(Color(0xFFD4F1F0), Color(0xFF0D5653), Color(0xFF1B8A85)),
    Triple(Color(0xFFFFF0C7), Color(0xFF6B4A00), Color(0xFFC58A00)),
)
fun indiceCouleur(id: String) = id.sumOf { it.code } % EVT_PALETTE.size
fun couleurEvt(id: String) = EVT_PALETTE[indiceCouleur(id)]

// « Dans 3 jours », « Demain », « Aujourd'hui »
fun dansJours(date: String): String {
    val j = kotlinx.datetime.LocalDate.parse(date.take(10)).toEpochDays() - aujourdhui().toEpochDays()
    // « Demain », « Dans 12 jours », puis en mois au-delà de 45 jours (« Dans 6 mois ») ; même règle que le site
    return when { j < 0 -> "En cours"; j == 0 -> "Aujourd’hui"; j == 1 -> "Demain"; j <= 45 -> "Dans $j jours"; else -> "Dans ${kotlin.math.round(j / 30.44).toInt()} mois" }
}

// =====================================================================
// Nouveautés : pastilles sur les onglets, cloche, notification du téléphone
// =====================================================================
val SECTIONS_NOUVEAUTES = listOf("ecritures", "depenses", "cotisations", "activites", "membres", "communiques")
val LIBELLES_SECTIONS = mapOf("ecritures" to "Opérations", "depenses" to "Demandes", "cotisations" to "Cotisations", "activites" to "Planning", "membres" to "Membres")

object EtatNouveautes {
    val n = mutableStateOf(Nouveautes())
    // Recharge ; prévient par une notification du téléphone (son, vibration, compteur sur l'icône) si un élément plus
    // récent est arrivé. Le dernier élément signalé est gardé sur le téléphone : l'application et la vérification en
    // arrière-plan ne préviennent jamais deux fois pour la même chose.
    suspend fun charger() {
        try {
            val r = Repo.nouveautes()
            val recent = r.elements.firstOrNull()
            val cle = "nouveautes.dernier." + (Repo.profilId() ?: "")
            val dernier = lirePreference(cle)
            if (dernier != null && recent != null && recent.quand > dernier) notifierSysteme("${Repo.nomAssociation() ?: "Trésorerie"} · ${recent.titre}", recent.detail, r.total)
            if (recent != null && (dernier == null || recent.quand > dernier)) garderPreference(cle, recent.quand) else if (dernier == null) garderPreference(cle, "")
            if (r.total == 0) effacerNotifications()
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
                Icon(Icons.Outlined.CheckCircle, null, tint = Couleurs.Vert, modifier = Modifier.size(36.dp))
                Text("Rien de nouveau. Vous êtes à jour.", color = Couleurs.Texte2)
            }
            els.forEach { e ->
                Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable { onAller(e.section); onFermer() }.padding(vertical = 6.dp),
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
// La suppression et son « Annuler » vivent dans une portée de l'application, pas dans celle de l'écran :
// sinon fermer la fiche qui porte « Supprimer » annulait la suite (liste non rechargée, « Annuler » perdu).
object Annulation { var hote: SnackbarHostState? = null; val portee = kotlinx.coroutines.MainScope() }

/** Renvoie la fonction qui demande confirmation puis supprime ; « Annuler » dans le message restaure. */
@Composable
fun rememberSuppression(message: (String) -> Unit, note: String? = null, apres: () -> Unit): (table: String, id: String, libelle: String) -> Unit {
    var cible by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val scope = Annulation.portee
    val apresActuel by rememberUpdatedState(apres)
    cible?.let { (table, id, libelle) ->
        var motif by remember(id) { mutableStateOf("") }
        var enCours by remember(id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cible = null },
            icon = { Icon(Icons.Outlined.Delete, null, tint = Couleurs.Erreur) },
            title = { Text("Supprimer «$NBSP$libelle$NBSP»$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Précision propre à l'élément (exercice : les écritures restent), comme la bande « info » du site
                    if (note != null) Surface(color = Color(0xFFEAF4FB), shape = Formes.XGrand, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB5D6EE))) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Info, null, tint = Couleurs.Bleu, modifier = Modifier.size(20.dp))
                            Text(note, fontSize = 14.sp, color = Couleurs.Texte)
                        }
                    }
                    Text("L’élément part dans la corbeille (Paramètres, Corbeille) avec ses pièces jointes ; il peut être restauré à tout moment.", fontSize = 14.sp, color = Couleurs.Texte2)
                    OutlinedTextField(motif, { motif = it.take(120) }, label = { Text("Motif (facultatif)") }, placeholder = { Text("Doublon, erreur de saisie…") }, singleLine = true)
                }
            },
            confirmButton = {
                // .btn-danger du site : contour et texte rouges, icône corbeille
                OutlinedButton(enabled = !enCours, colors = ButtonDefaults.outlinedButtonColors(contentColor = Couleurs.Erreur),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Erreur.copy(alpha = 0.5f)), onClick = {
                    enCours = true
                    scope.launch {
                        try {
                            val idCorbeille = Repo.supprimer(table, id, motif.trim().ifBlank { null })
                            cible = null; apresActuel(); EtatNouveautes.charger()
                            val hote = Annulation.hote
                            if (hote != null) {
                                val r = hote.showSnackbar("Supprimé, placé dans la corbeille", actionLabel = "Annuler", duration = SnackbarDuration.Long)
                                if (r == SnackbarResult.ActionPerformed) try { Repo.restaurer(idCorbeille); apresActuel(); message("Restauré") } catch (e: Exception) { message(traduireErreur(e)) }
                            } else message("Supprimé, placé dans la corbeille")
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Supprimer") }
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
            Row(Modifier.clip(Formes.XGrand).clickable { accord = !accord }, verticalAlignment = Alignment.CenterVertically) {
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
    LaunchedEffect(version, Synchro.version) {
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

    var trouvee by remember { mutableStateOf<AssociationAnnuaire?>(null) }
    var nomQuitte by remember { mutableStateOf(false) }
    var nomFocus by remember { mutableStateOf(false) }
    fun champ(k: String, l: String, clavier: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth()) =
        @Composable { OutlinedTextField(champs[k] ?: "", { champs[k] = it }, label = { Text(l) }, singleLine = k != "objet", keyboardOptions = KeyboardOptions(keyboardType = clavier), modifier = modifier) }

    fun suivant() {
        enCours = true
        scope.launch {
            try {
                val v = { k: String -> champs[k]?.trim()?.ifBlank { null } }
                when (etape) {
                    0 -> Repo.majIdentite(mapOf("nom" to (v("nom") ?: o.nom), "sigle" to v("sigle"), "objet" to v("objet"), "date_creation" to v("date_creation")?.let { dateDepuisFr(it)?.toString() }) +
                        // Coordonnées retrouvées dans l'annuaire : posées pour l'étape suivante (modifiables), comme le site
                        (trouvee?.let { a -> listOf("adresse" to a.adresse, "code_postal" to a.codePostal, "ville" to a.ville, "siret" to a.siret, "rna" to a.rna).filter { it.second != null }.toMap() } ?: emptyMap()))
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
        // Étapes : au téléphone « Étape n sur 6 · Intitulé » et une barre fine (comme le site) ; au-delà, les six ronds
        BoxWithConstraints(Modifier.fillMaxWidth()) { if (maxWidth < 520.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(androidx.compose.ui.text.buildAnnotatedString { append("Étape ${etape + 1} sur ${ETAPES.size} · "); pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Couleurs.Texte)); append(ETAPES[etape]); pop() }, fontSize = 13.sp, color = Couleurs.Texte2)
            val part by animateFloatAsState((etape + 1f) / ETAPES.size)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Formes.Petit).background(Couleurs.Surface2)) { Box(Modifier.fillMaxWidth(part).fillMaxHeight().clip(Formes.Petit).background(Couleurs.Orange)) }
        } else
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ETAPES.forEachIndexed { i, t ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val echelle by animateFloatAsState(if (i == etape) 1.15f else 1f)
                    Box(Modifier.size(28.dp).scale(echelle).clip(CircleShape).background(when { i < etape -> Couleurs.Bleu; i == etape -> Couleurs.Orange; else -> Couleurs.Surface2 }), contentAlignment = Alignment.Center) {
                        Text(if (i < etape) "✓" else "${i + 1}", color = if (i <= etape) Color.White else Couleurs.Texte2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(t, fontSize = 11.sp, fontWeight = if (i == etape) FontWeight.SemiBold else FontWeight.Normal, color = if (i == etape) Couleurs.Texte else Couleurs.Texte2, maxLines = 1)
                }
            }
        } }
        AnimatedContent(etape, transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) }) { e ->
            CarteBlanche {
                when (e) {
                    0 -> {
                        Text("Votre association", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Ces informations apparaissent sur les documents (rapports, PDF). Le logo se change dans Paramètres, Logo et bannière.", color = Couleurs.Texte2, fontSize = 14.sp)
                        AnnuaireAssociation(trouvee) { a -> trouvee = a; champs["nom"] = a.nom; a.dateCreation?.let { champs["date_creation"] = dateFr(it) }; a.adresse?.let { champs["adresse"] = it }
                            a.codePostal?.let { champs["code_postal"] = it }; a.ville?.let { champs["ville"] = it }; a.siret?.let { champs["siret"] = it }; a.rna?.let { champs["rna"] = it } }
                        val nomVide = (champs["nom"] ?: "").isBlank()
                        OutlinedTextField(champs["nom"] ?: "", { champs["nom"] = it.take(80) }, label = { Text("Nom de l’association *") }, singleLine = true,
                            isError = nomQuitte && nomVide, supportingText = if (nomQuitte && nomVide) ({ Text("Indiquez le nom de l’association.") }) else null,
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) nomFocus = true else if (nomFocus && (champs["nom"] ?: "").isBlank()) nomQuitte = true })
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
                            Surface(onClick = { val x = exerciceDe(deb); exDebut = x.debut; exFin = x.fin; exLibelle = x.libelle }, shape = Formes.Moyen,
                                color = if (choisi) Couleurs.OrangeClair else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(if (choisi) 2.dp else 1.dp, if (choisi) Couleurs.Orange else Couleurs.Bord)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    RadioButton(selected = choisi, onClick = null, modifier = Modifier.size(20.dp), colors = RadioButtonDefaults.colors(selectedColor = Couleurs.Orange))
                                    Column { Text(t, fontWeight = FontWeight.Bold); Text(s, fontSize = 13.sp, color = Couleurs.Texte2) }
                                }
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
                            Icon(Icons.Outlined.CheckCircle, null, tint = Couleurs.Vert, modifier = Modifier.size(36.dp))
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

// Recherche dans l'annuaire public + carte de confirmation (même rendu que .annuaire du site)
@Composable
fun AnnuaireAssociation(trouvee: AssociationAnnuaire?, onChoix: (AssociationAnnuaire) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<AssociationAnnuaire>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var montre by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        if (q.replace(" ", "").length < 3) { res = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        try { res = Repo.chercherAssociation(q); erreur = null } catch (e: Exception) { res = emptyList(); erreur = e.message ?: "Annuaire indisponible pour le moment" }
    }
    OutlinedTextField(q, { q = it; montre = true }, label = { Text("Retrouver l’association (facultatif)") }, placeholder = { Text("Nom, SIRET ou RNA") }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
    val l = res
    if (montre && l != null) Surface(shape = Formes.XGrand, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), shadowElevation = 4.dp) {
        Column {
            if (l.isEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(if (erreur != null) erreur!! else "Aucune association trouvée", fontWeight = FontWeight.Bold)
                Text("Essayez le SIRET ou le RNA, ou remplissez simplement les champs ci-dessous.", fontSize = 12.5.sp, color = Couleurs.Texte2)
            }
            l.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { montre = false; q = a.nom; onChoix(a) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(a.nom, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(a.ville, a.siret?.let { "SIRET $it" } ?: a.rna?.let { "RNA $it" }).joinToString(" · "), fontSize = 12.5.sp, color = Couleurs.Texte2)
                }
            }
        }
    }
    if (trouvee != null && !montre) {
        Surface(shape = Formes.XGrand, color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Business, null, Modifier.size(18.dp))
                Column {
                    Text(trouvee.nom, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(trouvee.adresse, listOfNotNull(trouvee.codePostal, trouvee.ville).joinToString(" ").ifBlank { null }).joinToString(", ").ifBlank { "Adresse non publiée" }, fontSize = 13.sp)
                    Text(listOfNotNull(trouvee.siret?.let { s -> "SIRET " + s.chunked(3).let { c -> if (s.length == 14) "${s.take(3)} ${s.substring(3, 6)} ${s.substring(6, 9)} ${s.substring(9)}" else s } }, trouvee.rna?.let { "RNA $it" }).joinToString(" · ").ifBlank { "Sans SIRET" }, fontSize = 13.sp)
                }
            }
        }
        Text("Ces informations remplissent l’identité et les coordonnées ; vous pourrez les corriger.", fontSize = 13.sp, color = Couleurs.Texte2)
    }
}

private fun nombre2(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").toDoubleOrNull()

// =====================================================================
// Paramètres : sections nouvelles (exercices, corbeille, identité, catégories, sécurité, notifications)
// =====================================================================
fun statutExercice(e: Exercice): Triple<String, Color, Color> {
    val auj = aujourdhui().toString()
    return when {
        e.cloture -> Triple("Clôturé", Couleurs.Surface2, Couleurs.Texte2)
        e.debut > auj -> Triple("À venir", Couleurs.Surface2, Couleurs.Texte2)
        e.fin < auj -> Triple("À clôturer", Couleurs.JauneClair, Couleurs.SurJaune)
        else -> Triple("En cours", Couleurs.BleuClair, Couleurs.SurBleuClair)   // puce-ok du site
    }
}

// Supprimer un exercice retire la période, jamais les écritures (même texte que NOTE_SUPPR_EXERCICE du site)
const val NOTE_SUPPR_EXERCICE = "Les écritures de cette période ne sont pas supprimées : seule la période l’est. Elles restent dans Opérations ; pour les retirer, supprimez-les une à une."

@Composable
fun ParamExercices(d: Donnees, message: (String) -> Unit, recharger: () -> Unit = {}) {
    var liste by remember { mutableStateOf<List<Exercice>?>(null) }
    var version by remember { mutableStateOf(0) }
    var edition by remember { mutableStateOf<Pair<Boolean, Exercice?>?>(null) }
    var aCloturer by remember { mutableStateOf<Exercice?>(null) }
    var aRouvrir by remember { mutableStateOf<Exercice?>(null) }
    val scope = rememberCoroutineScope()
    // Après suppression (ou « Annuler ») : la liste ET les données partagées (choix de Rapports, verrou des opérations)
    val supprimer = rememberSuppression(message, NOTE_SUPPR_EXERCICE) { version++; recharger() }
    LaunchedEffect(version, Synchro.version) { try { liste = Repo.exercices() } catch (e: Exception) { message(traduireErreur(e)) } }
    val l = liste ?: emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("L’exercice regroupe 12 mois de comptes. Une fois clôturé, ses opérations ne peuvent plus être ajoutées, modifiées ni supprimées (il peut être rouvert).", color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Button(onClick = { edition = true to null }) { Text("Nouvel exercice") }
                }
                if (liste != null && l.isEmpty()) Text("Aucun exercice. Créez le premier.", color = Couleurs.Texte2)
                l.forEachIndexed { i, e ->
                    key(e.id) {
                        if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA))
                        val (lib, fond, encre) = statutExercice(e)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = Couleurs.Texte2)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.libelle, fontWeight = FontWeight.Bold)
                                Text("${dateFr(e.debut)} au ${dateFr(e.fin)}" + (e.clotureLe?.let { " · clôturé le ${dateFr(it.take(10))}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                            }
                            Puce(lib, fond, encre)
                        }
                        // Même ordre que le site : Modifier, Clôturer, corbeille
                        Row(Modifier.fillMaxWidth().padding(start = 34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (e.cloture) TextButton(onClick = { aRouvrir = e }) { Text("Rouvrir") }
                            else {
                                TextButton(onClick = { edition = false to e }) { Text("Modifier") }
                                OutlinedButton(onClick = { aCloturer = e }) { Text("Clôturer") }
                                IconButton(onClick = { supprimer("exercices", e.id, e.libelle) }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer ${e.libelle}", tint = Couleurs.Texte2) }
                            }
                        }
                    }
                }
            }
        }
    }
    edition?.let { (_, e) ->
        val suivant = l.firstOrNull()?.let { exerciceDe(kotlinx.datetime.LocalDate.fromEpochDays(kotlinx.datetime.LocalDate.parse(it.fin).toEpochDays() + 1).toString()) } ?: exerciceDe("${aujourdhui().year}-01-01")
        var debut by remember(e) { mutableStateOf(dateFr(e?.debut ?: suivant.debut)) }
        var fin by remember(e) { mutableStateOf(dateFr(e?.fin ?: suivant.fin)) }
        var libelle by remember(e) { mutableStateOf(e?.libelle ?: suivant.libelle) }
        val dD = dateDepuisFr(debut); val dF = dateDepuisFr(fin)
        DialogueSimple(if (e == null) "Nouvel exercice" else "Modifier l’exercice", "Enregistrer", dD != null && dF != null && dF > dD && libelle.isNotBlank(), { edition = null }, {
            scope.launch {
                try { Repo.enregistrerExercice(e?.id, NouvelExercice(libelle.trim(), dD.toString(), dF.toString())); message(if (e == null) "Exercice créé" else "Exercice modifié"); version++; recharger() }
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
                    (ouvertes == 0) to (if (ouvertes > 0) "$ouvertes demande${if (ouvertes > 1) "s" else ""} de dépense non soldée${if (ouvertes > 1) "s" else ""} (à valider, à payer ou sans justificatif)" else "Demandes de dépense soldées"),
                    (e.fin < aujourdhui().toString()) to (if (e.fin < aujourdhui().toString()) "Période terminée" else "L’exercice n’est pas terminé (fin le ${dateFr(e.fin)})"))
            } catch (ex: Exception) { message(traduireErreur(ex)) }
        }
        AlertDialog(onDismissRequest = { aCloturer = null }, title = { Text("Clôturer ${e.libelle}$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    points?.forEach { (ok, t) -> Row(verticalAlignment = Alignment.CenterVertically) { Puce(if (ok) "OK" else "À voir", if (ok) Couleurs.BleuClair else Couleurs.JauneClair, if (ok) Couleurs.SurBleuClair else Couleurs.SurJaune); Spacer(Modifier.width(8.dp)); Text(t, fontSize = 14.sp) } }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Pensez à imprimer le rapport annuel (Rapports) avant de clôturer. L’exercice pourra être rouvert si besoin.", fontSize = 13.sp, color = Couleurs.Texte2)
                }
            },
            confirmButton = { Button(onClick = { scope.launch { try { Repo.cloturerExercice(e.id, true); message("Exercice clôturé"); version++; recharger() } catch (ex: Exception) { message(traduireErreur(ex)) }; aCloturer = null } }) { Text("Clôturer") } },
            dismissButton = { TextButton(onClick = { aCloturer = null }) { Text("Annuler") } })
    }
    aRouvrir?.let { e ->
        AlertDialog(onDismissRequest = { aRouvrir = null }, title = { Text("Rouvrir ${e.libelle}$NBSP?") },
            text = { Text("Les opérations de la période pourront de nouveau être modifiées. La réouverture est tracée.") },
            confirmButton = { Button(onClick = { scope.launch { try { Repo.cloturerExercice(e.id, false); message("Exercice rouvert"); version++; recharger() } catch (ex: Exception) { message(traduireErreur(ex)) }; aRouvrir = null } }) { Text("Rouvrir") } },
            dismissButton = { TextButton(onClick = { aRouvrir = null }) { Text("Annuler") } })
    }
}

val TYPES_CORBEILLE = mapOf("transactions" to "Opération", "members" to "Membre", "tiers" to "Tiers", "projects" to "Rendez-vous", "collectes" to "Collecte",
    "materiel" to "Matériel", "categories" to "Catégorie", "accounts" to "Compte", "budgets" to "Ligne de budget", "expense_requests" to "Demande", "exercices" to "Exercice", "communiques" to "Communiqué",
    "tarifs" to "Montant de cotisation", "comptes" to "Compte d’accès")

@Composable
fun ParamCorbeille(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var liste by remember { mutableStateOf<List<ElementCorbeille>?>(null) }
    var profils by remember { mutableStateOf<List<ProfilCourt>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var voirRestaures by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version, Synchro.version) { try { liste = Repo.corbeille(); profils = try { Repo.profils() } catch (_: Exception) { emptyList() } } catch (e: Exception) { message(traduireErreur(e)) } }
    // Même règle que le site : le nom du profil (le sien compris), sinon « un membre du bureau »
    fun nom(id: String?) = profils.firstOrNull { it.id == id }?.nom ?: if (id == d.profil.id) d.profil.nom else "un membre du bureau"
    val l = liste
    var enCours by remember { mutableStateOf<String?>(null) }
    // Compte d'accès rétabli : recréé par la fonction supprimer-compte avec un nouveau mot de passe provisoire à transmettre
    var retabli by remember { mutableStateOf<Pair<CompteRestaure, String>?>(null) }
    retabli?.let { (r, mdp) -> FeuilleCompteRetabli(d, r, mdp) { retabli = null } }
    // Une ligne, comme sur le site : type, libellé, « Supprimé le … par … · motif · restauré le … », Restaurer ou « Restauré »
    @Composable fun Ligne(x: ElementCorbeille) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Puce(TYPES_CORBEILLE[x.table] ?: x.table, Couleurs.Surface2, Couleurs.Texte2)
            Column(Modifier.weight(1f)) {
                Text(x.libelle, fontWeight = FontWeight.SemiBold)
                Text("Supprimé le ${dateFr(x.supprimeLe.take(10))} par ${nom(x.supprimePar)}" + (x.motif?.let { " · $it" } ?: "") +
                    (x.restaureLe?.let { " · restauré le ${dateFr(it.take(10))}" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
            }
            if (x.restaureLe != null) Puce("Restauré", Couleurs.BleuClair, Couleurs.SurBleuClair)
            else OutlinedButton(enabled = enCours != x.id, contentPadding = PaddingValues(horizontal = 12.dp), onClick = {
                enCours = x.id
                scope.launch {
                    try {
                        if (x.table == "comptes") { val mdp = motDePasseProvisoire(); retabli = Repo.restaurerCompte(x.id, mdp) to mdp; version++ }
                        else { Repo.restaurer(x.id); recharger(); message("Élément restauré"); version++; EtatNouveautes.charger() }
                    }
                    catch (e: Exception) { message(traduireErreur(e)) }
                    enCours = null
                }
            }) { Icon(Icons.Outlined.Restore, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Restaurer") }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            CarteBlanche {
                Text("Un élément supprimé n’est jamais perdu : il reste ici avec ses pièces jointes et ses liens, et «$NBSP" + "Restaurer$NBSP» le remet exactement en place. Chaque suppression et chaque restauration est tracée.", color = Couleurs.Texte2, fontSize = 14.sp)
                if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    val attente = l.filter { it.restaureLe == null }
                    if (attente.isEmpty()) Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Delete, null, tint = Couleurs.Texte2, modifier = Modifier.size(32.dp)); Text("La corbeille est vide.", color = Couleurs.Texte2)
                    }
                    attente.forEachIndexed { i, x -> key(x.id) { if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA)); Ligne(x) } }
                }
            }
        }
        val faits = l?.filter { it.restaureLe != null } ?: emptyList()
        // « Déjà restaurés (n) » : carte repliable, comme le <details> du site
        if (faits.isNotEmpty()) item {
            CarteBlanche {
                Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable { voirRestaures = !voirRestaures }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (voirRestaures) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                    Text("Déjà restaurés (${faits.size})", fontWeight = FontWeight.SemiBold)
                }
                if (voirRestaures) faits.forEachIndexed { i, x -> key(x.id) { if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA)); Ligne(x) } }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            CarteBlanche {
                libelles.forEach { (k, l) ->
                    if (k == "adresse") Text("Coordonnées", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    val texteExemple = when (k) { "rna" -> "W381000000"; "site_web" -> "https://"; else -> null }
                    val exemple: (@Composable () -> Unit)? = if (texteExemple == null) null else ({ Text(texteExemple) })
                    OutlinedTextField(c[k] ?: "", { c[k] = it.take(when (k) { "nom" -> 80; "sigle" -> 20; "objet" -> 300; "rna" -> 12; "siret" -> 17; "code_postal" -> 10; else -> 200 }) }, label = { Text(l) },
                        placeholder = exemple,
                        singleLine = k != "objet", minLines = if (k == "objet") 2 else 1, modifier = Modifier.fillMaxWidth(),
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
    val scope = rememberCoroutineScope()
    val supprimer = rememberSuppression(message) { version++; recharger() }
    LaunchedEffect(version, Synchro.version) { try { categories = Repo.categories().filter { !it.interne } } catch (e: Exception) { message(traduireErreur(e)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("recette" to "Recettes (ressources)", "depense" to "Dépenses (emplois)").forEach { (sens, titre) ->
            item(key = sens) {
                CarteBlanche {
                    Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    categories.filter { it.sens == sens }.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.nom, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { renommer = c }) { Icon(Icons.Outlined.Edit, contentDescription = "Renommer ${c.nom}", tint = Couleurs.Texte2) }
                            IconButton(onClick = { supprimer("categories", c.id, c.nom) }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer ${c.nom}", tint = Couleurs.Texte2) }
                        }
                    }
                    // Ajout dans la carte, comme le site : champ « Nouvelle catégorie » et bouton « Ajouter »
                    var nouveau by remember(sens) { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(nouveau, { nouveau = it.take(60) }, placeholder = { Text("Nouvelle catégorie") }, singleLine = true, modifier = Modifier.weight(1f))
                        FilledTonalButton(enabled = nouveau.isNotBlank(), onClick = {
                            val n = nouveau.trim()
                            scope.launch { try { Repo.ajouterCategorie(NouvelleCategorie(n, sens)); nouveau = ""; message("Catégorie ajoutée"); version++; recharger() } catch (e: Exception) { message(traduireErreur(e)) } }
                        }) { Text("Ajouter") }
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
}

// Mot de passe et session
@Composable
fun ParamSecurite(message: (String) -> Unit) {
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val email = remember { Repo.emailConnecte() }
    val autofill = androidx.compose.ui.platform.LocalAutofillManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Text("Connecté avec $email" + if (lirePreference("resterConnecte") == "0") ", session fermée à la fermeture de l’application." else ", session gardée sur cet appareil.", color = Couleurs.Texte2, fontSize = 14.sp)
                Button(onClick = { scope.launch { Repo.deconnecter() } }, colors = ButtonDefaults.buttonColors(containerColor = Couleurs.ErreurClair, contentColor = Couleurs.SurErreurClair)) { Text("Se déconnecter", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ParamNotifications() {
    var autorise by remember { mutableStateOf(notificationsPermises()) }
    val demander = rememberDemandeNotifications { autorise = it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            CarteBlanche {
                Text("Pastilles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Un chiffre apparaît sur un onglet quand quelque chose vous attend ou est nouveau depuis votre dernière visite : demande à valider ou à payer, opération saisie par un autre, rendez-vous ajouté, participation demandée, nouveau membre. La cloche les rassemble.", color = Couleurs.Texte2, fontSize = 14.sp)
            }
        }
        // « Alertes sur cet appareil » : même bloc que blocAlertes du site (état, Recevoir / Essayer, son, vibration)
        item {
            CarteBlanche {
                Text("Alertes sur cet appareil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = if (autorise) Couleurs.BleuClair else Couleurs.Survol, shape = Formes.XGrand) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (autorise) Icons.Outlined.CheckCircle else Icons.Outlined.Notifications, null, tint = if (autorise) Couleurs.Bleu else Couleurs.Texte2, modifier = Modifier.size(18.dp))
                        Column {
                            Text(if (autorise) "Notifications activées sur cet appareil" else "Notifications désactivées sur cet appareil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (autorise) "Vous êtes prévenu des communiqués, des rendez-vous (et la veille), des participations demandées, même application fermée."
                                else "Activez-les pour être prévenu même application fermée : communiqués, rendez-vous, participations.", fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!autorise) Button(onClick = demander) { Text("Recevoir les notifications") }
                    FilledTonalButton(onClick = { notifierSysteme("Essai", "Voici comment une nouveauté vous sera signalée", EtatNouveautes.n.value.total) }) { Text("Essayer") }
                }
                var son by remember { mutableStateOf(lirePreference("notif.son") != "0") }
                var vibre by remember { mutableStateOf(lirePreference("notif.vibration") != "0") }
                Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable { son = !son; garderPreference("notif.son", if (son) "1" else "0") }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = Couleurs.Texte2, modifier = Modifier.size(18.dp))
                    Checkbox(son, { son = it; garderPreference("notif.son", if (it) "1" else "0") }); Text("Son à l’arrivée d’une nouveauté", fontSize = 14.sp)
                }
                Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable { vibre = !vibre; garderPreference("notif.vibration", if (vibre) "1" else "0") }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Vibration, null, tint = Couleurs.Texte2, modifier = Modifier.size(18.dp))
                    Checkbox(vibre, { vibre = it; garderPreference("notif.vibration", if (it) "1" else "0") }); Text("Vibration du téléphone", fontSize = 14.sp)
                }
                Text("Le chiffre des nouveautés s’affiche aussi sur l’icône de l’application. Application fermée, elle vérifie les nouveautés toutes les 15 minutes.", color = Couleurs.Texte2, fontSize = 13.sp)
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
        "Association" to (if (admin) listOf(SectionParam("association", "Identité et coordonnées", "Nom, sigle, objet, adresse, RNA, SIRET", Icons.Outlined.Business),
            SectionParam("apparence", "Logo et bannière", "Logo, photo de l’accueil", Icons.Outlined.Image),
            SectionParam("exercices", "Exercices", "Créer, clôturer ou rouvrir un exercice", Icons.Outlined.CalendarMonth)) else emptyList()),
        "Finances" to if (admin) listOf(SectionParam("comptes", "Comptes et soldes de départ", "Banque, caisse, livret", Icons.Outlined.AccountBalanceWallet),
            SectionParam("categories", "Catégories", "Recettes et dépenses", Icons.Outlined.Sell),
            SectionParam("regles", "Cotisations et dépenses", "Montant, périodicité, délais, seuils", Icons.Outlined.Payments)) else emptyList(),
        "Accès" to if (admin) listOf(SectionParam("personnes", "Comptes et accès", "Vue d’ensemble : qui a un compte, actif ou non", Icons.Outlined.Groups),
            SectionParam("roles", "Rôles et droits", "Ce que chaque rôle peut faire", Icons.Outlined.Shield)) else emptyList(),
        "Données" to listOfNotNull(if (corbeille) SectionParam("corbeille", "Corbeille", "Éléments supprimés, à restaurer", Icons.Outlined.Delete) else null,
            if (admin) SectionParam("sauvegarde", "Sauvegarde", "Télécharger toutes les données", Icons.Outlined.Storage) else null,
            if (admin && (!d.organisation.configuree || EtatMiseEnRoute.restantes > 0)) SectionParam("miseenroute", "Mise en route", "Les étapes restantes, assistant de configuration", Icons.Outlined.AutoFixHigh) else null),
    ).filter { it.second.isNotEmpty() }
}

// Menu des Paramètres, sobre comme le reste de l'application (même rendu que .param-carte du site) :
// titres de groupe en petites capitales, une carte blanche par groupe, icônes neutres, filet orange sur la section ouverte.
@Composable
fun MenuParametres(d: Donnees, choisie: String?, onChoix: (String) -> Unit, modifier: Modifier = Modifier, avecTitre: Boolean = true) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (avecTitre) item { TitreParametres(d) }
        sectionsParametres(d).forEach { (groupe, sections) ->
            item(key = groupe) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(groupe.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Couleurs.Texte2, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Surface(shape = Formes.XGrand, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Column {
                            sections.forEachIndexed { i, sec ->
                                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                                val active = sec.cle == choisie
                                Row(Modifier.fillMaxWidth().background(if (active) Couleurs.Survol else Color.Transparent).clickable { onChoix(sec.cle) }
                                    .drawBehind { if (active) drawRect(Couleurs.Orange, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(sec.icone, null, tint = if (active) Couleurs.Orange else Couleurs.Texte2, modifier = Modifier.size(20.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(sec.titre, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(sec.description, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

// Titre de page, comme « Demandes » ou « Membres » : « Paramètres » puis la personne et son rôle
@Composable
fun TitreParametres(d: Donnees) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Paramètres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${d.profil.nom} · ${d.roles.firstOrNull { it.code == d.profil.role }?.nom ?: d.profil.role}", color = Couleurs.Texte2)
    }
}

// En-tête d'une section : sur téléphone « ‹ Paramètres » pour revenir au menu, puis le titre de la section comme un titre de page
@Composable
fun EnteteSection(s: SectionParam, retour: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (retour != null) 4.dp else 16.dp)) {
        if (retour != null) TextButton(onClick = retour, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(20.dp)); Text("Paramètres")
        }
        Text(s.titre, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(s.description, color = Couleurs.Texte2)
    }
}


// =====================================================================
// Mise en route et rappels dosés (mêmes règles que le site : rappelPermis, repousser, discretPermis)
// « Plus tard » repousse d'une semaine ; après deux refus, plus de fenêtre automatique, seulement un rappel
// discret (petit message en bas), au plus une fois par semaine.
// =====================================================================
object EtatMiseEnRoute { var restantes by mutableStateOf(1); var fenetreMontree = false }

object Rappels {
    private const val SEMAINE = 7L * 86_400_000L
    private fun maintenant() = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    private fun lire(cle: String): Pair<Int, Long>? = lirePreference(cle)?.split('|')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
    fun permis(profil: String, cle: String): Boolean { val r = lire("rappel.$profil.$cle") ?: return true; return r.first < 2 && maintenant() >= r.second }
    fun repousser(profil: String, cle: String) { val n = (lire("rappel.$profil.$cle")?.first ?: 0) + 1; garderPreference("rappel.$profil.$cle", "$n|${maintenant() + SEMAINE}") }
    fun discretPermis(profil: String, cle: String) = maintenant() >= (lire("discret.$profil.$cle")?.second ?: 0L)
    fun marquerDiscret(profil: String, cle: String) = garderPreference("discret.$profil.$cle", "0|${maintenant() + SEMAINE}")
}

/** Étapes de mise en route, cochées automatiquement : (libellé, fait, cible). Cibles : assistant, fiche, apparence, comptes, membres, cotisations. */
suspend fun etapesMiseEnRoute(d: Donnees): List<Triple<String, Boolean, String>> {
    val fiche = Triple("Remplir votre fiche de membre", d.profil.memberId != null, "fiche")
    if (!d.peut("administrer")) return listOf(fiche)
    val comptes = try { Repo.tousLesComptes() } catch (_: Exception) { d.comptes }
    val nbComptes = try { Repo.profilsComplets().size + Repo.invitations().size } catch (_: Exception) { 1 }
    val cotis = try { Repo.cotisations(aujourdhui().year).isNotEmpty() } catch (_: Exception) { false }
    return listOfNotNull(
        if (!d.organisation.configuree) Triple("Configurer l’association avec l’assistant", false, "assistant") else null,
        fiche,
        Triple("Ajouter le logo et la photo de l’association", d.organisation.logo != null || d.organisation.banniere != null, "apparence"),
        Triple("Saisir les soldes de départ de la caisse et de la banque", comptes.any { it.soldeInitial != 0.0 }, "comptes"),
        Triple("Ajouter les membres (un par un ou par import)", d.membres.size > 1, "membres"),
        Triple("Désigner le bureau : président, secrétaire…", nbComptes > 1, "membres"),
        Triple("Générer les cotisations de l’année", cotis, "cotisations"),
    )
}

@Composable
fun ListeMiseEnRoute(etapes: List<Triple<String, Boolean, String>>, onChoix: (String) -> Unit) {
    etapes.forEach { (titre, fait, cible) ->
        Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable(enabled = !fait) { onChoix(cible) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(26.dp).background(if (fait) Couleurs.Bleu else Color.Transparent, CircleShape)
                .then(if (fait) Modifier else Modifier.border(2.dp, Couleurs.Bord, CircleShape)), contentAlignment = Alignment.Center) {
                if (fait) Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Text(titre, fontWeight = FontWeight.SemiBold, color = if (fait) Couleurs.Texte2 else Couleurs.Texte, modifier = Modifier.weight(1f),
                textDecoration = if (fait) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            if (!fait) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleMiseEnRoute(etapes: List<Triple<String, Boolean, String>>, onChoix: (String) -> Unit, onPlusTard: () -> Unit, onFermer: () -> Unit) {
    val reste = etapes.count { !it.second }
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mise en route", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (reste > 0) "$reste étape${if (reste > 1) "s" else ""} restante${if (reste > 1) "s" else ""}. Touchez une étape pour la faire maintenant." else "Tout est en place.", color = Couleurs.Texte2)
            ListeMiseEnRoute(etapes) { onFermer(); onChoix(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onPlusTard) { Text("Me le rappeler dans une semaine") }
                Button(onClick = onFermer) { Text("Fermer") }
            }
        }
    }
}

@Composable
fun ParamMiseEnRoute(d: Donnees, onChoix: (String) -> Unit) {
    var etapes by remember { mutableStateOf<List<Triple<String, Boolean, String>>>(emptyList()) }
    LaunchedEffect(Unit) { etapes = etapesMiseEnRoute(d) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { item { CarteBlanche { ListeMiseEnRoute(etapes, onChoix) } } }
}
