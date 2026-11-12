package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

@DisplayName("Widgets")
class A09WidgetsIT extends SmokeTestBase {

    /** Simulation par script d'un glisser-déposer HTML5 (événements dragstart/dragover/drop). */
    private static final String HTML5_DND = """
            var src = arguments[0], dst = arguments[1], dt = new DataTransfer();
            function fire(el, type) { el.dispatchEvent(new DragEvent(type, { bubbles: true, cancelable: true, dataTransfer: dt })); }
            fire(src, 'dragstart'); fire(dst, 'dragenter'); fire(dst, 'dragover'); fire(dst, 'drop'); fire(src, 'dragend');
            """;

    @Test
    @Order(1)
    void onglets() {
        open("widgets.html");
        click("#tab-2");
        assertThat(visible("#panel-2") && !visible("#panel-1")).as("onglet 2").isTrue();
    }

    @Test
    @Order(2)
    void accordeon() {
        click("#acc-header-2");
        assertThat(waitVisible("#acc-body-2", 2)).as("accordéon ouvert").isTrue();
        click("#acc-header-2");
        assertThat(waitUntil(drv -> !visible("#acc-body-2"), 2)).as("accordéon fermé").isTrue();
    }

    @Test
    @Order(3)
    void modale() {
        click("#btn-open-modal");
        assertThat(waitVisible("#modal", 2)).as("modale ouverte").isTrue();
        pause(450); // fin de l'animation d'ouverture
        type("#modal-input", "oui");
        click("#modal-ok");
        assertThat(waitText("#modal-result", "Réponse saisie : oui")).as("modale validée").isTrue();
        assertThat(waitUntil(drv -> !visible("#modal"), 2)).as("modale fermée").isTrue();
    }

    @Test
    @Order(4)
    void sous_menu_au_survol() {
        new Actions(d).moveToElement(inView("#menu-products-button")).perform();
        assertThat(waitVisible("#submenu", 2)).as("sous-menu au survol").isTrue();
        click("#submenu-item-2");
        assertThat(waitText("#menu-result", "Téléphones")).as("choix du sous-menu").isTrue();
    }

    @Test
    @Order(5)
    void liste_personnalisee() {
        click("#custom-select-button");
        click("#custom-option-3");
        assertThat(waitText("#custom-select-result", "Bleu")).as("liste personnalisée").isTrue();
    }

    @Test
    @Order(6)
    void curseur_a_la_souris() {
        int target = 50;
        int reached = dragSliderUntilDisplayed("#slider-handle", "#slider-value", target);
        assertThat(reached).as("curseur glissé jusqu'à la valeur affichée %d", target).isEqualTo(target);
        assertThat(attr("#slider-handle", "aria-valuenow")).as("valeur accessible du curseur").isEqualTo(String.valueOf(target));
    }

    @Test
    @Order(7)
    void shadow_dom() {
        WebElement host = $("#shadow-host");
        host.getShadowRoot().findElement(By.cssSelector("#shadow-button")).click();
        assertThat(host.getShadowRoot().findElement(By.cssSelector("#shadow-result")).getText())
                .as("shadow DOM").isEqualTo("Bouton Shadow DOM cliqué");
    }

    @Test
    @Order(8)
    void clavier_ctrl_a() {
        click("#key-input");
        new Actions(d).keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
        boolean ok = waitText("#key-result", "Ctrl\\+a");
        // Chrome local sous macOS applique la disposition du clavier système aux raccourcis : en AZERTY, Ctrl+a arrive en Ctrl+q
        Assumptions.assumeFalse(!ok && !BrowserExtension.remote()
                        && System.getProperty("os.name").toLowerCase().contains("mac")
                        && textContent("#key-result").contains("Ctrl+q"),
                "Non applicable : navigateur local macOS avec clavier AZERTY (Ctrl+a reçu en Ctrl+q)");
        assertThat(ok).as("clavier Ctrl+a (affiché : %s)", textContent("#key-result")).isTrue();
    }

    @Test
    @Order(9)
    void zone_defilante() {
        click("#scroll-target");
        assertThat(waitText("#scroll-result", "cliqué")).as("zone défilante").isTrue();
    }

    @Test
    @Order(10)
    void glisser_deposer_html5() {
        WebElement item = inView("#drag-item");
        WebElement target = $("#drop-target");
        new Actions(d).dragAndDrop(item, target).perform();
        boolean viaActions = waitText("#drag-result", "déposé", 3);
        if (!viaActions) {
            // Limite connue des pilotes : les Actions ne déclenchent pas toujours les événements HTML5 natifs
            js(HTML5_DND, item, target);
        }
        assertThat(waitText("#drag-result", "déposé", 3)).as("glisser-déposer HTML5").isTrue();
    }
}
