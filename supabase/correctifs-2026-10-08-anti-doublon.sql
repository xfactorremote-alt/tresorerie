-- =====================================================================
-- 12. Anti-doublon (8 octobre 2026) : une même saisie envoyée deux fois
-- (double appui, réseau lent) est refusée par la base pendant 2 minutes.
-- Une saisie identique volontaire reste possible après ce délai.
-- =====================================================================
create or replace function public.anti_doublon() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_table_name = 'transactions' then
    if new.contrepasse_de is null and new.virement is null and exists (
      select 1 from transactions t
      where t.created_by is not distinct from new.created_by and t.date_op = new.date_op and t.account_id = new.account_id
        and t.sens = new.sens and t.montant = new.montant and t.libelle = new.libelle
        and t.member_id is not distinct from new.member_id and t.tiers_id is not distinct from new.tiers_id
        and t.created_at > now() - interval '2 minutes') then
      raise exception 'Opération déjà enregistrée il y a moins de 2 minutes (doublon évité)';
    end if;
  elsif tg_table_name = 'expense_requests' then
    if exists (
      select 1 from expense_requests r
      where r.demandeur = new.demandeur and r.objet = new.objet and r.montant = new.montant
        and r.created_at > now() - interval '2 minutes') then
      raise exception 'Demande déjà envoyée il y a moins de 2 minutes (doublon évité)';
    end if;
  end if;
  return new;
end $$;
revoke execute on function public.anti_doublon() from public, anon, authenticated;
drop trigger if exists trg_anti_doublon on public.transactions;
create trigger trg_anti_doublon before insert on public.transactions for each row execute function public.anti_doublon();
drop trigger if exists trg_anti_doublon on public.expense_requests;
create trigger trg_anti_doublon before insert on public.expense_requests for each row execute function public.anti_doublon();

-- Paiement d'une demande inexistante : message clair (avant : erreur technique de montant manquant)
create or replace function public.payer_demande(p_id uuid, p_compte uuid, p_mode mode_paiement, p_date date)
returns uuid language plpgsql security definer set search_path = public as $$
declare r expense_requests; t_id uuid;
begin
  if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
  select * into r from expense_requests where id = p_id for update;
  if r.id is null then raise exception 'Demande introuvable'; end if;
  if r.statut <> 'validee' then raise exception 'Demande non validée'; end if;
  if r.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
  insert into transactions(date_op, account_id, sens, montant, category_id, project_id, libelle, mode, request_id, created_by)
  values (p_date, p_compte, 'depense', r.montant, r.category_id, r.project_id, r.objet, p_mode, r.id, auth.uid())
  returning id into t_id;
  update expense_requests set statut = 'payee', account_id = p_compte where id = p_id;
  return t_id;
end $$;
