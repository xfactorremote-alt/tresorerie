package org.jpgrenoble.tresorerie

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Couleurs du logo JP Grenoble, assombries pour un contraste 4,5:1 (voir la charte graphique)
object Couleurs {
    val Orange = Color(0xFFC23E10)
    val OrangeClair = Color(0xFFFFDBCF)
    val SurOrangeClair = Color(0xFF3B0A00)
    val Jaune = Color(0xFFF8B334)
    val JauneClair = Color(0xFFFFE3A8)
    val SurJaune = Color(0xFF3D2B00)
    val Bleu = Color(0xFF1B77B0)
    val BleuClair = Color(0xFFCDE8F8)
    val SurBleuClair = Color(0xFF00344F)
    val Erreur = Color(0xFFBA1A1A)
    val ErreurClair = Color(0xFFFFDAD6)
    val Fond = Color(0xFFF6F6F6)
    val Texte = Color(0xFF1C1B1A)
    val Texte2 = Color(0xFF5A5350)
    // Jetons repris un pour un des variables CSS du site (docs/styles.css, :root) — voir conception/DESIGN_SYSTEM.md
    val OrangeTrait = Color(0xFFF5B39C)        // --primaire-trait
    val JauneTrait = Color(0xFFF2C66A)         // --jaune-trait
    val BleuTrait = Color(0xFF9CCBEA)          // --bleu-trait
    val ErreurTrait = Color(0xFFF2A8A0)        // --erreur-trait
    val SurErreurClair = Color(0xFF410002)     // --sur-erreur-clair
    val Vert = Color(0xFF2E8B4E)               // réglé, à jour
    val Surface2 = Color(0xFFEFEDEC)           // --surface-2 : fonds neutres, puces grises
    val Surface3 = Color(0xFFE7E3E1)           // --surface-3
    val Survol = Color(0xFFF3F1F0)             // --survol
    val Volet = Color(0xFFF0EEED)              // --volet
    val TexteFort2 = Color(0xFF4A4543)         // --texte-2
    val TexteDesactive = Color(0xFFA8A19E)     // --texte-desactive
    val Bord = Color(0xFFD9D3D0)               // --bord
    val BordDoux = Color(0xFFE8E3E1)           // --bord-doux
    val BordFort = Color(0xFF5A5350)           // --bord-fort
}

// Formes Fluent 2 du site (--r-petit 2, --r-moyen 4, --r-grand 6, --r-xgrand 8 ; --r-rond pour puces et +)
object Formes {
    val Petit = RoundedCornerShape(2.dp)
    val Moyen = RoundedCornerShape(4.dp)      // boutons, champs, puces de filtre
    val Grand = RoundedCornerShape(6.dp)
    val XGrand = RoundedCornerShape(8.dp)     // cartes, fenêtres, feuilles
    val Rond = RoundedCornerShape(50)         // puces d'état, bouton +
}

private val schema = lightColorScheme(
    primary = Couleurs.Orange, onPrimary = Color.White,
    primaryContainer = Couleurs.OrangeClair, onPrimaryContainer = Couleurs.SurOrangeClair,
    secondary = Couleurs.Bleu, onSecondary = Color.White,
    secondaryContainer = Couleurs.BleuClair, onSecondaryContainer = Couleurs.SurBleuClair,
    tertiary = Couleurs.Jaune, onTertiary = Couleurs.SurJaune,
    tertiaryContainer = Couleurs.JauneClair, onTertiaryContainer = Couleurs.SurJaune,
    background = Couleurs.Fond, onBackground = Couleurs.Texte,
    surface = Color.White, onSurface = Couleurs.Texte,
    surfaceVariant = Couleurs.Surface2, onSurfaceVariant = Couleurs.Texte2,
    outline = Couleurs.BordFort, outlineVariant = Couleurs.BordDoux,   // contour des champs : contraste 3:1 (WCAG 1.4.11)
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    error = Couleurs.Erreur, errorContainer = Couleurs.ErreurClair,
)

// Formes Fluent 2 (comme le site) : champs et menus 4, puces de filtre 4, cartes, fenêtres et feuilles 8.
// (Avant : grands rayons « Material 3 Expressive », 8 à 36 dp, qui donnaient à l'application un autre aspect que le site.)
private val formes = Shapes(
    extraSmall = Formes.Moyen,
    small = Formes.Moyen,
    medium = Formes.XGrand,
    large = Formes.XGrand,
    extraLarge = Formes.XGrand,
)

@Composable
fun Theme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = schema, shapes = formes, content = content)
