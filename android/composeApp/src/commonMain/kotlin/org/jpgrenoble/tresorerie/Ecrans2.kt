package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val STATUTS = mapOf(
    "soumise" to "À valider", "validee" to "À payer", "refusee" to "Refusée", "payee" to "Justificatif attendu",
    "justifiee" to "Clôturée", "annulee" to "Annulée", "brouillon" to "Brouillon",
)

@Composable
internal fun Titre(texte: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texte, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
internal fun Puce(texte: String, fond: Color, couleur: Color) =
    Surface(color = fond, contentColor = couleur, shape = RoundedCornerShape(13.dp)) {
        Text(texte, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

@Composable
internal fun CarteBlanche(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else Modifier)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }

private fun couleursStatut(s: String): Pair<Color, Color> = when (s) {
    "validee", "justifiee" -> Couleurs.BleuClair to Couleurs.SurBleuClair
    "refusee" -> Couleurs.ErreurClair to Color(0xFF410002)
    "annulee", "brouillon" -> Color(0xFFEFEDEC) to Couleurs.Texte2
    else -> Couleurs.JauneClair to Couleurs.SurJaune
}

// Barre d'avancement : demande, validation, paiement, justificatif
@Composable
private fun Etapes(d: Demande) {
    val ordre = listOf("soumise", "validee", "payee", "justifiee")
    val noms = listOf("Demande", "Validation", "Paiement", "Justificatif")
    val idx = when (d.statut) { "refusee" -> 1; "annulee" -> 0; else -> ordre.indexOf(d.statut) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        noms.forEachIndexed { i, n ->
            val couleur = when {
                d.statut == "refusee" && i == 1 -> Couleurs.Erreur
                i <= idx && !(d.statut == "annulee" && i > 0) -> Couleurs.Bleu
                i == idx + 1 && d.statut !in listOf("refusee", "annulee") -> Couleurs.Jaune
                else -> Color(0xFFEFEDEC)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(couleur, RoundedCornerShape(3.dp)))
                Text(n, fontSize = 11.sp, color = Couleurs.Texte2, maxLines = 1)
            }
        }
    }
}

// ---------- Dépenses ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranDepenses(d: Donnees, message: (String) -> Unit) {
    var liste by remember { mutableStateOf<List<Demande>?>(null) }
    var noms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }
    var filtre by remember { mutableStateOf(when { d.peut("valider_depenses") -> "a_valider"; d.peut("payer_depenses") -> "a_payer"; else -> "toutes" }) }
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    var delai by remember { mutableStateOf(7) }
    var detail by remember { mutableStateOf<Demande?>(null) }
    var nouvelle by remember { mutableStateOf(false) }
    var aValider by remember { mutableStateOf<Demande?>(null) }
    var aRefuser by remember { mutableStateOf<Demande?>(null) }
    var aPayer by remember { mutableStateOf<Demande?>(null) }
    var aJustifier by remember { mutableStateOf<Demande?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version) {
        try {
            liste = Repo.demandes()
            noms = try { Repo.profils().associate { it.id to it.nom } } catch (_: Exception) { mapOf(d.profil.id to d.profil.nom) }
            pieces = try { Repo.pieces() } catch (_: Exception) { emptyList() }
            delai = try { (Repo.reglages()["delai_justificatif_jours"] ?: 7.0).toInt() } catch (_: Exception) { 7 }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val filtres = listOf(
        "a_valider" to "À valider", "a_payer" to "À payer", "a_justifier" to "Justificatif attendu", "terminees" to "Terminées", "toutes" to "Toutes",
    )
    fun garde(x: Demande, f: String = filtre) = when (f) {
        "a_valider" -> x.statut == "soumise"; "a_payer" -> x.statut == "validee"; "a_justifier" -> x.statut == "payee"
        "terminees" -> x.statut in listOf("justifiee", "refusee", "annulee"); else -> true
    }
    val choixPiece = rememberChoixFichier { f, err ->
        val dem = aJustifier
        if (err != null) message(err)
        if (f != null && dem != null) scope.launch {
            try { Repo.justifierDemande(dem.id, f); message("Justificatif joint (${f.ko}$NBSP" + "Ko)"); version++ }
            catch (e: Exception) { message(traduireErreur(e)) }
        }
        aJustifier = null
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Titre("Dépenses") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtres.forEach { (k, l) ->
                        val n = liste?.count { x -> garde(x, k) } ?: 0
                        FilterChip(selected = filtre == k, onClick = { filtre = k },
                            label = { Text(if (k in listOf("toutes", "terminees") || n == 0) l else "$l ($n)") })
                    }
                }
            }
            val l = liste
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val vus = l.filter { garde(it) }
                if (vus.isEmpty()) item { Text("Aucune demande.", color = Couleurs.Texte2) }
                items(vus, key = { it.id }) { x ->
                    val (fond, couleur) = couleursStatut(x.statut)
                    val retard = x.statut == "payee" && x.payeeLe != null &&
                        aujourdhui().toEpochDays() - LocalDate.parse(x.payeeLe.take(10)).toEpochDays() > delai
                    CarteBlanche(onClick = { detail = x }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(x.objet, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(listOfNotNull(
                                    if (x.demandeur == d.profil.id) "Vous" else noms[x.demandeur] ?: "Membre du bureau",
                                    dateFr(x.creeLe), d.categories.firstOrNull { it.id == x.categorieId }?.nom,
                                ).joinToString(" · "), fontSize = 13.sp, color = Couleurs.Texte2)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(euros(x.montant), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Puce(STATUTS[x.statut] ?: x.statut, fond, couleur)
                            }
                        }
                        Etapes(x)
                        if (retard) Surface(color = Couleurs.ErreurClair, shape = RoundedCornerShape(14.dp)) {
                            Text("Justificatif en retard, délai de $delai$NBSP" + "jours dépassé", Modifier.padding(12.dp), fontSize = 14.sp)
                        }
                        x.motifRefus?.let { Text("Motif du refus$NBSP: $it", fontSize = 14.sp, color = Couleurs.Texte2) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            if (d.peut("valider_depenses") && x.statut == "soumise") {
                                TextButton(onClick = { aRefuser = x }) { Text("Refuser") }
                                Button(onClick = { aValider = x }) { Text("Valider et signer") }
                            }
                            if (d.peut("payer_depenses") && x.statut == "validee" && x.valideePar != d.profil.id) Button(onClick = { aPayer = x }) { Text("Payer") }
                            if (x.statut == "payee" && (d.peut("saisir_ecritures", "payer_depenses") || x.demandeur == d.profil.id))
                                FilledTonalButton(onClick = { aJustifier = x; choixPiece() }) { Text("Joindre le justificatif") }
                            if (x.statut == "soumise" && x.demandeur == d.profil.id)
                                TextButton(onClick = {
                                    scope.launch {
                                        try { Repo.annulerDemande(x.id); message("Demande annulée"); version++ } catch (e: Exception) { message(traduireErreur(e)) }
                                    }
                                }) { Text("Annuler la demande") }
                        }
                    }
                }
            }
        }
        if (d.peut("demander_depenses")) {
            ExtendedFloatingActionButton(
                onClick = { nouvelle = true }, containerColor = Couleurs.Jaune, contentColor = Couleurs.SurJaune,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Nouvelle demande") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }

    if (nouvelle) ModalBottomSheet(onDismissRequest = { nouvelle = false }) {
        FormulaireDemande(d, onFini = { ok -> nouvelle = false; if (ok) { message("Demande envoyée"); filtre = "toutes"; version++ } }, message)
    }
    detail?.let { x ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(x.objet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(euros(x.montant), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Couleurs.Orange)
                Etapes(x)
                Spacer(Modifier.height(4.dp))
                LigneInfo("Statut", STATUTS[x.statut])
                LigneInfo("Demandeur", if (x.demandeur == d.profil.id) "Vous" else noms[x.demandeur])
                LigneInfo("Demandée le", dateFr(x.creeLe))
                LigneInfo("Catégorie", d.categories.firstOrNull { it.id == x.categorieId }?.nom)
                LigneInfo("Validée par", x.valideePar?.let { if (it == d.profil.id) "Vous" else noms[it] })
                LigneInfo("Validée le", x.valideeLe?.let(::dateFr))
                LigneInfo("Payée le", x.payeeLe?.let(::dateFr))
                LigneInfo("Motif du refus", x.motifRefus)
                Spacer(Modifier.height(8.dp))
                PiecesVue(pieces.filter { it.demandeId == x.id }, x.signature)
                if (x.statut == "payee" && (d.peut("saisir_ecritures", "payer_depenses") || x.demandeur == d.profil.id))
                    FilledTonalButton(onClick = { aJustifier = x; detail = null; choixPiece() }, modifier = Modifier.align(Alignment.End)) { Text("Joindre le justificatif") }
            }
        }
    }
    aValider?.let { x ->
        ModalBottomSheet(onDismissRequest = { aValider = null }) {
            Signature(x, d, onFini = { ok -> aValider = null; if (ok) { message("Dépense validée"); version++ } }, message)
        }
    }
    aRefuser?.let { x ->
        var motif by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { aRefuser = null },
            title = { Text("Refuser cette dépense$NBSP?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${x.objet}, ${euros(x.montant)}")
                    OutlinedTextField(motif, { motif = it.take(300) }, label = { Text("Motif") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(enabled = motif.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Couleurs.Erreur), onClick = {
                    scope.launch {
                        try { Repo.refuserDemande(x.id, motif.trim()); message("Demande refusée"); version++ } catch (e: Exception) { message(traduireErreur(e)) }
                        aRefuser = null
                    }
                }) { Text("Refuser") }
            },
            dismissButton = { TextButton(onClick = { aRefuser = null }) { Text("Annuler") } },
        )
    }
    aPayer?.let { x ->
        var mode by remember { mutableStateOf("especes") }
        var enCours by remember { mutableStateOf(false) }
        var compte by remember(mode) { mutableStateOf(d.comptes.firstOrNull { it.type == if (mode == "especes") "caisse" else "banque" } ?: d.comptes.firstOrNull()) }
        AlertDialog(
            onDismissRequest = { aPayer = null },
            title = { Text("Payer cette dépense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${x.objet}, ${euros(x.montant)}")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("especes" to "Espèces", "virement" to "Virement").forEachIndexed { i, (k, v) ->
                            SegmentedButton(selected = mode == k, onClick = { mode = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
                        }
                    }
                    ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it] }
                }
            },
            confirmButton = {
                Button(enabled = !enCours && compte != null, onClick = {
                    enCours = true
                    scope.launch {
                        try { Repo.payerDemande(x.id, compte!!.id, mode, aujourdhui().toString()); message("Paiement enregistré"); version++ }
                        catch (e: Exception) { message(traduireErreur(e)) }
                        aPayer = null
                    }
                }) { Text("Enregistrer le paiement") }
            },
            dismissButton = { TextButton(onClick = { aPayer = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun FormulaireDemande(d: Donnees, onFini: (Boolean) -> Unit, message: (String) -> Unit) {
    var objet by remember { mutableStateOf("") }
    var montant by remember { mutableStateOf("") }
    val categories = d.categories.filter { it.sens == "depense" }
    var categorie by remember { mutableStateOf(categories.firstOrNull()) }
    var projets by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var projet by remember { mutableStateOf<Projet?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { projets = Repo.projets() } catch (_: Exception) { } }
    val valeur = montant.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nouvelle demande", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(objet, { objet = it.take(120) }, label = { Text("Objet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(montant, { montant = it }, label = { Text("Montant") }, suffix = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChoixListe("Catégorie", categorie?.nom ?: "", categories.map { it.nom }) { categorie = categories[it] }
        ChoixListe("Activité", projet?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { projet = if (it == 0) null else projets[it - 1] }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && objet.isNotBlank() && valeur != null && categorie != null, onClick = {
                enCours = true
                scope.launch {
                    try { Repo.creerDemande(NouvelleDemande(d.profil.id, objet.trim(), valeur!!, categorie!!.id, projet?.id)); onFini(true) }
                    catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Envoyer") }
        }
    }
}

@Composable
fun ChoixListe(titre: String, valeur: String, options: List<String>, onChoix: (Int) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { ouvert = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("$titre$NBSP: $valeur", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChoix(i); ouvert = false }) }
        }
    }
}

// Signature au doigt du président
@Composable
private fun Signature(x: Demande, d: Donnees, onFini: (Boolean) -> Unit, message: (String) -> Unit) {
    val traits = remember { mutableStateListOf<List<Offset>>() }
    var courant by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var taille by remember { mutableStateOf(IntSize.Zero) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val longueur = (traits + listOf(courant)).sumOf { t -> t.zipWithNext { a, b -> (a - b).getDistance().toDouble() }.sum() }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Valider cette dépense$NBSP?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${x.objet}, ${euros(x.montant)}", fontWeight = FontWeight.SemiBold)
        Text("Signature", fontSize = 13.sp, color = Couleurs.Texte2)
        Canvas(
            Modifier.fillMaxWidth().height(200.dp)
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFD9D3D0), RoundedCornerShape(20.dp))
                .onSizeChanged { taille = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { courant = listOf(it) },
                        onDrag = { change, _ -> courant = courant + change.position },
                        onDragEnd = { traits.add(courant); courant = emptyList() },
                    )
                },
        ) {
            (traits + listOf(courant)).filter { it.size > 1 }.forEach { t ->
                val p = Path().apply { moveTo(t[0].x, t[0].y); t.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(p, Color(0xFF1C1B1A), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { traits.clear(); courant = emptyList() }) { Text("Effacer") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours, onClick = {
                if (longueur < 40) { message("Signature obligatoire"); return@Button }
                enCours = true
                scope.launch {
                    try { Repo.validerDemande(x, signatureEnPng(traits.toList(), taille.width, taille.height), d.profil.id); onFini(true) }
                    catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Signer et valider") }
        }
    }
}

@Composable
internal fun Chiffre(titre: String, montant: Double, sous: String, couleur: Color, modifier: Modifier = Modifier) =
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(titre, fontSize = 13.sp, color = Couleurs.Texte2)
            Text(euros(montant), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = couleur, maxLines = 1)
            Text(sous, fontSize = 12.sp, color = Couleurs.Texte2)
        }
    }

@Composable
internal fun LigneBudgetVue(b: LigneBudget, activite: String?) {
    val taux = b.taux ?: 0.0
    val couleur = when {
        b.sens == "recette" -> Couleurs.Bleu
        taux > 100 -> Couleurs.Erreur
        b.alerte -> Couleurs.Jaune
        else -> Couleurs.Orange
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.categorie, fontWeight = FontWeight.SemiBold)
                activite?.let { Text(it, fontSize = 12.sp, color = Couleurs.Texte2) }
            }
            if (b.sens == "depense" && taux > 100) Puce("Dépassé", Couleurs.ErreurClair, Color(0xFF410002))
            else if (b.alerte) Puce("Alerte", Couleurs.JauneClair, Couleurs.SurJaune)
        }
        LinearProgressIndicator(progress = { (taux / 100).toFloat().coerceIn(0f, 1f) }, color = couleur,
            trackColor = Color(0xFFEFEDEC), modifier = Modifier.fillMaxWidth().height(8.dp))
        Text("${euros(b.realise)} sur ${euros(b.prevu)} · ${taux.toString().replace('.', ',')}$NBSP%", fontSize = 13.sp, color = Couleurs.Texte2)
    }
}

