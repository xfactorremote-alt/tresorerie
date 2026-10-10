package org.jpgrenoble.tresorerie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

// Données partagées entre les écrans. Les droits viennent de la base (rôle -> droits),
// jamais du nom du rôle : l'administrateur peut créer des rôles et changer leurs droits.
class Donnees(
    val profil: Profil,
    val organisation: Organisation,
    val comptes: List<Compte>,
    // Toutes les catégories, y compris « Virement interne » ; jamais proposées à la saisie
    val categoriesToutes: List<Categorie>,
    val membres: List<Membre>,
    val droits: Set<String>,
    val roles: List<Role>,
    val exercices: List<Exercice> = emptyList(),
) {
    // Exercice clôturé : ses opérations sont verrouillées (ni ajout, ni modification, ni suppression)
    fun exerciceClos(date: String) = exercices.any { it.cloture && date >= it.debut && date <= it.fin }
    val categories: List<Categorie> = categoriesToutes.filter { !it.interne }
    fun nomCategorie(id: String?) = categoriesToutes.firstOrNull { it.id == id }?.nom ?: ""
    fun peut(vararg codes: String) = codes.any { it in droits }
    fun nomRole(code: String) = roles.firstOrNull { it.code == code }?.nom ?: code
}

@Composable
fun App() = Theme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (Repo.demo) {
            val profil by Repo.profilDemo.collectAsState()
            val p = profil
            if (p == null) Connexion() else Principal(cle = p.id)
        } else {
            // Au retour dans l'application (choix d'une photo, d'un fichier…), Supabase revérifie la
            // session : l'état passe un instant par « Initializing » ou « RefreshFailure » (réseau).
            // L'écran principal reste affiché tant que la personne ne s'est pas déconnectée.
            LaunchedEffect(Unit) { Repo.oublierSessionSiDemande() }
            val statut by Repo.client.auth.sessionStatus.collectAsState()
            var connecte by rememberSaveable { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(statut) {
                when (statut) {
                    is SessionStatus.Authenticated -> connecte = true
                    is SessionStatus.NotAuthenticated -> connecte = false
                    is SessionStatus.RefreshFailure -> if (connecte == null) connecte = false
                    else -> {}
                }
            }
            when (connecte) {
                true -> Principal(cle = "supabase")
                false -> Connexion()
                null -> Chargement()
            }
        }
    }
}

@Composable
fun Chargement() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

