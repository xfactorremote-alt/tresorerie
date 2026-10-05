package org.jpgrenoble.tresorerie

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Données fictives du mode démonstration. Rien n'est enregistré : tout repart
// à zéro à la fermeture de l'application.
object Demo {
    const val MOT_DE_PASSE = "Demo2026"

    class CompteDemo(val email: String, val profil: Profil)

    val comptesTest = listOf(
        CompteDemo("tresorier@demo.jp", Profil("u-t", "Paul Ndongo", "tresorier")),
        CompteDemo("president@demo.jp", Profil("u-p", "Jean-Marc Ilunga", "president", memberId = "m10")),
        CompteDemo("bureau@demo.jp", Profil("u-b", "Marthe Kalala", "bureau", memberId = "m11")),
        CompteDemo("adherent@demo.jp", Profil("u-a", "Grâce Mbala", "adherent", memberId = "m0")),
    )

    var organisation = Organisation("JP Grenoble")
    val fichiers = mutableMapOf<String, ByteArray>()
    val reglages = mutableMapOf("cotisation_montant" to 20.0, "cotisation_periode_mois" to 1.0, "delai_justificatif_jours" to 7.0, "seuil_alerte_budget_pct" to 90.0)

    // Profils modifiables (rôle, accès) ; les comptes de test pointent vers ces profils
    private val profils = comptesTest.map { it.profil }.toMutableList()
    val profilsAjoutes = mutableListOf<Profil>()
    val invitations = mutableListOf<Invitation>()

    fun connecter(email: String, motDePasse: String): Profil {
        val c = comptesTest.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
        if (c == null || motDePasse != MOT_DE_PASSE) throw IllegalStateException("Invalid login credentials")
        val p = profils.first { it.id == c.profil.id }
        if (!p.actif) throw IllegalStateException("Accès désactivé par le trésorier")
        return p
    }

    // ---------- Rôles et droits (mêmes valeurs de départ que schema.sql) ----------
    val permissions = listOf(
        Permission("consulter_finances", "Voir les soldes, écritures, budget et rapports", "Finances", 1),
        Permission("saisir_ecritures", "Saisir et corriger les écritures, joindre les pièces", "Finances", 2),
        Permission("gerer_cotisations", "Générer, encaisser et relancer les cotisations", "Finances", 3),
        Permission("rapprocher", "Rapprocher la caisse et la banque", "Finances", 4),
        Permission("gerer_budget", "Construire et modifier le budget", "Finances", 5),
        Permission("demander_depenses", "Demander une dépense", "Dépenses", 6),
        Permission("valider_depenses", "Valider ou refuser une dépense (signature)", "Dépenses", 7),
        Permission("payer_depenses", "Payer une dépense validée", "Dépenses", 8),
        Permission("voir_membres", "Voir la liste des membres", "Membres", 9),
        Permission("gerer_membres", "Ajouter, modifier et importer des membres", "Membres", 10),
        Permission("gerer_activites", "Créer et modifier les activités", "Activités", 11),
        Permission("administrer", "Paramètres, rôles et accès", "Administration", 12),
    )
    val roles = mutableListOf(Role("tresorier", "Trésorier", true), Role("president", "Président", true),
        Role("bureau", "Bureau", true), Role("adherent", "Adhérent", true))
    val rolePermissions = (permissions.filter { it.code != "valider_depenses" }.map { RolePermission("tresorier", it.code) } +
        listOf("consulter_finances", "demander_depenses", "valider_depenses", "voir_membres").map { RolePermission("president", it) } +
        listOf("consulter_finances", "demander_depenses", "voir_membres").map { RolePermission("bureau", it) }).toMutableList()

    private fun actuel(profil: Profil?) = profil?.let { p -> profils.firstOrNull { it.id == p.id } }

    fun droitsDe(profil: Profil?): Set<String> {
        val p = actuel(profil) ?: return emptySet()
        if (!p.actif) return emptySet()
        return rolePermissions.filter { it.role == p.role }.map { it.permission }.toSet()
    }

    private fun exiger(profil: Profil?, vararg droits: String) {
        val d = droitsDe(profil)
        if (droits.none { it in d }) throw IllegalStateException("row-level security")
    }

    // Comme le déclencheur garde_administrateur : quelqu'un d'actif garde toujours « administrer »
    private fun verifierAdministrateur() {
        val ok = profils.any { p -> p.actif && rolePermissions.any { it.role == p.role && it.permission == "administrer" } }
        if (!ok) throw IllegalStateException("Au moins une personne active doit garder le droit d’administrer")
    }

    fun basculerDroit(role: String, permission: String, actif: Boolean, profil: Profil?) {
        exiger(profil, "administrer")
        val rp = RolePermission(role, permission)
        if (actif) { if (rp !in rolePermissions) rolePermissions += rp; return }
        rolePermissions.remove(rp)
        try { verifierAdministrateur() } catch (e: Exception) { rolePermissions += rp; throw e }
    }

