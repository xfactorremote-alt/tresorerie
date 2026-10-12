-- =====================================================================
-- Correctif du 12 octobre 2026 : sondages, questionnaires, photos et liens pour les membres
-- =====================================================================
-- Un communiqué a désormais un genre : annonce (comme avant), sondage (une ou plusieurs questions),
-- photos (album) ou lien (Drive, formulaire, vidéo…). Tout le reste est commun : période, priorité,
-- visibilité, bannière, notifications, corbeille.
-- Les membres répondent depuis leur page (lien personnel ou compte) ; le bureau voit les résultats.

alter table public.communiques add column if not exists genre text not null default 'annonce';
alter table public.communiques add column if not exists lien text;
alter table public.communiques add column if not exists photos jsonb not null default '[]'::jsonb;
alter table public.communiques add column if not exists questions jsonb not null default '[]'::jsonb;
alter table public.communiques add column if not exists anonyme boolean not null default false;
alter table public.communiques add column if not exists resultats_visibles boolean not null default true;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'communiques_genre_check') then
    alter table public.communiques add constraint communiques_genre_check check (genre in ('annonce', 'sondage', 'photos', 'lien'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'communiques_lien_check') then
    alter table public.communiques add constraint communiques_lien_check check (lien is null or (lien ~* '^https?://' and length(lien) <= 1000));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'communiques_contenu_check') then
    alter table public.communiques add constraint communiques_contenu_check check (
      jsonb_typeof(photos) = 'array' and jsonb_array_length(photos) <= 30 and jsonb_typeof(questions) = 'array' and jsonb_array_length(questions) <= 20
      and (genre <> 'sondage' or jsonb_array_length(questions) >= 1)
      and (genre <> 'photos' or jsonb_array_length(photos) >= 1)
      and (genre <> 'lien' or lien is not null));
  end if;
end $$;

-- Photos des publications : dossier public aux noms imprévisibles (ouvertes par les membres sans compte)
insert into storage.buckets(id, name, public) values ('partages', 'partages', true) on conflict (id) do nothing;
do $$ begin
  if not exists (select 1 from pg_policies where tablename = 'objects' and policyname = 'partages depot') then
    create policy "partages depot" on storage.objects for insert
      with check (bucket_id = 'partages' and (public.a_droit('gerer_activites') or public.a_droit('administrer')));
  end if;
end $$;

-- Réponses (une par membre et par sondage, modifiable jusqu'à la fin du sondage)
create table if not exists public.reponses_sondage (
  id uuid primary key default gen_random_uuid(),
  communique_id uuid not null,
  member_id uuid not null,
  reponses jsonb not null,
  cree_le timestamptz not null default now(),
  modifie_le timestamptz not null default now(),
  unique (communique_id, member_id)
);
alter table public.reponses_sondage enable row level security;
do $$ begin
  if not exists (select 1 from pg_policies where tablename = 'reponses_sondage' and policyname = 'reponses_lecture') then
    create policy reponses_lecture on public.reponses_sondage for select
      using (public.a_droit('gerer_activites') or public.a_droit('administrer'));
  end if;
end $$;

-- Le membre qui appelle : par son lien personnel, ou par son compte
create or replace function public.membre_appelant(p_jeton text) returns uuid
language sql stable security definer set search_path = public as $$
  select case when p_jeton is not null then
      (select member_id from liens_membres where (length(p_jeton) = 32 and jeton = p_jeton) or (length(p_jeton) = 12 and code = lower(p_jeton)))
    else (select member_id from profiles where id = auth.uid() and actif) end $$;
revoke execute on function public.membre_appelant(text) from public, anon, authenticated;

-- Résultats d'un sondage : comptes par choix ; le bureau voit aussi les réponses écrites et, sauf sondage anonyme, qui a répondu
create or replace function public.calcul_resultats(c communiques, p_bureau boolean) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'nb', (select count(*) from reponses_sondage r where r.communique_id = c.id),
    'questions', coalesce((select jsonb_agg(jsonb_build_object('id', q->>'id',
        'comptes', (select coalesce(jsonb_object_agg(ch, (select count(*) from reponses_sondage r
                     where r.communique_id = c.id and (r.reponses->(q->>'id') = to_jsonb(ch) or r.reponses->(q->>'id') @> jsonb_build_array(ch)))), '{}'::jsonb)
                    from jsonb_array_elements_text(coalesce(q->'choix', '[]'::jsonb)) ch),
        'textes', case when p_bureau and q->>'type' = 'texte' then (select coalesce(jsonb_agg(r.reponses->>(q->>'id') order by r.modifie_le), '[]'::jsonb)
                     from reponses_sondage r where r.communique_id = c.id and coalesce(r.reponses->>(q->>'id'), '') <> '') end) order by o)
      from jsonb_array_elements(c.questions) with ordinality as t(q, o)), '[]'::jsonb),
    'repondants', case when p_bureau and not c.anonyme then (select coalesce(jsonb_agg(jsonb_build_object('prenom', m.prenom, 'nom', m.nom,
        'reponses', r.reponses, 'le', r.modifie_le) order by m.nom, m.prenom), '[]'::jsonb)
      from reponses_sondage r join members m on m.id = r.member_id where r.communique_id = c.id) end) $$;
