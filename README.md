# Trésorerie JP Grenoble

Site web et application Android, mêmes fonctionnalités, gratuits, sur Supabase et GitHub.

| Dossier | Contenu |
|---|---|
| `supabase/schema.sql` | Base de données, rôles et droits modifiables, circuit des dépenses |
| `docs/` | Site web (publié par GitHub Pages) |
| `android/` | Application Android Kotlin Multiplatform |
| `.github/workflows/` | Construction automatique de l’APK, réveil de la base |

| Fonctionnalité | Site | Application Android |
|---|---|---|
| Accueil : bannière tout en haut (photo, logo, nom, roue dentée des paramètres) avec la trésorerie, comptes (touchez pour voir les opérations), rubriques dépliables mémorisées ; **Ma situation** (cotisation et participations) pour chaque membre du bureau | Oui | Oui |
| **Lien personnel** des membres : chaque membre ouvre sa page (cotisation, participations, rendez-vous, comment régler) d’un geste, sans compte, sans mot de passe, sans installation ; envoi par WhatsApp ou e-mail, un par un ou pour tous ; lien renouvelable ou coupé à tout moment | Oui | Oui (envoi ; la page s’ouvre dans le navigateur) |
| Opérations : recherche et un bouton **Filtres** (type, période, compte, catégorie, rubrique, sans pièce), filtres actifs en pastilles ; totaux et solde | Oui | Oui |
| **Virement interne** : dépôt d’espèces à la banque, retrait pour la caisse, virement entre comptes ; ni recette ni dépense, annulable avec motif | Oui | Oui |
| Historique de chaque opération (référence, auteur, demande, annulation et motif) ; contre-passation avec motif ; alerte compte/mode inhabituel | Oui | Oui |
| Pièces jointes et signature visibles dans le détail ; ajout de pièce ; contre-passation | Oui | Oui |
| Demandes de dépense (onglet **Demandes**) : demande, signature du président, paiement, justificatif | Oui | Oui |
| Dépense saisie directement dans les opérations : case « Faire valider par le président » (cochée par défaut) ou bouton « Faire valider » dans le détail ; le président la valide après coup, elle reste signalée « À valider » ou « Refusée » | Oui | Oui |
| Cotisations mensuelles (ou trimestrielles, annuelles) : grille par mois, retard, avance, dispense, relance WhatsApp | Oui | Oui |
| Encaissement rattaché à un tiers (membre, donateur, fournisseur) et à une rubrique : cotisation ou participation à une activité | Oui | Oui |
| Participations : collecte liée à une activité, montant par personne, qui a donné quoi, relance | Oui | Oui |
| Tiers : fiche de chaque membre ou tiers, ce qu’il a donné ou reçu | Oui | Oui |
| Planning : **Calendrier** (affichage par mois ou par semaine) et **À venir** (rendez-vous des douze prochains mois) ; case « Suivre le budget » pour une activité | Oui | Oui |
| Membres : fiche, photo, modification, import CSV | Oui | Oui |
| Budget : **ressources et emplois** côte à côte, tous les postes listés, prévu, réalisé, réalisé de l’année précédente, excédent ou déficit prévu, reprise du réalisé de l’an passé ; budget de chaque activité (ressources, emplois, résultat) | Oui | Oui |
| **Matériel** : inventaire des instruments, de la sonorisation, de l’informatique, des tenues ; valeur d’achat et valeur actuelle, lieu de rangement, membre qui l’a en main, vérification annuelle, sortie (vendu, perdu, volé…), historique, photo ; inventaire PDF signé ; achat de matériel inscrit depuis l’opération | Oui | Oui |
| Rapprochement avec relevé obligatoire | Oui | Oui |
| Analyse sur l’accueil : résultat, dépenses comparées à l’an passé, réserve en mois, jauge des cotisations, recettes et dépenses par mois, évolution de la trésorerie, diagrammes circulaires (origine des recettes, destination des dépenses) ; code couleur unique : bleu pour les recettes, orange pour les dépenses, gris pour la trésorerie | Oui | Oui |
| Rapport d’assemblée générale synthétique : l’essentiel, faits marquants, graphiques, trésorerie par compte, cotisations, participations, budget, contrôle interne, signatures | Oui | Oui |
| Documents PDF mis en page (en-tête avec logo, synthèse, totaux, pages numérotées, signatures) : journal des opérations, état des cotisations, budget, registre des demandes, liste des membres ; export PDF ou Excel depuis Opérations et Membres | Oui | Oui |
| Archive ZIP des pièces justificatives d’un exercice, classées par mois, avec inventaire et dépenses sans pièce ; relevés de rapprochement inclus | Oui | Oui |
| Rapports PDF, exports Excel, sauvegarde | Oui | Oui |
| Paramètres (roue dentée, en bas du menu ou en haut à droite sur téléphone) : **Mon compte** pour tous (nom, fiche de membre, mot de passe, déconnexion) ; pour l’administrateur : association, comptes, catégories, **rôles et droits**, accès | Oui | Oui |
| Membre d’abord, fonction ensuite : groupe Bureau en tête de la liste des membres, bouton « Fonction » pour donner un accès et désigner président, trésorier, bureau… | Oui | Oui |
| Accueil : « Bien démarrer » coche les étapes de mise en route au fur et à mesure | Oui | Oui |

