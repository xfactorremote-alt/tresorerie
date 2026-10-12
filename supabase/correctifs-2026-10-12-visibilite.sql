-- Correctif du 12 octobre 2026 : un rendez-vous est visible par tous les membres, sauf exception
-- L'application Android n'envoyait pas la case « Visible de tous les membres » quand elle était cochée
-- (valeur égale à sa valeur par défaut) : la base appliquait la sienne, « non visible ».
-- 1. Par défaut, un rendez-vous est désormais visible par tous les membres.
alter table public.projects alter column visible_adherents set default true;
-- 2. Réparation en production (le 12 octobre 2026) : les rendez-vous créés depuis le téléphone le 11 octobre
--    ont été rendus visibles, comme le trésorier les avait cochés :
--    update public.projects set visible_adherents = true where not visible_adherents and created_at::date = '2026-10-11';
