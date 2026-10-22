-- =====================================================================
-- 2026-10-22 · Plusieurs montants de cotisation et de participation ; suppression d'un compte d'accès
--
-- 1. Barème de cotisation (table tarifs) : chaque membre peut avoir son montant (« Choriste 5 € »,
--    « Musicien 3 € », « Exonéré 0 € »…) ; sans montant choisi, il paie le montant normal (settings.cotisation_montant).
--    generer_cotisations prend le montant de chacun ; appliquer_tarif change le montant d'un ou plusieurs membres
--    et met à jour les périodes à partir d'une date (les périodes à 0 € apparaissent « Dispensé »).
-- 2. Participations : montant propre à une personne (collecte_montants), sinon le montant par personne de la collecte.
--    Mêmes calculs partout : v_collectes.total_attendu, mes_participations, situation_par_lien, planning_activites,
--    notifications (collectes.montants_varies : « montant selon la personne »).
-- 3. Corbeille : montants de cotisation (tarifs) et comptes d'accès (supprimés et restaurés par la fonction
--    Edge supprimer-compte) ; les montants par personne partent et reviennent avec la collecte ou le membre.
-- Ce fichier ne contient aucune suppression directe : appliqué tel quel en production.
-- =====================================================================

-- ---------- 1. Barème de cotisation ----------
create table if not exists public.tarifs (
  id uuid primary key default gen_random_uuid(),
  nom text not null check (length(trim(nom)) between 1 and 60),
  montant numeric(10,2) not null check (montant >= 0),
  ordre int not null default 0,
  created_at timestamptz not null default now()
);
alter table public.tarifs enable row level security;
create policy lecture on public.tarifs for select using (
  public.a_droit('voir_membres') or public.a_droit('gerer_membres') or public.a_droit('gerer_cotisations') or public.a_droit('consulter_finances'));
-- Ajout et modification seulement : la suppression passe par la corbeille (supprimer / restaurer)
create policy ajout on public.tarifs for insert with check (public.a_droit('gerer_cotisations'));
create policy modification on public.tarifs for update using (public.a_droit('gerer_cotisations')) with check (public.a_droit('gerer_cotisations'));
create trigger audit_tarifs after insert or update on public.tarifs for each row execute function public.log_change();

-- Clé sans effacement en cascade : un montant encore attribué à des membres ne peut pas partir à la corbeille
alter table public.members add column if not exists tarif_id uuid references public.tarifs(id);

-- Montant de cotisation d'un membre : le sien, sinon le montant normal
create or replace function public.montant_cotisation(p_member uuid) returns numeric
language sql stable security definer set search_path = public as $$
  select coalesce((select t.montant from members m join tarifs t on t.id = m.tarif_id where m.id = p_member),
                  (select valeur from settings where cle = 'cotisation_montant'))
$$;

create or replace function public.generer_cotisations(an integer) returns integer
language plpgsql security definer set search_path = public as $$
declare n int; pas int; v_normal numeric;
begin
  if not a_droit('gerer_cotisations') then raise exception 'Droit « gérer les cotisations » requis'; end if;
  select valeur::int into pas from settings where cle = 'cotisation_periode_mois';
  select valeur into v_normal from settings where cle = 'cotisation_montant';
  if pas is null or pas not in (1, 3, 6, 12) then pas := 1; end if;
  insert into cotisations(member_id, periode, montant_du)
  select m.id, p::date, coalesce(t.montant, v_normal)
  from members m
  left join tarifs t on t.id = m.tarif_id
  cross join generate_series(make_date(an, 1, 1), make_date(an, 12, 1), make_interval(months => pas)) p
  where m.actif and (m.date_adhesion is null or m.date_adhesion < p + make_interval(months => pas))
  on conflict (member_id, periode) do nothing;
  get diagnostics n = row_count;
  return n;
end $$;

