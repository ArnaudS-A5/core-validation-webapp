package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@DisplayName("Iframes")
class A07IframesIT extends SmokeTestBase {

    private void frame(String css) {
        d.switchTo().frame(inView(css));
    }

    @AfterEach
    void backToPage() {
        d.switchTo().defaultContent();
    }

    @Test
    @Order(1)
    void frame_simple() {
        open("frames.html");
        frame("#frame-simple");
        click("#frame-a-button");
        assertThat(waitText("#frame-a-result", "Frame A cliqué")).as("frame simple").isTrue();
    }

    @Test
    @Order(2)
    void frames_imbriquees() {
        frame("#frame-nested");
        frame("#inner-frame");
        click("#frame-inner-button");
        assertThat(waitText("#frame-inner-result", "Frame interne cliqué")).as("frame imbriquée").isTrue();
        d.switchTo().parentFrame();
        click("#frame-outer-button");
        assertThat(waitText("#frame-outer-result", "Frame externe cliqué")).as("frame externe").isTrue();
    }

    @Test
    @Order(3)
    void frame_differee() {
        assertThat(exists("#frame-delayed")).as("frame différée absente au départ").isFalse();
        click("#btn-load-frame");
        assertThat(waitUntil(drv -> exists("#frame-delayed"), 4)).as("frame différée insérée").isTrue();
        frame("#frame-delayed");
        assertThat(waitUntil(drv -> exists("#frame-delayed-button"), 4)).isTrue();
        click("#frame-delayed-button");
        assertThat(waitText("#frame-delayed-result", "cliqué")).as("frame différée").isTrue();
    }

    @Test
    @Order(4)
    void frame_sans_id_par_index() {
        d.switchTo().frame(inView($$("iframe").get(3)));
        click("#frame-anonymous-button");
        assertThat(waitText("#frame-anonymous-result", "cliqué")).as("frame anonyme par index").isTrue();
    }

    @Test
    @Order(5)
    void frame_srcdoc() {
        frame("#frame-srcdoc");
        click("#srcdoc-button");
        assertThat(waitText("#srcdoc-text", "^Bouton srcdoc cliqué$")).as("frame srcdoc").isTrue();
    }

    @Test
    @Order(6)
    void formulaire_dans_une_frame() {
        frame("#frame-form");
        type("#frame-input", "Albert");
        click("#frame-checkbox");
        click("#frame-submit");
        assertThat(waitText("#frame-form-result", "Bonjour Albert \\(confirmation demandée\\)")).as("formulaire dans la frame").isTrue();
    }

    @Test
    @Order(7)
    void retour_page_parente() {
        click("#btn-parent");
        assertThat(waitText("#parent-result", "parente cliqué")).as("retour page parente").isTrue();
    }
}
