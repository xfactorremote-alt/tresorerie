-- =====================================================================
-- Trésorerie d'association - schéma Supabase (PostgreSQL), offre gratuite
-- Version 3 : rôles et droits configurables par l'administrateur
-- À exécuter en une fois dans Supabase > SQL Editor, sur un projet vide.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------- Types ----------
create type sens_flux      as enum ('recette','depense');
create type statut_demande as enum ('brouillon','soumise','validee','refusee','payee','justifiee','annulee');
create type type_compte    as enum ('caisse','banque');
create type mode_paiement  as enum ('especes','virement','autre');

-- =====================================================================
-- 1. Rôles et droits (contrôle d'accès par rôle, modifiable)
-- =====================================================================
-- Liste fermée des droits : chaque fonction de l'application en vérifie un.
create table permissions (
  code text primary key,
  libelle text not null,
  groupe text not null,
  ordre int not null
);
insert into permissions(code, libelle, groupe, ordre) values
 ('consulter_finances', 'Voir les soldes, écritures, budget et rapports', 'Finances', 1),
 ('saisir_ecritures',   'Saisir et corriger les écritures, joindre les pièces', 'Finances', 2),
 ('gerer_cotisations',  'Cotisations et participations : générer, encaisser, relancer', 'Finances', 3),
 ('rapprocher',         'Rapprocher la caisse et la banque', 'Finances', 4),
 ('gerer_budget',       'Construire et modifier le budget', 'Finances', 5),
 ('demander_depenses',  'Demander une dépense', 'Dépenses', 6),
 ('valider_depenses',   'Valider ou refuser une dépense (signature)', 'Dépenses', 7),
 ('payer_depenses',     'Payer une dépense validée', 'Dépenses', 8),
 ('voir_membres',       'Voir la liste des membres', 'Membres', 9),
 ('gerer_membres',      'Ajouter, modifier et importer des membres', 'Membres', 10),
 ('gerer_activites',    'Créer et modifier les activités et le planning', 'Activités', 11),
 ('administrer',        'Paramètres, rôles et accès', 'Administration', 12);

create table roles (
  code text primary key,
  nom text not null unique,
  systeme boolean not null default false,   -- rôles livrés : renommables, pas supprimables
  created_at timestamptz not null default now()
);
insert into roles(code, nom, systeme) values
 ('tresorier', 'Trésorier', true), ('president', 'Président', true),
 ('bureau', 'Bureau', true), ('adherent', 'Adhérent', true);

create table role_permissions (
  role text not null references roles(code) on update cascade on delete cascade,
  permission text not null references permissions(code) on delete cascade,
  primary key (role, permission)
);
-- Répartition de départ : le trésorier administre et paie, le président valide
insert into role_permissions(role, permission)
select 'tresorier', code from permissions where code <> 'valider_depenses';
insert into role_permissions(role, permission) values
 ('president','consulter_finances'), ('president','demander_depenses'), ('president','valider_depenses'), ('president','voir_membres'),
 ('bureau','consulter_finances'), ('bureau','demander_depenses'), ('bureau','voir_membres');

-- ---------- Utilisateurs (liés à Supabase Auth) ----------
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  nom text not null,
  role text not null default 'adherent' references roles(code) on update cascade,
  member_id uuid,
  actif boolean not null default true,
  created_at timestamptz not null default now()
);

-- La personne connectée a-t-elle ce droit ? (utilisé par toutes les règles)
create or replace function a_droit(p_permission text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from profiles p join role_permissions rp on rp.role = p.role
    where p.id = auth.uid() and p.actif and rp.permission = p_permission)
$$;

create or replace function est_connecte() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from profiles where id = auth.uid() and actif)
$$;

-- Droits de la personne connectée, pour adapter l'interface
create or replace function mes_droits() returns text[]
language sql stable security definer set search_path = public as $$
  select coalesce(array_agg(rp.permission order by rp.permission), '{}')
  from profiles p join role_permissions rp on rp.role = p.role
  where p.id = auth.uid() and p.actif
$$;

-- Garde-fou : il reste toujours au moins une personne active qui peut administrer
create or replace function garde_administrateur() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from profiles) and not exists (
    select 1 from profiles p join role_permissions rp on rp.role = p.role
    where p.actif and rp.permission = 'administrer') then
    raise exception 'Au moins une personne active doit garder le droit « administrer »';
  end if;
  return null;
end $$;

-- =====================================================================
-- 2. Données de l'association
-- =====================================================================
create table organisation (
  id int primary key default 1 check (id = 1),
  nom text not null default 'JP Grenoble',
  logo_path text,
  banniere_path text,                 -- photo de la bannière d'accueil (bucket logos)
  devise text not null default 'EUR',
  exercice_debut date not null default date_trunc('year', now())::date
);
insert into organisation default values;

create table settings (
  cle text primary key,
  valeur numeric,
  texte text,
  description text
);
insert into settings(cle, valeur, description) values
 ('cotisation_montant', 20, 'Montant de la cotisation par période, en euros'),
 ('cotisation_periode_mois', 1, 'Périodicité de la cotisation en mois : 1, 3, 6 ou 12'),
 ('delai_justificatif_jours', 7, 'Délai pour fournir le justificatif après paiement'),
 ('seuil_alerte_budget_pct', 90, 'Alerte quand le réalisé atteint ce pourcentage du prévu');

