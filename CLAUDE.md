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
8. Toute nouvelle liste ou fiche qui peut être saisie par erreur a un bouton « Supprimer » passant par la corbeille (`supprimer()` / `restaurer()` de la base, `Demo.supprimer`, `mock.js`), avec « Annuler » aussitôt ; jamais de suppression directe.
9. Toute nouveauté qui concerne quelqu'un apparaît dans ses pastilles (`mes_nouveautes()`), des deux côtés.
10. Chaque écran est pensé pour le téléphone ET le grand écran (deux colonnes ou deux volets au-delà de 840 dp / 1000 px), avec des animations courtes qui respectent « réduire les animations ».

## Liste de contrôle de parité (écran par écran)

| Écran | À retrouver à l'identique sur le site et sur Android |
|---|---|
| Connexion | Logo et nom ; onglets Se connecter / Première connexion ; œil sur le mot de passe ; Rester connecté ; Mot de passe oublié ; enregistrement du mot de passe (navigateur, remplissage automatique Android) |
| Arrivée | Administrateur : assistant de configuration (association, coordonnées, exercice, comptes, cotisation, récapitulatif) tant que `organisation.configuree` est faux ; tout le monde : fiche de membre demandée tant qu'elle n'est pas remplie (Plus tard) |
| Nouveautés | Pastilles sur les onglets (Opérations, Demandes, Cotisations, Planning, Membres, Plus), cloche avec la liste, marquées vues à l'ouverture de l'onglet ; alerte du navigateur / notification du téléphone |
| Accueil (adhérent compris) | Bannière (photo, logo, nom, cloche, roue dentée), soldes (le solde « monte »), comptes ; rubriques Bien démarrer, Ma situation, À traiter, Chiffres, Évolution, Répartition, Anniversaires, Dernières opérations. Membre : prochain rendez-vous en grand, tuiles Ma cotisation / Participations / Anniversaires, Comment régler, Ensuite au planning ; deux colonnes sur grand écran |
| Opérations | Recherche + bouton Filtres (pastilles retirables), totaux, Exporter, détail avec historique, contre-passation avec motif, virement interne |
| Demandes | Filtres d'état (dont À régulariser), justification, date, devis, signature, régularisation, voir l'opération ; activité sur la carte ; date de paiement modifiable ; annulation confirmée (Garder / Annuler la demande) ; motif du refus seulement si refusée |
| Cotisations | Onglets Cotisations / Participations, grille des mois, encaisser, relancer, exporter ; situation (Réglé, Partiel, À régler, Donné, Libre) toujours visible ; CSV avec Statut |
| Budget | Trois chiffres (ressources, emplois, excédent/déficit prévus), saisie du prévu dans la ligne, « Reprendre le réalisé N-1 », alerte de déficit, ressources et emplois (prévu / réalisé / avancement / N-1, Dépassé, Non prévu), cartes d'activité (Prévoir une ligne, Retirer), consultation pour le bureau, exporter PDF et Excel identiques |
| Planning | Bandeau du mois coloré avec flèches rondes ; une couleur par rendez-vous (`couleurEvt`, même calcul) ; week-ends teintés ; aujourd'hui entouré ; glisser pour changer de mois ; onglets Calendrier / À venir ; Mois / Semaine (noms des rendez-vous dans les cases, +N, prénoms des anniversaires) ; détail du jour (« Budget suivi ») ; détail d'un rendez-vous (Voir le budget, Voir les participations, Modifier) ; formulaire « Nouveau rendez-vous » avec la case « Suivre le budget » ; logo en filigrane |
| Matériel | Inventaire, fiche, confier / récupérer / vérifier / sortir, export |
| Tiers, Membres | Recherche, fiches (Exporter, Modifier, Cotisation), fonction et accès, liens personnels, import, export |
| Rapprochement | Début et Fin modifiables, relevé obligatoire (nom d'origine conservé), pointage (« période précédente »), Relevé / Pointé / Écart, historique (Voir, Terminé le ou En cours), consultation pour le bureau |
| Rapports | Un seul formulaire : document, période, format |
| Paramètres | Menu groupé : Mon compte (Profil et fiche, Mot de passe et session, Notifications), Association (Identité et coordonnées, Logo et bannière, Exercices), Finances (Comptes, Catégories, Cotisations et dépenses), Accès (Personnes, Rôles), Données (Corbeille, Sauvegarde, Assistant) ; deux volets sur grand écran ; roue dentée seulement (pas dans « Plus ») |
| Exercices | Créer, modifier, clôturer (points de contrôle), rouvrir, supprimer ; exercice clôturé = opérations verrouillées ; proposés dans Rapports |
| Corbeille | Supprimer (motif, Annuler aussitôt) sur opération, membre, tiers, rendez-vous, collecte, matériel, ligne de budget, demande, compte, catégorie, exercice ; restaurer depuis Paramètres > Corbeille |

## Base de données

- Toute migration de production : comparer d'abord l'empreinte de la fonction en production à celle du dépôt, tester sur PostgreSQL local, puis appliquer ; recopier dans `supabase/schema.sql` et dans un fichier daté `supabase/correctifs-*.sql`.
- Suppression réversible seulement : la corbeille garde une copie complète (ligne, pièces, liens) et `restaurer()` remet tout en place. Une opération rapprochée, corrigée, payant une demande ou dans un exercice clôturé ne se supprime pas : contre-passation avec motif.
- Le connecteur Supabase demande une confirmation pour tout texte contenant `drop` ou `delete` : écrire les migrations sans `drop … if exists` quand l'objet est neuf (`create or replace trigger`), et faire exécuter par le trésorier dans l'éditeur SQL ce qui contient `delete` (fichier `supabase/A-EXECUTER-*.sql`). Ne jamais contourner cette confirmation.