-- Attribue un montant (p_tarif ; null = montant normal) à des membres (p_membres), ou réapplique un montant
-- modifié à tous ceux qui l'ont (p_membres null) ; met à jour les périodes à partir de p_depuis (null : aucune)
create or replace function public.appliquer_tarif(p_tarif uuid, p_depuis date, p_membres uuid[] default null) returns integer
language plpgsql security definer set search_path = public as $$
declare v_montant numeric; n int;
begin
  if not a_droit('gerer_cotisations') then raise exception 'Droit « gérer les cotisations » requis'; end if;
  if p_tarif is null then
    select valeur into v_montant from settings where cle = 'cotisation_montant';
  else
    select montant into v_montant from tarifs where id = p_tarif;
    if v_montant is null then raise exception 'Montant de cotisation introuvable'; end if;
  end if;
  if p_membres is not null then
    update members set tarif_id = p_tarif where id = any(p_membres) and tarif_id is distinct from p_tarif;
  end if;
  if p_depuis is null or v_montant is null then return 0; end if;
  update cotisations c set montant_du = v_montant
   where c.periode >= p_depuis and c.montant_du is distinct from v_montant
     and c.member_id in (select m.id from members m
                          where (p_membres is not null and m.id = any(p_membres))
                             or (p_membres is null and m.tarif_id is not distinct from p_tarif));
  get diagnostics n = row_count;
  return n;
end $$;
revoke execute on function public.appliquer_tarif(uuid, date, uuid[]), public.montant_cotisation(uuid) from public, anon;
grant execute on function public.appliquer_tarif(uuid, date, uuid[]), public.montant_cotisation(uuid) to authenticated;

-- ---------- 2. Participations : montant par personne ----------
alter table public.collectes add column if not exists montants_varies boolean not null default false;
-- Clés étrangères (effacement en cascade avec la collecte ou le membre) : supabase/A-EXECUTER-montants-2026-10-22.sql,
-- à exécuter par le trésorier dans l'éditeur SQL (le connecteur demande une confirmation pour ce mot-clé)
create table if not exists public.collecte_montants (
  collecte_id uuid not null,
  member_id uuid not null,
  montant numeric(10,2) not null check (montant >= 0),
  primary key (collecte_id, member_id)
);
alter table public.collecte_montants enable row level security;
create policy lecture on public.collecte_montants for select using (
  public.a_droit('consulter_finances') or public.a_droit('gerer_cotisations') or public.a_droit('gerer_activites') or public.a_droit('saisir_ecritures'));
create policy gestion on public.collecte_montants for all using (public.a_droit('gerer_activites') or public.a_droit('gerer_cotisations'))
  with check (public.a_droit('gerer_activites') or public.a_droit('gerer_cotisations'));

-- Montant attendu d'un membre pour une collecte : le sien, sinon celui de la collecte (null : libre)
create or replace function public.montant_participation(p_collecte uuid, p_member uuid) returns numeric
language sql stable security definer set search_path = public as $$
  select coalesce((select x.montant from collecte_montants x where x.collecte_id = p_collecte and x.member_id = p_member),
                  (select c.montant_attendu from collectes c where c.id = p_collecte))
$$;
revoke execute on function public.montant_participation(uuid, uuid) from public, anon;
grant execute on function public.montant_participation(uuid, uuid) to authenticated;

create or replace view public.v_collectes with (security_invoker = true) as
select c.id, c.nom, c.project_id, c.montant_attendu, c.objectif, c.date_limite, c.tous_membres, c.cloturee, c.created_at,
       coalesce((select sum(t.montant) from transactions t where t.collecte_id = c.id), 0) as total_recu,
       (select count(*) from (select 1 from transactions t where t.collecte_id = c.id
          group by coalesce(t.member_id::text, t.tiers_id::text, t.contrepasse_de::text, t.id::text) having sum(t.montant) > 0) s) as nb_contributeurs,
       case when c.tous_membres then (select count(*) from members m where m.actif)
            else (select count(*) from collecte_membres cm where cm.collecte_id = c.id) end as nb_concernes,
       c.montants_varies,
       -- Total attendu : somme des montants de chacun (le sien, sinon celui de la collecte) ; null si tout est libre
       case when c.montant_attendu is null and not exists (select 1 from collecte_montants x where x.collecte_id = c.id) then null
            else (select coalesce(sum(coalesce(x.montant, c.montant_attendu, 0)), 0)
                  from (select m.id from members m where c.tous_membres and m.actif
                        union select cm.member_id from collecte_membres cm where not c.tous_membres and cm.collecte_id = c.id) k
                  left join collecte_montants x on x.collecte_id = c.id and x.member_id = k.id) end as total_attendu
