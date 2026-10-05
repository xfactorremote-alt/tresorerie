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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
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

private val JOURS_MOIS = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private fun nombre(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").toDoubleOrNull()

// =====================================================================
// Paramètres (droit « administrer ») : quatre onglets
// =====================================================================
private val ONGLETS_PARAM = listOf("Association", "Comptes", "Rôles et droits", "Personnes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranParametres(d: Donnees, message: (String) -> Unit, recharger: () -> Unit) {
    var onglet by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Paramètres", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        ScrollableTabRow(selectedTabIndex = onglet, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.background) {
            ONGLETS_PARAM.forEachIndexed { i, t -> Tab(selected = onglet == i, onClick = { onglet = i }, text = { Text(t, maxLines = 1) }) }
        }
        Box(Modifier.weight(1f)) {
            when (onglet) {
                0 -> ParamAssociation(d, message, recharger)
                1 -> ParamComptes(d, message, recharger)
                2 -> ParamRoles(d, message, recharger)
                else -> ParamPersonnes(d, message)
            }
        }
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
    var cotisation by remember { mutableStateOf("") }
    var periodicite by remember { mutableStateOf(1) }
    var delai by remember { mutableStateOf("") }
    var seuil by remember { mutableStateOf("") }
    val choixLogo = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) nouveauLogo = f }
    val choixBanniere = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) { nouvelleBanniere = f; retirerBanniere = false } }
    val action = rememberAction(message, recharger)
    LaunchedEffect(Unit) {
        try {
            val r = Repo.reglages()
            cotisation = montantSaisie(r["cotisation_montant"] ?: 0.0)
            periodicite = (r["cotisation_periode_mois"] ?: 1.0).toInt()
            delai = (r["delai_justificatif_jours"] ?: 7.0).toInt().toString()
            seuil = (r["seuil_alerte_budget_pct"] ?: 90.0).toInt().toString()
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val apercu = remember(nouveauLogo) { nouveauLogo?.let { imageDepuisOctets(it.octets) } }
                    if (apercu != null) Image(apercu, "Nouveau logo", contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape))
                    else LogoAsso(Modifier.size(72.dp))
                    FilledTonalButton(onClick = choixLogo) { Text("Changer le logo") }
                }
                OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom de l’association") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
        item {
            CarteBlanche {
                Text("Montants et délais", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(cotisation, { cotisation = it }, label = { Text("Cotisation par période") }, suffix = { Text("€") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                ChoixListe("Périodicité", PERIODICITES[periodicite] ?: "", PERIODICITES.values.toList()) { periodicite = PERIODICITES.keys.toList()[it] }
                OutlinedTextField(delai, { delai = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Délai du justificatif après paiement") },
                    suffix = { Text("jours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(seuil, { seuil = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Alerte budget à partir de") },
                    suffix = { Text("%") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Button(modifier = Modifier.align(Alignment.End), enabled = nombre(cotisation) != null && delai.isNotBlank() && seuil.isNotBlank(), onClick = {
                    action({
                        Repo.majReglage("cotisation_montant", nombre(cotisation)!!)
                        Repo.majReglage("cotisation_periode_mois", periodicite.toDouble())
                        Repo.majReglage("delai_justificatif_jours", delai.toDouble())
                        Repo.majReglage("seuil_alerte_budget_pct", seuil.toDouble())
                    }, "Paramètres enregistrés")
                }) { Text("Enregistrer") }
            }
        }
        item { BlocSauvegarde(message) }
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
            categories = Repo.categories()
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comptes et soldes de départ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { feuille = "compte" }) { Icon(Icons.Filled.Add, contentDescription = "Ajouter un compte") }
                }
                comptes.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(soldes[c.id] ?: "", { v -> soldes = soldes + (c.id to v) }, label = { Text(c.nom) }, suffix = { Text("€") },
                            singleLine = true, isError = nombre(soldes[c.id] ?: "") == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Switch(checked = c.actif, onCheckedChange = { v ->
                            action({ Repo.majCompte(c.id, c.soldeInitial, v) }, if (v) "Compte réactivé" else "Compte désactivé")
                        })
                    }
                }
                Button(modifier = Modifier.align(Alignment.End), enabled = comptes.all { nombre(soldes[it.id] ?: "") != null }, onClick = {
                    action({ comptes.forEach { c -> nombre(soldes[c.id]!!)!!.let { v -> if (v != c.soldeInitial) Repo.majCompte(c.id, v, c.actif) } } }, "Soldes de départ enregistrés")
                }) { Text("Enregistrer") }
            }
        }
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Catégories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { feuille = "categorie" }) { Icon(Icons.Filled.Add, contentDescription = "Ajouter une catégorie") }
                }
                listOf("recette" to "Recettes", "depense" to "Dépenses").forEach { (sens, titre) ->
                    Text(titre, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.filter { it.sens == sens }.forEach {
                            Puce(it.nom, if (sens == "recette") Couleurs.BleuClair else Couleurs.OrangeClair, if (sens == "recette") Couleurs.SurBleuClair else Couleurs.SurOrangeClair)
                        }
                    }
                }
            }
        }
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
        ExtendedFloatingActionButton(onClick = { inviter = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Inviter") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
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

@Composable
private fun BlocSauvegarde(message: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    CarteBlanche {
        Text("Sauvegarde complète", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = {
            scope.launch {
                try { enregistrer("sauvegarde-tresorerie-${aujourdhui()}.json", "application/json", Repo.sauvegardeJson().encodeToByteArray()) }
                catch (e: Exception) { message(traduireErreur(e)) }
            }
        }) { Text("Enregistrer la sauvegarde") }
    }
}

// =====================================================================
// Budget : saisie (trésorier) et suivi
// =====================================================================
@Composable
fun EcranBudget(d: Donnees, message: (String) -> Unit) {
    var annee by remember { mutableStateOf(aujourdhui().year) }
    var suivi by remember { mutableStateOf<List<LigneBudget>?>(null) }
    var lignes by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var ajout by remember { mutableStateOf(false) }
    var aModifier by remember { mutableStateOf<Pair<Budget, LigneBudget>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(annee, version) {
        try { suivi = Repo.budget(annee); lignes = Repo.lignesBudget(annee); projets = Repo.projets() } catch (e: Exception) { message(traduireErreur(e)) }
    }
    fun brute(b: LigneBudget) = lignes.firstOrNull { it.categorieId == b.categorieId && it.projetId == b.projetId }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Titre("Budget") {
                    Box(Modifier.width(140.dp)) { ChoixListe("Année", annee.toString(), (aujourdhui().year + 1 downTo aujourdhui().year - 2).map { it.toString() }) { annee = aujourdhui().year + 1 - it } }
                }
            }
            val l = suivi
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (l.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aucun budget pour $annee.", color = Couleurs.Texte2)
                    if (d.peut("gerer_budget")) Button(onClick = { ajout = true }) { Text("Ajouter une ligne") }
                }
            } else {
                val general = l.filter { it.projetId == null }
                val rp = general.filter { it.sens == "recette" }; val ep = general.filter { it.sens == "depense" }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chiffre("Ressources", rp.sumOf { it.realise }, "sur ${euros(rp.sumOf { it.prevu })}", Couleurs.Bleu, Modifier.weight(1f))
                        Chiffre("Emplois", ep.sumOf { it.realise }, "sur ${euros(ep.sumOf { it.prevu })}", Couleurs.Orange, Modifier.weight(1f))
                    }
                }
                item { Text("Résultat prévu$NBSP: ${euros(rp.sumOf { it.prevu } - ep.sumOf { it.prevu })}", color = Couleurs.Texte2) }
                listOf("Emplois (dépenses)" to ep, "Ressources (recettes)" to rp, "Par activité" to l.filter { it.projetId != null }).forEach { (titre, liste) ->
                    if (liste.isNotEmpty()) item {
                        CarteBlanche {
                            Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            liste.forEach { b ->
                                Box(Modifier.fillMaxWidth().clickable(enabled = d.peut("gerer_budget")) { brute(b)?.let { aModifier = it to b } }) {
                                    LigneBudgetVue(b, projets.firstOrNull { it.id == b.projetId }?.nom)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (d.peut("gerer_budget") && !suivi.isNullOrEmpty()) {
            ExtendedFloatingActionButton(onClick = { ajout = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Ajouter une ligne") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }

    if (ajout) {
        val cats = d.categories.sortedBy { it.sens }
        var cat by remember { mutableStateOf(cats.firstOrNull { it.sens == "depense" }) }
        var projet by remember { mutableStateOf<Projet?>(null) }
        var montant by remember { mutableStateOf("") }
        DialogueSimple("Ligne de budget $annee", "Ajouter", cat != null && nombre(montant) != null, { ajout = false }, {
            scope.launch {
                try {
                    val seuil = (Repo.reglages()["seuil_alerte_budget_pct"] ?: 90.0).toInt()
                    Repo.ajouterBudget(NouveauBudget(annee, cat!!.id, projet?.id, nombre(montant)!!, seuil)); message("Ligne ajoutée"); version++
                }
                catch (e: Exception) { message(traduireErreur(e)) }
                ajout = false
            }
        }) {
            ChoixListe("Poste", cat?.let { (if (it.sens == "recette") "Ressource : " else "Emploi : ") + it.nom } ?: "",
                cats.map { (if (it.sens == "recette") "Ressource : " else "Emploi : ") + it.nom }) { cat = cats[it] }
            ChoixListe("Activité", projet?.nom ?: "Budget général", listOf("Budget général") + projets.map { it.nom }) { projet = if (it == 0) null else projets[it - 1] }
            OutlinedTextField(montant, { montant = it }, label = { Text("Montant prévu") }, suffix = { Text("€") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
    aModifier?.let { (brut, ligne) ->
        var montant by remember(brut.id) { mutableStateOf(montantSaisie(brut.prevu)) }
        var confirmer by remember(brut.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { aModifier = null },
            title = { Text(ligne.categorie) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Réalisé$NBSP: ${euros(ligne.realise)}", color = Couleurs.Texte2)
                    OutlinedTextField(montant, { montant = it }, label = { Text("Montant prévu") }, suffix = { Text("€") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (confirmer) Text("Retirer cette ligne du budget$NBSP? Les écritures ne sont pas touchées.", color = Couleurs.Erreur, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(enabled = nombre(montant) != null, onClick = {
                    scope.launch {
                        try { Repo.majBudget(brut.id, nombre(montant)!!); message("Budget modifié"); version++ } catch (e: Exception) { message(traduireErreur(e)) }
                        aModifier = null
                    }
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!confirmer) confirmer = true
                    else scope.launch {
                        try { Repo.supprimerBudget(brut.id); message("Ligne retirée"); version++ } catch (e: Exception) { message(traduireErreur(e)) }
                        aModifier = null
                    }
                }) { Text(if (confirmer) "Confirmer le retrait" else "Retirer la ligne", color = Couleurs.Erreur) }
            },
        )
    }
}

// =====================================================================
// Activités et planning
// =====================================================================
@Composable
fun EcranActivites(d: Donnees, message: (String) -> Unit) {
    var projets by remember { mutableStateOf<List<Projet>?>(null) }
    var ecritures by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var budgets by remember { mutableStateOf<List<LigneBudget>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var edition by remember { mutableStateOf<Projet?>(null) }
    var nouvelle by remember { mutableStateOf(false) }
    val vue = "toutes"
    LaunchedEffect(version) {
        try { projets = Repo.projets(); ecritures = Repo.toutesEcritures(); budgets = Repo.budget(aujourdhui().year) } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val aujourd = aujourdhui().toString()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val l = projets?.filter { it.type == "activite" }
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val vus = l
                if (vus.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Aucune activité", color = Couleurs.Texte2)
                        if (d.peut("gerer_activites")) Button(onClick = { nouvelle = true }) { Text("Nouvelle activité") }
                    }
                }
                items(vus, key = { it.id }) { p ->
                    val dep = ecritures.filter { it.projetId == p.id && it.sens == "depense" }.sumOf { it.montant }
                    val rec = ecritures.filter { it.projetId == p.id && it.sens == "recette" }.sumOf { it.montant }
                    val prevu = budgets.filter { it.projetId == p.id && it.sens == "depense" }.sumOf { it.prevu }
                    CarteBlanche {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.nom, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (p.visible) Puce("Tous les membres", Couleurs.BleuClair, Couleurs.SurBleuClair) else Puce("Bureau", Color(0xFFEFEDEC), Couleurs.Texte2)
                        }
                        Text((p.debut?.let { dateFr(it) + (if (p.fin != null && p.fin != p.debut) " au " + dateFr(p.fin) else "") } ?: "Date à fixer") + (p.lieu?.let { " · $it" } ?: ""),
                            fontSize = 13.sp, color = Couleurs.Texte2)
                        p.description?.let { Text(it) }
                        if (vue == "toutes") {
                            Text("Budget ${euros(prevu)} · dépensé ${euros(dep)} · recettes ${euros(rec)}", fontSize = 13.sp, color = Couleurs.Texte2)
                            if (prevu > 0 && dep > prevu) Text("Budget de l’activité dépassé", color = Couleurs.Erreur, fontSize = 13.sp)
                        }
                        if (d.peut("gerer_activites")) TextButton(onClick = { edition = p }, modifier = Modifier.align(Alignment.End)) { Text("Modifier") }
                    }
                }
            }
        }
        if (d.peut("gerer_activites")) {
            ExtendedFloatingActionButton(onClick = { nouvelle = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nouvelle activité") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
    if (nouvelle || edition != null) {
        FormulaireActivite(edition, onFini = { ok -> if (ok) { message(if (edition != null) "Activité modifiée" else "Activité créée"); version++ }; nouvelle = false; edition = null }, message, typeInitial = "activite")
    }
}

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
    DialogueSimple(if (p == null) "Ajouter au planning" else "Modifier", "Enregistrer", nom.isNotBlank() && datesOk && !enCours, { onFini(false) }, {
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("evenement" to "Événement", "activite" to "Activité").forEachIndexed { i, (k, v) ->
                SegmentedButton(selected = type == k, onClick = { type = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
            }
        }
        OutlinedTextField(nom, { nom = it.take(80) }, label = { Text("Nom") }, singleLine = true)
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
fun EcranMembres(d: Donnees, message: (String) -> Unit) {
    var membres by remember { mutableStateOf(d.membres) }
    var recherche by remember { mutableStateOf("") }
    var fiche by remember { mutableStateOf<Membre?>(null) }
    var ajout by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var aImporter by remember { mutableStateOf<Pair<List<NouveauMembre>, List<String>>?>(null) }
    var photos by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val choixCsv = rememberChoixTexte { texte ->
        if (texte != null) aImporter = try { analyserCsvMembres(texte, membres) } catch (e: Exception) { message(e.message ?: "Fichier illisible"); null }
    }
    LaunchedEffect(version) {
        try {
            if (version > 0) membres = Repo.membres()
            photos = membres.mapNotNull { m -> m.photo?.let { ch -> try { Repo.telecharger("photos", ch)?.let { imageDepuisOctets(it) }?.let { m.id to it } } catch (_: Exception) { null } } }.toMap()
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val vus = membres.filter { recherche.isBlank() || it.nomComplet.lowercase().contains(recherche.trim().lowercase()) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item {
                Titre("Membres") { if (d.peut("gerer_membres")) TextButton(onClick = choixCsv) { Text("Importer") } }
            }
            item { Text("${membres.count { it.actif }} actifs", color = Couleurs.Texte2, modifier = Modifier.padding(bottom = 8.dp)) }
            item {
                OutlinedTextField(recherche, { recherche = it }, label = { Text("Rechercher") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            items(vus, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().clickable(enabled = d.peut("gerer_membres")) { fiche = m }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val img = photos[m.id]
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape))
                    else Box(Modifier.size(44.dp).background(Couleurs.OrangeClair, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${m.prenom.take(1)}${m.nom.take(1)}", color = Couleurs.SurOrangeClair, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.nomComplet + if (!m.actif) " (inactif)" else "", fontWeight = FontWeight.SemiBold)
                        Text("${m.jour} ${MOIS[m.mois - 1]}" + (m.profession?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    if (!m.consentement) Puce("Sans accord", Color(0xFFEFEDEC), Couleurs.Texte2)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (d.peut("gerer_membres")) {
            ExtendedFloatingActionButton(onClick = { ajout = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Ajouter") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
    if (ajout || fiche != null) ModalBottomSheet(onDismissRequest = { ajout = false; fiche = null }) {
        FicheMembre(fiche, membres, onFini = { ok -> if (ok) { message(if (fiche != null) "Fiche modifiée" else "Membre ajouté"); version++ }; ajout = false; fiche = null }, message)
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

@Composable
private fun FicheMembre(m: Membre?, existants: List<Membre>, onFini: (Boolean) -> Unit, message: (String) -> Unit) {
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
        Text(if (m == null) "Nouveau membre" else "Modifier la fiche", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
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
                            Repo.ajouterMembre(n)
                            if (photo != null) Repo.membres().lastOrNull { it.nomComplet == "${n.prenom} ${n.nom}" }?.let { Repo.majMembre(it.id, n, true, photo) }
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
@Composable
fun EcranRapports(d: Donnees, message: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    val an = aujourdhui().year
    var exercice by remember { mutableStateOf(an) }
    var debut by remember { mutableStateOf("01/${aujourdhui().monthNumber.toString().padStart(2, '0')}/$an") }
    var fin by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var enCours by remember { mutableStateOf(false) }
    fun lancer(bloc: suspend () -> Unit) { enCours = true; scope.launch { try { bloc() } catch (e: Exception) { message(traduireErreur(e)) }; enCours = false } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Titre("Rapports") }
        item {
            CarteBlanche {
                Text("Rapport d’assemblée générale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ChoixListe("Exercice", exercice.toString(), listOf(an, an - 1, an - 2).map { it.toString() }) { exercice = an - it }
                Button(enabled = !enCours, onClick = {
                    lancer { imprimer("Rapport AG $exercice", rapportHtml(d, "$exercice-01-01", "$exercice-12-31", "Rapport financier de l’exercice $exercice")) }
                }) { Text("Imprimer ou PDF") }
            }
        }
        item {
            val dDebut = dateDepuisFr(debut); val dFin = dateDepuisFr(fin)
            CarteBlanche {
                Text("Rapport périodique", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(debut, { debut = it }, label = { Text("Du") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dDebut == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fin, { fin = it }, label = { Text("Au") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
                    isError = dFin == null || (dDebut != null && dFin < dDebut), modifier = Modifier.fillMaxWidth())
                Button(enabled = !enCours && dDebut != null && dFin != null && dFin >= dDebut, onClick = {
                    lancer { imprimer("Rapport", rapportHtml(d, dDebut.toString(), dFin.toString(), "Rapport de trésorerie du $debut au $fin")) }
                }) { Text("Imprimer ou PDF") }
            }
        }
        item {
            CarteBlanche {
                Text("Exports Excel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                listOf("ecritures" to "Écritures $an", "membres" to "Membres", "cotisations" to "Cotisations $an", "participations" to "Participations", "budget" to "Budget $an").forEach { (k, l) ->
                    OutlinedButton(enabled = !enCours, modifier = Modifier.fillMaxWidth(), onClick = {
                        lancer { enregistrer("$k-$an.csv", "text/csv", exportCsv(d, k, an).encodeToByteArray()) }
                    }) { Text(l) }
                }
            }
        }
        if (d.peut("administrer")) item { BlocSauvegarde(message) }
    }
}

// CSV lisible par Excel en français : BOM, séparateur « ; », virgule décimale
private fun csv(entetes: List<String>, lignes: List<List<Any?>>): String {
    fun cel(v: Any?): String {
        val s = when (v) { is Double -> v.toString().replace('.', ','); null -> ""; else -> v.toString() }
        return if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    return "﻿" + (listOf(entetes) + lignes).joinToString("\r\n") { l -> l.joinToString(";") { cel(it) } }
}

private suspend fun exportCsv(d: Donnees, type: String, an: Int): String {
    val cat = { id: String -> d.categories.firstOrNull { it.id == id }?.nom ?: "" }
    return when (type) {
        "ecritures" -> csv(listOf("Date", "Sens", "Libellé", "Catégorie", "Compte", "Mode", "Montant", "Rapprochée"),
            Repo.toutesEcritures().filter { it.date.startsWith("$an") }.map {
                listOf(dateFr(it.date), if (it.sens == "recette") "Recette" else "Dépense", it.libelle, cat(it.categorieId),
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
        else -> csv(listOf("Poste", "Type", "Prévu", "Réalisé", "Taux %"),
            Repo.budget(an).map { listOf(it.categorie, if (it.sens == "recette") "Ressource" else "Emploi", it.prevu, it.realise, it.taux ?: 0.0) })
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
    val nomCat = { id: String -> d.categories.firstOrNull { it.id == id }?.nom ?: "" }
    fun somme(l: List<Ecriture>, sens: String) = l.filter { it.sens == sens }.sumOf { it.montant }
    val totR = somme(dans, "recette"); val totD = somme(dans, "depense")
    val precR = somme(prec, "recette"); val precD = somme(prec, "depense")
    val depart = comptes.sumOf { it.soldeInitial }
    val soldeDebut = depart + txs.filter { it.date < debut }.sumOf { it.signe }
    val soldeFin = depart + txs.sumOf { it.signe }
    val serie = serieMensuelle(txs, comptes, debut, fin)
    val serie12 = serieMensuelle(txs, comptes, LocalDate(dFin.year, dFin.monthNumber, 1).minus(DatePeriod(months = 11)).toString(), fin)
    val reserve = reserveEnMois(soldeFin, serie12)
    fun parCat(sens: String) = dans.filter { it.sens == sens }.groupBy { it.categorieId }.map { (id, l) -> Element(nomCat(id), l.sumOf { it.montant }) }
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
    val sansJustif = demandes.filter { it.statut == "payee" }
    val contrepassees = txs.mapNotNull { it.contrepasseDe }.toSet()
    val depSansPiece = dans.filter { e -> e.sens == "depense" && e.montant > 0 && e.contrepasseDe == null && e.id !in contrepassees &&
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
fun EcranAdherent(d: Donnees, onPlanning: (() -> Unit)? = null) {
    var cotis by remember { mutableStateOf<List<PeriodeCotisation>>(emptyList()) }
    var parts by remember { mutableStateOf<List<Participation>>(emptyList()) }
    var anniv by remember { mutableStateOf<List<Anniversaire>>(emptyList()) }
    var planning by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var pas by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        try { cotis = Repo.maCotisation() } catch (_: Exception) { }
        try { parts = Repo.mesParticipations() } catch (_: Exception) { }
        try { anniv = Repo.anniversaires(); planning = Repo.planning(aujourdhui().toString()).take(5) } catch (_: Exception) { }
        try { pas = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt() } catch (_: Exception) { }
    }
    val mois = aujourdhui().monthNumber
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CarteBlanche {
                Text("Ma cotisation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                BlocMaCotisation(cotis, pas)
            }
        }
        if (parts.isNotEmpty()) item {
            CarteBlanche {
                Text("Mes participations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ListeParticipations(parts)
            }
        }
        item {
            CarteBlanche {
                Text("À venir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (planning.isEmpty()) Text("Aucune activité prévue", color = Couleurs.Texte2)
                planning.forEach { p ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(p.nom, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(p.debut?.let { jourLong(it) }, p.heureDebut?.let { heureFr(it) }, p.lieu, p.participation?.let { "participation ${euros(it)}" }).joinToString(" · "),
                            fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                }
                onPlanning?.let { TextButton(onClick = it) { Text("Voir le planning") } }
            }
        }
        item {
            CarteBlanche {
                Text("Anniversaires ${deMois(mois)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (anniv.isEmpty()) Text("Aucun anniversaire ce mois-ci", color = Couleurs.Texte2)
                anniv.forEach { Text("${it.jour} ${MOIS[mois - 1]} · ${it.prenom} ${it.nom}") }
            }
        }
    }
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
        if (d.peut("rapprocher", "consulter_finances")) add(Triple("rapprochement", "Rapprochement", Icons.Outlined.AccountBalance))
        if (d.peut("consulter_finances")) add(Triple("rapports", "Rapports et exports", Icons.Outlined.Description))
        if (d.profil.memberId != null) add(Triple("moi", "Ma cotisation", Icons.Outlined.Person))
        if (d.peut("administrer")) add(Triple("parametres", "Paramètres", Icons.Outlined.Settings))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entrees, key = { it.first }) { (k, l, icone) ->
            Surface(onClick = { onChoix(k) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icone, null, tint = Couleurs.Orange)
                    Text(l, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                }
            }
        }
    }
}
