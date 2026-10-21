# Système de conception — Trésorerie JP Grenoble

Un seul produit, deux interfaces : le site (`docs/`, HTML + CSS) et l'application Android (`android/`, Compose Multiplatform).
**La référence est le site** (`docs/styles.css`, bloc `:root`). Android reprend chaque valeur sous le même nom français.
Fluent 2 (Microsoft) sert de **référence de conception** (formes, élévations, mouvements, tailles de commande) ; il n'existe pas de
bibliothèque Fluent 2 pour Compose : Android l'applique par ses propres jetons (`Theme.kt`) au-dessus de Material 3.

## 1. Jetons de couleur

| Rôle | Site (CSS) | Android (`Couleurs.`) | Valeur |
|---|---|---|---|
| Primaire (orange de l'association) | `--primaire` | `Orange` | #C23E10 |
| Primaire clair / trait | `--primaire-clair` / `--primaire-trait` | `OrangeClair` / `OrangeTrait` | #FFDBCF / #F5B39C |
| Jaune (attente, à faire) | `--jaune`, `--jaune-clair`, `--jaune-trait`, `--sur-jaune` | `Jaune`, `JauneClair`, `JauneTrait`, `SurJaune` | #F8B334 … |
| Bleu (information, recette) | `--bleu`, `--bleu-clair`, `--bleu-trait`, `--sur-bleu-clair` | `Bleu`, `BleuClair`, `BleuTrait`, `SurBleuClair` | #1B77B0 … |
| Erreur | `--erreur`, `--erreur-clair`, `--erreur-trait`, `--sur-erreur-clair` | `Erreur`, `ErreurClair`, `ErreurTrait`, `SurErreurClair` | #BA1A1A … |
| Réglé / à jour | (#2E8B4E en dur) | `Vert` | #2E8B4E |
| Fond de page | `--fond` | `Fond` | #F6F6F6 |
| Surfaces | `--surface`, `--surface-2`, `--surface-3`, `--survol`, `--volet` | `Color.White`, `Surface2`, `Surface3`, `Survol`, `Volet` | #FFF, #EFEDEC, #E7E3E1, #F3F1F0, #F0EEED |
| Texte | `--texte`, `--texte-2`, `--texte-3`, `--texte-desactive` | `Texte`, `TexteFort2`, `Texte2`, `TexteDesactive` | #1C1B1A, #4A4543, #5A5350, #A8A19E |
| Bords | `--bord`, `--bord-doux`, `--bord-fort` | `Bord`, `BordDoux`, `BordFort` | #D9D3D0, #E8E3E1, #5A5350 |

Règles :
- Contraste du texte ≥ 4,5:1 (couleurs du logo assombries pour cela). Contour d'un champ ≥ 3:1 : le site met un trait `--bord-fort` sous le champ, Android utilise `outline = BordFort`.
- **Jamais de couleur écrite en dur** quand un jeton existe (`Color(0xFF…)` sur Android, `#…` hors `:root` sur le site). Restent en dur, par exception documentée : les couleurs de rendez-vous (`couleurEvt`, calculées), les dégradés et voiles blancs de la bannière photo.
- La couleur n'est jamais le seul porteur d'information : chaque état a aussi un mot (« Réglé », « À valider », « Dépassé »).

## 2. Formes (Fluent 2)

| Jeton | Site | Android (`Formes.`) | Usage |
|---|---|---|---|
| Petit | `--r-petit` 2 px | `Petit` | cases à cocher, barres de rendez-vous |
| Moyen | `--r-moyen` 4 px | `Moyen` | **boutons**, champs, menus, encadrés d'information, puces de filtre |
| Grand | `--r-grand` 6 px | `Grand` | — |
| Très grand | `--r-xgrand` 8 px | `XGrand` | **cartes**, bannière, fenêtres, feuilles qui montent |
| Rond | `--r-rond` | `Rond` | puces d'état, pastilles, bouton + |

Android : `MaterialTheme.shapes` est réglé sur ces valeurs (extraSmall/small = 4, medium/large/extraLarge = 8) ;
les boutons Material 3 (pilules par défaut) sont rendus rectangulaires par `Boutons.kt` (voir § 4).

## 3. Typographie

| Rôle Fluent | Site | Android |
|---|---|---|
| Title 2 (titre de page) | `h1` 28/36 (24/32 au téléphone) | `headlineMedium` gras |
| Subtitle 1 (titre de fenêtre) | `h2` 20/28 (18/24) | `titleLarge` gras |
| Subtitle 2 (titre de carte) | `h3` 16/22 | 16 sp demi-gras |
| Body | 14/20 | 14 sp |
| Caption / muted | 13/18 et 12/16 | 13 sp et 12 sp, `Texte2` |
| Chiffres | `--police-chiffres` (Bahnschrift), `tabular-nums` | `FontWeight.ExtraBold` |

Les tailles littérales d'Android (331 occurrences) suivent déjà cette échelle (14, 13, 12 sp à 83 %) : pas de refonte mécanique, mais toute nouvelle taille doit appartenir à l'échelle.

## 4. Composants communs (contrats)

| Composant | Site | Android | Contrat |
|---|---|---|---|
| Bouton | `button`, `.btn-primaire`, `.btn-tonal`, `.btn-texte`, `.btn-danger` | `Button`, `FilledTonalButton`, `TextButton`, `OutlinedButton` (enveloppes de `Boutons.kt`) | Rectangulaire, coins 4, hauteur 32 px souris / 40 au doigt ; une seule action principale par zone ; la suppression en contour rouge. **Ne jamais importer `androidx.compose.material3.Button` explicitement** : l'enveloppe du paquet serait contournée. |
| Bouton + | `.fab` | `BoutonPlus(description) { }` | Rond 56, orange, « + » blanc, en bas à droite au téléphone ; bouton texte dans l'en-tête au-delà de 840 dp / 1000 px ; pas de second « Ajouter » dans une liste vide. |
| Carte | `.carte` | `CarteBlanche` | Surface blanche, coins 8, marge 16–18 ; une seule bannière forte par écran. |
| Puce d'état | `.puce`, `.puce-ok/-ko/-partiel/-neutre/-bleu` | `Puce(texte, fond, couleur, bord)` | Pilule, 12 px gras, couleur + mot. |
| Filtres | `.onglets-filtre` | pilules de filtre (Demandes, Membres…) | Une ligne défilante, compteur dans la pilule, choix gardé pendant la session. |
| Champ de date | `<input type="date">` | `ChampDate(valeur, onValeur, label, isError)` | Saisie JJ/MM/AAAA possible **et** calendrier ; valeur lue par `dateDepuisFr`. |
| Confirmation de suppression | `supprimerAvecConfirmation(table, id, libellé, après, note)` | `rememberSuppression(message, note) { après }` | Motif facultatif, corbeille, « Annuler » aussitôt ; tourne dans une portée d'application (`Annulation.portee`) pour survivre à la fermeture de la fiche. |
| Feuille | `ouvrirFeuille` / `fermerFeuille` | `ModalBottomSheet` | Monte du bas au téléphone, fenêtre centrée sur grand écran ; titre, contenu, actions en bas (principale à droite). |
| Pièce jointe | `.choix-piece` (le vrai `<input type=file>` posé, invisible, sur le premier bouton) | `ChoixPiece(fichier, message, onChoisir) { scanné }` | Titre du champ, puis « Choisir un fichier » et « Scanner » côte à côte (même forme, même largeur), puis le fichier retenu ; jamais le champ natif du navigateur ni un bouton d'un autre style dessous. |
| Filtres et regroupement | bouton `Filtres (n)` + `.pastilles` + feuille | `OutlinedButton` « Filtres (n) » + `InputChip` + `ModalBottomSheet` | Comme Opérations : les choix actifs en pastilles retirables, « Tout effacer » ; dans la feuille, contrôle segmenté pour un choix unique, puces de filtre avec leur nombre pour un choix multiple, bouton principal « Voir N … ». |
| Répartition | `.liste-repartition` + `.barre` | lignes + barre 4 dp | Libellé, barre (bleu), « effectif · part » à droite, puce jaune « Peu représenté » ; jamais de camembert sur téléphone. |
| En-tête de page | `.page-titre` (h1, boutons, `.muted` dessous) | `Titre(texte, resume) { actions }` | Titre et actions sur une ligne, résumé toujours dessous ; « Exporter » toujours dans l'en-tête et selon la vue affichée (filtres, regroupement, tri rappelés dans le titre du document) ; l'ajout par le + rond, jamais par un bouton d'en-tête au téléphone ; boutons d'en-tête 32 px de haut au moins. |
| Choix des colonnes à exporter | puces `.puce-filtre[data-col]` + « Tout » / « Par défaut » au-dessus des cartes PDF / Excel | `FilterChip` + `TextButton` dans `FeuilleExportMembres` | Le nom toujours ; le reste au choix de l'utilisateur, gardé sur l'appareil ; jamais de colonne retirée d'office. |
| Document en sections | `tableDocument(colonnes, lignes, totaux, largeurs)` | `table(…, largeurs)` | Une section par groupe « valeur — N · part % », colonnes de largeur fixe pour qu'elles s'alignent d'une section à l'autre, À l'italienne au-delà de 6 colonnes ; mots jamais coupés au milieu ; dates insécables. |
| Ligne de liste | `li.ligne-…` | `Row … clickable` dans une carte | Sobre : titre, une ligne d'infos, montant/état, chevron ; **jamais de boutons empilés sur la ligne** ; la fiche porte les actions. |

### Page des membres — Material 3 Expressive

| Élément | Site | Android | Contrat |
|---|---|---|---|
| Palette | `--m3-fond`, `--m3-primaire(-conteneur)`, `--m3-bleu/orange/vert/violet/rose(-conteneur)` | `Couleurs.M3*`, `Teintes` | Une teinte par rubrique : Communiqués bleu, Sondages orange, Photos vert, Liens violet, Au programme bleu, Cotisation vert (rouge si retard), Régler et Participations primaire, Anniversaires rose |
| Formes | `--m3-forme-s/m/l/xl` 12/16/24/28 | `Formes.M3S/M/L/XL` | Bannière 28, rubrique 24, galet de date 16, tuiles segmentées 24 aux extrémités et 12 aux jointures, pilules rondes |
| Rubrique | `.vue-membre details.rubrique` + `.rub-ic` + `.rub-badge` | `RubriqueMembre` | Pastille d'icône teintée (galet → rond à l'ouverture), titre 18 gras, badge « N nouveaux » / « N à répondre » de la couleur forte, chevron dans un rond teinté |
| Communiqué | `.liste-communiques li` | `TuileCommunique` | Tuile blanche, date en galet (rempli orange si Important), étiquettes sur une ligne sous le titre (Important rempli, • Nouveau, À répondre, Répondu, N photos), chevron ; non lu : liseré 2 px et reflet une fois |
| Mouvement | `--m3-ressort`, `m3-arrivee`, `m3-galet`, `m3-reflet`, `m3-point` | `ressort()`, `ArriveeM3`, `Animatable` | Courts (220–560 ms), en `backwards`, coupés par « réduire les animations » |

## 5. Modèles d'écran

1. **Liste + fiche** (Demandes, Membres, Matériel, Opérations, Tiers) : titre et résumé, recherche / filtres, une carte avec nombre et total, lignes sobres → fiche dans une feuille avec informations, historique, pièces, actions.
2. **Tableau de bord** (Accueil) : une bannière forte (photo, soldes), puis rubriques repliables en cartes ; deux ou trois colonnes sur grand écran.
3. **Calendrier** (Planning) : bandeau du mois, grille, « À venir » ; barres de couleur au téléphone, noms au-delà de 600 dp.
4. **Paramètres** : page ordinaire (même en-tête et navigation), menu sobre en cartes par groupe (icônes neutres, filet orange sur la section ouverte), « ‹ Paramètres » au téléphone, deux volets sur grand écran.
5. **États** : chargement sans écran blanc (on garde la page précédente), vide en carte avec icône et une phrase, erreur dans le message du bas avec le texte exact de la base.

## 6. Mouvement

Durées Fluent : 50 / 100 / 150 / 200 / 250 / 300 ms ; décélération `cubic-bezier(.1,.9,.2,1)`.
Entrée de page : 8 px + fondu (`entree`, remplissage `backwards`, **jamais `both`**). Changement de mois : glissement de 40 px.
« Réduire les animations » : le site coupe par `prefers-reduced-motion` ; Android hérite de l'échelle d'animation du système (0 = coupé).

## 7. Mise en page adaptative

- Téléphone < 600 dp / 640 px : une colonne, barre d'onglets en bas (4 entrées + Plus), bouton + flottant.
- Tablette 600–1199 : site avec menu ☰ par-dessus ; Android barre d'onglets ; deux colonnes au-delà de 840 dp / 1000 px.
- Ordinateur ≥ 1200 px : volet de navigation toujours déplié.
- Zones tactiles ≥ 24 px (WCAG 2.5.8) même quand le repère visible est plus petit (points de la bannière, barres du calendrier : on touche la case du jour).
