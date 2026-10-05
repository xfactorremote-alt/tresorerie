package org.jpgrenoble.tresorerie

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

// Logo choisi par le trésorier dans les paramètres, sinon logo livré avec l'application
@Composable
actual fun LogoAsso(modifier: Modifier) {
    val octets by Repo.logo.collectAsState()
    val image = remember(octets) { octets?.let { imageDepuisOctets(it) } }
    if (image != null) {
        Image(bitmap = image, contentDescription = "Logo de l’association", contentScale = ContentScale.Crop, modifier = modifier.clip(CircleShape))
    } else {
        Image(painter = painterResource(R.drawable.logo_asso), contentDescription = "Logo de l’association",
            contentScale = ContentScale.Crop, modifier = modifier.clip(CircleShape))
    }
}