create table members (
  id uuid primary key default gen_random_uuid(),
  prenom text not null,
  nom text not null,
  naissance_jour smallint not null check (naissance_jour between 1 and 31),   -- jour et mois, sans l'année
  naissance_mois smallint not null check (naissance_mois between 1 and 12),
  profession text,
  whatsapp text,
  email text,
  photo_path text,
  consent_anniversaire boolean not null default false,
  date_adhesion date,
  actif boolean not null default true,
  created_at timestamptz not null default now()
);
alter table profiles add constraint fk_profiles_member foreign key (member_id) references members(id);

-- Une ligne par membre et par période (mois, trimestre, semestre ou année).
-- Montant à 0 : membre dispensé pour cette période.
create table cotisations (
  id uuid primary key default gen_random_uuid(),
  member_id uuid not null references members(id) on delete cascade,
  periode date not null check (extract(day from periode) = 1),
  montant_du numeric(14,2) not null check (montant_du >= 0),
  unique (member_id, periode)
);

create table accounts (
  id uuid primary key default gen_random_uuid(),
  nom text not null,
  type type_compte not null,
  solde_initial numeric(14,2) not null default 0,
  actif boolean not null default true
);
insert into accounts(nom, type) values ('Caisse (espèces)','caisse'), ('Banque','banque');

create table categories (
  id uuid primary key default gen_random_uuid(),
  nom text not null,
  sens sens_flux not null,
  parent_id uuid references categories(id),
  unique (nom, sens)
);
insert into categories(nom, sens) values
 ('Cotisations','recette'),('Dons','recette'),('Offrandes dédiées','recette'),
 ('Activités / événements','recette'),('Autres recettes','recette'),
 ('Fonctionnement','depense'),('Activités / événements','depense'),
 ('Aides et solidarité','depense'),('Matériel','depense'),('Autres dépenses','depense');

-- Tiers hors membres : donateurs, fournisseurs, partenaires
create table tiers (
  id uuid primary key default gen_random_uuid(),
  nom text not null unique,
  type text not null default 'autre' check (type in ('donateur','fournisseur','partenaire','autre')),
  telephone text, email text, notes text,
  actif boolean not null default true,
  created_at timestamptz not null default now()
);

create table projects (          -- activités, événements, projets (planning)
  id uuid primary key default gen_random_uuid(),
  nom text not null,
  type text not null default 'activite' check (type in ('activite','evenement')),
  date_debut date, date_fin date,
  heure_debut time, heure_fin time,
  lieu text,
  visible_adherents boolean not null default false,
  description text,
  check (date_fin is null or date_debut is null or date_fin >= date_debut)
);

-- Appel à participation : contribution des membres à une activité, un événement ou un projet
create table collectes (
  id uuid primary key default gen_random_uuid(),
  nom text not null,
  project_id uuid references projects(id) on delete set null,
  montant_attendu numeric(14,2) check (montant_attendu is null or montant_attendu > 0),   -- par personne, facultatif
  objectif numeric(14,2) check (objectif is null or objectif > 0),                       -- total visé, facultatif
  date_limite date,
  tous_membres boolean not null default true,      -- sinon : liste dans collecte_membres
  cloturee boolean not null default false,
  created_at timestamptz not null default now()
);
create table collecte_membres (
  collecte_id uuid not null references collectes(id) on delete cascade,
  member_id uuid not null references members(id) on delete cascade,
  primary key (collecte_id, member_id)
);

create table budgets (
  id uuid primary key default gen_random_uuid(),
  annee int not null,
  category_id uuid not null references categories(id),
  project_id uuid references projects(id),
  montant_prevu numeric(14,2) not null check (montant_prevu >= 0),
  seuil_alerte_pct int not null default 90,
  unique nulls not distinct (annee, category_id, project_id)
);

create table expense_requests (
  id uuid primary key default gen_random_uuid(),
  demandeur uuid not null references profiles(id),
  objet text not null,
  montant numeric(14,2) not null check (montant > 0),
  category_id uuid not null references categories(id),
  project_id uuid references projects(id),
  account_id uuid references accounts(id),
  statut statut_demande not null default 'brouillon',
  validee_par uuid references profiles(id),
  validee_le timestamptz,
  signature_path text,
  signature_hash text,                 -- SHA-256 (demande + montant + objet + date + signataire)
  motif_refus text,
  payee_par uuid references profiles(id),
  payee_le timestamptz,
  created_at timestamptz not null default now()
);

create table reconciliations (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references accounts(id),
  periode_debut date not null,
  periode_fin date not null check (periode_fin >= periode_debut),
  solde_releve numeric(14,2) not null,
  statement_path text,
  statement_name text,
  statement_ko int,
  statut text not null default 'en_cours' check (statut in ('en_cours','termine')),
  ecart numeric(14,2),
  termine_par uuid references profiles(id),
  termine_le timestamptz,
  created_at timestamptz not null default now(),
  constraint releve_obligatoire check (statut <> 'termine' or statement_path is not null)
);

