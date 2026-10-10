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
-- =====================================================================
-- 9. Lien personnel des membres, inventaire du matériel, informations de paiement
-- =====================================================================
-- Informations de paiement affichées aux membres (IBAN, Lydia, remise en main propre…)
insert into public.settings(cle, texte, description) values
 ('infos_paiement', null, 'Comment régler sa cotisation : IBAN, application, remise au trésorier')
on conflict (cle) do nothing;

-- ---------- Lien personnel : consultation sans compte ni mot de passe ----------
-- Un jeton aléatoire (122 bits) par membre, envoyé par WhatsApp ou e-mail.
-- Il ne donne accès qu'à la situation de ce membre ; il se renouvelle ou se coupe à tout moment.
create table if not exists public.liens_membres (
  member_id uuid primary key references public.members(id) on delete cascade,
  jeton text not null unique,
  cree_par uuid references public.profiles(id),
  cree_le timestamptz not null default now(),
  derniere_consultation timestamptz,
  nb_consultations int not null default 0
);
alter table public.liens_membres enable row level security;
create policy lecture on public.liens_membres for select using (public.a_droit('gerer_membres') or public.a_droit('gerer_cotisations'));
create policy gestion on public.liens_membres for delete using (public.a_droit('gerer_membres') or public.a_droit('gerer_cotisations'));

create or replace function public.lien_membre(p_member uuid, p_renouveler boolean default false) returns text
language plpgsql security definer set search_path = public as $$
declare j text;
begin
  if not (a_droit('gerer_membres') or a_droit('gerer_cotisations')) then raise exception 'Droit « gérer les membres » requis'; end if;
  if not exists (select 1 from members where id = p_member) then raise exception 'Membre introuvable'; end if;
  if not p_renouveler then select jeton into j from liens_membres where member_id = p_member; end if;
  if j is null then
    j := replace(gen_random_uuid()::text, '-', '');
    insert into liens_membres(member_id, jeton, cree_par) values (p_member, j, auth.uid())
    on conflict (member_id) do update set jeton = excluded.jeton, cree_par = excluded.cree_par, cree_le = now(),
      derniere_consultation = null, nb_consultations = 0;
  end if;
  return j;
end $$;

-- Situation d'un membre à partir de son lien : cotisation, participations, rendez-vous à venir.
-- Appelable sans connexion ; ne renvoie rien si le jeton est inconnu.
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

-- ---------- Inventaire du matériel (instruments, sonorisation, informatique…) ----------
insert into public.permissions(code, libelle, groupe, ordre) values
 ('gerer_materiel', 'Tenir l’inventaire du matériel : ajouter, prêter, sortir', 'Matériel', 13)
on conflict (code) do nothing;
insert into public.role_permissions(role, permission) values ('tresorier', 'gerer_materiel') on conflict do nothing;

create table if not exists public.materiel (
  id uuid primary key default gen_random_uuid(),
  designation text not null,
  categorie text not null default 'autre'
    check (categorie in ('instrument','sonorisation','informatique','mobilier','textile','cuisine','autre')),
  marque text,
  numero_serie text,
  quantite int not null default 1 check (quantite > 0),
  origine text not null default 'achat' check (origine in ('achat','don','pret')),   -- prêt : appartient à un tiers
  date_acquisition date,
  valeur_acquisition numeric(14,2) check (valeur_acquisition is null or valeur_acquisition >= 0),   -- prix payé ou valeur estimée du don
  valeur_actuelle numeric(14,2) check (valeur_actuelle is null or valeur_actuelle >= 0),
  etat text not null default 'bon' check (etat in ('neuf','bon','usage','a_reparer','hors_service')),
  lieu text,                                                 -- lieu de rangement
  detenteur_id uuid references public.members(id) on delete set null,   -- membre qui l'a en main
  transaction_id uuid references public.transactions(id) on delete set null,   -- achat enregistré
  photo_path text,
  notes text,
  verifie_le date,                                           -- dernier inventaire physique
  sorti_le date,
  motif_sortie text check (motif_sortie is null or motif_sortie in ('vendu','donne','perdu','vole','detruit','rendu')),
  created_at timestamptz not null default now(),
  check ((sorti_le is null) = (motif_sortie is null))
);
create table if not exists public.materiel_mouvements (
  id uuid primary key default gen_random_uuid(),
  materiel_id uuid not null references public.materiel(id) on delete cascade,
  date_mvt date not null default current_date,
  type text not null check (type in ('entree','pret','retour','reparation','inventaire','sortie','modification')),
  member_id uuid references public.members(id) on delete set null,
  notes text,
  par uuid references public.profiles(id) default auth.uid(),
  created_at timestamptz not null default now()
);
create index if not exists materiel_mouvements_materiel on public.materiel_mouvements(materiel_id);
alter table public.materiel enable row level security;
alter table public.materiel_mouvements enable row level security;
create policy lecture on public.materiel for select using (
  public.a_droit('gerer_materiel') or public.a_droit('consulter_finances') or public.a_droit('voir_membres'));
