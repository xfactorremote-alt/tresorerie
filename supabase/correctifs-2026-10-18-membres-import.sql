-- =====================================================================
-- Fiche des membres : nationalité, pupitre, statut (18 octobre 2026)
-- Nouvelle structure d'import : prénom;nom;jour;mois;profession;whatsapp;email;accord;nationalité;pupitre;statut
-- Lecture limitée comme le reste de la fiche (voir_membres, gerer_membres, gerer_cotisations, le membre lui-même).
-- =====================================================================
alter table public.members add column if not exists nationalite text check (length(nationalite) <= 60);
alter table public.members add column if not exists pupitre text check (length(pupitre) <= 40);   -- Soprane, Alto, Ténor, Basse, Musicien…
alter table public.members add column if not exists statut text check (length(statut) <= 40);     -- Choriste, Musicien, Sympathisant… (actif / inactif reste la case « Membre actif »)
