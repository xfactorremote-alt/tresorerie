# Audit de parité : Cotisations (Cotisations, Participations, collectes) et Tiers

Référence : `pageCotisations`, `ongletCotisations`, `ficheCotisation`, `ongletParticipations`, `detailCollecte`, `feuilleCollecte`, `pageTiers`, `ficheTiers`, `feuilleTiers` (docs/app.js).
Android : `EcranCotisations`, `OngletCotisations`, `FicheCotisation`, `OngletParticipations`, `DetailCollecte`, `FormulaireCollecte`, `EcranTiers` (Ecrans4.kt).
Captures du site (téléphone et ordinateur) : `scratchpad/audit/cotisations/*.png` (avant) et `cotisations/apres/` (après la retouche du site). Aucune erreur JavaScript.

## Différences corrigées

### Onglet Cotisations
1. Bandeau « membres sans cotisation » : le texte devient « N membre(s) actif(s) sans cotisation en AAAA » (Android disait « N membres sans cotisation en AAAA »), le bouton « Générer les cotisations AAAA » (Android : « Générer »), et le message « N période(s) créée(s) » avec l'accord (Android disait toujours « périodes créées »). Coins de 8 dp au lieu de 20.
2. Liste : **une seule carte** qui contient la légende puis les lignes séparées par un filet, comme la `section.carte` du site. Android avait une carte par membre et la légende en dehors.
3. Ligne d'un membre : avatar, nom, grille ; sur téléphone, en dessous, la puce Retard/À jour, la puce « Avance X » puis Encaisser et Relancer ; sur grand écran (≥ 840 dp), puces et boutons à droite et grille des 12 mois **sur une ligne** (largeur max 420 dp), comme le site. Avant : puce sur la ligne du nom et grille toujours sur deux lignes.
4. « Avance » : puce neutre « Avance X » comme sur le site (Android affichait un texte « Avance : X »).
5. Bouton Encaisser de la ligne : bouton entouré (le `btn-tonal` du site est une surface claire bordée), Android avait un bouton tonal plein.
6. Ouverture de la fiche : par le nom (lien du site). Avant, toute la carte (avec ses boutons) ouvrait la fiche.
7. État vide : « Aucune cotisation » quand le filtre ne montre personne, « Aucune cotisation pour AAAA » quand l'année est vide. Android n'affichait rien dans le premier cas.
8. Légende : petits carrés de 14 dp et libellés, retour à la ligne au besoin (`.legende`). Android dessinait une mini-grille de 36 dp dans une ligne qui défile.
9. Indicateurs : Encaissé et En retard sont calculés sur toute la synthèse de l'année (`synth`), comme le site.
10. Message de relance : « votre cotisation à <association> présente un retard… » sans « à » quand l'association n'a pas de nom (même règle que le site).
11. Export : fenêtre « Cotisations AAAA » avec deux choix, **PDF** (« Mise en page pour imprimer, archiver ou transmettre ») et **Excel** (« Tableau modifiable (CSV) »), comme `choisirFormat`. Le CSV reprend maintenant **les colonnes du site** : Prénom, Nom, une colonne par période avec son état (Réglé, Partiel, Impayé, À venir, Dispensé), puis Dû, Réglé, Exigible, Retard, Réglé jusqu’à. Avant : export générique sans les périodes et dans un autre ordre.
12. Le choix de l'onglet (Cotisations ou Participations) et du filtre est gardé pendant la session (`PreferencesCotisations`, l'équivalent de `S.ongletCotis` et `S.filtreCotis`).

### Fiche cotisation d'un membre
13. La puce Retard/À jour passe **sous** l'en-tête (avatar et nom), comme sur le site.
14. Bouton « Montant dû » sur chaque période (gestionnaire). Avant, il fallait toucher la ligne, et rien ne l'indiquait.
15. « Aucune période » quand l'année est vide (absent sur Android).
16. Intertitres (année, Versements, Participations) en titre sombre comme les `h3` du site (Android : petits titres gris). Filet entre les périodes.
17. Encaisser : le montant proposé est le retard, **sinon le montant d'une période** (Android ne proposait rien s'il n'y avait pas de retard).
18. Boutons Encaisser (entouré) et **Fermer** (principal), comme le site. Fermer manquait.

