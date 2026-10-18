package org.jpgrenoble.tresorerie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Membres : filtrer, regrouper, répartition (comme filtrerMembres / groupesMembres / repartition du site, mêmes noms).
// Origines, pupitres, statuts : regroupés sans tenir compte des majuscules ni des accents ; le libellé affiché est l'écriture la plus fréquente.

val CRITERES_MEMBRES = linkedMapOf("pupitre" to ("Pupitre" to "Pupitres"), "nationalite" to ("Nationalité" to "Nationalités"), "statut" to ("Statut" to "Statuts"))

fun cleValeur(s: String?) = sansAccents(s ?: "").replace(Regex("\\s+"), " ")
private fun valeur(m: Membre, critere: String) = when (critere) { "pupitre" -> m.pupitre; "nationalite" -> m.nationalite; else -> m.statut }

/** Choix d'affichage de la liste (gardé pendant la session, comme S.vueMembres du site). */
data class VueMembres(val etat: String = "tous", val groupe: String = "fonction", val tri: String = "nom",
                      val valeurs: Map<String, Set<String>> = emptyMap()) {
    fun choisis(critere: String) = valeurs[critere].orEmpty()
}

fun filtrerMembres(membres: List<Membre>, v: VueMembres) = membres.filter { m ->
    (v.etat == "tous" || (v.etat == "actifs") == m.actif) && CRITERES_MEMBRES.keys.all { k -> v.choisis(k).isEmpty() || cleValeur(valeur(m, k)) in v.choisis(k) }
}

fun trierMembres(l: List<Membre>, tri: String) = if (tri == "prenom") l.sortedWith(compareBy({ sansAccents(it.prenom) }, { sansAccents(it.nom) }))
    else l.sortedWith(compareBy({ sansAccents(it.nom.ifBlank { it.prenom }) }, { sansAccents(it.prenom) }))

class GroupeMembres(val cle: String, val libelle: String, val membres: List<Membre>)

/** Groupes d'un critère : les pupitres dans l'ordre du chœur, les autres du plus nombreux au moins nombreux ; « Non renseigné » en dernier. */
fun groupesMembres(membres: List<Membre>, critere: String): List<GroupeMembres> {
    val g = linkedMapOf<String, MutableList<Membre>>()
    membres.forEach { m -> g.getOrPut(cleValeur(valeur(m, critere))) { mutableListOf() } += m }
    fun libelle(k: String, l: List<Membre>) = if (k.isEmpty()) "Non renseigné"
        else l.groupingBy { valeur(it, critere)!!.trim() }.eachCount().entries
            .sortedWith(compareBy({ -it.value }, { if (it.key.first().isLowerCase()) 1 else 0 }, { it.key })).first().key   // l'écriture avec majuscule l'emporte à égalité
    fun ordre(k: String) = if (critere == "pupitre") PUPITRES.indexOfFirst { cleValeur(it) == k } else -1
    return g.map { (k, l) -> GroupeMembres(k, libelle(k, l), l) }.sortedWith(compareBy<GroupeMembres>({ it.cle.isEmpty() }, { ordre(it.cle) < 0 }, { ordre(it.cle) },
        { -it.membres.size }, { sansAccents(it.libelle) }))
}

class PartRepartition(val cle: String, val libelle: String, val n: Int, val part: Double)

/** Répartition : effectif et part (au dixième) de chaque valeur, « Non renseigné » compté à part. */
fun repartition(membres: List<Membre>, critere: String): List<PartRepartition> {
    val total = maxOf(1, membres.size)
    return groupesMembres(membres, critere).map { PartRepartition(it.cle, it.libelle, it.membres.size, (1000.0 * it.membres.size / total).roundToInt() / 10.0) }
}

fun pourcent(p: Double) = (if (p == p.toInt().toDouble()) p.toInt().toString() else p.toString().replace('.', ',')) + "$NBSP%"

