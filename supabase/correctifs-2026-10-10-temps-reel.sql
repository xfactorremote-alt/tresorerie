-- Correctif du 10 octobre 2026 (soir) : communiqués, bannière réglable, temps réel
-- 1. Communiqués de l'association (affichés sur la bannière des membres, avec une priorité et une période)
-- 2. Règles de la bannière (priorité et fréquence de chaque type d'annonce, durée de rotation) dans settings
-- 3. Publication « supabase_realtime » : chaque écran se met à jour seul quand une donnée change
-- 4. Nouveautés : les communiqués comptent ; le lien personnel ne compte qu'une consultation par demi-heure
--    (la page du membre se rafraîchit seule)
-- Pas de suppression ici : la suppression d'un communiqué passe par la corbeille (supprimer / restaurer).

create table if not exists public.communiques (
  id uuid primary key default gen_random_uuid(),
  titre text not null check (length(trim(titre)) between 2 and 120),
  texte text check (texte is null or length(texte) <= 1500),
  debut date not null default current_date,
  fin date,
  priorite text not null default 'normale' check (priorite in ('haute', 'normale', 'basse')),
  visible_adherents boolean not null default true,
  created_by uuid default auth.uid(),
  created_at timestamptz not null default now(),
  check (fin is null or fin >= debut)
);
alter table public.communiques enable row level security;
do $$ begin
  if not exists (select 1 from pg_policies where tablename = 'communiques' and policyname = 'communiques_lecture') then
    create policy communiques_lecture on public.communiques for select
      using (public.est_connecte() and (visible_adherents or public.a_droit('gerer_activites') or public.a_droit('administrer')));
  end if;
  if not exists (select 1 from pg_policies where tablename = 'communiques' and policyname = 'communiques_ajout') then
    create policy communiques_ajout on public.communiques for insert
      with check (public.a_droit('gerer_activites') or public.a_droit('administrer'));
  end if;
  if not exists (select 1 from pg_policies where tablename = 'communiques' and policyname = 'communiques_modif') then
    create policy communiques_modif on public.communiques for update
      using (public.a_droit('gerer_activites') or public.a_droit('administrer'))
      with check (public.a_droit('gerer_activites') or public.a_droit('administrer'));
  end if;
end $$;
create or replace trigger audit_communiques after insert or update on public.communiques for each row execute function public.log_change();

-- Corbeille : le communiqué s'y range sous son titre
create or replace function public.libelle_corbeille() returns trigger
language plpgsql set search_path = public as $$
begin
  if new.table_nom = 'communiques' and (new.libelle is null or new.libelle = 'communiques') then
    new.libelle := 'Communiqué · ' || coalesce(new.donnees->>'titre', '');
  end if;
  return new;
end $$;
create or replace trigger trg_libelle_corbeille before insert on public.corbeille for each row execute function public.libelle_corbeille();

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
    else false end
$$;

-- Règles de la bannière : priorité (1 = d'abord) et fréquence (toujours, une fois par jour, jamais) de chaque annonce
insert into public.settings(cle, texte, description) values ('banniere',
  '{"rotation":8,"rdv":{"priorite":1,"frequence":"toujours"},"cotisation":{"priorite":2,"frequence":"jour"},"participation":{"priorite":2,"frequence":"jour"},"communique":{"priorite":1,"frequence":"toujours"}}',
  'Bannière des membres : ordre et fréquence des annonces, secondes entre deux annonces')
on conflict (cle) do nothing;

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

  v_depuis := coalesce((p.vus->>'communiques')::timestamptz, p.created_at);
  v_res := v_res || jsonb_build_object('communiques', (select count(*) from communiques c where c.created_at > v_depuis
     and c.created_by is distinct from v_moi and (c.visible_adherents or a_droit('gerer_activites') or a_droit('administrer'))
     and (c.fin is null or c.fin >= current_date)));
  v_liste := v_liste || coalesce((select jsonb_agg(x) from (
    select 'communiques' section, 'Communiqué' titre, c.titre detail, c.created_at quand
    from communiques c where c.created_at > v_depuis and c.created_by is distinct from v_moi
      and (c.visible_adherents or a_droit('gerer_activites') or a_droit('administrer')) and (c.fin is null or c.fin >= current_date)
    order by c.created_at desc limit 10) x), '[]');

  return jsonb_build_object('compteurs', v_res,
    'elements', coalesce((select jsonb_agg(e order by (e->>'quand') desc) from jsonb_array_elements(v_liste) e), '[]'));
end $$;

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
       'montant', (select valeur from settings where cle = 'cotisation_montant'),
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
        select jsonb_build_object('nom', c.nom, 'montant_attendu', c.montant_attendu, 'donne', coalesce(sum(t.montant), 0),
               'date_limite', c.date_limite, 'cloturee', c.cloturee, 'cree', c.created_at) x
        from collectes c
        left join transactions t on t.collecte_id = c.id and t.member_id = mid
        where c.tous_membres or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = mid)
        group by c.id
        having not c.cloturee or coalesce(sum(t.montant), 0) <> 0) s), '[]'::jsonb),
    'communiques', coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'titre', c.titre, 'texte', c.texte, 'priorite', c.priorite,
                   'debut', c.debut, 'fin', c.fin) order by c.debut desc)
                 from communiques c where c.visible_adherents and c.debut <= current_date and (c.fin is null or c.fin >= current_date)), '[]'::jsonb),
    'banniere', (select texte from settings where cle = 'banniere'),
    'a_venir', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'nom', p.nom, 'date_debut', p.date_debut, 'date_fin', p.date_fin,
                   'heure_debut', p.heure_debut, 'heure_fin', p.heure_fin, 'lieu', p.lieu, 'description', p.description)
                   order by p.date_debut, p.heure_debut nulls first)
                 from (select * from projects p where p.visible_adherents and p.date_debut is not null
                       and coalesce(p.date_fin, p.date_debut) >= current_date and p.date_debut <= current_date + 120
                       order by p.date_debut limit 12) p), '[]'::jsonb)
  ) into r;
  return r;
end $$;


-- Temps réel : les tables suivies sont publiées (les droits de lecture s'appliquent à chaque abonné)
do $$
declare t text;
begin
  foreach t in array array['transactions','expense_requests','projects','collectes','collecte_membres','members','communiques',
                           'cotisations','budgets','materiel','settings','organisation','accounts','categories','tiers','exercices','liens_membres'] loop
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;