### Participations (collectes)
19. Carte d'une collecte : le montant reçu en grand (24 sp) et « sur X » en texte normal (Android mettait tout en gras), barre de 4 dp aux bouts arrondis (Android : 8 dp). Le « visé » suit la même règle : objectif, sinon montant par personne × nombre de concernés, et seulement s'il est supérieur à 0.
20. **Deux colonnes** sur grand écran (`grille-2`).
21. Liste vide : carte « Aucune collecte » (Android : simple texte).
22. Détail : l'ordre des boutons de chaque membre est maintenant celui du site (Encaisser puis Relancer). Ils passent sous la ligne, alignés sur le nom (`actions-ligne`), au lieu d'être serrés à droite de la puce.
23. Détail : l'indicateur « Reste attendu / Objectif » s'affiche en couleur de texte normale (Android : orange). Filets entre les lignes. Intertitre « Autres contributions » sombre.
24. Détail : boutons dans l'ordre du site (Exporter, Modifier, Clôturer/Rouvrir, Autre encaissement entouré au lieu de plein, Supprimer, **Fermer**). Fermer manquait. Après Clôturer ou Rouvrir, la feuille se ferme et la liste se recharge, comme sur le site.
25. Export CSV de la collecte : nom de fichier `participations-<nom-avec-tirets>.csv` et fabrication par `csv()` (guillemets si besoin), mêmes colonnes : Prénom, Nom, Donné, Attendu, Statut.
26. **Suppression d'une collecte** (« ça ne s'en va pas ») : la corbeille était appelée depuis la feuille du détail, que l'on fermait aussitôt. La portée qui affichait le message et son « Annuler » disparaissait avec la feuille, et la restauration ne pouvait plus se faire. La suppression est maintenant faite par l'onglet Participations (nouveau paramètre facultatif `onSupprimer` de `DetailCollecte`). La liste se recharge après la suppression **et** après « Annuler ».
27. Formulaire de collecte : « Nom * » avec l'exemple « Participation à la sortie », libellés « Montant par personne (€) » et « Objectif total (€) ». En mode « Choisir » sans aucun membre coché, le bouton n'est plus grisé : il affiche le message du site, « Cochez au moins un membre, ou choisissez « Tous les membres » ».