/** Filtrer et regrouper (comme feuilleFiltresMembres du site) : appliqué avec « Voir N membres ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeuilleFiltresMembres(membres: List<Membre>, v0: VueMembres, onAppliquer: (VueMembres) -> Unit) {
    var v by remember { mutableStateOf(v0) }
    @Composable fun Segment(choix: List<Pair<String, String>>, valeur: String, onChoix: (String) -> Unit) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choix.forEachIndexed { i, (k, l) -> SegmentedButton(selected = valeur == k, onClick = { onChoix(k) }, shape = SegmentedButtonDefaults.itemShape(i, choix.size), icon = {}) { Text(l, maxLines = 1, fontSize = 13.sp) } }
        }
    }
    @Composable fun Titre(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filtrer et regrouper", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Titre("Regrouper par")
        Segment(listOf("fonction" to "Fonction", "pupitre" to "Pupitre", "nationalite" to "Nationalité", "statut" to "Statut"), v.groupe) { v = v.copy(groupe = it) }
        CRITERES_MEMBRES.forEach { (k, l) ->
            val gs = groupesMembres(membres, k)
            if (gs.size > 1 || gs.firstOrNull()?.cle?.isNotEmpty() == true) {
                Titre(l.first)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    gs.forEach { g ->
                        val actif = g.cle in v.choisis(k)
                        FilterChip(selected = actif, onClick = {
                            v = v.copy(valeurs = v.valeurs + (k to (if (actif) v.choisis(k) - g.cle else v.choisis(k) + g.cle)))
                        }, label = { Text("${g.libelle} ${g.membres.size}", maxLines = 1) })
                    }
                }
            }
        }
        Titre("État")
        Segment(listOf("tous" to "Tous", "actifs" to "Actifs", "inactifs" to "Inactifs"), v.etat) { v = v.copy(etat = it) }
        Titre("Trier par")
        Segment(listOf("nom" to "Nom", "prenom" to "Prénom"), v.tri) { v = v.copy(tri = it) }
        val n = filtrerMembres(membres, v).size
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = { v = VueMembres() }) { Text("Tout effacer") }
            Button(onClick = { onAppliquer(v) }) { Text("Voir $n membre${if (n > 1) "s" else ""}") }
        }
    }
}

/** Répartition des membres actifs (comme feuilleRepartition du site) : nationalités, pupitres, statuts ; groupes peu représentés. */
@Composable
fun FeuilleRepartition(membres: List<Membre>, onVoirPar: (String) -> Unit, onExporter: () -> Unit, onFermer: () -> Unit) {
    val actifs = membres.filter { it.actif }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Répartition des membres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${actifs.size} membre${if (actifs.size > 1) "s" else ""} actif${if (actifs.size > 1) "s" else ""}. Utile pour équilibrer les groupes (journées portes ouvertes, ateliers) et repérer les origines peu représentées.",
            fontSize = 13.sp, color = Couleurs.Texte2)
        var rien = true
        listOf("nationalite", "pupitre", "statut").forEach { k ->
            val r = repartition(actifs, k); val connus = r.filter { it.cle.isNotEmpty() }; val nr = r.firstOrNull { it.cle.isEmpty() }
            if (connus.isEmpty()) return@forEach
            rien = false
            val max = connus.maxOf { it.n }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(CRITERES_MEMBRES[k]!!.second, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("${connus.size} différent${if (connus.size > 1) "s" else ""}", fontSize = 13.sp, color = Couleurs.Texte2)
            }
            connus.forEach { x ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(x.libelle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(Formes.Rond).background(Couleurs.Surface3)) {
                            Box(Modifier.fillMaxWidth(x.n.toFloat() / max).fillMaxHeight().clip(Formes.Rond).background(Couleurs.Bleu))
                        }
                    }
                    Text("${x.n} · ${pourcent(x.part)}", fontSize = 13.sp, color = Couleurs.Texte2)
                    if (connus.size > 2 && x.part < 10) Puce("Peu représenté", Couleurs.JauneClair, Couleurs.SurJaune)
                }
                HorizontalDivider(color = Couleurs.BordDoux)
            }
            nr?.let { Text("Non renseigné : ${it.n} membre${if (it.n > 1) "s" else ""}", fontSize = 13.sp, color = Couleurs.Texte2) }
            TextButton(onClick = { onVoirPar(k) }) { Text("Voir la liste par ${CRITERES_MEMBRES[k]!!.first.lowercase()}") }
        }
        if (rien) Text("Aucune nationalité, aucun pupitre ni statut renseigné pour l’instant. Complétez les fiches ou importez la liste.", color = Couleurs.Texte2,
            modifier = Modifier.padding(vertical = 24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (!rien) OutlinedButton(onClick = onExporter) { Text("Exporter") }
            Button(onClick = onFermer) { Text("Fermer") }
        }
    }
}

