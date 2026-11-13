package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.interactions.Actions;

@DisplayName("Clics")
class A02ClicsIT extends SmokeTestBase {

    @Test
    @Order(1)
    void calque_recouvre_le_bouton_au_depart() {
        open("clicks.html");
        Object blocking = js("document.getElementById('btn-covered').scrollIntoView({ block: 'center' });"
                + "var b = document.getElementById('btn-covered').getBoundingClientRect();"
                + "var el = document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2); return el && el.id;");
        assertThat(blocking).as("calque recouvre le bouton au départ").isEqualTo("cover");
    }

    @Test
    @Order(2)
    void compteur() {
        click("#btn-increment");
        click("#btn-increment");
        click("#btn-decrement");
        click("#btn-increment");
        assertThat(text("#counter-value")).as("compteur = 2").isEqualTo("2");
    }

    @Test
    @Order(3)
    void double_clic_et_clic_droit() {
        new Actions(d).doubleClick(inView("#btn-double-click")).perform();
        assertThat(waitText("#double-click-result", "Double-clic")).as("double-clic").isTrue();
        new Actions(d).contextClick(inView("#btn-right-click")).perform();
        assertThat(waitText("#right-click-result", "Clic droit")).as("clic droit").isTrue();
    }

    @Test
    @Order(4)
    void survol() {
        new Actions(d).moveToElement(inView("#hover-target")).perform();
        assertThat(waitVisible("#hover-tooltip", 2)).as("info-bulle au survol").isTrue();
        new Actions(d).moveToLocation(5, 5).perform();
        assertThat(waitUntil(drv -> !visible("#hover-tooltip"), 2)).as("info-bulle masquée").isTrue();
    }

    @Test
    @Order(5)
    void bouton_desactive_puis_active() {
        assertThat($("#btn-disabled").isEnabled()).as("bouton désactivé au départ").isFalse();
        click("#btn-enable-disabled");
        assertThat(waitText("#disabled-result", "activé après chargement")).as("activation après chargement").isTrue();
        click("#btn-disabled");
        assertThat(text("#disabled-result")).as("clic sur bouton activé").contains("clic pris en compte");
    }

    @Test
    @Order(6)
    void bouton_cache_puis_revele() {
        assertThat(visible("#hidden-btn")).as("bouton caché invisible").isFalse();
        click("#btn-reveal");
        assertThat(waitVisible("#hidden-btn", 4)).isTrue();
        click("#hidden-btn");
        assertThat(text("#reveal-result")).as("bouton révélé cliqué").contains("révélé cliqué");
    }

    @Test
    @Order(7)
    void bascule() {
        click("#btn-toggle");
        assertThat(attr("#btn-toggle", "aria-pressed")).as("bascule").isEqualTo("true");
    }

    @Test
    @Order(8)
    void bouton_mobile() {
        click("#btn-move");
        pause(1000);
        click("#moving-btn");
        assertThat(waitText("#moving-result", "mobile cliqué")).as("bouton mobile").isTrue();
    }

    @Test
    @Order(9)
    void bouton_recouvert_cliquable_apres_2_s() {
        click("#btn-covered", 6);
        assertThat(waitText("#covered-result", "recouvert cliqué")).as("bouton recouvert cliquable après 2 s").isTrue();
    }

    @Test
    @Order(91)
    void bouton_recouvert_en_permanence_branches_d_erreur() {
        Object blocking = js("var b = document.getElementById('btn-always-covered'); b.scrollIntoView({ block: 'center' });"
                + "var r = b.getBoundingClientRect(); var el = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);"
                + "return el && el.id;");
        assertThat(blocking).as("calque permanent au-dessus du bouton").isEqualTo("permanent-cover");
        // Clic natif : toujours intercepté
        assertThatThrownBy(() -> $("#btn-always-covered").click()).as("clic natif intercepté")
                .isInstanceOf(ElementClickInterceptedException.class);
        // Attente d'un clic possible : expire et remonte l'erreur réelle (le calque ne part jamais)
        long t0 = System.currentTimeMillis();
        assertThatThrownBy(() -> click("#btn-always-covered", 3)).as("attente expirée, erreur d'interception remontée")
                .isInstanceOf(ElementClickInterceptedException.class);
        assertThat(System.currentTimeMillis() - t0).as("l'attente a duré au moins 3 s").isGreaterThanOrEqualTo(2900);
        assertThat(textContent("#always-covered-result")).as("aucun clic n'a abouti").isEmpty();
        // Seul un clic JavaScript atteint le bouton
        js("document.getElementById('btn-always-covered').click()");
        assertThat(waitText("#always-covered-result", "contournement par JavaScript")).as("clic par JavaScript").isTrue();
    }

    @Test
    @Order(10)
    void clic_avec_appel_serveur() {
        click("#btn-server-click");
        assertThat(waitText("#server-click-result", "Heure serveur : \\d{4}-")).as("clic serveur").isTrue();
    }

    @Test
    @Order(11)
    void suppression_de_ligne() {
        click("#delete-row-2");
        assertThat(waitText("#row-result", "row-2 supprimée")).isTrue();
        assertThat(exists("#row-2")).as("suppression ligne 2").isFalse();
    }
}
