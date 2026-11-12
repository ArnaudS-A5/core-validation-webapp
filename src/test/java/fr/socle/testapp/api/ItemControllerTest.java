package fr.socle.testapp.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ItemControllerTest extends AbstractApiTest {

    @Test
    void premiere_page_par_defaut() throws Exception {
        mvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.totalElements", is(57)))
                .andExpect(jsonPath("$.totalPages", is(6)))
                .andExpect(jsonPath("$.items[0].name", is("Article 001")));
    }

    @Test
    void derniere_page() throws Exception {
        mvc.perform(get("/api/items").param("page", "5"))
                .andExpect(jsonPath("$.items", hasSize(7)));
    }

    @Test
    void page_hors_limites_et_taille_bornee() throws Exception {
        mvc.perform(get("/api/items").param("page", "99").param("size", "500"))
                .andExpect(jsonPath("$.size", is(100)))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/items").param("page", "-1").param("size", "0"))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(1)));
    }

    @Test
    void tri_par_nom_decroissant() throws Exception {
        mvc.perform(get("/api/items").param("sort", "name").param("dir", "desc"))
                .andExpect(jsonPath("$.items[0].name", is("Article 057")));
    }

    @Test
    void tri_par_categorie_et_par_prix() throws Exception {
        mvc.perform(get("/api/items").param("sort", "category"))
                .andExpect(jsonPath("$.items[0].category", is("Informatique")));
        mvc.perform(get("/api/items").param("sort", "price").param("size", "57"))
                .andExpect(jsonPath("$.items", hasSize(57)));
        mvc.perform(get("/api/items").param("sort", "inconnu"))
                .andExpect(jsonPath("$.items[0].id", is(1)));
    }

    @Test
    void filtre_par_categorie_ou_nom() throws Exception {
        mvc.perform(get("/api/items").param("q", "Jardin")).andExpect(jsonPath("$.totalElements", is(11)));
        mvc.perform(get("/api/items").param("q", "article 05")).andExpect(jsonPath("$.totalElements", is(8)));
    }

    @Test
    void creation() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Nouvel article \",\"category\":\"Sport\",\"price\":12.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.id", is(58)))
                .andExpect(jsonPath("$.item.name", is("Nouvel article")))
                .andExpect(jsonPath("$.item.price", is(12.5)));
    }

    @Test
    void creation_avec_valeurs_par_defaut() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"category\":\" \"}"))
                .andExpect(jsonPath("$.item.category", is("Divers")))
                .andExpect(jsonPath("$.item.price", is(0.0)));
    }

    @Test
    void creation_sans_nom_refusee() throws Exception {
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.name", is("Le nom est obligatoire")));
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void modification() throws Exception {
        mvc.perform(put("/api/items/3").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Jardin édité\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.name", is("Jardin édité")))
                .andExpect(jsonPath("$.item.category", is("Jardin")));
        mvc.perform(put("/api/items/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"category\":\"Sport\",\"price\":1.0}"))
                .andExpect(jsonPath("$.item.name", is("Jardin édité")))
                .andExpect(jsonPath("$.item.category", is("Sport")))
                .andExpect(jsonPath("$.item.price", is(1.0)));
    }

    @Test
    void modification_article_inconnu() throws Exception {
        mvc.perform(put("/api/items/999").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void suppression() throws Exception {
        mvc.perform(delete("/api/items/2")).andExpect(status().isOk()).andExpect(jsonPath("$.deleted", is(2)));
        mvc.perform(delete("/api/items/2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/items")).andExpect(jsonPath("$.totalElements", is(56)));
    }
}