create policy gestion on public.materiel for all using (public.a_droit('gerer_materiel')) with check (public.a_droit('gerer_materiel'));
create policy lecture on public.materiel_mouvements for select using (
  public.a_droit('gerer_materiel') or public.a_droit('consulter_finances') or public.a_droit('voir_membres'));
create policy gestion on public.materiel_mouvements for insert with check (public.a_droit('gerer_materiel'));
create trigger audit_materiel after insert or update or delete on public.materiel for each row execute function public.log_change();

-- Photos du matériel : dossier « materiel/ » du bucket photos
create policy "photos materiel" on storage.objects for all
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = 'materiel' and public.a_droit('gerer_materiel'))
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = 'materiel' and public.a_droit('gerer_materiel'));

-- Droits d'exécution
revoke execute on function public.lien_membre(uuid, boolean) from public, anon;
grant execute on function public.lien_membre(uuid, boolean) to authenticated;
revoke execute on function public.situation_par_lien(text) from public;
grant execute on function public.situation_par_lien(text) to anon, authenticated;


-- =====================================================================
-- 10. Correctifs de l'audit du 7 octobre 2026 : voir correctifs-audit-2026-10.sql
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

-- =====================================================================
-- 11. Virement interne caisse/banque et demandes enrichies (8 octobre 2026)
-- Ajouts seulement : aucune donnée existante n'est modifiée.
-- =====================================================================

-- ---------- Virement interne : dépôt ou retrait d'espèces, virement entre comptes ----------
-- Deux écritures liées par le même identifiant « virement » : une sortie du compte de départ,
-- une entrée sur le compte d'arrivée. Elles comptent dans les soldes et le rapprochement,
-- jamais dans les recettes, les dépenses, le résultat ni le budget.
alter table public.categories add column if not exists interne boolean not null default false;
insert into public.categories(nom, sens, interne) values ('Virement interne', 'recette', true), ('Virement interne', 'depense', true)
on conflict (nom, sens) do update set interne = true;
alter table public.transactions add column if not exists virement uuid;
create index if not exists transactions_virement_idx on public.transactions(virement);

