package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

// Graphiques : mêmes règles que le site (une seule échelle, traits fins, légende dès deux séries,
// texte en encre neutre). En SVG pour le rapport imprimé, en Compose pour l'écran.
object CouleursGraph {
    val Recette = Color(0xFF1B77B0); val Depense = Color(0xFFC23E10); val Grille = Color(0xFFE9E5E3)
    val Piste = Color(0xFFF1EEEC); val Alerte = Color(0xFFBA1A1A); val Moyen = Color(0xFFB07A00)
    const val RECETTE = "#1B77B0"; const val DEPENSE = "#C23E10"; const val ALERTE = "#BA1A1A"
    // Code couleur unique : recettes en bleu, dépenses en orange, trésorerie en encre neutre
    val Tresorerie = Color(0xFF4A4543); const val TRESORERIE = "#4A4543"
}
private val MOIS_COURTS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

data class Mois(val cle: String, val rec: Double, val dep: Double, val solde: Double) { val libelle get() = MOIS_COURTS[cle.substring(5, 7).toInt() - 1] }
data class Element(val nom: String, val valeur: Double)
data class LigneBudgetG(val nom: String, val prevu: Double, val realise: Double, val sens: String)

fun moisEntre(debut: String, fin: String): List<String> {
    var a = debut.take(4).toInt(); var m = debut.substring(5, 7).toInt()
    val af = fin.take(4).toInt(); val mf = fin.substring(5, 7).toInt()
    val r = mutableListOf<String>()
    while (a < af || (a == af && m <= mf)) { r += "$a-${m.toString().padStart(2, '0')}"; m++; if (m > 12) { m = 1; a++ } }
    return r
}

// Recettes, dépenses et solde de fin de mois à partir des soldes de départ et de toutes les opérations
fun serieMensuelle(txs: List<Ecriture>, comptes: List<Compte>, debut: String, fin: String): List<Mois> {
    val mois = moisEntre(debut, fin)
    var solde = comptes.sumOf { it.soldeInitial } + txs.filter { it.date < mois.first() + "-01" }.sumOf { it.signe }
    return mois.map { m ->
        val l = txs.filter { it.date.startsWith(m) }
        val rec = l.filter { it.sens == "recette" }.sumOf { it.montant }; val dep = l.filter { it.sens == "depense" }.sumOf { it.montant }
        solde += rec - dep
        Mois(m, rec, dep, solde)
    }
}

// Nombre de mois de dépenses couverts par la trésorerie (moyenne des mois à dépenses des 12 derniers mois)
fun reserveEnMois(solde: Double, serie: List<Mois>): Double? {
    val d = serie.takeLast(12).map { it.dep }.filter { it > 0 }
    return if (d.isEmpty()) null else solde / d.average()
}

private fun graduations(min0: Double, max0: Double, n: Int = 4): List<Double> {
    val min = min0; val max = if (max0 == min0) min0 + 1 else max0
    val brut = (max - min) / n
    val p = 10.0.pow(floor(log10(brut)))
    val pas = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * p }.first { it >= brut }
    val bas = floor(min / pas) * pas; val haut = ceil(max / pas) * pas
    return generateSequence(bas) { it + pas }.takeWhile { it <= haut + pas / 2 }.map { round(it * 100) / 100 }.toList()
}
private fun court(n: Double) = if (abs(n) >= 1000) "${(round(n / 100) / 10).toString().replace('.', ',').removeSuffix(",0")}${NBSP_FINE}k" else round(n).toLong().toString()
private fun h(s: String?) = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun euros0(v: Double) = euros(round(v)).substringBeforeLast(',') + "$NBSP_FINE€"
private fun f(v: Double) = (round(v * 10) / 10).toString()

