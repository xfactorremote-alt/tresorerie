package org.jpgrenoble.tresorerie

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profil(
    val id: String,
    val nom: String,
    val role: String,
    @SerialName("member_id") val memberId: String? = null,
    val actif: Boolean = true,
)

@Serializable
data class Organisation(val nom: String = "JP Grenoble", @SerialName("logo_path") val logo: String? = null,
                        @SerialName("banniere_path") val banniere: String? = null,
                        // Identité et coordonnées (reprises sur les documents)
                        val sigle: String? = null, val objet: String? = null, val adresse: String? = null,
                        @SerialName("code_postal") val codePostal: String? = null, val ville: String? = null,
                        val email: String? = null, val telephone: String? = null, @SerialName("site_web") val siteWeb: String? = null,
                        val rna: String? = null, val siret: String? = null, @SerialName("date_creation") val dateCreation: String? = null,
                        // false tant que l'assistant de configuration n'a pas été terminé
                        val configuree: Boolean = true,
                        @SerialName("exercice_debut") val exerciceDebut: String? = null)

// Exercice comptable : clôturé, ses opérations sont verrouillées (réouverture possible et tracée)
@Serializable
data class Exercice(val id: String, val libelle: String, val debut: String, val fin: String, val cloture: Boolean = false,
                    @SerialName("cloture_le") val clotureLe: String? = null, @SerialName("cloture_par") val cloturePar: String? = null)

@Serializable
data class NouvelExercice(val libelle: String, val debut: String, val fin: String)

// Corbeille : copie complète de ce qui a été supprimé, restaurable
@Serializable
data class ElementCorbeille(val id: String, @SerialName("table_nom") val table: String, @SerialName("ligne_id") val ligneId: String,
                            val libelle: String, val motif: String? = null, @SerialName("supprime_par") val supprimePar: String? = null,
                            @SerialName("supprime_le") val supprimeLe: String, @SerialName("restaure_le") val restaureLe: String? = null)

// Nouveautés : à traiter + nouveau depuis la dernière visite de chaque onglet (pastilles, cloche)
@Serializable
data class ElementNouveaute(val section: String, val titre: String, val detail: String, val quand: String)

@Serializable
data class Nouveautes(val compteurs: Map<String, Int> = emptyMap(), val elements: List<ElementNouveaute> = emptyList()) {
    val total get() = compteurs.values.sum()
}

@Serializable
data class Solde(val id: String, val nom: String, val type: String, val solde: Double)

@Serializable
data class Compte(val id: String, val nom: String, val type: String, @SerialName("solde_initial") val soldeInitial: Double = 0.0, val actif: Boolean = true)

@Serializable
data class Categorie(val id: String, val nom: String, val sens: String, val interne: Boolean = false)

@Serializable
data class Membre(
    val id: String,
    val prenom: String,
    val nom: String,
    @SerialName("naissance_jour") val jour: Int,
    @SerialName("naissance_mois") val mois: Int,
    val profession: String? = null,
    val whatsapp: String? = null,
    val actif: Boolean = true,
    @SerialName("consent_anniversaire") val consentement: Boolean = false,
    val email: String? = null,
    @SerialName("photo_path") val photo: String? = null,
    @SerialName("created_at") val creeLe: String? = null,
) { val nomComplet get() = "$prenom $nom" }

@Serializable
data class Ecriture(
    val id: String,
    @SerialName("date_op") val date: String,
    @SerialName("account_id") val compteId: String,
    val sens: String,
    val montant: Double,
    @SerialName("category_id") val categorieId: String,
    val libelle: String,
    val mode: String = "especes",
    val rapproche: Boolean = false,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("reconciliation_id") val rapprochementId: String? = null,
    @SerialName("contrepasse_de") val contrepasseDe: String? = null,
    @SerialName("request_id") val demandeId: String? = null,
    @SerialName("member_id") val membreId: String? = null,
    @SerialName("tiers_id") val tiersId: String? = null,
    @SerialName("est_cotisation") val estCotisation: Boolean = false,
    @SerialName("collecte_id") val collecteId: String? = null,
    val virement: String? = null,                                    // virement interne : identifiant commun aux deux écritures
    @SerialName("created_by") val creePar: String? = null,
    @SerialName("created_at") val creeLe: String? = null,
    @SerialName("date_rapprochement") val dateRapprochement: String? = null,
) {
    val signe get() = if (sens == "recette") montant else -montant
    // Un virement interne change les soldes, jamais les recettes ni les dépenses
    val estFlux get() = virement == null
}