create or replace function public.virement_interne(p_date date, p_source uuid, p_dest uuid, p_montant numeric, p_libelle text default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v uuid := gen_random_uuid(); s accounts; d accounts; lib text; m mode_paiement;
begin
  if not a_droit('saisir_ecritures') then raise exception 'Droit « saisir les écritures » requis'; end if;
  if p_source = p_dest then raise exception 'Choisissez deux comptes différents'; end if;
  if p_montant is null or p_montant <= 0 then raise exception 'Le montant doit être positif'; end if;
  select * into s from accounts where id = p_source and actif;
  select * into d from accounts where id = p_dest and actif;
  if s.id is null or d.id is null then raise exception 'Compte introuvable ou inactif'; end if;
  lib := coalesce(nullif(trim(p_libelle), ''),
    case when s.type = 'caisse' and d.type = 'banque' then 'Dépôt d''espèces à la banque'
         when s.type = 'banque' and d.type = 'caisse' then 'Retrait d''espèces pour la caisse'
         else 'Virement entre comptes' end);
  m := case when s.type = 'caisse' or d.type = 'caisse' then 'especes' else 'virement' end;
  insert into transactions(date_op, account_id, sens, montant, category_id, libelle, mode, virement, created_by) values
    (p_date, p_source, 'depense', p_montant, (select id from categories where interne and sens = 'depense' limit 1), left(lib, 120), m, v, auth.uid()),
    (p_date, p_dest,   'recette', p_montant, (select id from categories where interne and sens = 'recette' limit 1), left(lib, 120), m, v, auth.uid());
  return v;
end $$;

-- Annulation d'un virement : contre-passation des deux écritures d'un coup, avec motif
create or replace function public.annuler_virement(p_virement uuid, p_motif text)
returns void language plpgsql security definer set search_path = public as $$
declare t transactions; n int := 0;
begin
  if not a_droit('saisir_ecritures') then raise exception 'Droit « saisir les écritures » requis'; end if;
  if coalesce(trim(p_motif), '') = '' then raise exception 'Motif obligatoire'; end if;
  if exists (select 1 from transactions where virement = p_virement and rapproche) then
    raise exception 'Virement rapproché : période verrouillée, annulation impossible'; end if;
  if exists (select 1 from transactions c join transactions o on o.id = c.contrepasse_de where o.virement = p_virement) then
    raise exception 'Ce virement est déjà annulé'; end if;
  for t in select * from transactions where virement = p_virement and contrepasse_de is null loop
    insert into transactions(date_op, account_id, sens, montant, category_id, libelle, mode, virement, contrepasse_de, created_by)
    values (current_date, t.account_id, t.sens, -t.montant, t.category_id,
            left('Contre-passation : ' || t.libelle, 100) || ' · motif : ' || left(trim(p_motif), 60), t.mode, p_virement, t.id, auth.uid());
    n := n + 1;
  end loop;
  if n = 0 then raise exception 'Virement introuvable'; end if;
end $$;

revoke execute on function public.virement_interne(date, uuid, uuid, numeric, text), public.annuler_virement(uuid, text) from public, anon;
grant execute on function public.virement_interne(date, uuid, uuid, numeric, text), public.annuler_virement(uuid, text) to authenticated;

-- ---------- Demandes : justification, date souhaitée, devis ----------
alter table public.expense_requests add column if not exists justification text;
alter table public.expense_requests add column if not exists date_souhaitee date;
alter table public.attachments add column if not exists nature text not null default 'justificatif';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'attachments_nature_check') then
    alter table public.attachments add constraint attachments_nature_check check (nature in ('justificatif', 'devis'));
  end if;
end $$;

-- Montant à partir duquel une demande de dépense doit être justifiée (modifiable dans Paramètres)
insert into public.settings(cle, valeur, description) values
 ('seuil_justification', 100, 'Montant à partir duquel une demande de dépense doit être justifiée')
on conflict (cle) do nothing;

-- Un devis ne remplace jamais le justificatif : seules les pièces « justificatif » clôturent une demande
create or replace function public.check_transition() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.statut = old.statut then return new; end if;
  if old.statut = 'soumise' and new.statut in ('validee','refusee') then
    if not a_droit('valider_depenses') then raise exception 'Droit « valider les dépenses » requis'; end if;
    if new.statut = 'validee' then
      if new.signature_path is null then raise exception 'Signature obligatoire'; end if;
      new.validee_par := auth.uid(); new.validee_le := now();
      if old.regularisation then
        new.statut := case when exists (select 1 from attachments a join transactions t on t.id = a.transaction_id
                                          where t.request_id = old.id and a.nature = 'justificatif')
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
    if not exists (select 1 from attachments a where a.request_id = old.id and a.nature = 'justificatif')
       and not exists (select 1 from attachments a join transactions t on t.id = a.transaction_id where t.request_id = old.id and a.nature = 'justificatif')
    then raise exception 'Justificatif manquant'; end if;
  elsif old.statut = 'soumise' and new.statut = 'annulee' then
    if old.demandeur <> auth.uid() then raise exception 'Seul le demandeur annule sa demande'; end if;
  else
    raise exception 'Changement de statut non autorisé';
  end if;
  return new;
end $$;
revoke execute on function public.check_transition() from public, anon, authenticated;