from collectes c;

create or replace function public.mes_participations()
returns table(collecte_id uuid, nom text, montant_attendu numeric, donne numeric, date_limite date, cloturee boolean)
language sql stable security definer set search_path = public as $$
  select c.id, c.nom, coalesce(x.montant, c.montant_attendu), coalesce(sum(t.montant), 0), c.date_limite, c.cloturee
  from profiles p
  join collectes c on c.tous_membres or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = p.member_id)
  left join collecte_montants x on x.collecte_id = c.id and x.member_id = p.member_id
  left join transactions t on t.collecte_id = c.id and t.member_id = p.member_id
  where p.id = auth.uid() and p.actif and p.member_id is not null
  group by c.id, x.montant
  having not c.cloturee or coalesce(sum(t.montant), 0) <> 0
  order by c.created_at desc
$$;

-- Planning : la participation affichée est celle de la personne connectée (sinon le montant par personne)
create or replace function public.planning_activites(p_debut date default null, p_fin date default null)
returns table(id uuid, nom text, type text, date_debut date, date_fin date, heure_debut time, heure_fin time, lieu text, description text,
              visible_adherents boolean, participation numeric, collecte_id uuid)
language sql stable security definer set search_path = public as $$
  select p.id, p.nom, p.type, p.date_debut, p.date_fin, p.heure_debut, p.heure_fin, p.lieu, p.description, p.visible_adherents,
         coalesce((select x.montant from collecte_montants x join profiles pr on pr.member_id = x.member_id
                   where x.collecte_id = c.id and pr.id = auth.uid()), c.montant_attendu), c.id
  from projects p
  left join lateral (select c.id, c.montant_attendu from collectes c where c.project_id = p.id and not c.cloturee
                     order by c.created_at limit 1) c on true
  where est_connecte()
    and (p.visible_adherents or a_droit('gerer_activites') or a_droit('consulter_finances'))
    and p.date_debut is not null
    and coalesce(p.date_fin, p.date_debut) >= coalesce(p_debut, current_date - 30)
    and (p_fin is null or p.date_debut <= p_fin)
  order by p.date_debut, p.heure_debut nulls first
$$;

-- Notifications : montant de la personne ; « selon la personne » pour l'annonce à tous quand les montants varient
create or replace function public.notif_collecte() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.tous_membres and not new.cloturee then
    perform mettre_en_file('col:' || new.id, 'Participation demandée · ' || new.nom,
      case when new.montants_varies then 'Montant selon la personne, indiqué dans ton espace'
           else coalesce(replace(to_char(new.montant_attendu, 'FM999999990.00'), '.', ',') || ' € par personne', 'Montant libre') end
        || coalesce(', avant le ' || date_fr(new.date_limite), ''), null);
  end if;
  return null;
end $$;

create or replace function public.notif_collecte_membre() returns trigger
language plpgsql security definer set search_path = public as $$
declare c collectes; v numeric;
begin
  select * into c from collectes where id = new.collecte_id;
  if c.id is not null and not c.cloturee and not c.tous_membres then
    v := montant_participation(c.id, new.member_id);
    perform mettre_en_file('col:' || c.id || ':' || new.member_id, 'Participation demandée · ' || c.nom,
      case when v = 0 then 'Pas de participation demandée pour toi'
           else coalesce(replace(to_char(v, 'FM999999990.00'), '.', ',') || ' €', 'Montant libre') end
        || coalesce(', avant le ' || date_fr(c.date_limite), ''), new.member_id);
  end if;
  return null;
