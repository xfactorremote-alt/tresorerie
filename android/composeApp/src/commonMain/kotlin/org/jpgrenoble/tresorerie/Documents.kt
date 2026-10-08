package org.jpgrenoble.tresorerie

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// =====================================================================
// Documents PDF : même mise en page que le site (en-tête avec logo, synthèse,
// tableau avec totaux, signatures, pied de page numéroté). Imprimés par le
// service d'impression d'Android, qui propose « Enregistrer au format PDF ».
// =====================================================================

private fun e(s: String?) = (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

@OptIn(ExperimentalEncodingApi::class)
private fun logoDataUri(): String {
    val o = Repo.logo.value ?: return ""
    val mime = if (o.size > 3 && o[0] == 0x89.toByte() && o[1] == 0x50.toByte()) "image/png" else "image/jpeg"
    return "data:$mime;base64," + Base64.encode(o)
}

private fun cssDocument(nom: String, titre: String) = """
@page{size:A4;margin:14mm 12mm 16mm;@bottom-left{content:"${nom.replace("\"", " ")} · ${titre.replace("\"", " ")}";font:9px sans-serif;color:#5A5350}@bottom-right{content:"Page " counter(page) " sur " counter(pages);font:9px sans-serif;color:#5A5350}}
body{font-family:sans-serif;color:#1C1B1A;font-size:11px;margin:0}
.doc-entete{display:flex;gap:16px;align-items:center;padding-bottom:14px;margin-bottom:16px;border-bottom:3px solid #C23E10}
.doc-entete img{width:58px;height:58px;border-radius:50%;object-fit:cover}
.doc-entete .t{flex:1;display:flex;flex-direction:column;gap:2px}
.doc-asso{font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:#C23E10}
h1{font-size:20px;margin:0}h2{font-size:14px;margin:18px 0 6px;color:#C23E10}
.doc-periode,.doc-edition,.m{color:#5A5350}.doc-edition{font-size:10px;text-align:right}
.doc-synthese{display:flex;gap:8px;margin-bottom:16px}.doc-synthese div{flex:1;background:#F6F6F6;border-radius:10px;padding:8px 10px;display:flex;flex-direction:column;-webkit-print-color-adjust:exact}
.doc-synthese span{font-size:10px;color:#5A5350}.doc-synthese b{font-size:14px}
table{width:100%;border-collapse:collapse;font-size:10.5px}
th{background:#1C1B1A;color:#fff;text-align:left;padding:6px;-webkit-print-color-adjust:exact}
td{padding:5px 6px;border-bottom:1px solid #E5E0DE;vertical-align:top}
tbody tr:nth-child(even) td{background:#FAF8F7;-webkit-print-color-adjust:exact}
tr{break-inside:avoid}tfoot{display:table-row-group}tfoot td{border-top:2px solid #1C1B1A;border-bottom:0;background:#fff}
.d{text-align:right;white-space:nowrap}.nw{white-space:nowrap}
.sig{display:flex;gap:40px;margin-top:40px;break-inside:avoid}.sig div{flex:1;border-top:1px solid #1C1B1A;padding-top:6px;height:70px}
"""

private fun page(d: Donnees, titre: String, periode: String, corps: String): String {
    val logo = logoDataUri()
    return """<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>${e(d.organisation.nom)} - ${e(titre)}</title><style>${cssDocument(d.organisation.nom, titre)}</style></head><body>
<header class="doc-entete">${if (logo.isNotEmpty()) "<img src=\"$logo\" alt=\"\">" else ""}<div class="t"><span class="doc-asso">${e(d.organisation.nom)}</span><h1>${e(titre)}</h1><span class="doc-periode">$periode</span></div>
<div class="doc-edition">Édité le ${dateFr(aujourdhui().toString())}<br>par ${e(d.profil.nom)}</div></header>
$corps</body></html>"""
}

private fun synthese(vararg tuiles: Triple<String, String, String?>) =
    "<div class=\"doc-synthese\">" + tuiles.joinToString("") { (l, v, c) -> "<div><span>$l</span><b${c?.let { " style=\"color:$it\"" } ?: ""}>$v</b></div>" } + "</div>"

