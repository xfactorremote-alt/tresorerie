package org.jpgrenoble.tresorerie

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// =====================================================================
// Création directe des comptes (fonction creer-comptes de la base), comme feuilleCreerComptes du site :
// l'administrateur coche des membres, choisit leur fonction et un mot de passe provisoire ; aucun e-mail
// d'invitation n'est envoyé. À la première connexion, la personne choisit son mot de passe (MotDePasseProvisoire).
// =====================================================================

/** Tirage sûr d'un entier dans [0, n) (aléa cryptographique de Uuid.random). */
@OptIn(ExperimentalUuidApi::class)
fun aleaSur(n: Int): Int {
    val o = Uuid.random().toByteArray()
    val v = ((o[0].toLong() and 0xFF) shl 24) or ((o[1].toLong() and 0xFF) shl 16) or ((o[2].toLong() and 0xFF) shl 8) or (o[3].toLong() and 0xFF)
    return (v % n).toInt()
}

/** Mot de passe provisoire lisible : trois syllabes, un tiret, quatre chiffres (« Bamoli-4827 »), comme motDePasseProvisoire du site. */
fun motDePasseProvisoire(alea: (Int) -> Int = ::aleaSur): String {
    val c = "bcdfghjkmnprstvz"; val v = "aeiou"
    fun syl() = "${c[alea(c.length)]}${v[alea(v.length)]}"
    val mot = syl() + syl() + syl()
    return mot.replaceFirstChar { it.uppercase() } + "-" + (1000 + alea(9000))
}

/** Message à envoyer à chacun (même texte que messageCompte du site). */
fun messageCompte(asso: String, m: Membre, email: String, mdp: String, existant: Boolean = false): String {
    val nom = if (asso.isNotBlank()) nomAssoLisible(asso) else "l’association"
    return "Bonjour ${m.prenom},\n\nTon compte sur l’espace de $nom est prêt.\n\n👉 ${Config.SITE_URL}\n📧 Identifiant : $email\n" +
        (if (existant) "🔑 Mot de passe : celui que tu as déjà choisi (sinon « Mot de passe oublié »)"
        else "🔑 Mot de passe provisoire : $mdp\n\nÀ ta première connexion, tu choisiras ton propre mot de passe.") + "\n\nLe bureau de $nom"
}

/** Membres qui peuvent recevoir un compte : actifs, sans compte ; ceux qui ont une invitation d'abord (comme candidatsCompte du site). */
fun candidatsCompte(membres: List<Membre>, profils: List<Profil>, invitations: List<Invitation>): List<Membre> =
    membres.filter { m -> m.actif && profils.none { it.memberId == m.id } }
        .sortedWith(compareBy<Membre>({ m -> if (invitations.any { it.membreId == m.id }) 0 else 1 }, { it.nomComplet.lowercase() }))

