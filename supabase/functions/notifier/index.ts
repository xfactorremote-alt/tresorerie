// Fonction « notifier » : envoie les notifications en attente (file notifications_push) à chaque appareil abonné.
// Appelée par la base (déclencheurs et tâche du matin). Elle ne reçoit rien d'extérieur : un appel de trop ne fait
// qu'envoyer ce qui attend déjà. Clés VAPID créées au premier passage et gardées dans push_cles.
import { createClient } from 'npm:@supabase/supabase-js@2.45.4';
import { creerClesVapid, envoyer } from './webpush.js';

const CONTACT = 'https://xfactorremote-alt.github.io/tresorerie/';
const sb = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false } });

async function cles() {
  const { data } = await sb.from('push_cles').select('publique, privee').eq('id', 1).maybeSingle();
  if (data) return data;
  const neuves = await creerClesVapid();
  await sb.from('push_cles').upsert({ id: 1, ...neuves }, { onConflict: 'id', ignoreDuplicates: true });
  const { data: relues } = await sb.from('push_cles').select('publique, privee').eq('id', 1).single();
  return relues;
}

Deno.serve(async () => {
  const k = await cles();
  const depuis = new Date(Date.now() - 2 * 86400e3).toISOString();
  const { data: file } = await sb.from('notifications_push').select('id').is('envoyee_le', null).gt('cree_le', depuis).order('id').limit(50);
  if (!file?.length) return Response.json({ cle: k.publique, envoyees: 0 });
  // Prise en charge : une notification n'est envoyée qu'une fois, même si deux appels se croisent
  const { data: prises } = await sb.from('notifications_push').update({ envoyee_le: new Date().toISOString() })
    .in('id', file.map((n) => n.id)).is('envoyee_le', null).select('*');
  const { data: abos } = await sb.from('abonnements_push').select('*').eq('actif', true);
  let envois = 0;
  for (const n of prises || []) {
    const cibles = (abos || []).filter((a) => !n.member_id || a.member_id === n.member_id);
    const message = { titre: n.titre, texte: n.texte || '', url: n.url || './', tag: n.cle };
    await Promise.allSettled(cibles.map(async (a) => {
      let code = 0;
      try { code = await envoyer(a, message, k, CONTACT); } catch (_) { code = 0; }
      if (code >= 200 && code < 300) { envois++; await sb.from('abonnements_push').update({ dernier_envoi: new Date().toISOString(), echecs: 0 }).eq('id', a.id); }
      else if (code === 404 || code === 410) await sb.from('abonnements_push').update({ actif: false }).eq('id', a.id);
      else await sb.from('abonnements_push').update({ echecs: a.echecs + 1, actif: a.echecs + 1 < 5 }).eq('id', a.id);
    }));
    await sb.from('notifications_push').update({ nb_envois: cibles.length }).eq('id', n.id);
  }
  return Response.json({ cle: k.publique, envoyees: prises?.length || 0, envois });
});