// ---------- SVG (rapport imprimé) ----------
fun svgColonnes(titre: String, libelles: List<String>, series: List<Triple<String, String, List<Double>>>): String {
    val L = 640.0; val H = 220.0; val g = 44.0; val d = 8.0; val hh = 16.0; val b = 26.0
    val toutes = series.flatMap { it.third }
    val t = graduations(minOf(0.0, toutes.minOrNull() ?: 0.0), maxOf(0.0, toutes.maxOrNull() ?: 0.0))
    fun y(v: Double) = hh + (H - hh - b) * (1 - (v - t.first()) / (t.last() - t.first()))
    val bande = (L - g - d) / libelles.size
    val l = minOf(18.0, (bande * 0.7 - 2 * (series.size - 1)) / series.size)
    val bloc = series.size * l + 2 * (series.size - 1)
    val sb = StringBuilder()
    t.forEach { v -> sb.append("<line x1=\"$g\" x2=\"${L - d}\" y1=\"${f(y(v))}\" y2=\"${f(y(v))}\" stroke=\"${if (v == 0.0) "#CFC8C5" else "#E9E5E3"}\"/><text x=\"${g - 6}\" y=\"${f(y(v) + 4)}\" text-anchor=\"end\" class=\"g-axe\">${court(v)}</text>") }
    libelles.forEachIndexed { i, lib ->
        val x0 = g + i * bande + (bande - bloc) / 2
        series.forEachIndexed { k, (_, coul, vals) ->
            val v = vals.getOrElse(i) { 0.0 }; val x = x0 + k * (l + 2); val y0 = y(0.0); val y1 = y(v)
            val haut = minOf(y0, y1); val hauteur = abs(y1 - y0); val r = minOf(4.0, hauteur, l / 2)
            if (hauteur >= 0.5) sb.append("<path d=\"M${f(x)},${f(y0)}V${f(haut + r)}Q${f(x)},${f(haut)} ${f(x + r)},${f(haut)}H${f(x + l - r)}Q${f(x + l)},${f(haut)} ${f(x + l)},${f(haut + r)}V${f(y0)}Z\" fill=\"$coul\"/>")
        }
        sb.append("<text x=\"${f(g + i * bande + bande / 2)}\" y=\"${H - 8}\" text-anchor=\"middle\" class=\"g-axe\">${h(lib)}</text>")
    }
    val legende = if (series.size < 2) "" else "<div class=\"g-legende\">" + series.joinToString("") { "<span><i style=\"background:${it.second}\"></i>${h(it.first)}</span>" } + "</div>"
    return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption>$legende<svg viewBox=\"0 0 $L $H\" role=\"img\" aria-label=\"${h(titre)}\">$sb</svg></figure>"
}

fun svgLigne(titre: String, libelles: List<String>, valeurs: List<Double>, couleur: String = CouleursGraph.TRESORERIE): String {
    val L = 640.0; val H = 180.0; val g = 44.0; val d = 70.0; val hh = 16.0; val b = 26.0
    val t = graduations(minOf(0.0, valeurs.minOrNull() ?: 0.0), maxOf(1.0, valeurs.maxOrNull() ?: 1.0))
    fun y(v: Double) = hh + (H - hh - b) * (1 - (v - t.first()) / (t.last() - t.first()))
    val pas = if (libelles.size > 1) (L - g - d) / (libelles.size - 1) else 0.0
    fun x(i: Int) = g + i * pas
    val pts = valeurs.mapIndexed { i, v -> "${f(x(i))},${f(y(v))}" }.joinToString(" ")
    val base = y(maxOf(0.0, t.first()))
    val sb = StringBuilder()
    t.forEach { v -> sb.append("<line x1=\"$g\" x2=\"${L - d}\" y1=\"${f(y(v))}\" y2=\"${f(y(v))}\" stroke=\"${if (v == 0.0) "#CFC8C5" else "#E9E5E3"}\"/><text x=\"${g - 6}\" y=\"${f(y(v) + 4)}\" text-anchor=\"end\" class=\"g-axe\">${court(v)}</text>") }
    sb.append("<polygon points=\"${f(x(0))},${f(base)} $pts ${f(x(valeurs.size - 1))},${f(base)}\" fill=\"$couleur\" opacity=\".1\"/>")
    sb.append("<polyline points=\"$pts\" fill=\"none\" stroke=\"$couleur\" stroke-width=\"2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>")
    libelles.forEachIndexed { i, lib -> sb.append("<text x=\"${f(x(i))}\" y=\"${H - 8}\" text-anchor=\"middle\" class=\"g-axe\">${h(lib)}</text>") }
    val n = valeurs.size - 1
    if (n >= 0) sb.append("<circle cx=\"${f(x(n))}\" cy=\"${f(y(valeurs[n]))}\" r=\"4.5\" fill=\"$couleur\" stroke=\"#fff\" stroke-width=\"2\"/><text x=\"${f(x(n) + 10)}\" y=\"${f(y(valeurs[n]) + 4)}\" class=\"g-valeur\">${h(euros0(valeurs[n]))}</text>")
    return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><svg viewBox=\"0 0 $L $H\" role=\"img\" aria-label=\"${h(titre)}\">$sb</svg></figure>"
}

// Répartition : 5 postes + « Autres »
fun regrouper(items: List<Element>, max: Int = 5): List<Element> {
    val tri = items.filter { it.valeur > 0 }.sortedByDescending { it.valeur }
    return if (tri.size > max + 1) tri.take(max) + Element("Autres", tri.drop(max).sumOf { it.valeur }) else tri
}

fun htmlBarres(titre: String, items: List<Element>, couleur: String): String {
    val l = regrouper(items); val total = items.filter { it.valeur > 0 }.sumOf { it.valeur }.takeIf { it > 0 } ?: 1.0
    val plus = l.maxOfOrNull { it.valeur }?.takeIf { it > 0 } ?: 1.0
    if (l.isEmpty()) return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><p class=\"g-vide\">Aucune donnée sur la période</p></figure>"
    return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><div class=\"g-barres\">" + l.joinToString("") { x ->
        "<div class=\"g-ligne\"><span class=\"g-nom\">${h(x.nom)}</span><span class=\"g-piste\"><i style=\"width:${f(maxOf(1.0, 100 * x.valeur / plus))}%;background:$couleur\"></i></span>" +
            "<span class=\"g-val\">${h(euros0(x.valeur))} <small>${round(100 * x.valeur / total).toInt()}$NBSP%</small></span></div>"
    } + "</div></figure>"
}

fun htmlJauge(titre: String, valeur: Double, cible: Double, detail: String): String {
    val p = if (cible > 0) minOf(100, round(100 * valeur / cible).toInt()) else 0
    val coul = CouleursGraph.RECETTE
    return "<div class=\"g-jauge\"><div class=\"g-jauge-tete\"><span>${h(titre)}</span><b>$p$NBSP%</b></div><div class=\"g-piste g-piste-jauge\"><i style=\"width:$p%;background:$coul\"></i></div><small>${h(detail)}</small></div>"
}

fun htmlBudget(titre: String, lignes: List<LigneBudgetG>): String {
    if (lignes.isEmpty()) return ""
    return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><div class=\"g-barres\">" + lignes.joinToString("") { b ->
        val taux = if (b.prevu > 0) round(100 * b.realise / b.prevu).toInt() else 0
        "<div class=\"g-ligne\"><span class=\"g-nom\">${h(b.nom)}</span><span class=\"g-piste\"><i style=\"width:${minOf(100, taux)}%;background:${if (b.sens == "recette") CouleursGraph.RECETTE else CouleursGraph.DEPENSE}\"></i></span>" +
            "<span class=\"g-val\">$taux$NBSP%${if (b.sens == "depense" && taux > 100) " <em class=\"g-depasse\">dépassé</em>" else ""}</span></div>"
    } + "</div></figure>"
}

const val CSS_GRAPHIQUES = """
.graphique{margin:0;display:flex;flex-direction:column;gap:6px;min-width:0}
.graphique figcaption{font-weight:700;font-size:13px}
.graphique svg{width:100%;height:auto;display:block;overflow:visible}
.g-axe{font:11px sans-serif;fill:#5A5350}.g-valeur{font:600 12px sans-serif;fill:#1C1B1A}
.g-legende{display:flex;gap:14px;font-size:11px;color:#5A5350}.g-legende span{display:inline-flex;align-items:center;gap:6px}
.g-legende i{width:10px;height:10px;border-radius:3px;display:inline-block}
.g-barres{display:flex;flex-direction:column;gap:7px}
.g-ligne{display:grid;grid-template-columns:34% 1fr auto;gap:8px;align-items:center;font-size:12px}
.g-nom{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.g-piste{height:9px;border-radius:5px;background:#F1EEEC;overflow:hidden;display:block}.g-piste i{display:block;height:100%;border-radius:0 5px 5px 0}
.g-piste-jauge{height:12px}.g-val{white-space:nowrap;text-align:right}.g-val small{color:#5A5350}
.g-depasse{font-style:normal;font-size:10px;font-weight:700;color:#410002;background:#FFDAD6;border-radius:8px;padding:1px 6px}
.g-jauge{display:flex;flex-direction:column;gap:6px}.g-jauge-tete{display:flex;justify-content:space-between;font-size:12px;color:#5A5350}
.g-jauge-tete b{color:#1C1B1A;font-size:14px}.g-jauge small{font-size:11px;color:#5A5350}.g-vide{color:#5A5350;font-size:12px;margin:0}
"""

// ---------- Compose (tableau de bord) ----------
@Composable
fun GraphColonnes(mois: List<Mois>, modifier: Modifier = Modifier) {
    val mesure = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = Couleurs.Texte2)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("Recettes" to CouleursGraph.Recette, "Dépenses" to CouleursGraph.Depense).forEach { (l, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp))); Text(l, fontSize = 12.sp, color = Couleurs.Texte2)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val g = 34.dp.toPx(); val b = 18.dp.toPx(); val haut = 6.dp.toPx()
            val t = graduations(0.0, maxOf(1.0, mois.maxOfOrNull { maxOf(it.rec, it.dep) } ?: 1.0))
            fun y(v: Double) = haut + (size.height - haut - b) * (1 - (v - t.first()) / (t.last() - t.first())).toFloat()
            t.forEach { v ->
                drawLine(CouleursGraph.Grille, Offset(g, y(v)), Offset(size.width, y(v)), 1f)
                val r = mesure.measure(court(v), style)
                drawText(r, topLeft = Offset(g - r.size.width - 4.dp.toPx(), y(v) - r.size.height / 2))
            }
            if (mois.isEmpty()) return@Canvas
            val bande = (size.width - g) / mois.size
            val l = minOf(14.dp.toPx(), (bande * 0.7f - 2.dp.toPx()) / 2)
            mois.forEachIndexed { i, m ->
                val x0 = g + i * bande + (bande - 2 * l - 2.dp.toPx()) / 2
                listOf(m.rec to CouleursGraph.Recette, m.dep to CouleursGraph.Depense).forEachIndexed { k, (v, c) ->
                    val x = x0 + k * (l + 2.dp.toPx()); val top = y(v); val bas = y(0.0)
                    if (bas - top > 0.5f) {
                        val r = minOf(4.dp.toPx(), bas - top, l / 2)
                        drawPath(Path().apply { addRoundRect(RoundRect(x, top, x + l, bas, CornerRadius(r), CornerRadius(r), CornerRadius.Zero, CornerRadius.Zero)) }, c)
                    }
                }
                if (mois.size <= 6 || i % 2 == mois.size % 2) {
                    val r = mesure.measure(m.libelle, style)
                    drawText(r, topLeft = Offset(g + i * bande + bande / 2 - r.size.width / 2, size.height - r.size.height))
                }
            }
        }
    }
}

