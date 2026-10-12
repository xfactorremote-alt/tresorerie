// Envoi Web Push sans bibliothèque : signature VAPID (RFC 8292) et chiffrement du message (RFC 8291, aes128gcm).
// Écrit en JavaScript pur (WebCrypto) pour tourner à l'identique dans Deno (Supabase) et dans Node (tests).

const enc = new TextEncoder();
export const b64url = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
export const deB64url = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
const concat = (...parts) => { const out = new Uint8Array(parts.reduce((t, p) => t + p.length, 0)); let i = 0; for (const p of parts) { out.set(p, i); i += p.length; } return out; };

// Nouvelle paire de clés VAPID : la publique (format brut, base64url) et la privée (JWK)
export async function creerClesVapid() {
  const k = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
  return { publique: b64url(await crypto.subtle.exportKey('raw', k.publicKey)), privee: await crypto.subtle.exportKey('jwk', k.privateKey) };
}

// Jeton VAPID pour le service de notification (Google, Mozilla, Apple…) qui héberge l'abonnement
export async function enteteVapid(endpoint, cles, contact) {
  const aud = new URL(endpoint).origin;
  const corps = b64url(enc.encode(JSON.stringify({ typ: 'JWT', alg: 'ES256' }))) + '.' +
    b64url(enc.encode(JSON.stringify({ aud, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: contact })));
  const cle = await crypto.subtle.importKey('jwk', cles.privee, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, cle, enc.encode(corps));
  return `vapid t=${corps}.${b64url(sig)}, k=${cles.publique}`;
}

const hkdf = async (sel, ikm, info, octets) => {
  const k = await crypto.subtle.importKey('raw', ikm, 'HKDF', false, ['deriveBits']);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: sel, info }, k, octets * 8));
};

// Chiffre le message pour un abonnement (clés p256dh et auth fournies par le navigateur)
export async function chiffrer(texte, p256dh, auth) {
  const uaPublique = deB64url(p256dh), secretAuth = deB64url(auth);
  const eph = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
  const asPublique = new Uint8Array(await crypto.subtle.exportKey('raw', eph.publicKey));
  const cleUa = await crypto.subtle.importKey('raw', uaPublique, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const secretEcdh = new Uint8Array(await crypto.subtle.deriveBits({ name: 'ECDH', public: cleUa }, eph.privateKey, 256));
  const ikm = await hkdf(secretAuth, secretEcdh, concat(enc.encode('WebPush: info\0'), uaPublique, asPublique), 32);
  const sel = crypto.getRandomValues(new Uint8Array(16));
  const cek = await hkdf(sel, ikm, enc.encode('Content-Encoding: aes128gcm\0'), 16);
  const nonce = await hkdf(sel, ikm, enc.encode('Content-Encoding: nonce\0'), 12);
  const cle = await crypto.subtle.importKey('raw', cek, 'AES-GCM', false, ['encrypt']);
  const chiffre = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, cle, concat(enc.encode(texte), new Uint8Array([2]))));
  const rs = new Uint8Array(4); new DataView(rs.buffer).setUint32(0, 4096);
  return concat(sel, rs, new Uint8Array([asPublique.length]), asPublique, chiffre);
}

// Envoie un message ; renvoie le code HTTP du service (201 = accepté, 404/410 = abonnement disparu)
export async function envoyer(abonnement, message, cles, contact) {
  const corps = await chiffrer(JSON.stringify(message), abonnement.p256dh, abonnement.auth);
  const r = await fetch(abonnement.endpoint, {
    method: 'POST',
    headers: { Authorization: await enteteVapid(abonnement.endpoint, cles, contact), TTL: '86400', Urgency: 'normal',
      'Content-Encoding': 'aes128gcm', 'Content-Type': 'application/octet-stream' },
    body: corps,
  });
  return r.status;
}
