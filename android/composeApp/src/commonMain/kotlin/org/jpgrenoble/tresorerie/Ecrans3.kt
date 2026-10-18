package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.daysUntil
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.animation.togetherWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val JOURS_MOIS = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private fun nombre(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").toDoubleOrNull()

// =====================================================================
// Paramètres : menu par thème (Mon compte, Association, Finances, Accès, Données) et section choisie ;
// deux volets sur tablette, menu puis section sur téléphone (comme le site)
// =====================================================================
@Composable
fun EcranParametres(d: Donnees, message: (String) -> Unit, recharger: () -> Unit, onAssistant: () -> Unit = {}, onFermer: () -> Unit = {},
                    sectionInitiale: String? = null, onCible: (String) -> Unit = {}) {
    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(sectionInitiale) }
    val toutes = sectionsParametres(d).flatMap { it.second }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= 840.dp
        val choisie = toutes.firstOrNull { it.cle == section } ?: if (large) toutes.first() else null
        @Composable fun Contenu(c: SectionParam) = when (c.cle) {
            "compte" -> ParamCompte(d, message, recharger)
            "securite" -> ParamSecurite(message)
            "notifications" -> ParamNotifications()
            "association" -> ParamIdentite(d, message, recharger)
            "apparence" -> ParamAssociation(d, message, recharger)
            "exercices" -> ParamExercices(d, message, recharger)
            "comptes" -> ParamComptes(d, message, recharger)
            "categories" -> ParamCategories(d, message, recharger)
            "regles" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { item { BlocMontants(message) } }
            "personnes" -> ParamPersonnes(d, message, recharger) { onCible("membres") }
            "roles" -> ParamRoles(d, message, recharger)
            "corbeille" -> ParamCorbeille(d, message, recharger)
            "sauvegarde" -> ParamSauvegarde(message)
            else -> ParamMiseEnRoute(d) { cible ->
                when (cible) { "assistant" -> onAssistant(); "apparence", "comptes" -> section = cible; else -> onCible(cible) }
            }
        }
        // Page ordinaire de l'application : même fond, titre « Paramètres » ; sur grand écran le menu à gauche et la section à droite
        if (large) Row(Modifier.fillMaxSize()) {
            MenuParametres(d, choisie?.cle, { section = it }, Modifier.width(360.dp).fillMaxHeight())
            Column(Modifier.weight(1f)) { choisie?.let { c -> EnteteSection(c); Box(Modifier.weight(1f)) { Contenu(c) } } }
        } else androidx.compose.animation.AnimatedContent(choisie, transitionSpec = {
            if (targetState != null) (androidx.compose.animation.slideInHorizontally { it / 4 } + androidx.compose.animation.fadeIn()) togetherWith androidx.compose.animation.fadeOut()
            else (androidx.compose.animation.slideInHorizontally { -it / 4 } + androidx.compose.animation.fadeIn()) togetherWith androidx.compose.animation.fadeOut()
        }) { c ->
            if (c == null) MenuParametres(d, null, { section = it }, Modifier.fillMaxSize())
            else Column(Modifier.fillMaxSize()) { EnteteSection(c) { section = null }; Box(Modifier.weight(1f)) { Contenu(c) } }
        }
        RetourSysteme(!large && section != null) { section = null }
    }
}

@Composable
private fun ParamSauvegarde(message: (String) -> Unit) {
    val enregistrer = rememberEnregistrer { it?.let(message) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            CarteBlanche {
                Text("Toutes les données (association, membres, opérations, demandes, budget, matériel, exercices, corbeille) dans un fichier à conserver hors ligne, au moins à chaque clôture d’exercice. Les pièces jointes s’archivent depuis Rapports (archive des pièces).", color = Couleurs.Texte2, fontSize = 14.sp)
                Button(onClick = { scope.launch { try { enregistrer("sauvegarde-tresorerie-${aujourdhui()}.json", "application/json", Repo.sauvegardeJson().encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Télécharger la sauvegarde")
                }
            }
        }
    }
}

// Mon compte : nom affiché, fiche de membre, mot de passe, déconnexion
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamCompte(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var nom by remember { mutableStateOf(d.profil.nom) }
    var mdp by remember { mutableStateOf("") }
    var creerFiche by remember { mutableStateOf(false) }
    var modifierFiche by remember { mutableStateOf(false) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val action = rememberAction(message, recharger)
    val fiche = d.membres.firstOrNull { it.id == d.profil.memberId }
    val email = remember { Repo.emailConnecte() }
    LaunchedEffect(Unit) { if (d.peut("administrer")) try { profils = Repo.profilsComplets() } catch (_: Exception) { } }
    val libres = d.membres.filter { m -> m.actif && profils.none { it.memberId == m.id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(d.profil.nom.substringBefore(' '), d.profil.nom.substringAfter(' ', ""), taille = 52)
                    Column {
                        Text(d.profil.nom, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (email.isNotBlank()) Text(email, color = Couleurs.Texte2, fontSize = 14.sp)
                        Text(d.nomRole(d.profil.role), color = Couleurs.SurBleuClair, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp).background(Couleurs.BleuClair, Formes.Moyen).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
                OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom affiché") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(enabled = nom.isNotBlank() && nom.trim() != d.profil.nom, modifier = Modifier.align(Alignment.End),
                    onClick = { action({ Repo.modifierMonNom(nom.trim()) }, "Nom enregistré") }) { Text("Enregistrer") }
            }
        }
        item {
            CarteBlanche {
                Text("Ma fiche de membre", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (fiche != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(fiche.prenom, fiche.nom)
                        Column {
                            Text(fiche.nomComplet, fontWeight = FontWeight.SemiBold)
                            Text("${fiche.jour} ${MOIS[fiche.mois - 1]}" + (fiche.profession?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                    if (d.peut("gerer_membres")) FilledTonalButton(onClick = { modifierFiche = true }) { Text("Modifier ma fiche") }
                } else if (d.peut("gerer_membres")) {
                    Text("Aucune fiche rattachée à votre compte.", color = Couleurs.Texte2)
                    Button(onClick = { creerFiche = true }) { Text("Remplir ma fiche") }
                    if (libres.isNotEmpty()) ChoixListe("Ou rattacher une fiche existante", "Choisir un membre", libres.map { it.nomComplet }) { i ->
                        val m = libres[i]; action({ Repo.lierProfil(d.profil.id, m.id, m.nomComplet) }, "Fiche rattachée")
                    }
                } else {
                    Text("Aucune fiche rattachée à votre compte.", color = Couleurs.Texte2)
                    Button(onClick = { creerFiche = true }) { Text("Remplir ma fiche") }
                }
            }
        }
    }
    if (creerFiche) FeuilleMaFiche(d, message) { ok -> creerFiche = false; if (ok) recharger() }
    if (modifierFiche) ModalBottomSheet(onDismissRequest = { modifierFiche = false }) {
        FicheMembre(fiche, d.membres, titre = null, onCree = { id, n -> Repo.lierProfil(d.profil.id, id, n) },
            onFini = { ok -> if (ok) { message("Fiche modifiée"); recharger() }; modifierFiche = false }, message = message)
    }
}

// Exécute une action, affiche le résultat, recharge si demandé
@Composable
private fun rememberAction(message: (String) -> Unit, apres: () -> Unit): (suspend () -> Unit, String) -> Unit {
    val scope = rememberCoroutineScope()
    return { bloc, ok -> scope.launch { try { bloc(); message(ok); apres() } catch (e: Exception) { message(traduireErreur(e)) } } }
}

@Composable
private fun ParamAssociation(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var nom by remember { mutableStateOf(d.organisation.nom) }
    var nouveauLogo by remember { mutableStateOf<Fichier?>(null) }
    var nouvelleBanniere by remember { mutableStateOf<Fichier?>(null) }
    var retirerBanniere by remember { mutableStateOf(false) }
    val banniereActuelle by Repo.banniere.collectAsState()
    val choixLogo = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) nouveauLogo = f }
    val choixBanniere = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) { nouvelleBanniere = f; retirerBanniere = false } }
    val action = rememberAction(message, recharger)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val apercu = remember(nouveauLogo) { nouveauLogo?.let { imageDepuisOctets(it.octets) } }
                    if (apercu != null) Image(apercu, "Nouveau logo", contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape))
                    else LogoAsso(Modifier.size(72.dp))
                    FilledTonalButton(onClick = choixLogo) { Text("Changer le logo") }
                }
                Text("Photo de la bannière d’accueil", fontWeight = FontWeight.SemiBold)
                val apercuBanniere = remember(nouvelleBanniere, banniereActuelle, retirerBanniere) {
                    (nouvelleBanniere?.octets ?: banniereActuelle.takeIf { !retirerBanniere })?.let { imageDepuisOctets(it) }
                }
                Box(Modifier.fillMaxWidth().height(120.dp).clip(Formes.XGrand).background(Couleurs.Surface2), contentAlignment = Alignment.Center) {
                    if (apercuBanniere != null) Image(apercuBanniere, "Bannière", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(if (retirerBanniere) "La photo sera retirée à l’enregistrement." else "Aucune photo. Format paysage conseillé, au moins 1600${NBSP}pixels de large.", fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = choixBanniere) { Text(if (apercuBanniere != null) "Changer la photo" else "Choisir une photo") }
                    if (apercuBanniere != null) TextButton(onClick = { nouvelleBanniere = null; retirerBanniere = true }) { Text("Retirer la photo") }
                }
                Button(enabled = nom.isNotBlank(), modifier = Modifier.align(Alignment.End), onClick = {
                    // Comme le site : rien de choisi, rien à enregistrer
                    if (nouveauLogo == null && nouvelleBanniere == null && !retirerBanniere) { message("Choisissez d’abord un logo ou une photo"); return@Button }
                    action({ Repo.majOrganisation(nom.trim(), nouveauLogo, nouvelleBanniere, retirerBanniere); nouveauLogo = null; nouvelleBanniere = null; retirerBanniere = false }, "Association enregistrée")
                }) { Text("Enregistrer") }
            }
        }
    }
}

