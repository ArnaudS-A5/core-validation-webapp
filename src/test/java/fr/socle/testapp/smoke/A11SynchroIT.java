package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;

@DisplayName("Synchronisation")
class A11SynchroIT extends SmokeTestBase {

    @Test
    @Order(1)
    void defilement_infini_jusqu_a_un_article() {
        open("synchro.html");
        assertThat(waitText("#infinite-status", "10 / 57 articles chargés")).as("premier lot chargé").isTrue();
        // Geste : faire défiler la liste jusqu'à voir l'article 45 (chargé en cours de route)
        WebElement zone = inView("#infinite-zone");
        for (int i = 0; i < 40 && !visibleInside("#infinite-item-45", "#infinite-zone"); i++) {
            new Actions(d).scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(zone), 0, 150).perform();
            pause(150);
        }
        assertThat(visibleInside("#infinite-item-45", "#infinite-zone")).as("article 45 atteint par défilement").isTrue();
        assertThat(textContent("#infinite-item-45")).isEqualTo("Article 045");
        for (int i = 0; i < 30 && !textContent("#infinite-status").startsWith("Tous"); i++) {
            new Actions(d).scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(zone), 0, 300).perform();
            pause(150);
        }
        assertThat(waitText("#infinite-status", "Tous les articles sont chargés \\(57\\)")).as("fin de liste").isTrue();
    }

    @Test
    @Order(2)
    void animation_longue_attendre_l_arret() {
        WebElement target = inView("#anim-target");
        click("#btn-animate");
        String start = rect(target);
        pause(400);
        assertThat(rect(target)).as("la cible se déplace").isNotEqualTo(start);
        // Synchronisation : attendre que la cible soit immobile avant de cliquer
        assertThat(waitUntil(drv -> {
            String before = rect(target);
            pause(250);
            return before.equals(rect(target)) && "idle".equals(attr("#anim-target", "data-state"));
        }, 6)).as("animation terminée").isTrue();
        click("#anim-target");
        assertThat(waitText("#anim-result", "Cible cliquée \\(à l'arrêt\\)")).as("clic après l'animation").isTrue();
    }

    @Test
    @Order(3)
    void flux_temps_reel_sse() {
        click("#btn-feed-start");
        assertThat(waitUntil(drv -> exists("#feed-item-8"), 10)).as("8e événement reçu").isTrue();
        assertThat(exists("#feed-item-1")).as("les plus anciens disparaissent").isFalse();
        assertThat($$("#feed-list li").size()).as("5 événements affichés au plus").isLessThanOrEqualTo(5);
        assertThat(waitText("#feed-status", "Flux terminé \\(12 événements\\)", 10)).as("fin du flux").isTrue();
        assertThat(textContent("#feed-list li:last-child")).isEqualTo("Événement 12");
    }

    @Test
    @Order(4)
    void action_disponible_a_partir_d_un_seuil() {
        click("#btn-threshold-start");
        assertThat($("#btn-threshold-action").isEnabled()).as("action indisponible au départ").isFalse();
        assertThat(waitUntil(drv -> $("#btn-threshold-action").isEnabled(), 10)).as("seuil de 80 % atteint").isTrue();
        click("#btn-threshold-action");
        assertThat(waitText("#threshold-result", "Action réalisée à (8\\d|9\\d|100) %")).as("action au-delà du seuil").isTrue();
    }

    @Test
    @Order(5)
    void shadow_dom_ferme() {
        WebElement host = inView("#shadow-closed-host");
        assertThat(js("return arguments[0].shadowRoot", host)).as("Shadow DOM fermé invisible pour le JavaScript de la page").isNull();
        // Clic à l'emplacement de l'hôte : fonctionne quel que soit le navigateur
        new Actions(d).moveToElement(host).click().perform();
        assertThat(waitText("#shadow-closed-result", "Bouton du Shadow DOM fermé cliqué")).as("clic par position dans le Shadow DOM fermé").isTrue();
        js("document.getElementById('shadow-closed-result').textContent = ''");
        // WebDriver, lui, peut atteindre un Shadow DOM fermé selon le navigateur (Chrome : oui)
        try {
            host.getShadowRoot().findElement(By.cssSelector("button")).click();
            assertThat(waitText("#shadow-closed-result", "Bouton du Shadow DOM fermé cliqué")).as("clic via WebDriver dans le Shadow DOM fermé").isTrue();
        } catch (NoSuchShadowRootException e) {
            System.out.println("[fumée] " + System.getProperty("browser", "chrome") + " : Shadow DOM fermé inaccessible à WebDriver");
        }
    }

    @Test
    @Order(6)
    void shadow_dom_imbrique_et_differe() {
        SearchContext outer = inView("#shadow-outer-host").getShadowRoot();
        assertThat(waitUntil(drv -> {
            try {
                outer.findElement(By.cssSelector("#shadow-inner-host")).getShadowRoot();
                return true;
            } catch (NoSuchShadowRootException e) {
                return false;
            }
        }, 4)).as("Shadow DOM interne créé après 1 s").isTrue();
        SearchContext inner = outer.findElement(By.cssSelector("#shadow-inner-host")).getShadowRoot();
        inner.findElement(By.cssSelector("#shadow-inner-button")).click();
        assertThat(inner.findElement(By.cssSelector("#shadow-inner-result")).getText())
                .as("clic dans le Shadow DOM imbriqué").isEqualTo("Bouton du Shadow DOM imbriqué cliqué");
    }

    @Test
    @Order(7)
    void bandeau_fixe_qui_recouvre() {
        WebElement button = $("#btn-under-banner");
        // Amené en bas de la zone visible, le bouton passe sous le bandeau fixe
        js("arguments[0].scrollIntoView({ block: 'end' })", button);
        Object covering = js("var r = arguments[0].getBoundingClientRect();"
                + "var e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);"
                + "return e && e.closest('#sticky-banner') ? 'bandeau' : (e && e.id);", button);
        assertThat(covering).as("le bandeau recouvre le bouton").isEqualTo("bandeau");
        assertThatThrownBy(button::click).as("clic intercepté par le bandeau")
                .isInstanceOf(ElementClickInterceptedException.class);
        // Geste d'utilisateur : accepter le bandeau, puis cliquer
        click("#btn-banner-accept");
        assertThat(waitAbsent("#sticky-banner", 2)).as("bandeau fermé").isTrue();
        click("#btn-under-banner");
        assertThat(waitText("#banner-result", "Bouton en bas de page cliqué")).isTrue();
    }
}