@Composable
fun GraphSolde(mois: List<Mois>, modifier: Modifier = Modifier) {
    val mesure = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = Couleurs.Texte2)
    val styleVal = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (mois.isEmpty()) return@Canvas
        val g = 34.dp.toPx(); val dr = 58.dp.toPx(); val b = 18.dp.toPx(); val haut = 6.dp.toPx()
        val vals = mois.map { it.solde }
        val t = graduations(minOf(0.0, vals.min()), maxOf(1.0, vals.max()))
        fun y(v: Double) = haut + (size.height - haut - b) * (1 - (v - t.first()) / (t.last() - t.first())).toFloat()
        val pas = if (mois.size > 1) (size.width - g - dr) / (mois.size - 1) else 0f
        fun x(i: Int) = g + i * pas
        t.forEach { v ->
            drawLine(CouleursGraph.Grille, Offset(g, y(v)), Offset(size.width - dr, y(v)), 1f)
            val r = mesure.measure(court(v), style); drawText(r, topLeft = Offset(g - r.size.width - 4.dp.toPx(), y(v) - r.size.height / 2))
        }
        val p = Path().apply { vals.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val aire = Path().apply { addPath(p); lineTo(x(vals.size - 1), y(maxOf(0.0, t.first()))); lineTo(x(0), y(maxOf(0.0, t.first()))); close() }
        drawPath(aire, CouleursGraph.Tresorerie.copy(alpha = 0.1f))
        drawPath(p, CouleursGraph.Tresorerie, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val n = vals.size - 1
        drawCircle(Color.White, 6.dp.toPx(), Offset(x(n), y(vals[n]))); drawCircle(CouleursGraph.Tresorerie, 4.dp.toPx(), Offset(x(n), y(vals[n])))
        val r = mesure.measure(euros0(vals[n]), styleVal); drawText(r, topLeft = Offset(x(n) + 8.dp.toPx(), y(vals[n]) - r.size.height / 2))
        mois.forEachIndexed { i, m ->
            if (mois.size <= 6 || i % 2 == mois.size % 2) {
                val rr = mesure.measure(m.libelle, style); drawText(rr, topLeft = Offset(x(i) - rr.size.width / 2, size.height - rr.size.height))
            }
        }
    }
}

