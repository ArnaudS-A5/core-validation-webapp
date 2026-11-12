package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.HasDownloads;

@DisplayName("Fichiers")
class A08FichiersIT extends SmokeTestBase {

    private static final String FETCH_INFO = """
            var done = arguments[arguments.length - 1];
            fetch(document.querySelector(arguments[0]).href)
              .then(function (r) { return r.arrayBuffer().then(function (b) {
                done({ type: r.headers.get('content-type'), cd: r.headers.get('content-disposition'), size: b.byteLength }); }); })
              .catch(function (e) { done({ error: String(e) }); });
            """;

    private Path sample;

    @Test
    @Order(1)
    void ouverture() throws Exception {
        open("files.html");
        assertThat(exists("#upload-input")).as("page Fichiers chargée").isTrue();
        Path dir = Files.createTempDirectory("socle-testapp-");
        sample = dir.resolve("sample-upload.txt");
        Files.writeString(sample, "contenu de test pour upload\n", StandardCharsets.UTF_8);
        assertThat(sample).as("fichier à envoyer prêt").isRegularFile().isNotEmptyFile();
    }

    @ParameterizedTest(name = "{0} ({1}, attachment)")
    @Order(2)
    @CsvSource({
            "download-txt, text/plain",
            "download-csv, text/csv",
            "download-json, application/json",
            "download-zip, application/zip",
            "download-bin-1mb, application/octet-stream"})
    @SuppressWarnings("unchecked")
    void telechargement_direct(String id, String type) {
        Map<String, Object> info = (Map<String, Object>) asyncJs(FETCH_INFO, "#" + id);
        assertThat(String.valueOf(info.get("type"))).as(id + " : type").startsWith(type);
        assertThat(String.valueOf(info.get("cd"))).as(id + " : attachment").contains("attachment");
        if (id.equals("download-bin-1mb")) {
            assertThat(((Number) info.get("size")).longValue()).as("1 Mo").isEqualTo(1_048_576L);
        }
    }

    @Test
    @Order(3)
    void fichier_lent() {
        long t0 = System.currentTimeMillis();
        asyncJs(FETCH_INFO, "#download-slow");
        assertThat(System.currentTimeMillis() - t0).as("fichier lent ≥ 3 s").isGreaterThanOrEqualTo(2900);
    }

    @Test
    @Order(4)
    void telechargement_par_script() {
        click("#btn-download-ajax");
        assertThat(waitText("#download-status", "terminé", 6)).as("téléchargement par script").isTrue();
        if (BrowserExtension.remote() && d instanceof HasDownloads downloads) {
            // Téléchargements gérés par le Grid : le fichier doit être présent sur le nœud
            assertThat(waitUntil(drv -> downloads.getDownloadableFiles().contains("export-script.csv"), 10))
                    .as("fichier export-script.csv présent sur le nœud du Grid").isTrue();
        }
    }

    @Test
    @Order(5)
    void envoi_sans_fichier_refuse() {
        click("#btn-upload");
        assertThat(waitText("#upload-result", "Aucun fichier")).as("envoi sans fichier refusé").isTrue();
    }

    @Test
    @Order(6)
    void envoi_de_fichier() {
        $("#upload-input").sendKeys(sample.toAbsolutePath().toString());
        click("#btn-upload");
        assertThat(waitText("#upload-result", "sample-upload\\.txt.*SHA-256")).as("upload").isTrue();
        assertThat(attr("#upload-progress", "aria-valuenow")).as("progression upload 100").isEqualTo("100");
    }

    @Test
    @Order(7)
    void envoi_par_champ_masque() {
        $("#upload-hidden-input").sendKeys(sample.toAbsolutePath().toString());
        assertThat(waitText("#hidden-upload-result", "sample-upload\\.txt")).as("upload via champ masqué").isTrue();
    }
}
