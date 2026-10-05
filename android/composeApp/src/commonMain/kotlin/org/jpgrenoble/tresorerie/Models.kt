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
                        @SerialName("banniere_path") val banniere: String? = null)

@Serializable
data class Solde(val id: String, val nom: String, val type: String, val solde: Double)

@Serializable
data class Compte(val id: String, val nom: String, val type: String, @SerialName("solde_initial") val soldeInitial: Double = 0.0, val actif: Boolean = true)

@Serializable
data class Categorie(val id: String, val nom: String, val sens: String)

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
) { val signe get() = if (sens == "recette") montant else -montant }

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
)

@Serializable
data class NouvelleDemande(
    val demandeur: String,
    val objet: String,
    val montant: Double,
    @SerialName("category_id") val categorieId: String,
    @SerialName("project_id") val projetId: String? = null,
    val statut: String = "soumise",
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
class Fichier(val octets: ByteArray, val mime: String, val extension: String) {
    val ko get() = octets.size / 1024
}

@Serializable
data class Reglage(val cle: String, val valeur: Double? = null)

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
)

@Serializable
data class NouvellePiece(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("storage_path") val chemin: String,
    val mime: String,
    @SerialName("taille_ko") val ko: Int,
    @SerialName("depose_par") val deposePar: String,
)
