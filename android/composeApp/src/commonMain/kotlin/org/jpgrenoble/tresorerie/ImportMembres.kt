package org.jpgrenoble.tresorerie

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Import des membres (comme analyserLignes / feuilleImport du site) : nouvelle structure
// prénom;nom;jour;mois;profession;whatsapp;email;accord;nationalité;pupitre;statut,
// doublons signalés et écartés (même nom), « à vérifier » (autre nom, même WhatsApp ou e-mail), erreurs refusées.

val COLONNES_IMPORT = listOf("prénom", "nom", "jour", "mois", "profession", "whatsapp", "email", "accord", "nationalité", "pupitre", "statut")
val PUPITRES = listOf("Soprane", "Alto", "Ténor", "Basse", "Musicien")
val STATUTS_MEMBRE = listOf("Choriste", "Musicien", "Sympathisant", "Membre d’honneur")

/** Pupitre écrit de façon homogène (« soprano », « SOPRANE » → « Soprane »), comme pupitreNormal du site. */
fun pupitreNormal(s: String?): String? {
    val t = sansAccents(s ?: ""); if (t.isEmpty()) return null
    return mapOf("soprano" to "Soprane", "soprane" to "Soprane", "sopranes" to "Soprane", "alto" to "Alto", "altos" to "Alto", "tenor" to "Ténor", "tenors" to "Ténor",
        "basse" to "Basse", "basses" to "Basse", "bass" to "Basse", "musicien" to "Musicien", "musicienne" to "Musicien", "musiciens" to "Musicien")[t]
        ?: s!!.trim().replaceFirstChar { it.uppercase() }
}

/** Statut : « actif » / « inactif » règlent la case Membre actif ; tout autre mot est gardé tel quel (comme statutImport du site). */
fun statutImport(s: String?): Pair<Boolean, String?> {
    val t = sansAccents(s ?: "")
    if (Regex("^(actif|active|actifs|oui)$").matches(t)) return true to null
    if (Regex("^(inactif|inactive|inactifs|ancien|ancienne|non|parti|partie)$").matches(t)) return false to null
    return true to (s?.trim()?.ifBlank { null })
}

private fun cleNom(p: String, n: String) = sansAccents(p + n).filter { it in 'a'..'z' }
/** Motifs qui écartent la ligne ; les autres la mettent « à vérifier ». */
val DOUBLONS_SURS = listOf("même nom", "même prénom et anniversaire")
/** Doublon : même prénom et nom (accents, majuscules, tirets, espaces et ordre ignorés), même WhatsApp ou même e-mail ; sinon null.
 *  Sans nom de famille (l'un ou l'autre) : même prénom et même anniversaire = doublon ; même prénom seul = à vérifier (comme cleDoublon du site). */
fun cleDoublon(prenom: String, nom: String, jour: Int, mois: Int, whatsapp: String?, email: String?,
               p2: String, n2: String, jour2: Int, mois2: Int, wa2: String?, mail2: String?): String? {
    if (cleNom(nom, "").isEmpty() || cleNom(n2, "").isEmpty()) {
        if (cleNom(prenom, "").isNotEmpty() && cleNom(prenom, "") == cleNom(p2, "")) return if (jour == jour2 && mois == mois2) "même prénom et anniversaire" else "même prénom"
    } else if (cleNom(prenom, nom) == cleNom(p2, n2) || cleNom(prenom, nom) == cleNom(n2, p2)) return "même nom"
    if (!whatsapp.isNullOrBlank() && !wa2.isNullOrBlank() && numeroWa(whatsapp) != null && numeroWa(whatsapp) == numeroWa(wa2)) return "même WhatsApp"
    if (!email.isNullOrBlank() && !mail2.isNullOrBlank() && sansAccents(email) == sansAccents(mail2)) return "même e-mail"
    return null
}

/** Une ligne du fichier : etat « nouveau », « doublon », « verifier » ou « erreur ». */
data class LigneImport(val ligne: Int, val prenom: String, val nom: String, val membre: NouveauMembre?, val erreurs: List<String>,
                       val etat: String, val motif: String? = null, val existant: Membre? = null)

