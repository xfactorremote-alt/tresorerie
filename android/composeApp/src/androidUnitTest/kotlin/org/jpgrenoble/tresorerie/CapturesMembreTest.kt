package org.jpgrenoble.tresorerie

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

// Captures d'écran de contrôle de la page des membres (démonstration), pour comparer avec le site.
// Lancement : CAPTURES=1 ./gradlew :composeApp:testDebugUnitTest --tests '*CapturesMembreTest*'
// Images dans composeApp/build/captures/. Ignoré sinon (le reste des tests n'en dépend pas).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturesMembreTest {
    @get:Rule val compose = createComposeRule()

    private fun capturer(nom: String) {
        assumeTrue(System.getenv("CAPTURES") == "1")
        ContexteApp.contexte = ApplicationProvider.getApplicationContext()
        // Démonstration : le dépôt lit les données fictives (Demo) au lieu de la base
        Repo.demo = true
        val p = Demo.connecter("adherent@demo.jp", Demo.MOT_DE_PASSE)
        Repo.profilDemo.value = p
        val d = Donnees(p, Demo.organisation, emptyList(), Demo.categories, Demo.membres.toList(), Demo.droitsDe(p), Demo.roles.toList())
        compose.setContent { Theme { EcranAdherent(d) } }
        compose.mainClock.advanceTimeBy(4000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/captures/$nom.png")
    }

    @Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
    @Test fun pageMembreTelephone() = capturer("membre-telephone")

    @Config(sdk = [34], qualifiers = "w1100dp-h1400dp-mdpi")
    @Test fun pageMembreTablette() = capturer("membre-tablette")
}
