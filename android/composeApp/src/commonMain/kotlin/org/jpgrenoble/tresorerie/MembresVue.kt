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
fun FeuilleRepartition(membres: List<Membre>, onVoirPar: (String) -> Unit, onFermer: () -> Unit) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onFermer) { Text("Fermer") } }
    }
}
