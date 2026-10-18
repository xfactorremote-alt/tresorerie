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

// Le montant et le moyen de paiement ressortent : ligne à part, emoji, *gras* WhatsApp (retiré dans la notification)
/** Messages proposés : mêmes textes que RAPPEL_DEFAUT du site. */
val RAPPEL_DEFAUT = mapOf(
    "cotisation" to "Bonjour {prénom} 😊\nOn espère que tu vas bien ! Petit message de {association} au sujet de la cotisation : c’est grâce à elle que l’association vit et que nos activités peuvent continuer.\n\n💶 Reste pour cette année : {montant}{règlement}{lien}\n\nMerci pour ton soutien et à très vite,\nLe bureau de {association}",
    "participation" to "Bonjour {prénom} 😊\nPour « {collecte} », ta participation compte : c’est grâce à elle que l’association vit et que ce projet peut se faire.\n{montant}{règlement}{lien}\n\nMerci et à bientôt,\nLe bureau de {association}",
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

/** « Comment régler » : une ligne → sur la même ligne ; plusieurs → une puce par moyen de paiement (comme blocReglement du site). */
fun blocReglement(infos: String?): String {
    val l = (infos ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    return when (l.size) { 0 -> ""; 1 -> "\n💳 Pour régler : *${l[0]}*"; else -> "\n💳 Pour régler :\n" + l.joinToString("\n") { "• *$it*" } }
}
/** La notification n'a pas de mise en forme : on retire les *astérisques* du gras WhatsApp. */
fun sansGras(t: String) = t.replace(Regex("\\*([^*\n]+)\\*"), "$1")

const val NOTE_RAPPEL = "Signé par le bureau. Les mots entre *astérisques* sont en gras dans WhatsApp. Le texte proposé se modifie dans Paramètres › Cotisations et dépenses."
private fun ilYaJours(j: Int) = when (j) { 0 -> "aujourd’hui"; 1 -> "hier"; else -> "il y a $j jours" }

/** Texte du rappel, mots remplacés comme texteRappel du site. */
fun texteRappel(modele: String?, prenom: String, association: String?, type: String, montant: Double, collecte: String, infosPaiement: String?, urlLien: String?): String {
    val valeurs = mapOf(
        "prénom" to prenom, "association" to nomAssoMessage(association), "collecte" to collecte,
        "montant" to if (type == "participation") (if (montant > 0) "\n💶 Montant proposé : *${euros(montant)}*" else "") else "*${euros(montant)}*",
        "règlement" to blocReglement(infosPaiement),
        "lien" to (urlLien?.let { "\n🔗 Ton espace membre : $it" } ?: ""),
    )
    return Regex("\\{(prénom|prenom|association|montant|collecte|règlement|reglement|lien)\\}").replace(modele?.takeIf { it.isNotBlank() } ?: RAPPEL_DEFAUT.getValue(type)) {
        val k = it.groupValues[1]; valeurs[mapOf("prenom" to "prénom", "reglement" to "règlement")[k] ?: k] ?: ""
    }.replace(Regex("\n{3,}"), "\n\n")  // une partie vide (pas de montant, pas de « Comment régler ») ne laisse pas de trou
}

/** Encadrés communs aux deux feuilles (même présentation que .info / .alerte du site). */
@Composable
internal fun EncadreRappel(texte: String, alerte: Boolean = false) {
    Surface(color = if (alerte) Couleurs.ErreurClair else Couleurs.BleuClair, shape = Formes.Moyen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, tint = if (alerte) Couleurs.Erreur else Couleurs.Bleu, modifier = Modifier.size(18.dp))
            Text(texte, fontSize = 14.sp, color = if (alerte) Couleurs.SurErreurClair else Couleurs.SurBleuClair)
        }
    }
}
/** Sans « Comment régler », le moyen de paiement manquerait au message : on le dit avant l'envoi. */
@Composable
internal fun AlerteReglement(infos: String?) {
    if (infos.isNullOrBlank()) EncadreRappel("« Comment régler » n’est pas renseigné : le moyen de paiement n’apparaîtra pas dans le message. Ajoutez-le dans Paramètres › Cotisations et dépenses.", alerte = true)
}

