-- =====================================================================
-- Rappels de cotisation / participation, au nom de l'association (18 octobre 2026)
-- - Le message part de l'application (notification « Un petit mot de JP Grenoble »),
--   et non plus du numéro WhatsApp personnel de la personne qui relance.
-- - Chaque rappel est noté (canal, date, auteur) : l'écran prévient quand un rappel
--   a déjà été envoyé il y a moins de 30 jours, pour ne pas être insistant.
-- =====================================================================

create table if not exists public.rappels (
  id uuid primary key default gen_random_uuid(),
  member_id uuid not null,      -- sans clé étrangère : un membre mis à la corbeille puis restauré retrouve ses rappels
  collecte_id uuid,
  canal text not null check (canal in ('notification', 'whatsapp', 'copie')),
  texte text check (length(texte) <= 1500),
  par uuid references public.profiles(id) default auth.uid(),
  le timestamptz not null default now()
);
create index if not exists rappels_membre on public.rappels(member_id, le desc);
alter table public.rappels enable row level security;
create policy "rappels lecture" on public.rappels for select
  using (public.a_droit('gerer_cotisations') or public.a_droit('consulter_finances'));
grant select on public.rappels to authenticated;
-- Pas d’écriture directe : seulement par envoyer_rappel()

-- Envoie (notification) ou note (WhatsApp, copie) un rappel ; renvoie le nombre d'appareils joints
create or replace function public.envoyer_rappel(p_member uuid, p_canal text, p_texte text, p_collecte uuid default null)
returns int language plpgsql security definer set search_path = public as $$
declare n int := 0; rid uuid; asso text;
begin
  if not public.a_droit('gerer_cotisations') then raise exception 'Accès refusé'; end if;
  if p_canal not in ('notification', 'whatsapp', 'copie') then raise exception 'Canal inconnu'; end if;
  if not exists (select 1 from members where id = p_member and actif) then raise exception 'Membre introuvable ou inactif'; end if;
  if coalesce(length(trim(p_texte)), 0) = 0 then raise exception 'Le message est vide'; end if;
  if p_canal = 'notification' then
    select count(*) into n from abonnements_push where member_id = p_member and actif;
    if n = 0 then raise exception 'Ce membre n’a pas activé les alertes : envoyez le message par WhatsApp ou copiez-le'; end if;
  end if;
  insert into rappels(member_id, collecte_id, canal, texte) values (p_member, p_collecte, p_canal, left(p_texte, 1500)) returning id into rid;
  if p_canal = 'notification' then
    select coalesce(nullif(trim(sigle), ''), nom) into asso from organisation where id = 1;
    insert into notifications_push(cle, titre, texte, member_id)
      values ('rappel-' || rid, 'Un petit mot de ' || coalesce(asso, 'l’association'), left(p_texte, 400), p_member);
    perform appeler_notifier();
  end if;
  return n;
end $$;
revoke execute on function public.envoyer_rappel(uuid, text, text, uuid) from public, anon;
grant execute on function public.envoyer_rappel(uuid, text, text, uuid) to authenticated;

-- Pour l'écran : qui peut recevoir une notification, et date du dernier rappel (cotisation ou participation)
create or replace function public.etat_rappels(p_collecte uuid default null)
returns table(member_id uuid, appareils int, dernier timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
  if not (public.a_droit('gerer_cotisations') or public.a_droit('consulter_finances')) then raise exception 'Accès refusé'; end if;
  return query select m.id,
    (select count(*)::int from abonnements_push a where a.member_id = m.id and a.actif),
    (select max(r.le) from rappels r where r.member_id = m.id and r.collecte_id is not distinct from p_collecte)
  from members m where m.actif;
end $$;
revoke execute on function public.etat_rappels(uuid) from public, anon;
grant execute on function public.etat_rappels(uuid) to authenticated;

-- Temps réel : l'écran des cotisations voit aussitôt le rappel envoyé par un autre membre du bureau
alter publication supabase_realtime add table public.rappels;
