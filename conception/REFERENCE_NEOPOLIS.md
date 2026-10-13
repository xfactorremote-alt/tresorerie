# Rétro-ingénierie : simulateur Neopolis → Trésorerie JP Grenoble

Référence : https://neopolis.fr/simulateur (relevé le 13 octobre 2026, paramètres publicitaires retirés).
Projet cible : Trésorerie de l'association (site `docs/` + application Android), identité **conservée** (orange de l'association, Fluent 2).
On transpose la *grammaire* (parcours, composants, retours), jamais le contenu, la marque ni la palette.

Étiquettes : `OBSERVÉ` (lu dans le HTML/CSS ou mesuré dans le navigateur) · `DÉDUIT` · `ESTIMATION` · `NON OBSERVABLE — À DÉFINIR` · `RECOMMANDÉ`.

**Non observé** : la page de résultats (elle n'apparaît qu'après l'envoi du formulaire e-mail + téléphone ; nous ne l'avons pas envoyé pour ne pas créer de fausse demande chez Neopolis) — sa structure est connue par le HTML caché, pas son rendu réel. Les animations de transition entre étapes (pilotées par JavaScript) n'ont pas été mesurées.

Sources : HTML (29 Ko, Webflow `OBSERVÉ`), feuille `neopolis-landing.webflow.shared.*.min.css` (236 Ko, 1 990 règles dont 269 propres au simulateur `.sm-*` / `.np*`), script `np-sim74.js` (77 Ko), rendu Chromium à 390 / 820 / 1440 px et parcours des étapes 1 → 2.

---

## 1. Diagnostic global

- Page d'acquisition SaaS pour associations : promesse chiffrée (« Combien votre association peut-elle aller chercher ? »), puis **formulaire progressif** (une question à la fois) qui débouche sur un rapport. `OBSERVÉ`
- Langage visuel : **soft UI / glassmorphism** — surfaces blanches translucides (`#ffffff8c`) floutées (`backdrop-filter: saturate(1.85) blur(30px)`), grandes ombres diffuses, fond en dégradé pastel, pilules (`border-radius: 980px`). `OBSERVÉ`
- Langage UX : progression explicite (3 étapes numérotées), **identification par un registre public** (nom ou SIRET → carte de confirmation verte avec adresse et SIRET), choix en cartes radio à deux lignes, bouton principal désactivé tant que la réponse manque, messages d'erreur sous le champ. `OBSERVÉ`
- Verdict : hybride *soft UI* + *editorial* (titres serrés −0,035 em). Efficace pour convertir ; plusieurs partis pris ne conviennent pas à un outil de gestion quotidien (voir § 8). `DÉDUIT`

## 2. ADN visuel

| Rôle | Valeur | Où | Statut |
|---|---|---|---|
| Texte principal | `#1d1d1f` | titres, champs | OBSERVÉ |
| Texte secondaire | `#6e6e73` | chapô, étapes | OBSERVÉ |
| Corps / encre de marque | `#013a40` (vert très foncé) | `body` | OBSERVÉ |
| Accent | `#35b5a8` (survol `#2a9d90`) | boutons, pastilles d'étape, focus | OBSERVÉ |
| Erreur | `#ff5630` (bord), `#d1382a` (texte) | champs invalides | OBSERVÉ |
| Surfaces | `#ffffff8c` + bord `#fff9` | nav, cartes, étapes | OBSERVÉ |

| Sélecteur | Famille | Taille / interligne / approche | Statut |
|---|---|---|---|
| `.sm-h1` | Hanken Grotesk 700 | 46 px / 1,06 / −0,035 em | OBSERVÉ |
| `.sm-h2` | Hanken Grotesk 700 | 34 px / 1,1 / −0,03 em | OBSERVÉ |
| `.sm-lead` | DM Sans 400 | 18 px / 1,5 | OBSERVÉ |
| `.sm-lbl` | DM Sans 700 | 16 px | OBSERVÉ |
| `.sm-step` | DM Sans 600 | 13,5 px | OBSERVÉ |

Formes : nav et boutons 980 px (pilule), cartes 24 px (20 px mobile), champs 14 px, pastille d'étape ronde 20 px. Ombre type `0 12px 40px rgba(30,40,70,.14)` + reflet interne `inset 0 1px #fff`. `OBSERVÉ`

**À ne pas reprendre** : palette sarcelle, polices Hanken/DM Sans, pilules et glassmorphism (contraires à la règle 12 bis : Fluent 2, coins 4/8, boutons rectangulaires), chiffres marketing, bannière cookies, capture de prospects (e-mail + téléphone obligatoires avant le résultat).

**ADN retenu** : progression lisible, recherche dans le registre public avec carte de confirmation, cartes de choix avec rond de sélection, bouton principal grisé tant que l'obligatoire manque, message d'erreur sous le champ.

## 3. Jetons (référence → cible)