-- =====================================================================
-- 12. Anti-doublon (8 octobre 2026) : une même saisie envoyée deux fois
-- (double appui, réseau lent) est refusée par la base pendant 2 minutes.
-- Une saisie identique volontaire reste possible après ce délai.
-- =====================================================================
create or replace function public.anti_doublon() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_table_name = 'transactions' then
    if new.contrepasse_de is null and new.virement is null and exists (
      select 1 from transactions t
      where t.created_by is not distinct from new.created_by and t.date_op = new.date_op and t.account_id = new.account_id
        and t.sens = new.sens and t.montant = new.montant and t.libelle = new.libelle
        and t.member_id is not distinct from new.member_id and t.tiers_id is not distinct from new.tiers_id
        and t.created_at > now() - interval '2 minutes') then
      raise exception 'Opération déjà enregistrée il y a moins de 2 minutes (doublon évité)';
    end if;
  elsif tg_table_name = 'expense_requests' then
    if exists (
      select 1 from expense_requests r
      where r.demandeur = new.demandeur and r.objet = new.objet and r.montant = new.montant
        and r.created_at > now() - interval '2 minutes') then
      raise exception 'Demande déjà envoyée il y a moins de 2 minutes (doublon évité)';
    end if;
  end if;
  return new;
end $$;
revoke execute on function public.anti_doublon() from public, anon, authenticated;
drop trigger if exists trg_anti_doublon on public.transactions;
create trigger trg_anti_doublon before insert on public.transactions for each row execute function public.anti_doublon();
drop trigger if exists trg_anti_doublon on public.expense_requests;
create trigger trg_anti_doublon before insert on public.expense_requests for each row execute function public.anti_doublon();

-- Paiement d'une demande inexistante : message clair (avant : erreur technique de montant manquant)
create or replace function public.payer_demande(p_id uuid, p_compte uuid, p_mode mode_paiement, p_date date)
returns uuid language plpgsql security definer set search_path = public as $$
declare r expense_requests; t_id uuid;
begin
  if not a_droit('payer_depenses') then raise exception 'Droit « payer les dépenses » requis'; end if;
  select * into r from expense_requests where id = p_id for update;
  if r.id is null then raise exception 'Demande introuvable'; end if;
  if r.statut <> 'validee' then raise exception 'Demande non validée'; end if;
  if r.validee_par = auth.uid() then raise exception 'La personne qui a validé ne peut pas payer'; end if;
  insert into transactions(date_op, account_id, sens, montant, category_id, project_id, libelle, mode, request_id, created_by)
  values (p_date, p_compte, 'depense', r.montant, r.category_id, r.project_id, r.objet, p_mode, r.id, auth.uid())
  returning id into t_id;
  update expense_requests set statut = 'payee', account_id = p_compte where id = p_id;
  return t_id;
end $$;

-- =====================================================================
-- 13. Exercices, coordonnées de l'association, corbeille réversible, nouveautés, fiche du nouveau membre
-- (9 octobre 2026). Migration additive : aucune donnée existante n'est modifiée ni supprimée.
-- =====================================================================

-- ---------- 13.1 Association : coordonnées et première configuration ----------
alter table public.organisation
  add column if not exists sigle text,
  add column if not exists objet text,
  add column if not exists adresse text,
  add column if not exists code_postal text,
  add column if not exists ville text,
  add column if not exists email text,
  add column if not exists telephone text,
  add column if not exists site_web text,
  add column if not exists rna text,              -- numéro au répertoire national des associations (W…)
  add column if not exists siret text,
  add column if not exists date_creation date,
  add column if not exists configuree boolean not null default false;   -- assistant de configuration terminé

-- ---------- 13.2 Exercices comptables ----------
create table if not exists public.exercices (
  id uuid primary key default gen_random_uuid(),
  libelle text not null,
  debut date not null,
  fin date not null,
  cloture boolean not null default false,
  cloture_le timestamptz,
  cloture_par uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  check (fin > debut)
);
alter table public.exercices enable row level security;
drop policy if exists lecture on public.exercices;
create policy lecture on public.exercices for select using (public.est_connecte());
drop policy if exists admin on public.exercices;
create policy admin on public.exercices for all using (public.a_droit('administrer')) with check (public.a_droit('administrer'));
drop trigger if exists audit_exercices on public.exercices;
create trigger audit_exercices after insert or update or delete on public.exercices for each row execute function public.log_change();

