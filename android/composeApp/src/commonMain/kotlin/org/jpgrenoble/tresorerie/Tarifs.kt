package org.jpgrenoble.tresorerie

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// =====================================================================
// Barème de cotisation : plusieurs montants selon la personne (comme paramMontants, feuilleTarif et
// feuilleTarifMembre du site). Chacun paie le montant normal, sauf s'il a un autre montant ; un changement
// s'applique aux périodes déjà créées à partir de la période choisie (appliquer_tarif de la base).
// =====================================================================

/** Carte « Montants selon la personne » de Paramètres › Cotisations et dépenses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocTarifs(d: Donnees, message: (String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    var tarifs by remember { mutableStateOf<List<Tarif>>(emptyList()) }
    var membres by remember { mutableStateOf(d.membres) }
    var normal by remember { mutableStateOf(0.0) }
    var feuille by remember { mutableStateOf<Tarif?>(null) }
    var nouveau by remember { mutableStateOf(false) }
    LaunchedEffect(version, Synchro.version) {
        try { tarifs = Repo.tarifs(); membres = Repo.membres(); normal = Repo.reglages()["cotisation_montant"] ?: 0.0 } catch (e: Exception) { message(traduireErreur(e)) }
    }
    fun nb(id: String?) = membres.count { it.actif && it.tarifId == id }
    fun membresTexte(n: Int) = "$n membre${if (n > 1) "s" else ""}"
    CarteBlanche {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Montants selon la personne", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (d.peut("gerer_cotisations")) FilledTonalButton(onClick = { nouveau = true }) { Text("Ajouter un montant") }
        }
        Text("Chacun paie le montant normal, sauf s’il a un autre montant (par exemple «${NBSP}Musicien${NBSP}» 3 €, «${NBSP}Exonéré${NBSP}» 0 €). Les personnes se choisissent ici ou dans la fiche de cotisation du membre.",
            fontSize = 14.sp, color = Couleurs.Texte2)
        Column {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Montant normal", fontWeight = FontWeight.SemiBold)
                Text("${euros(normal)} par période · ${membresTexte(nb(null))}", fontSize = 13.sp, color = Couleurs.Texte2)
            }
            tarifs.forEach { t ->
                HorizontalDivider(color = Couleurs.BordDoux)
                Row(Modifier.fillMaxWidth().clickable(enabled = d.peut("gerer_cotisations")) { feuille = t }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.nom, fontWeight = FontWeight.SemiBold)
                        Text("${if (t.montant == 0.0) "Dispensé (0 €)" else euros(t.montant) + " par période"} · ${membresTexte(nb(t.id))}", fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2)
                }
            }
        }
    }
    if (nouveau || feuille != null) ModalBottomSheet(onDismissRequest = { nouveau = false; feuille = null }) {
        FeuilleTarif(d, feuille, tarifs, membres, message) { ok -> nouveau = false; feuille = null; if (ok) version++ }
    }
}

/** Un montant du barème : nom, montant par période, personnes concernées, période à partir de laquelle il s'applique. */
@Composable
fun FeuilleTarif(d: Donnees, t: Tarif?, tarifs: List<Tarif>, membres: List<Membre>, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val actifs = membres.filter { it.actif }
    val avant = actifs.filter { t != null && it.tarifId == t.id }.map { it.id }
    var nom by remember { mutableStateOf(t?.nom ?: "") }
    var montant by remember { mutableStateOf(t?.montant?.let { montantSaisie(it) } ?: "") }
    var recherche by remember { mutableStateOf("") }
    val choisis = remember { mutableStateListOf<String>().apply { addAll(avant) } }
    val an = aujourdhui().year
    val pas = remember { mutableStateOf(1) }
    LaunchedEffect(Unit) { try { pas.value = (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt() } catch (_: Exception) { } }
    val options = periodesDepuis(an, pas.value)
    var depuis by remember { mutableStateOf<String?>("$an-01-01") }
    var enCours by remember { mutableStateOf(false) }
    val supprimer = rememberSuppression(message) { onFini(true) }
    val scope = rememberCoroutineScope()
    val valeur = lireMontant(montant)
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t?.nom ?: "Nouveau montant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(nom, { nom = it.take(60) }, label = { Text("Nom *") }, placeholder = { Text("Musicien, Étudiant, Exonéré…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(montant, { montant = it }, label = { Text("Montant (€ par période) *") }, placeholder = { Text("0 = dispensé") }, singleLine = true,
            isError = montant.isNotBlank() && (valeur == null || valeur < 0), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Text("Personnes concernées", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher un membre") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        val r = sansAccentsCode(recherche.trim())
        actifs.filter { r.isBlank() || sansAccentsCode(it.nomComplet).contains(r) }.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { if (m.id in choisis) choisis.remove(m.id) else choisis.add(m.id) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(m.id in choisis, { if (it) choisis.add(m.id) else choisis.remove(m.id) })
                Text(m.nomComplet, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.tarifId != null && m.tarifId != t?.id) Text("(${nomTarif(m, tarifs)})", fontSize = 13.sp, color = Couleurs.Texte2)
            }
        }
        ChoixListe("Cotisations mises à jour", depuis?.let { "À partir de ${nomPeriode(it, pas.value)}" } ?: "Seulement aux prochaines",
            options.map { "À partir de ${nomPeriode(it, pas.value)}" } + "Seulement aux prochaines") { i -> depuis = options.getOrNull(i) }
        Text("Les périodes déjà créées prennent le nouveau montant à partir de la période choisie (0 € : «${NBSP}Dispensé${NBSP}») ; ce qui a été encaissé reste acquis.",
            fontSize = 13.sp, color = Couleurs.Texte2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (t != null) BoutonSupprimer({
                val n = membres.count { it.tarifId == t.id }
                if (n > 0) message("$n membre${if (n > 1) "s ont" else " a"} ce montant : décochez-les d’abord") else supprimer("tarifs", t.id, t.nom)
            })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = nom.isNotBlank() && valeur != null && valeur >= 0 && !enCours, onClick = {
                enCours = true
                scope.launch {
                    try {
                        val id = Repo.enregistrerTarif(t?.id, nom.trim(), valeur!!)
                        // Décochés : retour au montant normal ; cochés : ce montant (et réapplication si le montant a changé)
                        val retires = avant.filter { it !in choisis }
                        var n = 0
                        if (retires.isNotEmpty()) n += Repo.appliquerTarif(null, depuis, retires)
                        if (choisis.isNotEmpty()) n += Repo.appliquerTarif(id, depuis, choisis.toList())
                        message("Montant enregistré" + if (n > 0) " · $n période${if (n > 1) "s" else ""} mise${if (n > 1) "s" else ""} à jour" else "")
                        onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
    }
}

/** Choisir le montant d'un membre (fiche de cotisation). */
@Composable
fun FeuilleTarifMembre(m: Membre, tarifs: List<Tarif>, normal: Double, pas: Int, annee: Int, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    var choix by remember { mutableStateOf(m.tarifId) }
    val options = periodesDepuis(annee, pas)
    var depuis by remember { mutableStateOf<String?>("$annee-01-01") }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Montant de ${m.nomComplet}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column {
            (listOf<Tarif?>(null) + tarifs).forEach { t ->
                Row(Modifier.fillMaxWidth().selectable(choix == t?.id, role = Role.RadioButton) { choix = t?.id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(choix == t?.id, null); Spacer(Modifier.width(8.dp)); Text(libelleTarif(t, normal))
                }
            }
        }
        if (tarifs.isEmpty()) Text("Pour proposer d’autres montants (Musicien, Exonéré…) : Paramètres › Cotisations et dépenses.", fontSize = 13.sp, color = Couleurs.Texte2)
        ChoixListe("Cotisations mises à jour", depuis?.let { "À partir de ${nomPeriode(it, pas)}" } ?: "Seulement aux prochaines",
            options.map { "À partir de ${nomPeriode(it, pas)}" } + "Seulement aux prochaines") { i -> depuis = options.getOrNull(i) }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours, onClick = {
                enCours = true
                scope.launch {
                    try {
                        val n = Repo.appliquerTarif(choix, depuis, listOf(m.id))
                        message("Montant enregistré" + if (n > 0) " · $n période${if (n > 1) "s" else ""} mise${if (n > 1) "s" else ""} à jour" else "")
                        onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
    }
}

/** Feuille ouverte par-dessus une autre feuille (fiche de cotisation) : fenêtre plein écran défilante. */
@Composable
fun DialogueFeuille(onFermer: () -> Unit, contenu: @Composable () -> Unit) =
    androidx.compose.ui.window.Dialog(onDismissRequest = onFermer, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = Formes.M3XL, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp)) {
            Box(Modifier.padding(top = 20.dp)) { contenu() }
        }
    }
