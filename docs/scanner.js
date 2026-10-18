// Scanner de documents (comme Scanner.kt d'Android, mêmes calculs et mêmes noms) :
// photo → détection du papier → coins ajustables (avec loupe) → redressement de la perspective
// → filtres (Magique, Original, Gris, Noir et blanc) et rotation → JPEG.
// Les calculs travaillent sur { w, h, data } (RGBA, comme ImageData) : testables sans navigateur.

// ---------------------------------------------------------------------
// Calculs
// ---------------------------------------------------------------------
const luma = (r, g, b) => ((r * 299 + g * 587 + b * 114) / 1000) | 0;
export const COTE_DETECTION = 256;   // l'image est réduite à 256 px de côté pour trouver le papier
export const COTE_SORTIE = 1600;     // côté le plus long du document redressé

// Coins du papier [haut-gauche, haut-droit, bas-droit, bas-gauche] en pixels de l'image ; trouve = faux → tout le cadre
export function detecterDocument(img) {
  const { w, h, data } = img;
  const k = Math.min(1, COTE_DETECTION / Math.max(w, h));
  const sw = Math.max(1, Math.round(w * k)), sh = Math.max(1, Math.round(h * k));
  const gris = new Uint8Array(sw * sh);
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    const sx = Math.min(w - 1, Math.floor((x + 0.5) / k)), sy = Math.min(h - 1, Math.floor((y + 0.5) / k)), i = (sy * w + sx) * 4;
    gris[y * sw + x] = luma(data[i], data[i + 1], data[i + 2]);
  }
  const flou = flouBoite(gris, sw, sh, 2);
  // Seuil d'Otsu : sépare le papier (clair) du fond
  const hist = new Array(256).fill(0); for (const v of flou) hist[v]++;
  const n = sw * sh; let somme = 0; for (let t = 0; t < 256; t++) somme += t * hist[t];
  let sB = 0, wB = 0, meilleur = 0, seuil = 128;
  for (let t = 0; t < 256; t++) {
    wB += hist[t]; if (!wB) continue; const wF = n - wB; if (!wF) break;
    sB += t * hist[t]; const mB = sB / wB, mF = (somme - sB) / wF, v = wB * wF * (mB - mF) * (mB - mF);
    if (v > meilleur) { meilleur = v; seuil = t; }
  }
  // Plus grande zone claire d'un seul tenant
  const marque = new Int32Array(n).fill(-1); let plusGrande = -1, taille = 0; const pile = new Int32Array(n);
  for (let d = 0, zone = 0; d < n; d++) {
    if (flou[d] <= seuil || marque[d] >= 0) continue;
    let haut = 0, t = 0; pile[haut++] = d; marque[d] = zone;
    while (haut) {
      const p = pile[--haut]; t++; const x = p % sw, y = (p - x) / sw;
      if (x > 0 && flou[p - 1] > seuil && marque[p - 1] < 0) { marque[p - 1] = zone; pile[haut++] = p - 1; }
      if (x < sw - 1 && flou[p + 1] > seuil && marque[p + 1] < 0) { marque[p + 1] = zone; pile[haut++] = p + 1; }
      if (y > 0 && flou[p - sw] > seuil && marque[p - sw] < 0) { marque[p - sw] = zone; pile[haut++] = p - sw; }
      if (y < sh - 1 && flou[p + sw] > seuil && marque[p + sw] < 0) { marque[p + sw] = zone; pile[haut++] = p + sw; }
    }
    if (t > taille) { taille = t; plusGrande = zone; } zone++;
  }
  const cadre = [[0, 0], [w - 1, 0], [w - 1, h - 1], [0, h - 1]];
  const part = taille / n;
  if (plusGrande < 0 || part < 0.1) return { coins: cadre, trouve: false };
  if (part > 0.97) return { coins: cadre, trouve: true };
  // Coins : extrêmes de x + y et de x − y
  let tl = null, tr = null, br = null, bl = null;
  for (let p = 0; p < n; p++) {
    if (marque[p] !== plusGrande) continue;
    const x = p % sw, y = (p - x) / sw;
    if (!tl || x + y < tl[0] + tl[1]) tl = [x, y];
    if (!br || x + y > br[0] + br[1]) br = [x, y];
    if (!tr || x - y > tr[0] - tr[1]) tr = [x, y];
    if (!bl || x - y < bl[0] - bl[1]) bl = [x, y];
  }
  const vers = ([x, y]) => [Math.min(w - 1, Math.max(0, (x + 0.5) / k)), Math.min(h - 1, Math.max(0, (y + 0.5) / k))];
  return { coins: [tl, tr, br, bl].map(vers), trouve: true };
}

