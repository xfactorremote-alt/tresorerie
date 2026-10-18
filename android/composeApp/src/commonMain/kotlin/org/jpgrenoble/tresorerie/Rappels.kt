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
    "cotisation" to "Bonjour {prénom} 😊\nOn espère que tu vas bien ! Petit message de {association} au sujet de la cotisation : elle nous aide à faire vivre l’association, mais elle reste libre et sans pression.\nSi tu le souhaites et quand cela t’arrange, il reste {montant} pour cette année.{règlement}\nSi ce n’est pas le bon moment, aucun souci : dis-le-nous simplement, tu restes le bienvenu parmi nous.{lien}\nÀ très vite,\nLe bureau de {association}",
    "participation" to "Bonjour {prénom} 😊\nPour « {collecte} », chacun participe s’il le peut{montant} : chaque geste compte, même petit.{règlement}\nPas de souci si ce n’est pas possible en ce moment.{lien}\nMerci et à bientôt,\nLe bureau de {association}",
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
