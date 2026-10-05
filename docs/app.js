// Trésorerie JP Grenoble — site web, phase 1
// Connexion, soldes, écritures, membres (fiche + import), cotisations, paramètres et accès.
import { SUPABASE_URL, SUPABASE_ANON_KEY } from './config.js';
import { C, eur0, colonnesGroupees, ligne, barresH, anneau, jauge, budgetBarres, serieMensuelle, reserveEnMois, libelleMois, brancherInfobulles, CSS_GRAPHIQUES } from './graphiques.js';

// Styles des graphiques (partagés par le tableau de bord et le rapport d'AG)
document.head.insertAdjacentHTML('beforeend', `<style>${CSS_GRAPHIQUES}</style>`);

const MOIS = ['janvier','février','mars','avril','mai','juin','juillet','août','septembre','octobre','novembre','décembre'];
const ROLES = { tresorier: 'Trésorier', president: 'Président', bureau: 'Bureau', adherent: 'Adhérent' };
const MODES = { especes: 'Espèces', virement: 'Virement', autre: 'Chèque ou carte' };

let sb;            // client Supabase (ou client de démonstration)
let demo = false;
const S = {        // état de l'application
  session: null, profil: null, org: null, logoUrl: 'logo.jpg', settings: {},
  comptes: [], categories: [], membres: [], photos: {}, droits: new Set(), roles: [], permissions: [], filtres: {},
  annee: new Date().getFullYear(),
};

// ---------- Outils ----------
const $ = (sel, root = document) => root.querySelector(sel);
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const eur = (n) => Number(n || 0).toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' });
const dateFr = (d) => d ? new Date(d + (String(d).length === 10 ? 'T12:00:00' : '')).toLocaleDateString('fr-FR') : '';
const aujourdhui = () => new Date().toISOString().slice(0, 10);
const peut = (...d) => d.some((x) => S.droits.has(x));   // au moins un de ces droits
const nomRole = (code) => S.roles.find((r) => r.code === code)?.nom || code;
const initiales = (m) => ((m.prenom || '')[0] || '') + ((m.nom || '')[0] || '');
const signe = (t) => (t.sens === 'recette' ? 1 : -1) * Number(t.montant);
const montantSigne = (t) => { const v = signe(t); return `<span class="num ${v >= 0 ? 'recette' : 'depense'}">${v >= 0 ? '+' : '−'} ${eur(Math.abs(v))}</span>`; };
const nomComplet = (m) => m ? `${m.prenom} ${m.nom}` : '';
const sansAccents = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toast.t); toast.t = setTimeout(() => (t.hidden = true), 3500);
}
function erreur(e) {
  console.error(e);
  const m = e?.message || String(e);
  toast(traduireErreur(m));
}
function traduireErreur(m) {
  if (/Invalid login credentials/i.test(m)) return 'Adresse ou mot de passe incorrect';
  if (/Email not confirmed/i.test(m)) return 'Adresse pas encore confirmée : ouvrez le lien reçu par e-mail';
  if (/row-level security|permission denied/i.test(m)) return 'Action réservée à un autre rôle';
  if (/foreign key/i.test(m) && /roles|profiles_role/i.test(m)) return 'Ce rôle est encore attribué à quelqu’un';
  if (/rate limit/i.test(m)) return 'Trop de tentatives, réessayez dans quelques minutes';
  if (/duplicate key/i.test(m)) return 'Cet élément existe déjà';
  return m;
}
async function q(promise) {           // exécute une requête et lève l'erreur éventuelle
  const { data, error } = await promise;
  if (error) throw error;
  return data;
}
function ouvrirFeuille(html, onReady) {
  const d = $('#sheet'); $('#sheet-body').innerHTML = html;
  if (!d.open) d.showModal();
  onReady?.($('#sheet-body'));
}
function fermerFeuille() { const d = $('#sheet'); if (d.open) d.close(); }
$('#sheet').addEventListener('click', (e) => { if (e.target.id === 'sheet') fermerFeuille(); });

// Numéro WhatsApp : 06 12 34 56 78 -> 33612345678 (format attendu par wa.me)
function numeroWa(n) {
  let d = String(n || '').replace(/[^\d+]/g, '');
  if (d.startsWith('+')) d = d.slice(1);
  else if (d.startsWith('00')) d = d.slice(2);
  else if (/^0[1-9]\d{8}$/.test(d)) d = '33' + d.slice(1);
  return /^\d{8,15}$/.test(d) ? d : '';
}

// Réduit une photo avant envoi : 400 px de côté, JPEG qualité 0,8 (environ 30 à 60 Ko)
async function compresserImage(file, max = 400, qualite = 0.8) {
  const bmp = await createImageBitmap(file);
  const r = Math.min(1, max / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas');
  c.width = Math.round(bmp.width * r); c.height = Math.round(bmp.height * r);
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  return new Promise((ok) => c.toBlob(ok, 'image/jpeg', qualite));
}

// Export CSV lisible par Excel en français : séparateur « ; », virgule décimale, BOM UTF-8
function telechargerCsv(nom, entetes, lignes) {
  const cell = (v) => {
    let s = typeof v === 'number' ? String(v).replace('.', ',') : String(v ?? '');
    return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = '﻿' + [entetes, ...lignes].map((l) => l.map(cell).join(';')).join('\r\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  a.download = nom; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}

// ---------- Démarrage ----------
async function demarrer() {
  const nonConfigure = SUPABASE_URL.includes('VOTRE') || SUPABASE_ANON_KEY.includes('VOTRE');
  demo = nonConfigure || new URLSearchParams(location.search).has('demo');
  if (demo) {
    const { createMockClient, COMPTES_DEMO, MOT_DE_PASSE } = await import('./mock.js');
    sb = createMockClient();
    S.comptesDemo = { liste: COMPTES_DEMO, mdp: MOT_DE_PASSE };
    afficherBandeauDemo();
  } else {
    const { createClient } = await import('https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm');
    sb = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  }
  sb.auth.onAuthStateChange((evt, session) => {
    if (evt === 'PASSWORD_RECOVERY') { S.session = session; return ecranNouveauMotDePasse(); }
    if (evt === 'SIGNED_OUT') { S.session = null; S.profil = null; ecranConnexion(); }
  });
  const { data } = await sb.auth.getSession();
  S.session = data.session;
  await chargerOrganisation();
  if (S.session) await entrer(); else ecranConnexion();
}

function afficherBandeauDemo() {
  const b = $('#demo-banner'); b.hidden = false;
  b.innerHTML = `<span><b>Mode démonstration</b> : données fictives, rien n’est enregistré.</span>
    <label>Voir en tant que <select id="demo-role">${Object.entries(ROLES).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></label>`;
  $('#demo-role').addEventListener('change', async (e) => {
    sb.__setRole(e.target.value);
    const { data } = await sb.auth.getSession(); S.session = data.session;
    await chargerOrganisation();
    await entrer();
  });
}

async function chargerOrganisation() {
  try {
    S.org = await q(sb.from('organisation').select('*').maybeSingle());
    if (S.org?.logo_path) { const u = sb.storage.from('logos').getPublicUrl(S.org.logo_path).data.publicUrl; S.logoUrl = u.startsWith('blob:') ? u : u + '?v=' + encodeURIComponent(S.org.logo_path); }
    S.banniereUrl = '';
    if (S.org?.banniere_path) { const u = sb.storage.from('logos').getPublicUrl(S.org.banniere_path).data.publicUrl; S.banniereUrl = u.startsWith('blob:') ? u : u + '?v=' + encodeURIComponent(S.org.banniere_path); }
    if (S.org?.nom) document.title = 'Trésorerie ' + S.org.nom;
  } catch (e) { console.warn(e); }
}

async function entrer() {
  try {
    S.profil = await q(sb.from('profiles').select('*').eq('id', S.session.user.id).maybeSingle());
    if (!S.profil || !S.profil.actif) return ecranSansAcces();
    if (!S.org) await chargerOrganisation();
    await chargerReferentiels();
    if (!location.hash) location.hash = '#tableau';
    router();
  } catch (e) { erreur(e); }
}

async function chargerReferentiels() {
  S.droits = new Set(await q(sb.rpc('mes_droits')));
  const [settings, categories, roles, permissions, projets] = await Promise.all([
    q(sb.from('settings').select('*')), q(sb.from('categories').select('*').order('nom')),
    q(sb.from('roles').select('*').order('nom')), q(sb.from('permissions').select('*').order('ordre')),
    q(sb.from('projects').select('*').order('date_debut', { ascending: false })),
  ]);
  S.settings = Object.fromEntries(settings.map((x) => [x.cle, x.valeur]));
  Object.assign(S, { categories, roles, permissions, projets });
  S.comptes = peut('consulter_finances', 'saisir_ecritures', 'payer_depenses', 'gerer_cotisations', 'rapprocher')
    ? await q(sb.from('accounts').select('*').eq('actif', true).order('nom')) : [];
  S.membres = peut('voir_membres', 'gerer_membres', 'gerer_cotisations') ? await q(sb.from('members').select('*').order('nom')) : [];
  S.tiers = peut('consulter_finances', 'saisir_ecritures', 'gerer_cotisations', 'payer_depenses') ? await q(sb.from('tiers').select('*').order('nom')) : [];
  S.collectes = peut('consulter_finances', 'gerer_cotisations', 'gerer_activites', 'saisir_ecritures') ? await q(sb.from('collectes').select('*').order('created_at', { ascending: false })) : [];
  await chargerPhotos(S.membres);
}

async function chargerPhotos(liste) {
  const chemins = liste.map((m) => m.photo_path).filter((p) => p && !S.photos[p]);
  if (!chemins.length) return;
  try {
    const urls = await q(sb.storage.from('photos').createSignedUrls(chemins, 3600));
    urls.forEach((u) => { if (u.signedUrl) S.photos[u.path] = u.signedUrl; });
  } catch (e) { console.warn(e); }
}

function avatar(m) {
  const url = m?.photo_path && S.photos[m.photo_path];
  return url ? `<span class="avatar"><img src="${esc(url)}" alt=""></span>` : `<span class="avatar" aria-hidden="true">${esc(initiales(m || {}))}</span>`;
}

// ---------- Connexion ----------
function ecranConnexion(message = '') {
  $('#app').innerHTML = `
  <main class="connexion"><form class="carte" id="f-connexion">
    <img src="${esc(S.logoUrl)}" alt="Logo ${esc(S.org?.nom || '')}">
    <h1>Trésorerie ${esc(S.org?.nom || '')}</h1>
    ${message ? `<p class="info">${esc(message)}</p>` : ''}
    <label class="champ">Adresse e-mail<input type="email" name="email" autocomplete="email" required></label>
    <label class="champ">Mot de passe<input type="password" name="mdp" autocomplete="current-password" minlength="8" required></label>
    <button class="btn-primaire" type="submit">Se connecter</button>
    <div class="actions" style="justify-content:space-between">
      <button class="btn-texte" type="button" id="b-creer">Créer mon compte</button>
      <button class="btn-texte" type="button" id="b-oubli">Mot de passe oublié</button>
    </div>
    <p class="muted">Le compte se crée avec l’adresse e-mail enregistrée par le trésorier.</p>
    ${demo ? '<button class="btn-tonal" type="button" id="b-demo">Comptes de démonstration</button>' : ''}
  </form></main>`;
  const f = $('#f-connexion');
  $('#b-demo')?.addEventListener('click', () => ouvrirFeuille(`<h2>Comptes de démonstration</h2>
    <p class="muted">Données fictives, effacées à la fermeture de la page.</p>
    <ul class="liste">${S.comptesDemo.liste.map(([r, e]) => `<li><div class="corps"><b>${r}</b><span>${e}</span></div><button class="btn-tonal btn-petit" data-email="${e}">Utiliser</button></li>`).join('')}</ul>
    <p>Mot de passe&nbsp;: <b>${S.comptesDemo.mdp}</b></p>
    <div class="actions"><button class="btn-texte" id="b-fermer">Fermer</button></div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    root.querySelectorAll('[data-email]').forEach((b) => b.addEventListener('click', () => { f.email.value = b.dataset.email; f.mdp.value = S.comptesDemo.mdp; fermerFeuille(); }));
  }));
  f.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const d = await q(sb.auth.signInWithPassword({ email: f.email.value.trim(), password: f.mdp.value }));
      S.session = d.session; await entrer();
    } catch (err) { erreur(err); }
  });
  $('#b-creer').addEventListener('click', async () => {
    if (!f.email.checkValidity() || !f.mdp.checkValidity()) return toast('Saisissez votre adresse et un mot de passe de 8 caractères minimum');
    try {
      const d = await q(sb.auth.signUp({ email: f.email.value.trim(), password: f.mdp.value, options: { emailRedirectTo: location.origin + location.pathname } }));
      if (d.session) { S.session = d.session; await entrer(); }
      else ecranConnexion('Compte créé. Ouvrez le lien reçu par e-mail pour le confirmer, puis connectez-vous.');
    } catch (err) { erreur(err); }
  });
  $('#b-oubli').addEventListener('click', async () => {
    if (!f.email.checkValidity()) return toast('Saisissez d’abord votre adresse e-mail');
    try {
      await q(sb.auth.resetPasswordForEmail(f.email.value.trim(), { redirectTo: location.origin + location.pathname }));
      toast('Lien envoyé, consultez votre messagerie');
    } catch (err) { erreur(err); }
  });
}

function ecranNouveauMotDePasse() {
  $('#app').innerHTML = `<main class="connexion"><form class="carte" id="f-mdp">
    <h1>Nouveau mot de passe</h1>
    <label class="champ">Mot de passe (8 caractères minimum)<input type="password" name="mdp" minlength="8" autocomplete="new-password" required></label>
    <button class="btn-primaire">Enregistrer</button></form></main>`;
  $('#f-mdp').addEventListener('submit', async (e) => {
    e.preventDefault();
    try { await q(sb.auth.updateUser({ password: e.target.mdp.value })); toast('Mot de passe modifié'); await entrer(); }
    catch (err) { erreur(err); }
  });
}

function ecranSansAcces() {
  $('#app').innerHTML = `<main class="connexion"><div class="carte">
    <img src="${esc(S.logoUrl)}" alt="">
    <h1>Accès en attente</h1>
    <p>Votre adresse <b>${esc(S.session?.user?.email)}</b> n’est pas encore autorisée. Demandez au trésorier de vous inviter avec cette adresse, puis reconnectez-vous.</p>
    <button class="btn-tonal" id="b-sortir">Se déconnecter</button></div></main>`;
  $('#b-sortir').addEventListener('click', () => sb.auth.signOut());
}

// ---------- Navigation ----------
const ICONES = {
  tableau: '<path d="M4 13h7V4H4zm9 7h7V4h-7zM4 20h7v-5H4z"/>',
  ecritures: '<path d="M5 4h14v16H5zM8 8h8M8 12h8M8 16h5"/>',
  membres: '<path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 9a7 7 0 0 1 14 0M17 11a3 3 0 1 0 0-6M22 20a5 5 0 0 0-4-4.9"/>',
  cotisations: '<path d="M3 7h18v12H3zM3 11h18M7 15h4"/>',
  depenses: '<path d="M12 3v18M17 7.5C17 5.6 14.8 4.5 12 4.5S7 5.6 7 7.5 9.2 10.3 12 11s5 1.6 5 3.5-2.2 3-5 3-5-1.1-5-3"/>',
  budget: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  activites: '<path d="M4 6h16v14H4zM4 10h16M9 3v5M15 3v5"/>',
  rapprochement: '<path d="M4 7h11M4 7l3-3M4 7l3 3M20 17H9M20 17l-3-3M20 17l-3 3"/>',
  rapports: '<path d="M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h7"/>',
  tiers: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM19 8v6M22 11h-6"/>',
  plus: '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
  parametres: '<path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
};
const icone = (k) => `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONES[k]}</svg>`;

// Les écrans affichés dépendent des droits du rôle de la personne
function pagesAutorisees() {
  const finances = peut('consulter_finances');
  return [
    ['tableau', 'Accueil', true],
    ['ecritures', 'Opérations', peut('consulter_finances', 'saisir_ecritures')],
    ['depenses', 'Dépenses', peut('demander_depenses', 'valider_depenses', 'payer_depenses', 'consulter_finances')],
    ['cotisations', peut('consulter_finances', 'gerer_cotisations') ? 'Cotisations' : 'Ma cotisation', true],
    ['budget', 'Budget', peut('consulter_finances', 'gerer_budget')],
    ['activites', 'Planning', true],
    ['tiers', 'Tiers', peut('consulter_finances', 'saisir_ecritures', 'gerer_cotisations')],
    ['membres', 'Membres', peut('voir_membres', 'gerer_membres')],
    ['rapprochement', 'Rapprochement', peut('consulter_finances', 'rapprocher')],
    ['rapports', 'Rapports', finances],
    ['parametres', 'Paramètres', peut('administrer')],
  ].filter((x) => x[2]).map(([k, l]) => [k, l]);
}

function coquille(page, contenu) {
  const pages = pagesAutorisees();
  const lien = ([k, l]) => `<a href="#${k}" ${k === page ? 'aria-current="page"' : ''}><span class="pastille">${icone(k)}</span>${l}</a>`;
  const nav = pages.map(lien).join('');
  // Téléphone : 4 entrées + « Plus » si la liste est longue
  const courtes = pages.length > 5 ? pages.slice(0, 4) : pages;
  const autres = pages.length > 5 ? pages.slice(4) : [];
  const enPlus = autres.some(([k]) => k === page);
  const navMobile = courtes.map(lien).join('') + (autres.length ? `<a href="#" id="b-plus" ${enPlus ? 'aria-current="page"' : ''}><span class="pastille">${icone('plus')}</span>Plus</a>` : '');
  $('#app').innerHTML = `
  <div class="shell">
    <nav class="rail" aria-label="Navigation"><img src="${esc(S.logoUrl)}" alt="">${nav}</nav>
    <div style="flex:1;min-width:0">
      <header class="entete">
        <img class="logo-mobile" src="${esc(S.logoUrl)}" alt="">
        <div class="titre"><b>${esc(S.org?.nom || 'Trésorerie')}</b></div>
        <div class="profil-menu">
          <button class="avatar-bouton" id="b-profil" aria-haspopup="true" aria-expanded="false" aria-label="Mon compte">${esc(initiales({ prenom: S.profil.nom.split(' ')[0], nom: S.profil.nom.split(' ')[1] || '' }))}</button>
          <div class="menu" id="menu-profil" hidden>
            <b>${esc(S.profil.nom)}</b><span class="muted">${esc(nomRole(S.profil.role))}</span>
            ${S.profil.member_id && peut('consulter_finances', 'gerer_cotisations') ? '<a class="btn btn-texte" href="#moi">Ma cotisation</a>' : ''}
            <button class="btn-texte" id="b-deconnexion">Se déconnecter</button>
          </div>
        </div>
      </header>
      <main class="contenu">${contenu}</main>
    </div>
    <nav class="barre-nav" aria-label="Navigation">${navMobile}</nav>
  </div>`;
  $('#b-deconnexion').addEventListener('click', () => sb.auth.signOut());
  const menu = $('#menu-profil'), bp = $('#b-profil');
  bp.addEventListener('click', (e) => { e.stopPropagation(); menu.hidden = !menu.hidden; bp.setAttribute('aria-expanded', !menu.hidden); });
  document.addEventListener('click', (e) => { if (!menu.contains(e.target)) { menu.hidden = true; bp.setAttribute('aria-expanded', 'false'); } });
  $('#b-plus')?.addEventListener('click', (e) => {
    e.preventDefault();
    ouvrirFeuille(`<h2>Plus</h2><ul class="liste">${autres.map(([k, l]) => `<li><a href="#${k}" class="lien-plus" style="display:flex;align-items:center;gap:12px;width:100%;color:inherit;text-decoration:none;font-weight:600;min-height:44px">${icone(k)}${l}</a></li>`).join('')}</ul>`,
      (root) => root.querySelectorAll('.lien-plus').forEach((a) => a.addEventListener('click', fermerFeuille)));
  });
}

const PAGES = { tableau: pageTableau, ecritures: pageEcritures, membres: pageMembres, cotisations: pageCotisations, parametres: pageParametres,
  depenses: pageDepenses, budget: pageBudget, activites: pageActivites, rapprochement: pageRapprochement, rapports: pageRapports,
  tiers: pageTiers, moi: () => pageCotisations(true) };
async function router() {
  if (!S.profil) return;
  let page = location.hash.slice(1) || 'tableau';
  if (!pagesAutorisees().some(([k]) => k === page) && !(page === 'moi' && S.profil.member_id)) page = 'tableau';
  fermerFeuille();
  coquille(page, '<p class="chargement">Chargement…</p>');
  try { await PAGES[page](); } catch (e) { erreur(e); }
  window.scrollTo(0, 0);
}
window.addEventListener('hashchange', router);
const rendre = (html) => { $('.contenu').innerHTML = html; };

// ---------- Tableau de bord ----------
// ---------- Accueil ----------
// Rubriques dépliables : l'état de chacune est mémorisé sur l'appareil.
const RUB_CLE = 'rubriquesAccueil';
const etatRubriques = () => { try { return JSON.parse(localStorage.getItem(RUB_CLE) || '{}'); } catch { return {}; } };
function rubrique(id, titre, resume, contenu, { ouverte = true, classe = '' } = {}) {
  const etat = etatRubriques()[id];
  const ouvert = etat === undefined ? ouverte : etat;
  return `<details class="carte rubrique ${classe}" data-rub="${id}" ${ouvert ? 'open' : ''}>
    <summary><span class="rub-titre"><h2>${titre}</h2>${resume ? `<span class="rub-resume">${resume}</span>` : ''}</span>
      <svg class="chevron" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg></summary>
    <div class="rub-corps">${contenu}</div></details>`;
}
function brancherRubriques() {
  const enregistrer = () => {
    const etat = {}; document.querySelectorAll('details[data-rub]').forEach((x) => { etat[x.dataset.rub] = x.open; });
    try { localStorage.setItem(RUB_CLE, JSON.stringify(etat)); } catch { /* stockage indisponible */ }
    const tout = $('#b-rubriques'); if (tout) tout.textContent = [...document.querySelectorAll('details[data-rub]')].every((x) => x.open) ? 'Tout replier' : 'Tout déplier';
  };
  document.querySelectorAll('details[data-rub]').forEach((x) => x.addEventListener('toggle', enregistrer));
  $('#b-rubriques')?.addEventListener('click', () => {
    const toutes = [...document.querySelectorAll('details[data-rub]')]; const ouvrir = !toutes.every((x) => x.open);
    toutes.forEach((x) => { x.open = ouvrir; }); enregistrer();
  });
  enregistrer();
}

// Bannière : photo de l'association (Paramètres > Association), sinon aplat aux couleurs du logo
function banniere(contenu) {
  const fond = S.banniereUrl ? ` style="--photo:url('${esc(S.banniereUrl)}')"` : '';
  const premier = esc(S.profil.nom.split(' ')[0]);
  const date = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return `<section class="banniere ${S.banniereUrl ? 'avec-photo' : ''}"${fond} aria-label="${esc(S.org?.nom || 'Association')}">
    <div class="banniere-tete"><img src="${esc(S.logoUrl)}" alt=""><div><b>${esc(S.org?.nom || '')}</b><span>Bonjour ${premier} · ${date}</span></div>
      ${!S.banniereUrl && peut('administrer') ? '<a class="banniere-ajout" href="#parametres" aria-label="Ajouter une photo" title="Ajouter une photo"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg><span>Ajouter une photo</span></a>' : ''}</div>
    ${contenu}</section>`;
}

const ICONE_COMPTE = {
  caisse: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="6" width="20" height="13" rx="2"/><circle cx="12" cy="12.5" r="2.5"/><path d="M6 10v5M18 10v5"/></svg>',
  banque: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 10l9-6 9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/></svg>',
};

async function pageTableau() {
  const mois = new Date().getMonth();
  const anniv = await q(sb.rpc('anniversaires_du_mois'));
  await chargerPhotos(anniv);
  if (!peut('consulter_finances')) return pageTableauAdherent(anniv, mois);

  const [soldes, cotis, retards, toutes, demandes, budget] = await Promise.all([
    q(sb.from('v_soldes').select('*')),
    q(sb.from('v_cotisations').select('*').eq('annee', S.annee)),
    q(sb.from('v_justificatifs_en_retard').select('*')),
    q(sb.from('transactions').select('*').order('date_op', { ascending: false }).order('created_at', { ascending: false })),
    q(sb.from('expense_requests').select('*')),
    q(sb.from('v_budget_suivi').select('*').eq('annee', new Date().getFullYear())),
  ]);
  const aValider = demandes.filter((d) => d.statut === 'soumise');
  const aPayer = demandes.filter((d) => d.statut === 'validee');
  const aJustifier = demandes.filter((d) => d.statut === 'payee' && (peut('payer_depenses', 'saisir_ecritures') || d.demandeur === S.profil.id));
  const alertesBudget = budget.filter((b) => b.alerte);
  const pl = (n, mot) => `${n} ${mot}${n > 1 ? 's' : ''}`;
  const somme = (l) => l.reduce((t, d) => t + Number(d.montant), 0);
  const taches = [
    retards.length ? `<li class="tache-alerte"><div class="corps"><b>${pl(retards.length, 'justificatif')} en retard</b><span>${retards.map((r) => `${esc(r.objet)} (${eur(r.montant)}, ${r.jours}&nbsp;jours)`).join(', ')}</span></div><a class="btn btn-tonal btn-petit" href="#depenses">Voir</a></li>` : '',
    peut('valider_depenses') && aValider.length ? `<li><div class="corps"><b>${pl(aValider.length, 'demande')} à valider</b><span>${eur(somme(aValider))}</span></div><a class="btn btn-primaire btn-petit" href="#depenses">Valider</a></li>` : '',
    peut('payer_depenses') && aPayer.length ? `<li><div class="corps"><b>${pl(aPayer.length, 'demande')} à payer</b><span>${eur(somme(aPayer))}</span></div><a class="btn btn-primaire btn-petit" href="#depenses">Payer</a></li>` : '',
    aJustifier.length ? `<li><div class="corps"><b>${pl(aJustifier.length, 'justificatif')} à joindre</b><span>Délai de ${S.settings.delai_justificatif_jours ?? 7}&nbsp;jours après paiement</span></div><a class="btn btn-tonal btn-petit" href="#depenses">Joindre</a></li>` : '',
    alertesBudget.length ? `<li><div class="corps"><b>${pl(alertesBudget.length, 'poste')} de budget en alerte</b><span>${alertesBudget.map((b) => esc(b.categorie)).join(', ')}</span></div><a class="btn btn-tonal btn-petit" href="#budget">Voir le budget</a></li>` : '',
  ].filter(Boolean);
  const total = soldes.reduce((t, x) => t + Number(x.solde), 0);
  const cat = (id) => S.categories.find((c) => c.id === id)?.nom || '';
  const dernieres = toutes.slice(0, 5);
  // Chiffres de l'année en cours, comparés à la même période l'an dernier ; série sur 12 mois
  const auj = new Date(), jour = aujourdhui(), an = auj.getFullYear();
  const serie = serieMensuelle(toutes, S.comptes, isoLocal(new Date(an, auj.getMonth() - 11, 1, 12)), jour);
  const flux = (l, sens) => l.filter((t) => t.sens === sens).reduce((t2, t) => t2 + Number(t.montant), 0);
  const ytd = toutes.filter((t) => t.date_op >= `${an}-01-01` && t.date_op <= jour);
  const ytdN1 = toutes.filter((t) => t.date_op >= `${an - 1}-01-01` && t.date_op <= `${an - 1}${jour.slice(4)}`);
  const rec = flux(ytd, 'recette'), dep = flux(ytd, 'depense'), resultat = rec - dep;
  const reserve = reserveEnMois(total, serie);
  const exigible = cotis.reduce((t, c) => t + Number(c.exigible || 0), 0);
  const encaisse = cotis.reduce((t, c) => t + Number(c.montant_paye), 0);
  const parCat = (sens) => { const o = {}; ytd.filter((t) => t.sens === sens).forEach((t) => { o[t.category_id] = (o[t.category_id] || 0) + Number(t.montant); }); return Object.entries(o).map(([id, v]) => ({ nom: cat(id), valeur: v })).sort((x, y) => y.valeur - x.valeur); };
  const recCat = parCat('recette'), depCat = parCat('depense');
  const variation = (a, b) => (b > 0 ? Math.round(100 * (a - b) / b) : null);
  const txtVar = (v) => (v == null ? 'Pas de comparaison' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v)}&nbsp;% sur un an`);
  const signeEur = (n) => `${n >= 0 ? '+' : '−'}&nbsp;${eur0(Math.abs(n))}`;
  const ecart12 = serie.length ? serie[serie.length - 1].solde - (serie[0].solde - serie[0].rec + serie[0].dep) : 0;
  const reserveTxt = reserve == null ? '–' : `${reserve.toLocaleString('fr-FR', { maximumFractionDigits: 1 })}&nbsp;mois`;

  const situation = banniere(`<a class="banniere-solde" href="#ecritures" data-compte=""><span>Trésorerie au ${dateFr(jour)}</span><b class="num">${eur(total)}</b></a>
    <div class="banniere-indic">
      <span>Résultat ${an}<b class="num">${signeEur(resultat)}</b></span>
      <span>Réserve<b class="num">${reserveTxt}</b></span>
      ${exigible > 0 ? `<span>Cotisations<b class="num">${Math.round(100 * encaisse / exigible)}&nbsp;%</b></span>` : ''}
    </div>`);
  const comptes = `<nav class="comptes" aria-label="Comptes">${soldes.map((c) => `<a class="compte" href="#ecritures" data-compte="${c.id}">
      <span class="compte-icone">${ICONE_COMPTE[c.type] || ICONE_COMPTE.banque}</span><span class="compte-nom">${esc(c.nom)}</span><b class="num ${Number(c.solde) < 0 ? 'negatif' : ''}">${eur(c.solde)}</b></a>`).join('')}</nav>`;

  const indicateurs = `<div class="kpis kpis-3">
      <div class="carte stat"><span class="muted">Recettes ${an}</span><b class="num recette">${eur0(rec)}</b><small class="muted">${txtVar(variation(rec, flux(ytdN1, 'recette')))}</small></div>
      <div class="carte stat"><span class="muted">Dépenses ${an}</span><b class="num depense">${eur0(dep)}</b><small class="muted">${txtVar(variation(dep, flux(ytdN1, 'depense')))}</small></div>
      <div class="carte stat">${exigible > 0 ? jauge({ titre: 'Cotisations encaissées', valeur: encaisse, cible: exigible, detail: `${eur0(encaisse)} sur ${eur0(exigible)} · ${cotis.filter((c) => c.statut === 'a_jour').length} membres à jour sur ${cotis.length}` }) : '<span class="muted">Cotisations</span><b class="num">–</b>'}</div>
    </div>`;
  const evolution = `<div class="grille grille-2">
      ${colonnesGroupees({ titre: 'Recettes et dépenses par mois', libelles: serie.map((x) => libelleMois(x.mois)), series: [{ nom: 'Recettes', couleur: C.recette, valeurs: serie.map((x) => x.rec) }, { nom: 'Dépenses', couleur: C.depense, valeurs: serie.map((x) => x.dep) }] })}
      ${ligne({ titre: 'Trésorerie en fin de mois', libelles: serie.map((x) => libelleMois(x.mois)), valeurs: serie.map((x) => x.solde) })}
    </div>`;
  const repartition = `<div class="grille grille-2">
      ${anneau({ titre: 'Origine des recettes', items: recCat, sens: 'recette' })}
      ${anneau({ titre: 'Destination des dépenses', items: depCat, sens: 'depense' })}
    </div>`;
  const operations = dernieres.length ? `<ul class="liste">${dernieres.map((t) => `<li><div class="corps"><b>${esc(t.libelle)}</b><span>${dateFr(t.date_op)} · ${esc(cat(t.category_id))}</span></div>${montantSigne(t)}</li>`).join('')}</ul>
      <a class="btn btn-texte" href="#ecritures" style="align-self:flex-start">Toutes les opérations</a>`
    : `<div class="vide">Aucune opération.${peut('saisir_ecritures') ? '<a class="btn btn-primaire" href="#ecritures">Nouvelle opération</a>' : ''}</div>`;

  rendre(`<div class="page accueil">
    ${situation}
    ${comptes}
    <div class="rub-outils"><button class="btn-texte btn-petit" id="b-rubriques">Tout replier</button></div>
    ${taches.length ? rubrique('traiter', 'À traiter', pl(taches.length, 'action'), `<ul class="liste">${taches.join('')}</ul>`, { classe: retards.length ? 'rub-alerte' : '' }) : ''}
    ${rubrique('indicateurs', `Chiffres ${an}`, `Recettes ${eur0(rec)} · Dépenses ${eur0(dep)}`, indicateurs)}
    ${rubrique('evolution', 'Évolution sur 12 mois', `Trésorerie ${signeEur(ecart12)}`, evolution)}
    ${rubrique('repartition', `Répartition ${an}`, depCat.length ? `Premier poste de dépense&nbsp;: ${esc(depCat[0].nom)}` : '', repartition)}
    <div class="grille grille-2 rub-grille">
      ${rubrique('anniversaires', `Anniversaires ${/^[aeiouéâ]/.test(MOIS[mois]) ? 'd’' : 'de '}${MOIS[mois]}`, anniv.length ? pl(anniv.length, 'personne') : 'Aucun', listeAnniversaires(anniv, mois))}
      ${rubrique('operations', 'Dernières opérations', '', operations)}
    </div>
  </div>`);
  brancherInfobulles($('.contenu'));
  brancherRubriques();
  document.querySelectorAll('[data-compte]').forEach((a) => a.addEventListener('click', () => {
    S.filtres = { periode: 'annee', compte: a.dataset.compte };
  }));
}

function listeAnniversaires(anniv, mois) {
  const jour = new Date().getDate();
  return anniv.length ? `<ul class="liste">${anniv.map((a) => {
    const libelle = a.jour === jour ? '<span class="puce puce-partiel">Aujourd’hui</span>' : a.jour < jour ? '<span class="puce puce-neutre">Passé</span>' : '';
    return `<li>${avatar({ ...a })}<div class="corps"><b>${esc(a.prenom)} ${esc(a.nom)}</b><span>${a.jour} ${MOIS[mois]}${a.profession ? ' · ' + esc(a.profession) : ''}</span></div>${libelle}</li>`;
  }).join('')}</ul>` : '<div class="vide">Aucun anniversaire ce mois-ci.</div>';
}

async function pageTableauAdherent(anniv, mois) {
  const [cot, planning, parts] = await Promise.all([q(sb.rpc('ma_cotisation')), q(sb.rpc('planning_activites', { p_debut: isoLocal(new Date()) })), q(sb.rpc('mes_participations'))]);
  rendre(`<div class="page accueil">
    ${banniere('')}
    <div class="rub-outils"><button class="btn-texte btn-petit" id="b-rubriques">Tout replier</button></div>
    <div class="grille grille-2 rub-grille">
      ${rubrique('macotisation', 'Ma cotisation', '', blocMaCotisation(cot))}
      ${rubrique('anniversaires', `Anniversaires ${/^[aeiouéâ]/.test(MOIS[mois]) ? 'd’' : 'de '}${MOIS[mois]}`, anniv.length ? `${anniv.length} personne${anniv.length > 1 ? 's' : ''}` : 'Aucun', listeAnniversaires(anniv, mois))}
    </div>
    ${rubrique('avenir', 'À venir', planning.length ? `${Math.min(5, planning.length)} rendez-vous` : '', `${listePlanning(planning.slice(0, 5))}
      <a class="btn btn-texte" href="#activites" style="align-self:flex-start">Voir le planning</a>`)}
    ${parts.length ? rubrique('participations', 'Mes participations', '', listeParticipations(parts)) : ''}
    </div>`);
  brancherRubriques();
  document.querySelectorAll('[data-evt]').forEach((li) => li.addEventListener('click', () => detailEvenement(planning.find((p) => p.id === li.dataset.evt), () => router())));
}

// ---------- Opérations : recettes, dépenses et soldes, avec filtres ----------
const debutMois = (d = new Date()) => new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
const finMois = (d = new Date()) => new Date(d.getFullYear(), d.getMonth() + 1, 0, 12).toISOString().slice(0, 10);
const PERIODES = {
  mois: ['Ce mois', () => [debutMois(), finMois()]],
  precedent: ['Mois précédent', () => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - 1); return [debutMois(d), finMois(d)]; }],
  annee: ['Cette année', () => { const a = new Date().getFullYear(); return [`${a}-01-01`, `${a}-12-31`]; }],
  tout: ['Tout', () => ['', '']],
};

async function pageEcritures() {
  const f = S.filtres;
  if (!f.periode) Object.assign(f, { periode: 'annee', sens: '', compte: f.compte || '', categorie: '', texte: '', sansPiece: false });
  if (f.periode !== 'perso') [f.du, f.au] = PERIODES[f.periode][1]();
  const [toutes, piecesListe] = await Promise.all([
    q(sb.from('transactions').select('*').order('date_op', { ascending: false }).order('created_at', { ascending: false })),
    q(sb.from('attachments').select('*')),
  ]);
  const pieces = {};
  piecesListe.forEach((a) => { if (a.transaction_id) pieces[a.transaction_id] = a; });
  const contrepassees = new Set(toutes.filter((t) => t.contrepasse_de).map((t) => t.contrepasse_de));
  const cat = (id) => S.categories.find((c) => c.id === id)?.nom || '';
  const cpt = (id) => S.comptes.find((c) => c.id === id)?.nom || '';
  const texte = sansAccents(f.texte);
  const base = toutes.filter((t) => (!f.du || t.date_op >= f.du) && (!f.au || t.date_op <= f.au)
    && (!f.compte || t.account_id === f.compte) && (!f.categorie || t.category_id === f.categorie)
    && (!f.rubrique || (f.rubrique === 'cotisation' ? t.est_cotisation : t.collecte_id === f.rubrique))
    && (!texte || sansAccents(`${t.libelle} ${nomTiers(t)} ${cat(t.category_id)} ${nomRubrique(t)}`).includes(texte))
    && (!f.sansPiece || (t.sens === 'depense' && t.montant > 0 && !t.contrepasse_de && !contrepassees.has(t.id) && !pieces[t.id])));
  // Totaux sur tous les sens : recettes et dépenses restent visibles quel que soit le filtre
  const lignes = base.filter((t) => !f.sens || t.sens === f.sens);
  const rec = base.filter((t) => t.sens === 'recette').reduce((s, t) => s + Number(t.montant), 0);
  const dep = base.filter((t) => t.sens === 'depense').reduce((s, t) => s + Number(t.montant), 0);
  // Solde à la fin de la période : solde de départ + toutes les écritures jusqu'à cette date
  const comptesVus = f.compte ? S.comptes.filter((c) => c.id === f.compte) : S.comptes;
  const soldeFin = comptesVus.reduce((s, c) => s + Number(c.solde_initial || 0), 0)
    + toutes.filter((t) => (!f.au || t.date_op <= f.au) && comptesVus.some((c) => c.id === t.account_id)).reduce((s, t) => s + signe(t), 0);
  const filtresActifs = f.sens || f.compte || f.categorie || f.rubrique || f.texte || f.sansPiece || f.periode !== 'annee';
  const puceEtat = (t) => t.rapproche ? '<span class="puce puce-ok">Rapprochée</span>' : t.contrepasse_de ? '<span class="puce puce-neutre">Correction</span>'
    : contrepassees.has(t.id) ? '<span class="puce puce-neutre">Annulée</span>' : '';
  const iconePiece = (t) => pieces[t.id] ? '<svg class="trombone" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" role="img" aria-label="Pièce jointe"><path d="M21 11.5l-8.6 8.6a5 5 0 0 1-7.1-7.1l8.6-8.6a3.3 3.3 0 0 1 4.7 4.7l-8.6 8.6a1.7 1.7 0 0 1-2.4-2.4l7.9-7.9"/></svg>'
    : t.sens === 'depense' && t.montant > 0 && !t.contrepasse_de ? '<span class="puce puce-ko">Sans pièce</span>' : '';

  rendre(`<div class="page">
    <div class="page-titre"><h1>Opérations</h1><button class="btn-bleu btn-petit" id="b-export">Exporter</button></div>
    <div class="filtres">
      <div class="groupe" role="group" aria-label="Type">${[['', 'Tout'], ['recette', 'Recettes'], ['depense', 'Dépenses']].map(([k, l]) => `<button type="button" data-sens="${k}" aria-pressed="${f.sens === k}">${l}</button>`).join('')}</div>
      <select id="f-periode" aria-label="Période">${Object.entries(PERIODES).map(([k, [l]]) => `<option value="${k}" ${f.periode === k ? 'selected' : ''}>${l}</option>`).join('')}<option value="perso" ${f.periode === 'perso' ? 'selected' : ''}>Du… au…</option></select>
      ${f.periode === 'perso' ? `<input type="date" id="f-du" value="${esc(f.du)}" aria-label="Du"><input type="date" id="f-au" value="${esc(f.au)}" aria-label="Au">` : ''}
      <select id="f-compte" aria-label="Compte"><option value="">Tous les comptes</option>${S.comptes.map((c) => `<option value="${c.id}" ${f.compte === c.id ? 'selected' : ''}>${esc(c.nom)}</option>`).join('')}</select>
      <select id="f-cat" aria-label="Catégorie"><option value="">Toutes les catégories</option>${S.categories.filter((c) => !f.sens || c.sens === f.sens).map((c) => `<option value="${c.id}" ${f.categorie === c.id ? 'selected' : ''}>${esc(c.nom)}${f.sens ? '' : c.sens === 'recette' ? ' (recette)' : ' (dépense)'}</option>`).join('')}</select>
      <select id="f-rub" aria-label="Rubrique"><option value="">Toutes les rubriques</option><option value="cotisation" ${f.rubrique === 'cotisation' ? 'selected' : ''}>Cotisations</option>${(S.collectes || []).map((c) => `<option value="${c.id}" ${f.rubrique === c.id ? 'selected' : ''}>${esc(c.nom)}</option>`).join('')}</select>
      <input type="search" id="f-texte" value="${esc(f.texte)}" placeholder="Libellé, tiers…" aria-label="Rechercher">
      <label class="case"><input type="checkbox" id="f-piece" ${f.sansPiece ? 'checked' : ''}> Sans pièce</label>
      ${filtresActifs ? '<button class="btn-texte btn-petit" id="f-raz">Réinitialiser</button>' : ''}
    </div>
    <div class="kpis kpis-4">
      <button class="carte kpi-bouton" data-sens="recette"><span class="muted">Recettes</span><b class="num recette">${eur(rec)}</b></button>
      <button class="carte kpi-bouton" data-sens="depense"><span class="muted">Dépenses</span><b class="num depense">${eur(dep)}</b></button>
      <div class="carte"><span class="muted">Résultat</span><b class="num ${rec - dep < 0 ? 'negatif' : ''}">${eur(rec - dep)}</b></div>
      <div class="carte"><span class="muted">Solde${f.au ? ' au ' + dateFr(f.au > aujourdhui() ? aujourdhui() : f.au) : ''}</span><b class="num ${soldeFin < 0 ? 'negatif' : ''}">${eur(soldeFin)}</b></div>
    </div>
    <section class="carte">
    ${lignes.length ? `<ul class="liste">${lignes.map((t) => `<li class="cliquable" data-detail="${t.id}" tabindex="0" role="button">
        <div class="corps"><b>${esc(t.libelle)}</b><span>${dateFr(t.date_op)} · ${nomTiers(t) ? esc(nomTiers(t)) + ' · ' : ''}${esc(nomRubrique(t) || cat(t.category_id))} · ${esc(cpt(t.account_id))}</span></div>
        ${puceEtat(t)} ${iconePiece(t)} ${montantSigne(t)}</li>`).join('')}</ul>`
      : `<div class="vide">Aucune opération${filtresActifs ? ' pour ces filtres' : ''}.${peut('saisir_ecritures') && !filtresActifs ? '<button class="btn-primaire" id="b-nouvelle-vide">Nouvelle opération</button>' : ''}</div>`}
    </section>
    ${peut('saisir_ecritures') ? `<button class="fab" id="b-nouvelle" aria-label="Nouvelle opération" title="Nouvelle opération"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg></button>` : ''}
  </div>`);

  const recharger = () => pageEcritures().catch(erreur);
  document.querySelectorAll('[data-sens]').forEach((b) => b.addEventListener('click', () => { f.sens = b.dataset.sens; f.categorie = ''; recharger(); }));
  $('#f-periode').addEventListener('change', (e) => { f.periode = e.target.value; if (f.periode === 'perso') { f.du = f.du || debutMois(); f.au = f.au || aujourdhui(); } recharger(); });
  $('#f-du')?.addEventListener('change', (e) => { f.du = e.target.value; recharger(); });
  $('#f-au')?.addEventListener('change', (e) => { f.au = e.target.value; recharger(); });
  $('#f-compte').addEventListener('change', (e) => { f.compte = e.target.value; recharger(); });
  $('#f-cat').addEventListener('change', (e) => { f.categorie = e.target.value; recharger(); });
  $('#f-rub').addEventListener('change', (e) => { f.rubrique = e.target.value; recharger(); });
  let minuteur; $('#f-texte').addEventListener('input', (e) => { clearTimeout(minuteur); minuteur = setTimeout(() => { f.texte = e.target.value; recharger().then?.(() => { const i = $('#f-texte'); i.focus(); i.setSelectionRange(i.value.length, i.value.length); }); }, 350); });
  $('#f-piece').addEventListener('change', (e) => { f.sansPiece = e.target.checked; recharger(); });
  $('#f-raz')?.addEventListener('click', () => { S.filtres = {}; recharger(); });
  ['#b-nouvelle', '#b-nouvelle-vide'].forEach((sel) => $(sel)?.addEventListener('click', () => feuilleEcriture()));
  $('#b-export').addEventListener('click', () => telechargerCsv(`operations-${f.du || 'debut'}-${f.au || aujourdhui()}.csv`,
    ['Date', 'Sens', 'Libellé', 'Tiers', 'Rubrique', 'Catégorie', 'Activité', 'Compte', 'Mode', 'Montant', 'Pièce', 'Rapprochée'],
    lignes.map((t) => [dateFr(t.date_op), t.sens === 'recette' ? 'Recette' : 'Dépense', t.libelle, nomTiers(t), nomRubrique(t), cat(t.category_id), nomProjet(t.project_id), cpt(t.account_id), MODES[t.mode], signe(t), pieces[t.id] ? 'Oui' : 'Non', t.rapproche ? 'Oui' : 'Non'])));
  document.querySelectorAll('[data-detail]').forEach((li) => {
    const ouvrir = () => detailEcriture(toutes.find((t) => t.id === li.dataset.detail), pieces, contrepassees, recharger);
    li.addEventListener('click', ouvrir);
    li.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); ouvrir(); } });
  });
}