function flouBoite(src, w, h, r) {
  const tmp = new Float32Array(w * h), out = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let s = 0, c = 0; for (let d = -r; d <= r; d++) { const xx = x + d; if (xx >= 0 && xx < w) { s += src[y * w + xx]; c++; } }
    tmp[y * w + x] = s / c;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let s = 0, c = 0; for (let d = -r; d <= r; d++) { const yy = y + d; if (yy >= 0 && yy < h) { s += tmp[yy * w + x]; c++; } }
    out[y * w + x] = Math.round(s / c);
  }
  return out;
}

const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
// Transformation du carré unité vers le quadrilatère (Heckbert)
function carreVersQuad([[x0, y0], [x1, y1], [x2, y2], [x3, y3]]) {
  const dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3, dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;
  let g = 0, h = 0;
  if (Math.abs(dx3) > 1e-9 || Math.abs(dy3) > 1e-9) { const den = dx1 * dy2 - dx2 * dy1; g = (dx3 * dy2 - dx2 * dy3) / den; h = (dx1 * dy3 - dx3 * dy1) / den; }
  return { a: x1 - x0 + g * x1, b: x3 - x0 + h * x3, c: x0, d: y1 - y0 + g * y1, e: y3 - y0 + h * y3, f: y0, g, h };
}

// Document redressé : coins → rectangle (taille d'après les côtés mesurés, 1600 px au plus)
export function redresser(img, coins, max = COTE_SORTIE) {
  const [tl, tr, br, bl] = coins;
  let W = Math.max(dist(tl, tr), dist(bl, br)), H = Math.max(dist(tl, bl), dist(tr, br));
  const s = Math.min(1, max / Math.max(W, H, 1));
  W = Math.max(1, Math.round(W * s)); H = Math.max(1, Math.round(H * s));
  const m = carreVersQuad(coins), out = new Uint8ClampedArray(W * H * 4), { w, h, data } = img;
  for (let j = 0; j < H; j++) {
    const v = (j + 0.5) / H;
    for (let i = 0; i < W; i++) {
      const u = (i + 0.5) / W, z = m.g * u + m.h * v + 1;
      const X = Math.min(w - 1, Math.max(0, (m.a * u + m.b * v + m.c) / z - 0.5)), Y = Math.min(h - 1, Math.max(0, (m.d * u + m.e * v + m.f) / z - 0.5));
      const x0 = Math.floor(X), y0 = Math.floor(Y), x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1), fx = X - x0, fy = Y - y0;
      const o = (j * W + i) * 4;
      for (let c = 0; c < 3; c++) {
        const p = (yy, xx) => data[(yy * w + xx) * 4 + c];
        out[o + c] = (p(y0, x0) * (1 - fx) + p(y0, x1) * fx) * (1 - fy) + (p(y1, x0) * (1 - fx) + p(y1, x1) * fx) * fy;
      }
      out[o + 3] = 255;
    }
  }
  return { w: W, h: H, data: out };
}