    fun creerRole(code: String, nom: String, modele: String?, profil: Profil?) {
        exiger(profil, "administrer")
        if (roles.any { it.code == code || it.nom.equals(nom, true) }) throw IllegalStateException("duplicate key")
        roles += Role(code, nom)
        if (modele != null) rolePermissions += rolePermissions.filter { it.role == modele }.map { RolePermission(code, it.permission) }
    }

    fun renommerRole(code: String, nom: String, profil: Profil?) {
        exiger(profil, "administrer")
        if (roles.any { it.code != code && it.nom.equals(nom, true) }) throw IllegalStateException("duplicate key")
        val i = roles.indexOfFirst { it.code == code }; roles[i] = roles[i].copy(nom = nom)
    }

    fun supprimerRole(code: String, profil: Profil?) {
        exiger(profil, "administrer")
        val r = roles.first { it.code == code }
        if (r.systeme) throw IllegalStateException("Rôle de base, non supprimable")
        if (profils.any { it.role == code } || invitations.any { it.role == code }) throw IllegalStateException("foreign key role")
        roles.remove(r); rolePermissions.removeAll { it.role == code }
    }

    fun profilsActuels(): List<Profil> = profils.toList() + profilsAjoutes

    fun majProfil(id: String, role: String?, actif: Boolean?, profil: Profil?) {
        exiger(profil, "administrer")
        val i = profils.indexOfFirst { it.id == id }
        if (i < 0) return
        val avant = profils[i]
        profils[i] = avant.copy(role = role ?: avant.role, actif = actif ?: avant.actif)
        try { verifierAdministrateur() } catch (e: Exception) { profils[i] = avant; throw e }
    }

    fun lierProfil(id: String, membreId: String?, nom: String?, profil: Profil?) {
        exiger(profil, "administrer")
        val i = profils.indexOfFirst { it.id == id }
        if (i >= 0) profils[i] = profils[i].copy(memberId = membreId, nom = nom ?: profils[i].nom)
    }

    fun modifierNom(id: String?, nom: String) {
        val i = profils.indexOfFirst { it.id == id }
        if (nom.isBlank()) throw IllegalStateException("Nom obligatoire")
        if (i >= 0) profils[i] = profils[i].copy(nom = nom.trim().take(80))
    }

    fun emailDe(id: String?): String = comptesTest.firstOrNull { it.profil.id == id }?.email ?: ""

    fun inviter(i: Invitation, profil: Profil?) {
        exiger(profil, "administrer")
        if (invitations.any { it.email.equals(i.email, true) }) throw IllegalStateException("duplicate key")
        invitations += i
    }

    fun majReglage(cle: String, valeur: Double, profil: Profil?) { exiger(profil, "administrer"); reglages[cle] = valeur }

    fun majOrganisation(nom: String, logo: String?, profil: Profil?, banniere: String? = null, retirerBanniere: Boolean = false) {
        exiger(profil, "administrer")
        organisation = organisation.copy(nom = nom, logo = logo ?: organisation.logo,
            banniere = if (retirerBanniere) null else banniere ?: organisation.banniere)
    }

    val categories = mutableListOf(
        Categorie("c1", "Cotisations", "recette"), Categorie("c2", "Dons", "recette"),
        Categorie("c3", "Offrandes dédiées", "recette"), Categorie("c4", "Activités / événements", "recette"),
        Categorie("c5", "Autres recettes", "recette"),
        Categorie("c6", "Fonctionnement", "depense"), Categorie("c7", "Activités / événements", "depense"),
        Categorie("c8", "Aides et solidarité", "depense"), Categorie("c9", "Matériel", "depense"),
        Categorie("c10", "Autres dépenses", "depense"),
    )

    val comptes = mutableListOf(Compte("acc-caisse", "Caisse (espèces)", "caisse", 412.50), Compte("acc-banque", "Banque", "banque", 2860.00))
    private val soldesDepart get() = comptes.associate { it.id to it.soldeInitial }

    fun majSoldeInitial(id: String, v: Double, profil: Profil?) {
        exiger(profil, "administrer")
        val i = comptes.indexOfFirst { it.id == id }; comptes[i] = comptes[i].copy(soldeInitial = v)
    }

    fun majCompte(id: String, solde: Double, actif: Boolean, profil: Profil?) {
        exiger(profil, "administrer")
        val i = comptes.indexOfFirst { it.id == id }; comptes[i] = comptes[i].copy(soldeInitial = solde, actif = actif)
    }

    fun ajouterCompte(c: NouveauCompte, profil: Profil?) {
        exiger(profil, "administrer")
        comptes += Compte("acc${compteur++}", c.nom, c.type, c.soldeInitial)
    }

    fun ajouterCategorie(c: NouvelleCategorie, profil: Profil?) {
        exiger(profil, "administrer")
        if (categories.any { it.nom.equals(c.nom, true) && it.sens == c.sens }) throw IllegalStateException("duplicate key")
        categories += Categorie("c${compteur++}", c.nom, c.sens)
    }