// Fiche d'une opération : détails, pièce jointe affichée, actions
async function detailEcriture(t, pieces, contrepassees, recharger) {
  const piece = pieces[t.id];
  let apercu = '';
  if (piece) {
    try {
      const d = await q(sb.storage.from('justificatifs').createSignedUrl(piece.storage_path, 600));
      apercu = /pdf/i.test(piece.mime || piece.storage_path)
        ? `<a class="btn btn-tonal" href="${esc(d.signedUrl)}" target="_blank" rel="noopener">Ouvrir le PDF</a>`
        : `<a href="${esc(d.signedUrl)}" target="_blank" rel="noopener"><img src="${esc(d.signedUrl)}" alt="Justificatif de ${esc(t.libelle)}" class="apercu-piece"></a>`;
    } catch { apercu = '<p class="muted">Pièce indisponible</p>'; }
  }
  const demande = t.request_id ? (await q(sb.from('expense_requests').select('*').eq('id', t.request_id)))[0] : null;
  const ligne = (l, v) => v ? `<div class="ligne-detail"><span class="muted">${l}</span><span>${v}</span></div>` : '';
  const peutCorriger = peut('saisir_ecritures') && !t.rapproche && !t.contrepasse_de && !contrepassees.has(t.id);
  ouvrirFeuille(`<div style="display:flex;justify-content:space-between;gap:12px;align-items:flex-start"><h2>${esc(t.libelle)}</h2>${montantSigne(t)}</div>
    <div class="details">
      ${ligne('Date', dateFr(t.date_op))}
      ${ligne('Catégorie', esc(nomCategorie(t.category_id)))}
      ${ligne('Compte', esc(S.comptes.find((c) => c.id === t.account_id)?.nom || ''))}
      ${ligne('Mode', MODES[t.mode])}
      ${ligne('Activité', esc(nomProjet(t.project_id)))}
      ${ligne('Tiers', esc(nomTiers(t)))}
      ${ligne('Rubrique', esc(nomRubrique(t)))}
      ${ligne('Demande', demande ? `${esc(demande.objet)} · validée le ${dateFr(String(demande.validee_le || '').slice(0, 10))}` : '')}
      ${ligne('État', t.rapproche ? 'Rapprochée, verrouillée' : t.contrepasse_de ? 'Correction d’une autre écriture' : contrepassees.has(t.id) ? 'Annulée par contre-passation' : '')}
    </div>
    ${apercu}
    <div class="actions">
      ${demande?.signature_path ? `<button class="btn-texte" data-voir="${esc(demande.signature_path)}" data-bucket="signatures">Signature</button>` : ''}
      ${!piece && t.sens === 'depense' && t.montant > 0 && peut('saisir_ecritures') ? `<button class="btn-tonal" data-joindre="${t.id}">Joindre une pièce</button>` : ''}
      ${peutCorriger ? `<button class="btn-texte" id="b-contre">Contre-passer</button>` : ''}
      <button class="btn-primaire" id="b-fermer">Fermer</button>
    </div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    $('#b-contre', root)?.addEventListener('click', () => contrePasser(t));
    brancherPieces(() => { fermerFeuille(); recharger(); });
  });
}

// Saisie d'une opération. Une recette se rattache à un tiers et, si besoin, à une rubrique :
// cotisation (membre obligatoire) ou participation à une activité.
// pre : { sens, member_id, rubrique ('cotisation' ou id de collecte), montant, apres }
function feuilleEcriture(pre = {}) {
  const limite = !peut('saisir_ecritures');   // droit « cotisations » seul : encaissements rattachés à une rubrique
  let sens = limite ? 'recette' : (pre.sens || (pre.rubrique ? 'recette' : 'depense'));
  const options = () => S.categories.filter((c) => c.sens === sens).map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('');
  const collectes = (S.collectes || []).filter((c) => !c.cloturee || c.id === pre.rubrique);
  ouvrirFeuille(`<form id="f-ecr" class="champs">
    <h2>${pre.rubrique || limite ? 'Encaissement' : 'Nouvelle opération'}</h2>
    ${limite ? '' : `<div class="groupe" role="group" aria-label="Type">
      <button type="button" data-sens="depense" aria-pressed="${sens === 'depense'}">Dépense</button>
      <button type="button" data-sens="recette" aria-pressed="${sens === 'recette'}">Recette</button>
    </div>`}
    <div class="champs champs-2">
      <label class="champ"><span class="obligatoire">Montant (€)</span><input name="montant" type="number" inputmode="decimal" step="0.01" min="0.01" required></label>
      <label class="champ"><span class="obligatoire">Date</span><input name="date" type="date" value="${aujourdhui()}" required></label>
      ${champTiers(sens, pre.member_id ? nomMembre(pre.member_id) : '')}
      <label class="champ" id="l-rubrique">Rubrique<select name="rubrique">${limite ? '' : '<option value="">Aucune</option>'}<option value="cotisation">Cotisation</option>${collectes.map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('')}</select></label>
    </div>
    <p class="info" id="i-rub" hidden></p>
    <label class="champ"><span class="obligatoire">Libellé</span><input name="libelle" maxlength="120" required></label>
    <div class="champs champs-2">
      <label class="champ"><span class="obligatoire">Catégorie</span><select name="categorie" required>${options()}</select></label>
      <label class="champ"><span class="obligatoire">Compte</span><select name="compte" required>${S.comptes.map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('')}</select></label>
      <label class="champ">Mode de paiement<select name="mode">${Object.entries(MODES).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></label>
      <label class="champ">Activité<select name="projet"><option value="">Aucune</option>${optionsProjets()}</select></label>
      <label class="champ" id="l-piece">Justificatif<input type="file" name="piece" accept="image/*,application/pdf"></label>
    </div>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer</button></div>
  </form>`, (root) => {
    const f = $('#f-ecr', root);
    let libelleSaisi = false, infoMembre = null;
    if (pre.rubrique) f.rubrique.value = pre.rubrique;
    if (pre.montant) f.montant.value = Number(pre.montant).toFixed(2);
    const choisirCat = (nom) => { const c = S.categories.find((x) => x.sens === 'recette' && x.nom === nom); if (c) f.categorie.value = c.id; };
    const maj = async () => {
      const rub = sens === 'recette' ? f.rubrique.value : '';
      $('#l-rubrique', root).hidden = sens !== 'recette';
      $('#l-piece', root).hidden = sens !== 'depense';
      const info = $('#i-rub', root);
      const nom = f.tiers.value.trim();
      const t = trouverTiers(nom);
      info.hidden = true;
      if (rub === 'cotisation') {
        choisirCat('Cotisations');
        if (!libelleSaisi) f.libelle.value = nom ? `Cotisation : ${nom}` : 'Cotisation';
        if (t.member_id) {
          if (infoMembre?.id !== t.member_id) infoMembre = { id: t.member_id, periodes: await q(sb.from('v_cotisations_periodes').select('*').eq('member_id', t.member_id)) };
          const retard = retardDe(infoMembre.periodes);
          const montant = Number(f.montant.value || 0);
          const c = couverture(infoMembre.periodes, montant);
          info.innerHTML = (retard > 0 ? `En retard&nbsp;: <b>${eur(retard)}</b>` : 'À jour')
            + (montant > 0 ? ` · Ce versement règle jusqu’à <b>${c.jusqua ? nomPeriode(c.jusqua) : '–'}</b>${c.partiel ? `, ${nomPeriode(c.partiel)} en partie` : ''}${c.avance > 0 ? `, avance de ${eur(c.avance)}` : ''}` : '');
        } else info.textContent = 'Choisissez un membre dans la liste';
        info.hidden = false;
      } else if (rub) {
        const co = S.collectes.find((x) => x.id === rub);
        choisirCat('Activités / événements');
        if (co?.project_id) f.projet.value = co.project_id;
        if (!libelleSaisi) f.libelle.value = nom ? `${co.nom} : ${nom}` : co.nom;
        if (co?.montant_attendu) { info.innerHTML = `Attendu&nbsp;: <b>${eur(co.montant_attendu)}</b> par personne`; info.hidden = false; }
      } else if (!libelleSaisi && /^Cotisation|^Participation/.test(f.libelle.value)) f.libelle.value = '';
    };
    f.libelle.addEventListener('input', () => { libelleSaisi = f.libelle.value !== ''; });
    root.querySelectorAll('[data-sens]').forEach((b) => b.addEventListener('click', () => {
      sens = b.dataset.sens;
      root.querySelectorAll('[data-sens]').forEach((x) => x.setAttribute('aria-pressed', x === b));
      f.categorie.innerHTML = options();
      f.tiers.placeholder = sens === 'recette' ? 'Membre ou donateur' : 'Fournisseur';
      maj().catch(erreur);
    }));
    ['change', 'input'].forEach((ev) => { f.tiers.addEventListener(ev, () => maj().catch(erreur)); f.montant.addEventListener(ev, () => maj().catch(erreur)); });
    f.rubrique.addEventListener('change', () => maj().catch(erreur));
    // Espèces -> caisse, virement -> banque par défaut
    f.mode.addEventListener('change', () => {
      const t = f.mode.value === 'especes' ? 'caisse' : f.mode.value === 'virement' ? 'banque' : null;
      const c = t && S.comptes.find((x) => x.type === t); if (c) f.compte.value = c.id;
    });
    f.mode.dispatchEvent(new Event('change'));
    maj().catch(erreur);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const rub = sens === 'recette' ? f.rubrique.value : '';
      if (rub === 'cotisation' && !trouverTiers(f.tiers.value).member_id) return toast('Une cotisation se rattache à un membre : choisissez-le dans la liste');
      const btn = f.querySelector('button:not([type])'); btn.disabled = true;
      try {
        const tiers = await resoudreTiers(f.tiers.value, sens);
        const [t] = await q(sb.from('transactions').insert({
          date_op: f.date.value, sens, montant: Number(f.montant.value), libelle: f.libelle.value.trim(),
          category_id: f.categorie.value, account_id: f.compte.value, mode: f.mode.value, project_id: f.projet.value || null, created_by: S.profil.id,
          member_id: tiers.member_id || null, tiers_id: tiers.tiers_id || null,
          est_cotisation: rub === 'cotisation', collecte_id: rub && rub !== 'cotisation' ? rub : null,
        }).select());
        if (sens === 'depense' && f.piece.files[0]) await deposerPiece(f.piece.files[0], { transaction_id: t.id });
        fermerFeuille(); toast(rub ? 'Encaissement enregistré' : 'Opération enregistrée');
        (pre.apres || (() => router()))();
      } catch (err) { btn.disabled = false; erreur(err); }
    });
  });
}

// Pas de suppression : une erreur se corrige par une écriture inverse (contre-passation)
function contrePasser(t) {
  ouvrirFeuille(`<h2>Contre-passer cette opération&#8239;?</h2>
    <p>Une opération de <b>−${eur(t.montant)}</b> annule «&nbsp;${esc(t.libelle)}&nbsp;» à la date du jour. L’opération d’origine reste dans l’historique.</p>
    <div class="actions"><button class="btn-texte" id="b-non">Annuler</button><button class="btn-danger" id="b-oui">Contre-passer</button></div>`, (root) => {
    $('#b-non', root).addEventListener('click', fermerFeuille);
    $('#b-oui', root).addEventListener('click', async () => {
      try {
        await q(sb.from('transactions').insert({
          date_op: aujourdhui(), sens: t.sens, montant: -Number(t.montant), contrepasse_de: t.id,
          libelle: `Contre-passation : ${t.libelle}`.slice(0, 120), category_id: t.category_id, project_id: t.project_id,
          account_id: t.account_id, mode: t.mode, member_id: t.member_id, tiers_id: t.tiers_id,
          est_cotisation: !!t.est_cotisation, collecte_id: t.collecte_id || null, created_by: S.profil.id,
        }));
        fermerFeuille(); toast('Opération contre-passée'); router();
      } catch (err) { erreur(err); }
    });
  });
}

// ---------- Membres ----------
async function pageMembres() {
  const actifs = S.membres.filter((m) => m.actif);
  const inactifs = S.membres.length - actifs.length;
  rendre(`<div class="page">
    <div class="page-titre"><h1>Membres</h1>
      <span class="muted">${actifs.length} actif${actifs.length > 1 ? 's' : ''}${inactifs ? `, ${inactifs} inactif${inactifs > 1 ? 's' : ''}` : ''}</span>
      ${peut('gerer_membres') ? '<button class="btn-tonal btn-petit" id="b-import">Importer (CSV, Excel)</button>' : ''}
      <button class="btn-bleu btn-petit" id="b-export-m">Exporter</button>
    </div>
    <input type="search" id="recherche" placeholder="Rechercher un membre" aria-label="Rechercher un membre">
    <section class="carte">
      ${S.membres.length ? `<ul class="liste" id="liste-membres">${S.membres.map((m) => `
        <li data-nom="${esc(sansAccents(nomComplet(m)))}" ${m.actif ? '' : 'style="opacity:.55"'}>
          ${avatar(m)}
          <div class="corps"><b>${esc(nomComplet(m))}</b><span>${m.naissance_jour} ${MOIS[m.naissance_mois - 1]}${m.profession ? ' · ' + esc(m.profession) : ''}${m.actif ? '' : ' · inactif'}</span></div>
          ${m.consent_anniversaire ? '' : '<span class="puce puce-neutre" title="Anniversaire visible uniquement par le bureau">Sans accord</span>'}
          ${peut('gerer_membres') ? `<button class="btn-texte btn-petit" data-modif="${m.id}">Modifier</button>` : ''}
        </li>`).join('')}</ul>`
      : `<div class="vide">Aucun membre.${peut('gerer_membres') ? '<span>Ajoutez-les un par un ou importez votre liste.</span>' : ''}</div>`}
    </section>
    ${peut('gerer_membres') ? `<button class="fab" id="b-ajout" aria-label="Ajouter un membre" title="Ajouter un membre"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg></button>` : ''}
  </div>`);
  $('#recherche').addEventListener('input', (e) => {
    const v = sansAccents(e.target.value);
    document.querySelectorAll('#liste-membres li').forEach((li) => { li.hidden = v && !li.dataset.nom.includes(v); });
  });
  $('#b-ajout')?.addEventListener('click', () => feuilleMembre());
  $('#b-import')?.addEventListener('click', feuilleImport);
  document.querySelectorAll('[data-modif]').forEach((b) => b.addEventListener('click', () => feuilleMembre(S.membres.find((m) => m.id === b.dataset.modif))));
  $('#b-export-m').addEventListener('click', () => telechargerCsv('membres.csv',
    ['Prénom', 'Nom', 'Jour', 'Mois', 'Profession', 'WhatsApp', 'E-mail', 'Accord anniversaire', 'Actif'],
    S.membres.map((m) => [m.prenom, m.nom, m.naissance_jour, m.naissance_mois, m.profession, m.whatsapp, m.email, m.consent_anniversaire ? 'Oui' : 'Non', m.actif ? 'Oui' : 'Non'])));
}

function feuilleMembre(m = null) {
  const v = m || { prenom: '', nom: '', naissance_jour: '', naissance_mois: '', profession: '', whatsapp: '', email: '', consent_anniversaire: false, actif: true };
  let photoBlob = null;
  ouvrirFeuille(`<form id="f-membre" class="champs">
    <h2>${m ? 'Modifier la fiche' : 'Nouveau membre'}</h2>
    <div style="display:flex;align-items:center;gap:16px">
      <span id="apercu">${avatar(v)}</span>
      <label class="btn btn-tonal btn-petit">Ajouter une photo<input type="file" name="photo" accept="image/*" hidden></label>
      <span class="muted">Facultatif</span>
    </div>
    <div class="champs champs-2">
      <label class="champ"><span class="obligatoire">Prénom</span><input name="prenom" value="${esc(v.prenom)}" maxlength="60" required autocomplete="off"></label>
      <label class="champ"><span class="obligatoire">Nom</span><input name="nom" value="${esc(v.nom)}" maxlength="60" required autocomplete="off"></label>
    </div>
    <fieldset style="border:0;padding:0;margin:0"><legend class="champ obligatoire" style="font-size:13px;font-weight:600;color:var(--texte-2);margin-bottom:4px">Anniversaire (jour et mois, sans l’année)</legend>
      <div class="champs champs-2">
        <select name="jour" required aria-label="Jour"><option value="">Jour</option>${Array.from({ length: 31 }, (_, i) => `<option ${v.naissance_jour == i + 1 ? 'selected' : ''}>${i + 1}</option>`).join('')}</select>
        <select name="mois" required aria-label="Mois"><option value="">Mois</option>${MOIS.map((x, i) => `<option value="${i + 1}" ${v.naissance_mois == i + 1 ? 'selected' : ''}>${x}</option>`).join('')}</select>
      </div></fieldset>
    <label class="champ">Profession (facultatif)<input name="profession" value="${esc(v.profession)}" maxlength="80"></label>
    <div class="champs champs-2">
      <label class="champ">WhatsApp<input name="whatsapp" type="tel" value="${esc(v.whatsapp)}" placeholder="06 12 34 56 78"></label>
      <label class="champ">E-mail<input name="email" type="email" value="${esc(v.email)}"></label>
    </div>
    <label class="case"><input type="checkbox" name="consent" ${v.consent_anniversaire ? 'checked' : ''}> Le membre accepte que son anniversaire soit affiché aux autres membres</label>
    ${m ? `<label class="case"><input type="checkbox" name="actif" ${v.actif ? 'checked' : ''}> Membre actif</label>` : ''}
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer</button></div>
  </form>`, (root) => {
    const f = $('#f-membre', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.photo.addEventListener('change', async () => {
      const file = f.photo.files[0]; if (!file) return;
      try {
        photoBlob = await compresserImage(file);
        $('#apercu', root).innerHTML = `<span class="avatar"><img src="${URL.createObjectURL(photoBlob)}" alt=""></span>`;
      } catch { toast('Image illisible, choisissez une photo JPEG ou PNG'); }
    });
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const j = Number(f.jour.value), mo = Number(f.mois.value);
      if (j > new Date(2024, mo, 0).getDate()) return toast(`Le ${j} ${MOIS[mo - 1]} n’existe pas`);
      const wa = f.whatsapp.value.trim();
      if (wa && !numeroWa(wa)) return toast('Numéro WhatsApp invalide : 06… ou +33…');
      const donnees = {
        prenom: f.prenom.value.trim(), nom: f.nom.value.trim(), naissance_jour: j, naissance_mois: mo,
        profession: f.profession.value.trim() || null, whatsapp: wa || null, email: f.email.value.trim() || null,
        consent_anniversaire: f.consent.checked, ...(m ? { actif: f.actif.checked } : {}),
      };
      const btn = f.querySelector('button:not([type])'); btn.disabled = true;
      try {
        // Doublon probable : même prénom, nom et anniversaire
        if (!m && S.membres.some((x) => sansAccents(nomComplet(x)) === sansAccents(`${donnees.prenom} ${donnees.nom}`) && x.naissance_jour === j && x.naissance_mois === mo)) {
          btn.disabled = false; return toast('Ce membre existe déjà');
        }
        const ligne = m ? (await q(sb.from('members').update(donnees).eq('id', m.id).select()))[0]
                        : (await q(sb.from('members').insert({ ...donnees, date_adhesion: aujourdhui() }).select()))[0];
        if (photoBlob) {
          const chemin = `${ligne.id}.jpg`;
          await q(sb.storage.from('photos').upload(chemin, photoBlob, { upsert: true, contentType: 'image/jpeg' }));
          await q(sb.from('members').update({ photo_path: chemin }).eq('id', ligne.id));
          delete S.photos[chemin];
        }
        S.membres = await q(sb.from('members').select('*').order('nom'));
        await chargerPhotos(S.membres);
        fermerFeuille(); toast(m ? 'Fiche modifiée' : 'Membre ajouté'); pageMembres();
      } catch (err) { btn.disabled = false; erreur(err); }
    });
  });
}

// Import CSV ou Excel : chaque ligne doit avoir prénom, nom, jour et mois valides
function parserCsv(texte) {
  const premiere = texte.split(/\r?\n/)[0] || '';
  const sep = (premiere.match(/;/g) || []).length >= (premiere.match(/,/g) || []).length ? ';' : ',';
  const lignes = []; let ligne = [], cell = '', guill = false;
  for (let i = 0; i < texte.length; i++) {
    const c = texte[i];
    if (guill) { if (c === '"' && texte[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') guill = false; else cell += c; }
    else if (c === '"') guill = true;
    else if (c === sep) { ligne.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && texte[i + 1] === '\n') i++; ligne.push(cell); lignes.push(ligne); ligne = []; cell = ''; }
    else cell += c;
  }
  if (cell || ligne.length) { ligne.push(cell); lignes.push(ligne); }
  return lignes.filter((l) => l.some((x) => String(x).trim()));
}

function analyserLignes(lignes) {
  const entetes = lignes[0].map(sansAccents);
  const col = (...noms) => entetes.findIndex((h) => noms.some((n) => h === n || h.startsWith(n)));
  const idx = {
    prenom: col('prenom'), nom: col('nom'), jour: col('jour'), mois: col('mois'), date: col('anniversaire', 'date'),
    profession: col('profession', 'metier'), whatsapp: col('whatsapp', 'telephone', 'tel'), email: col('email', 'e-mail', 'mail'), consent: col('accord', 'consent'),
  };
  if (idx.prenom < 0 || idx.nom < 0 || ((idx.jour < 0 || idx.mois < 0) && idx.date < 0)) {
    throw new Error('Colonnes attendues : prénom, nom, jour, mois (ou anniversaire au format jj/mm)');
  }
  const moisTexte = (s) => { const n = Number(s); if (n) return n; const i = MOIS.findIndex((x) => sansAccents(x) === sansAccents(s)); return i + 1; };
  return lignes.slice(1).map((l, i) => {
    const g = (k) => (idx[k] >= 0 ? String(l[idx[k]] ?? '').trim() : '');
    let jour = Number(g('jour')), mois = moisTexte(g('mois'));
    const dateTexte = g('date') || (/[\/\-.]/.test(g('jour')) ? g('jour') : '');
    if ((!jour || !mois) && dateTexte) { const p = dateTexte.split(/[\/\-. ]/); jour = Number(p[0]); mois = moisTexte(p[1]); }
    const r = {
      ligne: i + 2, prenom: g('prenom'), nom: g('nom'), naissance_jour: jour, naissance_mois: mois,
      profession: g('profession') || null, whatsapp: g('whatsapp') || null, email: g('email') || null,
      consent_anniversaire: /^(oui|o|yes|1|x|vrai)$/i.test(g('consent')), erreurs: [],
    };
    if (!r.prenom) r.erreurs.push('prénom manquant');
    if (!r.nom) r.erreurs.push('nom manquant');
    if (!(mois >= 1 && mois <= 12)) r.erreurs.push('mois invalide');
    else if (!(jour >= 1 && jour <= new Date(2024, mois, 0).getDate())) r.erreurs.push('jour invalide');
    if (r.whatsapp && !numeroWa(r.whatsapp)) r.erreurs.push('WhatsApp invalide');
    if (S.membres.some((x) => sansAccents(nomComplet(x)) === sansAccents(`${r.prenom} ${r.nom}`))) r.erreurs.push('déjà enregistré');
    return r;
  });
}

function feuilleImport() {
  ouvrirFeuille(`<h2>Importer des membres</h2>
    <p class="muted">Fichier CSV ou Excel avec une ligne d’en-têtes : <b>prénom, nom, jour, mois</b> (obligatoires), profession, whatsapp, email, accord (oui ou non). Les lignes incomplètes sont refusées.</p>
    <div class="actions" style="justify-content:flex-start">
      <label class="btn btn-tonal">Choisir le fichier<input type="file" id="fichier" accept=".csv,.xlsx,.xls,text/csv" hidden></label>
      <button class="btn-texte" id="b-modele">Télécharger le modèle</button>
    </div>
    <div id="resultat"></div>`, (root) => {
    $('#b-modele', root).addEventListener('click', () => telechargerCsv('modele-membres.csv',
      ['prénom', 'nom', 'jour', 'mois', 'profession', 'whatsapp', 'email', 'accord'], [['Marie', 'Exemple', 14, 3, 'Infirmière', '06 12 34 56 78', '', 'oui']]));
    $('#fichier', root).addEventListener('change', async (e) => {
      const file = e.target.files[0]; if (!file) return;
      try {
        let lignes;
        if (/\.xlsx?$/i.test(file.name)) {
          const XLSX = await import('https://cdn.jsdelivr.net/npm/xlsx@0.18.5/+esm');
          const wb = XLSX.read(await file.arrayBuffer());
          lignes = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, raw: false }).filter((l) => l.some((x) => String(x ?? '').trim()));
        } else {
          let txt = await file.text();
          if (txt.includes('\ufffd')) txt = new TextDecoder('windows-1252').decode(await file.arrayBuffer()); // CSV Excel ancien
          lignes = parserCsv(txt.replace(/^﻿/, ''));
        }
        const res = analyserLignes(lignes);
        const ok = res.filter((r) => !r.erreurs.length), ko = res.filter((r) => r.erreurs.length);
        $('#resultat', root).innerHTML = `
          <p><b>${ok.length}</b> ligne${ok.length > 1 ? 's' : ''} prête${ok.length > 1 ? 's' : ''}, <b>${ko.length}</b> refusée${ko.length > 1 ? 's' : ''}.</p>
          ${ko.length ? `<div class="alerte" style="max-height:180px;overflow:auto">${ko.map((r) => `Ligne ${r.ligne} (${esc(r.prenom)} ${esc(r.nom)}) : ${r.erreurs.join(', ')}`).join('<br>')}</div>` : ''}
          <div class="actions"><button class="btn-texte" id="b-fermer">Annuler</button><button class="btn-primaire" id="b-importer" ${ok.length ? '' : 'disabled'}>Importer ${ok.length} membre${ok.length > 1 ? 's' : ''}</button></div>`;
        $('#b-fermer', root).addEventListener('click', fermerFeuille);
        $('#b-importer', root).addEventListener('click', async (ev) => {
          ev.target.disabled = true;
          try {
            await q(sb.from('members').insert(ok.map(({ ligne, erreurs, ...r }) => ({ ...r, date_adhesion: aujourdhui() }))));
            S.membres = await q(sb.from('members').select('*').order('nom'));
            fermerFeuille(); toast(`${ok.length} membres importés`); pageMembres();
          } catch (err) { ev.target.disabled = false; erreur(err); }
        });
      } catch (err) { $('#resultat', root).innerHTML = `<div class="alerte">${esc(err.message)}</div>`; }
    });
  });
}

// ---------- Cotisations ----------
// ---------- Cotisations (par période) et participations aux activités ----------
async function pageCotisations(moi = false) {
  if (moi || !peut('consulter_finances', 'gerer_cotisations')) return pageMaCotisation();
  const onglet = S.ongletCotis || 'cotisations';
  rendre(`<div class="page">
    <div class="page-titre"><h1>Cotisations</h1></div>
    <div class="onglets" role="tablist">${[['cotisations', 'Cotisations'], ['participations', 'Participations']].map(([k, l]) => `<button role="tab" aria-selected="${onglet === k}" data-onglet="${k}">${l}</button>`).join('')}</div>
    <div id="zone-cotis"><p class="chargement">Chargement…</p></div></div>`);
  document.querySelectorAll('[data-onglet]').forEach((b) => b.addEventListener('click', () => { S.ongletCotis = b.dataset.onglet; pageCotisations().catch(erreur); }));
  if (onglet === 'participations') return ongletParticipations($('#zone-cotis'));
  return ongletCotisations($('#zone-cotis'));
}

async function ongletCotisations(zone) {
  const [synth, periodes] = await Promise.all([
    q(sb.from('v_cotisations').select('*').eq('annee', S.annee)),
    q(sb.from('v_cotisations_periodes').select('*').eq('annee', S.annee)),
  ]);
  const parMembre = Object.fromEntries(synth.map((c) => [c.member_id, c]));
  const cases = [...new Set(periodes.map((p) => p.periode))].sort();
  const cle = (mid, p) => mid + '|' + p;
  const parCase = Object.fromEntries(periodes.map((p) => [cle(p.member_id, p.periode), p]));
  const filtre = S.filtreCotis || 'tous';
  const lignes = S.membres.filter((m) => parMembre[m.id]).map((m) => ({ m, c: parMembre[m.id] }))
    .sort((a, b) => Number(b.c.retard) - Number(a.c.retard) || a.m.nom.localeCompare(b.m.nom));
  const vues = lignes.filter(({ c }) => filtre === 'tous' || (filtre === 'retard' ? Number(c.retard) > 0 : Number(c.retard) <= 0));
  const sansCotis = S.membres.filter((m) => m.actif && !parMembre[m.id]).length;
  const nbRetard = lignes.filter(({ c }) => Number(c.retard) > 0).length;
  const montant = Number(S.settings.cotisation_montant || 0);
  const gere = peut('gerer_cotisations');
  zone.innerHTML = `
    <div class="filtres" style="margin:12px 0">
      <select id="annee" aria-label="Année" style="width:auto">${[S.annee + 1, S.annee, S.annee - 1, S.annee - 2].map((a) => `<option ${a === S.annee ? 'selected' : ''}>${a}</option>`).join('')}</select>
      <span class="muted">${PERIODICITES[pasCotis()]}, ${eur(montant)} par période</span>
      <span style="flex:1"></span>
      <button class="btn-bleu btn-petit" id="b-export-c">Exporter</button>
    </div>
    ${gere && sansCotis ? `<div class="info" style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:12px"><span style="flex:1">${sansCotis} membre${sansCotis > 1 ? 's' : ''} actif${sansCotis > 1 ? 's' : ''} sans cotisation en ${S.annee}</span><button class="btn-primaire btn-petit" id="b-generer">Générer les cotisations ${S.annee}</button></div>` : ''}
    ${lignes.length ? `<div class="kpis">
      <div class="carte"><span class="muted">Encaissé</span><b class="num recette">${eur(synth.reduce((s, c) => s + Number(c.montant_paye), 0))}</b></div>
      <div class="carte"><span class="muted">En retard</span><b class="num depense">${eur(synth.reduce((s, c) => s + Number(c.retard), 0))}</b></div>
      <div class="carte"><span class="muted">À jour</span><b class="num">${lignes.length - nbRetard} / ${lignes.length}</b></div>
    </div>
    <div class="filtres" style="margin:12px 0">${[['tous', 'Tous', lignes.length], ['retard', 'En retard', nbRetard], ['ajour', 'À jour', lignes.length - nbRetard]].map(([k, l, n]) =>
      `<button class="puce-filtre" data-filtre="${k}" aria-pressed="${filtre === k}">${l} (${n})</button>`).join('')}</div>` : ''}
    <section class="carte">
      ${vues.length ? `<div class="legende muted">${Object.entries(STATUT_PERIODE).map(([k, [, l]]) => `<span><i class="case-p p-${k}"></i>${l}</span>`).join('')}</div>
      <ul class="liste">${vues.map(({ m, c }) => `<li class="ligne-cotis">
        ${avatar(m)}
        <div class="corps"><b class="lien" data-fiche="${m.id}" role="button" tabindex="0">${esc(nomComplet(m))}</b>
          <div class="grille-periodes" style="grid-template-columns:repeat(${cases.length},1fr)">${cases.map((p) => {
            const x = parCase[cle(m.id, p)];
            return `<i class="case-p p-${x ? x.statut : 'vide'}" title="${esc(nomPeriode(p))} : ${x ? STATUT_PERIODE[x.statut][1] : 'non dû'}"><small>${pasCotis() === 1 ? MOIS[Number(p.slice(5, 7)) - 1][0].toUpperCase() : ''}</small></i>`;
          }).join('')}</div></div>
        ${Number(c.retard) > 0 ? `<span class="puce puce-ko">Retard ${eur(c.retard)}</span>` : '<span class="puce puce-ok">À jour</span>'}
        ${Number(c.avance) > 0 ? `<span class="puce puce-neutre">Avance ${eur(c.avance)}</span>` : ''}
        ${gere ? `<div class="actions-ligne"><button class="btn-tonal btn-petit" data-encaisser="${m.id}">Encaisser</button>
          ${Number(c.retard) > 0 && numeroWa(m.whatsapp) ? `<a class="btn btn-texte btn-petit" target="_blank" rel="noopener" href="${lienRelance(m, `votre cotisation ${S.org?.nom ? 'à ' + S.org.nom : ''} présente un retard de ${eur(c.retard)}`)}">Relancer</a>` : ''}</div>` : ''}
      </li>`).join('')}</ul>` : `<div class="vide">Aucune cotisation${lignes.length ? '' : ' pour ' + S.annee}</div>`}
    </section>`;
  $('#annee', zone).addEventListener('change', (e) => { S.annee = Number(e.target.value); pageCotisations().catch(erreur); });
  zone.querySelectorAll('[data-filtre]').forEach((b) => b.addEventListener('click', () => { S.filtreCotis = b.dataset.filtre; pageCotisations().catch(erreur); }));
  $('#b-generer', zone)?.addEventListener('click', async () => {
    try { const n = await q(sb.rpc('generer_cotisations', { an: S.annee })); toast(`${n} période${n > 1 ? 's' : ''} créée${n > 1 ? 's' : ''}`); pageCotisations(); } catch (e) { erreur(e); }
  });
  zone.querySelectorAll('[data-encaisser]').forEach((b) => b.addEventListener('click', () => {
    const c = parMembre[b.dataset.encaisser];
    feuilleEcriture({ sens: 'recette', rubrique: 'cotisation', member_id: b.dataset.encaisser, montant: Number(c.retard) > 0 ? Number(c.retard) : montant, apres: () => pageCotisations() });
  }));
  zone.querySelectorAll('[data-fiche]').forEach((b) => {
    const ouvrir = () => ficheCotisation(S.membres.find((m) => m.id === b.dataset.fiche));
    b.addEventListener('click', ouvrir); b.addEventListener('keydown', (e) => { if (e.key === 'Enter') ouvrir(); });
  });
  $('#b-export-c', zone).addEventListener('click', () => telechargerCsv(`cotisations-${S.annee}.csv`,
    ['Prénom', 'Nom', ...cases.map((p) => nomPeriode(p)), 'Dû', 'Réglé', 'Exigible', 'Retard', 'Réglé jusqu’à'],
    lignes.map(({ m, c }) => [m.prenom, m.nom, ...cases.map((p) => { const x = parCase[cle(m.id, p)]; return x ? STATUT_PERIODE[x.statut][1] : ''; }),
      Number(c.montant_du), Number(c.montant_paye), Number(c.exigible || 0), Number(c.retard), c.regle_jusqu_a ? nomPeriode(c.regle_jusqu_a) : ''])));
}

function lienRelance(m, objet) {
  const msg = `Bonjour ${m.prenom}, ${objet}. Vous pouvez régler en espèces auprès du trésorier ou par virement. Merci.`;
  return `https://wa.me/${numeroWa(m.whatsapp)}?text=${encodeURIComponent(msg)}`;
}

// Fiche cotisation d'un membre : périodes, montant dû modifiable (dispense), versements
async function ficheCotisation(m) {
  const [periodes, versements] = await Promise.all([
    q(sb.from('v_cotisations_periodes').select('*').eq('member_id', m.id)),
    q(sb.from('transactions').select('*').eq('member_id', m.id).order('date_op', { ascending: false })),
  ]);
  const deLAnnee = periodes.filter((p) => p.annee === S.annee).sort((a, b) => (a.periode > b.periode ? 1 : -1));
  const cotis = versements.filter((t) => t.est_cotisation);
  const autres = versements.filter((t) => t.collecte_id);
  const gere = peut('gerer_cotisations');
  ouvrirFeuille(`<div style="display:flex;gap:12px;align-items:center">${avatar(m)}<h2 style="flex:1">${esc(nomComplet(m))}</h2></div>
    <p>${retardDe(periodes) > 0 ? `<span class="puce puce-ko">Retard ${eur(retardDe(periodes))}</span>` : '<span class="puce puce-ok">À jour</span>'}</p>
    <h3>${S.annee}</h3>
    <ul class="liste">${deLAnnee.map((p) => `<li><div class="corps"><b>${esc(nomPeriode(p.periode))}</b><span>${eur(p.regle)} sur ${eur(p.montant_du)}</span></div>
      <span class="puce ${STATUT_PERIODE[p.statut][0]}">${STATUT_PERIODE[p.statut][1]}</span>
      ${gere ? `<button class="btn-texte btn-petit" data-du="${p.periode}" data-montant="${p.montant_du}">Montant dû</button>` : ''}</li>`).join('') || '<li class="muted">Aucune période</li>'}</ul>
    <h3>Versements</h3>
    <ul class="liste">${cotis.map((t) => `<li><div class="corps"><b>${dateFr(t.date_op)}</b><span>${MODES[t.mode]}</span></div>${montantSigne(t)}</li>`).join('') || '<li class="muted">Aucun versement</li>'}</ul>
    ${autres.length ? `<h3>Participations</h3><ul class="liste">${autres.map((t) => `<li><div class="corps"><b>${esc(nomCollecte(t.collecte_id))}</b><span>${dateFr(t.date_op)}</span></div>${montantSigne(t)}</li>`).join('')}</ul>` : ''}
    <div class="actions">${gere ? `<button class="btn-tonal" id="b-enc">Encaisser</button>` : ''}<button class="btn-primaire" id="b-fermer">Fermer</button></div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    $('#b-enc', root)?.addEventListener('click', () => feuilleEcriture({ sens: 'recette', rubrique: 'cotisation', member_id: m.id, montant: retardDe(periodes) || Number(S.settings.cotisation_montant), apres: () => pageCotisations() }));
    root.querySelectorAll('[data-du]').forEach((b) => b.addEventListener('click', () => {
      const li = b.closest('li');
      li.innerHTML = `<form class="filtres" style="width:100%"><b style="flex:1">${esc(nomPeriode(b.dataset.du))}</b>
        <input name="v" type="number" step="0.01" min="0" value="${Number(b.dataset.montant).toFixed(2)}" aria-label="Montant dû" style="width:110px">
        <button class="btn-primaire btn-petit">Enregistrer</button></form>`;
      const f = $('form', li); f.v.focus();
      f.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
          await q(sb.from('cotisations').update({ montant_du: Number(f.v.value) }).eq('member_id', m.id).eq('periode', b.dataset.du));
          toast(Number(f.v.value) === 0 ? 'Période dispensée' : 'Montant dû modifié'); ficheCotisation(m); pageCotisations();
        } catch (err) { erreur(err); }
      });
    }));
  });
}

// Espace personnel : cotisation par période et participations demandées
async function pageMaCotisation() {
  const [periodes, parts] = await Promise.all([q(sb.rpc('ma_cotisation')), q(sb.rpc('mes_participations'))]);
  rendre(`<div class="page"><h1>Ma cotisation</h1>
    <section class="carte">${blocMaCotisation(periodes)}</section>
    <section class="carte"><h2>Mes participations</h2>${listeParticipations(parts)}</section>
  </div>`);
}

function blocMaCotisation(periodes) {
  if (!periodes?.length) return '<p class="muted">Aucune cotisation enregistrée</p>';
  const an = new Date().getFullYear();
  const l = periodes.filter((p) => p.annee === an).sort((a, b) => (a.periode > b.periode ? 1 : -1));
  const retard = retardDe(periodes);
  const regles = periodes.filter((p) => ['regle', 'dispense'].includes(p.statut)).map((p) => p.periode).sort();
  return `<p class="muted">${an}</p>
    <p>${retard > 0 ? `<span class="num" style="font-size:28px;font-weight:800;color:var(--erreur)">${eur(retard)}</span> en retard`
      : `<span class="num" style="font-size:28px;font-weight:800">À jour</span>${regles.length ? ` jusqu’à ${esc(nomPeriode(regles.at(-1)))}` : ''}`}</p>
    ${l.length ? `<div class="grille-periodes" style="grid-template-columns:repeat(${l.length},1fr)">${l.map((p) => `<i class="case-p p-${p.statut}" title="${esc(nomPeriode(p.periode))} : ${STATUT_PERIODE[p.statut][1]}"><small>${pasCotis() === 1 ? MOIS[Number(p.periode.slice(5, 7)) - 1][0].toUpperCase() : ''}</small></i>`).join('')}</div>` : ''}`;
}

function listeParticipations(parts) {
  if (!parts.length) return '<p class="muted">Aucune participation demandée</p>';
  return `<ul class="liste">${parts.map((p) => {
    const att = Number(p.montant_attendu || 0), donne = Number(p.donne);
    const puce = att ? (donne >= att ? '<span class="puce puce-ok">Réglé</span>' : donne > 0 ? `<span class="puce puce-partiel">Reste ${eur(att - donne)}</span>` : `<span class="puce puce-ko">${eur(att)} attendus</span>`)
      : donne > 0 ? '<span class="puce puce-ok">Merci</span>' : '<span class="puce puce-neutre">Libre</span>';
    return `<li><div class="corps"><b>${esc(p.nom)}</b><span>Donné&nbsp;: ${eur(donne)}${p.date_limite ? ' · avant le ' + dateFr(p.date_limite) : ''}</span></div>${puce}</li>`;
  }).join('')}</ul>`;
}

// Liste des collectes (participations demandées aux membres)
async function ongletParticipations(zone) {
  const coll = await q(sb.from('v_collectes').select('*').order('created_at', { ascending: false }));
  const gere = peut('gerer_activites', 'gerer_cotisations');
  const vise = (c) => Number(c.objectif || 0) || Number(c.montant_attendu || 0) * Number(c.nb_concernes || 0);
  zone.innerHTML = `
    <div class="filtres" style="margin:12px 0"><span style="flex:1"></span>${gere ? '<button class="btn-primaire btn-petit" id="b-collecte">Nouvelle collecte</button>' : ''}</div>
    ${coll.length ? `<div class="grille grille-2">${coll.map((c) => {
      const v = vise(c), pct = v ? Math.min(100, Math.round(100 * Number(c.total_recu) / v)) : null;
      return `<article class="carte cliquable-carte" data-collecte="${c.id}" tabindex="0" role="button">
        <div style="display:flex;gap:8px;align-items:flex-start"><h2 style="flex:1">${esc(c.nom)}</h2>${c.cloturee ? '<span class="puce puce-neutre">Clôturée</span>' : ''}</div>
        <span class="muted">${c.project_id ? esc(nomProjet(c.project_id)) + ' · ' : ''}${c.montant_attendu ? eur(c.montant_attendu) + ' par personne' : 'Montant libre'}${c.date_limite ? ' · avant le ' + dateFr(c.date_limite) : ''}</span>
        <p><span class="num" style="font-size:24px;font-weight:800">${eur(c.total_recu)}</span>${v ? ` sur ${eur(v)}` : ''}</p>
        ${pct != null ? `<div class="barre"><span style="width:${pct}%"></span></div>` : ''}
        <span class="muted">${c.nb_contributeurs} contributeur${c.nb_contributeurs > 1 ? 's' : ''} · ${c.nb_concernes} membre${c.nb_concernes > 1 ? 's' : ''} concerné${c.nb_concernes > 1 ? 's' : ''}</span>
      </article>`;
    }).join('')}</div>` : `<div class="carte vide">Aucune collecte${gere ? '<button class="btn-primaire" id="b-collecte-vide">Nouvelle collecte</button>' : ''}</div>`}`;
  ['#b-collecte', '#b-collecte-vide'].forEach((s) => $(s, zone)?.addEventListener('click', () => feuilleCollecte(null, () => pageCotisations())));
  zone.querySelectorAll('[data-collecte]').forEach((a) => {
    const ouvrir = () => detailCollecte(coll.find((c) => c.id === a.dataset.collecte));
    a.addEventListener('click', ouvrir); a.addEventListener('keydown', (e) => { if (e.key === 'Enter') ouvrir(); });
  });
}

// Qui a donné quoi pour une collecte
async function detailCollecte(c) {
  S.collectes = await q(sb.from('collectes').select('*').order('created_at', { ascending: false }));
  const [txs, choisis] = await Promise.all([
    q(sb.from('transactions').select('*').eq('collecte_id', c.id)),
    c.tous_membres ? Promise.resolve([]) : q(sb.from('collecte_membres').select('*').eq('collecte_id', c.id)),
  ]);
  const concernes = c.tous_membres ? S.membres.filter((m) => m.actif) : S.membres.filter((m) => choisis.some((x) => x.member_id === m.id));
  const donne = (mid) => txs.filter((t) => t.member_id === mid).reduce((s, t) => s + Number(t.montant), 0);
  const att = Number(c.montant_attendu || 0);
  const lignes = concernes.map((m) => ({ m, v: donne(m.id) })).sort((a, b) => a.v - b.v || a.m.nom.localeCompare(b.m.nom));
  const autres = txs.filter((t) => !concernes.some((m) => m.id === t.member_id));
  const total = txs.reduce((s, t) => s + Number(t.montant), 0);
  const gere = peut('gerer_cotisations');
  let filtre = 'tous';
  const rendu = () => `<div style="display:flex;gap:8px;align-items:flex-start"><h2 style="flex:1">${esc(c.nom)}</h2>${c.cloturee ? '<span class="puce puce-neutre">Clôturée</span>' : ''}</div>
    <p class="muted">${c.project_id ? esc(nomProjet(c.project_id)) + ' · ' : ''}${att ? eur(att) + ' par personne' : 'Montant libre'}${c.date_limite ? ' · avant le ' + dateFr(c.date_limite) : ''}</p>
    <div class="kpis"><div class="carte" style="background:var(--fond)"><span class="muted">Reçu</span><b class="num recette">${eur(total)}</b></div>
      <div class="carte" style="background:var(--fond)"><span class="muted">Ont donné</span><b class="num">${lignes.filter((x) => x.v > 0).length} / ${lignes.length}</b></div>
      <div class="carte" style="background:var(--fond)"><span class="muted">${att ? 'Reste attendu' : 'Objectif'}</span><b class="num">${att ? eur(lignes.reduce((s, x) => s + Math.max(0, att - x.v), 0)) : c.objectif ? eur(c.objectif) : '–'}</b></div></div>
    <div class="filtres">${[['tous', 'Tous'], ['donne', 'Ont donné'], ['pas', 'N’ont pas donné']].map(([k, l]) => `<button class="puce-filtre" data-f="${k}" aria-pressed="${filtre === k}">${l}</button>`).join('')}</div>
    <ul class="liste">${lignes.filter((x) => filtre === 'tous' || (filtre === 'donne' ? x.v > 0 : x.v <= 0)).map(({ m, v }) => `<li>${avatar(m)}<div class="corps"><b>${esc(nomComplet(m))}</b><span>${eur(v)}${att ? ' sur ' + eur(att) : ''}</span></div>
      ${att ? (v >= att ? '<span class="puce puce-ok">Réglé</span>' : v > 0 ? '<span class="puce puce-partiel">Partiel</span>' : '<span class="puce puce-ko">À régler</span>') : v > 0 ? '<span class="puce puce-ok">Donné</span>' : ''}
      ${gere && !c.cloturee ? `<div class="actions-ligne"><button class="btn-tonal btn-petit" data-enc="${m.id}">Encaisser</button>
        ${(!att || v < att) && numeroWa(m.whatsapp) ? `<a class="btn btn-texte btn-petit" target="_blank" rel="noopener" href="${lienRelance(m, `pour « ${c.nom} », la participation demandée est de ${att ? eur(att - v) : 'votre choix'}`)}">Relancer</a>` : ''}</div>` : ''}</li>`).join('') || '<li class="muted">Personne</li>'}</ul>
    ${autres.length ? `<h3>Autres contributions</h3><ul class="liste">${autres.map((t) => `<li><div class="corps"><b>${esc(nomTiers(t) || t.libelle)}</b><span>${dateFr(t.date_op)}</span></div>${montantSigne(t)}</li>`).join('')}</ul>` : ''}
    <div class="actions">
      <button class="btn-bleu" id="b-exp">Exporter</button>
      ${peut('gerer_activites', 'gerer_cotisations') ? `<button class="btn-texte" id="b-modif">Modifier</button><button class="btn-texte" id="b-clot">${c.cloturee ? 'Rouvrir' : 'Clôturer'}</button>` : ''}
      ${gere && !c.cloturee ? '<button class="btn-tonal" id="b-enc-autre">Autre encaissement</button>' : ''}
      <button class="btn-primaire" id="b-fermer">Fermer</button></div>`;
  const brancher = (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    root.querySelectorAll('[data-f]').forEach((b) => b.addEventListener('click', () => { filtre = b.dataset.f; root.innerHTML = rendu(); brancher(root); }));
    const apres = () => { pageCotisations().catch(erreur); };
    root.querySelectorAll('[data-enc]').forEach((b) => b.addEventListener('click', () => {
      const v = donne(b.dataset.enc);
      feuilleEcriture({ sens: 'recette', rubrique: c.id, member_id: b.dataset.enc, montant: att ? Math.max(att - v, 0) || att : '', apres });
    }));
    $('#b-enc-autre', root)?.addEventListener('click', () => feuilleEcriture({ sens: 'recette', rubrique: c.id, apres }));
    $('#b-modif', root)?.addEventListener('click', () => feuilleCollecte(S.collectes.find((x) => x.id === c.id), apres, choisis.map((x) => x.member_id)));
    $('#b-clot', root)?.addEventListener('click', async () => {
      try { await q(sb.from('collectes').update({ cloturee: !c.cloturee }).eq('id', c.id)); fermerFeuille(); toast(c.cloturee ? 'Collecte rouverte' : 'Collecte clôturée'); apres(); } catch (e) { erreur(e); }
    });
    $('#b-exp', root).addEventListener('click', () => telechargerCsv(`participations-${sansAccents(c.nom).replace(/[^a-z0-9]+/g, '-')}.csv`, ['Prénom', 'Nom', 'Donné', 'Attendu', 'Statut'],
      [...lignes.map(({ m, v }) => [m.prenom, m.nom, v, att || '', att ? (v >= att ? 'Réglé' : v > 0 ? 'Partiel' : 'À régler') : v > 0 ? 'Donné' : '']),
        ...autres.map((t) => ['', nomTiers(t) || t.libelle, Number(t.montant), '', 'Autre contribution'])]));
  };
  ouvrirFeuille(rendu(), brancher);
}

// Création ou modification d'une collecte ; preProjet : activité à laquelle la rattacher
function feuilleCollecte(c, apres, choisis = [], preProjet = '') {
  const v = c || { nom: '', project_id: preProjet, montant_attendu: '', objectif: '', date_limite: '', tous_membres: true };
  const actifs = S.membres.filter((m) => m.actif);
  ouvrirFeuille(`<form id="f-col" class="champs"><h2>${c ? 'Modifier la collecte' : 'Nouvelle collecte'}</h2>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" maxlength="80" required value="${esc(v.nom)}" placeholder="Participation à la sortie"></label>
    <div class="champs champs-2">
      <label class="champ">Activité<select name="projet"><option value="">Aucune</option>${optionsProjets(v.project_id || '')}</select></label>
      <label class="champ">Montant par personne (€)<input name="attendu" type="number" step="0.01" min="0.01" value="${esc(v.montant_attendu ?? '')}" placeholder="Libre"></label>
      <label class="champ">Objectif total (€)<input name="objectif" type="number" step="0.01" min="0.01" value="${esc(v.objectif ?? '')}"></label>
      <label class="champ">Date limite<input name="limite" type="date" value="${esc(v.date_limite || '')}"></label>
    </div>
    <div class="groupe" role="group" aria-label="Membres concernés">
      <button type="button" data-tous="1" aria-pressed="${v.tous_membres}">Tous les membres</button>
      <button type="button" data-tous="0" aria-pressed="${!v.tous_membres}">Choisir</button></div>
    <div id="choix-membres" class="liste-cases" ${v.tous_membres ? 'hidden' : ''}>${actifs.map((m) => `<label class="case"><input type="checkbox" name="m" value="${m.id}" ${choisis.includes(m.id) ? 'checked' : ''}> ${esc(nomComplet(m))}</label>`).join('')}</div>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer</button></div></form>`, (root) => {
    const f = $('#f-col', root);
    let tous = v.tous_membres;
    root.querySelectorAll('[data-tous]').forEach((b) => b.addEventListener('click', () => {
      tous = b.dataset.tous === '1';
      root.querySelectorAll('[data-tous]').forEach((x) => x.setAttribute('aria-pressed', x === b));
      $('#choix-membres', root).hidden = tous;
    }));
    f.projet.addEventListener('change', () => { if (!f.nom.value && f.projet.value) f.nom.value = `Participation : ${nomProjet(f.projet.value)}`; });
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const ids = [...f.querySelectorAll('input[name=m]:checked')].map((x) => x.value);
      if (!tous && !ids.length) return toast('Cochez au moins un membre, ou choisissez « Tous les membres »');
      const d = { nom: f.nom.value.trim(), project_id: f.projet.value || null, montant_attendu: f.attendu.value ? Number(f.attendu.value) : null,
        objectif: f.objectif.value ? Number(f.objectif.value) : null, date_limite: f.limite.value || null, tous_membres: tous };
      try {
        let id = c?.id;
        if (c) await q(sb.from('collectes').update(d).eq('id', c.id));
        else id = (await q(sb.from('collectes').insert(d).select()))[0].id;
        await q(sb.from('collecte_membres').delete().eq('collecte_id', id));
        if (!tous) await q(sb.from('collecte_membres').insert(ids.map((m) => ({ collecte_id: id, member_id: m }))));
        S.collectes = await q(sb.from('collectes').select('*').order('created_at', { ascending: false }));
        fermerFeuille(); toast(c ? 'Collecte modifiée' : 'Collecte créée'); apres?.();
      } catch (err) { erreur(err); }
    });
  });
}

// ---------- Tiers : membres et autres tiers, ce que chacun a donné ou reçu ----------
async function pageTiers() {
  const [txs, tiers] = await Promise.all([
    q(sb.from('transactions').select('*')),
    q(sb.from('tiers').select('*').order('nom')),
  ]);
  S.tiers = tiers;
  const f = (S.filtreTiers ||= { type: 'tous', texte: '', an: String(new Date().getFullYear()) });
  const dansAn = (t) => f.an === 'tout' || t.date_op.startsWith(f.an);
  const cumul = (filtre) => {
    const l = txs.filter((t) => filtre(t) && dansAn(t));
    return { rec: l.filter((t) => t.sens === 'recette').reduce((s, t) => s + Number(t.montant), 0),
      dep: l.filter((t) => t.sens === 'depense').reduce((s, t) => s + Number(t.montant), 0), n: l.length };
  };
  const lignes = [
    ...S.membres.map((m) => ({ id: m.id, genre: 'membre', nom: nomComplet(m), type: 'Membre', m, ...cumul((t) => t.member_id === m.id) })),
    ...tiers.map((t) => ({ id: t.id, genre: 'tiers', nom: t.nom, type: TYPES_TIERS[t.type], t, ...cumul((x) => x.tiers_id === t.id) })),
  ].filter((l) => (f.type === 'tous' || (f.type === 'membres' ? l.genre === 'membre' : l.genre === 'tiers'))
    && (!f.texte || sansAccents(l.nom).includes(sansAccents(f.texte))))
    .sort((a, b) => b.rec + b.dep - (a.rec + a.dep) || a.nom.localeCompare(b.nom));
  const an = new Date().getFullYear();
  rendre(`<div class="page">
    <div class="page-titre"><h1>Tiers</h1>${peut('saisir_ecritures', 'gerer_cotisations') ? '<button class="btn-primaire btn-petit" id="b-tiers">Nouveau tiers</button>' : ''}</div>
    <div class="filtres">
      ${[['tous', 'Tous'], ['membres', 'Membres'], ['autres', 'Autres tiers']].map(([k, l]) => `<button class="puce-filtre" data-type="${k}" aria-pressed="${f.type === k}">${l}</button>`).join('')}
      <select id="t-an" aria-label="Année">${[an, an - 1, an - 2].map((a) => `<option ${String(a) === f.an ? 'selected' : ''}>${a}</option>`).join('')}<option value="tout" ${f.an === 'tout' ? 'selected' : ''}>Toutes les années</option></select>
      <input type="search" id="t-texte" value="${esc(f.texte)}" placeholder="Rechercher" aria-label="Rechercher">
    </div>
    <section class="carte">${lignes.length ? `<ul class="liste">${lignes.map((l) => `<li class="cliquable" data-tiers="${l.genre}:${l.id}" tabindex="0" role="button">
      ${l.m ? avatar(l.m) : `<span class="avatar" aria-hidden="true">${esc(l.nom.slice(0, 2).toUpperCase())}</span>`}
      <div class="corps"><b>${esc(l.nom)}</b><span>${esc(l.type)}${l.n ? ` · ${l.n} opération${l.n > 1 ? 's' : ''}` : ''}</span></div>
      ${l.rec ? `<span class="num recette">+ ${eur(l.rec)}</span>` : ''}${l.dep ? `<span class="num depense">− ${eur(l.dep)}</span>` : ''}</li>`).join('')}</ul>` : '<div class="vide">Aucun tiers</div>'}</section>
  </div>`);
  const recharger = () => pageTiers().catch(erreur);
  document.querySelectorAll('[data-type]').forEach((b) => b.addEventListener('click', () => { f.type = b.dataset.type; recharger(); }));
  $('#t-an').addEventListener('change', (e) => { f.an = e.target.value; recharger(); });
  let minuteur; $('#t-texte').addEventListener('input', (e) => { clearTimeout(minuteur); minuteur = setTimeout(() => { f.texte = e.target.value; recharger().then?.(() => { const i = $('#t-texte'); i.focus(); i.setSelectionRange(i.value.length, i.value.length); }); }, 350); });
  $('#b-tiers')?.addEventListener('click', () => feuilleTiers(null, recharger));
  document.querySelectorAll('[data-tiers]').forEach((li) => {
    const ouvrir = () => {
      const [genre, id] = li.dataset.tiers.split(':');
      ficheTiers(genre, id, txs.filter((t) => (genre === 'membre' ? t.member_id === id : t.tiers_id === id)), recharger);
    };
    li.addEventListener('click', ouvrir); li.addEventListener('keydown', (e) => { if (e.key === 'Enter') ouvrir(); });
  });
}

function ficheTiers(genre, id, txs, recharger) {
  const m = genre === 'membre' ? S.membres.find((x) => x.id === id) : null;
  const t = genre === 'tiers' ? S.tiers.find((x) => x.id === id) : null;
  const tri = [...txs].sort((a, b) => (a.date_op < b.date_op ? 1 : -1));
  const parRubrique = {};
  txs.filter((x) => x.sens === 'recette').forEach((x) => { const k = nomRubrique(x) || nomCategorie(x.category_id); parRubrique[k] = (parRubrique[k] || 0) + Number(x.montant); });
  ouvrirFeuille(`<div style="display:flex;gap:12px;align-items:center">${m ? avatar(m) : ''}<h2 style="flex:1">${esc(m ? nomComplet(m) : t.nom)}</h2><span class="puce puce-neutre">${m ? 'Membre' : TYPES_TIERS[t.type]}</span></div>
    ${t && (t.telephone || t.email || t.notes) ? `<p class="muted">${[t.telephone, t.email, t.notes].filter(Boolean).map(esc).join(' · ')}</p>` : ''}
    ${Object.keys(parRubrique).length ? `<h3>Ce qu’il a donné</h3><ul class="liste">${Object.entries(parRubrique).map(([k, v]) => `<li><div class="corps"><b>${esc(k)}</b></div><span class="num recette">${eur(v)}</span></li>`).join('')}</ul>` : ''}
    <h3>Opérations</h3>
    <ul class="liste">${tri.map((x) => `<li><div class="corps"><b>${esc(x.libelle)}</b><span>${dateFr(x.date_op)}${nomRubrique(x) ? ' · ' + esc(nomRubrique(x)) : ''}</span></div>${montantSigne(x)}</li>`).join('') || '<li class="muted">Aucune opération</li>'}</ul>
    <div class="actions">
      <button class="btn-bleu" id="b-exp">Exporter</button>
      ${t && peut('saisir_ecritures', 'gerer_cotisations') ? '<button class="btn-texte" id="b-modif">Modifier</button>' : ''}
      ${m && peut('gerer_cotisations') ? '<button class="btn-texte" id="b-cotis">Cotisation</button>' : ''}
      <button class="btn-primaire" id="b-fermer">Fermer</button></div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    $('#b-modif', root)?.addEventListener('click', () => feuilleTiers(t, recharger));
    $('#b-cotis', root)?.addEventListener('click', () => ficheCotisation(m));
    $('#b-exp', root).addEventListener('click', () => telechargerCsv(`tiers-${sansAccents(m ? nomComplet(m) : t.nom).replace(/[^a-z0-9]+/g, '-')}.csv`,
      ['Date', 'Libellé', 'Rubrique', 'Catégorie', 'Montant'], tri.map((x) => [dateFr(x.date_op), x.libelle, nomRubrique(x), nomCategorie(x.category_id), signe(x)])));
  });
}

function feuilleTiers(t, apres) {
  const v = t || { nom: '', type: 'donateur', telephone: '', email: '', notes: '', actif: true };
  ouvrirFeuille(`<form id="f-tiers" class="champs"><h2>${t ? 'Modifier le tiers' : 'Nouveau tiers'}</h2>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" required maxlength="80" value="${esc(v.nom)}"></label>
    <div class="champs champs-2">
      <label class="champ">Type<select name="type">${Object.entries(TYPES_TIERS).map(([k, l]) => `<option value="${k}" ${v.type === k ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
      <label class="champ">Téléphone<input name="telephone" type="tel" value="${esc(v.telephone || '')}"></label>
      <label class="champ">E-mail<input name="email" type="email" value="${esc(v.email || '')}"></label>
      <label class="champ">Notes<input name="notes" maxlength="200" value="${esc(v.notes || '')}"></label>
    </div>
    ${t ? `<label class="case"><input type="checkbox" name="actif" ${v.actif ? 'checked' : ''}> Actif</label>` : ''}
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer</button></div></form>`, (root) => {
    const f = $('#f-tiers', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const d = { nom: f.nom.value.trim(), type: f.type.value, telephone: f.telephone.value.trim() || null, email: f.email.value.trim() || null, notes: f.notes.value.trim() || null };
      if (t) d.actif = f.actif.checked;
      try {
        if (t) await q(sb.from('tiers').update(d).eq('id', t.id)); else await q(sb.from('tiers').insert(d));
        fermerFeuille(); toast(t ? 'Tiers modifié' : 'Tiers ajouté'); apres();
      } catch (err) { erreur(err); }
    });
  });
}

// ---------- Paramètres (trésorier) ----------
async function pageParametres() {
  const onglets = [['association', 'Association'], ['finances', 'Montants et comptes'], ['roles', 'Rôles et droits'], ['personnes', 'Personnes']];
  if (!S.ongletParam) S.ongletParam = 'association';
  const corps = { association: paramAssociation, finances: paramFinances, roles: paramRoles, personnes: paramPersonnes };
  rendre(`<div class="page"><h1>Paramètres</h1>
    <div class="onglets" role="tablist">${onglets.map(([k, l]) => `<button role="tab" aria-selected="${S.ongletParam === k}" data-onglet="${k}">${l}</button>`).join('')}</div>
    <div id="param-corps"></div></div>`);
  document.querySelectorAll('[data-onglet]').forEach((b) => b.addEventListener('click', () => { S.ongletParam = b.dataset.onglet; pageParametres().catch(erreur); }));
  await corps[S.ongletParam]($('#param-corps'));
}

async function paramAssociation(zone) {
  zone.innerHTML = `<form class="carte" id="f-org" style="max-width:560px">
    <div style="display:flex;align-items:center;gap:16px"><img id="apercu-logo" src="${esc(S.logoUrl)}" alt="Logo" style="width:72px;height:72px;border-radius:50%;object-fit:cover">
      <label class="btn btn-tonal btn-petit">Changer le logo<input type="file" name="logo" accept="image/*" hidden></label></div>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" value="${esc(S.org?.nom)}" required maxlength="80"></label>
    <div class="champ"><span>Photo de la bannière d’accueil</span>
      <div id="apercu-banniere" class="apercu-banniere" style="${S.banniereUrl ? `background-image:url('${esc(S.banniereUrl)}')` : ''}">${S.banniereUrl ? '' : '<span class="muted">Aucune photo. Format paysage conseillé, au moins 1600&nbsp;pixels de large.</span>'}</div>
      <div style="display:flex;gap:8px;flex-wrap:wrap"><label class="btn btn-tonal btn-petit">${S.banniereUrl ? 'Changer la photo' : 'Choisir une photo'}<input type="file" name="banniere" accept="image/*" hidden></label>
      ${S.banniereUrl ? '<button type="button" class="btn-texte btn-petit" id="b-sans-banniere">Retirer la photo</button>' : ''}</div></div>
    <button class="btn-primaire" style="align-self:flex-end">Enregistrer</button></form>`;
  let logoBlob = null, banniereBlob = null, retirerBanniere = false;
  const fo = $('#f-org', zone);
  fo.logo.addEventListener('change', async () => {
    const file = fo.logo.files[0]; if (!file) return;
    logoBlob = await compresserImage(file, 512, 0.85); $('#apercu-logo').src = URL.createObjectURL(logoBlob);
  });
  fo.banniere.addEventListener('change', async () => {
    const file = fo.banniere.files[0]; if (!file) return;
    banniereBlob = await compresserImage(file, 1600, 0.8); retirerBanniere = false;
    const ap = $('#apercu-banniere'); ap.style.backgroundImage = `url('${URL.createObjectURL(banniereBlob)}')`; ap.innerHTML = '';
  });
  $('#b-sans-banniere')?.addEventListener('click', () => {
    retirerBanniere = true; banniereBlob = null;
    const ap = $('#apercu-banniere'); ap.style.backgroundImage = ''; ap.innerHTML = '<span class="muted">La photo sera retirée à l’enregistrement.</span>';
  });
  fo.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const maj = { nom: fo.nom.value.trim() };
      if (logoBlob) {
        const chemin = `logo-${Date.now()}.jpg`;
        await q(sb.storage.from('logos').upload(chemin, logoBlob, { contentType: 'image/jpeg' }));
        maj.logo_path = chemin;
      }
      if (banniereBlob) {
        const chemin = `banniere-${Date.now()}.jpg`;
        await q(sb.storage.from('logos').upload(chemin, banniereBlob, { contentType: 'image/jpeg' }));
        maj.banniere_path = chemin;
      } else if (retirerBanniere) maj.banniere_path = null;
      await q(sb.from('organisation').update(maj).eq('id', 1));
      await chargerOrganisation(); toast('Association enregistrée'); router();
    } catch (err) { erreur(err); }
  });
}

async function paramFinances(zone) {
  const comptes = await q(sb.from('accounts').select('*').order('nom'));
  zone.innerHTML = `<div class="grille grille-2">
    <form class="carte" id="f-montants"><h2>Montants et délais</h2>
      <div class="champs champs-2"><label class="champ">Cotisation (€ par période)<input name="cotisation_montant" type="number" step="0.01" min="0" value="${esc(S.settings.cotisation_montant)}"></label>
      <label class="champ">Périodicité<select name="cotisation_periode_mois">${Object.entries(PERIODICITES).map(([k, l]) => `<option value="${k}" ${Number(k) === pasCotis() ? 'selected' : ''}>${l}</option>`).join('')}</select></label></div>
      <label class="champ">Délai du justificatif (jours après paiement)<input name="delai_justificatif_jours" type="number" min="1" max="90" value="${esc(S.settings.delai_justificatif_jours)}"></label>
      <label class="champ">Alerte budget (%)<input name="seuil_alerte_budget_pct" type="number" min="1" max="200" value="${esc(S.settings.seuil_alerte_budget_pct ?? 90)}"></label>
      <button class="btn-primaire" style="align-self:flex-end">Enregistrer</button></form>
    <form class="carte" id="f-soldes"><h2>Comptes et soldes de départ</h2>
      ${comptes.map((c) => `<div class="ligne-compte"><label class="champ" style="flex:1">${esc(c.nom)} (€)<input type="number" step="0.01" name="c-${c.id}" value="${Number(c.solde_initial || 0).toFixed(2)}"></label>
        <label class="case"><input type="checkbox" name="a-${c.id}" ${c.actif ? 'checked' : ''}> Actif</label></div>`).join('')}
      <div class="actions"><button type="button" class="btn-texte" id="b-compte">Ajouter un compte</button><button class="btn-primaire">Enregistrer</button></div></form>
    <section class="carte" style="grid-column:1/-1"><h2>Catégories</h2>
      <div class="grille grille-2">${['recette', 'depense'].map((sens) => `<div><h3>${sens === 'recette' ? 'Recettes' : 'Dépenses'}</h3>
        <div class="filtres">${S.categories.filter((c) => c.sens === sens).map((c) => `<span class="puce puce-neutre">${esc(c.nom)}</span>`).join('')}</div></div>`).join('')}</div>
      <form id="f-cat" class="filtres">
        <input name="nom" required maxlength="60" placeholder="Nouvelle catégorie" aria-label="Nom de la catégorie">
        <select name="sens" aria-label="Type"><option value="depense">Dépense</option><option value="recette">Recette</option></select>
        <button class="btn-tonal btn-petit">Ajouter</button></form></section></div>`;
  const fm = $('#f-montants', zone);
  fm.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      for (const cle of ['cotisation_montant', 'cotisation_periode_mois', 'delai_justificatif_jours', 'seuil_alerte_budget_pct']) {
        await q(sb.from('settings').update({ valeur: Number(fm[cle].value) }).eq('cle', cle));
        S.settings[cle] = Number(fm[cle].value);
      }
      toast('Paramètres enregistrés');
    } catch (err) { erreur(err); }
  });
  const fs = $('#f-soldes', zone);
  fs.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      for (const c of comptes) {
        const v = Number(fs[`c-${c.id}`].value), a = fs[`a-${c.id}`].checked;
        if (v !== Number(c.solde_initial) || a !== c.actif) await q(sb.from('accounts').update({ solde_initial: v, actif: a }).eq('id', c.id));
      }
      S.comptes = await q(sb.from('accounts').select('*').eq('actif', true).order('nom'));
      toast('Comptes enregistrés');
    } catch (err) { erreur(err); }
  });
  $('#b-compte', zone).addEventListener('click', () => ouvrirFeuille(`<form id="f-cpt" class="champs"><h2>Nouveau compte</h2>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" required maxlength="60" placeholder="Livret A"></label>
    <div class="champs champs-2"><label class="champ">Type<select name="type"><option value="banque">Banque</option><option value="caisse">Caisse</option></select></label>
      <label class="champ">Solde de départ (€)<input name="solde" type="number" step="0.01" value="0"></label></div>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Ajouter</button></div></form>`, (root) => {
    const f = $('#f-cpt', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await q(sb.from('accounts').insert({ nom: f.nom.value.trim(), type: f.type.value, solde_initial: Number(f.solde.value || 0) }));
        S.comptes = await q(sb.from('accounts').select('*').eq('actif', true).order('nom'));
        fermerFeuille(); toast('Compte ajouté'); pageParametres();
      } catch (err) { erreur(err); }
    });
  }));
  const fc = $('#f-cat', zone);
  fc.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await q(sb.from('categories').insert({ nom: fc.nom.value.trim(), sens: fc.sens.value }));
      S.categories = await q(sb.from('categories').select('*').order('nom'));
      toast('Catégorie ajoutée'); pageParametres();
    } catch (err) { erreur(err); }
  });
}

// Matrice rôles × droits : chaque case active ou retire un droit pour tout le rôle
async function paramRoles(zone) {
  const [roles, liens, profils] = await Promise.all([
    q(sb.from('roles').select('*').order('nom')), q(sb.from('role_permissions').select('*')), q(sb.from('profiles').select('*')),
  ]);
  S.roles = roles;
  const a = (r, p) => liens.some((l) => l.role === r && l.permission === p);
  const groupes = [...new Set(S.permissions.map((p) => p.groupe))];
  const conflit = (r) => a(r.code, 'valider_depenses') && a(r.code, 'payer_depenses');
  zone.innerHTML = `<section class="carte">
    <div class="page-titre"><h2 style="flex:1">Rôles et droits</h2><button class="btn-primaire btn-petit" id="b-role">Nouveau rôle</button></div>
    <div class="tableau-wrap"><table class="matrice">
      <thead><tr><th>Droit</th>${roles.map((r) => `<th class="centre"><button class="btn-texte btn-petit" data-renommer="${esc(r.code)}">${esc(r.nom)}</button><br><span class="muted">${profils.filter((p) => p.role === r.code).length} pers.</span></th>`).join('')}</tr></thead>
      <tbody>${groupes.map((g) => `<tr class="groupe-ligne"><td colspan="${roles.length + 1}">${esc(g)}</td></tr>` +
        S.permissions.filter((p) => p.groupe === g).map((p) => `<tr><td>${esc(p.libelle)}</td>${roles.map((r) => `<td class="centre">
          <input type="checkbox" data-r="${esc(r.code)}" data-p="${p.code}" ${a(r.code, p.code) ? 'checked' : ''} aria-label="${esc(r.nom)} : ${esc(p.libelle)}"></td>`).join('')}</tr>`).join('')).join('')}
      </tbody></table></div>
    ${roles.filter(conflit).map((r) => `<p class="alerte">${esc(r.nom)} peut valider et payer. La même personne ne pourra pas payer une dépense qu’elle a validée.</p>`).join('')}
  </section>`;
  zone.querySelectorAll('[data-r]').forEach((c) => c.addEventListener('change', async () => {
    try {
      if (c.checked) await q(sb.from('role_permissions').insert({ role: c.dataset.r, permission: c.dataset.p }));
      else await q(sb.from('role_permissions').delete().eq('role', c.dataset.r).eq('permission', c.dataset.p));
      if (S.profil.role === c.dataset.r) S.droits = new Set(await q(sb.rpc('mes_droits')));
      toast('Droit modifié'); paramRoles(zone);
    } catch (e) { c.checked = !c.checked; erreur(e); }
  }));
  $('#b-role', zone).addEventListener('click', () => feuilleRole(null, profils, () => paramRoles(zone)));
  zone.querySelectorAll('[data-renommer]').forEach((b) => b.addEventListener('click', () => feuilleRole(roles.find((r) => r.code === b.dataset.renommer), profils, () => paramRoles(zone))));
}

function feuilleRole(r, profils, apres) {
  const utilise = r && profils.some((p) => p.role === r.code);
  ouvrirFeuille(`<form id="f-role" class="champs"><h2>${r ? 'Rôle' : 'Nouveau rôle'}</h2>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" value="${esc(r?.nom || '')}" required maxlength="40" placeholder="Secrétaire"></label>
    ${!r ? `<label class="champ">Copier les droits de<select name="modele"><option value="">Aucun</option>${S.roles.map((x) => `<option value="${esc(x.code)}">${esc(x.nom)}</option>`).join('')}</select></label>` : ''}
    <div class="actions" style="justify-content:space-between">
      ${r && !r.systeme && !utilise ? '<button type="button" class="btn-danger" id="b-suppr">Supprimer</button>' : '<span></span>'}
      <span><button type="button" class="btn-texte" id="b-annuler">Annuler</button> <button class="btn-primaire">Enregistrer</button></span></div></form>`, (root) => {
    const f = $('#f-role', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    $('#b-suppr', root)?.addEventListener('click', async () => {
      try { await q(sb.from('roles').delete().eq('code', r.code)); fermerFeuille(); toast('Rôle supprimé'); apres(); } catch (e) { erreur(e); }
    });
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const nom = f.nom.value.trim();
      try {
        if (r) await q(sb.from('roles').update({ nom }).eq('code', r.code));
        else {
          const code = sansAccents(nom).replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') || `role_${Date.now()}`;
          await q(sb.from('roles').insert({ code, nom }));
          if (f.modele.value) {
            const droits = await q(sb.from('role_permissions').select('*').eq('role', f.modele.value));
            if (droits.length) await q(sb.from('role_permissions').insert(droits.map((d) => ({ role: code, permission: d.permission }))));
          }
        }
        S.roles = await q(sb.from('roles').select('*').order('nom'));
        fermerFeuille(); toast(r ? 'Rôle renommé' : 'Rôle créé'); apres();
      } catch (err) { erreur(err); }
    });
  });
}

async function paramPersonnes(zone) {
  const [profils, invitations] = await Promise.all([
    q(sb.from('profiles').select('*').order('nom')), q(sb.from('invitations').select('*').order('created_at', { ascending: false })),
  ]);
  const optionsRoles = (choisi) => S.roles.map((r) => `<option value="${esc(r.code)}" ${r.code === choisi ? 'selected' : ''}>${esc(r.nom)}</option>`).join('');
  zone.innerHTML = `<section class="carte">
    <div class="page-titre"><h2 style="flex:1">Personnes</h2><button class="btn-primaire btn-petit" id="b-inviter">Inviter</button></div>
    <ul class="liste">
      ${profils.map((p) => `<li>
        <span class="avatar" aria-hidden="true">${esc(initiales({ prenom: p.nom.split(' ')[0], nom: p.nom.split(' ')[1] || '' }))}</span>
        <div class="corps"><b>${esc(p.nom)}${p.id === S.profil.id ? ' (vous)' : ''}</b><span>${p.actif ? 'Accès actif' : 'Accès désactivé'}</span></div>
        <select data-role="${p.id}" style="width:auto;min-height:40px" aria-label="Rôle de ${esc(p.nom)}">${optionsRoles(p.role)}</select>
        <label class="case"><input type="checkbox" data-actif="${p.id}" ${p.actif ? 'checked' : ''}> Actif</label></li>`).join('')}
      ${invitations.map((i) => `<li style="opacity:.75"><span class="avatar" aria-hidden="true">@</span>
        <div class="corps"><b>${esc(i.nom || i.email)}</b><span>${esc(i.email)} · invitation en attente</span></div>
        <span class="puce puce-neutre">${esc(nomRole(i.role))}</span>
        <button class="btn-texte btn-petit" data-retirer="${esc(i.email)}">Retirer</button></li>`).join('')}
    </ul></section>`;
  zone.querySelectorAll('[data-role]').forEach((s) => s.addEventListener('change', async () => {
    try {
      await q(sb.from('profiles').update({ role: s.value }).eq('id', s.dataset.role)); toast('Rôle attribué');
      if (s.dataset.role === S.profil.id) { S.profil.role = s.value; S.droits = new Set(await q(sb.rpc('mes_droits'))); router(); }
    } catch (err) { erreur(err); paramPersonnes(zone); }
  }));
  zone.querySelectorAll('[data-actif]').forEach((c) => c.addEventListener('change', async () => {
    try { await q(sb.from('profiles').update({ actif: c.checked }).eq('id', c.dataset.actif)); toast(c.checked ? 'Accès réactivé' : 'Accès désactivé'); }
    catch (err) { erreur(err); paramPersonnes(zone); }
  }));
  zone.querySelectorAll('[data-retirer]').forEach((b) => b.addEventListener('click', async () => {
    try { await q(sb.from('invitations').delete().eq('email', b.dataset.retirer)); toast('Invitation retirée'); paramPersonnes(zone); } catch (err) { erreur(err); }
  }));
  $('#b-inviter', zone).addEventListener('click', () => ouvrirFeuille(`<form id="f-invit" class="champs"><h2>Inviter</h2>
    <label class="champ"><span class="obligatoire">E-mail</span><input name="email" type="email" required></label>
    <label class="champ"><span class="obligatoire">Nom affiché</span><input name="nom" required maxlength="60"></label>
    <div class="champs champs-2"><label class="champ">Rôle<select name="role">${optionsRoles('adherent')}</select></label>
      <label class="champ">Fiche membre<select name="membre"><option value="">Aucune</option>${S.membres.filter((m) => m.actif).map((m) => `<option value="${m.id}">${esc(nomComplet(m))}</option>`).join('')}</select></label></div>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Inviter</button></div></form>`, (root) => {
    const fi = $('#f-invit', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    fi.membre.addEventListener('change', () => {
      const m = S.membres.find((x) => x.id === fi.membre.value);
      if (m) { if (!fi.nom.value) fi.nom.value = nomComplet(m); if (!fi.email.value && m.email) fi.email.value = m.email; }
    });
    fi.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await q(sb.from('invitations').insert({ email: fi.email.value.trim().toLowerCase(), nom: fi.nom.value.trim(), role: fi.role.value, member_id: fi.membre.value || null }));
        fermerFeuille(); toast('Invitation enregistrée'); paramPersonnes(zone);
      } catch (err) { erreur(err); }
    });
  }));
}

// =====================================================================
// Phase 2 et 3 : dépenses, justificatifs, budget, activités, rapprochement, rapports
// =====================================================================
const STATUTS = {
  soumise: ['puce-partiel', 'À valider'], validee: ['puce-ok', 'À payer'], refusee: ['puce-ko', 'Refusée'],
  payee: ['puce-partiel', 'Justificatif attendu'], justifiee: ['puce-ok', 'Clôturée'], annulee: ['puce-neutre', 'Annulée'], brouillon: ['puce-neutre', 'Brouillon'],
};
const optionsProjets = (choisi = '') => (S.projets || []).map((p) => `<option value="${p.id}" ${p.id === choisi ? 'selected' : ''}>${esc(p.nom)}</option>`).join('');
const nomProjet = (id) => (S.projets || []).find((p) => p.id === id)?.nom || '';
const nomCategorie = (id) => S.categories.find((c) => c.id === id)?.nom || '';
const joursDepuis = (d) => Math.floor((Date.now() - new Date(d).getTime()) / 864e5);

// ---------- Tiers, rubriques, cotisations par période ----------
const TYPES_TIERS = { donateur: 'Donateur', fournisseur: 'Fournisseur', partenaire: 'Partenaire', autre: 'Autre' };
const PERIODICITES = { 1: 'Mensuelle', 3: 'Trimestrielle', 6: 'Semestrielle', 12: 'Annuelle' };
const pad2 = (n) => String(n).padStart(2, '0');
const isoLocal = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const pasCotis = () => { const p = Number(S.settings.cotisation_periode_mois || 1); return [1, 3, 6, 12].includes(p) ? p : 1; };
function nomPeriode(p, court = false) {
  const m = Number(p.slice(5, 7)) - 1, a = p.slice(0, 4), pas = pasCotis();
  if (pas === 12) return a;
  if (pas === 1) return court ? MOIS[m].slice(0, 3) : `${MOIS[m]} ${a}`;
  return `${MOIS[m].slice(0, 3)}.–${MOIS[(m + pas - 1) % 12].slice(0, 3)}.${court ? '' : ' ' + a}`;
}
const nomMembre = (id) => nomComplet(S.membres.find((m) => m.id === id));
const nomTiers = (t) => (t.member_id ? nomMembre(t.member_id) : t.tiers_id ? ((S.tiers || []).find((x) => x.id === t.tiers_id)?.nom || '') : '');
const nomCollecte = (id) => (S.collectes || []).find((c) => c.id === id)?.nom || 'Participation';
const nomRubrique = (t) => (t.est_cotisation ? 'Cotisation' : t.collecte_id ? nomCollecte(t.collecte_id) : '');
const STATUT_PERIODE = { regle: ['puce-ok', 'Réglé'], partiel: ['puce-partiel', 'Partiel'], impaye: ['puce-ko', 'Impayé'], a_venir: ['puce-neutre', 'À venir'], dispense: ['puce-neutre', 'Dispensé'] };

// Champ « Tiers » : un membre, un tiers enregistré ou un nouveau nom (créé à l'enregistrement)
function champTiers(sens, valeur = '') {
  return `<label class="champ">Tiers<input name="tiers" list="dl-tiers" autocomplete="off" value="${esc(valeur)}" placeholder="${sens === 'recette' ? 'Membre ou donateur' : 'Fournisseur'}"></label>
    <datalist id="dl-tiers">${S.membres.filter((m) => m.actif).map((m) => `<option value="${esc(nomComplet(m))}">Membre</option>`).join('')}${(S.tiers || []).filter((t) => t.actif).map((t) => `<option value="${esc(t.nom)}">${TYPES_TIERS[t.type] || ''}</option>`).join('')}</datalist>`;
}
function trouverTiers(nom) {
  const n = sansAccents(nom);
  if (!n) return {};
  const m = S.membres.find((x) => sansAccents(nomComplet(x)) === n);
  if (m) return { member_id: m.id };
  const t = (S.tiers || []).find((x) => sansAccents(x.nom) === n);
  if (t) return { tiers_id: t.id };
  return { nouveau: String(nom).trim() };
}
async function resoudreTiers(nom, sens) {
  const r = trouverTiers(nom);
  if (!r.nouveau) return r;
  const [t] = await q(sb.from('tiers').insert({ nom: r.nouveau, type: sens === 'recette' ? 'donateur' : 'fournisseur' }).select());
  S.tiers = [...(S.tiers || []), t];
  return { tiers_id: t.id };
}

// Imputation d'un versement sur les périodes les plus anciennes non réglées
function couverture(periodes, montant) {
  let reste = Number(montant || 0), jusqua = null, partiel = null;
  for (const p of [...periodes].sort((a, b) => (a.periode > b.periode ? 1 : -1))) {
    const du = Number(p.montant_du) - Number(p.regle);
    if (du <= 0) { jusqua = p.periode; continue; }
    if (reste >= du) { reste -= du; jusqua = p.periode; } else { if (reste > 0) partiel = p.periode; reste = 0; break; }
  }
  return { jusqua, partiel, avance: Math.round(reste * 100) / 100 };
}
const retardDe = (periodes) => periodes.filter((p) => p.periode <= aujourdhui()).reduce((s, p) => s + Number(p.montant_du) - Number(p.regle), 0);

// Photo réduite à 1600 px (environ 200 à 400 Ko) ; PDF accepté jusqu'à 3 Mo
async function preparerFichier(file) {
  if (file.type === 'application/pdf') {
    if (file.size > 3 * 1024 * 1024) throw new Error('Ce PDF dépasse 3 Mo. Photographiez plutôt la page.');
    return { blob: file, ext: 'pdf', mime: 'application/pdf', ko: Math.round(file.size / 1024) };
  }
  if (!file.type.startsWith('image/')) throw new Error('Format accepté : photo ou PDF');
  const blob = await compresserImage(file, 1600, 0.75);
  return { blob, ext: 'jpg', mime: 'image/jpeg', ko: Math.round(blob.size / 1024) };
}
const nomFichier = (ext) => `${new Date().getFullYear()}/${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${ext}`;

async function deposerPiece(file, lien) {
  const f = await preparerFichier(file);
  const chemin = nomFichier(f.ext);
  await q(sb.storage.from('justificatifs').upload(chemin, f.blob, { contentType: f.mime }));
  await q(sb.from('attachments').insert({ ...lien, storage_path: chemin, mime: f.mime, taille_ko: f.ko, depose_par: S.profil.id }));
  return chemin;
}

// Aperçu dans la page (fonctionne aussi sur téléphone, où les fenêtres sont souvent bloquées)
async function ouvrirFichier(bucket, chemin) {
  try {
    const d = await q(sb.storage.from(bucket).createSignedUrl(chemin, 600));
    const image = /\.(jpe?g|png|webp)$/i.test(chemin) || /^data:image|^blob:/.test(d.signedUrl);
    ouvrirFeuille(`<h2>${bucket === 'signatures' ? 'Signature du président' : bucket === 'releves' ? 'Relevé' : 'Justificatif'}</h2>
      ${image ? `<img src="${esc(d.signedUrl)}" alt="Document joint" style="width:100%;border-radius:16px;border:1px solid var(--bord);background:#fff">` : ''}
      <div class="actions"><a class="btn btn-texte" href="${esc(d.signedUrl)}" target="_blank" rel="noopener">Ouvrir dans un onglet</a><button class="btn-tonal" id="b-fermer">Fermer</button></div>`,
      (root) => $('#b-fermer', root).addEventListener('click', fermerFeuille));
  } catch (e) { erreur(e); }
}

function choisirFichier(accept = 'image/*,application/pdf') {
  return new Promise((ok) => {
    const i = document.createElement('input'); i.type = 'file'; i.accept = accept;
    i.addEventListener('change', () => ok(i.files[0] || null)); i.click();
  });
}

function brancherPieces(rafraichir) {
  document.querySelectorAll('[data-voir]').forEach((b) => b.addEventListener('click', () => ouvrirFichier(b.dataset.bucket || 'justificatifs', b.dataset.voir)));
  document.querySelectorAll('[data-joindre]').forEach((b) => b.addEventListener('click', async () => {
    const file = await choisirFichier(); if (!file) return;
    try { b.disabled = true; await deposerPiece(file, { transaction_id: b.dataset.joindre }); toast('Justificatif joint'); rafraichir(); }
    catch (e) { b.disabled = false; erreur(e); }
  }));
}

async function sha256(texte) {
  const h = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(texte));
  return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// Zone de signature au doigt ou à la souris
function zoneSignature(canvas) {
  const ctx = canvas.getContext('2d');
  const ratio = window.devicePixelRatio || 1;
  const r = canvas.getBoundingClientRect();
  canvas.width = r.width * ratio; canvas.height = r.height * ratio; ctx.scale(ratio, ratio);
  ctx.lineWidth = 2.4; ctx.lineCap = 'round'; ctx.lineJoin = 'round'; ctx.strokeStyle = '#1C1B1A';
  let trace = false, longueur = 0, px = 0, py = 0;
  const pos = (e) => { const b = canvas.getBoundingClientRect(); return [e.clientX - b.left, e.clientY - b.top]; };
  canvas.addEventListener('pointerdown', (e) => { trace = true; [px, py] = pos(e); canvas.setPointerCapture(e.pointerId); });
  canvas.addEventListener('pointermove', (e) => {
    if (!trace) return; const [x, y] = pos(e);
    ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(x, y); ctx.stroke();
    longueur += Math.hypot(x - px, y - py); px = x; py = y;
  });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => canvas.addEventListener(ev, () => { trace = false; }));
  return {
    vide: () => longueur < 40,
    effacer: () => { ctx.clearRect(0, 0, canvas.width, canvas.height); longueur = 0; },
    image: () => new Promise((ok) => canvas.toBlob(ok, 'image/png')),
  };
}

function etapes(d) {
  const ordre = ['soumise', 'validee', 'payee', 'justifiee'];
  const noms = ['Demande', 'Validation', 'Paiement', 'Justificatif'];
  const idx = d.statut === 'refusee' ? 1 : d.statut === 'annulee' ? 0 : ordre.indexOf(d.statut);
  return `<ol class="etapes" aria-label="Avancement">${noms.map((n, i) => {
    const etat = d.statut === 'refusee' && i === 1 ? 'ko' : i <= idx && !(d.statut === 'annulee' && i > 0) ? 'fait' : i === idx + 1 && !['refusee', 'annulee'].includes(d.statut) ? 'encours' : '';
    return `<li class="${etat}"><span></span>${n}</li>`;
  }).join('')}</ol>`;
}

// ---------- Dépenses : demande, validation signée, paiement, justificatif ----------
async function pageDepenses() {
  const [demandes, profils, pieces] = await Promise.all([
    q(sb.from('expense_requests').select('*').order('created_at', { ascending: false })),
    q(sb.from('profiles').select('*')).catch(() => [S.profil]),
    q(sb.from('attachments').select('*')),
  ]);
  const nomDe = (id) => id === S.profil.id ? 'Vous' : profils.find((p) => p.id === id)?.nom || 'Membre du bureau';
  const pieceDe = (d) => pieces.find((a) => a.request_id === d.id);
  const delai = Number(S.settings.delai_justificatif_jours ?? 7);
  const FILTRES = {
    a_valider: ['À valider', (d) => d.statut === 'soumise'],
    a_payer: ['À payer', (d) => d.statut === 'validee'],
    a_justifier: ['Justificatif attendu', (d) => d.statut === 'payee'],
    terminees: ['Terminées', (d) => ['justifiee', 'refusee', 'annulee'].includes(d.statut)],
    toutes: ['Toutes', () => true],
  };
  if (!S.filtreDepenses) S.filtreDepenses = peut('valider_depenses') ? 'a_valider' : peut('payer_depenses') ? 'a_payer' : 'toutes';
  const liste = demandes.filter(FILTRES[S.filtreDepenses][1]);
  const peutDemander = peut('demander_depenses');

  const carte = (d) => {
    const piece = pieceDe(d);
    const retard = d.statut === 'payee' && d.payee_le && joursDepuis(d.payee_le) > delai;
    const actions = [];
    if (peut('valider_depenses') && d.statut === 'soumise') actions.push(`<button class="btn-texte" data-refuser="${d.id}">Refuser</button>`, `<button class="btn-primaire" data-valider="${d.id}">Valider et signer</button>`);
    if (peut('payer_depenses') && d.statut === 'validee' && d.validee_par !== S.profil.id) actions.push(`<button class="btn-primaire" data-payer="${d.id}">Payer</button>`);
    if (d.statut === 'payee' && (peut('payer_depenses', 'saisir_ecritures') || d.demandeur === S.profil.id)) actions.push(`<button class="btn-tonal" data-justifier="${d.id}">Joindre le justificatif</button>`);
    if (d.statut === 'soumise' && d.demandeur === S.profil.id) actions.push(`<button class="btn-texte" data-annuler="${d.id}">Annuler la demande</button>`);
    return `<article class="carte">
      <div style="display:flex;gap:12px;align-items:flex-start;flex-wrap:wrap">
        <div style="flex:1;min-width:180px"><h3 style="font-size:18px">${esc(d.objet)}</h3>
          <span class="muted">${esc(nomDe(d.demandeur))} · ${dateFr(String(d.created_at).slice(0, 10))} · ${esc(nomCategorie(d.category_id))}${d.project_id ? ' · ' + esc(nomProjet(d.project_id)) : ''}</span></div>
        <div style="text-align:right"><b class="num" style="font-size:22px">${eur(d.montant)}</b><br><span class="puce ${STATUTS[d.statut][0]}">${STATUTS[d.statut][1]}</span></div>
      </div>
      ${etapes(d)}
      ${retard ? `<div class="alerte">Justificatif en retard&nbsp;: payé il y a ${joursDepuis(d.payee_le)}&nbsp;jours, délai de ${delai}&nbsp;jours.</div>` : ''}
      ${d.statut === 'refusee' && d.motif_refus ? `<p class="muted">Motif du refus&nbsp;: ${esc(d.motif_refus)}</p>` : ''}
      <div class="filtres">
        ${d.signature_path ? `<button class="btn-texte btn-petit" data-voir="${esc(d.signature_path)}" data-bucket="signatures">Signature</button>` : ''}
        ${piece ? `<button class="btn-texte btn-petit" data-voir="${esc(piece.storage_path)}">Justificatif</button>` : ''}
        ${d.validee_le ? `<span class="muted">Validée le ${dateFr(String(d.validee_le).slice(0, 10))}</span>` : ''}
        ${d.payee_le ? `<span class="muted">Payée le ${dateFr(String(d.payee_le).slice(0, 10))}</span>` : ''}
      </div>
      ${actions.length ? `<div class="actions">${actions.join('')}</div>` : ''}
    </article>`;
  };

  rendre(`<div class="page">
    <div class="page-titre"><h1>Dépenses</h1>${peutDemander ? '<button class="btn-primaire" id="b-demande">Nouvelle demande</button>' : ''}</div>
    <div class="filtres" role="tablist">${Object.entries(FILTRES).map(([k, [l, f]]) => {
      const n = demandes.filter(f).length;
      return `<button class="btn-petit ${S.filtreDepenses === k ? 'btn-primaire' : ''}" data-filtre="${k}" role="tab" aria-selected="${S.filtreDepenses === k}">${l}${k !== 'toutes' && k !== 'terminees' && n ? ` (${n})` : ''}</button>`;
    }).join('')}</div>
    ${liste.length ? liste.map(carte).join('') : `<div class="carte vide">Aucune demande ${S.filtreDepenses === 'toutes' ? '' : FILTRES[S.filtreDepenses][0].toLowerCase()}.${peutDemander && S.filtreDepenses === 'toutes' ? '<button class="btn-primaire" id="b-demande-vide">Nouvelle demande</button>' : ''}</div>`}
  </div>`);

  const recharger = () => pageDepenses().catch(erreur);
  const trouver = (id) => demandes.find((d) => d.id === id);
  document.querySelectorAll('[data-filtre]').forEach((b) => b.addEventListener('click', () => { S.filtreDepenses = b.dataset.filtre; recharger(); }));
  ['#b-demande', '#b-demande-vide'].forEach((sel) => $(sel)?.addEventListener('click', () => feuilleDemande(recharger)));
  document.querySelectorAll('[data-valider]').forEach((b) => b.addEventListener('click', () => feuilleValider(trouver(b.dataset.valider), nomDe, recharger)));
  document.querySelectorAll('[data-refuser]').forEach((b) => b.addEventListener('click', () => feuilleRefuser(trouver(b.dataset.refuser), recharger)));
  document.querySelectorAll('[data-payer]').forEach((b) => b.addEventListener('click', () => feuillePayer(trouver(b.dataset.payer), recharger)));
  document.querySelectorAll('[data-justifier]').forEach((b) => b.addEventListener('click', () => feuilleJustifier(trouver(b.dataset.justifier), recharger)));
  document.querySelectorAll('[data-annuler]').forEach((b) => b.addEventListener('click', () => {
    const d = trouver(b.dataset.annuler);
    ouvrirFeuille(`<h2>Annuler cette demande&#8239;?</h2><p>«&nbsp;${esc(d.objet)}&nbsp;», ${eur(d.montant)}</p>
      <div class="actions"><button class="btn-texte" id="b-non">Garder</button><button class="btn-danger" id="b-oui">Annuler la demande</button></div>`, (root) => {
      $('#b-non', root).addEventListener('click', fermerFeuille);
      $('#b-oui', root).addEventListener('click', async () => {
        try { await q(sb.from('expense_requests').update({ statut: 'annulee' }).eq('id', d.id)); fermerFeuille(); toast('Demande annulée'); recharger(); } catch (e) { erreur(e); }
      });
    });
  }));
  brancherPieces(recharger);
}

function feuilleDemande(apres) {
  ouvrirFeuille(`<form id="f-dem" class="champs">
    <h2>Nouvelle demande</h2>
    <label class="champ"><span class="obligatoire">Objet</span><input name="objet" maxlength="120" required placeholder="Location de la sono pour le concert"></label>
    <div class="champs champs-2">
      <label class="champ"><span class="obligatoire">Montant (€)</span><input name="montant" type="number" inputmode="decimal" step="0.01" min="0.01" required></label>
      <label class="champ"><span class="obligatoire">Catégorie</span><select name="categorie" required>${S.categories.filter((c) => c.sens === 'depense').map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('')}</select></label>
    </div>
    <label class="champ">Activité<select name="projet"><option value="">Aucune</option>${optionsProjets()}</select></label>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Envoyer</button></div>
  </form>`, (root) => {
    const f = $('#f-dem', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = f.querySelector('button:not([type])'); btn.disabled = true;
      try {
        await q(sb.from('expense_requests').insert({
          demandeur: S.profil.id, objet: f.objet.value.trim(), montant: Number(f.montant.value),
          category_id: f.categorie.value, project_id: f.projet.value || null, statut: 'soumise',
        }));
        fermerFeuille(); toast('Demande envoyée au président'); S.filtreDepenses = 'toutes'; apres();
      } catch (err) { btn.disabled = false; erreur(err); }
    });
  });
}

function feuilleValider(d, nomDe, apres) {
  ouvrirFeuille(`<h2>Valider cette dépense&#8239;?</h2>
    <p><b>${esc(d.objet)}</b>, ${eur(d.montant)}<br><span class="muted">Demandée par ${esc(nomDe(d.demandeur))} · ${esc(nomCategorie(d.category_id))}</span></p>
    <label class="champ obligatoire" for="sig">Signature</label>
    <canvas id="sig" style="width:100%;height:180px;border:1px dashed var(--bord);border-radius:20px;background:#FFF;touch-action:none"></canvas>
    <div class="actions" style="justify-content:space-between"><button class="btn-texte" id="b-effacer">Effacer</button>
      <span><button class="btn-texte" id="b-annuler">Annuler</button> <button class="btn-primaire" id="b-signer">Signer et valider</button></span></div>`, (root) => {
    const zone = zoneSignature($('#sig', root));
    $('#b-effacer', root).addEventListener('click', zone.effacer);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    $('#b-signer', root).addEventListener('click', async (e) => {
      if (zone.vide()) return toast('Signature obligatoire');
      e.target.disabled = true;
      try {
        const chemin = `${d.id}-${Date.now()}.png`;
        await q(sb.storage.from('signatures').upload(chemin, await zone.image(), { contentType: 'image/png' }));
        // Empreinte : prouve que la signature porte sur ce montant et cet objet
        const empreinte = await sha256([d.id, d.montant, d.objet, new Date().toISOString(), S.profil.id].join('|'));
        await q(sb.from('expense_requests').update({ statut: 'validee', signature_path: chemin, signature_hash: empreinte }).eq('id', d.id));
        fermerFeuille(); toast('Dépense validée'); apres();
      } catch (err) { e.target.disabled = false; erreur(err); }
    });
  });
}

function feuilleRefuser(d, apres) {
  ouvrirFeuille(`<form id="f-ref" class="champs"><h2>Refuser cette dépense&#8239;?</h2>
    <p><b>${esc(d.objet)}</b>, ${eur(d.montant)}</p>
    <label class="champ"><span class="obligatoire">Motif</span><textarea name="motif" rows="3" required maxlength="300"></textarea></label>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-danger">Refuser</button></div></form>`, (root) => {
    const f = $('#f-ref', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      try { await q(sb.from('expense_requests').update({ statut: 'refusee', motif_refus: f.motif.value.trim() }).eq('id', d.id)); fermerFeuille(); toast('Demande refusée'); apres(); }
      catch (err) { erreur(err); }
    });
  });
}

function feuillePayer(d, apres) {
  ouvrirFeuille(`<form id="f-pay" class="champs"><h2>Payer cette dépense</h2>
    <p><b>${esc(d.objet)}</b>, ${eur(d.montant)}</p>
    <div class="groupe" role="group" aria-label="Mode de paiement">
      <button type="button" data-mode="especes" aria-pressed="true">Espèces</button>
      <button type="button" data-mode="virement" aria-pressed="false">Virement</button>
    </div>
    <div class="champs champs-2">
      <label class="champ">Compte<select name="compte">${S.comptes.map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('')}</select></label>
      <label class="champ">Date<input type="date" name="date" value="${aujourdhui()}" required></label>
    </div>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer le paiement</button></div></form>`, (root) => {
    const f = $('#f-pay', root); let mode = 'especes';
    const majCompte = () => { const c = S.comptes.find((x) => x.type === (mode === 'especes' ? 'caisse' : 'banque')); if (c) f.compte.value = c.id; };
    majCompte();
    root.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => {
      mode = b.dataset.mode; root.querySelectorAll('[data-mode]').forEach((x) => x.setAttribute('aria-pressed', x === b)); majCompte();
    }));
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      try { await q(sb.rpc('payer_demande', { p_id: d.id, p_compte: f.compte.value, p_mode: mode, p_date: f.date.value })); fermerFeuille(); toast('Paiement enregistré'); apres(); }
      catch (err) { erreur(err); }
    });
  });
}

