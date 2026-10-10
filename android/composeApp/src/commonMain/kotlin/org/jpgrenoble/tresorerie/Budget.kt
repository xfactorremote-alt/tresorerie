package org.jpgrenoble.tresorerie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// =====================================================================
// Budget : même calcul que calculBudget() du site (docs/app.js)
// Ressources et emplois = catégories non internes ; réalisé = opérations de l'exercice par catégorie ;
// activités = projets ayant une ligne de budget ou une opération dans l'année.
// =====================================================================
data class PosteBudget(val cat: Categorie, val ligne: Budget?, val prevu: Double, val realise: Double, val n1: Double)

data class ActiviteBudget(val p: Projet, val lignes: List<Budget>, val resPrevu: Double, val empPrevu: Double, val resReel: Double, val empReel: Double)

data class CalculBudget(val lignes: List<Budget>, val ressources: List<PosteBudget>, val emplois: List<PosteBudget>, val activites: List<ActiviteBudget>) {
    val resPrevu get() = ressources.sumOf { it.prevu }
    val empPrevu get() = emplois.sumOf { it.prevu }
    val resReel get() = ressources.sumOf { it.realise }
    val empReel get() = emplois.sumOf { it.realise }
    val resN1 get() = ressources.sumOf { it.n1 }
    val empN1 get() = emplois.sumOf { it.n1 }
}

fun calculBudget(an: Int, categories: List<Categorie>, projets: List<Projet>, lignes: List<Budget>, ecritures: List<Ecriture>): CalculBudget {
    val txs = ecritures.filter { it.date.startsWith("$an-") }
    val txsN1 = ecritures.filter { it.date.startsWith("${an - 1}-") }
    fun somme(l: List<Ecriture>, cat: String) = l.filter { it.categorieId == cat }.sumOf { it.montant }
    fun postes(sens: String) = categories.filter { it.sens == sens && !it.interne }.map { c ->
        val b = lignes.firstOrNull { it.categorieId == c.id && it.projetId == null }
        PosteBudget(c, b, b?.prevu ?: 0.0, somme(txs, c.id), somme(txsN1, c.id))
    }
    val sensDe = categories.associate { it.id to it.sens }
    val ids = (lignes.mapNotNull { it.projetId } + txs.mapNotNull { it.projetId }).distinct()
    val activites = ids.map { pid ->
        val p = projets.firstOrNull { it.id == pid } ?: Projet(pid, "Activité")
        val bl = lignes.filter { it.projetId == pid }
        val t = txs.filter { it.projetId == pid && it.estFlux }
        ActiviteBudget(p, bl,
            bl.filter { sensDe[it.categorieId] == "recette" }.sumOf { it.prevu },
            bl.filter { sensDe[it.categorieId] == "depense" }.sumOf { it.prevu },
            t.filter { it.sens == "recette" }.sumOf { it.montant },
            t.filter { it.sens == "depense" }.sumOf { it.montant })
    }.sortedBy { it.p.debut ?: "9" }
    return CalculBudget(lignes, postes("recette"), postes("depense"), activites)
}

suspend fun chargerBudget(an: Int, categories: List<Categorie>): CalculBudget =
    calculBudget(an, categories, Repo.projets(), Repo.lignesBudget(an), Repo.toutesEcritures())

private fun taux(r: Double, p: Double): Int? = if (p > 0) kotlin.math.round(100 * r / p).toInt() else null
private fun nombreBudget(s: String) = s.replace(',', '.').replace(" ", "").replace(NBSP.toString(), "").toDoubleOrNull()