/** Analyse un CSV de membres (mêmes règles que analyserLignes du site) ; corrections : valeurs saisies dans l'aperçu, par numéro de ligne. */
fun analyserCsvMembres(texte: String, existants: List<Membre>, corrections: Map<Int, Map<String, String>> = emptyMap()): List<LigneImport> {
    val lignes = lignesCsv(texte)
    if (lignes.size < 2) throw IllegalArgumentException("Fichier vide ou sans ligne d’en-têtes")
    val entetes = lignes[0].map { sansAccents(it) }
    fun col(vararg noms: String) = entetes.indexOfFirst { h -> noms.any { h == it || h.startsWith(it) } }
    val iPrenom = col("prenom"); val iNom = col("nom"); val iJour = col("jour"); val iMois = col("mois"); val iDate = col("anniversaire", "date")
    val iProf = col("profession", "metier"); val iWa = col("whatsapp", "telephone", "tel"); val iMail = col("email", "e-mail", "mail"); val iAccord = col("accord", "consent")
    val iNat = col("nationalite", "pays"); val iPup = col("pupitre", "voix"); val iStatut = col("statut")
    if (iPrenom < 0 || ((iJour < 0 || iMois < 0) && iDate < 0))
        throw IllegalArgumentException("Colonnes attendues : prénom, jour, mois (ou anniversaire au format jj/mm) ; le nom peut manquer")
    fun moisDe(s: String): Int? = s.trim().toIntOrNull() ?: MOIS.indexOfFirst { sansAccents(it) == sansAccents(s) }.takeIf { it >= 0 }?.plus(1)
    val retenues = mutableListOf<LigneImport>()
    return lignes.drop(1).mapIndexed { k, l ->
        val corr = corrections[k + 2]
        // cle : la valeur corrigée dans l'aperçu l'emporte, même si la colonne manque dans le fichier
        fun g(i: Int, cle: String? = null) = (cle?.let { corr?.get(it) } ?: if (i >= 0) l.getOrNull(i) ?: "" else "").trim()
        var jour = g(iJour, "jour").toIntOrNull(); var mois = moisDe(g(iMois, "mois"))
        val dateTexte = g(iDate).ifBlank { if (Regex("[/\\-.]").containsMatchIn(g(iJour, "jour"))) g(iJour, "jour") else "" }
        if ((jour == null || mois == null) && dateTexte.isNotBlank()) {
            val p = dateTexte.split('/', '-', '.', ' ').filter { it.isNotBlank() }
            jour = p.getOrNull(0)?.toIntOrNull(); mois = p.getOrNull(1)?.let { moisDe(it) }
        }
        val erreurs = mutableListOf<String>()
        val prenom = g(iPrenom, "prenom"); val nom = g(iNom, "nom"); val wa = g(iWa, "whatsapp").ifBlank { null }; val mail = g(iMail, "email").ifBlank { null }
        if (prenom.isBlank()) erreurs += "prénom manquant"
        if (mois == null || mois !in 1..12) erreurs += "mois invalide"
        else if (jour == null || jour !in 1..JOURS_MOIS[mois - 1]) erreurs += "jour invalide"
        if (wa != null && numeroWa(wa) == null) erreurs += "WhatsApp invalide"
        if (mail != null && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(mail)) erreurs += "e-mail invalide"
        if (erreurs.isNotEmpty()) return@mapIndexed LigneImport(k + 2, prenom, nom, null, erreurs, "erreur")
        val (actif, statut) = statutImport(g(iStatut)); val jj = jour!!; val mm = mois!!
        val n = NouveauMembre(prenom, nom, jour!!, mois!!, g(iProf).ifBlank { null }, wa,
            Regex("^(oui|o|yes|1|x|vrai)$", RegexOption.IGNORE_CASE).matches(g(iAccord)), aujourdhui().toString(), mail,
            g(iNat).ifBlank { null }, pupitreNormal(g(iPup)), statut, actif)
        fun motif(m: Membre) = cleDoublon(prenom, nom, jj, mm, wa, mail, m.prenom, m.nom, m.jour, m.mois, m.whatsapp, m.email)
        fun motif(r: LigneImport) = cleDoublon(prenom, nom, jj, mm, wa, mail, r.prenom, r.nom, r.membre?.jour ?: 0, r.membre?.mois ?: 0, r.membre?.whatsapp, r.membre?.email)
        val ex = existants.firstOrNull { motif(it) in DOUBLONS_SURS } ?: existants.firstOrNull { motif(it) != null }
        val avant = retenues.firstOrNull { motif(it) in DOUBLONS_SURS } ?: retenues.firstOrNull { motif(it) != null }
        val r = when {
            ex != null -> motif(ex)!!.let { mo -> if (mo in DOUBLONS_SURS) LigneImport(k + 2, prenom, nom, n, emptyList(), "doublon", "déjà membre : ${ex.nomComplet}", ex)
                else LigneImport(k + 2, prenom, nom, n, emptyList(), "verifier", "$mo que : ${ex.nomComplet}") }
            avant != null -> motif(avant)!!.let { mo -> if (mo in DOUBLONS_SURS) LigneImport(k + 2, prenom, nom, n, emptyList(), "doublon", "doublon de la ligne ${avant.ligne}")
                else LigneImport(k + 2, prenom, nom, n, emptyList(), "verifier", "$mo que la ligne ${avant.ligne}") }
            else -> LigneImport(k + 2, prenom, nom, n, emptyList(), "nouveau")
        }
        if (r.etat != "doublon") retenues += r
        r
    }
}