create table transactions (
  id uuid primary key default gen_random_uuid(),
  date_op date not null,
  account_id uuid not null references accounts(id),
  sens sens_flux not null,
  montant numeric(14,2) not null check (montant <> 0),   -- négatif uniquement pour une contre-passation
  category_id uuid not null references categories(id),
  project_id uuid references projects(id),
  libelle text not null,
  contrepasse_de uuid unique references transactions(id),
  mode mode_paiement not null default 'especes',
  member_id uuid references members(id),          -- tiers membre
  tiers_id uuid references tiers(id),             -- tiers hors membres
  est_cotisation boolean not null default false,  -- rubrique « cotisation » : imputée sur la période la plus ancienne non réglée
  collecte_id uuid references collectes(id),      -- rubrique « participation »
  request_id uuid references expense_requests(id),
  reconciliation_id uuid references reconciliations(id),
  rapproche boolean not null default false,
  date_rapprochement date,
  created_by uuid references profiles(id),
  created_at timestamptz not null default now(),
  constraint montant_negatif_si_contrepassation check (montant > 0 or contrepasse_de is not null),
  constraint un_seul_tiers check (member_id is null or tiers_id is null),
  constraint cotisation_d_un_membre check (not est_cotisation or (member_id is not null and sens = 'recette' and collecte_id is null)),
  constraint participation_en_recette check (collecte_id is null or sens = 'recette')
);
create index on transactions(member_id);
create index on transactions(collecte_id);
create index on transactions(date_op);
create index on transactions(account_id);
create index on transactions(category_id);

create table attachments (
  id uuid primary key default gen_random_uuid(),
  transaction_id uuid references transactions(id) on delete cascade,
  request_id uuid references expense_requests(id) on delete cascade,
  storage_path text not null,
  mime text, taille_ko int,
  depose_par uuid references profiles(id),
  created_at timestamptz not null default now(),
  check (transaction_id is not null or request_id is not null)
);

create table invitations (
  email text primary key,
  nom text,
  role text not null default 'adherent' references roles(code) on update cascade,
  member_id uuid references members(id),
  created_at timestamptz not null default now()
);

create table audit_log (
  id bigserial primary key,
  at timestamptz not null default now(),
  user_id uuid, action text, table_name text, row_id text, detail jsonb
);

-- =====================================================================
-- 3. Vues (respectent les droits de la personne connectée)
-- =====================================================================
create view v_soldes with (security_invoker = true) as
select a.id, a.nom, a.type, a.solde_initial,
       a.solde_initial + coalesce(sum(case when t.sens='recette' then t.montant else -t.montant end),0) as solde
from accounts a left join transactions t on t.account_id = a.id
where a.actif
group by a.id;

