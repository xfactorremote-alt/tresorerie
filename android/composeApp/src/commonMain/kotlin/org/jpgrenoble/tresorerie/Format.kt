package org.jpgrenoble.tresorerie

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong

val MOIS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
private const val ESPACE_FINE = ' '

fun aujourdhui(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

// 1234.5 -> « 1 234,50 € » (espace fine insécable, usage français)
fun euros(valeur: Double): String {
    val centimes = (abs(valeur) * 100).roundToLong()
    val entier = (centimes / 100).toString().reversed().chunked(3).joinToString(ESPACE_FINE.toString()).reversed()
    val dec = (centimes % 100).toString().padStart(2, '0')
    return (if (valeur < 0) "−" else "") + "$entier,$dec$ESPACE_FINE€"
}

const val NBSP = '\u00A0'
const val NBSP_FINE = '\u202F'

// 20.0 -> « 20,00 » pour préremplir un champ montant
fun montantSaisie(v: Double): String {
    val c = (abs(v) * 100).roundToLong()
    return "${c / 100},${(c % 100).toString().padStart(2, '0')}"
}

// « d’octobre », « de mars »
fun deMois(mois: Int): String { val m = MOIS[mois - 1]; return if (m.first() in "aeiouéèâ") "d’$m" else "de $m" }

// « 03/10/2026 » -> 2026-10-03, ou null si la date n'existe pas
fun dateDepuisFr(saisie: String): LocalDate? {
    val p = saisie.trim().split('/', '-', '.', ' ').filter { it.isNotEmpty() }
    if (p.size != 3) return null
    val (j, m, a) = Triple(p[0].toIntOrNull(), p[1].toIntOrNull(), p[2].toIntOrNull())
    if (j == null || m == null || a == null) return null
    val annee = if (a < 100) 2000 + a else a
    return runCatching { LocalDate(annee, m, j) }.getOrNull()
}

fun dateFr(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
}

// 06 12 34 56 78 -> 33612345678 (format wa.me)
fun numeroWa(n: String?): String? {
    var d = (n ?: return null).filter { it.isDigit() || it == '+' }
    d = when {
        d.startsWith("+") -> d.drop(1)
        d.startsWith("00") -> d.drop(2)
        Regex("^0[1-9]\\d{8}$").matches(d) -> "33" + d.drop(1)
        else -> d
    }
    return if (Regex("^\\d{8,15}$").matches(d)) d else null
}

fun encoderUrl(s: String): String = buildString {
    s.encodeToByteArray().forEach { b ->
        val c = b.toInt().toChar()
        if (c.isLetterOrDigit() && b >= 0 || c in "-_.~") append(c)
        else append("%" + (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}

fun traduireErreur(e: Throwable): String {
    val m = e.message ?: e.toString()
    return when {
        "Invalid login credentials" in m -> "Adresse ou mot de passe incorrect"
        "Email not confirmed" in m -> "Adresse pas encore confirmée. Ouvrez le lien reçu par e-mail."
        "row-level security" in m || "permission denied" in m -> "Action réservée à un autre rôle"
        "Réservé au trésorier" in m -> "Action réservée au trésorier"
        "foreign key" in m && "role" in m -> "Ce rôle est encore attribué à quelqu’un"
        "duplicate key" in m -> "Cet élément existe déjà"
        "Unable to resolve host" in m || "UnknownHost" in m || "timeout" in m.lowercase() -> "Connexion perdue. Réessayez."
        else -> m.lineSequence().firstOrNull() ?: m
    }
}

// « Trésorier adjoint » -> tresorier_adjoint (identifiant d'un rôle)
fun sansAccentsCode(s: String): String {
    val de = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿœæ"; val vers = "aaaaaaceeeeiiiinooooouuuuyyoa"
    val base = s.lowercase().trim().map { c -> val i = de.indexOf(c); if (i >= 0) vers[i] else c }.joinToString("")
    return base.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "role" }
}
