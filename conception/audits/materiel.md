# Audit Matériel (site `pageMateriel` / Android `Materiel.kt`)

## Photo de « PIANO » absente sur Android : causes trouvées
1. **Cause principale : l'article est sorti, donc caché par le filtre par défaut, des deux côtés** (« En service »). Le site demande pourtant la photo : `chargerPhotos(items)` signe en un seul lot les URL de **tous** les articles, y compris les sortis qui ne sont pas affichés. Le journal du seau montre donc une demande du site pour PIANO alors que l'article n'est pas à l'écran. Android ne télécharge que les lignes affichées : tant que « Sortis » n'est pas choisi, aucune demande ne part. Cela explique le « jamais demandé ».
2. Android **oubliait le filtre** à chaque ouverture de l'onglet (retour à « En service »). Le site le garde pour la session (`S.filtreMateriel`). Corrigé : le filtre est mémorisé dans `filtreMaterielMemo`.
3. Téléchargement fragile : `catch (_: Exception) { }` vide, aucun nouvel essai et aucun cache (chaque ligne qui revient dans la `LazyColumn` téléchargeait de nouveau). Corrigé : cache pour la session, 3 essais espacés, nouvel essai à chaque `Synchro.version` et message dans le journal (`println`) en cas d'échec ou d'image illisible.
4. Décodage en pleine taille avec `imageDepuisOctets`, dont le `catch (Exception)` ne rattrape pas `OutOfMemoryError`. Corrigé : nouvelle fonction `imageDepuisOctetsReduite(octets, max)` (inSampleSize, `catch Throwable`). L'ancienne fonction n'a pas été touchée. Le JPEG de 157 Ko n'aurait de toute façon pas posé problème.
5. Appareil photo : le site propose `capture="environment"` (sur téléphone, il ouvre directement l'appareil). Android ne proposait que la galerie. Ajouté : `rememberPrendrePhoto` (TakePicture + le FileProvider existant `cache/pieces`, photo redressée selon l'EXIF, réduite à 1600 px, sans permission CAMERA).
6. Emplacement sans photo : le site affiche l'**initiale** dans un carré gris, pas une icône. Android faisait déjà de même, c'est conservé (`VignetteMateriel`). Le site affiche la photo en grand en tête de la fiche (`.photo-materiel` : pleine largeur, 240 px de haut au plus). Android affichait un carré de 160 dp. C'est corrigé avec `GrandePhotoMateriel`.

## Différences corrigées (Android)
- Le filtre est gardé pour la session.
- Cartes chiffrées avec leur sous-ligne, comme le site : « N lignes d'inventaire », « dons compris, hors prêts », « N à vérifier » ou « Inventaire à jour ». Le titre devient « Valeur actuelle estimée ».
- En-tête de catégorie : le nombre est affiché atténué à côté du nom.
- Recherche : texte d'aide « … numéro de série… ».
- Exporter : même texte que `choisirFormat` (« PDF : mis en page pour imprimer, archiver ou transmettre. Excel : tableau modifiable (CSV). »).
- Fiche :
  - photo en grand ;
  - ajout de l'encart « Achat enregistré le … : libellé, montant » (droit `consulter_finances`, nouveau `Repo.ecritureParId`) ;
  - historique sur deux lignes comme le site (mouvement : membre en gras, puis date · notes).
- **Supprimer** : `rememberSuppression` vivait dans la fiche. La fiche se fermait aussitôt, ce qui annulait sa coroutine : le bouton « Annuler » de la barre ne restaurait rien. La suppression est maintenant tenue par l'écran ; après suppression ou restauration, la fiche se ferme et la liste se recharge (`version++`).
- Formulaire :
  - même ordre que le site, deux champs par ligne (Catégorie/Quantité, Marque/N° de série, Origine/Date, Valeurs, État/Lieu) ;
  - champ « Valeur actuelle estimée » avec le texte d'aide « Prix d'occasion » ;
  - bloc Photo avec aperçu (photo actuelle, nouvelle photo ou initiale), plus « Prendre une photo » et « Choisir une photo ».
- Démo : `Demo.kt` reprend les 9 articles et les 2 mouvements de `mock.js` (mêmes valeurs ; les Micros sont liés à l'opération « Câbles et micro »).

## Différences restantes
- Date de sortie et date d'entrée : saisie JJ/MM/AAAA sur Android, champ date sur le site. Il n'existe pas de sélecteur de date commun dans l'application.
- Formes : arrondis Material (10 et 20 dp) sur Android, coins Fluent de 4 et 8 px sur le site. C'est la conception propre à chaque support.
- La photo choisie dans la galerie n'est pas redressée selon l'EXIF (`reduirePhoto`, partagé par d'autres écrans, n'a pas été modifié). Le site compresse à 900 px, Android à 1600 px.

## Android meilleur que le site (à reporter)
- La recherche Android porte aussi sur le **numéro de série**. Le site l'annonce dans son texte d'aide mais ne cherche que dans la désignation et la marque (`data-nom`) : c'est un défaut du site.
- Pendant une recherche, Android masque les catégories vides. Le site laisse les en-têtes de catégorie même quand toutes leurs lignes sont masquées.
- Android propose à la fois l'appareil photo et la galerie. Sur téléphone, le site n'ouvre que l'appareil.