/** Description des choix (même texte que libelleVue du site) : « Filtres : actifs, Soprane · regroupés par nationalité · triés par prénom ». */
fun libelleVue(membres: List<Membre>, v: VueMembres): String {
    val f = listOfNotNull(if (v.etat != "tous") v.etat else null) +
        CRITERES_MEMBRES.keys.flatMap { k -> v.choisis(k).map { x -> groupesMembres(membres, k).firstOrNull { it.cle == x }?.libelle ?: "Non renseigné" } }
    return listOfNotNull(if (f.isNotEmpty()) "Filtres : ${f.joinToString(", ")}" else null,
        if (v.groupe != "fonction") "regroupés par ${CRITERES_MEMBRES[v.groupe]!!.first.lowercase()}" else null,
        if (v.tri == "prenom") "triés par prénom" else null).joinToString(" · ")
}

/** Une section de la liste telle qu'affichée : titre (Bureau, Membres ou valeur du groupe), membres, part (groupes seulement). */
class SectionMembres(val titre: String, val membres: List<Membre>, val part: Double? = null)

/** Sections dans l'ordre de l'écran (comme sectionsMembres du site) ; rangFonction : rang du rôle de bureau, null pour un simple membre. */
fun sectionsMembres(membres: List<Membre>, v: VueMembres, rangFonction: (Membre) -> Int?): List<SectionMembres> {
    val l = trierMembres(filtrerMembres(membres, v), v.tri)
    if (v.groupe != "fonction") return groupesMembres(l, v.groupe).map { SectionMembres(it.libelle, it.membres, (1000.0 * it.membres.size / maxOf(1, l.size)).roundToInt() / 10.0) }
    val bureau = l.filter { rangFonction(it) != null }.sortedBy { rangFonction(it) }
    return listOf(SectionMembres("Bureau", bureau), SectionMembres("Membres", l - bureau.toSet())).filter { it.membres.isNotEmpty() }
}

private suspend fun fonctionsMembres(d: Donnees): (Membre) -> Pair<String, String>? {
    val profils = try { Repo.profilsComplets() } catch (_: Exception) { emptyList() }
    val invitations = try { Repo.invitations() } catch (_: Exception) { emptyList() }
    return { m -> profils.firstOrNull { it.memberId == m.id }?.let { it.role to "compte" } ?: invitations.firstOrNull { it.membreId == m.id }?.let { it.role to "invite" } }
}

/** Colonnes proposées à l'export des membres (même liste que COLONNES_EXPORT_MEMBRES du site) : donnée, titre du PDF,
 *  largeur relative, en-têtes du fichier Excel (ceux du modèle d'import), cochée par défaut. Le membre sort toujours. */
data class ColonneExport(val cle: String, val titre: String, val largeur: Int, val enTetes: List<String>, val defaut: Boolean)
val COLONNES_EXPORT_MEMBRES = listOf(
    ColonneExport("nom", "Membre", 18, listOf("Prénom", "Nom"), true), ColonneExport("fonction", "Fonction", 10, listOf("Fonction"), true),
    ColonneExport("pupitre", "Pupitre", 9, listOf("Pupitre"), true), ColonneExport("nationalite", "Nationalité", 13, listOf("Nationalité"), true),
    ColonneExport("statut", "Statut", 9, listOf("Statut"), true), ColonneExport("anniversaire", "Anniversaire", 10, listOf("Jour", "Mois"), true),
    ColonneExport("profession", "Profession", 12, listOf("Profession"), true), ColonneExport("whatsapp", "WhatsApp", 13, listOf("WhatsApp"), true),
    ColonneExport("email", "E-mail", 16, listOf("E-mail"), true), ColonneExport("adhesion", "Adhésion", 9, listOf("Adhésion"), true),
    ColonneExport("accord", "Accord anniversaire", 9, listOf("Accord"), false), ColonneExport("actif", "Actif", 7, listOf("Actif"), false))