    private val moisCourant = aujourdhui().monthNumber
    val membres = mutableListOf(
        Membre("m0", "Grâce", "Mbala", 5, moisCourant, "Infirmière", "06 12 34 56 70"),
        Membre("m1", "Daniel", "Kouassi", 12, moisCourant, "Électricien", "06 12 34 56 71"),
        Membre("m2", "Esther", "Nzeyimana", 27, moisCourant),
        Membre("m3", "Samuel", "Okafor", 3, 2, "Étudiant", "06 12 34 56 73"),
        Membre("m4", "Ruth", "Diallo", 19, 7, "Comptable", "06 12 34 56 74"),
        Membre("m5", "Jonathan", "Mabiala", 8, 11, whatsapp = "06 12 34 56 75"),
        Membre("m6", "Déborah", "Tshibangu", 30, 4, "Aide-soignante"),
        Membre("m7", "Élie", "Bamba", 14, 9, "Chauffeur", "06 12 34 56 77"),
        Membre("m8", "Naomie", "Kabongo", 22, 1),
        Membre("m9", "Josué", "Mensah", 1, 6, "Ingénieur", "06 12 34 56 79"),
        Membre("m10", "Jean-Marc", "Ilunga", 17, 3, "Enseignant", "06 12 34 56 80"),
        Membre("m11", "Marthe", "Kalala", 9, 12, "Secrétaire médicale"),
    )
    // Accord pour afficher l'anniversaire aux adhérents
    init {
        val accord = setOf("m0", "m1", "m3", "m4", "m5", "m6", "m8", "m9")
        for (i in membres.indices) membres[i] = membres[i].copy(consentement = membres[i].id in accord)
    }

    private class Ligne(var e: Ecriture)
    private val lignes = mutableListOf<Ligne>()
    // Cotisation mensuelle : (membre, période « AAAA-MM-01 ») -> montant dû
    private val cotisationsDues = mutableMapOf<Pair<String, String>, Double>()
    private var compteur = 0
    private fun p2(n: Int) = n.toString().padStart(2, '0')

    // ---------- Tiers et collectes ----------
    val tiers = mutableListOf(Tiers("ti1", "Thomann", "fournisseur"), Tiers("ti2", "Boulangerie du Lac", "fournisseur"),
        Tiers("ti3", "Paroisse Saint-Bruno", "partenaire"), Tiers("ti4", "M. et Mme Lefèvre", "donateur"))
    private val collectes = mutableListOf(
        Collecte("co1", "Participation à la sortie des jeunes", "p1", 15.0, 300.0, aujourdhui().plus(DatePeriod(days = 10)).toString(), creeLe = "1"),
        Collecte("co2", "Repas de Noël", "p2", null, 500.0, "${aujourdhui().year}-12-15", creeLe = "2"),
    )
    val collecteMembres = mutableListOf<CollecteMembre>()

    private fun ilYa(jours: Int) = aujourdhui().minus(DatePeriod(days = jours)).toString()