private val EMAIL_COMPTE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Feuille « Créer des comptes » puis son résultat (comptes créés, message à envoyer, refus avec leur motif). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleCreerComptes(d: Donnees, profils: List<Profil>, invitations: List<Invitation>, preselection: List<DemandeCompte>,
                        message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val candidats = remember(profils, invitations) { candidatsCompte(d.membres, profils, invitations) }
    val choix = remember { mutableStateMapOf<String, DemandeCompte>().apply { preselection.forEach { put(it.memberId, it) } } }
    var mdp by remember { mutableStateOf(motDePasseProvisoire()) }
    var recherche by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var resultats by remember { mutableStateOf<List<ResultatCompte>?>(null) }
    val scope = rememberCoroutineScope()
    val roles = d.roles.sortedBy { rangRole(it.code) }
    fun defaut(m: Membre): DemandeCompte {
        val inv = invitations.firstOrNull { it.membreId == m.id }
        return DemandeCompte(m.id, inv?.email ?: m.email ?: "", inv?.role ?: "adherent")
    }
    ModalBottomSheet(onDismissRequest = { onFini(resultats?.any { it.ok } == true) }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val r = resultats
            if (r != null) { ResultatComptes(d, r, mdp) { onFini(r.any { it.ok }) }; return@Column }
            Text("Créer des comptes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Le compte est prêt tout de suite : aucun e-mail d’invitation n’est envoyé. Chacun se connecte avec son adresse et le mot de passe provisoire, puis choisit son propre mot de passe.",
                color = Couleurs.Texte2, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(mdp, { mdp = it.trim() }, label = { Text("Mot de passe provisoire *") }, singleLine = true, isError = mdp.length < 8,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { mdp = motDePasseProvisoire() }) { Text("Autre") }
            }
            Text("Le même pour les personnes cochées, à leur transmettre ; 8 caractères au moins. Chacun le remplace à sa première connexion.", color = Couleurs.Texte2, fontSize = 13.sp)
            if (candidats.size > 8) OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher un membre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val visibles = candidats.filter { m -> recherche.isBlank() || sansAccentsCode("${m.nomComplet} ${m.email ?: ""}").contains(sansAccentsCode(recherche)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${choix.size} coché${if (choix.size > 1) "s" else ""} sur ${candidats.size}", color = Couleurs.Texte2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { visibles.forEach { m -> if (m.id !in choix) choix[m.id] = defaut(m) } }) { Text("Tout cocher") }
                TextButton(onClick = { choix.clear() }) { Text("Tout décocher") }
            }
            if (candidats.isEmpty()) Text("Tous les membres actifs ont déjà un compte.", color = Couleurs.Texte2)
            CarteBlanche {
                visibles.forEachIndexed { i, m ->
                    key(m.id) {
                        if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                        val c = choix[m.id]
                        val e = c?.email ?: defaut(m).email
                        Row(Modifier.fillMaxWidth().clickable { if (c != null) choix.remove(m.id) else choix[m.id] = defaut(m) }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(c != null, { v -> if (v) choix[m.id] = defaut(m) else choix.remove(m.id) })
                            Column(Modifier.weight(1f)) {
                                Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((if (e.isNotBlank()) e else "e-mail manquant") + if (invitations.any { it.membreId == m.id }) " · invitation en attente" else "",
                                    fontSize = 13.sp, color = if (e.isNotBlank()) Couleurs.Texte2 else Couleurs.Erreur, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (c != null) Column(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(c.email, { choix[m.id] = c.copy(email = it.trim()) }, label = { Text("Adresse e-mail *") }, singleLine = true,
                                isError = !EMAIL_COMPTE.matches(c.email), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                            ChoixListe("Fonction", roles.firstOrNull { it.code == c.role }?.nom ?: c.role, roles.map { it.nom }) { j -> choix[m.id] = c.copy(role = roles[j].code) }
                        }
                    }
                }
                if (visibles.isEmpty() && candidats.isNotEmpty()) Text("Aucun membre trouvé.", color = Couleurs.Texte2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = { onFini(false) }) { Text("Fermer") }
                Button(enabled = choix.isNotEmpty() && !enCours, onClick = {
                    if (mdp.length < 8) { message("Mot de passe provisoire : 8 caractères au moins"); return@Button }
                    choix.values.firstOrNull { !EMAIL_COMPTE.matches(it.email) }?.let { x -> message("Adresse e-mail à compléter pour ${d.membres.firstOrNull { it.id == x.memberId }?.prenom ?: ""}"); return@Button }
                    enCours = true
                    scope.launch {
                        try { resultats = Repo.creerComptes(mdp, choix.values.map { it.copy(email = it.email.lowercase()) }) }
                        catch (e: Exception) { message(traduireErreur(e)) }
                        enCours = false
                    }
                }) { Text(if (enCours) "Création…" else "Créer ${if (choix.isEmpty()) "" else "${choix.size} "}compte${if (choix.size > 1) "s" else ""}") }
            }
        }
    }
}

// nomSansFiche : nom affiché pour un compte sans fiche de membre (compte rétabli depuis la corbeille)
@Composable
internal fun ResultatComptes(d: Donnees, resultats: List<ResultatCompte>, mdp: String, nomSansFiche: String = "", onFermer: () -> Unit) {
    val oks = resultats.filter { it.ok }; val kos = resultats.filter { !it.ok }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val presse = LocalClipboardManager.current
    val envoyes = remember { mutableStateListOf<String>() }
    fun membre(id: String?) = d.membres.firstOrNull { it.id == id } ?: Membre(id ?: "", nomSansFiche, "", 1, 1)
    Text(if (oks.isEmpty()) "Aucun compte créé" else "${oks.size} compte${if (oks.size > 1) "s" else ""} créé${if (oks.size > 1) "s" else ""}",
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (oks.isNotEmpty()) {
        Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.XGrand) {
            Text(if (oks.any { !it.existant }) buildAnnotatedString { append("Mot de passe provisoire : "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(mdp) }
                append(". Envoyez à chacun son message : à la première connexion, il choisit son propre mot de passe.") }
                else AnnotatedString("Ces personnes avaient déjà un compte : il est relié à leur fiche et elles gardent leur mot de passe. Envoyez-leur leur message."),
                fontSize = 14.sp, modifier = Modifier.padding(16.dp).fillMaxWidth())
        }
        CarteBlanche {
            oks.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                val m = membre(r.memberId)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.nomComplet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.email + if (r.existant) " · compte existant relié, garde son mot de passe" else "", fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val wa = numeroWa(m.whatsapp ?: "")
                    when {
                        wa == null -> Text("sans WhatsApp", fontSize = 13.sp, color = Couleurs.Texte2)
                        r.email in envoyes -> TextButton(onClick = { uri.openUri("https://wa.me/$wa?text=" + encoderUrl(messageCompte(d.organisation.nom, m, r.email, mdp, r.existant))) }) { Text("✓ Envoyé") }
                        else -> OutlinedButton(onClick = { envoyes += r.email; uri.openUri("https://wa.me/$wa?text=" + encoderUrl(messageCompte(d.organisation.nom, m, r.email, mdp, r.existant))) }) { Text("WhatsApp") }
                    }
                }
            }
        }
    }
    if (kos.isNotEmpty()) {
        Text("Non créés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        CarteBlanche {
            kos.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                Column { Text(membre(r.memberId).nomComplet, fontWeight = FontWeight.SemiBold); Text(r.erreur, fontSize = 13.sp, color = Couleurs.Erreur) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (oks.isNotEmpty()) OutlinedButton(onClick = {
            presse.setText(AnnotatedString(oks.joinToString("\n\n———\n\n") { messageCompte(d.organisation.nom, membre(it.memberId), it.email, mdp, it.existant) }))
        }) { Text("Copier les messages") }
        Button(onClick = onFermer) { Text("Terminé") }
    }
}

/** Première connexion avec le mot de passe provisoire : choisir son mot de passe (comme ecranMotDePasseProvisoire du site). */
@Composable
fun MotDePasseProvisoire(d: Donnees, onFini: () -> Unit) {
    var mdp by remember { mutableStateOf("") }
    var mdp2 by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val autofill = androidx.compose.ui.platform.LocalAutofillManager.current
    val prenom = d.profil.nom.substringBefore(' ').takeIf { '@' !in it } ?: ""
    fun valider() {
        when {
            mdp.length < 8 -> { erreur = "8 caractères au moins"; return }
            mdp != mdp2 -> { erreur = "Les deux mots de passe sont différents"; return }
        }
        enCours = true
        scope.launch {
            try { Repo.changerMotDePasse(mdp); Repo.motDePasseDefinitif(); autofill?.commit(); onFini() }
            catch (e: Exception) {
                erreur = if ("different from the old" in (e.message ?: "")) "Choisissez un mot de passe différent du mot de passe provisoire" else traduireErreur(e)
                enCours = false
            }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoAsso(Modifier.size(56.dp))
                Column { Text(d.organisation.nom, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Trésorerie de l’association", color = Couleurs.Texte2, fontSize = 13.sp) }
            }
            Text("Bienvenue" + if (prenom.isNotEmpty()) " $prenom" else "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.XGrand) {
                Text("Votre compte a été créé par le bureau avec un mot de passe provisoire. Choisissez maintenant votre mot de passe personnel : vous l’utiliserez désormais pour vous connecter.",
                    fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            }
            ChampMotDePasse(mdp, { mdp = it; erreur = null }, "Nouveau mot de passe (8 caractères minimum)", ContentType.NewPassword, ImeAction.Next)
            ChampMotDePasse(mdp2, { mdp2 = it; erreur = null }, "Confirmez le mot de passe", ContentType.NewPassword, erreur = mdp2.isNotEmpty() && mdp2 != mdp) { valider() }
            erreur?.let { Text(it, color = Couleurs.Erreur, fontSize = 14.sp) }
            Button(onClick = { valider() }, enabled = !enCours, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Enregistrer mon mot de passe") }
            TextButton(onClick = { scope.launch { Repo.deconnecter() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Se déconnecter") }
        }
    }
}

// =====================================================================
// Supprimer un compte d'accès créé par erreur ou pour essai (fonction supprimer-compte), comme supprimerCompte du site :
// le compte part à la corbeille, l'adresse redevient libre, la fiche de membre reste ; « Annuler » le recrée
// avec un nouveau mot de passe provisoire, puis le message à transmettre (ResultatComptes).
// =====================================================================
/** Renvoie la fonction qui demande confirmation puis supprime le compte ; la suite tourne dans la portée de l'application. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSuppressionCompte(d: Donnees, message: (String) -> Unit, apres: () -> Unit): (Profil) -> Unit {
    var cible by remember { mutableStateOf<Profil?>(null) }
    var retabli by remember { mutableStateOf<Pair<CompteRestaure, String>?>(null) }
    val apresActuel by rememberUpdatedState(apres)
    cible?.let { p ->
        var motif by remember(p.id) { mutableStateOf("") }
        var enCours by remember(p.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cible = null },
            icon = { Icon(Icons.Outlined.Delete, null, tint = Couleurs.Erreur) },
            title = { Text("Supprimer le compte de ${p.nom}$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pour un compte créé par erreur ou pour essai. La personne ne peut plus se connecter et son adresse redevient libre ; sa fiche de membre et ses cotisations ne bougent pas. Le compte part dans la corbeille : « Restaurer » le recrée avec un nouveau mot de passe provisoire.",
                        fontSize = 14.sp, color = Couleurs.Texte2)
                    Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = Formes.XGrand) {
                        Text("Un compte qui a déjà servi (opérations, demandes…) se désactive plutôt : décochez « Actif ».", fontSize = 14.sp, modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(motif, { motif = it.take(120) }, label = { Text("Motif (facultatif)") }, placeholder = { Text("Compte d’essai, mauvaise adresse…") }, singleLine = true)
                }
            },
            confirmButton = {
                OutlinedButton(enabled = !enCours, colors = ButtonDefaults.outlinedButtonColors(contentColor = Couleurs.Erreur),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Erreur.copy(alpha = 0.5f)), onClick = {
                    enCours = true
                    Annulation.portee.launch {
                        try {
                            val idCorbeille = Repo.supprimerCompte(p.id, motif.trim().ifBlank { null })
                            cible = null; apresActuel()
                            val hote = Annulation.hote
                            if (hote != null) {
                                val r = hote.showSnackbar("Compte supprimé, placé dans la corbeille", actionLabel = "Annuler", duration = SnackbarDuration.Long)
                                if (r == SnackbarResult.ActionPerformed) try {
                                    val mdp = motDePasseProvisoire()
                                    retabli = Repo.restaurerCompte(idCorbeille, mdp) to mdp; apresActuel()
                                } catch (e: Exception) { message(traduireErreur(e)) }
                            } else message("Compte supprimé, placé dans la corbeille")
                        } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                    }
                }) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Supprimer le compte") }
            },
            dismissButton = { TextButton(onClick = { cible = null }) { Text("Annuler") } },
        )
    }
    retabli?.let { (r, mdp) -> FeuilleCompteRetabli(d, r, mdp) { retabli = null } }
    return { cible = it }
}

/** Compte rétabli (Annuler ou Paramètres › Corbeille) : même résultat que « Créer des comptes », avec le message à transmettre. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleCompteRetabli(d: Donnees, r: CompteRestaure, mdp: String, onFermer: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onFermer) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultatComptes(d, listOf(ResultatCompte(r.memberId ?: "", r.email, ok = true)), mdp, r.nom, onFermer)
        }
    }
}