// Fond du papier : luminosité locale du blanc (maximum par bloc, élargi de deux blocs pour effacer les lettres, lissé),
// interpolée entre les centres des blocs ; plus la teinte du papier (moyenne des blocs les plus clairs), pour la balance des blancs
function fondPapier(img) {
  const { w, h, data } = img, B = Math.max(8, Math.round(Math.min(w, h) / 24)), gw = Math.ceil(w / B), gh = Math.ceil(h / B);
  let g = new Float32Array(gw * gh); const rgb = new Float32Array(gw * gh * 3);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4, v = luma(data[i], data[i + 1], data[i + 2]), q = ((y / B) | 0) * gw + ((x / B) | 0);
    if (v > g[q]) { g[q] = v; rgb[q * 3] = data[i]; rgb[q * 3 + 1] = data[i + 1]; rgb[q * 3 + 2] = data[i + 2]; }
  }
  // Teinte du papier : blocs dont le blanc est dans la moitié la plus claire
  const tries = [...g].sort((a, b) => a - b), mediane = tries[tries.length >> 1]; const t = [0, 0, 0]; let nt = 0;
  for (let q = 0; q < gw * gh; q++) if (g[q] >= mediane) { t[0] += rgb[q * 3]; t[1] += rgb[q * 3 + 1]; t[2] += rgb[q * 3 + 2]; nt++; }
  const lt = luma(t[0] / nt, t[1] / nt, t[2] / nt) || 1, gains = t.map((c) => lt / Math.max(c / nt, 1));
  const voisins = (src, f) => { const o = new Float32Array(gw * gh); for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) { const l = []; for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) { const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < gw && yy < gh) l.push(src[yy * gw + xx]); } o[y * gw + x] = f(l); } return o; };
  g = voisins(voisins(voisins(g, (l) => Math.max(...l)), (l) => Math.max(...l)), (l) => l.reduce((a, b) => a + b, 0) / l.length);
  const fond = (x, y) => {
    const fx = Math.min(gw - 1, Math.max(0, (x + 0.5) / B - 0.5)), fy = Math.min(gh - 1, Math.max(0, (y + 0.5) / B - 0.5));
    const x0 = Math.floor(fx), y0 = Math.floor(fy), x1 = Math.min(gw - 1, x0 + 1), y1 = Math.min(gh - 1, y0 + 1), ax = fx - x0, ay = fy - y0;
    return (g[y0 * gw + x0] * (1 - ax) + g[y0 * gw + x1] * ax) * (1 - ay) + (g[y1 * gw + x0] * (1 - ax) + g[y1 * gw + x1] * ax) * ay;
  };
  return { fond, gains };
}

export const FILTRES = [['magique', 'Magique'], ['original', 'Original'], ['gris', 'Gris'], ['nb', 'Noir et blanc']];
// Magique : le papier devient blanc (ombres et teinte retirées), l'encre plus nette, les couleurs gardées
export function ameliorer(img, mode = 'magique') {
  const { w, h, data } = img, out = new Uint8ClampedArray(data.length);
  if (mode === 'original') { out.set(data); return { w, h, data: out }; }
  const papier = mode === 'gris' ? null : fondPapier(img);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4;
    if (mode === 'gris') { const v = luma(data[i], data[i + 1], data[i + 2]); out[i] = out[i + 1] = out[i + 2] = v; out[i + 3] = 255; continue; }
    // Balance des blancs, puis division par le blanc local : le papier devient blanc ; niveaux resserrés (encre plus franche)
    const k = 255 / Math.max(papier.fond(x, y), 48);
    const niv = (c) => Math.min(255, Math.max(0, Math.round((data[i + c] * papier.gains[c] * k - 30) * 255 / 200)));
    const r = niv(0), g = niv(1), b = niv(2);
    if (mode === 'nb') { const v = luma(r, g, b) > 165 ? 255 : 0; out[i] = out[i + 1] = out[i + 2] = v; }
    else { out[i] = r; out[i + 1] = g; out[i + 2] = b; }
    out[i + 3] = 255;
  }
  return { w, h, data: out };
}

