// Graphiques en SVG, sans bibliothèque : colonnes groupées, ligne, barres horizontales,
// jauge, budget prévu/réalisé. Utilisés par le tableau de bord et le rapport d'AG
// (le SVG s'imprime tel quel). Règles : traits fins, une seule échelle, légende dès
// deux séries, texte en encre neutre (jamais de la couleur de la série).
// Code couleur unique de l'application : une couleur = un sens.
// Recettes (ce qui entre) en bleu, dépenses (ce qui sort) en orange, trésorerie
// (ce qui reste) en encre neutre. Les répartitions déclinent la couleur de leur sens.
export const C = {
  recette: '#1B77B0', depense: '#C23E10', tresorerie: '#4A4543', depenseClair: '#F2C8B8', recetteClair: '#CDE8F8',
  encre: '#1C1B1A', encre2: '#5A5350', grille: '#E9E5E3', surface: '#FFFFFF', alerte: '#BA1A1A',
};
const MOIS_COURTS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
export const eur0 = (n) => Number(n || 0).toLocaleString('fr-FR', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const eur = (n) => Number(n || 0).toLocaleString('fr-FR', { style: 'currency', currency: 'EUR' });
// 1 234 -> « 1,2 k » pour les graduations
const court = (n) => (Math.abs(n) >= 1000 ? (n / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' k' : Math.round(n).toLocaleString('fr-FR'));

// Graduations « rondes » entre min et max
function graduations(min, max, n = 4) {
  if (max === min) max = min + 1;
  const brut = (max - min) / n;
  const p = 10 ** Math.floor(Math.log10(brut));
  // Montants en euros : jamais de pas inférieur à 1 €, sinon les graduations arrondies se répètent (0, 0, 1, 1)
  const pas = Math.max(1, [1, 2, 2.5, 5, 10].map((k) => k * p).find((s) => s >= brut));
  const bas = Math.floor(min / pas) * pas, haut = Math.ceil(max / pas) * pas;
  const t = []; for (let v = bas; v <= haut + pas / 2; v += pas) t.push(Math.round(v * 100) / 100);
  return t;
}

// Colonne arrondie côté valeur, carrée sur la ligne de base
function colonne(x, y0, y1, l) {
  const haut = Math.min(y0, y1), h = Math.abs(y1 - y0), r = Math.min(4, h, l / 2);
  if (h < 0.5) return '';
  return y1 <= y0
    ? `M${x},${y0}V${haut + r}Q${x},${haut} ${x + r},${haut}H${x + l - r}Q${x + l},${haut} ${x + l},${haut + r}V${y0}Z`
    : `M${x},${y0}V${y1 - r}Q${x},${y1} ${x + r},${y1}H${x + l - r}Q${x + l},${y1} ${x + l},${y1 - r}V${y0}Z`;
}

function legende(series) {
  return series.length < 2 ? '' : `<div class="g-legende">${series.map((s) => `<span><i style="background:${s.couleur}"></i>${esc(s.nom)}</span>`).join('')}</div>`;
}

// Recettes et dépenses par mois : colonnes groupées, une seule échelle
export function colonnesGroupees({ libelles, series, titre, hauteur = 220 }) {
  const L = 640, H = hauteur, g = 44, d = 8, h = 16, b = 26;
  const toutes = series.flatMap((s) => s.valeurs);
  const t = graduations(Math.min(0, ...toutes), Math.max(0, ...toutes));
  const y = (v) => h + (H - h - b) * (1 - (v - t[0]) / (t[t.length - 1] - t[0]));
  const bande = (L - g - d) / libelles.length;
  const l = Math.min(18, (bande * 0.7 - 2 * (series.length - 1)) / series.length);
  const bloc = series.length * l + 2 * (series.length - 1);
  let svg = t.map((v) => `<line x1="${g}" x2="${L - d}" y1="${y(v)}" y2="${y(v)}" stroke="${v === 0 ? '#CFC8C5' : C.grille}" stroke-width="1"/>
    <text x="${g - 6}" y="${y(v) + 4}" text-anchor="end" class="g-axe">${court(v)}</text>`).join('');
  libelles.forEach((lib, i) => {
    const x0 = g + i * bande + (bande - bloc) / 2;
    series.forEach((s, k) => {
      const v = s.valeurs[i] || 0;
      svg += `<path d="${colonne(x0 + k * (l + 2), y(0), y(v), l)}" fill="${s.couleur}" data-tip="${esc(lib)} · ${esc(s.nom)} : ${esc(eur(v))}"><title>${esc(lib)} · ${esc(s.nom)} : ${esc(eur(v))}</title></path>`;
    });
    // zone de survol plus large que la colonne
    svg += `<rect x="${g + i * bande}" y="${h}" width="${bande}" height="${H - h - b}" fill="transparent" data-tip="${esc(lib)} · ${series.map((s) => `${esc(s.nom)} ${esc(eur(s.valeurs[i] || 0))}`).join(' · ')}"/>`;
    if (libelles.length <= 12 || i % 2 === 0) svg += `<text x="${g + i * bande + bande / 2}" y="${H - 8}" text-anchor="middle" class="g-axe">${esc(lib)}</text>`;
  });
  return `<figure class="graphique">${titre ? `<figcaption>${esc(titre)}</figcaption>` : ''}${legende(series)}
    <svg viewBox="0 0 ${L} ${H}" role="img" aria-label="${esc(titre || 'Graphique')}">${svg}</svg></figure>`;
}

// Solde en fin de mois : une ligne, valeur de fin étiquetée
export function ligne({ libelles, valeurs, couleur = C.tresorerie, titre, hauteur = 180 }) {
  const L = 640, H = hauteur, g = 44, d = 70, h = 16, b = 26;
  const t = graduations(Math.min(0, ...valeurs), Math.max(...valeurs, 1));
  const y = (v) => h + (H - h - b) * (1 - (v - t[0]) / (t[t.length - 1] - t[0]));
  const pas = libelles.length > 1 ? (L - g - d) / (libelles.length - 1) : 0;
  const x = (i) => g + i * pas;
  const pts = valeurs.map((v, i) => `${x(i)},${y(v)}`).join(' ');
  let svg = t.map((v) => `<line x1="${g}" x2="${L - d}" y1="${y(v)}" y2="${y(v)}" stroke="${v === 0 ? '#CFC8C5' : C.grille}"/><text x="${g - 6}" y="${y(v) + 4}" text-anchor="end" class="g-axe">${court(v)}</text>`).join('');
  svg += `<polygon points="${x(0)},${y(Math.max(0, t[0]))} ${pts} ${x(valeurs.length - 1)},${y(Math.max(0, t[0]))}" fill="${couleur}" opacity=".1"/>`;
  svg += `<polyline points="${pts}" fill="none" stroke="${couleur}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>`;
  valeurs.forEach((v, i) => {
    svg += `<circle cx="${x(i)}" cy="${y(v)}" r="10" fill="transparent" data-tip="${esc(libelles[i])} : ${esc(eur(v))}"><title>${esc(libelles[i])} : ${esc(eur(v))}</title></circle>`;
    if (libelles.length <= 12 || i % 2 === 0) svg += `<text x="${x(i)}" y="${H - 8}" text-anchor="middle" class="g-axe">${esc(libelles[i])}</text>`;
  });
  const n = valeurs.length - 1;
  svg += `<circle cx="${x(n)}" cy="${y(valeurs[n])}" r="4.5" fill="${couleur}" stroke="${C.surface}" stroke-width="2"/>
    <text x="${x(n) + 10}" y="${y(valeurs[n]) + 4}" class="g-valeur">${esc(eur0(valeurs[n]))}</text>`;
  return `<figure class="graphique">${titre ? `<figcaption>${esc(titre)}</figcaption>` : ''}<svg viewBox="0 0 ${L} ${H}" role="img" aria-label="${esc(titre || 'Graphique')}">${svg}</svg></figure>`;
}

// Répartition : barres horizontales triées, 5 postes + « Autres », valeur et part en fin de barre
export function barresH({ items, couleur = C.depense, titre, max = 5 }) {
  const tri = items.filter((x) => x.valeur > 0).sort((a, b) => b.valeur - a.valeur);
  const lignes = tri.length > max + 1 ? [...tri.slice(0, max), { nom: 'Autres', valeur: tri.slice(max).reduce((s, x) => s + x.valeur, 0) }] : tri;
  const total = tri.reduce((s, x) => s + x.valeur, 0) || 1;
  const plus = Math.max(...lignes.map((x) => x.valeur), 1);
  if (!lignes.length) return `<figure class="graphique">${titre ? `<figcaption>${esc(titre)}</figcaption>` : ''}<p class="g-vide">Aucune donnée sur la période</p></figure>`;
  return `<figure class="graphique">${titre ? `<figcaption>${esc(titre)}</figcaption>` : ''}
    <div class="g-barres" role="list">${lignes.map((x) => `<div class="g-ligne" role="listitem" data-tip="${esc(x.nom)} : ${esc(eur(x.valeur))}">
      <span class="g-nom">${esc(x.nom)}</span>
      <span class="g-piste"><i style="width:${Math.max(1, 100 * x.valeur / plus)}%;background:${couleur}"></i></span>
      <span class="g-val">${esc(eur0(x.valeur))} <small>${Math.round(100 * x.valeur / total)}&nbsp;%</small></span></div>`).join('')}</div></figure>`;
}

// Répartition en anneau (part du total) : 4 postes au plus + « Autres » en gris.
// Chaque anneau décline la couleur de son sens, du plus foncé (premier poste) au
// plus clair : un lecteur reconnaît recettes et dépenses sans lire la légende.
export const RAMPES = {
  recette: ['#0A3A5A', '#1B77B0', '#62AEE0', '#BCDEF4'],
  depense: ['#6A2208', '#C23E10', '#EE7D4C', '#F8C2A6'],
};
const GRIS_AUTRES = '#6E6764';
export function anneau({ items, titre, sens = 'recette', max = 4 }) {
  const PALETTE_SECTEURS = RAMPES[sens] || RAMPES.recette;
  const tri = items.filter((x) => x.valeur > 0).sort((a, b) => b.valeur - a.valeur);
  const parts = tri.length > max + 1 ? [...tri.slice(0, max), { nom: 'Autres', valeur: tri.slice(max).reduce((s, x) => s + x.valeur, 0), autres: true }] : tri;
  const total = tri.reduce((s, x) => s + x.valeur, 0);
  const tete = titre ? `<figcaption>${esc(titre)}</figcaption>` : '';
  if (!total) return `<figure class="graphique">${tete}<p class="g-vide">Aucune donnée sur la période</p></figure>`;
  const R = 80, r = 52, cx = 90, cy = 90;
  let a = -Math.PI / 2, svg = '';
  const pt = (rad, ang) => `${(cx + rad * Math.cos(ang)).toFixed(2)},${(cy + rad * Math.sin(ang)).toFixed(2)}`;
  parts.forEach((x, i) => {
    const coul = x.autres ? GRIS_AUTRES : PALETTE_SECTEURS[i % PALETTE_SECTEURS.length];
    const ang = (2 * Math.PI * x.valeur) / total;
    const tip = `${x.nom} : ${eur(x.valeur)} (${Math.round(100 * x.valeur / total)} %)`;
    if (parts.length === 1) svg += `<circle cx="${cx}" cy="${cy}" r="${(R + r) / 2}" fill="none" stroke="${coul}" stroke-width="${R - r}" data-tip="${esc(tip)}"><title>${esc(tip)}</title></circle>`;
    else {
      const b = a + ang, grand = ang > Math.PI ? 1 : 0;
      svg += `<path d="M${pt(R, a)}A${R},${R} 0 ${grand} 1 ${pt(R, b)}L${pt(r, b)}A${r},${r} 0 ${grand} 0 ${pt(r, a)}Z" fill="${coul}" stroke="${C.surface}" stroke-width="2" stroke-linejoin="round" data-tip="${esc(tip)}"><title>${esc(tip)}</title></path>`;
      a = b;
    }
    x.couleur = coul;
  });
  svg += `<text x="${cx}" y="${cy - 2}" text-anchor="middle" class="g-centre">${esc(eur0(total))}</text><text x="${cx}" y="${cy + 15}" text-anchor="middle" class="g-axe">total</text>`;
  return `<figure class="graphique">${tete}<div class="g-anneau"><svg viewBox="0 0 180 180" role="img" aria-label="${esc(titre || 'Répartition')}">${svg}</svg>
    <ul class="g-parts">${parts.map((x) => `<li data-tip="${esc(x.nom)} : ${esc(eur(x.valeur))}"><i style="background:${x.couleur}"></i><span class="g-nom">${esc(x.nom)}</span><b>${Math.round(100 * x.valeur / total)}&nbsp;%</b><small>${esc(eur0(x.valeur))}</small></li>`).join('')}</ul></div></figure>`;
}

// Jauge : part d'un objectif (cotisations encaissées sur l'exigible)
export function jauge({ valeur, cible, titre, detail }) {
  const p = cible > 0 ? Math.min(100, Math.round(100 * valeur / cible)) : 0;
  const coul = C.recette;
  return `<div class="g-jauge"><div class="g-jauge-tete"><span>${esc(titre)}</span><b>${p}&nbsp;%</b></div>
    <div class="g-piste g-piste-jauge" role="meter" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${p}" aria-label="${esc(titre)}"><i style="width:${p}%;background:${coul}"></i></div>
    ${detail ? `<small>${detail}</small>` : ''}</div>`;
}

// Budget : réalisé sur prévu par poste, dépassement signalé par une étiquette
export function budgetBarres({ lignes, titre }) {
  if (!lignes.length) return '';
  return `<figure class="graphique">${titre ? `<figcaption>${esc(titre)}</figcaption>` : ''}
    <div class="g-barres">${lignes.map((b) => {
      const taux = b.prevu > 0 ? Math.round(100 * b.realise / b.prevu) : 0;
      const coul = b.sens === 'recette' ? C.recette : C.depense;
      return `<div class="g-ligne" data-tip="${esc(b.nom)} : ${esc(eur(b.realise))} sur ${esc(eur(b.prevu))}">
        <span class="g-nom">${esc(b.nom)}</span>
        <span class="g-piste"><i style="width:${Math.min(100, taux)}%;background:${coul}"></i></span>
        <span class="g-val">${taux}&nbsp;%${b.sens === 'depense' && taux > 100 ? ' <em class="g-depasse">dépassé</em>' : ''}</span></div>`;
    }).join('')}</div></figure>`;
}

// ---------- Calculs ----------
const signe = (t) => (t.sens === 'recette' ? 1 : -1) * Number(t.montant);

// Mois (« AAAA-MM ») de debut à fin inclus
export function moisEntre(debut, fin) {
  const r = []; let [a, m] = debut.slice(0, 7).split('-').map(Number);
  const [af, mf] = fin.slice(0, 7).split('-').map(Number);
  while (a < af || (a === af && m <= mf)) { r.push(`${a}-${String(m).padStart(2, '0')}`); m++; if (m > 12) { m = 1; a++; } }
  return r;
}
export const libelleMois = (cle, avecAnnee = false) => MOIS_COURTS[Number(cle.slice(5, 7)) - 1] + (avecAnnee ? ' ' + cle.slice(2, 4) : '');

// Recettes, dépenses et solde de fin de mois, à partir des soldes de départ et de toutes les opérations
export function serieMensuelle(txs, comptes, debut, fin) {
  const mois = moisEntre(debut, fin);
  const depart = comptes.reduce((s, c) => s + Number(c.solde_initial || 0), 0);
  const avant = txs.filter((t) => t.date_op < mois[0] + '-01').reduce((s, t) => s + signe(t), 0);
  let solde = depart + avant;
  return mois.map((m) => {
    const l = txs.filter((t) => t.date_op.startsWith(m));
    // Un virement interne (caisse <-> banque) change les soldes, jamais les recettes ni les dépenses
    const rec = l.filter((t) => t.sens === 'recette' && !t.virement).reduce((s, t) => s + Number(t.montant), 0);
    const dep = l.filter((t) => t.sens === 'depense' && !t.virement).reduce((s, t) => s + Number(t.montant), 0);
    solde += l.reduce((s, t) => s + signe(t), 0);
    return { mois: m, rec, dep, solde };
  });
}

// Réserve : nombre de mois de dépenses couverts par la trésorerie (moyenne des 12 derniers mois)
export function reserveEnMois(solde, serie) {
  const d = serie.slice(-12).map((x) => x.dep).filter((v) => v > 0);
  const moyenne = d.length ? d.reduce((s, v) => s + v, 0) / d.length : 0;
  return moyenne > 0 ? solde / moyenne : null;
}

// Survol : une bulle suit la souris sur tout élément [data-tip]
export function brancherInfobulles(racine = document) {
  let bulle = document.getElementById('g-bulle');
  if (!bulle) { bulle = document.createElement('div'); bulle.id = 'g-bulle'; bulle.setAttribute('role', 'tooltip'); bulle.hidden = true; document.body.appendChild(bulle); }
  racine.querySelectorAll('[data-tip]').forEach((el) => {
    el.addEventListener('mousemove', (e) => {
      bulle.textContent = el.dataset.tip; bulle.hidden = false;
      const x = Math.min(e.clientX + 14, window.innerWidth - bulle.offsetWidth - 8);
      bulle.style.left = x + 'px'; bulle.style.top = (e.clientY - bulle.offsetHeight - 10) + 'px';
    });
    el.addEventListener('mouseleave', () => { bulle.hidden = true; });
  });
}

export const CSS_GRAPHIQUES = `
.graphique{margin:0;display:flex;flex-direction:column;gap:6px;min-width:0}
.graphique figcaption{font-weight:600;font-size:14px;line-height:20px;color:${C.encre}}
.graphique svg{width:100%;height:auto;display:block;overflow:visible}
.g-axe{font-family:inherit;font-size:11px;fill:${C.encre2};font-variant-numeric:tabular-nums}
.g-valeur{font-family:inherit;font-weight:600;font-size:12px;fill:${C.encre}}
.g-legende{display:flex;gap:14px;font-size:12px;color:${C.encre2};flex-wrap:wrap}
.g-legende span{display:inline-flex;align-items:center;gap:6px}
.g-legende i{width:10px;height:10px;border-radius:2px;display:inline-block}
.g-barres{display:flex;flex-direction:column;gap:8px}
.g-ligne{display:grid;grid-template-columns:minmax(90px,34%) 1fr auto;gap:10px;align-items:center;font-size:13px}
.g-nom{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:${C.encre}}
.g-piste{height:8px;border-radius:2px;background:#EFEDEC;overflow:hidden;display:block}
.g-piste i{display:block;height:100%;border-radius:0 2px 2px 0}
.g-piste-jauge{height:12px}
.g-val{font-variant-numeric:tabular-nums;color:${C.encre};white-space:nowrap;text-align:right}
.g-val small{color:${C.encre2}}
.g-depasse{font-style:normal;font-size:11px;font-weight:600;color:#410002;background:#FFDAD6;border:1px solid #F2A8A0;border-radius:10000px;padding:0 6px}
.g-jauge{display:flex;flex-direction:column;gap:6px}
.g-jauge-tete{display:flex;justify-content:space-between;font-size:13px;color:${C.encre2}}
.g-jauge-tete b{color:${C.encre};font-size:15px}
.g-jauge small{font-size:12px;color:${C.encre2}}
.g-vide{color:${C.encre2};font-size:13px;margin:0}
.g-anneau{display:grid;grid-template-columns:minmax(120px,170px) 1fr;gap:16px;align-items:center}
.g-anneau svg{max-width:170px}
.g-centre{font-family:inherit;font-weight:600;font-size:15px;fill:${C.encre}}
.g-parts{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:6px;font-size:13px;min-width:0}
.g-parts li{display:grid;grid-template-columns:12px 1fr auto auto;gap:8px;align-items:center}
.g-parts i{width:12px;height:12px;border-radius:2px;display:block}
.g-parts b{font-variant-numeric:tabular-nums}
.g-parts small{color:${C.encre2};font-variant-numeric:tabular-nums;min-width:64px;text-align:right}
@media (max-width:420px){.g-anneau{grid-template-columns:1fr}.g-anneau svg{margin:0 auto}}
#g-bulle{position:fixed;z-index:200;pointer-events:none;background:#fff;color:#1C1B1A;font-size:12px;line-height:16px;padding:5px 11px 7px;border-radius:4px;max-width:280px;box-shadow:0 0 2px rgba(0,0,0,.12),0 8px 16px rgba(0,0,0,.14)}
`;
