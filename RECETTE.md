# Recette technique sur données fictives (8 octobre 2026)

Grille de la page 17 de l'audit du 7 octobre 2026. Un contrôle non effectué reste « non validé ».

Moyens utilisés :
- **Base** : PostgreSQL 16 local avec le `schema.sql` complet (sections 1 à 12), et les rôles réels (trésorier, président, bureau, adhérent) testés avec les règles d'accès actives.
- **Site** : mode démonstration piloté par navigateur automatique (Chromium), sans erreur JavaScript sur tous les parcours.
- **Application Android** : 9 tests automatiques des règles (`./gradlew :composeApp:testDebugUnitTest`) et compilation de l’APK 0.13.0.

| Scénario | Résultat attendu | Résultat | Preuve |
|---|---|---|---|
| Dépense payée puis annulée | Montants cohérents ; demande avec état actuel explicite | **Validé** | Demande « Opération annulée », sort de « Justificatif attendu » et des retards (site, Android, vue `v_justificatifs_en_retard`) |
| Dépense refusée après paiement | Traitement explicite ; aucun remboursement supposé | **Validé** | État « Refusée · à régulariser » jusqu'au choix « erreur de saisie » ou « remboursement reçu » (date, compte, mode) |
| Cotisation partielle puis complétée | Reste dû exact ; partiel puis réglé | **Validé** | Base : 10 € sur 20 € → « partiel », +10 € → « réglé » |
| Participation encaissée puis contre-passée | Montant net et contributeurs cohérents | **Validé** | 20 € annulés + 5 € → 5 €, 1 contributeur ; en production « achat polo » passe de 1 à 0 contributeur |
| Même demande envoyée deux fois | Pas de doublon ; contrôle côté serveur | **Validé sur la base de test, en attente en production** | Boutons bloqués pendant l'envoi (site, Android) ; la base refuse une saisie identique dans les 2 minutes. Fichier `correctifs-2026-10-08-anti-doublon.sql` à exécuter |
| Membre, bureau et trésorier | Chaque rôle ne voit et ne modifie que ce qui est autorisé | **Validé** | Adhérent : 0 opération, 0 solde, saisie refusée. Bureau : demande acceptée, saisie et validation refusées. Président : valide, ne paie pas. Trésorier : paie |
| Rapport PDF et export Excel | Fichiers ouvrables, totaux identiques | **Validé en démonstration** | Les 16 combinaisons document × format produisent un document ou un fichier ; virements internes exclus des totaux |
| Restauration de sauvegarde | Données restaurées sur une base distincte | **Validé pour les données** | `outils/restaurer-sauvegarde.mjs` : restauration complète sur une base vierge, nombres de lignes, soldes et cotisations identiques. Les pièces (fichiers) restent dans Supabase Storage ou dans le ZIP annuel |
| Téléphone et clavier | Actions atteignables, focus visible | **Validé pour le site** | Mise en page vérifiée à 360 px (recherche et filtres sur une ligne, mois sur deux lignes). Android : pas d'émulateur disponible, à tester sur un téléphone |

## Points restant à valider par une personne

1. **Application Android sur un vrai téléphone** : installer l’APK 0.13.0 et refaire les parcours (virement, régularisation, filtres, export).
2. **Export réel** depuis le site en ligne : ouvrir un PDF et un Excel du journal d'octobre et comparer les totaux à l'écran Opérations.
3. **Restauration sur Supabase** : la commande `set local session_replication_role = replica` du script de restauration doit être acceptée par l'éditeur SQL de Supabase (à confirmer lors d'un premier essai, sur un projet de test).

## Décision de mise en service

Date de revue : ___________  Participants : ___________

Réserves restantes : ___________________________

Décision et responsable du suivi : ___________________________
