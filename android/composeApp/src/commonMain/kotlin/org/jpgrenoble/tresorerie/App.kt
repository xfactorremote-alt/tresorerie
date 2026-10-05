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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val categories: List<Categorie>,
    val membres: List<Membre>,
    val droits: Set<String>,
    val roles: List<Role>,
) {
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
            val statut by Repo.client.auth.sessionStatus.collectAsState()
            when (statut) {
                is SessionStatus.Authenticated -> Principal(cle = "supabase")
                is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> Connexion()
                else -> Chargement()
            }
        }
    }
}

@Composable
fun Chargement() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
fun Connexion() {
    var email by remember { mutableStateOf("") }
    var mdp by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var nomAsso by remember { mutableStateOf("JP Grenoble") }
    var comptesDemo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { nomAsso = Repo.organisation().nom; Repo.chargerLogo() } catch (_: Exception) { }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LogoAsso(Modifier.size(112.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nomAsso, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Trésorerie", style = MaterialTheme.typography.titleMedium, color = Couleurs.Texte2)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            email, { email = it.trim() }, label = { Text("Adresse e-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            mdp, { mdp = it }, label = { Text("Mot de passe") }, singleLine = true,
            supportingText = { Text("8 caractères minimum") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
        Button(
            onClick = {
                enCours = true; erreur = null
                scope.launch {
                    try { Repo.connecter(email, mdp) } catch (e: Exception) { erreur = traduireErreur(e) }
                    enCours = false
                }
            },
            enabled = !enCours && email.isNotBlank() && mdp.length >= 8,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text("Se connecter", fontWeight = FontWeight.Bold) }
        if (Repo.demo) {
            TextButton(onClick = { comptesDemo = true }) { Text("Comptes de démonstration") }
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
                            onClick = { email = c.email; mdp = Demo.MOT_DE_PASSE; comptesDemo = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(Demo.roles.firstOrNull { it.code == c.profil.role }?.nom ?: c.profil.role, fontWeight = FontWeight.Bold)
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

private enum class Onglet(val titre: String) { Accueil("Accueil"), Operations("Opérations"), Depenses("Dépenses"), Cotisations("Cotisations"), Plus("Plus") }

@Composable
fun Principal(cle: String) {
    var donnees by remember(cle) { mutableStateOf<Donnees?>(null) }
    var erreur by remember(cle) { mutableStateOf<String?>(null) }
    var sansProfil by remember(cle) { mutableStateOf(false) }
    var version by remember(cle) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cle, version) {
        try {
            val p = Repo.profil()
            if (p == null || !p.actif) { sansProfil = true; return@LaunchedEffect }
            val droits = Repo.mesDroits()
            fun a(vararg c: String) = c.any { it in droits }
            donnees = Donnees(p, Repo.organisation(),
                if (a("consulter_finances", "saisir_ecritures", "payer_depenses", "gerer_cotisations", "rapprocher")) Repo.comptes() else emptyList(),
                Repo.categories(),
                if (a("voir_membres", "gerer_membres", "gerer_cotisations")) Repo.membres() else emptyList(),
                droits, Repo.roles())
            try { Repo.chargerLogo() } catch (_: Exception) { }
            erreur = null
        } catch (e: Exception) { erreur = traduireErreur(e) }
    }

    val d = donnees
    val err = erreur
    when {
        sansProfil -> MessagePlein("Accès en attente",
            "Votre adresse n’a pas encore d’accès. Demandez à l’administrateur de vous inviter.") { scope.launch { Repo.deconnecter() } }
        d != null -> Navigation(d, recharger = { version++ })
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

// Barre du haut : logo et nom de l'association, retour, menu du profil (déconnexion discrète)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarreHaut(d: Donnees, retour: (() -> Unit)?, onMonEspace: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TopAppBar(
        navigationIcon = {
            if (retour != null) IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            else LogoAsso(Modifier.padding(start = 12.dp, end = 4.dp).size(36.dp))
        },
        title = { Text(d.organisation.nom, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Avatar(d.profil.nom.substringBefore(' '), d.profil.nom.substringAfter(' ', ""), taille = 34) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(d.profil.nom, fontWeight = FontWeight.Bold)
                        Text(d.nomRole(d.profil.role), fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    HorizontalDivider()
                    if (onMonEspace != null) DropdownMenuItem(text = { Text("Ma cotisation") }, onClick = { menu = false; onMonEspace() },
                        leadingIcon = { Icon(Icons.Outlined.Person, null) })
                    DropdownMenuItem(text = { Text("Se déconnecter") }, onClick = { menu = false; scope.launch { Repo.deconnecter() } },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) })
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun Navigation(d: Donnees, recharger: () -> Unit) {
    val onglets = Onglet.entries.filter { o ->
        when (o) {
            Onglet.Accueil, Onglet.Plus -> true
            Onglet.Operations -> d.peut("consulter_finances", "saisir_ecritures")
            Onglet.Depenses -> d.peut("demander_depenses", "valider_depenses", "payer_depenses", "consulter_finances")
            Onglet.Cotisations -> d.peut("gerer_cotisations", "consulter_finances")
        }
    }
    var onglet by remember { mutableStateOf(Onglet.Accueil) }
    var sousEcran by remember { mutableStateOf<String?>(null) }
    var compteFiltre by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }
    // Personne sans droit financier : espace adhérent seul
    val adherentSeul = d.droits.isEmpty()
    var vueAdherent by remember { mutableStateOf(0) }   // 0 : accueil, 1 : planning
    val retour: (() -> Unit)? = when {
        onglet == Onglet.Plus && sousEcran != null && !adherentSeul -> { { sousEcran = null } }
        adherentSeul && sousEcran != null -> { { sousEcran = null } }
        else -> null
    }
    RetourSysteme(retour != null || onglet != Onglet.Accueil || vueAdherent == 1) {
        if (vueAdherent == 1) vueAdherent = 0 else if (retour != null) retour() else onglet = Onglet.Accueil
    }
    Scaffold(
        topBar = { BarreHaut(d, retour, if (d.profil.memberId != null && !adherentSeul) ({ onglet = Onglet.Plus; sousEcran = "moi" }) else null) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (adherentSeul) NavigationBar {
                listOf("Accueil" to Icons.Outlined.Home, "Planning" to Icons.Outlined.Event).forEachIndexed { i, (l, ic) ->
                    NavigationBarItem(selected = vueAdherent == i, onClick = { vueAdherent = i }, icon = { Icon(ic, contentDescription = null) }, label = { Text(l) })
                }
            } else NavigationBar {
                onglets.forEach { o ->
                    NavigationBarItem(
                        selected = onglet == o, onClick = { onglet = o; sousEcran = null; if (o == Onglet.Operations) compteFiltre = null },
                        icon = {
                            Icon(when (o) {
                                Onglet.Accueil -> Icons.Outlined.Home
                                Onglet.Operations -> Icons.AutoMirrored.Outlined.ReceiptLong
                                Onglet.Cotisations -> Icons.Outlined.Payments
                                Onglet.Depenses -> Icons.Outlined.Euro
                                Onglet.Plus -> Icons.Outlined.GridView
                            }, contentDescription = null)
                        },
                        label = { Text(o.titre, maxLines = 1) },
                    )
                }
            }
        },
    ) { marges ->
        Box(Modifier.padding(marges).consumeWindowInsets(marges).fillMaxSize()) {
            if (adherentSeul) { if (vueAdherent == 1) EcranPlanning(d, message) else EcranAdherent(d) { vueAdherent = 1 } }
            else when (onglet) {
                Onglet.Accueil -> EcranAccueil(d, onAller = { cible ->
                    when {
                        cible == "depenses" -> onglet = Onglet.Depenses
                        cible.startsWith("operations:") -> { compteFiltre = cible.substringAfter(':'); onglet = Onglet.Operations }
                        else -> { sousEcran = cible; onglet = Onglet.Plus }
                    }
                })
                Onglet.Operations -> EcranOperations(d, message, compteFiltre)
                Onglet.Depenses -> EcranDepenses(d, message)
                Onglet.Cotisations -> EcranCotisations(d, message)
                Onglet.Plus -> when (sousEcran) {
                    "membres" -> EcranMembres(d, message)
                    "budget" -> EcranBudget(d, message)
                    "activites" -> EcranPlanning(d, message)
                    "tiers" -> EcranTiers(d, message)
                    "rapprochement" -> EcranRapprochement(d, message)
                    "rapports" -> EcranRapports(d, message)
                    "parametres" -> if (d.peut("administrer")) EcranParametres(d, message, recharger) else EcranPlus(d) { sousEcran = it }
                    "moi" -> EcranAdherent(d)
                    else -> EcranPlus(d) { sousEcran = it }
                }
            }
        }
    }
}
