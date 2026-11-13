package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;

/**
 * Calque plein écran jamais retiré : vérifie que la cible des branches d'erreur du socle
 * reste bloquée quoi qu'il arrive (interception, attente expirée), et que seul un clic JavaScript passe.
 */
@DisplayName("Calque permanent")
class A12CalquePermanentIT extends SmokeTestBase {

    private Object elementAuPremierPlan(String css) {
        return js("var r = document.querySelector(arguments[0]).getBoundingClientRect();"
                + "var e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);"
                + "return e && (e.closest('#page-overlay') ? 'page-overlay' : e.id);", css);
    }

    @Test
    @Order(1)
    void calque_plein_ecran_au_premier_plan() {
        open("calque.html");
        assertThat(visible("#page-overlay")).as("calque affiché").isTrue();
        assertThat(js("var r = document.getElementById('page-overlay').getBoundingClientRect();"
                + "return r.width >= window.innerWidth && r.height >= window.innerHeight;"))
                .as("le calque couvre toute la zone visible").isEqualTo(true);
        assertThat(elementAuPremierPlan("#btn-under-overlay")).as("calque au-dessus du bouton").isEqualTo("page-overlay");
        assertThat(elementAuPremierPlan("#link-under-overlay")).as("calque au-dessus du lien").isEqualTo("page-overlay");
        assertThat(elementAuPremierPlan("#nav-home")).as("calque au-dessus de la navigation").isEqualTo("page-overlay");
        assertThat(js("return getComputedStyle(document.getElementById('page-overlay')).cursor"))
                .as("curseur normal sur le calque").isIn("auto", "default");
    }

    @Test
    @Order(2)
    void clic_natif_toujours_intercepte() {
        assertThatThrownBy(() -> $("#btn-under-overlay").click()).as("clic sur le bouton intercepté")
                .isInstanceOf(ElementClickInterceptedException.class);
        assertThatThrownBy(() -> $("#link-under-overlay").click()).as("clic sur le lien intercepté")
                .isInstanceOf(ElementClickInterceptedException.class);
    }

    @Test
    @Order(3)
    void attente_expiree_et_calque_toujours_present() {
        long t0 = System.currentTimeMillis();
        assertThatThrownBy(() -> click("#btn-under-overlay", 4)).as("attente expirée, erreur d'interception remontée")
                .isInstanceOf(ElementClickInterceptedException.class);
        assertThat(System.currentTimeMillis() - t0).as("l'attente a duré au moins 4 s").isGreaterThanOrEqualTo(3900);
        assertThat(visible("#page-overlay")).as("calque toujours présent après l'attente").isTrue();
        assertThat(textContent("#under-overlay-result")).as("aucun clic n'a abouti").isEmpty();
    }

    @Test
    @Order(4)
    void seul_un_clic_javascript_passe() {
        js("document.getElementById('btn-under-overlay').click()");
        assertThat(waitText("#under-overlay-result", "Bouton sous le calque cliqué \\(contournement par JavaScript\\)"))
                .as("clic JavaScript sur le bouton").isTrue();
        assertThat(visible("#page-overlay")).as("le calque n'est pas retiré pour autant").isTrue();
    }
}