val COLONNES_MEMBRES_DEFAUT = COLONNES_EXPORT_MEMBRES.filter { it.defaut }.map { it.cle }
/** Colonnes retenues dans l'ordre de la liste, le membre toujours en premier (comme colonnesExport du site). */
fun colonnesExport(choix: Collection<String>) = COLONNES_EXPORT_MEMBRES.filter { it.cle == "nom" || it.cle in choix }
/** Au-delà de 6 colonnes, la liste passe à l'italienne pour que les mots ne soient pas coupés (comme paysageMembres du site). */
fun paysageMembres(cols: List<ColonneExport>) = cols.size > 6
/** Largeurs en % : les colonnes retenues se partagent toute la page (comme largeursExport du site). */
fun largeursExport(cols: List<ColonneExport>): List<Int> { val t = cols.sumOf { it.largeur }; return cols.map { (100.0 * it.largeur / t).roundToInt() } }
/** Choix gardé sur l'appareil (« membres.colonnes », comme le site). */
fun colonnesMembresChoisies(): List<String> =
    lirePreference("membres.colonnes")?.let { t -> t.split(",").filter { k -> COLONNES_EXPORT_MEMBRES.any { it.cle == k } } } ?: COLONNES_MEMBRES_DEFAUT
fun garderColonnesMembres(l: List<String>) = garderPreference("membres.colonnes", l.joinToString(","))
/** En-têtes du fichier Excel : le groupe d'abord si la liste est regroupée, puis les colonnes choisies. */
fun enTetesCsvMembres(v: VueMembres, choix: Collection<String>) =
    (if (v.groupe != "fonction") listOf("Groupe (${CRITERES_MEMBRES[v.groupe]!!.first.lowercase()})") else emptyList()) + colonnesExport(choix).flatMap { it.enTetes }

/** CSV de ce qui est affiché, avec les colonnes choisies (comme csvMembres du site) ; le fichier se réimporte tel quel. */
suspend fun csvMembres(d: Donnees, membres: List<Membre>, v: VueMembres, choix: Collection<String> = colonnesMembresChoisies()): String {
    val fonction = fonctionsMembres(d)
    val groupe = v.groupe != "fonction"
    val rang = { m: Membre -> fonction(m)?.first?.takeIf { it != "adherent" }?.let { rangRole(it) } }
    val cols = colonnesExport(choix)
    return csv(enTetesCsvMembres(v, choix),
        sectionsMembres(membres, v, rang).flatMap { s -> s.membres.map { m ->
            (if (groupe) listOf<Any?>(s.titre) else emptyList()) + cols.flatMap { c -> when (c.cle) {
                "nom" -> listOf<Any?>(m.prenom, m.nom); "fonction" -> listOf(fonction(m)?.first?.let { d.nomRole(it) } ?: "")
                "pupitre" -> listOf(m.pupitre); "nationalite" -> listOf(m.nationalite); "statut" -> listOf(m.statut)
                "anniversaire" -> listOf(m.jour, m.mois); "profession" -> listOf(m.profession); "whatsapp" -> listOf(m.whatsapp)
                "email" -> listOf(m.email); "adhesion" -> listOf(m.adhesion?.take(10) ?: "")
                "accord" -> listOf(if (m.consentement) "Oui" else "Non"); else -> listOf(if (m.actif) "Oui" else "Non") } }
        } })
}

/** CSV de la répartition (comme csvRepartition du site). */
fun csvRepartition(membres: List<Membre>): String {
    val actifs = membres.filter { it.actif }
    return csv(listOf("Critère", "Valeur", "Effectif", "Part (%)"),
        listOf("nationalite", "pupitre", "statut").flatMap { k -> repartition(actifs, k).map { listOf<Any?>(CRITERES_MEMBRES[k]!!.first, it.libelle, it.n, it.part) } })
}

