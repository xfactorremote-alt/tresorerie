# Carte de navigation

## Destinations principales (barre d'onglets du téléphone, site et Android)

Les 4 premières destinations autorisées puis **Plus** (le reste) — même calcul des deux côtés :

| Rôle | Onglets |
|---|---|
| Trésorier / président | Accueil · Opérations · Demandes · Cotisations · Plus |
| Bureau | Accueil · Demandes · (selon droits) · Plus |
| Adhérent | Accueil · Planning |

Paramètres ne sont **jamais** dans la barre ni dans Plus : on y entre par la **roue dentée** de la bannière (et le bas du volet sur ordinateur).

## Destinations secondaires (Plus, dans cet ordre)

Budget · Planning · Communiqués · Matériel · Tiers · Membres · Rapprochement · Rapports — chacune seulement si le rôle y a droit (`pagesAutorisees` / `EcranPlus`).

## Grand écran

- Site ≥ 1200 px : volet à gauche toujours déplié avec toutes les destinations, Nouveautés et Paramètres en bas du volet.
- Site 640–1199 px : menu ☰ par-dessus, qui se referme seul (clic à côté, Échap, choix).
- Android : barre d'onglets ; contenus en deux colonnes au-delà de 840 dp.

## Relations parent → enfant

```
Accueil ── À traiter ──► Demandes (filtre À valider / À payer / À régulariser) ─► fiche de la demande
        ├─ Soldes ─────► Opérations (compte filtré) ─► détail d'opération
        └─ Cloche ─────► Nouveautés ─► écran concerné
Opérations ─► détail ─► contre-passation | demande liée | pièce
Demandes ──► fiche ──► Voir l'opération (Android : par-dessus ; site : page Opérations)
Planning ──► jour ──► rendez-vous ──► Voir le budget (Budget) | Voir les participations (Cotisations › Participations)
Membres ───► fiche du membre ──► Lien personnel | Fonction et accès | Cotisation (Cotisations)
Paramètres ► menu en cartes ──► sous-page ──► Fermer = retour à l'écran d'avant
```

## Retour et restauration

- Fermer une feuille : geste retour (Android), Échap ou clic à côté (site) ; la feuille ne garde rien en cours sans confirmation.
- Paramètres : « Fermer » ramène à l'écran d'où l'on vient (`avantParam` / `S.avantParam`).
- Choix gardés pendant la session : filtres de Demandes, Membres, Matériel, Cotisations ; vue du Planning gardée sur l'appareil.
- Liens profonds : site par ancre (`#depenses`…) et `?m=` pour le lien personnel ; Android par les notifications (ouverture de l'onglet concerné).

## Écarts assumés

- **Plus** : feuille sur le site, écran sur Android (même contenu, même ordre).
- **Voir l'opération** depuis une demande : Android l'ouvre par-dessus la fiche (à reporter sur le site, REFACTOR_PLAN lot 6).
