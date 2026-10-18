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
