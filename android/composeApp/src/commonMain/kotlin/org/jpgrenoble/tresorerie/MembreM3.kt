package org.jpgrenoble.tresorerie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

// =====================================================================
// Page des membres en Material 3 Expressive (comme la section « Page des membres » de styles.css) :
// rubriques à grands arrondis avec pastille d'icône colorée qui s'arrondit à l'ouverture, badge des nouveautés,
// listes segmentées (tuiles blanches, extrémités plus arrondies), mouvements « ressort » courts.
// Android coupe lui-même ces animations quand « Supprimer les animations » est activé.
// =====================================================================

/** Ressorts de la page des membres (--m3-ressort, --m3-ressort-doux du site). */
internal fun <T> ressort() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
internal fun <T> ressortDoux() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/** Forme d'une tuile dans une liste segmentée : extrémités arrondies à 24, jointures à 12 (comme le site). */
fun formeSegment(index: Int, total: Int): RoundedCornerShape {
    val haut = if (index == 0) 24.dp else 12.dp
    val bas = if (index == total - 1) 24.dp else 12.dp
    return RoundedCornerShape(topStart = haut, topEnd = haut, bottomStart = bas, bottomEnd = bas)
}

/** Rubrique de la page des membres (même rendu que .vue-membre details.rubrique du site). */
@Composable
fun RubriqueMembre(id: String, titre: String, resume: String, teinte: Teinte, icone: ImageVector, ouverteParDefaut: Boolean = true,
                   forcee: Boolean = false, badge: String? = null, fondTeinte: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val ouverte = forcee || (EtatAccueil.ouvertes[id] ?: ouverteParDefaut)
    val rotation by animateFloatAsState(if (ouverte) 180f else 0f, ressort(), label = "chevron")
    // La pastille d'icône passe du galet (16) au rond quand la rubrique est ouverte
    val coin by animateDpAsState(if (ouverte) 22.dp else 16.dp, ressort(), label = "forme")
    // Halo une fois à l'arrivée d'une nouveauté, point qui palpite sur la pastille
    val halo = remember { Animatable(0f) }
    val point = remember { Animatable(if (badge != null) 0.4f else 1f) }
    LaunchedEffect(badge != null) {
        if (badge != null) {
            launch { delay(500); halo.animateTo(1f, tween(900)); halo.snapTo(0f) }
            launch { repeat(3) { delay(600); point.snapTo(0.4f); point.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) } }
        }
    }
    val fond = if (fondTeinte) lerp(Color.White, teinte.conteneur, 0.38f) else Color.White
    Surface(color = fond, shape = Formes.M3L, shadowElevation = 1.dp,
        border = if (halo.value > 0f) BorderStroke((2 + 8 * halo.value).dp, teinte.fort.copy(alpha = 0.45f * (1f - halo.value))) else null) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clip(Formes.M3L).clickable(onClickLabel = if (ouverte) "Replier" else "Déplier") { EtatAccueil.ouvertes[id] = !ouverte }
                .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(coin)).background(teinte.conteneur), contentAlignment = Alignment.Center) {
                        Icon(icone, null, tint = teinte.fort, modifier = Modifier.size(22.dp))
                    }
                    if (badge != null) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(15.dp)
                        .graphicsLayer { scaleX = point.value; scaleY = point.value }.background(Color.White, CircleShape).padding(2.dp).background(teinte.fort, CircleShape))
                }
                Column(Modifier.weight(1f)) {
                    Text(titre, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Couleurs.Texte)
                    if (!ouverte && resume.isNotBlank()) Text(resume, fontSize = 13.sp, color = Couleurs.TexteFort2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (badge != null) {
                    val entree = remember { Animatable(0.4f) }
                    LaunchedEffect(Unit) { delay(250); entree.animateTo(1f, ressort()) }
                    Text(badge, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                        modifier = Modifier.graphicsLayer { scaleX = entree.value; scaleY = entree.value; alpha = entree.value.coerceIn(0f, 1f) }
                            .background(teinte.fort, Formes.Rond).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                Box(Modifier.size(32.dp).background(teinte.conteneur.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ExpandMore, if (ouverte) "Replier" else "Déplier", tint = teinte.fort, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
                }
            }
            AnimatedVisibility(ouverte, enter = expandVertically(ressortDoux()) + fadeIn(tween(220)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            }
        }
    }
}

/** Étiquette en pilule de la page des membres (même rendu que .vue-membre .cq-etiquettes .puce). */
@Composable
fun PiluleM3(texte: String, fond: Color, encre: Color) =
    Text(texte, fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = encre, maxLines = 1,
        modifier = Modifier.background(fond, Formes.Rond).padding(horizontal = 9.dp, vertical = 2.dp))

/** « Nouveau » : point qui palpite et texte de la couleur de la rubrique (comme .puce-nouveau du site). */
@Composable
fun PointNouveau(couleur: Color) {
    val point = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { repeat(3) { delay(800); point.snapTo(0.4f); point.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).graphicsLayer { scaleX = point.value; scaleY = point.value }.background(couleur, CircleShape))
        Text("Nouveau", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = couleur)
    }
}

/** Un communiqué dans sa rubrique : tuile blanche, date en galet coloré, étiquettes sur une ligne, liseré et reflet s'il est nouveau. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TuileCommunique(c: Communique, g: GenreCommunique, index: Int, total: Int, nonLu: Boolean, nouveau: Boolean, mesReponses: Map<String, JsonObject>, onOuvrir: () -> Unit) {
    val forme = formeSegment(index, total)
    val t = g.teinte
    // Date qui « éclot » (galet qui tourne et grossit) et reflet qui passe une fois sur un communiqué pas encore lu
    val eclosion = remember { Animatable(if (nonLu) 0.55f else 1f) }
    val reflet = remember { Animatable(-0.4f) }
    LaunchedEffect(nonLu) {
        if (nonLu) {
            launch { delay(350); eclosion.animateTo(1f, ressort()) }
            launch { delay(900); reflet.animateTo(1.4f, tween(1400)) }
        }
    }
    val important = c.priorite == "haute"
    Surface(onClick = onOuvrir, color = Color.White, shape = forme,
        border = if (nonLu) BorderStroke(2.dp, t.fort.copy(alpha = 0.55f)) else null, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.drawWithContent {
            drawContent()
            if (nonLu && reflet.value in -0.39f..1.39f) {
                val x = size.width * reflet.value; val l = size.width * 0.4f
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0f), lerp(t.conteneur, Color.White, 0.15f).copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    start = Offset(x - l / 2, 0f), end = Offset(x + l / 2, size.height)))
            }
        }.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val (fondDate, encreDate) = if (important) Couleurs.M3Primaire to Color.White else t.conteneur to t.sur
            Column(Modifier.size(52.dp, 56.dp).graphicsLayer { scaleX = eclosion.value; scaleY = eclosion.value; rotationZ = (1f - eclosion.value) * -26f }
                .clip(Formes.M3M).background(fondDate), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("${c.debut.substring(8, 10).toInt()}", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, color = encreDate)
                Text(MOIS_COURTS[c.debut.substring(5, 7).toInt() - 1].uppercase(), fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = encreDate)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val repondu = c.id in mesReponses
                val avecEtiquettes = important || nouveau || c.genre == "sondage" || c.genre == "photos"
                if (avecEtiquettes) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
                    if (important) PiluleM3("Important", Couleurs.M3Primaire, Color.White)
                    if (nouveau) PointNouveau(t.fort)
                    when (c.genre) {
                        "sondage" -> if (repondu) PiluleM3("Répondu", Couleurs.M3VertConteneur, Couleurs.M3SurVertConteneur) else PiluleM3("À répondre", Couleurs.M3OrangeConteneur, Couleurs.M3SurOrangeConteneur)
                        "photos" -> PiluleM3("${c.photos.size} photo" + if (c.photos.size > 1) "s" else "", t.conteneur, t.sur)
                    }
                }
                Text(c.titre, fontSize = 15.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Couleurs.Texte, maxLines = 2, overflow = TextOverflow.Ellipsis)
                c.texte?.lineSequence()?.firstOrNull()?.let { Text(it, fontSize = 13.5.sp, lineHeight = 19.sp, color = Couleurs.TexteFort2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (c.genre == "photos") Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    c.photos.take(4).forEach { PhotoPartage(it, Modifier.size(48.dp).clip(Formes.M3S)) }
                }
                if (c.genre == "lien") Text(hoteDe(c.lien), fontSize = 12.sp, color = Couleurs.Texte2)
                c.fin?.let { Text((if (c.genre == "sondage") "Répondre avant le " else "Jusqu’au ") + dateFr(it), fontSize = 12.sp, color = Couleurs.Texte2) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = t.fort.copy(alpha = 0.7f))
        }
    }
}

/** Rendez-vous de « Au programme » : tuile de sa couleur, date en galet (comme .vue-membre .liste-evt du site). */
@Composable
fun TuileRendezVous(p: Projet, index: Int, total: Int, onClick: () -> Unit) {
    val (fond, encre, accent) = couleurEvt(p.id)
    Surface(onClick = onClick, color = lerp(Color.White, fond, 0.45f), shape = formeSegment(index, total), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            p.debut?.let { dt ->
                val j = kotlinx.datetime.LocalDate.parse(dt.take(10))
                Column(Modifier.size(52.dp, 56.dp).clip(Formes.M3M).background(accent), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("${j.dayOfMonth}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp, color = Color.White)
                    Text(MOIS_COURTS[j.monthNumber - 1], fontSize = 11.sp, lineHeight = 12.sp, color = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(p.nom, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(p.debut?.let { jourLong(it) }, p.heureDebut?.let { heureFr(it) }, p.lieu).joinToString(" · ").ifEmpty { "Date à fixer" }, fontSize = 13.sp, color = Couleurs.TexteFort2)
            }
            p.participation?.let { PiluleM3(euros(it), Couleurs.JauneClair, Couleurs.SurJaune) }
                ?: p.debut?.let { PiluleM3(dansJours(it), lerp(Color.White, fond, 0.9f), encre) }
        }
    }
}

/** Entrée en cascade de la page des membres : montée avec léger rebond (comme m3-arrivee du site). */
@Composable
fun ArriveeM3(rang: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(rang * 60L); p.animateTo(1f, ressortDoux()) }
    Box(Modifier.graphicsLayer { alpha = p.value.coerceIn(0f, 1f); translationY = (1f - p.value) * 18.dp.toPx(); scaleX = 0.98f + 0.02f * p.value; scaleY = scaleX }) { content() }
}

/** Fond chaud de la page des membres et de la bannière sans photo (aplat vivant aux couleurs de l'association). */
val FondBanniereMembre = Brush.linearGradient(listOf(Color(0xFFD44A16), Color(0xFFB3360C), Color(0xFF7E2347)), start = Offset(0f, 0f), end = Offset(1400f, 1000f))