| Jeton | Neopolis | Trésorerie (conservé) |
|---|---|---|
| Accent | `#35b5a8` | `--primaire #C23E10` / `Couleurs.Orange` (contraste 5,3:1 sur blanc) |
| Texte 2 | `#6e6e73` | `--texte-3 #5A5350` / `Couleurs.Texte2` |
| Carte | 24 px, translucide | `--r-xgrand 8 px`, `--surface` opaque, `--ombre4` |
| Champ | 14 px, focus bord accent | `--r-moyen 4 px`, trait `--bord-fort`, focus trait primaire |
| Bouton | pilule 980 px, 15×34 | rectangulaire 4 px (`Boutons.kt`), 40 px au doigt |
| Carte de confirmation | vert pâle | `--bleu-clair` / `Couleurs.BleuClair` (couleur d'information du produit) |
| Barre de progression | — (pastilles numérotées) | 4 px, `--surface-2` + `--primaire` |
| Points de rupture | 479 / 767 / 991 / 1440 | 520 (assistant), 640, 840/1000, 1200 (inchangés) |

## 4. Architecture UI (composants transposés)

| Composant | Référence (OBSERVÉ) | Cible (RECOMMANDÉ → fait) |
|---|---|---|
| Progression | 3 pastilles « 1 Votre association… » | ≥ 521 px : 6 ronds avec **tous** les intitulés ; ≤ 520 px : « Étape n sur 6 · Intitulé » + barre (avant : intitulé tronqué « Associ… ») |
| Identification | champ « Nom ou SIRET », liste de propositions, « Aucune association trouvée / Essayez avec le SIRET ou le RNA », carte verte (bâtiment, adresse, SIRET) | champ « Retrouver l'association (facultatif) — Nom, SIRET ou RNA », liste, message vide, carte bleue (association, adresse, SIRET · RNA) qui **remplit** nom, date de création, adresse, code postal, ville, SIRET, RNA |
| Choix | `.sm-seg-b` : carte titre + sous-titre + rond 18 px | `.choix-carte` / carte Android : rond 18 px visible, rempli en orange quand choisi |
| Bouton principal | `.sm-go.sm-off` grisé tant que invalide | « Suivant » grisé tant que le nom est vide |
| Erreur | texte sous le champ, bord rouge | « Indiquez le nom de l'association. » sous le champ, `aria-invalid` |

États : survol des propositions (`--survol`), focus visible conservé, propositions ≥ 44 px de haut.

## 5. Architecture UX

- Pourquoi ça marche (référence) : chaque écran ne pose qu'une question ; le registre public évite la saisie et rassure (« c'est bien nous ») ; le bouton grisé dit ce qui manque. `DÉDUIT`
- Où ça échoue : résultat caché derrière e-mail + téléphone obligatoires (friction et méfiance) ; promesse chiffrée non vérifiable ; pilules translucides peu contrastées (`#919eab` sur blanc pour le bouton grisé : 2,7:1). `DÉDUIT`
- Pour la cible : la recherche est **facultative** (beaucoup de petites associations n'ont pas de SIRET, donc ne sont pas dans le registre — c'est le cas de JP Grenoble lors du relevé), on ne demande aucune donnée de contact, et chaque valeur trouvée reste modifiable. `RECOMMANDÉ`

## 6. Responsive

Référence : un seul vrai changement de mise en page (≤ 767 px : colonnes empilées, cartes 20 px). `OBSERVÉ`
Cible : téléphone ≤ 520 px → progression compacte, champs pleine largeur ; ≥ 521 px → ronds + intitulés ; carte de l'assistant 720 px max. Aucun débordement horizontal (vérifié 390 px et 1440 px).

## 7. Mouvement

| Animation | Référence | Classement | Décision |
|---|---|---|---|
| Survol bouton (−1 px + ombre) | `.sm-go:hover` | DÉCORATIVE | abandonnée (Fluent : changement de teinte) |
| Focus champ (bord accent 150 ms) | `.sm-input` | UTILE | conservée (trait primaire déjà en place) |
| Barre de progression | — | UTILE | ajoutée : largeur 300 ms, décélération Fluent, coupée par « réduire les animations » |
| Passage d'étape | JS non mesuré | NON OBSERVABLE | inchangé (entrée 8 px existante) |

## 8. Adaptation au projet

| Élément | Décision | Justification |
|---|---|---|
| Recherche par registre public + carte de confirmation | **Ajouté** | moins de saisie, moins d'erreurs sur SIRET/RNA/adresse (documents officiels) |
| Progression « Étape n sur N » + barre au téléphone | **Adapté** | l'intitulé était coupé à 390 px |
| Rond de sélection sur les cartes de choix | **Ajouté** | affordance de radio explicite |
| Bouton grisé + message sous le champ | **Adapté** | règle déjà présente sur Android, ajoutée au site ; message identique |
| Glassmorphism, pilules, dégradé pastel, polices | **Abandonné** | contraire au système Fluent 2 du produit |
| Capture e-mail/téléphone avant résultat | **Abandonné** | pas de sens dans un outil interne ; friction |
| Promesse chiffrée en tête | **Abandonné** | contenu marketing |

## 9–10. Implémentation

- Site : `chercherAssociation`, `brancherAnnuaire`, `ANNUAIRE_DEMO` (docs/app.js) ; styles `.annuaire*`, `.etape-courante`, `.barre-etapes`, rond des `.choix-carte`, `.erreur-champ` (docs/styles.css).
- Android : `Repo.chercherAssociation` (même URL, même lecture), `AssociationAnnuaire` (Models.kt), `Demo.annuaire` (mêmes données), `AnnuaireAssociation` et progression compacte dans `AssistantConfiguration` (Arrivee.kt), `casseTitre`.
- Annuaire : `https://recherche-entreprises.api.gouv.fr/search` (registre SIRENE, public, sans clé), filtre `est_association=true` pour une recherche par nom ; en démonstration, données fictives locales (aucun appel réseau).

## 11. QA

Fait : Playwright 390 × 844 et 1440 × 900 — nom vide → bouton grisé + message ; recherche sans résultat → message ; choix → nom, date, puis à l'étape 2 adresse, code postal, ville, SIRET, RNA remplis ; aucun débordement ; aucune erreur JS ; 144 vues du site sans erreur. Android : compilation, 25 tests dont `annuaireCommeLeSite`.
Restant : appel réel de l'annuaire depuis un téléphone (l'environnement de travail a été coupé par l'annuaire après quelques appels) ; rendu Android sur appareil.
