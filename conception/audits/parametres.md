# Audit de parité : périmètre « parametres »

Périmètre couvert : Paramètres (menu et sous-pages), Plus › Communiqués, Budget, Connexion, navigation (survol).
Captures du site : `scratchpad/audit/parametres/*.png`. Le site a été testé avec Playwright (Pixel 7) : suppression d'un exercice, puis Rapports et Corbeille. Aucune erreur JavaScript, aucun débordement horizontal.
Gradle n'a pas été lancé, conformément aux consignes.

## Problème signalé : suppression de l'exercice 2026

- **Pourquoi « j'ai toujours les mêmes écritures » :** c'est normal. La base (`supprimer()`), `mock.js` et `Demo.supprimer` retirent seulement la ligne `exercices`. Rien n'est supprimé en cascade dans `transactions`.
- **Phrase ajoutée des deux côtés, avec le même texte.** Elle apparaît dans un encadré « info » bleu, au-dessus du texte habituel sur la corbeille :
  « Les écritures de cette période ne sont pas supprimées : seule la période l'est. Elles restent dans Opérations ; pour les retirer, supprimez-les une à une. »
  - Site : `NOTE_SUPPR_EXERCICE` et un 5ᵉ paramètre facultatif `note` dans `supprimerAvecConfirmation`. Les autres appels ne changent pas.
  - Android : `NOTE_SUPPR_EXERCICE` et un paramètre facultatif `note` dans `rememberSuppression(message, note = null, apres)`. Les appels existants `rememberSuppression(message) { … }` ne changent pas.
- **Défaut Android corrigé : l'exercice supprimé restait dans les choix de Rapports.** Avant, après une suppression, `ParamExercices` ne rechargeait que sa propre liste. Or `Donnees.exercices`, qu'utilisent Rapports et le verrou des opérations, n'était rechargé que par le temps réel. En démo, `Synchro.localement` n'est jamais appelé, donc rien ne se mettait à jour. Correction :
  - `ParamExercices(d, message, recharger)` appelle `recharger()` après chaque suppression, chaque « Annuler », création, modification, clôture et réouverture.
  - `EcranParametres` lui transmet `recharger`.
- **Corbeille Android, alignée sur le site :**
  - Une seule carte contient le texte, la liste et l'état vide (« La corbeille est vide. »).
  - Chaque ligne : puce du type, libellé, « Supprimé le … par <nom> · motif · restauré le … », et un bouton « Restaurer » à contour sur la même ligne.
  - Une carte repliable « Déjà restaurés (n) » reprend la même ligne, avec la puce « Restauré » en bleu (`puce-ok`).
  - Le nom affiché suit la règle du site : son propre nom au lieu de « vous ».
  - La restauration recharge `Donnees`, la liste et les pastilles, et le bouton est désactivé pendant l'envoi.
  - Le type « Communiqué » a été ajouté à `TYPES_CORBEILLE` des deux côtés (il manquait sur le site aussi).

## Différences corrigées (Android aligné sur le site)

### Exercices
- Une seule carte avec des filets entre les lignes. Le texte d'explication et « Nouvel exercice » sont sur la même ligne.
- Les actions suivent l'ordre du site : Modifier, Clôturer (contour), puis l'icône corbeille. Avant : Supprimer, Modifier, Clôturer.
- Message vide ajouté : « Aucun exercice. Créez le premier. »
- Couleurs alignées sur `puce-ok` (bleu) : la puce « En cours » et le « OK » des points de contrôle.
- Le point de contrôle des demandes porte désormais « (à valider, à payer ou sans justificatif) ».

### Confirmation de suppression (tous les écrans Android)
- Le bouton « Supprimer » est à contour rouge avec l'icône corbeille, comme `.btn-danger` du site. Avant, il était plein.

### Comptes
- Chaque ligne présente :
  - une icône banque ou caisse ;
  - le libellé « <nom> · solde de départ (€) » ;
  - la case « Actif » ;
  - une corbeille grise.
- En bas : « Ajouter un compte » et « Enregistrer ». L'enregistrement prend les soldes et les cases ensemble et affiche le message « Comptes enregistrés ». Avant, l'interrupteur enregistrait aussitôt.
- La note dit maintenant « décochez « Actif » pour le masquer ».
- Fenêtre « Nouveau compte » : « Nom * », puis Type (Banque, Caisse) et « Solde de départ (€) ».
- Code mort supprimé : la fenêtre « categorie ».

### Catégories
- L'ajout se fait dans la carte, avec le champ « Nouvelle catégorie » et le bouton « Ajouter ». Avant, il ouvrait une fenêtre.
- Filets entre les lignes ; la corbeille est grise.

### Cotisations et dépenses
- Ordre et libellés du site :
  - section **Cotisation** : « Montant (€ par période) », Périodicité, « Comment régler (affiché aux membres) » (placeholder du site, 400 caractères) ;
  - section **Demandes de dépense et budget** : « Délai du justificatif (jours après paiement) », « Justification obligatoire à partir de (€) », « Alerte budget (% du prévu) ».

### Identité et coordonnées
- Placeholders ajoutés : W381000000 et https://.
- Longueurs maximales du site.
- Objet sur 2 lignes.

### Logo et bannière
- Textes du site : « Aucune photo. Format paysage conseillé, au moins 1600 pixels de large. » et « … retirée à l'enregistrement. »
- Si rien n'a été choisi, le message affiché est « Choisissez d'abord un logo ou une photo ».

