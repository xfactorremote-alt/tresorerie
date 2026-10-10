// Client de démonstration : imite le client Supabase avec des données fictives en mémoire.
// Chargé seulement quand config.js n'est pas rempli (ou avec ?demo dans l'adresse).
// Les règles de la vraie base (rôles, circuit, rapprochement) sont reproduites pour que
// la démonstration se comporte comme la version en ligne.
export const MOT_DE_PASSE = 'Demo2026';
export const COMPTES_DEMO = [['Trésorier', 'tresorier@demo.jp'], ['Président', 'president@demo.jp'], ['Bureau', 'bureau@demo.jp'], ['Adhérent', 'adherent@demo.jp'], ['Nouveau membre (sans fiche)', 'nouveau@demo.jp']];
const uid = () => (crypto.randomUUID ? crypto.randomUUID() : String(Math.random()).slice(2));
const an = new Date().getFullYear();
const moisCourant = new Date().getMonth() + 1;
const iso = (d) => d.toISOString().slice(0, 10);
const ilYa = (j) => { const d = new Date(); d.setDate(d.getDate() - j); return d; };
const dansJours = (j) => iso(ilYa(-j));
const signe = (x) => (x.sens === 'recette' ? 1 : -1) * Number(x.montant);

// Document fictif affiché quand on ouvre une pièce de la démonstration
const DOC_DEMO = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800"><rect width="600" height="800" fill="#fff"/><rect x="40" y="40" width="520" height="720" fill="none" stroke="#C23E10" stroke-width="4" rx="24"/><text x="300" y="380" font-family="sans-serif" font-size="28" text-anchor="middle" fill="#1C1B1A">Document de démonstration</text><text x="300" y="420" font-family="sans-serif" font-size="18" text-anchor="middle" fill="#5A5350">Facture, relevé ou signature fictive</text></svg>');

