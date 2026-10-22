// Fonction « supprimer-compte » : l'administrateur supprime un compte d'accès créé par erreur ou pour essai,
// en le plaçant dans la corbeille (copie du profil et de l'adresse), puis le restaure si besoin.
// Le compte de connexion est effacé (l'adresse redevient libre) ; la fiche de membre n'est jamais touchée.
// Refus : son propre compte, le dernier administrateur, un compte qui a déjà servi (opérations, demandes…) :
// dans ce cas, décocher « Actif » retire l'accès sans perdre l'historique.
// Appels (POST, jeton de la personne connectée) :
//   { action: 'supprimer', id, motif }            → { corbeille_id }
//   { action: 'restaurer', corbeille_id, mot_de_passe } → { email, nom, member_id, role } (nouveau mot de passe provisoire)
import { createClient } from 'npm:@supabase/supabase-js@2.45.4';

const URL = Deno.env.get('SUPABASE_URL')!;
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { ...cors, 'Content-Type': 'application/json' } });

// Traces laissées par un compte : on ne le supprime pas, l'historique doit rester lisible (qui a saisi, validé, payé…)
const TRACES: [string, string][] = [
  ['transactions', 'created_by'], ['expense_requests', 'demandeur'], ['expense_requests', 'validee_par'], ['expense_requests', 'payee_par'],
  ['attachments', 'depose_par'], ['reconciliations', 'termine_par'], ['liens_membres', 'cree_par'], ['materiel_mouvements', 'par'],
  ['exercices', 'cloture_par'], ['rappels', 'par'], ['corbeille', 'supprime_par'], ['corbeille', 'restaure_par'],
];

