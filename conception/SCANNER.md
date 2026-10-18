# Scanner de documents — rétro-ingénierie de CamScanner (18 octobre 2026)

Référence : CamScanner (INTSIG), parcours public « photographier un document » (application Android et iOS), relevé par usage et par la documentation publique de l'éditeur.
Étiquettes : `OBSERVÉ` (parcours visible par tout utilisateur) · `DÉDUIT` · `RECOMMANDÉ` · `ABANDONNÉ`.

## 1. Parcours de la référence

| Étape | CamScanner | Statut |
|---|---|---|
| Prise de vue | viseur plein écran, contour du papier dessiné en direct, déclenchement automatique quand l'image est stable | OBSERVÉ |
| Cadrage | photo avec 4 coins (et 4 milieux de côté) à faire glisser, **loupe** au-dessus du doigt, « Tout le cadre » / « Détection auto » | OBSERVÉ |
| Redressement | la zone choisie devient un rectangle (perspective corrigée), proportions d'après les côtés mesurés | OBSERVÉ |
| Améliorer | filtres : Original, Magic Color (papier blanc, couleurs gardées), Gris, Noir et blanc, Éclaircir ; rotation ; recadrer | OBSERVÉ |
| Sortie | JPG ou PDF de plusieurs pages, nom daté, reconnaissance de texte (payante) | OBSERVÉ |

Ce qui fait sa qualité (DÉDUIT) : la détection automatique évite presque toujours le cadrage manuel, la loupe rend le cadrage précis au doigt, et le filtre « Magic » efface ombres et teinte du papier sans perdre les tampons de couleur.

## 2. Transposition (site + Android, mêmes calculs : `scanner.js` ↔ `Scanner.kt`)

| Élément | Décision | Détail |
|---|---|---|
| Prise de vue | **Adapté** | appareil photo du téléphone (`capture="environment"` sur le site, `TakePicture` sur Android) : fonctionne partout, iPhone compris, sans autorisation de caméra en direct ; pas de viseur animé |
| Détection du papier | **Repris** | `detecterDocument` : image réduite à 256 px, flou, seuil d'Otsu, plus grande zone claire, coins = extrêmes de x + y et x − y ; papier non trouvé → tout le cadre et message |
| Coins + loupe | **Repris** | 4 coins orange (zone de 44 px), loupe ×2,5 du côté opposé au doigt ; site : coins au clavier (flèches) |
| Redressement | **Repris** | `redresser` : homographie carré → quadrilatère (Heckbert), interpolation bilinéaire, 1 600 px au plus |
| Filtres | **Repris (4)** | `ameliorer` : Magique (balance des blancs + division par le blanc local + niveaux), Original, Gris, Noir et blanc ; `tourner` (quart de tour) |
| Sortie | **Adapté** | un JPEG (`scan-AAAAMMJJ-HHMM.jpg`, qualité 85) placé dans le champ de la pièce, comme un fichier choisi |
| Plusieurs pages / PDF | **Reporté** | les pièces de la trésorerie sont surtout des tickets et factures d'une page |
| Reconnaissance de texte, déclenchement auto | **Abandonné** | poids et complexité sans gain pour un justificatif |
| Marque, couleurs, abonnement | **Abandonné** | identité du produit conservée (orange, Fluent 2) |

## 3. Où le trouver

Chaque pièce jointe : saisie d'une opération (justificatif), fiche d'une opération (« Scanner » à côté de « Joindre une pièce »), virement interne (bordereau), demande de dépense (devis), justificatif d'une demande payée (facture ou ticket), rapprochement (relevé ou procès-verbal).

## 4. Vérifié / non vérifié

- Vérifié : image de synthèse identique des deux côtés (coins à ±4 px du vrai papier, mêmes coins site/Android, mêmes proportions de noir et de blanc) ; parcours complet du site au téléphone et à l'ordinateur (photo, glisser un coin, loupe, filtres, fichier placé dans le champ).
- Non vérifié : vraies photos (lumière, papier blanc sur table claire — la détection peut alors prendre tout le cadre : on ajuste les coins), rendu Android sur téléphone, Safari iPhone.
