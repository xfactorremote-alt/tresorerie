# Audit de parité — Planning (site `pageActivites` / Android `EcranPlanning`)

Captures du site (démo, trésorier, téléphone Pixel 7 et ordinateur 1280×900) : `audit/planning/*.png`. Aucune erreur JavaScript.
Site non modifié. Android : `Ecrans4.kt` (partie Planning seulement, de « Planning : mois, semaine, agenda » à `Dialogue`, plus `FlecheMois` en fin de fichier et quatre imports) et `Ecrans3.kt` (`FormulaireActivite` : libellés seulement).

## Différences corrigées

### Structure et grand écran
- Cases du mois sur téléphone (< 600 dp) : le site montre des **barres de couleur** (6 dp, couleur d'accent du rendez-vous, coins 3) et une **barre jaune** de 4 dp pour les anniversaires, avec des cases de 58 dp. Android montrait du texte 9 sp dans des cases de 88 dp. C'est la principale raison du « ce n'est pas la même chose ». Android suit maintenant le site : barres sur téléphone, et au-delà de 600 dp le nom du rendez-vous (11 sp, heure à 80 % d'opacité, filet de 3 dp) avec les prénoms des anniversaires (11 sp), dans des cases de 88 dp.
- Le résumé « N rendez-vous ce mois · N anniversaires » est maintenant **dans la carte** du calendrier (comme `.cal-resume`). Il était au-dessus.
- « Nouveau rendez-vous » : à partir de 640 dp, c'est un bouton dans l'en-tête de page, sans bouton + (comme `.page-titre .fab`). Sur téléphone, c'est le bouton +.
- Mois / Semaine : sur téléphone, le groupe prend toute la largeur et « Aujourd’hui » passe dessous, à droite. Sur grand écran, ils sont sur une seule ligne, groupe à gauche et « Aujourd’hui » à droite (comme le site).
- Glisser pour changer : le geste marche aussi en **Semaine** (le site l'avait, pas Android). Le seuil passe à 60 dp (le site utilise 60 px ; Android avait 120 px).
- Filigrane : largeur 55 % (340 dp au plus) et opacité 0,08 ; sur téléphone, 70 % et 0,07 (Android avait 60 % partout).

### Composants (jetons Fluent du site : coins 4/8, traits, couleurs)
- Onglets Calendrier / À venir : ils étaient en `TabRow` Material pleine largeur. Ce sont maintenant des onglets soulignés alignés à gauche (souligné orange de 3 dp, texte 600 si choisi, `#4A4543` sinon) avec un filet sous la rangée.
- Mois / Semaine : c'était un `SegmentedButton` Material. C'est maintenant le groupe du site : fond `#EFEDEC`, bordure `#E8E3E1`, coins 4, bouton choisi blanc avec ombre et petit trait orange 16×3.
- « Aujourd’hui » : bouton blanc à bordure `#D9D3D0`, coins 4, comme `btn-tonal btn-petit` (c'était un OutlinedButton Material).
- Bandeau du mois : coins 8 (au lieu de 20), ombre, **deux cercles clairs décoratifs** (ils manquaient), padding 14/12, titre 600 de 20 sp sur téléphone et 24 sp ailleurs (il était en gras 22 sp), flèches de 40 dp sur téléphone et 44 dp ailleurs, fond des flèches à 14 %.
- Grille du mois : carte blanche, coins 8, bordure `#E8E3E1`, padding 4/8. En-têtes de jours 10/12 sp en 600, week-end en orange. Écart de 4 dp. Cases à coins 4 (au lieu de 12). Le fond suit le site : aujourd'hui `#FFF4EF` avec bordure orange de 2 dp, jour occupé blanc avec bordure, week-end `#FFF7F3`, autres jours `#FAFAF9`. Les jours d'anniversaire ont un **dégradé jaune en bas** (il manquait). Hors du mois, le **contenu** passe à 45 % et la case garde son fond (avant : toute la case à 50 % et chiffres gris).
- Numéro du jour : 24 dp, 600. Aujourd'hui : rond orange avec un **halo `#FFDBCF` qui respire** (pulsation du site).
- Les rendez-vous dans une case ouvrent directement leur détail (`.evt` du site). Un appui ailleurs sur la case ouvre le détail du jour.
- Légende : dégradé de quatre couleurs de 24×4, anniversaire 12×4, rond de 12 dp pour aujourd'hui, texte `#5A5350`.
- Semaine et À venir : vraie liste avec **filet entre les lignes** (padding 10). Date de 48×48 à coins 4 (au lieu de 14), chiffre 18 sp en 600, jour 11 sp. Le fond week-end `#FFEDE5` ne s'applique plus qu'en Semaine (sur le site, À venir ne teinte pas les week-ends).
- Carte de rendez-vous (`.agenda-evt`) : coins 4 (au lieu de 16), filet de 4, padding 6/12, nom 14 sp en 600, ligne d'heure 12 sp à 85 %. **« Toute la journée »** remplace « Journée » (même texte que le site). La puce de participation est **sous** l'heure (elle était à droite). Le repère « Bureau » est une puce bordée avec l'**icône œil barré** (comme `puce-bureau`).
- À venir : chaque mois a son **filet de couleur 10×24** devant le titre (`.carte-mois`). Le titre est en 600 de 18/20 sp. Les rendez-vous sont triés par date puis par heure (comme le RPC). La carte vide est centrée.
- Puces (« Budget suivi », « Participation … », « Bureau ») : arrondies, bordées, 12 sp en 600, comme `.puce`. Le texte « Anniversaire de … » est sur fond jaune clair à coins 4, en 13 sp.

### Détail du jour (`feuilleJour`)
- Le titre n'a plus de majuscule (« mercredi 14 octobre », comme le site). Il est en 600 de 20 sp.
- Les rendez-vous sont en **liste sobre** (nom en 600, « 18 h 30 · Église » en 12 sp grisé, filet, puce « Budget suivi » à droite). C'étaient des cartes colorées.
- « Ajouter un rendez-vous » est un bouton bordé (btn-tonal) et « Fermer » le bouton principal ; tous les deux à coins 4.

### Détail d'un rendez-vous (`detailEvenement`)
- Lignes Date / Heure / Lieu / Visibilité au format `.ligne-detail` : intitulé à gauche, **valeur à droite**, filet dessous.
- Visibilité : interrupteur puis libellé en gras, à droite.
- La puce « Budget suivi » s'affiche pour **toute** activité suivie, comme sur le site (Android la réservait aux personnes qui ont accès aux finances). « Voir le budget » reste réservé à ces personnes.
- La participation est dans une barre d'information du site (fond `#EAF4FB`, filet `#9CCBEA`, icône i, montant reçu ou demandé **en gras**). C'était un bloc bleu à coins 16.
- « Modifier » est un bouton bordé et « Fermer » le bouton principal, à coins 4.

### Suppression et « Annuler » (demande du propriétaire)
- **Défaut corrigé** : la suppression d'un rendez-vous était gérée *dans* la feuille du détail. Après la suppression, la feuille se fermait, ce qui annulait le message « Supprimé, placé dans la corbeille ». « Annuler » ne pouvait donc pas restaurer et la liste n'était pas rechargée après une restauration. La suppression est maintenant gérée par l'écran Planning (nouveau paramètre facultatif `onSupprimer` de `DetailEvenement`). La feuille se ferme, la confirmation s'ouvre, puis après suppression `version++` recharge le planning et le rendez-vous disparaît tout de suite. « Annuler » restaure et recharge aussi.

### Transitions
- Changement de mois ou de semaine : le nouveau glisse de 40 dp du côté où l'on va, en partant d'une opacité de 0,3, en 300 ms (`glisse-gauche/droite`). Avant, il glissait d'un tiers de la largeur. « Aujourd’hui » et Mois / Semaine font un simple fondu, sans glissement (comme le site).
- Les cases entrent en fondu, avec une échelle de 0,96 à 1, en cascade de 6 ms (`case-entree`), **une seule fois par mois affiché**. Elles ne bougent pas à une mise à jour en temps réel (règle 13).
- Le titre du bandeau entre en montant de 8 dp avec un fondu de 250 ms (`entree`). Avant : glissement d'une demi-hauteur.
- Toutes ces animations passent par Compose et s'arrêtent quand le téléphone réduit les animations.

### Préférences
- Calendrier / À venir et Mois / Semaine sont **gardés sur le téléphone** (`lirePreference` / `garderPreference`, mêmes clés que le site : `vuePlanning2`, `affichagePlanning`). Avant, ils étaient perdus à la fermeture de l'application.

### Formulaire « Nouveau rendez-vous » (`FormulaireActivite`, Ecrans3.kt)
- Libellés « Heure de début » et « Heure de fin » (au lieu de « Début » et « Fin »), et « Montant par personne (€) ».
- Les aides sont sous les cases, entre parenthèses et en gris : « (décochez seulement pour un rendez-vous réservé au bureau) » et « (ressources, emplois et résultat dans Budget ; la date peut rester à fixer) ».

### Démo
- `Demo.planning`, `Demo.anniversaires` et les rendez-vous p1 à p8 correspondent déjà à `mock.js`. Rien à changer.

## Différences restantes (raison)
- **Bouton + :** sur Android il est jaune et grand (`LargeFloatingActionButton`). Sur le site il est orange (couleur primaire), rond, de 56 px. Tous les écrans Android utilisent le même jaune ; c'est une décision globale (coordinateur), pas propre au Planning.
- **Formulaire :** Android l'affiche dans une fenêtre (`DialogueSimple`), le site dans une feuille. Les dates se tapent au clavier (JJ/MM/AAAA), sans sélecteur. Les erreurs de dates désactivent « Enregistrer » au lieu d'afficher « La date de fin doit suivre la date de début ». Aucun astérisque rouge sur Nom et Date. Ce formulaire est partagé et vit dans Ecrans3.kt ; je n'y ai changé que les libellés.
- **Feuilles :** le détail du jour et le détail d'un rendez-vous sont des `ModalBottomSheet` Material, avec des boutons texte Material (Voir les participations, Demander une participation, Supprimer). Le site utilise des feuilles Fluent. Le comportement est le même, seul l'habillage du système diffère.
- **Interrupteur de visibilité :** c'est le `Switch` Material, plus grand que celui du site (36×20).
- **Accueil membre :** `EcranAdherent` (Ecrans3.kt) appelle encore `DetailEvenement` sans `onSupprimer`. Si une personne du bureau supprime un rendez-vous depuis l'accueil, « Annuler » peut encore être perdu. Il suffit d'ajouter `onSupprimer` avec `rememberSuppression` dans `EcranAdherent`, mais ce fichier relève d'un autre périmètre.
- **Survol (ordinateur) :** la case qui se soulève et l'ombre au survol n'ont pas d'équivalent tactile. Android garde l'effet d'appui.
- **Liste vide d'À venir :** le site et Android affichent tous les deux « Ajouter un rendez-vous » alors que le + est déjà là, ce qui contredit la règle 11. Il faut le retirer **des deux côtés**.

## Android meilleur que le site (à reporter sur le site)
- **Temps réel :** les cases ne rejouent pas leur animation d'entrée quand les données changent. Sur le site, `rendre()` relance `case-entree` à chaque rafraîchissement, ce qui va à l'encontre de la règle 13 (« sans animation »).
- **Barre de progression :** Android affiche une barre fine pendant le tout premier chargement. Le site n'affiche rien tant que la page n'est pas prête.
- **Repli des anniversaires :** Android lit aussi les anniversaires du mois de référence (`+ ref.monthNumber`) ; le site s'appuie sur `[debut, debut+15, fin, ref]`. Le résultat est le même.
