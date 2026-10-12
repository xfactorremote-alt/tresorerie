# Inventaire des écrans (site ↔ Android)

Relevé dans le code le 12 octobre 2026 (`docs/app.js` : `PAGES`, `pagesAutorisees` ; Android : `App.kt`, `Onglet`, `EcranPlus`).
Audit détaillé de chaque écran : `conception/audits/*.md`.

| Écran | Site (fonction, ancre) | Android (fonction) | Droits | Audit | État de parité |
|---|---|---|---|---|---|
| Connexion | `ecranConnexion` | `Connexion` (App.kt) | — | parametres.md | Identique ; mot de passe ≥ 8 seulement à la création |
| Accueil bureau | `pageTableau` `#tableau` | `EcranAccueil` (Ecrans.kt) | tous | operations.md | Identique (rubriques, boutons d'action, 2–3 colonnes) |
| Accueil membre | `pageTableauAdherent` / `vueMembre` | `EcranAdherent` (Ecrans3.kt) | adhérent | membres.md | Identique ; bannière, rubriques par genre |
| Page du lien personnel | `pageLien` `?m=…` | — (ouvre le navigateur) | lien | membres.md | Page web par nature (exception documentée) |
| Opérations | `pageEcritures` `#ecritures` | `EcranOperations` (Ecrans.kt) | consulter_finances, saisir_ecritures | operations.md | Identique |
| Demandes | `pageDepenses` `#depenses` | `EcranDepenses` (Ecrans2.kt) | demander/valider/payer_depenses | demandes.md | Identique |
| Rapprochement | `pageRapprochement` `#rapprochement` | `EcranRapprochement` (Ecrans2.kt) | rapprocher | demandes.md | Identique |
| Cotisations / Participations | `pageCotisations` `#cotisations` | `EcranCotisations` (Ecrans4.kt) | gerer_cotisations | cotisations.md | Identique ; « Dispensé » gris uni (Android) / hachuré (site) |
| Tiers | `pageTiers` `#tiers` | `EcranTiers` (Ecrans4.kt) | saisir_ecritures, gerer_cotisations | cotisations.md | Identique |
| Budget | `pageBudget` `#budget` | `EcranBudget` (Budget.kt) | gerer_budget | parametres.md | Identique |
| Planning | `pageActivites` `#activites` | `EcranPlanning` (Ecrans4.kt) | tous | planning.md | Identique |
| Matériel | `pageMateriel` `#materiel` | `EcranMateriel` (Materiel.kt) | gerer_materiel | materiel.md | Identique ; import photo galerie non redressée (Android) |
| Membres | `pageMembres` `#membres` | `EcranMembres` (Ecrans3.kt) | voir/gerer_membres | membres.md | Identique ; import Excel .xlsx : site seulement |
| Communiqués (bureau) | `pageCommuniques` `#communiques` | `ParamBanniere(enPage)` (Annonces.kt) | gerer_activites, administrer | parametres.md | Identique |
| Rapports | `pageRapports` `#rapports` | `EcranRapports` (Ecrans3.kt) | consulter_finances | membres.md | Identique (colonnes des exports alignées) |
| Paramètres (15 sous-pages) | `pageParametres` `#parametres` | `EcranParametres` (Ecrans3.kt) + `Param*` (Arrivee.kt) | selon section | parametres.md | Identique |
| Plus | feuille `#b-plus` | écran `EcranPlus` | — | membres.md | Feuille (site) / écran (Android) : même liste, même ordre |
| Nouveautés (cloche) | `feuilleNouveautes` | `FeuilleNouveautes` | — | — | Identique |
| Assistant de configuration / fiche d'arrivée | `assistant…` | `Arrivee.kt` | administrer / nouveau membre | — | Identique |

Plates-formes : site testé dans Chromium (téléphone Pixel 7 et 1280×900) ; Android compilé et testé par les tests unitaires,
**pas sur un appareil** (aucun émulateur dans l'environnement).