// Connexion : en-tête, deux onglets (Se connecter / Première connexion), œil sur le mot de passe,
// « Rester connecté », mot de passe oublié sous le champ ; le gestionnaire de mots de passe du téléphone
// remplit et propose d'enregistrer les identifiants (remplissage automatique Android).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Connexion() {
    var premiere by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    var rester by remember { mutableStateOf(lirePreference("resterConnecte") != "0") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var nomAsso by remember { mutableStateOf("JP Grenoble") }
    var comptesDemo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val autofill = LocalAutofillManager.current
    LaunchedEffect(Unit) {
        try { nomAsso = Repo.organisation().nom; Repo.chargerLogo() } catch (_: Exception) { }
    }
    fun seConnecter() {
        enCours = true; erreur = null; info = null
        scope.launch {
            try {
                garderPreference("resterConnecte", if (rester) "1" else "0")
                Repo.connecter(email, mdp)
                autofill?.commit()
            } catch (e: Exception) { erreur = traduireErreur(e) }
            enCours = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 480.dp).fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Couleurs.OrangeClair)) { LogoAsso(Modifier.fillMaxSize().padding(3.dp).clip(CircleShape)) }
            Column {
                Text(nomAsso, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Trésorerie de l’association", color = Couleurs.Texte2)
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Se connecter", true to "Première connexion").forEachIndexed { i, (v, l) ->
                SegmentedButton(selected = premiere == v, onClick = { premiere = v; erreur = null; info = null }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l, maxLines = 1) }
            }
        }
        info?.let { Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(12.dp)) { Text(it, Modifier.padding(12.dp), fontSize = 14.sp) } }
        if (premiere) Text("Utilisez l’adresse e-mail que le trésorier a enregistrée pour vous, puis choisissez votre mot de passe.", color = Couleurs.Texte2, fontSize = 14.sp)
        OutlinedTextField(
            email, { email = it.trim() }, label = { Text("Adresse e-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username + ContentType.EmailAddress },
        )
        ChampMotDePasse(mdp, { mdp = it }, if (premiere) "Mot de passe (8 caractères minimum)" else "Mot de passe",
            if (premiere) ContentType.NewPassword else ContentType.Password, if (premiere) ImeAction.Next else ImeAction.Done) { if (!premiere && email.isNotBlank() && mdp.length >= 8) seConnecter() }
        if (premiere) ChampMotDePasse(mdp2, { mdp2 = it }, "Confirmez le mot de passe", ContentType.NewPassword, ImeAction.Done,
            erreur = mdp2.isNotEmpty() && mdp2 != mdp)
        if (!premiere) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { rester = !rester }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rester, { rester = it }); Text("Rester connecté", fontSize = 14.sp)
            }
            TextButton(onClick = {
                if (!email.contains('@')) { erreur = "Saisissez d’abord votre adresse e-mail"; return@TextButton }
                scope.launch { try { Repo.motDePasseOublie(email); info = "Lien envoyé, consultez votre messagerie" } catch (e: Exception) { erreur = traduireErreur(e) } }
            }) { Text("Mot de passe oublié$NBSP?") }
        }
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
        Button(
            onClick = {
                if (!premiere) seConnecter()
                else {
                    enCours = true; erreur = null
                    scope.launch {
                        try {
                            val connecte = Repo.inscrire(email, mdp)
                            autofill?.commit()
                            if (!connecte) { premiere = false; info = "Compte créé. Ouvrez le lien reçu par e-mail pour le confirmer, puis connectez-vous." }
                        } catch (e: Exception) { erreur = traduireErreur(e) }
                        enCours = false
                    }
                }
            },
            enabled = !enCours && email.contains('@') && mdp.length >= 8 && (!premiere || mdp == mdp2),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(if (enCours) "Connexion…" else if (premiere) "Créer mon compte" else "Se connecter", fontWeight = FontWeight.Bold) }
        if (Repo.demo) {
            OutlinedButton(onClick = { comptesDemo = true }, modifier = Modifier.fillMaxWidth()) { Text("Comptes de démonstration") }
        }
    }

    if (comptesDemo) {
        AlertDialog(
            onDismissRequest = { comptesDemo = false },
            title = { Text("Comptes de démonstration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Demo.comptesTest.forEach { c ->
                        TextButton(
                            onClick = { premiere = false; email = c.email; mdp = Demo.MOT_DE_PASSE; comptesDemo = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(if (c.profil.memberId == null && c.profil.role == "adherent") "Nouveau membre (sans fiche)" else Demo.roles.firstOrNull { it.code == c.profil.role }?.nom ?: c.profil.role, fontWeight = FontWeight.Bold)
                                Text(c.email, fontSize = 13.sp, color = Couleurs.Texte2)
                            }
                        }
                    }
                    Text("Mot de passe$NBSP: ${Demo.MOT_DE_PASSE}", fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { comptesDemo = false }) { Text("Fermer") } },
        )
    }
}

// Mot de passe avec bouton « afficher » (œil) et type pour le remplissage automatique
@Composable
fun ChampMotDePasse(valeur: String, onChange: (String) -> Unit, libelle: String, type: ContentType = ContentType.Password,
                    ime: ImeAction = ImeAction.Done, erreur: Boolean = false, onValider: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        valeur, onChange, label = { Text(libelle) }, singleLine = true, isError = erreur,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onValider() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = type },
    )
}

private enum class Onglet(val titre: String) { Accueil("Accueil"), Operations("Opérations"), Depenses("Demandes"), Cotisations("Cotisations"), Plus("Plus") }

@Composable
fun Principal(cle: String) {
    var donnees by remember(cle) { mutableStateOf<Donnees?>(null) }
    var erreur by remember(cle) { mutableStateOf<String?>(null) }
    var sansProfil by remember(cle) { mutableStateOf(false) }
    var version by remember(cle) { mutableStateOf(0) }
    var assistantRepousse by rememberSaveable(cle) { mutableStateOf(false) }
    var assistantDemande by rememberSaveable(cle) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cle, version, Synchro.versionReferentiels) {
        try {
            val p = Repo.profil()
            if (p == null || !p.actif) { sansProfil = true; return@LaunchedEffect }
            val droits = Repo.mesDroits()
            fun a(vararg c: String) = c.any { it in droits }
            donnees = Donnees(p, Repo.organisation(),
                if (a("consulter_finances", "saisir_ecritures", "payer_depenses", "gerer_cotisations", "rapprocher")) Repo.comptes() else emptyList(),
                Repo.categories(),
                if (a("voir_membres", "gerer_membres", "gerer_cotisations")) Repo.membres() else emptyList(),
                droits, Repo.roles(), try { Repo.exercices() } catch (_: Exception) { emptyList() })
            try { Repo.chargerLogo() } catch (_: Exception) { }
            erreur = null
        } catch (e: Exception) { erreur = traduireErreur(e) }
    }

    val d = donnees
    val err = erreur
    when {
        sansProfil -> MessagePlein("Accès en attente",
            "Votre adresse n’a pas encore d’accès. Demandez à l’administrateur de vous inviter.") { scope.launch { Repo.deconnecter() } }
        // Assistant : proposé à l'administrateur tant que l'association n'est pas configurée, mais dosé (Rappels)
        d != null && d.peut("administrer") && !d.organisation.configuree && !assistantRepousse && remember(d.profil.id) { Rappels.permis(d.profil.id, "assistant") } ->
            AssistantConfiguration(d, onFini = { assistantRepousse = true; version++ }, onPlusTard = { Rappels.repousser(d.profil.id, "assistant"); EtatMiseEnRoute.fenetreMontree = true; assistantRepousse = true })
        d != null && assistantDemande -> AssistantConfiguration(d, onFini = { assistantDemande = false; version++ }, onPlusTard = { assistantDemande = false })
        d != null -> Navigation(d, recharger = { version++ }, onAssistant = { assistantDemande = true })
        err != null -> MessagePlein("Connexion impossible", err, action = "Réessayer") { version++ }
        else -> Chargement()
    }
}