@Composable
fun EcranBudget(d: Donnees, message: (String) -> Unit) {
    val anCourant = aujourdhui().year
    var an by remember { mutableStateOf(anCourant) }
    var b by remember { mutableStateOf<CalculBudget?>(null) }
    var version by remember { mutableStateOf(0) }
    var feuille by remember { mutableStateOf<String?>(null) }      // null : fermée ; "" : activité à choisir ; id : activité imposée
    var exporter by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    val gere = d.peut("gerer_budget")
    LaunchedEffect(an, version, Synchro.version) {
        try { b = chargerBudget(an, d.categoriesToutes) } catch (e: Exception) { message(traduireErreur(e)) }
    }
    suspend fun seuil() = (Repo.reglages()["seuil_alerte_budget_pct"] ?: 90.0).toInt()
    fun enregistrerPrevu(x: PosteBudget, v: Double) = scope.launch {
        try {
            when {
                x.ligne != null && v == 0.0 -> Repo.supprimer("budgets", x.ligne.id, "Prévu remis à zéro")
                x.ligne != null -> Repo.majBudget(x.ligne.id, v)
                v > 0 -> Repo.ajouterBudget(NouveauBudget(an, x.cat.id, null, v, seuil()))
                else -> return@launch
            }
            message("Budget enregistré"); version++
        } catch (e: Exception) { message(traduireErreur(e)) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Budget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PuceMenu(an.toString(), true, listOf(anCourant + 1, anCourant, anCourant - 1, anCourant - 2).map { it.toString() }) { an = anCourant + 1 - it }
                Button(onClick = { exporter = true }, colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Bleu)) { Text("Exporter") }
            }
        }
        val x = b
        if (x == null) { item { LinearProgressIndicator(Modifier.fillMaxWidth()) }; return@LazyColumn }
        val equilibre = x.resPrevu - x.empPrevu
        val vide = x.resPrevu == 0.0 && x.empPrevu == 0.0
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chiffre("Ressources prévues", x.resPrevu, "réalisé ${euros(x.resReel)}", Couleurs.Bleu, Modifier.weight(1f))
                Chiffre("Emplois prévus", x.empPrevu, "réalisé ${euros(x.empReel)}", Couleurs.Orange, Modifier.weight(1f))
            }
        }
        item {
            Chiffre(if (equilibre >= 0) "Excédent prévu" else "Déficit prévu", kotlin.math.abs(equilibre), "résultat réalisé ${euros(x.resReel - x.empReel)}",
                if (equilibre < 0) Couleurs.Erreur else MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth())
        }
        if (!gere) item {
            Bandeau("Consultation$NBSP: le budget est construit par le trésorier. " +
                if (vide) "Aucun montant prévu pour $an pour l’instant." else "Vous voyez le prévu et le réalisé de chaque poste.")
        }
        if (vide && gere) item {
            val n1 = x.resN1 > 0 || x.empN1 > 0
            Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Budget $an à construire.", fontWeight = FontWeight.Bold)
                    Text("Saisissez le montant prévu de chaque ressource et de chaque emploi" + (if (n1) ", ou partez du réalisé ${an - 1}." else "."), fontSize = 14.sp)
                    if (n1) Button(onClick = {
                        scope.launch {
                            try {
                                val a = (x.ressources + x.emplois).filter { it.n1 > 0 && it.ligne == null }
                                val s = seuil()
                                a.forEach { p -> Repo.ajouterBudget(NouveauBudget(an, p.cat.id, null, kotlin.math.round(p.n1), s)) }
                                message("${a.size} ligne${if (a.size > 1) "s" else ""} reprise${if (a.size > 1) "s" else ""} : ajustez les montants"); version++
                            } catch (e: Exception) { message(traduireErreur(e)) }
                        }
                    }) { Text("Reprendre le réalisé ${an - 1}") }
                }
            }
        } else if (equilibre < 0) item {
            Surface(color = Couleurs.ErreurClair, contentColor = Couleurs.Erreur, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Les emplois prévus dépassent les ressources de ${euros(-equilibre)}. À couvrir par la réserve ou par des ressources supplémentaires.", Modifier.padding(12.dp), fontSize = 14.sp)
            }
        }
        item { TableauPostes("Ressources", "(recettes)", x.ressources, "recette", an, gere, ::enregistrerPrevu) }
        item { TableauPostes("Emplois", "(dépenses)", x.emplois, "depense", an, gere, ::enregistrerPrevu) }
        item {
            CarteBlanche {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activités", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (gere) FilledTonalButton(onClick = { feuille = "" }) { Text("Prévoir pour une activité") }
                }
                if (x.activites.isEmpty()) Text("Aucune activité budgétée. Une activité suivie apparaît ici dès qu’une ligne est prévue ou qu’une opération y est rattachée.", color = Couleurs.Texte2, fontSize = 14.sp)
            }
        }
        x.activites.forEach { a -> item(key = "act-" + a.p.id) { CarteActiviteBudget(a, d, gere, onPrevoir = { feuille = a.p.id }, onRetirer = { r ->
            // Comme sur le site : retirée aussitôt, avec « Annuler » (la ligne passe par la corbeille)
            scope.launch {
                try {
                    val idCorbeille = Repo.supprimer("budgets", r.id, "Retirée du budget"); version++
                    val hote = Annulation.hote
                    if (hote != null && hote.showSnackbar("Ligne retirée", actionLabel = "Annuler", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        Repo.restaurer(idCorbeille); version++
                    }
                } catch (e: Exception) { message(traduireErreur(e)) }
            }
        }) } }
        if (gere) item { Text("Le montant prévu s’enregistre dès que vous validez la case. Les catégories se créent dans Paramètres, Montants et comptes.", color = Couleurs.Texte2, fontSize = 13.sp) }
    }

    if (exporter) AlertDialog(
        onDismissRequest = { exporter = false },
        title = { Text("Exporter le budget $an") },
        text = { Text("PDF : budget prévu et réalisé mis en page. Excel : tableau modifiable (CSV).") },
        confirmButton = { Button(onClick = { exporter = false; scope.launch { try { val (t, h) = documentHtml(d, "budget", an); imprimer(t, h) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("PDF") } },
        dismissButton = { OutlinedButton(onClick = { exporter = false; scope.launch { try { enregistrer("budget-$an.csv", "text/csv", exportCsv(d, "budget", an).encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Excel") } },
    )
    feuille?.let { pid -> FeuilleLigneBudget(d, an, pid.ifEmpty { null }, message) { ok -> feuille = null; if (ok) version++ } }
}

@Composable
private fun Bandeau(texte: String) =
    Surface(color = Couleurs.BleuClair, contentColor = Couleurs.SurBleuClair, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(texte, Modifier.padding(12.dp), fontSize = 14.sp)
    }

@Composable
private fun TableauPostes(titre: String, sous: String, postes: List<PosteBudget>, sens: String, an: Int, gere: Boolean, onPrevu: (PosteBudget, Double) -> Unit) {
    CarteBlanche {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(titre, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(" $sous", color = Couleurs.Texte2)
        }
        if (postes.isEmpty()) Text("Aucune catégorie. Créez-les dans Paramètres, Montants et comptes.", color = Couleurs.Texte2, fontSize = 14.sp)
        postes.forEach { x -> LignePoste(x, sens, an, gere, onPrevu); HorizontalDivider(color = Color(0xFFE6E1DE)) }
        val tp = postes.sumOf { it.prevu }; val tr = postes.sumOf { it.realise }; val tn = postes.sumOf { it.n1 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${euros(tr)} sur ${euros(tp)}" + (taux(tr, tp)?.let { " · $it$NBSP%" } ?: ""), fontWeight = FontWeight.Bold)
                Text("Réalisé ${an - 1} : ${euros(tn)}", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
    }
}

@Composable
private fun LignePoste(x: PosteBudget, sens: String, an: Int, gere: Boolean, onPrevu: (PosteBudget, Double) -> Unit) {
    val t = taux(x.realise, x.prevu)
    val depasse = sens == "depense" && x.prevu > 0 && x.realise > x.prevu
    val hors = x.prevu == 0.0 && x.realise > 0
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(x.cat.nom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (depasse) Puce("Dépassé", Couleurs.ErreurClair, Couleurs.Erreur)
            else if (hors) Puce("Non prévu", Color(0xFFEFEDEC), Couleurs.Texte2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (gere) ChampPrevu(x, onPrevu, Modifier.width(130.dp))
            else Column { Text("Prévu", fontSize = 12.sp, color = Couleurs.Texte2); Text(euros(x.prevu)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Réalisé ${euros(x.realise)}", fontWeight = FontWeight.SemiBold)
                Text("${an - 1} : " + (if (x.n1 != 0.0) euros(x.n1) else "–"), fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
        if (t != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val couleur = if (sens == "recette") Couleurs.Bleu else if (depasse) Couleurs.Erreur else Couleurs.Orange
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE6E1DE))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((t.coerceIn(0, 100)) / 100f).background(couleur))
            }
            Text("$t$NBSP%", fontSize = 12.sp, color = Couleurs.Texte2)
        }
    }
}

// Saisie en ligne du prévu : enregistrée quand la case perd le focus ou à la validation du clavier (comme « change » sur le site)
@Composable
private fun ChampPrevu(x: PosteBudget, onPrevu: (PosteBudget, Double) -> Unit, modifier: Modifier) {
    val initial = if (x.prevu > 0) montantSaisie(x.prevu) else ""
    var texte by remember(x.cat.id, x.prevu) { mutableStateOf(initial) }
    var focus by remember { mutableStateOf(false) }
    fun valider() {
        if (texte == initial) return
        val v = if (texte.isBlank()) 0.0 else nombreBudget(texte) ?: return
        if (v < 0) return
        if (v != x.prevu) onPrevu(x, v)
    }
    OutlinedTextField(texte, { texte = it }, label = { Text("Prévu") }, suffix = { Text("€") }, singleLine = true, placeholder = { Text("0") },
        isError = texte.isNotBlank() && (nombreBudget(texte) ?: -1.0) < 0,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { valider() }),
        modifier = modifier.onFocusChanged { f -> if (focus && !f.isFocused) valider(); focus = f.isFocused })
}

@Composable
private fun CarteActiviteBudget(a: ActiviteBudget, d: Donnees, gere: Boolean, onPrevoir: () -> Unit, onRetirer: (Budget) -> Unit) {
    val res = a.resReel - a.empReel
    CarteBlanche {
        Row(verticalAlignment = Alignment.Top) {
            Text(a.p.nom, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(a.p.debut?.let { dateFr(it) } ?: "Date à fixer", fontSize = 13.sp, color = Couleurs.Texte2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Ressources", a.resReel to a.resPrevu, Couleurs.Bleu), Triple("Emplois", a.empReel to a.empPrevu, Couleurs.Orange)).forEach { (t, v, c) ->
                Column(Modifier.weight(1f)) {
                    Text(t, fontSize = 12.sp, color = Couleurs.Texte2)
                    Text(euros(v.first), fontWeight = FontWeight.Bold, color = c)
                    Text("prévu ${euros(v.second)}", fontSize = 12.sp, color = Couleurs.Texte2)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Résultat", fontSize = 12.sp, color = Couleurs.Texte2)
                Text((if (res >= 0) "+" else "−") + NBSP + euros(kotlin.math.abs(res)), fontWeight = FontWeight.Bold, color = if (res < 0) Couleurs.Erreur else MaterialTheme.colorScheme.onSurface)
                Text("prévu ${euros(a.resPrevu - a.empPrevu)}", fontSize = 12.sp, color = Couleurs.Texte2)
            }
        }
        if (a.empPrevu > 0 && a.empReel > a.empPrevu) Text("Emplois de l’activité au-delà du prévu", color = Couleurs.Erreur, fontSize = 13.sp)
        a.lignes.forEach { l ->
            val c = d.categoriesToutes.firstOrNull { it.id == l.categorieId }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c?.nom ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(if (c?.sens == "recette") "Ressource prévue" else "Emploi prévu", fontSize = 12.sp, color = Couleurs.Texte2)
                }
                Text(euros(l.prevu))
                if (gere) TextButton(onClick = { onRetirer(l) }) { Text("Retirer") }
            }
        }
        if (gere) TextButton(onClick = onPrevoir) { Text("Prévoir une ligne") }
    }
}

// Ligne de budget d'une activité (ressource ou emploi) ; ajoutée au montant existant si la ligne existe déjà
@Composable
private fun FeuilleLigneBudget(d: Donnees, an: Int, projetImpose: String?, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    LaunchedEffect(Unit) { try { projets = Repo.projets() } catch (e: Exception) { message(traduireErreur(e)) } }
    val activites = projets.filter { it.type == "activite" || it.id == projetImpose }
    var projetId by remember { mutableStateOf(projetImpose) }
    var sens by remember { mutableStateOf("depense") }
    val cats = d.categories.filter { it.sens == sens }
    var catId by remember(sens) { mutableStateOf(cats.firstOrNull()?.id) }
    var montant by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pid = projetId ?: activites.firstOrNull()?.id
    val v = nombreBudget(montant)
    DialogueSimple("Budget d’une activité $an", "Ajouter", !enCours && pid != null && catId != null && v != null && v > 0, { onFini(false) }, {
        enCours = true
        scope.launch {
            try {
                val existe = Repo.lignesBudget(an).firstOrNull { it.categorieId == catId && it.projetId == pid }
                if (existe != null) Repo.majBudget(existe.id, existe.prevu + v!!)
                else Repo.ajouterBudget(NouveauBudget(an, catId!!, pid, v!!, (Repo.reglages()["seuil_alerte_budget_pct"] ?: 90.0).toInt()))
                message("Ligne ajoutée"); onFini(true)
            } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
        }
    }) {
        if (activites.isEmpty()) Text("Créez d’abord l’activité dans le Planning en cochant « Suivre le budget ».", color = Couleurs.Texte2, fontSize = 14.sp)
        else ChoixListe("Activité", activites.firstOrNull { it.id == pid }?.nom ?: "", activites.map { it.nom }) { projetId = activites[it].id }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("recette" to "Ressource", "depense" to "Emploi").forEachIndexed { i, (s, l) ->
                SegmentedButton(selected = sens == s, onClick = { sens = s }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
            }
        }
        ChoixListe("Poste", cats.firstOrNull { it.id == catId }?.nom ?: "", cats.map { it.nom }) { catId = cats[it].id }
        OutlinedTextField(montant, { montant = it }, label = { Text("Montant prévu") }, suffix = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}