@Serializable
data class NouvelleEcriture(
    @SerialName("date_op") val date: String,
    @SerialName("account_id") val compteId: String,
    val sens: String,
    val montant: Double,
    @SerialName("category_id") val categorieId: String,
    val libelle: String,
    val mode: String,
    @SerialName("created_by") val creePar: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("contrepasse_de") val contrepasseDe: String? = null,
    @SerialName("member_id") val membreId: String? = null,
    @SerialName("tiers_id") val tiersId: String? = null,
    @SerialName("est_cotisation") val estCotisation: Boolean = false,
    @SerialName("collecte_id") val collecteId: String? = null,
)

@Serializable
data class Anniversaire(
    val prenom: String,
    val nom: String,
    val jour: Int,
    val profession: String? = null,
)

@Serializable
data class Cotisation(
    @SerialName("member_id") val membreId: String,
    val annee: Int,
    @SerialName("montant_du") val du: Double,
    @SerialName("montant_paye") val paye: Double,
    val reste: Double,
    val statut: String,
    val exigible: Double? = null,
    val retard: Double = 0.0,
    @SerialName("regle_jusqu_a") val regleJusqua: String? = null,
    val avance: Double = 0.0,
)

// Une période de cotisation (mois, trimestre…) : montant dû et part réglée après imputation
@Serializable
data class PeriodeCotisation(
    @SerialName("member_id") val membreId: String = "",
    val periode: String,
    val annee: Int,
    @SerialName("montant_du") val du: Double,
    val regle: Double,
    val statut: String,
)

@Serializable
data class Tiers(
    val id: String,
    val nom: String,
    val type: String = "autre",
    val telephone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val actif: Boolean = true,
)

@Serializable
data class NouveauTiers(val nom: String, val type: String, val telephone: String? = null, val email: String? = null, val notes: String? = null, val actif: Boolean = true)

// Appel à participation (v_collectes)
@Serializable
data class Collecte(
    val id: String,
    val nom: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("montant_attendu") val montantAttendu: Double? = null,
    val objectif: Double? = null,
    @SerialName("date_limite") val dateLimite: String? = null,
    @SerialName("tous_membres") val tousMembres: Boolean = true,
    val cloturee: Boolean = false,
    @SerialName("total_recu") val totalRecu: Double = 0.0,
    @SerialName("nb_contributeurs") val nbContributeurs: Int = 0,
    @SerialName("nb_concernes") val nbConcernes: Int = 0,
    @SerialName("created_at") val creeLe: String = "",
)

@Serializable
data class NouvelleCollecte(
    val nom: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("montant_attendu") val montantAttendu: Double? = null,
    val objectif: Double? = null,
    @SerialName("date_limite") val dateLimite: String? = null,
    @SerialName("tous_membres") val tousMembres: Boolean = true,
)

@Serializable
data class CollecteMembre(@SerialName("collecte_id") val collecteId: String, @SerialName("member_id") val membreId: String)

@Serializable
data class Participation(
    @SerialName("collecte_id") val collecteId: String,
    val nom: String,
    @SerialName("montant_attendu") val montantAttendu: Double? = null,
    val donne: Double = 0.0,
    @SerialName("date_limite") val dateLimite: String? = null,
    val cloturee: Boolean = false,
)

@Serializable
data class Retard(val id: String, val objet: String, val montant: Double, val jours: Int)

