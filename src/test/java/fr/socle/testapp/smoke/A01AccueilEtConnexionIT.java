package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@DisplayName("Accueil et connexion")
class A01AccueilEtConnexionIT extends SmokeTestBase {

    // ---------- Accueil ----------

    @Test
    @Order(1)
    void racine_propose_le_front_classic() {
        d.get(APP + "/");
        assertThat(visible("#front-classic")).as("racine : lien classic").isTrue();
    }

    @Test
    @Order(2)
    void version_deployee_affichee() throws Exception {
        open("index.html");
        assertThat(waitUntil(drv -> attr("#app-version", "data-version") != null, 5)).as("version affichée dans l'en-tête").isTrue();
        String displayed = attr("#app-version", "data-version");
        String api = new ObjectMapper().readTree(HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(APP + "/api/version")).build(),
                HttpResponse.BodyHandlers.ofString()).body()).path("version").asText();
        assertThat(displayed).as("version affichée = version de l'API").isEqualTo(api);
        assertThat(text("#app-version")).as("libellé de version").startsWith("v" + api);
        // En CI : la version déployée doit être celle que le pipeline vient de construire
        String expected = System.getProperty("app.expectedVersion", "").trim();
        if (!expected.isEmpty()) {
            assertThat(displayed).as("version déployée = version construite par le pipeline").isEqualTo(expected);
        }
    }

    @Test
    @Order(3)
    void ping_asynchrone_et_data_testid() {
        open("index.html");
        assertThat(waitText("#server-status", "Serveur disponible")).as("ping asynchrone").isTrue();
        assertThat(attr("#nav-clicks", "data-testid")).as("data-testid miroir de id").isEqualTo("nav-clicks");
    }

    @Test
    @Order(4)
    void navigation_par_carte() {
        click("#card-clicks");
        assertThat(waitUntil(drv -> drv.getCurrentUrl().endsWith("clicks.html"), 5)).as("navigation vers clicks.html").isTrue();
    }

    @Test
    @Order(5)
    void parametre_delay_de_la_page() {
        long t0 = System.currentTimeMillis();
        open("index.html?delay=1200");
        assertThat(waitText("#server-status", "Serveur disponible")).isTrue();
        assertThat(System.currentTimeMillis() - t0).as("paramètre ?delay=1200 appliqué").isGreaterThanOrEqualTo(1100);
    }

    // ---------- Connexion ----------

    @Test
    @Order(10)
    void connexion_refusee_401() {
        open("login.html");
        type("#login-username", "bob");
        type("#login-password", "x");
        click("#login-submit");
        assertThat(waitText("#login-message", "Identifiants invalides.*401")).as("refus 401").isTrue();
    }

    @Test
    @Order(11)
    void compte_verrouille_423() {
        clearAndType("#login-username", "locked");
        click("#login-submit");
        assertThat(waitText("#login-message", "423")).as("compte verrouillé 423").isTrue();
    }

    @Test
    @Order(12)
    void connexion_reussie_avec_spinner() {
        clearAndType("#login-username", "test");
        clearAndType("#login-password", "test");
        click("#login-submit");
        assertThat(visible("#login-spinner")).as("spinner pendant l'appel").isTrue();
        assertThat(waitUntil(drv -> exists("#welcome-panel:not(.hidden)"), 5)).isTrue();
        assertThat(text("#welcome-message")).as("message de bienvenue").contains("Utilisateur Test");
    }

    @Test
    @Order(13)
    void deconnexion() {
        click("#logout-button");
        assertThat(waitVisible("#login-form", 3)).as("déconnexion").isTrue();
    }
}
