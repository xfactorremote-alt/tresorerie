package org.jpgrenoble.tresorerie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Boutons du site (Fluent 2) : rectangulaires, coins de 4 dp (--r-moyen), au lieu des pilules de Material 3.
// Ces fonctions portent le même nom que celles de Material 3 et sont déclarées dans le paquet de l'application :
// Kotlin les préfère à l'import `androidx.compose.material3.*`, donc tous les écrans les utilisent sans changement.
// Ne pas importer explicitement androidx.compose.material3.Button (ni les autres) dans un écran : cela contournerait la règle.

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Formes.Moyen,
    colors: ButtonColors = ButtonDefaults.buttonColors(), elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Formes.Moyen,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(), elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Formes.Moyen,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(), elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, Couleurs.Bord),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Formes.Moyen,
    colors: ButtonColors = ButtonDefaults.textButtonColors(), elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) =
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content)
