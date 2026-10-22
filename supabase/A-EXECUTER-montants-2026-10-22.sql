-- =====================================================================
-- À EXÉCUTER PAR LE TRÉSORIER dans Supabase › SQL Editor (une seule fois), après la 0.46.0.
-- Relie les montants par personne d'une participation (collecte_montants) à leur collecte et à leur membre :
-- quand l'un part à la corbeille, ses montants partent avec lui (une copie est gardée dans la corbeille
-- et « Restaurer » les remet). Seuls d’éventuels montants orphelins (collecte ou membre déjà effacés) sont retirés.
-- =====================================================================
delete from public.collecte_montants x
 where not exists (select 1 from public.collectes c where c.id = x.collecte_id)
    or not exists (select 1 from public.members m where m.id = x.member_id);
alter table public.collecte_montants
  add constraint collecte_montants_collecte_id_fkey foreign key (collecte_id) references public.collectes(id) on delete cascade,
  add constraint collecte_montants_member_id_fkey foreign key (member_id) references public.members(id) on delete cascade;
