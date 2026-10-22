# Rapport de validation — 0.29.0 (12 octobre 2026)

## Exécuté

| Vérification | Commande / outil | Résultat |
|---|---|---|
| Compilation Android | `./gradlew :composeApp:compileDebugKotlinAndroid` | ✅ sans erreur |
| Tests unitaires Android | `:composeApp:testDebugUnitTest` | ✅ AuditTest 23/23, VersionTest 1/1 |
| Boutons rectangulaires partout | `javap` sur les classes compilées (Ecrans2Kt, MaterielKt) | ✅ 48 appels vers `BoutonsKt`, 0 appel direct Material |
| Site, toutes les vues | Playwright `a5.mjs` : 144 vues (rôles × pages × téléphone/ordinateur) | ✅ 0 débordement, 0 erreur JavaScript |
| Demandes | `dm2.mjs` | ✅ fiches et actions selon le rôle |
| Membres | `m2.mjs` | ✅ fiche, menu ⋯, filtre, suppression |
| Accessibilité site | `acc.mjs` (13 pages × 2 rôles × 2 formats) | ✅ 0 nom manquant, 0 alt manquant, 0 étiquette manquante ; cibles < 24 px : 0 tactile |
| Zones tactiles | `tap.mjs` : point de bannière 24×24 sélectionne l'annonce ; toucher une barre du calendrier ouvre le jour | ✅ |
| Version | `VersionTest` (versionName = `?v=` de index.html) | ✅ 0.29.0 |

## Non vérifié (à faire)

- Affichage réel sur téléphone Android et tablette : nouvelles formes, boutons, sélecteur de date, points de la bannière.
- TalkBack / lecteur d'écran.
- Mode sombre (non proposé par le produit).

## Risques restants

- `Boutons.kt` repose sur la priorité des déclarations du paquet sur l'import `androidx.compose.material3.*` : un import explicite de `androidx.compose.material3.Button` dans un fichier contournerait la règle (signalé dans le fichier et dans DESIGN_SYSTEM.md).
- 82 couleurs restent écrites en dur (couleurs de rendez-vous calculées, voiles de la bannière) : exceptions documentées.

## 0.32.0 — Planning compact et balayage iPhone / iPad (14 octobre 2026)

