package fr.socle.testapp.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import fr.socle.testapp.service.EspaceRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Isolation des données entre espaces (sessions de test parallèles). */
class EspaceTest extends AbstractApiTest {

    private static Cookie espace(String id) {
        return new Cookie(EspaceRegistry.COOKIE, id);
    }

    @Test
    void sans_cookie_l_espace_commun() throws Exception {
        mvc.perform(get("/api/admin/espace")).andExpect(jsonPath("$.espace", is("commun")));
        mvc.perform(get("/api/admin/espace").cookie(espace("x y !")))
                .andExpect(jsonPath("$.espace", is("commun")));
        mvc.perform(get("/api/admin/espace").cookie(new Cookie("autre", "a")))
                .andExpect(jsonPath("$.espace", is("commun")));
    }

    @Test
    void les_articles_sont_isoles_par_espace() throws Exception {
        mvc.perform(delete("/api/items/1").cookie(espace("session-a")));
        mvc.perform(delete("/api/items/2").cookie(espace("session-a")));
        mvc.perform(get("/api/items").cookie(espace("session-a"))).andExpect(jsonPath("$.totalElements", is(55)));
        mvc.perform(get("/api/items").cookie(espace("session-b"))).andExpect(jsonPath("$.totalElements", is(57)));
        mvc.perform(get("/api/items")).andExpect(jsonPath("$.totalElements", is(57)));
        mvc.perform(get("/api/admin/espace").cookie(espace("session-a"))).andExpect(jsonPath("$.espace", is("session-a")));
    }

    @Test
    void la_remise_a_zero_ne_touche_que_son_espace() throws Exception {
        mvc.perform(delete("/api/items/1").cookie(espace("session-c")));
        mvc.perform(delete("/api/items/1").cookie(espace("session-d")));
        mvc.perform(post("/api/admin/reset").cookie(espace("session-c")))
                .andExpect(jsonPath("$.espace", is("session-c")));
        mvc.perform(get("/api/items").cookie(espace("session-c"))).andExpect(jsonPath("$.totalElements", is(57)));
        mvc.perform(get("/api/items").cookie(espace("session-d"))).andExpect(jsonPath("$.totalElements", is(56)));
    }

    @Test
    void compteurs_et_latence_isoles() throws Exception {
        mvc.perform(get("/api/flaky").param("key", "k").cookie(espace("session-e")));
        mvc.perform(get("/api/flaky").param("key", "k").cookie(espace("session-f"))).andExpect(jsonPath("$.attempt", is(1)));
        mvc.perform(put("/api/admin/settings").cookie(espace("session-e")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultDelayMs\":5}")).andExpect(jsonPath("$.defaultDelayMs", is(5)));
        mvc.perform(get("/api/admin/settings").cookie(espace("session-f"))).andExpect(jsonPath("$.defaultDelayMs", is(0)));
    }
}
