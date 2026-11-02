-- =====================================================================
-- Correctifs de sécurité du 2 novembre 2026
-- Failles confirmées sur la base de production par des essais en transaction annulée :
--  1. une demande de dépense déjà signée pouvait voir son montant, son objet ou sa catégorie
--     modifiés par son demandeur (la signature restait attachée au nouveau montant) ;
--  2. une opération enregistrée pouvait être réécrite (montant, date, compte…) sans contre-passation ;
--  3. des fonctions de déclencheur étaient appelables par tout le monde, y compris sans connexion ;
--  4. tout membre ayant le droit « demander une dépense » lisait toutes les pièces justificatives.
-- =====================================================================

-- 1. Demande de dépense : seul le changement d'état (et ce qui l'accompagne) passe par une mise à jour.
--    Le contenu (objet, montant, catégorie, activité, demandeur…) ne change plus jamais après la création.
--    Les fonctions de la base (paiement, justificatif) passent par les mêmes règles.
create or replace function public.garde_demande() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  modifiables constant text[] := array['statut', 'motif_refus', 'signature_path', 'signature_hash', 'account_id'];
begin
  if (to_jsonb(new) - modifiables) is distinct from (to_jsonb(old) - modifiables) then
    raise exception 'Une demande enregistrée ne se modifie pas : annulez-la et faites-en une nouvelle';
  end if;
  if new.statut = old.statut and (to_jsonb(new) is distinct from to_jsonb(old)) then
    raise exception 'Demande non modifiable sans changement d''état';
  end if;
  return new;
end $$;
-- Nom choisi pour passer avant trg_check_transition (ordre alphabétique des déclencheurs)
create or replace trigger trg_a_garde_demande before update on public.expense_requests
  for each row execute function public.garde_demande();

-- 2. Opération : une écriture enregistrée ne change plus ; seuls le rapprochement et le lien vers
--    une demande (posé par la base) évoluent. Une erreur se corrige par contre-passation ou corbeille.
create or replace function public.garde_ecriture() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  modifiables constant text[] := array['rapproche', 'date_rapprochement', 'reconciliation_id', 'request_id'];
begin
  if (to_jsonb(new) - modifiables) is distinct from (to_jsonb(old) - modifiables) then
    raise exception 'Une opération enregistrée ne se modifie pas : corrigez-la par contre-passation';
  end if;
  if old.request_id is not null and new.request_id is distinct from old.request_id then
    raise exception 'Lien avec la demande de dépense non modifiable';
  end if;
  return new;
end $$;
create or replace trigger trg_a_garde_ecriture before update on public.transactions
  for each row execute function public.garde_ecriture();

-- 3. Fonctions de déclencheur : jamais appelables directement par l'API
revoke execute on function
  public.check_exercice(), public.check_paiement(), public.check_rapprochement(), public.libelle_corbeille(),
  public.lock_exercice(), public.notif_collecte(), public.notif_collecte_membre(), public.notif_communique(),
  public.notif_rendez_vous(), public.garde_demande(), public.garde_ecriture()
  from public, anon, authenticated;

-- Fonctions d'usage connecté seulement : retirées des visiteurs sans compte
-- (situation_par_lien, abonner_push, desabonner_push, cle_push, repondre_sondage, resultats_sondage
--  et mes_reponses restent ouvertes : la page du lien personnel en a besoin et elles vérifient le jeton)
revoke execute on function
  public.supprimer(text, uuid, text), public.restaurer(uuid), public.droit_suppression(text),
  public.enregistrer_ma_fiche(text, text, integer, integer, text, text, boolean),
  public.marquer_vu(text), public.mes_nouveautes(), public.mot_de_passe_definitif()
  from public, anon;
grant execute on function
  public.supprimer(text, uuid, text), public.restaurer(uuid), public.droit_suppression(text),
  public.enregistrer_ma_fiche(text, text, integer, integer, text, text, boolean),
  public.marquer_vu(text), public.mes_nouveautes(), public.mot_de_passe_definitif()
  to authenticated;
-- a_droit, est_connecte et exercice_clos restent appelables par tous : les règles d'accès les évaluent
-- aussi pour un visiteur sans compte (elles répondent alors « non »).

-- 4. Pièces justificatives et signatures : les finances, la validation, le paiement,
--    et chacun pour les fichiers qu'il a lui-même déposés (devis, justificatif de sa demande)
alter policy "pieces lecture" on storage.objects using (
  bucket_id in ('justificatifs', 'signatures')
  and (a_droit('consulter_finances') or a_droit('valider_depenses') or a_droit('payer_depenses') or owner = auth.uid()));

-- 5. Chemin de recherche figé (avertissement du conseiller de sécurité)
alter function public.date_fr set search_path = public;
alter function public.titre_notification set search_path = public;
