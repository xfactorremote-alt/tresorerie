package org.jpgrenoble.tresorerie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Petit rappel de cotisation ou de participation (comme feuilleRappel du site) :
// un message chaleureux — la cotisation reste libre —, signé par le bureau, envoyé au nom de l'association
// (notification sur les appareils du membre) plutôt que depuis le numéro personnel de celui qui relance.

/** Messages proposés : mêmes textes que RAPPEL_DEFAUT du site. */
val RAPPEL_DEFAUT = mapOf(
    "cotisation" to "Bonjour {prénom} 😊\nOn espère que tu vas bien ! Petit message de {association} au sujet de la cotisation : c’est grâce à elle que l’association vit et que nos activités peuvent continuer.\nQuand tu peux, il reste {montant} pour cette année.{règlement}{lien}\nMerci pour ton soutien et à très vite,\nLe bureau de {association}",
    "participation" to "Bonjour {prénom} 😊\nPour « {collecte} », ta participation compte{montant} : c’est grâce à elle que l’association vit et que ce projet peut se faire.{règlement}{lien}\nMerci et à bientôt,\nLe bureau de {association}",
)
/** Clé du réglage qui garde le message modifié (Paramètres › Cotisations et dépenses). */
fun cleRappel(type: String) = if (type == "participation") "message_rappel_participation" else "message_rappel"

private val PETITS_MOTS = Regex("(?<=\\s)(De|Des|Du|La|Le|Les|Et|En|Pour|Au|Aux|À)(?=\\s)")
/** « JEUNES DE TOUS PAYS » → « Jeunes de Tous Pays » (comme nomAssoMessage du site). */
fun nomAssoMessage(nom: String?): String {
    val n = (nom ?: "").trim()
    if (n.isEmpty()) return "l’association"
    if (n != n.uppercase()) return n
    return PETITS_MOTS.replace(casseTitre(n)) { it.value.lowercase() }
}

/** Texte du rappel, mots remplacés comme texteRappel du site. */
fun texteRappel(modele: String?, prenom: String, association: String?, type: String, montant: Double, collecte: String, infosPaiement: String?, urlLien: String?): String {
    val valeurs = mapOf(
        "prénom" to prenom, "association" to nomAssoMessage(association), "collecte" to collecte,
        "montant" to if (type == "participation") (if (montant > 0) " (montant proposé : ${euros(montant)})" else "") else euros(montant),
        "règlement" to (infosPaiement?.takeIf { it.isNotBlank() }?.let { "\nPour régler : " + it.trim().replace(Regex("\\s*\n\\s*"), " ; ") } ?: ""),
        "lien" to (urlLien?.let { "\nTon espace membre : $it" } ?: ""),
    )
    return Regex("\\{(prénom|prenom|association|montant|collecte|règlement|reglement|lien)\\}").replace(modele?.takeIf { it.isNotBlank() } ?: RAPPEL_DEFAUT.getValue(type)) {
        val k = it.groupValues[1]; valeurs[mapOf("prenom" to "prénom", "reglement" to "règlement")[k] ?: k] ?: ""
    }
}