/** Ce qu'un doublon peut ajouter à la fiche existante : seulement ses cases vides (comme complementFiche du site). */
fun complementFiche(r: LigneImport): Map<String, String>? {
    val ex = r.existant ?: return null; val n = r.membre ?: return null
    val c = listOf("profession" to (n.profession to ex.profession), "whatsapp" to (n.whatsapp to ex.whatsapp), "email" to (n.email to ex.email),
        "nationalite" to (n.nationalite to ex.nationalite), "pupitre" to (n.pupitre to ex.pupitre), "statut" to (n.statut to ex.statut))
        .mapNotNull { (k, v) -> if (!v.first.isNullOrBlank() && v.second.isNullOrBlank()) k to v.first!! else null }.toMap()
    return c.ifEmpty { null }
}

/** Champ libre avec propositions (comme <input list> + <datalist> du site). */
@Composable
fun ChampPropositions(valeur: String, onValeur: (String) -> Unit, libelle: String, propositions: List<String>, aide: String? = null) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(valeur, onValeur, label = { Text(libelle) }, placeholder = aide?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { ouvert = true }) { Icon(Icons.Outlined.ArrowDropDown, "Propositions") } })
        DropdownMenu(ouvert, { ouvert = false }) {
            propositions.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { onValeur(p); ouvert = false }) }
        }
    }
}

/** Correction d'une ligne refusée, sous la liste de l'aperçu (comme formCorrection du site). */
@Composable
fun CorrectionLigneImport(r: LigneImport, onAnnuler: () -> Unit, onValider: (Map<String, String>) -> Unit) {
    var prenom by remember(r.ligne) { mutableStateOf(r.prenom) }
    var nom by remember(r.ligne) { mutableStateOf(r.nom) }
    var jour by remember(r.ligne) { mutableStateOf(r.membre?.jour) }
    var mois by remember(r.ligne) { mutableStateOf(r.membre?.mois) }
    var whatsapp by remember(r.ligne) { mutableStateOf(r.membre?.whatsapp ?: "") }
    var email by remember(r.ligne) { mutableStateOf(r.membre?.email ?: "") }
    Surface(color = Couleurs.Surface2, shape = Formes.Moyen, border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Corriger la ligne ${r.ligne}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(r.erreurs.joinToString(", "), fontSize = 13.sp, color = Couleurs.Texte2)
            OutlinedTextField(prenom, { prenom = it.take(60) }, label = { Text("Prénom *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom (facultatif)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ChoixListe("Jour", jour?.toString() ?: "–", (1..31).map { it.toString() }) { jour = it + 1 } }
                Box(Modifier.weight(1f)) { ChoixListe("Mois", mois?.let { MOIS.getOrNull(it - 1) } ?: "–", MOIS) { mois = it + 1 } }
            }
            OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it.trim() }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onAnnuler) { Text("Annuler") }
                Button(enabled = prenom.isNotBlank(), onClick = {
                    onValider(mapOf("prenom" to prenom.trim(), "nom" to nom.trim(), "jour" to (jour?.toString() ?: ""), "mois" to (mois?.toString() ?: ""),
                        "whatsapp" to whatsapp.trim(), "email" to email.trim()))
                }) { Text("Valider la ligne") }
            }
        }
    }
}
