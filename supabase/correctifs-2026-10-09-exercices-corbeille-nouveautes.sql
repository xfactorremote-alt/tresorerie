-- =====================================================================
-- 13. Exercices, coordonnées de l'association, corbeille réversible, nouveautés, fiche du nouveau membre
-- (9 octobre 2026). Migration additive : aucune donnée existante n'est modifiée ni supprimée.
-- =====================================================================

-- ---------- 13.1 Association : coordonnées et première configuration ----------
alter table public.organisation
  add column if not exists sigle text,
  add column if not exists objet text,
  add column if not exists adresse text,
  add column if not exists code_postal text,
  add column if not exists ville text,
  add column if not exists email text,
  add column if not exists telephone text,
  add column if not exists site_web text,
  add column if not exists rna text,              -- numéro au répertoire national des associations (W…)
  add column if not exists siret text,
  add column if not exists date_creation date,
  add column if not exists configuree boolean not null default false;   -- assistant de configuration terminé

-- ---------- 13.2 Exercices comptables ----------
create table if not exists public.exercices (
  id uuid primary key default gen_random_uuid(),
  libelle text not null,
  debut date not null,
  fin date not null,
  cloture boolean not null default false,
  cloture_le timestamptz,
  cloture_par uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  check (fin > debut)
);
alter table public.exercices enable row level security;
drop policy if exists lecture on public.exercices;
create policy lecture on public.exercices for select using (public.est_connecte());
drop policy if exists admin on public.exercices;
create policy admin on public.exercices for all using (public.a_droit('administrer')) with check (public.a_droit('administrer'));
drop trigger if exists audit_exercices on public.exercices;
create trigger audit_exercices after insert or update or delete on public.exercices for each row execute function public.log_change();

-- Deux exercices ne se chevauchent pas ; un exercice clôturé ne change plus de dates
create or replace function public.check_exercice() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from exercices e where e.id <> new.id and e.debut <= new.fin and e.fin >= new.debut) then
    raise exception 'Cet exercice chevauche un exercice existant';
  end if;
  if tg_op = 'UPDATE' and old.cloture and new.cloture and (new.debut <> old.debut or new.fin <> old.fin) then
    raise exception 'Exercice clôturé : rouvrez-le pour changer ses dates';
  end if;
  if new.cloture and not coalesce(old.cloture, false) then new.cloture_le := now(); new.cloture_par := auth.uid(); end if;
  if not new.cloture then new.cloture_le := null; new.cloture_par := null; end if;
  return new;
end $$;
drop trigger if exists trg_check_exercice on public.exercices;
create trigger trg_check_exercice before insert or update on public.exercices for each row execute function public.check_exercice();

-- Premier exercice : l'année en cours (à partir du début d'exercice de l'association)
insert into public.exercices(libelle, debut, fin)
select 'Exercice ' || extract(year from o.exercice_debut)::int, o.exercice_debut, (o.exercice_debut + interval '1 year' - interval '1 day')::date
from public.organisation o
where not exists (select 1 from public.exercices);

-- Exercice clôturé : ses opérations ne s'ajoutent, ne se modifient et ne se suppriment plus
create or replace function public.exercice_clos(p_date date) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from exercices where cloture and p_date between debut and fin)
$$;
create or replace function public.lock_exercice() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op <> 'INSERT' and exercice_clos(old.date_op) then
    raise exception 'Exercice clôturé : opération verrouillée';
  end if;
  if tg_op <> 'DELETE' and exercice_clos(new.date_op) then
    raise exception 'Exercice clôturé : impossible d’enregistrer à cette date';
  end if;
  return coalesce(new, old);
end $$;
drop trigger if exists trg_lock_exercice on public.transactions;
create trigger trg_lock_exercice before insert or update or delete on public.transactions for each row execute function public.lock_exercice();