@Composable
private fun MessagePlein(titre: String, texte: String, action: String = "Se déconnecter", onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoAsso(Modifier.size(72.dp))
        Text(titre, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(texte, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

// Barre du haut : logo et nom de l'association, retour ; Paramètres (compte, déconnexion) à droite
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarreHaut(d: Donnees, retour: (() -> Unit)?, surReglages: Boolean, onCloche: () -> Unit, onReglages: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            if (retour != null) IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            else LogoAsso(Modifier.padding(start = 12.dp, end = 4.dp).size(36.dp))
        },
        title = { Text(d.organisation.nom, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            BoutonCloche(onCloche)
            IconButton(onClick = onReglages, colors = if (surReglages) IconButtonDefaults.iconButtonColors(containerColor = Couleurs.OrangeClair, contentColor = Couleurs.SurOrangeClair) else IconButtonDefaults.iconButtonColors()) {
                Icon(Icons.Outlined.Settings, contentDescription = "Paramètres")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun Navigation(d: Donnees, recharger: () -> Unit, onAssistant: () -> Unit = {}) {
    val onglets = Onglet.entries.filter { o ->
        when (o) {
            Onglet.Accueil, Onglet.Plus -> true
            Onglet.Operations -> d.peut("consulter_finances", "saisir_ecritures")
            Onglet.Depenses -> d.peut("demander_depenses", "valider_depenses", "payer_depenses", "consulter_finances")
            Onglet.Cotisations -> d.peut("gerer_cotisations", "consulter_finances")
        }
    }
    var onglet by rememberSaveable { mutableStateOf(Onglet.Accueil) }
    var sousEcran by rememberSaveable { mutableStateOf<String?>(null) }
    var compteFiltre by remember { mutableStateOf<String?>(null) }
    // Paramètres : un espace à part (plus de barre d'onglets ni de barre du haut de l'application) ; « Fermer » ramène où l'on était
    var sectionParam by remember { mutableStateOf<String?>(null) }
    var avantParam by remember { mutableStateOf<Pair<Onglet, String?>?>(null) }
    val enParametres = sousEcran == "parametres"
    val ouvrirParametres = { if (sousEcran != "parametres") avantParam = onglet to sousEcran; if (!d.droits.isEmpty()) onglet = Onglet.Plus; sousEcran = "parametres" }
    val fermerParametres = { val a = avantParam; if (a != null) { onglet = a.first; sousEcran = a.second } else sousEcran = null; avantParam = null; sectionParam = null }
    val snackbar = remember { SnackbarHostState() }
    Annulation.hote = snackbar
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }
    var nouveautesOuvertes by remember { mutableStateOf(false) }
    var ficheRepoussee by rememberSaveable { mutableStateOf(false) }
    var ficheDemandee by remember { mutableStateOf(false) }
    // Fiche de membre : demandée une fois par ouverture, jamais dans les Paramètres, et dosée (Rappels)
    val ficheAuto = remember(d.profil.id) { d.profil.memberId == null && Rappels.permis(d.profil.id, "fiche") }
    var etapes by remember { mutableStateOf<List<Triple<String, Boolean, String>>?>(null) }
    var miseEnRouteOuverte by remember { mutableStateOf(false) }
    // Nouveautés : rechargées chaque minute tant que l'application est ouverte
    LaunchedEffect(d.profil.id) { while (true) { EtatNouveautes.charger(); kotlinx.coroutines.delay(60_000) } }
    // Temps réel : chaque écran ouvert se met à jour seul quand une donnée change (Synchro)
    val porteeSynchro = rememberCoroutineScope()
    LaunchedEffect(d.profil.id) { Synchro.demarrer(porteeSynchro) }
    // Personne sans droit financier : espace adhérent seul
    val adherentSeul = d.droits.isEmpty()
    var vueAdherent by rememberSaveable { mutableStateOf(0) }   // 0 : accueil, 1 : planning
    val retour: (() -> Unit)? = when {
        enParametres -> fermerParametres
        onglet == Onglet.Plus && sousEcran != null && !adherentSeul -> { { sousEcran = null } }
        adherentSeul && sousEcran != null -> { { sousEcran = null } }
        else -> null
    }
    RetourSysteme(retour != null || onglet != Onglet.Accueil || vueAdherent == 1) {
        if (vueAdherent == 1) vueAdherent = 0 else if (retour != null) retour() else onglet = Onglet.Accueil
    }
    // Onglet ouvert : ses nouveautés sont marquées comme vues (comme sur le site)
    val sectionVue = when {
        adherentSeul -> if (vueAdherent == 1) "activites" else if (sousEcran == null) "cotisations" else null
        onglet == Onglet.Operations -> "ecritures"; onglet == Onglet.Depenses -> "depenses"; onglet == Onglet.Cotisations -> "cotisations"
        onglet == Onglet.Plus && sousEcran == "activites" -> "activites"; onglet == Onglet.Plus && sousEcran == "membres" -> "membres"
        else -> null
    }
    LaunchedEffect(sectionVue) { sectionVue?.let { kotlinx.coroutines.delay(1500); EtatNouveautes.vu(it) } }
    val aller: (String) -> Unit = { section ->
        if (adherentSeul) { sousEcran = null; vueAdherent = if (section == "activites") 1 else 0 }
        else when (section) {
            "ecritures" -> { compteFiltre = null; onglet = Onglet.Operations }
            "depenses" -> onglet = Onglet.Depenses
            "cotisations" -> onglet = if (Onglet.Cotisations in onglets) Onglet.Cotisations else Onglet.Accueil
            "communiques" -> onglet = Onglet.Accueil   // les communiqués s'affichent sur la bannière de l'accueil
            else -> { sousEcran = section; onglet = Onglet.Plus }
        }
    }
    val allerCible: (String) -> Unit = { cible ->
        when (cible) {
            "assistant" -> onAssistant()
            "fiche" -> ficheDemandee = true
            "apparence", "comptes" -> { sectionParam = cible; ouvrirParametres() }
            else -> { if (enParametres) fermerParametres(); aller(cible) }
        }
    }
    // Rappel discret de la mise en route : petit message en bas, au plus une fois par semaine, jamais par-dessus une fenêtre
    LaunchedEffect(d.profil.id) {
        val l = etapesMiseEnRoute(d); etapes = l
        val reste = l.count { !it.second }
        EtatMiseEnRoute.restantes = reste
        if (reste > 0 && !ficheAuto && !EtatMiseEnRoute.fenetreMontree && Rappels.discretPermis(d.profil.id, "miseenroute")) {
            kotlinx.coroutines.delay(2500)
            Rappels.marquerDiscret(d.profil.id, "miseenroute")
            val admin = d.peut("administrer")
            val r = snackbar.showSnackbar(if (admin) "Mise en route : $reste étape${if (reste > 1) "s" else ""} restante${if (reste > 1) "s" else ""}" else "Votre fiche de membre est à compléter",
                actionLabel = if (admin) "Voir" else "Remplir", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) { if (admin) miseEnRouteOuverte = true else ficheDemandee = true }
        }
    }
    val c = EtatNouveautes.n.value.compteurs
    Scaffold(
        // Accueil : pas de barre du haut, la bannière porte déjà le logo, le nom et la roue dentée
        topBar = { if (!enParametres && (if (adherentSeul) (vueAdherent != 0 || sousEcran != null) else onglet != Onglet.Accueil)) BarreHaut(d, retour, false, onCloche = { nouveautesOuvertes = true }) { ouvrirParametres() } },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (enParametres) Unit
            else if (adherentSeul) NavigationBar {
                listOf(Triple("Accueil", Icons.Outlined.Home, "cotisations"), Triple("Planning", Icons.Outlined.Event, "activites")).forEachIndexed { i, (l, ic, sec) ->
                    NavigationBarItem(selected = vueAdherent == i && sousEcran == null, onClick = { vueAdherent = i; sousEcran = null },
                        icon = { IconeAvecPastille(ic, c[sec] ?: 0, null) }, label = { Text(l) })
                }
            } else NavigationBar {
                onglets.forEach { o ->
                    NavigationBarItem(
                        selected = onglet == o, onClick = { onglet = o; sousEcran = null; if (o == Onglet.Operations) compteFiltre = null },
                        icon = {
                            IconeAvecPastille(when (o) {
                                Onglet.Accueil -> Icons.Outlined.Home
                                Onglet.Operations -> Icons.AutoMirrored.Outlined.ReceiptLong
                                Onglet.Cotisations -> Icons.Outlined.Payments
                                Onglet.Depenses -> Icons.Outlined.Euro
                                Onglet.Plus -> Icons.Outlined.GridView
                            }, when (o) {
                                Onglet.Operations -> c["ecritures"] ?: 0; Onglet.Depenses -> c["depenses"] ?: 0; Onglet.Cotisations -> c["cotisations"] ?: 0
                                Onglet.Plus -> (c["activites"] ?: 0) + (c["membres"] ?: 0); else -> 0
                            }, null)
                        },
                        label = { Text(o.titre, maxLines = 1) },
                    )
                }
            }
        },
    ) { marges ->
        // Changement d'onglet ou d'écran : fondu enchaîné court
        androidx.compose.animation.AnimatedContent(Triple(onglet, sousEcran, vueAdherent), Modifier.padding(marges).consumeWindowInsets(marges).fillMaxSize(),
            transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
            label = "ecran") { (ongletVu, sousEcranVu, vueVu) ->
        Box(Modifier.fillMaxSize()) {
            if (adherentSeul) {
                if (sousEcranVu == "parametres") EcranParametres(d, message, recharger, onFermer = fermerParametres, sectionInitiale = sectionParam, onCible = allerCible)
                else if (vueVu == 1) EcranPlanning(d, message) else EcranAdherent(d, message, onReglages = ouvrirParametres, onCloche = { nouveautesOuvertes = true })
            }
            else when (ongletVu) {
                Onglet.Accueil -> EcranAccueil(d, onAller = { cible ->
                    when {
                        cible == "nouveautes" -> nouveautesOuvertes = true
                        cible == "parametres" -> ouvrirParametres()
                        cible == "depenses" -> onglet = Onglet.Depenses
                        cible.startsWith("operations:") -> { compteFiltre = cible.substringAfter(':'); onglet = Onglet.Operations }
                        cible == "cotisations" -> onglet = Onglet.Cotisations
                        else -> { sousEcran = cible; onglet = Onglet.Plus }
                    }
                })
                Onglet.Operations -> EcranOperations(d, message, compteFiltre)
                Onglet.Depenses -> EcranDepenses(d, message)
                Onglet.Cotisations -> EcranCotisations(d, message)
                Onglet.Plus -> when (sousEcranVu) {
                    "membres" -> EcranMembres(d, message, recharger)
                    "budget" -> EcranBudget(d, message)
                    "activites" -> EcranPlanning(d, message, onBudget = if (d.peut("consulter_finances", "gerer_budget")) ({ sousEcran = "budget" }) else null)
                    "tiers" -> EcranTiers(d, message)
                    "rapprochement" -> EcranRapprochement(d, message)
                    "materiel" -> EcranMateriel(d, message)
                    "rapports" -> EcranRapports(d, message)
                    "parametres" -> EcranParametres(d, message, recharger, onAssistant = onAssistant, onFermer = fermerParametres, sectionInitiale = sectionParam, onCible = allerCible)
                    else -> EcranPlus(d) { sousEcran = it }
                }
            }
        }
        }
    }
    if (nouveautesOuvertes) FeuilleNouveautes(onAller = aller) { nouveautesOuvertes = false }
    // Nouveau membre : sa fiche lui est demandée tant qu'elle n'est pas remplie (« Plus tard » jusqu'à la prochaine ouverture)
    if (d.profil.memberId == null && (ficheDemandee || (ficheAuto && !ficheRepoussee && !enParametres)))
        FeuilleMaFiche(d, message) { ok -> if (!ok && !ficheDemandee) { Rappels.repousser(d.profil.id, "fiche"); message("D’accord, on vous le rappellera plus tard") }; ficheRepoussee = true; ficheDemandee = false; if (ok) recharger() }
    if (miseEnRouteOuverte) etapes?.let { l -> FeuilleMiseEnRoute(l, onChoix = allerCible, onPlusTard = { Rappels.marquerDiscret(d.profil.id, "miseenroute"); miseEnRouteOuverte = false }) { miseEnRouteOuverte = false } }
}
