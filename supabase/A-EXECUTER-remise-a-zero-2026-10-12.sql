-- ============================================================================
-- REMISE À ZÉRO DES DONNÉES DE TEST — à exécuter UNE FOIS par le trésorier
-- Supabase › SQL Editor › New query › coller tout ce fichier › Run
--
-- AVANT : Paramètres › Données › Sauvegarde › « Télécharger la sauvegarde »
--         (on ne peut pas revenir en arrière après ce script).
--
-- CE QUI EST VIDÉ (données de test) :
--   écritures (opérations, contre-passations, virements) et leurs pièces jointes,
--   rapprochements, demandes de dépense, budgets, collectes / participations,
--   matériel et son historique, tiers, communiqués et réponses aux sondages,
--   notifications en attente, corbeille, exercices.
--
-- CE QUI EST GARDÉ (paramétrage réel) :
--   l'association (nom, bannière, réglages), les comptes (Banque, Caisse) et leur
--   solde initial, les catégories, les rôles et droits, les comptes utilisateurs,
--   les membres et leurs liens personnels, la grille des cotisations dues,
--   le planning (messes, répétitions, concerts…), les abonnements aux notifications,
--   le journal d'audit (il garde la trace de cette remise à zéro).
--
-- Pour vider AUSSI le planning ou la grille des cotisations : retirer les deux
-- tirets « -- » devant les lignes marquées (OPTION) plus bas.
--
-- Les fichiers déjà déposés (photos de matériel, relevés, justificatifs) restent
-- dans le stockage sans être affichés ; pour libérer la place : Storage › seau
-- (justificatifs, releves, signatures, photos/materiel) › tout sélectionner › Delete.
-- ============================================================================

begin;

-- Garde-fou : uniquement sur la base de l'association
do $$
begin
  if not exists (select 1 from public.organisation where nom ilike '%tous pays%') then
    raise exception 'Base inattendue : script arrêté, rien n''a été supprimé.';
  end if;
end $$;

-- Les écritures rapprochées ou d'un exercice clôturé sont protégées contre la
-- suppression (c'est voulu en usage réel). On lève la protection le temps du
-- script seulement ; elle est remise en place juste après, dans la même transaction.
alter table public.transactions disable trigger trg_lock_reconciled;
alter table public.transactions disable trigger trg_lock_exercice;

delete from public.attachments;
delete from public.materiel_mouvements;
delete from public.materiel;
delete from public.transactions;
delete from public.reconciliations;
delete from public.expense_requests;
delete from public.budgets;
delete from public.collecte_membres;
delete from public.collectes;
delete from public.tiers;
delete from public.reponses_sondage;
delete from public.communiques;
delete from public.notifications_push;
delete from public.corbeille;
delete from public.exercices;
-- (OPTION) vider aussi le planning :
-- delete from public.projects;
-- (OPTION) vider aussi la grille des cotisations dues :
-- delete from public.cotisations;

alter table public.transactions enable trigger trg_lock_reconciled;
alter table public.transactions enable trigger trg_lock_exercice;

-- Les pastilles « nouveautés » repartent de maintenant
update public.profiles set vus = '{}'::jsonb;

-- Contrôle : tout doit afficher 0 (sauf les lignes « gardé »)
select 'écritures' as donnees, count(*) as reste from public.transactions
union all select 'demandes', count(*) from public.expense_requests
union all select 'rapprochements', count(*) from public.reconciliations
union all select 'matériel', count(*) from public.materiel
union all select 'collectes', count(*) from public.collectes
union all select 'tiers', count(*) from public.tiers
union all select 'communiqués', count(*) from public.communiques
union all select 'corbeille', count(*) from public.corbeille
union all select 'exercices', count(*) from public.exercices
union all select 'gardé : membres', count(*) from public.members
union all select 'gardé : comptes', count(*) from public.accounts
union all select 'gardé : catégories', count(*) from public.categories
union all select 'gardé : planning', count(*) from public.projects;

commit;
