package org.jpgrenoble.tresorerie

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset

// Fonctions qui dépendent du téléphone (Android aujourd'hui, iOS plus tard)

/** Empreinte SHA-256 en hexadécimal. */
expect fun sha256(texte: String): String

/** Dessine les traits de la signature sur fond blanc et renvoie une image PNG. */
expect fun signatureEnPng(traits: List<List<Offset>>, largeur: Int, hauteur: Int): ByteArray

/**
 * Sélecteur de fichier : photo (réduite à 1600 px, JPEG) ou PDF (3 Mo maximum).
 * Renvoie la fonction qui ouvre le sélecteur.
 */
@Composable
expect fun rememberChoixFichier(pdfAccepte: Boolean = true, quandChoisi: (Fichier?, String?) -> Unit): () -> Unit

/** Image affichable à partir d'octets (logo, photo, justificatif), ou null si illisible. */
expect fun imageDepuisOctets(octets: ByteArray): androidx.compose.ui.graphics.ImageBitmap?

/** Choix d'un fichier texte (CSV) ; renvoie son contenu. */
@Composable
expect fun rememberChoixTexte(quandChoisi: (String?) -> Unit): () -> Unit

/** Enregistre un fichier là où la personne le choisit (Téléchargements, Drive…). */
@Composable
expect fun rememberEnregistrer(quandFini: (String?) -> Unit): (nom: String, mime: String, octets: ByteArray) -> Unit

/** Ouvre l'impression du téléphone sur une page HTML (enregistrable en PDF). */
@Composable
expect fun rememberImpression(): (titre: String, html: String) -> Unit

/** Ouvre un fichier (PDF, photo) dans l'application du téléphone prévue pour ce format. */
@Composable
expect fun rememberOuvrirFichier(): (nom: String, mime: String, octets: ByteArray) -> Unit

/** Bouton « retour » du téléphone : ferme le sous-écran au lieu de quitter l'application. */
@Composable
expect fun RetourSysteme(actif: Boolean, onRetour: () -> Unit)

/** Archive ZIP en mémoire : chemins relatifs et contenus. */
expect fun zipper(fichiers: List<Pair<String, ByteArray>>): ByteArray

/** Petite préférence gardée sur le téléphone (rester connecté, dernière vue…). */
expect fun lirePreference(cle: String): String?
expect fun garderPreference(cle: String, valeur: String?)

/** Notification du téléphone (nouveautés) ; sans effet si la personne ne l'a pas autorisée. */
expect fun notifierSysteme(titre: String, texte: String)
expect fun notificationsPermises(): Boolean

/** Demande l'autorisation d'afficher des notifications (Android 13 et plus). */
@Composable
expect fun rememberDemandeNotifications(quandFini: (Boolean) -> Unit): () -> Unit

/** Ajoute un rendez-vous à l'agenda du téléphone (Google Agenda…) ; faux si aucune application d'agenda. */
expect fun ajouterAgenda(nom: String, date: String, heureDebut: String?, dateFin: String?, heureFin: String?, lieu: String?, description: String?): Boolean