    init {
        val an = aujourdhui().year
        fun ajout(j: Int, sens: String, montant: Double, cat: String, libelle: String, compte: String, m: String? = null,
                  cotis: Boolean = false, collecte: String? = null, projet: String? = null, tiersId: String? = null) {
            lignes += Ligne(Ecriture("e${compteur++}", ilYa(j), compte, sens, montant, cat, libelle,
                mode = if (compte == "acc-caisse") "especes" else "virement", membreId = m, estCotisation = cotis,
                collecteId = collecte, projetId = projet, tiersId = tiersId))
        }
        ajout(2, "recette", 185.0, "c3", "Offrande pour la sortie des jeunes", "acc-caisse")
        lignes.last().e = lignes.last().e.copy(projetId = "p1")
        ajout(4, "depense", 64.90, "c7", "Boissons et gobelets, fête de rentrée", "acc-caisse")
        ajout(9, "depense", 120.0, "c9", "Câbles et micro", "acc-banque", tiersId = "ti1")
        lignes.last().e = lignes.last().e.copy(projetId = "p3")
        ajout(15, "recette", 300.0, "c2", "Don anonyme", "acc-banque")
        ajout(52, "depense", 45.0, "c6", "Frais de tenue de compte", "acc-banque")
        lignes.last().e = lignes.last().e.copy(rapproche = true, rapprochementId = "rec0")
        ajout(10, "depense", 75.0, "c7", "Décoration de la salle", "acc-caisse")
        ajout(29, "depense", 60.0, "c8", "Colis alimentaire famille Diallo", "acc-caisse")
        // Versements de cotisation, imputés sur les mois les plus anciens
        listOf("m0" to 200.0, "m1" to 100.0, "m3" to 180.0, "m4" to 240.0, "m6" to 40.0, "m9" to 140.0, "m2" to 60.0).forEachIndexed { k, (id, v) ->
            val m = membres.first { it.id == id }
            ajout(3 + k * 4, "recette", v, "c1", "Cotisation$NBSP: ${m.nomComplet}", if (k % 2 == 0) "acc-caisse" else "acc-banque", id, cotis = true)
        }
        listOf("m0" to 15.0, "m1" to 15.0, "m3" to 10.0, "m4" to 15.0, "m5" to 15.0).forEachIndexed { k, (id, v) ->
            ajout(1 + k, "recette", v, "c4", "Participation sortie$NBSP: ${membres.first { it.id == id }.nomComplet}", "acc-caisse", id, collecte = "co1", projet = "p1")
        }
        ajout(6, "recette", 50.0, "c2", "Don pour la sortie des jeunes", "acc-banque", collecte = "co1", projet = "p1", tiersId = "ti3")
        ajout(1, "recette", 20.0, "c4", "Repas de Noël$NBSP: Grâce Mbala", "acc-caisse", "m0", collecte = "co2", projet = "p2")
        ajout(20, "recette", 150.0, "c2", "Don", "acc-banque", tiersId = "ti4")
        // Historique de 12 mois pour les graphiques d'analyse
        val hist = listOf(listOf(95, 32, 120, 0), listOf(140, 28, 0, 210), listOf(110, 35, 60, 0), listOf(160, 41, 0, 0),
            listOf(125, 30, 250, 180), listOf(90, 26, 0, 0), listOf(180, 38, 90, 0), listOf(105, 29, 0, 320),
            listOf(150, 33, 140, 0), listOf(120, 31, 0, 0), listOf(135, 36, 70, 150))
        hist.forEachIndexed { k, (offrande, fonct, activite, don) ->
            val j = 35 + k * 30
            ajout(j, "recette", offrande.toDouble(), "c3", "Offrandes du mois", "acc-caisse")
            ajout(j + 2, "depense", fonct.toDouble(), "c6", "Fournitures et photocopies", "acc-caisse")
            ajout(j + 4, "depense", 5.0, "c6", "Frais bancaires", "acc-banque")
            if (activite > 0) ajout(j + 6, "depense", activite.toDouble(), "c7", "Activité du mois", "acc-caisse")
            if (don > 0) ajout(j + 8, "recette", don.toDouble(), "c2", "Don", "acc-banque", tiersId = "ti4")
            if (k % 3 == 1) ajout(j + 10, "depense", (45 + k * 5).toDouble(), "c8", "Aide à une famille", "acc-caisse")
        }
        // m9 a adhéré en mars : pas de cotisation avant
        membres.forEach { m -> for (mo in (if (m.id == "m9") 3 else 1)..12) cotisationsDues[m.id to "$an-${p2(mo)}-01"] = 20.0 }
    }

    fun ecritures(): List<Ecriture> = lignes.map { it.e }.sortedByDescending { it.date }

    // ---------- Pièces jointes ----------
    val pieces = mutableListOf<Piece>()

    fun joindrePiece(transactionId: String, chemin: String, f: Fichier, profil: Profil?) {
        exiger(profil, "saisir_ecritures", "payer_depenses")
        fichiers["justificatifs/$chemin"] = f.octets
        pieces += Piece("pj${compteur++}", transactionId, null, chemin, f.mime)
    }

    fun soldes(): List<Solde> = comptes.filter { it.actif }.map { c ->
        Solde(c.id, c.nom, c.type, (soldesDepart[c.id] ?: 0.0) + lignes.filter { it.e.compteId == c.id }.sumOf { it.e.signe })
    }

    fun ajouter(n: NouvelleEcriture, profil: Profil?): String {
        val d = droitsDe(profil)
        val rubrique = n.sens == "recette" && (n.estCotisation || n.collecteId != null) && "gerer_cotisations" in d
        if ("saisir_ecritures" !in d && !rubrique) throw IllegalStateException("row-level security")
        if (n.membreId != null && n.tiersId != null) throw IllegalStateException("violates check constraint un_seul_tiers")
        if (n.estCotisation && (n.membreId == null || n.sens != "recette")) throw IllegalStateException("Une cotisation se rattache à un membre")
        if (n.contrepasseDe != null && lignes.any { it.e.contrepasseDe == n.contrepasseDe }) throw IllegalStateException("Écriture déjà contre-passée")
        lignes += Ligne(Ecriture("e${compteur++}", n.date, n.compteId, n.sens, n.montant, n.categorieId, n.libelle, n.mode,
            projetId = n.projetId, contrepasseDe = n.contrepasseDe, membreId = n.membreId, tiersId = n.tiersId,
            estCotisation = n.estCotisation, collecteId = n.collecteId))
        return lignes.last().e.id
    }

    // Dépense saisie directement : demande de validation a posteriori, rattachée à l'opération
    fun demanderValidation(transactionId: String, profil: Profil?): String {
        exiger(profil, "saisir_ecritures")
        val i = lignes.indexOfFirst { it.e.id == transactionId }
        if (i < 0) throw IllegalStateException("Opération introuvable")
        val t = lignes[i].e
        if (t.sens != "depense" || t.montant <= 0 || t.contrepasseDe != null) throw IllegalStateException("Seule une dépense peut être soumise au président")
        if (t.demandeId != null) throw IllegalStateException("Cette dépense a déjà une demande de validation")
        val id = "r${compteur++}"
        demandesListe += Demande(id, profil!!.id, t.libelle, t.montant, t.categorieId, t.projetId, "soumise", payeeLe = t.date,
            creeLe = aujourdhui().toString(), regularisation = true)
        lignes[i].e = t.copy(demandeId = id)
        return id
    }

