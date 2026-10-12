-- =====================================================================
-- Correctif du 12 octobre 2026 : vraies notifications (Web Push), même page fermée
-- =====================================================================
-- Un membre (lien personnel ou compte) accepte les notifications sur son appareil : son abonnement est gardé ici.
-- Quand un communiqué paraît, qu'un rendez-vous est annoncé ou déplacé, ou qu'une participation est demandée,
-- une ligne est mise dans la file « notifications_push » et la fonction « notifier » (Edge Function) est appelée :
-- elle chiffre le message (RFC 8291) et l'envoie au service de notification du navigateur.
-- Chaque matin : rappel la veille d'un rendez-vous et communiqués programmés qui commencent ce jour-là.
-- Pas de clé à saisir : la fonction crée elle-même sa paire de clés VAPID et la garde dans push_cles.

create extension if not exists pg_net;
create extension if not exists pg_cron;

-- Abonnements des appareils (pas de clé étrangère : un membre mis à la corbeille puis restauré garde ses abonnements)
create table if not exists public.abonnements_push (
  id uuid primary key default gen_random_uuid(),
  endpoint text not null unique check (endpoint like 'https://%' and length(endpoint) < 1000),
  p256dh text not null check (length(p256dh) between 80 and 100),
  auth text not null check (length(auth) between 16 and 30),
  member_id uuid,
  profile_id uuid,
  actif boolean not null default true,
  cree_le timestamptz not null default now(),
  dernier_envoi timestamptz,
  echecs int not null default 0
);
alter table public.abonnements_push enable row level security;  -- aucune politique : accès par les fonctions seulement

-- Paire de clés VAPID, créée par la fonction « notifier » à son premier passage (lue par le service seulement)
create table if not exists public.push_cles (
  id int primary key default 1 check (id = 1),
  publique text not null,
  privee jsonb not null,
  cree_le timestamptz not null default now()
);
alter table public.push_cles enable row level security;

-- File des notifications à envoyer (member_id vide = tous les membres)
create table if not exists public.notifications_push (
  id bigint generated always as identity primary key,
  cle text not null unique,
  titre text not null,
  texte text,
  url text not null default './',
  member_id uuid,
  cree_le timestamptz not null default now(),
  envoyee_le timestamptz,
  nb_envois int not null default 0
);
alter table public.notifications_push enable row level security;

-- Clé publique pour s'abonner (rien de secret)
create or replace function public.cle_push() returns text
language sql stable security definer set search_path = public as $$ select publique from push_cles where id = 1 $$;
grant execute on function public.cle_push() to anon, authenticated;

-- S'abonner : avec le lien personnel (sans compte) ou connecté (le membre rattaché au compte)
create or replace function public.abonner_push(p_jeton text, p_abonnement jsonb) returns boolean
language plpgsql security definer set search_path = public as $$
declare mid uuid; pid uuid;
begin
  if p_jeton is not null then
    if length(p_jeton) not in (12, 32) then return false; end if;
    select member_id into mid from liens_membres
     where (length(p_jeton) = 32 and jeton = p_jeton) or (length(p_jeton) = 12 and code = lower(p_jeton));
    if mid is null then return false; end if;
  elsif auth.uid() is not null then
    pid := auth.uid();
    select member_id into mid from profiles where id = pid;
  else
    return false;
  end if;
  insert into abonnements_push(endpoint, p256dh, auth, member_id, profile_id)
  values (p_abonnement->>'endpoint', p_abonnement->'keys'->>'p256dh', p_abonnement->'keys'->>'auth', mid, pid)
  on conflict (endpoint) do update set p256dh = excluded.p256dh, auth = excluded.auth, member_id = excluded.member_id,
     profile_id = excluded.profile_id, actif = true, echecs = 0;
  return true;
end $$;
grant execute on function public.abonner_push(text, jsonb) to anon, authenticated;

-- Se désabonner (l'appareil connaît son adresse d'abonnement)
create or replace function public.desabonner_push(p_endpoint text) returns void
language sql security definer set search_path = public as $$ update abonnements_push set actif = false where endpoint = p_endpoint $$;
grant execute on function public.desabonner_push(text) to anon, authenticated;

-- Appel de la fonction d'envoi (sans attendre sa réponse ; une panne ne bloque jamais la saisie)
create or replace function public.appeler_notifier() returns void
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_net') then
    execute $q$ select net.http_post(url := 'https://kihxkuotukbzrbsiqanq.supabase.co/functions/v1/notifier',
              body := '{}'::jsonb, headers := '{"Content-Type":"application/json"}'::jsonb) $q$;
  end if;
exception when others then raise warning 'notifier : %', sqlerrm;
end $$;
revoke execute on function public.appeler_notifier() from public, anon, authenticated;

create or replace function public.mettre_en_file(p_cle text, p_titre text, p_texte text, p_member uuid default null, p_url text default './')
returns void language plpgsql security definer set search_path = public as $$
begin
  insert into notifications_push(cle, titre, texte, member_id, url) values (p_cle, left(p_titre, 120), left(p_texte, 240), p_member, p_url)
  on conflict (cle) do nothing;
  if found then perform appeler_notifier(); end if;
