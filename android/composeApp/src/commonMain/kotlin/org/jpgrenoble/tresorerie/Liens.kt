package org.jpgrenoble.tresorerie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Lien personnel d'un membre (même conception que le site) : il ouvre sa page — cotisation,
// participations, rendez-vous — sans compte ni mot de passe ; envoi par WhatsApp ou e-mail,
// copie, coupure, renouvellement.
private fun messageLien(d: Donnees, m: Membre, url: String) =
    "Bonjour ${m.prenom}, voici votre lien personnel pour suivre votre cotisation, vos participations et les rendez-vous de ${d.organisation.nom} : $url " +
        "Il ouvre directement votre page, sans compte ni mot de passe. Gardez-le pour vous."

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeuilleLien(d: Donnees, m: Membre, lien: LienMembre?, message: (String) -> Unit, onChange: () -> Unit, onFermer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val presse = LocalClipboardManager.current
    val url = lien?.let { Repo.urlLien(it.jeton) }
    fun faire(msg: String, bloc: suspend () -> Unit) = scope.launch { try { bloc(); message(msg); onChange() } catch (e: Exception) { message(traduireErreur(e)) } }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lien personnel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(m.prenom, m.nom)
            Column {
                Text(m.nomComplet, fontWeight = FontWeight.Bold)
                Text(when { lien == null -> "Pas encore de lien"; lien.nbConsultations > 0 -> "Ouvert ${lien.nbConsultations} fois" +
                    (lien.derniereConsultation?.let { ", dernière fois le ${dateFr(it.take(10))}" } ?: ""); else -> "Lien créé, pas encore ouvert" }, fontSize = 13.sp, color = Couleurs.Texte2)
            }
        }
        Text("Le lien ouvre la page de ${m.prenom} : cotisation, participations, rendez-vous à venir. Aucun compte ni mot de passe. Il ne montre rien d’autre et peut être coupé à tout moment.",
            fontSize = 14.sp, color = Couleurs.Texte2)
        if (url != null) {
            Surface(color = androidx.compose.ui.graphics.Color(0xFFEFEDEC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text(url, Modifier.padding(12.dp), fontSize = 13.sp) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                numeroWa(m.whatsapp)?.let { wa -> Button(onClick = { uri.openUri("https://wa.me/$wa?text=" + encoderUrl(messageLien(d, m, url))) }) { Text("Envoyer par WhatsApp") } }
                m.email?.takeIf { it.isNotBlank() }?.let { mail -> FilledTonalButton(onClick = {
                    uri.openUri("mailto:$mail?subject=" + encoderUrl("Votre page personnelle") + "&body=" + encoderUrl(messageLien(d, m, url))) }) { Text("Envoyer par e-mail") } }
                FilledTonalButton(onClick = { presse.setText(AnnotatedString(url)); message("Lien copié") }) { Text("Copier") }
                TextButton(onClick = { uri.openUri(url) }) { Text("Voir comme ${m.prenom}") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { faire("Lien coupé") { Repo.couperLien(m.id) } }) { Text("Couper le lien") }
                TextButton(onClick = { faire("Nouveau lien créé, l’ancien ne fonctionne plus") { Repo.lienMembre(m.id, true) } }) { Text("Renouveler") }
                Button(onClick = onFermer) { Text("Fermer") }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onFermer) { Text("Fermer") }
            Button(onClick = { faire("Lien créé") { Repo.lienMembre(m.id, false) } }) { Text("Créer le lien") }
        }
    }
}

// Envoi groupé : un lien par membre, un geste par envoi
@Composable
fun FeuilleLiens(d: Donnees, membres: List<Membre>, liens: List<LienMembre>, message: (String) -> Unit, onChange: () -> Unit, onFermer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var enCours by remember { mutableStateOf(false) }
    val actifs = membres.filter { it.actif }.sortedBy { it.nomComplet }
    val sans = actifs.filter { m -> liens.none { it.membreId == m.id } }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Liens personnels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Chaque membre reçoit un lien qui ouvre sa page : cotisation, participations, rendez-vous. Pas de compte, pas d’installation.", fontSize = 14.sp, color = Couleurs.Texte2)
        if (sans.isNotEmpty()) Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sans.size} membre${if (sans.size > 1) "s" else ""} sans lien", Modifier.weight(1f))
                Button(enabled = !enCours, onClick = {
                    enCours = true
                    scope.launch { try { sans.forEach { Repo.lienMembre(it.id, false) }; message("Liens créés"); onChange() } catch (e: Exception) { message(traduireErreur(e)) }; enCours = false }
                }) { Text("Créer les liens") }
            }
        }
        actifs.forEach { m ->
            val l = liens.firstOrNull { it.membreId == m.id }; val wa = numeroWa(m.whatsapp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Avatar(m.prenom, m.nom, 40)
                Column(Modifier.weight(1f)) {
                    Text(m.nomComplet, fontWeight = FontWeight.SemiBold)
                    Text((if (l == null) "Pas de lien" else if (l.nbConsultations > 0) "Ouvert ${l.nbConsultations} fois" else "Pas encore ouvert") + if (wa == null) " · sans numéro WhatsApp" else "",
                        fontSize = 13.sp, color = Couleurs.Texte2)
                }
                if (l != null && wa != null) FilledTonalButton(onClick = { uri.openUri("https://wa.me/$wa?text=" + encoderUrl(messageLien(d, m, Repo.urlLien(l.jeton)))) }) { Text("WhatsApp") }
                else if (l != null && !m.email.isNullOrBlank()) TextButton(onClick = {
                    uri.openUri("mailto:${m.email}?subject=" + encoderUrl("Votre page personnelle") + "&body=" + encoderUrl(messageLien(d, m, Repo.urlLien(l.jeton)))) }) { Text("E-mail") }
            }
        }
        Button(onClick = onFermer, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
    }
}