function seed() {
  const ids = { t: 'u-tresorier', p: 'u-president', b: 'u-bureau', a: 'u-adherent', n: 'u-nouveau' };
  const cat = (nom, sens) => ({ id: uid(), nom, sens, parent_id: null });
  const categories = [
    cat('Cotisations', 'recette'), cat('Dons', 'recette'), cat('Offrandes dédiées', 'recette'), cat('Activités / événements', 'recette'), cat('Autres recettes', 'recette'),
    cat('Fonctionnement', 'depense'), cat('Activités / événements', 'depense'), cat('Aides et solidarité', 'depense'), cat('Matériel', 'depense'), cat('Autres dépenses', 'depense'),
    { ...cat('Virement interne', 'recette'), interne: true }, { ...cat('Virement interne', 'depense'), interne: true },
  ];
  const C = (nom, sens) => categories.find((c) => c.nom === nom && c.sens === sens).id;
  const accounts = [
    { id: 'acc-caisse', nom: 'Caisse (espèces)', type: 'caisse', solde_initial: 412.5, actif: true },
    { id: 'acc-banque', nom: 'Banque', type: 'banque', solde_initial: 2860, actif: true },
  ];
  const noms = [
    ['Grâce', 'Mbala', 5, moisCourant, 'Infirmière', true], ['Daniel', 'Kouassi', 12, moisCourant, 'Électricien', true],
    ['Esther', 'Nzeyimana', 27, moisCourant, null, false], ['Samuel', 'Okafor', 3, 2, 'Étudiant', true],
    ['Ruth', 'Diallo', 19, 7, 'Comptable', true], ['Jonathan', 'Mabiala', 8, 11, null, true],
    ['Déborah', 'Tshibangu', 30, 4, 'Aide-soignante', true], ['Élie', 'Bamba', 14, 9, 'Chauffeur', false],
    ['Naomie', 'Kabongo', 22, 1, null, true], ['Josué', 'Mensah', 1, 6, 'Ingénieur', true],
    ['Jean-Marc', 'Ilunga', 17, 3, 'Enseignant', true], ['Marthe', 'Kalala', 9, 12, 'Secrétaire médicale', true],
    ['Paul', 'Ndongo', 25, 5, 'Gestionnaire de paie', true],
  ];
  const members = noms.map(([prenom, nom, j, m, profession, consent], i) => ({
    id: 'm' + i, prenom, nom, naissance_jour: j, naissance_mois: m, profession, whatsapp: i % 3 ? '06 12 34 56 7' + i : null,
    email: null, photo_path: null, consent_anniversaire: consent, date_adhesion: `${an - 1}-09-01`, actif: true, created_at: ilYa(i === 8 ? 2 : 300).toISOString(),
  }));
  members[9].date_adhesion = `${an}-03-15`;   // adhésion en cours d'année : cotisations à partir de mars
  // Cotisation mensuelle : une ligne par membre et par mois
  const cotisations = [];
  members.forEach((m) => {
    for (let mo = 1; mo <= 12; mo++) {
      const periode = `${an}-${String(mo).padStart(2, '0')}-01`;
      const fin = new Date(an, mo, 1);
      if (!m.date_adhesion || new Date(m.date_adhesion) < fin) cotisations.push({ id: uid(), member_id: m.id, periode, montant_du: 20 });
    }
  });
  const tiers = [
    { id: 'ti1', nom: 'Thomann', type: 'fournisseur' }, { id: 'ti2', nom: 'Boulangerie du Lac', type: 'fournisseur' },
    { id: 'ti3', nom: 'Paroisse Saint-Bruno', type: 'partenaire' }, { id: 'ti4', nom: 'M. et Mme Lefèvre', type: 'donateur' },
  ].map((x) => ({ telephone: null, email: null, notes: null, actif: true, created_at: new Date().toISOString(), ...x }));
  const P = (id, nom, type, debut, heure_debut, heure_fin, lieu, visible, description, fin = debut) =>
    ({ id, nom, type, date_debut: debut, date_fin: fin, heure_debut, heure_fin, lieu, visible_adherents: visible, description, created_at: ilYa(60).toISOString() });
  const projects = [
    P('p1', 'Sortie des jeunes', 'activite', dansJours(12), '09:00', '18:00', 'Lac de Paladru', true, 'Journée au lac, pique-nique partagé'),
    P('p2', 'Fête de Noël', 'activite', `${an}-12-20`, '17:00', '22:00', 'Salle paroissiale', true, 'Repas partagé et spectacle des enfants'),
    P('p3', 'Achat de la sono', 'activite', null, null, null, null, false, 'Projet d’équipement'),
    P('p4', 'Réunion du bureau', 'evenement', dansJours(5), '19:30', '21:00', 'Chez le président', false, 'Ordre du jour : budget de la sortie'),
    P('p5', 'Culte des jeunes', 'evenement', dansJours(9), '15:00', '17:00', 'Église', true, null),
    P('p6', 'Répétition de la chorale', 'evenement', dansJours(2), '18:30', '20:00', 'Église', true, null),
  ];
  projects.find((x) => x.id === 'p6').created_at = ilYa(1).toISOString();   // ajoutés récemment : apparaissent dans « Nouveautés »
  projects.find((x) => x.id === 'p5').created_at = ilYa(2).toISOString();
  const collectes = [
    { id: 'co1', nom: 'Participation à la sortie des jeunes', project_id: 'p1', montant_attendu: 15, objectif: 300, date_limite: dansJours(10), tous_membres: true, cloturee: false, created_at: ilYa(20).toISOString() },
    { id: 'co2', nom: 'Repas de Noël', project_id: 'p2', montant_attendu: null, objectif: 500, date_limite: `${an}-12-15`, tous_membres: true, cloturee: false, created_at: ilYa(6).toISOString() },
  ];
  const rec0 = { id: 'rec0', account_id: 'acc-banque', periode_debut: iso(ilYa(75)), periode_fin: iso(ilYa(41)), solde_releve: 2815,
    statement_path: 'acc-banque/demo-releve.pdf', statement_name: 'releve-banque.pdf', statement_ko: 120, statut: 'termine', ecart: 0,
    termine_par: ids.t, termine_le: ilYa(38).toISOString(), created_at: ilYa(38).toISOString() };
  const tx = (j, sens, montant, nomCat, libelle, account_id, extra = {}) => ({
    id: uid(), date_op: iso(ilYa(j)), account_id, sens, montant, category_id: C(nomCat, sens), project_id: null, libelle, tiers_id: null,
    mode: account_id === 'acc-caisse' ? 'especes' : 'virement', member_id: null, est_cotisation: false, collecte_id: null, request_id: null,
    rapproche: false, reconciliation_id: null, date_rapprochement: null, contrepasse_de: null, created_by: ids.t, created_at: ilYa(j).toISOString(), ...extra,
  });
  const transactions = [
    tx(2, 'recette', 185, 'Offrandes dédiées', 'Offrande pour la sortie des jeunes', 'acc-caisse', { project_id: 'p1' }),
    tx(4, 'depense', 64.9, 'Activités / événements', 'Boissons et gobelets, fête de rentrée', 'acc-caisse'),
    tx(9, 'depense', 120, 'Matériel', 'Câbles et micro', 'acc-banque', { tiers_id: 'ti1', project_id: 'p3' }),
    tx(15, 'recette', 300, 'Dons', 'Don anonyme', 'acc-banque'),
    tx(52, 'depense', 45, 'Fonctionnement', 'Frais de tenue de compte', 'acc-banque', { mode: 'autre', rapproche: true, reconciliation_id: 'rec0', date_rapprochement: rec0.periode_fin }),
  ];
  // Versements de cotisation (imputés sur les mois les plus anciens)
  [[0, 200], [1, 100], [3, 180], [4, 240], [6, 40], [9, 140], [2, 60]].forEach(([i, montant], k) => transactions.push(tx(3 + k * 4, 'recette', montant, 'Cotisations',
    `Cotisation : ${members[i].prenom} ${members[i].nom}`, k % 2 ? 'acc-banque' : 'acc-caisse', { member_id: members[i].id, est_cotisation: true })));
  // Participations à la sortie et au repas de Noël
  [[0, 15], [1, 15], [3, 10], [4, 15], [5, 15]].forEach(([i, montant], k) => transactions.push(tx(1 + k, 'recette', montant, 'Activités / événements',
    `Participation sortie : ${members[i].prenom} ${members[i].nom}`, 'acc-caisse', { member_id: members[i].id, collecte_id: 'co1', project_id: 'p1' })));
  transactions.push(tx(6, 'recette', 50, 'Dons', 'Don pour la sortie des jeunes', 'acc-banque', { tiers_id: 'ti3', collecte_id: 'co1', project_id: 'p1' }));
  transactions.push(tx(1, 'recette', 20, 'Activités / événements', `Repas de Noël : ${members[0].prenom} ${members[0].nom}`, 'acc-caisse', { member_id: 'm0', collecte_id: 'co2', project_id: 'p2' }));
  transactions.push(tx(20, 'recette', 150, 'Dons', 'Don', 'acc-banque', { tiers_id: 'ti4' }));
  // Historique sur douze mois pour que les graphiques aient du sens (montants fixes, pas de hasard)
  const H = [[95, 32, 120, 0], [140, 28, 0, 210], [110, 35, 60, 0], [160, 41, 0, 0], [125, 30, 250, 180], [90, 26, 0, 0],
    [180, 38, 90, 0], [105, 29, 0, 320], [150, 33, 140, 0], [120, 31, 0, 0], [135, 36, 70, 150]];
  H.forEach(([offrande, fonct, activite, don], k) => {
    const j = 35 + k * 30;
    transactions.push(tx(j, 'recette', offrande, 'Offrandes dédiées', 'Offrandes du mois', 'acc-caisse'));
    transactions.push(tx(j + 2, 'depense', fonct, 'Fonctionnement', 'Fournitures et photocopies', 'acc-caisse'));
    transactions.push(tx(j + 4, 'depense', 5, 'Fonctionnement', 'Frais bancaires', 'acc-banque', { mode: 'autre' }));
    if (activite) transactions.push(tx(j + 6, 'depense', activite, 'Activités / événements', 'Activité du mois', 'acc-caisse'));
    if (don) transactions.push(tx(j + 8, 'recette', don, 'Dons', 'Don', 'acc-banque', { tiers_id: 'ti4' }));
    if (k % 3 === 1) transactions.push(tx(j + 10, 'depense', 45 + k * 5, 'Aides et solidarité', 'Aide à une famille', 'acc-caisse'));
  });
  // vus : dernière visite de chaque onglet (pastilles « nouveau ») ; le compte « nouveau » n'a pas encore de fiche
  const vu = ilYa(3).toISOString();
  const profiles = [
    { id: ids.t, nom: 'Paul Ndongo', role: 'tresorier', member_id: 'm12', actif: true, created_at: ilYa(400).toISOString(), vus: { ecritures: vu, depenses: vu, activites: vu, membres: vu, cotisations: vu } },
    { id: ids.p, nom: 'Jean-Marc Ilunga', role: 'president', member_id: 'm10', actif: true, created_at: ilYa(400).toISOString(), vus: { ecritures: vu, depenses: vu, activites: vu, membres: vu, cotisations: vu } },
    { id: ids.b, nom: 'Marthe Kalala', role: 'bureau', member_id: 'm11', actif: true, created_at: ilYa(400).toISOString(), vus: { ecritures: vu, depenses: vu, activites: ilYa(30).toISOString(), membres: vu, cotisations: vu } },
    { id: ids.a, nom: 'Grâce Mbala', role: 'adherent', member_id: 'm0', actif: true, created_at: ilYa(400).toISOString(), vus: { activites: ilYa(30).toISOString(), cotisations: ilYa(30).toISOString() } },
    { id: ids.n, nom: 'nouveau@demo.jp', role: 'adherent', member_id: null, actif: true, created_at: new Date().toISOString(), vus: {} },
  ];
  const dem = (id, demandeur, objet, montant, nomCat, statut, extra = {}) => ({
    id, demandeur, objet, montant, category_id: C(nomCat, 'depense'), project_id: null, account_id: null, statut,
    validee_par: null, validee_le: null, signature_path: null, signature_hash: null, motif_refus: null, payee_le: null, created_at: ilYa(20).toISOString(), ...extra,
  });
  const signee = (j) => ({ validee_par: ids.p, validee_le: ilYa(j).toISOString(), signature_path: 'demo-signature.png', signature_hash: 'demo' });
  const expense_requests = [
    dem('r1', ids.b, 'Décoration de la salle', 75, 'Activités / événements', 'payee', { ...signee(12), payee_le: ilYa(10).toISOString(), account_id: 'acc-caisse' }),
    dem('r2', ids.b, 'Location du car pour la sortie', 240, 'Activités / événements', 'soumise', { project_id: 'p1', created_at: ilYa(1).toISOString() }),
    dem('r3', ids.p, 'Enceinte portable', 189, 'Matériel', 'validee', { project_id: 'p3', ...signee(2), created_at: ilYa(3).toISOString() }),
    dem('r4', ids.t, 'Colis alimentaire famille Diallo', 60, 'Aides et solidarité', 'justifiee', { ...signee(30), payee_le: ilYa(29).toISOString(), account_id: 'acc-caisse', created_at: ilYa(31).toISOString() }),
  ];
  // Écritures liées aux demandes payées
  transactions.push(tx(10, 'depense', 75, 'Activités / événements', 'Décoration de la salle', 'acc-caisse', { request_id: 'r1' }));
  const t4 = tx(29, 'depense', 60, 'Aides et solidarité', 'Colis alimentaire famille Diallo', 'acc-caisse', { request_id: 'r4' });
  transactions.push(t4);
  const attachments = [{ id: 'a1', request_id: 'r4', transaction_id: t4.id, storage_path: 'demo-facture.jpg', mime: 'image/jpeg', taille_ko: 180, depose_par: ids.t }];
  const budgets = [
    ['Fonctionnement', 'depense', 300], ['Activités / événements', 'depense', 800], ['Aides et solidarité', 'depense', 500], ['Matériel', 'depense', 150],
    ['Cotisations', 'recette', 1000], ['Dons', 'recette', 1500], ['Offrandes dédiées', 'recette', 600],
  ].map(([n, s, m]) => ({ id: uid(), annee: an, category_id: C(n, s), project_id: null, montant_prevu: m, seuil_alerte_pct: 90 }));
  budgets.push({ id: uid(), annee: an, category_id: C('Activités / événements', 'depense'), project_id: 'p1', montant_prevu: 300, seuil_alerte_pct: 90 });
  budgets.push({ id: uid(), annee: an, category_id: C('Matériel', 'depense'), project_id: 'p3', montant_prevu: 400, seuil_alerte_pct: 90 });
  const permissions = [
    ['consulter_finances', 'Voir les soldes, écritures, budget et rapports', 'Finances'], ['saisir_ecritures', 'Saisir et corriger les écritures, joindre les pièces', 'Finances'],
    ['gerer_cotisations', 'Cotisations et participations : générer, encaisser, relancer', 'Finances'], ['rapprocher', 'Rapprocher la caisse et la banque', 'Finances'],
    ['gerer_budget', 'Construire et modifier le budget', 'Finances'], ['demander_depenses', 'Demander une dépense', 'Dépenses'],
    ['valider_depenses', 'Valider ou refuser une dépense (signature)', 'Dépenses'], ['payer_depenses', 'Payer une dépense validée', 'Dépenses'],
    ['voir_membres', 'Voir la liste des membres', 'Membres'], ['gerer_membres', 'Ajouter, modifier et importer des membres', 'Membres'],
    ['gerer_activites', 'Créer et modifier les activités et le planning', 'Activités'], ['administrer', 'Paramètres, rôles et accès', 'Administration'],
    ['gerer_materiel', 'Tenir l’inventaire du matériel : ajouter, prêter, sortir', 'Matériel'],
  ].map(([code, libelle, groupe], i) => ({ code, libelle, groupe, ordre: i + 1 }));
  const roles = [['tresorier', 'Trésorier'], ['president', 'Président'], ['bureau', 'Bureau'], ['adherent', 'Adhérent']].map(([code, nom]) => ({ code, nom, systeme: true }));
  const role_permissions = [
    ...permissions.filter((p) => p.code !== 'valider_depenses').map((p) => ({ role: 'tresorier', permission: p.code })),
    ...['consulter_finances', 'demander_depenses', 'valider_depenses', 'voir_membres'].map((p) => ({ role: 'president', permission: p })),
    ...['consulter_finances', 'demander_depenses', 'voir_membres'].map((p) => ({ role: 'bureau', permission: p })),
  ];
  // Inventaire du matériel
  const M = (id, designation, categorie, extra) => ({ id, designation, categorie, marque: null, numero_serie: null, quantite: 1, origine: 'achat', date_acquisition: null,
    valeur_acquisition: null, valeur_actuelle: null, etat: 'bon', lieu: 'Église, local de rangement', detenteur_id: null, transaction_id: null, photo_path: null,
    notes: null, verifie_le: null, sorti_le: null, motif_sortie: null, created_at: ilYa(200).toISOString(), ...extra });
  const materiel = [
    M('mat1', 'Guitare électro-acoustique', 'instrument', { marque: 'Yamaha APX600', numero_serie: 'HPX09123', date_acquisition: `${an - 2}-04-12`, valeur_acquisition: 320, valeur_actuelle: 220, detenteur_id: 'm1', verifie_le: iso(ilYa(60)) }),
    M('mat2', 'Clavier arrangeur', 'instrument', { marque: 'Yamaha PSR-E473', origine: 'don', date_acquisition: `${an - 1}-11-05`, valeur_acquisition: 380, valeur_actuelle: 300, notes: 'Don de la famille Lefèvre', verifie_le: iso(ilYa(60)) }),
    M('mat3', 'Batterie complète', 'instrument', { marque: 'Pearl Roadshow', date_acquisition: `${an - 4}-09-01`, valeur_acquisition: 680, valeur_actuelle: 350, etat: 'usage', lieu: 'Salle de répétition' }),
    M('mat4', 'Djembés', 'instrument', { quantite: 3, date_acquisition: `${an - 3}-06-20`, valeur_acquisition: 210, valeur_actuelle: 150, verifie_le: iso(ilYa(60)) }),
    M('mat5', 'Table de mixage 12 voies', 'sonorisation', { marque: 'Behringer Xenyx 1202', date_acquisition: `${an - 2}-01-15`, valeur_acquisition: 289, valeur_actuelle: 180 }),
    M('mat6', 'Micros sans fil', 'sonorisation', { marque: 'Shure BLX', quantite: 2, date_acquisition: iso(ilYa(9)), valeur_acquisition: 120, valeur_actuelle: 120, etat: 'neuf' }),
    M('mat7', 'Vidéoprojecteur', 'informatique', { marque: 'Epson EB-W06', origine: 'don', date_acquisition: `${an - 3}-02-10`, valeur_acquisition: 300, valeur_actuelle: 80, etat: 'a_reparer', notes: 'Lampe à remplacer' }),
    M('mat8', 'Tenues de chorale', 'textile', { quantite: 20, date_acquisition: `${an - 1}-03-01`, valeur_acquisition: 600, valeur_actuelle: 400, lieu: 'Chez la responsable de la chorale' }),
    M('mat9', 'Tables pliantes', 'mobilier', { quantite: 6, origine: 'pret', notes: 'Prêtées par la paroisse Saint-Bruno', lieu: 'Salle paroissiale' }),
  ];
  const tMicro = transactions.find((x) => x.libelle === 'Câbles et micro'); if (tMicro) materiel[5].transaction_id = tMicro.id;
  const materiel_mouvements = [
    { id: 'mv1', materiel_id: 'mat1', date_mvt: iso(ilYa(20)), type: 'pret', member_id: 'm1', notes: 'Pour les répétitions à domicile', par: 'u-tresorier', created_at: ilYa(20).toISOString() },
    { id: 'mv2', materiel_id: 'mat7', date_mvt: iso(ilYa(15)), type: 'reparation', member_id: null, notes: 'Lampe hors service', par: 'u-tresorier', created_at: ilYa(15).toISOString() },
  ];
  return {
    ids,
    tables: {
      permissions, roles, role_permissions,
      organisation: [{ id: 1, nom: 'JP Grenoble', logo_path: null, banniere_path: null, devise: 'EUR', exercice_debut: `${an}-01-01`,
        sigle: 'JP', objet: 'Rassembler et accompagner les jeunes de tous pays à Grenoble', adresse: '12 rue de la Paix', code_postal: '38000', ville: 'Grenoble',
        email: 'contact@jp-grenoble.fr', telephone: '06 12 34 56 78', site_web: null, rna: 'W381000000', siret: null, date_creation: '2015-09-01', configuree: true }],
      exercices: [
        { id: 'ex1', libelle: `Exercice ${an - 1}`, debut: `${an - 1}-01-01`, fin: `${an - 1}-12-31`, cloture: true, cloture_le: `${an}-02-15T10:00:00Z`, cloture_par: ids.t, created_at: `${an - 1}-01-02T10:00:00Z` },
        { id: 'ex2', libelle: `Exercice ${an}`, debut: `${an}-01-01`, fin: `${an}-12-31`, cloture: false, cloture_le: null, cloture_par: null, created_at: `${an}-01-02T10:00:00Z` },
      ],
      corbeille: [],
      communiques: [
        { id: 'cq1', titre: 'Assemblée générale le 15 novembre', texte: 'Tous les membres sont attendus à 15 h, salle paroissiale. Ordre du jour : bilan de l’année, budget, élection du bureau.', debut: iso(ilYa(1)), fin: null, priorite: 'haute', visible_adherents: true, created_by: ids.t, created_at: ilYa(1).toISOString() },
        { id: 'cq2', titre: 'Réunion du bureau jeudi', texte: 'Préparation de l’assemblée générale.', debut: iso(ilYa(2)), fin: null, priorite: 'normale', visible_adherents: false, created_by: ids.p, created_at: ilYa(2).toISOString() },
      ],
      settings: [
        { cle: 'banniere', valeur: null, texte: JSON.stringify({ rotation: 8, rdv: { priorite: 1, frequence: 'toujours' }, cotisation: { priorite: 2, frequence: 'jour' }, participation: { priorite: 2, frequence: 'jour' }, communique: { priorite: 1, frequence: 'toujours' } }) },
        { cle: 'cotisation_montant', valeur: 20 }, { cle: 'cotisation_periode_mois', valeur: 1 },
        { cle: 'delai_justificatif_jours', valeur: 7 }, { cle: 'seuil_alerte_budget_pct', valeur: 90 }, { cle: 'seuil_justification', valeur: 100 },
        { cle: 'infos_paiement', valeur: null, texte: 'Virement : IBAN FR76 0000 0000 0000 0000 0000 000 (démonstration)\nEspèces : auprès du trésorier après le culte' },
      ],
      categories, accounts, members, cotisations, transactions, profiles, expense_requests, invitations: [],
      projects, budgets, attachments, reconciliations: [rec0], tiers, collectes, collecte_membres: [],
      liens_membres: [{ member_id: 'm0', jeton: 'a1b2c3d4e5f60718293a4b5c6d7e8f90', code: 'k7qp2xyz9abc', cree_par: ids.t, cree_le: ilYa(2).toISOString(), nb_consultations: 3, derniere_consultation: ilYa(1).toISOString() }], materiel, materiel_mouvements,
    },
  };
}

