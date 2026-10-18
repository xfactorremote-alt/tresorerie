package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Scanner de documents (comme scanner.js du site, mêmes calculs et mêmes noms) :
// photo → détection du papier → coins ajustables (avec loupe) → redressement de la perspective
// → filtres (Magique, Original, Gris, Noir et blanc) et rotation → JPEG.

/** Image en mémoire : largeur, hauteur, pixels ARGB. */
class Pixels(val w: Int, val h: Int, val argb: IntArray)

private fun rouge(p: Int) = (p shr 16) and 255
private fun vert(p: Int) = (p shr 8) and 255
private fun bleu(p: Int) = p and 255
private fun luma(r: Int, g: Int, b: Int) = (r * 299 + g * 587 + b * 114) / 1000
private fun luma(r: Double, g: Double, b: Double) = ((r * 299 + g * 587 + b * 114) / 1000).toInt()
private fun argb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

const val COTE_DETECTION = 256   // l'image est réduite à 256 px de côté pour trouver le papier
const val COTE_SORTIE = 1600     // côté le plus long du document redressé

/** Coins du papier [haut-gauche, haut-droit, bas-droit, bas-gauche] ; trouve = faux → tout le cadre. */
class Detection(val coins: List<Offset>, val trouve: Boolean)

fun detecterDocument(img: Pixels): Detection {
    val w = img.w; val h = img.h
    val k = min(1.0, COTE_DETECTION.toDouble() / max(w, h))
    val sw = max(1, (w * k).roundToInt()); val sh = max(1, (h * k).roundToInt())
    val gris = IntArray(sw * sh)
    for (y in 0 until sh) for (x in 0 until sw) {
        val sx = min(w - 1, floor((x + 0.5) / k).toInt()); val sy = min(h - 1, floor((y + 0.5) / k).toInt()); val p = img.argb[sy * w + sx]
        gris[y * sw + x] = luma(rouge(p), vert(p), bleu(p))
    }
    val flou = flouBoite(gris, sw, sh, 2)
    // Seuil d'Otsu : sépare le papier (clair) du fond
    val hist = IntArray(256); for (v in flou) hist[v]++
    val n = sw * sh; var somme = 0.0; for (t in 0 until 256) somme += t.toDouble() * hist[t]
    var sB = 0.0; var wB = 0.0; var meilleur = 0.0; var seuil = 128
    for (t in 0 until 256) {
        wB += hist[t]; if (wB == 0.0) continue; val wF = n - wB; if (wF == 0.0) break
        sB += t.toDouble() * hist[t]; val mB = sB / wB; val mF = (somme - sB) / wF; val v = wB * wF * (mB - mF) * (mB - mF)
        if (v > meilleur) { meilleur = v; seuil = t }
    }
    // Plus grande zone claire d'un seul tenant
    val marque = IntArray(n) { -1 }; var plusGrande = -1; var taille = 0; val pile = IntArray(n); var zone = 0
    for (d in 0 until n) {
        if (flou[d] <= seuil || marque[d] >= 0) continue
        var haut = 0; var t = 0; pile[haut++] = d; marque[d] = zone
        while (haut > 0) {
            val p = pile[--haut]; t++; val x = p % sw; val y = p / sw
            if (x > 0 && flou[p - 1] > seuil && marque[p - 1] < 0) { marque[p - 1] = zone; pile[haut++] = p - 1 }
            if (x < sw - 1 && flou[p + 1] > seuil && marque[p + 1] < 0) { marque[p + 1] = zone; pile[haut++] = p + 1 }
            if (y > 0 && flou[p - sw] > seuil && marque[p - sw] < 0) { marque[p - sw] = zone; pile[haut++] = p - sw }
            if (y < sh - 1 && flou[p + sw] > seuil && marque[p + sw] < 0) { marque[p + sw] = zone; pile[haut++] = p + sw }
        }
        if (t > taille) { taille = t; plusGrande = zone }; zone++
    }
    val cadre = listOf(Offset(0f, 0f), Offset(w - 1f, 0f), Offset(w - 1f, h - 1f), Offset(0f, h - 1f))
    val part = taille.toDouble() / n
    if (plusGrande < 0 || part < 0.1) return Detection(cadre, false)
    if (part > 0.97) return Detection(cadre, true)
    // Coins : extrêmes de x + y et de x − y
    var tl: IntArray? = null; var tr: IntArray? = null; var br: IntArray? = null; var bl: IntArray? = null
    for (p in 0 until n) {
        if (marque[p] != plusGrande) continue
        val x = p % sw; val y = p / sw
        if (tl == null || x + y < tl[0] + tl[1]) tl = intArrayOf(x, y)
        if (br == null || x + y > br[0] + br[1]) br = intArrayOf(x, y)
        if (tr == null || x - y > tr[0] - tr[1]) tr = intArrayOf(x, y)
        if (bl == null || x - y < bl[0] - bl[1]) bl = intArrayOf(x, y)
    }
    fun vers(c: IntArray) = Offset(min(w - 1.0, max(0.0, (c[0] + 0.5) / k)).toFloat(), min(h - 1.0, max(0.0, (c[1] + 0.5) / k)).toFloat())
    return Detection(listOf(vers(tl!!), vers(tr!!), vers(br!!), vers(bl!!)), true)
}