## Rôles et droits

Rien n’est figé : **Paramètres > Rôles et droits** permet de créer un rôle (par exemple Secrétaire ou Trésorier adjoint), de copier les droits d’un autre rôle, puis d’activer ou retirer chaque droit. Le menu de l’application s’adapte aux droits de chaque personne.

| Droit | Trésorier | Président | Bureau | Adhérent |
|---|:-:|:-:|:-:|:-:|
| Voir les soldes, opérations, budget et rapports | ✓ | ✓ | ✓ | |
| Saisir et corriger les opérations, joindre les pièces | ✓ | | | |
| Gérer les cotisations et les participations | ✓ | | | |
| Rapprocher la caisse et la banque | ✓ | | | |
| Construire le budget | ✓ | | | |
| Demander une dépense | ✓ | ✓ | ✓ | |
| Valider ou refuser une dépense (signature) | | ✓ | | |
| Payer une dépense validée | ✓ | | | |
| Voir les membres | ✓ | ✓ | ✓ | |
| Gérer les membres | ✓ | | | |
| Gérer les activités et le planning | ✓ | | | |
| Tenir l’inventaire du matériel | ✓ | | | |
| Administrer (paramètres, rôles, accès) | ✓ | | | |

Règles que la base impose, quels que soient les droits accordés :
- au moins une personne active garde le droit d’administrer ;
- la personne qui a validé une dépense ne peut pas la payer ;
- un rôle encore attribué ne peut pas être supprimé ;
- sans aucun droit, une personne voit seulement sa cotisation, les anniversaires et le planning.

Avant toute installation, le site (`docs/index.html`) et l’application Android s’ouvrent en **mode démonstration** tant que l’adresse Supabase n’est pas renseignée : données fictives, effacées à la fermeture.

| Rôle | Adresse | Mot de passe |
|---|---|---|
| Trésorier | tresorier@demo.jp | Demo2026 |
| Président | president@demo.jp | Demo2026 |
| Bureau | bureau@demo.jp | Demo2026 |
| Adhérent | adherent@demo.jp | Demo2026 |

Ces comptes n’existent que dans le mode démonstration : ils disparaissent dès que l’adresse Supabase est renseignée.

---

## Mise en ligne, pas à pas (environ 45 minutes)

### 1. Créer la base Supabase

1. Créez un compte sur supabase.com, puis **New project**. Nom : `tresorerie-jp`. Région : **Europe (Paris ou Frankfurt)** pour garder les données dans l’Union européenne. Notez le mot de passe de la base dans un endroit sûr.
2. Menu **SQL Editor** > **New query** : collez tout le contenu de `supabase/schema.sql`, puis **Run**. Le message attendu est « Success. No rows returned ».
3. Menu **Project Settings** > **API** : notez l’**URL du projet** et la clé **anon** (ou **publishable**). Ne copiez jamais la clé `service_role` dans le site ou l’application.

### 2. Publier le site sur GitHub Pages

