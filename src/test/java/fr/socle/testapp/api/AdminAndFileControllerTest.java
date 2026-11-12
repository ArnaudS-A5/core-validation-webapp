package fr.socle.testapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class AdminAndFileControllerTest extends AbstractApiTest {

    // ---------- Administration ----------

    @Test
    void lecture_et_modification_des_reglages() throws Exception {
        mvc.perform(get("/api/admin/settings")).andExpect(jsonPath("$.defaultDelayMs", is(0)));
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultDelayMs\":5,\"jitterMs\":2}"))
                .andExpect(jsonPath("$.defaultDelayMs", is(5)))
                .andExpect(jsonPath("$.jitterMs", is(2)));
        // la latence globale (avec gigue) s'applique quand aucun délai n'est demandé
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.defaultDelayMs", is(5)));
    }

    @Test
    void remise_a_zero() throws Exception {
        mvc.perform(delete("/api/items/1"));
        mvc.perform(get("/api/flaky").param("key", "r"));
        mvc.perform(post("/api/admin/reset")).andExpect(jsonPath("$.status", is("reset")));
        mvc.perform(get("/api/items")).andExpect(jsonPath("$.totalElements", is(57)));
        mvc.perform(get("/api/flaky").param("key", "r")).andExpect(jsonPath("$.attempt", is(1)));
    }

    // ---------- Téléchargements ----------

    @ParameterizedTest
    @CsvSource({
            "txt, text/plain, exemple.txt",
            "csv, text/csv, exemple.csv",
            "json, application/json, exemple.json",
            "zip, application/zip, exemple.zip",
            "bin, application/octet-stream, exemple.bin"})
    void telechargement(String kind, String type, String filename) throws Exception {
        mvc.perform(get("/api/files/download/" + kind))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(type)))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(filename)));
    }

    @Test
    void telechargement_binaire_de_taille_demandee() throws Exception {
        byte[] body = mvc.perform(get("/api/files/download/bin").param("size", "300").param("delay", "1"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).hasSize(300);
        assertThat(body[257]).isEqualTo((byte) 1);
        mvc.perform(get("/api/files/download/bin").param("size", "-5"))
                .andExpect(header().longValue("Content-Length", 0));
    }

    @Test
    void archive_zip_contient_deux_fichiers() throws Exception {
        byte[] zip = mvc.perform(get("/api/files/download/zip")).andReturn().getResponse().getContentAsByteArray();
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (in.getNextEntry() != null) {
                entries++;
            }
        }
        assertThat(entries).isEqualTo(2);
    }

    @Test
    void telechargement_type_inconnu() throws Exception {
        mvc.perform(get("/api/files/download/pdf")).andExpect(status().isNotFound());
    }

    // ---------- Envoi de fichiers ----------

    @Test
    void envoi_de_fichier() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sample-upload.txt", "text/plain",
                "abc".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("sample-upload.txt")))
                .andExpect(jsonPath("$.size", is(3)))
                .andExpect(jsonPath("$.sha256", is("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")));
    }

    @Test
    void envoi_de_fichier_vide_refuse() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "vide.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/api/files/upload").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Fichier vide ou absent")));
    }

    // ---------- Redirections des fronts ----------

    @ParameterizedTest
    @CsvSource({"/classic", "/classic/"})
    void le_front_classic_pointe_vers_sa_page_d_accueil(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/classic/index.html"));
    }

    @Test
    void ressources_statiques_servies() throws Exception {
        mvc.perform(get("/classic/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"nav-clicks\"")));
    }
}