private fun flouBoite(src: IntArray, w: Int, h: Int, r: Int): IntArray {
    val tmp = DoubleArray(w * h); val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0.0; var c = 0; for (d in -r..r) { val xx = x + d; if (xx in 0 until w) { s += src[y * w + xx]; c++ } }
        tmp[y * w + x] = s / c
    }
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0.0; var c = 0; for (d in -r..r) { val yy = y + d; if (yy in 0 until h) { s += tmp[yy * w + x]; c++ } }
        out[y * w + x] = (s / c).roundToInt()
    }
    return out
}

private fun dist(a: Offset, b: Offset) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

/** Document redressé : coins → rectangle (taille d'après les côtés mesurés, 1600 px au plus). */
fun redresser(img: Pixels, coins: List<Offset>, maxCote: Int = COTE_SORTIE): Pixels {
    val (tl, tr, br, bl) = coins
    var bw = max(dist(tl, tr), dist(bl, br)); var bh = max(dist(tl, bl), dist(tr, br))
    val s = min(1.0, maxCote / max(max(bw, bh), 1.0))
    val W = max(1, (bw * s).roundToInt()); val H = max(1, (bh * s).roundToInt())
    // Transformation du carré unité vers le quadrilatère (Heckbert)
    val x0 = tl.x.toDouble(); val y0 = tl.y.toDouble(); val x1 = tr.x.toDouble(); val y1 = tr.y.toDouble()
    val x2 = br.x.toDouble(); val y2 = br.y.toDouble(); val x3 = bl.x.toDouble(); val y3 = bl.y.toDouble()
    val dx1 = x1 - x2; val dx2 = x3 - x2; val dx3 = x0 - x1 + x2 - x3; val dy1 = y1 - y2; val dy2 = y3 - y2; val dy3 = y0 - y1 + y2 - y3
    var g = 0.0; var hh = 0.0
    if (abs(dx3) > 1e-9 || abs(dy3) > 1e-9) { val den = dx1 * dy2 - dx2 * dy1; g = (dx3 * dy2 - dx2 * dy3) / den; hh = (dx1 * dy3 - dx3 * dy1) / den }
    val a = x1 - x0 + g * x1; val b = x3 - x0 + hh * x3; val c = x0; val d = y1 - y0 + g * y1; val e = y3 - y0 + hh * y3; val f = y0
    val w = img.w; val h = img.h; val src = img.argb; val out = IntArray(W * H)
    for (j in 0 until H) {
        val v = (j + 0.5) / H
        for (i in 0 until W) {
            val u = (i + 0.5) / W; val z = g * u + hh * v + 1
            val X = min(w - 1.0, max(0.0, (a * u + b * v + c) / z - 0.5)); val Y = min(h - 1.0, max(0.0, (d * u + e * v + f) / z - 0.5))
            val xa = floor(X).toInt(); val ya = floor(Y).toInt(); val xb = min(w - 1, xa + 1); val yb = min(h - 1, ya + 1); val fx = X - xa; val fy = Y - ya
            val p00 = src[ya * w + xa]; val p01 = src[ya * w + xb]; val p10 = src[yb * w + xa]; val p11 = src[yb * w + xb]
            fun canal(sh: Int): Int {
                val q00 = (p00 shr sh) and 255; val q01 = (p01 shr sh) and 255; val q10 = (p10 shr sh) and 255; val q11 = (p11 shr sh) and 255
                return ((q00 * (1 - fx) + q01 * fx) * (1 - fy) + (q10 * (1 - fx) + q11 * fx) * fy).roundToInt().coerceIn(0, 255)
            }
            out[j * W + i] = argb(canal(16), canal(8), canal(0))
        }
    }
    return Pixels(W, H, out)
}

