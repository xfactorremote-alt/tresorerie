# Plateforme de trésorerie – Association (cadrage v4)

## 1. Architecture 100 % gratuite

| Brique | Choix | Pourquoi |
|---|---|---|
| Base + authentification + fichiers | Supabase (offre gratuite) | Postgres, comptes utilisateurs, stockage 1 Go, droits par rôle intégrés |
| Site web (bureau, adhérents) | Page web statique sur Cloudflare Pages ou GitHub Pages | Gratuit, sans serveur à gérer |
| Application trésorier | Android Kotlin Multiplatform (Compose Multiplatform) | Un seul code, extensible à iOS plus tard |
| Sauvegarde | Export automatique hebdomadaire vers Google Drive (sauvegarde des données + justificatifs) | Demande exprimée |
| Relances | Lien WhatsApp pré-rempli (wa.me) généré par l'application | L'API WhatsApp officielle est payante ; le lien est gratuit, l'envoi reste un clic du trésorier |

Limite à connaître : Supabase gratuit met le projet en pause après 7 jours sans activité et offre 1 Go de fichiers. D'où la compression obligatoire des photos (cible 150 à 300 Ko par pièce, soit plus de 3 000 justificatifs).

## 2. Rôles et droits

| Action | Trésorier | Président | Bureau | Adhérent |
|---|---|---|---|---|
| Saisir recettes/dépenses, cotisations | Oui | Non | Non | Non |
| Consulter indicateurs et tableaux | Oui | Oui | Oui | Non |
| Demander un décaissement | Oui | Oui | Oui (ses demandes) | Non |
| Valider (signature) une demande | Non | Oui | Non | Non |
| Payer une demande validée | Oui | Non | Non | Non |
| Joindre le justificatif | Oui (s'il a remis l'argent) | Oui (ses demandes) | Oui (ses demandes) | Non |
| Paramètres, seuils, membres, import | Oui | Non | Non | Non |
| Sa cotisation, planning, anniversaires du mois | Oui | Oui | Oui | Oui |

## 3. Circuit de décaissement

1. Demande (objet, montant, catégorie, projet) par le trésorier, le président ou un membre du bureau. Un membre du bureau ne voit que ses propres demandes.
2. Validation par le président sur le site : signature manuscrite à l'écran, enregistrée avec date, identité et empreinte SHA-256 du contenu.
3. Paiement : le trésorier enregistre la sortie ; la base refuse tout paiement d'une demande non validée.
4. Justificatif : déposé par la personne qui a demandé, ou par le trésorier s'il lui a remis l'argent directement, dans la semaine qui suit le paiement (délai modifiable). Passé ce délai, la demande est signalée « justificatif en retard » sur le tableau de bord, et le reste tant que la pièce n'est pas déposée.
5. Le président a le dernier mot : il valide ou refuse seul, y compris ses propres demandes, et en assume la responsabilité. Pas de second validateur.

## 4. Fonctionnalités par phase

**Phase 1 (socle, 2 à 3 semaines)** : schéma et droits, connexion, saisie recettes/dépenses, catégories, comptes caisse et banque, import des membres Excel/CSV, cotisations et statuts.
**Phase 2** : circuit de décaissement avec signature, dépôt de pièces compressées, budget par catégorie et projet avec alertes, planning des activités.
**Phase 3** : tableau de bord, rapprochement bancaire, exports Excel/PDF (synthèse AG, rapport périodique, état des ressources et emplois), sauvegarde Drive, relances WhatsApp.

## 4 bis. Soldes

Le tableau de bord affiche le solde total, le solde de la caisse et celui de la banque, calculés à partir des soldes de départ (saisis une fois dans les paramètres, hors résultat) et de toutes les écritures. Une erreur se corrige par contre-passation : écriture de correction en négatif, même catégorie, même compte, une seule fois par écriture.

## 5. Rapprochement (compte personnel, relevés PDF)

Le relevé de la période est obligatoire : sans pièce jointe (PDF ou photo pour la banque, PV de comptage pour la caisse), le rapprochement ne peut pas être terminé. Le trésorier pointe chaque écriture contre le relevé, l'application affiche l'écart, et le bouton « Terminer » reste bloqué tant que l'écart n'est pas nul et que le relevé n'est pas joint. Une fois terminé, la période est verrouillée : les écritures ne sont plus modifiables, toute correction passe par une contre-passation. Une lecture automatique du PDF pourra s'ajouter plus tard.

## 5 bis. Le trésorier administrateur

Il peut modifier : nom et logo de l'association, budgets (par catégorie et par activité), activités et leur visibilité pour les adhérents, membres (ajout, import CSV ou Excel), montant de la cotisation, seuils de validation, taux d'alerte du budget, accès (invitation par e-mail, rôle, désactivation). Deux garde-fous : le dernier trésorier actif ne peut pas être retiré, et le trésorier ne valide pas les décaissements (réservé au président) pour garder la séparation des tâches. Tous les changements sensibles sont inscrits au journal d'audit.

Exports prévus : écritures, membres et cotisations, budget (Excel), synthèse pour l'assemblée générale et rapports périodiques (PDF), sauvegarde complète vers Google Drive.

## 5 ter. Membres, photos et anniversaires

Un membre ne peut être créé que si ses données sont enregistrées : prénom, nom, jour et mois d'anniversaire obligatoires (pas d'année de naissance) ; profession, photo, WhatsApp et e-mail facultatifs (la photo est réduite à 400 px, environ 40 Ko). Le tableau de bord affiche les anniversaires du mois. Données personnelles : seuls le jour et le mois existent dans la base, uniquement pour les membres ayant donné leur accord (case « consentement anniversaire ») ; les adhérents voient le prénom et l'initiale du nom. Photos et dates de naissance sont à mentionner dans la note d'information aux membres.

## 6. Points de vigilance (usages du métier)

- Compte bancaire personnel : séparer strictement fonds de l'association et fonds personnels dans les écritures, et documenter par écrit que le compte est détenu pour l'association (risque en cas de décès ou litige). Recommander à terme un compte dédié.
- Dons : tenir un registre nominatif des donateurs ; ne jamais émettre de reçu fiscal sans statut y ouvrant droit.
- Espèces : compter la caisse régulièrement (PV de comptage à deux personnes) ; plafond de caisse à fixer.
- Séparation des tâches : celui qui paie n'est pas celui qui valide.
- Clôture annuelle : verrouiller l'exercice après l'AG pour interdire les modifications rétroactives.
- Données personnelles des membres : accès restreint, consentement, pas de numéros WhatsApp visibles des adhérents entre eux.
- Aucune suppression d'écriture : correction par contre-passation, avec journal d'audit.
- Sauvegardes : tester une restauration.

## 7. Avancement

Phases 1, 2 et 3 livrées le 3 octobre 2026 :
- base de données testée sur PostgreSQL 16 (circuit des dépenses, justificatifs, rapprochement atomique avec relevé obligatoire, planning) ;
- site complet testé en mode démonstration (dépenses signées, budget, activités, rapprochement, rapports PDF, exports, sauvegarde) ;
- application Android 0.2.0 compilée sur GitHub Actions (accueil, dépenses avec signature, écritures, cotisations, membres, budget, activités, rapprochement).

Restent à faire : sauvegarde automatique vers Google Drive (aujourd'hui manuelle, une fois par mois), signature d'une version Android définitive, création de la base Supabase réelle.