    // Comme la vue v_cotisations_periodes : versements imputés sur les périodes les plus anciennes
    fun periodes(): List<PeriodeCotisation> {
        val auj = aujourdhui().toString()
        val paye = lignes.filter { it.e.estCotisation }.groupBy { it.e.membreId }.mapValues { e -> e.value.sumOf { it.e.montant } }
        return cotisationsDues.entries.groupBy { it.key.first }.flatMap { (mid, l) ->
            var avant = 0.0
            l.sortedBy { it.key.second }.map { (k, du) ->
                val dispo = (paye[mid] ?: 0.0) - avant
                avant += du
                PeriodeCotisation(mid, k.second, k.second.take(4).toInt(), du, minOf(du, maxOf(0.0, dispo)),
                    when { du == 0.0 -> "dispense"; dispo >= du -> "regle"; dispo > 0 -> "partiel"; k.second > auj -> "a_venir"; else -> "impaye" })
            }
        }
    }

    fun cotisations(annee: Int): List<Cotisation> {
        val toutes = periodes()
        val auj = aujourdhui().toString()
        val paye = lignes.filter { it.e.estCotisation }.groupBy { it.e.membreId }.mapValues { e -> e.value.sumOf { it.e.montant } }
        return toutes.filter { it.annee == annee }.groupBy { it.membreId }.map { (mid, l) ->
            val du = l.sumOf { it.du }; val regle = l.sumOf { it.regle }
            val exigible = l.filter { it.periode <= auj }.sumOf { it.du }
            Cotisation(mid, annee, du, regle, du - regle, if (regle >= exigible) "a_jour" else if (regle > 0) "partiel" else "impaye",
                exigible, maxOf(0.0, exigible - regle), l.filter { it.statut in listOf("regle", "dispense") }.maxOfOrNull { it.periode },
                maxOf(0.0, (paye[mid] ?: 0.0) - toutes.filter { it.membreId == mid }.sumOf { it.du }))
        }
    }

    fun majDu(membreId: String, periode: String, montant: Double, profil: Profil?) {
        exiger(profil, "gerer_cotisations")
        cotisationsDues[membreId to periode] = montant
    }

    fun genererCotisations(annee: Int, profil: Profil?): Int {
        exiger(profil, "gerer_cotisations")
        val pas = (reglages["cotisation_periode_mois"] ?: 1.0).toInt().let { if (it in listOf(1, 3, 6, 12)) it else 1 }
        var n = 0
        membres.filter { it.actif }.forEach { m ->
            for (mo in 1..12 step pas) {
                val cle = m.id to "$annee-${p2(mo)}-01"
                if (cle !in cotisationsDues) { cotisationsDues[cle] = reglages["cotisation_montant"] ?: 20.0; n++ }
            }
        }
        return n
    }

    // ---------- Tiers ----------
    fun ajouterTiers(t: NouveauTiers, profil: Profil?): Tiers {
        exiger(profil, "saisir_ecritures", "gerer_cotisations")
        if (tiers.any { it.nom.equals(t.nom, true) }) throw IllegalStateException("duplicate key")
        return Tiers("ti${compteur++}", t.nom, t.type, t.telephone, t.email, t.notes, t.actif).also { tiers += it }
    }

    fun majTiers(id: String, t: NouveauTiers, profil: Profil?) {
        exiger(profil, "saisir_ecritures", "gerer_cotisations")
        val i = tiers.indexOfFirst { it.id == id }; tiers[i] = Tiers(id, t.nom, t.type, t.telephone, t.email, t.notes, t.actif)
    }

    // ---------- Collectes ----------
    fun collectesVue(): List<Collecte> = collectes.map { c ->
        val l = lignes.filter { it.e.collecteId == c.id }
        c.copy(totalRecu = l.sumOf { it.e.montant }, nbContributeurs = l.map { it.e.membreId ?: it.e.tiersId ?: it.e.id }.toSet().size,
            nbConcernes = if (c.tousMembres) membres.count { it.actif } else collecteMembres.count { it.collecteId == c.id })
    }.sortedByDescending { it.creeLe }

    fun enregistrerCollecte(id: String?, n: NouvelleCollecte, choisis: List<String>, profil: Profil?): String {
        exiger(profil, "gerer_activites", "gerer_cotisations")
        val cle = id ?: "co${compteur++}"
        val i = collectes.indexOfFirst { it.id == cle }
        val c = Collecte(cle, n.nom, n.projetId, n.montantAttendu, n.objectif, n.dateLimite, n.tousMembres,
            if (i >= 0) collectes[i].cloturee else false, creeLe = if (i >= 0) collectes[i].creeLe else "9${compteur}")
        if (i >= 0) collectes[i] = c else collectes += c
        collecteMembres.removeAll { it.collecteId == cle }
        if (!n.tousMembres) collecteMembres += choisis.map { CollecteMembre(cle, it) }
        return cle
    }