### Comptes et accès
Refait comme la carte du site :
- bouton « Donner un accès », qui ouvre Membres ;
- par ligne : état « Accès actif » ou « Accès désactivé », choix de la fiche de membre (« Sans fiche de membre » et les fiches libres), choix du rôle, case « Actif » ;
- invitations dans la même liste, atténuées, avec l'avatar « @ », « … · invitation en attente », la puce du rôle et « Retirer » ;
- messages « Rôle attribué » et « Fiche rattachée ». Si on modifie son propre compte, les données sont rechargées.

### Rôles
- Alerte au texte du site : « <Rôle> peut valider et payer. La même personne ne pourra pas payer une dépense qu'elle a validée. »

### Notifications
- Carte « Alertes sur cet appareil » identique à `blocAlertes` :
  - état « Notifications activées / désactivées sur cet appareil » avec sa phrase ;
  - boutons « Recevoir les notifications » et « Essayer » ;
  - cases à cocher avec icônes : « Son à l'arrivée d'une nouveauté » et « Vibration du téléphone ».
- Avant, Android avait trois cartes et des interrupteurs.

### Mot de passe et session
- « session gardée sur cet appareil ».

### Sauvegarde
- Le texte se termine par « (archive des pièces) ».
- Le bouton porte une icône.

### Plus › Communiqués (`ParamBanniere`, `EditionCommunique`)
- Liste avec filets et avatar orange, comme `.avatar` du site. Avant, l'avatar était bleu.
- Deux colonnes au-delà de 840 dp : Publications d'un côté, Ordre et fréquence de l'autre.
- Édition :
  - texte avec le placeholder « Ce que les membres doivent savoir » sur 3 lignes ;
  - « Réponse obligatoire » ;
  - choix avec le placeholder « Un choix par ligne » ;
  - « Ajouter une question » et « Ajouter des photos » en boutons tonals ;
  - « Afficher à partir du », « Jusqu'au (facultatif) » / « Réponses jusqu'au (facultatif) » ;
  - priorité « Important (en premier sur la bannière) » ;
  - icônes des genres dans le choix du type.

### Budget
- Les trois chiffres sont sur une même ligne, comme `.kpis`.
- La ligne d'un poste suit le tableau du site sur téléphone : poste et puce | prévu ; « Réalisé … » | barre de 64 dp et %, ou « – ».
- Pied du tableau : « Total · Prévu … · Réalisé … ».
- Deux colonnes, Ressources et Emplois, au-delà de 840 dp.
- Activités :
  - leurs cartes sont dans la carte « Activités », deux par ligne sur grand écran ;
  - trois pavés sur fond gris ;
  - l'alerte est un encadré ;
  - « Prévoir une ligne » apparaît avant les lignes, comme sur le site.
- Export : feuille « Budget <an> » avec les cartes PDF et Excel, mêmes textes que `choisirFormat`. Les colonnes CSV et PDF étaient déjà identiques.
- Aide : « dès que vous quittez la case ».
- Rayons de 8 dp sur les encadrés.

### Connexion
- Android exigeait 8 caractères pour **se connecter** : un mot de passe ancien plus court ne pouvait plus entrer. Désormais, comme sur le site, le mot de passe est simplement requis pour se connecter ; les 8 caractères ne sont exigés qu'à la création.

## Différences restantes (avec la raison)

- **Rôles :** le site affiche une matrice rôles × droits ; Android garde un rôle choisi par puces, avec des interrupteurs. Une matrice n'est pas lisible sur un téléphone. Même règle, même conflit signalé.
- **Ordre et fréquence des annonces :** Android utilise des puces-menus là où le site a deux listes déroulantes pleine largeur. Fonction identique.
- **Barre des Paramètres :** sur téléphone, le site affiche « Fermer » en icône ronde seule ; Android garde l'icône et le texte « Fermer ».
- **Notifications :** le site propose « Couper », qu'Android ne peut pas faire depuis l'application (c'est un réglage du système). La description du menu dit « alertes du navigateur » sur le site et « notifications du téléphone » sur Android, ce qui est propre à chaque support.
- **Connexion :** le site présente le formulaire dans une carte, avec le logo et le nom côte à côte. Android le présente sans carte, logo et nom centrés, ce qui correspond à la liste de contrôle du CLAUDE.md.
- **Exercices :** sur Android, `Repo.enregistrerExercice` met aussi à jour `organisation.exercice_debut` ; le site ne le fait pas. Ce point relève de Repo, hors de mon périmètre, et n'est pas modifié.
- **Navigation, pastilles, cloche :** relus, même logique (Plus = activités, membres, et communiqués pour le bureau). Rien à corriger dans le temps imparti.

## Android meilleur que le site (à reporter sur le site)

- **Budget :** le réalisé N-1 de chaque poste reste visible sur téléphone. Le site le masque sous 600 px, alors que la liste de contrôle demande N-1.
- **Comptes et accès :** le site affiche un titre « Comptes et accès » qui répète l'en-tête de section (règle 11). Android ne le reprend pas.
- **Budget :** message « Aucune catégorie. Créez-les dans Paramètres… » quand un tableau est vide ; le site n'en a pas.
- **Site, code mort :** `paramPersonnes` contient encore le gestionnaire `#b-inviter` et sa feuille « Inviter », sans bouton. Le même code mort existe dans `ParamPersonnes` sur Android.
- **Site, texte à corriger :** l'aide du Budget renvoie à « Paramètres, Montants et comptes ». Cette section n'existe plus (aujourd'hui : Finances › Catégories). Le texte est identique des deux côtés et à corriger ensemble.