-- ---------- 13.3 Corbeille : suppression réversible ----------
-- Une suppression enregistre une copie complète (ligne + lignes rattachées) puis efface ;
-- « Restaurer » remet tout en place. Rien n'est perdu, tout est tracé (qui, quand, pourquoi).
create table if not exists public.corbeille (
  id uuid primary key default gen_random_uuid(),
  table_nom text not null,
  ligne_id uuid not null,
  libelle text not null,
  donnees jsonb not null,
  dependances jsonb not null default '[]',    -- [{table, donnees}] lignes effacées avec la principale
  liens jsonb not null default '[]',          -- [{table, id, colonne}] références remises à vide, rétablies à la restauration
  motif text,
  supprime_par uuid references public.profiles(id) default auth.uid(),
  supprime_le timestamptz not null default now(),
  restaure_par uuid references public.profiles(id),
  restaure_le timestamptz
);
alter table public.corbeille enable row level security;
drop policy if exists lecture on public.corbeille;
create policy lecture on public.corbeille for select using (
  public.est_connecte() and (supprime_par = auth.uid() or public.a_droit('administrer') or public.a_droit('consulter_finances')));

-- Droit nécessaire pour supprimer ou restaurer une ligne de chaque table
create or replace function public.droit_suppression(p_table text) returns boolean
language sql stable security definer set search_path = public as $$
  select case p_table
    when 'transactions' then a_droit('saisir_ecritures')
    when 'members' then a_droit('gerer_membres')
    when 'tiers' then a_droit('saisir_ecritures') or a_droit('gerer_cotisations')
    when 'projects' then a_droit('gerer_activites')
    when 'collectes' then a_droit('gerer_activites') or a_droit('gerer_cotisations')
    when 'materiel' then a_droit('gerer_materiel')
    when 'categories' then a_droit('administrer')
    when 'accounts' then a_droit('administrer')
    when 'budgets' then a_droit('gerer_budget')
    when 'expense_requests' then est_connecte()
    when 'exercices' then a_droit('administrer')
    else false end
$$;