/** Fond du papier : blanc local (maximum par bloc, élargi de deux blocs, lissé, interpolé) et teinte du papier (balance des blancs). */
private class Papier(val fond: (Int, Int) -> Double, val gains: DoubleArray)

private fun fondPapier(img: Pixels): Papier {
    val w = img.w; val h = img.h; val B = max(8, (min(w, h) / 24.0).roundToInt()); val gw = (w + B - 1) / B; val gh = (h + B - 1) / B
    var g = DoubleArray(gw * gh); val rgb = DoubleArray(gw * gh * 3)
    for (y in 0 until h) for (x in 0 until w) {
        val p = img.argb[y * w + x]; val v = luma(rouge(p), vert(p), bleu(p)).toDouble(); val q = (y / B) * gw + (x / B)
        if (v > g[q]) { g[q] = v; rgb[q * 3] = rouge(p).toDouble(); rgb[q * 3 + 1] = vert(p).toDouble(); rgb[q * 3 + 2] = bleu(p).toDouble() }
    }
    // Teinte du papier : blocs dont le blanc est dans la moitié la plus claire
    val mediane = g.sorted()[g.size shr 1]; val t = DoubleArray(3); var nt = 0
    for (q in 0 until gw * gh) if (g[q] >= mediane) { t[0] += rgb[q * 3]; t[1] += rgb[q * 3 + 1]; t[2] += rgb[q * 3 + 2]; nt++ }
    val lt = luma(t[0] / nt, t[1] / nt, t[2] / nt).let { if (it == 0) 1 else it }
    val gains = DoubleArray(3) { lt / max(t[it] / nt, 1.0) }
    fun voisins(src: DoubleArray, f: (List<Double>) -> Double): DoubleArray {
        val o = DoubleArray(gw * gh)
        for (y in 0 until gh) for (x in 0 until gw) {
            val l = mutableListOf<Double>()
            for (dy in -1..1) for (dx in -1..1) { val xx = x + dx; val yy = y + dy; if (xx in 0 until gw && yy in 0 until gh) l += src[yy * gw + xx] }
            o[y * gw + x] = f(l)
        }
        return o
    }
    g = voisins(voisins(voisins(g) { it.max() }) { it.max() }) { it.average() }
    val gf = g
    return Papier({ x, y ->
        val fx = min(gw - 1.0, max(0.0, (x + 0.5) / B - 0.5)); val fy = min(gh - 1.0, max(0.0, (y + 0.5) / B - 0.5))
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt(); val x1 = min(gw - 1, x0 + 1); val y1 = min(gh - 1, y0 + 1); val ax = fx - x0; val ay = fy - y0
        (gf[y0 * gw + x0] * (1 - ax) + gf[y0 * gw + x1] * ax) * (1 - ay) + (gf[y1 * gw + x0] * (1 - ax) + gf[y1 * gw + x1] * ax) * ay
    }, gains)
}

val FILTRES = listOf("magique" to "Magique", "original" to "Original", "gris" to "Gris", "nb" to "Noir et blanc")

/** Magique : le papier devient blanc (ombres et teinte retirées), l'encre plus nette, les couleurs gardées. */
fun ameliorer(img: Pixels, mode: String = "magique"): Pixels {
    if (mode == "original") return Pixels(img.w, img.h, img.argb.copyOf())
    val papier = if (mode == "gris") null else fondPapier(img)
    val out = IntArray(img.argb.size)
    for (y in 0 until img.h) for (x in 0 until img.w) {
        val i = y * img.w + x; val p = img.argb[i]
        if (papier == null) { val v = luma(rouge(p), vert(p), bleu(p)); out[i] = argb(v, v, v); continue }
        // Balance des blancs, puis division par le blanc local : le papier devient blanc ; niveaux resserrés (encre plus franche)
        val k = 255 / max(papier.fond(x, y), 48.0)
        fun niv(c: Int, ch: Int) = ((c * papier.gains[ch] * k - 30) * 255 / 200).roundToInt().coerceIn(0, 255)
        val r = niv(rouge(p), 0); val g = niv(vert(p), 1); val b = niv(bleu(p), 2)
        out[i] = if (mode == "nb") { val v = if (luma(r, g, b) > 165) 255 else 0; argb(v, v, v) } else argb(r, g, b)
    }
    return Pixels(img.w, img.h, out)
}

/** Quart de tour dans le sens des aiguilles d'une montre. */
fun tourner(img: Pixels): Pixels {
    val out = IntArray(img.argb.size)
    for (y in 0 until img.h) for (x in 0 until img.w) out[x * img.h + (img.h - 1 - y)] = img.argb[y * img.w + x]
    return Pixels(img.h, img.w, out)
}