// Montants et délais (même bloc que le site) : cotisation, périodicité, délai et seuil de justification,
// alerte budget, et « Comment régler » affiché aux membres sur leur page
@Composable
private fun BlocMontants(message: (String) -> Unit) {
    var cotisation by remember { mutableStateOf("") }
    var periodicite by remember { mutableStateOf(1) }
    var delai by remember { mutableStateOf("") }
    var seuil by remember { mutableStateOf("") }
    var seuilJustif by remember { mutableStateOf("") }
    var infos by remember { mutableStateOf("") }
    var rappelCotis by remember { mutableStateOf(RAPPEL_DEFAUT.getValue("cotisation")) }
    var rappelPart by remember { mutableStateOf(RAPPEL_DEFAUT.getValue("participation")) }
    val action = rememberAction(message) {}
    LaunchedEffect(Unit) {
        try {
            val r = Repo.reglages()
            cotisation = montantSaisie(r["cotisation_montant"] ?: 0.0)
            periodicite = (r["cotisation_periode_mois"] ?: 1.0).toInt()
            delai = (r["delai_justificatif_jours"] ?: 7.0).toInt().toString()
            seuil = (r["seuil_alerte_budget_pct"] ?: 90.0).toInt().toString()
            seuilJustif = (r["seuil_justification"] ?: 100.0).toInt().toString()
            infos = Repo.texteReglage("infos_paiement") ?: ""
            rappelCotis = Repo.texteReglage("message_rappel") ?: RAPPEL_DEFAUT.getValue("cotisation")
            rappelPart = Repo.texteReglage("message_rappel_participation") ?: RAPPEL_DEFAUT.getValue("participation")
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    // Même ordre et mêmes libellés que paramMontants du site
    CarteBlanche {
        Text("Cotisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(cotisation, { cotisation = it }, label = { Text("Montant (€ par période)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChoixListe("Périodicité", PERIODICITES[periodicite] ?: "", PERIODICITES.values.toList()) { periodicite = PERIODICITES.keys.toList()[it] }
        OutlinedTextField(infos, { infos = it.take(400) }, label = { Text("Comment régler (affiché aux membres)") },
            placeholder = { Text("Virement : IBAN FR76…\nEspèces : auprès du trésorier après le culte") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Petit rappel aux membres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text("Message proposé quand on rappelle une cotisation ou une participation. Restez chaleureux. Mots remplacés : {prénom}, {association}, {montant}, {collecte}, {règlement} (comment régler), {lien} (espace membre).",
            fontSize = 13.sp, color = Couleurs.Texte2)
        OutlinedTextField(rappelCotis, { rappelCotis = it.take(1500) }, label = { Text("Cotisation") }, minLines = 8, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(rappelPart, { rappelPart = it.take(1500) }, label = { Text("Participation") }, minLines = 6, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { rappelCotis = RAPPEL_DEFAUT.getValue("cotisation"); rappelPart = RAPPEL_DEFAUT.getValue("participation") }) { Text("Rétablir les messages proposés") }
        Text("Demandes de dépense et budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        OutlinedTextField(delai, { delai = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Délai du justificatif (jours après paiement)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(seuilJustif, { seuilJustif = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Justification obligatoire à partir de (€)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(seuil, { seuil = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Alerte budget (% du prévu)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(modifier = Modifier.align(Alignment.End), enabled = nombre(cotisation) != null && delai.isNotBlank() && seuil.isNotBlank() && seuilJustif.isNotBlank(), onClick = {
            action({
                Repo.majReglage("cotisation_montant", nombre(cotisation)!!)
                Repo.majReglage("cotisation_periode_mois", periodicite.toDouble())
                Repo.majReglage("delai_justificatif_jours", delai.toDouble())
                Repo.majReglage("seuil_alerte_budget_pct", seuil.toDouble())
                Repo.majReglage("seuil_justification", seuilJustif.toDouble())
                Repo.majTexteReglage("infos_paiement", infos.trim().ifBlank { null })
                // Enregistré seulement s'il diffère du texte proposé (sinon il suit les améliorations futures), comme le site
                Repo.majTexteReglage("message_rappel", rappelCotis.trim().takeIf { it.isNotEmpty() && it != RAPPEL_DEFAUT["cotisation"] }, "Message de rappel (cotisation)")
                Repo.majTexteReglage("message_rappel_participation", rappelPart.trim().takeIf { it.isNotEmpty() && it != RAPPEL_DEFAUT["participation"] }, "Message de rappel (participation)")
            }, "Paramètres enregistrés")
        }) { Text("Enregistrer") }
    }
}

// Comptes (comme paramComptes du site) : par compte, icône, « <nom> · solde de départ (€) », case Actif, corbeille ;
// « Ajouter un compte » et « Enregistrer » (soldes et cases ensemble) en bas
@Composable
private fun ParamComptes(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var soldes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var actifs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var feuille by remember { mutableStateOf<String?>(null) }
    val action = rememberAction(message) { version++; recharger() }
    LaunchedEffect(version, Synchro.version) {
        try {
            comptes = Repo.tousLesComptes().sortedBy { it.nom.lowercase() }
            soldes = comptes.associate { it.id to montantSaisie(it.soldeInitial) }; actifs = comptes.associate { it.id to it.actif }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val supprimerCompte = rememberSuppression(message) { version++; recharger() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                comptes.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (c.type == "caisse") Icons.Outlined.Payments else Icons.Outlined.AccountBalance, null, tint = Couleurs.Texte2)
                        OutlinedTextField(soldes[c.id] ?: "", { v -> soldes = soldes + (c.id to v) }, label = { Text("${c.nom} · solde de départ (€)") },
                            singleLine = true, isError = nombre(soldes[c.id] ?: "") == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Row(Modifier.clip(Formes.XGrand).clickable { actifs = actifs + (c.id to !(actifs[c.id] ?: c.actif)) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(actifs[c.id] ?: c.actif, { v -> actifs = actifs + (c.id to v) }); Text("Actif", fontSize = 14.sp)
                        }
                        IconButton(onClick = { supprimerCompte("accounts", c.id, c.nom) }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer ${c.nom}", tint = Couleurs.Texte2) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { feuille = "compte" }) { Text("Ajouter un compte") }
                    Button(enabled = comptes.all { nombre(soldes[it.id] ?: "") != null }, onClick = {
                        action({
                            comptes.forEach { c ->
                                val v = nombre(soldes[c.id]!!)!!; val a = actifs[c.id] ?: c.actif
                                if (v != c.soldeInitial || a != c.actif) Repo.majCompte(c.id, v, a)
                            }
                        }, "Comptes enregistrés")
                    }) { Text("Enregistrer") }
                }
            }
        }
        item { Text("Un compte déjà utilisé ne se supprime pas : décochez «${NBSP}Actif${NBSP}» pour le masquer.", color = Couleurs.Texte2, fontSize = 13.sp) }
    }
    if (feuille == "compte") {
        var nomC by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("banque") }
        var solde by remember { mutableStateOf("0") }
        DialogueSimple("Nouveau compte", "Ajouter", nomC.isNotBlank() && nombre(solde.ifBlank { "0" }) != null, { feuille = null }, {
            action({ Repo.ajouterCompte(NouveauCompte(nomC.trim(), type, nombre(solde.ifBlank { "0" })!!)) }, "Compte ajouté"); feuille = null
        }) {
            OutlinedTextField(nomC, { nomC = it.take(60) }, label = { Text("Nom *") }, placeholder = { Text("Livret A") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ChoixListe("Type", if (type == "caisse") "Caisse" else "Banque", listOf("Banque", "Caisse")) { type = if (it == 1) "caisse" else "banque" } }
                OutlinedTextField(solde, { solde = it }, label = { Text("Solde de départ (€)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
    }
}

// Rôles et droits : un rôle choisi en haut, ses droits en interrupteurs, groupés
@Composable
private fun ParamRoles(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var roles by remember { mutableStateOf<List<Role>>(emptyList()) }
    var permissions by remember { mutableStateOf<List<Permission>>(emptyList()) }
    var liens by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    var choisi by remember { mutableStateOf<String?>(null) }
    var feuille by remember { mutableStateOf<String?>(null) }   // "nouveau", "renommer", "supprimer"
    val action = rememberAction(message) { version++; recharger() }
    LaunchedEffect(version, Synchro.version) {
        try {
            roles = Repo.roles(); permissions = Repo.permissions()
            liens = Repo.rolePermissions().map { it.role to it.permission }.toSet()
            profils = Repo.profilsComplets()
            if (choisi == null || roles.none { it.code == choisi }) choisi = roles.firstOrNull()?.code
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val role = roles.firstOrNull { it.code == choisi }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                roles.forEach { r ->
                    val n = profils.count { it.role == r.code && it.actif }
                    FilterChip(selected = r.code == choisi, onClick = { choisi = r.code }, label = { Text(if (n > 0) "${r.nom} ($n)" else r.nom) })
                }
                AssistChip(onClick = { feuille = "nouveau" }, label = { Text("Nouveau rôle") }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
            }
        }
        if (role != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(role.nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { feuille = "renommer" }) { Text("Renommer") }
                    if (!role.systeme) TextButton(onClick = { feuille = "supprimer" }) { Text("Supprimer", color = Couleurs.Erreur) }
                }
            }
            if ((role.code to "valider_depenses") in liens && (role.code to "payer_depenses") in liens) item {
                Surface(color = Couleurs.JauneClair, contentColor = Couleurs.SurJaune, shape = Formes.Moyen) {
                    Text("${role.nom} peut valider et payer. La même personne ne pourra pas payer une dépense qu’elle a validée.", Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }
            permissions.groupBy { it.groupe }.forEach { (groupe, liste) ->
                item {
                    CarteBlanche {
                        Text(groupe, fontWeight = FontWeight.Bold, color = Couleurs.Texte2, fontSize = 14.sp)
                        liste.sortedBy { it.ordre }.forEach { p ->
                            val actif = (role.code to p.code) in liens
                            Row(Modifier.fillMaxWidth().clickable { action({ Repo.basculerDroit(role.code, p.code, !actif) }, if (actif) "Droit retiré" else "Droit accordé") }
                                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.libelle, modifier = Modifier.weight(1f), fontSize = 15.sp)
                                Switch(checked = actif, onCheckedChange = { v -> action({ Repo.basculerDroit(role.code, p.code, v) }, if (v) "Droit accordé" else "Droit retiré") })
                            }
                        }
                    }
                }
            }
            val membresRole = profils.filter { it.role == role.code }
            if (membresRole.isNotEmpty()) item {
                CarteBlanche {
                    Text("Personnes", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, fontSize = 14.sp)
                    membresRole.forEach { Text(it.nom + if (!it.actif) " (désactivé)" else "") }
                }
            }
        }
    }
    when (feuille) {
        "nouveau" -> {
            var nom by remember { mutableStateOf("") }
            var modele by remember { mutableStateOf<Role?>(null) }
            DialogueSimple("Nouveau rôle", "Créer", nom.isNotBlank(), { feuille = null }, {
                val code = sansAccentsCode(nom)
                action({ Repo.creerRole(nom.trim(), modele?.code); choisi = code }, "Rôle créé"); feuille = null
            }) {
                OutlinedTextField(nom, { nom = it.take(40) }, label = { Text("Nom") }, placeholder = { Text("Secrétaire") }, singleLine = true)
                ChoixListe("Copier les droits de", modele?.nom ?: "Aucun", listOf("Aucun") + roles.map { it.nom }) { modele = if (it == 0) null else roles[it - 1] }
            }
        }
        "renommer" -> role?.let { r ->
            var nom by remember { mutableStateOf(r.nom) }
            DialogueSimple("Renommer le rôle", "Enregistrer", nom.isNotBlank(), { feuille = null }, {
                action({ Repo.renommerRole(r.code, nom.trim()) }, "Rôle renommé"); feuille = null
            }) {
                OutlinedTextField(nom, { nom = it.take(40) }, label = { Text("Nom") }, singleLine = true)
            }
        }
        "supprimer" -> role?.let { r ->
            DialogueSimple("Supprimer « ${r.nom} »$NBSP?", "Supprimer", true, { feuille = null }, {
                action({ Repo.supprimerRole(r.code); choisi = null }, "Rôle supprimé"); feuille = null
            }) {
                val n = profils.count { it.role == r.code }
                if (n > 0) Text("$n personne${if (n > 1) "s ont" else " a"} ce rôle. Attribuez-leur un autre rôle d’abord.", color = Couleurs.Erreur)
            }
        }
    }
}

@Composable
private fun ParamPersonnes(d: Donnees, message: (String) -> Unit, recharger: () -> Unit = {}, onMembres: () -> Unit = {}) {
    var version by remember { mutableStateOf(0) }
    var roles by remember { mutableStateOf(d.roles) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<Invitation>>(emptyList()) }
    var inviter by remember { mutableStateOf(false) }
    val action = rememberAction(message) { version++ }
    LaunchedEffect(version, Synchro.version) {
        try { roles = Repo.roles(); profils = Repo.profilsComplets().sortedBy { it.nom.lowercase() }; invitations = Repo.invitations() } catch (e: Exception) { message(traduireErreur(e)) }
    }
    fun nomRole(c: String) = roles.firstOrNull { it.code == c }?.nom ?: c
    // Même carte que paramPersonnes du site : bouton « Donner un accès » (vers Membres), texte, puis une ligne par compte
    // (avatar, nom, état, fiche de membre, rôle, case Actif) et les invitations en attente atténuées
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vue d’ensemble des comptes. Pour donner un accès à quelqu’un, ouvrez sa fiche dans Membres, puis «${NBSP}Fonction${NBSP}» : on est d’abord membre, puis on reçoit une fonction.",
                        color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = onMembres) { Text("Donner un accès") }
                }
                profils.forEachIndexed { i, p ->
                    key(p.id) {
                        if (i > 0) HorizontalDivider(color = Color(0xFFEDEBEA))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(p.nom.substringBefore(' '), p.nom.substringAfter(' ', ""), taille = 40)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.nom + if (p.id == d.profil.id) " (vous)" else "", fontWeight = FontWeight.SemiBold)
                                Text(if (p.actif) "Accès actif" else "Accès désactivé", fontSize = 13.sp, color = Couleurs.Texte2)
                                // Fiche de membre : les fiches actives sans autre compte, ou la sienne
                                val fiches = d.membres.filter { m -> m.actif && (m.id == p.memberId || profils.none { it.memberId == m.id }) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PuceMenu(fiches.firstOrNull { it.id == p.memberId }?.nomComplet ?: "Sans fiche de membre", false, listOf("Sans fiche de membre") + fiches.map { it.nomComplet }) { j ->
                                        val m = if (j == 0) null else fiches[j - 1]
                                        if (m?.id != p.memberId) action({ Repo.lierProfil(p.id, m?.id, m?.nomComplet); if (p.id == d.profil.id) recharger() }, "Fiche rattachée")
                                    }
                                    PuceMenu(nomRole(p.role), false, roles.map { it.nom }) { j ->
                                        val r = roles[j].code
                                        if (r != p.role) action({ Repo.majProfil(p.id, role = r); if (p.id == d.profil.id) recharger() }, "Rôle attribué")
                                    }
                                }
                            }
                            Row(Modifier.clip(Formes.XGrand).clickable { action({ Repo.majProfil(p.id, actif = !p.actif) }, if (!p.actif) "Accès réactivé" else "Accès désactivé") },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(p.actif, { v -> action({ Repo.majProfil(p.id, actif = v) }, if (v) "Accès réactivé" else "Accès désactivé") }); Text("Actif", fontSize = 14.sp)
                            }
                        }
                    }
                }
                invitations.forEach { inv ->
                    key("i-" + inv.email) {
                        HorizontalDivider(color = Color(0xFFEDEBEA))
                        Row(Modifier.fillMaxWidth().alpha(0.75f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) { Text("@", color = Couleurs.SurOrangeClair, fontWeight = FontWeight.Bold) }
                            Column(Modifier.weight(1f)) {
                                Text(inv.nom ?: inv.email, fontWeight = FontWeight.SemiBold)
                                Text("${inv.email} · invitation en attente", fontSize = 13.sp, color = Couleurs.Texte2)
                            }
                            Puce(nomRole(inv.role), Couleurs.Surface2, Couleurs.Texte2)
                            TextButton(onClick = { action({ Repo.retirerInvitation(inv.email) }, "Invitation retirée") }) { Text("Retirer") }
                        }
                    }
                }
            }
        }
    }
    if (inviter) {
        var email by remember { mutableStateOf("") }
        var nomI by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(roles.firstOrNull { it.code == "adherent" } ?: roles.firstOrNull()) }
        var membre by remember { mutableStateOf<Membre?>(null) }
        val emailOk = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
        DialogueSimple("Inviter une personne", "Inviter", emailOk && nomI.isNotBlank() && role != null, { inviter = false }, {
            action({ Repo.inviter(Invitation(email.trim().lowercase(), nomI.trim(), role!!.code, membre?.id)) }, "Invitation enregistrée"); inviter = false
        }) {
            OutlinedTextField(email, { email = it.trim() }, label = { Text("Adresse e-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(nomI, { nomI = it.take(60) }, label = { Text("Nom affiché") }, singleLine = true)
            ChoixListe("Rôle", role?.nom ?: "", roles.map { it.nom }) { role = roles[it] }
            val actifs = d.membres.filter { it.actif }
            ChoixListe("Fiche membre", membre?.nomComplet ?: "Aucune", listOf("Aucune") + actifs.map { it.nomComplet }) {
                membre = if (it == 0) null else actifs[it - 1]
                membre?.let { m -> if (nomI.isBlank()) nomI = m.nomComplet; if (email.isBlank()) m.email?.let { e -> email = e } }
            }
        }
    }
}

@Composable
fun DialogueSimple(titre: String, bouton: String, valide: Boolean, onAnnuler: () -> Unit, onValider: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnuler,
        title = { Text(titre) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenu) },
        confirmButton = { Button(enabled = valide, onClick = onValider) { Text(bouton) } },
        dismissButton = { TextButton(onClick = onAnnuler) { Text("Annuler") } },
    )
}


// =====================================================================
// Activités et planning
// =====================================================================
// Activité ou événement du planning ; « Demander une participation » crée la collecte liée
@Composable
fun FormulaireActivite(p: Projet?, onFini: (Boolean) -> Unit, message: (String) -> Unit, dateInitiale: String? = null, typeInitial: String = "evenement") {
    var type by remember { mutableStateOf(p?.type ?: typeInitial) }
    var nom by remember { mutableStateOf(p?.nom ?: "") }
    var debut by remember { mutableStateOf(p?.debut?.let { dateFr(it) } ?: dateInitiale?.let { dateFr(it) } ?: "") }
    var fin by remember { mutableStateOf(p?.fin?.takeIf { it != p.debut }?.let { dateFr(it) } ?: "") }
    var hDebut by remember { mutableStateOf(p?.heureDebut?.take(5) ?: "") }
    var hFin by remember { mutableStateOf(p?.heureFin?.take(5) ?: "") }
    var lieu by remember { mutableStateOf(p?.lieu ?: "") }
    var description by remember { mutableStateOf(p?.description ?: "") }
    var visible by remember { mutableStateOf(p?.visible ?: true) }
    var participation by remember { mutableStateOf(false) }
    var attendu by remember { mutableStateOf("") }
    var limite by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dDebut = if (debut.isBlank()) null else dateDepuisFr(debut)
    val dFin = if (fin.isBlank()) null else dateDepuisFr(fin)
    val hD = if (hDebut.isBlank()) "" else heureDepuisSaisie(hDebut)
    val hF = if (hFin.isBlank()) "" else heureDepuisSaisie(hFin)
    val dLimite = if (limite.isBlank()) null else dateDepuisFr(limite)
    val datesOk = (debut.isBlank() || dDebut != null) && (fin.isBlank() || dFin != null) && (dDebut == null || dFin == null || dFin >= dDebut) &&
        (type == "activite" || dDebut != null) && hD != null && hF != null && (hD.isEmpty() || hF.isEmpty() || dFin != null || hF >= hD) &&
        (!participation || ((attendu.isBlank() || lireMontant(attendu)?.let { it > 0 } == true) && (limite.isBlank() || dLimite != null)))
    DialogueSimple(if (p == null) "Nouveau rendez-vous" else "Modifier le rendez-vous", "Enregistrer", nom.isNotBlank() && datesOk && !enCours, { onFini(false) }, {
        enCours = true
        scope.launch {
            try {
                val n = NouveauProjet(nom.trim(), dDebut?.toString(), (dFin ?: dDebut)?.toString(), description.trim().ifBlank { null }, visible, type,
                    hD?.ifEmpty { null }, hF?.ifEmpty { null }, lieu.trim().ifBlank { null })
                val id = if (p == null) Repo.ajouterProjet(n) else { Repo.majProjet(p.id, n); p.id }
                if (participation) Repo.enregistrerCollecte(null, NouvelleCollecte("Participation$NBSP: ${n.nom}", id, lireMontant(attendu), null, dLimite?.toString(), true), emptyList())
                onFini(true)
            } catch (e: Exception) { message(traduireErreur(e)); onFini(false) }
        }
    }) {
        OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom") }, placeholder = { Text("Répétition, réunion, concert, sortie…") }, singleLine = true)
        ChampDate(debut, { debut = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
            isError = (debut.isNotBlank() && dDebut == null) || (type == "evenement" && debut.isBlank()))
        ChampDate(fin, { fin = it }, label = { Text("Jusqu’au") }, placeholder = { Text("Même jour") }, singleLine = true,
            isError = fin.isNotBlank() && (dFin == null || (dDebut != null && dFin < dDebut)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(hDebut, { hDebut = it.take(5) }, label = { Text("Heure de début") }, placeholder = { Text("18:30") }, singleLine = true,
                isError = hD == null, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(hFin, { hFin = it.take(5) }, label = { Text("Heure de fin") }, placeholder = { Text("20:00") }, singleLine = true,
                isError = hF == null, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(lieu, { lieu = it.take(120) }, label = { Text("Lieu") }, singleLine = true)
        OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description") }, minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { visible = !visible }) {
            Checkbox(visible, { visible = it })
            Column {
                Text("Visible de tous les membres", fontSize = 14.sp)
                Text("(décochez seulement pour un rendez-vous réservé au bureau)", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
        // Comme le site : une case « Suivre le budget » plutôt qu'un choix Événement / Activité
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { type = if (type == "activite") "evenement" else "activite" }) {
            Checkbox(type == "activite", { type = if (it) "activite" else "evenement" })
            Column {
                Text("Suivre le budget de cette activité", fontSize = 14.sp)
                Text("(ressources, emplois et résultat dans Budget$NBSP; la date peut rester à fixer)", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
        if (p == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { participation = !participation }) {
                Checkbox(participation, { participation = it }); Text("Demander une participation aux membres", fontSize = 14.sp)
            }
            if (participation) {
                OutlinedTextField(attendu, { attendu = it }, label = { Text("Montant par personne (€)") }, placeholder = { Text("Libre") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                ChampDate(limite, { limite = it }, label = { Text("Date limite") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
                    isError = limite.isNotBlank() && dLimite == null)
            }
        }
    }
}

// =====================================================================
// Membres : liste, fiche (ajout et modification), photo, import CSV
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranMembres(d: Donnees, message: (String) -> Unit, recharger: () -> Unit = {}) {
    var membres by remember { mutableStateOf(d.membres.sortedBy { sansAccentsCode(it.nom) }) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<Invitation>>(emptyList()) }
    var fonction by remember { mutableStateOf<Membre?>(null) }
    var maFiche by remember { mutableStateOf(false) }
    val admin = d.peut("administrer")
    var recherche by remember { mutableStateOf("") }
    var fiche by remember { mutableStateOf<Membre?>(null) }        // modification
    var detail by remember { mutableStateOf<Membre?>(null) }       // fiche du membre (coordonnées et actions)
    var importer by remember { mutableStateOf(false) }
    var menuActions by remember { mutableStateOf(false) }
    var ajout by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var aImporter by remember { mutableStateOf<Pair<List<NouveauMembre>, List<String>>?>(null) }
    var photos by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var liens by remember { mutableStateOf<List<LienMembre>>(emptyList()) }
    var lienDe by remember { mutableStateOf<Membre?>(null) }
    var tousLiens by remember { mutableStateOf(false) }
    var exporter by remember { mutableStateOf(false) }
    val gereLiens = d.peut("gerer_membres", "gerer_cotisations")
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    val scope = rememberCoroutineScope()
    var erreurImport by remember { mutableStateOf<String?>(null) }
    val choixCsv = rememberChoixTexte { texte ->
        if (texte != null) { erreurImport = null; aImporter = try { analyserCsvMembres(texte, membres) } catch (e: Exception) { erreurImport = e.message ?: "Fichier illisible"; null } }
    }
    LaunchedEffect(version, Synchro.version) {
        try {
            // Rechargé après chaque changement (ajout, suppression, restauration) et à chaque nouvelle de la base (temps réel)
            if (version > 0 || Synchro.version > 0) membres = Repo.membres()
            if (d.peut("administrer", "consulter_finances", "valider_depenses", "payer_depenses")) profils = Repo.profilsComplets()
            if (admin) invitations = Repo.invitations()
            if (gereLiens) liens = try { Repo.liens() } catch (_: Exception) { emptyList() }
            photos = membres.mapNotNull { m -> m.photo?.let { ch -> try { Repo.telecharger("photos", ch)?.let { imageDepuisOctets(it) }?.let { m.id to it } } catch (_: Exception) { null } } }.toMap()
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    // Fonction de chaque membre : rôle de son compte ou de son invitation
    fun fonctionDe(m: Membre): Pair<String, String>? =
        profils.firstOrNull { it.memberId == m.id }?.let { it.role to (if (it.actif) "compte" else "coupe") }
            ?: invitations.firstOrNull { it.membreId == m.id }?.let { it.role to "invite" }
    // Comme le site : la recherche ne change pas les sections (sans accents), le filtre Tous / Actifs / Inactifs est gardé
    val filtre = EtatMembres.filtre.takeIf { it in listOf("tous", "actifs", "inactifs") } ?: "tous"
    val cle = sansAccents(recherche)
    val vus = membres.filter { filtre == "tous" || (filtre == "actifs") == it.actif }
    val nbInactifs = membres.count { !it.actif }
    val nbActifs = membres.size - nbInactifs
    val bureau = vus.filter { fonctionDe(it)?.first?.let { r -> r != "adherent" } == true }.sortedBy { rangRole(fonctionDe(it)!!.first) }
    val autres = vus - bureau.toSet()
    val trouve = { m: Membre -> cle.isEmpty() || sansAccents(m.nomComplet).contains(cle) }
    @Composable fun PuceFonction(m: Membre) {
        val f = fonctionDe(m)
        when {
            f == null -> {}
            f.second == "invite" -> Puce("${d.nomRole(f.first)} · invité", Couleurs.JauneClair, Couleurs.SurJaune)
            f.second == "coupe" -> Puce("${d.nomRole(f.first)} · accès coupé", Couleurs.Surface2, Couleurs.Texte2)
            f.first == "adherent" -> Puce("Accès adhérent", Couleurs.Surface2, Couleurs.Texte2)
            else -> Puce(d.nomRole(f.first), Couleurs.BleuClair, Couleurs.SurBleuClair)
        }
    }
    @Composable fun AvatarMembre(m: Membre, taille: Int) {
        val img = photos[m.id]
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(taille.dp).clip(CircleShape))
        else Box(Modifier.size(taille.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) {
            Text("${m.prenom.take(1)}${m.nom.take(1)}", color = Couleurs.SurOrangeClair, fontWeight = FontWeight.Bold, fontSize = (taille / 3).sp)
        }
    }
    // Ligne sobre : photo, nom (« (vous) » atténué), anniversaire et profession, fonction ; un appui ouvre la fiche (comme le site)
    @Composable fun Ligne(m: Membre, derniere: Boolean) {
        Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable { detail = m }.padding(horizontal = 4.dp, vertical = 10.dp).alpha(if (m.actif) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically) {
            AvatarMembre(m, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (m.id == d.profil.memberId) Text(" (vous)", fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1)
                }
                Text("${m.jour} ${MOIS[m.mois - 1]}" + (m.profession?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PuceFonction(m)
                if (!m.actif) Puce("Inactif", Couleurs.Surface2, Couleurs.Texte2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
        }
        if (!derniere) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
    // Section en carte sobre (Bureau, Membres), comme les cartes du site
    @Composable fun Section(titre: String?, l: List<Membre>, vide: (@Composable () -> Unit)? = null) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                titre?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)) }
                val v = l.filter(trouve)
                v.forEachIndexed { k, m -> key(m.id) { Ligne(m, k == v.lastIndex) } }
                if (l.isEmpty()) vide?.invoke()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("Membres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("$nbActifs actif" + (if (nbActifs > 1) "s" else "") + if (nbInactifs > 0) " · $nbInactifs inactif" + (if (nbInactifs > 1) "s" else "") else "",
                        color = Couleurs.Texte2, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            // Recherche et menu « ⋯ » (liens personnels, import, export), comme le site ; seulement s'il y a des membres
            if (membres.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher un membre") }, singleLine = true,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Rechercher un membre" })
                    Box {
                        OutlinedIconButton(onClick = { menuActions = true }, shape = Formes.Moyen, modifier = Modifier.size(52.dp)) { Icon(Icons.Outlined.MoreHoriz, "Autres actions") }
                        DropdownMenu(menuActions, { menuActions = false }) {
                            if (gereLiens && membres.isNotEmpty()) DropdownMenuItem(text = { Text("Liens personnels") }, leadingIcon = { Icon(Icons.Outlined.Link, null) }, onClick = { menuActions = false; tousLiens = true })
                            if (d.peut("gerer_membres")) DropdownMenuItem(text = { Text("Importer (CSV, Excel)") }, leadingIcon = { Icon(Icons.Outlined.UploadFile, null) }, onClick = { menuActions = false; importer = true })
                            DropdownMenuItem(text = { Text("Exporter la liste") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { menuActions = false; exporter = true })
                        }
                    }
                }
            }
            if (membres.isNotEmpty() && nbInactifs > 0) item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Triple("tous", "Tous", membres.size), Triple("actifs", "Actifs", nbActifs), Triple("inactifs", "Inactifs", nbInactifs)).forEachIndexed { k, (c, l, n) ->
                        SegmentedButton(selected = filtre == c, onClick = { EtatMembres.filtre = c }, shape = SegmentedButtonDefaults.itemShape(k, 3), icon = {}) {
                            Text(buildAnnotatedString { append("$l "); withStyle(SpanStyle(fontSize = 12.sp, color = Couleurs.Texte2)) { append("$n") } }, maxLines = 1)
                        }
                    }
                }
            }
            if (bureau.isNotEmpty()) item(key = "bureau") { Section("Bureau", bureau) }
            item(key = "membres") {
                Section(if (bureau.isNotEmpty()) "Membres" else null, autres) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when { membres.isNotEmpty() && filtre == "inactifs" -> "Aucun membre inactif."; membres.isNotEmpty() -> "Tous ces membres font partie du bureau."; else -> "Aucun membre." },
                            color = Couleurs.Texte2)
                        if (membres.isEmpty() && d.peut("gerer_membres")) Text("Ajoutez-les avec le bouton +, ou importez votre liste avec ⋯.", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                }
            }
        }
        // Bouton + flottant (rond, couleur principale), comme le site sur téléphone
        if (d.peut("gerer_membres")) {
            BoutonPlus("Ajouter un membre", Modifier.align(Alignment.BottomEnd)) { ajout = true }
        }
    }
    val supprimerMembre = rememberSuppression(message) { version++; recharger() }
    if (ajout || fiche != null) ModalBottomSheet(onDismissRequest = { ajout = false; fiche = null }) {
        FicheMembre(fiche, membres, onFini = { ok -> if (ok) { message(if (fiche != null) "Fiche modifiée" else "Membre ajouté"); version++; recharger() }; ajout = false; fiche = null }, message,
            onSupprimer = if (d.peut("gerer_membres")) ({ fiche?.let { m -> supprimerMembre("members", m.id, m.nomComplet) }; fiche = null }) else null)
    }
    if (maFiche) ModalBottomSheet(onDismissRequest = { maFiche = false }) {
        FicheMembre(null, membres, titre = "Ma fiche de membre", onCree = { id, n -> Repo.lierProfil(d.profil.id, id, n) },
            onFini = { ok -> maFiche = false; if (ok) { message("Votre fiche est créée"); recharger() } }, message = message)
    }
    detail?.let { m -> FicheDetailMembre(d, m, { AvatarMembre(m, 64) }, { PuceFonction(m) }, gereLiens, message,
        onLien = { detail = null; lienDe = m }, onFonction = { detail = null; fonction = m }, onModifier = { detail = null; fiche = m },
        onActif = { detail = null; scope.launch {
            try { Repo.majMembre(m.id, NouveauMembre(m.prenom, m.nom, m.jour, m.mois, m.profession, m.whatsapp, m.consentement, null, m.email), !m.actif, null)
                message(if (m.actif) "${m.prenom} est inactif" else "${m.prenom} est réactivé"); version++; recharger() } catch (e: Exception) { message(traduireErreur(e)) } } },
        onSupprimer = if (d.peut("gerer_membres")) ({ detail = null; supprimerMembre("members", m.id, m.nomComplet) }) else null) { detail = null } }
    lienDe?.let { m ->
        ModalBottomSheet(onDismissRequest = { lienDe = null }) {
            FeuilleLien(d, m, liens.firstOrNull { it.membreId == m.id }, message, onChange = { scope.launch { liens = try { Repo.liens() } catch (_: Exception) { liens } } }) { lienDe = null }
        }
    }
    if (tousLiens) ModalBottomSheet(onDismissRequest = { tousLiens = false }) {
        FeuilleLiens(d, membres, liens, message, onChange = { scope.launch { liens = try { Repo.liens() } catch (_: Exception) { liens } } }) { tousLiens = false }
    }
    // Choix du format, comme choisirFormat du site : deux cartes, PDF puis Excel
    if (exporter) ModalBottomSheet(onDismissRequest = { exporter = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Exporter la liste des membres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                @Composable fun Choix(titre: String, sous: String, onClick: () -> Unit) =
                    Surface(onClick = onClick, shape = Formes.XGrand, color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E0DE)), modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(titre, fontWeight = FontWeight.Bold); Text(sous, fontSize = 13.sp, color = Couleurs.Texte2)
                        }
                    }
                Choix("PDF", "Mise en page pour imprimer, archiver ou transmettre") {
                    exporter = false; scope.launch { try { val (t, h) = documentHtml(d, "membres", aujourdhui().year); imprimer(t, h) } catch (e: Exception) { message(traduireErreur(e)) } }
                }
                Choix("Excel", "Tableau modifiable (CSV)") {
                    exporter = false; scope.launch { try { enregistrer("membres.csv", "text/csv", exportCsv(d, "membres", aujourdhui().year).encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } }
                }
            }
        }
    }
    fonction?.let { m ->
        FeuilleFonction(d, m, profils, invitations, onFini = { msg -> fonction = null; if (msg != null) { message(msg); version++; if (profils.any { it.id == d.profil.id && it.memberId == m.id }) recharger() } }, message = message)
    }
    // Import : explication, choix du fichier, modèle, puis le résultat dans la même feuille (comme feuilleImport du site)
    if (importer) ModalBottomSheet(onDismissRequest = { importer = false; aImporter = null; erreurImport = null }) {
        var enCours by remember { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Importer des membres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(buildAnnotatedString {
                append("Fichier CSV (depuis Excel : Enregistrer sous › CSV) avec une ligne d’en-têtes : ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("prénom, nom, jour, mois") }
                append(" (obligatoires), profession, whatsapp, email, accord (oui ou non). Les lignes incomplètes sont refusées.")
            }, fontSize = 14.sp, color = Couleurs.Texte2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = choixCsv) { Text("Choisir le fichier") }
                TextButton(onClick = { enregistrer("modele-membres.csv", "text/csv", csv(listOf("prénom", "nom", "jour", "mois", "profession", "whatsapp", "email", "accord"),
                    listOf(listOf("Marie", "Exemple", 14, 3, "Infirmière", "06 12 34 56 78", "", "oui"))).encodeToByteArray()) }) { Text("Télécharger le modèle") }
            }
            erreurImport?.let { Surface(color = Couleurs.ErreurClair, contentColor = Couleurs.SurErreurClair, shape = Formes.XGrand, modifier = Modifier.fillMaxWidth()) { Text(it, Modifier.padding(12.dp), fontSize = 14.sp) } }
            aImporter?.let { (ok, erreurs) ->
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${ok.size}") }; append(" ligne${if (ok.size > 1) "s" else ""} prête${if (ok.size > 1) "s" else ""}, ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${erreurs.size}") }; append(" refusée${if (erreurs.size > 1) "s" else ""}.")
                })
                if (erreurs.isNotEmpty()) Surface(color = Couleurs.ErreurClair, contentColor = Couleurs.SurErreurClair, shape = Formes.XGrand, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) { erreurs.forEach { Text(it, fontSize = 13.sp) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { importer = false; aImporter = null }) { Text("Annuler") }
                    Button(enabled = ok.isNotEmpty() && !enCours, onClick = {
                        enCours = true
                        scope.launch {
                            try { Repo.importerMembres(ok); importer = false; aImporter = null; message("${ok.size} membres importés"); version++; recharger() }
                            catch (e: Exception) { message(traduireErreur(e)) }
                            enCours = false
                        }
                    }) { Text("Importer ${ok.size} membre${if (ok.size > 1) "s" else ""}") }
                }
            }
        }
    }
}

private object EtatMembres { var filtre by mutableStateOf("tous") }

/** Fiche d'un membre : coordonnées et toutes les actions (lien personnel, fonction, modifier, inactif, supprimer), comme feuilleFicheMembre du site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicheDetailMembre(d: Donnees, m: Membre, avatar: @Composable () -> Unit, puceFonction: @Composable () -> Unit, gereLiens: Boolean, message: (String) -> Unit,
                              onLien: () -> Unit, onFonction: () -> Unit, onModifier: () -> Unit, onActif: () -> Unit, onSupprimer: (() -> Unit)?, onFermer: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                avatar()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.nomComplet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        puceFonction()
                        if (m.actif) Puce("Actif", Couleurs.BleuClair, Couleurs.SurBleuClair) else Puce("Inactif", Couleurs.Surface2, Couleurs.Texte2)
                        if (!m.consentement) Puce("Anniversaire privé", Couleurs.Surface2, Couleurs.Texte2)
                    }
                }
            }
            @Composable fun Info(l: String, v: String, lien: String? = null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(l, color = Couleurs.Texte2, modifier = Modifier.weight(1f))
                    Text(v, color = if (lien != null) Couleurs.Bleu else Couleurs.Texte, modifier = if (lien != null) Modifier.clickable { runCatching { uri.openUri(lien) } } else Modifier)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            Info("Anniversaire", "${m.jour} ${MOIS[m.mois - 1]}")
            m.profession?.let { Info("Profession", it) }
            m.whatsapp?.let { w -> Info("WhatsApp", w, numeroWa(w)?.let { "https://wa.me/$it" }) }
            m.email?.let { Info("E-mail", it, "mailto:$it") }
            @Composable fun Action(icone: androidx.compose.ui.graphics.vector.ImageVector, titre: String, sous: String, onClick: () -> Unit) {
                Row(Modifier.fillMaxWidth().clip(Formes.XGrand).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icone, null, tint = Couleurs.Orange)
                    Column(Modifier.weight(1f)) { Text(titre, fontWeight = FontWeight.SemiBold); Text(sous, fontSize = 13.sp, color = Couleurs.Texte2) }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                }
            }
            if (gereLiens && m.actif) Action(Icons.Outlined.Link, "Lien personnel", "Sa page de cotisation et de rendez-vous, sans compte", onLien)
            if (d.peut("administrer")) Action(Icons.Outlined.ManageAccounts, "Fonction et accès", "Membre du bureau, compte sur la plateforme", onFonction)
            if (d.peut("gerer_membres")) {
                Action(Icons.Outlined.Edit, "Modifier la fiche", "Nom, anniversaire, coordonnées, photo", onModifier)
                Action(if (m.actif) Icons.Outlined.Lock else Icons.Outlined.CheckCircle, if (m.actif) "Rendre inactif" else "Réactiver",
                    if (m.actif) "Il n’apparaît plus dans les cotisations ni les relances" else "Il revient dans les cotisations et les relances", onActif)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onSupprimer?.let { BoutonSupprimer(it) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onFermer) { Text("Fermer") }
            }
        }
    }
}

private val ORDRE_ROLES = listOf("president", "vice_president", "tresorier", "tresorier_adjoint", "secretaire", "bureau")
internal fun rangRole(r: String): Int = ORDRE_ROLES.indexOf(r).let { if (it >= 0) it else if (r == "adherent") 99 else 50 }

// Accès et fonction d'un membre : donner un accès (invitation), changer sa fonction, couper l'accès
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleFonction(d: Donnees, m: Membre, profils: List<Profil>, invitations: List<Invitation>, onFini: (String?) -> Unit, message: (String) -> Unit) {
    val compte = profils.firstOrNull { it.memberId == m.id }
    val invitation = invitations.firstOrNull { it.membreId == m.id }
    val roles = d.roles.sortedBy { rangRole(it.code) }
    var role by remember { mutableStateOf(compte?.role ?: invitation?.role ?: "adherent") }
    var actif by remember { mutableStateOf(compte?.actif ?: true) }
    var email by remember { mutableStateOf(m.email ?: "") }
    var lier by remember { mutableStateOf<Profil?>(null) }
    var envoye by remember { mutableStateOf(false) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val libres = profils.filter { it.memberId == null }
    val emailOk = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    ModalBottomSheet(onDismissRequest = { onFini(if (envoye) "Accès accordé" else null) }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Accès et fonction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(m.prenom, m.nom)
                Column { Text(m.nomComplet, fontWeight = FontWeight.SemiBold); Text(m.profession ?: "Membre", fontSize = 13.sp, color = Couleurs.Texte2) }
            }
            // État de l'accès : pastille et nom du compte (ou adresse invitée), comme feuilleAcces du site
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    compte != null -> { if (compte.actif) Puce("Compte actif", Couleurs.BleuClair, Couleurs.SurBleuClair) else Puce("Accès coupé", Couleurs.Surface2, Couleurs.Texte2)
                        Text(compte.nom, fontSize = 14.sp, color = Couleurs.Texte2) }
                    invitation != null -> { Puce("Invitation en attente", Couleurs.JauneClair, Couleurs.SurJaune); Text(invitation.email, fontSize = 14.sp, color = Couleurs.Texte2) }
                    else -> Puce("Sans accès", Couleurs.Surface2, Couleurs.Texte2)
                }
            }
            if (compte == null && invitation == null && !envoye)
                OutlinedTextField(email, { email = it.trim() }, label = { Text("E-mail de connexion *") }, singleLine = true, enabled = lier == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (!envoye) ChoixListe("Fonction", roles.firstOrNull { it.code == role }?.nom ?: role, roles.map { it.nom }) { role = roles[it].code }
            if (compte != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = compte.id != d.profil.id) { actif = !actif }) {
                Checkbox(if (compte.id == d.profil.id) true else actif, { actif = it }, enabled = compte.id != d.profil.id); Text("Accès à la plateforme", fontSize = 14.sp)
            }
            if (invitation != null) Text("La personne crée son compte sur le site avec cette adresse ; sa fonction s’applique dès la création.", fontSize = 14.sp, color = Couleurs.Texte2)
            if (compte == null && invitation == null && libres.isNotEmpty() && !envoye)
                ChoixListe("Ou rattacher un compte déjà créé", lier?.nom ?: "Aucun", listOf("Aucun") + libres.map { it.nom }) { lier = if (it == 0) null else libres[it - 1] }
            if (envoye) {
                // Après l'invitation : prévenir la personne (WhatsApp, e-mail), comme le site
                Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.XGrand) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Accès accordé.") }; append(" ${m.prenom} crée son compte sur le site avec ${email.trim().lowercase()}.") }, fontSize = 14.sp)
                        val texte = "Bonjour ${m.prenom}, votre accès à la trésorerie ${d.organisation.nom} est prêt. Créez votre compte sur ${Config.SITE_URL} avec l’adresse ${email.trim().lowercase()}."
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            numeroWa(m.whatsapp ?: "")?.let { wa -> FilledTonalButton(onClick = { uri.openUri("https://wa.me/$wa?text=" + encoderUrl(texte)) }) { Text("Prévenir par WhatsApp") } }
                            TextButton(onClick = { runCatching { uri.openUri("mailto:${email.trim().lowercase()}?subject=" + encoderUrl("Accès à la trésorerie") + "&body=" + encoderUrl(texte)) } }) { Text("Prévenir par e-mail") }
                        }
                    }
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                if (invitation != null) TextButton(onClick = { scope.launch { try { Repo.retirerInvitation(invitation.email); onFini("Invitation annulée") } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Annuler l’invitation") }
                else TextButton(onClick = { onFini(null) }) { Text("Fermer") }
                Button(enabled = !enCours && (compte != null || invitation != null || lier != null || emailOk), onClick = {
                    enCours = true
                    scope.launch {
                        try {
                            when {
                                compte != null -> { Repo.majProfil(compte.id, role = role, actif = if (compte.id == d.profil.id) null else actif); onFini("Fonction enregistrée") }
                                invitation != null -> { Repo.majInvitation(invitation.email, role); onFini("Fonction enregistrée") }
                                lier != null -> { Repo.majProfil(lier!!.id, role = role); Repo.lierProfil(lier!!.id, m.id, m.nomComplet); onFini("Compte rattaché") }
                                profils.any { it.nom.lowercase() == email.trim().lowercase() } -> { enCours = false; message("Un compte existe déjà avec cette adresse : rattachez-le") }
                                else -> { Repo.inviter(Invitation(email.trim().lowercase(), m.nomComplet, role, m.id)); envoye = true; enCours = false }
                            }
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }) { Text(if (compte != null || invitation != null) "Enregistrer" else "Donner l’accès") }
            }
        }
    }
}

@Composable
private fun FicheMembre(m: Membre?, existants: List<Membre>, onFini: (Boolean) -> Unit, message: (String) -> Unit,
                        titre: String? = null, onCree: (suspend (String, String) -> Unit)? = null, onSupprimer: (() -> Unit)? = null) {
    var prenom by remember { mutableStateOf(m?.prenom ?: "") }
    var nom by remember { mutableStateOf(m?.nom ?: "") }
    var jour by remember { mutableStateOf(m?.jour) }
    var mois by remember { mutableStateOf(m?.mois) }
    var profession by remember { mutableStateOf(m?.profession ?: "") }
    var whatsapp by remember { mutableStateOf(m?.whatsapp ?: "") }
    var email by remember { mutableStateOf(m?.email ?: "") }
    var accord by remember { mutableStateOf(m?.consentement ?: false) }
    var actif by remember { mutableStateOf(m?.actif ?: true) }
    var photo by remember { mutableStateOf<Fichier?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val choixPhoto = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) photo = f }
    val joursMax = mois?.let { JOURS_MOIS[it - 1] } ?: 31
    val valide = prenom.isNotBlank() && nom.isNotBlank() && jour != null && mois != null && (whatsapp.isBlank() || numeroWa(whatsapp) != null)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titre ?: if (m == null) "Nouveau membre" else "Modifier la fiche", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val apercu = remember(photo) { photo?.let { imageDepuisOctets(it.octets) } }
            if (apercu != null) Image(apercu, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape))
            else Avatar(prenom, nom, 56)
            FilledTonalButton(onClick = choixPhoto) { Text(if (m?.photo != null || photo != null) "Changer la photo" else "Ajouter une photo") }
            Text("Facultatif", fontSize = 13.sp, color = Couleurs.Texte2)
        }
        OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Anniversaire (jour et mois, sans l’année) *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ChoixListe("Jour", jour?.toString() ?: "–", (1..joursMax).map { it.toString() }) { jour = it + 1 } }
            Box(Modifier.weight(1f)) { ChoixListe("Mois", mois?.let { MOIS[it - 1] } ?: "–", MOIS) { mois = it + 1; if ((jour ?: 0) > JOURS_MOIS[it]) jour = null } }
        }
        OutlinedTextField(profession, { profession = it.take(80) }, label = { Text("Profession (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp") }, placeholder = { Text("06 12 34 56 78") }, singleLine = true,
            isError = whatsapp.isNotBlank() && numeroWa(whatsapp) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.trim() }, label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { accord = !accord }) {
            Checkbox(accord, { accord = it })
            Text("Le membre accepte que son anniversaire soit affiché aux autres membres", fontSize = 14.sp)
        }
        if (m != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { actif = !actif }) {
            Checkbox(actif, { actif = it }); Text("Membre actif", fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (m != null && onSupprimer != null) BoutonSupprimer(onSupprimer)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && valide, onClick = {
                if (m == null && existants.any { it.nomComplet.lowercase() == "${prenom.trim()} ${nom.trim()}".lowercase() && it.jour == jour && it.mois == mois }) {
                    message("Ce membre existe déjà"); return@Button
                }
                enCours = true
                scope.launch {
                    try {
                        val n = NouveauMembre(prenom.trim(), nom.trim(), jour!!, mois!!, profession.trim().ifBlank { null }, whatsapp.trim().ifBlank { null },
                            accord, if (m == null) aujourdhui().toString() else null, email.trim().ifBlank { null })
                        if (m == null) {
                            val id = Repo.ajouterMembre(n)
                            if (photo != null) Repo.majMembre(id, n, true, photo)
                            onCree?.invoke(id, "${n.prenom} ${n.nom}")
                        } else Repo.majMembre(m.id, n, actif, photo)
                        onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
    }
}

private fun sansAccents(s: String): String {
    val de = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿœæ"; val vers = "aaaaaaceeeeiiiinooooouuuuyyoa"
    return s.lowercase().trim().map { c -> val i = de.indexOf(c); if (i >= 0) vers[i] else c }.joinToString("")
}

private fun lignesCsv(texte: String): List<List<String>> {
    val premiere = texte.lineSequence().firstOrNull() ?: ""
    val sep = if (premiere.count { it == ';' } >= premiere.count { it == ',' }) ';' else ','
    val lignes = mutableListOf<List<String>>(); var ligne = mutableListOf<String>(); val cell = StringBuilder(); var guill = false
    var i = 0
    while (i < texte.length) {
        val c = texte[i]
        if (guill) {
            if (c == '"' && i + 1 < texte.length && texte[i + 1] == '"') { cell.append('"'); i++ }
            else if (c == '"') guill = false else cell.append(c)
        } else when (c) {
            '"' -> guill = true
            sep -> { ligne.add(cell.toString()); cell.clear() }
            '\n', '\r' -> {
                if (c == '\r' && i + 1 < texte.length && texte[i + 1] == '\n') i++
                ligne.add(cell.toString()); cell.clear(); lignes.add(ligne); ligne = mutableListOf()
            }
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || ligne.isNotEmpty()) { ligne.add(cell.toString()); lignes.add(ligne) }
    return lignes.filter { l -> l.any { it.isNotBlank() } }
}

// Analyse un CSV de membres : renvoie les lignes valides et les motifs de refus
fun analyserCsvMembres(texte: String, existants: List<Membre>): Pair<List<NouveauMembre>, List<String>> {
    val lignes = lignesCsv(texte)
    if (lignes.size < 2) throw IllegalArgumentException("Fichier vide ou sans ligne d’en-têtes")
    val entetes = lignes[0].map { sansAccents(it) }
    fun col(vararg noms: String) = entetes.indexOfFirst { h -> noms.any { h == it || h.startsWith(it) } }
    val iPrenom = col("prenom"); val iNom = col("nom"); val iJour = col("jour"); val iMois = col("mois"); val iDate = col("anniversaire", "date")
    val iProf = col("profession", "metier"); val iWa = col("whatsapp", "telephone", "tel"); val iMail = col("email", "e-mail", "mail"); val iAccord = col("accord", "consent")
    if (iPrenom < 0 || iNom < 0 || ((iJour < 0 || iMois < 0) && iDate < 0))
        throw IllegalArgumentException("Colonnes attendues : prénom, nom, jour, mois (ou anniversaire au format jj/mm)")
    fun moisDe(s: String): Int? = s.trim().toIntOrNull() ?: MOIS.indexOfFirst { sansAccents(it) == sansAccents(s) }.takeIf { it >= 0 }?.plus(1)
    val ok = mutableListOf<NouveauMembre>(); val ko = mutableListOf<String>()
    lignes.drop(1).forEachIndexed { k, l ->
        fun g(i: Int) = if (i >= 0) l.getOrNull(i)?.trim() ?: "" else ""
        var jour = g(iJour).toIntOrNull(); var mois = moisDe(g(iMois))
        val dateTexte = g(iDate).ifBlank { if (g(iJour).contains('/')) g(iJour) else "" }
        if ((jour == null || mois == null) && dateTexte.isNotBlank()) {
            val p = dateTexte.split('/', '-', '.', ' ').filter { it.isNotBlank() }
            jour = p.getOrNull(0)?.toIntOrNull(); mois = p.getOrNull(1)?.let { moisDe(it) }
        }
        val erreurs = mutableListOf<String>()
        val prenom = g(iPrenom); val nom = g(iNom)
        if (prenom.isBlank()) erreurs += "prénom manquant"
        if (nom.isBlank()) erreurs += "nom manquant"
        if (mois == null || mois !in 1..12) erreurs += "mois invalide"
        else if (jour == null || jour !in 1..JOURS_MOIS[mois - 1]) erreurs += "jour invalide"
        val wa = g(iWa)
        if (wa.isNotBlank() && numeroWa(wa) == null) erreurs += "WhatsApp invalide"
        if (existants.any { sansAccents(it.nomComplet) == sansAccents("$prenom $nom") }) erreurs += "déjà enregistré"
        if (erreurs.isEmpty()) ok += NouveauMembre(prenom, nom, jour!!, mois!!, g(iProf).ifBlank { null }, wa.ifBlank { null },
            Regex("^(oui|o|yes|1|x|vrai)$", RegexOption.IGNORE_CASE).matches(g(iAccord)), aujourdhui().toString(), g(iMail).ifBlank { null })
        else ko += "Ligne ${k + 2} ($prenom $nom)$NBSP: ${erreurs.joinToString(", ")}"
    }
    return ok to ko
}

// =====================================================================
// Rapports : synthèse annuelle et rapport périodique (impression ou PDF), exports Excel
// =====================================================================
// Un seul formulaire pour tout exporter : le document, la période, le format
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranRapports(d: Donnees, message: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    val an = aujourdhui().year
    // (clé, libellé, formats, période : "libre" = exercice, mois, trimestre ou dates ; "annee" ; "" = sans période)
    val docs = buildList {
        add(Quadruple("rapport", "Rapport financier (assemblée générale ou période)", listOf("pdf"), "libre"))
        add(Quadruple("journal", "Journal des opérations", listOf("pdf", "excel"), "libre"))
        add(Quadruple("participations", "Participations aux activités", listOf("excel"), ""))
        add(Quadruple("demandes", "Registre des demandes de dépense", listOf("pdf", "excel"), "annee"))
        add(Quadruple("pieces", "Pièces justificatives (fichier ZIP classé par mois)", listOf("zip"), "annee"))
    }
    var doc by remember { mutableStateOf(docs.first()) }
    var type by remember { mutableStateOf("annee") }
    var annee by remember { mutableStateOf(an) }
    var mois by remember { mutableStateOf(aujourdhui().monthNumber) }
    var trimestre by remember { mutableStateOf((aujourdhui().monthNumber - 1) / 3) }
    var du by remember { mutableStateOf("01/${aujourdhui().monthNumber.toString().padStart(2, '0')}/$an") }
    var au by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var format by remember { mutableStateOf("pdf") }
    var enCours by remember { mutableStateOf(false) }
    var etat by remember { mutableStateOf<String?>(null) }
    if (format !in doc.c) format = doc.c.first()
    val libre = doc.d == "libre"
    fun fin(a: Int, m: Int) = LocalDate(a, m, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
    // Période choisie : début, fin, intitulé
    val periode: Triple<String, String, String>? = when {
        // Exercice déclaré (Paramètres, Exercices) : dates et intitulé exacts ; sinon l'année civile
        !libre || type == "annee" -> d.exercices.firstOrNull { it.debut.take(4).toInt() == annee }?.let { Triple(it.debut, it.fin, it.libelle.replaceFirstChar { c -> c.lowercase() }) }
            ?: Triple("$annee-01-01", "$annee-12-31", "exercice $annee")
        type == "mois" -> Triple(LocalDate(annee, mois, 1).toString(), fin(annee, mois).toString(), "${MOIS[mois - 1]} $annee")
        type == "trimestre" -> Triple(LocalDate(annee, trimestre * 3 + 1, 1).toString(), fin(annee, trimestre * 3 + 3).toString(), "${if (trimestre == 0) "1er" else "${trimestre + 1}e"} trimestre $annee")
        else -> { val a = dateDepuisFr(du); val b = dateDepuisFr(au); if (a != null && b != null && b >= a) Triple(a.toString(), b.toString(), "période du $du au $au") else null }
    }
    val noms = mapOf("pdf" to "PDF", "excel" to "Excel", "zip" to "ZIP", "json" to "fichier de sauvegarde")
    fun longue(iso: String) = LocalDate.parse(iso).let { "${if (it.dayOfMonth == 1) "1er" else it.dayOfMonth} ${MOIS[it.monthNumber - 1]} ${it.year}" }
    val rappel = doc.b + ", " + noms[format] + when { doc.d == "annee" -> " : exercice $annee"; doc.d.isNotEmpty() && periode != null -> " : du ${longue(periode.first)} au ${longue(periode.second)}"; else -> "" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Titre("Rapports et exports") }
        item {
            CarteBlanche {
                ChoixListe("Document", doc.b, docs.map { it.b }) { doc = docs[it] }
                if (libre) ChoixListe("Période", mapOf("annee" to "Exercice complet", "mois" to "Un mois", "trimestre" to "Un trimestre", "libre" to "Du… au…")[type]!!,
                    listOf("Exercice complet", "Un mois", "Un trimestre", "Du… au…")) { type = listOf("annee", "mois", "trimestre", "libre")[it] }
                // Exercices déclarés : intitulé et dates exactes, affichés tels quels une fois choisis (comme le site)
                val nomExercice = { a: Int -> d.exercices.firstOrNull { it.debut.take(4).toInt() == a }?.let { "${it.libelle} (${dateFr(it.debut)} – ${dateFr(it.fin)})" } ?: a.toString() }
                if (doc.d.isNotEmpty() && !(libre && type == "libre"))
                    ChoixListe("Exercice", nomExercice(annee), listOf(an, an - 1, an - 2).map(nomExercice)) { annee = an - it }
                if (libre && type == "mois") ChoixListe("Mois", MOIS[mois - 1].replaceFirstChar { it.uppercase() }, MOIS.map { m -> m.replaceFirstChar { it.uppercase() } }) { mois = it + 1 }
                val trimestres = listOf("1er trimestre (janv. à mars)", "2e trimestre (avr. à juin)", "3e trimestre (juil. à sept.)", "4e trimestre (oct. à déc.)")
                if (libre && type == "trimestre") ChoixListe("Trimestre", trimestres[trimestre], trimestres) { trimestre = it }
                if (libre && type == "libre") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampDate(du, { du = it }, label = { Text("Du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dateDepuisFr(du) == null, modifier = Modifier.weight(1f))
                    ChampDate(au, { au = it }, label = { Text("Au") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = periode == null, modifier = Modifier.weight(1f))
                }
                if (doc.c.size > 1) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    doc.c.forEachIndexed { i, f -> SegmentedButton(selected = format == f, onClick = { format = f }, shape = SegmentedButtonDefaults.itemShape(i, doc.c.size)) { Text(noms[f]!!) } }
                }
                Text(rappel, fontSize = 13.sp, color = Couleurs.Texte2)
                Button(enabled = !enCours && periode != null, modifier = Modifier.align(Alignment.End), onClick = {
                    val (deb, fi, intitule) = periode!!
                    enCours = true
                    scope.launch {
                        try {
                            when (doc.a) {
                                "rapport" -> imprimer("Rapport", rapportHtml(d, deb, fi, if (type == "annee") "Rapport financier de l’exercice $annee" else "Rapport de trésorerie, $intitule"))
                                "journal" -> if (format == "excel") enregistrer(if (deb == "$annee-01-01" && fi == "$annee-12-31") "ecritures-$annee.csv" else "ecritures-$deb-au-$fi.csv", "text/csv", exportCsv(d, "ecritures", annee, deb, fi).encodeToByteArray())
                                             else { val (t, html) = documentHtml(d, "journal", annee, deb, fi, intitule); imprimer(t, html) }
                                "pieces" -> {
                                    val (zip, n) = archivePieces(d, annee) { etat = it }
                                    etat = null
                                    enregistrer("${d.organisation.nom.lowercase().replace(Regex("[^a-z0-9]+"), "-")}-pieces-$annee.zip", "application/zip", zip)
                                    message("$n pièce${if (n > 1) "s" else ""} archivée${if (n > 1) "s" else ""}")
                                }
                                "sauvegarde" -> enregistrer("sauvegarde-tresorerie-${aujourdhui()}.json", "application/json", Repo.sauvegardeJson().encodeToByteArray())
                                "inventaire" -> if (format == "excel") enregistrer("inventaire-materiel-${aujourdhui()}.csv", "text/csv", csvInventaire(d, Repo.materiel()).encodeToByteArray())
                                                else { val (t, html) = documentHtml(d, "inventaire", annee); imprimer(t, html) }
                                else -> if (format == "excel") enregistrer(if (doc.a == "demandes") "demandes-de-depense.csv" else "${doc.a}-$annee.csv", "text/csv", exportCsv(d, doc.a, annee).encodeToByteArray())
                                        else { val (t, html) = documentHtml(d, doc.a, annee); imprimer(t, html) }
                            }
                        } catch (e: Exception) { message(traduireErreur(e)) }
                        enCours = false
                    }
                }) { Text(etat ?: "Exporter en ${noms[format]}") }
            }
        }
        item {
            Text("PDF$NBSP: mis en page pour imprimer, signer ou transmettre. Excel$NBSP: tableau modifiable (CSV). Cotisations, budget, membres et matériel s’exportent depuis leur page (bouton Exporter)$NBSP; la sauvegarde complète est dans Paramètres › Sauvegarde.", fontSize = 13.sp, color = Couleurs.Texte2)
        }
    }
}

private data class Quadruple(val a: String, val b: String, val c: List<String>, val d: String)

// CSV lisible par Excel en français : BOM, séparateur « ; », virgule décimale
internal fun csv(entetes: List<String>, lignes: List<List<Any?>>): String {
    fun cel(v: Any?): String {
        val s = when (v) { is Double -> v.toString().replace('.', ','); null -> ""; else -> v.toString() }
        return if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    return "﻿" + (listOf(entetes) + lignes).joinToString("\r\n") { l -> l.joinToString(";") { cel(it) } }
}

internal suspend fun exportCsv(d: Donnees, type: String, an: Int, du: String = "$an-01-01", au: String = "$an-12-31"): String {
    val cat = { id: String -> d.nomCategorie(id) }
    return when (type) {
        // Mêmes colonnes que exporter('ecritures') du site
        "ecritures" -> {
            val ms = try { Repo.membres() } catch (_: Exception) { d.membres }; val ts = try { Repo.tiers() } catch (_: Exception) { emptyList() }
            val cs = try { Repo.collectes() } catch (_: Exception) { emptyList() }; val ps = try { Repo.projets() } catch (_: Exception) { emptyList() }
            csv(listOf("Date", "Sens", "Libellé", "Tiers", "Rubrique", "Catégorie", "Activité", "Compte", "Mode", "Montant", "Rapprochée"),
                Repo.toutesEcritures().filter { it.date in du..au }.sortedBy { it.date }.map {
                    listOf(dateFr(it.date), if (it.virement != null) (if (it.sens == "recette") "Virement interne (entrée)" else "Virement interne (sortie)") else if (it.sens == "recette") "Recette" else "Dépense", it.libelle,
                        nomTiers(it, ms, ts), nomRubrique(it, cs), cat(it.categorieId), ps.firstOrNull { p -> p.id == it.projetId }?.nom ?: "",
                        d.comptes.firstOrNull { c -> c.id == it.compteId }?.nom, MODES[it.mode] ?: it.mode, it.signe, if (it.rapproche) "Oui" else "Non")
                })
        }
        // Mêmes colonnes que csvMembres du site (dont la fonction : rôle du compte ou de l'invitation)
        "membres" -> {
            val profils = try { Repo.profilsComplets() } catch (_: Exception) { emptyList() }
            val invitations = try { Repo.invitations() } catch (_: Exception) { emptyList() }
            fun fonction(m: Membre) = (profils.firstOrNull { it.memberId == m.id }?.role ?: invitations.firstOrNull { it.membreId == m.id }?.role)?.let { d.nomRole(it) } ?: ""
            csv(listOf("Prénom", "Nom", "Jour", "Mois", "Profession", "WhatsApp", "E-mail", "Fonction", "Accord anniversaire", "Actif"),
                Repo.membres().map { listOf(it.prenom, it.nom, it.jour, it.mois, it.profession, it.whatsapp, it.email, fonction(it), if (it.consentement) "Oui" else "Non", if (it.actif) "Oui" else "Non") })
        }
        // Registre des demandes (mêmes colonnes que exporter('demandes') du site)
        "demandes" -> {
            val ps = try { Repo.projets() } catch (_: Exception) { emptyList() }
            val statuts = mapOf("soumise" to "À valider", "validee" to "À payer", "refusee" to "Refusée", "payee" to "Justificatif attendu",
                "justifiee" to "Clôturée", "annulee" to "Annulée", "brouillon" to "Brouillon")
            csv(listOf("Date", "Objet", "Justification", "Pour le", "Catégorie", "Activité", "Montant", "Statut", "Validée le", "Payée le", "Empreinte signature"),
                Repo.demandes().sortedBy { it.creeLe }.map { r ->
                    listOf(dateFr(r.creeLe.take(10)), r.objet, r.justification ?: "", r.dateSouhaitee?.let { dateFr(it) } ?: "", cat(r.categorieId), ps.firstOrNull { it.id == r.projetId }?.nom ?: "",
                        r.montant, statuts[r.statut] ?: r.statut, r.valideeLe?.let { dateFr(it.take(10)) } ?: "", r.payeeLe?.let { dateFr(it.take(10)) } ?: "", r.empreinte ?: "")
                })
        }
        "cotisations" -> {
            val ms = Repo.membres()
            val pas = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt()
            csv(listOf("Prénom", "Nom", "Dû sur l’année", "Réglé", "Exigible", "Retard", "Réglé jusqu’à"), Repo.cotisations(an).map { c ->
                val m = ms.firstOrNull { it.id == c.membreId }
                listOf(m?.prenom, m?.nom, c.du, c.paye, c.exigible ?: 0.0, c.retard, c.regleJusqua?.let { nomPeriode(it, pas) })
            })
        }
        "participations" -> {
            val ms = Repo.membres(); val ts = Repo.tiers(); val cs = Repo.collectes()
            val ps = try { Repo.projets() } catch (_: Exception) { emptyList() }
            csv(listOf("Collecte", "Activité", "Date", "Tiers", "Montant"), Repo.toutesEcritures().filter { it.collecteId != null }.sortedBy { it.date }.map { e ->
                listOf(cs.firstOrNull { it.id == e.collecteId }?.nom, ps.firstOrNull { it.id == e.projetId }?.nom ?: "", dateFr(e.date), nomTiers(e, ms, ts), e.montant)
            })
        }
        else -> {
            val ps = Repo.projets()
            csv(listOf("Poste", "Type", "Activité", "Prévu", "Réalisé", "Écart", "Taux %"),
                Repo.budget(an).map { listOf(it.categorie, if (it.sens == "recette") "Ressource" else "Emploi", ps.firstOrNull { p -> p.id == it.projetId }?.nom ?: "", it.prevu, it.realise, it.prevu - it.realise, it.taux ?: 0.0) })
        }
    }
}

private fun h(s: String?) = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

// Rapport périodique synthétique pour l'assemblée générale : l'essentiel, faits marquants,
// graphiques utiles, contrôle interne, signatures. Imprimable en A4 (2 pages environ).
private suspend fun rapportHtml(d: Donnees, debut: String, finDemandee: String, titre: String): String {
    val fin = minOf(finDemandee, aujourdhui().toString())   // pas de solde à une date future
    val an = fin.take(4).toInt()
    val dDeb = LocalDate.parse(debut); val dFin = LocalDate.parse(fin)
    val jours = dFin.toEpochDays() - dDeb.toEpochDays() + 1
    val finPrec = dDeb.minus(DatePeriod(days = 1)).toString(); val debutPrec = dDeb.minus(DatePeriod(days = jours)).toString()
    val tout = Repo.toutesEcritures()
    val txs = tout.filter { it.date <= fin }
    val dans = txs.filter { it.date >= debut }
    val prec = tout.filter { it.date in debutPrec..finPrec }
    val cotis = Repo.cotisations(an)
    val collectes = try { Repo.collectes() } catch (_: Exception) { emptyList() }
    val budget = Repo.budget(an)
    val demandes = Repo.demandes().filter { it.payeeLe != null && it.payeeLe.take(10) in debut..fin }
    val rapps = Repo.rapprochements().filter { it.fin in debut..fin }
    val comptes = try { Repo.tousLesComptes() } catch (_: Exception) { Repo.comptes() }
    val pieces = try { Repo.pieces() } catch (_: Exception) { emptyList() }
    val nomCat = { id: String -> d.nomCategorie(id) }
    fun somme(l: List<Ecriture>, sens: String) = l.filter { it.sens == sens && it.estFlux }.sumOf { it.montant }
    val totR = somme(dans, "recette"); val totD = somme(dans, "depense")
    val precR = somme(prec, "recette"); val precD = somme(prec, "depense")
    val depart = comptes.sumOf { it.soldeInitial }
    val soldeDebut = depart + txs.filter { it.date < debut }.sumOf { it.signe }
    val soldeFin = depart + txs.sumOf { it.signe }
    val serie = serieMensuelle(txs, comptes, debut, fin)
    val serie12 = serieMensuelle(txs, comptes, LocalDate(dFin.year, dFin.monthNumber, 1).minus(DatePeriod(months = 11)).toString(), fin)
    val reserve = reserveEnMois(soldeFin, serie12)
    fun parCat(sens: String) = dans.filter { it.sens == sens && it.estFlux }.groupBy { it.categorieId }.map { (id, l) -> Element(nomCat(id), l.sumOf { it.montant }) }
    val rec = parCat("recette"); val dep = parCat("depense")
    fun pct(a: Double, b: Double) = if (b > 0) kotlin.math.round(100 * (a - b) / b).toInt() else null
    fun variation(a: Double, b: Double, hausseBonne: Boolean): String {
        val p = pct(a, b) ?: return ""
        val cls = if (p == 0) "" else if ((p > 0) == hausseBonne) "var-bon" else "var-mauvais"
        return "<span class=\"var $cls\">${if (p > 0) "+" else if (p < 0) "−" else ""}${kotlin.math.abs(p)}$NBSP%</span> vs période précédente"
    }
    val exigible = cotis.sumOf { it.exigible ?: 0.0 }; val encaisse = cotis.sumOf { it.paye }
    val enRetard = cotis.filter { it.retard > 0.005 }
    val parts = dans.filter { it.collecteId != null }
    val partsCol = collectes.mapNotNull { c -> parts.filter { it.collecteId == c.id }.takeIf { it.isNotEmpty() }?.let { l -> Triple(c, l.sumOf { it.montant }, l.map { it.membreId ?: it.tiersId ?: it.id }.toSet().size) } }
    val etatsDem = EtatsDemandes(demandes, tout)
    val sansJustif = demandes.filter { it.statut == "payee" && it.id !in etatsDem.annulees }
    val contrepassees = txs.mapNotNull { it.contrepasseDe }.toSet()
    val depSansPiece = dans.filter { e -> e.sens == "depense" && e.estFlux && e.montant > 0 && e.contrepasseDe == null && e.id !in contrepassees &&
        pieces.none { it.transactionId == e.id || (e.demandeId != null && it.demandeId == e.demandeId) } }
    val depasses = budget.filter { it.sens == "depense" && it.prevu > 0 && it.realise > it.prevu }
    val premierDep = dep.maxByOrNull { it.valeur }; val premiereRec = rec.maxByOrNull { it.valeur }
    fun pc(v: Double, t: Double) = kotlin.math.round(100 * v / (if (t > 0) t else 1.0)).toInt()
    val faits = listOfNotNull(
        "Résultat ${if (totR - totD >= 0) "excédentaire" else "déficitaire"} de <b>${euros(kotlin.math.abs(totR - totD))}</b>$NBSP: ${euros(totR)} de recettes pour ${euros(totD)} de dépenses.",
        pct(totD, precD)?.takeIf { kotlin.math.abs(it) >= 10 }?.let { "Dépenses en ${if (totD > precD) "hausse" else "baisse"} de ${kotlin.math.abs(it)}$NBSP% par rapport à la période précédente de même durée." },
        premierDep?.let { "Premier poste de dépense$NBSP: <b>${h(it.nom)}</b>, ${pc(it.valeur, totD)}$NBSP% des dépenses." },
        premiereRec?.let { "Première ressource$NBSP: <b>${h(it.nom)}</b>, ${pc(it.valeur, totR)}$NBSP% des recettes." },
        if (exigible > 0) "Cotisations$NBSP: ${pc(encaisse, exigible)}$NBSP% de l’exigible encaissé ; ${enRetard.size} membre${if (enRetard.size > 1) "s" else ""} en retard pour ${euros(enRetard.sumOf { it.retard })}." else null,
        reserve?.let { "La trésorerie couvre <b>${(kotlin.math.round(it * 10) / 10).toString().replace('.', ',')} mois</b> de dépenses courantes." },
        if (depasses.isNotEmpty()) "Budget dépassé sur ${depasses.size} poste${if (depasses.size > 1) "s" else ""}$NBSP: ${depasses.joinToString(", ") { h(it.categorie) }}." else null,
        if (sansJustif.isNotEmpty()) "<b>${sansJustif.size} dépense${if (sansJustif.size > 1) "s" else ""} payée${if (sansJustif.size > 1) "s" else ""} sans justificatif</b> à ce jour." else null,
    )
    fun tuile(lib: String, v: String, sous: String = "") = "<div class=\"tuile\"><span>$lib</span><b>$v</b>${if (sous.isNotEmpty()) "<small>$sous</small>" else ""}</div>"
    val budgetL = budget.filter { it.projetId == null }.map { LigneBudgetG(it.categorie, it.prevu, it.realise, it.sens) }
    val signeRes = if (totR - totD >= 0) "+" else "−"
    return """<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${h(titre)}</title>
<style>@page{size:A4;margin:14mm}body{font-family:sans-serif;color:#1C1B1A;margin:0;font-size:12px}h1{font-size:20px;margin:0}
h2{font-size:14px;margin:18px 0 6px;color:#C23E10;border-bottom:2px solid #FFDBCF;padding-bottom:3px;break-after:avoid}h3{font-size:13px;margin:14px 0 4px}
table{width:100%;border-collapse:collapse;break-inside:avoid}td,th{padding:4px 6px;border-bottom:1px solid #E5E0DE;text-align:left}th{background:#F6F6F6}.d{text-align:right;white-space:nowrap}
.tot td{font-weight:bold;border-top:2px solid #1C1B1A}.sig{display:flex;gap:40px;margin-top:36px}.sig div{flex:1;border-top:1px solid #1C1B1A;padding-top:6px;height:60px}.m{color:#5A5350}
.tuiles{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.tuile{background:#F6F6F6;border-radius:12px;padding:10px;display:flex;flex-direction:column;gap:2px}
.tuile>span{font-size:11px;color:#5A5350}.tuile b{font-size:18px}.tuile small{font-size:10.5px;color:#5A5350}
.faits{margin:0;padding-left:18px}.faits li{margin:2px 0}.deux{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:14px;break-inside:avoid}
.var{font-weight:700}.var-bon{color:#1B77B0}.var-mauvais{color:#BA1A1A}
$CSS_GRAPHIQUES $CSS_ANNEAU</style></head><body>
<h1>${h(d.organisation.nom)}</h1><b>${h(titre)}</b><div class=m>Du ${dateFr(debut)} au ${dateFr(fin)} · édité le ${dateFr(aujourdhui().toString())} par ${h(d.profil.nom)}</div>
<h2>L’essentiel</h2><div class="tuiles">
${tuile("Trésorerie au ${dateFr(fin)}", euros0(soldeFin), "${if (soldeFin >= soldeDebut) "+" else "−"} ${euros0(kotlin.math.abs(soldeFin - soldeDebut))} sur la période")}
${tuile("Recettes", euros0(totR), variation(totR, precR, true))}${tuile("Dépenses", euros0(totD), variation(totD, precD, false))}
${tuile("Résultat", "<span style=\"color:${if (totR - totD >= 0) "#1B77B0" else "#BA1A1A"}\">$signeRes ${euros0(kotlin.math.abs(totR - totD))}</span>", if (totR - totD >= 0) "excédent" else "déficit")}
${reserve?.let { tuile("Réserve", "${(kotlin.math.round(it * 10) / 10).toString().replace('.', ',')} mois", "de dépenses couvertes") } ?: ""}</div>
<h3>Faits marquants</h3><ul class="faits">${faits.joinToString("") { "<li>$it</li>" }}</ul>
${if (serie.size >= 2) "<div class=\"deux\">" + svgColonnes("Recettes et dépenses par mois", serie.map { it.libelle },
        listOf(Triple("Recettes", CouleursGraph.RECETTE, serie.map { it.rec }), Triple("Dépenses", CouleursGraph.DEPENSE, serie.map { it.dep }))) +
        svgLigne("Trésorerie en fin de mois", serie.map { it.libelle }, serie.map { it.solde }) + "</div>" else ""}
<div class="deux">${svgAnneau("Origine des recettes", rec, "recette")}${svgAnneau("Destination des dépenses", dep, "depense")}</div>
<h2>Trésorerie par compte</h2><table><tr><th>Compte</th><th class=d>Au ${dateFr(debut)}</th><th class=d>Recettes</th><th class=d>Dépenses</th><th class=d>Au ${dateFr(fin)}</th></tr>
${comptes.joinToString("") { c -> val t = dans.filter { it.compteId == c.id }; val sd = c.soldeInitial + txs.filter { it.compteId == c.id && it.date < debut }.sumOf { it.signe }
        "<tr><td>${h(c.nom)}</td><td class=d>${euros(sd)}</td><td class=d>${euros(somme(t, "recette"))}</td><td class=d>${euros(somme(t, "depense"))}</td><td class=d>${euros(sd + t.sumOf { it.signe })}</td></tr>" }}
<tr class=tot><td>Total</td><td class=d>${euros(soldeDebut)}</td><td class=d>${euros(totR)}</td><td class=d>${euros(totD)}</td><td class=d>${euros(soldeFin)}</td></tr></table>
<div class="deux"><div><h2>Cotisations $an</h2>${if (exigible > 0) htmlJauge("Encaissé sur l’exigible", encaisse, exigible, "${euros(encaisse)} sur ${euros(exigible)} · ${cotis.size - enRetard.size} membres à jour sur ${cotis.size}") else "<p class=m>Aucune cotisation exigible</p>"}</div>
<div><h2>Participations aux activités</h2>${if (partsCol.isEmpty()) "<p class=m>Aucune participation sur la période</p>" else "<table><tr><th>Collecte</th><th class=d>Reçu</th><th class=d>Donateurs</th></tr>" +
        partsCol.joinToString("") { (c, recu, n) -> "<tr><td>${h(c.nom)}</td><td class=d>${euros(recu)}${c.objectif?.let { " / ${euros0(it)}" } ?: ""}</td><td class=d>$n</td></tr>" } + "</table>"}</div></div>
${if (budgetL.isNotEmpty()) "<h2>Budget $an : réalisé sur prévu</h2><div class=\"deux\">" + htmlBudget("Dépenses (emplois)", budgetL.filter { it.sens == "depense" }) + htmlBudget("Recettes (ressources)", budgetL.filter { it.sens == "recette" }) + "</div>" else ""}
<h2>Contrôle interne</h2><table>
<tr><td>Dépenses payées sur demande validée</td><td class=d>${demandes.size} · ${euros(demandes.sumOf { it.montant })}</td></tr>
<tr><td>Dont sans justificatif à ce jour</td><td class=d>${if (sansJustif.isNotEmpty()) "<b>${sansJustif.size}</b>" else "0"}</td></tr>
<tr><td>Dépenses sans pièce jointe</td><td class=d>${if (depSansPiece.isNotEmpty()) "<b>${depSansPiece.size} · ${euros(depSansPiece.sumOf { it.montant })}</b>" else "0"}</td></tr>
<tr><td>Rapprochements terminés (relevé joint, écart nul)</td><td class=d>${if (rapps.isEmpty()) "<b>aucun</b>" else rapps.joinToString(", ") { r -> "${h(comptes.firstOrNull { it.id == r.compteId }?.nom)} au ${dateFr(r.fin)}" }}</td></tr></table>
<div class=sig><div>Le trésorier</div><div>Le président</div></div></body></html>"""
}

// =====================================================================
// Adhérent : sa cotisation, les anniversaires, le planning
// =====================================================================
@Composable
fun EcranAdherent(d: Donnees, message: (String) -> Unit = {}, onReglages: (() -> Unit)? = null, onCloche: (() -> Unit)? = null) {
    // Même vue que vueMembre() du site (accueil connecté et lien personnel), par ordre d'importance :
    // le prochain rendez-vous en grand et en couleur (compte à rebours, « Ajouter à mon agenda »), une alerte seulement
    // si quelque chose est dû, la suite du planning, puis ma cotisation, comment régler, mes participations, les anniversaires.
    // Deux colonnes sur tablette et en paysage.
    var cotis by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var parts by remember { mutableStateOf<List<Participation>>(emptyList()) }
    var anniv by remember { mutableStateOf<List<Anniversaire>>(emptyList()) }
    var planning by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var infos by remember { mutableStateOf<String?>(null) }
    var pas by remember { mutableStateOf(1) }
    var version by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<Projet?>(null) }
    var communiques by remember { mutableStateOf<List<Communique>>(emptyList()) }
    var regles by remember { mutableStateOf<String?>(null) }
    var communiqueLu by remember { mutableStateOf<Communique?>(null) }
    var mesReponses by remember { mutableStateOf<Map<String, kotlinx.serialization.json.JsonObject>>(emptyMap()) }
    LaunchedEffect(version, Synchro.version) {
        try { communiques = communiquesActifs(Repo.communiques()); regles = Repo.texteReglage("banniere") } catch (_: Exception) { }
        try { mesReponses = Repo.mesReponses() } catch (_: Exception) { }
        try { cotis = Repo.maCotisation() } catch (_: Exception) { }
        try { parts = Repo.mesParticipations() } catch (_: Exception) { }
        try { anniv = Repo.anniversaires(); planning = Repo.planning(aujourdhui().toString()) } catch (_: Exception) { }
        try { pas = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt(); infos = Repo.texteReglage("infos_paiement") } catch (_: Exception) { }
        EtatNouveautes.vu("cotisations"); EtatNouveautes.vu("communiques")
    }
    val mois = aujourdhui().monthNumber
    // Au programme : tous les rendez-vous de la période choisie par le bouton de filtre (semaine, mois, 3 mois, année),
    // y compris le prochain même s'il passe aussi sur la bannière (sinon un mois qui n'en a qu'un paraît vide)
    var vuePlanning by remember { mutableStateOf(lirePreference("planning.membre")?.takeIf { it in PERIODES_PLANNING } ?: "mois") }
    val suite = planningMembreFiltre(planning, vuePlanning, aujourdhui())
    val retard = retardDe(cotis)
    val resteParts = parts.filter { (it.montantAttendu ?: 0.0) > 0 && it.donne < it.montantAttendu!! && !it.cloturee }.sumOf { it.montantAttendu!! - it.donne }
    val agenda = { p: Projet -> if (!ajouterAgenda(p.nom, p.debut ?: "", p.heureDebut, p.fin, p.heureFin, p.lieu, p.description)) message("Aucune application d’agenda sur ce téléphone") }
    val liste = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val du = retard > 0.005 || resteParts > 0.005

    // Une seule bannière forte : la photo, avec les annonces posées dessus, une à la fois (comme banniereAnnonces du site)
    val (annonces, rotation) = remember(planning, cotis, parts, communiques, regles) { annoncesBanniere(planning, cotis, parts, communiques, regles) }
    @Composable fun Tete() = Banniere(d, onReglages, onCloche) {
        Spacer(Modifier.height(16.dp))
        BanniereAnnonces(annonces, rotation, onEvt = { detail = it }, onAgenda = agenda,
            onRegler = { EtatAccueil.ouvertes["m-regler"] = true; scope.launch { liste.animateScrollToItem(if (communiquesActifs(communiques).isNotEmpty()) 4 else 3) } }, onCommunique = { communiqueLu = it })
    }
    val etatCotis = if (cotis.isEmpty()) "Non commencée" else if (retard > 0.005) "${euros(retard)} en retard" else "À jour"
    val nomMois = MOIS[mois - 1]
    @Composable fun Suite() = Rubrique("m-planning", "Au programme",
        "${if (suite.isEmpty()) "Rien" else "${suite.size} rendez-vous"} ${periodePlanning(vuePlanning).resume}") {
        FiltrePlanning(vuePlanning) { vuePlanning = it; garderPreference("planning.membre", it) }
        if (suite.isEmpty()) Text("${periodePlanning(vuePlanning).vide} Le bouton de filtre montre une période plus longue.", color = Couleurs.Texte2)
        if (vuePlanning == "semaine" || vuePlanning == "mois") suite.forEach { p -> LigneRendezVous(p) { detail = p } }
        else suite.groupBy { maxOf(it.debut!!.take(10), aujourdhui().toString()).take(7) }.forEach { (m, ps) ->
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(MOIS[m.drop(5).toInt() - 1].replaceFirstChar { it.uppercase() } + if (m.take(4) != aujourdhui().year.toString()) " " + m.take(4) else "",
                    fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
                Text("${ps.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2,
                    modifier = Modifier.background(Couleurs.Surface2, RoundedCornerShape(50)).padding(horizontal = 7.dp))
            }
            ps.forEach { p -> LigneRendezVous(p) { detail = p } }
        }
    }
    // Ma cotisation : l'état coloré dans le titre (icône verte ou rouge), la grille de l'année et sa légende (comme le site)
    @Composable fun Cotisation() = Rubrique("m-cotisation", "Ma cotisation", etatCotis, alerte = retard > 0.005,
        icone = if (retard > 0.005) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
        teinteIcone = if (retard > 0.005) Couleurs.Erreur else if (cotis.isNotEmpty()) Couleurs.Vert else Couleurs.Texte) {
        val regle = cotis.filter { it.statut == "regle" || it.statut == "dispense" }.maxOfOrNull { it.periode }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = if (retard > 0.005) Couleurs.Erreur else Couleurs.Texte)) { append(etatCotis) }
            withStyle(SpanStyle(color = Couleurs.Texte)) {
                if (retard > 0.005) append(" · périodes commencées et non réglées") else if (regle != null) append(" · réglée jusqu’à ${nomPeriode(regle, pas)}")
            }
        }, fontSize = 15.sp)
        val l = cotis.filter { it.annee == aujourdhui().year }.sortedBy { it.periode }
        if (l.isNotEmpty()) {
            GrillePeriodes(l.map { it.periode to it.statut }, pas)
            // Légende : seulement les états présents dans la grille
            val libelles = listOf("regle" to "Réglé", "partiel" to "Partiel", "impaye" to "Impayé", "a_venir" to "À venir", "dispense" to "Dispensé")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                libelles.filter { (k, _) -> l.any { it.statut == k } }.forEach { (k, lib) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(14.dp)) { GrillePeriodes(listOf("2026-01-01" to k), 12) }
                        Text(lib, fontSize = 12.sp, color = Couleurs.Texte2)
                    }
                }
            }
        }
    }
    @Composable fun Regler() { if (du) Rubrique("m-regler", "Comment régler", "À régler") { Text(infos ?: "Adressez-vous au trésorier.", fontSize = 14.sp) } }
    @Composable fun Participations() { if (parts.isNotEmpty()) Rubrique("m-participations", "Mes participations", if (resteParts > 0.005) "${euros(resteParts)} à régler" else "${parts.size}") { ListeParticipations(parts) } }
    @Composable fun Anniversaires() = Rubrique("m-anniversaires", "Anniversaires ${deMois(mois)}", if (anniv.isEmpty()) "Aucun" else "${anniv.size}", ouverteParDefaut = false) { ListeAnniversaires(anniv, mois) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        if (large) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Tete()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) { RubriqueCommuniques(communiques, mesReponses) { communiqueLu = it }; Apparition(1) { Suite() } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { Apparition(2) { Cotisation() }; Regler(); Participations(); Anniversaires() }
            }
        } else LazyColumn(Modifier.fillMaxSize(), state = liste, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Tete() }
            if (communiquesActifs(communiques).isNotEmpty()) item { RubriqueCommuniques(communiques, mesReponses) { communiqueLu = it } }
            item { Apparition(1) { Suite() } }
            item { Apparition(2) { Cotisation() } }
            item { Regler() }
            item { Participations() }
            item { Anniversaires() }
        }
    }
    communiqueLu?.let { c -> FeuilleCommunique(c, mesReponses[c.id], onRepondu = { mesReponses = mesReponses + (c.id to it); message("Merci pour votre réponse") }, message = message) { communiqueLu = null } }
    detail?.let { e ->
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailEvenement(d, e, message, onChange = { version++ }, onFermer = { detail = null })
        }
    }
}

// Rendez-vous dans une liste, avec sa couleur (filet, date pleine, « Dans N jours »)
// Planning de la page du membre : période choisie par le bouton de filtre, gardée sur l'appareil (même règle que le site)
class PeriodePlanning(val libelle: String, val detail: String, val resume: String, val vide: String)
val PERIODES_PLANNING = listOf("semaine", "mois", "trimestre", "annee")
fun periodePlanning(k: String, auj: LocalDate = aujourdhui()): PeriodePlanning = when (k) {
    "semaine" -> PeriodePlanning("Semaine", "7 prochains jours", "cette semaine", "Rien de prévu dans les 7 prochains jours.")
    "trimestre" -> PeriodePlanning("3 mois", "3 prochains mois", "sur 3 mois", "Rien de prévu dans les 3 prochains mois.")
    "annee" -> PeriodePlanning("Année", "12 prochains mois", "sur l’année", "Rien d’annoncé pour les 12 prochains mois.")
    else -> { val m = MOIS[auj.monthNumber - 1]; val de = if (m.first() in "aeiouéâ") "d’" else "de "
        PeriodePlanning(m.replaceFirstChar { it.uppercase() }, "mois en cours", "en $m", "Rien de prévu d’ici la fin $de$m.") }
}
/** Un rendez-vous est retenu s'il n'est pas terminé et commence avant la fin de la période (même calcul que le site). */
fun planningMembreFiltre(planning: List<Projet>, vue: String, auj: LocalDate): List<Projet> {
    val fin = when (vue) {
        "semaine" -> auj.plus(DatePeriod(days = 6))
        "mois" -> LocalDate(auj.year, auj.monthNumber, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        "trimestre" -> auj.plus(DatePeriod(months = 3))
        else -> auj.plus(DatePeriod(years = 1))
    }.toString()
    val a = auj.toString()
    return planning.filter { it.debut != null && (it.fin ?: it.debut).take(10) >= a && it.debut.take(10) <= fin }
}

/** Bouton de filtre « Période » avec son petit menu (comme le site). */
@Composable
fun FiltrePlanning(vue: String, onChoix: (String) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { ouvert = true }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp)
            .semantics { contentDescription = "Période affichée : ${periodePlanning(vue).libelle}" }) {
            Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(periodePlanning(vue).libelle); Spacer(Modifier.width(2.dp))
            Icon(if (ouvert) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(ouvert, onDismissRequest = { ouvert = false }) {
            PERIODES_PLANNING.forEach { k ->
                val p = periodePlanning(k)
                DropdownMenuItem(text = { Column { Text(p.libelle, fontWeight = FontWeight.SemiBold, color = if (k == vue) Couleurs.Orange else Couleurs.Texte)
                        Text(p.detail, fontSize = 12.sp, color = Couleurs.Texte2) } },
                    leadingIcon = { Box(Modifier.size(20.dp)) { if (k == vue) Icon(Icons.Outlined.Check, null, tint = Couleurs.Orange) } },
                    onClick = { ouvert = false; onChoix(k) })
            }
        }
    }
}

@Composable
fun LigneRendezVous(p: Projet, onClick: () -> Unit) {
    val (fond, encre, accent) = couleurEvt(p.id)
    Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable(onClick = onClick)
        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        p.debut?.let { dt ->
            val j = LocalDate.parse(dt.take(10))
            Column(Modifier.width(48.dp).clip(Formes.Moyen).background(accent).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${j.dayOfMonth}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                Text(MOIS_COURTS[j.monthNumber - 1], fontSize = 11.sp, color = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(p.nom, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(p.debut?.let { jourLong(it) }, p.heureDebut?.let { heureFr(it) }, p.lieu).joinToString(" · ").ifEmpty { "Date à fixer" }, fontSize = 13.sp, color = Couleurs.Texte2)
        }
        p.participation?.let { Puce(euros(it), Couleurs.JauneClair, Couleurs.SurJaune) }
            ?: p.debut?.let { Text(dansJours(it), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = encre, modifier = Modifier.background(fond, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp)) }
    }
}

// Entrée en cascade (légère montée + fondu), désactivée par la réduction des animations du téléphone
@Composable
fun Apparition(rang: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(rang * 60L); visible = true }
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(250)) { it / 8 }) { content() }
}

// =====================================================================
// Menu « Plus »
// =====================================================================
@Composable
fun EcranPlus(d: Donnees, onChoix: (String) -> Unit) {
    // Même ordre et mêmes libellés que pagesAutorisees() du site (le menu « Plus » reprend les pages qui suivent les onglets)
    val entrees = buildList {
        if (d.peut("consulter_finances", "gerer_budget")) add(Triple("budget", "Budget", Icons.Outlined.PieChart))
        add(Triple("activites", "Planning", Icons.Outlined.Event))
        if (d.peut("gerer_activites", "administrer")) add(Triple("communiques", "Communiqués", Icons.Outlined.Campaign))
        if (d.peut("gerer_materiel", "consulter_finances", "voir_membres")) add(Triple("materiel", "Matériel", Icons.Outlined.Inventory2))
        if (d.peut("consulter_finances", "saisir_ecritures", "gerer_cotisations")) add(Triple("tiers", "Tiers", Icons.Outlined.Contacts))
        if (d.peut("voir_membres", "gerer_membres")) add(Triple("membres", "Membres", Icons.Outlined.Groups))
        if (d.peut("consulter_finances", "rapprocher")) add(Triple("rapprochement", "Rapprochement", Icons.Outlined.AccountBalance))
        if (d.peut("consulter_finances")) add(Triple("rapports", "Rapports", Icons.Outlined.Description))
        // Paramètres : roue dentée en haut de chaque écran (et sur la bannière de l'accueil), pas de doublon ici
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entrees, key = { it.first }) { (k, l, icone) ->
            Surface(onClick = { onChoix(k) }, color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icone, null, tint = Couleurs.Orange)
                    Text(l, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    val n = EtatNouveautes.compte(k)
                    if (n > 0) Badge(containerColor = Couleurs.Erreur, contentColor = Color.White) { Text("$n") }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                }
            }
        }
    }
}
