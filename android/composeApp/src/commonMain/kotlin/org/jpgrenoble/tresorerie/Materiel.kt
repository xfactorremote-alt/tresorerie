package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================
// Matériel : même conception que le site. Inventaire des instruments, de la
// sonorisation, de l'informatique, des tenues ; valeurs, lieu, membre qui l'a
// en main, vérification annuelle, sortie (vendu, perdu, volé…), historique.
// =====================================================================
val CATEGORIES_MATERIEL = linkedMapOf("instrument" to "Instruments de musique", "sonorisation" to "Sonorisation et éclairage", "informatique" to "Informatique et vidéo",
    "mobilier" to "Mobilier", "textile" to "Tenues et textiles", "cuisine" to "Cuisine et réception", "autre" to "Autres")
val ETATS_MATERIEL = linkedMapOf("neuf" to "Neuf", "bon" to "Bon état", "usage" to "Usé", "a_reparer" to "À réparer", "hors_service" to "Hors service")
val ORIGINES_MATERIEL = linkedMapOf("achat" to "Acheté", "don" to "Reçu en don", "pret" to "Prêté par un tiers")
val MOTIFS_SORTIE = linkedMapOf("vendu" to "Vendu", "donne" to "Donné", "perdu" to "Perdu", "vole" to "Volé", "detruit" to "Détruit ou jeté", "rendu" to "Rendu au propriétaire")
private val MOUVEMENTS = mapOf("entree" to "Entrée à l’inventaire", "pret" to "Prêté", "retour" to "Rendu", "reparation" to "Réparation", "inventaire" to "Vérifié",
    "sortie" to "Sorti de l’inventaire", "modification" to "Fiche modifiée")

// Non vérifié depuis plus d'un an : à contrôler avant l'assemblée générale
fun aVerifier(x: Materiel) = x.sortiLe == null && (x.verifieLe == null || aujourdhui().toEpochDays() - kotlinx.datetime.LocalDate.parse(x.verifieLe.take(10)).toEpochDays() > 365)

// Filtre gardé pendant la session (comme S.filtreMateriel du site) : retrouver « Sortis » en revenant sur l'onglet
private var filtreMaterielMemo = "service"