function feuilleJustifier(d, apres) {
  ouvrirFeuille(`<form id="f-just" class="champs"><h2>Joindre le justificatif</h2>
    <p><b>${esc(d.objet)}</b>, ${eur(d.montant)}</p>
    <label class="champ"><span class="obligatoire">Facture ou ticket</span><input type="file" name="piece" accept="image/*,application/pdf" capture="environment" required></label>
    <p class="muted">Photo ou PDF de 3&nbsp;Mo maximum.</p>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Joindre</button></div></form>`, (root) => {
    const f = $('#f-just', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const btn = f.querySelector('button:not([type])'); btn.disabled = true;
      try {
        const fi = await preparerFichier(f.piece.files[0]);
        const chemin = nomFichier(fi.ext);
        await q(sb.storage.from('justificatifs').upload(chemin, fi.blob, { contentType: fi.mime }));
        await q(sb.rpc('justifier_demande', { p_id: d.id, p_chemin: chemin, p_mime: fi.mime, p_ko: fi.ko }));
        fermerFeuille(); toast(`Justificatif joint (${fi.ko} Ko)`); apres();
      } catch (err) { btn.disabled = false; erreur(err); }
    });
  });
}

// ---------- Budget : prévisionnel (ressources et emplois), réalisé, alertes ----------
async function pageBudget() {
  if (!S.anneeBudget) S.anneeBudget = new Date().getFullYear();
  const an = S.anneeBudget;
  const [suivi, lignes, txs] = await Promise.all([
    q(sb.from('v_budget_suivi').select('*').eq('annee', an)),
    q(sb.from('budgets').select('*').eq('annee', an)),
    q(sb.from('transactions').select('*').gte('date_op', `${an}-01-01`).lte('date_op', `${an}-12-31`)),
  ]);
  const ligneDe = (s) => lignes.find((l) => l.category_id === s.category_id && (l.project_id || null) === (s.project_id || null));
  const general = suivi.filter((s) => !s.project_id);
  const parActivite = suivi.filter((s) => s.project_id);
  const tot = (l, sens, champ) => l.filter((s) => s.sens === sens).reduce((t, s) => t + Number(s[champ]), 0);
  // Réalisé hors budget : catégories mouvementées sans ligne de budget général
  const horsBudget = {};
  txs.forEach((t) => {
    if (general.some((g) => g.category_id === t.category_id)) return;
    horsBudget[t.category_id] = (horsBudget[t.category_id] || 0) + Number(t.montant);
  });
  const ligneHtml = (s) => {
    const l = ligneDe(s); const taux = Number(s.taux_pct || 0);
    const etat = s.sens === 'depense' && taux > 100 ? '<span class="puce puce-ko">Dépassé</span>' : s.alerte ? '<span class="puce puce-partiel">Alerte</span>' : '';
    return `<tr>
      <td>${esc(s.categorie)}${s.project_id ? `<br><span class="muted">${esc(nomProjet(s.project_id))}</span>` : ''}</td>
      <td class="droite">${peut('gerer_budget') && l ? `<input type="number" step="0.01" min="0" value="${Number(s.montant_prevu).toFixed(2)}" data-prevu="${l.id}" aria-label="Prévu ${esc(s.categorie)}" style="width:120px;min-height:40px;text-align:right">` : `<span class="num">${eur(s.montant_prevu)}</span>`}</td>
      <td class="droite num">${eur(s.realise)}</td>
      <td style="min-width:120px"><div class="barre"><span style="width:${Math.min(100, taux)}%;background:${s.sens === 'recette' ? 'var(--bleu)' : taux > 100 ? 'var(--erreur)' : s.alerte ? 'var(--jaune)' : 'var(--primaire)'}"></span></div><span class="muted">${taux.toLocaleString('fr-FR')}&nbsp;%</span></td>
      <td>${etat}${peut('gerer_budget') && l ? ` <button class="btn-texte btn-petit" data-suppr="${l.id}" aria-label="Retirer la ligne ${esc(s.categorie)}">Retirer</button>` : ''}</td></tr>`;
  };
  const table = (l) => `<div class="tableau-wrap"><table><thead><tr><th>Poste</th><th class="droite">Prévu</th><th class="droite">Réalisé</th><th>Taux</th><th></th></tr></thead><tbody>${l.map(ligneHtml).join('')}</tbody></table></div>`;
  const resPrevu = tot(general, 'recette', 'montant_prevu') - tot(general, 'depense', 'montant_prevu');
  const resReel = txs.reduce((t, x) => t + (x.sens === 'recette' ? 1 : -1) * Number(x.montant), 0);

  rendre(`<div class="page">
    <div class="page-titre"><h1>Budget</h1>
      <select id="an" aria-label="Année" style="width:auto">${[an + 1, an, an - 1, an - 2].map((a) => `<option ${a === an ? 'selected' : ''}>${a}</option>`).join('')}</select>
      ${peut('gerer_budget') ? '<button class="btn-primaire btn-petit" id="b-ligne">Ajouter une ligne</button>' : ''}</div>
    <div class="kpis">
      <div class="carte"><span class="muted">Ressources</span><b class="num recette">${eur(tot(general, 'recette', 'realise'))}</b><span class="muted">sur ${eur(tot(general, 'recette', 'montant_prevu'))}</span></div>
      <div class="carte"><span class="muted">Emplois</span><b class="num depense">${eur(tot(general, 'depense', 'realise'))}</b><span class="muted">sur ${eur(tot(general, 'depense', 'montant_prevu'))}</span></div>
      <div class="carte"><span class="muted">Résultat</span><b class="num ${resReel < 0 ? 'negatif' : ''}">${eur(resReel)}</b><span class="muted">prévu ${eur(resPrevu)}</span></div>
    </div>
    ${general.length ? `<section class="carte"><h2>Emplois (dépenses)</h2>${table(general.filter((s) => s.sens === 'depense'))}</section>
      <section class="carte"><h2>Ressources (recettes)</h2>${table(general.filter((s) => s.sens === 'recette'))}</section>`
      : `<div class="carte vide">Aucun budget pour ${an}.${peut('gerer_budget') ? '<button class="btn-primaire" id="b-ligne-vide">Ajouter une ligne</button>' : ''}</div>`}
    ${parActivite.length ? `<section class="carte"><h2>Par activité</h2>${table(parActivite)}</section>` : ''}
    ${Object.keys(horsBudget).length ? `<section class="carte"><h2>Hors budget</h2><ul class="liste">${Object.entries(horsBudget).map(([id, m]) => `<li><div class="corps"><b>${esc(nomCategorie(id))}</b><span>${S.categories.find((c) => c.id === id)?.sens === 'recette' ? 'Recette' : 'Dépense'} sans ligne de budget</span></div><span class="num">${eur(m)}</span></li>`).join('')}</ul></section>` : ''}
  </div>`);
  const recharger = () => pageBudget().catch(erreur);
  $('#an').addEventListener('change', (e) => { S.anneeBudget = Number(e.target.value); recharger(); });
  ['#b-ligne', '#b-ligne-vide'].forEach((sel) => $(sel)?.addEventListener('click', () => feuilleLigneBudget(an, recharger)));
  document.querySelectorAll('[data-prevu]').forEach((i) => i.addEventListener('change', async () => {
    try { await q(sb.from('budgets').update({ montant_prevu: Number(i.value) }).eq('id', i.dataset.prevu)); toast('Budget modifié'); recharger(); } catch (e) { erreur(e); }
  }));
  document.querySelectorAll('[data-suppr]').forEach((b) => b.addEventListener('click', async () => {
    try { await q(sb.from('budgets').delete().eq('id', b.dataset.suppr)); toast('Ligne retirée'); recharger(); } catch (e) { erreur(e); }
  }));
}

