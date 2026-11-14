package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Script et feuille de style servis avec 4 s de retard ({@code ?ressources=4000}), demandés après le chargement
 * complet de la page : ils ne peuvent pas arriver moins de 4 s après l'ouverture de la page (minimum, sans maximum).
 * L'instant d'arrivée est mesuré dans la page, depuis le début de la navigation : vu de Java, le style pourrait
 * être déjà là avant que le test ne le regarde, et la mesure ne prouverait rien.
 */
@DisplayName("Script et style tardifs")
class L02RessourcesTardivesLT extends LatenceTestBase {

    /** Retard imposé aux fichiers du dossier lent/ par le paramètre ressources de la page. */
    private static final long RETARD_RESSOURCES_MS = 4_000;

    @Test
    @Order(1)
    void page_chargee_bouton_present_mais_inerte() {
        open("latence.html?ressources=" + RETARD_RESSOURCES_MS);
        assertThat(js("return document.readyState")).as("page complètement chargée").isEqualTo("complete");
        assertThat($("#btn-script-tardif").isDisplayed() && $("#btn-script-tardif").isEnabled())
                .as("bouton affiché et actif pour Selenium").isTrue();
        click("#btn-script-tardif");
        pause(500);
        assertThat(textContent("#script-tardif-resultat")).as("clic trop tôt : aucun effet").isEmpty();
        assertThat(attr("#etat-script", "data-pret")).isEqualTo("false");
        assertThat(visible("#btn-css-tardif")).as("bouton masqué tant que le style n'est pas là").isFalse();
    }

    @Test
    @Order(2)
    void attendre_le_script_puis_cliquer() {
        assertThat(waitUntil(drv -> "true".equals(attr("#etat-script", "data-pret")), 15)).as("script arrivé").isTrue();
        assertThat(Long.parseLong(attr("#etat-script", "data-arrivee-ms"))).as("script arrivé au moins 4 s après l'ouverture")
                .isGreaterThanOrEqualTo(RETARD_RESSOURCES_MS);
        click("#btn-script-tardif");
        assertThat(waitText("#script-tardif-resultat", "Action exécutée par le script tardif")).isTrue();
    }

    @Test
    @Order(3)
    void attendre_le_style_puis_cliquer_le_bouton_revele() {
        assertThat(waitVisible("#btn-css-tardif", 15)).as("bouton révélé par le style tardif").isTrue();
        assertThat(Long.parseLong(attr("#etat-css", "data-arrivee-ms"))).as("style arrivé au moins 4 s après l'ouverture")
                .isGreaterThanOrEqualTo(RETARD_RESSOURCES_MS);
        assertThat(text("#etat-css")).isEqualTo("Style chargé");
        click("#btn-css-tardif");
        assertThat(waitText("#css-tardif-resultat", "Bouton révélé par le style tardif cliqué")).isTrue();
    }
}