end $$;

-- Nouveautés : pas de montant unique annoncé quand il varie selon la personne
do $$
declare d text;
begin
  select pg_get_functiondef('public.mes_nouveautes()'::regprocedure) into d;
  if position('montants_varies' in d) = 0 then
    d := replace(d, $r$nom || coalesce(' · ' || to_char(montant_attendu, 'FM999G990D00') || ' €', '') detail$r$,
                    $r$nom || case when c.montants_varies then ' · montant selon la personne' else coalesce(' · ' || to_char(montant_attendu, 'FM999G990D00') || ' €', '') end detail$r$);
    execute d;
  end if;
end $$;

-- Page du lien personnel : montant de cotisation et participations de la personne
create or replace function public.situation_par_lien(p_jeton text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare mid uuid; r jsonb;
begin
  if p_jeton is null or length(p_jeton) not in (12, 32) then return null; end if;
  update liens_membres set nb_consultations = nb_consultations
         + case when derniere_consultation is null or derniere_consultation < now() - interval '30 minutes' then 1 else 0 end,
       derniere_consultation = now()
   where (length(p_jeton) = 32 and jeton = p_jeton) or (length(p_jeton) = 12 and code = lower(p_jeton)) returning member_id into mid;
  if mid is null then return null; end if;
  select jsonb_build_object(
    'association', (select jsonb_build_object('nom', o.nom, 'logo_path', o.logo_path, 'banniere_path', o.banniere_path) from organisation o),
    'membre', (select jsonb_build_object('prenom', m.prenom, 'nom', m.nom, 'actif', m.actif) from members m where m.id = mid),
    'reglages', jsonb_build_object(
       'montant', montant_cotisation(mid),
       'periode_mois', (select valeur from settings where cle = 'cotisation_periode_mois'),
       'infos_paiement', (select texte from settings where cle = 'infos_paiement')),
    'periodes', coalesce((select jsonb_agg(jsonb_build_object('periode', v.periode, 'annee', v.annee, 'montant_du', v.montant_du,
                   'regle', v.regle, 'statut', v.statut) order by v.periode desc)
                 from v_cotisations_periodes v where v.member_id = mid), '[]'::jsonb),
    'avance', greatest(0, coalesce((select sum(t.montant) from transactions t where t.est_cotisation and t.member_id = mid), 0)
                 - coalesce((select sum(c.montant_du) from cotisations c where c.member_id = mid), 0)),
    'versements', coalesce((select jsonb_agg(jsonb_build_object('date', t.date_op, 'montant', t.montant,
                   'objet', case when t.est_cotisation then 'Cotisation' else coalesce((select c.nom from collectes c where c.id = t.collecte_id), t.libelle) end)
                   order by t.date_op desc)
                 from (select * from transactions t where t.member_id = mid and t.sens = 'recette'
                       and (t.est_cotisation or t.collecte_id is not null) order by t.date_op desc limit 24) t), '[]'::jsonb),
    'participations', coalesce((select jsonb_agg(x order by x->>'cree' desc) from (
        select jsonb_build_object('nom', c.nom, 'montant_attendu', coalesce(cmx.montant, c.montant_attendu), 'donne', coalesce(sum(t.montant), 0),
               'date_limite', c.date_limite, 'cloturee', c.cloturee, 'cree', c.created_at) x
        from collectes c
        left join collecte_montants cmx on cmx.collecte_id = c.id and cmx.member_id = mid
        left join transactions t on t.collecte_id = c.id and t.member_id = mid
        where c.tous_membres or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = mid)
        group by c.id, cmx.montant
        having not c.cloturee or coalesce(sum(t.montant), 0) <> 0) s), '[]'::jsonb),
    'communiques', coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'titre', c.titre, 'texte', c.texte, 'priorite', c.priorite,
                   'debut', c.debut, 'fin', c.fin, 'genre', c.genre, 'lien', c.lien, 'photos', c.photos, 'questions', c.questions,
                   'anonyme', c.anonyme, 'resultats_visibles', c.resultats_visibles,
                   'ma_reponse', (select rs.reponses from reponses_sondage rs where rs.communique_id = c.id and rs.member_id = mid),
                   'resultats', case when c.genre = 'sondage' and c.resultats_visibles
                       and exists (select 1 from reponses_sondage rs where rs.communique_id = c.id and rs.member_id = mid) then calcul_resultats(c, false) end)
                   order by c.debut desc)
                 from communiques c where c.visible_adherents and c.debut <= current_date and (c.fin is null or c.fin >= current_date)), '[]'::jsonb),
    'banniere', (select texte from settings where cle = 'banniere'),
    'a_venir', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'nom', p.nom, 'date_debut', p.date_debut, 'date_fin', p.date_fin,
                   'heure_debut', p.heure_debut, 'heure_fin', p.heure_fin, 'lieu', p.lieu, 'description', p.description)
                   order by p.date_debut, p.heure_debut nulls first)
                 from (select * from projects p where p.visible_adherents and p.date_debut is not null
                       and coalesce(p.date_fin, p.date_debut) >= current_date and p.date_debut <= current_date + 366
                       order by p.date_debut limit 60) p), '[]'::jsonb)
  ) into r;
  return r;