-- Cotisations par période. Les versements d'un membre sont imputés sur la période
-- la plus ancienne non réglée (usage courant, comme l'article 1342-10 du Code civil).
create view v_cotisations_periodes with (security_invoker = true) as
with dues as (
  select c.member_id, c.periode, c.montant_du,
         coalesce(sum(c.montant_du) over (partition by c.member_id order by c.periode
                  rows between unbounded preceding and 1 preceding), 0) as avant
  from cotisations c),
paye as (
  select member_id, sum(montant) as total from transactions where est_cotisation group by member_id)
select d.member_id, d.periode, extract(year from d.periode)::int as annee, d.montant_du,
       least(d.montant_du, greatest(0, coalesce(p.total, 0) - d.avant)) as regle,
       case when d.montant_du = 0 then 'dispense'
            when coalesce(p.total, 0) - d.avant >= d.montant_du then 'regle'
            when coalesce(p.total, 0) - d.avant > 0 then 'partiel'
            when d.periode > current_date then 'a_venir'
            else 'impaye' end as statut
from dues d left join paye p on p.member_id = d.member_id;

-- Synthèse par membre et par année. « Exigible » : périodes déjà commencées.
-- À jour : tout l'exigible est réglé. Avance : versé au-delà de toutes les périodes générées.
create view v_cotisations with (security_invoker = true) as
select v.member_id, v.annee,
       sum(v.montant_du) as montant_du,
       sum(v.regle) as montant_paye,
       sum(v.montant_du) - sum(v.regle) as reste,
       sum(v.montant_du) filter (where v.periode <= current_date) as exigible,
       greatest(0, coalesce(sum(v.montant_du) filter (where v.periode <= current_date), 0) - sum(v.regle)) as retard,
       case when sum(v.regle) >= coalesce(sum(v.montant_du) filter (where v.periode <= current_date), 0) then 'a_jour'
            when sum(v.regle) > 0 then 'partiel' else 'impaye' end as statut,
       max(v.periode) filter (where v.statut in ('regle','dispense')) as regle_jusqu_a,
       greatest(0, coalesce((select sum(t.montant) from transactions t where t.est_cotisation and t.member_id = v.member_id), 0)
                 - (select sum(c.montant_du) from cotisations c where c.member_id = v.member_id)) as avance
from v_cotisations_periodes v
group by v.member_id, v.annee;

create view v_collectes with (security_invoker = true) as
select c.id, c.nom, c.project_id, c.montant_attendu, c.objectif, c.date_limite, c.tous_membres, c.cloturee, c.created_at,
       coalesce(sum(t.montant), 0) as total_recu,
       count(distinct coalesce(t.member_id::text, t.tiers_id::text, t.id::text)) filter (where t.id is not null) as nb_contributeurs,
       case when c.tous_membres then (select count(*) from members m where m.actif)
            else (select count(*) from collecte_membres cm where cm.collecte_id = c.id) end as nb_concernes
from collectes c left join transactions t on t.collecte_id = c.id
group by c.id;

create view v_budget_suivi with (security_invoker = true) as
select b.id as budget_id, b.annee, b.category_id, cat.nom as categorie, cat.sens, b.project_id,
       b.montant_prevu,
       coalesce(sum(t.montant),0) as realise,
       b.montant_prevu - coalesce(sum(t.montant),0) as ecart,
       case when b.montant_prevu > 0 then round(100*coalesce(sum(t.montant),0)/b.montant_prevu,1) end as taux_pct,
       (cat.sens='depense' and coalesce(sum(t.montant),0) >= b.montant_prevu*b.seuil_alerte_pct/100.0) as alerte
from budgets b join categories cat on cat.id = b.category_id
left join transactions t on t.category_id = b.category_id
  and extract(year from t.date_op) = b.annee
  and (b.project_id is null or t.project_id = b.project_id)
group by b.id, cat.nom, cat.sens;

create view v_justificatifs_en_retard with (security_invoker = true) as
select r.id, r.objet, r.montant, r.demandeur, r.payee_le, (current_date - r.payee_le::date) as jours
from expense_requests r
where r.statut = 'payee'
  and r.payee_le::date + (select valeur::int from settings where cle = 'delai_justificatif_jours') < current_date;

-- =====================================================================
-- 4. Règles métier (déclencheurs)
-- =====================================================================
-- Paiement uniquement après validation
create or replace function check_paiement() returns trigger
language plpgsql as $$
begin
  if new.request_id is not null and new.sens = 'depense' and not exists (
       select 1 from expense_requests r where r.id = new.request_id and r.statut in ('validee','payee','justifiee')) then
    raise exception 'Décaissement refusé : demande non validée';
  end if;
  return new;
end $$;
create trigger trg_check_paiement before insert on transactions for each row execute function check_paiement();

-- Circuit : demande -> validation signée -> paiement -> justificatif
create or replace function check_transition() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.statut = old.statut then return new; end if;
  if old.statut = 'soumise' and new.statut in ('validee','refusee') then
    if not a_droit('valider_depenses') then raise exception 'Droit « valider les dépenses » requis'; end if;
    if new.statut = 'validee' then
      if new.signature_path is null then raise exception 'Signature obligatoire'; end if;
      new.validee_par := auth.uid(); new.validee_le := now();
    end if;
  elsif old.statut = 'validee' and new.statut = 'payee' then
    if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
    if old.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
    new.payee_par := auth.uid(); new.payee_le := now();
  elsif old.statut = 'payee' and new.statut = 'justifiee' then
    if not (a_droit('saisir_ecritures') or a_droit('payer_depenses') or old.demandeur = auth.uid()) then
      raise exception 'Le justificatif est déposé par le demandeur ou par la personne qui paie';
    end if;
    if not exists (select 1 from attachments a where a.request_id = old.id) then raise exception 'Justificatif manquant'; end if;
  elsif old.statut = 'soumise' and new.statut = 'annulee' then
    if old.demandeur <> auth.uid() then raise exception 'Seul le demandeur annule sa demande'; end if;
  else
    raise exception 'Changement de statut non autorisé';
  end if;
  return new;
end $$;
create trigger trg_check_transition before update on expense_requests for each row execute function check_transition();

-- Rapprochement terminé : non modifiable
create or replace function check_rapprochement() returns trigger
language plpgsql as $$
begin
  if old.statut = 'termine' then raise exception 'Rapprochement terminé : modification impossible'; end if;
  if new.statut = 'termine' then
    if coalesce(new.ecart, 1) <> 0 then raise exception 'Rapprochement impossible : écart non nul'; end if;
    new.termine_par := auth.uid(); new.termine_le := now();
  end if;
  return new;
end $$;
create trigger trg_check_rapprochement before update on reconciliations for each row execute function check_rapprochement();

-- Écriture rapprochée : verrouillée, correction par contre-passation
create or replace function lock_reconciled() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if old.reconciliation_id is not null and exists (
       select 1 from reconciliations r where r.id = old.reconciliation_id and r.statut = 'termine') then
    raise exception 'Écriture rapprochée : modification impossible';
  end if;
  return coalesce(new, old);
end $$;
create trigger trg_lock_reconciled before update or delete on transactions for each row execute function lock_reconciled();

-- Toujours un administrateur actif
create trigger trg_garde_admin_profils after update or delete on profiles
for each statement execute function garde_administrateur();
create trigger trg_garde_admin_droits after delete or update on role_permissions
for each statement execute function garde_administrateur();

-- Comptes : premier compte = administrateur (rôle trésorier), ensuite sur invitation uniquement
create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from profiles) then
    insert into profiles(id, nom, role) values (new.id, new.email, 'tresorier');
    return new;
  end if;
  insert into profiles(id, nom, role, member_id)
  select new.id, coalesce(i.nom, new.email), i.role, i.member_id
  from invitations i where lower(i.email) = lower(new.email);
  delete from invitations where lower(email) = lower(new.email);
  return new;
end $$;
create trigger trg_new_user after insert on auth.users for each row execute function handle_new_user();

-- Journal d'audit
create or replace function log_change() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into audit_log(user_id, action, table_name, row_id, detail)
  values (auth.uid(), tg_op, tg_table_name,
          coalesce(to_jsonb(new)->>'id', to_jsonb(old)->>'id', to_jsonb(new)->>'cle', to_jsonb(old)->>'cle', to_jsonb(new)->>'code', to_jsonb(old)->>'code'),
          jsonb_build_object('avant', to_jsonb(old), 'apres', to_jsonb(new)));
  return coalesce(new, old);
