package org.jpgrenoble.tresorerie

import kotlin.test.Test
import kotlinx.datetime.minus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Règles issues de l'audit du 7 octobre 2026, vérifiées sur les données de démonstration
class AuditTest {
    private val tresorier = Profil("u-t", "Paul Ndongo", "tresorier")
    private val president = Profil("u-p", "Jean-Marc Ilunga", "president", memberId = "m10")
    private val bureau = Profil("u-b", "Marthe Kalala", "bureau", memberId = "m11")

    @Test fun motifEtLibelleDeCorrection() {
        val l = libelleCorrection("Remboursement reçu", "fournitures", "dépense refusée")
        assertTrue(l.startsWith("Remboursement reçu"))
        assertEquals("dépense refusée", motifDe(l))
        assertTrue(l.length <= 120)
        assertEquals("", motifDe("Contre-passation : fournitures"))
        assertEquals("OP-ABCDEF", refOperation("abcdef12-3456"))
    }

    @Test fun controleCompteMode() {
        val banque = Compte("b", "Banque", "banque"); val caisse = Compte("c", "Caisse", "caisse")
        assertTrue(incoherenceMode(banque, "especes").isNotEmpty())
        assertTrue(incoherenceMode(caisse, "virement").isNotEmpty())
        assertEquals("", incoherenceMode(caisse, "especes"))
        assertEquals("", incoherenceMode(banque, "virement"))
    }

    @Test fun virementInterneNiRecetteNiDepense() {
        val avant = Demo.soldes().associate { it.id to it.solde }
        Demo.virementInterne(aujourdhui().toString(), "acc-caisse", "acc-banque", 200.0, null, tresorier)
        val apres = Demo.soldes().associate { it.id to it.solde }
        assertEquals(avant["acc-caisse"]!! - 200.0, apres["acc-caisse"]!!, 0.001)
        assertEquals(avant["acc-banque"]!! + 200.0, apres["acc-banque"]!!, 0.001)
        val jambes = Demo.ecritures().filter { it.virement != null && it.contrepasseDe == null }
        assertEquals(2, jambes.size)
        assertTrue(jambes.none { it.estFlux })
        // Le graphique mensuel ne compte pas le virement en recette ni en dépense, mais garde le solde
        val serie = serieMensuelle(jambes, emptyList(), aujourdhui().toString().take(7) + "-01", aujourdhui().toString())
        assertEquals(0.0, serie.last().rec, 0.001); assertEquals(0.0, serie.last().dep, 0.001); assertEquals(0.0, serie.last().solde, 0.001)
        // Même compte refusé, double annulation refusée
        assertFailsWith<IllegalStateException> { Demo.virementInterne(aujourdhui().toString(), "acc-caisse", "acc-caisse", 5.0, null, tresorier) }
        Demo.annulerVirement(jambes.first().virement!!, "erreur de montant", tresorier)
        assertFailsWith<IllegalStateException> { Demo.annulerVirement(jambes.first().virement!!, "encore", tresorier) }
        val final = Demo.soldes().associate { it.id to it.solde }
        assertEquals(avant["acc-caisse"]!!, final["acc-caisse"]!!, 0.001)
        // Le bureau ne peut pas faire de virement
        assertFailsWith<Exception> { Demo.virementInterne(aujourdhui().toString(), "acc-caisse", "acc-banque", 10.0, null, bureau) }
    }

    @Test fun depenseRefuseeApresPaiementPuisRegularisee() {
        val id = Demo.ajouter(NouvelleEcriture(aujourdhui().toString(), "acc-banque", "depense", 5.0, "c6", "fournitures test", "especes", tresorier.id), tresorier)
        val r = Demo.demanderValidation(id, tresorier)
        Demo.changerStatut(r, "refusee", president, motif = "pas autorisé")
        var etats = EtatsDemandes(Demo.demandes(tresorier), Demo.ecritures())
        val dem = Demo.demandes(tresorier).first { it.id == r }
        assertTrue(etats.aRegulariser(dem))
        // Remboursement reçu : contre-passation datée, sur le compte où l'argent est revenu
        val op = etats.operation(dem)!!
        Demo.ajouter(NouvelleEcriture(aujourdhui().toString(), "acc-caisse", op.sens, -op.montant, op.categorieId,
            libelleCorrection("Remboursement reçu", op.libelle, "dépense refusée"), "especes", tresorier.id, contrepasseDe = op.id), tresorier)
        etats = EtatsDemandes(Demo.demandes(tresorier), Demo.ecritures())
        assertNotNull(etats.annulation(dem))
        assertTrue(!etats.aRegulariser(dem))
        assertTrue(r in etats.annulees)
    }

