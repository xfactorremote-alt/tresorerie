package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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

// Bouton + flottant commun (.fab du site sur téléphone) : rond de 56 dp, orange de l'association, « + » blanc
@Composable
internal fun BoutonPlus(description: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    FloatingActionButton(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, containerColor = Couleurs.Orange, contentColor = Color.White,
        modifier = modifier.padding(16.dp).size(56.dp)) { Icon(Icons.Filled.Add, contentDescription = description) }

@Composable
internal fun Puce(texte: String, fond: Color, couleur: Color, bord: Color? = null) =
    Surface(color = fond, contentColor = couleur, shape = RoundedCornerShape(13.dp), border = bord?.let { BorderStroke(1.dp, it) }) {
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

// Chronologie datée d'une demande (même lecture que .chronologie du site) :
// "fait" = étape franchie, "encours" = étape attendue, "ko" = refus, "" = à venir ; un seul filet gris continu
@Composable
private fun Chronologie(etapes: List<Triple<String, String, String>>) {
    Column(Modifier.padding(vertical = 4.dp)) {
        etapes.forEachIndexed { i, (titre, etat, detail) ->
            val couleur = when (etat) { "fait" -> Couleurs.Bleu; "encours" -> Couleurs.Jaune; "ko" -> Couleurs.Erreur; else -> Color.White }
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(16.dp).fillMaxHeight()) {
                    // Filet : du point de cette étape jusqu'au point suivant
                    if (i < etapes.lastIndex) Box(Modifier.padding(start = 7.dp, top = 9.dp).width(2.dp).fillMaxHeight().background(Color(0xFFE8E3E1)))
                    if (i > 0) Box(Modifier.padding(start = 7.dp).width(2.dp).height(9.dp).background(Color(0xFFE8E3E1)))
                    Box(Modifier.padding(top = 3.dp, start = 2.dp).size(12.dp).clip(RoundedCornerShape(6.dp)).background(couleur)
                        .border(2.dp, if (etat.isEmpty()) Color(0xFFD9D3D0) else couleur, RoundedCornerShape(6.dp)))
                }
                Column(Modifier.padding(start = 6.dp, bottom = if (i < etapes.lastIndex) 12.dp else 0.dp)) {
                    Text(titre, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (detail.isNotBlank()) Text(detail, fontSize = 14.sp, color = Couleurs.Texte2)
                }
            }
        }
    }
}

// ---------- Dépenses ----------
// Mêmes éléments visuels que le site : puces bordées, onglets de filtre, ligne de détail, encarts, justification
private fun bordStatut(s: String): Color = when (s) {
    "validee", "justifiee" -> Color(0xFF9CCBEA)
    "refusee" -> Color(0xFFF2A8A0)
    "annulee", "brouillon" -> Color(0xFFD9D3D0)
    else -> Color(0xFFF2C66A)
}

// Libellé de champ obligatoire : astérisque rouge comme `.obligatoire` du site
private fun obligatoire(t: String) = buildAnnotatedString { append(t); withStyle(SpanStyle(color = Couleurs.Erreur)) { append(" *") } }

// « Objet », montant (objet en gras), comme `<b>objet</b>, montant` du site
private fun objetMontant(x: Demande) = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(x.objet) }; append(", " + euros(x.montant)) }

@Composable
private fun PuceDemande(x: Demande, etats: EtatsDemandes) {
    val annul = etats.annulation(x) != null; val regul = etats.aRegulariser(x)
    val cle = when { annul -> "annulee"; regul -> "refusee"; else -> x.statut }
    val (fond, couleur) = couleursStatut(cle)
    Puce(when { annul -> "Opération annulée"; regul -> "À régulariser"; else -> STATUTS[x.statut] ?: x.statut }, fond, couleur, bordStatut(cle))
}

// Onglets de filtre en pilules avec leur nombre (`.onglets-filtre` du site)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OngletsFiltre(options: List<Triple<String, String, Int>>, choisi: String, onChoix: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, l, n) ->
            val actif = k == choisi
            Surface(onClick = { onChoix(k) }, shape = RoundedCornerShape(50), color = if (actif) Couleurs.OrangeClair else Color.White,
                contentColor = if (actif) Couleurs.SurOrangeClair else Couleurs.Texte2,
                border = BorderStroke(1.dp, if (actif) Color(0xFFF5B39C) else Color(0xFFD9D3D0))) {
                Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    if (n > 0) Box(Modifier.padding(start = 6.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(50))
                        .background(if (actif) Couleurs.Orange else Color(0xFFEFEDEC)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("$n", fontSize = 12.sp, color = if (actif) Color.White else Couleurs.Texte)
                    }
                }
            }
        }
    }
}

// Liste vide : carte centrée avec l'icône « valide » (`.carte.vide` du site)
@Composable
private fun CarteVide(texte: String) = CarteBlanche {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(32.dp), tint = Couleurs.Texte2)
        Text(texte, color = Couleurs.Texte2, textAlign = TextAlign.Center)
    }
}

// Ligne « libellé … valeur » séparée par un filet (`.ligne-detail` du site)
@Composable
private fun LigneDetail(titre: String, valeur: String?) {
    if (valeur.isNullOrBlank()) return
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(titre, color = Couleurs.Texte2, fontSize = 14.sp)
            Text(valeur, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Color(0xFFE8E3E1))
    }
}

