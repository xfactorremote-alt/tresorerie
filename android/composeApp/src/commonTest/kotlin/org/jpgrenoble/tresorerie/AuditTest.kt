package org.jpgrenoble.tresorerie

import kotlin.test.Test
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
}
