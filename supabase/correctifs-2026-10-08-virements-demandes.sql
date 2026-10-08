-- =====================================================================
-- 11. Virement interne caisse/banque et demandes enrichies (8 octobre 2026)
-- Ajouts seulement : aucune donnée existante n'est modifiée.
-- =====================================================================

-- ---------- Virement interne : dépôt ou retrait d'espèces, virement entre comptes ----------
-- Deux écritures liées par le même identifiant « virement » : une sortie du compte de départ,
-- une entrée sur le compte d'arrivée. Elles comptent dans les soldes et le rapprochement,
-- jamais dans les recettes, les dépenses, le résultat ni le budget.
alter table public.categories add column if not exists interne boolean not null default false;
insert into public.categories(nom, sens, interne) values ('Virement interne', 'recette', true), ('Virement interne', 'depense', true)
on conflict (nom, sens) do update set interne = true;
alter table public.transactions add column if not exists virement uuid;
create index if not exists transactions_virement_idx on public.transactions(virement);

create or replace function public.virement_interne(p_date date, p_source uuid, p_dest uuid, p_montant numeric, p_libelle text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v uuid := gen_random_uuid(); s accounts; d accounts; lib text; m mode_paiement;
begin
  if not a_droit('saisir_ecritures') then raise exception 'Droit « saisir les écritures » requis'; end if;
  if p_source = p_dest then raise exception 'Choisissez deux comptes différents'; end if;
  if p_montant is null or p_montant <= 0 then raise exception 'Le montant doit être positif'; end if;
  select * into s from accounts where id = p_source and actif;
  select * into d from accounts where id = p_dest and actif;
  if s.id is null or d.id is null then raise exception 'Compte introuvable ou inactif'; end if;
  lib := coalesce(nullif(trim(p_libelle), ''),
    case when s.type = 'caisse' and d.type = 'banque' then 'Dépôt d''espèces à la banque'
         when s.type = 'banque' and d.type = 'caisse' then 'Retrait d''espèces pour la caisse'
         else 'Virement entre comptes' end);
  m := case when s.type = 'caisse' or d.type = 'caisse' then 'especes' else 'virement' end;
  insert into transactions(date_op, account_id, sens, montant, category_id, libelle, mode, virement, created_by) values
    (p_date, p_source, 'depense', p_montant, (select id from categories where interne and sens = 'depense' limit 1), left(lib, 120), m, v, auth.uid()),
    (p_date, p_dest,   'recette', p_montant, (select id from categories where interne and sens = 'recette' limit 1), left(lib, 120), m, v, auth.uid());
  return v;
end $$;

-- Annulation d'un virement : contre-passation des deux écritures d'un coup, avec motif
create or replace function public.annuler_virement(p_virement uuid, p_motif text)
returns void language plpgsql security definer set search_path = public as $$
declare t transactions; n int := 0;
begin
  if not a_droit('saisir_ecritures') then raise exception 'Droit « saisir les écritures » requis'; end if;
  if coalesce(trim(p_motif), '') = '' then raise exception 'Motif obligatoire'; end if;
  if exists (select 1 from transactions where virement = p_virement and rapproche) then
    raise exception 'Virement rapproché : période verrouillée, annulation impossible'; end if;
  if exists (select 1 from transactions c join transactions o on o.id = c.contrepasse_de where o.virement = p_virement) then
    raise exception 'Ce virement est déjà annulé'; end if;
  for t in select * from transactions where virement = p_virement and contrepasse_de is null loop
    insert into transactions(date_op, account_id, sens, montant, category_id, libelle, mode, virement, contrepasse_de, created_by)
    values (current_date, t.account_id, t.sens, -t.montant, t.category_id,
            left('Contre-passation : ' || t.libelle, 100) || ' · motif : ' || left(trim(p_motif), 60), t.mode, p_virement, t.id, auth.uid());
    n := n + 1;
  end loop;
  if n = 0 then raise exception 'Virement introuvable'; end if;
end $$;

revoke execute on function public.virement_interne(date, uuid, uuid, numeric, text), public.annuler_virement(uuid, text) from public, anon;
grant execute on function public.virement_interne(date, uuid, uuid, numeric, text), public.annuler_virement(uuid, text) to authenticated;

-- ---------- Demandes : justification, date souhaitée, devis ----------
alter table public.expense_requests add column if not exists justification text;
alter table public.expense_requests add column if not exists date_souhaitee date;
alter table public.attachments add column if not exists nature text not null default 'justificatif';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'attachments_nature_check') then
    alter table public.attachments add constraint attachments_nature_check check (nature in ('justificatif', 'devis'));
  end if;
end $$;

-- Montant à partir duquel une demande de dépense doit être justifiée (modifiable dans Paramètres)
insert into public.settings(cle, valeur, description) values
 ('seuil_justification', 100, 'Montant à partir duquel une demande de dépense doit être justifiée')
on conflict (cle) do nothing;

-- Un devis ne remplace jamais le justificatif : seules les pièces « justificatif » clôturent une demande
create or replace function public.check_transition() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.statut = old.statut then return new; end if;
  if old.statut = 'soumise' and new.statut in ('validee','refusee') then
    if not a_droit('valider_depenses') then raise exception 'Droit « valider les dépenses » requis'; end if;
    if new.statut = 'validee' then
      if new.signature_path is null then raise exception 'Signature obligatoire'; end if;
      new.validee_par := auth.uid(); new.validee_le := now();
      if old.regularisation then
        new.statut := case when exists (select 1 from attachments a join transactions t on t.id = a.transaction_id
                                          where t.request_id = old.id and a.nature = 'justificatif')
                           then 'justifiee'::statut_demande else 'payee'::statut_demande end;
        new.payee_le := coalesce(old.payee_le, now());
      end if;
    end if;
  elsif old.statut = 'validee' and new.statut = 'payee' then
    if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
    if old.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
    new.payee_par := auth.uid(); new.payee_le := now();
  elsif old.statut = 'payee' and new.statut = 'justifiee' then
    if not (a_droit('saisir_ecritures') or a_droit('payer_depenses') or old.demandeur = auth.uid()) then
      raise exception 'Le justificatif est déposé par le demandeur ou par la personne qui paie';
    end if;
    if not exists (select 1 from attachments a where a.request_id = old.id and a.nature = 'justificatif')
       and not exists (select 1 from attachments a join transactions t on t.id = a.transaction_id where t.request_id = old.id and a.nature = 'justificatif')
    then raise exception 'Justificatif manquant'; end if;
  elsif old.statut = 'soumise' and new.statut = 'annulee' then
    if old.demandeur <> auth.uid() then raise exception 'Seul le demandeur annule sa demande'; end if;
  else
    raise exception 'Changement de statut non autorisé';
  end if;
  return new;
end $$;
revoke execute on function public.check_transition() from public, anon, authenticated;
