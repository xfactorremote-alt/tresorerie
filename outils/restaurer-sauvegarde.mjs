#!/usr/bin/env node
// Restauration d'une sauvegarde de la trésorerie (fichier sauvegarde-tresorerie-AAAA-MM-JJ.json,
// exporté depuis Rapports > Sauvegarde complète). Produit un script SQL à coller dans
// Supabase > SQL Editor, sur une base où schema.sql a déjà été exécuté.
//
//   node outils/restaurer-sauvegarde.mjs sauvegarde-tresorerie-2026-10-08.json > restauration.sql
//
// - Les données de l'association sont remplacées par celles de la sauvegarde (une seule transaction :
//   tout passe ou rien ne change).
// - Les comptes de connexion (profiles, invitations) ne sont pas touchés : les personnes se
//   reconnectent ou sont réinvitées. Les auteurs des écritures sont gardés s'ils existent encore.
// - Les fichiers (pièces, relevés, photos) ne sont pas dans la sauvegarde : ils restent dans
//   Supabase Storage, ou dans l'archive ZIP des pièces de fin d'exercice.
import { readFileSync } from 'node:fs';

const fichier = process.argv[2];
if (!fichier) { console.error('Usage : node outils/restaurer-sauvegarde.mjs sauvegarde.json > restauration.sql'); process.exit(1); }
const s = JSON.parse(readFileSync(fichier, 'utf8'));
if (s.format !== 'tresorerie-jp-v1') { console.error('Ce fichier n’est pas une sauvegarde de la trésorerie (format inconnu).'); process.exit(1); }

// Ordre d'insertion : chaque table après celles qu'elle référence
const ORDRE = ['organisation', 'settings', 'exercices', 'accounts', 'categories', 'projects', 'budgets', 'members', 'cotisations', 'tiers', 'collectes',
  'collecte_membres', 'reconciliations', 'expense_requests', 'transactions', 'attachments', 'materiel', 'materiel_mouvements', 'corbeille'];
const IGNOREES = ['profiles', 'invitations'];
// Colonnes calculées ou absentes d'une base plus ancienne : ignorées si présentes dans le fichier
const litteral = (v) => v === null || v === undefined ? 'null'
  : typeof v === 'number' || typeof v === 'boolean' ? String(v)
  : typeof v === 'object' ? `'${JSON.stringify(v).replace(/'/g, "''")}'::jsonb`
  : `'${String(v).replace(/'/g, "''")}'`;

const sortie = [];
const p = (l) => sortie.push(l);
p(`-- Restauration de la sauvegarde du ${s.exporte_le} (${fichier.split('/').pop()})`);
p('begin;');
p("set local session_replication_role = replica;   -- règles et journal suspendus le temps de la restauration");
[...ORDRE].reverse().forEach((t) => { if (s[t]) p(`delete from public.${t};`); });
let total = 0;
for (const t of ORDRE) {
  const lignes = s[t];
  if (!Array.isArray(lignes) || !lignes.length) continue;
  const cols = Object.keys(lignes[0]);
  p(`-- ${t} : ${lignes.length} ligne(s)`);
  for (let i = 0; i < lignes.length; i += 200) {
    const lot = lignes.slice(i, i + 200);
    p(`insert into public.${t} (${cols.map((c) => `"${c}"`).join(', ')}) values\n` +
      lot.map((l) => `(${cols.map((c) => litteral(l[c])).join(', ')})`).join(',\n') + ';');
  }
  total += lignes.length;
}
// Auteurs disparus : on garde l'écriture, sans auteur
p("update public.transactions set created_by = null where created_by is not null and created_by not in (select id from public.profiles);");
p("update public.expense_requests set validee_par = null where validee_par is not null and validee_par not in (select id from public.profiles);");
p('commit;');
p(`-- Contrôle : comparez ces soldes à ceux de l'application avant la sauvegarde`);
p('select nom, solde from public.v_soldes order by nom;');
process.stdout.write(sortie.join('\n') + '\n');
console.error(`${total} lignes à restaurer ; ignorées : ${IGNOREES.filter((t) => s[t]).join(', ') || 'aucune'}.`);
