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

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
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
    const r = { member_id: d.member_id, email, ok: false, erreur: '' };
    resultats.push(r);
    if (!EMAIL.test(email)) { r.erreur = 'Adresse e-mail invalide'; continue; }
    if (!codes.has(String(d.role))) { r.erreur = 'Fonction inconnue'; continue; }
    const { data: m } = await sb.from('members').select('id, prenom, nom, email').eq('id', d.member_id).maybeSingle();
    if (!m) { r.erreur = 'Membre introuvable'; continue; }
    const { data: deja } = await sb.from('profiles').select('id').eq('member_id', m.id).maybeSingle();
    if (deja) { r.erreur = 'A déjà un compte'; continue; }
    const nom = `${m.prenom || ''} ${m.nom || ''}`.trim();
    // L'invitation porte la fonction et la fiche : le déclencheur de la base crée le profil à la création du compte
    await sb.from('invitations').delete().eq('member_id', m.id).neq('email', email);
    const { error: eInv } = await sb.from('invitations').upsert({ email, nom, role: d.role, member_id: m.id }, { onConflict: 'email' });
    if (eInv) { r.erreur = 'Préparation impossible : ' + eInv.message; continue; }
    const { data: cree, error } = await sb.auth.admin.createUser({ email, password: mdp, email_confirm: true, user_metadata: { nom } });
    if (error || !cree?.user) {
      await sb.from('invitations').delete().eq('email', email);
      r.erreur = /already|registered|exists/i.test(error?.message || '')
        ? 'Cette adresse a déjà un compte : rattachez-le dans Paramètres › Comptes et accès'
        : 'Création refusée : ' + (error?.message || 'raison inconnue');
      continue;
    }
    const id = cree.user.id;
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
