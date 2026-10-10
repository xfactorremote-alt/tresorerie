-- Correctif du 10 octobre 2026 : page du lien personnel des membres
-- Les rendez-vous à venir portent leur identifiant : même couleur que dans le calendrier (couleurEvt)
-- et fichier d'agenda (.ics) avec un identifiant stable.
create or replace function public.situation_par_lien(p_jeton text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare mid uuid; r jsonb;
begin
  if p_jeton is null or length(p_jeton) <> 32 then return null; end if;
  update liens_membres set derniere_consultation = now(), nb_consultations = nb_consultations + 1
   where jeton = p_jeton returning member_id into mid;
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
    'a_venir', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'nom', p.nom, 'date_debut', p.date_debut, 'date_fin', p.date_fin,
                   'heure_debut', p.heure_debut, 'heure_fin', p.heure_fin, 'lieu', p.lieu, 'description', p.description)
                   order by p.date_debut, p.heure_debut nulls first)
                 from (select * from projects p where p.visible_adherents and p.date_debut is not null
                       and coalesce(p.date_fin, p.date_debut) >= current_date and p.date_debut <= current_date + 120
                       order by p.date_debut limit 12) p), '[]'::jsonb)
  ) into r;
  return r;
end $$;
