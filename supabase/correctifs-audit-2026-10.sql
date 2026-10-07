-- =====================================================================
-- Correctifs de l'audit du 7 octobre 2026 (à exécuter une fois dans Supabase > SQL Editor)
-- Sans risque : ne modifie aucune donnée, remplace seulement deux vues de lecture.
-- =====================================================================

-- Participations : un contributeur compte seulement si son versement net reste positif
-- (un versement annulé par contre-passation ne compte plus).
create or replace view public.v_collectes with (security_invoker = true) as
select c.id, c.nom, c.project_id, c.montant_attendu, c.objectif, c.date_limite, c.tous_membres, c.cloturee, c.created_at,
       coalesce((select sum(t.montant) from transactions t where t.collecte_id = c.id), 0) as total_recu,
       (select count(*) from (
          select 1 from transactions t where t.collecte_id = c.id
          group by coalesce(t.member_id::text, t.tiers_id::text, t.contrepasse_de::text, t.id::text)
          having sum(t.montant) > 0) s) as nb_contributeurs,
       case when c.tous_membres then (select count(*) from members m where m.actif)
            else (select count(*) from collecte_membres cm where cm.collecte_id = c.id) end as nb_concernes
from collectes c;

-- Justificatifs en retard : une dépense dont le paiement a été annulé par contre-passation n'en attend plus
create or replace view public.v_justificatifs_en_retard with (security_invoker = true) as
select r.id, r.objet, r.montant, r.demandeur, r.payee_le, (current_date - r.payee_le::date) as jours
from expense_requests r
where r.statut = 'payee'
  and r.payee_le::date + (select valeur::int from settings where cle = 'delai_justificatif_jours') < current_date
  and not exists (select 1 from transactions t join transactions c on c.contrepasse_de = t.id where t.request_id = r.id);
