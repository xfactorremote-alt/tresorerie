# Audit de parité : Accueil du bureau et Opérations

Périmètre : `pageTableau` / `pageEcritures` (docs/app.js), docs/graphiques.js et, sur Android, `EcranAccueil`, `EcranOperations`, `DetailOperation`, `FormulaireVirement` (Ecrans.kt) et Graphiques.kt. Rôles vérifiés : tresorier, president, bureau (captures du site dans `audit/operations/`, téléphone et 1280 px, aucune erreur JavaScript). Gradle n'a pas été lancé (consigne) : la compilation reste à faire par le coordinateur.

## Différences corrigées (Android aligné sur le site)

### Suppression et messages de refus (points demandés)
- **Supprimer une opération** : la suppression est maintenant portée par `EcranOperations` (nouveau paramètre `supprimerOp` de `DetailOperation`). Avant, elle était portée par la fiche, qui se ferme dès la suppression : son `rememberCoroutineScope` était annulé, si bien que le message « Annuler » disparaissait aussitôt, voire affichait une erreur d'annulation. Désormais, la ligne est retirée **immédiatement** de la liste affichée (les deux jambes pour un virement), puis la liste est rechargée (`version++`). « Annuler » restaure la ligne et recharge la liste. Les soldes de l'accueil sont relus à chaque ouverture de l'onglet (`Repo.soldes()`, vue `v_soldes`, sans cache), donc l'opération supprimée n'y figure plus. En production, `Synchro` recharge aussi tout écran ouvert. `DetailOperation` garde un repli local pour l'appel venant de Demandes (Ecrans2).
- **Messages de refus de la base** : `traduireErreur` prend maintenant `PostgrestRestException.error`, qui est le texte exact du `raise exception`, au lieu de la première ligne de `message`. Le texte est donc identique au site : « Opération rapprochée : utilisez la contre-passation », « Exercice clôturé : opération verrouillée », « Cette opération a été corrigée : supprimez d’abord la correction », « Cette opération paie une demande de dépense : utilisez « Régulariser » ou la contre-passation », « Virement rapproché : utilisez « Annuler le virement » ». Ces textes sont identiques dans schema.sql, mock.js et Demo.kt. Un test a été ajouté dans `AuditTest.motifEtLibelleDeCorrection`. J'ai aussi ajouté la traduction « rate limit » du site.
- Le bouton Supprimer suit les mêmes conditions que le site (pas rapprochée, pas corrigée, pas un paiement de demande, pas dans un exercice clôturé, pas un virement annulé ou rapproché).

### Accueil
- « À traiter » : chaque ligne a le bouton du site (« Voir », « Valider » et « Payer » en bouton plein, « Joindre », « Voir le budget »), avec un filet entre les lignes. Avant, c'était un « Voir » orange partout.
- « À traiter » : « N justificatifs à joindre » affiche « Délai de N jours après paiement » (réglage `delai_justificatif_jours`) et non plus une somme.
- « À traiter » : « Voir » sur « dépenses refusées après paiement » ouvre Demandes sur le filtre « À régulariser » (`FiltreDemandesAccueil`, lu dans Ecrans2). J'ai aussi corrigé le filtre, qui retombait sur « Toutes » pendant le chargement.
- Rubrique en alerte : un filet rouge à gauche (`rub-alerte`) remplace le cadre rouge.
- « Ma situation » n'est ouverte d'office que si la cotisation est en retard.
- La bannière affiche toujours Résultat, Réserve (« Non définie » s'il n'y a pas de dépenses) et Cotisations. Avant, ces chiffres étaient masqués sans opérations.
- « Chiffres » sans aucune opération : la rubrique affiche « Aucune opération » et « Les chiffres et les graphiques apparaissent dès la première opération. », plus « Nouvelle opération » si l'on peut saisir. Avant, la rubrique était absente.
- « Chiffres » : les tuiles sont des cartes blanches bordées (et non plus grises). La carte Cotisations affiche « – » quand rien n'est exigible.
- « Dernières opérations » : un filet entre les lignes, et « Toutes les opérations » seulement s'il y a des opérations.
- Grand écran (≥ 840 dp) : chiffres sur 3 colonnes, Évolution et Répartition sur 2 colonnes, Anniversaires et Dernières opérations côte à côte, comptes sur 4 colonnes, pastilles de la bannière en largeur naturelle.
- Graphiques : tous les mois sont étiquetés jusqu'à 12, la ligne du zéro est plus marquée (#CFC8C5), les proportions sont celles du site (640×220 et 640×180, avec une hauteur minimale), l'échelle part du minimum négatif, et les coins de la légende et de la jauge font 2 dp.
- Anneau : « total » sous le montant, rayons 80/52 sur 180, anneau de 170 au-dessus de la légende sur téléphone et à côté au-delà, montant de chaque part aligné à droite.