create or replace function public.supprimer(p_table text, p_id uuid, p_motif text default null) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v jsonb; v_lib text; v_dep jsonb := '[]'; v_liens jsonb := '[]'; v_id uuid; r record; v_autre jsonb;
begin
  if not droit_suppression(p_table) then raise exception 'Droit insuffisant pour supprimer'; end if;
  execute format('select to_jsonb(t) from %I t where id = $1', p_table) into v using p_id;
  if v is null then raise exception 'Élément introuvable'; end if;

  if p_table = 'transactions' then
    if (v->>'reconciliation_id') is not null then raise exception 'Opération rapprochée : utilisez la contre-passation'; end if;
    if exercice_clos((v->>'date_op')::date) then raise exception 'Exercice clôturé : opération verrouillée'; end if;
    if exists (select 1 from transactions where contrepasse_de = p_id) then
      raise exception 'Cette opération a été corrigée : supprimez d’abord la correction';
    end if;
    if (v->>'request_id') is not null and (v->>'contrepasse_de') is null then
      raise exception 'Cette opération paie une demande de dépense : utilisez « Régulariser » ou la contre-passation';
    end if;
    v_lib := (v->>'libelle') || ' · ' || to_char((v->>'montant')::numeric, 'FM999G999G990D00') || ' €';
    -- Virement interne : les deux mouvements partent ensemble
    if (v->>'virement') is not null then
      for r in select to_jsonb(t) j from transactions t where t.virement = (v->>'virement')::uuid and t.id <> p_id loop
        if (r.j->>'reconciliation_id') is not null then raise exception 'Virement rapproché : utilisez « Annuler le virement »'; end if;
        if exists (select 1 from transactions where contrepasse_de = (r.j->>'id')::uuid) then raise exception 'Virement déjà annulé'; end if;
        v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'transactions', 'donnees', r.j));
      end loop;
      v_lib := 'Virement interne · ' || v_lib;
    end if;
    for r in select to_jsonb(a) j from attachments a where a.transaction_id = p_id or a.transaction_id in
        (select (d->'donnees'->>'id')::uuid from jsonb_array_elements(v_dep) d) loop
      v_dep := jsonb_build_array(jsonb_build_object('table', 'attachments', 'donnees', r.j)) || v_dep;
    end loop;
    for r in select id from materiel where transaction_id = p_id loop
      v_liens := v_liens || jsonb_build_array(jsonb_build_object('table', 'materiel', 'id', r.id, 'colonne', 'transaction_id', 'valeur', p_id));
    end loop;
  elsif p_table = 'members' then
    if exists (select 1 from transactions where member_id = p_id) then
      raise exception 'Ce membre a des opérations enregistrées : désactivez-le plutôt (fiche, case Actif)';
    end if;
    if exists (select 1 from profiles where member_id = p_id) or exists (select 1 from invitations where member_id = p_id) then
      raise exception 'Ce membre a un accès ou une invitation à la plateforme : retirez d’abord son accès';
    end if;
    v_lib := (v->>'prenom') || ' ' || (v->>'nom');
    for r in select 'cotisations' t, to_jsonb(c) j from cotisations c where c.member_id = p_id
             union all select 'collecte_membres', to_jsonb(c) from collecte_membres c where c.member_id = p_id
             union all select 'liens_membres', to_jsonb(l) from liens_membres l where l.member_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', r.t, 'donnees', r.j));
    end loop;
    for r in select id from materiel where detenteur_id = p_id loop
      v_liens := v_liens || jsonb_build_array(jsonb_build_object('table', 'materiel', 'id', r.id, 'colonne', 'detenteur_id', 'valeur', p_id));
    end loop;
  elsif p_table = 'tiers' then
    if exists (select 1 from transactions where tiers_id = p_id) then
      raise exception 'Ce tiers a des opérations enregistrées : désactivez-le plutôt';
    end if;
    v_lib := v->>'nom';
  elsif p_table = 'projects' then
    if exists (select 1 from transactions where project_id = p_id) or exists (select 1 from expense_requests where project_id = p_id) then
      raise exception 'Des opérations ou des demandes sont rattachées à ce rendez-vous : impossible de le supprimer';
    end if;
    v_lib := (v->>'nom') || coalesce(' · ' || to_char((v->>'date_debut')::date, 'DD/MM/YYYY'), '');
    for r in select to_jsonb(b) j from budgets b where b.project_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'budgets', 'donnees', r.j));
    end loop;
    for r in select id from collectes where project_id = p_id loop
      v_liens := v_liens || jsonb_build_array(jsonb_build_object('table', 'collectes', 'id', r.id, 'colonne', 'project_id', 'valeur', p_id));
    end loop;
  elsif p_table = 'collectes' then
    if exists (select 1 from transactions where collecte_id = p_id) then
      raise exception 'Des participations sont déjà encaissées : clôturez la collecte plutôt';
    end if;
    v_lib := v->>'nom';
    for r in select to_jsonb(c) j from collecte_membres c where c.collecte_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'collecte_membres', 'donnees', r.j));
    end loop;
  elsif p_table = 'materiel' then
    v_lib := v->>'designation';
    for r in select to_jsonb(m) j from materiel_mouvements m where m.materiel_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'materiel_mouvements', 'donnees', r.j));
    end loop;
  elsif p_table = 'categories' then
    if (v->>'interne')::boolean then raise exception 'Catégorie interne : nécessaire aux virements'; end if;
    if exists (select 1 from transactions where category_id = p_id) or exists (select 1 from expense_requests where category_id = p_id)
       or exists (select 1 from categories where parent_id = p_id) then
      raise exception 'Catégorie utilisée : impossible de la supprimer';
    end if;
    v_lib := v->>'nom';
    for r in select to_jsonb(b) j from budgets b where b.category_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'budgets', 'donnees', r.j));
    end loop;
  elsif p_table = 'accounts' then
    if exists (select 1 from transactions where account_id = p_id) or exists (select 1 from reconciliations where account_id = p_id)
       or exists (select 1 from expense_requests where account_id = p_id) then
      raise exception 'Compte utilisé : désactivez-le plutôt';
    end if;
    v_lib := v->>'nom';
  elsif p_table = 'budgets' then
    v_lib := 'Ligne de budget ' || (v->>'annee') || ' · ' || coalesce((select nom from categories where id = (v->>'category_id')::uuid), '');
  elsif p_table = 'expense_requests' then
    if (v->>'demandeur')::uuid <> auth.uid() and not a_droit('administrer') then raise exception 'Seul le demandeur supprime sa demande'; end if;
    if exists (select 1 from transactions where request_id = p_id) then
      raise exception 'Cette demande a été payée : utilisez « Régulariser »';
    end if;
    v_lib := (v->>'objet') || ' · ' || to_char((v->>'montant')::numeric, 'FM999G999G990D00') || ' €';
    for r in select to_jsonb(a) j from attachments a where a.request_id = p_id loop
      v_dep := v_dep || jsonb_build_array(jsonb_build_object('table', 'attachments', 'donnees', r.j));
    end loop;
  elsif p_table = 'exercices' then
    if (v->>'cloture')::boolean then raise exception 'Exercice clôturé : rouvrez-le d’abord'; end if;
    v_lib := v->>'libelle';
  end if;

  insert into corbeille(table_nom, ligne_id, libelle, donnees, dependances, liens, motif)
  values (p_table, p_id, coalesce(v_lib, p_table), v, v_dep, v_liens, nullif(trim(coalesce(p_motif, '')), ''))
  returning id into v_id;
  for r in select l from jsonb_array_elements(v_liens) l loop
    execute format('update %I set %I = null where id = $1', r.l->>'table', r.l->>'colonne') using (r.l->>'id')::uuid;
  end loop;
  -- Les lignes rattachées d'abord (pièces, autres mouvements), puis la ligne principale
  for r in select d from jsonb_array_elements(v_dep) d where d->>'table' <> 'transactions' loop
    execute format('delete from %I t where to_jsonb(t) = $1', r.d->>'table') using r.d->'donnees';
  end loop;
  execute format('delete from %I where id = $1', p_table) using p_id;
  for r in select d from jsonb_array_elements(v_dep) d where d->>'table' = 'transactions' loop
    delete from transactions where id = (r.d->'donnees'->>'id')::uuid;
  end loop;
  return v_id;
