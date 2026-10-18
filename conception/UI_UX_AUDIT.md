# Audit UI/UX — 12 octobre 2026 (version 0.29.0)

Méthode : skill « UI/UX Normalization » appliqué en deux passes.
1. **Écran par écran** (7 auditeurs, site = référence) : rapports dans `conception/audits/` (planning, materiel, demandes, operations, cotisations, membres, parametres) — 0.28.0.
2. **Système** (ce document) : jetons, composants communs, navigation, adaptatif, accessibilité — 0.29.0.

Faits observés (mesurés dans le code ou par Playwright) / déductions / recommandations sont séparés.

## Synthèse

Le site est cohérent (Fluent 2, jetons CSS centralisés). Android avait un **socle différent** du site, qui faisait paraître chaque écran « pas pareil »
même après l'alignement écran par écran : thème Material 3 Expressive (rayons de 8 à 36 dp), boutons en pilule, 226 couleurs écrites en dur,
dates à taper. Ces causes racines sont corrigées au niveau du thème et des composants communs, sans toucher à la logique des écrans.

## Constats prioritaires

| Id | Gravité | Écrans / supports | Constat (fait) | Impact | Cause racine | Correction | Statut |
|---|---|---|---|---|---|---|---|
| S1 | P1 | Android, tous | `Theme.kt` : formes 8/14/20/28/36 dp « Material 3 Expressive » ; site 2/4/6/8 px | Aspect différent du site partout | Thème global | `Formes` (jetons Fluent) + `MaterialTheme.shapes` = 4/8 | ✅ 0.29.0 |
| S2 | P1 | Android, tous | 206 boutons Material 3 en pilule ; site rectangulaire 4 px | idem | Forme par défaut non thématisable de Material 3 | `Boutons.kt` : enveloppes de même nom, prioritaires sur l'import `material3.*` ; bytecode vérifié (0 appel direct) | ✅ |
| S3 | P1 | Android, tous | 226 `Color(0xFF…)` en dur, dont 49 × #EFEDEC, 17 × #410002 | Dérives de couleur, maintenance | Pas de jetons au-delà de 14 couleurs | 15 jetons ajoutés (noms des variables CSS) ; 144 remplacements de même valeur ; 82 restants = couleurs calculées ou voiles de la bannière | ✅ (reste documenté) |
| S4 | P1 | Android, 19 formulaires | Dates saisies « JJ/MM/AAAA » au clavier ; site : sélecteur | Saisie lente, erreurs | Pas de champ de date commun | `ChampDate` : saisie + calendrier, même valeur texte | ✅ |
| S5 | P1 | Android | Suppression lancée depuis une feuille : « Annuler » perdu, liste non rechargée | Données qui « ne s'en vont pas » | Coroutine liée à la feuille | `Annulation.portee` + `onSupprimer` porté par l'écran | ✅ 0.28.0 |
| S6 | P2 | Site, téléphone | Zones tactiles : points de bannière 6×6, cases du rapprochement 20×20, barres du calendrier 43×6 | Cible difficile au doigt (WCAG 2.5.8 : 24 px) | Taille visuelle = taille tactile | Zone de 24 px autour du point (visuel inchangé), cases 24 px, barres non tactiles (on touche la case du jour) | ✅ |
| S7 | P2 | Android | Points de la bannière 6 dp cliquables, sans nom | idem + lecteur d'écran muet | idem | Zone 24 dp + « Annonce n sur N » | ✅ |
| S8 | P2 | Site | Opération payant une demande dont le justificatif est sur la demande : « Sans pièce » à tort (liste et journal PDF) | Fausse alerte de contrôle | Pièce cherchée seulement par `transaction_id` | Pièce de la demande prise en compte (site et journal PDF Android) | ✅ |
| S9 | P2 | Site, accueil | « N demandes à payer » compte celles que la personne a validées (elle ne peut pas les payer) | Tâche impossible affichée | Séparation des tâches non appliquée au compteur | Exclues, comme Android | ✅ |
| S10 | P2 | Android | Contour des champs : avec `--bord` (#D9D3D0) le contraste serait 1,4:1 | Champs invisibles pour malvoyants | Le site compense par un trait foncé sous le champ | `outline = BordFort` (≥ 3:1) | ✅ |
| S12 | P1 | Site et Android, Paramètres | Espace à part : en-tête et onglets cachés, fond bleuté, bannière orange en dégradé, icônes de 5 couleurs, bouton Fermer rond ; rappel « Mise en route » affiché par-dessus | Rupture avec le reste de l'application (« très disruptif », propriétaire) | Choix de conception antérieur | Page ordinaire, menu sobre, rappel jamais dans les Paramètres | ✅ 0.30.0 |
| S11 | P3 | Android | 331 tailles de police littérales | Maintenance | Pas de rôles typographiques nommés | Déjà sur l'échelle du site (14/13/12 = 83 %) : pas de refonte mécanique ; règle documentée | Documenté |

## Navigation et structure

- Même barre d'onglets au téléphone (4 entrées + Plus, même calcul par droits), Paramètres seulement par la roue dentée : **conforme**.
- Doublons (règle 11) : « Ajouter un rendez-vous » de la liste vide retiré des deux côtés (0.28.0) ; celui du détail d'un jour gardé (il préremplit la date).
- Écarts assumés : Plus = feuille (site) / écran (Android) ; page du lien personnel = web seulement.

## Accessibilité (site, scan Playwright : 13 pages × trésorier/adhérent × téléphone/ordinateur)

- 0 bouton ou lien sans nom accessible, 0 image sans `alt`, 0 champ sans étiquette.
- Zones tactiles < 24 px : 31 → 0 réellement tactiles (les 4 barres signalées ne reçoivent plus le toucher).
- Android : tous les `IconButton` ont une description ; 2 icônes sans description sont décoratives.

## Non vérifié

- Rendu Android sur un appareil réel ou un émulateur (aucun dans l'environnement) : formes, boutons et calendrier vérifiés à la compilation et dans le bytecode seulement.
- Lecteurs d'écran (TalkBack, NVDA), mode sombre (le produit est en thème clair uniquement : `color-scheme: light`).

## Choix des colonnes exportées — 18 octobre 2026 (version 0.39.0)

| Constat (retour utilisateur) | Correction |
|---|---|
| Nationalité absente du PDF regroupé par nationalité (colonne retirée d'office) | Plus aucune colonne retirée d'office ; l'utilisateur coche les colonnes |
| Colonnes fixées par le programme | Feuille d'export avec puces de colonnes, Tout / Par défaut, choix gardé sur l'appareil |
| Mots coupés au milieu (« Présiden-t », « 01/09/20-25 ») avec 10 colonnes en A4 portrait | À l'italienne au-delà de 6 colonnes, coupure aux espaces seulement, dates insécables |

## Contrôle des en-têtes et exports — 18 octobre 2026 (version 0.38.0)

| Constat | Correction |
|---|---|
| Export des membres sans lien avec les filtres et le tri affichés | Export selon la vue, rappel de la vue dans le titre du document |
| Colonnes du PDF décalées d'une section à l'autre | Largeurs fixes (`largeurs`), colonne de regroupement retirée |
| Résumé tantôt à droite, tantôt dessous ; bouton Exporter caché dans « ⋯ » (Membres) | En-tête commun : résumé toujours dessous, Exporter toujours dans l'en-tête |
| Boutons « Nouveau… » dans l'en-tête au téléphone (Tiers, Communiqués, Nouvelle collecte) en plus du + | + rond seul (règle 11) |
| Rapprochement et Rapports hors du modèle d'en-tête | Passés en `.page-titre` |

## Contrôle des ajouts récents — 18 octobre 2026 (version 0.37.0)

Signalé par le propriétaire sur son téléphone : liste des membres illisible, fiche qui élargit l'écran, « boutons ajoutés en désordre ». Reproduit en démonstration après ajout de valeurs longues (le balayage passait faute de données longues).

| Id | Gravité | Écran | Constat (fait) | Cause racine | Correction | Statut |
|---|---|---|---|---|---|---|
| R1 | P1 | Membres (site) | Profession longue qui sort de la carte ; puce « Trésorier » posée sur le texte | `.corps` sans `min-width:0` sur ses enfants : pas de points de suspension | Ligne réduite à nom + pupitre, enfants de `.corps` bornés, puces `flex:none` | ✅ |
| R2 | P1 | Fiche du membre (site) | Page élargie à 501 px sur un écran de 412 px (e-mail long) | Valeur de `.ligne-detail` sans retour à la ligne | `overflow-wrap:anywhere`, libellé fixe | ✅ |
| R3 | P1 | Fiche du membre (Android) | Libellé écrasé par une valeur longue | Poids donné au libellé au lieu de la valeur | Valeur `weight(1f)`, alignée à droite, sur plusieurs lignes | ✅ |
| R4 | P2 | Pièces jointes (site) | Champ natif « Choose File / No file chosen » (en anglais) et bouton Scanner d'un autre style dessous | Ajout posé sous le champ sans composant commun | Composant `.choix-piece` ↔ `ChoixPiece` : deux boutons égaux + nom du fichier | ✅ |
| R5 | P2 | Pièces jointes (Android) | Deux boutons pleine largeur empilés de styles différents | idem | idem | ✅ |
| R6 | P2 | Rapprochement | Nom du relevé affiché deux fois (puce + champ) | Doublon après l'ajout du composant | Puce retirée, nom dans le composant (gardé après redessin) | ✅ |
| R7 | P2 | Contrôle qualité | `a7.mjs` ne voyait pas le texte hors carte | Démo sans valeurs longues, pas de contrôle « hors carte » | Valeurs longues dans `mock.js` / `Demo.kt`, contrôle `horsCarte` (vérifié : signale l'ancienne version, 0 sur la nouvelle) | ✅ |
