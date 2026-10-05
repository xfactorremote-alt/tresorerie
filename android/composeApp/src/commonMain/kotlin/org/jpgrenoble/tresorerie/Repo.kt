package org.jpgrenoble.tresorerie

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Accès aux données. Tant que Config.kt n'est pas rempli, l'application tourne
// en mode démonstration (données fictives en mémoire, comptes de test).
// Avec Supabase, les droits sont vérifiés par la base (règles RLS).
object Repo {
    val demo: Boolean = Config.SUPABASE_URL.contains("VOTRE") || Config.SUPABASE_ANON_KEY.contains("VOTRE")

    // Session du mode démonstration (avec Supabase, la session vient de client.auth)
    val profilDemo = MutableStateFlow<Profil?>(null)

    val client: SupabaseClient by lazy {
        createSupabaseClient(Config.SUPABASE_URL, Config.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    suspend fun connecter(adresse: String, motDePasse: String) {
        if (demo) { profilDemo.value = Demo.connecter(adresse, motDePasse); return }
        client.auth.signInWith(Email) { email = adresse; password = motDePasse }
    }

    suspend fun deconnecter() {
        if (demo) { profilDemo.value = null; return }
        client.auth.signOut()
    }

    suspend fun profil(): Profil? {
        if (demo) return profilDemo.value?.let { p -> Demo.profilsActuels().firstOrNull { it.id == p.id } }
        val id = client.auth.currentUserOrNull()?.id ?: return null
        return client.from("profiles").select { filter { eq("id", id) } }.decodeSingleOrNull<Profil>()
    }

    suspend fun organisation(): Organisation =
        if (demo) Demo.organisation
        else client.from("organisation").select().decodeSingleOrNull<Organisation>() ?: Organisation()

    suspend fun soldes(): List<Solde> =
        if (demo) Demo.soldes() else client.from("v_soldes").select().decodeList()

    suspend fun comptes(): List<Compte> =
        if (demo) Demo.comptes.filter { it.actif }
        else client.from("accounts").select { filter { eq("actif", true) }; order("nom", Order.ASCENDING) }.decodeList()

    suspend fun categories(): List<Categorie> =
        if (demo) Demo.categories.toList()
        else client.from("categories").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun membres(): List<Membre> =
        if (demo) Demo.membres.toList()
        else client.from("members").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun ecritures(limite: Long = 100): List<Ecriture> =
        if (demo) Demo.ecritures()
        else client.from("transactions").select {
            order("date_op", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
            limit(limite)
        }.decodeList()

    suspend fun toutesEcritures(): List<Ecriture> =
        if (demo) Demo.ecritures()
        else client.from("transactions").select { order("date_op", Order.ASCENDING) }.decodeList()

    suspend fun ajouter(e: NouvelleEcriture): String {
        if (demo) return Demo.ajouter(e, profilDemo.value)
        return client.from("transactions").insert(e) { select() }.decodeSingle<Ecriture>().id
    }

    // Dépense saisie directement : le président la valide après coup
    suspend fun demanderValidation(transactionId: String) {
        if (demo) { Demo.demanderValidation(transactionId, profilDemo.value); return }
        client.postgrest.rpc("demander_validation_operation", buildJsonObject { put("p_transaction", transactionId) })
    }

    suspend fun anniversaires(mois: Int? = null): List<Anniversaire> =
        if (demo) Demo.anniversaires(profilDemo.value, mois)
        else if (mois == null) client.postgrest.rpc("anniversaires_du_mois").decodeList()
        else client.postgrest.rpc("anniversaires_du_mois", buildJsonObject { put("p_mois", mois) }).decodeList()

    suspend fun cotisations(annee: Int): List<Cotisation> =
        if (demo) Demo.cotisations(annee)
        else client.from("v_cotisations").select { filter { eq("annee", annee) } }.decodeList()

    suspend fun genererCotisations(annee: Int): Int =
        if (demo) Demo.genererCotisations(annee, profilDemo.value)
        else client.postgrest.rpc("generer_cotisations", buildJsonObject { put("an", annee) }).decodeAs()

    suspend fun retards(): List<Retard> =
        if (demo) Demo.retards() else client.from("v_justificatifs_en_retard").select().decodeList()

    // ---------- Fichiers ----------
    private fun typeDe(f: Fichier) = when (f.mime) {
        "application/pdf" -> ContentType.Application.Pdf
        "image/png" -> ContentType.Image.PNG
        else -> ContentType.Image.JPEG
    }

    private suspend fun deposer(bucket: String, chemin: String, f: Fichier) {
        if (demo) return
        client.storage.from(bucket).upload(chemin, f.octets) { contentType = typeDe(f) }
    }

    private fun nomFichier(ext: String) = "${aujourdhui().year}/${Clock.System.now().toEpochMilliseconds()}-${(1000..9999).random()}.$ext"

    // ---------- Demandes de dépense ----------
    suspend fun demandes(): List<Demande> =
        if (demo) Demo.demandes(profilDemo.value)
        else client.from("expense_requests").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun profils(): List<ProfilCourt> =
        if (demo) Demo.profilsActuels().map { ProfilCourt(it.id, it.nom) }
        else client.from("profiles").select().decodeList()

    suspend fun creerDemande(d: NouvelleDemande) {
        if (demo) { Demo.creerDemande(d, profilDemo.value); return }
        client.from("expense_requests").insert(d)
    }

    suspend fun validerDemande(d: Demande, signaturePng: ByteArray, monId: String) {
        val chemin = "${d.id}-${Clock.System.now().toEpochMilliseconds()}.png"
        // Empreinte : prouve que la signature porte sur ce montant et cet objet
        val empreinte = sha256(listOf(d.id, d.montant, d.objet, Clock.System.now().toString(), monId).joinToString("|"))
        if (demo) { Demo.changerStatut(d.id, "validee", profilDemo.value, chemin); return }
        deposer("signatures", chemin, Fichier(signaturePng, "image/png", "png"))
        client.from("expense_requests").update({
            set("statut", "validee"); set("signature_path", chemin); set("signature_hash", empreinte)
        }) { filter { eq("id", d.id) } }
    }

    suspend fun refuserDemande(id: String, motif: String) {
        if (demo) { Demo.changerStatut(id, "refusee", profilDemo.value, motif = motif); return }
        client.from("expense_requests").update({ set("statut", "refusee"); set("motif_refus", motif) }) { filter { eq("id", id) } }
    }

    suspend fun annulerDemande(id: String) {
        if (demo) { Demo.changerStatut(id, "annulee", profilDemo.value); return }
        client.from("expense_requests").update({ set("statut", "annulee") }) { filter { eq("id", id) } }
    }

    suspend fun payerDemande(id: String, compteId: String, mode: String, date: String) {
        if (demo) { Demo.payer(id, compteId, mode, date, profilDemo.value); return }
        client.postgrest.rpc("payer_demande", buildJsonObject {
            put("p_id", id); put("p_compte", compteId); put("p_mode", mode); put("p_date", date)
        })
    }

    suspend fun justifierDemande(id: String, f: Fichier) {
        val chemin = nomFichier(f.extension)
        if (demo) { Demo.justifier(id, chemin, f, profilDemo.value); return }
        deposer("justificatifs", chemin, f)
        client.postgrest.rpc("justifier_demande", buildJsonObject {
            put("p_id", id); put("p_chemin", chemin); put("p_mime", f.mime); put("p_ko", f.ko)
        })
    }

    // ---------- Budget et activités ----------
    suspend fun budget(annee: Int): List<LigneBudget> =
        if (demo) Demo.budget(annee)
        else client.from("v_budget_suivi").select { filter { eq("annee", annee) } }.decodeList()

    suspend fun projets(): List<Projet> =
        if (demo) Demo.projets.toList()
        else client.from("projects").select { order("date_debut", Order.DESCENDING) }.decodeList()

    // ---------- Membres ----------
    suspend fun ajouterMembre(m: NouveauMembre): String {
        if (demo) return Demo.ajouterMembre(m, profilDemo.value)
        return client.from("members").insert(m) { select() }.decodeSingle<Membre>().id
    }

    // Rattache un compte à une fiche de membre ; le nom affiché reprend celui de la fiche
    suspend fun lierProfil(profilId: String, membreId: String?, nom: String?) {
        if (demo) { Demo.lierProfil(profilId, membreId, nom, profilDemo.value); return }
        client.from("profiles").update({ set<String?>("member_id", membreId); if (nom != null) set("nom", nom) }) { filter { eq("id", profilId) } }
    }

    suspend fun modifierMonNom(nom: String) {
        if (demo) { Demo.modifierNom(profilDemo.value?.id, nom); return }
        client.postgrest.rpc("modifier_mon_nom", buildJsonObject { put("p_nom", nom) })
    }

    suspend fun changerMotDePasse(motDePasse: String) {
        if (demo) return
        client.auth.updateUser { password = motDePasse }
    }

    fun emailConnecte(): String = if (demo) Demo.emailDe(profilDemo.value?.id) else client.auth.currentUserOrNull()?.email ?: ""

    // ---------- Rapprochement ----------
    suspend fun rapprochements(): List<Rapprochement> =
        if (demo) Demo.rapprochements.toList()
        else client.from("reconciliations").select { order("periode_fin", Order.DESCENDING) }.decodeList()

    suspend fun ecrituresCompte(compteId: String, fin: String): List<Ecriture> =
        if (demo) Demo.ecritures().filter { it.compteId == compteId && it.date <= fin }
        else client.from("transactions").select {
            filter { eq("account_id", compteId); lte("date_op", fin) }
            order("date_op", Order.ASCENDING)
        }.decodeList()

    suspend fun soldePointe(compteId: String): Double =
        if (demo) Demo.soldePointe(compteId)
        else client.postgrest.rpc("solde_pointe", buildJsonObject { put("p_compte", compteId) }).decodeAs()

    suspend fun terminerRapprochement(compteId: String, debut: String, fin: String, soldeReleve: Double, releve: Fichier, nom: String, ecritures: List<String>) {
        val chemin = "$compteId/$fin-${Clock.System.now().toEpochMilliseconds()}.${releve.extension}"
        if (demo) { Demo.terminerRapprochement(compteId, debut, fin, soldeReleve, ecritures, profilDemo.value); return }
        deposer("releves", chemin, releve)
        client.postgrest.rpc("terminer_rapprochement", buildJsonObject {
            put("p_compte", compteId); put("p_debut", debut); put("p_fin", fin); put("p_solde_releve", soldeReleve)
            put("p_chemin", chemin); put("p_nom", nom); put("p_ko", releve.ko)
            putJsonArray("p_ecritures") { ecritures.forEach { add(it) } }
        })
    }

    // ---------- Logo et images ----------
    val logo = MutableStateFlow<ByteArray?>(null)

    // Photo de la bannière d'accueil (même dossier public que le logo)
    val banniere = MutableStateFlow<ByteArray?>(null)

    suspend fun chargerLogo() {
        val org = organisation()
        banniere.value = org.banniere?.let { c -> try { if (demo) Demo.fichiers["logos/$c"] else client.storage.from("logos").downloadPublic(c) } catch (_: Exception) { null } }
        val chemin = org.logo ?: return
        logo.value = if (demo) Demo.fichiers["logos/$chemin"] else client.storage.from("logos").downloadPublic(chemin)
    }

    suspend fun telecharger(bucket: String, chemin: String): ByteArray? =
        if (demo) Demo.fichiers["$bucket/$chemin"] else client.storage.from(bucket).downloadAuthenticated(chemin)

    // ---------- Paramètres (trésorier) ----------
    suspend fun majOrganisation(nom: String, nouveauLogo: Fichier?, nouvelleBanniere: Fichier? = null, retirerBanniere: Boolean = false) {
        var chemin: String? = null
        var cheminBanniere: String? = null
        val t = Clock.System.now().toEpochMilliseconds()
        if (nouveauLogo != null) {
            chemin = "logo-$t.${nouveauLogo.extension}"
            if (demo) Demo.fichiers["logos/$chemin"] = nouveauLogo.octets else deposer("logos", chemin, nouveauLogo)
        }
        if (nouvelleBanniere != null) {
            cheminBanniere = "banniere-$t.${nouvelleBanniere.extension}"
            if (demo) Demo.fichiers["logos/$cheminBanniere"] = nouvelleBanniere.octets else deposer("logos", cheminBanniere, nouvelleBanniere)
        }
        val retirer = retirerBanniere && nouvelleBanniere == null
        if (demo) { Demo.majOrganisation(nom, chemin, profilDemo.value, cheminBanniere, retirer) }
        else client.from("organisation").update({
            set("nom", nom); if (chemin != null) set("logo_path", chemin)
            if (cheminBanniere != null) set("banniere_path", cheminBanniere) else if (retirer) set<String?>("banniere_path", null)
        }) { filter { eq("id", 1) } }
        if (nouveauLogo != null) logo.value = nouveauLogo.octets
        if (nouvelleBanniere != null) banniere.value = nouvelleBanniere.octets else if (retirer) banniere.value = null
    }

    suspend fun reglages(): Map<String, Double> =
        if (demo) Demo.reglages.toMap()
        else client.from("settings").select().decodeList<Reglage>().associate { it.cle to (it.valeur ?: 0.0) }

    suspend fun majReglage(cle: String, valeur: Double) {
        if (demo) { Demo.majReglage(cle, valeur, profilDemo.value); return }
        client.from("settings").update({ set("valeur", valeur) }) { filter { eq("cle", cle) } }
    }

    suspend fun majSoldeInitial(compteId: String, valeur: Double) {
        if (demo) { Demo.majSoldeInitial(compteId, valeur, profilDemo.value); return }
        client.from("accounts").update({ set("solde_initial", valeur) }) { filter { eq("id", compteId) } }
    }

    suspend fun ajouterCompte(c: NouveauCompte) {
        if (demo) { Demo.ajouterCompte(c, profilDemo.value); return }
        client.from("accounts").insert(c)
    }

    suspend fun ajouterCategorie(c: NouvelleCategorie) {
        if (demo) { Demo.ajouterCategorie(c, profilDemo.value); return }
        client.from("categories").insert(c)
    }

    suspend fun profilsComplets(): List<Profil> =
        if (demo) Demo.profilsActuels()
        else client.from("profiles").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun majProfil(id: String, role: String? = null, actif: Boolean? = null) {
        if (demo) { Demo.majProfil(id, role, actif, profilDemo.value); return }
        client.from("profiles").update({ role?.let { set("role", it) }; actif?.let { set("actif", it) } }) { filter { eq("id", id) } }
    }

    suspend fun invitations(): List<Invitation> =
        if (demo) Demo.invitations.toList() else client.from("invitations").select().decodeList()

    suspend fun inviter(i: Invitation) {
        if (demo) { Demo.inviter(i, profilDemo.value); return }
        client.from("invitations").insert(i)
    }

    suspend fun majInvitation(email: String, role: String) {
        if (demo) { val i = Demo.invitations.indexOfFirst { it.email == email }; if (i >= 0) Demo.invitations[i] = Demo.invitations[i].copy(role = role); return }
        client.from("invitations").update({ set("role", role) }) { filter { eq("email", email) } }
    }

    suspend fun retirerInvitation(email: String) {
        if (demo) { Demo.invitations.removeAll { it.email == email }; return }
        client.from("invitations").delete { filter { eq("email", email) } }
    }

    // ---------- Budget (saisie) ----------
    suspend fun lignesBudget(annee: Int): List<Budget> =
        if (demo) Demo.budgetsBruts(annee)
        else client.from("budgets").select { filter { eq("annee", annee) } }.decodeList()

    suspend fun ajouterBudget(b: NouveauBudget) {
        if (demo) { Demo.ajouterBudget(b, profilDemo.value); return }
        client.from("budgets").insert(b)
    }

    suspend fun majBudget(id: String, prevu: Double) {
        if (demo) { Demo.majBudget(id, prevu, profilDemo.value); return }
        client.from("budgets").update({ set("montant_prevu", prevu) }) { filter { eq("id", id) } }
    }

    suspend fun supprimerBudget(id: String) {
        if (demo) { Demo.supprimerBudget(id, profilDemo.value); return }
        client.from("budgets").delete { filter { eq("id", id) } }
    }

    // ---------- Activités (saisie) ----------
    suspend fun ajouterProjet(p: NouveauProjet): String {
        if (demo) return Demo.ajouterProjet(p, profilDemo.value)
        return client.from("projects").insert(p) { select() }.decodeSingle<Projet>().id
    }

    suspend fun majProjet(id: String, p: NouveauProjet) {
        if (demo) { Demo.majProjet(id, p, profilDemo.value); return }
        client.from("projects").update({
            set("nom", p.nom); set("date_debut", p.debut); set("date_fin", p.fin); set("description", p.description); set("visible_adherents", p.visible)
            set("type", p.type); set("heure_debut", p.heureDebut); set("heure_fin", p.heureFin); set("lieu", p.lieu)
        }) { filter { eq("id", id) } }
    }

    suspend fun planning(debut: String? = null, fin: String? = null): List<Projet> =
        if (demo) Demo.planning(profilDemo.value, debut, fin)
        else client.postgrest.rpc("planning_activites", buildJsonObject { debut?.let { put("p_debut", it) }; fin?.let { put("p_fin", it) } }).decodeList()

    // ---------- Membres (modification, photo, import) ----------
    suspend fun majMembre(id: String, m: NouveauMembre, actif: Boolean, photo: Fichier?) {
        var chemin: String? = null
        if (photo != null) {
            chemin = "$id.jpg"
            if (demo) Demo.fichiers["photos/$chemin"] = photo.octets
            else client.storage.from("photos").upload(chemin, photo.octets) { contentType = ContentType.Image.JPEG; upsert = true }
        }
        if (demo) { Demo.majMembre(id, m, actif, chemin, profilDemo.value); return }
        client.from("members").update({
            set("prenom", m.prenom); set("nom", m.nom); set("naissance_jour", m.jour); set("naissance_mois", m.mois)
            set("profession", m.profession); set("whatsapp", m.whatsapp); set("email", m.email)
            set("consent_anniversaire", m.consentement); set("actif", actif)
            if (chemin != null) set("photo_path", chemin)
        }) { filter { eq("id", id) } }
    }

    suspend fun importerMembres(liste: List<NouveauMembre>) {
        if (demo) { liste.forEach { Demo.ajouterMembre(it, profilDemo.value) }; return }
        client.from("members").insert(liste)
    }

    // ---------- Adhérent ----------
    suspend fun mesParticipations(): List<Participation> =
        if (demo) Demo.mesParticipations(profilDemo.value)
        else client.postgrest.rpc("mes_participations").decodeList()

    suspend fun maCotisation(): List<PeriodeCotisation> =
        if (demo) Demo.maCotisation(profilDemo.value)
        else client.postgrest.rpc("ma_cotisation").decodeList()

    // ---------- Sauvegarde ----------
    suspend fun sauvegardeJson(): String {
        if (demo) return Demo.sauvegardeJson()
        val tables = listOf("organisation", "settings", "accounts", "categories", "projects", "budgets", "members", "cotisations",
            "tiers", "collectes", "collecte_membres", "transactions", "expense_requests", "attachments", "reconciliations", "profiles", "invitations")
        return buildString {
            append("""{"format":"tresorerie-jp-v1","exporte_le":"${Clock.System.now()}"""")
            tables.forEach { t -> append(""","$t":"""); append(client.from(t).select().data) }
            append("}")
        }
    }

    // ---------- Droits ----------
    suspend fun mesDroits(): Set<String> =
        if (demo) Demo.droitsDe(profilDemo.value) else client.postgrest.rpc("mes_droits").decodeList<String>().toSet()

    suspend fun roles(): List<Role> =
        if (demo) Demo.roles.toList() else client.from("roles").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun permissions(): List<Permission> =
        if (demo) Demo.permissions else client.from("permissions").select { order("ordre", Order.ASCENDING) }.decodeList()

    suspend fun rolePermissions(): List<RolePermission> =
        if (demo) Demo.rolePermissions.toList() else client.from("role_permissions").select().decodeList()

    suspend fun basculerDroit(role: String, permission: String, actif: Boolean) {
        if (demo) { Demo.basculerDroit(role, permission, actif, profilDemo.value); return }
        if (actif) client.from("role_permissions").insert(RolePermission(role, permission))
        else client.from("role_permissions").delete { filter { eq("role", role); eq("permission", permission) } }
    }

    suspend fun creerRole(nom: String, modele: String?) {
        val code = sansAccentsCode(nom)
        if (demo) { Demo.creerRole(code, nom, modele, profilDemo.value); return }
        client.from("roles").insert(Role(code, nom))
        if (modele != null) {
            val droits = client.from("role_permissions").select { filter { eq("role", modele) } }.decodeList<RolePermission>()
            if (droits.isNotEmpty()) client.from("role_permissions").insert(droits.map { RolePermission(code, it.permission) })
        }
    }

    suspend fun renommerRole(code: String, nom: String) {
        if (demo) { Demo.renommerRole(code, nom, profilDemo.value); return }
        client.from("roles").update({ set("nom", nom) }) { filter { eq("code", code) } }
    }

    suspend fun supprimerRole(code: String) {
        if (demo) { Demo.supprimerRole(code, profilDemo.value); return }
        client.from("roles").delete { filter { eq("code", code) } }
    }

    // ---------- Pièces jointes ----------
    suspend fun pieces(): List<Piece> =
        if (demo) Demo.pieces.toList() else client.from("attachments").select().decodeList()

    suspend fun joindrePiece(transactionId: String, f: Fichier, monId: String) {
        val chemin = nomFichier(f.extension)
        if (demo) { Demo.joindrePiece(transactionId, chemin, f, profilDemo.value); return }
        deposer("justificatifs", chemin, f)
        client.from("attachments").insert(NouvellePiece(transactionId, chemin, f.mime, f.ko, monId))
    }

    // Correction : écriture de même catégorie et même compte, montant négatif
    suspend fun contrePasser(e: Ecriture, monId: String) = ajouter(NouvelleEcriture(
        aujourdhui().toString(), e.compteId, e.sens, -e.montant, e.categorieId, "Contre-passation$NBSP: ${e.libelle}".take(120),
        e.mode, monId, e.projetId, e.id, e.membreId, e.tiersId, e.estCotisation, e.collecteId))

    // ---------- Cotisations par période ----------
    suspend fun periodes(annee: Int): List<PeriodeCotisation> =
        if (demo) Demo.periodes().filter { it.annee == annee }
        else client.from("v_cotisations_periodes").select { filter { eq("annee", annee) } }.decodeList()

    suspend fun periodesMembre(membreId: String): List<PeriodeCotisation> =
        if (demo) Demo.periodes().filter { it.membreId == membreId }
        else client.from("v_cotisations_periodes").select { filter { eq("member_id", membreId) } }.decodeList()

    // Montant dû d'une période ; 0 = membre dispensé
    suspend fun majDu(membreId: String, periode: String, montant: Double) {
        if (demo) { Demo.majDu(membreId, periode, montant, profilDemo.value); return }
        client.from("cotisations").update({ set("montant_du", montant) }) { filter { eq("member_id", membreId); eq("periode", periode) } }
    }

    // ---------- Tiers ----------
    suspend fun tiers(): List<Tiers> =
        if (demo) Demo.tiers.toList() else client.from("tiers").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun ajouterTiers(t: NouveauTiers): Tiers {
        if (demo) return Demo.ajouterTiers(t, profilDemo.value)
        return client.from("tiers").insert(t) { select() }.decodeSingle()
    }

    suspend fun majTiers(id: String, t: NouveauTiers) {
        if (demo) { Demo.majTiers(id, t, profilDemo.value); return }
        client.from("tiers").update({
            set("nom", t.nom); set("type", t.type); set("telephone", t.telephone); set("email", t.email); set("notes", t.notes); set("actif", t.actif)
        }) { filter { eq("id", id) } }
    }

    // ---------- Collectes (participations aux activités) ----------
    suspend fun collectes(): List<Collecte> =
        if (demo) Demo.collectesVue() else client.from("v_collectes").select { order("created_at", Order.DESCENDING) }.decodeList()

    suspend fun collecteMembres(id: String): List<String> =
        if (demo) Demo.collecteMembres.filter { it.collecteId == id }.map { it.membreId }
        else client.from("collecte_membres").select { filter { eq("collecte_id", id) } }.decodeList<CollecteMembre>().map { it.membreId }

    suspend fun enregistrerCollecte(id: String?, c: NouvelleCollecte, membres: List<String>): String {
        if (demo) return Demo.enregistrerCollecte(id, c, membres, profilDemo.value)
        val cle = if (id == null) client.from("collectes").insert(c) { select() }.decodeSingle<Collecte>().id
        else {
            client.from("collectes").update({
                set("nom", c.nom); set("project_id", c.projetId); set("montant_attendu", c.montantAttendu); set("objectif", c.objectif)
                set("date_limite", c.dateLimite); set("tous_membres", c.tousMembres)
            }) { filter { eq("id", id) } }
            id
        }
        client.from("collecte_membres").delete { filter { eq("collecte_id", cle) } }
        if (!c.tousMembres && membres.isNotEmpty()) client.from("collecte_membres").insert(membres.map { CollecteMembre(cle, it) })
        return cle
    }

    suspend fun cloturerCollecte(id: String, cloturee: Boolean) {
        if (demo) { Demo.cloturerCollecte(id, cloturee, profilDemo.value); return }
        client.from("collectes").update({ set("cloturee", cloturee) }) { filter { eq("id", id) } }
    }

    suspend fun tousLesComptes(): List<Compte> =
        if (demo) Demo.comptes.toList()
        else client.from("accounts").select { order("nom", Order.ASCENDING) }.decodeList()

    suspend fun majCompte(id: String, solde: Double, actif: Boolean) {
        if (demo) { Demo.majCompte(id, solde, actif, profilDemo.value); return }
        client.from("accounts").update({ set("solde_initial", solde); set("actif", actif) }) { filter { eq("id", id) } }
    }
}
