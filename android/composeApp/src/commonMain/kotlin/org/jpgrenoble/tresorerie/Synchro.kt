package org.jpgrenoble.tresorerie

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Temps réel (même règle que demarrerTempsReel / rafraichirDoucement du site) : la base prévient quand une donnée
 * change ; les changements sont regroupés pendant un court instant, puis chaque écran ouvert recharge ses données
 * sans écran de chargement (les écrans ajoutent [version] à la clé de leur chargement). Les référentiels (membres,
 * comptes, catégories, réglages…) sont rechargés seulement quand ils changent ([versionReferentiels]).
 * Sans temps réel (réseau qui bloque), vérification douce toutes les 45 secondes.
 */
object Synchro {
    var version by mutableStateOf(0)
        private set
    var versionReferentiels by mutableStateOf(0)
        private set
    private var job: Job? = null
    private val REFERENTIELS = setOf("members", "accounts", "categories", "organisation", "settings", "exercices", "liens_membres", "tiers", "projects", "profiles")
    private val enAttente = mutableSetOf<String>()
    private var minuterie: Job? = null

    fun signaler(scope: CoroutineScope, table: String) {
        enAttente += table
        minuterie?.cancel()
        minuterie = scope.launch {
            delay(900)
            val tables = enAttente.toSet(); enAttente.clear()
            if (tables.any { it in REFERENTIELS || it == "*" }) versionReferentiels++
            version++
            EtatNouveautes.charger()
        }
    }

    // Tables suivies (mêmes que la publication « supabase_realtime » de la base)
    private val TABLES = listOf("transactions", "expense_requests", "projects", "collectes", "collecte_membres", "members", "communiques",
        "cotisations", "budgets", "materiel", "settings", "organisation", "accounts", "categories", "tiers", "exercices", "liens_membres")

    fun demarrer(scope: CoroutineScope) {
        if (job?.isActive == true || Repo.demo) return
        job = scope.launch {
            try {
                val canal = Repo.client.channel("changements")
                TABLES.forEach { t ->
                    val flux = canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = t }
                    launch { flux.collect { signaler(scope, t) } }
                }
                canal.subscribe()
            } catch (_: Exception) {
                while (true) { delay(45_000); signaler(scope, "*") }
            }
        }
    }

    fun arreter() { job?.cancel(); job = null; try { kotlinx.coroutines.MainScope().launch { Repo.client.realtime.removeAllChannels() } } catch (_: Exception) { } }

    /** Démo et tests : une écriture locale prévient les écrans comme le ferait la base. */
    fun localement(scope: CoroutineScope, table: String) = signaler(scope, table)
}
