# Recette technique sur données fictives (8 octobre 2026)

Grille de la page 17 de l'audit du 7 octobre 2026. Un contrôle non effectué reste « non validé ».

Moyens utilisés :
- **Base** : PostgreSQL 16 local avec le `schema.sql` complet (sections 1 à 12), et les rôles réels (trésorier, président, bureau, adhérent) testés avec les règles d'accès actives.
- **Site** : mode démonstration piloté par navigateur automatique (Chromium), sans erreur JavaScript sur tous les parcours.
- **Application Android** : 16 tests automatiques des règles (`./gradlew :composeApp:testDebugUnitTest`) et compilation de l’APK 0.18.0.

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

| Suppression par erreur puis restauration | Rien n’est perdu ; soldes revenus à l’identique | **Validé sur la base de test et en démonstration** | Opération avec pièce supprimée : solde de la caisse 93 → 100 €, restaurée : 93 €, pièce revenue ; virement : les deux mouvements partent et reviennent ensemble ; membre avec opérations, opération rapprochée ou corrigée : refus motivé. En production : fonction `supprimer` installée le 10 octobre (contenu identique au dépôt, seules les fins de ligne Windows diffèrent) |
| Exercice clôturé | Opérations verrouillées, réouverture tracée | **Validé** | Saisie, modification et suppression refusées aux dates d’un exercice clôturé ; chevauchement refusé ; date et auteur de la clôture enregistrés |
| Nouveau membre | Sa fiche lui est demandée, rattachée par l’e-mail | **Validé sur la base de test et en démonstration** | Fiche existante rattachée par l’adresse e-mail ; sinon fiche créée ; prénom ou nom vide refusé |
| Page d’un membre (lien et compte) | Prochain rendez-vous visible sans faire défiler, sur téléphone, tablette en paysage et ordinateur | **Validé en démonstration** | 1366 × 768, 1024 × 768 et 390 × 844 : rendez-vous en tête, deux colonnes dès 900 px en paysage ; « Ajouter à mon agenda » produit un fichier .ics (rappel la veille) ; Android : agenda du téléphone. Base : identifiant du rendez-vous ajouté à `situation_par_lien` (empreinte vérifiée) |
| Fenêtres et rappels dosés | « Plus tard » respecté, rien d’intrusif | **Validé en démonstration** | Nouveau compte : fiche proposée une fois ; après « Plus tard », rien dans les Paramètres ni sur les autres pages ; à la connexion suivante, seulement un petit rappel « Remplir » ; la suivante, rien (une fois par semaine au plus) ; trésorier : plus de « Bien démarrer », un rappel « Mise en route » discret |
| Lien personnel court | Lien lisible, aperçu rassurant, anciens liens valables | **Validé sur la base de test, en démonstration et en production** | `?m=grace-k7qp2xyz9abc` ouvre la page ; l’ancien lien à 32 caractères aussi ; un code inconnu affiche « Lien inactif » ; 20 000 codes tirés sans doublon ; aperçu WhatsApp (titre et logo) par les balises Open Graph ; code ajouté au lien déjà existant en production |
| Communiqués et bannière | Annonces dans le bon ordre, droits respectés, corbeille | **Validé sur la base de test, en démonstration et en production** | Adhérent : publication refusée ; note « bureau seulement » invisible ; nouveauté comptée puis vue ; suppression et restauration ; bureau sans droit refusé ; ordre « Important » puis réglages ; « Jamais » retire le type |
| Temps réel et notifications | Écrans à jour sans recharger, alerte sonore et vibrante, compteur sur l’icône | **Validé en démonstration ; à vérifier sur deux appareils réels** | Publication de 17 tables en production ; écran redessiné sans chargement ni animation ; carillon, vibration et notification du système (site) ; notification Android avec son, vibration et nombre, vérification toutes les 15 minutes application fermée |
| Parcours complet du site | Aucune erreur, aucun débordement | **Validé** | 12 pages × 4 rôles × 3 tailles = 144 vues sans erreur JavaScript ni défilement horizontal |
| Nouveautés | Pastilles exactes selon le rôle | **Validé** | Président : demande à valider comptée ; bureau : ses propres saisies non comptées ; adhérent : rendez-vous et participations seulement ; vu → 0 |

## Points restant à valider par une personne

1. **Application Android sur un vrai téléphone** : installer l’APK 0.18.0 et refaire les parcours (virement, régularisation, filtres, export).
2. **Export réel** depuis le site en ligne : ouvrir un PDF et un Excel du journal d'octobre et comparer les totaux à l'écran Opérations.
3. **Restauration sur Supabase** : la commande `set local session_replication_role = replica` du script de restauration doit être acceptée par l'éditeur SQL de Supabase (à confirmer lors d'un premier essai, sur un projet de test).

## Décision de mise en service

Date de revue : ___________  Participants : ___________

Réserves restantes : ___________________________

Décision et responsable du suivi : ___________________________
