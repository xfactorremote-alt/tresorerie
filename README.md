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
| Accueil : bannière avec la photo de l’association et la trésorerie, comptes (touchez pour voir les opérations), rubriques dépliables mémorisées | Oui | Oui |
| Opérations : filtres recettes, dépenses, période, compte, catégorie, sans pièce, recherche ; totaux et solde | Oui | Oui |
| Pièces jointes et signature visibles dans le détail ; ajout de pièce ; contre-passation | Oui | Oui |
| Demandes de dépense : demande, signature, paiement, justificatif | Oui | Oui |
| Cotisations mensuelles (ou trimestrielles, annuelles) : grille par mois, retard, avance, dispense, relance WhatsApp | Oui | Oui |
| Encaissement rattaché à un tiers (membre, donateur, fournisseur) et à une rubrique : cotisation ou participation à une activité | Oui | Oui |
| Participations : collecte liée à une activité, montant par personne, qui a donné quoi, relance | Oui | Oui |
| Tiers : fiche de chaque membre ou tiers, ce qu’il a donné ou reçu | Oui | Oui |
| Planning : mois, semaine, agenda ; ajout d’un événement sur une date ; anniversaires | Oui | Oui |
| Membres : fiche, photo, modification, import CSV | Oui | Oui |
| Budget ; suivi des activités (budget, dépenses, recettes, participations) | Oui | Oui |
| Rapprochement avec relevé obligatoire | Oui | Oui |
| Analyse sur l’accueil : résultat, dépenses comparées à l’an passé, réserve en mois, jauge des cotisations, recettes et dépenses par mois, évolution de la trésorerie, diagrammes circulaires (origine des recettes, destination des dépenses) ; code couleur unique : bleu pour les recettes, orange pour les dépenses, gris pour la trésorerie | Oui | Oui |
| Rapport d’assemblée générale synthétique : l’essentiel, faits marquants, graphiques, trésorerie par compte, cotisations, participations, budget, contrôle interne, signatures | Oui | Oui |
| Rapports PDF, exports Excel, sauvegarde | Oui | Oui |
| Paramètres : association, comptes, catégories, **rôles et droits**, personnes et invitations | Oui | Oui |
| Menu du profil (en haut à droite) : ma cotisation, déconnexion | Oui | Oui |

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

1. **Paramètres** : nom, logo, cotisation (20 € par mois par défaut ; périodicité mensuelle, trimestrielle, semestrielle ou annuelle), délai du justificatif (7 jours après paiement).
2. **Membres** > **Importer** : téléchargez le modèle, remplissez-le dans Excel, enregistrez en CSV ou laissez en .xlsx, puis importez. Les lignes sans prénom, nom, jour ou mois sont refusées avec la raison.
3. **Cotisations** > **Générer les cotisations** de l’année : une ligne par membre et par période, à partir de son mois d’adhésion. Réglez la périodicité avant de générer l’année : les périodes déjà créées ne bougent pas.
4. **Paramètres** > **Comptes** : montant compté dans la caisse et montant du dernier relevé bancaire, au jour du démarrage. Ce ne sont pas des recettes, ils ne gonflent donc pas le résultat de l’année.
5. **Paramètres** > **Personnes** > **Inviter** : le président (rôle Président), les membres du bureau (Bureau), puis les adhérents (Adhérent, avec leur fiche liée). Chaque personne crée ensuite son compte sur le site avec la même adresse.
6. Si besoin, **Paramètres** > **Rôles et droits** : créez d’autres rôles et ajustez les droits.

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
- Anniversaires : jour et mois seulement, sans l’année. Les adhérents ne voient que les membres qui ont donné leur accord, avec l’initiale du nom.

## Vérifier après installation

1. Créez une recette de 10 € en caisse : le solde de la caisse augmente de 10 €.
2. Contre-passez-la : le solde revient à la valeur de départ.
3. Connectez-vous avec un compte adhérent : seuls la cotisation, les anniversaires et le planning apparaissent.
4. Retirez un droit au rôle Bureau, reconnectez-vous en bureau : l’entrée correspondante disparaît du menu.

## Sauvegarde

Chaque mois, **Rapports** > **Télécharger la sauvegarde**, puis déposez le fichier sur le Google Drive de l’association. Les justificatifs restent dans Supabase (1 Go gratuit, soit plusieurs milliers de photos réduites).