    fun cloturerCollecte(id: String, cloturee: Boolean, profil: Profil?) {
        exiger(profil, "gerer_activites", "gerer_cotisations")
        val i = collectes.indexOfFirst { it.id == id }; collectes[i] = collectes[i].copy(cloturee = cloturee)
    }

    fun mesParticipations(profil: Profil?): List<Participation> {
        val mid = actuel(profil)?.memberId ?: return emptyList()
        return collectes.filter { c -> c.tousMembres || collecteMembres.any { it.collecteId == c.id && it.membreId == mid } }.map { c ->
            Participation(c.id, c.nom, c.montantAttendu, lignes.filter { it.e.collecteId == c.id && it.e.membreId == mid }.sumOf { it.e.montant }, c.dateLimite, c.cloturee)
        }.filter { !it.cloturee || it.donne != 0.0 }
    }

    fun anniversaires(profil: Profil?, mois: Int? = null): List<Anniversaire> {
        val bureau = droitsDe(profil).let { "voir_membres" in it || "gerer_membres" in it }
        return membres.filter { it.actif && it.mois == (mois ?: moisCourant) && (bureau || it.consentement) }
            .map { Anniversaire(it.prenom, if (bureau) it.nom else it.nom.take(1) + ".", it.jour, it.profession) }
            .sortedBy { it.jour }
    }

    fun retards(): List<Retard> {
        val delai = (reglages["delai_justificatif_jours"] ?: 7.0).toInt()
        return demandesListe.filter { it.statut == "payee" && it.payeeLe != null }.mapNotNull { d ->
            val jours = aujourdhui().toEpochDays() - kotlinx.datetime.LocalDate.parse(d.payeeLe!!.take(10)).toEpochDays()
            if (jours > delai) Retard(d.id, d.objet, d.montant, jours) else null
        }
    }

    // ---------- Activités ----------
    val projets = mutableListOf(
        Projet("p1", "Sortie des jeunes", ilYaPublic(-12), ilYaPublic(-12), "Journée au lac, pique-nique partagé", true, "activite", "09:00", "18:00", "Lac de Paladru"),
        Projet("p2", "Fête de Noël", "${aujourdhui().year}-12-20", "${aujourdhui().year}-12-20", "Repas partagé et spectacle des enfants", true, "activite", "17:00", "22:00", "Salle paroissiale"),
        Projet("p3", "Achat de la sono", null, null, "Projet d’équipement", false),
        Projet("p4", "Réunion du bureau", ilYaPublic(-5), ilYaPublic(-5), "Ordre du jour : budget de la sortie", false, "evenement", "19:30", "21:00", "Chez le président"),
        Projet("p5", "Culte des jeunes", ilYaPublic(-9), ilYaPublic(-9), null, true, "evenement", "15:00", "17:00", "Église"),
        Projet("p6", "Répétition de la chorale", ilYaPublic(-2), ilYaPublic(-2), null, true, "evenement", "18:30", "20:00", "Église"),
    )
    private fun ilYaPublic(j: Int) = aujourdhui().minus(DatePeriod(days = j)).toString()

    // ---------- Demandes de dépense ----------
    private val demandesListe = mutableListOf(
        Demande("r1", "u-b", "Décoration de la salle", 75.0, "c7", null, "payee", ilYaPublic(12), ilYaPublic(10), creeLe = ilYaPublic(14), signature = "demo.png", valideePar = "u-p"),
        Demande("r2", "u-b", "Location du car pour la sortie", 240.0, "c7", "p1", "soumise", creeLe = ilYaPublic(1)),
        Demande("r3", "u-p", "Enceinte portable", 189.0, "c9", "p3", "validee", ilYaPublic(2), creeLe = ilYaPublic(3), signature = "demo.png", valideePar = "u-p"),
        Demande("r4", "u-t", "Colis alimentaire famille Diallo", 60.0, "c8", null, "justifiee", ilYaPublic(30), ilYaPublic(29), creeLe = ilYaPublic(31), signature = "demo.png", valideePar = "u-p"),
    )

    // Lien des demandes payées avec leur écriture et leur justificatif (données de départ)
    init {
        fichiers["signatures/demo.png"] = ImagesDemo.signature
        fun lier(demande: String, libelle: String, image: ByteArray?) {
            val l = lignes.first { it.e.libelle == libelle }
            l.e = l.e.copy(demandeId = demande)
            if (image != null) {
                fichiers["justificatifs/demo-$demande.png"] = image
                pieces += Piece("pj-$demande", l.e.id, demande, "demo-$demande.png", "image/png")
            }
        }
        lier("r1", "Décoration de la salle", null)
        lier("r4", "Colis alimentaire famille Diallo", ImagesDemo.ticketColis)
        fichiers["justificatifs/demo-boissons.png"] = ImagesDemo.ticketDecoration
        pieces += Piece("pj-b", lignes.first { it.e.libelle.startsWith("Boissons") }.e.id, null, "demo-boissons.png", "image/png")
    }

