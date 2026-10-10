-- =====================================================================
-- À EXÉCUTER UNE FOIS dans Supabase : SQL Editor > New query > coller tout ce fichier > Run
-- (10 octobre 2026). Active le bouton « Supprimer » (corbeille réversible) du site et de l'application.
-- Sans danger : ne supprime rien, crée seulement la fonction « supprimer » qui place les éléments
-- dans la corbeille. Le reste de la mise à jour du 9 octobre est déjà installé.
-- =====================================================================

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

grant execute on function public.supprimer(text, uuid, text) to authenticated;
revoke execute on function public.supprimer(text, uuid, text) from anon;
select 'Corbeille activée' as resultat;
