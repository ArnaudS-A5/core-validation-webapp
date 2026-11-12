package fr.socle.testapp.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class ApiControllerTest extends AbstractApiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ping_repond_ok() throws Exception {
        mvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.serverTime", notNullValue()));
    }

    @Test
    void ping_accepte_un_delai_explicite() throws Exception {
        mvc.perform(get("/api/ping").param("delay", "1")).andExpect(status().isOk());
    }

    @Test
    void horloge_renvoie_l_heure() throws Exception {
        mvc.perform(get("/api/clock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time", notNullValue()))
                .andExpect(jsonPath("$.epochMs", notNullValue()));
    }

    @Test
    void reponse_lente_indique_son_delai() throws Exception {
        mvc.perform(get("/api/slow").param("delay", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayMs", is(5)))
                .andExpect(jsonPath("$.message", containsString("5 ms")));
    }

    @Test
    void connexion_reussie() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Utilisateur Test")))
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void connexion_compte_verrouille() throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locked\",\"password\":\"x\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error", is("Compte verrouillé")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"username\":\"bob\",\"password\":\"x\"}", "{\"username\":\"test\",\"password\":\"x\"}"})
    void connexion_refusee(String body) throws Exception {
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Identifiants invalides")));
    }

    @Test
    void liste_des_pays() throws Exception {
        mvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].code", is("FR")))
                .andExpect(jsonPath("$[1].name", is("Allemagne")));
    }

    @Test
    void villes_d_un_pays_insensible_a_la_casse() throws Exception {
        mvc.perform(get("/api/countries/fr/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Lyon")));
    }

    @Test
    void villes_d_un_pays_inconnu() throws Exception {
        mvc.perform(get("/api/countries/XX/cities")).andExpect(status().isNotFound());
    }

    @Test
    void autocompletion() throws Exception {
        mvc.perform(get("/api/search").param("q", " AN "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query", is(" AN ")))
                .andExpect(jsonPath("$.results", contains("Ananas", "Banane", "Mangue", "Orange")));
    }

    @Test
    void autocompletion_vide() throws Exception {
        mvc.perform(get("/api/search")).andExpect(jsonPath("$.results", hasSize(0)));
    }

    @Test
    void service_instable_deux_echecs_puis_succes() throws Exception {
        mvc.perform(get("/api/flaky").param("key", "k")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.attempt", is(1)));
        mvc.perform(get("/api/flaky").param("key", "k")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/flaky").param("key", "k")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Succès à la tentative 3")));
    }

    @Test
    void code_http_demande() throws Exception {
        mvc.perform(get("/api/status/418")).andExpect(status().is(418)).andExpect(jsonPath("$.status", is(418)));
        mvc.perform(get("/api/status/201")).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 600})
    void code_http_hors_plage(int code) throws Exception {
        mvc.perform(get("/api/status/" + code)).andExpect(status().isBadRequest());
    }

    @Test
    void formulaire_invalide() throws Exception {
        mvc.perform(post("/api/forms/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"email\":\"sans-arobase\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.name", is("Le nom est obligatoire")))
                .andExpect(jsonPath("$.errors.email", is("Adresse e-mail invalide")));
    }

    @Test
    void formulaire_sans_champs() throws Exception {
        mvc.perform(post("/api/forms/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void formulaire_valide() throws Exception {
        mvc.perform(post("/api/forms/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Albert\",\"email\":\"a@b.fr\",\"city\":\"Lyon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Formulaire enregistré")))
                .andExpect(jsonPath("$.received.city", is("Lyon")));
    }

    @Test
    void tache_longue_progresse_puis_se_termine() throws Exception {
        String body = mvc.perform(post("/api/jobs").param("duration", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMs", is(500)))
                .andReturn().getResponse().getContentAsString();
        String jobId = mapper.readTree(body).get("jobId").asText();

        mvc.perform(get("/api/jobs/" + jobId)).andExpect(jsonPath("$.status", is("RUNNING")));
        Thread.sleep(600);
        JsonNode done = mapper.readTree(mvc.perform(get("/api/jobs/" + jobId)).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(done.get("status").asText()).isEqualTo("DONE");
        org.assertj.core.api.Assertions.assertThat(done.get("percent").asInt()).isEqualTo(100);
    }

    @Test
    void tache_longue_duree_bornee() throws Exception {
        mvc.perform(post("/api/jobs").param("duration", "999999")).andExpect(jsonPath("$.durationMs", is(60000)));
    }

    @Test
    void tache_inconnue() throws Exception {
        mvc.perform(get("/api/jobs/inconnue")).andExpect(status().isNotFound());
    }
}