| Vérification | Résultat |
|---|---|
| Planning (site) : mois entier visible sans défiler | ✅ Pixel 7, iPhone 13, iPhone 14 Pro Max, Galaxy S9+, iPad, ordinateur 1280×800 ; ⚠️ iPhone SE 1re génération (320×568) : la dernière semaine demande un petit défilement |
| Balayage responsive `a7.mjs` : 6 tailles iPhone/iPad × 4 rôles × 13 pages = 312 vues | ✅ 0 débordement, 0 élément coupé, 0 champ < 16 px, 0 erreur JS |
| Page du lien personnel aux tailles iPhone/iPad/PC | ✅ 0 débordement, 0 erreur |
| Android : onglets Calendrier / À venir, compilation, 25 tests | ✅ compilé et testé ; **rendu à vérifier sur téléphone** (pas d'émulateur) |
| Safari réel (WebKit) | ❌ non disponible ici : à essayer sur un iPhone |

## 0.33.0 — Petit rappel au nom de l'association (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| Base locale : `envoyer_rappel` / `etat_rappels` (droits, canal, membre actif, message vide, sans appareil) | ✅ ; appliqué en production, empreintes identiques au dépôt |
| Site : `rappel.mjs` téléphone et ordinateur (notification, avertissement < 30 jours, membre sans alertes) | ✅ 0 erreur JS |
| Site : `a5.mjs` 144 vues, `a7.mjs` 312 vues iPhone/iPad | ✅ 0 débordement, 0 erreur |
| Android : compilation, 26 tests dont `rappelCommeLeSite` | ✅ ; **feuille et WhatsApp Business à vérifier sur un téléphone** |

## 0.34.0 — Petit rappel groupé (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| Site : `rappel-groupe.mjs` téléphone et ordinateur (cases, aperçu, envoi, étape 2, copie, réouverture, collecte) | ✅ 0 erreur JS |
| Site : `a5.mjs` 144 vues, `a7.mjs` 312 vues | ✅ |
| Android : compilation, 27 tests dont `rappelGroupeNoteChaqueEnvoi` | ✅ ; rendu à vérifier sur téléphone |
| Corrigé au passage : double icône dans l’encadré « déjà envoyé » du petit rappel (site) | ✅ |

## 0.35.0 — Rappels : même structure, montant et paiement mis en évidence (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| Site : structure comparée (individuel / groupé) par `rappel-groupe.mjs` | ✅ mêmes éléments, même ordre ; 0 erreur JS |
| Site : `rappel.mjs`, `a5.mjs` 144 vues, `a7.mjs` 312 vues | ✅ |
| Android : compilation, 27 tests (`rappelCommeLeSite` : 4 textes identiques au site, dont `sansGras`) | ✅ ; rendu à vérifier sur téléphone |

## 0.36.0 — Import des membres et scanner de documents (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| Base : colonnes nationalité, pupitre, statut (local puis production) | ✅ |
| Site : `import.mjs` (6 cas), `scanner.mjs` (téléphone, ordinateur, iPhone SE) | ✅ 0 erreur JS |
| Site : `a5.mjs` 144 vues, `a7.mjs` 312 vues | ✅ |
| Android : compilation, 29 tests dont `importMembresDoublons` et `scannerCommeLeSite` | ✅ ; scanner à essayer sur téléphone (appareil photo réel) |

## 0.36.1 — Nom de famille facultatif, correction des lignes refusées (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| Site : `import.mjs`, `import2.mjs` (sans nom, doublons par prénom + anniversaire, « Corriger ») | ✅ 0 erreur JS |
| Site : `a5.mjs` 144 vues, `a7.mjs` 312 vues | ✅ |
| Android : compilation, 30 tests dont `importSansNomEtCorrection` | ✅ |

## 0.44.0 — Material 3 Expressive sur tout le produit (21 octobre 2026)

- Site : jetons neutres (fond blanc, gris sans teinte, contours #74757E pour les champs), formes 8/12/16/24/28, boutons en pilule, bouton tonal orange doux, champs au contour net (focus 2 px), cartes et rubriques à filet gris, barre d'onglets et volet à indicateur en pilule, bouton + carré arrondi, fenêtres 28, bannière du bureau vivante, mois en pilules, arrivée des pages 320 ms.
- Android : mêmes neutres et formes (`Theme.kt`), boutons en pilule (`Boutons.kt`), cartes et rubriques à filet, barre d'onglets à indicateur orange doux, bouton + coins 16, bannière du bureau vivante ; captures `CapturesBureauTest` (accueil, opérations, demandes, cotisations, membres, paramètres) comparées au site.
- Contrôles : 33 tests d'audit + VersionTest ; a7 312 vues 0 problème ; a5 144 vues sans erreur ; fluidité des rubriques 60 images/s ; scénarios membres, comptes, export : sans erreur ni débordement.

## 0.43.0 — Fluidité, fond blanc neutre, bannière photo (21 octobre 2026)

- Mesure image par image de l'ouverture d'une rubrique (`fluidite.mjs`) : avant, la hauteur sautait d'un coup (fermeture retardée de 280 ms puis fermeture sèche) ; après, courbe continue 215 → 153 → 108 → 88 → 79 → 74 px, aucune image au-delà de 21 ms (60 images/s), dans les deux sens.
- Fond de page blanc neutre (plus de teinte orangée), cartes blanches cernées d'un filet gris doux ; ombre de bannière neutre.
- Bannière avec photo plus haute et cadrée sur les visages, voile seulement en haut et en bas ; sans photo, plus de grand vide.
- Android : `glisse()` 280 ms pour ouvrir, fermer et tourner le chevron ; arrivées et effets de nouveauté joués une fois (`DejaJoue`), plus de relance au défilement ; captures avec et sans photo.
- Contrôles : 33 tests d'audit + VersionTest ; a7 312 vues 0 problème ; a5 144 vues ; `membre-lien.mjs` ; captures Android comparées au site.

## 0.42.0 — Page des membres en Material 3 Expressive (21 octobre 2026)

- Constat : les teintes des rubriques (`--g-fort`, `--g-clair`) n'étaient définies nulle part (toutes les rubriques retombaient sur la même couleur), étiquettes empilées à droite, rien ne signalait un nouveau communiqué.
- Site (`.vue-membre`) et Android (`MembreM3.kt`) : palette et formes M3 Expressive, rubriques teintées avec pastille d'icône et badge des nouveautés, tuiles segmentées, étiquettes sur une ligne, bannière vivante sans photo, annonces en verre clair avec drapeau « Important », arrivée en cascade, galet de date qui éclot, reflet et liseré sur un non-lu ; tout respecte « réduire les animations » ; animations d'entrée passées de `both` à `backwards` (règle 10).
- Contrôles : 33 tests d'audit + VersionTest ; captures Android Robolectric/Roborazzi (téléphone, tablette) comparées au site ; `membre-lien.mjs` (page du lien, badge retiré à la lecture, sondage « Répondu », aucune animation avec réduction) ; a7 312 vues 0 problème ; a5 144 vues sans erreur.

## 0.41.0 — Comptes existants reliés, anniversaires sans profession (21 octobre 2026)

- Constat en production (journaux) : création refusée pour une adresse déjà inscrite (« email_exists ») — compte ouvert seul le 7 octobre, sans profil ; le message renvoyait vers un rattachement impossible. Correction : `creer-comptes` relie ce compte à la fiche (fonction choisie, mot de passe gardé, adresse confirmée, audit `RELIER_COMPTE`) ; refus seulement si l'adresse appartient au compte d'un autre membre. En-têtes CORS demandés acceptés. Un compte créé normalement le même jour l'a bien été (profil Bureau, mot de passe provisoire).
- Anniversaires du mois : la profession n'est plus transmise par la base ni affichée (site, Android, démos).
- Contrôles : 33 tests d'audit + VersionTest ; `comptes3.mjs` (compte existant relié, adresse d'un autre membre refusée, anniversaires sans profession) ; `comptes.mjs`, `comptes2.mjs` ; a7 312 vues 0 problème ; a5 144 vues sans erreur.

## 0.40.0 — Comptes créés directement par l'administrateur (21 octobre 2026)

- Base : `profiles.mdp_provisoire`, `mot_de_passe_definitif()` (testés sur PostgreSQL local, appliqués en production) ; fonction Edge `creer-comptes` (droit `administrer` vérifié avec le jeton de l'appelant, création sans e-mail, profil relié à la fiche, journal d'audit `CREER_COMPTE`) ; appel sans droit refusé (403) vérifié en production.
- Site et Android : « Créer des comptes » (Comptes et accès) et « Créer le compte tout de suite » (Membres › Fonction) ; résultat avec message par personne ; écran de choix du mot de passe à la première connexion.
- Contrôles : 33 tests d'audit (dont `comptesDirectsCommeLeSite`) + VersionTest ; scénarios `comptes.mjs` (375 px et ordinateur) et `comptes2.mjs` (connexion provisoire, même mot de passe refusé, reconnexion) sans erreur ; a7 312 vues 0 problème ; a5 144 vues sans erreur.

## 0.39.0 — Colonnes d'export au choix (18 octobre 2026)

- Membres › Exporter : puces de colonnes (Nationalité, Statut, Profession… ; Accord anniversaire et Actif en option), Tout / Par défaut, choix gardé sur l'appareil ; même liste `COLONNES_EXPORT_MEMBRES` des deux côtés.
- PDF à l'italienne au-delà de 6 colonnes, mots non coupés ; Excel avec les en-têtes du modèle d'import (réimportable).
- Contrôles : Android compilé, 32 tests d'audit (dont `colonnesExportCommeLeSite`) + VersionTest ; mêmes largeurs vérifiées sur le site (node) ; scénario `colonnes.mjs` au téléphone 320 px et à l'ordinateur sans erreur ni débordement ; a7 312 vues 0 problème ; a5 144 vues sans erreur.

## 0.38.0 — Exports selon la vue, en-têtes unifiés (18 octobre 2026)

- Membres : « Exporter » dans l'en-tête ; PDF et Excel suivent filtres, regroupement et tri (titre du document qui rappelle la vue, sections « valeur — N · part % », colonne « Groupe (…) » dans le CSV) ; Répartition exportable. Mêmes calculs `sectionsMembres`, `libelleVue`, `csvMembres`, `documentMembres` (test `exportSelonLaVue`).
- Colonnes de largeur fixe dans les documents en sections (`largeurs`), colonne de regroupement retirée.
- En-têtes : titre + actions sur une ligne, résumé dessous (Membres, Demandes, Cotisations, Rapprochement, Rapports) ; ajout par le + rond (Tiers, Communiqués, Nouvelle collecte).
- Contrôles : Android compilé, 31 tests d'audit + VersionTest ; balayages a7 (312 vues, 0 problème) et a5 (144 vues, aucune erreur) ; scénario `export-membres.mjs` au téléphone et à l'ordinateur sans erreur JavaScript.

## 0.37.0 — Membres : liste sobre, filtres, regroupement, répartition ; composant pièce jointe (18 octobre 2026)

| Vérification | Résultat |
|---|---|
| `a7.mjs` avec le nouveau contrôle « texte hors de sa carte » : ancienne version / nouvelle | ❌ signalé sur toutes les tailles d'iPhone / ✅ 312 vues, 0 problème |
| `membres2.mjs` (Pixel 7, iPhone SE, ordinateur) : liste, fiche, filtres, regroupement par nationalité, pastilles, répartition | ✅ largeur de page = écran, 0 erreur JS |
| `scanner.mjs`, `import2.mjs`, `rappel.mjs`, `rappel-groupe.mjs`, `a5.mjs` | ✅ |
| Android : compilation, 31 tests dont `groupesEtRepartitionCommeLeSite` | ✅ ; rendu à vérifier sur téléphone |

## 0.45.0 — Planning sobre et aération

| Contrôle | Résultat |
|---|---|
| Site, Pixel 7 et ordinateur : Mois, Semaine, À venir, détail du jour, mois suivant | ✅ 0 erreur JS |
| `a7.mjs` (6 tailles d'iPhone/iPad, 4 rôles, toutes les pages) | ✅ 312 vues, 0 problème |
| `a5.mjs` | ✅ 144 vues, aucun débordement |
| Android : compilation, tests unitaires, capture Roborazzi `bureau-planning` | ✅ rendu identique au site |


## 0.46.0 — Plusieurs montants (cotisations et participations), suppression d'un compte d'accès, nom JTP

| Contrôle | Résultat |
|---|---|
| Migration testée sur PostgreSQL local (barème, `appliquer_tarif` depuis une date, réapplication d'un montant modifié, refus de supprimer un montant attribué, collecte avec montants par personne, corbeille aller-retour d'une collecte et d'un membre avec leurs montants) | ✅ puis appliquée en production |
| Fonction `supprimer-compte` déployée (refus : soi-même, dernier administrateur, compte qui a servi) | ✅ |
| Site en démonstration (`montants.mjs`) : barème, montant d'un membre depuis juin, détail et formulaire de collecte, suppression d'un compte puis « Annuler » | ✅ 0 erreur JS |
| `a7.mjs` (6 tailles, 4 rôles) / `a5.mjs` | ✅ 312 vues, 0 problème / 144 vues, aucun débordement |
| Android : compilation, 34 tests dont `montantsSelonLaPersonne`, capture `bureau-cotisations` | ✅ |
| À faire par le trésorier | `supabase/A-EXECUTER-montants-2026-10-22.sql` (clés de `collecte_montants`) |
