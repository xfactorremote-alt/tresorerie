# Audit de parité : Demandes de dépense et Rapprochement

Captures du site (démo, rôles trésorier, président, bureau et adhérent, en téléphone et sur ordinateur) : `scratchpad/audit/demandes/*.png`.
L'adhérent n'a pas l'onglet Demandes : il arrive sur sa vue membre, des deux côtés.

## Différences corrigées

### Site (un vrai défaut)
- **Bouton + vide** : le bouton « Nouvelle demande » s'affichait comme un rectangle orange vide sur ordinateur et un rond vide sur téléphone. `rendre()` ne remplissait que `.page > .fab`, alors que Demandes et Membres placent ce bouton dans `.page-titre`. Le sélecteur est étendu à `.page > .page-titre > .fab`. Résultat : icône + et « Nouvelle demande » sur ordinateur, rond + sur téléphone. Membres est corrigé du même coup. Vérifié avec Playwright : aucune erreur JS et aucun débordement sur Demandes, Membres, Opérations et Rapprochement.

### Android, liste des demandes (EcranDepenses)
- **Filtres** : les FilterChip Material avec badge rouge sont remplacés par des pilules identiques à `.onglets-filtre`. Elles ont un fond blanc et un filet gris ; leur nombre est dans une petite pastille grise. Le filtre choisi a un fond orange clair et sa pastille passe en orange plein. Toujours aucun nombre sur « Terminées » ni sur « Toutes ».
- **Liste vide** : le texte nu devient une carte centrée avec l'icône « valide » (`.carte.vide`).
- **Carte de liste** : le total est en gras 18 au lieu d'extra-gras, et le filet sous l'en-tête est plus doux. Il n'y a plus de filet après la dernière ligne.
- **Ligne** : mêmes espacements (12), montant 16, chevron 18. Le demandeur inconnu s'affiche « Membre du bureau » (`nomDe`). La catégorie est toujours montrée.
- **Puces d'état** : elles ont maintenant le filet coloré du site (`puce-ok`, `puce-partiel`, `puce-ko`, `puce-neutre`), grâce au paramètre facultatif `bord` ajouté à `Puce`.
- **Bouton +** : l'ExtendedFAB jaune « Nouvelle demande » devient un bouton rond + avec l'icône seule, comme le site sur téléphone. Au-delà de 840 dp, il est remplacé par un bouton « + Nouvelle demande » dans l'en-tête, à droite du résumé, comme le site sur ordinateur.

### Android, fiche d'une demande
L'ordre des blocs suit maintenant `ficheDemande` :
1. En-tête : objet, puce d'état (et « Déjà payée »), montant.
2. Informations en `.ligne-detail` : libellé à gauche, valeur à droite, filet. Avant, la colonne faisait 120 dp sans filet. « Demandée par » se replie sur « Membre du bureau ».
3. Justification : filet gauche de 3 dp sur fond gris, comme `.justification`.
4. Chronologie : un seul filet gris continu, et les étapes à venir ne sont plus grisées. Les titres sont en gras et les détails passent de 12 à 14 sp.
5. **Pièce justificative** : elle est maintenant aussi trouvée par l'opération liée (`pieceDe` du site : pièce de la demande OU de son opération). Avant, un justificatif joint à l'opération laissait « Justificatif attendu » dans la chronologie et n'apparaissait pas.
6. Encart bleu `.info` avec le texte exact du site : « Paiement du … neutralisé par contre-passation le … (motif). Le montant n'entre plus dans les comptes. »
7. Encart rouge `.alerte` avec le texte exact du site : « Dépense déjà payée, puis refusée par le président : l'argent est sorti de la caisse / du compte bancaire. Régularisez-la… » ou « Le trésorier doit la régulariser. »
8. **Pièces** : les grands aperçus empilés (PiecesVue, et un titre « Devis ») deviennent une rangée de petits boutons, dans l'ordre Devis (trombone), Signature (valide), Justificatif (trombone). Chacun ouvre un aperçu dans l'application, comme `ouvrirFichier` : titre « Signature du président » ou « Justificatif », image, puis « Ouvrir » et « Fermer ».
9. Actions secondaires à gauche : « Voir l'opération », « Annuler la demande ».
10. Dernière rangée : « Supprimer » (corbeille) à gauche ; à droite Refuser + Valider et signer, Payer, Régulariser ou Joindre le justificatif. « Joindre le justificatif » passe de tonal à principal. S'il n'y a aucune action, un bouton **Fermer** s'affiche.

Avant, toutes les actions étaient mêlées dans un seul FlowRow aligné à droite, dans un autre ordre, sans « Fermer ».

### Android, fenêtres
- **Valider (Signature)** :
  - ajout de la ligne « Demandée par X · catégorie · pour le … » et du bouton « Voir le devis » (avant : grand aperçu) ;
  - libellé « Signature » avec un astérisque rouge ;
  - zone de 180 dp à coins de 4 dp et bord pointillé (avant : 200 dp, coins de 20 dp, trait plein) ;
  - trait de 2,4 dp.
