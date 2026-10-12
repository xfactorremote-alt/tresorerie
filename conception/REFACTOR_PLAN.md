# Plan de normalisation (lots ordonnés)

| Lot | Contenu | Dépend de | Critère d'acceptation | Statut |
|---|---|---|---|---|
| 1 | Défauts fonctionnels : suppression fiable, photo du matériel, refus de la base lisibles | — | Ligne supprimée disparaît, « Annuler » marche après fermeture de la fiche | ✅ 0.28.0 |
| 2 | Jetons : couleurs et formes Fluent dans `Theme.kt` (`Couleurs`, `Formes`, `MaterialTheme.shapes`) | — | Valeurs = `:root` du site ; compilation | ✅ 0.29.0 |
| 3 | Composants très réutilisés : boutons rectangulaires (`Boutons.kt`), `BoutonPlus`, `ChampDate`, `Puce`, `CarteBlanche` | 2 | 0 appel direct aux boutons Material (bytecode) ; 19 champs de date | ✅ |
| 4 | Remplacement des valeurs en dur de même valeur (144 couleurs, 99 rayons) | 2 | Aucun changement visuel attendu ; compilation | ✅ |
| 5 | Accessibilité : zones tactiles 24 px, noms accessibles, contraste des champs | 3 | Scan Playwright sans cible tactile < 24 px | ✅ |
| 6 | Reports « Android meilleur que le site » : pièce de la demande, « à payer » hors validateur | — | Site = Android | ✅ |
| 7 | Reste à faire (proposé) : « Voir l'opération » par-dessus la fiche sur le site ; import .xlsx sur Android ; redresser les photos de la galerie (EXIF) ; case « Dispensé » hachurée sur Android ; rôles typographiques nommés (`Texte.Corps`, `Texte.Legende`) | 3 | Parité ; tests | À faire |
| 8 | Vérification sur appareil : parcours complets sur un téléphone Android et une tablette | 2–5 | Captures téléphone et tablette comparées au site | **À faire (propriétaire)** |

Règles pour la suite (aussi dans `CLAUDE.md`) : toute nouvelle couleur ou forme passe par un jeton ; tout nouveau bouton par les composants communs ;
toute nouvelle date par `ChampDate` / `<input type="date">` ; aucune zone tactile sous 24 px.