-- Deux exercices ne se chevauchent pas ; un exercice clôturé ne change plus de dates
create or replace function public.check_exercice() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from exercices e where e.id <> new.id and e.debut <= new.fin and e.fin >= new.debut) then
    raise exception 'Cet exercice chevauche un exercice existant';
  end if;
  if tg_op = 'UPDATE' and old.cloture and new.cloture and (new.debut <> old.debut or new.fin <> old.fin) then
    raise exception 'Exercice clôturé : rouvrez-le pour changer ses dates';
  end if;
  if new.cloture and not coalesce(old.cloture, false) then new.cloture_le := now(); new.cloture_par := auth.uid(); end if;
  if not new.cloture then new.cloture_le := null; new.cloture_par := null; end if;
  return new;
end $$;
drop trigger if exists trg_check_exercice on public.exercices;
create trigger trg_check_exercice before insert or update on public.exercices for each row execute function public.check_exercice();

-- Premier exercice : l'année en cours (à partir du début d'exercice de l'association)
insert into public.exercices(libelle, debut, fin)
select 'Exercice ' || extract(year from o.exercice_debut)::int, o.exercice_debut, (o.exercice_debut + interval '1 year' - interval '1 day')::date
from public.organisation o
where not exists (select 1 from public.exercices);

-- Exercice clôturé : ses opérations ne s'ajoutent, ne se modifient et ne se suppriment plus
create or replace function public.exercice_clos(p_date date) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from exercices where cloture and p_date between debut and fin)
$$;
create or replace function public.lock_exercice() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op <> 'INSERT' and exercice_clos(old.date_op) then
    raise exception 'Exercice clôturé : opération verrouillée';
  end if;
  if tg_op <> 'DELETE' and exercice_clos(new.date_op) then
    raise exception 'Exercice clôturé : impossible d’enregistrer à cette date';
  end if;
  return coalesce(new, old);
end $$;
drop trigger if exists trg_lock_exercice on public.transactions;
create trigger trg_lock_exercice before insert or update or delete on public.transactions for each row execute function public.lock_exercice();

-- ---------- 13.3 Corbeille : suppression réversible ----------
-- Une suppression enregistre une copie complète (ligne + lignes rattachées) puis efface ;
-- « Restaurer » remet tout en place. Rien n'est perdu, tout est tracé (qui, quand, pourquoi).
create table if not exists public.corbeille (
  id uuid primary key default gen_random_uuid(),
  table_nom text not null,
  ligne_id uuid not null,
  libelle text not null,
  donnees jsonb not null,
  dependances jsonb not null default '[]',    -- [{table, donnees}] lignes effacées avec la principale
  liens jsonb not null default '[]',          -- [{table, id, colonne}] références remises à vide, rétablies à la restauration
  motif text,
  supprime_par uuid references public.profiles(id) default auth.uid(),
  supprime_le timestamptz not null default now(),
  restaure_par uuid references public.profiles(id),
  restaure_le timestamptz
);
alter table public.corbeille enable row level security;
drop policy if exists lecture on public.corbeille;
create policy lecture on public.corbeille for select using (
  public.est_connecte() and (supprime_par = auth.uid() or public.a_droit('administrer') or public.a_droit('consulter_finances')));

-- Droit nécessaire pour supprimer ou restaurer une ligne de chaque table
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
    else false end
$$;

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

create or replace function public.restaurer(p_id uuid) returns void
language plpgsql security definer set search_path = public as $$
declare c corbeille; r record;
begin
  select * into c from corbeille where id = p_id;
  if c.id is null then raise exception 'Élément introuvable dans la corbeille'; end if;
  if c.restaure_le is not null then raise exception 'Élément déjà restauré'; end if;
  if not droit_suppression(c.table_nom) then raise exception 'Droit insuffisant pour restaurer'; end if;
  if c.table_nom = 'expense_requests' and (c.donnees->>'demandeur')::uuid <> auth.uid() and not a_droit('administrer') then
    raise exception 'Seul le demandeur restaure sa demande';
  end if;
  begin
    execute format('insert into %I select * from jsonb_populate_record(null::%I, $1)', c.table_nom, c.table_nom) using c.donnees;
    for r in select d from jsonb_array_elements(c.dependances) d where d->>'table' = 'transactions' loop
      insert into transactions select * from jsonb_populate_record(null::transactions, r.d->'donnees');
    end loop;
    for r in select d from jsonb_array_elements(c.dependances) d where d->>'table' <> 'transactions' loop
      execute format('insert into %I select * from jsonb_populate_record(null::%I, $1)', r.d->>'table', r.d->>'table') using r.d->'donnees';
    end loop;
    for r in select l from jsonb_array_elements(c.liens) l loop
      execute format('update %I set %I = $2 where id = $1 and %I is null', r.l->>'table', r.l->>'colonne', r.l->>'colonne')
        using (r.l->>'id')::uuid, (r.l->>'valeur')::uuid;
    end loop;
  exception when unique_violation then
    raise exception 'Restauration impossible : un élément identique existe déjà';
  when foreign_key_violation then
    raise exception 'Restauration impossible : un élément lié (compte, catégorie, membre…) a été supprimé entre-temps';
  end;
  update corbeille set restaure_le = now(), restaure_par = auth.uid() where id = p_id;