end $$;

create or replace function public.restaurer(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
declare c corbeille; r record;
begin
  select * into c from corbeille where id = p_id;
  if c.id is null then raise exception 'Élément introuvable dans la corbeille'; end if;
  if c.restaure_le is not null then raise exception 'Élément déjà restauré'; end if;
  if not droit_suppression(c.table_nom) then raise exception 'Droit insuffisant pour restaurer'; end if;
  if c.table_nom = 'expense_requests' and (c.donnees->>'demandeur')::uuid <> auth.uid() and not a_droit('administrer') then
    raise exception 'Seul le demandeur restaure sa demande';
  end if;
  begin
    execute format('insert into %I select * from jsonb_populate_record(null::%I, $1)', c.table_nom, c.table_nom) using c.donnees;
    for r in select d from jsonb_array_elements(c.dependances) d where d->>'table' = 'transactions' loop
      insert into transactions select * from jsonb_populate_record(null::transactions, r.d->'donnees');
    end loop;
    for r in select d from jsonb_array_elements(c.dependances) d where d->>'table' <> 'transactions' loop
      execute format('insert into %I select * from jsonb_populate_record(null::%I, $1)', r.d->>'table', r.d->>'table') using r.d->'donnees';
    end loop;
    for r in select l from jsonb_array_elements(c.liens) l loop
      execute format('update %I set %I = $2 where id = $1 and %I is null', r.l->>'table', r.l->>'colonne', r.l->>'colonne')
        using (r.l->>'id')::uuid, (r.l->>'valeur')::uuid;
    end loop;
  exception when unique_violation then
    raise exception 'Restauration impossible : un élément identique existe déjà';
  when foreign_key_violation then
    raise exception 'Restauration impossible : un élément lié (compte, catégorie, membre…) a été supprimé entre-temps';
  end;
  update corbeille set restaure_le = now(), restaure_par = auth.uid() where id = p_id;
end $$;

-- ---------- 13.4 Nouveautés : pastilles sur les onglets et liste des nouveautés ----------
alter table public.profiles add column if not exists vus jsonb not null default '{}';
-- Rendez-vous déjà existants : datés de la création des comptes (ils ne s'affichent pas comme « nouveaux »)
alter table public.projects add column if not exists created_at timestamptz;
update public.projects set created_at = coalesce((select min(created_at) from public.profiles), now()) where created_at is null;
alter table public.projects alter column created_at set default now(), alter column created_at set not null;

-- Pour chaque onglet : ce qui attend une action (à traiter) et ce qui est nouveau depuis la dernière visite :
-- demandes (à valider, à payer, les miennes mises à jour), opérations saisies par d'autres, rendez-vous ajoutés,
-- membres arrivés, participations demandées (pour moi) ou collectes créées (gestion)
create or replace function public.mes_nouveautes() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  p profiles; v_depuis timestamptz; v_moi uuid := auth.uid(); v_res jsonb := '{}'; v_liste jsonb := '[]'; n int;
begin
  select * into p from profiles where id = v_moi and actif;
  if p.id is null then return jsonb_build_object('compteurs', '{}'::jsonb, 'elements', '[]'::jsonb); end if;

  v_depuis := coalesce((p.vus->>'depenses')::timestamptz, p.created_at);
  n := 0;
  if a_droit('valider_depenses') then n := n + (select count(*) from expense_requests where statut = 'soumise' and demandeur <> v_moi); end if;
  if a_droit('payer_depenses') then n := n + (select count(*) from expense_requests where statut = 'validee' and validee_par is distinct from v_moi); end if;
  n := n + (select count(*) from expense_requests where demandeur = v_moi and greatest(validee_le, payee_le) > v_depuis);
  v_res := v_res || jsonb_build_object('depenses', n);
  v_liste := v_liste || coalesce((select jsonb_agg(x) from (
    select 'depenses' section, case statut when 'soumise' then 'À valider' when 'validee' then 'À payer' else 'Demande mise à jour' end titre,
           objet || ' · ' || to_char(montant, 'FM999G990D00') || ' €' detail, greatest(created_at, validee_le, payee_le) quand
    from expense_requests
    where (statut = 'soumise' and demandeur <> v_moi and a_droit('valider_depenses'))
       or (statut = 'validee' and validee_par is distinct from v_moi and a_droit('payer_depenses'))
       or (demandeur = v_moi and greatest(validee_le, payee_le) > v_depuis)
    order by 4 desc limit 10) x), '[]');

  if a_droit('consulter_finances') or a_droit('saisir_ecritures') then
    v_depuis := coalesce((p.vus->>'ecritures')::timestamptz, p.created_at);
    v_res := v_res || jsonb_build_object('ecritures', (select count(*) from transactions where created_at > v_depuis and created_by is distinct from v_moi));
    v_liste := v_liste || coalesce((select jsonb_agg(x) from (
      select 'ecritures' section, case when sens = 'recette' then 'Nouvelle recette' else 'Nouvelle dépense' end titre,
             libelle || ' · ' || to_char(montant, 'FM999G990D00') || ' €' detail, created_at quand
      from transactions where created_at > v_depuis and created_by is distinct from v_moi order by created_at desc limit 10) x), '[]');
  end if;

  v_depuis := coalesce((p.vus->>'activites')::timestamptz, p.created_at);
  v_res := v_res || jsonb_build_object('activites', (select count(*) from projects pr where pr.created_at > v_depuis
     and (pr.visible_adherents or a_droit('gerer_activites') or a_droit('consulter_finances') or a_droit('demander_depenses'))));
  v_liste := v_liste || coalesce((select jsonb_agg(x) from (
    select 'activites' section, 'Nouveau rendez-vous' titre, nom || coalesce(' · ' || to_char(date_debut, 'DD/MM'), '') detail, created_at quand
    from projects pr where pr.created_at > v_depuis
      and (pr.visible_adherents or a_droit('gerer_activites') or a_droit('consulter_finances') or a_droit('demander_depenses'))
    order by created_at desc limit 10) x), '[]');

  if a_droit('voir_membres') or a_droit('gerer_membres') then
    v_depuis := coalesce((p.vus->>'membres')::timestamptz, p.created_at);
    v_res := v_res || jsonb_build_object('membres', (select count(*) from members where created_at > v_depuis));
    v_liste := v_liste || coalesce((select jsonb_agg(x) from (
      select 'membres' section, 'Nouveau membre' titre, prenom || ' ' || nom detail, created_at quand
      from members where created_at > v_depuis order by created_at desc limit 10) x), '[]');
  end if;

  v_depuis := coalesce((p.vus->>'cotisations')::timestamptz, p.created_at);
  if a_droit('gerer_cotisations') or a_droit('consulter_finances') then
    n := (select count(*) from collectes where created_at > v_depuis);
  elsif p.member_id is not null then
    n := (select count(*) from collectes c where c.created_at > v_depuis and not c.cloturee
          and (c.tous_membres or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = p.member_id)));
  else n := 0; end if;
  v_res := v_res || jsonb_build_object('cotisations', n);
  if n > 0 then
    v_liste := v_liste || coalesce((select jsonb_agg(x) from (
      select 'cotisations' section, 'Participation demandée' titre, nom || coalesce(' · ' || to_char(montant_attendu, 'FM999G990D00') || ' €', '') detail, created_at quand
      from collectes c where c.created_at > v_depuis and not c.cloturee
        and (a_droit('gerer_cotisations') or a_droit('consulter_finances') or c.tous_membres
             or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = p.member_id))
      order by created_at desc limit 10) x), '[]');
  end if;

  return jsonb_build_object('compteurs', v_res,
    'elements', coalesce((select jsonb_agg(e order by (e->>'quand') desc) from jsonb_array_elements(v_liste) e), '[]'));