/** Ce qu'il faut pour ouvrir la feuille : type « cotisation » (montant restant) ou « participation » (collecte). */
data class DemandeRappel(val membre: Membre, val type: String, val montant: Double, val collecte: String = "", val collecteId: String? = null)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeuilleRappel(d: Donnees, r: DemandeRappel, message: (String) -> Unit, onFermer: () -> Unit) {
    val m = r.membre
    val presse = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var etat by remember { mutableStateOf(EtatRappel(m.id)) }
    var texte by remember { mutableStateOf<String?>(null) }
    var envoi by remember { mutableStateOf(false) }
    LaunchedEffect(m.id, r.collecteId) {
        try { Repo.etatRappels(r.collecteId).firstOrNull { it.membreId == m.id }?.let { etat = it } } catch (_: Exception) {}
        val modele = try { Repo.texteReglage(cleRappel(r.type)) } catch (_: Exception) { null }
        val infos = try { Repo.texteReglage("infos_paiement") } catch (_: Exception) { null }
        val lien = try { Repo.liens().firstOrNull { it.membreId == m.id } } catch (_: Exception) { null }
        texte = texteRappel(modele, m.prenom, d.organisation.nom, r.type, r.montant, r.collecte, infos, lien?.let { Repo.urlLien(it, m) })
    }
    val asso = nomAssoMessage(d.organisation.nom)
    val jours = etat.dernier?.let { joursDepuis(it) }
    val num = numeroWa(m.whatsapp)
    // Le rappel est noté dans la base (anti-insistance) ; la feuille se ferme, comme sur le site
    fun noter(canal: String, ok: String?) = Annulation.portee.launch {
        try { Repo.envoyerRappel(m.id, canal, texte!!.trim(), r.collecteId); ok?.let(message) } catch (e: Exception) { if (canal == "notification") message(traduireErreur(e)) }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Petit rappel à ${m.prenom}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (jours != null && jours < 30) Surface(color = Couleurs.BleuClair, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Info, null, tint = Couleurs.SurBleuClair, modifier = Modifier.size(18.dp))
                Text("Un petit mot lui a déjà été envoyé ${when (jours) { 0 -> "aujourd’hui"; 1 -> "hier"; else -> "il y a $jours jours" }}. Pour ne pas être insistant, mieux vaut laisser passer un peu de temps.",
                    fontSize = 14.sp, color = Couleurs.SurBleuClair)
            }
        }
        val t = texte
        if (t == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else OutlinedTextField(t, { texte = it.take(1500) }, label = { Text("Message") }, minLines = 8, modifier = Modifier.fillMaxWidth())
        Text("Signé par le bureau. Le texte proposé se modifie dans Paramètres › Cotisations et dépenses.", fontSize = 13.sp, color = Couleurs.Texte2)
        val pret = !t.isNullOrBlank() && !envoi
        Button(enabled = pret && etat.appareils > 0, modifier = Modifier.fillMaxWidth(), onClick = {
            envoi = true
            scope.launch {
                try { Repo.envoyerRappel(m.id, "notification", t!!.trim(), r.collecteId); message("Petit mot envoyé à ${m.prenom} au nom de l’association"); onFermer() }
                catch (e: Exception) { envoi = false; message(traduireErreur(e)) }
            }
        }) { Icon(Icons.Outlined.Notifications, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Notification de l’association") }
        Text(if (etat.appareils > 0) "Reçue sur ${if (etat.appareils > 1) "ses ${etat.appareils} appareils" else "son téléphone"}, au nom de $asso : aucun numéro personnel."
            else "${m.prenom} n’a pas activé les alertes sur son téléphone : utilisez WhatsApp ou copiez le message.", fontSize = 13.sp, color = Couleurs.Texte2)
        if (num != null) {
            val business = remember { whatsappBusinessInstalle() }
            FilledTonalButton(enabled = pret, modifier = Modifier.fillMaxWidth(), onClick = {
                ouvrirWhatsApp(num, t!!.trim(), business); noter("whatsapp", null); onFermer()
            }) { Text(if (business) "WhatsApp Business" else "WhatsApp") }
            Text(if (business) "S’ouvre dans WhatsApp Business : utilisez-le avec le numéro de l’association pour écrire en son nom."
                else "S’ouvre dans le WhatsApp de cet appareil. Pour écrire au nom de l’association plutôt que depuis votre numéro, utilisez WhatsApp Business avec le numéro de l’association.",
                fontSize = 13.sp, color = Couleurs.Texte2)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = pret, onClick = { presse.setText(AnnotatedString(t!!.trim())); noter("copie", "Message copié"); onFermer() }) { Text("Copier le message") }
            TextButton(onClick = onFermer) { Text("Fermer") }
        }
    }
}

/** Jours écoulés depuis un horodatage ISO (« 2026-10-08T09:12:00Z »). */
private fun joursDepuis(iso: String): Int? = try {
    val i = kotlinx.datetime.Instant.parse(if (iso.length == 10) iso + "T00:00:00Z" else iso.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it })
    ((kotlinx.datetime.Clock.System.now() - i).inWholeHours / 24).toInt()
} catch (_: Exception) { null }

