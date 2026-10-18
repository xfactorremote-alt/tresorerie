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
/** Choix de plusieurs photos d'un coup (publications « Photos ») : chacune réduite à 1600 px en JPEG. */
@androidx.compose.runtime.Composable
expect fun rememberChoixPhotos(quandChoisi: (List<Fichier>, String?) -> Unit): () -> Unit

expect fun imageDepuisOctets(octets: ByteArray): androidx.compose.ui.graphics.ImageBitmap?

/** Image décodée en taille réduite (côté le plus petit ≥ max px) : une grande photo d'appareil ne sature pas la mémoire. Null si illisible. */
expect fun imageDepuisOctetsReduite(octets: ByteArray, max: Int): androidx.compose.ui.graphics.ImageBitmap?

/** Appareil photo : prend une photo (redressée, réduite à 1600 px, JPEG), comme `capture="environment"` du site. Renvoie la fonction qui ouvre l'appareil. */
@Composable
expect fun rememberPrendrePhoto(quandPrise: (Fichier?, String?) -> Unit): () -> Unit

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
expect fun notifierSysteme(titre: String, texte: String, nombre: Int = 0)
/** Tout est vu : retire la notification (et le compteur de l'icône). */
expect fun effacerNotifications()
/** Vérifie les nouveautés en arrière-plan, application fermée (toutes les 15 minutes). */
expect fun planifierVerification()
expect fun notificationsPermises(): Boolean

/** Demande l'autorisation d'afficher des notifications (Android 13 et plus). */
@Composable
expect fun rememberDemandeNotifications(quandFini: (Boolean) -> Unit): () -> Unit

/** Ajoute un rendez-vous à l'agenda du téléphone (Google Agenda…) ; faux si aucune application d'agenda. */
expect fun ajouterAgenda(nom: String, date: String, heureDebut: String?, dateFin: String?, heureFin: String?, lieu: String?, description: String?): Boolean

/** WhatsApp Business est-il installé ? (pour écrire avec le numéro de l'association plutôt que le sien) */
expect fun whatsappBusinessInstalle(): Boolean
/** Ouvre une conversation WhatsApp préremplie ; dans WhatsApp Business si `business`. */
expect fun ouvrirWhatsApp(numero: String, texte: String, business: Boolean)
