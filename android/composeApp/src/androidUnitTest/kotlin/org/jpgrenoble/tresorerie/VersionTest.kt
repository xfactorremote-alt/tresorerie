package org.jpgrenoble.tresorerie

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// Le site et l'application portent le même numéro de version ; il est dans les adresses des fichiers du site
// (styles.css?v=…), pour que les téléphones rechargent la nouvelle présentation au lieu de garder l'ancienne.
class VersionTest {
    @Test fun versionDuSiteAlignee() {
        val gradle = File("build.gradle.kts").readText()
        val index = File("../../docs/index.html").readText()
        val v = Regex("versionName = \"([^\"]+)\"").find(gradle)!!.groupValues[1]
        listOf("styles.css", "app.js", "config.js", "icones.js", "graphiques.js", "mock.js").forEach { f ->
            assertTrue("$f?v=$v" in index, "docs/index.html doit appeler $f?v=$v (version de l'application)")
        }
    }
}