    // Comme la règle de lecture : toutes les demandes si l'on valide, paie ou consulte ; sinon les siennes
    fun demandes(profil: Profil?): List<Demande> {
        val d = droitsDe(profil)
        val tout = listOf("valider_depenses", "payer_depenses", "consulter_finances").any { it in d }
        return demandesListe.filter { tout || it.demandeur == profil?.id }.sortedByDescending { it.creeLe }
    }

    fun creerDemande(n: NouvelleDemande, profil: Profil?) {
        exiger(profil, "demander_depenses")
        demandesListe += Demande("r${compteur++}", n.demandeur, n.objet, n.montant, n.categorieId, n.projetId, "soumise", creeLe = aujourdhui().toString() + "T23:59")
    }

    fun changerStatut(id: String, statut: String, profil: Profil?, signature: String? = null, motif: String? = null) {
        val i = demandesListe.indexOfFirst { it.id == id }
        val d = demandesListe[i]
        when (statut) {
            "validee", "refusee" -> {
                exiger(profil, "valider_depenses")
                if (d.statut != "soumise") throw IllegalStateException("Changement de statut non autorisé")
            }
            "annulee" -> if (d.demandeur != profil?.id || d.statut != "soumise") throw IllegalStateException("Seul le demandeur annule sa demande")
        }
        val final = if (statut == "validee" && d.regularisation)
            (if (pieces.any { p -> lignes.any { it.e.demandeId == d.id && it.e.id == p.transactionId } }) "justifiee" else "payee") else statut
        demandesListe[i] = d.copy(statut = final, signature = signature ?: d.signature, motifRefus = motif,
            valideeLe = if (statut == "validee") aujourdhui().toString() else d.valideeLe,
            valideePar = if (statut == "validee") profil?.id else d.valideePar)
    }

    fun payer(id: String, compteId: String, mode: String, date: String, profil: Profil?) {
        exiger(profil, "payer_depenses")
        val i = demandesListe.indexOfFirst { it.id == id }
        val d = demandesListe[i]
        if (d.valideePar == profil?.id) throw IllegalStateException("La personne qui a validé ne peut pas payer")
        if (d.statut != "validee") throw IllegalStateException("Demande non validée par le président")
        lignes += Ligne(Ecriture("e${compteur++}", date, compteId, "depense", d.montant, d.categorieId, d.objet, mode = mode, projetId = d.projetId, demandeId = d.id))
        demandesListe[i] = d.copy(statut = "payee", payeeLe = aujourdhui().toString())
    }

    fun justifier(id: String, chemin: String, f: Fichier, profil: Profil?) {
        val i = demandesListe.indexOfFirst { it.id == id }
        val d = demandesListe[i]
        val droits = droitsDe(profil)
        if (!("saisir_ecritures" in droits || "payer_depenses" in droits || d.demandeur == profil?.id))
            throw IllegalStateException("Le justificatif est déposé par le demandeur ou par la personne qui paie")
        if (d.statut != "payee") throw IllegalStateException("La demande doit être payée avant le justificatif")
        val ecriture = lignes.firstOrNull { it.e.demandeId == id && it.e.contrepasseDe == null }?.e?.id
        fichiers["justificatifs/$chemin"] = f.octets
        pieces += Piece("pj${compteur++}", ecriture, id, chemin, f.mime)
        demandesListe[i] = d.copy(statut = "justifiee")
    }

    // ---------- Budget ----------
    private val budgets = listOf(
        Triple("c6", null, 300.0), Triple("c7", null, 800.0), Triple("c8", null, 500.0), Triple("c9", null, 150.0),
        Triple("c1", null, 1000.0), Triple("c2", null, 1500.0), Triple("c3", null, 600.0),
        Triple("c7", "p1", 300.0), Triple("c9", "p3", 400.0),
    ).mapIndexed { i, (c, p, m) -> Budget("b$i", aujourdhui().year, c, p, m) }.toMutableList()

    fun budgetsBruts(annee: Int) = budgets.filter { it.annee == annee }

    fun ajouterBudget(b: NouveauBudget, profil: Profil?) {
        exiger(profil, "gerer_budget")
        if (budgets.any { it.annee == b.annee && it.categorieId == b.categorieId && it.projetId == b.projetId })
            throw IllegalStateException("Cette ligne de budget existe déjà")
        budgets += Budget("b${compteur++}", b.annee, b.categorieId, b.projetId, b.prevu, b.seuil)
    }

    fun majBudget(id: String, prevu: Double, profil: Profil?) {
        exiger(profil, "gerer_budget")
        val i = budgets.indexOfFirst { it.id == id }; budgets[i] = budgets[i].copy(prevu = prevu)
    }

    fun supprimerBudget(id: String, profil: Profil?) { exiger(profil, "gerer_budget"); budgets.removeAll { it.id == id } }

