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
    surfaceVariant = Color(0xFFEFEDEC), onSurfaceVariant = Couleurs.Texte2,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    error = Couleurs.Erreur, errorContainer = Couleurs.ErreurClair,
)

// Formes Material 3 Expressive : grands rayons
private val formes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun Theme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = schema, shapes = formes, content = content)