@Composable
private fun couleursEtat(etat: String): Pair<Color, Color> = when (etat) {
    "neuf", "bon" -> Couleurs.BleuClair to Couleurs.SurBleuClair
    "a_reparer" -> Couleurs.JauneClair to Couleurs.SurJaune
    "hors_service" -> Couleurs.ErreurClair to Couleurs.SurErreurClair
    else -> Couleurs.Surface2 to Couleurs.Texte2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranMateriel(d: Donnees, message: (String) -> Unit, pre: NouveauMateriel? = null) {
    var items by remember { mutableStateOf<List<Materiel>?>(null) }
    var version by remember { mutableStateOf(0) }
    var filtre by remember { mutableStateOf(filtreMaterielMemo) }
    var recherche by remember { mutableStateOf("") }
    var fiche by remember { mutableStateOf<Materiel?>(null) }
    var formulaire by remember { mutableStateOf<Pair<Materiel?, NouveauMateriel?>?>(if (pre != null) null to pre else null) }
    var exporter by remember { mutableStateOf(false) }
    val gere = d.peut("gerer_materiel")
    val scope = rememberCoroutineScope()
    val imprimer = rememberImpression()
    val enregistrer = rememberEnregistrer { it?.let(message) }
    // Suppression tenue par l'écran (pas par la fiche, qui se ferme) : « Annuler » de la barre reste actif et recharge la liste
    val supprimerMat = rememberSuppression(message) { fiche = null; version++ }
    LaunchedEffect(version, Synchro.version) { try { items = Repo.materiel() } catch (e: Exception) { message(traduireErreur(e)) } }
    val tous = items.orEmpty()
    val enService = tous.filter { it.sortiLe == null }
    val filtres = linkedMapOf(
        "service" to ("En service" to enService), "pretes" to ("Chez un membre" to enService.filter { it.detenteurId != null }),
        "reparer" to ("À réparer" to enService.filter { it.etat in listOf("a_reparer", "hors_service") }),
        "verifier" to ("À vérifier" to enService.filter(::aVerifier)), "sortis" to ("Sortis" to tous.filter { it.sortiLe != null }),
    )
    val q = sansAccentsCode(recherche)
    val vus = filtres[filtre]!!.second.filter { q.isEmpty() || sansAccentsCode("${it.designation} ${it.marque ?: ""} ${it.numeroSerie ?: ""}").contains(q) }
    val propres = enService.filter { it.origine != "pret" }
    val valAchat = propres.sumOf { it.valeurAcquisition ?: 0.0 }
    val valActuelle = propres.sumOf { it.valeurActuelle ?: it.valeurAcquisition ?: 0.0 }
    val nbArticles = enService.sumOf { it.quantite }
    fun nomMembre(id: String?) = d.membres.firstOrNull { it.id == id }?.nomComplet ?: "un membre"

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Titre("Matériel") { OutlinedButton(onClick = { exporter = true }) { Text("Exporter") } } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val nbVerifier = filtres["verifier"]!!.second.size
                    KpiMateriel("Articles en service", "$nbArticles", "${enService.size} ligne${if (enService.size > 1) "s" else ""} d’inventaire", Modifier.weight(1f))
                    KpiMateriel("Valeur d’achat", euros0(valAchat), "dons compris, hors prêts", Modifier.weight(1f))
                    KpiMateriel("Valeur actuelle estimée", euros0(valActuelle), if (nbVerifier > 0) "$nbVerifier à vérifier" else "Inventaire à jour", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtres.forEach { (k, v) -> FilterChip(selected = filtre == k, onClick = { filtre = k; filtreMaterielMemo = k }, label = { Text("${v.first} (${v.second.size})") }) }
                }
            }
            if (tous.isNotEmpty()) item {
                OutlinedTextField(recherche, { recherche = it }, placeholder = { Text("Rechercher : guitare, micro, numéro de série…") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth())
            }
            if (items == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (vus.isEmpty()) item {
                CarteBlanche {
                    if (tous.isEmpty()) {
                        Text("Aucun matériel inscrit.", fontWeight = FontWeight.Bold)
                        if (gere) {
                            Text("Instruments, sonorisation, informatique, tenues : inscrivez chaque bien de l’association, avec sa valeur et son lieu de rangement.", color = Couleurs.Texte2)
                        }
                    } else Text("Aucun article dans cette vue.", color = Couleurs.Texte2)
                }
            }
            CATEGORIES_MATERIEL.forEach { (c, nom) ->
                val l = vus.filter { it.categorie == c }
                if (l.isNotEmpty()) item(key = c) {
                    CarteBlanche {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${l.sumOf { it.quantite }}", style = MaterialTheme.typography.titleLarge, color = Couleurs.Texte2)
                        }
                        l.forEach { x -> LigneMateriel(x, nomMembre(x.detenteurId)) { fiche = x } }
                    }
                }
            }
            if (gere && tous.isNotEmpty()) item {
                Text("Une fois par an, avant l’assemblée générale, vérifiez chaque article sur place et touchez « Vérifié ». L’inventaire PDF se signe et se joint au rapport.",
                    fontSize = 13.sp, color = Couleurs.Texte2)
            }
        }
        if (gere) BoutonPlus("Ajouter un article", Modifier.align(Alignment.BottomEnd)) { formulaire = null to null }
    }
    fiche?.let { x ->
        ModalBottomSheet(onDismissRequest = { fiche = null }) {
            FicheMateriel(d, x, message, onModifier = { fiche = null; formulaire = x to null }, onSupprimer = { supprimerMat("materiel", x.id, x.designation) }) { change -> fiche = null; if (change) version++ }
        }
    }
    formulaire?.let { (x, p) ->
        ModalBottomSheet(onDismissRequest = { formulaire = null }) {
            FormulaireMateriel(x, p, message) { ok -> formulaire = null; if (ok) { message(if (x == null) "Article inscrit à l’inventaire" else "Article modifié"); version++ } }
        }
    }
    if (exporter) AlertDialog(
        onDismissRequest = { exporter = false },
        title = { Text("Exporter l’inventaire") },
        text = { Text("PDF : mis en page pour imprimer, archiver ou transmettre. Excel : tableau modifiable (CSV).") },
        confirmButton = {
            Button(onClick = { exporter = false; scope.launch { try { val (t, h) = documentHtml(d, "inventaire", aujourdhui().year); imprimer(t, h) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("PDF") }
        },
        dismissButton = {
            OutlinedButton(onClick = { exporter = false; scope.launch { try { enregistrer("inventaire-materiel-${aujourdhui()}.csv", "text/csv", csvInventaire(d, tous).encodeToByteArray()) } catch (e: Exception) { message(traduireErreur(e)) } } }) { Text("Excel") }
        },
    )
}

// Chiffre clé avec sa ligne d'explication, comme les cartes .kpis du site
@Composable
private fun KpiMateriel(titre: String, valeur: String, sous: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = Formes.XGrand, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(titre, fontSize = 12.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(valeur, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Couleurs.Texte, maxLines = 1)
            Text(sous, fontSize = 11.sp, color = Couleurs.Texte2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Photos du matériel (seau privé « photos ») : gardées en mémoire pour la session, comme les URL signées S.photos du site ;
// décodées en taille réduite (une photo d'appareil de 12 Mpx ne sature pas la mémoire) ; nouvel essai en cas d'échec réseau.
private object CacheMateriel { val images = mutableMapOf<String, ImageBitmap>() }

@Composable
private fun rememberPhotoMateriel(chemin: String?, max: Int): ImageBitmap? {
    val cle = chemin?.let { "$it@$max" }
    var image by remember(cle) { mutableStateOf(cle?.let { CacheMateriel.images[it] }) }
    LaunchedEffect(cle, Synchro.version) {
        if (chemin == null || cle == null || image != null) return@LaunchedEffect
        for (essai in 1..3) {
            val octets = try { Repo.telecharger("photos", chemin) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("Matériel : photo $chemin non téléchargée (essai $essai) : ${e.message}"); null
            }
            val img = octets?.let { imageDepuisOctetsReduite(it, max) }
            if (img != null) {
                CacheMateriel.images[cle] = img
                if (CacheMateriel.images.size > 80) CacheMateriel.images.remove(CacheMateriel.images.keys.first())
                image = img; return@LaunchedEffect
            }
            if (octets != null) { println("Matériel : photo $chemin illisible (${octets.size} octets)"); return@LaunchedEffect }
            delay(1500L * essai)
        }
    }
    return image
}

// Vignette carrée de la liste et du formulaire : la photo, sinon l'initiale (comme .avatar-carre du site)
@Composable
private fun VignetteMateriel(chemin: String?, designation: String, taille: Int = 44, apercu: ImageBitmap? = null) {
    val enLigne = rememberPhotoMateriel(chemin, 160)
    val img = apercu ?: enLigne
    Box(Modifier.size(taille.dp).clip(Formes.Moyen).background(Couleurs.Surface2), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(designation.take(1).uppercase().ifBlank { "–" }, fontWeight = FontWeight.Bold, color = Couleurs.Texte2)
    }
}

// Grande photo en tête de la fiche : toute la largeur, 240 dp de haut au plus (.photo-materiel du site)
@Composable
private fun GrandePhotoMateriel(chemin: String?) {
    val img = rememberPhotoMateriel(chemin, 1200) ?: return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hauteur = minOf(maxWidth * (img.height.toFloat() / img.width.coerceAtLeast(1)), 240.dp)
        Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(hauteur).clip(Formes.Moyen))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LigneMateriel(x: Materiel, detenteur: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Formes.Moyen).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VignetteMateriel(x.photo, x.designation)
        Column(Modifier.weight(1f)) {
            Text(x.designation + if (x.quantite > 1) "  × ${x.quantite}" else "", fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(x.marque, x.lieu).joinToString(" · ").ifBlank { ORIGINES_MATERIEL[x.origine] ?: "" }, fontSize = 13.sp, color = Couleurs.Texte2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (x.sortiLe != null) Puce("${MOTIFS_SORTIE[x.motifSortie]} le ${dateFr(x.sortiLe)}", Couleurs.Surface2, Couleurs.Texte2)
                else {
                    if (x.etat !in listOf("bon", "neuf")) couleursEtat(x.etat).let { (f, c) -> Puce(ETATS_MATERIEL[x.etat] ?: x.etat, f, c) }
                    if (x.detenteurId != null) Puce("Chez $detenteur", Couleurs.JauneClair, Couleurs.SurJaune)
                    if (x.origine == "pret") Puce("Prêt d’un tiers", Couleurs.Surface2, Couleurs.Texte2)
                    if (aVerifier(x)) Puce("À vérifier", Couleurs.Surface2, Couleurs.Texte2)
                }
            }
        }
        if (x.origine != "pret") (x.valeurActuelle ?: x.valeurAcquisition)?.let { Text(euros(it), fontWeight = FontWeight.SemiBold) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FicheMateriel(d: Donnees, x: Materiel, message: (String) -> Unit, onModifier: () -> Unit, onSupprimer: () -> Unit, onFini: (Boolean) -> Unit) {
    // Saisi par erreur : retiré de la liste (réversible, onSupprimer) ; un article réel qui part se « sort » de l'inventaire
    val scope = rememberCoroutineScope()
    val gere = d.peut("gerer_materiel")
    var mvts by remember { mutableStateOf<List<MouvementMateriel>>(emptyList()) }
    var action by remember { mutableStateOf<String?>(null) }
    var membre by remember { mutableStateOf<Membre?>(null) }
    var notes by remember { mutableStateOf("") }
    var motif by remember { mutableStateOf("vendu") }
    var date by remember { mutableStateOf(dateFr(aujourdhui().toString())) }
    var achat by remember { mutableStateOf<Ecriture?>(null) }
    LaunchedEffect(x.id) { try { mvts = Repo.mouvementsMateriel(x.id) } catch (_: Exception) { } }
    LaunchedEffect(x.transactionId) { if (x.transactionId != null && d.peut("consulter_finances")) try { achat = Repo.ecritureParId(x.transactionId) } catch (_: Exception) { } }
    fun nomMembre(id: String?) = d.membres.firstOrNull { it.id == id }?.nomComplet ?: ""
    fun faire(msg: String, bloc: suspend () -> Unit) = scope.launch { try { bloc(); message(msg); onFini(true) } catch (e: Exception) { message(traduireErreur(e)) } }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GrandePhotoMateriel(x.photo)
        Row(verticalAlignment = Alignment.Top) {
            Text(x.designation + if (x.quantite > 1) " × ${x.quantite}" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            couleursEtat(x.etat).let { (f, c) -> Puce(ETATS_MATERIEL[x.etat] ?: x.etat, f, c) }
        }
        LigneInfo("Catégorie", CATEGORIES_MATERIEL[x.categorie])
        LigneInfo("Marque et modèle", x.marque)
        LigneInfo("N° de série", x.numeroSerie)
        LigneInfo("Origine", (ORIGINES_MATERIEL[x.origine] ?: "") + (x.dateAcquisition?.let { " le ${dateFr(it)}" } ?: ""))
        if (x.origine != "pret") { LigneInfo("Valeur d’achat", x.valeurAcquisition?.let(::euros)); LigneInfo("Valeur actuelle", x.valeurActuelle?.let(::euros)) }
        LigneInfo("Rangé", x.lieu)
        LigneInfo("Chez", x.detenteurId?.let(::nomMembre))
        LigneInfo("Dernière vérification", x.verifieLe?.let(::dateFr) ?: "jamais")
        x.sortiLe?.let { LigneInfo("Sorti", "${MOTIFS_SORTIE[x.motifSortie]} le ${dateFr(it)}") }
        x.notes?.let { Text(it, fontSize = 14.sp) }
        achat?.let { a ->
            Surface(color = Color(0xFFEAF4FB), shape = Formes.XGrand, modifier = Modifier.fillMaxWidth()) {
                Text("Achat enregistré le ${dateFr(a.date)} : ${a.libelle}, ${euros(a.montant)}", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        if (mvts.isNotEmpty()) {
            Text("Historique", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            mvts.forEach { m ->
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${MOUVEMENTS[m.type] ?: m.type}${m.membreId?.let { " : ${nomMembre(it)}" } ?: ""}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(dateFr(m.date) + (m.notes?.let { " · $it" } ?: ""), fontSize = 13.sp, color = Couleurs.Texte2)
                }
            }
        }
        when (action) {
            "preter" -> CarteBlanche {
                Text("Confier à un membre", fontWeight = FontWeight.Bold)
                val actifs = d.membres.filter { it.actif }
                ChoixListe("Membre", membre?.nomComplet ?: "Choisir", actifs.map { it.nomComplet }) { membre = actifs[it] }
                OutlinedTextField(notes, { notes = it.take(120) }, label = { Text("Motif") }, placeholder = { Text("Répétitions, concert du 12 juin…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = membre != null, onClick = { faire("Article confié") { Repo.confierMateriel(x.id, membre!!.id, notes.trim().ifBlank { null }) } }) { Text("Enregistrer") }
            }
            "sortir" -> CarteBlanche {
                Text("Sortir de l’inventaire", fontWeight = FontWeight.Bold)
                val motifs = MOTIFS_SORTIE.filterKeys { x.origine == "pret" || it != "rendu" }
                ChoixListe("Motif", motifs[motif] ?: "", motifs.values.toList()) { motif = motifs.keys.toList()[it] }
                val dt = dateDepuisFr(date)
                ChampDate(date, { date = it }, label = { Text("Date") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = dt == null, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(160) }, label = { Text("Précision") }, placeholder = { Text("Vendu 80 € à…, déclaration de vol du…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("L’article reste dans l’historique. Une vente s’enregistre aussi en recette dans Opérations.", fontSize = 13.sp, color = Couleurs.Texte2)
                Button(enabled = dt != null, onClick = { faire("Article sorti de l’inventaire") { Repo.sortirMateriel(x.id, dt.toString(), motif, listOfNotNull(MOTIFS_SORTIE[motif], notes.trim().ifBlank { null }).joinToString(" : ")) } }) { Text("Confirmer la sortie") }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (gere && x.sortiLe == null) {
                if (x.detenteurId != null) FilledTonalButton(onClick = { faire("Retour enregistré") { Repo.confierMateriel(x.id, null, null) } }) { Text("Récupéré") }
                else FilledTonalButton(onClick = { action = "preter" }) { Text("Confier à un membre") }
                FilledTonalButton(onClick = { faire("Vérification enregistrée") { Repo.verifierMateriel(x.id) } }) { Text("Vérifié") }
                TextButton(onClick = onModifier) { Text("Modifier") }
                TextButton(onClick = { action = "sortir" }) { Text("Sortir") }
            }
            if (gere) BoutonSupprimer(onSupprimer)
            Button(onClick = { onFini(false) }) { Text("Fermer") }
        }
    }
}

@Composable
fun FormulaireMateriel(x: Materiel?, pre: NouveauMateriel?, message: (String) -> Unit, onFini: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var designation by remember { mutableStateOf(x?.designation ?: pre?.designation ?: "") }
    var categorie by remember { mutableStateOf(x?.categorie ?: pre?.categorie ?: "instrument") }
    var quantite by remember { mutableStateOf((x?.quantite ?: pre?.quantite ?: 1).toString()) }
    var marque by remember { mutableStateOf(x?.marque ?: "") }
    var serie by remember { mutableStateOf(x?.numeroSerie ?: "") }
    var origine by remember { mutableStateOf(x?.origine ?: pre?.origine ?: "achat") }
    var date by remember { mutableStateOf((x?.dateAcquisition ?: pre?.dateAcquisition ?: aujourdhui().toString()).let(::dateFr)) }
    var valeur by remember { mutableStateOf((x?.valeurAcquisition ?: pre?.valeurAcquisition)?.let(::montantSaisie) ?: "") }
    var actuelle by remember { mutableStateOf((x?.valeurActuelle ?: pre?.valeurActuelle)?.let(::montantSaisie) ?: "") }
    var etat by remember { mutableStateOf(x?.etat ?: pre?.etat ?: "bon") }
    var lieu by remember { mutableStateOf(x?.lieu ?: "") }
    var notes by remember { mutableStateOf(x?.notes ?: "") }
    var photo by remember { mutableStateOf<Fichier?>(null) }
    var enCours by remember { mutableStateOf(false) }
    val choix = rememberChoixFichier(pdfAccepte = false) { f, err -> if (err != null) message(err); if (f != null) photo = f }
    val appareil = rememberPrendrePhoto { f, err -> if (err != null) message(err); if (f != null) photo = f }
    val apercu = remember(photo) { photo?.let { imageDepuisOctetsReduite(it.octets, 160) } }
    val dt = if (date.isBlank()) null else dateDepuisFr(date)
    val qte = quantite.toIntOrNull()?.takeIf { it > 0 }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (x == null) "Nouvel article" else "Modifier l’article", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(designation, { designation = it.take(80) }, label = { Text("Désignation") }, placeholder = { Text("Guitare basse, enceinte, vidéoprojecteur…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // Même ordre que le site : deux par ligne (Catégorie, Quantité ; Marque, N° de série ; Origine, Date ; Valeurs ; État, Lieu)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoixListe("Catégorie", CATEGORIES_MATERIEL[categorie] ?: "", CATEGORIES_MATERIEL.values.toList()) { categorie = CATEGORIES_MATERIEL.keys.toList()[it] } }
            OutlinedTextField(quantite, { quantite = it }, label = { Text("Quantité") }, singleLine = true, isError = qte == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(marque, { marque = it.take(80) }, label = { Text("Marque et modèle") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(serie, { serie = it.take(60) }, label = { Text("N° de série") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoixListe("Origine", ORIGINES_MATERIEL[origine] ?: "", ORIGINES_MATERIEL.values.toList()) { origine = ORIGINES_MATERIEL.keys.toList()[it] } }
            ChampDate(date, { date = it }, label = { Text("Date d’entrée") }, placeholder = { Text("JJ/MM/AAAA") }, singleLine = true, isError = date.isNotBlank() && dt == null, modifier = Modifier.weight(1f))
        }
        if (origine != "pret") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(valeur, { valeur = it }, label = { Text("Valeur d’achat ou du don") }, suffix = { Text("€") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(actuelle, { actuelle = it }, label = { Text("Valeur actuelle estimée") }, placeholder = { Text("Prix d’occasion") }, suffix = { Text("€") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ChoixListe("État", ETATS_MATERIEL[etat] ?: "", ETATS_MATERIEL.values.toList()) { etat = ETATS_MATERIEL.keys.toList()[it] } }
            OutlinedTextField(lieu, { lieu = it.take(80) }, label = { Text("Lieu de rangement") }, placeholder = { Text("Église, local du fond") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("Notes") }, placeholder = { Text("Accessoires, propriétaire en cas de prêt, garantie…") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        // Photo : aperçu (photo actuelle, nouvelle photo ou initiale), galerie ou appareil photo (le site ouvre l'appareil sur téléphone : capture="environment")
        Text("Photo", fontSize = 13.sp, color = Couleurs.Texte2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VignetteMateriel(x?.photo, designation, 56, apercu)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = appareil) { Text("Prendre une photo") }
                OutlinedButton(onClick = choix) { Text("Choisir une photo") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { onFini(false) }) { Text("Annuler") }
            Button(enabled = !enCours && designation.isNotBlank() && qte != null && (date.isBlank() || dt != null), onClick = {
                enCours = true
                scope.launch {
                    try {
                        val pret = origine == "pret"
                        Repo.enregistrerMateriel(x?.id, NouveauMateriel(designation.trim(), categorie, marque.trim().ifBlank { null }, serie.trim().ifBlank { null }, qte!!, origine,
                            dt?.toString(), if (pret) null else lireMontant(valeur), if (pret) null else lireMontant(actuelle), etat, lieu.trim().ifBlank { null },
                            notes.trim().ifBlank { null }, if (x == null) pre?.transactionId else null), photo)
                        onFini(true)
                    } catch (e: Exception) { enCours = false; message(traduireErreur(e)) }
                }
            }) { Text("Enregistrer") }
        }
    }
}

// Export Excel (CSV) de l'inventaire, mêmes colonnes que le site
fun csvInventaire(d: Donnees, items: List<Materiel>): String {
    fun cel(v: Any?): String { val s = when (v) { is Double -> v.toString().replace('.', ','); null -> ""; else -> v.toString() }; return if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s }
    val entetes = listOf("Désignation", "Catégorie", "Marque et modèle", "N° de série", "Quantité", "Origine", "Acquis le", "Valeur d’achat", "Valeur actuelle", "État", "Lieu", "Chez", "Vérifié le", "Sorti le", "Motif")
    val lignes = items.map { x -> listOf(x.designation, CATEGORIES_MATERIEL[x.categorie], x.marque, x.numeroSerie, x.quantite, ORIGINES_MATERIEL[x.origine], x.dateAcquisition?.let(::dateFr),
        x.valeurAcquisition, x.valeurActuelle, ETATS_MATERIEL[x.etat], x.lieu, x.detenteurId?.let { id -> d.membres.firstOrNull { it.id == id }?.nomComplet },
        x.verifieLe?.let(::dateFr), x.sortiLe?.let(::dateFr), x.motifSortie?.let { MOTIFS_SORTIE[it] }) }
    return "﻿" + (listOf(entetes) + lignes).joinToString("\r\n") { l -> l.joinToString(";") { cel(it) } }
}