// ---------- Rapprochement ----------
@Composable
fun EcranRapprochement(d: Donnees, message: (String) -> Unit) {
    var compte by remember { mutableStateOf(d.comptes.firstOrNull { it.type == "banque" } ?: d.comptes.firstOrNull()) }
    var historique by remember { mutableStateOf<List<Rapprochement>>(emptyList()) }
    var ecritures by remember { mutableStateOf<List<Ecriture>?>(null) }
    var pointe by remember { mutableStateOf(0.0) }
    val coches = remember { mutableStateListOf<String>() }
    var solde by remember { mutableStateOf("") }
    var releve by remember { mutableStateOf<Fichier?>(null) }
    var version by remember { mutableStateOf(0) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fin = aujourdhui().toString()
    val choix = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) releve = f }

    LaunchedEffect(compte, version) {
        val c = compte ?: return@LaunchedEffect
        try {
            historique = Repo.rapprochements()
            val l = Repo.ecrituresCompte(c.id, fin).filter { it.rapprochementId == null }
            ecritures = l; pointe = Repo.soldePointe(c.id)
            coches.clear(); coches.addAll(l.map { it.id })
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val dernier = historique.firstOrNull { it.compteId == compte?.id && it.statut == "termine" }
    val debut = dernier?.fin?.let { LocalDate.parse(it).plus(DatePeriod(days = 1)).toString() } ?: "${aujourdhui().year}-01-01"
    val total = pointe + (ecritures ?: emptyList()).filter { it.id in coches }.sumOf { it.signe }
    val releveValeur = solde.replace(',', '.').toDoubleOrNull()
    val ecart = releveValeur?.let { kotlin.math.round((it - total) * 100) / 100 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Titre("Rapprochement") }
        if (d.peut("rapprocher")) {
            item {
                CarteBlanche {
                    ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it]; solde = ""; releve = null }
                    Text("Période du ${dateFr(debut)} au ${dateFr(fin)}", fontSize = 13.sp, color = Couleurs.Texte2)
                    OutlinedTextField(solde, { solde = it }, label = { Text("Solde du relevé au ${dateFr(fin)}") }, suffix = { Text("€") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = choix, modifier = Modifier.fillMaxWidth()) {
                        Text(releve?.let { "Relevé joint (${it.ko}$NBSP" + "Ko)" } ?: if (compte?.type == "caisse") "Joindre le procès-verbal de comptage" else "Joindre le relevé")
                    }
                }
            }
            item { Text("Écritures à pointer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val l = ecritures
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (l.isEmpty()) item { Text("Toutes les écritures sont rapprochées.", color = Couleurs.Texte2) }
            else items(l, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable { if (e.id in coches) coches.remove(e.id) else coches.add(e.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.id in coches, { if (it) coches.add(e.id) else coches.remove(e.id) })
                    Column(Modifier.weight(1f)) {
                        Text(e.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dateFr(e.date), fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                    val v = e.signe
                    Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)), color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chiffre("Pointé", total, "", Couleurs.Texte, Modifier.weight(1f))
                    Surface(color = if (ecart == 0.0) Couleurs.BleuClair else Couleurs.JauneClair, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Écart", fontSize = 13.sp, color = Couleurs.Texte2)
                            Text(ecart?.let { euros(it) } ?: "–", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }
                }
            }
            item {
                Text(when {
                    releve == null -> "Joignez le relevé pour terminer."
                    ecart == null -> "Saisissez le solde du relevé."
                    ecart != 0.0 -> "Écart non nul : cochez ou décochez des écritures, ou saisissez celle qui manque."
                    else -> "Écart nul. La période sera verrouillée."
                }, fontSize = 13.sp, color = Couleurs.Texte2)
            }
            item {
                Button(enabled = !enCours && releve != null && ecart == 0.0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                    val c = compte ?: return@Button
                    enCours = true
                    scope.launch {
                        try {
                            Repo.terminerRapprochement(c.id, debut, fin, releveValeur!!, releve!!, "releve-${c.type}-$fin.${releve!!.extension}", coches.toList())
                            message("Rapprochement terminé, période verrouillée"); solde = ""; releve = null; version++
                        } catch (e: Exception) { message(traduireErreur(e)) }
                        enCours = false
                    }
                }) { Text("Terminer le rapprochement", fontWeight = FontWeight.Bold) }
            }
        }
        item { Text("Historique", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (historique.isEmpty()) item { Text("Aucun rapprochement terminé.", color = Couleurs.Texte2) }
        items(historique, key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(d.comptes.firstOrNull { it.id == r.compteId }?.nom ?: "", fontWeight = FontWeight.SemiBold)
                    Text("${dateFr(r.debut)} au ${dateFr(r.fin)}", fontSize = 13.sp, color = Couleurs.Texte2)
                }
                Text(euros(r.soldeReleve))
            }
        }
    }
}