### Tiers
28. La liste est dans **une seule carte** avec des filets (Android : lignes posées sur le fond).
29. Initiales d'un tiers : ses deux premières lettres (« M. », « TH », « BO »), comme le site. Android prenait la première lettre de chaque mot (« ME » pour « M. et Mme Lefèvre »).
30. État vide « Aucun tiers » (absent sur Android).
31. Année et recherche sur une même ligne, sous les puces Tous / Membres / Autres tiers. Les filtres sont gardés pendant la session (`PreferencesTiers`, l'équivalent de `S.filtreTiers`).
32. Fiche : avatar du membre dans l'en-tête ; « Ce qu’il a donné » montre les montants sans le signe « + » (comme le site) ; filets entre les opérations ; intertitres sombres.
33. Fiche : Exporter en bouton entouré (le site utilise `btn-bleu`, une surface claire ; Android avait un bouton bleu plein) et bouton **Fermer** ajouté. Ordre : Exporter, Modifier, Supprimer, Cotisation, Fermer.
34. Formulaire du tiers : « Nom * » obligatoire, comme sur le site.
35. Suppression d'un tiers : déjà par la corbeille. Le rappel recharge la liste (`version++`) après la suppression et après « Annuler » : vérifié.

### Site (défaut réel, règle 11 de CLAUDE.md)
36. Participations vide : le site montrait deux fois « Nouvelle collecte » (le bouton du haut et un second dans la carte vide). Le second est retiré (`docs/app.js`, `ongletParticipations`). Playwright : aucune erreur JavaScript.

## Différences restantes (avec la raison)
- **Création par bouton flottant sur Android** (« + Nouvelle collecte », « + Nouveau tiers ») : le site met un bouton en haut. CLAUDE.md prévoit le + flottant pour les listes (Demandes, Membres) ; je l'ai gardé et c'est au site de s'aligner (voir plus bas).
- **Formes générales** : Android garde le thème Material Expressive (cartes à 28 dp, puces à 13 dp) au lieu des coins Fluent de 4 à 8 dp du site. Ces formes viennent des éléments communs (`CarteBlanche`, `Puce`, `Indicateur`, `Theme.kt`), hors de mon périmètre. Il faut les changer pour toute l'application d'un coup.
- **Saisie de la date limite** d'une collecte : Android utilise un champ texte JJ/MM/AAAA. Il n'existe pas de sélecteur de date commun dans l'application ; le site utilise le champ date du navigateur.
- **Montant dû** : le site modifie le montant dans la ligne même, Android ouvre une petite fenêtre (avec l'aide « 0 pour dispenser »). Le comportement et les messages sont identiques.
- **Formulaire du tiers** : il s'ouvre dans une fenêtre sur Android, dans une feuille sur le site. Les champs, leur ordre, les règles et les messages sont identiques.
- **Case « Dispensé »** : hachurée sur le site, grise unie sur Android.
- **Choix de l'année** (Cotisations) : le site propose les années autour de l'année *choisie* (la liste se décale à chaque choix), Android autour de l'année en cours. J'ai gardé la liste fixe d'Android, plus stable.
- **Détail d'une collecte ouvert depuis le Planning** (`DetailEvenement`, périmètre de l'agent Planning) : il n'utilise pas encore `onSupprimer`, donc « Annuler » après une suppression peut s'y perdre de la même manière. Il suffit d'y créer `rememberSuppression` au niveau de `DetailEvenement` et de le passer par `onSupprimer`.
- **CSV des cotisations dans Rapports** (`exportCsv(…, "cotisations")` dans Ecrans3.kt) : il garde l'ancien format, que je n'ai pas modifié parce qu'il est hors de mon périmètre. L'export de la page Cotisations, lui, est identique au site.

## Android meilleur que le site (à reporter)
- **Bouton + flottant** « Nouvelle collecte » et « Nouveau tiers » sur téléphone, comme Demandes et Membres (CLAUDE.md, liste de contrôle).
- **Fenêtre « Montant dû »** avec l'aide « 0 pour dispenser » : plus clair que le champ dans la ligne.
- **Liste des années fixe** (année suivante, en cours et deux précédentes) au lieu d'une liste qui se décale.
- **Mémoire de l'onglet et des filtres** déjà présente sur le site (`S.*`) : rien à reporter.

## Fichiers modifiés
- `android/composeApp/src/commonMain/kotlin/org/jpgrenoble/tresorerie/Ecrans4.kt` : seulement `GrillePeriodes` (nouveau paramètre facultatif `uneLigne`), `Legende()`, le bloc Cotisations/Participations (avec `PreferencesCotisations`, `ChoixFormat`, `CarteCollecte`, `TitreFiche`, `DetailCollecte` avec le paramètre facultatif `onSupprimer`, `FormulaireCollecte`) et le bloc `EcranTiers` (avec `PreferencesTiers`). Les signatures publiques sont inchangées ; il n'y a que des paramètres facultatifs en plus.
- `docs/app.js` : `ongletParticipations`, retrait du second bouton « Nouvelle collecte ».
- Démo : `Demo.kt` et `mock.js` sont déjà alignés pour ces écrans (mêmes tiers, collectes et règles de génération). Je n'y ai rien changé.
- Gradle n'a pas été lancé (consigne) : la compilation reste à faire par le coordinateur.
