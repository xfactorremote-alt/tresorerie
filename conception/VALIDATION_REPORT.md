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