    fun budget(annee: Int): List<LigneBudget> = budgets.filter { it.annee == annee }.map { b ->
        val c = categories.first { it.id == b.categorieId }
        val realise = lignes.filter { it.e.categorieId == b.categorieId && it.e.date.startsWith(annee.toString()) && (b.projetId == null || it.e.projetId == b.projetId) }.sumOf { it.e.montant }
        val taux = if (b.prevu > 0) kotlin.math.round(1000 * realise / b.prevu) / 10 else null
        LigneBudget(annee, b.categorieId, c.nom, c.sens, b.projetId, b.prevu, realise, taux, c.sens == "depense" && realise >= b.prevu * b.seuil / 100)
    }

    // ---------- Activités (saisie) ----------
    private fun versProjet(id: String, p: NouveauProjet) = Projet(id, p.nom, p.debut, p.fin, p.description, p.visible, p.type, p.heureDebut, p.heureFin, p.lieu)

    fun ajouterProjet(p: NouveauProjet, profil: Profil?): String {
        exiger(profil, "gerer_activites")
        val id = "p${compteur++}"
        projets += versProjet(id, p)
        return id
    }

    fun majProjet(id: String, p: NouveauProjet, profil: Profil?) {
        exiger(profil, "gerer_activites")
        val i = projets.indexOfFirst { it.id == id }; projets[i] = versProjet(id, p)
    }

    // Comme planning_activites : événements datés de la période, avec la participation demandée
    fun planning(profil: Profil?, debut: String? = null, fin: String? = null): List<Projet> {
        val d0 = debut ?: ilYaPublic(30)
        val d = droitsDe(profil)
        return projets.filter { p -> (p.visible || "gerer_activites" in d || "consulter_finances" in d) && p.debut != null &&
            (p.fin ?: p.debut) >= d0 && (fin == null || p.debut <= fin) }
            .map { p -> val c = collectes.filter { it.projetId == p.id && !it.cloturee }.minByOrNull { it.creeLe }
                p.copy(participation = c?.montantAttendu, collecteId = c?.id) }
            .sortedWith(compareBy({ it.debut }, { it.heureDebut ?: "" }))
    }

    // ---------- Adhérent ----------
    fun maCotisation(profil: Profil?): List<PeriodeCotisation> {
        val mid = actuel(profil)?.memberId ?: return emptyList()
        return periodes().filter { it.membreId == mid }.sortedByDescending { it.periode }
    }

    fun sauvegardeJson(): String = """{"format":"tresorerie-jp-v1","demonstration":true,"membres":${membres.size},"ecritures":${lignes.size}}"""

    // ---------- Membres ----------
    fun ajouterMembre(n: NouveauMembre, profil: Profil?): String {
        exiger(profil, "gerer_membres")
        val m = Membre("m${compteur++}", n.prenom, n.nom, n.jour, n.mois, n.profession, n.whatsapp, consentement = n.consentement, email = n.email)
        membres += m
        val auj = aujourdhui()
        for (mo in auj.monthNumber..12) cotisationsDues[m.id to "${auj.year}-${p2(mo)}-01"] = reglages["cotisation_montant"] ?: 20.0
        return m.id
    }

    fun majMembre(id: String, n: NouveauMembre, actif: Boolean, photo: String?, profil: Profil?) {
        exiger(profil, "gerer_membres")
        val i = membres.indexOfFirst { it.id == id }
        val m = membres[i]
        membres[i] = m.copy(prenom = n.prenom, nom = n.nom, jour = n.jour, mois = n.mois, profession = n.profession, whatsapp = n.whatsapp,
            email = n.email, consentement = n.consentement, actif = actif, photo = photo ?: m.photo)
    }

    // ---------- Rapprochement ----------
    val rapprochements = mutableListOf(Rapprochement("rec0", "acc-banque", ilYaPublic(75), ilYaPublic(41), 2815.0, "termine", ilYaPublic(38)))

    fun soldePointe(compteId: String): Double =
        (soldesDepart[compteId] ?: 0.0) + lignes.filter { it.e.compteId == compteId && it.e.rapprochementId != null }.sumOf { it.e.signe }

    fun terminerRapprochement(compteId: String, debut: String, fin: String, solde: Double, ids: List<String>, profil: Profil?) {
        exiger(profil, "rapprocher")
        val choisies = lignes.filter { it.e.id in ids }
        if (choisies.any { it.e.compteId != compteId || it.e.rapprochementId != null || it.e.date > fin })
            throw IllegalStateException("Écriture d’un autre compte, déjà rapprochée ou postérieure à la période")
        val ecart = kotlin.math.round((solde - soldePointe(compteId) - choisies.sumOf { it.e.signe }) * 100) / 100
        if (ecart != 0.0) throw IllegalStateException("Écart de ${euros(ecart)} entre le relevé et les écritures pointées")
        val id = "rec${compteur++}"
        rapprochements.add(0, Rapprochement(id, compteId, debut, fin, solde, "termine", aujourdhui().toString()))
        choisies.forEach { it.e = it.e.copy(rapproche = true, rapprochementId = id) }
    }
}