@Serializable
data class Demande(
    val id: String,
    val demandeur: String,
    val objet: String,
    val montant: Double,
    @SerialName("category_id") val categorieId: String,
    @SerialName("project_id") val projetId: String? = null,
    val statut: String,
    @SerialName("validee_le") val valideeLe: String? = null,
    @SerialName("payee_le") val payeeLe: String? = null,
    @SerialName("motif_refus") val motifRefus: String? = null,
    @SerialName("signature_path") val signature: String? = null,
    @SerialName("created_at") val creeLe: String = "",
    @SerialName("validee_par") val valideePar: String? = null,
    val regularisation: Boolean = false,
    @SerialName("signature_hash") val empreinte: String? = null,
    val justification: String? = null,
    @SerialName("date_souhaitee") val dateSouhaitee: String? = null,
)

@Serializable
data class NouvelleDemande(
    val demandeur: String,
    val objet: String,
    val montant: Double,
    @SerialName("category_id") val categorieId: String,
    @SerialName("project_id") val projetId: String? = null,
    val statut: String = "soumise",
    val justification: String? = null,
    @SerialName("date_souhaitee") val dateSouhaitee: String? = null,
)

@Serializable
data class LigneBudget(
    val annee: Int,
    @SerialName("category_id") val categorieId: String,
    val categorie: String,
    val sens: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("montant_prevu") val prevu: Double,
    val realise: Double,
    @SerialName("taux_pct") val taux: Double? = null,
    val alerte: Boolean = false,
)

@Serializable
data class Projet(
    val id: String,
    val nom: String,
    @SerialName("date_debut") val debut: String? = null,
    @SerialName("date_fin") val fin: String? = null,
    val description: String? = null,
    @SerialName("visible_adherents") val visible: Boolean = false,
    val type: String = "activite",
    @SerialName("heure_debut") val heureDebut: String? = null,
    @SerialName("heure_fin") val heureFin: String? = null,
    val lieu: String? = null,
    val participation: Double? = null,               // planning : montant demandé par personne
    @SerialName("collecte_id") val collecteId: String? = null,
    @SerialName("created_at") val creeLe: String? = null,
)

@Serializable
data class Rapprochement(
    val id: String,
    @SerialName("account_id") val compteId: String,
    @SerialName("periode_debut") val debut: String,
    @SerialName("periode_fin") val fin: String,
    @SerialName("solde_releve") val soldeReleve: Double,
    val statut: String,
    @SerialName("termine_le") val termineLe: String? = null,
    @SerialName("statement_path") val releve: String? = null,
)

@Serializable
data class NouveauMembre(
    val prenom: String,
    val nom: String,
    @SerialName("naissance_jour") val jour: Int,
    @SerialName("naissance_mois") val mois: Int,
    val profession: String? = null,
    val whatsapp: String? = null,
    @SerialName("consent_anniversaire") val consentement: Boolean = false,
    @SerialName("date_adhesion") val adhesion: String? = null,
    val email: String? = null,
)

@Serializable
data class ProfilCourt(val id: String, val nom: String)

// Fichier choisi sur le téléphone, déjà réduit (photo) ou contrôlé (PDF)
class Fichier(val octets: ByteArray, val mime: String, val extension: String, val nom: String? = null) {   // nom : nom d'origine du fichier choisi
    val ko get() = octets.size / 1024
}

@Serializable
data class Reglage(val cle: String, val valeur: Double? = null, val texte: String? = null)

@Serializable
data class Invitation(
    val email: String,
    val nom: String? = null,
    val role: String = "adherent",
    @SerialName("member_id") val membreId: String? = null,
)

@Serializable
data class Budget(
    val id: String,
    val annee: Int,
    @SerialName("category_id") val categorieId: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("montant_prevu") val prevu: Double,
    @SerialName("seuil_alerte_pct") val seuil: Int = 90,
)

@Serializable
data class NouveauBudget(
    val annee: Int,
    @SerialName("category_id") val categorieId: String,
    @SerialName("project_id") val projetId: String? = null,
    @SerialName("montant_prevu") val prevu: Double,
    @SerialName("seuil_alerte_pct") val seuil: Int = 90,
)