function feuilleLigneBudget(an, apres) {
  const groupe = (sens, titre) => `<optgroup label="${titre}">${S.categories.filter((c) => c.sens === sens).map((c) => `<option value="${c.id}">${esc(c.nom)}</option>`).join('')}</optgroup>`;
  ouvrirFeuille(`<form id="f-bud" class="champs"><h2>Ligne de budget ${an}</h2>
    <label class="champ"><span class="obligatoire">Poste</span><select name="categorie">${groupe('depense', 'Emplois (dépenses)')}${groupe('recette', 'Ressources (recettes)')}</select></label>
    <label class="champ">Activité<select name="projet"><option value="">Budget général</option>${optionsProjets()}</select></label>
    <label class="champ"><span class="obligatoire">Montant prévu (€)</span><input name="montant" type="number" step="0.01" min="0" required></label>
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Ajouter</button></div></form>`, (root) => {
    const f = $('#f-bud', root);
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      try {
        await q(sb.from('budgets').insert({ annee: an, category_id: f.categorie.value, project_id: f.projet.value || null,
          montant_prevu: Number(f.montant.value), seuil_alerte_pct: Number(S.settings.seuil_alerte_budget_pct ?? 90) }));
        fermerFeuille(); toast('Ligne ajoutée'); apres();
      } catch (err) { erreur(err); }
    });
  });
}

