# Règles du projet Trésorerie

## Un seul produit, deux interfaces

Le site (`docs/`) et l'application Android (`android/`) sont **le même produit**. Toute modification de fonction, d'écran, de texte, de règle ou de conception faite sur l'un est faite **dans le même travail** sur l'autre, sans attendre qu'on le demande. Une seule exception : ce qui n'a de sens que sur un support (impression du navigateur, partage Android).

Avant de dire qu'un travail est fini :
1. Relire la liste de contrôle ci-dessous pour chaque écran touché.
2. Compiler Android (`./gradlew :composeApp:compileDebugKotlinAndroid`) et lancer les tests (`:composeApp:testDebugUnitTest`).
3. Tester le site en mode démonstration (Playwright), sans erreur JavaScript.
4. Mettre à jour la démo des deux côtés (`docs/mock.js` et `Demo.kt`) avec les mêmes règles que la base.
5. Toute règle de gestion vit d'abord dans la base (`supabase/`), puis à l'identique dans `mock.js` et `Demo.kt`.
6. Un calcul affiché porte le même nom des deux côtés (`calculBudget`, `serieMensuelle`, `EtatsDemandes`…) et le document PDF/Excel Android reprend les mêmes colonnes que le site.
7. Les catégories internes (virements) ne sont jamais proposées à la saisie ni au budget, y compris après l'ajout d'une catégorie.

## Liste de contrôle de parité (écran par écran)

| Écran | À retrouver à l'identique sur le site et sur Android |
|---|---|
| Accueil | Bannière (photo, logo, nom, roue dentée), soldes, comptes ; rubriques Bien démarrer, Ma situation, À traiter, Chiffres, Évolution, Répartition, Anniversaires, Dernières opérations |
| Opérations | Recherche + bouton Filtres (pastilles retirables), totaux, Exporter, détail avec historique, contre-passation avec motif, virement interne |
| Demandes | Filtres d'état (dont À régulariser), justification, date, devis, signature, régularisation, voir l'opération |
| Cotisations | Onglets Cotisations / Participations, grille des mois, encaisser, relancer, exporter |
| Budget | Trois chiffres (ressources, emplois, excédent/déficit prévus), saisie du prévu dans la ligne, « Reprendre le réalisé N-1 », alerte de déficit, ressources et emplois (prévu / réalisé / avancement / N-1, Dépassé, Non prévu), cartes d'activité (Prévoir une ligne, Retirer), consultation pour le bureau, exporter PDF et Excel identiques |
| Planning | Onglets Calendrier / À venir ; Mois / Semaine ; détail du jour ; logo en filigrane |
| Matériel | Inventaire, fiche, confier / récupérer / vérifier / sortir, export |
| Tiers, Membres | Recherche, fiches, fonction et accès, liens personnels, import, export |
| Rapprochement | Relevé obligatoire, pointage, écart, historique, consultation pour le bureau |
| Rapports | Un seul formulaire : document, période, format |
| Paramètres | Onglets Mon compte, Association, Montants et comptes, Rôles et droits, Accès ; roue dentée seulement (pas dans « Plus ») |

## Base de données

- Toute migration de production : comparer d'abord l'empreinte de la fonction en production à celle du dépôt, tester sur PostgreSQL local, puis appliquer ; recopier dans `supabase/schema.sql` et dans un fichier daté `supabase/correctifs-*.sql`.
- Jamais de suppression d'écriture : contre-passation avec motif.