/** Liste imprimable de ce qui est affiché (comme pdfMembres du site). */
suspend fun documentMembres(d: Donnees, membres: List<Membre>, v: VueMembres, choix: Collection<String> = colonnesMembresChoisies()): Pair<String, String> {
    val fonction = fonctionsMembres(d)
    val rang = { m: Membre -> fonction(m)?.first?.takeIf { it != "adherent" }?.let { rangRole(it) } }
    val sections = sectionsMembres(membres, v, rang); val n = sections.sumOf { it.membres.size }; val lib = libelleVue(membres, v)
    // Colonnes choisies, largeurs fixes (mêmes positions d'une section à l'autre)
    val cols = colonnesExport(choix); val largeurs = largeursExport(cols)
    val corps = sections.joinToString("") { s ->
        "<h2>${eHtml(s.titre)} <span class=\"m\">— ${s.membres.size}${s.part?.let { " · ${pourcent(it)}" } ?: ""}</span></h2>" +
            tableHtml(cols.map { it.titre to "" }, s.membres.map { m -> cols.map { c -> when (c.cle) {
                "nom" -> eHtml(m.nomComplet) + if (m.actif) "" else " <span class=\"m\">(inactif)</span>"
                "fonction" -> fonction(m)?.takeIf { it.second != "invite" }?.let { eHtml(d.nomRole(it.first)) } ?: ""
                "pupitre" -> eHtml(m.pupitre); "nationalite" -> eHtml(m.nationalite); "anniversaire" -> "${m.jour}&nbsp;${MOIS[m.mois - 1]}"
                "statut" -> eHtml(m.statut); "profession" -> eHtml(m.profession)
                "whatsapp" -> eHtml(m.whatsapp); "email" -> eHtml(m.email); "accord" -> if (m.consentement) "Oui" else "Non"; "actif" -> if (m.actif) "Oui" else "Non"
                else -> m.adhesion?.let { "<span class=\"nw\">${dateFr(it.take(10))}</span>" } ?: "" } } }, largeurs)
    }.ifEmpty { "<p class=\"m\">Aucun membre.</p>" }
    return "Liste des membres" to pageHtml(d, "Liste des membres", "$n membre${if (n > 1) "s" else ""}${if (lib.isNotEmpty()) " · ${eHtml(lib)}" else ""} · au ${dateFr(aujourdhui().toString())}",
        corps + "<p class=\"m\">Document interne : données personnelles à ne pas diffuser en dehors du bureau.</p>", paysageMembres(cols))
}

/** Répartition imprimable (comme pdfRepartition du site). */
fun documentRepartition(d: Donnees, membres: List<Membre>): Pair<String, String> {
    val actifs = membres.filter { it.actif }
    val corps = listOf("nationalite", "pupitre", "statut").joinToString("") { k ->
        "<h2>${CRITERES_MEMBRES[k]!!.second}</h2>" + tableHtml(listOf(CRITERES_MEMBRES[k]!!.first to "", "Effectif" to "d", "Part" to "d"),
            repartition(actifs, k).map { listOf(eHtml(it.libelle), it.n.toString(), pourcent(it.part)) })
    }
    return "Répartition des membres" to pageHtml(d, "Répartition des membres", "${actifs.size} membre${if (actifs.size > 1) "s" else ""} actif${if (actifs.size > 1) "s" else ""} · au ${dateFr(aujourdhui().toString())}", corps)
}

/** Feuille « Exporter N membres » (comme feuilleExportMembres du site) : colonnes en puces, Tout / Par défaut, puis PDF ou Excel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeuilleExportMembres(n: Int, vue: String, onPdf: (List<String>) -> Unit, onExcel: (List<String>) -> Unit) {
    var choix by remember { mutableStateOf(colonnesMembresChoisies()) }
    fun changer(l: List<String>) { choix = l; garderColonnesMembres(l) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Exporter $n membre${if (n > 1) "s" else ""}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (vue.isNotEmpty()) Text(vue, color = Couleurs.Texte2, fontSize = 14.sp)
        Text("Colonnes", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text("Prénom et nom sortent toujours. Touchez une colonne pour l’ajouter ou la retirer.", color = Couleurs.Texte2, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COLONNES_EXPORT_MEMBRES.filter { it.cle != "nom" }.forEach { c ->
                FilterChip(selected = c.cle in choix, onClick = { changer(if (c.cle in choix) choix - c.cle else choix + c.cle) }, label = { Text(c.titre, maxLines = 1) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { changer(COLONNES_EXPORT_MEMBRES.map { it.cle }) }) { Text("Tout") }
            TextButton(onClick = { changer(COLONNES_MEMBRES_DEFAUT) }) { Text("Par défaut") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            @Composable fun Choix(titre: String, sous: String, onClick: () -> Unit) =
                Surface(onClick = onClick, shape = Formes.XGrand, color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Couleurs.Bord), modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(titre, fontWeight = FontWeight.Bold); Text(sous, fontSize = 13.sp, color = Couleurs.Texte2)
                    }
                }
            Choix("PDF", "Mise en page pour imprimer, archiver ou transmettre") { onPdf(choix) }
            Choix("Excel", "Tableau modifiable (CSV), réimportable") { onExcel(choix) }
        }
    }
}