end $$;
create trigger audit_settings  after insert or update or delete on settings         for each row execute function log_change();
create trigger audit_profiles  after insert or update or delete on profiles         for each row execute function log_change();
create trigger audit_roles     after insert or update or delete on roles            for each row execute function log_change();
create trigger audit_droits    after insert or delete on role_permissions           for each row execute function log_change();
create trigger audit_budgets   after insert or update or delete on budgets          for each row execute function log_change();
create trigger audit_org       after update on organisation                         for each row execute function log_change();
create trigger audit_trans     after insert or update or delete on transactions     for each row execute function log_change();
create trigger audit_demandes  after insert or update on expense_requests           for each row execute function log_change();
create trigger audit_reconcil  after insert or update on reconciliations            for each row execute function log_change();
create trigger audit_cotis     after insert or update or delete on cotisations      for each row execute function log_change();
create trigger audit_collectes after insert or update or delete on collectes        for each row execute function log_change();
create trigger audit_tiers     after insert or update or delete on tiers            for each row execute function log_change();

-- =====================================================================
-- 5. Opérations atomiques
-- =====================================================================
-- Crée les périodes de l'année pour chaque membre actif, à partir de son mois d'adhésion.
-- Sans effet sur les périodes déjà créées (montant modifié ou dispense conservés).
create or replace function generer_cotisations(an int) returns int
language plpgsql security definer set search_path = public as $$
declare n int; pas int; montant numeric;
begin
  if not a_droit('gerer_cotisations') then raise exception 'Droit « gérer les cotisations » requis'; end if;
  select valeur::int into pas from settings where cle = 'cotisation_periode_mois';
  select valeur into montant from settings where cle = 'cotisation_montant';
  if pas is null or pas not in (1, 3, 6, 12) then pas := 1; end if;
  insert into cotisations(member_id, periode, montant_du)
  select m.id, p::date, montant
  from members m
  cross join generate_series(make_date(an, 1, 1), make_date(an, 12, 1), make_interval(months => pas)) p
  where m.actif and (m.date_adhesion is null or m.date_adhesion < p + make_interval(months => pas))
  on conflict (member_id, periode) do nothing;
  get diagnostics n = row_count;
  return n;
end $$;

create or replace function payer_demande(p_id uuid, p_compte uuid, p_mode mode_paiement, p_date date)
returns uuid language plpgsql security definer set search_path = public as $$
declare r expense_requests; t_id uuid;
begin
  if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
  select * into r from expense_requests where id = p_id for update;
  if r.statut <> 'validee' then raise exception 'Demande non validée'; end if;
  if r.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
  insert into transactions(date_op, account_id, sens, montant, category_id, project_id, libelle, mode, request_id, created_by)
  values (p_date, p_compte, 'depense', r.montant, r.category_id, r.project_id, r.objet, p_mode, r.id, auth.uid())
  returning id into t_id;
  update expense_requests set statut = 'payee', account_id = p_compte where id = p_id;
  return t_id;
end $$;

create or replace function justifier_demande(p_id uuid, p_chemin text, p_mime text, p_ko int)
returns void language plpgsql security definer set search_path = public as $$
declare r expense_requests; t_id uuid;
begin
  select * into r from expense_requests where id = p_id for update;
  if r.id is null then raise exception 'Demande introuvable'; end if;
  if not (a_droit('saisir_ecritures') or a_droit('payer_depenses') or r.demandeur = auth.uid()) then
    raise exception 'Le justificatif est déposé par le demandeur ou par la personne qui paie';
  end if;
  if r.statut <> 'payee' then raise exception 'La demande doit être payée avant le justificatif'; end if;
  select id into t_id from transactions where request_id = p_id and contrepasse_de is null order by created_at limit 1;
  insert into attachments(request_id, transaction_id, storage_path, mime, taille_ko, depose_par)
  values (p_id, t_id, p_chemin, p_mime, p_ko, auth.uid());
  update expense_requests set statut = 'justifiee' where id = p_id;
end $$;

create or replace function solde_pointe(p_compte uuid) returns numeric
language sql stable security definer set search_path = public as $$
  select a.solde_initial + coalesce((
    select sum(case when t.sens = 'recette' then t.montant else -t.montant end)
    from transactions t join reconciliations r on r.id = t.reconciliation_id
    where t.account_id = p_compte and r.statut = 'termine'), 0)
  from accounts a where a.id = p_compte
    and (a_droit('rapprocher') or a_droit('consulter_finances'))
$$;

create or replace function terminer_rapprochement(
  p_compte uuid, p_debut date, p_fin date, p_solde_releve numeric,
  p_chemin text, p_nom text, p_ko int, p_ecritures uuid[])
returns uuid language plpgsql security definer set search_path = public as $$
declare rec_id uuid; pointe numeric; ecart numeric;
begin
  if not a_droit('rapprocher') then raise exception 'Droit « rapprocher » requis'; end if;
  if p_chemin is null then raise exception 'Relevé de la période obligatoire'; end if;
  if exists (select 1 from transactions where id = any(p_ecritures)
             and (account_id <> p_compte or reconciliation_id is not null or date_op > p_fin)) then
    raise exception 'Écriture d''un autre compte, déjà rapprochée ou postérieure à la période';
  end if;
  pointe := solde_pointe(p_compte) + coalesce((
    select sum(case when sens = 'recette' then montant else -montant end) from transactions where id = any(p_ecritures)), 0);
  ecart := p_solde_releve - pointe;
  if ecart <> 0 then raise exception 'Écart de % € entre le relevé et les écritures pointées', ecart; end if;
  insert into reconciliations(account_id, periode_debut, periode_fin, solde_releve, statement_path, statement_name, statement_ko, ecart)
  values (p_compte, p_debut, p_fin, p_solde_releve, p_chemin, p_nom, p_ko, 0) returning id into rec_id;
  update transactions set reconciliation_id = rec_id, rapproche = true, date_rapprochement = p_fin where id = any(p_ecritures);
  update reconciliations set statut = 'termine' where id = rec_id;
  return rec_id;
