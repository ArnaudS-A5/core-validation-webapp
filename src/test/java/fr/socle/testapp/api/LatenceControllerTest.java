package fr.socle.testapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import fr.socle.testapp.service.ProfilReseau;
import fr.socle.testapp.service.Reseau;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Compte-gouttes, ressources lentes et administration du profil réseau. */
class LatenceControllerTest extends AbstractApiTest {

    private String corps(MvcResult demarre) throws Exception {
        MvcResult fini = mvc.perform(asyncDispatch(demarre)).andExpect(status().isOk()).andReturn();
        return fini.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void goutte_en_flux_ndjson() throws Exception {
        MvcResult demarre = mvc.perform(get("/api/latence/goutte").param("nombre", "3").param("intervalle", "1"))
                .andExpect(request().asyncStarted()).andReturn();
        String corps = corps(demarre);
        assertThat(corps.lines().toList()).containsExactly(
                "{\"rang\":1,\"texte\":\"Élément 1\"}", "{\"rang\":2,\"texte\":\"Élément 2\"}", "{\"rang\":3,\"texte\":\"Élément 3\"}");
        assertThat(demarre.getResponse().getContentType()).startsWith("application/x-ndjson");
    }

    @Test
    void goutte_en_tableau_json_et_bornes() throws Exception {
        MvcResult demarre = mvc.perform(get("/api/latence/goutte").param("format", "json").param("nombre", "0").param("intervalle", "-5"))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(corps(demarre)).isEqualTo("[{\"rang\":1,\"texte\":\"Élément 1\"}]");
        MvcResult beaucoup = mvc.perform(get("/api/latence/goutte").param("format", "json").param("nombre", "500").param("intervalle", "0"))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(corps(beaucoup)).contains("\"rang\":" + LatenceController.MAX_ELEMENTS + ",").doesNotContain("\"rang\":51,");
    }

    @Test
    void fichier_au_compte_gouttes() throws Exception {
        MvcResult demarre = mvc.perform(get("/api/latence/fichier").param("duree", "20").param("taille", "1000").param("morceaux", "4"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fichier-goutte.txt\""))
                .andExpect(header().longValue("Content-Length", 1000L))
                .andReturn();
        String corps = corps(demarre);
        assertThat(corps).hasSize(1000).startsWith("ligne 000001 - fichier au compte-gouttes");
        assertThat(corps.substring(64)).startsWith("ligne 000002");
    }

    @Test
    void contenu_du_fichier_borne_et_reproductible() {
        assertThat(LatenceController.contenuFichier(64)).hasSize(64).endsWith((byte) '\n');
        assertThat(LatenceController.contenuFichier(10)).isEqualTo(LatenceController.contenuFichier(10));
    }

    @Test
    void ressource_lente_servie_apres_le_delai() throws Exception {
        long debut = System.nanoTime();
        mvc.perform(get("/classic/lent/latence-tardif.js").param("delai", "300"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat((System.nanoTime() - debut) / 1_000_000).isGreaterThanOrEqualTo(290);
        mvc.perform(get("/classic/lent/latence-tardif.css").param("delai", "0")).andExpect(status().isOk());
    }

    // ---------- Administration du profil réseau ----------

    @Test
    void profil_lu_applique_puis_retire_par_la_remise_a_zero() throws Exception {
        mvc.perform(get("/api/admin/profil")).andExpect(jsonPath("$.profil", is("aucun")));
        mvc.perform(put("/api/admin/profil").contentType(MediaType.APPLICATION_JSON).content("{\"profil\":\"lent\",\"graine\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil", is("lent")))
                .andExpect(jsonPath("$.graine", is(12)))
                .andExpect(jsonPath("$.latenceMinMs", is(2000)));
        mvc.perform(post("/api/admin/reset")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/profil")).andExpect(jsonPath("$.profil", is("aucun")));
    }

    @Test
    void profil_inconnu_refuse() throws Exception {
        mvc.perform(put("/api/admin/profil").contentType(MediaType.APPLICATION_JSON).content("{\"profil\":\"tempete\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.profils", hasItem("catastrophique")));
    }

    @Test
    void sans_profil_aucun_tirage() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk()).andExpect(header().doesNotExist("X-Reseau-Tirage"));
    }

    @Test
    void numero_de_tirage_invalide_remplace_par_la_sequence() throws Exception {
        long graine = graineRapide();
        espaces.commonSpace().reseau().configurer(ProfilReseau.INSTABLE, graine);
        mvc.perform(get("/api/ping").param("tirage", "abc"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Reseau-Tirage", startsWith("instable;numero=0;")));
        espaces.commonSpace().reseau().configurer(ProfilReseau.INSTABLE, graine);
        mvc.perform(get("/api/ping").param("tirage", " "))
                .andExpect(header().string("X-Reseau-Tirage", startsWith("instable;numero=0;")));
    }

    // ---------- Journal des tirages ----------

    @Test
    void journal_des_tirages_sans_profil() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/tirages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil", is("aucun")))
                .andExpect(jsonPath("$.tirages", hasSize(0)));
    }

    @Test
    void journal_des_tirages_avec_profil_sans_consommer_de_tirage() throws Exception {
        long graine = graineRapide();
        Reseau reseau = espaces.commonSpace().reseau();
        reseau.configurer(ProfilReseau.INSTABLE, graine);
        mvc.perform(get("/api/ping").param("tirage", "0")).andExpect(status().isOk());
        mvc.perform(get("/api/countries").param("tirage", "0")).andExpect(status().isOk());
        Reseau.Tirage attendu = reseau.calculer(0L);
        mvc.perform(get("/api/admin/tirages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil", is("instable")))
                .andExpect(jsonPath("$.graine", is((int) graine)))
                .andExpect(jsonPath("$.tiragesEffectues", is(0)))
                .andExpect(jsonPath("$.tirages", hasSize(2)))
                .andExpect(jsonPath("$.tirages[0].chemin", is("/api/countries")))
                .andExpect(jsonPath("$.tirages[0].numero", is(0)))
                .andExpect(jsonPath("$.tirages[0].latenceMs", is(attendu.latenceMs())))
                .andExpect(jsonPath("$.tirages[0].incident", is("aucun")))
                .andExpect(jsonPath("$.tirages[1].chemin", is("/api/ping")));
        // Lire le journal ne passe pas par le profil : aucun tirage de plus
        mvc.perform(get("/api/admin/tirages")).andExpect(jsonPath("$.tirages", hasSize(2)));
    }

    /** Graine dont le premier tirage du profil instable est rapide et sans incident. */
    private static long graineRapide() {
        for (long graine = 1; ; graine++) {
            Reseau reseau = new Reseau();
            reseau.configurer(ProfilReseau.INSTABLE, graine);
            Reseau.Tirage t = reseau.calculer(0L);
            if (t.incident() == Reseau.Incident.AUCUN && t.latenceMs() < 400) {
                return graine;
            }
        }
    }
}