// Droit exigé pour modifier chaque table (comme les règles RLS de la base)
const DROIT_ECRITURE = {
  members: ['gerer_membres'], budgets: ['gerer_budget'], projects: ['gerer_activites'], cotisations: ['gerer_cotisations'], reconciliations: ['rapprocher'],
  categories: ['administrer'], accounts: ['administrer'], settings: ['administrer'], organisation: ['administrer'], invitations: ['administrer'], exercices: ['administrer'], corbeille: ['__aucun'],
  profiles: ['administrer'], roles: ['administrer'], role_permissions: ['administrer'],
  materiel: ['gerer_materiel'], materiel_mouvements: ['gerer_materiel'], liens_membres: ['gerer_membres', 'gerer_cotisations'],
  tiers: ['saisir_ecritures', 'gerer_cotisations'], collectes: ['gerer_activites', 'gerer_cotisations'], collecte_membres: ['gerer_activites', 'gerer_cotisations'],
  communiques: ['gerer_activites', 'administrer'],
};

// Cotisations par période, versements imputés sur la période la plus ancienne non réglée (comme la vue de la base)
function periodesCotisation(t) {
  const aujourdhui = new Date().toISOString().slice(0, 10);
  const paye = {};
  t.transactions.filter((x) => x.est_cotisation).forEach((x) => { paye[x.member_id] = (paye[x.member_id] || 0) + Number(x.montant); });
  const parMembre = {};
  [...t.cotisations].sort((a, b) => (a.periode > b.periode ? 1 : -1)).forEach((c) => (parMembre[c.member_id] = parMembre[c.member_id] || []).push(c));
  const res = [];
  Object.entries(parMembre).forEach(([mid, liste]) => {
    let avant = 0;
    liste.forEach((c) => {
      const dispo = (paye[mid] || 0) - avant;
      const du = Number(c.montant_du);
      const regle = Math.min(du, Math.max(0, dispo));
      res.push({ member_id: mid, periode: c.periode, annee: Number(c.periode.slice(0, 4)), montant_du: du, regle,
        statut: du === 0 ? 'dispense' : dispo >= du ? 'regle' : dispo > 0 ? 'partiel' : c.periode > aujourdhui ? 'a_venir' : 'impaye' });
      avant += du;
    });
  });
  return { periodes: res, paye };
}

const t0 = (db, v) => v.some((x) => db.tables.role_permissions.some((rp) => rp.role === x.role && rp.permission === x.permission));