// ---------- Planning : calendrier du mois, semaine, agenda, et suivi des activités ----------
const JOURS_COURTS = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];
const JOURS = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];
const dateDe = (s) => new Date(s + 'T12:00:00');
const ajouterJours = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const lundiDe = (d) => ajouterJours(d, -((d.getDay() + 6) % 7));
// 09:30 -> « 9 h 30 », 18:00 -> « 18 h » (usage français)
const heure = (h) => { if (!h) return ''; const [hh, mm] = String(h).split(':'); return `${Number(hh)}\u00a0h${mm && mm !== '00' ? '\u00a0' + mm : ''}`; };
const majuscule = (t) => t.charAt(0).toUpperCase() + t.slice(1);
const jourLong = (s) => { const d = dateDe(s); return `${JOURS[(d.getDay() + 6) % 7]} ${d.getDate()} ${MOIS[d.getMonth()]}`; };
const preferenceVue = () => { try { return localStorage.getItem('vuePlanning'); } catch { return null; } };

function listePlanning(l) {
  if (!l.length) return '<p class="muted">Aucune activité prévue</p>';
  return `<ul class="liste">${l.map((p) => `<li class="cliquable" data-evt="${p.id}" tabindex="0" role="button"><span class="avatar date-pastille"><b>${p.date_debut ? Number(p.date_debut.slice(8, 10)) : '–'}</b>${p.date_debut ? MOIS[Number(p.date_debut.slice(5, 7)) - 1].slice(0, 4) : ''}</span>
    <div class="corps"><b>${esc(p.nom)}</b><span>${p.date_debut ? jourLong(p.date_debut) : 'Date à fixer'}${p.heure_debut ? ' · ' + heure(p.heure_debut) : ''}${p.lieu ? ' · ' + esc(p.lieu) : ''}</span></div>
    ${p.participation ? `<span class="puce puce-partiel">${eur(p.participation)}</span>` : ''}</li>`).join('')}</ul>`;
}