// ---------------------------------------------------------------------
// Fenêtre plein écran
// ---------------------------------------------------------------------
const val COTE_TRAVAIL = 2000

/** Bouton « Scanner avec l'appareil photo » (comme brancherScanners du site) : photo, cadrage, filtre, puis le fichier JPEG. */
@Composable
fun BoutonScanner(message: (String) -> Unit, modifier: Modifier = Modifier, libelle: String = "Scanner avec l’appareil photo", quandScanne: (Fichier) -> Unit) {
    val scanner = rememberScanner { f, err -> if (err != null) message(err); if (f != null) quandScanne(f) }
    FilledTonalButton(onClick = scanner, modifier = modifier) {
        Icon(Icons.Outlined.CameraAlt, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(libelle)
    }
}

@Composable
fun rememberScanner(quandFini: (Fichier?, String?) -> Unit): () -> Unit {
    var photo by remember { mutableStateOf<Pixels?>(null) }
    val scope = rememberCoroutineScope()
    lateinit var prendre: () -> Unit
    prendre = rememberPrendrePhoto { f, err ->
        if (err != null) quandFini(null, err)
        if (f != null) scope.launch {
            val p = withContext(Dispatchers.Default) { pixelsDepuisOctets(f.octets, COTE_TRAVAIL) }
            if (p == null) quandFini(null, "Photo illisible : réessayez") else photo = p
        }
    }
    photo?.let { p ->
        FenetreScanner(p, onReprendre = { photo = null; prendre() }) { f -> photo = null; if (f != null) quandFini(f, null) }
    }
    return prendre
}

@Composable
private fun FenetreScanner(photo: Pixels, onReprendre: () -> Unit, onFini: (Fichier?) -> Unit) {
    val detection = remember(photo) { detecterDocument(photo) }
    var coins by remember(photo) { mutableStateOf(detection.coins) }
    var redresse by remember(photo) { mutableStateOf<Pixels?>(null) }
    var filtre by remember(photo) { mutableStateOf("magique") }
    var resultat by remember(photo) { mutableStateOf<Pixels?>(null) }
    val scope = rememberCoroutineScope()
    val apercu = remember(photo) { imageDepuisPixels(photo) }
    LaunchedEffect(redresse, filtre) { val r = redresse; resultat = if (r == null) null else withContext(Dispatchers.Default) { ameliorer(r, filtre) } }
    Dialog(onDismissRequest = { onFini(null) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Couleurs.Texte).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onFini(null) }) { Icon(Icons.Outlined.Close, "Fermer", tint = Color.White) }
                Text(if (redresse == null) "Cadrer le document" else "Améliorer", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (redresse == null) "1 / 2" else "2 / 2", color = Color.White.copy(alpha = .75f), fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (redresse == null) Cadrage(photo, apercu, coins) { coins = it }
                else resultat?.let { r -> val im = remember(r) { imageDepuisPixels(r) }; Image(im, "Document scanné", Modifier.background(Color.White)) }
                    ?: CircularProgressIndicator(color = Color.White)
            }
            if (redresse == null) {
                Text(if (detection.trouve) "Papier repéré. Faites glisser les coins sur les bords du papier si besoin." else "Papier non repéré : faites glisser les quatre coins sur les bords du papier.",
                    color = Color.White.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onReprendre) { Text("Reprendre", color = Color.White) }
                    TextButton(onClick = { coins = listOf(Offset(0f, 0f), Offset(photo.w - 1f, 0f), Offset(photo.w - 1f, photo.h - 1f), Offset(0f, photo.h - 1f)) }) { Text("Tout le cadre", color = Color.White) }
                    Button(onClick = { scope.launch { redresse = withContext(Dispatchers.Default) { redresser(photo, coins) } } }) { Text("Valider le cadrage") }
                }
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    FILTRES.forEach { (k, l) ->
                        val choisi = filtre == k
                        OutlinedButton(onClick = { filtre = k }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (choisi) Couleurs.Orange else Color.Transparent, contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (choisi) Couleurs.Orange else Color.White.copy(alpha = .4f))) { Text(l) }
                    }
                    OutlinedButton(onClick = { redresse = tourner(redresse!!) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .4f))) { Text("↻ Tourner") }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { redresse = null }) { Text("‹ Recadrer", color = Color.White) }
                    Button(enabled = resultat != null, onClick = {
                        val r = resultat ?: return@Button
                        scope.launch {
                            val octets = withContext(Dispatchers.Default) { jpegDepuisPixels(r, 85) }
                            val d = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                            fun p2(n: Int) = n.toString().padStart(2, '0')
                            onFini(Fichier(octets, "image/jpeg", "jpg", "scan-${d.year}${p2(d.monthNumber)}${p2(d.dayOfMonth)}-${p2(d.hour)}${p2(d.minute)}.jpg"))
                        }
                    }) { Text("Utiliser ce document") }
                }
            }
        }
    }
}