class Requete {
  constructor(db, table) { this.db = db; this.table = table; this.filtres = []; this.tri = []; this.op = 'select'; this.max = null; this.unique = null; this.retour = false; }
  select() { if (this.op !== 'select') this.retour = true; return this; }
  insert(v) { this.op = 'insert'; this.valeur = Array.isArray(v) ? v : [v]; return this; }
  update(v) { this.op = 'update'; this.valeur = v; return this; }
  delete() { this.op = 'delete'; return this; }
  eq(c, v) { this.filtres.push((r) => r[c] == v); return this; }
  in(c, v) { this.filtres.push((r) => v.includes(r[c])); return this; }
  neq(c, v) { this.filtres.push((r) => r[c] != v); return this; }
  gte(c, v) { this.filtres.push((r) => r[c] >= v); return this; }
  lte(c, v) { this.filtres.push((r) => r[c] <= v); return this; }
  not(c, op, v) { this.filtres.push((r) => (op === 'is' && v === null ? r[c] != null : r[c] != v)); return this; }
  order(c, o = {}) { this.tri.push([c, o.ascending !== false]); return this; }
  limit(n) { this.max = n; return this; }
  maybeSingle() { this.unique = 'maybe'; return this; }
  single() { this.unique = 'single'; return this; }
  then(ok, ko) {
    return Promise.resolve().then(() => this.executer())
      .then((r) => { if (this.op !== 'select' && !r?.error) this.db.emettre?.(this.table); return r; }).then(ok, ko);
  }
  executer() {
    try {
      const db = this.db;
      if (!db.session && !(this.table === 'organisation' && this.op === 'select')) throw new Error('Non connecté');
      const peut = (d) => db.droits().has(d);
      if (this.op !== 'select' && DROIT_ECRITURE[this.table] && !DROIT_ECRITURE[this.table].some(peut)) throw new Error('row-level security');
      if (this.op !== 'select' && this.table === 'transactions') {
        const cotis = this.op === 'insert' && this.valeur.every((v) => v.sens === 'recette' && (v.est_cotisation || v.collecte_id));
        if (!(peut('saisir_ecritures') || peut('rapprocher') || (cotis && peut('gerer_cotisations')))) throw new Error('row-level security');
        if (this.op === 'insert') this.valeur.forEach((v) => {
          if (v.member_id && v.tiers_id) throw new Error('violates check constraint "un_seul_tiers"');
          if (v.est_cotisation && (!v.member_id || v.sens !== 'recette' || v.collecte_id)) throw new Error('violates check constraint "cotisation_d_un_membre"');
          if (v.collecte_id && v.sens !== 'recette') throw new Error('violates check constraint "participation_en_recette"');
        });
      }
      // Exercice clôturé : opérations verrouillées (comme le déclencheur lock_exercice)
      const clos = (dt) => dt && db.tables.exercices.some((e) => e.cloture && dt >= e.debut && dt <= e.fin);
      if (this.table === 'transactions' && this.op === 'insert' && this.valeur.some((v) => clos(v.date_op))) throw new Error('Exercice clôturé : impossible d’enregistrer à cette date');
      if (this.table === 'transactions' && ['update', 'delete'].includes(this.op)) {
        const vis = db.tables.transactions.filter((r) => this.filtres.every((f) => f(r)));
        if (vis.some((r) => clos(r.date_op)) || (this.op === 'update' && clos(this.valeur.date_op))) throw new Error('Exercice clôturé : opération verrouillée');
      }
      if (this.table === 'exercices' && this.op !== 'delete' && this.op !== 'select') {
        const cibles = this.op === 'insert' ? this.valeur : db.tables.exercices.filter((r) => this.filtres.every((f) => f(r))).map((r) => ({ ...r, ...this.valeur }));
        cibles.forEach((e) => {
          if (!(e.fin > e.debut)) throw new Error('violates check constraint exercices_check');
          if (db.tables.exercices.some((x) => x.id !== e.id && x.debut <= e.fin && x.fin >= e.debut)) throw new Error('Cet exercice chevauche un exercice existant');
        });
        if (this.op === 'update' && 'cloture' in this.valeur) Object.assign(this.valeur, this.valeur.cloture ? { cloture_le: new Date().toISOString(), cloture_par: db.moi() } : { cloture_le: null, cloture_par: null });
      }
      if (this.op === 'insert' && this.table === 'tiers' && this.valeur.some((v) => db.tables.tiers.some((x) => x.nom.toLowerCase() === String(v.nom).toLowerCase()))) throw new Error('duplicate key tiers_nom_key');
      let lignes;
      if (this.op === 'insert') {
        if (this.table === 'expense_requests' && !peut('demander_depenses')) throw new Error('row-level security');
        // Comme la règle de la base : trésorier, payeur, ou demandeur pour sa propre demande
        if (this.table === 'attachments' && !(peut('saisir_ecritures') || peut('payer_depenses')
          || this.valeur.every((a) => a.request_id && db.tables.expense_requests.find((r) => r.id === a.request_id)?.demandeur === db.moi()))) throw new Error('row-level security');
        if (this.table === 'role_permissions' && t0(db, this.valeur)) throw new Error('duplicate key');
        const t = db.tables[this.table];
        lignes = this.valeur.map((v) => ({ id: uid(), created_at: new Date().toISOString(), ...(this.table === 'transactions' ? { rapproche: false, contrepasse_de: null, reconciliation_id: null, member_id: null, tiers_id: null, est_cotisation: false, collecte_id: null } : {}),
          ...(this.table === 'collectes' ? { cloturee: false, tous_membres: true } : {}), ...(this.table === 'tiers' ? { type: 'autre' } : {}),
          ...(['members', 'profiles', 'accounts', 'tiers'].includes(this.table) ? { actif: true } : {}), ...v }));
        if (this.table === 'cotisations' && lignes.some((l) => t.some((x) => x.member_id === l.member_id && x.periode === l.periode))) throw new Error('duplicate key');
        if (this.table === 'invitations' && lignes.some((l) => t.some((x) => x.email === l.email))) throw new Error('duplicate key');
        t.push(...lignes);
        return { data: this.retour ? lignes.map((r) => ({ ...r })) : null, error: null };
      }
      lignes = db.lire(this.table).filter((r) => this.filtres.every((f) => f(r)));
      if (this.op === 'update') {
        if (this.table === 'transactions' && lignes.some((r) => db.tables.reconciliations.some((x) => x.id === r.reconciliation_id && x.statut === 'termine'))) throw new Error('Écriture rapprochée : modification impossible');
        if (this.table === 'expense_requests') lignes.forEach((r) => db.transition(r, this.valeur));
        const avant = lignes.map((r) => ({ ...r }));
        lignes.forEach((r) => Object.assign(r, this.valeur));
        if (this.table === 'profiles' && !db.adminActif()) { lignes.forEach((r, i) => Object.assign(r, avant[i])); throw new Error('Au moins une personne active doit garder le droit « administrer »'); }
        return { data: this.retour ? lignes.map((r) => ({ ...r })) : null, error: null };
      }
      if (this.op === 'delete') {
        if (this.table === 'roles' && lignes.some((r) => db.tables.profiles.some((p) => p.role === r.code))) throw new Error('foreign key profiles_role_fkey roles');
        const sauvegarde = db.tables[this.table];
        db.tables[this.table] = sauvegarde.filter((r) => !lignes.includes(r));
        if (this.table === 'roles') db.tables.role_permissions = db.tables.role_permissions.filter((rp) => !lignes.some((r) => r.code === rp.role));
        if (['role_permissions', 'roles'].includes(this.table) && !db.adminActif()) { db.tables[this.table] = sauvegarde; throw new Error('Au moins une personne active doit garder le droit « administrer »'); }
        return { data: null, error: null };
      }
      lignes = [...lignes];
      for (const [c, asc] of [...this.tri].reverse()) lignes.sort((a, b) => (a[c] == null ? 1 : b[c] == null ? -1 : a[c] > b[c] ? 1 : a[c] < b[c] ? -1 : 0) * (asc ? 1 : -1));
      if (this.max != null) lignes = lignes.slice(0, this.max);
      if (this.unique) return { data: lignes[0] ? { ...lignes[0] } : null, error: null };
      return { data: lignes.map((r) => ({ ...r })), error: null };
    } catch (e) { return { data: null, error: { message: e.message } }; }
  }
}