end $$;

-- ---------- 13.4 Nouveautés : pastilles sur les onglets et liste des nouveautés ----------
alter table public.profiles add column if not exists vus jsonb not null default '{}';
-- Rendez-vous déjà existants : datés de la création des comptes (ils ne s'affichent pas comme « nouveaux »)
alter table public.projects add column if not exists created_at timestamptz;
update public.projects set created_at = coalesce((select min(created_at) from public.profiles), now()) where created_at is null;
alter table public.projects alter column created_at set default now(), alter column created_at set not null;

-- Pour chaque onglet : ce qui attend une action (à traiter) et ce qui est nouveau depuis la dernière visite :
-- demandes (à valider, à payer, les miennes mises à jour), opérations saisies par d'autres, rendez-vous ajoutés,
-- membres arrivés, participations demandées (pour moi) ou collectes créées (gestion)
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

  return jsonb_build_object('compteurs', v_res,
    'elements', coalesce((select jsonb_agg(e order by (e->>'quand') desc) from jsonb_array_elements(v_liste) e), '[]'));
end $$;

create or replace function public.marquer_vu(p_section text) returns void
language sql security definer set search_path = public as $$
  update profiles set vus = vus || jsonb_build_object(p_section, now()) where id = auth.uid()
$$;

-- ---------- 13.5 Nouveau membre : il remplit lui-même sa fiche ----------
-- Si le compte est déjà rattaché à une fiche, elle est complétée ; sinon une fiche portant la même adresse
-- e-mail est rattachée ; à défaut, une fiche est créée. Le nom affiché reprend celui de la fiche.
create or replace function public.enregistrer_ma_fiche(p_prenom text, p_nom text, p_jour int, p_mois int,
  p_whatsapp text default null, p_profession text default null, p_consent boolean default true) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_membre uuid; v_email text;
begin
  if not est_connecte() then raise exception 'Connexion requise'; end if;
  if coalesce(trim(p_prenom), '') = '' or coalesce(trim(p_nom), '') = '' then raise exception 'Prénom et nom obligatoires'; end if;
  if p_jour not between 1 and 31 or p_mois not between 1 and 12 then raise exception 'Date de naissance invalide'; end if;
  select member_id into v_membre from profiles where id = auth.uid();
  select email into v_email from auth.users where id = auth.uid();
  if v_membre is null and v_email is not null then
    select m.id into v_membre from members m
    where lower(m.email) = lower(v_email) and not exists (select 1 from profiles x where x.member_id = m.id) limit 1;
  end if;
  if v_membre is null then
    insert into members(prenom, nom, naissance_jour, naissance_mois, whatsapp, profession, email, consent_anniversaire, date_adhesion, actif)
    values (trim(p_prenom), trim(p_nom), p_jour, p_mois, nullif(trim(coalesce(p_whatsapp, '')), ''), nullif(trim(coalesce(p_profession, '')), ''),
            v_email, coalesce(p_consent, true), current_date, true)
    returning id into v_membre;
  else
    update members set prenom = trim(p_prenom), nom = trim(p_nom), naissance_jour = p_jour, naissance_mois = p_mois,
      whatsapp = coalesce(nullif(trim(coalesce(p_whatsapp, '')), ''), whatsapp), profession = coalesce(nullif(trim(coalesce(p_profession, '')), ''), profession),
      email = coalesce(email, v_email), consent_anniversaire = coalesce(p_consent, consent_anniversaire)
    where id = v_membre;
  end if;
  update profiles set member_id = v_membre, nom = trim(p_prenom) || ' ' || trim(p_nom) where id = auth.uid();
  return v_membre;
end $$;

grant execute on function public.supprimer(text, uuid, text), public.restaurer(uuid), public.mes_nouveautes(),
  public.marquer_vu(text), public.enregistrer_ma_fiche(text, text, int, int, text, text, boolean), public.exercice_clos(date) to authenticated;
revoke execute on function public.supprimer(text, uuid, text), public.restaurer(uuid), public.mes_nouveautes(),
  public.marquer_vu(text), public.enregistrer_ma_fiche(text, text, int, int, text, text, boolean) from anon;

-- =====================================================================
-- 14. Lien personnel court (10 octobre 2026) : voir correctifs-2026-10-10-lien-court.sql
-- =====================================================================
-- Correctif du 10 octobre 2026 : lien personnel court et rassurant
-- Chaque lien reçoit un code de 12 caractères (lettres et chiffres sans ambiguïté : ni 0/o, ni 1/l/i),
-- tiré du générateur aléatoire de PostgreSQL (~59 bits, impossible à deviner).
-- L'adresse devient …/tresorerie/?m=grace-k7qp2xyz9abc (prénom pour rassurer, puis le code).
-- Les anciens liens (jeton de 32 caractères) continuent de fonctionner.
alter table public.liens_membres add column if not exists code text;
create unique index if not exists liens_membres_code_key on public.liens_membres(code);

create or replace function public.code_lien() returns text
language plpgsql volatile set search_path = public as $$
declare alpha constant text := 'abcdefghjkmnpqrstuvwxyz23456789'; b bytea; r text := ''; i int;
begin
  -- 16 octets aléatoires (les octets 6 et 8 d'un UUID v4 sont en partie fixes : on les saute)
  b := uuid_send(gen_random_uuid());
  foreach i in array array[0,1,2,3,4,5,7,9,10,11,12,13] loop
    r := r || substr(alpha, 1 + (get_byte(b, i) % 31), 1);
  end loop;
  return r;
end $$;
revoke execute on function public.code_lien() from public, anon;

update public.liens_membres set code = public.code_lien() where code is null;

create or replace function public.lien_membre(p_member uuid, p_renouveler boolean default false) returns text
language plpgsql security definer set search_path = public as $$
declare j text;
begin
  if not (a_droit('gerer_membres') or a_droit('gerer_cotisations')) then raise exception 'Droit « gérer les membres » requis'; end if;
  if not exists (select 1 from members where id = p_member) then raise exception 'Membre introuvable'; end if;
  if not p_renouveler then select jeton into j from liens_membres where member_id = p_member; end if;
  if j is null then
    j := replace(gen_random_uuid()::text, '-', '');
    insert into liens_membres(member_id, jeton, code, cree_par) values (p_member, j, code_lien(), auth.uid())
    on conflict (member_id) do update set jeton = excluded.jeton, code = excluded.code, cree_par = excluded.cree_par, cree_le = now(),
      derniere_consultation = null, nb_consultations = 0;
  end if;
  return j;
end $$;

-- Situation par lien : jeton de 32 caractères (anciens liens) ou code de 12 caractères
create or replace function public.situation_par_lien(p_jeton text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare mid uuid; r jsonb;
begin
  if p_jeton is null or length(p_jeton) not in (12, 32) then return null; end if;
  update liens_membres set derniere_consultation = now(), nb_consultations = nb_consultations + 1
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
    'a_venir', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'nom', p.nom, 'date_debut', p.date_debut, 'date_fin', p.date_fin,
                   'heure_debut', p.heure_debut, 'heure_fin', p.heure_fin, 'lieu', p.lieu, 'description', p.description)
                   order by p.date_debut, p.heure_debut nulls first)
                 from (select * from projects p where p.visible_adherents and p.date_debut is not null
                       and coalesce(p.date_fin, p.date_debut) >= current_date and p.date_debut <= current_date + 120
                       order by p.date_debut limit 12) p), '[]'::jsonb)
  ) into r;
  return r;
end $$;