// Compte d'authentification existant pour une adresse (parcours des pages de l'annuaire des comptes)
// deno-lint-ignore no-explicit-any
async function utilisateurParEmail(sb: any, email: string) {
  for (let page = 1; page <= 20; page++) {
    const { data, error } = await sb.auth.admin.listUsers({ page, perPage: 200 });
    if (error || !data?.users?.length) return null;
    const u = data.users.find((x: { email?: string }) => (x.email || '').toLowerCase() === email);
    if (u) return u;
    if (data.users.length < 200) return null;
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: { ...cors, 'Access-Control-Allow-Headers': req.headers.get('Access-Control-Request-Headers') || cors['Access-Control-Allow-Headers'] } });
  if (req.method !== 'POST') return json({ erreur: 'Méthode non prise en charge' }, 405);
  const appelant = createClient(URL, Deno.env.get('SUPABASE_ANON_KEY')!, {
    global: { headers: { Authorization: req.headers.get('Authorization') || '' } }, auth: { persistSession: false },
  });
  const { data: moi } = await appelant.auth.getUser();
  const { data: autorise } = await appelant.rpc('a_droit', { p_permission: 'administrer' });
  if (!moi?.user || autorise !== true) return json({ erreur: 'Réservé à l’administrateur de la plateforme' }, 403);

  let corps: { action?: string; id?: string; motif?: string; corbeille_id?: string; mot_de_passe?: string };
  try { corps = await req.json(); } catch { return json({ erreur: 'Demande illisible' }, 400); }
  const sb = createClient(URL, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });

  if (corps.action === 'supprimer') {
    const id = String(corps.id || '');
    if (id === moi.user.id) return json({ erreur: 'Vous ne pouvez pas supprimer votre propre compte' }, 400);
    const { data: p } = await sb.from('profiles').select('*').eq('id', id).maybeSingle();
    if (!p) return json({ erreur: 'Compte introuvable' }, 404);
    // Le dernier administrateur actif reste : sinon plus personne ne peut gérer les accès
    const { data: rolesAdmin } = await sb.from('role_permissions').select('role').eq('permission', 'administrer');
    const admins = new Set((rolesAdmin || []).map((r) => r.role));
    if (admins.has(p.role)) {
      const { count } = await sb.from('profiles').select('id', { count: 'exact', head: true }).in('role', [...admins]).eq('actif', true).neq('id', id);
      if (!count) return json({ erreur: 'C’est le dernier administrateur : donnez d’abord ce rôle à une autre personne' }, 400);
    }
    for (const [table, colonne] of TRACES) {
      const { count } = await sb.from(table).select('*', { count: 'exact', head: true }).eq(colonne, id);
      if (count) return json({ erreur: 'Ce compte a déjà servi (opérations, demandes, rappels…) : décochez « Actif » pour lui retirer l’accès, l’historique reste lisible' }, 400);
    }
    const { data: u } = await sb.auth.admin.getUserById(id);
    const email = (u?.user?.email || '').toLowerCase();
    const { data: entree, error: eC } = await sb.from('corbeille').insert({
      table_nom: 'comptes', ligne_id: id, libelle: `Compte de ${p.nom}${email ? ' (' + email + ')' : ''}`,
      donnees: { ...p, email }, motif: (corps.motif || '').trim() || null, supprime_par: moi.user.id,
    }).select('id').single();
    if (eC || !entree) return json({ erreur: 'Mise à la corbeille impossible : ' + (eC?.message || '') }, 500);
    // Effacer l'identifiant de connexion efface aussi le profil (clé en cascade) ; la fiche de membre reste
    const { error: eS } = await sb.auth.admin.deleteUser(id);
    if (eS) {
      await sb.from('corbeille').delete().eq('id', entree.id);
      return json({ erreur: 'Suppression refusée : ' + eS.message }, 500);
    }
    await sb.from('audit_log').insert({ user_id: moi.user.id, action: 'SUPPRIMER_COMPTE', table_name: 'profiles', row_id: id, detail: { email, role: p.role, member_id: p.member_id } });
    return json({ corbeille_id: entree.id });
  }

  if (corps.action === 'restaurer') {
    const mdp = String(corps.mot_de_passe || '');
    if (mdp.length < 8) return json({ erreur: 'Le mot de passe provisoire doit faire au moins 8 caractères' }, 400);
    const { data: c } = await sb.from('corbeille').select('*').eq('id', corps.corbeille_id).eq('table_nom', 'comptes').maybeSingle();
    if (!c) return json({ erreur: 'Élément introuvable dans la corbeille' }, 404);
    if (c.restaure_le) return json({ erreur: 'Compte déjà restauré' }, 400);
    const d = c.donnees || {};
    const email = String(d.email || '').toLowerCase();
    if (!email) return json({ erreur: 'Adresse du compte inconnue : créez-le à nouveau depuis « Créer des comptes »' }, 400);
    if (await utilisateurParEmail(sb, email)) return json({ erreur: 'Un compte existe déjà pour cette adresse' }, 400);
    // La fiche de membre a pu être reliée à un autre compte entre-temps : on ne la reprend pas
    let member = d.member_id || null;
    if (member) {
      const { data: pris } = await sb.from('profiles').select('id').eq('member_id', member).maybeSingle();
      const { data: existe } = await sb.from('members').select('id').eq('id', member).maybeSingle();
      if (pris || !existe) member = null;
    }
    const { data: roles } = await sb.from('roles').select('code');
    const role = (roles || []).some((r) => r.code === d.role) ? d.role : 'adherent';
    await sb.from('invitations').upsert({ email, nom: d.nom, role, member_id: member }, { onConflict: 'email' });
    const { data: cree, error } = await sb.auth.admin.createUser({ email, password: mdp, email_confirm: true, user_metadata: { nom: d.nom } });
    if (error || !cree?.user) {
      await sb.from('invitations').delete().eq('email', email);
      return json({ erreur: 'Restauration refusée : ' + (error?.message || 'raison inconnue') }, 500);
    }
    const id = cree.user.id;
    const { data: profil } = await sb.from('profiles').select('id').eq('id', id).maybeSingle();
    const valeurs = { nom: d.nom, role, member_id: member, mdp_provisoire: true, actif: d.actif !== false };
    if (profil) await sb.from('profiles').update(valeurs).eq('id', id);
    else await sb.from('profiles').insert({ id, ...valeurs });
    await sb.from('invitations').delete().eq('email', email);
    await sb.from('corbeille').update({ restaure_le: new Date().toISOString(), restaure_par: moi.user.id }).eq('id', c.id);
    await sb.from('audit_log').insert({ user_id: moi.user.id, action: 'RESTAURER_COMPTE', table_name: 'profiles', row_id: id, detail: { email, role, member_id: member } });
    return json({ email, nom: d.nom, member_id: member, role });
  }
  return json({ erreur: 'Action inconnue' }, 400);
});