export function createMockClient() {
  const { ids, tables } = seed();
  const fichiers = {};
  const ecouteurs = [];
  const abonnes = [];
  const db = {
    tables, session: null,
    // Temps réel simulé : chaque écriture prévient les abonnés (comme supabase.channel().on('postgres_changes'))
    emettre(table) { setTimeout(() => abonnes.forEach((f) => f({ table, schema: 'public', eventType: '*' })), 30); },
    moi() { return db.session?.user.id; },
    role() { return tables.profiles.find((p) => p.id === db.session?.user.id)?.role; },
    droits() {
      const p = tables.profiles.find((x) => x.id === db.session?.user.id && x.actif);
      return new Set(p ? tables.role_permissions.filter((rp) => rp.role === p.role).map((rp) => rp.permission) : []);
    },
    adminActif() { return tables.profiles.some((p) => p.actif && tables.role_permissions.some((rp) => rp.role === p.role && rp.permission === 'administrer')); },
    // Même contrôle que le déclencheur check_transition de la base
    transition(r, v) {
      if (!v.statut || v.statut === r.statut) return;
      const d = db.droits();
      if (r.statut === 'soumise' && ['validee', 'refusee'].includes(v.statut)) {
        if (!d.has('valider_depenses')) throw new Error('Droit « valider les dépenses » requis');
        if (v.statut === 'validee') {
          if (!v.signature_path) throw new Error('Signature obligatoire');
          v.validee_par = db.moi(); v.validee_le = new Date().toISOString();
          if (r.regularisation) {
            const tx = tables.transactions.find((x) => x.request_id === r.id);
            v.statut = tables.attachments.some((a) => a.transaction_id === tx?.id && (a.nature || 'justificatif') === 'justificatif') ? 'justifiee' : 'payee';
            v.payee_le = r.payee_le || new Date().toISOString();
          }
        }
      } else if (r.statut === 'soumise' && v.statut === 'annulee') {
        if (r.demandeur !== db.moi()) throw new Error('Seul le demandeur annule sa demande');
      } else throw new Error('Changement de statut non autorisé');
    },
    lire(nom) {
      const t = tables;
      const d = db.droits();
      const finances = d.has('consulter_finances');
      if (nom === 'v_soldes') return t.accounts.map((a) => ({ ...a, solde: a.solde_initial + t.transactions.filter((x) => x.account_id === a.id).reduce((s, x) => s + signe(x), 0) }));
      if (nom.startsWith('v_cotisations') && !(finances || d.has('gerer_cotisations'))) return [];
      if (nom === 'v_cotisations_periodes') return periodesCotisation(t).periodes;
      if (nom === 'v_cotisations') {
        const { periodes, paye } = periodesCotisation(t);
        const auj = new Date().toISOString().slice(0, 10);
        const groupes = {};
        periodes.forEach((p) => (groupes[p.member_id + '|' + p.annee] = groupes[p.member_id + '|' + p.annee] || []).push(p));
        return Object.values(groupes).map((l) => {
          const du = l.reduce((s, p) => s + p.montant_du, 0), regle = l.reduce((s, p) => s + p.regle, 0);
          const exigible = l.filter((p) => p.periode <= auj).reduce((s, p) => s + p.montant_du, 0);
          const totalDu = periodes.filter((p) => p.member_id === l[0].member_id).reduce((s, p) => s + p.montant_du, 0);
          const ok = l.filter((p) => ['regle', 'dispense'].includes(p.statut)).map((p) => p.periode).sort();
          return { member_id: l[0].member_id, annee: l[0].annee, montant_du: du, montant_paye: regle, reste: du - regle, exigible, retard: Math.max(0, exigible - regle),
            statut: regle >= exigible ? 'a_jour' : regle > 0 ? 'partiel' : 'impaye', regle_jusqu_a: ok[ok.length - 1] || null, avance: Math.max(0, (paye[l[0].member_id] || 0) - totalDu) };
        });
      }
      if (nom === 'v_collectes') {
        if (!(finances || d.has('gerer_cotisations') || d.has('gerer_activites') || d.has('saisir_ecritures'))) return [];
        return t.collectes.map((c) => {
          const tx = t.transactions.filter((x) => x.collecte_id === c.id);
          return { ...c, total_recu: tx.reduce((s, x) => s + Number(x.montant), 0), nb_contributeurs: (() => { const net = {}; tx.forEach((x) => { const k = x.member_id || x.tiers_id || x.contrepasse_de || x.id; net[k] = (net[k] || 0) + Number(x.montant); }); return Object.values(net).filter((v) => v > 0).length; })(),
            nb_concernes: c.tous_membres ? t.members.filter((m) => m.actif).length : t.collecte_membres.filter((x) => x.collecte_id === c.id).length };
        });
      }
      if (nom === 'v_justificatifs_en_retard') {
        const delai = t.settings.find((s) => s.cle === 'delai_justificatif_jours').valeur;
        return t.expense_requests.filter((r) => r.statut === 'payee').map((r) => ({ ...r, jours: Math.floor((Date.now() - new Date(r.payee_le)) / 864e5) })).filter((r) => r.jours > delai);
      }
      if (nom === 'v_budget_suivi') return t.budgets.map((b) => {
        const c = t.categories.find((x) => x.id === b.category_id);
        const realise = t.transactions.filter((x) => x.category_id === b.category_id && Number(x.date_op.slice(0, 4)) === b.annee && (!b.project_id || x.project_id === b.project_id)).reduce((s, x) => s + Number(x.montant), 0);
        return { annee: b.annee, category_id: b.category_id, categorie: c.nom, sens: c.sens, project_id: b.project_id, montant_prevu: b.montant_prevu, realise,
          ecart: b.montant_prevu - realise, taux_pct: b.montant_prevu > 0 ? Math.round(1000 * realise / b.montant_prevu) / 10 : null,
          alerte: c.sens === 'depense' && realise >= b.montant_prevu * b.seuil_alerte_pct / 100 };
      });
      const moi = t.profiles.find((p) => p.id === db.moi());
      const regles = {
        transactions: finances || d.has('saisir_ecritures') || d.has('gerer_cotisations') || d.has('rapprocher'),
        accounts: finances || d.has('saisir_ecritures') || d.has('payer_depenses') || d.has('gerer_cotisations') || d.has('rapprocher'),
        budgets: finances || d.has('gerer_budget'), v_budget_suivi: finances || d.has('gerer_budget'),
        reconciliations: finances || d.has('rapprocher'), audit_log: d.has('administrer'), invitations: d.has('administrer'),
        tiers: finances || d.has('saisir_ecritures') || d.has('gerer_cotisations') || d.has('payer_depenses'),
        collectes: finances || d.has('gerer_cotisations') || d.has('gerer_activites') || d.has('saisir_ecritures'),
        collecte_membres: finances || d.has('gerer_cotisations') || d.has('gerer_activites') || d.has('saisir_ecritures'),
        materiel: finances || d.has('gerer_materiel') || d.has('voir_membres'), materiel_mouvements: finances || d.has('gerer_materiel') || d.has('voir_membres'),
        liens_membres: d.has('gerer_membres') || d.has('gerer_cotisations'),
      };
      if (nom === 'corbeille') return t.corbeille.filter((c) => c.supprime_par === db.moi() || d.has('administrer') || finances);
      if (nom in regles && !regles[nom]) return [];
      if (nom === 'members' && !(d.has('voir_membres') || d.has('gerer_membres') || d.has('gerer_cotisations'))) return t.members.filter((m) => m.id === moi?.member_id);
      if (nom === 'cotisations' && !(finances || d.has('gerer_cotisations'))) return t.cotisations.filter((c) => c.member_id === moi?.member_id);
      if (nom === 'projects' && !(finances || d.has('gerer_activites') || d.has('demander_depenses'))) return t.projects.filter((p) => p.visible_adherents);
      if (nom === 'communiques' && !(d.has('gerer_activites') || d.has('administrer'))) return t.communiques.filter((c) => c.visible_adherents);
      if (nom === 'expense_requests' && !(d.has('valider_depenses') || d.has('payer_depenses') || finances)) return t.expense_requests.filter((r) => r.demandeur === db.moi());
      if (nom === 'attachments' && !(finances || d.has('valider_depenses') || d.has('payer_depenses'))) return t.attachments.filter((a) => t.expense_requests.some((r) => r.id === a.request_id && r.demandeur === db.moi()));
      if (nom === 'profiles' && !(d.has('administrer') || finances || d.has('valider_depenses') || d.has('payer_depenses'))) return t.profiles.filter((p) => p.id === db.moi());
      return t[nom] || [];
    },
  };
  const session = (id) => ({ user: { id, email: id.replace('u-', '') + '@demo.jp' } });
  const ok = (data) => ({ data, error: null });
  const ko = (message) => ({ data: null, error: { message } });
  const soldePointe = (compte) => {
    const a = tables.accounts.find((x) => x.id === compte);
    return a.solde_initial + tables.transactions.filter((x) => x.account_id === compte && tables.reconciliations.some((r) => r.id === x.reconciliation_id && r.statut === 'termine')).reduce((s, x) => s + signe(x), 0);
  };

  return {
    __setRole(r) { db.session = session(ids[{ tresorier: 't', president: 'p', bureau: 'b', adherent: 'a', nouveau: 'n' }[r]]); },
    channel() {
      const canal = { cb: null, on(_type, _filtre, cb) { canal.cb = cb; return canal; }, subscribe(statut) { if (canal.cb) abonnes.push(canal.cb); statut?.('SUBSCRIBED'); return canal; },
        unsubscribe() { const i = abonnes.indexOf(canal.cb); if (i >= 0) abonnes.splice(i, 1); } };
      return canal;
    },
    removeChannel(c) { c?.unsubscribe?.(); },
    from: (table) => new Requete(db, table),
    async rpc(nom, args = {}) {
      const t = tables;
      if (nom === 'situation_par_lien') {
        // Même règle que la base : jeton de 32 caractères (anciens liens) ou code de 12 caractères
        const v = String(args.p_jeton || '');
        const l = t.liens_membres.find((x) => (v.length === 32 && x.jeton === v) || (v.length === 12 && x.code === v.toLowerCase()));
        if (!l) return ok(null);
        l.nb_consultations++; l.derniere_consultation = new Date().toISOString();
        const m = t.members.find((x) => x.id === l.member_id);
        const reg = (c) => t.settings.find((s) => s.cle === c);
        const periodes = periodesCotisation(t).periodes.filter((p) => p.member_id === m.id).sort((a, b) => (a.periode < b.periode ? 1 : -1));
        const paye = t.transactions.filter((x) => x.est_cotisation && x.member_id === m.id).reduce((s, x) => s + Number(x.montant), 0);
        const du = t.cotisations.filter((c) => c.member_id === m.id).reduce((s, c) => s + Number(c.montant_du), 0);
        return ok({
          association: { nom: t.organisation[0].nom, logo_path: t.organisation[0].logo_path, banniere_path: t.organisation[0].banniere_path },
          membre: { prenom: m.prenom, nom: m.nom, actif: m.actif },
          reglages: { montant: reg('cotisation_montant').valeur, periode_mois: reg('cotisation_periode_mois').valeur, infos_paiement: reg('infos_paiement')?.texte || null },
          periodes, avance: Math.max(0, paye - du),
          versements: t.transactions.filter((x) => x.member_id === m.id && x.sens === 'recette' && (x.est_cotisation || x.collecte_id)).sort((a, b) => (a.date_op < b.date_op ? 1 : -1)).slice(0, 24)
            .map((x) => ({ date: x.date_op, montant: x.montant, objet: x.est_cotisation ? 'Cotisation' : (t.collectes.find((c) => c.id === x.collecte_id)?.nom || x.libelle) })),
          participations: t.collectes.filter((c) => c.tous_membres || t.collecte_membres.some((x) => x.collecte_id === c.id && x.member_id === m.id)).map((c) => ({
            nom: c.nom, montant_attendu: c.montant_attendu, date_limite: c.date_limite, cloturee: c.cloturee,
            donne: t.transactions.filter((x) => x.collecte_id === c.id && x.member_id === m.id).reduce((s, x) => s + Number(x.montant), 0) })).filter((c) => !c.cloturee || c.donne !== 0),
          communiques: t.communiques.filter((c) => c.visible_adherents && c.debut <= iso(new Date()) && (!c.fin || c.fin >= iso(new Date()))).map(({ id, titre, texte, priorite, debut, fin }) => ({ id, titre, texte, priorite, debut, fin })),
          banniere: reg('banniere')?.texte || null,
          a_venir: t.projects.filter((p) => p.visible_adherents && p.date_debut && (p.date_fin || p.date_debut) >= iso(new Date()) && p.date_debut <= dansJours(120))
            .sort((a, b) => (a.date_debut > b.date_debut ? 1 : -1)).slice(0, 12),
        });
      }
      if (!db.session) return ko('Non connecté');
      const d = db.droits();
      if (nom === 'mes_droits') return ok([...d].sort());
      if (nom === 'lien_membre') {
        if (!(d.has('gerer_membres') || d.has('gerer_cotisations'))) return ko('Droit « gérer les membres » requis');
        let l = t.liens_membres.find((x) => x.member_id === args.p_member);
        if (l && !args.p_renouveler) return ok(l.jeton);
        const jeton = Array.from({ length: 32 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('');
        const code = Array.from({ length: 12 }, () => 'abcdefghjkmnpqrstuvwxyz23456789'[Math.floor(Math.random() * 31)]).join('');
        if (l) Object.assign(l, { jeton, code, cree_le: new Date().toISOString(), nb_consultations: 0, derniere_consultation: null });
        else t.liens_membres.push({ member_id: args.p_member, jeton, code, cree_par: db.moi(), cree_le: new Date().toISOString(), nb_consultations: 0, derniere_consultation: null });
        return ok(jeton);
      }
      if (nom === 'demander_validation_operation') {
        if (!d.has('saisir_ecritures')) return ko('Droit « saisir les écritures » requis');
        const tx = t.transactions.find((x) => x.id === args.p_transaction);
        if (!tx || tx.sens !== 'depense' || tx.montant <= 0 || tx.contrepasse_de) return ko('Seule une dépense peut être soumise au président');
        if (tx.request_id) return ko('Cette dépense a déjà une demande de validation');
        const r = { id: uid(), demandeur: db.moi(), objet: tx.libelle, montant: tx.montant, category_id: tx.category_id, project_id: tx.project_id, account_id: tx.account_id,
          statut: 'soumise', regularisation: true, validee_par: null, validee_le: null, signature_path: null, signature_hash: null, motif_refus: null,
          payee_par: tx.created_by, payee_le: new Date(tx.date_op).toISOString(), created_at: new Date().toISOString() };
        t.expense_requests.push(r); tx.request_id = r.id;
        return ok(r.id);
      }
      if (nom === 'virement_interne') {
        if (!d.has('saisir_ecritures')) return ko('Droit « saisir les écritures » requis');
        if (args.p_source === args.p_dest) return ko('Choisissez deux comptes différents');
        if (!(Number(args.p_montant) > 0)) return ko('Le montant doit être positif');
        const s2 = t.accounts.find((x) => x.id === args.p_source), d2 = t.accounts.find((x) => x.id === args.p_dest);
        if (!s2 || !d2) return ko('Compte introuvable ou inactif');
        const lib = (args.p_libelle || '').trim() || (s2.type === 'caisse' && d2.type === 'banque' ? 'Dépôt d’espèces à la banque' : s2.type === 'banque' && d2.type === 'caisse' ? 'Retrait d’espèces pour la caisse' : 'Virement entre comptes');
        const v = uid(), mode = s2.type === 'caisse' || d2.type === 'caisse' ? 'especes' : 'virement';
        const jambe = (sens, compte) => ({ id: uid(), date_op: args.p_date, account_id: compte, sens, montant: Number(args.p_montant), category_id: t.categories.find((c) => c.interne && c.sens === sens).id,
          project_id: null, libelle: lib.slice(0, 120), tiers_id: null, mode, member_id: null, est_cotisation: false, collecte_id: null, request_id: null, rapproche: false, reconciliation_id: null,
          date_rapprochement: null, contrepasse_de: null, virement: v, created_by: db.moi(), created_at: new Date().toISOString() });
        t.transactions.push(jambe('depense', s2.id), jambe('recette', d2.id));
        return ok(v);
      }
      if (nom === 'annuler_virement') {
        if (!d.has('saisir_ecritures')) return ko('Droit « saisir les écritures » requis');
        if (!String(args.p_motif || '').trim()) return ko('Motif obligatoire');
        const jambes = t.transactions.filter((x) => x.virement === args.p_virement && !x.contrepasse_de);
        if (!jambes.length) return ko('Virement introuvable');
        if (jambes.some((x) => x.rapproche)) return ko('Virement rapproché : période verrouillée, annulation impossible');
        if (t.transactions.some((x) => x.virement === args.p_virement && x.contrepasse_de)) return ko('Ce virement est déjà annulé');
        jambes.forEach((x) => t.transactions.push({ ...x, id: uid(), date_op: iso(new Date()), montant: -x.montant, contrepasse_de: x.id, rapproche: false, reconciliation_id: null,
          libelle: `Contre-passation : ${x.libelle}`.slice(0, 100) + ' · motif : ' + String(args.p_motif).trim().slice(0, 60), created_by: db.moi(), created_at: new Date().toISOString() }));
        return ok(null);
      }
      if (nom === 'modifier_mon_nom') {
        const p = t.profiles.find((x) => x.id === db.moi());
        if (!String(args.p_nom || '').trim()) return ko('Nom obligatoire');
        p.nom = String(args.p_nom).trim().slice(0, 80); return ok(null);
      }
      if (nom === 'anniversaires_du_mois') {
        const bureau = d.has('voir_membres') || d.has('gerer_membres');
        const mois = args.p_mois || moisCourant;
        return ok(t.members.filter((m) => m.actif && m.naissance_mois === mois && (m.consent_anniversaire || bureau))
          .map((m) => ({ prenom: m.prenom, nom: bureau ? m.nom : m.nom[0] + '.', jour: m.naissance_jour, photo_path: m.photo_path, profession: m.profession }))
          .sort((a, b) => a.jour - b.jour));
      }
      if (nom === 'ma_cotisation') {
        const p = t.profiles.find((x) => x.id === db.moi());
        return ok(periodesCotisation(t).periodes.filter((c) => c.member_id === p?.member_id).sort((a, b) => (a.periode < b.periode ? 1 : -1)));
      }
      if (nom === 'mes_participations') {
        const p = t.profiles.find((x) => x.id === db.moi());
        if (!p?.member_id) return ok([]);
        return ok(t.collectes.filter((c) => c.tous_membres || t.collecte_membres.some((x) => x.collecte_id === c.id && x.member_id === p.member_id)).map((c) => ({
          collecte_id: c.id, nom: c.nom, montant_attendu: c.montant_attendu, date_limite: c.date_limite, cloturee: c.cloturee,
          donne: t.transactions.filter((x) => x.collecte_id === c.id && x.member_id === p.member_id).reduce((s, x) => s + Number(x.montant), 0),
        })).filter((c) => !c.cloturee || c.donne !== 0));
      }
      if (nom === 'generer_cotisations') {
        if (!d.has('gerer_cotisations')) return ko('Droit « gérer les cotisations » requis');
        const montant = t.settings.find((s) => s.cle === 'cotisation_montant').valeur;
        let pas = Number(t.settings.find((s) => s.cle === 'cotisation_periode_mois')?.valeur || 1);
        if (![1, 3, 6, 12].includes(pas)) pas = 1;
        let n = 0;
        t.members.filter((m) => m.actif).forEach((m) => {
          for (let mo = 1; mo <= 12; mo += pas) {
            const periode = `${args.an}-${String(mo).padStart(2, '0')}-01`;
            if (m.date_adhesion && new Date(m.date_adhesion) >= new Date(args.an, mo - 1 + pas, 1)) continue;
            if (!t.cotisations.some((c) => c.member_id === m.id && c.periode === periode)) { t.cotisations.push({ id: uid(), member_id: m.id, periode, montant_du: montant }); n++; }
          }
        });
        return ok(n);
      }
      if (nom === 'payer_demande') {
        if (!d.has('payer_depenses')) return ko('Droit « payer les dépenses » requis');
        const r = t.expense_requests.find((x) => x.id === args.p_id);
        if (r?.statut !== 'validee') return ko('Demande non validée');
        if (r.validee_par === db.moi()) return ko('La personne qui a validé ne peut pas payer');
        const tr = { id: uid(), date_op: args.p_date, account_id: args.p_compte, sens: 'depense', montant: r.montant, category_id: r.category_id, project_id: r.project_id,
          libelle: r.objet, tiers_id: null, mode: args.p_mode, member_id: null, est_cotisation: false, collecte_id: null, request_id: r.id, rapproche: false, reconciliation_id: null,
          contrepasse_de: null, created_by: db.moi(), created_at: new Date().toISOString() };
        t.transactions.push(tr);
        Object.assign(r, { statut: 'payee', account_id: args.p_compte, payee_le: new Date().toISOString(), payee_par: db.moi() });
        return ok(tr.id);
      }
      if (nom === 'justifier_demande') {
        const r = t.expense_requests.find((x) => x.id === args.p_id);
        if (!r) return ko('Demande introuvable');
        if (!(d.has('saisir_ecritures') || d.has('payer_depenses') || r.demandeur === db.moi())) return ko('Le justificatif est déposé par le demandeur ou par la personne qui paie');
        if (r.statut !== 'payee') return ko('La demande doit être payée avant le justificatif');
        const tr = t.transactions.find((x) => x.request_id === r.id);
        t.attachments.push({ id: uid(), request_id: r.id, transaction_id: tr?.id || null, storage_path: args.p_chemin, mime: args.p_mime, taille_ko: args.p_ko, depose_par: db.moi() });
        r.statut = 'justifiee';
        return ok(null);
      }
      if (nom === 'solde_pointe') return ok(soldePointe(args.p_compte));
      if (nom === 'terminer_rapprochement') {
        if (!d.has('rapprocher')) return ko('Droit « rapprocher » requis');
        if (!args.p_chemin) return ko('Relevé de la période obligatoire');
        const choisies = t.transactions.filter((x) => args.p_ecritures.includes(x.id));
        if (choisies.some((x) => x.account_id !== args.p_compte || x.reconciliation_id || x.date_op > args.p_fin)) return ko('Écriture d’un autre compte, déjà rapprochée ou postérieure à la période');
        const ecart = Math.round((args.p_solde_releve - soldePointe(args.p_compte) - choisies.reduce((s, x) => s + signe(x), 0)) * 100) / 100;
        if (ecart !== 0) return ko(`Écart de ${ecart} € entre le relevé et les écritures pointées`);
        const rec = { id: uid(), account_id: args.p_compte, periode_debut: args.p_debut, periode_fin: args.p_fin, solde_releve: args.p_solde_releve, statement_path: args.p_chemin,
          statement_name: args.p_nom, statement_ko: args.p_ko, statut: 'termine', ecart: 0, termine_par: db.moi(), termine_le: new Date().toISOString(), created_at: new Date().toISOString() };
        t.reconciliations.push(rec);
        choisies.forEach((x) => Object.assign(x, { reconciliation_id: rec.id, rapproche: true, date_rapprochement: args.p_fin }));
        return ok(rec.id);
      }
      if (nom === 'planning_activites') {
        const debut = args.p_debut || iso(ilYa(30)), fin = args.p_fin || '9999-12-31';
        return ok(t.projects.filter((p) => (p.visible_adherents || d.has('gerer_activites') || d.has('consulter_finances')) && p.date_debut
          && (p.date_fin || p.date_debut) >= debut && p.date_debut <= fin)
          .map((p) => { const c = t.collectes.filter((x) => x.project_id === p.id && !x.cloturee).sort((a, b) => (a.created_at > b.created_at ? 1 : -1))[0];
            return { ...p, participation: c?.montant_attendu ?? null, collecte_id: c?.id ?? null }; })
          .sort((a, b) => ((a.date_debut + (a.heure_debut || '')) > (b.date_debut + (b.heure_debut || '')) ? 1 : -1)));
      }
      // Corbeille : copie complète puis effacement ; restauration à l'identique (comme supprimer() et restaurer() de la base)
      if (nom === 'supprimer' || nom === 'restaurer') {
        const DROITS = { transactions: ['saisir_ecritures'], members: ['gerer_membres'], tiers: ['saisir_ecritures', 'gerer_cotisations'], projects: ['gerer_activites'],
          collectes: ['gerer_activites', 'gerer_cotisations'], materiel: ['gerer_materiel'], categories: ['administrer'], accounts: ['administrer'], budgets: ['gerer_budget'],
          expense_requests: null, exercices: ['administrer'], communiques: ['gerer_activites', 'administrer'] };
        const peutTable = (tb) => tb in DROITS && (DROITS[tb] === null || DROITS[tb].some((x) => d.has(x)));
        const clos = (dt) => t.exercices.some((e) => e.cloture && dt >= e.debut && dt <= e.fin);
        const eur = (m) => Number(m).toFixed(2).replace('.', ',') + ' €';
        if (nom === 'restaurer') {
          const c = t.corbeille.find((x) => x.id === args.p_id);
          if (!c) return ko('Élément introuvable dans la corbeille');
          if (c.restaure_le) return ko('Élément déjà restauré');
          if (!peutTable(c.table_nom)) return ko('Droit insuffisant pour restaurer');
          if (c.table_nom === 'expense_requests' && c.donnees.demandeur !== db.moi() && !d.has('administrer')) return ko('Seul le demandeur restaure sa demande');
          if (t[c.table_nom].some((x) => x.id === c.ligne_id)) return ko('Restauration impossible : un élément identique existe déjà');
          t[c.table_nom].push({ ...c.donnees });
          c.dependances.forEach((x) => t[x.table].push({ ...x.donnees }));
          c.liens.forEach((l) => { const r = t[l.table].find((x) => x.id === l.id); if (r && r[l.colonne] == null) r[l.colonne] = l.valeur; });
          Object.assign(c, { restaure_le: new Date().toISOString(), restaure_par: db.moi() });
          db.emettre(c.table_nom);
          return ok(null);
        }
        const tb = args.p_table, id = args.p_id;
        if (!peutTable(tb)) return ko('Droit insuffisant pour supprimer');
        const v = t[tb]?.find((x) => x.id === id);
        if (!v) return ko('Élément introuvable');
        let lib = tb === 'communiques' ? 'Communiqué · ' + v.titre : tb, dep = [], liens = [];
        const avec = (table, lignes) => lignes.forEach((x) => dep.push({ table, donnees: { ...x } }));
        if (tb === 'transactions') {
          if (v.reconciliation_id) return ko('Opération rapprochée : utilisez la contre-passation');
          if (clos(v.date_op)) return ko('Exercice clôturé : opération verrouillée');
          if (t.transactions.some((x) => x.contrepasse_de === id)) return ko('Cette opération a été corrigée : supprimez d’abord la correction');
          if (v.request_id && !v.contrepasse_de) return ko('Cette opération paie une demande de dépense : utilisez « Régulariser » ou la contre-passation');
          lib = `${v.libelle} · ${eur(v.montant)}`;
          if (v.virement) {
            const autres = t.transactions.filter((x) => x.virement === v.virement && x.id !== id);
            if (autres.some((x) => x.reconciliation_id)) return ko('Virement rapproché : utilisez « Annuler le virement »');
            if (autres.some((x) => t.transactions.some((y) => y.contrepasse_de === x.id))) return ko('Virement déjà annulé');
            avec('transactions', autres); lib = 'Virement interne · ' + lib;
          }
          const txIds = [id, ...dep.map((x) => x.donnees.id)];
          avec('attachments', t.attachments.filter((a) => txIds.includes(a.transaction_id)));
          t.materiel.filter((x) => x.transaction_id === id).forEach((x) => liens.push({ table: 'materiel', id: x.id, colonne: 'transaction_id', valeur: id }));
        } else if (tb === 'members') {
          if (t.transactions.some((x) => x.member_id === id)) return ko('Ce membre a des opérations enregistrées : désactivez-le plutôt (fiche, case Actif)');
          if (t.profiles.some((p) => p.member_id === id) || t.invitations.some((i) => i.member_id === id)) return ko('Ce membre a un accès ou une invitation à la plateforme : retirez d’abord son accès');
          lib = `${v.prenom} ${v.nom}`;
          avec('cotisations', t.cotisations.filter((x) => x.member_id === id)); avec('collecte_membres', t.collecte_membres.filter((x) => x.member_id === id));
          avec('liens_membres', t.liens_membres.filter((x) => x.member_id === id));
          t.materiel.filter((x) => x.detenteur_id === id).forEach((x) => liens.push({ table: 'materiel', id: x.id, colonne: 'detenteur_id', valeur: id }));
        } else if (tb === 'tiers') {
          if (t.transactions.some((x) => x.tiers_id === id)) return ko('Ce tiers a des opérations enregistrées : désactivez-le plutôt');
          lib = v.nom;
        } else if (tb === 'projects') {
          if (t.transactions.some((x) => x.project_id === id) || t.expense_requests.some((x) => x.project_id === id)) return ko('Des opérations ou des demandes sont rattachées à ce rendez-vous : impossible de le supprimer');
          lib = v.nom + (v.date_debut ? ' · ' + v.date_debut.split('-').reverse().join('/') : '');
          avec('budgets', t.budgets.filter((x) => x.project_id === id));
          t.collectes.filter((x) => x.project_id === id).forEach((x) => liens.push({ table: 'collectes', id: x.id, colonne: 'project_id', valeur: id }));
        } else if (tb === 'collectes') {
          if (t.transactions.some((x) => x.collecte_id === id)) return ko('Des participations sont déjà encaissées : clôturez la collecte plutôt');
          lib = v.nom; avec('collecte_membres', t.collecte_membres.filter((x) => x.collecte_id === id));
        } else if (tb === 'materiel') {
          lib = v.designation; avec('materiel_mouvements', t.materiel_mouvements.filter((x) => x.materiel_id === id));
        } else if (tb === 'categories') {
          if (v.interne) return ko('Catégorie interne : nécessaire aux virements');
          if (t.transactions.some((x) => x.category_id === id) || t.expense_requests.some((x) => x.category_id === id)) return ko('Catégorie utilisée : impossible de la supprimer');
          lib = v.nom; avec('budgets', t.budgets.filter((x) => x.category_id === id));
        } else if (tb === 'accounts') {
          if (t.transactions.some((x) => x.account_id === id) || t.reconciliations.some((x) => x.account_id === id)) return ko('Compte utilisé : désactivez-le plutôt');
          lib = v.nom;
        } else if (tb === 'budgets') {
          lib = `Ligne de budget ${v.annee} · ${t.categories.find((c) => c.id === v.category_id)?.nom || ''}`;
        } else if (tb === 'expense_requests') {
          if (v.demandeur !== db.moi() && !d.has('administrer')) return ko('Seul le demandeur supprime sa demande');
          if (t.transactions.some((x) => x.request_id === id)) return ko('Cette demande a été payée : utilisez « Régulariser »');
          lib = `${v.objet} · ${eur(v.montant)}`; avec('attachments', t.attachments.filter((a) => a.request_id === id));
        } else if (tb === 'exercices') {
          if (v.cloture) return ko('Exercice clôturé : rouvrez-le d’abord');
          lib = v.libelle;
        }
        const entree = { id: uid(), table_nom: tb, ligne_id: id, libelle: lib, donnees: { ...v }, dependances: dep, liens, motif: (args.p_motif || '').trim() || null,
          supprime_par: db.moi(), supprime_le: new Date().toISOString(), restaure_par: null, restaure_le: null };
        t.corbeille.push(entree);
        liens.forEach((l) => { const r = t[l.table].find((x) => x.id === l.id); if (r) r[l.colonne] = null; });
        dep.forEach((x) => { t[x.table] = t[x.table].filter((r) => !(x.table === 'transactions' ? r.id === x.donnees.id : JSON.stringify(r) === JSON.stringify(x.donnees))); });
        t[tb] = t[tb].filter((x) => x.id !== id);
        db.emettre(tb);
        return ok(entree.id);
      }
      // Nouveautés : à traiter + nouveau depuis la dernière visite de chaque onglet (comme mes_nouveautes() de la base)
      if (nom === 'mes_nouveautes') {
        const p = t.profiles.find((x) => x.id === db.moi() && x.actif);
        if (!p) return ok({ compteurs: {}, elements: [] });
        const depuis = (sec) => p.vus?.[sec] || p.created_at || '1970-01-01';
        const eur = (m) => Number(m).toFixed(2).replace('.', ',') + ' €';
        const compteurs = {}, elements = [];
        const maj = (r) => [r.validee_le, r.payee_le].filter(Boolean).sort().pop();
        const aTraiter = t.expense_requests.filter((r) => (r.statut === 'soumise' && r.demandeur !== p.id && d.has('valider_depenses'))
          || (r.statut === 'validee' && r.validee_par !== p.id && d.has('payer_depenses')) || (r.demandeur === p.id && (maj(r) || '') > depuis('depenses')));
        compteurs.depenses = aTraiter.length;
        aTraiter.forEach((r) => elements.push({ section: 'depenses', titre: r.statut === 'soumise' ? 'À valider' : r.statut === 'validee' ? 'À payer' : 'Demande mise à jour', detail: `${r.objet} · ${eur(r.montant)}`, quand: maj(r) || r.created_at }));
        if (d.has('consulter_finances') || d.has('saisir_ecritures')) {
          const l = t.transactions.filter((x) => x.created_at > depuis('ecritures') && x.created_by !== p.id);
          compteurs.ecritures = l.length;
          l.forEach((x) => elements.push({ section: 'ecritures', titre: x.sens === 'recette' ? 'Nouvelle recette' : 'Nouvelle dépense', detail: `${x.libelle} · ${eur(x.montant)}`, quand: x.created_at }));
        }
        const voitProjet = (x) => x.visible_adherents || d.has('gerer_activites') || d.has('consulter_finances') || d.has('demander_depenses');
        const pl = t.projects.filter((x) => (x.created_at || '') > depuis('activites') && voitProjet(x));
        compteurs.activites = pl.length;
        pl.forEach((x) => elements.push({ section: 'activites', titre: 'Nouveau rendez-vous', detail: x.nom + (x.date_debut ? ' · ' + x.date_debut.slice(8, 10) + '/' + x.date_debut.slice(5, 7) : ''), quand: x.created_at }));
        if (d.has('voir_membres') || d.has('gerer_membres')) {
          const ms = t.members.filter((x) => x.created_at > depuis('membres'));
          compteurs.membres = ms.length;
          ms.forEach((x) => elements.push({ section: 'membres', titre: 'Nouveau membre', detail: `${x.prenom} ${x.nom}`, quand: x.created_at }));
        }
        const gere = d.has('gerer_cotisations') || d.has('consulter_finances');
        const co = t.collectes.filter((c) => c.created_at > depuis('cotisations') && (gere || (p.member_id && !c.cloturee
          && (c.tous_membres || t.collecte_membres.some((x) => x.collecte_id === c.id && x.member_id === p.member_id)))));
        compteurs.cotisations = co.length;
        co.forEach((c) => elements.push({ section: 'cotisations', titre: 'Participation demandée', detail: c.nom + (c.montant_attendu ? ' · ' + eur(c.montant_attendu) : ''), quand: c.created_at }));
        const auj = iso(new Date());
        const cq = t.communiques.filter((c) => c.created_at > depuis('communiques') && c.created_by !== p.id && (c.visible_adherents || d.has('gerer_activites') || d.has('administrer')) && (!c.fin || c.fin >= auj));
        compteurs.communiques = cq.length;
        cq.forEach((c) => elements.push({ section: 'communiques', titre: 'Communiqué', detail: c.titre, quand: c.created_at }));
        return ok({ compteurs, elements: elements.sort((a, b) => (a.quand < b.quand ? 1 : -1)) });
      }
      if (nom === 'marquer_vu') {
        const p = t.profiles.find((x) => x.id === db.moi());
        if (p) p.vus = { ...(p.vus || {}), [args.p_section]: new Date().toISOString() };
        return ok(null);
      }
      // Le nouveau membre remplit sa fiche : rattachée si elle existe (même e-mail), créée sinon
      if (nom === 'enregistrer_ma_fiche') {
        const p = t.profiles.find((x) => x.id === db.moi());
        if (!p) return ko('Connexion requise');
        if (!String(args.p_prenom || '').trim() || !String(args.p_nom || '').trim()) return ko('Prénom et nom obligatoires');
        if (!(args.p_jour >= 1 && args.p_jour <= 31 && args.p_mois >= 1 && args.p_mois <= 12)) return ko('Date de naissance invalide');
        const email = db.session.user.email;
        let m = t.members.find((x) => x.id === p.member_id)
          || t.members.find((x) => x.email && x.email.toLowerCase() === email.toLowerCase() && !t.profiles.some((q) => q.member_id === x.id));
        const champs = { prenom: args.p_prenom.trim(), nom: args.p_nom.trim(), naissance_jour: args.p_jour, naissance_mois: args.p_mois };
        if (m) Object.assign(m, champs, { whatsapp: args.p_whatsapp?.trim() || m.whatsapp, profession: args.p_profession?.trim() || m.profession, email: m.email || email, consent_anniversaire: args.p_consent ?? m.consent_anniversaire });
        else {
          m = { id: uid(), ...champs, whatsapp: args.p_whatsapp?.trim() || null, profession: args.p_profession?.trim() || null, email, photo_path: null,
            consent_anniversaire: args.p_consent ?? true, date_adhesion: iso(new Date()), actif: true, created_at: new Date().toISOString() };
          t.members.push(m);
        }
        Object.assign(p, { member_id: m.id, nom: `${m.prenom} ${m.nom}` });
        return ok(m.id);
      }
      return ko('Fonction inconnue : ' + nom);
    },
    storage: {
      from: (bucket) => ({
        async upload(chemin, blob) { fichiers[bucket + '/' + chemin] = URL.createObjectURL(blob); return ok({ path: chemin }); },
        async createSignedUrls(chemins) { return ok(chemins.map((p) => ({ path: p, signedUrl: fichiers[bucket + '/' + p] || null }))); },
        async createSignedUrl(chemin) { return ok({ signedUrl: fichiers[bucket + '/' + chemin] || DOC_DEMO }); },
        getPublicUrl(chemin) { return { data: { publicUrl: fichiers[bucket + '/' + chemin] || 'logo.jpg' } }; },
        async download(chemin) { const r = await fetch(fichiers[bucket + '/' + chemin] || DOC_DEMO); return ok(await r.blob()); },
      }),
    },
    auth: {
      async getSession() { return ok({ session: db.session }); },
      async signInWithPassword({ email, password }) {
        const cle = { 'tresorier@demo.jp': 't', 'president@demo.jp': 'p', 'bureau@demo.jp': 'b', 'adherent@demo.jp': 'a', 'nouveau@demo.jp': 'n' }[String(email).toLowerCase()];
        if (!cle || password !== MOT_DE_PASSE) return { data: { session: null }, error: { message: 'Invalid login credentials' } };
        db.session = session(ids[cle]); return ok({ session: db.session });
      },
      async signUp() { return ok({ session: null }); },
      async signOut() { db.session = null; ecouteurs.forEach((f) => f('SIGNED_OUT', null)); return { error: null }; },
      async resetPasswordForEmail() { return ok({}); },
      async updateUser() { return ok({}); },
      onAuthStateChange(f) { ecouteurs.push(f); return { data: { subscription: { unsubscribe() {} } } }; },
    },
  };
}
