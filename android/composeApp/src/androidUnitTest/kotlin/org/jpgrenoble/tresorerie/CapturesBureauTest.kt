package org.jpgrenoble.tresorerie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Captures de contrôle des écrans du bureau (trésorier, démonstration), pour comparer avec le site.
// Lancement : CAPTURES=1 ./gradlew :composeApp:testDebugUnitTest --tests '*CapturesBureauTest*'
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class CapturesBureauTest {
    @get:Rule val compose = createComposeRule()

    private fun capturer(nom: String, ecran: @Composable (Donnees) -> Unit) {
        assumeTrue(System.getenv("CAPTURES") == "1")
        ContexteApp.contexte = ApplicationProvider.getApplicationContext()
        Repo.demo = true
        val p = Demo.connecter("tresorier@demo.jp", Demo.MOT_DE_PASSE)
        Repo.profilDemo.value = p
        Repo.banniere.value = java.io.File("../../docs/demo/photo2.jpg").readBytes()
        val d = Donnees(p, Demo.organisation, Demo.comptes.toList(), Demo.categories, Demo.membres.toList(), Demo.droitsDe(p), Demo.roles.toList())
        compose.setContent { Theme { Box(Modifier.fillMaxSize().background(Couleurs.Fond)) { ecran(d) } } }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/captures/bureau-$nom.png")
    }

    @Test fun accueil() = capturer("accueil") { EcranAccueil(it) }
    @Test fun operations() = capturer("operations") { EcranOperations(it, {}) }
    @Test fun demandes() = capturer("demandes") { EcranDepenses(it, {}) }
    @Test fun cotisations() = capturer("cotisations") { EcranCotisations(it, {}) }
    @Test fun membres() = capturer("membres") { EcranMembres(it, {}) }
    @Test fun planning() = capturer("planning") { EcranPlanning(it, {}) }
    @Test fun parametres() = capturer("parametres") { EcranParametres(it, {}, {}) }
}