1. Créez un compte sur github.com, puis un dépôt **public** nommé `tresorerie`.
2. Dans `docs/config.js`, remplacez `https://VOTRE-PROJET.supabase.co` et `VOTRE_CLE_ANON` par les deux valeurs notées.
3. **Add file** > **Upload files** : déposez le contenu de ce dossier (`docs`, `android`, `supabase`, `.github`, `README.md`), puis **Commit changes**. Le dossier `.github` est caché sur certains ordinateurs : affichez les fichiers cachés pour le voir.
4. **Settings** > **Pages** : Source **Deploy from a branch**, branche `main`, dossier `/docs`. Au bout d’une minute, le site est à l’adresse `https://<votre-identifiant>.github.io/tresorerie/`.
5. Retour dans Supabase, **Authentication** > **URL Configuration** : mettez cette adresse dans **Site URL** et dans **Redirect URLs**. Sans cela, les liens de confirmation et de mot de passe oublié renvoient vers une mauvaise page.

### 3. Créer le compte du trésorier (à faire tout de suite)

Le **premier compte créé devient trésorier**, avec le droit d’administrer. Les suivants n’ont accès à rien sans invitation.

1. Ouvrez le site, saisissez votre adresse et un mot de passe, puis **Créer mon compte**.
2. Ouvrez le lien de confirmation reçu par e-mail, puis connectez-vous.
3. Dans Supabase, **Table Editor** > `profiles` : vérifiez qu’il y a une seule ligne, avec le rôle `tresorier`.

### 4. Paramétrer

L’accueil affiche **Bien démarrer** : chaque étape se coche toute seule une fois faite.

1. **Créer votre fiche de membre** : on est d’abord membre, puis on reçoit une fonction. **Membres** > **Créer ma fiche** (ou **Paramètres** > **Mon compte**). Votre compte y est rattaché et vous apparaissez dans le groupe **Bureau**.
2. **Paramètres** > **Association** : nom, logo, photo de la bannière.
3. **Paramètres** > **Montants et comptes** : cotisation (20 € par mois par défaut ; périodicité mensuelle, trimestrielle, semestrielle ou annuelle), délai du justificatif (7 jours après paiement), puis montant compté dans la caisse et montant du dernier relevé bancaire au jour du démarrage. Ces soldes de départ ne sont pas des recettes : ils ne gonflent pas le résultat de l’année.
4. **Membres** : ajoutez-les un par un (bouton +) ou **Importer** (CSV ou Excel). Les lignes sans prénom, nom, jour ou mois sont refusées avec la raison.
5. **Envoyer à chaque membre son lien personnel** : **Paramètres** > **Montants et comptes** > « Comment régler » (IBAN, espèces…), puis **Membres** > **Liens personnels** > **Créer les liens**, et un geste par membre pour l’envoyer par WhatsApp. Le membre touche le lien : sa page s’ouvre, sans compte ni mot de passe. Il l’ajoute à l’écran d’accueil de son téléphone pour l’ouvrir ensuite d’un geste. Les relances de cotisation contiennent aussi ce lien.
6. **Désigner le bureau** : dans **Membres**, bouton **Fonction** sur la ligne du membre, saisissez son e-mail, choisissez sa fonction (Président, Bureau…), puis **Donner l’accès**. La personne crée son compte sur le site avec cette adresse ; sa fonction s’applique dès la création. Un lien permet de la prévenir par WhatsApp.
7. **Cotisations** > **Générer les cotisations** de l’année : une ligne par membre et par période, à partir de son mois d’adhésion. Réglez la périodicité avant : les périodes déjà créées ne bougent pas.
8. **Budget** : saisissez le montant prévu de chaque ressource et de chaque emploi (ou **Reprendre le réalisé** de l’an passé), visez l’équilibre.
9. **Matériel** : inscrivez les instruments et autres biens, avec leur valeur et leur lieu de rangement ; vérifiez-les une fois par an avant l’assemblée générale.
10. Si besoin, **Paramètres** > **Rôles et droits** : créez d’autres fonctions (Secrétaire, Vice-président, Trésorier adjoint…) et ajustez leurs droits.

Pour se déconnecter : **Paramètres** > **Mon compte** > **Se déconnecter**.

Limite de l’offre gratuite : Supabase envoie environ 2 e-mails de confirmation par heure. Pour inviter beaucoup d’adhérents d’un coup, branchez un service d’e-mail gratuit (Brevo, 300 e-mails par jour) dans **Authentication** > **Emails** > **SMTP Settings**, ou étalez les invitations.

### 5. Construire l’application Android