/** Photo avec les quatre coins à faire glisser et la loupe (agrandie 2,5 fois) du côté opposé au doigt. */
@Composable
private fun Cadrage(photo: Pixels, apercu: androidx.compose.ui.graphics.ImageBitmap, coins: List<Offset>, onCoins: (List<Offset>) -> Unit) {
    var taille by remember { mutableStateOf(IntSize.Zero) }
    var actif by remember { mutableStateOf(-1) }
    val coinsActuels by rememberUpdatedState(coins)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = photo.w.toFloat() / photo.h
        val largeur = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        Box(Modifier.size(largeur, largeur / ratio).onSizeChanged { taille = it }) {
            val e = if (taille.width > 0) taille.width.toFloat() / photo.w else 1f
            Canvas(Modifier.fillMaxSize().pointerInput(photo) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val ech = size.width.toFloat() / photo.w
                        actif = coinsActuels.withIndex().filter { (_, c) -> (Offset(c.x * ech, c.y * ech) - pos).getDistance() < 48.dp.toPx() }
                            .minByOrNull { (_, c) -> (Offset(c.x * ech, c.y * ech) - pos).getDistance() }?.index ?: -1
                    },
                    onDragEnd = { actif = -1 }, onDragCancel = { actif = -1 },
                ) { change, glisse ->
                    if (actif < 0) return@detectDragGestures
                    change.consume()
                    val ech = size.width.toFloat() / photo.w
                    val c = coinsActuels[actif] + glisse / ech
                    onCoins(coinsActuels.toMutableList().also { it[actif] = Offset(c.x.coerceIn(0f, photo.w - 1f), c.y.coerceIn(0f, photo.h - 1f)) })
                }
            }) {
                drawImage(apercu, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                val p = Path().apply { coins.forEachIndexed { i, c -> if (i == 0) moveTo(c.x * e, c.y * e) else lineTo(c.x * e, c.y * e) }; close() }
                drawPath(p, Couleurs.Orange.copy(alpha = .12f)); drawPath(p, Couleurs.Orange, style = Stroke(2.dp.toPx()))
                coins.forEach { c -> drawCircle(Color.White.copy(alpha = .9f), 11.dp.toPx(), Offset(c.x * e, c.y * e)); drawCircle(Couleurs.Orange, 11.dp.toPx(), Offset(c.x * e, c.y * e), style = Stroke(3.dp.toPx())) }
                if (actif >= 0) {
                    val t = 120.dp.toPx(); val c = coins[actif]; val src = t / (e * 2.5f)
                    val gauche = c.x * e > size.width / 2; val o = Offset(if (gauche) 8.dp.toPx() else size.width - t - 8.dp.toPx(), 8.dp.toPx())
                    val rond = Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, Size(t, t))) }
                    clipPath(rond) {
                        drawRect(Color.Black, o, Size(t, t))
                        drawImage(apercu, srcOffset = IntOffset((c.x - src / 2).toInt(), (c.y - src / 2).toInt()), srcSize = IntSize(src.toInt(), src.toInt()),
                            dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize(t.toInt(), t.toInt()))
                    }
                    drawCircle(Color.White, t / 2, o + Offset(t / 2, t / 2), style = Stroke(3.dp.toPx()))
                    val m = o + Offset(t / 2, t / 2); val l = 12.dp.toPx()
                    drawLine(Couleurs.Orange, m - Offset(l, 0f), m + Offset(l, 0f), 2.dp.toPx()); drawLine(Couleurs.Orange, m - Offset(0f, l), m + Offset(0f, l), 2.dp.toPx())
                }
            }
        }
    }
}

/** Pixels d'une image JPEG ou PNG, réduite à `max` px de côté. */
expect fun pixelsDepuisOctets(octets: ByteArray, max: Int): Pixels?
expect fun jpegDepuisPixels(p: Pixels, qualite: Int): ByteArray
expect fun imageDepuisPixels(p: Pixels): androidx.compose.ui.graphics.ImageBitmap