end $$;

-- Ce que tout membre connecté voit, sans accès aux tables
create or replace function anniversaires_du_mois(p_mois int default null)
returns table(prenom text, nom text, jour int, photo_path text, profession text)
language sql stable security definer set search_path = public as $$
  select m.prenom,
         case when a_droit('voir_membres') or a_droit('gerer_membres') then m.nom else left(m.nom, 1) || '.' end,
         m.naissance_jour::int, m.photo_path, m.profession
  from members m
  where est_connecte() and m.actif and m.naissance_mois = coalesce(p_mois, extract(month from now())::int)
    and (m.consent_anniversaire or a_droit('voir_membres') or a_droit('gerer_membres'))
  order by 3
$$;

-- Cotisation de la personne connectée, période par période
create or replace function ma_cotisation()
returns table(periode date, annee int, montant_du numeric, regle numeric, statut text)
language sql stable security definer set search_path = public as $$
  select v.periode, v.annee, v.montant_du, v.regle, v.statut
  from v_cotisations_periodes v join profiles p on p.member_id = v.member_id
  where p.id = auth.uid() and p.actif
  order by v.periode desc
$$;

-- Participations demandées à la personne connectée et ce qu'elle a donné
create or replace function mes_participations()
returns table(collecte_id uuid, nom text, montant_attendu numeric, donne numeric, date_limite date, cloturee boolean)
language sql stable security definer set search_path = public as $$
  select c.id, c.nom, c.montant_attendu, coalesce(sum(t.montant), 0), c.date_limite, c.cloturee
  from profiles p
  join collectes c on c.tous_membres or exists (select 1 from collecte_membres cm where cm.collecte_id = c.id and cm.member_id = p.member_id)
  left join transactions t on t.collecte_id = c.id and t.member_id = p.member_id
  where p.id = auth.uid() and p.actif and p.member_id is not null
  group by c.id
  having not c.cloturee or coalesce(sum(t.montant), 0) <> 0
  order by c.created_at desc
$$;

-- Planning (calendrier et agenda) : activités et événements visibles par la personne connectée
create or replace function planning_activites(p_debut date default null, p_fin date default null)
returns table(id uuid, nom text, type text, date_debut date, date_fin date, heure_debut time, heure_fin time,
              lieu text, description text, visible_adherents boolean, participation numeric, collecte_id uuid)
language sql stable security definer set search_path = public as $$
  select p.id, p.nom, p.type, p.date_debut, p.date_fin, p.heure_debut, p.heure_fin, p.lieu, p.description, p.visible_adherents,
         c.montant_attendu, c.id
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

-- =====================================================================
-- 6. Règles d'accès (RLS) : chaque table vérifie un droit
-- =====================================================================
alter table permissions       enable row level security;
alter table roles             enable row level security;
alter table role_permissions  enable row level security;
alter table profiles          enable row level security;
alter table organisation      enable row level security;
alter table settings          enable row level security;
alter table members           enable row level security;
alter table cotisations       enable row level security;
alter table accounts          enable row level security;
alter table categories        enable row level security;
alter table projects          enable row level security;
alter table tiers             enable row level security;
alter table collectes         enable row level security;
alter table collecte_membres  enable row level security;
alter table budgets           enable row level security;
alter table expense_requests  enable row level security;
alter table reconciliations   enable row level security;
alter table transactions      enable row level security;
alter table attachments       enable row level security;
alter table invitations       enable row level security;
alter table audit_log         enable row level security;

-- Rôles et droits : lisibles par tout membre connecté, modifiables par l'administration
create policy lecture on permissions      for select using (est_connecte());
create policy lecture on roles            for select using (est_connecte());
create policy lecture on role_permissions for select using (est_connecte());
create policy admin   on roles            for all using (a_droit('administrer')) with check (a_droit('administrer'));
create policy admin   on role_permissions for all using (a_droit('administrer')) with check (a_droit('administrer'));

create policy lecture on profiles for select using (
  id = auth.uid() or a_droit('administrer') or a_droit('consulter_finances') or a_droit('valider_depenses') or a_droit('payer_depenses'));
create policy admin   on profiles for all using (a_droit('administrer')) with check (a_droit('administrer'));
create policy admin   on invitations for all using (a_droit('administrer')) with check (a_droit('administrer'));

create policy lecture on organisation for select using (true);   -- nom et logo visibles dès la connexion
create policy admin   on organisation for all using (a_droit('administrer')) with check (a_droit('administrer'));
create policy lecture on settings for select using (est_connecte());
create policy admin   on settings for all using (a_droit('administrer')) with check (a_droit('administrer'));
create policy lecture on categories for select using (est_connecte());
create policy admin   on categories for all using (a_droit('administrer')) with check (a_droit('administrer'));
create policy lecture on accounts for select using (
  a_droit('consulter_finances') or a_droit('saisir_ecritures') or a_droit('payer_depenses') or a_droit('gerer_cotisations') or a_droit('rapprocher'));