/** Ce qu'il faut pour ouvrir la feuille : type « cotisation » (montant restant) ou « participation » (collecte). */
data class DemandeRappel(val membre: Membre, val type: String, val montant: Double, val collecte: String = "", val collecteId: String? = null)

/** Boutons d'envoi communs aux deux feuilles (même ordre, mêmes explications que .choix-envoi du site). */
@Composable
private fun ChoixEnvoi(notif: String, notifActif: Boolean, notifAide: String, whatsapp: String?, waActif: Boolean, waAide: String, copie: String, copieActif: Boolean,
                       onNotif: () -> Unit, onWhatsApp: () -> Unit, onCopie: () -> Unit, onFermer: () -> Unit) {
    Button(enabled = notifActif, modifier = Modifier.fillMaxWidth(), onClick = onNotif) {
        Icon(Icons.Outlined.Notifications, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(notif)
    }
    Text(notifAide, fontSize = 13.sp, color = Couleurs.Texte2)
    if (whatsapp != null) {
        FilledTonalButton(enabled = waActif, modifier = Modifier.fillMaxWidth(), onClick = onWhatsApp) { Text(whatsapp) }
        Text(waAide, fontSize = 13.sp, color = Couleurs.Texte2)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = copieActif, onClick = onCopie) { Text(copie) }
        TextButton(onClick = onFermer) { Text("Fermer") }
    }
}
private const val AIDE_WA_BUSINESS = "S’ouvre dans WhatsApp Business : utilisez-le avec le numéro de l’association pour écrire en son nom."
private const val AIDE_WA = "S’ouvre dans le WhatsApp de cet appareil. Pour écrire au nom de l’association plutôt que depuis votre numéro, utilisez WhatsApp Business avec le numéro de l’association."

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeuilleRappel(d: Donnees, r: DemandeRappel, message: (String) -> Unit, onFermer: () -> Unit) {
    val m = r.membre
    val presse = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var etat by remember { mutableStateOf(EtatRappel(m.id)) }
    var texte by remember { mutableStateOf<String?>(null) }
    var infos by remember { mutableStateOf<String?>("…") }
    var envoi by remember { mutableStateOf(false) }
    LaunchedEffect(m.id, r.collecteId) {
        try { Repo.etatRappels(r.collecteId).firstOrNull { it.membreId == m.id }?.let { etat = it } } catch (_: Exception) {}
        val modele = try { Repo.texteReglage(cleRappel(r.type)) } catch (_: Exception) { null }
        infos = try { Repo.texteReglage("infos_paiement") } catch (_: Exception) { null }
        val lien = try { Repo.liens().firstOrNull { it.membreId == m.id } } catch (_: Exception) { null }
        texte = texteRappel(modele, m.prenom, d.organisation.nom, r.type, r.montant, r.collecte, infos, lien?.let { Repo.urlLien(it, m) })
    }
    val jours = etat.dernier?.let { joursDepuis(it) }
    val num = numeroWa(m.whatsapp)
    val business = remember { whatsappBusinessInstalle() }
    // Le rappel est noté dans la base (anti-insistance) ; la feuille se ferme, comme sur le site
    fun noter(canal: String, ok: String?) = Annulation.portee.launch {
        try { Repo.envoyerRappel(m.id, canal, texte!!.trim(), r.collecteId); ok?.let(message) } catch (_: Exception) {}
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Petit rappel à ${m.prenom}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (jours != null && jours < 30) EncadreRappel("Un petit mot lui a déjà été envoyé ${ilYaJours(jours)}. Pour ne pas être insistant, mieux vaut laisser passer un peu de temps.")
        if (infos != "…") AlerteReglement(infos)
        val t = texte
        if (t == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else OutlinedTextField(t, { texte = it.take(1500) }, label = { Text("Message") }, minLines = 8, maxLines = 14, modifier = Modifier.fillMaxWidth())
        Text(NOTE_RAPPEL, fontSize = 13.sp, color = Couleurs.Texte2)
        val pret = !t.isNullOrBlank() && !envoi
        ChoixEnvoi(
            notif = "Notification de l’association", notifActif = pret && etat.appareils > 0,
            notifAide = if (etat.appareils > 0) "Reçue sur ${if (etat.appareils > 1) "ses ${etat.appareils} appareils" else "son téléphone"}, au nom de ${nomAssoMessage(d.organisation.nom)} : aucun numéro personnel."
                else "${m.prenom} n’a pas activé les alertes sur son téléphone : utilisez WhatsApp ou copiez le message.",
            whatsapp = if (num != null) (if (business) "WhatsApp Business" else "WhatsApp") else null, waActif = pret, waAide = if (business) AIDE_WA_BUSINESS else AIDE_WA,
            copie = "Copier le message", copieActif = pret,
            onNotif = {
                envoi = true
                scope.launch {
                    try { Repo.envoyerRappel(m.id, "notification", sansGras(t!!.trim()), r.collecteId); message("Petit mot envoyé à ${m.prenom} au nom de l’association"); onFermer() }
                    catch (e: Exception) { envoi = false; message(traduireErreur(e)) }
                }
            },
            onWhatsApp = { ouvrirWhatsApp(num!!, t!!.trim(), business); noter("whatsapp", null); onFermer() },
            onCopie = { presse.setText(AnnotatedString(t!!.trim())); noter("copie", "Message copié"); onFermer() },
            onFermer = onFermer,
        )
    }
}

/** Jours écoulés depuis un horodatage ISO (« 2026-10-08T09:12:00Z »). */
private fun joursDepuis(iso: String): Int? = try {
    val i = kotlinx.datetime.Instant.parse(if (iso.length == 10) iso + "T00:00:00Z" else iso.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it })
    ((kotlinx.datetime.Clock.System.now() - i).inWholeHours / 24).toInt()
} catch (_: Exception) { null }

