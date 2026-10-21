-- Anniversaires du mois : la profession n'est plus transmise (ni affichée) ; la colonne reste pour ne pas changer
-- la signature de la fonction (anciennes versions de l'application), mais elle est toujours vide.
create or replace function anniversaires_du_mois(p_mois int default null)
returns table(prenom text, nom text, jour int, photo_path text, profession text)
language sql stable security definer set search_path = public as $$
  select m.prenom,
         case when a_droit('voir_membres') or a_droit('gerer_membres') then m.nom else left(m.nom, 1) || '.' end,
         m.naissance_jour::int, m.photo_path, null::text
  from members m
  where est_connecte() and m.actif and m.naissance_mois = coalesce(p_mois, extract(month from now())::int)
    and (m.consent_anniversaire or a_droit('voir_membres') or a_droit('gerer_membres'))
  order by 3
$$;