/** Une personne d'un petit rappel groupé : son montant restant (0 = montant libre). */
data class CibleRappel(val membre: Membre, val montant: Double)

// Petit rappel groupé (comme feuilleRappelGroupe du site) : chacun reçoit SON message (prénom, montant, lien),
// par notification au nom de l'association ; ceux qui n'ont pas les alertes : WhatsApp un par un (pas d'envoi groupé possible) ou copie.
// Personne n'est coché d'office s'il a eu un petit mot il y a moins de 30 jours.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeuilleRappelGroupe(d: Donnees, cibles: List<CibleRappel>, type: String, collecte: String, collecteId: String?, message: (String) -> Unit, onFermer: () -> Unit) {
    val presse = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val liste = remember(cibles) { cibles.filter { it.membre.actif } }
    var etats by remember { mutableStateOf<Map<String, EtatRappel>>(emptyMap()) }
    var modele by remember { mutableStateOf<String?>(null) }
    var infos by remember { mutableStateOf<String?>(null) }
    var liens by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val coches = remember { mutableStateListOf<String>() }
    var envoi by remember { mutableStateOf(false) }
    var restants by remember { mutableStateOf<List<CibleRappel>?>(null) }
    var envoyes by remember { mutableStateOf(0) }
    val ouverts = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        try { etats = Repo.etatRappels(collecteId).associateBy { it.membreId } } catch (_: Exception) {}
        infos = try { Repo.texteReglage("infos_paiement") } catch (_: Exception) { null }
        liens = try { Repo.liens().mapNotNull { l -> liste.firstOrNull { it.membre.id == l.membreId }?.let { l.membreId to Repo.urlLien(l, it.membre) } }.toMap() } catch (_: Exception) { emptyMap() }
        coches.addAll(liste.filter { c -> etats[c.membre.id]?.dernier?.let { joursDepuis(it) }?.let { it >= 30 } ?: true }.map { it.membre.id })
        modele = (try { Repo.texteReglage(cleRappel(type)) } catch (_: Exception) { null }) ?: RAPPEL_DEFAUT.getValue(type)
    }
    fun texte(c: CibleRappel) = texteRappel(modele, c.membre.prenom, d.organisation.nom, type, c.montant, collecte, infos, liens[c.membre.id]).trim()
    fun appareils(c: CibleRappel) = etats[c.membre.id]?.appareils ?: 0
    fun noter(c: CibleRappel, canal: String) = Annulation.portee.launch { try { Repo.envoyerRappel(c.membre.id, canal, texte(c), collecteId) } catch (_: Exception) {} }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val m = modele
        val reste = restants
        if (m == null) { Text("Petit rappel groupé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (reste == null) {
            val choisis = liste.filter { it.membre.id in coches }
            val nNotif = choisis.count { appareils(it) > 0 }; val nAutres = choisis.size - nNotif
            Text("Petit rappel groupé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Chacun reçoit son propre message, avec son prénom et son montant, au nom de ${nomAssoMessage(d.organisation.nom)}.", fontSize = 14.sp, color = Couleurs.Texte2)
            OutlinedTextField(m, { modele = it.take(1500) }, label = { Text("Message") }, minLines = 6, modifier = Modifier.fillMaxWidth())
            (choisis.firstOrNull() ?: liste.firstOrNull())?.let { c ->
                var apercu by remember { mutableStateOf(false) }
                TextButton(onClick = { apercu = !apercu }, contentPadding = PaddingValues(0.dp)) { Text((if (apercu) "▾ " else "▸ ") + "Aperçu pour ${c.membre.prenom}") }
                if (apercu) Surface(color = Couleurs.Survol, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) { Text(texte(c), Modifier.padding(8.dp), fontSize = 13.sp, color = Couleurs.TexteFort2) }
            }
            Text("Destinataires (${choisis.size} sur ${liste.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Surface(shape = Formes.Moyen, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), modifier = Modifier.fillMaxWidth()) {
                Column {
                    liste.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                        val coche = c.membre.id in coches
                        val j = etats[c.membre.id]?.dernier?.let { joursDepuis(it) }
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(coche, { if (it) coches.add(c.membre.id) else coches.remove(c.membre.id) })
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(c.membre.nomComplet, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(listOfNotNull(if (type == "cotisation" || c.montant > 0) euros(c.montant) else null,
                                    if (appareils(c) > 0) "Notification" else if (numeroWa(c.membre.whatsapp) != null) "WhatsApp" else "Copie du message").joinToString(" · "),
                                    fontSize = 12.sp, color = Couleurs.Texte2)
                                if (j != null && j < 30) Text("petit mot ${when (j) { 0 -> "aujourd’hui"; 1 -> "hier"; else -> "il y a $j jours" }}", fontSize = 12.sp, color = Couleurs.Erreur)
                            }
                        }
                    }
                }
            }
            if (nAutres > 0) Text("$nAutres sans alertes sur leur téléphone : vous les préviendrez ensuite un par un (WhatsApp) ou en copiant leur message.", fontSize = 13.sp, color = Couleurs.Texte2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onFermer) { Text("Fermer") }
                Button(enabled = choisis.isNotEmpty() && !envoi && m.isNotBlank(), onClick = {
                    envoi = true
                    scope.launch {
                        var n = 0; val echecs = mutableListOf<CibleRappel>()
                        for (c in choisis.filter { appareils(it) > 0 }) { try { Repo.envoyerRappel(c.membre.id, "notification", texte(c), collecteId); n++ } catch (_: Exception) { echecs += c } }
                        val r = choisis.filter { appareils(it) == 0 } + echecs
                        val bilan = "$n petit${if (n > 1) "s" else ""} mot${if (n > 1) "s" else ""} envoyé${if (n > 1) "s" else ""} au nom de l’association"
                        if (r.isEmpty()) { message(bilan); onFermer() } else { envoyes = n; restants = r }
                        envoi = false
                    }
                }) { Text(if (envoi) "Envoi…" else if (nNotif > 0) "Envoyer $nNotif notification${if (nNotif > 1) "s" else ""}" else "Continuer") }
            }
        } else {
            Text("Encore ${reste.size} à prévenir", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (envoyes > 0) Surface(color = Couleurs.BleuClair, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = Couleurs.Bleu, modifier = Modifier.size(18.dp))
                    Text("$envoyes petit${if (envoyes > 1) "s" else ""} mot${if (envoyes > 1) "s" else ""} envoyé${if (envoyes > 1) "s" else ""} au nom de l’association.", fontSize = 14.sp, color = Couleurs.SurBleuClair)
                }
            }
            Text("Ils n’ont pas activé les alertes. WhatsApp ne permet pas d’envoi groupé : chaque message est prêt, un appui par personne. Pour écrire au nom de l’association, utilisez WhatsApp Business avec son numéro.",
                fontSize = 13.sp, color = Couleurs.Texte2)
            val business = remember { whatsappBusinessInstalle() }
            reste.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                val num = numeroWa(c.membre.whatsapp)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.membre.nomComplet, fontWeight = FontWeight.SemiBold)
                        Text(if (num != null) "WhatsApp" else "Pas de numéro WhatsApp", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    if (num != null) OutlinedButton(enabled = c.membre.id !in ouverts, onClick = {
                        ouvrirWhatsApp(num, texte(c), business); ouverts.add(c.membre.id); noter(c, "whatsapp")
                    }) { Text(if (c.membre.id in ouverts) "Ouvert" else "WhatsApp") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = {
                    presse.setText(AnnotatedString(reste.joinToString("\n\n") { "— ${it.membre.nomComplet} —\n${texte(it)}" }))
                    message("Messages copiés"); reste.forEach { noter(it, "copie") }
                }) { Text("Copier tous les messages") }
                Button(onClick = onFermer) { Text("Terminé") }
            }
        }
    }
}