/** Une personne d'un petit rappel groupé : son montant restant (0 = montant libre). */
data class CibleRappel(val membre: Membre, val montant: Double)

// Petit rappel groupé (comme feuilleRappelGroupe du site) : MÊME STRUCTURE que le petit rappel individuel
// (titre, avertissement, message, note, Notification de l'association / WhatsApp / Copier, Fermer), plus la liste des destinataires.
// Chacun reçoit SON message (prénom, montant, lien) ; ceux qui ont eu un petit mot depuis moins de 30 jours sont décochés d'office.
// WhatsApp n'a pas d'envoi groupé : une liste « un appui par personne ».
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeuilleRappelGroupe(d: Donnees, cibles: List<CibleRappel>, type: String, collecte: String, collecteId: String?, message: (String) -> Unit, onFermer: () -> Unit) {
    val presse = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val liste = remember(cibles) { cibles.filter { it.membre.actif } }
    var etats by remember { mutableStateOf<Map<String, EtatRappel>?>(null) }
    var modele by remember { mutableStateOf("") }
    var infos by remember { mutableStateOf<String?>(null) }
    var liens by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val coches = remember { mutableStateListOf<String>() }
    val faits = remember { mutableStateMapOf<String, String>() }
    var edition by remember { mutableStateOf(false) }
    var envoi by remember { mutableStateOf(false) }
    var waListe by remember { mutableStateOf<List<CibleRappel>?>(null) }
    val business = remember { whatsappBusinessInstalle() }
    LaunchedEffect(Unit) {
        val e = try { Repo.etatRappels(collecteId).associateBy { it.membreId } } catch (_: Exception) { emptyMap() }
        infos = try { Repo.texteReglage("infos_paiement") } catch (_: Exception) { null }
        liens = try { Repo.liens().mapNotNull { l -> liste.firstOrNull { it.membre.id == l.membreId }?.let { l.membreId to Repo.urlLien(l, it.membre) } }.toMap() } catch (_: Exception) { emptyMap() }
        modele = (try { Repo.texteReglage(cleRappel(type)) } catch (_: Exception) { null }) ?: RAPPEL_DEFAUT.getValue(type)
        coches.addAll(liste.filter { c -> e[c.membre.id]?.dernier?.let { joursDepuis(it) }?.let { it >= 30 } ?: true }.map { it.membre.id })
        etats = e
    }
    fun jours(c: CibleRappel) = etats?.get(c.membre.id)?.dernier?.let { joursDepuis(it) }
    fun texte(c: CibleRappel) = texteRappel(modele, c.membre.prenom, d.organisation.nom, type, c.montant, collecte, infos, liens[c.membre.id]).trim()
    fun appareils(c: CibleRappel) = etats?.get(c.membre.id)?.appareils ?: 0
    fun noter(c: CibleRappel, canal: String) = Annulation.portee.launch { try { Repo.envoyerRappel(c.membre.id, canal, texte(c), collecteId) } catch (_: Exception) {} }
    fun pluriel(n: Int, s: String, p: String) = if (n > 1) p else s
    fun montantTexte(c: CibleRappel) = if (type == "cotisation" || c.montant > 0) euros(c.montant) else "Montant libre"
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val wl = waListe
        if (etats == null) { Text("Petit rappel à ${liste.size} ${pluriel(liste.size, "membre", "membres")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (wl != null) {
            // WhatsApp : un appui par personne, message prêt
            Text("WhatsApp · ${wl.size} ${pluriel(wl.size, "message", "messages")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("WhatsApp ne permet pas d’envoi groupé : un appui par personne ouvre la conversation avec son message prêt ; envoyez-le puis revenez ici.", fontSize = 13.sp, color = Couleurs.Texte2)
            wl.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.membre.nomComplet, fontWeight = FontWeight.SemiBold)
                        Text(montantTexte(c), fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    val ouvert = faits[c.membre.id] != null
                    FilledTonalButton(enabled = !ouvert, onClick = {
                        ouvrirWhatsApp(numeroWa(c.membre.whatsapp)!!, texte(c), business); faits[c.membre.id] = "WhatsApp ouvert"; coches.remove(c.membre.id); noter(c, "whatsapp")
                    }) { Text(if (ouvert) "Ouvert" else "WhatsApp") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { waListe = null }) { Text("‹ Retour") }
                Button(onClick = onFermer) { Text("Terminé") }
            }
        } else {
            val choisis = liste.filter { it.membre.id in coches }
            val notif = choisis.filter { appareils(it) > 0 }; val wa = choisis.filter { numeroWa(it.membre.whatsapp) != null }
            val recents = liste.filter { (jours(it) ?: 99) < 30 }
            val ex = choisis.firstOrNull() ?: liste.firstOrNull()
            Text("Petit rappel à ${liste.size} ${pluriel(liste.size, "membre", "membres")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (recents.isNotEmpty()) EncadreRappel((if (recents.size == 1) "${recents[0].membre.prenom} a déjà reçu un petit mot ${ilYaJours(jours(recents[0])!!)} : décoché"
                else "${recents.size} ont déjà reçu un petit mot il y a moins de 30 jours : décochés") + " pour ne pas être insistant.")
            AlerteReglement(infos)
            if (edition) {
                OutlinedTextField(modele, { modele = it.take(1500) }, label = { Text("Message (modèle commun)") }, minLines = 8, maxLines = 14, modifier = Modifier.fillMaxWidth())
                Text("Chacun reçoit son propre message : {prénom}, {montant}, {règlement}, {lien}… sont remplacés pour chaque personne. $NOTE_RAPPEL", fontSize = 13.sp, color = Couleurs.Texte2)
                if (ex != null) TextButton(onClick = { edition = false }) { Text("Voir le message de ${ex.membre.prenom}") }
            } else if (ex != null) {
                // Le vrai message d'une personne, à la place du champ (comme le petit rappel individuel)
                OutlinedTextField(texte(ex), {}, readOnly = true, label = { Text("Message · exemple pour ${ex.membre.prenom}") }, minLines = 8, maxLines = 14, modifier = Modifier.fillMaxWidth())
                Text("Chacun reçoit son propre message, avec son prénom et son montant. $NOTE_RAPPEL", fontSize = 13.sp, color = Couleurs.Texte2)
                TextButton(onClick = { edition = true }) { Text("Modifier le message") }
            }
            Text("Destinataires (${choisis.size} sur ${liste.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Surface(shape = Formes.Moyen, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), modifier = Modifier.fillMaxWidth()) {
                Column {
                    liste.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = Couleurs.BordDoux)
                        val j = jours(c); val fait = faits[c.membre.id]
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(c.membre.id in coches, { if (it) coches.add(c.membre.id) else coches.remove(c.membre.id) }, enabled = fait == null)
                            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text(c.membre.nomComplet, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(listOfNotNull(if (type == "cotisation" || c.montant > 0) euros(c.montant) else null,
                                    if (fait != null) "✓ $fait" else if (appareils(c) > 0) "Alertes activées" else if (numeroWa(c.membre.whatsapp) != null) "WhatsApp" else "Pas d’alertes ni de WhatsApp").joinToString(" · "),
                                    fontSize = 12.sp, color = if (fait != null) Couleurs.Bleu else Couleurs.Texte2)
                                if (j != null && j < 30) Text("petit mot ${ilYaJours(j)}", fontSize = 12.sp, color = Couleurs.Orange)
                            }
                        }
                    }
                }
            }
            fun finir(msg: String) { message(msg); if (coches.isEmpty()) onFermer() }
            ChoixEnvoi(
                notif = if (envoi) "Envoi…" else "Notification de l’association" + if (notif.isNotEmpty()) " (${notif.size})" else "",
                notifActif = notif.isNotEmpty() && !envoi && modele.isNotBlank(),
                notifAide = if (notif.isNotEmpty()) "Reçue par ${if (notif.size > 1) "les ${notif.size} membres cochés qui ont" else "le membre coché qui a"} activé les alertes, au nom de ${nomAssoMessage(d.organisation.nom)} : aucun numéro personnel."
                    else "Aucun des membres cochés n’a activé les alertes sur son téléphone : utilisez WhatsApp ou copiez les messages.",
                whatsapp = if (wa.isNotEmpty()) (if (business) "WhatsApp Business" else "WhatsApp") + " (${wa.size})" else null, waActif = !envoi,
                waAide = "Un message prêt par personne, un appui chacun. " + if (business) AIDE_WA_BUSINESS else "Pour écrire au nom de l’association plutôt que depuis votre numéro, utilisez WhatsApp Business avec le numéro de l’association.",
                copie = "Copier les messages" + if (choisis.isNotEmpty()) " (${choisis.size})" else "", copieActif = choisis.isNotEmpty() && !envoi,
                onNotif = {
                    envoi = true
                    scope.launch {
                        var n = 0
                        for (c in notif) { try { Repo.envoyerRappel(c.membre.id, "notification", sansGras(texte(c)), collecteId); faits[c.membre.id] = "Notification envoyée"; coches.remove(c.membre.id); n++ } catch (_: Exception) {} }
                        envoi = false
                        finir("$n ${pluriel(n, "petit mot envoyé", "petits mots envoyés")} au nom de l’association")
                    }
                },
                onWhatsApp = { waListe = wa },
                onCopie = {
                    presse.setText(AnnotatedString(choisis.joinToString("\n\n") { "— ${it.membre.nomComplet} —\n${texte(it)}" }))
                    choisis.forEach { faits[it.membre.id] = "Message copié"; coches.remove(it.membre.id); noter(it, "copie") }
                    finir("${choisis.size} ${pluriel(choisis.size, "message copié", "messages copiés")}")
                },
                onFermer = onFermer,
            )
        }
    }
}