1. Dans GitHub, **Settings** > **Secrets and variables** > **Actions** > **New repository secret** : créez `SUPABASE_URL` et `SUPABASE_ANON_KEY` avec les deux valeurs de l’étape 1.
2. Onglet **Actions** > **APK Android** > **Run workflow**. Comptez 5 à 8 minutes.
3. Ouvrez l’exécution terminée, téléchargez **tresorerie-apk** (un .zip), décompressez-le sur le téléphone et ouvrez l’APK. Android demande d’autoriser l’installation depuis cette source : acceptez pour ce fichier uniquement.

Le même secret sert au **réveil de la base** : Supabase gratuit se met en pause après 7 jours sans activité, une tâche GitHub l’appelle tous les 3 jours.

---

## Règles de gestion appliquées par la base

- Par défaut, le trésorier saisit et administre ; le **président valide seul** les demandes de dépense, avec sa signature, et en assume la responsabilité ; le trésorier paie ; le demandeur (ou la personne qui a payé) joint le justificatif.
- Cotisations : chaque versement est imputé sur la période la plus ancienne non réglée. Un membre est « à jour » quand toutes les périodes déjà commencées sont réglées ; le surplus apparaît en avance. Montant dû à 0 : membre dispensé pour cette période.
- Une cotisation se rattache toujours à un membre ; une opération n’a qu’un tiers (un membre ou un autre tiers).
- Justificatif en retard : signalé dès 7 jours après le paiement, tant qu’il n’est pas déposé.
- Aucune suppression d’écriture : une erreur se corrige par **contre-passation** (écriture de correction en négatif, même catégorie, même compte).
- Lien personnel : jeton aléatoire de 32 caractères, impossible à deviner ; il donne accès à la situation d’un seul membre et à rien d’autre ; renouveler le lien rend l’ancien inutilisable.
- Anniversaires : jour et mois seulement, sans l’année. Les adhérents ne voient que les membres qui ont donné leur accord, avec l’initiale du nom.

## Vérifier après installation

1. Créez une recette de 10 € en caisse : le solde de la caisse augmente de 10 €.
2. Contre-passez-la : le solde revient à la valeur de départ.
3. Connectez-vous avec un compte adhérent : seuls la cotisation, les anniversaires et le planning apparaissent.
4. Retirez un droit au rôle Bureau, reconnectez-vous en bureau : l’entrée correspondante disparaît du menu.

## Sauvegarde

Les pièces justificatives (photos et PDF des factures) sont conservées dans Supabase (1 Go gratuit, plusieurs milliers de pièces réduites) : elles ne se perdent pas d’un exercice à l’autre.

La copie sur Google Drive n’est **pas automatique** : elle demanderait de confier à un service extérieur les clés d’accès complètes de la base. Deux gestes suffisent :

1. **Chaque mois**, **Rapports** > **Télécharger la sauvegarde** (toutes les données), puis déposez le fichier dans le dossier Drive de l’association.
2. **En fin d’exercice**, **Rapports** > **Pièces justificatives** > **Télécharger les pièces** : un ZIP classé par mois (fichiers nommés date_montant_libellé), avec `inventaire.csv` qui liste aussi les dépenses sans pièce, et les relevés des rapprochements. Déposez-le sur Drive avec le **Journal des opérations** et le **Rapport d’assemblée générale** en PDF.

Testez une fois la restauration : ouvrez le ZIP et vérifiez qu’une facture s’affiche.

## Sauvegarde et restauration

1. **Sauvegarder** : Rapports > Document « Sauvegarde complète des données » > Exporter. Un fichier `sauvegarde-tresorerie-AAAA-MM-JJ.json` est téléchargé. À faire chaque mois, et à ranger hors de la plateforme (Drive, clé USB).
2. **Restaurer** (sur un ordinateur avec Node.js) :
   `node outils/restaurer-sauvegarde.mjs sauvegarde-tresorerie-AAAA-MM-JJ.json > restauration.sql`
   puis coller `restauration.sql` dans Supabase > SQL Editor, sur une base où `supabase/schema.sql` a déjà été exécuté. Tout passe ou rien ne change ; la dernière requête affiche les soldes à comparer avec ceux d'avant.
3. Les comptes de connexion ne sont pas restaurés (les personnes se reconnectent ou sont réinvitées). Les pièces justificatives restent dans Supabase Storage ou dans l'archive ZIP annuelle.

Essayez une restauration sur un projet Supabase de test au moins une fois par an : une sauvegarde jamais restaurée n'est pas une sauvegarde vérifiée.