    @Test fun depensePayeePuisAnnuleeNAttendPlusDeJustificatif() {
        val r1 = Demo.demandes(tresorier).first { it.objet == "Décoration de la salle" }
        var etats = EtatsDemandes(Demo.demandes(tresorier), Demo.ecritures())
        assertNull(etats.annulation(r1))
        val op = etats.operation(r1)!!
        Demo.ajouter(NouvelleEcriture(aujourdhui().toString(), op.compteId, op.sens, -op.montant, op.categorieId,
            libelleCorrection("Contre-passation", op.libelle, "doublon"), op.mode, tresorier.id, contrepasseDe = op.id), tresorier)
        etats = EtatsDemandes(Demo.demandes(tresorier), Demo.ecritures())
        assertTrue(r1.id in etats.annulees)
    }

    @Test fun demandeAvecJustificationEtDevis() {
        Demo.creerDemande(NouvelleDemande(bureau.id, "Location sono", 150.0, "c7", justification = "Concert de Noël", dateSouhaitee = "2026-12-15"),
            bureau, Fichier(byteArrayOf(1, 2, 3), "application/pdf", "pdf"))
        val d = Demo.demandes(bureau).first { it.objet == "Location sono" }
        assertEquals("Concert de Noël", d.justification)
        assertEquals("2026-12-15", d.dateSouhaitee)
        val devis = Demo.pieces.first { it.demandeId == d.id }
        assertEquals("devis", devis.nature)
    }

    @Test fun categoriesInternesHorsSaisie() {
        val d = Donnees(tresorier, Organisation(), Demo.comptes, Demo.categories.toList(), emptyList(), emptySet(), emptyList())
        assertTrue(d.categories.none { it.interne })
        assertEquals("Virement interne", d.nomCategorie("ci-d"))
    }

    @Test fun budgetCommeLeSite() {
        val cats = listOf(Categorie("r1", "Cotisations", "recette"), Categorie("d1", "Fournitures", "depense"),
            Categorie("ci-d", "Virement interne (sortie)", "depense", interne = true))
        val projets = listOf(Projet("p1", "Sortie été", debut = "2026-07-01"))
        val lignes = listOf(Budget("b1", 2026, "r1", null, 500.0), Budget("b2", 2026, "d1", null, 100.0), Budget("b3", 2026, "d1", "p1", 50.0))
        fun ec(id: String, date: String, sens: String, m: Double, cat: String, p: String? = null, v: String? = null) =
            Ecriture(id, date, "acc", sens, m, cat, id, projetId = p, virement = v)
        val txs = listOf(ec("1", "2026-02-01", "recette", 300.0, "r1"), ec("2", "2026-03-01", "depense", 120.0, "d1", "p1"),
            ec("3", "2026-03-02", "depense", 200.0, "ci-d", v = "v1"), ec("4", "2025-05-01", "recette", 450.0, "r1"))
        val b = calculBudget(2026, cats, projets, lignes, txs)
        // Catégorie interne exclue des postes ; réalisé et N-1 par catégorie
        assertEquals(listOf("d1"), b.emplois.map { it.cat.id })
        assertEquals(500.0, b.resPrevu, 0.001); assertEquals(300.0, b.resReel, 0.001); assertEquals(450.0, b.resN1, 0.001)
        assertEquals(120.0, b.empReel, 0.001)
        // Activité : prévu de ses lignes, réalisé hors virements
        val a = b.activites.single()
        assertEquals("Sortie été", a.p.nom); assertEquals(50.0, a.empPrevu, 0.001); assertEquals(120.0, a.empReel, 0.001)
    }

    @Test fun materielAVerifier() {
        val ancien = aujourdhui().minus(kotlinx.datetime.DatePeriod(days = 400)).toString()
        assertTrue(aVerifier(Materiel("m", "Sono")))
        assertTrue(aVerifier(Materiel("m", "Sono", verifieLe = ancien)))
        assertTrue(!aVerifier(Materiel("m", "Sono", verifieLe = aujourdhui().toString())))
        assertTrue(!aVerifier(Materiel("m", "Sono", sortiLe = aujourdhui().toString())))
    }

