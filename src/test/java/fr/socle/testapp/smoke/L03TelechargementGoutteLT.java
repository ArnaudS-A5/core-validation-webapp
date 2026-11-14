package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.HasDownloads;

/**
 * Fichier de 512 Ko envoyé en 20 morceaux sur 10 s. Minimum de 10 s, sans maximum : mesuré dans la page pour le
 * téléchargement par script, et depuis avant le clic pour le téléchargement du navigateur (le test guette le fichier).
 */
@DisplayName("Téléchargement au compte-gouttes")
class L03TelechargementGoutteLT extends LatenceTestBase {

    private static final String FICHIER = "fichier-goutte.txt";
    private static final long TAILLE = 524_288L;
    /** Lien de la page : 20 morceaux, chacun précédé d'une pause de 10 000 / 20 = 500 ms. */
    private static final long DUREE_MS = 10_000;

    private static final String FETCH_INFO = """
            var done = arguments[arguments.length - 1], debut = Date.now();
            fetch(document.querySelector(arguments[0]).href)
              .then(function (r) { return r.arrayBuffer().then(function (b) {
                done({ taille: b.byteLength, annonce: r.headers.get('content-length'), duree: Date.now() - debut }); }); })
              .catch(function (e) { done({ error: String(e) }); });
            """;

    @Test
    @Order(1)
    @SuppressWarnings("unchecked")
    void contenu_annonce_d_emblee_et_recu_en_10_s() {
        open("latence.html");
        Map<String, Object> info = (Map<String, Object>) asyncJs(FETCH_INFO, "#lien-fichier-goutte");
        assertThat(info).as("téléchargement par script").doesNotContainKey("error");
        assertThat(String.valueOf(info.get("annonce"))).as("taille annoncée d'emblée").isEqualTo(String.valueOf(TAILLE));
        assertThat(((Number) info.get("taille")).longValue()).as("taille reçue").isEqualTo(TAILLE);
        assertThat(((Number) info.get("duree")).longValue()).as("reçu en au moins 10 s").isGreaterThanOrEqualTo(DUREE_MS);
    }

    @Test
    @Order(2)
    void telechargement_navigateur_attendre_le_fichier_complet() throws Exception {
        assumeTrue(BrowserExtension.remote() && d instanceof HasDownloads, "téléchargements consultables seulement via le Grid");
        HasDownloads downloads = (HasDownloads) d;
        downloads.deleteDownloadableFiles();
        long debut = System.currentTimeMillis();
        click("#lien-fichier-goutte");
        // Le fichier peut apparaître (vide ou partiel selon le navigateur) bien avant d'être complet
        boolean complet = waitUntil(drv -> downloads.getDownloadedFiles().stream()
                .anyMatch(f -> f.getName().equals(FICHIER) && f.getSize() == TAILLE), 30);
        assertThat(complet).as("fichier complet sur le nœud du Grid").isTrue();
        assertThat(System.currentTimeMillis() - debut).as("téléchargement d'au moins 10 s").isGreaterThanOrEqualTo(DUREE_MS);
        Path dossier = Files.createTempDirectory("socle-testapp-goutte-");
        downloads.downloadFile(FICHIER, dossier);
        String contenu = Files.readString(dossier.resolve(FICHIER), StandardCharsets.US_ASCII);
        assertThat(contenu).hasSize((int) TAILLE).startsWith("ligne 000001 - fichier au compte-gouttes");
    }
}
