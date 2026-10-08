package org.jpgrenoble.tresorerie

// Règles partagées issues de l'audit du 7 octobre 2026 : corrections traçables,
// états cohérents entre demandes et opérations, contrôle compte / mode de paiement.

// Le motif d'une contre-passation est gardé dans le libellé : « … · motif : … »
const val SEP_MOTIF = " · motif : "
fun motifDe(libelle: String?): String = libelle?.substringAfter(SEP_MOTIF, "")?.trim() ?: ""
fun libelleCorrection(prefixe: String, libelle: String, motif: String): String {
    val fin = if (motif.isBlank()) "" else SEP_MOTIF + motif.trim()
    return "$prefixe$NBSP: $libelle".take(maxOf(20, 120 - fin.length)) + fin.take(100)
}

// Référence courte et stable d'une opération, citée dans l'historique et les échanges
fun refOperation(id: String) = "OP-" + id.replace("-", "").take(6).uppercase()

// Mode de paiement inhabituel pour le compte : espèces sur la banque, virement ou carte sur la caisse
fun incoherenceMode(compte: Compte?, mode: String): String = when {
    compte == null -> ""
    compte.type == "banque" && mode == "especes" -> "Espèces sur le compte « ${compte.nom} » : une opération en espèces passe normalement par la caisse. Vérifiez le compte ou le mode."
    compte.type == "caisse" && mode != "especes" -> "${MODES[mode] ?: mode} sur la caisse : un virement, un chèque ou une carte passe normalement par la banque. Vérifiez le compte ou le mode."
    else -> ""
}

// Demande et opération : paiement neutralisé par contre-passation, ou refus après paiement à régulariser
class EtatsDemandes(demandes: List<Demande>, ecritures: List<Ecriture>) {
    private val operationDe = ecritures.filter { it.demandeId != null && it.contrepasseDe == null }.associateBy { it.demandeId!! }
    private val correctionDe = ecritures.filter { it.contrepasseDe != null }.associateBy { it.contrepasseDe!! }
    fun operation(d: Demande): Ecriture? = operationDe[d.id]
    fun annulation(d: Demande): Ecriture? = operationDe[d.id]?.let { correctionDe[it.id] }
    fun aRegulariser(d: Demande) = d.regularisation && d.statut == "refusee" && operationDe[d.id] != null && annulation(d) == null
    val annulees: Set<String> = demandes.filter { annulation(it) != null }.map { it.id }.toSet()
}