    @Test fun corbeilleSupprimerEtRestaurer() {
        val avant = Demo.soldes().first { it.id == "acc-caisse" }.solde
        val id = Demo.ajouter(NouvelleEcriture(aujourdhui().toString(), "acc-caisse", "depense", 7.0, "c6", "pain doublon", "especes", tresorier.id), tresorier)
        Demo.joindrePiece(id, "x/pain.jpg", Fichier(ByteArray(4), "image/jpeg", "jpg"), tresorier)
        val c = Demo.supprimer("transactions", id, "doublon", tresorier)
        assertTrue(Demo.ecritures().none { it.id == id }); assertTrue(Demo.pieces.none { it.transactionId == id })
        assertEquals(avant, Demo.soldes().first { it.id == "acc-caisse" }.solde, 0.001)
        assertEquals("doublon", Demo.corbeilleVisible(tresorier).first { it.id == c }.motif)
        Demo.restaurer(c, tresorier)
        assertTrue(Demo.ecritures().any { it.id == id }); assertTrue(Demo.pieces.any { it.transactionId == id })
        assertFailsWith<IllegalStateException> { Demo.restaurer(c, tresorier) }
        // Le bureau ne supprime pas une opération ; un membre avec des opérations ne se supprime pas
        assertFailsWith<IllegalStateException> { Demo.supprimer("transactions", id, null, bureau) }
        assertFailsWith<IllegalStateException> { Demo.supprimer("members", "m0", null, tresorier) }
        // Opération rapprochée : contre-passation obligatoire
        val rapprochee = Demo.ecritures().first { it.rapprochementId != null }
        assertFailsWith<IllegalStateException> { Demo.supprimer("transactions", rapprochee.id, null, tresorier) }
    }

    @Test fun exerciceClotureVerrouille() {
        val ex = Demo.exercices.first { it.cloture }
        assertFailsWith<IllegalStateException> { Demo.ajouter(NouvelleEcriture(ex.fin, "acc-caisse", "recette", 3.0, "c2", "don tardif", "especes", tresorier.id), tresorier) }
        // Chevauchement refusé ; réouverture puis saisie possible ; nouvelle clôture
        assertFailsWith<IllegalStateException> { Demo.enregistrerExercice(null, NouvelExercice("Chevauche", ex.debut, ex.fin), tresorier) }
        Demo.cloturerExercice(ex.id, false, tresorier)
        val id = Demo.ajouter(NouvelleEcriture(ex.fin, "acc-caisse", "recette", 3.0, "c2", "don tardif", "especes", tresorier.id), tresorier)
        Demo.cloturerExercice(ex.id, true, tresorier)
        assertFailsWith<IllegalStateException> { Demo.supprimer("transactions", id, null, tresorier) }
        assertNotNull(Demo.exercices.first { it.id == ex.id }.clotureLe)
        assertFailsWith<IllegalStateException> { Demo.cloturerExercice(ex.id, false, bureau) }
    }

    @Test fun nouveautesEtPastilles() {
        val n = Demo.nouveautes(president)
        assertTrue((n.compteurs["depenses"] ?: 0) >= 1)        // demande à valider
        assertTrue(n.elements.isNotEmpty())
        val adherent = Profil("u-a", "Grâce Mbala", "adherent", memberId = "m0")
        assertNull(Demo.nouveautes(adherent).compteurs["membres"])   // l'adhérent ne voit pas les arrivées de membres
        assertTrue((Demo.nouveautes(adherent).compteurs["activites"] ?: 0) >= 1)
        Demo.marquerVu("activites", adherent)
        assertEquals(0, Demo.nouveautes(adherent).compteurs["activites"])
    }

    @Test fun nouveauMembreRemplitSaFiche() {
        val nouveau = Demo.connecter("nouveau@demo.jp", Demo.MOT_DE_PASSE)
        assertNull(nouveau.memberId)
        assertFailsWith<IllegalStateException> { Demo.enregistrerMaFiche("", "Neuf", 10, 10, null, null, true, nouveau) }
        val id = Demo.enregistrerMaFiche("Léa", "Nouvelle", 14, 7, "0600000000", null, true, nouveau)
        val p = Demo.profilsActuels().first { it.id == "u-n" }
        assertEquals(id, p.memberId); assertEquals("Léa Nouvelle", p.nom)
        assertEquals(14, Demo.membres.first { it.id == id }.jour)
    }