@Serializable
data class NouveauProjet(
    val nom: String,
    @SerialName("date_debut") val debut: String? = null,
    @SerialName("date_fin") val fin: String? = null,
    val description: String? = null,
    @SerialName("visible_adherents") val visible: Boolean = true,
    val type: String = "activite",
    @SerialName("heure_debut") val heureDebut: String? = null,
    @SerialName("heure_fin") val heureFin: String? = null,
    val lieu: String? = null,
)

@Serializable
data class NouvelleCategorie(val nom: String, val sens: String)

@Serializable
data class NouveauCompte(val nom: String, val type: String, @SerialName("solde_initial") val soldeInitial: Double = 0.0)


@Serializable
data class Role(val code: String, val nom: String, val systeme: Boolean = false)

@Serializable
data class Permission(val code: String, val libelle: String, val groupe: String, val ordre: Int = 0)

@Serializable
data class RolePermission(val role: String, val permission: String)

@Serializable
data class Piece(
    val id: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("request_id") val demandeId: String? = null,
    @SerialName("storage_path") val chemin: String,
    val mime: String? = null,
    val nature: String = "justificatif",                             // « devis » : ne remplace jamais le justificatif
)

@Serializable
data class NouveauDevis(
    @SerialName("request_id") val demandeId: String,
    @SerialName("storage_path") val chemin: String,
    val mime: String,
    @SerialName("taille_ko") val ko: Int,
    @SerialName("depose_par") val deposePar: String,
    val nature: String = "devis",
)

@Serializable
data class NouvellePiece(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("storage_path") val chemin: String,
    val mime: String,
    @SerialName("taille_ko") val ko: Int,
    @SerialName("depose_par") val deposePar: String,
)

// ---------- Inventaire du matériel ----------
@Serializable
data class Materiel(
    val id: String,
    val designation: String,
    val categorie: String = "autre",
    val marque: String? = null,
    @SerialName("numero_serie") val numeroSerie: String? = null,
    val quantite: Int = 1,
    val origine: String = "achat",
    @SerialName("date_acquisition") val dateAcquisition: String? = null,
    @SerialName("valeur_acquisition") val valeurAcquisition: Double? = null,
    @SerialName("valeur_actuelle") val valeurActuelle: Double? = null,
    val etat: String = "bon",
    val lieu: String? = null,
    @SerialName("detenteur_id") val detenteurId: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("photo_path") val photo: String? = null,
    val notes: String? = null,
    @SerialName("verifie_le") val verifieLe: String? = null,
    @SerialName("sorti_le") val sortiLe: String? = null,
    @SerialName("motif_sortie") val motifSortie: String? = null,
)

@Serializable
data class NouveauMateriel(
    val designation: String,
    val categorie: String,
    val marque: String? = null,
    @SerialName("numero_serie") val numeroSerie: String? = null,
    val quantite: Int = 1,
    val origine: String = "achat",
    @SerialName("date_acquisition") val dateAcquisition: String? = null,
    @SerialName("valeur_acquisition") val valeurAcquisition: Double? = null,
    @SerialName("valeur_actuelle") val valeurActuelle: Double? = null,
    val etat: String = "bon",
    val lieu: String? = null,
    val notes: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("photo_path") val photo: String? = null,
    @SerialName("verifie_le") val verifieLe: String? = null,
)

@Serializable
data class MouvementMateriel(
    @SerialName("materiel_id") val materielId: String,
    val type: String,
    @SerialName("member_id") val membreId: String? = null,
    val notes: String? = null,
    @SerialName("date_mvt") val date: String = "",
    val par: String? = null,
    @SerialName("created_at") val creeLe: String? = null,
)

// Lien personnel d'un membre : sa page (cotisation, participations, rendez-vous) sans compte
@Serializable
data class LienMembre(
    @SerialName("member_id") val membreId: String,
    val jeton: String,
    @SerialName("nb_consultations") val nbConsultations: Int = 0,
    @SerialName("derniere_consultation") val derniereConsultation: String? = null,
    val code: String? = null,   // code court de 12 caractères (liens créés depuis le 10 octobre 2026)
)