create policy admin   on accounts for all using (a_droit('administrer')) with check (a_droit('administrer'));

create policy lecture on members for select using (
  a_droit('voir_membres') or a_droit('gerer_membres') or a_droit('gerer_cotisations')
  or id = (select member_id from profiles where id = auth.uid()));
create policy gestion on members for all using (a_droit('gerer_membres')) with check (a_droit('gerer_membres'));

create policy lecture on cotisations for select using (
  a_droit('consulter_finances') or a_droit('gerer_cotisations')
  or member_id = (select member_id from profiles where id = auth.uid()));
create policy gestion on cotisations for all using (a_droit('gerer_cotisations')) with check (a_droit('gerer_cotisations'));

create policy lecture on projects for select using (
  (est_connecte() and visible_adherents) or a_droit('gerer_activites') or a_droit('consulter_finances') or a_droit('demander_depenses'));
create policy gestion on projects for all using (a_droit('gerer_activites')) with check (a_droit('gerer_activites'));

create policy lecture on tiers for select using (
  a_droit('consulter_finances') or a_droit('saisir_ecritures') or a_droit('gerer_cotisations') or a_droit('payer_depenses'));
create policy gestion on tiers for all using (a_droit('saisir_ecritures') or a_droit('gerer_cotisations'))
  with check (a_droit('saisir_ecritures') or a_droit('gerer_cotisations'));

create policy lecture on collectes for select using (
  a_droit('consulter_finances') or a_droit('gerer_cotisations') or a_droit('gerer_activites') or a_droit('saisir_ecritures'));
create policy gestion on collectes for all using (a_droit('gerer_activites') or a_droit('gerer_cotisations'))
  with check (a_droit('gerer_activites') or a_droit('gerer_cotisations'));
create policy lecture on collecte_membres for select using (
  a_droit('consulter_finances') or a_droit('gerer_cotisations') or a_droit('gerer_activites') or a_droit('saisir_ecritures'));
create policy gestion on collecte_membres for all using (a_droit('gerer_activites') or a_droit('gerer_cotisations'))
  with check (a_droit('gerer_activites') or a_droit('gerer_cotisations'));

create policy lecture on budgets for select using (a_droit('consulter_finances') or a_droit('gerer_budget'));
create policy gestion on budgets for all using (a_droit('gerer_budget')) with check (a_droit('gerer_budget'));

create policy lecture on transactions for select using (
  a_droit('consulter_finances') or a_droit('saisir_ecritures') or a_droit('gerer_cotisations') or a_droit('rapprocher'));
create policy saisie on transactions for insert with check (
  a_droit('saisir_ecritures') or (a_droit('gerer_cotisations') and sens = 'recette' and (est_cotisation or collecte_id is not null)));
create policy correction on transactions for update using (a_droit('saisir_ecritures') or a_droit('rapprocher'));

-- Demandes : on voit toutes les demandes si l'on valide, paie ou consulte les finances ; sinon les siennes
create policy lecture on expense_requests for select using (
  demandeur = auth.uid() or a_droit('valider_depenses') or a_droit('payer_depenses') or a_droit('consulter_finances'));
create policy creation on expense_requests for insert with check (
  a_droit('demander_depenses') and demandeur = auth.uid() and statut = 'soumise');
create policy suivi on expense_requests for update using (
  demandeur = auth.uid() or a_droit('valider_depenses') or a_droit('payer_depenses'));

create policy lecture on attachments for select using (
  a_droit('consulter_finances') or a_droit('valider_depenses') or a_droit('payer_depenses')
  or exists (select 1 from expense_requests r where r.id = request_id and r.demandeur = auth.uid()));
create policy depot on attachments for insert with check (
  a_droit('saisir_ecritures') or a_droit('payer_depenses')
  or exists (select 1 from expense_requests r where r.id = request_id and r.demandeur = auth.uid()));

create policy lecture on reconciliations for select using (a_droit('consulter_finances') or a_droit('rapprocher'));
create policy gestion on reconciliations for all using (a_droit('rapprocher')) with check (a_droit('rapprocher'));

create policy lecture on audit_log for select using (a_droit('administrer'));

-- =====================================================================
-- 7. Fichiers (Supabase Storage)
-- =====================================================================
insert into storage.buckets(id, name, public) values
 ('logos','logos',true), ('justificatifs','justificatifs',false), ('releves','releves',false),
 ('signatures','signatures',false), ('photos','photos',false)
on conflict (id) do nothing;

create policy "logos lecture" on storage.objects for select using (bucket_id = 'logos');
create policy "logos admin" on storage.objects for all
  using (bucket_id = 'logos' and a_droit('administrer')) with check (bucket_id = 'logos' and a_droit('administrer'));
create policy "pieces lecture" on storage.objects for select using (
  bucket_id in ('justificatifs','signatures')
  and (a_droit('consulter_finances') or a_droit('valider_depenses') or a_droit('payer_depenses') or a_droit('demander_depenses')));
create policy "justificatifs depot" on storage.objects for insert with check (
  bucket_id = 'justificatifs' and (a_droit('saisir_ecritures') or a_droit('payer_depenses') or a_droit('demander_depenses')));
