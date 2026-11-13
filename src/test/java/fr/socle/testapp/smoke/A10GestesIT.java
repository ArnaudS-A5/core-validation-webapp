package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;

@DisplayName("Gestes souris et clavier")
class A10GestesIT extends SmokeTestBase {

    @Test
    @Order(1)
    void appui_long_jusqu_a_validation() {
        open("gestes.html");
        // Geste : maintenir le bouton enfoncé jusqu'à ce que l'application confirme, puis relâcher
        new Actions(d).clickAndHold(inView("#btn-long-press")).perform();
        boolean validated = waitText("#long-press-result", "Appui long validé", 4);
        new Actions(d).release().perform();
        assertThat(validated).as("appui long validé").isTrue();
    }

    @Test
    @Order(2)
    void appui_trop_court() {
        new Actions(d).clickAndHold($("#btn-long-press")).pause(300).release().perform();
        assertThat(waitText("#long-press-result", "Appui trop court")).as("appui trop court refusé").isTrue();
    }

    @Test
    @Order(3)
    void liste_triable_a_la_souris() {
        // Geste : saisir « Élément 1 » et le faire glisser jusque sous « Élément 3 »
        WebElement first = inView("#sortable-item-1");
        WebElement third = $("#sortable-item-3");
        int quarter = third.getSize().getHeight() / 4;
        Actions drag = new Actions(d).clickAndHold(first);
        for (int i = 0; i < 6; i++) {
            drag.moveByOffset(0, 8);
        }
        drag.moveToElement(third, 0, quarter).moveToElement(third, 0, quarter + 1).release().perform();
        assertThat(waitText("#sortable-result", "Ordre : 2, 3, 1, 4, 5")).as("élément 1 déplacé sous l'élément 3 (affiché : %s)",
                textContent("#sortable-result")).isTrue();
    }

    @Test
    @Order(4)
    void molette_jusqu_au_bouton_du_bas() {
        // Geste : faire défiler la zone à la molette jusqu'à voir le bouton du bas
        WebElement zone = inView("#wheel-zone");
        for (int i = 0; i < 15 && !visibleInside("#wheel-target", "#wheel-zone"); i++) {
            new Actions(d).scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(zone), 0, 120).perform();
            pause(100);
        }
        assertThat(visibleInside("#wheel-target", "#wheel-zone")).as("bouton atteint à la molette").isTrue();
        click("#wheel-target");
        assertThat(waitText("#wheel-result", "Bouton du bas de la zone cliqué \\(molette : [1-9]")).as("molette comptée").isTrue();
    }

    @Test
    @Order(5)
    void defilement_horizontal() {
        WebElement zone = inView("#hscroll-zone");
        for (int i = 0; i < 30 && !visibleInside("#hscroll-target", "#hscroll-zone"); i++) {
            new Actions(d).scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(zone), 200, 0).perform();
            pause(50);
        }
        assertThat(visibleInside("#hscroll-target", "#hscroll-zone")).as("bouton de droite atteint").isTrue();
        click("#hscroll-target");
        assertThat(waitText("#hscroll-result", "tout à droite cliqué")).isTrue();
    }

    @Test
    @Order(6)
    void dessin_dans_un_canvas() {
        // Geste : tracer une signature en zigzag, de gauche à droite
        WebElement canvas = inView("#draw-canvas");
        Actions trace = new Actions(d).moveToElement(canvas, -160, 0).clickAndHold();
        for (int i = 0; i < 16; i++) {
            trace.moveByOffset(20, i % 2 == 0 ? -30 : 30);
        }
        trace.release().perform();
        assertThat(waitText("#draw-result", "Tracé : \\d+ points, largeur (2\\d\\d|3\\d\\d) px")).as("signature tracée (%s)",
                textContent("#draw-result")).isTrue();
        click("#btn-draw-clear");
        assertThat(waitText("#draw-result", "Tracé : 0 point")).as("canvas effacé").isTrue();
    }

    @Test
    @Order(7)
    void clic_par_coordonnees_dans_un_canvas() {
        // Les cibles A, B, C (centres à x = 70, 210, 350 ; y = 60) n'existent que sous forme de dessin
        WebElement canvas = inView("#targets-canvas");
        int originX = -canvas.getSize().getWidth() / 2;
        int originY = -canvas.getSize().getHeight() / 2;
        new Actions(d).moveToElement(canvas, originX + 210, originY + 60).click().perform();
        assertThat(waitText("#canvas-click-result", "Cible B touchée")).as("cible B").isTrue();
        new Actions(d).moveToElement(canvas, originX + 350, originY + 60).click().perform();
        assertThat(waitText("#canvas-click-result", "Cible C touchée")).as("cible C").isTrue();
        new Actions(d).moveToElement(canvas, originX + 140, originY + 110).click().perform();
        assertThat(waitText("#canvas-click-result", "Aucune cible touchée")).as("clic hors cible").isTrue();
    }

    @Test
    @Order(8)
    void elements_svg() {
        click("#svg-point-2");
        assertThat(waitText("#svg-result", "Point Février : 42")).as("clic sur un point SVG").isTrue();
        new Actions(d).moveToElement(inView("#svg-point-4")).perform();
        assertThat(waitText("#svg-tooltip", "Avril — 58")).as("survol d'un point SVG").isTrue();
    }

    @Test
    @Order(9)
    void navigation_au_clavier() {
        click("#tab-field-1");
        assertThat(waitText("#focus-result", "Focus : champ 1")).isTrue();
        press(Keys.TAB);
        assertThat(focusedId()).as("Tab : champ suivant").isEqualTo("tab-field-2");
        assertThat(waitText("#blur-result", "Sortie de : champ 1")).as("perte de focus signalée").isTrue();
        new Actions(d).keyDown(Keys.SHIFT).sendKeys(Keys.TAB).keyUp(Keys.SHIFT).perform();
        assertThat(focusedId()).as("Maj+Tab : champ précédent").isEqualTo("tab-field-1");
        press(Keys.TAB, Keys.TAB, Keys.TAB);
        assertThat(focusedId()).as("élément focalisable atteint au clavier").isEqualTo("tab-custom");
        press(Keys.ENTER);
        assertThat(waitText("#tab-custom-result", "activé au clavier \\(Entrée\\)")).isTrue();
        press(Keys.SPACE);
        assertThat(waitText("#tab-custom-result", "activé au clavier \\(Espace\\)")).isTrue();
    }

    @Test
    @Order(10)
    void copier_coller_au_clavier() {
        click("#copy-source");
        press(Keys.chord(shortcutKey(), "a"));
        press(Keys.chord(shortcutKey(), "c"));
        boolean copied = waitText("#copy-result", "Texte copié", 3);
        Assumptions.assumeFalse(!copied && localMac(),
                "Non applicable : navigateur local macOS (raccourcis soumis à la disposition du clavier système)");
        assertThat(copied).as("copie au clavier").isTrue();
        click("#paste-target");
        press(Keys.chord(shortcutKey(), "v"));
        assertThat(waitText("#paste-result", "Collé : Texte à copier 42")).as("collage au clavier").isTrue();
    }
}