### Opérations
- Recherche sans accents, qui porte aussi sur la **catégorie**, comme le site.
- Résultat en encre neutre (rouge s'il est négatif) ; Solde en rouge s'il est négatif ; « Solde » seul pour la période « Tout ».
- Appuyer sur Recettes ou Dépenses retire aussi le filtre de catégorie.
- « Tout effacer » (pastilles et feuille) efface aussi la recherche.
- Liste vide : « Aucune opération. » / « Aucune opération pour ces filtres. », dans une carte. La liste elle-même tient dans une carte (`section.carte`).
- Ligne : l'état et « Sans pièce » peuvent se suivre (une opération « Rapprochée » sans pièce affiche les deux, comme le site). « Virement interne » est en bleu.
- Grand écran : bouton « Nouvelle opération » dans l'en-tête dès 640 dp (au lieu du bouton flottant), totaux sur 4 colonnes au-delà de 840 dp.
- Exporter : feuille à deux cartes « PDF / Mise en page pour imprimer, archiver ou transmettre » et « Excel / Tableau modifiable (CSV) », comme `choisirFormat`.
- CSV : « Virement interne (entrée) » ou « (sortie) » ; montants sans « ,0 » (15 et non 15,0).
- Feuille Filtres : ordre du site (Période, Compte, puis Du/Au, Catégorie, Rubrique). Sans type choisi, chaque catégorie porte « (recette) » ou « (dépense) ». Changer de type remet la catégorie à « Toutes ». Le message « Période invalide : la fin doit suivre le début » s'affiche.
- Saisie : le message vient du formulaire, comme sur le site (« Opération enregistrée », « Dépense enregistrée et envoyée au président », « Encaissement enregistré »). Avant, « Opération enregistrée » écrasait le message de validation.
- Formulaire de saisie : « Mode de paiement », bouton « Virement interne », libellé proposé « Cotisation : Nom » / « Collecte : Nom » avec une espace simple.

### Fiche d'une opération
- Libellé et montant sur la même ligne ; ordre Date, Catégorie, Compte, Mode (« · À vérifier »), Activité, Tiers, Rubrique, Validation (« Refusée · motif »), Virement interne, État, Référence.
- Historique : « Corrige « X » », « motif : » avec espaces insécables. Le préfixe de la correction se lit avec « : » simple, ce qui reste compatible avec les anciens libellés Android à espace insécable.
- Actions dans l'ordre du site :
  - « Joindre une pièce » : seulement pour une dépense sans pièce et non corrigée, avec le droit saisir_ecritures.
  - « Faire valider par le président » (libellé complet).
  - « Inscrire à l’inventaire ».
  - « Contre-passer » et « Annuler le virement » en neutre.
  - « Supprimer ».
  - « Fermer » (nouveau bouton).
- Pièces : rien n'est affiché quand il n'y en a pas (plus de cadre « Aucune pièce jointe »).
- Fenêtre « Contre-passer » :
  - texte du site : « Une opération de −X annule « … » à la date du jour. L’opération d’origine reste dans l’historique, reliée à sa correction. » ;
  - exemple de motif « Erreur de montant, doublon, dépense refusée… » ;
  - bouton « Annuler » (« Garder » reste pour le virement, comme le site).
- Virement interne : date future refusée (`max` du site), exemple de libellé selon les comptes, bouton « Bordereau ou ticket ».

### Base, démo et règles communes
- `libelleCorrection` : « Préfixe : libellé » avec une espace simple, comme la base (`annuler_virement`) et le site. Avant, Android enregistrait une espace insécable et l'historique du site ne savait pas relire ces corrections.
- Demo.kt aligné sur mock.js :
  - libellés « Cotisation : », « Participation sortie : », « Repas de Noël : » ;
  - opérations saisies par le trésorier, avec une date de saisie (historique « Saisie par vous ») ;
  - mode « autre » pour Frais bancaires et Frais de tenue de compte ;
  - date de rapprochement ;
  - plus de pièce sur « Boissons et gobelets » (« Sans pièce » des deux côtés).

## Différences restantes
- Formes : Android garde la charte Material Expressive (coins de 20 à 28 dp), le site est en Fluent 2 (4 à 8 px). Ce choix vaut pour toute l'application (Theme.kt) et dépasse ce périmètre.
- La période « Du… au… » se saisit au clavier (JJ/MM/AAAA) sur Android et par un sélecteur de date sur le site. C'est propre à chaque support.
- Les infobulles des graphiques au survol n'existent pas sur un écran tactile.
- **Problème commun, signalé au coordinateur** : `rememberSuppression` (Arrivee.kt) lance « Annuler » dans le scope du composable appelant. Partout où le rappel `apres` ferme la feuille qui porte la suppression, ce scope est annulé et « Annuler » disparaît, avec en prime une erreur d'annulation possible à cause de `catch (e: Exception)`. C'est le cas de Materiel.kt (`onFini(true)`), Ecrans4 collecte et rendez-vous (`onFermer()`) et DetailOperation ouvert depuis Demandes. Correction recommandée : lancer la partie snackbar dans une portée durable (par exemple `Annulation`) ou remonter la suppression comme ici, et ne pas attraper `CancellationException`.

## Android meilleur que le site (à reporter sur le site)
- « N demandes à payer » (accueil) exclut celles que la personne a validées elle-même, ce qui respecte la séparation des tâches. Le site les compte.
- Pièce d'une opération : Android compte aussi le justificatif déposé sur la demande payée (`aPiece`). Le site ne regarde que `attachments.transaction_id` et peut afficher « Sans pièce » à tort.
- La fiche Android montre en grand le justificatif de la demande et la signature, là où le site n'a qu'un bouton « Signature ».
- La recherche porte aussi sur le montant (« 64,90 »).
- Le nombre d'opérations affichées (« 42 opérations ») apparaît au-dessus de la liste.
- Libellé grisé pour les opérations annulées ou corrigées.
- « Inscrire à l’inventaire » est exclu pour un virement interne (`estFlux`).

## Fichiers modifiés
- android/…/Ecrans.kt : EcranAccueil, EcranOperations, FeuilleFiltres, LigneOperation, DetailOperation, FormulaireVirement, Rubrique (filet d'alerte), Tuile ; ajouts TacheAccueil, FiltreDemandesAccueil, BORD_CARTE, DeuxColonnes, FormatExportOperations
- android/…/Graphiques.kt : GraphColonnes, GraphSolde, Jauge, GraphAnneau
- android/…/Format.kt : traduireErreur
- android/…/Coherence.kt : libelleCorrection
- android/…/Demo.kt : données de départ des opérations
- android/…/Ecrans4.kt : FormulaireEcriture (message, libellés)
- android/…/Ecrans2.kt : filtre initial venu de l'accueil, garde pendant le chargement
- android/…/commonTest/AuditTest.kt : format du libellé de correction, message de refus d'une opération rapprochée