// colonnes : (libellé, classe) ; la classe « d » aligne les montants à droite
private fun table(colonnes: List<Pair<String, String>>, lignes: List<List<String>>, totaux: List<String>? = null): String {
    if (lignes.isEmpty()) return "<p class=\"m\">Aucune ligne.</p>"
    fun cl(i: Int) = colonnes[i].second.let { if (it.isEmpty()) "" else " class=\"$it\"" }
    return "<table><thead><tr>" + colonnes.mapIndexed { i, c -> "<th${cl(i)}>${c.first}</th>" }.joinToString("") + "</tr></thead><tbody>" +
        lignes.joinToString("") { l -> "<tr>" + l.mapIndexed { i, v -> "<td${cl(i)}>$v</td>" }.joinToString("") + "</tr>" } + "</tbody>" +
        (totaux?.let { t -> "<tfoot><tr>" + t.mapIndexed { i, v -> "<td${cl(i)}>$v</td>" }.joinToString("") + "</tr></tfoot>" } ?: "") + "</table>"
}

private const val SIGNATURES = "<div class=\"sig\"><div>Le trésorier</div><div>Le président</div></div>"

/** Types : journal, cotisations, budget, demandes, membres. */
suspend fun documentHtml(d: Donnees, type: String, an: Int, du: String? = null, au: String? = null, intitule: String? = null,
                         compte: String? = null, sens: String? = null, categorie: String? = null): Pair<String, String> = when (type) {
    "journal" -> {
        val toutes = Repo.toutesEcritures().sortedBy { it.date }
        val comptes = try { Repo.tousLesComptes() } catch (_: Exception) { d.comptes }
        val avecPiece = Repo.pieces().mapNotNull { it.transactionId }.toSet()
        val ms = d.membres; val ts = try { Repo.tiers() } catch (_: Exception) { emptyList() }
        val debut = du ?: "$an-01-01"; val fin = au ?: "$an-12-31"
        val vus = comptes.filter { compte == null || it.id == compte }
        val soldeDebut = vus.sumOf { it.soldeInitial } + toutes.filter { it.date < debut && vus.any { c -> c.id == it.compteId } }.sumOf { it.signe }
        val lignes = toutes.filter { it.date in debut..fin && (compte == null || it.compteId == compte) && (sens == null || it.sens == sens) && (categorie == null || it.categorieId == categorie) }
        // Les virements internes figurent au journal mais pas dans les totaux de recettes et de dépenses
        val rec = lignes.filter { it.sens == "recette" && it.estFlux }.sumOf { it.montant }; val dep = lignes.filter { it.sens == "depense" && it.estFlux }.sumOf { it.montant }
        val titre = "Journal des opérations, " + (intitule ?: "exercice $an")
        titre to page(d, titre, "Du ${dateFr(debut)} au ${dateFr(fin)}",
            synthese(Triple("Solde au début", euros(soldeDebut), null), Triple("Recettes", euros(rec), "#1B77B0"), Triple("Dépenses", euros(dep), "#C23E10"), Triple("Solde à la fin", euros(soldeDebut + rec - dep), null)) +
            table(listOf("N°" to "nw", "Date" to "nw", "Libellé" to "", "Tiers" to "", "Catégorie" to "", "Compte" to "", "Pièce" to "", "Recette" to "d", "Dépense" to "d"),
                lignes.mapIndexed { i, t ->
                    listOf("${i + 1}", dateFr(t.date), e(t.libelle) + if (t.virement != null) " <span class=\"muted\">(virement interne, hors totaux)</span>" else "", e(nomTiers(t, ms, ts)), e(d.nomCategorie(t.categorieId)),
                        e(comptes.firstOrNull { it.id == t.compteId }?.nom), if (t.sens == "depense" && t.estFlux && t.montant > 0) (if (t.id in avecPiece) "Oui" else "<b>Non</b>") else "",
                        if (t.sens == "recette") euros(t.montant) else "", if (t.sens == "depense") euros(t.montant) else "")
                }, listOf("", "", "<b>${lignes.size} opérations</b>", "", "", "", "", "<b>${euros(rec)}</b>", "<b>${euros(dep)}</b>")) + SIGNATURES)
    }
    "cotisations" -> {
        val l = Repo.cotisations(an); val ms = d.membres.ifEmpty { Repo.membres() }
        val pas = try { (Repo.reglages()["cotisation_periode_mois"] ?: 1.0).toInt() } catch (_: Exception) { 1 }
        fun tot(f: (Cotisation) -> Double) = l.sumOf(f)
        val lignes = l.map { c -> c to ms.firstOrNull { it.id == c.membreId } }.sortedBy { it.second?.nomComplet ?: "" }
        val titre = "État des cotisations $an"
        titre to page(d, titre, "Arrêté au ${dateFr(aujourdhui().toString())} · périodicité ${(PERIODICITES[pas] ?: "").lowercase()}",
            synthese(Triple("Exigible à ce jour", euros(tot { it.exigible ?: 0.0 }), null), Triple("Encaissé", euros(tot { it.paye }), "#1B77B0"),
                Triple("En retard", euros(tot { it.retard }), if (tot { it.retard } > 0) "#BA1A1A" else null), Triple("Membres à jour", "${l.count { it.statut == "a_jour" }} sur ${l.size}", null)) +
            table(listOf("Membre" to "", "Dû sur l’année" to "d", "Exigible" to "d", "Réglé" to "d", "Retard" to "d", "Réglé jusqu’à" to "", "Situation" to ""),
                lignes.map { (c, m) -> listOf(e(m?.nomComplet ?: "?"), euros(c.du), euros(c.exigible ?: 0.0), euros(c.paye), if (c.retard > 0) "<b>${euros(c.retard)}</b>" else "–",
                    c.regleJusqua?.let { nomPeriode(it, pas) } ?: "–", when (c.statut) { "a_jour" -> "À jour"; "partiel" -> "En retard"; else -> "Impayé" }) },
                listOf("<b>Total</b>", "<b>${euros(tot { it.du })}</b>", "<b>${euros(tot { it.exigible ?: 0.0 })}</b>", "<b>${euros(tot { it.paye })}</b>", "<b>${euros(tot { it.retard })}</b>", "", "")) +
            "<p class=\"m\">Les versements sont imputés sur la période la plus ancienne non réglée.</p>" + SIGNATURES)
    }
    "budget" -> {
        // Même contenu que pdfBudget() du site : postes non internes, réalisé N-1, activités
        val x = chargerBudget(an, d.categoriesToutes)
        fun pct(r: Double, p: Double) = if (p > 0) "${kotlin.math.round(100 * r / p).toInt()}$NBSP%" else "–"
        fun bloc(postes: List<PosteBudget>, titre: String, sens: String): String {
            val l = postes.filter { it.prevu != 0.0 || it.realise != 0.0 || it.n1 != 0.0 }
            val tp = l.sumOf { it.prevu }; val tr = l.sumOf { it.realise }; val tn = l.sumOf { it.n1 }
            return "<h2>$titre</h2>" + table(listOf("Poste" to "", "Prévu" to "d", "Réalisé" to "d", "Écart" to "d", "Taux" to "d", "Réalisé ${an - 1}" to "d"),
                l.map { listOf(e(it.cat.nom), euros(it.prevu), euros(it.realise), euros(it.realise - it.prevu), pct(it.realise, it.prevu) + if (sens == "depense" && it.prevu > 0 && it.realise > it.prevu) " <b>dépassé</b>" else "", euros(it.n1)) },
                listOf("<b>Total</b>", "<b>${euros(tp)}</b>", "<b>${euros(tr)}</b>", "<b>${euros(tr - tp)}</b>", "<b>${pct(tr, tp)}</b>", "<b>${euros(tn)}</b>"))
        }
        val eq = x.resPrevu - x.empPrevu
        val activites = if (x.activites.isEmpty()) "" else "<h2>Activités</h2>" + table(listOf("Activité" to "", "Ressources prévues" to "d", "Ressources réalisées" to "d", "Emplois prévus" to "d", "Emplois réalisés" to "d", "Résultat" to "d"),
            x.activites.map { listOf(e(it.p.nom), euros(it.resPrevu), euros(it.resReel), euros(it.empPrevu), euros(it.empReel), euros(it.resReel - it.empReel)) })
        val titre = "Budget $an : prévu et réalisé"
        titre to page(d, titre, "Arrêté au ${dateFr(aujourdhui().toString())}",
            synthese(Triple("Ressources prévues", euros(x.resPrevu), "#1B77B0"), Triple("Emplois prévus", euros(x.empPrevu), "#C23E10"),
                Triple(if (eq >= 0) "Excédent prévu" else "Déficit prévu", euros(kotlin.math.abs(eq)), if (eq < 0) "#BA1A1A" else null), Triple("Résultat réalisé", euros(x.resReel - x.empReel), null)) +
            bloc(x.ressources, "Ressources (recettes)", "recette") + bloc(x.emplois, "Emplois (dépenses)", "depense") + activites +
            "<p class=\"m\">Le réalisé reprend toutes les opérations de l’exercice par catégorie, activités comprises.</p>" + SIGNATURES)
    }
    "demandes" -> {
        val l = Repo.demandes().filter { it.creeLe.startsWith("$an") }.sortedBy { it.creeLe }
        val profils = try { Repo.profilsComplets() } catch (_: Exception) { emptyList() }
        fun nom(id: String?) = profils.firstOrNull { it.id == id }?.nom ?: ""
        val situation = mapOf("soumise" to "À valider", "validee" to "À payer", "refusee" to "Refusée", "payee" to "Justificatif attendu", "justifiee" to "Clôturée", "annulee" to "Annulée", "brouillon" to "Brouillon")
        val titre = "Registre des demandes de dépense $an"
        titre to page(d, titre, "${l.size} demandes · ${euros(l.filter { it.statut in listOf("payee", "justifiee") }.sumOf { it.montant })} payés",
            table(listOf("Date" to "nw", "Objet" to "", "Demandeur" to "", "Montant" to "d", "Situation" to "", "Validée le" to "nw", "Par" to "", "Payée le" to "nw", "Empreinte" to ""),
                l.map { r -> listOf(dateFr(r.creeLe.take(10)), e(r.objet) + if (r.regularisation) " <span class=\"m\">(déjà payée)</span>" else "", e(nom(r.demandeur)), euros(r.montant),
                    situation[r.statut] ?: r.statut, r.valideeLe?.let { dateFr(it.take(10)) } ?: "", e(nom(r.valideePar)), r.payeeLe?.let { dateFr(it.take(10)) } ?: "",
                    r.empreinte?.let { "<span class=\"m\">${it.take(12)}…</span>" } ?: "") }) +
            "<p class=\"m\">L’empreinte SHA-256 relie chaque signature au montant et à l’objet validés.</p>" + SIGNATURES)
    }
    "inventaire" -> {
        val tous = Repo.materiel()
        val items = tous.filter { it.sortiLe == null }
        val sortis = tous.filter { it.sortiLe != null && it.sortiLe >= "${aujourdhui().year}-01-01" }
        val propres = items.filter { it.origine != "pret" }
        val va = propres.sumOf { it.valeurAcquisition ?: 0.0 }; val vc = propres.sumOf { it.valeurActuelle ?: it.valeurAcquisition ?: 0.0 }
        fun nom(id: String?) = d.membres.firstOrNull { it.id == id }?.nomComplet ?: ""
        val blocs = CATEGORIES_MATERIEL.filterKeys { c -> items.any { it.categorie == c } }.map { (c, l) ->
            "<h2>${e(l)}</h2>" + table(listOf("Désignation" to "", "Qté" to "d", "N° de série" to "", "Origine" to "", "Entrée" to "nw", "Valeur d’achat" to "d", "Valeur actuelle" to "d", "État" to "", "Lieu ou détenteur" to "", "Vérifié" to "nw"),
                items.filter { it.categorie == c }.map { x -> listOf(e(x.designation) + (x.marque?.let { "<br><span class=\"m\">${e(it)}</span>" } ?: ""), "${x.quantite}", e(x.numeroSerie), ORIGINES_MATERIEL[x.origine] ?: "",
                    x.dateAcquisition?.let(::dateFr) ?: "", if (x.origine == "pret") "–" else euros(x.valeurAcquisition ?: 0.0), if (x.origine == "pret") "–" else euros(x.valeurActuelle ?: x.valeurAcquisition ?: 0.0),
                    ETATS_MATERIEL[x.etat] ?: "", e(x.detenteurId?.let { "Chez " + nom(it) } ?: x.lieu), x.verifieLe?.let(::dateFr) ?: "<b>non</b>") })
        }.joinToString("")
        val titre = "Inventaire du matériel"
        titre to page(d, titre, "Arrêté au ${dateFr(aujourdhui().toString())}",
            synthese(Triple("Articles en service", "${items.sumOf { it.quantite }}", null), Triple("Valeur d’achat", euros(va), null), Triple("Valeur actuelle estimée", euros(vc), null),
                Triple("Non vérifiés depuis un an", "${items.count(::aVerifier)}", null)) +
            blocs.ifEmpty { "<p class=\"m\">Aucun article.</p>" } +
            (if (sortis.isNotEmpty()) "<h2>Sorties de l’exercice</h2>" + table(listOf("Désignation" to "", "Date" to "nw", "Motif" to ""), sortis.map { listOf(e(it.designation), dateFr(it.sortiLe!!), MOTIFS_SORTIE[it.motifSortie] ?: "") }) else "") +
            "<p class=\"m\">Le matériel prêté par un tiers figure pour mémoire, sans valeur. Les valeurs actuelles sont des estimations du bureau.</p>" + SIGNATURES)
    }
    else -> {
        val ms = Repo.membres().filter { it.actif }
        val profils = try { Repo.profilsComplets() } catch (_: Exception) { emptyList() }
        fun fonction(m: Membre) = profils.firstOrNull { it.memberId == m.id }?.let { d.nomRole(it.role) } ?: ""
        val titre = "Liste des membres"
        titre to page(d, titre, "${ms.size} membres actifs au ${dateFr(aujourdhui().toString())}",
            table(listOf("Membre" to "", "Fonction" to "", "Anniversaire" to "", "Profession" to "", "WhatsApp" to "", "E-mail" to ""),
                ms.sortedWith(compareBy({ if (fonction(it).isEmpty() || fonction(it) == "Adhérent") 1 else 0 }, { it.nomComplet })).map {
                    listOf(e(it.nomComplet), e(fonction(it)), "${it.jour} ${MOIS[it.mois - 1]}", e(it.profession), e(it.whatsapp), e(it.email))
                }) + "<p class=\"m\">Document interne : données personnelles à ne pas diffuser en dehors du bureau.</p>")
    }
}