    // Lien personnel court : prénom sans accent puis code de 12 caractères ; les anciens liens gardent leur jeton
    @Test fun lienCourtEtRassurant() {
        assertEquals("grace", slugPrenom("Grâce")); assertEquals("jeanmarie", slugPrenom("Jean-Marie"))
        assertEquals("Jeunes de Tous Pays", nomAssoLisible("JEUNES DE TOUS PAYS")); assertEquals("JP Grenoble", nomAssoLisible("JP Grenoble"))
        val tres = Demo.profilsActuels().first { it.id == "u-t" }
        val nouveau = Demo.lienMembre("m1", true, tres)
        val l = Demo.liens.first { it.membreId == "m1" }
        assertEquals(nouveau, l.jeton); assertEquals(12, l.code?.length)
        assertTrue(l.code!!.none { it in "01ilo" })
        val url = Repo.urlLien(l, Demo.membres.first { it.id == "m1" })
        assertTrue(url.endsWith("?m=" + slugPrenom(Demo.membres.first { it.id == "m1" }.prenom) + "-" + l.code), url)
        assertTrue(Repo.urlLien(LienMembre("m9", "a".repeat(32)), null).endsWith("?m=" + "a".repeat(32)))
    }

    // Bannière : un communiqué « Important » d'abord, puis l'ordre réglé ; « jamais » retire le type ; règles relues à l'identique
    @Test fun annoncesBanniereOrdreEtRegles() {
        val auj = aujourdhui().toString()
        val rdv = Projet("p1", "Répétition", debut = auj)
        val cot = listOf(PeriodeCotisation("m", auj.take(7), aujourdhui().year, 20.0, 0.0, "impaye"))
        val parts = listOf(Participation("c1", "Sortie", 15.0, 0.0))
        val cq = listOf(Communique("q1", "AG", null, auj, null, "haute"), Communique("q2", "Info", null, auj, null, "normale"), Communique("q3", "Ancien", null, "2000-01-01", "2000-02-01"))
        val regles = """{"rotation":5,"rdv":{"priorite":2,"frequence":"toujours"},"cotisation":{"priorite":1,"frequence":"toujours"},"participation":{"priorite":3,"frequence":"jamais"},"communique":{"priorite":3,"frequence":"toujours"}}"""
        val (l, rotation) = annoncesBanniere(listOf(rdv), cot, parts, cq, regles)
        assertEquals(5, rotation)
        assertEquals(listOf("communique:q1", "cotisation", "rdv:p1", "communique:q2"), l.map { it.cle })
        val r = reglesBanniere(regles); assertEquals(r, reglesBanniere(texteReglesBanniere(r)))
        assertEquals(8, reglesBanniere(null).rotation)
    }

    // Communiqués : publiés par qui gère les activités, visibles selon le choix, comptés dans les nouveautés, corbeille réversible
    @Test fun communiquesDroitsNouveautesCorbeille() {
        val tres = Demo.profilsActuels().first { it.id == "u-t" }
        val adh = Demo.profilsActuels().first { it.id == "u-a" }
        assertFailsWith<IllegalStateException> { Demo.enregistrerCommunique(null, NouveauCommunique("Interdit", debut = aujourdhui().toString()), adh) }
        assertFailsWith<IllegalStateException> { Demo.enregistrerCommunique(null, NouveauCommunique("Dates", debut = "2026-10-10", fin = "2026-10-01"), tres) }
        Demo.enregistrerCommunique(null, NouveauCommunique("Répétition déplacée", "À 19 h", aujourdhui().toString()), tres)
        assertTrue(Demo.communiquesVisibles(adh).none { it.titre == "Réunion du bureau jeudi" })
        assertTrue(Demo.communiquesVisibles(tres).any { it.titre == "Réunion du bureau jeudi" })
        assertTrue((Demo.nouveautes(adh).compteurs["communiques"] ?: 0) >= 1)
        val id = Demo.communiques.first { it.titre == "Répétition déplacée" }.id
        assertFailsWith<IllegalStateException> { Demo.supprimer("communiques", id, null, adh) }
        val cb = Demo.supprimer("communiques", id, "erreur", tres)
        assertTrue(Demo.communiques.none { it.id == id })
        Demo.restaurer(cb, tres)
        assertTrue(Demo.communiques.any { it.id == id })
    }
}
