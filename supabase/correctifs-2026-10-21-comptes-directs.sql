-- Comptes créés directement par l'administrateur (fonction creer-comptes) : pas d'e-mail d'invitation,
-- donc pas de limite d'envoi ; mot de passe provisoire à remplacer à la première connexion.
alter table profiles add column if not exists mdp_provisoire boolean not null default false;

-- La personne connectée a choisi son propre mot de passe : on lève l'obligation (elle ne peut agir que sur elle-même)
create or replace function mot_de_passe_definitif() returns void
language sql security definer set search_path = public as $$
  update profiles set mdp_provisoire = false where id = auth.uid()
$$;
revoke all on function mot_de_passe_definitif() from public;
grant execute on function mot_de_passe_definitif() to authenticated;