// Quart de tour dans le sens des aiguilles d'une montre
export function tourner(img) {
  const { w, h, data } = img, out = new Uint8ClampedArray(data.length);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const s = (y * w + x) * 4, d = (x * h + (h - 1 - y)) * 4;
    out[d] = data[s]; out[d + 1] = data[s + 1]; out[d + 2] = data[s + 2]; out[d + 3] = data[s + 3];
  }
  return { w: h, h: w, data: out };
}

// ---------------------------------------------------------------------
// Fenêtre plein écran (navigateur seulement)
// ---------------------------------------------------------------------
const COTE_TRAVAIL = 2000;   // la photo est réduite à 2000 px pour les calculs (au-delà, rien de plus lisible)

function versCanvas(img) {
  const c = document.createElement('canvas'); c.width = img.w; c.height = img.h;
  c.getContext('2d').putImageData(new ImageData(img.data, img.w, img.h), 0, 0); return c;
}
async function lirePhoto(file) {
  const bmp = await createImageBitmap(file);   // tient compte de l'orientation de l'appareil (EXIF)
  const k = Math.min(1, COTE_TRAVAIL / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas'); c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
  const x = c.getContext('2d'); x.drawImage(bmp, 0, 0, c.width, c.height); bmp.close?.();
  const d = x.getImageData(0, 0, c.width, c.height); return { w: c.width, h: c.height, data: d.data };
}
const choisirPhoto = () => new Promise((ok) => {
  const i = document.createElement('input'); i.type = 'file'; i.accept = 'image/*'; i.setAttribute('capture', 'environment');
  i.addEventListener('change', () => ok(i.files[0] || null), { once: true });
  i.addEventListener('cancel', () => ok(null), { once: true });
  i.click();
});

// Ouvre l'appareil photo puis l'éditeur ; renvoie un File JPEG (ou null si abandon)
export async function scannerDocument() {
  const fichier = await choisirPhoto(); if (!fichier) return null;
  let photo;
  try { photo = await lirePhoto(fichier); } catch { throw new Error('Photo illisible : réessayez ou choisissez une image JPEG ou PNG'); }
  return new Promise((resoudre) => {
    const dlg = document.createElement('dialog'); dlg.className = 'scanner'; dlg.setAttribute('aria-label', 'Scanner le document');
    document.body.append(dlg);
    let { coins, trouve } = detecterDocument(photo), redresse = null, filtre = 'magique', etape = 'cadrer';
    const finir = (f) => { dlg.close(); dlg.remove(); resoudre(f); };
    dlg.addEventListener('cancel', (e) => { e.preventDefault(); finir(null); });
    const apercu = versCanvas(photo);
    const rendre = () => {
      dlg.innerHTML = `<div class="scanner-tete"><button type="button" class="scanner-fermer" aria-label="Fermer">✕</button>
          <h2>${etape === 'cadrer' ? 'Cadrer le document' : 'Améliorer'}</h2><span class="scanner-etape">${etape === 'cadrer' ? '1' : '2'} / 2</span></div>
        <div class="scanner-zone">${etape === 'cadrer'
          ? `<div class="scanner-cadre"><canvas class="scanner-image"></canvas><svg class="scanner-coins" aria-hidden="true"><polygon></polygon>${['haut gauche', 'haut droit', 'bas droit', 'bas gauche'].map((l, k) => `<circle data-k="${k}" r="11" tabindex="0" role="button" aria-label="Coin ${l} : flèches pour le déplacer"></circle>`).join('')}</svg><canvas class="scanner-loupe" width="120" height="120" hidden></canvas></div>`
          : '<canvas class="scanner-image scanner-resultat"></canvas>'}</div>
        ${etape === 'cadrer'
          ? `<p class="scanner-aide">${trouve ? 'Papier repéré. Faites glisser les coins sur les bords du papier si besoin.' : 'Papier non repéré : faites glisser les quatre coins sur les bords du papier.'}</p>
            <div class="scanner-actions"><button type="button" class="btn-texte" id="sc-reprendre">Reprendre</button><button type="button" class="btn-texte" id="sc-cadre">Tout le cadre</button><button type="button" class="btn-primaire" id="sc-valider">Valider le cadrage</button></div>`
          : `<div class="scanner-filtres" role="radiogroup" aria-label="Filtre">${FILTRES.map(([k, l]) => `<button type="button" role="radio" aria-checked="${filtre === k}" data-f="${k}">${l}</button>`).join('')}<button type="button" id="sc-tourner" aria-label="Tourner d’un quart de tour">↻ Tourner</button></div>
            <div class="scanner-actions"><button type="button" class="btn-texte" id="sc-recadrer">‹ Recadrer</button><button type="button" class="btn-primaire" id="sc-utiliser">Utiliser ce document</button></div>`}`;
      dlg.querySelector('.scanner-fermer').addEventListener('click', () => finir(null));
      if (etape === 'cadrer') brancherCadrage(); else brancherResultat();
    };
    const brancherCadrage = () => {
      const cv = dlg.querySelector('.scanner-image'), svg = dlg.querySelector('.scanner-coins'), loupe = dlg.querySelector('.scanner-loupe');
      const zone = dlg.querySelector('.scanner-zone');
      const ajuster = () => {
        const k = Math.min((zone.clientWidth - 32) / photo.w, (zone.clientHeight - 32) / photo.h);
        cv.width = Math.round(photo.w * k); cv.height = Math.round(photo.h * k);
        cv.getContext('2d').drawImage(apercu, 0, 0, cv.width, cv.height);
        svg.setAttribute('width', cv.width); svg.setAttribute('height', cv.height); svg.setAttribute('viewBox', `0 0 ${cv.width} ${cv.height}`);
        dessiner();
      };
      const echelle = () => cv.width / photo.w;
      const dessiner = () => {
        const e = echelle();
        svg.querySelector('polygon').setAttribute('points', coins.map(([x, y]) => `${x * e},${y * e}`).join(' '));
        svg.querySelectorAll('circle').forEach((c, k) => { c.setAttribute('cx', coins[k][0] * e); c.setAttribute('cy', coins[k][1] * e); });
      };
      let actif = -1;
      const point = (ev) => { const r = cv.getBoundingClientRect(); return [(ev.clientX - r.left) / echelle(), (ev.clientY - r.top) / echelle()]; };
      // Loupe : la zone autour du coin, agrandie 2,5 fois, du côté opposé au doigt pour ne pas être cachée
      const montrerLoupe = (cote) => {
        const [x, y] = coins[actif], t = 120, src = t / (echelle() * 2.5), c = loupe.getContext('2d');
        c.clearRect(0, 0, t, t); c.save(); c.beginPath(); c.arc(t / 2, t / 2, t / 2, 0, Math.PI * 2); c.clip();
        c.fillStyle = '#000'; c.fillRect(0, 0, t, t);
        c.drawImage(apercu, x - src / 2, y - src / 2, src, src, 0, 0, t, t); c.restore();
        c.strokeStyle = '#C23E10'; c.lineWidth = 2; c.beginPath(); c.moveTo(t / 2 - 12, t / 2); c.lineTo(t / 2 + 12, t / 2); c.moveTo(t / 2, t / 2 - 12); c.lineTo(t / 2, t / 2 + 12); c.stroke();
        loupe.style.left = cote === 'gauche' ? '8px' : `${cv.width - t - 8}px`; loupe.style.top = '8px'; loupe.hidden = false;
      };
      const coteLoupe = () => (coins[actif][0] * echelle() > cv.width / 2 ? 'gauche' : 'droite');
      svg.addEventListener('pointerdown', (ev) => {
        const p = point(ev), e = echelle(); let mieux = -1, d = 44 / e;
        coins.forEach((c, k) => { const dd = dist(c, p); if (dd < d) { d = dd; mieux = k; } });
        if (mieux < 0) return;
        actif = mieux; svg.setPointerCapture(ev.pointerId); ev.preventDefault(); montrerLoupe(coteLoupe());
      });
      svg.addEventListener('pointermove', (ev) => {
        if (actif < 0) return;
        const [x, y] = point(ev); coins[actif] = [Math.min(photo.w - 1, Math.max(0, x)), Math.min(photo.h - 1, Math.max(0, y))];
        dessiner(); montrerLoupe(coteLoupe());
      });
      const lacher = () => { actif = -1; loupe.hidden = true; };
      svg.addEventListener('pointerup', lacher); svg.addEventListener('pointercancel', lacher);
      // Clavier : un coin choisi au tabulateur se déplace aux flèches (accessibilité)
      svg.querySelectorAll('circle').forEach((c, k) => c.addEventListener('keydown', (ev) => {
        const pas = Math.max(photo.w, photo.h) / 100, d = { ArrowLeft: [-pas, 0], ArrowRight: [pas, 0], ArrowUp: [0, -pas], ArrowDown: [0, pas] }[ev.key];
        if (!d) return; ev.preventDefault(); actif = k;
        coins[k] = [Math.min(photo.w - 1, Math.max(0, coins[k][0] + d[0])), Math.min(photo.h - 1, Math.max(0, coins[k][1] + d[1]))]; dessiner(); montrerLoupe(coteLoupe());
      }));
      svg.querySelectorAll('circle').forEach((c) => c.addEventListener('blur', lacher));
      dlg.querySelector('#sc-cadre').addEventListener('click', () => { coins = [[0, 0], [photo.w - 1, 0], [photo.w - 1, photo.h - 1], [0, photo.h - 1]]; dessiner(); });
      dlg.querySelector('#sc-reprendre').addEventListener('click', async () => {
        const f = await choisirPhoto(); if (!f) return;
        try { photo = await lirePhoto(f); } catch { return; }
        ({ coins, trouve } = detecterDocument(photo)); apercu.width = photo.w; apercu.height = photo.h;
        apercu.getContext('2d').putImageData(new ImageData(photo.data, photo.w, photo.h), 0, 0); rendre();
      });
      dlg.querySelector('#sc-valider').addEventListener('click', () => { redresse = redresser(photo, coins); etape = 'ameliorer'; rendre(); });
      requestAnimationFrame(ajuster);
      new ResizeObserver(ajuster).observe(zone);
    };
    const brancherResultat = () => {
      const cv = dlg.querySelector('.scanner-resultat');
      const montrer = () => { const r = ameliorer(redresse, filtre); cv.width = r.w; cv.height = r.h; cv.getContext('2d').putImageData(new ImageData(r.data, r.w, r.h), 0, 0); };
      montrer();
      dlg.querySelectorAll('[data-f]').forEach((b) => b.addEventListener('click', () => {
        filtre = b.dataset.f; dlg.querySelectorAll('[data-f]').forEach((x) => x.setAttribute('aria-checked', String(x === b))); montrer();
      }));
      dlg.querySelector('#sc-tourner').addEventListener('click', () => { redresse = tourner(redresse); montrer(); });
      dlg.querySelector('#sc-recadrer').addEventListener('click', () => { etape = 'cadrer'; rendre(); });
      dlg.querySelector('#sc-utiliser').addEventListener('click', () => {
        cv.toBlob((b) => {
          const d = new Date(), p = (n) => String(n).padStart(2, '0');
          finir(new File([b], `scan-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.jpg`, { type: 'image/jpeg' }));
        }, 'image/jpeg', 0.85);
      });
    };
    rendre(); dlg.showModal();
  });
}