/** Archive ZIP des pièces justificatives d'un exercice, classées par mois, avec inventaire. */
suspend fun archivePieces(d: Donnees, an: Int, progression: (String) -> Unit): Pair<ByteArray, Int> {
    val txs = Repo.toutesEcritures().filter { it.date.startsWith("$an") }.sortedBy { it.date }
    val pieces = Repo.pieces()
    val rapps = try { Repo.rapprochements().filter { it.fin.startsWith("$an") && it.releve != null } } catch (_: Exception) { emptyList() }
    val ms = d.membres; val ts = try { Repo.tiers() } catch (_: Exception) { emptyList() }
    fun propre(s: String) = s.lowercase().map { c -> val i = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿœæ".indexOf(c); if (i >= 0) "aaaaaaceeeeiiiinooooouuuuyyoa"[i] else c }
        .joinToString("").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50)
    fun montant(v: Double) = v.toString().let { if (it.contains('.')) it else "$it.0" }.let { val (a, b) = it.split('.'); "$a,${b.padEnd(2, '0').take(2)}" }
    val dossier = "Pieces-justificatives-$an"
    val fichiers = mutableListOf<Pair<String, ByteArray>>()
    val inventaire = mutableListOf(listOf("Date", "Libellé", "Tiers", "Catégorie", "Montant", "Fichier"))
    val annulees = txs.mapNotNull { it.contrepasseDe }.toSet()
    var n = 0
    txs.forEach { t ->
        val p = pieces.filter { it.transactionId == t.id }
        val cat = d.categories.firstOrNull { it.id == t.categorieId }?.nom ?: ""
        val mois = "${t.date.substring(5, 7)}-${MOIS[t.date.substring(5, 7).toInt() - 1]}"
        if (p.isEmpty()) {
            if (t.sens == "depense" && t.estFlux && t.montant > 0 && t.contrepasseDe == null && t.id !in annulees) inventaire += listOf(dateFr(t.date), t.libelle, nomTiers(t, ms, ts), cat, montant(t.montant), "MANQUANTE")
            return@forEach
        }
        p.forEachIndexed { i, a ->
            n++; progression("Pièce $n…")
            val ext = a.chemin.substringAfterLast('.', "jpg").lowercase().take(4)
            val nom = "${t.date}_${montant(t.montant)}EUR_${propre(t.libelle)}${if (p.size > 1) "_${i + 1}" else ""}.$ext"
            val octets = try { Repo.telecharger("justificatifs", a.chemin) } catch (_: Exception) { null }
            if (octets != null) { fichiers += "$dossier/$mois/$nom" to octets; inventaire += listOf(dateFr(t.date), t.libelle, nomTiers(t, ms, ts), cat, montant(t.montant), "$mois/$nom") }
            else inventaire += listOf(dateFr(t.date), t.libelle, nomTiers(t, ms, ts), cat, montant(t.montant), "ILLISIBLE")
        }
    }
    rapps.forEach { r ->
        val octets = try { Repo.telecharger("releves", r.releve!!) } catch (_: Exception) { null } ?: return@forEach
        val compte = d.comptes.firstOrNull { it.id == r.compteId }?.nom ?: "compte"
        fichiers += "$dossier/Releves-et-PV-de-caisse/${r.fin}_${propre(compte)}.${r.releve!!.substringAfterLast('.', "pdf").take(4)}" to octets
    }
    fun cel(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
    fichiers += "$dossier/inventaire.csv" to ("﻿" + inventaire.joinToString("\r\n") { l -> l.joinToString(";") { cel(it) } }).encodeToByteArray()
    progression("Compression…")
    return zipper(fichiers) to n
}