create policy "signatures depot" on storage.objects for insert with check (bucket_id = 'signatures' and a_droit('valider_depenses'));
create policy "releves lecture" on storage.objects for select using (
  bucket_id = 'releves' and (a_droit('consulter_finances') or a_droit('rapprocher')));
create policy "releves depot" on storage.objects for insert with check (bucket_id = 'releves' and a_droit('rapprocher'));
create policy "photos lecture" on storage.objects for select using (bucket_id = 'photos' and est_connecte());
create policy "photos gestion" on storage.objects for all
  using (bucket_id = 'photos' and a_droit('gerer_membres')) with check (bucket_id = 'photos' and a_droit('gerer_membres'));

-- =====================================================================
-- 8. Compléments (mise en production)
-- =====================================================================
-- Nom affiché modifiable par chaque personne connectée
create or replace function public.modifier_mon_nom(p_nom text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not est_connecte() then raise exception 'Non connecté'; end if;
  if coalesce(trim(p_nom), '') = '' then raise exception 'Nom obligatoire'; end if;
  update profiles set nom = left(trim(p_nom), 80) where id = auth.uid();
end $$;

-- Dépense saisie directement : validation du président a posteriori
alter table expense_requests add column if not exists regularisation boolean not null default false;

create or replace function public.demander_validation_operation(p_transaction uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare t transactions; r_id uuid;
begin
  if not a_droit('saisir_ecritures') then raise exception 'Droit « saisir les écritures » requis'; end if;
  select * into t from transactions where id = p_transaction for update;
  if t.id is null then raise exception 'Opération introuvable'; end if;
  if t.sens <> 'depense' or t.montant <= 0 or t.contrepasse_de is not null then raise exception 'Seule une dépense peut être soumise au président'; end if;
  if t.request_id is not null then raise exception 'Cette dépense a déjà une demande de validation'; end if;
  if exists (select 1 from transactions c where c.contrepasse_de = t.id) then raise exception 'Dépense annulée par contre-passation'; end if;
  insert into expense_requests(demandeur, objet, montant, category_id, project_id, account_id, statut, regularisation, payee_par, payee_le, created_at)
  values (auth.uid(), t.libelle, t.montant, t.category_id, t.project_id, t.account_id, 'soumise', true, t.created_by, t.date_op::timestamptz, now())
  returning id into r_id;
  update transactions set request_id = r_id where id = t.id;
  return r_id;
end $$;

create or replace function check_transition() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.statut = old.statut then return new; end if;
  if old.statut = 'soumise' and new.statut in ('validee','refusee') then
    if not a_droit('valider_depenses') then raise exception 'Droit « valider les dépenses » requis'; end if;
    if new.statut = 'validee' then
      if new.signature_path is null then raise exception 'Signature obligatoire'; end if;
      new.validee_par := auth.uid(); new.validee_le := now();
      if old.regularisation then
        new.statut := case when exists (select 1 from attachments a join transactions t on t.id = a.transaction_id where t.request_id = old.id)
                           then 'justifiee'::statut_demande else 'payee'::statut_demande end;
        new.payee_le := coalesce(old.payee_le, now());
      end if;
    end if;
  elsif old.statut = 'validee' and new.statut = 'payee' then
    if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
    if old.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
    new.payee_par := auth.uid(); new.payee_le := now();
  elsif old.statut = 'payee' and new.statut = 'justifiee' then
    if not (a_droit('saisir_ecritures') or a_droit('payer_depenses') or old.demandeur = auth.uid()) then
      raise exception 'Le justificatif est déposé par le demandeur ou par la personne qui paie';
    end if;
    if not exists (select 1 from attachments a where a.request_id = old.id)
       and not exists (select 1 from attachments a join transactions t on t.id = a.transaction_id where t.request_id = old.id)
    then raise exception 'Justificatif manquant'; end if;
  elsif old.statut = 'soumise' and new.statut = 'annulee' then
    if old.demandeur <> auth.uid() then raise exception 'Seul le demandeur annule sa demande'; end if;
  else
    raise exception 'Changement de statut non autorisé';
  end if;
  return new;
end $$;

-- Fonctions fermées aux visiteurs non connectés ; déclencheurs jamais appelables directement
revoke execute on function public.check_transition(), public.garde_administrateur(), public.lock_reconciled(), public.log_change(), public.handle_new_user()
  from public, anon, authenticated;
revoke execute on function
  public.generer_cotisations(int), public.payer_demande(uuid, uuid, public.mode_paiement, date),
  public.justifier_demande(uuid, text, text, int), public.solde_pointe(uuid),
  public.terminer_rapprochement(uuid, date, date, numeric, text, text, int, uuid[]),
  public.anniversaires_du_mois(int), public.ma_cotisation(), public.mes_participations(),
  public.planning_activites(date, date), public.mes_droits(), public.modifier_mon_nom(text), public.demander_validation_operation(uuid)
  from public, anon;
grant execute on function
  public.generer_cotisations(int), public.payer_demande(uuid, uuid, public.mode_paiement, date),
  public.justifier_demande(uuid, text, text, int), public.solde_pointe(uuid),
  public.terminer_rapprochement(uuid, date, date, numeric, text, text, int, uuid[]),
  public.anniversaires_du_mois(int), public.ma_cotisation(), public.mes_participations(),
  public.planning_activites(date, date), public.mes_droits(), public.modifier_mon_nom(text), public.demander_validation_operation(uuid)
  to authenticated;
