package fr.socle.testapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Predicate;

import fr.socle.testapp.service.EspaceRegistry;
import fr.socle.testapp.service.ProfilReseau;
import fr.socle.testapp.service.Reseau;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Profil réseau vu d'un vrai client HTTP (serveur démarré sur un port aléatoire) :
 * erreur 503 et connexion coupée en cours de réponse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"testapp.latency.default-ms=0", "testapp.latency.jitter-ms=0"})
class ReseauHttpTest {

    private static final String ESPACE = "test-reseau-http";
    private static final long GRAINE = 7L;

    @LocalServerPort
    private int port;

    @Autowired
    private EspaceRegistry espaces;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void profilCatastrophique() {
        espaces.get(ESPACE).reseau().configurer(ProfilReseau.CATASTROPHIQUE, GRAINE);
    }

    /** Premier tirage qui produit l'incident voulu avec une latence courte (test rapide). */
    private static Reseau.Tirage tirage(Predicate<Reseau.Tirage> voulu) {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.CATASTROPHIQUE, GRAINE);
        for (long n = 0; n < 100_000; n++) {
            Reseau.Tirage t = reseau.calculer(n);
            if (voulu.test(t) && t.latenceMs() <= 1_300) {
                return t;
            }
        }
        throw new IllegalStateException("aucun tirage trouvé");
    }

    private HttpRequest ping(long numero) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ping?tirage=" + numero))
                .header("Cookie", EspaceRegistry.COOKIE + "=" + ESPACE)
                .timeout(Duration.ofSeconds(20))
                .build();
    }

    private static long depuis(long debutNanos) {
        return (System.nanoTime() - debutNanos) / 1_000_000;
    }

    /** Le serveur a noté ce tirage dans le journal de l'espace, avec la latence calculée d'après la graine. */
    private void journalise(Reseau.Tirage attendu) {
        Reseau.Entree entree = espaces.get(ESPACE).reseau().journal().get(0);
        assertThat(entree.numero()).isEqualTo(attendu.numero());
        assertThat(entree.chemin()).isEqualTo("/api/ping");
        assertThat(entree.latenceMs()).isEqualTo(attendu.latenceMs());
        assertThat(entree.incident()).isEqualTo(attendu.incident());
    }

    @Test
    void erreur_simulee_en_503_apres_la_latence_tiree() throws Exception {
        Reseau.Tirage t = tirage(x -> x.incident() == Reseau.Incident.ERREUR);
        long debut = System.nanoTime();
        HttpResponse<String> response = client.send(ping(t.numero()), HttpResponse.BodyHandlers.ofString());
        assertThat(depuis(debut)).as("erreur reçue après la latence tirée").isGreaterThanOrEqualTo(t.latenceMs());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Incident réseau simulé");
        assertThat(response.headers().firstValue("X-Reseau-Tirage"))
                .hasValue("catastrophique;numero=" + t.numero() + ";latence=" + t.latenceMs());
        journalise(t);
    }

    @Test
    void connexion_coupee_apres_la_latence_tiree() {
        Reseau.Tirage t = tirage(x -> x.incident() == Reseau.Incident.COUPURE);
        long debut = System.nanoTime();
        assertThatThrownBy(() -> client.send(ping(t.numero()), HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(IOException.class);
        assertThat(depuis(debut)).as("coupure après la latence tirée").isGreaterThanOrEqualTo(t.latenceMs());
        journalise(t);
    }

    @Test
    void requete_sans_incident_aboutit_apres_la_latence_tiree() throws Exception {
        Reseau.Tirage t = tirage(x -> x.incident() == Reseau.Incident.AUCUN);
        long debut = System.nanoTime();
        HttpResponse<String> response = client.send(ping(t.numero()), HttpResponse.BodyHandlers.ofString());
        // Latence de base de l'espace réglée à 0 dans ce test : seule la latence tirée s'applique
        assertThat(depuis(debut)).as("réponse après la latence tirée").isGreaterThanOrEqualTo(t.latenceMs());
        assertThat(response.statusCode()).isEqualTo(200);
        journalise(t);
    }

    @Test
    void administration_et_version_echappent_au_profil() throws Exception {
        for (String chemin : new String[]{"/api/admin/profil", "/api/version"}) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                    .header("Cookie", EspaceRegistry.COOKIE + "=" + ESPACE).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(chemin).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Reseau-Tirage")).as(chemin).isEmpty();
        }
    }
}
