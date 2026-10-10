package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.animation.togetherWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            "exercices" -> ParamExercices(d, message)
            "comptes" -> ParamComptes(d, message, recharger)
            "categories" -> ParamCategories(d, message, recharger)
            "regles" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) { item { BlocMontants(message) } }
            "personnes" -> ParamPersonnes(d, message)
            "roles" -> ParamRoles(d, message, recharger)
            "corbeille" -> ParamCorbeille(d, message, recharger)
            "sauvegarde" -> ParamSauvegarde(message)
            else -> ParamMiseEnRoute(d) { cible ->
                when (cible) { "assistant" -> onAssistant(); "apparence", "comptes" -> section = cible; else -> onCible(cible) }
            }
        }
        Column(Modifier.fillMaxSize().background(Color(0xFFEEF1F6))) {
            BarreParametres(d, if (!large && choisie != null) choisie.titre else "Paramètres", if (!large && choisie != null) ({ section = null }) else null, onFermer)
            if (large) Row(Modifier.fillMaxSize()) {
                MenuParametres(d, choisie?.cle, { section = it }, Modifier.width(340.dp).fillMaxHeight())
                Column(Modifier.weight(1f)) { choisie?.let { c -> EnteteSection(c); Box(Modifier.weight(1f)) { Contenu(c) } } }
            } else androidx.compose.animation.AnimatedContent(choisie, transitionSpec = {
                if (targetState != null) (androidx.compose.animation.slideInHorizontally { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith androidx.compose.animation.fadeOut()
                else (androidx.compose.animation.slideInHorizontally { -it / 2 } + androidx.compose.animation.fadeIn()) togetherWith androidx.compose.animation.fadeOut()
            }) { c ->
                if (c == null) MenuParametres(d, null, { section = it }, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize()) { Contenu(c) }
            }
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
                Text("Toutes les données (association, membres, opérations, demandes, budget, matériel, exercices, corbeille) dans un fichier à conserver hors ligne, au moins à chaque clôture d’exercice. Les pièces jointes s’archivent depuis Rapports.", color = Couleurs.Texte2)
                Button(onClick = { scope.launch { try { enregistrer("sauvegarde-tresorerie-${aujourdhui()}.json", "application/json", Repo.sauvegardeJson().encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) {
                    Text("Télécharger la sauvegarde")
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
                            modifier = Modifier.padding(top = 4.dp).background(Couleurs.BleuClair, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
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
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEFEDEC)), contentAlignment = Alignment.Center) {
                    if (apercuBanniere != null) Image(apercuBanniere, "Bannière", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(if (retirerBanniere) "La photo sera retirée à l’enregistrement" else "Aucune photo. Format paysage conseillé.", fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = choixBanniere) { Text(if (apercuBanniere != null) "Changer la photo" else "Choisir une photo") }
                    if (apercuBanniere != null) TextButton(onClick = { nouvelleBanniere = null; retirerBanniere = true }) { Text("Retirer la photo") }
                }
                Button(enabled = nom.isNotBlank(), modifier = Modifier.align(Alignment.End), onClick = {
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
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    CarteBlanche {
        Text("Montants et délais", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(cotisation, { cotisation = it }, label = { Text("Cotisation par période") }, suffix = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChoixListe("Périodicité", PERIODICITES[periodicite] ?: "", PERIODICITES.values.toList()) { periodicite = PERIODICITES.keys.toList()[it] }
        OutlinedTextField(delai, { delai = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Délai du justificatif après paiement") },
            suffix = { Text("jours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(seuilJustif, { seuilJustif = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Justification obligatoire à partir de") },
            suffix = { Text("€") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(seuil, { seuil = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Alerte budget à partir de") },
            suffix = { Text("%") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(infos, { infos = it.take(500) }, label = { Text("Comment régler (affiché aux membres)") },
            placeholder = { Text("IBAN, application, remise au trésorier…") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(modifier = Modifier.align(Alignment.End), enabled = nombre(cotisation) != null && delai.isNotBlank() && seuil.isNotBlank() && seuilJustif.isNotBlank(), onClick = {
            action({
                Repo.majReglage("cotisation_montant", nombre(cotisation)!!)
                Repo.majReglage("cotisation_periode_mois", periodicite.toDouble())
                Repo.majReglage("delai_justificatif_jours", delai.toDouble())
                Repo.majReglage("seuil_alerte_budget_pct", seuil.toDouble())
                Repo.majReglage("seuil_justification", seuilJustif.toDouble())
                Repo.majTexteReglage("infos_paiement", infos.trim().ifBlank { null })
            }, "Paramètres enregistrés")
        }) { Text("Enregistrer") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParamComptes(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    var comptes by remember { mutableStateOf<List<Compte>>(emptyList()) }
    var soldes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var categories by remember { mutableStateOf(d.categories) }
    var feuille by remember { mutableStateOf<String?>(null) }
    val action = rememberAction(message) { version++; recharger() }
    LaunchedEffect(version) {
        try {
            comptes = Repo.tousLesComptes(); soldes = comptes.associate { it.id to montantSaisie(it.soldeInitial) }
            categories = Repo.categories().filter { !it.interne }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val supprimerCompte = rememberSuppression(message) { version++; recharger() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Solde de départ et compte actif", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { feuille = "compte" }) { Icon(Icons.Filled.Add, contentDescription = null); Text("Ajouter") }
                }
                comptes.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(soldes[c.id] ?: "", { v -> soldes = soldes + (c.id to v) }, label = { Text(c.nom) }, suffix = { Text("€") },
                            singleLine = true, isError = nombre(soldes[c.id] ?: "") == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Switch(checked = c.actif, onCheckedChange = { v ->
                            action({ Repo.majCompte(c.id, c.soldeInitial, v) }, if (v) "Compte réactivé" else "Compte désactivé")
                        })
                        IconButton(onClick = { supprimerCompte("accounts", c.id, c.nom) }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer ${c.nom}", tint = Couleurs.Erreur) }
                    }
                }
                Button(modifier = Modifier.align(Alignment.End), enabled = comptes.all { nombre(soldes[it.id] ?: "") != null }, onClick = {
                    action({ comptes.forEach { c -> nombre(soldes[c.id]!!)!!.let { v -> if (v != c.soldeInitial) Repo.majCompte(c.id, v, c.actif) } } }, "Soldes de départ enregistrés")
                }) { Text("Enregistrer") }
            }
        }
        item { Text("Un compte déjà utilisé ne se supprime pas : désactivez-le pour le masquer.", color = Couleurs.Texte2, fontSize = 13.sp) }
    }
    when (feuille) {
        "compte" -> {
            var nomC by remember { mutableStateOf("") }
            var type by remember { mutableStateOf("banque") }
            var solde by remember { mutableStateOf("0") }
            DialogueSimple("Nouveau compte", "Ajouter", nomC.isNotBlank() && nombre(solde) != null, { feuille = null }, {
                action({ Repo.ajouterCompte(NouveauCompte(nomC.trim(), type, nombre(solde)!!)) }, "Compte ajouté"); feuille = null
            }) {
                OutlinedTextField(nomC, { nomC = it.take(60) }, label = { Text("Nom") }, placeholder = { Text("Livret A") }, singleLine = true)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("caisse" to "Caisse", "banque" to "Banque").forEachIndexed { i, (k, v) ->
                        SegmentedButton(selected = type == k, onClick = { type = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
                    }
                }
                OutlinedTextField(solde, { solde = it }, label = { Text("Solde de départ") }, suffix = { Text("€") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        "categorie" -> {
            var nomC by remember { mutableStateOf("") }
            var sens by remember { mutableStateOf("depense") }
            DialogueSimple("Nouvelle catégorie", "Ajouter", nomC.isNotBlank(), { feuille = null }, {
                action({ Repo.ajouterCategorie(NouvelleCategorie(nomC.trim(), sens)) }, "Catégorie ajoutée"); feuille = null
            }) {
                OutlinedTextField(nomC, { nomC = it.take(60) }, label = { Text("Nom") }, singleLine = true)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("depense" to "Dépense", "recette" to "Recette").forEachIndexed { i, (k, v) ->
                        SegmentedButton(selected = sens == k, onClick = { sens = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
                    }
                }
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
    LaunchedEffect(version) {
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
                Surface(color = Couleurs.JauneClair, contentColor = Couleurs.SurJaune, shape = RoundedCornerShape(14.dp)) {
                    Text("Ce rôle valide et paie. Une même personne ne paie jamais une dépense qu’elle a validée.", Modifier.padding(12.dp), fontSize = 14.sp)
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
private fun ParamPersonnes(d: Donnees, message: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    var roles by remember { mutableStateOf(d.roles) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<Invitation>>(emptyList()) }
    var inviter by remember { mutableStateOf(false) }
    val action = rememberAction(message) { version++ }
    LaunchedEffect(version) {
        try { roles = Repo.roles(); profils = Repo.profilsComplets(); invitations = Repo.invitations() } catch (e: Exception) { message(traduireErreur(e)) }
    }
    fun nomRole(c: String) = roles.firstOrNull { it.code == c }?.nom ?: c
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text("Vue d’ensemble des comptes. Pour donner un accès à quelqu’un, ouvrez sa fiche dans Membres, puis « Fonction » : on est d’abord membre, puis on reçoit une fonction.",
                    color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(profils, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(p.nom.substringBefore(' '), p.nom.substringAfter(' ', ""), taille = 40)
                    Column(Modifier.weight(1f)) {
                        Text(p.nom + if (p.id == d.profil.id) " (vous)" else "", fontWeight = FontWeight.SemiBold,
                            color = if (p.actif) Color.Unspecified else Couleurs.Texte2)
                        PuceMenu(nomRole(p.role), false, roles.map { it.nom }) { i ->
                            val r = roles[i].code
                            if (r != p.role) action({ Repo.majProfil(p.id, role = r) }, "Rôle modifié")
                        }
                    }
                    Switch(checked = p.actif, onCheckedChange = { v -> action({ Repo.majProfil(p.id, actif = v) }, if (v) "Accès réactivé" else "Accès désactivé") })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            if (invitations.isNotEmpty()) item {
                Text("Invitations en attente", fontWeight = FontWeight.Bold, color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
            }
            items(invitations, key = { "i-" + it.email }) { i ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(i.nom ?: i.email, fontWeight = FontWeight.SemiBold)
                        Text("${i.email} · ${nomRole(i.role)}", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    TextButton(onClick = { action({ Repo.retirerInvitation(i.email) }, "Invitation retirée") }) { Text("Retirer") }
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
        OutlinedTextField(debut, { debut = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
            isError = (debut.isNotBlank() && dDebut == null) || (type == "evenement" && debut.isBlank()))
        OutlinedTextField(fin, { fin = it }, label = { Text("Jusqu’au") }, placeholder = { Text("Même jour") }, singleLine = true,
            isError = fin.isNotBlank() && (dFin == null || (dDebut != null && dFin < dDebut)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(hDebut, { hDebut = it.take(5) }, label = { Text("Début") }, placeholder = { Text("18:30") }, singleLine = true,
                isError = hD == null, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(hFin, { hFin = it.take(5) }, label = { Text("Fin") }, placeholder = { Text("20:00") }, singleLine = true,
                isError = hF == null, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(lieu, { lieu = it.take(120) }, label = { Text("Lieu") }, singleLine = true)
        OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description") }, minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { visible = !visible }) {
            Checkbox(visible, { visible = it }); Text("Visible de tous les membres", fontSize = 14.sp)
        }
        // Comme le site : une case « Suivre le budget » plutôt qu'un choix Événement / Activité
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { type = if (type == "activite") "evenement" else "activite" }) {
            Checkbox(type == "activite", { type = if (it) "activite" else "evenement" })
            Column {
                Text("Suivre le budget de cette activité", fontSize = 14.sp)
                Text("Ressources, emplois et résultat dans Budget$NBSP; la date peut rester à fixer", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
        if (p == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { participation = !participation }) {
                Checkbox(participation, { participation = it }); Text("Demander une participation aux membres", fontSize = 14.sp)
            }
            if (participation) {
                OutlinedTextField(attendu, { attendu = it }, label = { Text("Montant par personne") }, placeholder = { Text("Libre") }, suffix = { Text("€") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(limite, { limite = it }, label = { Text("Date limite") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
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
    var membres by remember { mutableStateOf(d.membres) }
    var profils by remember { mutableStateOf<List<Profil>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<Invitation>>(emptyList()) }
    var fonction by remember { mutableStateOf<Membre?>(null) }
    var maFiche by remember { mutableStateOf(false) }
    val admin = d.peut("administrer")
    var recherche by remember { mutableStateOf("") }
    var fiche by remember { mutableStateOf<Membre?>(null) }
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
    val choixCsv = rememberChoixTexte { texte ->
        if (texte != null) aImporter = try { analyserCsvMembres(texte, membres) } catch (e: Exception) { message(e.message ?: "Fichier illisible"); null }
    }
    LaunchedEffect(version) {
        try {
            if (version > 0) membres = Repo.membres()
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
    val vus = membres.filter { recherche.isBlank() || it.nomComplet.lowercase().contains(recherche.trim().lowercase()) }
    val bureau = vus.filter { fonctionDe(it)?.first?.let { r -> r != "adherent" } == true }.sortedBy { rangRole(fonctionDe(it)!!.first) }
    val autres = vus - bureau.toSet()
    val sansFiche = d.profil.memberId == null && d.peut("gerer_membres")
    val ligne: @Composable (Membre) -> Unit = { m ->
        Row(Modifier.fillMaxWidth().clickable(enabled = d.peut("gerer_membres")) { fiche = m }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val img = photos[m.id]
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape))
            else Box(Modifier.size(44.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) {
                Text("${m.prenom.take(1)}${m.nom.take(1)}", color = Couleurs.SurOrangeClair, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.nomComplet + (if (m.id == d.profil.memberId) " (vous)" else "") + if (!m.actif) " (inactif)" else "", fontWeight = FontWeight.SemiBold)
                Text("${m.jour} ${MOIS[m.mois - 1]}" + (m.profession?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                val f = fonctionDe(m)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    when {
                        f == null -> {}
                        f.second == "invite" -> Puce("${d.nomRole(f.first)} · invité", Couleurs.JauneClair, Couleurs.SurJaune)
                        f.second == "coupe" -> Puce("${d.nomRole(f.first)} · accès coupé", Color(0xFFEFEDEC), Couleurs.Texte2)
                        f.first == "adherent" -> Puce("Accès adhérent", Color(0xFFEFEDEC), Couleurs.Texte2)
                        else -> Puce(d.nomRole(f.first), Couleurs.BleuClair, Couleurs.SurBleuClair)
                    }
                    if (!m.consentement) Puce("Sans accord", Color(0xFFEFEDEC), Couleurs.Texte2)
                }
            }
            if (gereLiens && m.actif) TextButton(onClick = { lienDe = m }) { Text("Lien") }
            if (admin) TextButton(onClick = { fonction = m }) { Text("Fonction") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item {
                Titre("Membres") { OutlinedButton(onClick = { exporter = true }) { Text("Exporter") } }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (gereLiens && membres.isNotEmpty()) FilledTonalButton(onClick = { tousLiens = true }) { Text("Liens personnels") }
                    if (d.peut("gerer_membres")) OutlinedButton(onClick = choixCsv) { Text("Importer (CSV, Excel)") }
                }
            }
            item { Text("${membres.count { it.actif }} actifs", color = Couleurs.Texte2, modifier = Modifier.padding(bottom = 8.dp)) }
            item {
                OutlinedTextField(recherche, { recherche = it }, label = { Text("Rechercher") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            if (bureau.isNotEmpty()) {
                item { Text("Bureau", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(bureau, key = { "b-" + it.id }) { ligne(it) }
                item { Text("Membres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp)) }
            }
            items(autres, key = { it.id }) { ligne(it) }
            if (admin) item {
                Text("Pour désigner un membre du bureau : « Fonction » sur sa ligne, puis choisissez sa fonction. Les fonctions et leurs droits se règlent dans Paramètres, Rôles et droits.",
                    fontSize = 13.sp, color = Couleurs.Texte2, modifier = Modifier.padding(top = 16.dp))
            }
        }
        if (d.peut("gerer_membres")) {
            ExtendedFloatingActionButton(onClick = { ajout = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Ajouter") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
    val supprimerMembre = rememberSuppression(message) { version++; recharger() }
    if (ajout || fiche != null) ModalBottomSheet(onDismissRequest = { ajout = false; fiche = null }) {
        FicheMembre(fiche, membres, onFini = { ok -> if (ok) { message(if (fiche != null) "Fiche modifiée" else "Membre ajouté"); version++ }; ajout = false; fiche = null }, message,
            onSupprimer = if (d.peut("gerer_membres")) ({ fiche?.let { m -> supprimerMembre("members", m.id, m.nomComplet) }; fiche = null }) else null)
    }
    if (maFiche) ModalBottomSheet(onDismissRequest = { maFiche = false }) {
        FicheMembre(null, membres, titre = "Ma fiche de membre", onCree = { id, n -> Repo.lierProfil(d.profil.id, id, n) },
            onFini = { ok -> maFiche = false; if (ok) { message("Votre fiche est créée"); recharger() } }, message = message)
    }
    lienDe?.let { m ->
        ModalBottomSheet(onDismissRequest = { lienDe = null }) {
            FeuilleLien(d, m, liens.firstOrNull { it.membreId == m.id }, message, onChange = { scope.launch { liens = try { Repo.liens() } catch (_: Exception) { liens } } }) { lienDe = null }
        }
    }
    if (tousLiens) ModalBottomSheet(onDismissRequest = { tousLiens = false }) {
        FeuilleLiens(d, membres, liens, message, onChange = { scope.launch { liens = try { Repo.liens() } catch (_: Exception) { liens } } }) { tousLiens = false }
    }
    if (exporter) AlertDialog(
        onDismissRequest = { exporter = false },
        title = { Text("Exporter la liste des membres") },
        text = { Text("PDF : mis en page pour imprimer ou transmettre. Excel : tableau modifiable (CSV). Données personnelles : à garder dans le bureau.") },
        confirmButton = { Button(onClick = { exporter = false; scope.launch { try { val (t, h) = documentHtml(d, "membres", aujourdhui().year); imprimer(t, h) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("PDF") } },
        dismissButton = { OutlinedButton(onClick = { exporter = false; scope.launch { try { enregistrer("membres.csv", "text/csv", exportCsv(d, "membres", aujourdhui().year).encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Excel") } },
    )
    fonction?.let { m ->
        FeuilleFonction(d, m, profils, invitations, onFini = { msg -> fonction = null; if (msg != null) { message(msg); version++; if (profils.any { it.id == d.profil.id && it.memberId == m.id }) recharger() } }, message = message)
    }
    aImporter?.let { (ok, erreurs) ->
        AlertDialog(
            onDismissRequest = { aImporter = null },
            title = { Text("Importer des membres") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ok.size} ligne${if (ok.size > 1) "s" else ""} prête${if (ok.size > 1) "s" else ""}, ${erreurs.size} refusée${if (erreurs.size > 1) "s" else ""}.")
                    erreurs.forEach { Text(it, fontSize = 13.sp, color = Couleurs.Erreur) }
                    Text("Colonnes attendues$NBSP: prénom, nom, jour, mois (ou anniversaire jj/mm), profession, whatsapp, email, accord.", fontSize = 12.sp, color = Couleurs.Texte2)
                }
            },
            confirmButton = {
                Button(enabled = ok.isNotEmpty(), onClick = {
                    scope.launch {
                        try { Repo.importerMembres(ok); message("${ok.size} membres importés"); version++ } catch (e: Exception) { message(traduireErreur(e)) }
                        aImporter = null
                    }
                }) { Text("Importer ${ok.size}") }
            },
            dismissButton = { TextButton(onClick = { aImporter = null }) { Text("Annuler") } },
        )
    }
}

private val ORDRE_ROLES = listOf("president", "vice_president", "tresorier", "tresorier_adjoint", "secretaire", "bureau")
private fun rangRole(r: String): Int = ORDRE_ROLES.indexOf(r).let { if (it >= 0) it else if (r == "adherent") 99 else 50 }

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
            if (envoye) {
                Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Accès accordé", fontWeight = FontWeight.Bold)
                        Text("${m.prenom} crée son compte avec ${email.trim()} sur le site ou dans l’application ; sa fonction s’applique dès la création.", fontSize = 14.sp)
                        val texte = "Bonjour ${m.prenom}, votre accès à la trésorerie ${d.organisation.nom} est prêt. Créez votre compte avec l’adresse ${email.trim()}."
                        numeroWa(m.whatsapp ?: "")?.let { wa -> FilledTonalButton(onClick = { uri.openUri("https://wa.me/$wa?text=" + encoderUrl(texte)) }) { Text("Prévenir par WhatsApp") } }
                        TextButton(onClick = { onFini("Accès accordé") }) { Text("Terminer") }
                    }
                }
                return@Column
            }
            val statut = when { compte != null -> if (compte.actif) "Compte actif" else "Accès coupé"; invitation != null -> "Invitation en attente · ${invitation.email}"; else -> "Sans accès" }
            Text(statut, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
            if (compte == null && invitation == null && lier == null)
                OutlinedTextField(email, { email = it.trim() }, label = { Text("E-mail de connexion") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            ChoixListe("Fonction", roles.firstOrNull { it.code == role }?.nom ?: role, roles.map { it.nom }) { role = roles[it].code }
            if (compte != null && compte.id != d.profil.id) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { actif = !actif }) {
                Checkbox(actif, { actif = it }); Text("Accès à la plateforme", fontSize = 14.sp)
            }
            if (compte == null && invitation == null && libres.isNotEmpty())
                ChoixListe("Ou rattacher un compte déjà créé", lier?.nom ?: "Aucun", listOf("Aucun") + libres.map { it.nom }) { lier = if (it == 0) null else libres[it - 1] }
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
            else Box(Modifier.size(56.dp).background(Couleurs.OrangeClair, CircleShape))
            FilledTonalButton(onClick = choixPhoto) { Text(if (m?.photo != null || photo != null) "Changer la photo" else "Ajouter une photo") }
        }
        OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Anniversaire (jour et mois)", fontSize = 13.sp, color = Couleurs.Texte2)
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
            Text("Accepte que son anniversaire soit affiché aux autres membres", fontSize = 14.sp)
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
        add(Quadruple("demandes", "Registre des demandes de dépense", listOf("pdf"), "annee"))
        add(Quadruple("pieces", "Pièces justificatives (fichier ZIP)", listOf("zip"), "annee"))
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
                if (doc.d.isNotEmpty() && !(libre && type == "libre"))
                    ChoixListe("Exercice", d.exercices.firstOrNull { it.debut.take(4).toInt() == annee }?.libelle ?: annee.toString(),
                        listOf(an, an - 1, an - 2).map { a -> d.exercices.firstOrNull { it.debut.take(4).toInt() == a }?.let { "${it.libelle} (${dateFr(it.debut)} – ${dateFr(it.fin)})" } ?: a.toString() }) { annee = an - it }
                if (libre && type == "mois") ChoixListe("Mois", MOIS[mois - 1].replaceFirstChar { it.uppercase() }, MOIS.map { m -> m.replaceFirstChar { it.uppercase() } }) { mois = it + 1 }
                if (libre && type == "trimestre") ChoixListe("Trimestre", listOf("1er trimestre", "2e trimestre", "3e trimestre", "4e trimestre")[trimestre],
                    listOf("1er trimestre (janv. à mars)", "2e trimestre (avr. à juin)", "3e trimestre (juil. à sept.)", "4e trimestre (oct. à déc.)")) { trimestre = it }
                if (libre && type == "libre") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(du, { du = it }, label = { Text("Du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dateDepuisFr(du) == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(au, { au = it }, label = { Text("Au") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = periode == null, modifier = Modifier.weight(1f))
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
                                "journal" -> if (format == "excel") enregistrer("ecritures-$deb-au-$fi.csv", "text/csv", exportCsv(d, "ecritures", annee, deb, fi).encodeToByteArray())
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
                                else -> if (format == "excel") enregistrer("${doc.a}-$annee.csv", "text/csv", exportCsv(d, doc.a, annee).encodeToByteArray())
                                        else { val (t, html) = documentHtml(d, doc.a, annee); imprimer(t, html) }
                            }
                        } catch (e: Exception) { message(traduireErreur(e)) }
                        enCours = false
                    }
                }) { Text(etat ?: "Exporter en ${noms[format]}") }
            }
        }
        item {
            Text("PDF$NBSP: mis en page pour imprimer, signer ou transmettre. Excel$NBSP: tableau modifiable (CSV).", fontSize = 13.sp, color = Couleurs.Texte2)
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
        "ecritures" -> csv(listOf("Date", "Sens", "Libellé", "Catégorie", "Compte", "Mode", "Montant", "Rapprochée"),
            Repo.toutesEcritures().filter { it.date in du..au }.sortedBy { it.date }.map {
                listOf(dateFr(it.date), if (it.virement != null) (if (it.sens == "recette") "Virement interne (entrée)" else "Virement interne (sortie)") else if (it.sens == "recette") "Recette" else "Dépense", it.libelle, cat(it.categorieId),
                    d.comptes.firstOrNull { c -> c.id == it.compteId }?.nom, it.mode, it.signe, if (it.rapproche) "Oui" else "Non")
            })
        "membres" -> csv(listOf("Prénom", "Nom", "Jour", "Mois", "Profession", "WhatsApp", "E-mail", "Accord anniversaire", "Actif"),
            Repo.membres().map { listOf(it.prenom, it.nom, it.jour, it.mois, it.profession, it.whatsapp, it.email, if (it.consentement) "Oui" else "Non", if (it.actif) "Oui" else "Non") })
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
            csv(listOf("Collecte", "Date", "Tiers", "Montant"), Repo.toutesEcritures().filter { it.collecteId != null }.sortedBy { it.date }.map { e ->
                listOf(cs.firstOrNull { it.id == e.collecteId }?.nom, dateFr(e.date), nomTiers(e, ms, ts), e.montant)
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
    LaunchedEffect(version) {
        try { cotis = Repo.maCotisation() } catch (_: Exception) { }
        try { parts = Repo.mesParticipations() } catch (_: Exception) { }
        try { anniv = Repo.anniversaires(); planning = Repo.planning(aujourdhui().toString()) } catch (_: Exception) { }
        try { pas = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt(); infos = Repo.texteReglage("infos_paiement") } catch (_: Exception) { }
        EtatNouveautes.vu("cotisations")
    }
    val mois = aujourdhui().monthNumber
    val prochain = planning.firstOrNull()
    val suite = planning.drop(1).take(6)
    val retard = retardDe(cotis)
    val resteParts = parts.filter { (it.montantAttendu ?: 0.0) > 0 && it.donne < it.montantAttendu!! && !it.cloturee }.sumOf { it.montantAttendu!! - it.donne }
    val agenda = { p: Projet -> if (!ajouterAgenda(p.nom, p.debut ?: "", p.heureDebut, p.fin, p.heureFin, p.lieu, p.description)) message("Aucune application d’agenda sur ce téléphone") }
    val liste = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val du = retard > 0.005 || resteParts > 0.005

    @Composable fun Hero() = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroRendezVous(prochain, parts.firstOrNull { it.collecteId != null && it.collecteId == prochain?.collecteId }, onAgenda = { prochain?.let(agenda) }) { prochain?.let { detail = it } }
        if (du) AlerteMembre(listOfNotNull(if (retard > 0.005) "Cotisation : ${euros(retard)} en retard" else null, if (resteParts > 0.005) "Participations : ${euros(resteParts)} à régler" else null).joinToString(" · ")) {
            EtatAccueil.ouvertes["m-regler"] = true; scope.launch { liste.animateScrollToItem(4) }   // « Comment régler »
        }
    }
    val etatCotis = if (cotis.isEmpty()) "Non commencée" else if (retard > 0.005) "${euros(retard)} en retard" else "À jour"
    @Composable fun Suite() = Rubrique("m-planning", "Ensuite au planning", if (suite.isEmpty()) "Rien d’autre" else "${suite.size} rendez-vous") {
        if (suite.isEmpty()) Text("Rien d’autre d’annoncé pour l’instant.", color = Couleurs.Texte2)
        suite.forEach { p -> LigneRendezVous(p) { detail = p } }
    }
    @Composable fun Cotisation() = Rubrique("m-cotisation", "Ma cotisation", etatCotis, alerte = retard > 0.005) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (retard > 0.005) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, tint = if (retard > 0.005) Couleurs.Erreur else Color(0xFF2E8B4E), modifier = Modifier.size(20.dp))
            Text(etatCotis, fontWeight = FontWeight.Bold, color = if (retard > 0.005) Couleurs.Erreur else Couleurs.Texte)
            val regle = cotis.filter { it.statut == "regle" || it.statut == "dispense" }.maxOfOrNull { it.periode }
            if (retard <= 0.005 && regle != null) Text("· réglée jusqu’à ${nomPeriode(regle, pas)}", fontSize = 14.sp, color = Couleurs.Texte2)
        }
        val l = cotis.filter { it.annee == aujourdhui().year }.sortedBy { it.periode }
        if (l.isNotEmpty()) GrillePeriodes(l.map { it.periode to it.statut }, pas)
    }
    @Composable fun Regler() { if (du) Rubrique("m-regler", "Comment régler", "À régler") { Text(infos ?: "Adressez-vous au trésorier.", fontSize = 14.sp) } }
    @Composable fun Participations() { if (parts.isNotEmpty()) Rubrique("m-participations", "Mes participations", if (resteParts > 0.005) "${euros(resteParts)} à régler" else "${parts.size}") { ListeParticipations(parts) } }
    @Composable fun Anniversaires() = Rubrique("m-anniversaires", "Anniversaires ${deMois(mois)}", if (anniv.isEmpty()) "Aucun" else "${anniv.size}", ouverteParDefaut = false) { ListeAnniversaires(anniv, mois) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        if (large) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onReglages != null) Banniere(d, onReglages, onCloche, compacte = true) { }
            Apparition(0) { Hero() }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(16.dp)) { Apparition(1) { Suite() } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { Apparition(2) { Cotisation() }; Regler(); Participations(); Anniversaires() }
            }
        } else LazyColumn(Modifier.fillMaxSize(), state = liste, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onReglages != null) item { Banniere(d, onReglages, onCloche, compacte = true) { } } else item { }
            item { Apparition(0) { Hero() } }
            item { Apparition(1) { Suite() } }
            item { Apparition(2) { Cotisation() } }
            item { Regler() }
            item { Participations() }
            item { Anniversaires() }
        }
    }
    detail?.let { e ->
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            DetailEvenement(d, e, message, onChange = { version++ }, onFermer = { detail = null })
        }
    }
}

// Prochain rendez-vous : carte sobre sous la bannière (pas une seconde bannière, comme .prochain du site) ;
// la couleur du rendez-vous tient dans la date et le filet de gauche ; toucher la carte ouvre le détail
@Composable
fun HeroRendezVous(p: Projet?, part: Participation?, onAgenda: () -> Unit = {}, onClick: () -> Unit) {
    if (p == null || p.debut == null) {
        CarteBlanche {
            Text("Prochain rendez-vous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
            Text("Rien de prévu pour l’instant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Il s’affichera ici dès qu’il sera annoncé.", color = Couleurs.Texte2, fontSize = 14.sp)
        }
        return
    }
    val (fond, encre, accent) = couleurEvt(p.id)
    val j = LocalDate.parse(p.debut.take(10))
    val imminent = aujourdhui().daysUntil(j) <= 1
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }.padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(56.dp).clip(RoundedCornerShape(14.dp)).background(accent).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(JOURS_COURTS[j.dayOfWeek.ordinal].uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${j.dayOfMonth}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 28.sp)
                Text(MOIS_COURTS[j.monthNumber - 1].uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Prochain rendez-vous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte2)
                    Text(dansJours(p.debut), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (imminent) Color.White else encre,
                        modifier = Modifier.background(if (imminent) accent else fond, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 1.dp))
                }
                Text(p.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(if (p.heureDebut != null) heureFr(p.heureDebut) + (p.heureFin?.let { " – " + heureFr(it) } ?: "") else "Toute la journée", p.lieu).joinToString(" · "),
                    fontSize = 14.sp, color = Couleurs.Texte2)
                p.description?.let { Text(it, fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                p.participation?.let { m -> Puce("Participation ${euros(m)}" + (part?.let { " · donné ${euros(it.donne)}" } ?: ""), Couleurs.JauneClair, Couleurs.SurJaune) }
            }
            Surface(onClick = onAgenda, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, contentColor = encre,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6E2E0))) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).widthIn(min = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = "Ajouter à mon agenda", modifier = Modifier.size(20.dp))
                    Text("Agenda", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Alerte seulement si quelque chose est dû (même texte que .alerte-membre du site)
@Composable
fun AlerteMembre(texte: String, onClick: () -> Unit) =
    Surface(onClick = onClick, color = Couleurs.ErreurClair, contentColor = Color(0xFF410002), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.drawBehind { drawRect(Couleurs.Erreur, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = Couleurs.Erreur)
            Column(Modifier.weight(1f)) { Text(texte, fontWeight = FontWeight.Bold); Text("Voir comment régler", fontSize = 13.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }

// Rendez-vous dans une liste, avec sa couleur (filet, date pleine, « Dans N jours »)
@Composable
fun LigneRendezVous(p: Projet, onClick: () -> Unit) {
    val (fond, encre, accent) = couleurEvt(p.id)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        p.debut?.let { dt ->
            val j = LocalDate.parse(dt.take(10))
            Column(Modifier.width(48.dp).clip(RoundedCornerShape(12.dp)).background(accent).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
    val entrees = buildList {
        if (d.peut("voir_membres", "gerer_membres")) add(Triple("membres", "Membres", Icons.Outlined.Groups))
        if (d.peut("consulter_finances", "gerer_budget")) add(Triple("budget", "Budget", Icons.Outlined.PieChart))
        add(Triple("activites", "Planning", Icons.Outlined.Event))
        if (d.peut("consulter_finances", "saisir_ecritures", "gerer_cotisations")) add(Triple("tiers", "Tiers", Icons.Outlined.Contacts))
        if (d.peut("gerer_materiel", "consulter_finances", "voir_membres")) add(Triple("materiel", "Matériel", Icons.Outlined.Inventory2))
        if (d.peut("rapprocher", "consulter_finances")) add(Triple("rapprochement", "Rapprochement", Icons.Outlined.AccountBalance))
        if (d.peut("consulter_finances")) add(Triple("rapports", "Rapports et exports", Icons.Outlined.Description))
        // Paramètres : roue dentée en haut de chaque écran (et sur la bannière de l'accueil), pas de doublon ici
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entrees, key = { it.first }) { (k, l, icone) ->
            Surface(onClick = { onChoix(k) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
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
