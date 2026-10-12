# Audit de parité « membres » (Membres, Rapports, Plus, vue du membre)

Référence : docs/app.js (`pageMembres`, `feuilleFicheMembre`, `feuilleAcces`, `feuilleMembre`, `feuilleImport`, `feuilleLien(s)`, `csvMembres`, `pdfMembres`, `pageRapports`, `exporter`, `coquille` › Plus, `vueMembre`, `banniereAnnonces`, `rubriqueCommuniques`, `feuilleCommunique`, `rendreSondage`).
Captures du site (démo, téléphone et 1280×900) : scratchpad/audit/membres/*.png, aucune erreur JavaScript. Le site n'a pas été modifié.

## Différences corrigées (Android)

### Membres (`EcranMembres`, Ecrans3.kt)
1. Sections **Bureau** et **Membres** dans des cartes sobres (coins 8, lignes séparées par un filet, pas de filet après la dernière), comme `section.carte` ; le titre « Membres » n'apparaît que s'il y a un bureau.
2. Recherche **sans accents** (`sansAccents`) et qui n'altère pas les sections (comme le masquage des `li` du site) ; champ sans loupe, comme le site.
3. Barre de recherche + « ⋯ » affichées **seulement s'il y a des membres** ; bouton « ⋯ » rectangulaire (Fluent).
4. Filtre Tous / Actifs / Inactifs : nombre en petit et atténué (`<small>`), pas de coche, **gardé pendant la session** (`EtatMembres.filtre`, comme `S.filtreMembres`).
5. Messages vides du site : « Aucun membre inactif. », « Tous ces membres font partie du bureau. », « Aucun membre. » + « Ajoutez-les avec le bouton +, ou importez votre liste avec ⋯. » (l'ancien « Aucun membre trouvé. » n'existait pas sur le site).
6. Ligne : « (vous) » atténué et plus petit, fonction et « Inactif » côte à côte.
7. Bouton **+ rond, couleur principale** (`.fab`), libellé « Ajouter un membre », au lieu d'un bouton étendu jaune « Ajouter ».
8. **Temps réel / suppression** : la liste n'était rechargée que si `version > 0` ; un changement venu de la base (`Synchro.version`) laissait l'ancienne liste. Désormais rechargée aussi sur `Synchro.version`. Suppression et « Annuler » rechargent la liste (`version++`) et les données partagées (`recharger()`) ; ajout/modification de fiche appellent aussi `recharger()`.
9. Liste initiale triée par nom (comme `order('nom')`), et la démo (`Repo.membres()` en démo) aussi.
10. **Exporter la liste** : feuille « Exporter la liste des membres » avec les deux cartes du site (PDF « Mise en page pour imprimer, archiver ou transmettre », Excel « Tableau modifiable (CSV) ») au lieu d'une boîte de dialogue au texte différent.
11. **Importer** : vraie feuille comme `feuilleImport` (titre, explication avec colonnes obligatoires en gras, « Choisir le fichier », **« Télécharger le modèle »** — même `modele-membres.csv`), résultat dans la feuille (« N lignes prêtes, N refusées. », motifs en encadré rouge défilant, « Annuler » / « Importer N membres »), erreur de colonnes affichée dans la feuille. Avant : sélecteur de fichier direct puis dialogue, sans modèle.
12. CSV des membres : colonne **Fonction** ajoutée (mêmes colonnes que `csvMembres`).
13. PDF des membres (`documentHtml` « membres », Documents.kt) : tri par ordre des fonctions (`rangRole`) puis nom, sous-titre « N membres actifs », colonne **Adhésion** (nouveau champ `Membre.adhesion` ← `date_adhesion`).

### Fonction et accès (`FeuilleFonction`)
14. État en pastille (Compte actif / Accès coupé / Invitation en attente / Sans accès) suivi du nom du compte ou de l'adresse invitée.
15. Phrase de l'invitation en attente : « La personne crée son compte sur le site avec cette adresse ; sa fonction s’applique dès la création. »
16. « E-mail de connexion * » obligatoire ; case « Accès à la plateforme » visible mais grisée pour son propre compte (comme `disabled`).
17. Contrôle « Un compte existe déjà avec cette adresse : rattachez-le ».
18. Après l'invitation : « **Accès accordé.** X crée son compte sur le site avec … », message avec l'adresse du site (`Config.SITE_URL`), boutons **Prévenir par WhatsApp** et **Prévenir par e-mail** (le second manquait ; « Terminer » retiré).

### Fiche (`FicheMembre`)
19. Libellés obligatoires « Prénom * », « Nom * », « Anniversaire (jour et mois, sans l’année) * » ; « Facultatif » à côté de « Ajouter une photo » ; avatar à initiales au lieu d'un rond vide ; case « **Le membre** accepte que son anniversaire soit affiché aux autres membres ».

### Liens personnels (Liens.kt)
20. `messageLien` : « de l’association » si le nom est vide (comme le site).
21. Envoi groupé : tri alphabétique sans tenir compte des accents (Élie avec les E, comme `localeCompare`), filets entre les lignes.

### Rapports (`EcranRapports`, `exportCsv`)
22. Registre des demandes : formats **PDF et Excel** (Excel manquait ; il tombait sur l'export du budget). Nouveau `exportCsv("demandes")` avec les colonnes du site, fichier `demandes-de-depense.csv`.
23. Libellé « Pièces justificatives (fichier ZIP **classé par mois**) ».
24. Journal Excel : colonnes du site (Date, Sens, Libellé, **Tiers, Rubrique**, Catégorie, **Activité**, Compte, Mode en clair, Montant, Rapprochée) ; nom `ecritures-AAAA.csv` pour un exercice complet.
25. Participations Excel : colonne **Activité**.
26. Exercice et trimestre choisis affichés avec le même texte que les options (« Exercice 2026 (01/01/2026 – 31/12/2026) », « 1er trimestre (janv. à mars) »).
27. Texte d'aide complet : « … Cotisations, budget, membres et matériel s’exportent depuis leur page (bouton Exporter) ; la sauvegarde complète est dans Paramètres › Sauvegarde. »

### Plus (`EcranPlus`)
28. Ordre du site (`pagesAutorisees`) : Budget, Planning, Communiqués, Matériel, Tiers, Membres, Rapprochement, Rapports ; libellé « Rapports » (au lieu de « Rapports et exports »).

### Vue du membre (`EcranAdherent`, Annonces.kt, Partages.kt)
29. Ma cotisation : icône d'état **dans le titre** (verte si à jour, rouge si retard), texte « **À jour** · réglée jusqu’à … » ou « **X € en retard** · périodes commencées et non réglées » ; **légende** sous la grille (seulement les états présents). Ajout d'un paramètre facultatif `teinteIcone` à `Rubrique` (Ecrans.kt).
30. Bannière : l'annonce « Rappel » faisait défiler vers la mauvaise rubrique (index décalé d'un) : corrigé vers « Comment régler ».
31. Bannière : **pause au toucher** et reprise 4 s après (comme touchstart/touchend), y compris après un glisser (avant : reprise immédiate).
32. Sondage : « Merci pour votre réponse » après l'envoi ; « Chargement des résultats… » pendant le chargement.
33. Lien : « Copier le lien » affiche « Lien copié » ; boutons dans l'ordre du site (Copier le lien, bouton du lien, **Fermer tonal**) ; l'hôte n'est plus répété dans la fiche (il reste dans la rubrique).

### Démo (Demo.kt aligné sur mock.js)
34. WhatsApp seulement pour i % 3 ≠ 0 (Grâce, Samuel, Déborah, Josué, Paul sans numéro ; Esther, Naomie, Marthe, Jean-Marc avec), accord d'anniversaire pour tous sauf Esther et Élie, date d'adhésion (septembre N-1, Josué 15 mars N), adhésion reprise à l'ajout.

## Différences restantes (et raison)
- **Page du lien personnel** (`pageLien` : bannière avec cloche des nouveautés depuis la dernière visite, Mes versements, pied « Oublier ce lien », minuterie) : c'est une page web par nature ; Android ouvre le lien dans le navigateur. « Voir comme X » ouvre donc la vraie page du lien dans le navigateur (le site affiche un aperçu interne où répondre est impossible).
- Import **Excel (.xlsx)** : Android ne lit que le CSV (`rememberChoixTexte`) ; le texte de la feuille l'indique (« depuis Excel : Enregistrer sous › CSV ») au lieu de « CSV ou Excel ». Le menu garde « Importer (CSV, Excel) ».
- Invitation : le site complète l'e-mail de la fiche du membre s'il est vide ; Android ne le fait pas (il faudrait un `Repo` ciblé, hors des signatures existantes).
- Plus : sur le site c'est une feuille qui s'ouvre depuis l'onglet ; sur Android c'est un écran (structure de navigation dans App.kt, hors périmètre). Les cartes Android gardent un chevron.
- Grand écran Membres : le site place le bouton + dans le titre (rectangle) au-delà de 1000 px ; Android le garde flottant.
- Bannière : effet « rdv imminent » (rendez-vous à J-1) du site non reproduit ; pas de détection « réduire les animations » côté Android (aucun utilitaire commun) — la durée est déjà comptée pas à pas.
- Sondage : une réponse obligatoire manquante s'affiche en ligne sur Android (toast sur le site).

## Android meilleur que le site (à reporter)
- La fiche « Fonction et accès » Android désactive « Donner l’accès » tant que l'e-mail n'est pas valide (le site laisse la validation au navigateur) — équivalent.
- Rapports : le bouton Android affiche la progression de l'archive ZIP (« N / M pièces… ») dans le bouton ; le site seulement via `archivePieces(a2, btn)` — à vérifier côté site.
