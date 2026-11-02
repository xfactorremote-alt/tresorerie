// Fonction « creer-comptes » : l'administrateur crée directement les comptes de membres choisis, avec un mot de passe
// provisoire. Aucun e-mail n'est envoyé (adresse confirmée d'office), donc pas de limite d'envoi du service d'e-mails.
// À la première connexion, l'application demande à la personne de choisir son mot de passe (profiles.mdp_provisoire).
// Appel : POST { mot_de_passe, comptes: [{ member_id, email, role }] } avec le jeton de la personne connectée.
import { createClient } from 'npm:@supabase/supabase-js@2.45.4';

const URL = Deno.env.get('SUPABASE_URL')!;
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

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
  // Pré-vérification du navigateur : on accepte les en-têtes demandés (la bibliothèque Supabase en ajoute selon les versions)
  if (req.method === 'OPTIONS') return new Response('ok', { headers: { ...cors, 'Access-Control-Allow-Headers': req.headers.get('Access-Control-Request-Headers') || cors['Access-Control-Allow-Headers'] } });
  if (req.method !== 'POST') return json({ erreur: 'Méthode non prise en charge' }, 405);
  // La personne qui appelle doit avoir le droit « administrer » (vérifié par la base avec son propre jeton)
  const appelant = createClient(URL, Deno.env.get('SUPABASE_ANON_KEY')!, {
    global: { headers: { Authorization: req.headers.get('Authorization') || '' } }, auth: { persistSession: false },
  });
  const { data: moi } = await appelant.auth.getUser();
  const { data: autorise } = await appelant.rpc('a_droit', { p_permission: 'administrer' });
  if (!moi?.user || autorise !== true) return json({ erreur: 'Réservé à l’administrateur de la plateforme' }, 403);

  let corps: { mot_de_passe?: string; comptes?: { member_id?: string; email?: string; role?: string }[] };
  try { corps = await req.json(); } catch { return json({ erreur: 'Demande illisible' }, 400); }
  const mdp = String(corps.mot_de_passe || '');
  if (mdp.length < 8) return json({ erreur: 'Le mot de passe provisoire doit faire au moins 8 caractères' }, 400);
  const demandes = Array.isArray(corps.comptes) ? corps.comptes.slice(0, 100) : [];

  const sb = createClient(URL, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });
  const { data: roles } = await sb.from('roles').select('code');
  const codes = new Set((roles || []).map((r) => r.code));
  const resultats = [];
  for (const d of demandes) {
    const email = String(d.email || '').trim().toLowerCase();
    const r = { member_id: d.member_id, email, ok: false, existant: false, erreur: '' };
    resultats.push(r);
    if (!EMAIL.test(email)) { r.erreur = 'Adresse e-mail invalide'; continue; }
    if (!codes.has(String(d.role))) { r.erreur = 'Fonction inconnue'; continue; }
    const { data: m } = await sb.from('members').select('id, prenom, nom, email').eq('id', d.member_id).maybeSingle();
    if (!m) { r.erreur = 'Membre introuvable'; continue; }
    const { data: deja } = await sb.from('profiles').select('id').eq('member_id', m.id).maybeSingle();
    if (deja) {
      // Fiche déjà rattachée à un compte jamais confirmé ni utilisé : ouvert par quelqu'un qui n'a pas prouvé posséder
      // l'adresse (invitation prise de vitesse) ; il est remplacé. Un compte réel n'est jamais touché.
      const { data: du } = await sb.auth.admin.getUserById(deja.id);
      if (du?.user && !du.user.email_confirmed_at && !du.user.last_sign_in_at) await sb.auth.admin.deleteUser(deja.id);
      else { r.erreur = 'A déjà un compte'; continue; }
    }
    const nom = `${m.prenom || ''} ${m.nom || ''}`.trim();
    // L'invitation porte la fonction et la fiche : le déclencheur de la base crée le profil à la création du compte
    const preparer = async () => {
      await sb.from('invitations').delete().eq('member_id', m.id).neq('email', email);
      return (await sb.from('invitations').upsert({ email, nom, role: d.role, member_id: m.id }, { onConflict: 'email' })).error;
    };
    const eInv = await preparer();
    if (eInv) { r.erreur = 'Préparation impossible : ' + eInv.message; continue; }
    let { data: cree, error } = await sb.auth.admin.createUser({ email, password: mdp, email_confirm: true, user_metadata: { nom } });
    if (error || !cree?.user) {
      await sb.from('invitations').delete().eq('email', email);
      if (!/already|registered|exists/i.test(error?.message || '')) { r.erreur = 'Création refusée : ' + (error?.message || 'raison inconnue'); continue; }
      const existant = await utilisateurParEmail(sb, email);
      if (!existant) { r.erreur = 'Adresse déjà enregistrée mais compte introuvable'; continue; }
      const { data: p } = await sb.from('profiles').select('id, member_id').eq('id', existant.id).maybeSingle();
      if (p?.member_id && p.member_id !== m.id) { r.erreur = 'Cette adresse est déjà celle du compte d’un autre membre'; continue; }
      if (p && existant.email_confirmed_at) {
        // Compte réel déjà autorisé sur la plateforme (profil existant, adresse prouvée) : rattaché à la fiche, il garde son mot de passe
        await sb.from('profiles').update({ member_id: m.id, role: d.role, nom }).eq('id', p.id);
        if (!m.email) await sb.from('members').update({ email }).eq('id', m.id);
        await sb.from('audit_log').insert({ user_id: moi.user.id, action: 'RELIER_COMPTE', table_name: 'profiles', row_id: existant.id, detail: { email, role: d.role, member_id: m.id } });
        r.ok = true; r.existant = true;
        continue;
      }
      // Pré-inscription (« Créer mon mot de passe » sans invitation, ou adresse jamais confirmée) : n'importe qui peut ouvrir
      // un compte au nom d'une adresse qu'il ne possède pas. Le relier en gardant son mot de passe donnait la fonction
      // choisie (trésorier, président…) à celui qui l'avait ouvert. Ce compte, sans aucun droit ni donnée, est remplacé
      // par un compte neuf au mot de passe provisoire : seul celui qui reçoit le message du bureau peut entrer.
      const { error: eDel } = await sb.auth.admin.deleteUser(existant.id);
      if (eDel) { r.erreur = 'Adresse déjà prise par un compte non vérifié : suppression impossible (' + eDel.message + ')'; continue; }
      const eInv2 = await preparer();
      if (eInv2) { r.erreur = 'Préparation impossible : ' + eInv2.message; continue; }
      ({ data: cree, error } = await sb.auth.admin.createUser({ email, password: mdp, email_confirm: true, user_metadata: { nom } }));
      if (error || !cree?.user) { await sb.from('invitations').delete().eq('email', email); r.erreur = 'Création refusée : ' + (error?.message || 'raison inconnue'); continue; }
      await sb.from('audit_log').insert({ user_id: moi.user.id, action: 'REMPLACER_PREINSCRIPTION', table_name: 'profiles', row_id: existant.id, detail: { email } });
    }
    const id = cree!.user.id;
    const { data: profil } = await sb.from('profiles').select('id').eq('id', id).maybeSingle();
    if (profil) await sb.from('profiles').update({ mdp_provisoire: true }).eq('id', id);
    else await sb.from('profiles').insert({ id, nom, role: d.role, member_id: m.id, mdp_provisoire: true });
    await sb.from('invitations').delete().eq('email', email);
    if (!m.email) await sb.from('members').update({ email }).eq('id', m.id);
    await sb.from('audit_log').insert({ user_id: moi.user.id, action: 'CREER_COMPTE', table_name: 'profiles', row_id: id, detail: { email, role: d.role, member_id: m.id } });
    r.ok = true;
  }
  return json({ resultats });
});