- **Refuser** : AlertDialog → feuille qui monte, comme le site. L'objet est en gras. Le champ « Motif * » fait 3 lignes. Le bouton « Refuser » prend le style danger du site (fond clair, filet et texte rouges). La feuille reste ouverte en cas d'erreur.
- **Payer** : AlertDialog → feuille, objet en gras. La feuille reste ouverte en cas d'erreur.
- **Joindre le justificatif** : avant, l'appui ouvrait directement le sélecteur de fichier. C'est maintenant la feuille du site : « Joindre le justificatif », objet et montant, « Facture ou ticket * », choix du fichier, « Photo ou PDF de 3 Mo maximum. », puis Annuler et Joindre. Le message « Justificatif joint (N Ko) » est inchangé.
- **Annuler la demande** : AlertDialog → feuille avec un bouton danger. « Garder » revient à la fiche, comme le site.
- **Régulariser** :
  - le début des deux choix est en gras (« **Erreur de saisie** : … », « **Remboursement reçu** : … ») ;
  - « Arrivé sur » devient « Arrivé sur le compte » ;
  - la date du remboursement ne peut pas être dans le futur (`max` du site) ;
  - le libellé de l'opération est en gras.
- **Nouvelle demande** :
  - astérisques rouges sur Objet, Montant (€) et Catégorie, et sur « Pourquoi cette dépense » au-delà du seuil ;
  - Objet a le placeholder « Location de la sono pour le concert » ;
  - le libellé « Montant (€) » remplace le suffixe € ;
  - le placeholder complet « …, ce qui se passe si on ne la fait pas » ;
  - « Pour le » au lieu de « Pour le (facultatif) », et pas de date avant aujourd'hui (`min` du site) ;
  - libellé « Devis (facultatif) » au-dessus du bouton de choix ;
  - montant minimum de 0,01 ;
  - message **« Demande envoyée au président »** (avant : « Demande envoyée », qui s'ajoutait aussi au message d'échec du devis).

### Android, Rapprochement
- Les mêmes cartes que le site :
  - carte de saisie ;
  - carte « Écritures à pointer », dont les lignes étaient auparavant posées sur le fond ;
  - carte de bilan : Relevé, Pointé, Écart, le message, puis « Terminer le rapprochement » aligné à droite (avant : pleine largeur, en dehors des cartes) ;
  - carte « Historique », avec des filets entre les lignes.
- Les astérisques rouges remplacent « * » écrit dans le texte. Le suffixe € en double est retiré.
- Le nom d'origine du relevé choisi s'affiche en puce bleue, comme le site.
- La consultation (président, bureau) a l'encart `.info` du site au lieu d'une surface bleue pleine.
- « Voir » un relevé ouvre l'aperçu dans l'application, comme sur le site, au lieu de passer directement par une autre application.
- Après « Terminer », le début redevient le lendemain de la période verrouillée.

### Démonstration
- `Demo.kt` : la demande r1 « Décoration de la salle » est créée il y a 20 jours, comme `mock.js` (avant : 14 jours).

### Suppression et restauration
- Contrôlé : « Supprimer » passe par `rememberSuppression` (corbeille et « Annuler »). Le rappel ferme la fiche et recharge la liste (`version++`), après la suppression comme après la restauration. La liste des demandes est locale à l'écran (pas dans `Donnees`), donc la ligne disparaît aussitôt.

## Différences restantes
- **Couleur du bouton + au téléphone** : le site utilise l'orange principal, et toute l'application Android le jaune (Opérations, Membres, Planning, Matériel…). Je l'ai laissé jaune pour ne pas avoir une seule page différente : c'est à harmoniser globalement, en orange partout pour coller au site.
- **Forme des boutons et des cartes** : le site est en Fluent 2 (coins de 4 et 8 px, boutons rectangulaires), Android en Material 3 Expressive (coins arrondis). C'est le thème global, pas cet écran.
- **Dates** : saisies « JJ/MM/AAAA » sur Android contre un sélecteur de date sur le site. Convention commune à toute l'application.
- **Payer** : sur ordinateur, le site met Compte et Date côte à côte. Android les empile, sans incidence sur téléphone.
- **Défauts du site à corriger** (je n'y ai pas touché, ils dépassent le périmètre ou touchent une fonction partagée) :
  - `ouvrirFichier` titre « Justificatif » l'aperçu d'un **devis**. Android reprend ce titre pour rester identique.
  - Sur la feuille « Valider », l'astérisque de « Signature » passe à la ligne : `label.champ.obligatoire` sur un canvas.
  - Filtre « Toutes » vide : le texte affiche « Aucune demande . », avec une espace avant le point. Android écrit « Aucune demande. ».
  - `S.ouvrirDemande` est lu, mais rien ne le remplit. L'ouverture directe d'une demande n'existe ni sur le site ni sur Android.

## Android meilleur que le site (à reporter sur le site)
- **Voir l'opération** ouvre le détail de l'opération par-dessus la fiche, sans quitter Demandes. Le site change de page vers Opérations.
- Une barre de progression s'affiche au tout premier chargement. Le site garde la page précédente jusqu'à la réponse.