revoke execute on function public.calcul_resultats(communiques, boolean) from public, anon, authenticated;

create or replace function public.resultats_sondage(p_communique uuid, p_jeton text default null) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare c communiques; mid uuid; bureau boolean := a_droit('gerer_activites') or a_droit('administrer');
begin
  select * into c from communiques where id = p_communique and genre = 'sondage';
  if c.id is null then return null; end if;
  if bureau then return calcul_resultats(c, true); end if;
  mid := membre_appelant(p_jeton);
  if mid is null or not c.visible_adherents or not c.resultats_visibles
     or not exists (select 1 from reponses_sondage where communique_id = c.id and member_id = mid) then return null; end if;
  return calcul_resultats(c, false);
end $$;
grant execute on function public.resultats_sondage(uuid, text) to anon, authenticated;

-- Répondre (ou changer sa réponse) : contrôle des questions obligatoires et des choix proposés
create or replace function public.repondre_sondage(p_communique uuid, p_reponses jsonb, p_jeton text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare c communiques; mid uuid; q jsonb; v jsonb; propre jsonb := '{}'::jsonb;
begin
  mid := membre_appelant(p_jeton);
  if mid is null then raise exception 'Lien ou compte non reconnu'; end if;
  select * into c from communiques where id = p_communique and genre = 'sondage' and visible_adherents;
  if c.id is null then raise exception 'Sondage introuvable'; end if;
  if c.debut > current_date or (c.fin is not null and c.fin < current_date) then raise exception 'Ce sondage est clos'; end if;
  for q in select * from jsonb_array_elements(c.questions) loop
    v := p_reponses->(q->>'id');
    if q->>'type' = 'texte' then
      if v is not null and jsonb_typeof(v) = 'string' and length(trim(v #>> '{}')) > 0 then propre := propre || jsonb_build_object(q->>'id', left(trim(v #>> '{}'), 1000)); v := 'true'; else v := null; end if;
    elsif q->>'type' = 'multiple' then
      if v is not null and jsonb_typeof(v) = 'array' and jsonb_array_length(v) > 0 then
        if exists (select 1 from jsonb_array_elements(v) x where not (q->'choix' @> jsonb_build_array(x))) then raise exception 'Choix inconnu'; end if;
        propre := propre || jsonb_build_object(q->>'id', v);
      else v := null; end if;
    else
      if v is not null and jsonb_typeof(v) = 'string' then
        if not (q->'choix' @> jsonb_build_array(v)) then raise exception 'Choix inconnu'; end if;
        propre := propre || jsonb_build_object(q->>'id', v);
      else v := null; end if;
    end if;
    if v is null and coalesce((q->>'obligatoire')::boolean, true) then raise exception 'Merci de répondre à : %', q->>'intitule'; end if;
  end loop;
  insert into reponses_sondage(communique_id, member_id, reponses) values (c.id, mid, propre)
  on conflict (communique_id, member_id) do update set reponses = excluded.reponses, modifie_le = now();
  return case when c.resultats_visibles then calcul_resultats(c, false) end;
end $$;
grant execute on function public.repondre_sondage(uuid, jsonb, text) to anon, authenticated;

-- Mes réponses (membre connecté ou lien) : { communique_id: réponses }
create or replace function public.mes_reponses(p_jeton text default null) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_object_agg(r.communique_id, r.reponses), '{}'::jsonb) from reponses_sondage r where r.member_id = membre_appelant(p_jeton) $$;
grant execute on function public.mes_reponses(text) to anon, authenticated;

-- Notification : le titre dit de quoi il s'agit
create or replace function public.titre_notification(c communiques) returns text language sql immutable as $$
  select case when c.priorite = 'haute' then 'Important · ' else '' end
    || case c.genre when 'sondage' then 'Votre avis · ' when 'photos' then 'Photos · ' when 'lien' then 'À voir · ' else case when c.priorite = 'haute' then '' else 'Communiqué · ' end end
    || c.titre $$;

create or replace function public.notif_communique() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.visible_adherents and new.debut <= current_date and (new.fin is null or new.fin >= current_date)
     and (tg_op = 'INSERT' or not old.visible_adherents) then
    perform mettre_en_file('cq:' || new.id, titre_notification(new),
      coalesce(nullif(split_part(new.texte, E'\n', 1), ''), case new.genre when 'sondage' then 'Touchez pour répondre' when 'photos' then jsonb_array_length(new.photos) || ' photo(s)' when 'lien' then 'Touchez pour ouvrir' else '' end), null);
  end if;
  return null;
end $$;

-- Chaque matin : en plus, rappel la veille de la fin d'un sondage à ceux qui n'ont pas répondu (et sont abonnés)
create or replace function public.notifications_du_matin() returns void
language plpgsql security definer set search_path = public as $$
declare p record; c record; a record;
begin
  for p in select * from projects where visible_adherents and date_debut = current_date + 1 loop
    perform mettre_en_file('veille:' || p.id || ':' || p.date_debut, 'Demain · ' || p.nom,
      coalesce(to_char(p.heure_debut, 'HH24"h"MI'), 'Toute la journée') || coalesce(' · ' || p.lieu, ''), null);
  end loop;
  for c in select * from communiques where visible_adherents and debut = current_date and created_at::date < current_date loop
    perform mettre_en_file('cq:' || c.id, titre_notification(c), coalesce(split_part(c.texte, E'\n', 1), ''), null);
  end loop;
  for c in select * from communiques where visible_adherents and genre = 'sondage' and fin = current_date + 1 loop
    for a in select distinct ap.member_id from abonnements_push ap where ap.actif and ap.member_id is not null
               and not exists (select 1 from reponses_sondage r where r.communique_id = c.id and r.member_id = ap.member_id) loop
      perform mettre_en_file('sondage-fin:' || c.id || ':' || a.member_id, 'Dernier jour demain · ' || c.titre, 'Votre avis compte : touchez pour répondre', a.member_id);
    end loop;
  end loop;
  if exists (select 1 from notifications_push where envoyee_le is null) then perform appeler_notifier(); end if;
end $$;
revoke execute on function public.notifications_du_matin() from public, anon, authenticated;

-- Page du lien : les communiqués portent leur genre, leur contenu, ma réponse et les résultats (si permis)
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

-- Temps réel : la liste du bureau (nombre de réponses) se met à jour seule
do $$ begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'reponses_sondage') then
    alter publication supabase_realtime add table public.reponses_sondage;
  end if;
end $$;