end $$;

-- ---------- 3. Corbeille ----------
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
    when 'communiques' then a_droit('gerer_activites') or a_droit('administrer')
    when 'tarifs' then a_droit('gerer_cotisations')
    when 'comptes' then a_droit('administrer')
    else false end
$$;

-- Libellé lisible et lignes rattachées ajoutées à l'entrée de corbeille (avant l'effacement par supprimer) :
-- les montants par personne partent et reviennent avec leur collecte ou leur membre
create or replace function public.libelle_corbeille() returns trigger
language plpgsql set search_path = public as $$
begin
  if new.table_nom = 'communiques' and (new.libelle is null or new.libelle = 'communiques') then
    new.libelle := 'Communiqué · ' || coalesce(new.donnees->>'titre', '');
  elsif new.table_nom = 'tarifs' and (new.libelle is null or new.libelle = 'tarifs') then
    new.libelle := 'Montant de cotisation · ' || coalesce(new.donnees->>'nom', '');
  end if;
  if new.table_nom = 'collectes' then
    new.dependances := new.dependances || coalesce((select jsonb_agg(jsonb_build_object('table', 'collecte_montants', 'donnees', to_jsonb(x)))
                                                     from collecte_montants x where x.collecte_id = new.ligne_id), '[]'::jsonb);
  elsif new.table_nom = 'members' then
    new.dependances := new.dependances || coalesce((select jsonb_agg(jsonb_build_object('table', 'collecte_montants', 'donnees', to_jsonb(x)))
                                                     from collecte_montants x where x.member_id = new.ligne_id), '[]'::jsonb);
  end if;
  return new;
end $$;

-- Un compte d'accès se restaure par la fonction Edge supprimer-compte (il faut recréer l'identifiant de connexion)
create or replace function public.restaurer(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
declare c corbeille; r record;
begin
  select * into c from corbeille where id = p_id;
  if c.id is null then raise exception 'Élément introuvable dans la corbeille'; end if;
  if c.restaure_le is not null then raise exception 'Élément déjà restauré'; end if;
  if c.table_nom = 'comptes' then raise exception 'Un compte d’accès se restaure par « Restaurer » dans Paramètres › Corbeille'; end if;
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

-- ---------- 4. Temps réel ----------
do $$
declare t text;
begin
  foreach t in array array['tarifs', 'collecte_montants'] loop
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;
