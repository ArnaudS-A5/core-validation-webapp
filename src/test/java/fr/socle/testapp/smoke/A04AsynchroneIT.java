package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

@DisplayName("Asynchrone")
class A04AsynchroneIT extends SmokeTestBase {

    @Test
    @Order(1)
    void contenu_differe_avec_spinner() {
        open("dynamic.html");
        // 2 s : le spinner doit rester affiché assez longtemps pour être vu après un clic passé par le Grid
        // (avec 600 ms, un navigateur un peu lent ne le voyait plus : build 23, Firefox)
        js("document.getElementById('delay-input').value = '2000'");
        click("#btn-load-delayed");
        assertThat(visible("#delayed-spinner")).as("spinner visible").isTrue();
        assertThat(waitText("#delayed-content", "après 2000 ms")).as("contenu différé").isTrue();
    }

    @Test
    @Order(2)
    void calque_bloquant_temporaire() {
        click("#btn-blocking");
        assertThat(visible("#blocking-overlay")).as("calque présent").isTrue();
        assertThat(waitAbsent("#blocking-overlay", 3)).isTrue();
        click("#btn-after-blocking");
        assertThat(text("#blocking-result")).as("clic après calque").contains("sous le calque");
    }

    @Test
    @Order(3)
    void element_re_rendu() {
        WebElement before = $("#rerender-target");
        click("#btn-rerender");
        assertThat(waitText("#rerender-count", "^2$")).isTrue();
        boolean detached;
        try {
            before.isDisplayed();
            detached = false;
        } catch (StaleElementReferenceException e) {
            detached = true;
        }
        assertThat(detached).as("élément ré-rendu (ancienne référence périmée)").isTrue();
        click("#rerender-target");
        assertThat(text("#rerender-clicks")).as("clic sur la nouvelle cible").isEqualTo("1");
    }

    @Test
    @Order(4)
    void auto_completion() {
        type("#autocomplete-input", "an");
        assertThat(waitUntil(drv -> exists("#suggestion-0"), 4)).isTrue();
        // La liste s'ouvre sous le champ, éventuellement hors de la zone visible : on la fait défiler à l'écran
        // pour la lire, comme un utilisateur (hors de la zone visible, Firefox en renvoie un texte vide)
        inView("#autocomplete-list");
        List<String> suggestions = $$("#autocomplete-list li").stream().map(WebElement::getText).toList();
        assertThat(suggestions).as("suggestions contiennent Banane").contains("Banane");
        click("#suggestion-0");
        assertThat(waitText("#autocomplete-result", "Sélection")).as("sélection suggestion").isTrue();
    }

    @Test
    @Order(5)
    void horloge_par_polling() {
        click("#btn-start-clock");
        assertThat(waitText("#clock-value", "\\d{4}-")).isTrue();
        String t1 = textContent("#clock-value");
        pause(1600);
        String t2 = textContent("#clock-value");
        click("#btn-stop-clock");
        assertThat(t2).as("polling horloge").isNotEqualTo(t1);
    }

    @Test
    @Order(6)
    void tache_longue() {
        js("document.getElementById('job-duration').value = '1500'");
        click("#btn-start-job");
        assertThat(waitText("#job-status", "Terminé", 6)).as("job terminé").isTrue();
        assertThat(attr("#progress-bar", "aria-valuenow")).as("progression 100").isEqualTo("100");
    }

    @Test
    @Order(7)
    void service_instable() {
        click("#btn-flaky");
        assertThat(waitText("#flaky-status", "Échec")).isTrue();
        String first = textContent("#flaky-status");
        click("#btn-flaky");
        pause(700);
        click("#btn-flaky");
        assertThat(waitText("#flaky-status", "Succès à la tentative 3")).as("2 échecs puis succès").isTrue();
        assertThat(first).as("première tentative en échec").contains("tentative 1");
    }

    @Test
    @Order(8)
    void charger_plus() {
        click("#btn-load-more");
        assertThat(waitText("#load-more-count", "^5$")).isTrue();
        click("#btn-load-more");
        assertThat(waitText("#load-more-count", "^10$")).isTrue();
        assertThat(exists("#more-item-10")).as("charger plus").isTrue();
    }

    @Test
    @Order(9)
    void notification_ephemere() {
        click("#btn-toast");
        assertThat(visible("#toast")).as("toast visible").isTrue();
        assertThat(waitAbsent("#toast", 5)).as("toast disparu").isTrue();
    }

    @Test
    @Order(10)
    void codes_http() {
        select("#select-status", "404");
        click("#btn-status");
        assertThat(waitText("#status-result", "Échec : HTTP 404")).as("code 404").isTrue();
        select("#select-status", "200");
        click("#btn-status");
        assertThat(waitText("#status-result", "Succès : HTTP 200")).as("code 200").isTrue();
    }

    @Test
    @Order(11)
    void requetes_paralleles_dans_le_desordre() {
        click("#btn-parallel");
        assertThat(waitText("#parallel-done", "trois appels", 6)).as("parallèle terminé").isTrue();
        List<String> order = $$("#parallel-list li").stream().map(e -> e.getDomAttribute("id")).toList();
        assertThat(order).as("ordre de fin 2, 3, 1").containsExactly("parallel-2", "parallel-3", "parallel-1");
    }
}