async function pageActivites() {
  const P = (S.planning ||= { vue: preferenceVue() || 'mois', ref: isoLocal(new Date()) });
  const suivi = peut('gerer_activites', 'consulter_finances');
  const vues = [['mois', 'Mois'], ['semaine', 'Semaine'], ['agenda', 'Agenda'], ...(suivi ? [['liste', 'Activités']] : [])];
  if (!vues.some(([k]) => k === P.vue)) P.vue = 'mois';
  if (P.vue === 'liste') return pageSuiviActivites(vues);
  const ref = dateDe(P.ref);
  let debut, fin, titre;
  if (P.vue === 'mois') {
    const premier = new Date(ref.getFullYear(), ref.getMonth(), 1, 12);
    debut = lundiDe(premier); fin = ajouterJours(lundiDe(new Date(ref.getFullYear(), ref.getMonth() + 1, 0, 12)), 6);
    titre = majuscule(`${MOIS[ref.getMonth()]} ${ref.getFullYear()}`);
  } else if (P.vue === 'semaine') {
    debut = lundiDe(ref); fin = ajouterJours(debut, 6);
    titre = `Semaine du ${debut.getDate()} ${MOIS[debut.getMonth()]}`;
  } else { debut = ref; fin = ajouterJours(ref, 90); titre = `À partir du ${ref.getDate()} ${MOIS[ref.getMonth()]}`; }
  const moisVus = [...new Set([debut, ajouterJours(debut, 15), fin, ref].map((d) => d.getMonth() + 1))];
  const [evts, ...anniv] = await Promise.all([
    q(sb.rpc('planning_activites', { p_debut: isoLocal(debut), p_fin: isoLocal(fin) })),
    ...moisVus.map((m) => q(sb.rpc('anniversaires_du_mois', { p_mois: m })).then((l) => l.map((a) => ({ ...a, mois: m })))),
  ]);
  S.evts = evts;
  const parJour = {};
  evts.forEach((e) => {
    let d = dateDe(e.date_debut); const f = dateDe(e.date_fin || e.date_debut);
    for (let i = 0; d <= f && i < 62; i++, d = ajouterJours(d, 1)) (parJour[isoLocal(d)] ||= []).push(e);
  });
  const annivDe = (s) => { const d = dateDe(s); return anniv.flat().filter((a) => a.mois === d.getMonth() + 1 && a.jour === d.getDate()); };
  const auj = isoLocal(new Date());
  const puce = (e) => `<button class="evt evt-${e.type}" data-evt="${e.id}">${e.heure_debut ? `<small>${String(e.heure_debut).slice(0, 5)}</small> ` : ''}${esc(e.nom)}</button>`;
  let corps = '';
  if (P.vue === 'mois') {
    const cases = [];
    for (let d = new Date(debut); d <= fin; d = ajouterJours(d, 1)) cases.push(isoLocal(d));
    corps = `<div class="cal"><div class="cal-tete">${JOURS_COURTS.map((j) => `<span>${j}</span>`).join('')}</div>
      <div class="cal-grille">${cases.map((s) => {
        const e = parJour[s] || [], a = annivDe(s), horsMois = dateDe(s).getMonth() !== ref.getMonth();
        return `<div class="cal-jour ${horsMois ? 'hors' : ''} ${s === auj ? 'auj' : ''}" data-jour="${s}" tabindex="0" role="button" aria-label="${jourLong(s)}${e.length ? ', ' + e.length + ' événement' + (e.length > 1 ? 's' : '') : ''}">
          <span class="num-jour">${Number(s.slice(8))}</span>${e.slice(0, 3).map(puce).join('')}${e.length > 3 ? `<small class="muted">+${e.length - 3}</small>` : ''}
          ${a.length ? `<small class="anniv" title="Anniversaire : ${esc(a.map((x) => x.prenom).join(', '))}">Anniv. ${esc(a.map((x) => x.prenom).join(', '))}</small>` : ''}</div>`;
      }).join('')}</div></div>`;
  } else {
    const jours = [];
    for (let d = new Date(debut); d <= fin; d = ajouterJours(d, 1)) jours.push(isoLocal(d));
    const montres = P.vue === 'semaine' ? jours : jours.filter((s) => parJour[s] || annivDe(s).length);
    corps = montres.length ? `<section class="carte"><ul class="liste agenda">${montres.map((s) => `<li class="agenda-jour ${s === auj ? 'auj' : ''}">
      <button class="agenda-date" data-jour="${s}"><b>${Number(s.slice(8))}</b><span>${JOURS_COURTS[(dateDe(s).getDay() + 6) % 7]}</span></button>
      <div class="corps">${(parJour[s] || []).map((e) => `<button class="agenda-evt evt-${e.type}" data-evt="${e.id}"><b>${esc(e.nom)}</b><span>${[e.heure_debut ? heure(e.heure_debut) + (e.heure_fin ? ' – ' + heure(e.heure_fin) : '') : 'Journée', e.lieu ? esc(e.lieu) : ''].filter(Boolean).join(' · ')}</span></button>`).join('')}
        ${annivDe(s).map((a) => `<span class="anniv">Anniversaire de ${esc(a.prenom)} ${esc(a.nom)}</span>`).join('')}
        ${!(parJour[s] || []).length && !annivDe(s).length ? '<span class="muted">–</span>' : ''}</div></li>`).join('')}</ul></section>`
      : '<div class="carte vide">Aucun événement sur cette période</div>';
  }
  rendre(`<div class="page">
    <div class="page-titre"><h1>Planning</h1></div>
    <div class="onglets" role="tablist">${vues.map(([k, l]) => `<button role="tab" aria-selected="${P.vue === k}" data-vue="${k}">${l}</button>`).join('')}</div>
    <div class="filtres cal-nav"><button class="btn-texte btn-petit" id="b-prec" aria-label="Précédent">‹</button><h2 style="margin:0;flex:1;text-align:center">${esc(titre)}</h2><button class="btn-texte btn-petit" id="b-suiv" aria-label="Suivant">›</button><button class="btn-tonal btn-petit" id="b-auj">Aujourd’hui</button></div>
    ${corps}
    ${peut('gerer_activites') ? `<button class="fab" id="b-evt" aria-label="Nouvel événement" title="Nouvel événement"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg></button>` : ''}
  </div>`);
  const recharger = () => pageActivites().catch(erreur);
  document.querySelectorAll('[data-vue]').forEach((b) => b.addEventListener('click', () => {
    P.vue = b.dataset.vue; try { localStorage.setItem('vuePlanning', P.vue); } catch { /* stockage indisponible */ }
    recharger();
  }));
  const decaler = (n) => {
    const d = dateDe(P.ref);
    if (P.vue === 'mois') P.ref = isoLocal(new Date(d.getFullYear(), d.getMonth() + n, 1, 12));
    else P.ref = isoLocal(ajouterJours(d, n * (P.vue === 'semaine' ? 7 : 90)));
    recharger();
  };
  $('#b-prec').addEventListener('click', () => decaler(-1));
  $('#b-suiv').addEventListener('click', () => decaler(1));
  $('#b-auj').addEventListener('click', () => { P.ref = isoLocal(new Date()); recharger(); });
  $('#b-evt')?.addEventListener('click', () => feuilleActivite(null, recharger, { date: P.vue === 'mois' && dateDe(P.ref).getMonth() !== new Date().getMonth() ? P.ref : auj }));
  document.querySelectorAll('[data-evt]').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); detailEvenement(evts.find((x) => x.id === b.dataset.evt), recharger); }));
  document.querySelectorAll('[data-jour]').forEach((c) => {
    const ouvrir = () => feuilleJour(c.dataset.jour, parJour[c.dataset.jour] || [], annivDe(c.dataset.jour), recharger);
    c.addEventListener('click', ouvrir); c.addEventListener('keydown', (e) => { if (e.key === 'Enter') ouvrir(); });
  });
}