exception when others then raise warning 'mise en file : %', sqlerrm;  -- jamais de saisie bloquée par une notification
end $$;
revoke execute on function public.mettre_en_file(text, text, text, uuid, text) from public, anon, authenticated;

-- Date lisible en français (« samedi 16 octobre »)
create or replace function public.date_fr(d date) returns text language sql immutable as $$
  select (array['dimanche','lundi','mardi','mercredi','jeudi','vendredi','samedi'])[extract(dow from d)::int + 1] || ' ' || extract(day from d)::int || ' ' ||
         (array['janvier','février','mars','avril','mai','juin','juillet','août','septembre','octobre','novembre','décembre'])[extract(month from d)::int] $$;

-- Communiqué publié aujourd'hui (ceux programmés plus tard partent le matin de leur début)
create or replace function public.notif_communique() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.visible_adherents and new.debut <= current_date and (new.fin is null or new.fin >= current_date)
     and (tg_op = 'INSERT' or not old.visible_adherents) then
    perform mettre_en_file('cq:' || new.id, case when new.priorite = 'haute' then 'Important · ' else 'Communiqué · ' end || new.titre,
                           coalesce(split_part(new.texte, E'\n', 1), ''), null);
  end if;
  return null;
end $$;
create or replace trigger notif_communique after insert or update of visible_adherents on public.communiques
  for each row execute function public.notif_communique();

-- Rendez-vous annoncé (visible) ou déplacé
create or replace function public.notif_rendez_vous() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.visible_adherents and new.date_debut is not null and new.date_debut >= current_date
     and (tg_op = 'INSERT' or not old.visible_adherents or old.date_debut is distinct from new.date_debut) then
    perform mettre_en_file('rdv:' || new.id || ':' || new.date_debut,
      case when tg_op = 'UPDATE' and old.visible_adherents and old.date_debut is not null then 'Rendez-vous déplacé · ' else 'Nouveau rendez-vous · ' end || new.nom,
      initcap(left(date_fr(new.date_debut), 1)) || substr(date_fr(new.date_debut), 2)
        || coalesce(' à ' || to_char(new.heure_debut, 'HH24"h"MI'), '') || coalesce(' · ' || new.lieu, ''), null);
  end if;
  return null;
end $$;
create or replace trigger notif_rendez_vous after insert or update of visible_adherents, date_debut on public.projects
  for each row execute function public.notif_rendez_vous();

-- Participation demandée : à tous, ou aux membres choisis
create or replace function public.notif_collecte() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.tous_membres and not new.cloturee then
    perform mettre_en_file('col:' || new.id, 'Participation demandée · ' || new.nom,
      coalesce(replace(to_char(new.montant_attendu, 'FM999999990.00'), '.', ',') || ' € par personne', 'Montant libre')
        || coalesce(', avant le ' || date_fr(new.date_limite), ''), null);
  end if;
  return null;
end $$;
create or replace trigger notif_collecte after insert on public.collectes
  for each row execute function public.notif_collecte();

create or replace function public.notif_collecte_membre() returns trigger
language plpgsql security definer set search_path = public as $$
declare c collectes;
begin
  select * into c from collectes where id = new.collecte_id;
  if c.id is not null and not c.cloturee and not c.tous_membres then
    perform mettre_en_file('col:' || c.id || ':' || new.member_id, 'Participation demandée · ' || c.nom,
      coalesce(replace(to_char(c.montant_attendu, 'FM999999990.00'), '.', ',') || ' €', 'Montant libre') || coalesce(', avant le ' || date_fr(c.date_limite), ''), new.member_id);
  end if;
  return null;
end $$;
create or replace trigger notif_collecte_membre after insert on public.collecte_membres
  for each row execute function public.notif_collecte_membre();

-- Chaque matin : rappel la veille et communiqués programmés ; relance de ce qui n'est pas parti
create or replace function public.notifications_du_matin() returns void
language plpgsql security definer set search_path = public as $$
declare p record; c record;
begin
  for p in select * from projects where visible_adherents and date_debut = current_date + 1 loop
    perform mettre_en_file('veille:' || p.id || ':' || p.date_debut, 'Demain · ' || p.nom,
      coalesce(to_char(p.heure_debut, 'HH24"h"MI'), 'Toute la journée') || coalesce(' · ' || p.lieu, ''), null);
  end loop;
  for c in select * from communiques where visible_adherents and debut = current_date and created_at::date < current_date loop
    perform mettre_en_file('cq:' || c.id, case when c.priorite = 'haute' then 'Important · ' else 'Communiqué · ' end || c.titre,
                           coalesce(split_part(c.texte, E'\n', 1), ''), null);
  end loop;
  if exists (select 1 from notifications_push where envoyee_le is null) then perform appeler_notifier(); end if;
end $$;
revoke execute on function public.notifications_du_matin() from public, anon, authenticated;

do $$ begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    -- 7 h UTC = 9 h à Grenoble l'été, 8 h l'hiver
    perform cron.schedule('notifications-du-matin', '0 7 * * *', 'select public.notifications_du_matin()');
    perform cron.schedule('notifications-relance', '*/15 * * * *',
      $c$select public.appeler_notifier() where exists (select 1 from public.notifications_push where envoyee_le is null and cree_le > now() - interval '2 days')$c$);
  end if;
end $$;