// Justification : filet à gauche sur fond gris (`.justification` du site)
@Composable
private fun BlocJustification(texte: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEFEDEC))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFD9D3D0)))
        Text(texte, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 14.sp)
    }
}

// Encarts `.info` (bleu) et `.alerte` (rouge) du site
@Composable
private fun BlocInfo(texte: String, alerte: Boolean = false) {
    Surface(color = if (alerte) Color(0xFFFDEDEB) else Color(0xFFEAF4FB), contentColor = if (alerte) Color(0xFF410002) else Couleurs.Texte,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (alerte) Color(0xFFF2A8A0) else Color(0xFF9CCBEA)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (alerte) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, Modifier.size(20.dp), tint = if (alerte) Couleurs.Erreur else Couleurs.Bleu)
            Text(texte, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

// Bouton « danger » du site (fond clair, filet et texte rouges)
@Composable
private fun BoutonDanger(texte: String, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, border = BorderStroke(1.dp, Color(0xFFF2A8A0)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Couleurs.Erreur)) { Text(texte) }

// Pièce de la fiche (Devis, Signature, Justificatif) : petit bouton qui ouvre l'aperçu
@Composable
private fun BoutonPiece(texte: String, icone: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.heightIn(min = 36.dp)) {
        Icon(icone, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(texte, fontSize = 14.sp)
    }

// Aperçu d'un document dans l'application (comme ouvrirFichier du site) : titre, image, Ouvrir, Fermer
@Composable
private fun FeuilleApercu(seau: String, chemin: String, onFermer: () -> Unit) {
    // « devis » : pièce du seau justificatifs, titrée « Devis » comme sur le site
    val bucket = if (seau == "devis") "justificatifs" else seau
    val ouvrir = rememberOuvrirFichier()
    var octets by remember(chemin) { mutableStateOf<ByteArray?>(null) }
    var image by remember(chemin) { mutableStateOf<ImageBitmap?>(null) }
    var echec by remember(chemin) { mutableStateOf(false) }
    val pdf = chemin.endsWith(".pdf", ignoreCase = true)
    val mime = if (pdf) "application/pdf" else if (chemin.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
    LaunchedEffect(chemin) {
        try { val o = Repo.telecharger(bucket, chemin); octets = o; if (o == null) echec = true else if (!pdf) image = imageDepuisOctets(o) }
        catch (_: Exception) { echec = true }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (seau == "devis") "Devis" else if (bucket == "signatures") "Signature du président" else if (bucket == "releves") "Relevé" else "Justificatif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val img = image
        when {
            echec -> Text("Fichier indisponible", color = Couleurs.Erreur, fontSize = 14.sp)
            img != null -> Image(img, "Document joint", contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, Color(0xFFD9D3D0), RoundedCornerShape(4.dp)))
            octets == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(enabled = octets != null, onClick = { octets?.let { ouvrir(chemin.substringAfterLast('/'), mime, it) } }) { Text("Ouvrir") }
            FilledTonalButton(onClick = onFermer) { Text("Fermer") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranDepenses(d: Donnees, message: (String) -> Unit) {
    var liste by remember { mutableStateOf<List<Demande>?>(null) }
    var noms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }
    var filtre by remember { mutableStateOf(FiltreDemandesAccueil.prendre() ?: when { d.peut("valider_depenses") -> "a_valider"; d.peut("payer_depenses") -> "a_payer"; else -> "toutes" }) }
    var pieces by remember { mutableStateOf<List<Piece>>(emptyList()) }
    var delai by remember { mutableStateOf(7) }
    var detail by remember { mutableStateOf<Demande?>(null) }
    var nouvelle by remember { mutableStateOf(false) }
    var aValider by remember { mutableStateOf<Demande?>(null) }
    var aRefuser by remember { mutableStateOf<Demande?>(null) }
    var aPayer by remember { mutableStateOf<Demande?>(null) }
    var aJustifier by remember { mutableStateOf<Demande?>(null) }
    var aRegulariser by remember { mutableStateOf<Demande?>(null) }
    var aAnnuler by remember { mutableStateOf<Demande?>(null) }
    var apercu by remember { mutableStateOf<Pair<String, String>?>(null) }
    var voirOperation by remember { mutableStateOf<Ecriture?>(null) }
    var ecritures by remember { mutableStateOf<List<Ecriture>>(emptyList()) }
    var projetsTous by remember { mutableStateOf<List<Projet>>(emptyList()) }
    var profilsCourts by remember { mutableStateOf<List<ProfilCourt>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version, Synchro.version) {
        try {
            liste = Repo.demandes()
            ecritures = try { Repo.toutesEcritures() } catch (_: Exception) { emptyList() }
            projetsTous = try { Repo.projets() } catch (_: Exception) { emptyList() }
            profilsCourts = try { Repo.profils() } catch (_: Exception) { emptyList() }
            noms = try { Repo.profils().associate { it.id to it.nom } } catch (_: Exception) { mapOf(d.profil.id to d.profil.nom) }
            pieces = try { Repo.pieces() } catch (_: Exception) { emptyList() }
            delai = try { (Repo.reglages()["delai_justificatif_jours"] ?: 7.0).toInt() } catch (_: Exception) { 7 }
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    // État de chaque demande déduit de son opération et de l'éventuelle contre-passation
    val etats = remember(liste, ecritures) { EtatsDemandes(liste ?: emptyList(), ecritures) }
    val filtres = listOf(
        "a_valider" to "À valider", "a_payer" to "À payer", "a_justifier" to "Justificatif attendu", "a_regulariser" to "À régulariser", "terminees" to "Terminées", "toutes" to "Toutes",
    ).filter { (k, _) -> k != "a_regulariser" || liste.orEmpty().any { etats.aRegulariser(it) } }
    fun garde(x: Demande, f: String = filtre) = when (f) {
        "a_valider" -> x.statut == "soumise"; "a_payer" -> x.statut == "validee"; "a_justifier" -> x.statut == "payee" && etats.annulation(x) == null
        "a_regulariser" -> etats.aRegulariser(x)
        "terminees" -> !etats.aRegulariser(x) && (x.statut in listOf("justifiee", "refusee", "annulee") || etats.annulation(x) != null); else -> true
    }
    if (filtre == "a_regulariser" && liste != null && filtres.none { it.first == "a_regulariser" }) filtre = "toutes"
    // Mêmes calculs que le site : nomDe, pieceDe (pièce de la demande ou de son opération), devisDe, retardDe
    val nomDe = { id: String? -> if (id == d.profil.id) "Vous" else id?.let { noms[it] } ?: "Membre du bureau" }
    val nomProjet = { pid: String? -> pid?.let { p -> projetsTous.firstOrNull { it.id == p }?.nom } }
    val devisDe = { x: Demande -> pieces.firstOrNull { it.nature == "devis" && it.demandeId == x.id } }
    val pieceDe = { x: Demande -> val op = etats.operation(x); pieces.firstOrNull { it.nature != "devis" && (it.demandeId == x.id || (op != null && it.transactionId == op.id)) } }
    val joursDepuisPaiement = { x: Demande -> x.payeeLe?.let { (aujourdhui().toEpochDays() - LocalDate.parse(it.take(10)).toEpochDays()).toInt() } ?: 0 }
    val retardDe = { x: Demande -> if (x.statut == "payee" && etats.annulation(x) == null && x.payeeLe != null && joursDepuisPaiement(x) > delai) joursDepuisPaiement(x) else 0 }

    val supprimerDemande = rememberSuppression(message) { detail = null; version++ }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Grand écran : le bouton « Nouvelle demande » est dans l'en-tête (comme le site) ; téléphone : bouton + flottant
        val large = maxWidth >= 840.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // En-tête : titre et ce qui attend (comme le site)
            item {
                val l0 = liste.orEmpty()
                val resume = listOf("a_valider", "a_payer", "a_justifier", "a_regulariser").mapNotNull { k ->
                    val n = l0.count { garde(it, k) }; if (n > 0) "$n " + (filtres.firstOrNull { it.first == k }?.second ?: "").lowercase() else null }
                    .joinToString(" · ").ifEmpty { "Rien en attente" }
                if (large) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Demandes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(resume, color = Couleurs.Texte2, fontSize = 13.sp)
                    if (d.peut("demander_depenses")) Button(onClick = { nouvelle = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Nouvelle demande")
                    }
                } else Column {
                    Text("Demandes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(resume, color = Couleurs.Texte2)
                }
            }
            item {
                OngletsFiltre(filtres.map { (k, l) -> Triple(k, l, if (k in listOf("toutes", "terminees")) 0 else liste.orEmpty().count { garde(it, k) }) }, filtre) { filtre = it }
            }
            val l = liste
            if (l == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                val vus = l.filter { garde(it) }
                if (vus.isEmpty()) item {
                    CarteVide("Aucune demande" + (if (filtre == "toutes") "" else " " + (filtres.firstOrNull { it.first == filtre }?.second ?: "").lowercase()) + ".")
                } else item {
                    // Liste sobre dans une carte : nombre et total, puis une ligne par demande qui ouvre sa fiche
                    CarteBlanche {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                                Text("${vus.size} demande" + if (vus.size > 1) "s" else "", color = Couleurs.Texte2, modifier = Modifier.weight(1f))
                                Text(euros(vus.sumOf { it.montant }), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            HorizontalDivider(color = Color(0xFFE8E3E1))
                            vus.forEachIndexed { i, x ->
                                val r = retardDe(x)
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { detail = x }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(x.objet, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(listOfNotNull(nomDe(x.demandeur), dateFr(x.creeLe.take(10)), d.nomCategorie(x.categorieId), nomProjet(x.projetId)).joinToString(" · "),
                                            fontSize = 13.sp, color = Couleurs.Texte2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (r > 0) Text("Justificatif en retard de ${r - delai} jour" + if (r - delai > 1) "s" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Erreur)
                                        else if (etats.aRegulariser(x)) Text("Déjà payée puis refusée : à régulariser", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Erreur)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(euros(x.montant), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        PuceDemande(x, etats)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Couleurs.Texte2, modifier = Modifier.size(18.dp))
                                }
                                if (i < vus.lastIndex) HorizontalDivider(color = Color(0xFFE8E3E1))
                            }
                        }
                    }
                }
            }
        }
        if (d.peut("demander_depenses") && !large) {
            BoutonPlus("Nouvelle demande", Modifier.align(Alignment.BottomEnd)) { nouvelle = true }
        }
    }

    if (nouvelle) ModalBottomSheet(onDismissRequest = { nouvelle = false }) {
        FormulaireDemande(d, onFini = { ok -> nouvelle = false; if (ok) { filtre = "toutes"; version++ } }, message)
    }
    detail?.let { x ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            // Fiche d'une demande (comme ficheDemande du site) : en-tête, informations, justification, chronologie datée, pièces, actions
            val annul = etats.annulation(x); val regul = etats.aRegulariser(x); val op = etats.operation(x)
            val piece = pieceDe(x); val devis = devisDe(x); val r = retardDe(x)
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(x.objet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PuceDemande(x, etats)
                            if (x.regularisation) Puce("Déjà payée", Color(0xFFEFEDEC), Couleurs.Texte2, Color(0xFFD9D3D0))
                        }
                    }
                    Text(euros(x.montant), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Column {
                    LigneDetail("Demandée par", nomDe(x.demandeur))
                    LigneDetail("Catégorie", d.nomCategorie(x.categorieId))
                    LigneDetail("Activité", nomProjet(x.projetId))
                    LigneDetail("Souhaitée pour le", x.dateSouhaitee?.let(::dateFr))
                }
                x.justification?.takeIf { it.isNotBlank() }?.let { BlocJustification(it) }
                Chronologie(buildList {
                    add(Triple("Demande", "fait", dateFr(x.creeLe.take(10))))
                    add(when {
                        x.statut == "refusee" -> Triple("Refusée", "ko", (x.valideeLe?.let { dateFr(it.take(10)) + " · " } ?: "") + nomDe(x.valideePar) + (x.motifRefus?.let { " · « $it »" } ?: ""))
                        x.valideeLe != null -> Triple("Validée et signée", "fait", dateFr(x.valideeLe.take(10)) + " · " + nomDe(x.valideePar))
                        else -> Triple("Validation", if (x.statut == "soumise") "encours" else "", if (x.statut == "annulee") "Demande annulée" else "En attente du président")
                    })
                    if (!(x.statut == "refusee" && op == null)) {
                        add(if (x.payeeLe != null || op != null) Triple("Payée", "fait", x.payeeLe?.let { dateFr(it.take(10)) } ?: dateFr(op!!.date))
                            else Triple("Paiement", if (x.statut == "validee") "encours" else "", "À faire par le trésorier"))
                        val fourni = piece != null || x.statut == "justifiee"
                        add(Triple("Justificatif", if (fourni) "fait" else if (x.statut == "payee") (if (r > 0) "ko" else "encours") else "",
                            if (fourni) "Fourni" else if (r > 0) "En retard (délai de $delai jours)" else "Attendu dans les $delai jours après paiement"))
                    }
                })
                if (annul != null && op != null) BlocInfo("Paiement du ${dateFr(op.date)} neutralisé par contre-passation le ${dateFr(annul.date)}" +
                    motifDe(annul.libelle).let { m -> if (m.isNotEmpty()) " ($m)" else "" } + ". Le montant n’entre plus dans les comptes.")
                if (regul && op != null) BlocInfo("Dépense déjà payée, puis refusée par le président : l’argent est sorti " +
                    (if (d.comptes.firstOrNull { it.id == op.compteId }?.type == "caisse") "de la caisse" else "du compte bancaire") + ". " +
                    (if (d.peut("saisir_ecritures")) "Régularisez-la : erreur de saisie, ou remboursement reçu." else "Le trésorier doit la régulariser."), alerte = true)
                if (devis != null || x.signature != null || piece != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devis?.let { dv -> BoutonPiece("Devis", Icons.Outlined.AttachFile) { apercu = "devis" to dv.chemin } }
                    x.signature?.let { s -> BoutonPiece("Signature", Icons.Outlined.CheckCircle) { apercu = "signatures" to s } }
                    piece?.let { p -> BoutonPiece("Justificatif", Icons.Outlined.AttachFile) { apercu = "justificatifs" to p.chemin } }
                }
                // Actions secondaires à gauche (Voir l'opération, Annuler la demande)
                val voirOp = op != null && d.peut("consulter_finances", "saisir_ecritures")
                val annulable = x.statut == "soumise" && x.demandeur == d.profil.id
                if (voirOp || annulable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (voirOp) TextButton(onClick = { detail = null; voirOperation = op }) { Text("Voir l’opération") }
                    if (annulable) TextButton(onClick = { detail = null; aAnnuler = x }) { Text("Annuler la demande") }
                }
                // Supprimer (corbeille) à gauche, actions principales à droite ; Fermer s'il n'y a rien à faire
                val supprimable = x.statut in listOf("annulee", "refusee", "brouillon") && op == null && (x.demandeur == d.profil.id || d.peut("administrer"))
                val valider = d.peut("valider_depenses") && x.statut == "soumise"
                val payer = d.peut("payer_depenses") && x.statut == "validee" && x.valideePar != d.profil.id
                val regulariser = regul && d.peut("saisir_ecritures") && op?.rapproche == false
                val justifier = x.statut == "payee" && annul == null && (d.peut("payer_depenses", "saisir_ecritures") || x.demandeur == d.profil.id)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (supprimable) BoutonSupprimer({ supprimerDemande("expense_requests", x.id, x.objet) })
                    Spacer(Modifier.weight(1f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        if (valider) {
                            TextButton(onClick = { detail = null; aRefuser = x }) { Text("Refuser") }
                            Button(onClick = { detail = null; aValider = x }) { Text("Valider et signer") }
                        }
                        if (payer) Button(onClick = { detail = null; aPayer = x }) { Text("Payer") }
                        if (regulariser) Button(onClick = { detail = null; aRegulariser = x }) { Text("Régulariser") }
                        if (justifier) Button(onClick = { detail = null; aJustifier = x }) { Text("Joindre le justificatif") }
                        if (!valider && !payer && !regulariser && !justifier) Button(onClick = { detail = null }) { Text("Fermer") }
                    }
                }
            }
        }
    }
    aValider?.let { x ->
        ModalBottomSheet(onDismissRequest = { aValider = null }) {
            Signature(x, d, nomDe(x.demandeur), devisDe(x), onVoirDevis = { apercu = "devis" to it.chemin },
                onFini = { ok -> aValider = null; if (ok) { message("Dépense validée"); version++ } }, message = message)
        }
    }
    aRefuser?.let { x ->
        var motif by remember { mutableStateOf("") }
        var enCours by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { aRefuser = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Refuser cette dépense$NBSP_FINE?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(objetMontant(x))
                OutlinedTextField(motif, { motif = it.take(300) }, label = { Text(obligatoire("Motif")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { aRefuser = null }) { Text("Annuler") }
                    BoutonDanger("Refuser", enabled = !enCours && motif.isNotBlank()) {
                        enCours = true
                        scope.launch {
                            try { Repo.refuserDemande(x.id, motif.trim()); message("Demande refusée"); aRefuser = null; version++ }
                            catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                        }
                    }
                }
            }
        }
    }
    aPayer?.let { x ->
        var mode by remember { mutableStateOf("especes") }
        var enCours by remember { mutableStateOf(false) }
        var compte by remember(mode) { mutableStateOf(d.comptes.firstOrNull { it.type == if (mode == "especes") "caisse" else "banque" } ?: d.comptes.firstOrNull()) }
        var date by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
        val dt = dateDepuisFr(date)
        ModalBottomSheet(onDismissRequest = { aPayer = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Payer cette dépense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(objetMontant(x))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("especes" to "Espèces", "virement" to "Virement").forEachIndexed { i, (k, v) ->
                        SegmentedButton(selected = mode == k, onClick = { mode = k }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(v) }
                    }
                }
                ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it] }
                OutlinedTextField(date, { date = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dt == null, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { aPayer = null }) { Text("Annuler") }
                    Button(enabled = !enCours && compte != null && dt != null, onClick = {
                        enCours = true
                        scope.launch {
                            try { Repo.payerDemande(x.id, compte!!.id, mode, dt.toString()); message("Paiement enregistré"); aPayer = null; version++ }
                            catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                        }
                    }) { Text("Enregistrer le paiement") }
                }
            }
        }
    }
    aJustifier?.let { x ->
        ModalBottomSheet(onDismissRequest = { aJustifier = null }) {
            FeuilleJustifier(x, message) { ok -> aJustifier = null; if (ok) version++ }
        }
    }
    aAnnuler?.let { x ->
        var enCours by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { aAnnuler = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Annuler cette demande$NBSP_FINE?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("«$NBSP${x.objet}$NBSP», ${euros(x.montant)}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    // « Garder » revient à la fiche, comme le site
                    TextButton(onClick = { aAnnuler = null; detail = x }) { Text("Garder") }
                    BoutonDanger("Annuler la demande", enabled = !enCours) {
                        enCours = true
                        scope.launch {
                            try { Repo.annulerDemande(x.id); message("Demande annulée"); aAnnuler = null; version++ }
                            catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                        }
                    }
                }
            }
        }
    }
    aRegulariser?.let { x ->
        val op = etats.operation(x)
        if (op != null) ModalBottomSheet(onDismissRequest = { aRegulariser = null }) {
            FeuilleRegulariser(d, x, op, message) { ok -> aRegulariser = null; if (ok) version++ }
        }
    }
    voirOperation?.let { e ->
        ModalBottomSheet(onDismissRequest = { voirOperation = null }) {
            DetailOperation(d, e, ecritures, pieces, liste.orEmpty(), projetsTous, profilsCourts, "", "", message, onVoir = { voirOperation = it }) { change ->
                voirOperation = null; if (change) version++
            }
        }
    }
    // Aperçu d'une pièce : en dernier pour passer au-dessus de la fiche ou de la signature
    apercu?.let { (bucket, chemin) ->
        ModalBottomSheet(onDismissRequest = { apercu = null }) { FeuilleApercu(bucket, chemin) { apercu = null } }
    }
}

// Joindre le justificatif (comme feuilleJustifier du site) : facture ou ticket, photo ou PDF de 3 Mo maximum
@Composable
private fun FeuilleJustifier(x: Demande, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var fichier by remember { mutableStateOf<Fichier?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val choix = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) fichier = f }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Joindre le justificatif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(objetMontant(x))
        Text(obligatoire("Facture ou ticket"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = choix, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(fichier?.let { it.nom ?: "Fichier choisi (${it.ko}$NBSP" + "Ko)" } ?: "Choisir une photo ou un PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Photo ou PDF de 3$NBSP" + "Mo maximum.", fontSize = 13.sp, color = Couleurs.Texte2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && fichier != null, onClick = {
                val f = fichier ?: return@Button
                enCours = true
                scope.launch {
                    try { Repo.justifierDemande(x.id, f); message("Justificatif joint (${f.ko}$NBSP" + "Ko)"); onFini(true) }
                    catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Joindre") }
        }
    }
}

// Dépense payée puis refusée : soit la dépense n'a jamais eu lieu (erreur de saisie), soit la personne
// a rendu l'argent (remboursement reçu, à la date et sur le compte où il est arrivé).
// Tant qu'aucun des deux n'est enregistré, la demande reste « à régulariser ».
@Composable
private fun FeuilleRegulariser(d: Donnees, x: Demande, op: Ecriture, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var rembourse by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var compte by remember { mutableStateOf(d.comptes.firstOrNull { it.id == op.compteId } ?: d.comptes.firstOrNull()) }
    var mode by remember { mutableStateOf(op.mode) }
    var enCours by remember { mutableStateOf(false) }
    // Date du remboursement : pas dans le futur (max du site)
    val dt = dateDepuisFr(date)?.takeIf { it <= aujourdhui() }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Régulariser la dépense refusée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column {
            Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(op.libelle) }; append(", ${euros(op.montant)}, payée le ${dateFr(op.date)}") })
            x.motifRefus?.let { Text("Motif du refus$NBSP: $it", color = Couleurs.Texte2, fontSize = 14.sp) }
        }
        listOf(false to ("Erreur de saisie" to "la dépense n’a pas eu lieu, aucun argent n’est sorti"), true to ("Remboursement reçu" to "la personne a rendu l’argent")).forEach { (v, l) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { rembourse = v }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(rembourse == v, { rembourse = v })
                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(l.first) }; append("$NBSP: " + l.second) }, fontSize = 15.sp)
            }
        }
        if (rembourse) {
            OutlinedTextField(date, { date = it }, label = { Text("Date du remboursement") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dt == null, modifier = Modifier.fillMaxWidth())
            ChoixListe("Arrivé sur le compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it] }
            ChoixListe("Mode", MODES[mode] ?: mode, MODES.values.toList()) { mode = MODES.keys.toList()[it] }
        }
        Text("Si l’argent n’a pas encore été rendu, ne faites rien$NBSP: la demande reste dans «$NBSP" + "À régulariser$NBSP».", color = Couleurs.Texte2, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && (!rembourse || (dt != null && compte != null)), onClick = {
                enCours = true
                scope.launch {
                    try {
                        if (rembourse) Repo.contrePasser(op, d.profil.id, "dépense refusée", "Remboursement reçu", dt.toString(), compte!!.id, mode)
                        else Repo.contrePasser(op, d.profil.id, "dépense refusée", "Erreur de saisie")
                        message(if (rembourse) "Remboursement enregistré" else "Dépense annulée"); onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
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
    var justification by remember { mutableStateOf("") }
    var pourLe by remember { mutableStateOf("") }
    var devis by remember { mutableStateOf<Fichier?>(null) }
    var seuil by remember { mutableStateOf(100.0) }
    var delai by remember { mutableStateOf(7) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val choixDevis = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) devis = f }
    LaunchedEffect(Unit) {
        try { projets = Repo.projets() } catch (_: Exception) { }
        try { Repo.reglages().let { r -> seuil = r["seuil_justification"] ?: 100.0; delai = (r["delai_justificatif_jours"] ?: 7.0).toInt() } } catch (_: Exception) { }
    }
    val valeur = montant.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.01 }
    // Au-delà du seuil réglé dans Paramètres, la justification devient obligatoire
    val justifRequise = valeur != null && valeur >= seuil
    // « Pour le » : pas avant aujourd'hui (min du site)
    val dateSouhaitee = if (pourLe.isBlank()) null else dateDepuisFr(pourLe)?.takeIf { it >= aujourdhui() }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nouvelle demande", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(objet, { objet = it.take(120) }, label = { Text(obligatoire("Objet")) }, placeholder = { Text("Location de la sono pour le concert") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(montant, { montant = it }, label = { Text(obligatoire("Montant (€)")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        ChoixListe("Catégorie", categorie?.nom ?: "", categories.map { it.nom }) { categorie = categories[it] }
        ChoixListe("Activité", projet?.nom ?: "Aucune", listOf("Aucune") + projets.map { it.nom }) { projet = if (it == 0) null else projets[it - 1] }
        OutlinedTextField(justification, { justification = it.take(500) }, label = { Text(if (justifRequise) obligatoire("Pourquoi cette dépense") else AnnotatedString("Pourquoi cette dépense")) },
            placeholder = { Text("À quoi elle sert, pourquoi ce fournisseur, ce qui se passe si on ne la fait pas") }, minLines = 2,
            isError = justifRequise && justification.isBlank(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pourLe, { pourLe = it }, label = { Text("Pour le") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true,
            isError = pourLe.isNotBlank() && dateSouhaitee == null, modifier = Modifier.fillMaxWidth())
        Text("Devis (facultatif)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = choixDevis, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(devis?.let { it.nom ?: "Devis joint (${it.ko}$NBSP" + "Ko)" } ?: "Choisir une photo ou un PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("La demande part au président, qui la valide en signant. Le trésorier paie ensuite, puis le justificatif est à déposer dans les $delai$NBSP" + "jours.",
            fontSize = 13.sp, color = Couleurs.Texte2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && objet.isNotBlank() && valeur != null && categorie != null && (!justifRequise || justification.isNotBlank()) && (pourLe.isBlank() || dateSouhaitee != null), onClick = {
                enCours = true
                scope.launch {
                    try {
                        val devisOk = Repo.creerDemande(NouvelleDemande(d.profil.id, objet.trim(), valeur!!, categorie!!.id, projet?.id,
                            justification = justification.trim().ifBlank { null }, dateSouhaitee = dateSouhaitee?.toString()), devis)
                        message(if (devisOk) "Demande envoyée au président" else "Demande envoyée, mais le devis n’a pas pu être joint")
                        onFini(true)
                    }
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

// Signature au doigt du président (comme feuilleValider du site)
@Composable
private fun Signature(x: Demande, d: Donnees, demandePar: String, devis: Piece?, onVoirDevis: (Piece) -> Unit, onFini: (Boolean) -> Unit, message: (String) -> Unit) {
    val traits = remember { mutableStateListOf<List<Offset>>() }
    var courant by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var taille by remember { mutableStateOf(IntSize.Zero) }
    var enCours by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val longueur = (traits + listOf(courant)).sumOf { t -> t.zipWithNext { a, b -> (a - b).getDistance().toDouble() }.sum() }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Valider cette dépense$NBSP_FINE?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column {
            Text(objetMontant(x))
            Text("Demandée par $demandePar · ${d.nomCategorie(x.categorieId)}" + (x.dateSouhaitee?.let { " · pour le ${dateFr(it)}" } ?: ""), fontSize = 14.sp, color = Couleurs.Texte2)
        }
        x.justification?.takeIf { it.isNotBlank() }?.let { BlocJustification(it) }
        devis?.let { dv -> FilledTonalButton(onClick = { onVoirDevis(dv) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("Voir le devis", fontSize = 14.sp) } }
        Text(obligatoire("Signature"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Canvas(
            Modifier.fillMaxWidth().height(180.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRoundRect(Color(0xFF5A5350), cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
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
                drawPath(p, Color(0xFF1C1B1A), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
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

// ---------- Rapprochement ----------
// Même page que le site : carte de saisie, carte « Écritures à pointer », carte de bilan (Relevé, Pointé, Écart, Terminer), carte « Historique »
@OptIn(ExperimentalMaterial3Api::class)
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
    var apercu by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Période modifiable comme sur le site : début = lendemain du dernier rapprochement, fin = aujourd'hui
    var debutSaisi by remember(compte) { mutableStateOf<String?>(null) }
    var finSaisie by remember(compte) { mutableStateOf(dateFr(aujourdhui().toString())) }
    val fin = dateDepuisFr(finSaisie)?.toString() ?: aujourdhui().toString()
    val choix = rememberChoixFichier { f, err -> if (err != null) message(err); if (f != null) releve = f }

    LaunchedEffect(compte, version, fin, Synchro.version) {
        try {
            historique = Repo.rapprochements()
            val c = compte ?: return@LaunchedEffect
            if (!d.peut("rapprocher")) return@LaunchedEffect
            val l = Repo.ecrituresCompte(c.id, fin).filter { it.rapprochementId == null }
            ecritures = l; pointe = Repo.soldePointe(c.id)
            coches.clear(); coches.addAll(l.map { it.id })
        } catch (e: Exception) { message(traduireErreur(e)) }
    }
    val dernier = historique.firstOrNull { it.compteId == compte?.id && it.statut == "termine" }
    val debutDefaut = dernier?.fin?.let { LocalDate.parse(it).plus(DatePeriod(days = 1)).toString() } ?: "${aujourdhui().year}-01-01"
    val debut = debutSaisi?.let { dateDepuisFr(it)?.toString() } ?: debutDefaut
    val total = pointe + (ecritures ?: emptyList()).filter { it.id in coches }.sumOf { it.signe }
    val releveValeur = solde.replace(',', '.').toDoubleOrNull()
    val ecart = releveValeur?.let { kotlin.math.round((it - total) * 100) / 100 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Titre("Rapprochement") }
        if (!d.peut("rapprocher")) item {
            BlocInfo("Consultation$NBSP: le rapprochement est fait par le trésorier. Il compare chaque mois les opérations enregistrées au relevé de la banque et au comptage de la caisse$NBSP; une période rapprochée est verrouillée." +
                if (historique.isEmpty()) " Aucun rapprochement n’a encore été terminé." else "")
        }
        if (d.peut("rapprocher")) {
            item {
                CarteBlanche {
                    ChoixListe("Compte", compte?.nom ?: "", d.comptes.map { it.nom }) { compte = d.comptes[it]; solde = ""; releve = null }
                    OutlinedTextField(solde, { solde = it }, label = { Text(obligatoire("Solde du relevé à la fin (€)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ds = debutSaisi ?: dateFr(debutDefaut)
                        OutlinedTextField(ds, { debutSaisi = it }, label = { Text("Début") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dateDepuisFr(ds) == null, modifier = Modifier.weight(1f))
                        OutlinedTextField(finSaisie, { finSaisie = it }, label = { Text("Fin") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dateDepuisFr(finSaisie) == null, modifier = Modifier.weight(1f))
                    }
                    Text(obligatoire(if (compte?.type == "caisse") "Procès-verbal de comptage" else "Relevé de la période"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = choix, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Choisir une photo ou un PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Nom d'origine du fichier choisi, en puce bleue (puce-ok) comme le site
                    releve?.let { Puce(it.nom ?: "Fichier joint (${it.ko}$NBSP" + "Ko)", Couleurs.BleuClair, Couleurs.SurBleuClair, Color(0xFF9CCBEA)) }
                }
            }
            item {
                CarteBlanche {
                    Text("Écritures à pointer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val l = ecritures
                    if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (l.isEmpty()) Text("Tout est rapproché.", color = Couleurs.Texte2)
                    else Column {
                        l.forEachIndexed { i, e ->
                            Row(Modifier.fillMaxWidth().clickable { if (e.id in coches) coches.remove(e.id) else coches.add(e.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(e.id in coches, { if (it) coches.add(e.id) else coches.remove(e.id) })
                                Column(Modifier.weight(1f)) {
                                    Text(e.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dateFr(e.date) + if (e.date < debut) " · période précédente" else "", fontSize = 13.sp, color = Couleurs.Texte2)
                                }
                                val v = e.signe
                                Text((if (v >= 0) "+ " else "− ") + euros(kotlin.math.abs(v)), color = if (v >= 0) Couleurs.Bleu else Couleurs.Orange, fontWeight = FontWeight.SemiBold)
                            }
                            if (i < l.lastIndex) HorizontalDivider(color = Color(0xFFE8E3E1))
                        }
                    }
                }
            }
            item {
                CarteBlanche {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Relevé", releveValeur?.let { euros(it) } ?: "–", Couleurs.Fond),
                            Triple("Pointé", euros(total), Couleurs.Fond),
                            Triple("Écart", ecart?.let { euros(it) } ?: "–", if (ecart == 0.0) Couleurs.BleuClair else Couleurs.JauneClair),
                        ).forEach { (t, v, fond) ->
                            Surface(color = fond, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(t, fontSize = 13.sp, color = Couleurs.Texte2)
                                    Text(v, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    Text(when {
                        releve == null -> "Joignez le relevé pour terminer."
                        ecart == null -> "Saisissez le solde du relevé."
                        ecart != 0.0 -> "Écart non nul : cochez ou décochez des écritures, ou saisissez celle qui manque."
                        else -> "Écart nul. La période sera verrouillée."
                    }, fontSize = 14.sp, color = Couleurs.Texte2)
                    Button(enabled = !enCours && releve != null && ecart == 0.0 && dateDepuisFr(finSaisie) != null && debut <= fin, modifier = Modifier.align(Alignment.End), onClick = {
                        val c = compte ?: return@Button
                        enCours = true
                        scope.launch {
                            try {
                                Repo.terminerRapprochement(c.id, debut, fin, releveValeur!!, releve!!, releve!!.nom ?: "releve-${c.type}-$fin.${releve!!.extension}", coches.toList())
                                message("Rapprochement terminé, période verrouillée"); solde = ""; releve = null; debutSaisi = null; version++
                            } catch (e: Exception) { message(traduireErreur(e)) }
                            enCours = false
                        }
                    }) { Text("Terminer le rapprochement") }
                }
            }
        }
        item {
            CarteBlanche {
                Text("Historique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (historique.isEmpty()) Text("Aucun rapprochement terminé.", color = Couleurs.Texte2)
                else Column {
                    historique.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(d.comptes.firstOrNull { it.id == r.compteId }?.nom ?: "", fontWeight = FontWeight.SemiBold)
                                Text("${dateFr(r.debut)} au ${dateFr(r.fin)}", fontSize = 13.sp, color = Couleurs.Texte2)
                                if (r.termineLe != null) Text("Terminé le ${dateFr(r.termineLe.take(10))}", fontSize = 13.sp, color = Couleurs.Texte2)
                                else Puce("En cours", Couleurs.JauneClair, Couleurs.SurJaune, Color(0xFFF2C66A))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(euros(r.soldeReleve), fontWeight = FontWeight.SemiBold)
                                if (r.releve != null) TextButton(onClick = { apercu = r.releve }) { Text("Voir") }
                            }
                        }
                        if (i < historique.lastIndex) HorizontalDivider(color = Color(0xFFE8E3E1))
                    }
                }
            }
        }
    }
    apercu?.let { chemin ->
        ModalBottomSheet(onDismissRequest = { apercu = null }) { FeuilleApercu("releves", chemin) { apercu = null } }
    }
}