function feuilleJour(s, evts, anniv, recharger) {
  ouvrirFeuille(`<h2>${esc(jourLong(s))}</h2>
    ${evts.length ? `<ul class="liste">${evts.map((e) => `<li class="cliquable" data-e="${e.id}" tabindex="0" role="button"><div class="corps"><b>${esc(e.nom)}</b><span>${[e.heure_debut ? heure(e.heure_debut) : 'Journée', e.lieu].filter(Boolean).map(esc).join(' · ')}</span></div>${e.type === 'evenement' ? '<span class="puce puce-neutre">Événement</span>' : '<span class="puce puce-partiel">Activité</span>'}</li>`).join('')}</ul>` : '<p class="muted">Rien de prévu</p>'}
    ${anniv.map((a) => `<p class="anniv">Anniversaire de ${esc(a.prenom)} ${esc(a.nom)}</p>`).join('')}
    <div class="actions">${peut('gerer_activites') ? '<button class="btn-tonal" id="b-ajout">Ajouter un événement</button>' : ''}<button class="btn-primaire" id="b-fermer">Fermer</button></div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    $('#b-ajout', root)?.addEventListener('click', () => feuilleActivite(null, recharger, { date: s }));
    root.querySelectorAll('[data-e]').forEach((li) => li.addEventListener('click', () => detailEvenement(evts.find((x) => x.id === li.dataset.e), recharger)));
  });
}

async function detailEvenement(e, recharger) {
  let part = '';
  if (e.collecte_id) {
    if (peut('consulter_finances', 'gerer_cotisations', 'gerer_activites')) {
      const c = (await q(sb.from('v_collectes').select('*').eq('id', e.collecte_id)))[0];
      if (c) part = `<div class="info">Participation${c.montant_attendu ? ` de ${eur(c.montant_attendu)} par personne` : ''}&nbsp;: <b>${eur(c.total_recu)}</b> reçus de ${c.nb_contributeurs} contributeur${c.nb_contributeurs > 1 ? 's' : ''}</div>`;
    } else {
      const m = (await q(sb.rpc('mes_participations'))).find((x) => x.collecte_id === e.collecte_id);
      if (m) part = `<div class="info">Participation demandée&nbsp;: <b>${m.montant_attendu ? eur(m.montant_attendu) : 'libre'}</b> · vous avez donné ${eur(m.donne)}</div>`;
    }
  }
  const quand = e.date_debut ? jourLong(e.date_debut) + (e.date_fin && e.date_fin !== e.date_debut ? ' au ' + jourLong(e.date_fin) : '') : 'Date à fixer';
  ouvrirFeuille(`<div style="display:flex;gap:8px;align-items:flex-start"><h2 style="flex:1">${esc(e.nom)}</h2>${e.type === 'evenement' ? '<span class="puce puce-neutre">Événement</span>' : '<span class="puce puce-partiel">Activité</span>'}</div>
    <div class="details">
      <div class="ligne-detail"><span class="muted">Date</span><span>${esc(quand)}</span></div>
      ${e.heure_debut ? `<div class="ligne-detail"><span class="muted">Heure</span><span>${heure(e.heure_debut)}${e.heure_fin ? ' – ' + heure(e.heure_fin) : ''}</span></div>` : ''}
      ${e.lieu ? `<div class="ligne-detail"><span class="muted">Lieu</span><span>${esc(e.lieu)}</span></div>` : ''}
      ${peut('gerer_activites') ? `<div class="ligne-detail"><span class="muted">Visibilité</span><span>${e.visible_adherents ? 'Tous les membres' : 'Bureau seulement'}</span></div>` : ''}
    </div>
    ${e.description ? `<p>${esc(e.description)}</p>` : ''}
    ${part}
    <div class="actions">
      ${e.collecte_id && peut('consulter_finances', 'gerer_cotisations') ? '<button class="btn-texte" id="b-part">Voir les participations</button>' : ''}
      ${!e.collecte_id && peut('gerer_activites') ? '<button class="btn-texte" id="b-demander">Demander une participation</button>' : ''}
      ${peut('gerer_activites') ? '<button class="btn-tonal" id="b-modif">Modifier</button>' : ''}
      <button class="btn-primaire" id="b-fermer">Fermer</button></div>`, (root) => {
    $('#b-fermer', root).addEventListener('click', fermerFeuille);
    $('#b-modif', root)?.addEventListener('click', async () => {
      const p = (await q(sb.from('projects').select('*').eq('id', e.id)))[0] || e;
      feuilleActivite(p, recharger);
    });
    $('#b-demander', root)?.addEventListener('click', () => feuilleCollecte(null, recharger, [], e.id));
    $('#b-part', root)?.addEventListener('click', async () => {
      const c = (await q(sb.from('v_collectes').select('*').eq('id', e.collecte_id)))[0];
      if (c) detailCollecte(c);
    });
  });
}

// Activité ou événement ; « Demander une participation » crée la collecte liée
function feuilleActivite(p, apres, pre = {}) {
  const v = p || { nom: '', type: pre.type || 'evenement', date_debut: pre.date || '', date_fin: '', heure_debut: '', heure_fin: '', lieu: '', description: '', visible_adherents: true };
  ouvrirFeuille(`<form id="f-act" class="champs"><h2>${p ? 'Modifier' : 'Nouvel événement'}</h2>
    <div class="groupe" role="group" aria-label="Type">
      <button type="button" data-type="evenement" aria-pressed="${v.type !== 'activite'}">Événement</button>
      <button type="button" data-type="activite" aria-pressed="${v.type === 'activite'}">Activité</button></div>
    <label class="champ"><span class="obligatoire">Nom</span><input name="nom" value="${esc(v.nom)}" maxlength="80" required></label>
    <div class="champs champs-2">
      <label class="champ"><span class="obligatoire">Date</span><input type="date" name="debut" value="${esc(v.date_debut || '')}" ${v.type === 'activite' ? '' : 'required'}></label>
      <label class="champ">Jusqu’au<input type="date" name="fin" value="${esc(v.date_fin && v.date_fin !== v.date_debut ? v.date_fin : '')}"></label>
      <label class="champ">Heure de début<input type="time" name="hdebut" value="${esc(String(v.heure_debut || '').slice(0, 5))}"></label>
      <label class="champ">Heure de fin<input type="time" name="hfin" value="${esc(String(v.heure_fin || '').slice(0, 5))}"></label>
    </div>
    <label class="champ">Lieu<input name="lieu" value="${esc(v.lieu || '')}" maxlength="120"></label>
    <label class="champ">Description<textarea name="description" rows="2" maxlength="500">${esc(v.description || '')}</textarea></label>
    <label class="case"><input type="checkbox" name="visible" ${v.visible_adherents ? 'checked' : ''}> Visible de tous les membres</label>
    ${p ? '' : `<label class="case"><input type="checkbox" name="part"> Demander une participation aux membres</label>
    <div class="champs champs-2" id="z-part" hidden>
      <label class="champ">Montant par personne (€)<input name="attendu" type="number" step="0.01" min="0.01" placeholder="Libre"></label>
      <label class="champ">Date limite<input name="limite" type="date"></label></div>`}
    <div class="actions"><button type="button" class="btn-texte" id="b-annuler">Annuler</button><button class="btn-primaire">Enregistrer</button></div></form>`, (root) => {
    const f = $('#f-act', root);
    let type = v.type || 'evenement';
    root.querySelectorAll('[data-type]').forEach((b) => b.addEventListener('click', () => {
      type = b.dataset.type; root.querySelectorAll('[data-type]').forEach((x) => x.setAttribute('aria-pressed', x === b));
      f.debut.required = type !== 'activite';
    }));
    f.part?.addEventListener('change', () => { $('#z-part', root).hidden = !f.part.checked; });
    $('#b-annuler', root).addEventListener('click', fermerFeuille);
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (f.debut.value && f.fin.value && f.fin.value < f.debut.value) return toast('La date de fin doit suivre la date de début');
      if (f.hdebut.value && f.hfin.value && !f.fin.value && f.hfin.value < f.hdebut.value) return toast('L’heure de fin doit suivre l’heure de début');
      const d = { nom: f.nom.value.trim(), type, date_debut: f.debut.value || null, date_fin: f.fin.value || f.debut.value || null,
        heure_debut: f.hdebut.value || null, heure_fin: f.hfin.value || null, lieu: f.lieu.value.trim() || null,
        description: f.description.value.trim() || null, visible_adherents: f.visible.checked };
      try {
        let id = p?.id;
        if (p) await q(sb.from('projects').update(d).eq('id', p.id));
        else id = (await q(sb.from('projects').insert(d).select()))[0].id;
        if (f.part?.checked) await q(sb.from('collectes').insert({ nom: `Participation : ${d.nom}`, project_id: id,
          montant_attendu: f.attendu.value ? Number(f.attendu.value) : null, date_limite: f.limite.value || null, tous_membres: true }));
        S.projets = await q(sb.from('projects').select('*').order('date_debut', { ascending: false }));
        if (peut('consulter_finances', 'gerer_cotisations', 'gerer_activites', 'saisir_ecritures')) S.collectes = await q(sb.from('collectes').select('*').order('created_at', { ascending: false }));
        fermerFeuille(); toast(p ? 'Modifications enregistrées' : 'Ajouté au planning'); apres();
      } catch (err) { erreur(err); }
    });
  });
}

// Suivi financier des activités : budget, dépenses, recettes, participations
async function pageSuiviActivites(vues) {
  S.projets = await q(sb.from('projects').select('*').order('date_debut', { ascending: false }));
  const finances = peut('consulter_finances');
  const [txs, budgets, coll] = await Promise.all([
    finances ? q(sb.from('transactions').select('*').not('project_id', 'is', null)) : [],
    finances ? q(sb.from('budgets').select('*').not('project_id', 'is', null)) : [],
    q(sb.from('v_collectes').select('*')),
  ]);
  const chiffres = (p) => {
    const t = txs.filter((x) => x.project_id === p.id);
    const dep = t.filter((x) => x.sens === 'depense').reduce((s, x) => s + Number(x.montant), 0);
    const rec = t.filter((x) => x.sens === 'recette').reduce((s, x) => s + Number(x.montant), 0);
    const prevu = budgets.filter((b) => b.project_id === p.id && S.categories.find((c) => c.id === b.category_id)?.sens === 'depense').reduce((s, b) => s + Number(b.montant_prevu), 0);
    return { dep, rec, prevu };
  };
  const activites = S.projets.filter((p) => p.type !== 'evenement' || coll.some((c) => c.project_id === p.id));
  rendre(`<div class="page">
    <div class="page-titre"><h1>Planning</h1></div>
    <div class="onglets" role="tablist">${vues.map(([k, l]) => `<button role="tab" aria-selected="${k === 'liste'}" data-vue="${k}">${l}</button>`).join('')}</div>
    ${activites.length ? `<div class="grille grille-2">${activites.map((p) => {
      const c = chiffres(p), co = coll.filter((x) => x.project_id === p.id);
      return `<article class="carte">
        <div style="display:flex;gap:8px;align-items:flex-start"><h2 style="flex:1">${esc(p.nom)}</h2>${p.visible_adherents ? '<span class="puce puce-ok">Tous les membres</span>' : '<span class="puce puce-neutre">Bureau</span>'}</div>
        <span class="muted">${p.date_debut ? dateFr(p.date_debut) : 'Date à fixer'}${p.date_fin && p.date_fin !== p.date_debut ? ' au ' + dateFr(p.date_fin) : ''}${p.lieu ? ' · ' + esc(p.lieu) : ''}</span>
        ${finances ? `<div class="kpis"><div class="carte" style="background:var(--fond)"><span class="muted">Budget</span><b class="num">${eur(c.prevu)}</b></div>
          <div class="carte" style="background:var(--fond)"><span class="muted">Dépensé</span><b class="num depense">${eur(c.dep)}</b></div>
          <div class="carte" style="background:var(--fond)"><span class="muted">Recettes</span><b class="num recette">${eur(c.rec)}</b></div></div>` : ''}
        ${c.prevu && c.dep > c.prevu ? '<div class="alerte">Budget de l’activité dépassé</div>' : ''}
        ${co.map((x) => `<button class="lien-collecte" data-col="${x.id}">${esc(x.nom)} : ${eur(x.total_recu)} reçus, ${x.nb_contributeurs} contributeur${x.nb_contributeurs > 1 ? 's' : ''}</button>`).join('')}
        ${peut('gerer_activites') ? `<div class="actions">${co.length ? '' : `<button class="btn-texte" data-part="${p.id}">Demander une participation</button>`}<button class="btn-tonal" data-modif-act="${p.id}">Modifier</button></div>` : ''}
      </article>`;
    }).join('')}</div>` : `<div class="carte vide">Aucune activité${peut('gerer_activites') ? '<button class="btn-primaire" id="b-act-vide">Nouvelle activité</button>' : ''}</div>`}
    ${peut('gerer_activites') ? `<button class="fab" id="b-act" aria-label="Nouvelle activité" title="Nouvelle activité"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg></button>` : ''}
  </div>`);
  const recharger = () => pageActivites().catch(erreur);
  document.querySelectorAll('[data-vue]').forEach((b) => b.addEventListener('click', () => {
    S.planning.vue = b.dataset.vue; try { localStorage.setItem('vuePlanning', S.planning.vue); } catch { /* stockage indisponible */ }
    recharger();
  }));
  ['#b-act', '#b-act-vide'].forEach((sel) => $(sel)?.addEventListener('click', () => feuilleActivite(null, recharger, { type: 'activite' })));
  document.querySelectorAll('[data-modif-act]').forEach((b) => b.addEventListener('click', () => feuilleActivite(S.projets.find((p) => p.id === b.dataset.modifAct), recharger)));
  document.querySelectorAll('[data-part]').forEach((b) => b.addEventListener('click', () => feuilleCollecte(null, recharger, [], b.dataset.part)));
  document.querySelectorAll('[data-col]').forEach((b) => b.addEventListener('click', () => detailCollecte(coll.find((c) => c.id === b.dataset.col))));
}

// ---------- Rapprochement : pointage, relevé obligatoire, période verrouillée ----------
async function pageRapprochement() {
  const historique = await q(sb.from('reconciliations').select('*').order('periode_fin', { ascending: false }));
  const nomCompte = (id) => S.comptes.find((c) => c.id === id)?.nom || '';
  const tableHist = historique.length ? `<div class="tableau-wrap"><table><thead><tr><th>Compte</th><th>Période</th><th class="droite">Solde du relevé</th><th>Relevé</th><th>Terminé le</th></tr></thead><tbody>
    ${historique.map((r) => `<tr><td>${esc(nomCompte(r.account_id))}</td><td>${dateFr(r.periode_debut)} au ${dateFr(r.periode_fin)}</td><td class="droite num">${eur(r.solde_releve)}</td>
      <td>${r.statement_path ? `<button class="btn-texte btn-petit" data-voir="${esc(r.statement_path)}" data-bucket="releves">Voir</button>` : ''}</td><td>${r.termine_le ? dateFr(String(r.termine_le).slice(0, 10)) : '<span class="puce puce-partiel">En cours</span>'}</td></tr>`).join('')}</tbody></table></div>`
    : '<p class="muted">Aucun rapprochement terminé.</p>';
  if (!peut('rapprocher')) {
    rendre(`<div class="page"><h1>Rapprochement</h1><section class="carte"><h2>Historique</h2>${tableHist}</section></div>`);
    return brancherPieces(() => {});
  }
  if (!S.rapp) S.rapp = { compte: S.comptes.find((c) => c.type === 'banque')?.id || S.comptes[0]?.id };
  const compte = S.rapp.compte;
  const dernier = historique.find((r) => r.account_id === compte && r.statut === 'termine');
  const lendemain = (d) => { const x = new Date(d + 'T12:00:00'); x.setDate(x.getDate() + 1); return x.toISOString().slice(0, 10); };
  if (!S.rapp.debut || S.rapp.compteInit !== compte) {
    S.rapp.debut = dernier ? lendemain(dernier.periode_fin) : `${new Date().getFullYear()}-01-01`;
    S.rapp.fin = aujourdhui(); S.rapp.compteInit = compte; S.rapp.coches = null;
  }
  const [txs, pointe] = await Promise.all([
    q(sb.from('transactions').select('*').eq('account_id', compte).lte('date_op', S.rapp.fin).order('date_op')),
    q(sb.rpc('solde_pointe', { p_compte: compte })),
  ]);
  const aPointer = txs.filter((t) => !t.reconciliation_id);
  if (!S.rapp.coches) S.rapp.coches = new Set(aPointer.map((t) => t.id));
  const signe = (t) => (t.sens === 'recette' ? 1 : -1) * Number(t.montant);
  const calcul = () => {
    const pointeTotal = Number(pointe) + aPointer.filter((t) => S.rapp.coches.has(t.id)).reduce((s, t) => s + signe(t), 0);
    const releve = S.rapp.solde === undefined || S.rapp.solde === '' ? null : Number(S.rapp.solde);
    return { pointeTotal, ecart: releve === null ? null : Math.round((releve - pointeTotal) * 100) / 100 };
  };

  rendre(`<div class="page">
    <h1>Rapprochement</h1>
    <section class="carte">
      <div class="champs champs-2">
        <label class="champ">Compte<select id="r-compte">${S.comptes.map((c) => `<option value="${c.id}" ${c.id === compte ? 'selected' : ''}>${esc(c.nom)}</option>`).join('')}</select></label>
        <label class="champ"><span class="obligatoire">Solde du relevé à la fin (€)</span><input id="r-solde" type="number" step="0.01" value="${S.rapp.solde ?? ''}"></label>
        <label class="champ">Début<input id="r-debut" type="date" value="${S.rapp.debut}"></label>
        <label class="champ">Fin<input id="r-fin" type="date" value="${S.rapp.fin}"></label>
      </div>
      <label class="champ"><span class="obligatoire">${S.comptes.find((c) => c.id === compte)?.type === 'caisse' ? 'Procès-verbal de comptage' : 'Relevé de la période'}</span>
        <input id="r-releve" type="file" accept="application/pdf,image/*"></label>
      ${S.rapp.fichier ? `<span class="puce puce-ok" style="align-self:flex-start">${esc(S.rapp.fichier.name)}</span>` : ''}
    </section>
    <section class="carte">
      <h2>Écritures à pointer</h2>
      ${aPointer.length ? `<ul class="liste">${aPointer.map((t) => `<li><label class="case" style="flex:1;min-width:0"><input type="checkbox" data-coche="${t.id}" ${S.rapp.coches.has(t.id) ? 'checked' : ''}>
        <span class="corps"><b>${esc(t.libelle)}</b><span>${dateFr(t.date_op)}${t.date_op < S.rapp.debut ? ' · période précédente' : ''}</span></span></label>${montantSigne(t)}</li>`).join('')}</ul>`
        : '<p class="muted">Tout est rapproché.</p>'}
    </section>
    <section class="carte" id="r-bilan"></section>
    <section class="carte"><h2>Historique</h2>${tableHist}</section>
  </div>`);

  const bilan = () => {
    const { pointeTotal, ecart } = calcul();
    const pret = ecart === 0 && S.rapp.fichier;
    $('#r-bilan').innerHTML = `<div class="kpis">
        <div class="carte" style="background:var(--fond)"><span class="muted">Relevé</span><b class="num">${S.rapp.solde !== undefined && S.rapp.solde !== '' ? eur(S.rapp.solde) : '–'}</b></div>
        <div class="carte" style="background:var(--fond)"><span class="muted">Pointé</span><b class="num">${eur(pointeTotal)}</b></div>
        <div class="carte" style="background:${ecart === 0 ? 'var(--bleu-clair)' : 'var(--jaune-clair)'}"><span class="muted">Écart</span><b class="num">${ecart === null ? '–' : eur(ecart)}</b></div></div>
      <p class="muted">${!S.rapp.fichier ? 'Joignez le relevé pour terminer.' : ecart === null ? 'Saisissez le solde du relevé.' : ecart !== 0 ? 'Écart non nul : cochez ou décochez des écritures, ou saisissez celle qui manque.' : 'Écart nul. La période sera verrouillée.'}</p>
      <div class="actions"><button class="btn-primaire" id="r-terminer" ${pret ? '' : 'disabled'}>Terminer le rapprochement</button></div>`;
    $('#r-terminer').addEventListener('click', terminer);
  };
  const terminer = async (e) => {
    e.target.disabled = true;
    try {
      const fi = await preparerFichier(S.rapp.fichier);
      const chemin = `${compte}/${S.rapp.fin}-${Date.now()}.${fi.ext}`;
      await q(sb.storage.from('releves').upload(chemin, fi.blob, { contentType: fi.mime }));
      await q(sb.rpc('terminer_rapprochement', { p_compte: compte, p_debut: S.rapp.debut, p_fin: S.rapp.fin, p_solde_releve: Number(S.rapp.solde),
        p_chemin: chemin, p_nom: S.rapp.fichier.name, p_ko: fi.ko, p_ecritures: [...S.rapp.coches] }));
      toast('Rapprochement terminé, période verrouillée'); S.rapp = null; pageRapprochement().catch(erreur);
    } catch (err) { e.target.disabled = false; erreur(err); }
  };
  bilan();
  $('#r-compte').addEventListener('change', (e) => { S.rapp = { compte: e.target.value }; pageRapprochement().catch(erreur); });
  $('#r-solde').addEventListener('input', (e) => { S.rapp.solde = e.target.value; bilan(); });
  $('#r-debut').addEventListener('change', (e) => { S.rapp.debut = e.target.value; pageRapprochement().catch(erreur); });
  $('#r-fin').addEventListener('change', (e) => { S.rapp.fin = e.target.value; S.rapp.coches = null; pageRapprochement().catch(erreur); });
  $('#r-releve').addEventListener('change', (e) => { S.rapp.fichier = e.target.files[0] || null; bilan(); });
  document.querySelectorAll('[data-coche]').forEach((c) => c.addEventListener('change', () => { if (c.checked) S.rapp.coches.add(c.dataset.coche); else S.rapp.coches.delete(c.dataset.coche); bilan(); }));
  brancherPieces(() => {});
}

// ---------- Rapports : synthèse pour l'assemblée générale, rapport périodique, exports ----------
async function pageRapports() {
  const an = new Date().getFullYear();
  rendre(`<div class="page"><h1>Rapports</h1>
    <div class="grille grille-2">
      <section class="carte"><h2>Rapport d’assemblée générale</h2>
        <label class="champ">Exercice<select id="ag-an">${[an, an - 1, an - 2].map((a) => `<option>${a}</option>`).join('')}</select></label>
        <button class="btn-primaire" id="b-ag">Exporter en PDF</button></section>
      <section class="carte"><h2>Rapport périodique</h2>
        <div class="champs champs-2"><label class="champ">Du<input type="date" id="rp-debut" value="${aujourdhui().slice(0, 8)}01"></label>
          <label class="champ">Au<input type="date" id="rp-fin" value="${aujourdhui()}"></label></div>
        <button class="btn-primaire" id="b-rp">Exporter en PDF</button></section>
      <section class="carte"><h2>Exports Excel</h2>
        <div class="filtres">
          <button class="btn-bleu btn-petit" data-export="ecritures">Écritures ${an}</button>
          <button class="btn-bleu btn-petit" data-export="cotisations">Cotisations ${an}</button>
          <button class="btn-bleu btn-petit" data-export="participations">Participations</button>
          <button class="btn-bleu btn-petit" data-export="budget">Budget ${an}</button>
          <button class="btn-bleu btn-petit" data-export="demandes">Demandes de dépense</button>
        </div></section>
      ${peut('administrer') ? `<section class="carte"><h2>Sauvegarde complète</h2>
        <button class="btn-tonal" id="b-sauve">Télécharger la sauvegarde</button></section>` : ''}
    </div></div>`);
  $('#b-ag').addEventListener('click', () => { const a = Number($('#ag-an').value); imprimerRapport(`${a}-01-01`, `${a}-12-31`, `Rapport financier de l’exercice ${a}`); });
  $('#b-rp').addEventListener('click', () => {
    const d = $('#rp-debut').value, f = $('#rp-fin').value;
    if (!d || !f || f < d) return toast('Période invalide : la fin doit suivre le début');
    imprimerRapport(d, f, `Rapport de trésorerie du ${dateFr(d)} au ${dateFr(f)}`);
  });
  document.querySelectorAll('[data-export]').forEach((b) => b.addEventListener('click', () => exporter(b.dataset.export, an).catch(erreur)));
  $('#b-sauve')?.addEventListener('click', () => sauvegarder().catch(erreur));
}

async function exporter(type, an) {
  if (type === 'ecritures') {
    const l = await q(sb.from('transactions').select('*').gte('date_op', `${an}-01-01`).lte('date_op', `${an}-12-31`).order('date_op'));
    return telechargerCsv(`ecritures-${an}.csv`, ['Date', 'Sens', 'Libellé', 'Tiers', 'Rubrique', 'Catégorie', 'Activité', 'Compte', 'Mode', 'Montant', 'Rapprochée'],
      l.map((t) => [dateFr(t.date_op), t.sens === 'recette' ? 'Recette' : 'Dépense', t.libelle, nomTiers(t), nomRubrique(t), nomCategorie(t.category_id), nomProjet(t.project_id),
        S.comptes.find((c) => c.id === t.account_id)?.nom, MODES[t.mode], signe(t), t.rapproche ? 'Oui' : 'Non']));
  }
  if (type === 'cotisations') {
    const l = await q(sb.from('v_cotisations').select('*').eq('annee', an));
    return telechargerCsv(`cotisations-${an}.csv`, ['Prénom', 'Nom', 'Dû sur l’année', 'Réglé', 'Exigible', 'Retard', 'Réglé jusqu’à'], l.map((c) => {
      const m = S.membres.find((x) => x.id === c.member_id) || {};
      return [m.prenom, m.nom, Number(c.montant_du), Number(c.montant_paye), Number(c.exigible || 0), Number(c.retard), c.regle_jusqu_a ? nomPeriode(c.regle_jusqu_a) : ''];
    }));
  }
  if (type === 'participations') {
    const [l, txs] = await Promise.all([q(sb.from('v_collectes').select('*')), q(sb.from('transactions').select('*').not('collecte_id', 'is', null))]);
    return telechargerCsv(`participations-${an}.csv`, ['Collecte', 'Activité', 'Date', 'Tiers', 'Montant'],
      txs.sort((a, b) => (a.date_op > b.date_op ? 1 : -1)).map((t) => [l.find((c) => c.id === t.collecte_id)?.nom, nomProjet(t.project_id), dateFr(t.date_op), nomTiers(t), Number(t.montant)]));
  }
  if (type === 'budget') {
    const l = await q(sb.from('v_budget_suivi').select('*').eq('annee', an));
    return telechargerCsv(`budget-${an}.csv`, ['Poste', 'Type', 'Activité', 'Prévu', 'Réalisé', 'Écart', 'Taux %'],
      l.map((s) => [s.categorie, s.sens === 'recette' ? 'Ressource' : 'Emploi', nomProjet(s.project_id), Number(s.montant_prevu), Number(s.realise), Number(s.ecart), Number(s.taux_pct || 0)]));
  }
  const l = await q(sb.from('expense_requests').select('*').order('created_at'));
  telechargerCsv('demandes-de-depense.csv', ['Date', 'Objet', 'Catégorie', 'Activité', 'Montant', 'Statut', 'Validée le', 'Payée le', 'Empreinte signature'],
    l.map((d) => [dateFr(String(d.created_at).slice(0, 10)), d.objet, nomCategorie(d.category_id), nomProjet(d.project_id), Number(d.montant), STATUTS[d.statut][1],
      d.validee_le ? dateFr(String(d.validee_le).slice(0, 10)) : '', d.payee_le ? dateFr(String(d.payee_le).slice(0, 10)) : '', d.signature_hash || '']));
}

async function sauvegarder() {
  const tables = ['organisation', 'settings', 'accounts', 'categories', 'projects', 'budgets', 'members', 'cotisations', 'tiers', 'collectes', 'collecte_membres', 'transactions', 'expense_requests', 'attachments', 'reconciliations', 'profiles', 'invitations'];
  const donnees = { format: 'tresorerie-jp-v1', exporte_le: new Date().toISOString() };
  for (const t of tables) donnees[t] = await q(sb.from(t).select('*'));
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([JSON.stringify(donnees, null, 1)], { type: 'application/json' }));
  a.download = `sauvegarde-tresorerie-${aujourdhui()}.json`; a.click();
  toast('Sauvegarde téléchargée');
}

// Rapport périodique synthétique pour l'assemblée générale : l'essentiel en chiffres,
// faits marquants, graphiques utiles, contrôle interne, signatures. Imprimable en A4.
async function imprimerRapport(debut, fin, titre) {
  try {
    if (fin > aujourdhui()) fin = aujourdhui();   // un solde futur n'a pas de sens
    const an = Number(fin.slice(0, 4));
    const jours = Math.round((new Date(fin) - new Date(debut)) / 864e5) + 1;
    const finPrec = isoLocal(new Date(new Date(debut + 'T12:00:00').getTime() - 864e5));
    const debutPrec = isoLocal(new Date(new Date(debut + 'T12:00:00').getTime() - jours * 864e5));
    const [txs, cotis, budget, demandes, rapps, collectes, comptesTous] = await Promise.all([
      q(sb.from('transactions').select('*').lte('date_op', fin)),
      q(sb.from('v_cotisations').select('*').eq('annee', an)),
      q(sb.from('v_budget_suivi').select('*').eq('annee', an)),
      q(sb.from('expense_requests').select('*')),
      q(sb.from('reconciliations').select('*').gte('periode_fin', debut).lte('periode_fin', fin)),
      q(sb.from('v_collectes').select('*')),
      q(sb.from('accounts').select('*')),
    ]);
    const pieces = await q(sb.from('attachments').select('*'));
    const comptes = comptesTous.filter((c) => c.actif || txs.some((t) => t.account_id === c.id));
    const dans = txs.filter((t) => t.date_op >= debut);
    const prec = txs.filter((t) => t.date_op >= debutPrec && t.date_op <= finPrec);
    const somme = (l, sens) => l.filter((t) => t.sens === sens).reduce((s, t) => s + Number(t.montant), 0);
    const totR = somme(dans, 'recette'), totD = somme(dans, 'depense');
    const precR = somme(prec, 'recette'), precD = somme(prec, 'depense');
    const soldeAu = (date, avant) => comptes.reduce((s, c) => s + Number(c.solde_initial || 0), 0)
      + txs.filter((t) => (avant ? t.date_op < date : t.date_op <= date)).reduce((s, t) => s + signe(t), 0);
    const soldeDebut = soldeAu(debut, true), soldeFin = soldeAu(fin, false);
    const serie = serieMensuelle(txs, comptes, debut, fin);
    const serie12 = serieMensuelle(txs, comptes, isoLocal(new Date(new Date(fin).getFullYear(), new Date(fin).getMonth() - 11, 1, 12)), fin);
    const reserve = reserveEnMois(soldeFin, serie12);
    const parCat = (sens) => {
      const m = {}; dans.filter((t) => t.sens === sens).forEach((t) => { m[t.category_id] = (m[t.category_id] || 0) + Number(t.montant); });
      return Object.entries(m).map(([id, v]) => ({ nom: nomCategorie(id), valeur: v }));
    };
    const rec = parCat('recette'), dep = parCat('depense');
    const pct = (a, b) => (b > 0 ? Math.round(100 * (a - b) / b) : null);
    const variation = (a, b, hausseBonne) => {
      const p = pct(a, b);
      if (p == null) return '';
      const bon = p === 0 ? null : (p > 0) === hausseBonne;
      return `<span class="var ${bon == null ? '' : bon ? 'var-bon' : 'var-mauvais'}">${p > 0 ? '+' : p < 0 ? '−' : ''}${Math.abs(p)}&nbsp;%</span>`;
    };
    // Cotisations : exigible à la fin de la période
    const exigible = cotis.reduce((s, c) => s + Number(c.exigible || 0), 0);
    const encaisse = cotis.reduce((s, c) => s + Number(c.montant_paye), 0);
    const enRetard = cotis.filter((c) => Number(c.retard) > 0.005);
    // Participations de la période
    const parts = dans.filter((t) => t.collecte_id);
    const partsCol = collectes.map((c) => { const l = parts.filter((t) => t.collecte_id === c.id); return { c, recu: l.reduce((s, t) => s + Number(t.montant), 0), n: new Set(l.map((t) => t.member_id || t.tiers_id || t.id)).size }; }).filter((x) => x.recu);
    // Contrôle interne
    const demP = demandes.filter((d) => d.payee_le && String(d.payee_le).slice(0, 10) >= debut && String(d.payee_le).slice(0, 10) <= fin);
    const sansJustif = demP.filter((d) => d.statut === 'payee');
    const contrepassees = new Set(txs.filter((t) => t.contrepasse_de).map((t) => t.contrepasse_de));
    const depSansPiece = dans.filter((t) => t.sens === 'depense' && t.montant > 0 && !t.contrepasse_de && !contrepassees.has(t.id)
      && !pieces.some((p) => p.transaction_id === t.id || (t.request_id && p.request_id === t.request_id)));
    const depasses = budget.filter((b) => b.sens === 'depense' && Number(b.realise) > Number(b.montant_prevu) && Number(b.montant_prevu) > 0);
    // Faits marquants : seulement ce qui est significatif
    const premierDep = [...dep].sort((a, b) => b.valeur - a.valeur)[0];
    const premiereRec = [...rec].sort((a, b) => b.valeur - a.valeur)[0];
    const faits = [
      `Résultat ${totR - totD >= 0 ? 'excédentaire' : 'déficitaire'} de <b>${eur(Math.abs(totR - totD))}</b> : ${eur(totR)} de recettes pour ${eur(totD)} de dépenses.`,
      pct(totD, precD) != null && Math.abs(pct(totD, precD)) >= 10 ? `Dépenses en ${totD > precD ? 'hausse' : 'baisse'} de ${Math.abs(pct(totD, precD))}&nbsp;% par rapport à la période précédente de même durée.` : '',
      premierDep ? `Premier poste de dépense : <b>${esc(premierDep.nom)}</b>, ${Math.round(100 * premierDep.valeur / (totD || 1))}&nbsp;% des dépenses.` : '',
      premiereRec ? `Première ressource : <b>${esc(premiereRec.nom)}</b>, ${Math.round(100 * premiereRec.valeur / (totR || 1))}&nbsp;% des recettes.` : '',
      exigible > 0 ? `Cotisations : ${Math.round(100 * encaisse / exigible)}&nbsp;% de l’exigible encaissé ; ${enRetard.length} membre${enRetard.length > 1 ? 's' : ''} en retard pour ${eur(enRetard.reduce((s, c) => s + Number(c.retard), 0))}.` : '',
      reserve != null ? `La trésorerie couvre <b>${reserve.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} mois</b> de dépenses courantes.` : '',
      depasses.length ? `Budget dépassé sur ${depasses.length} poste${depasses.length > 1 ? 's' : ''} : ${depasses.map((b) => esc(b.categorie)).join(', ')}.` : '',
      sansJustif.length ? `<b>${sansJustif.length} dépense${sansJustif.length > 1 ? 's' : ''} payée${sansJustif.length > 1 ? 's' : ''} sans justificatif</b> à ce jour.` : '',
    ].filter(Boolean);
    const tuile = (lib, val, sous = '') => `<div class="tuile"><span>${lib}</span><b>${val}</b>${sous ? `<small>${sous}</small>` : ''}</div>`;
    const libMois = serie.map((x) => libelleMois(x.mois, serie.length > 12));
    const ligneT = (l, m) => `<tr><td>${esc(l)}</td><td class="d">${eur(m)}</td></tr>`;
    const budgetLignes = budget.filter((b) => !b.project_id).map((b) => ({ nom: b.categorie, prevu: Number(b.montant_prevu), realise: Number(b.realise), sens: b.sens }));
    const html = `
<div class="ent"><img src="${esc(new URL(S.logoUrl, location.href).href)}" alt=""><div><h1>${esc(S.org?.nom || '')}</h1><b>${esc(titre)}</b>
  <div class="muted">Du ${dateFr(debut)} au ${dateFr(fin)} · édité le ${dateFr(aujourdhui())} par ${esc(S.profil.nom)}</div></div></div>

<h2>L’essentiel</h2>
<div class="tuiles">
  ${tuile('Trésorerie au ' + dateFr(fin), eur0(soldeFin), `${soldeFin >= soldeDebut ? '+' : '−'} ${eur0(Math.abs(soldeFin - soldeDebut))} sur la période`)}
  ${tuile('Recettes', eur0(totR), variation(totR, precR, true) ? variation(totR, precR, true) + ' vs période précédente' : '')}
  ${tuile('Dépenses', eur0(totD), variation(totD, precD, false) ? variation(totD, precD, false) + ' vs période précédente' : '')}
  ${tuile('Résultat', `<span style="color:${totR - totD >= 0 ? C.recette : C.alerte}">${totR - totD >= 0 ? '+' : '−'} ${eur0(Math.abs(totR - totD))}</span>`, totR - totD >= 0 ? 'excédent' : 'déficit')}
  ${reserve != null ? tuile('Réserve', `${reserve.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} mois`, 'de dépenses couvertes') : ''}
</div>
<h3>Faits marquants</h3><ul class="faits">${faits.map((f) => `<li>${f}</li>`).join('')}</ul>

${serie.length >= 2 ? `<div class="deux bloc">
  ${colonnesGroupees({ titre: 'Recettes et dépenses par mois', libelles: libMois, series: [{ nom: 'Recettes', couleur: C.recette, valeurs: serie.map((x) => x.rec) }, { nom: 'Dépenses', couleur: C.depense, valeurs: serie.map((x) => x.dep) }] })}
  ${ligne({ titre: 'Trésorerie en fin de mois', libelles: libMois, valeurs: serie.map((x) => x.solde) })}
</div>` : ''}

<div class="deux bloc">
  ${anneau({ titre: 'Origine des recettes', items: rec, sens: 'recette' })}
  ${anneau({ titre: 'Destination des dépenses', items: dep, sens: 'depense' })}
</div>

<h2>Trésorerie par compte</h2>
<table><tr><th>Compte</th><th class="d">Au ${dateFr(debut)}</th><th class="d">Recettes</th><th class="d">Dépenses</th><th class="d">Au ${dateFr(fin)}</th></tr>
${comptes.map((c) => { const t = dans.filter((x) => x.account_id === c.id); const sd = Number(c.solde_initial || 0) + txs.filter((x) => x.account_id === c.id && x.date_op < debut).reduce((s, x) => s + signe(x), 0);
  return `<tr><td>${esc(c.nom)}</td><td class="d">${eur(sd)}</td><td class="d">${eur(somme(t, 'recette'))}</td><td class="d">${eur(somme(t, 'depense'))}</td><td class="d">${eur(sd + t.reduce((s, x) => s + signe(x), 0))}</td></tr>`; }).join('')}
<tr class="tot"><td>Total</td><td class="d">${eur(soldeDebut)}</td><td class="d">${eur(totR)}</td><td class="d">${eur(totD)}</td><td class="d">${eur(soldeFin)}</td></tr></table>

<div class="deux bloc">
  <div><h2>Cotisations ${an}</h2>
    ${exigible > 0 ? jauge({ titre: 'Encaissé sur l’exigible', valeur: encaisse, cible: exigible, detail: `${eur(encaisse)} sur ${eur(exigible)} · ${cotis.length - enRetard.length} membres à jour sur ${cotis.length}` }) : '<p class="muted">Aucune cotisation exigible</p>'}
    ${enRetard.length ? `<p class="muted" style="margin-top:8px">Retard total : ${eur(enRetard.reduce((s, c) => s + Number(c.retard), 0))}</p>` : ''}</div>
  <div><h2>Participations aux activités</h2>
    ${partsCol.length ? `<table><tr><th>Collecte</th><th class="d">Reçu</th><th class="d">Donateurs</th></tr>${partsCol.map(({ c, recu, n }) => `<tr><td>${esc(c.nom)}</td><td class="d">${eur(recu)}${c.objectif ? ` / ${eur0(c.objectif)}` : ''}</td><td class="d">${n}</td></tr>`).join('')}</table>` : '<p class="muted">Aucune participation sur la période</p>'}</div>
</div>

${budgetLignes.length ? `<h2>Budget ${an} : réalisé sur prévu</h2>
<div class="deux">${budgetBarres({ titre: 'Dépenses (emplois)', lignes: budgetLignes.filter((b) => b.sens === 'depense') })}${budgetBarres({ titre: 'Recettes (ressources)', lignes: budgetLignes.filter((b) => b.sens === 'recette') })}</div>` : ''}

<h2>Contrôle interne</h2>
<table>
<tr><td>Dépenses payées sur demande validée</td><td class="d">${demP.length} · ${eur(demP.reduce((s, d) => s + Number(d.montant), 0))}</td></tr>
<tr><td>Dont sans justificatif à ce jour</td><td class="d">${sansJustif.length ? `<b>${sansJustif.length}</b>` : '0'}</td></tr>
<tr><td>Dépenses sans pièce jointe</td><td class="d">${depSansPiece.length ? `<b>${depSansPiece.length} · ${eur(depSansPiece.reduce((s, t) => s + Number(t.montant), 0))}</b>` : '0'}</td></tr>
<tr><td>Rapprochements terminés (relevé joint, écart nul)</td><td class="d">${rapps.length ? rapps.map((r) => `${esc(comptes.find((c) => c.id === r.account_id)?.nom || '')} au ${dateFr(r.periode_fin)}`).join(', ') : '<b>aucun</b>'}</td></tr>
</table>

<div class="sig"><div>Le trésorier</div><div>Le président</div></div>`;
    let v = $('#rapport');
    if (!v) { v = document.createElement('div'); v.id = 'rapport'; document.body.appendChild(v); }
    v.innerHTML = `<div class="rapport-barre"><b>${esc(titre)}</b><span><button class="btn-primaire btn-petit" id="rp-imprimer">Imprimer ou PDF</button> <button class="btn-tonal btn-petit" id="rp-fermer">Fermer</button></span></div>
      <div class="rapport-page">${html}</div>`;
    v.hidden = false; document.body.classList.add('avec-rapport');
    brancherInfobulles(v);
    $('#rp-fermer').addEventListener('click', () => { v.hidden = true; document.body.classList.remove('avec-rapport'); });
    $('#rp-imprimer').addEventListener('click', () => window.print());
  } catch (e) { erreur(e); }
}

demarrer().catch((e) => { erreur(e); $('#app').innerHTML = `<p class="chargement">Impossible de démarrer : ${esc(e.message)}</p>`; });