end $$;

create or replace function public.marquer_vu(p_section text) returns void
language sql security definer set search_path = public as $$
  update profiles set vus = vus || jsonb_build_object(p_section, now()) where id = auth.uid()
$$;

-- ---------- 13.5 Nouveau membre : il remplit lui-même sa fiche ----------
-- Si le compte est déjà rattaché à une fiche, elle est complétée ; sinon une fiche portant la même adresse
-- e-mail est rattachée ; à défaut, une fiche est créée. Le nom affiché reprend celui de la fiche.
create or replace function public.enregistrer_ma_fiche(p_prenom text, p_nom text, p_jour int, p_mois int,
  p_whatsapp text default null, p_profession text default null, p_consent boolean default true) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_membre uuid; v_email text;
begin
  if not est_connecte() then raise exception 'Connexion requise'; end if;
  if coalesce(trim(p_prenom), '') = '' or coalesce(trim(p_nom), '') = '' then raise exception 'Prénom et nom obligatoires'; end if;
  if p_jour not between 1 and 31 or p_mois not between 1 and 12 then raise exception 'Date de naissance invalide'; end if;
  select member_id into v_membre from profiles where id = auth.uid();
  select email into v_email from auth.users where id = auth.uid();
  if v_membre is null and v_email is not null then
    select m.id into v_membre from members m
    where lower(m.email) = lower(v_email) and not exists (select 1 from profiles x where x.member_id = m.id) limit 1;
  end if;
  if v_membre is null then
    insert into members(prenom, nom, naissance_jour, naissance_mois, whatsapp, profession, email, consent_anniversaire, date_adhesion, actif)
    values (trim(p_prenom), trim(p_nom), p_jour, p_mois, nullif(trim(coalesce(p_whatsapp, '')), ''), nullif(trim(coalesce(p_profession, '')), ''),
            v_email, coalesce(p_consent, true), current_date, true)
    returning id into v_membre;
  else
    update members set prenom = trim(p_prenom), nom = trim(p_nom), naissance_jour = p_jour, naissance_mois = p_mois,
      whatsapp = coalesce(nullif(trim(coalesce(p_whatsapp, '')), ''), whatsapp), profession = coalesce(nullif(trim(coalesce(p_profession, '')), ''), profession),
      email = coalesce(email, v_email), consent_anniversaire = coalesce(p_consent, consent_anniversaire)
    where id = v_membre;
  end if;
  update profiles set member_id = v_membre, nom = trim(p_prenom) || ' ' || trim(p_nom) where id = auth.uid();
  return v_membre;
end $$;

grant execute on function public.supprimer(text, uuid, text), public.restaurer(uuid), public.mes_nouveautes(),
  public.marquer_vu(text), public.enregistrer_ma_fiche(text, text, int, int, text, text, boolean), public.exercice_clos(date) to authenticated;
revoke execute on function public.supprimer(text, uuid, text), public.restaurer(uuid), public.mes_nouveautes(),
  public.marquer_vu(text), public.enregistrer_ma_fiche(text, text, int, int, text, text, boolean) from anon;