@Composable
fun GraphBarres(items: List<Element>, couleur: Color, modifier: Modifier = Modifier) {
    val l = regrouper(items); val total = items.filter { it.valeur > 0 }.sumOf { it.valeur }.takeIf { it > 0 } ?: 1.0
    val plus = l.maxOfOrNull { it.valeur }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (l.isEmpty()) Text("Aucune donnée sur la période", color = Couleurs.Texte2, fontSize = 13.sp)
        l.forEach { x ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(x.nom, Modifier.weight(0.38f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(0.37f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(CouleursGraph.Piste)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((x.valeur / plus).toFloat().coerceIn(0.01f, 1f)).background(couleur, RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)))
                }
                Text("${euros0(x.valeur)} · ${round(100 * x.valeur / total).toInt()}$NBSP%", Modifier.weight(0.25f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun Jauge(titre: String, valeur: Double, cible: Double, detail: String, modifier: Modifier = Modifier) {
    val p = if (cible > 0) minOf(100, round(100 * valeur / cible).toInt()) else 0
    val coul = CouleursGraph.Recette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row { Text(titre, Modifier.weight(1f), fontSize = 13.sp, color = Couleurs.Texte2); Text("$p$NBSP%", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(CouleursGraph.Piste)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p / 100f).background(coul, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)))
        }
        Text(detail, fontSize = 12.sp, color = Couleurs.Texte2)
    }
}

// ---------- Anneau (part du total) : 5 postes au plus + « Autres » en gris, palette validée daltonisme ----------
// Chaque anneau décline la couleur de son sens, du plus foncé (premier poste) au plus clair
val RAMPES = mapOf(
    "recette" to listOf("#0A3A5A", "#1B77B0", "#62AEE0", "#BCDEF4"),
    "depense" to listOf("#6A2208", "#C23E10", "#EE7D4C", "#F8C2A6"),
)
const val GRIS_AUTRES = "#6E6764"
private fun couleurHex(s: String) = Color(("FF" + s.removePrefix("#")).toLong(16))
data class Secteur(val nom: String, val valeur: Double, val couleur: String)

fun secteurs(items: List<Element>, sens: String = "recette", max: Int = 4): List<Secteur> {
    val palette = RAMPES[sens] ?: RAMPES.getValue("recette")
    val tri = items.filter { it.valeur > 0 }.sortedByDescending { it.valeur }
    val l = if (tri.size > max + 1) tri.take(max) + Element("Autres", tri.drop(max).sumOf { it.valeur }) else tri
    return l.mapIndexed { i, x -> Secteur(x.nom, x.valeur, if (x.nom == "Autres" && i == max) GRIS_AUTRES else palette[i % palette.size]) }
}

fun svgAnneau(titre: String, items: List<Element>, sens: String): String {
    val parts = secteurs(items, sens); val total = parts.sumOf { it.valeur }
    if (total <= 0) return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><p class=\"g-vide\">Aucune donnée sur la période</p></figure>"
    val R = 80.0; val r = 52.0; val c = 90.0
    fun pt(rad: Double, a: Double) = "${f(c + rad * kotlin.math.cos(a))},${f(c + rad * kotlin.math.sin(a))}"
    var a = -kotlin.math.PI / 2
    val sb = StringBuilder()
    parts.forEach { x ->
        val ang = 2 * kotlin.math.PI * x.valeur / total
        if (parts.size == 1) sb.append("<circle cx=\"$c\" cy=\"$c\" r=\"${(R + r) / 2}\" fill=\"none\" stroke=\"${x.couleur}\" stroke-width=\"${R - r}\"/>")
        else {
            val b = a + ang; val grand = if (ang > kotlin.math.PI) 1 else 0
            sb.append("<path d=\"M${pt(R, a)}A$R,$R 0 $grand 1 ${pt(R, b)}L${pt(r, b)}A$r,$r 0 $grand 0 ${pt(r, a)}Z\" fill=\"${x.couleur}\" stroke=\"#fff\" stroke-width=\"2\"/>")
            a = b
        }
    }
    sb.append("<text x=\"$c\" y=\"${c - 2}\" text-anchor=\"middle\" class=\"g-centre\">${h(euros0(total))}</text><text x=\"$c\" y=\"${c + 15}\" text-anchor=\"middle\" class=\"g-axe\">total</text>")
    return "<figure class=\"graphique\"><figcaption>${h(titre)}</figcaption><div class=\"g-anneau\"><svg viewBox=\"0 0 180 180\">$sb</svg><ul class=\"g-parts\">" +
        parts.joinToString("") { "<li><i style=\"background:${it.couleur}\"></i><span class=\"g-nom\">${h(it.nom)}</span><b>${round(100 * it.valeur / total).toInt()}$NBSP%</b><small>${h(euros0(it.valeur))}</small></li>" } + "</ul></div></figure>"
}

const val CSS_ANNEAU = """
.g-anneau{display:grid;grid-template-columns:150px 1fr;gap:14px;align-items:center}.g-anneau svg{max-width:150px}
.g-centre{font:700 15px sans-serif;fill:#1C1B1A}
.g-parts{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:5px;font-size:12px;min-width:0}
.g-parts li{display:grid;grid-template-columns:11px 1fr auto auto;gap:7px;align-items:center}
.g-parts i{width:11px;height:11px;border-radius:3px;display:block}.g-parts small{color:#5A5350;min-width:56px;text-align:right}
"""

@Composable
fun GraphAnneau(items: List<Element>, sens: String, modifier: Modifier = Modifier) {
    val parts = secteurs(items, sens); val total = parts.sumOf { it.valeur }
    if (total <= 0) { Text("Aucune donnée sur la période", color = Couleurs.Texte2, fontSize = 13.sp); return }
    val mesure = rememberTextMeasurer()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(120.dp)) {
            val ep = size.minDimension * 0.17f
            val rect = Size(size.minDimension - ep, size.minDimension - ep)
            val coin = Offset(ep / 2, ep / 2)
            var debut = -90f
            val ecart = if (parts.size > 1) 1.2f else 0f   // fin espace blanc entre secteurs
            parts.forEach { x ->
                val balayage = (360f * x.valeur / total).toFloat()
                drawArc(couleurHex(x.couleur), debut + ecart / 2, (balayage - ecart).coerceAtLeast(0.5f), false, coin, rect, style = Stroke(ep))
                debut += balayage
            }
            val r = mesure.measure(euros0(total), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Couleurs.Texte))
            drawText(r, topLeft = Offset(center.x - r.size.width / 2, center.y - r.size.height / 2))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEach { x ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(11.dp).background(couleurHex(x.couleur), RoundedCornerShape(3.dp)))
                    Text(x.nom, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${round(100 * x.valeur / total).toInt()}$NBSP%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(euros0(x.valeur), fontSize = 12.sp, color = Couleurs.Texte2)
                }
            }
        }
    }
}
