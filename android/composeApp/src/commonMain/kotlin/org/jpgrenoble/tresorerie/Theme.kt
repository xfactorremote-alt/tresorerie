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
    val Fond = Color(0xFFFFFFFF)
    val Texte = Color(0xFF1B1B1F)
    val Texte2 = Color(0xFF5B5D66)
    // Jetons repris un pour un des variables CSS du site (docs/styles.css, :root) — voir conception/DESIGN_SYSTEM.md
    val OrangeTrait = Color(0xFFF5B39C)        // --primaire-trait
    val JauneTrait = Color(0xFFF2C66A)         // --jaune-trait
    val BleuTrait = Color(0xFF9CCBEA)          // --bleu-trait
    val ErreurTrait = Color(0xFFF2A8A0)        // --erreur-trait
    val SurErreurClair = Color(0xFF410002)     // --sur-erreur-clair
    val Vert = Color(0xFF2E8B4E)               // réglé, à jour
    val Surface2 = Color(0xFFF3F3F5)           // --surface-2 : fonds neutres, puces grises
    val Surface3 = Color(0xFFE9E9EC)           // --surface-3
    val Survol = Color(0xFFF3F3F5)             // --survol
    val Volet = Color(0xFFF7F7F8)              // --volet
    val TexteFort2 = Color(0xFF44464F)         // --texte-2
    val TexteDesactive = Color(0xFFA4A5AC)     // --texte-desactive
    val Bord = Color(0xFFDCDCE0)               // --bord
    val BordDoux = Color(0xFFE7E7E9)           // --bord-doux
    val BordFort = Color(0xFF74757E)           // --bord-fort
    // Page des membres en Material 3 Expressive : mêmes valeurs que les variables --m3-* du site
    val M3Fond = Color(0xFFFFFFFF)             // --m3-fond : blanc neutre, les couleurs des rubriques ressortent
    val M3SurfaceBasse = Color(0xFFF4F4F5)     // --m3-surface-basse
    val M3Contour = Color(0xFFE7E7E9)          // --m3-contour : filet des cartes blanches
    val M3Primaire = Color(0xFFC23E10)         // --m3-primaire
    val M3PrimaireConteneur = Color(0xFFFFDBCF); val M3SurPrimaireConteneur = Color(0xFF3B0A00)
    val M3Bleu = Color(0xFF1B6EA8); val M3BleuConteneur = Color(0xFFD3E6FF); val M3SurBleuConteneur = Color(0xFF001D35)
    val M3Orange = Color(0xFFA3560A); val M3OrangeConteneur = Color(0xFFFFDCC0); val M3SurOrangeConteneur = Color(0xFF2F1500)
    val M3Vert = Color(0xFF2C7A4B); val M3VertConteneur = Color(0xFFC3EFCF); val M3SurVertConteneur = Color(0xFF00210E)
    val M3Violet = Color(0xFF6A4FA3); val M3VioletConteneur = Color(0xFFEADDFF); val M3SurVioletConteneur = Color(0xFF22005D)
    val M3Rose = Color(0xFFA0306B); val M3RoseConteneur = Color(0xFFFFD8E8); val M3SurRoseConteneur = Color(0xFF3E0022)
    val M3Drapeau = Color(0xFFFFE08A); val M3SurDrapeau = Color(0xFF4A2E00)   // « Important » sur la bannière
}

/** Teinte d'une rubrique de la page des membres : couleur forte, conteneur, texte sur conteneur (--g-fort, --g-clair, --g-sur du site). */
data class Teinte(val fort: Color, val conteneur: Color, val sur: Color)
object Teintes {
    val Bleu = Teinte(Couleurs.M3Bleu, Couleurs.M3BleuConteneur, Couleurs.M3SurBleuConteneur)
    val Orange = Teinte(Couleurs.M3Orange, Couleurs.M3OrangeConteneur, Couleurs.M3SurOrangeConteneur)
    val Vert = Teinte(Couleurs.M3Vert, Couleurs.M3VertConteneur, Couleurs.M3SurVertConteneur)
    val Violet = Teinte(Couleurs.M3Violet, Couleurs.M3VioletConteneur, Couleurs.M3SurVioletConteneur)
    val Rose = Teinte(Couleurs.M3Rose, Couleurs.M3RoseConteneur, Couleurs.M3SurRoseConteneur)
    val Primaire = Teinte(Couleurs.M3Primaire, Couleurs.M3PrimaireConteneur, Couleurs.M3SurPrimaireConteneur)
    val Erreur = Teinte(Couleurs.Erreur, Couleurs.ErreurClair, Couleurs.SurErreurClair)
}

// Formes Material 3 Expressive du site (--r-petit 8, --r-moyen 12, --r-grand 16, --r-xgrand 24 ; --r-rond pour boutons et puces)
object Formes {
    val Petit = RoundedCornerShape(8.dp)      // puces de filtre, cases
    val Moyen = RoundedCornerShape(12.dp)     // champs, menus, petites tuiles
    val Grand = RoundedCornerShape(16.dp)     // bouton +, pastilles d'icône
    val XGrand = RoundedCornerShape(24.dp)    // cartes et rubriques
    val Rond = RoundedCornerShape(50)         // puces d'état, bouton +
    // Page des membres (Material 3 Expressive) : --m3-forme-s / m / l / xl du site
    val M3S = RoundedCornerShape(12.dp)
    val M3M = RoundedCornerShape(16.dp)
    val M3L = RoundedCornerShape(24.dp)
    val M3XL = RoundedCornerShape(28.dp)
}

private val schema = lightColorScheme(
    primary = Couleurs.Orange, onPrimary = Color.White,
    primaryContainer = Couleurs.OrangeClair, onPrimaryContainer = Couleurs.SurOrangeClair,
    secondary = Couleurs.Bleu, onSecondary = Color.White,
    // Conteneur « secondaire » = teinte douce de l'association : boutons tonals, indicateur de la barre d'onglets, puces choisies (comme le site)
    secondaryContainer = Couleurs.OrangeClair, onSecondaryContainer = Couleurs.SurOrangeClair,
    tertiary = Couleurs.Jaune, onTertiary = Couleurs.SurJaune,
    tertiaryContainer = Couleurs.JauneClair, onTertiaryContainer = Couleurs.SurJaune,
    background = Couleurs.Fond, onBackground = Couleurs.Texte,
    surface = Color.White, onSurface = Couleurs.Texte,
    surfaceVariant = Couleurs.Surface2, onSurfaceVariant = Couleurs.Texte2,
    outline = Couleurs.BordFort, outlineVariant = Couleurs.BordDoux,   // contour des champs : contraste 3:1 (WCAG 1.4.11)
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    error = Couleurs.Erreur, errorContainer = Couleurs.ErreurClair,
)

// Formes Material 3 Expressive (comme le site depuis la 0.44.0) : champs 12, puces 8, cartes 24, fenêtres et feuilles 28.
private val formes = Shapes(
    extraSmall = Formes.Moyen,
    small = Formes.Petit,
    medium = Formes.Moyen,
    large = Formes.XGrand,
    extraLarge = Formes.M3XL,
)

@Composable
fun Theme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = schema, shapes = formes, content = content)
