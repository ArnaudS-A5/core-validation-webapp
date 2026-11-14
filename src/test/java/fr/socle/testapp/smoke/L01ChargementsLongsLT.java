package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

/**
 * Chargements longs de la page Latence. Chaque durée est comparée à un minimum tiré de la configuration de la
 * page (pas de maximum : le délai d'attente du test en tient lieu).
 * <ul>
 *   <li>Ce que la page fait seule (bouton, bloc, tableau) : durée mesurée dans la page, car vu de Java l'événement
 *       a pu se produire avant que le test ne le regarde, et la mesure ne prouverait rien.</li>
 *   <li>Ce qui suit un geste du test (flux, réponse JSON) : chronomètre démarré avant le geste, et le test guette
 *       aussitôt le résultat ; le temps mesuré englobe le vrai délai, malgré le trajet par le Grid.</li>
 * </ul>
 */
@DisplayName("Chargements longs et compte-gouttes")
class L01ChargementsLongsLT extends LatenceTestBase {

    /** latence.js : bouton activé par la réponse de /api/slow?delay=3500. */
    private static final long ACTIVATION_BOUTON_MS = 3_500;
    /** latence.js : bloc chargé par /api/slow?delay=4000. */
    private static final long CHARGEMENT_BLOC_MS = 4_000;
    /** latence.js : 10 éléments, une pause de 500 ms avant chacun (/api/latence/goutte). */
    private static final long PREMIER_ELEMENT_MS = 500;
    private static final long FLUX_COMPLET_MS = 10 * 500;
    /** latence.js : tableau relancé toutes les 4 s. */
    private static final long INTERVALLE_TABLEAU_MS = 4_000;

    @Test
    @Order(1)
    void arrivee_sur_la_page_rien_n_est_pret() {
        open("latence.html");
        assertThat(attr("#bloc-long", "data-etat")).as("bloc en cours de chargement").isEqualTo("chargement");
        assertThat($("#btn-active-tard").isEnabled()).as("bouton encore désactivé").isFalse();
    }

    @Test
    @Order(2)
    void bouton_active_apres_3_5_s() {
        assertThat(waitUntil(drv -> $("#btn-active-tard").isEnabled(), 15)).as("bouton activé").isTrue();
        assertThat(Long.parseLong(attr("#bouton-tardif-etat", "data-active-ms"))).as("activation au moins 3,5 s après l'ouverture")
                .isGreaterThanOrEqualTo(ACTIVATION_BOUTON_MS);
        click("#btn-active-tard");
        assertThat(waitText("#bouton-tardif-resultat", "Action différée exécutée")).isTrue();
    }

    @Test
    @Order(3)
    void bloc_charge_en_4_s() {
        assertThat(waitUntil(drv -> "charge".equals(attr("#bloc-long", "data-etat")), 15)).as("bloc chargé").isTrue();
        assertThat(Long.parseLong(attr("#bloc-long", "data-duree-ms"))).as("chargement d'au moins 4 s")
                .isGreaterThanOrEqualTo(CHARGEMENT_BLOC_MS);
        assertThat(text("#bloc-long")).startsWith("Bloc chargé en");
    }

    @Test
    @Order(4)
    void flux_au_compte_gouttes_attendre_le_dernier_element() {
        long avantClic = System.currentTimeMillis();
        click("#btn-goutte-flux");
        // Les premiers éléments arrivent alors que le flux n'est pas terminé
        assertThat(waitUntil(drv -> exists("#goutte-item-1"), 10)).as("premier élément reçu").isTrue();
        assertThat(System.currentTimeMillis() - avantClic).as("premier élément après au moins 500 ms")
                .isGreaterThanOrEqualTo(PREMIER_ELEMENT_MS);
        assertThat(exists("#goutte-item-10")).as("dernier élément pas encore là").isFalse();
        assertThat(attr("#goutte-statut", "data-etat")).isEqualTo("en-cours");
        assertThat(waitUntil(drv -> "termine".equals(attr("#goutte-statut", "data-etat")), 20)).as("flux terminé").isTrue();
        assertThat(System.currentTimeMillis() - avantClic).as("flux complet après au moins 5 s")
                .isGreaterThanOrEqualTo(FLUX_COMPLET_MS);
        assertThat($$("#goutte-list li")).hasSize(10);
        assertThat(text("#goutte-item-10")).isEqualTo("Élément 10");
    }

    @Test
    @Order(5)
    void reponse_json_au_compte_gouttes_inexploitable_avant_la_fin() {
        long avantClic = System.currentTimeMillis();
        click("#btn-goutte-json");
        pause(1_500);
        assertThat(text("#goutte-json-statut")).as("rien d'exploitable après 1,5 s").contains("En attente");
        assertThat(waitText("#goutte-json-statut", "Réponse complète : 10 éléments", 20)).isTrue();
        assertThat(System.currentTimeMillis() - avantClic).as("réponse complète après au moins 5 s")
                .isGreaterThanOrEqualTo(FLUX_COMPLET_MS);
    }

    @Test
    @Order(6)
    void tableau_rafraichi_reference_perimee_puis_clic() {
        assertThat(waitUntil(drv -> exists("#auto-select-1"), 10)).as("tableau affiché").isTrue();
        WebElement ancienne = $("#auto-select-1");
        int avant = Integer.parseInt(attr("#auto-refresh-count", "data-count"));
        assertThat(waitUntil(drv -> Integer.parseInt(attr("#auto-refresh-count", "data-count")) > avant, 15))
                .as("rafraîchissement automatique").isTrue();
        // Intervalle entre deux lancements, mesuré dans la page : vu de Java, il dépendrait du rythme d'observation
        assertThat(Long.parseLong(attr("#auto-refresh-count", "data-intervalle-ms")))
                .as("rafraîchissements espacés d'au moins 4 s").isGreaterThanOrEqualTo(INTERVALLE_TABLEAU_MS);
        boolean perimee;
        try {
            ancienne.isDisplayed();
            perimee = false;
        } catch (StaleElementReferenceException e) {
            perimee = true;
        }
        assertThat(perimee).as("référence prise avant le rafraîchissement périmée").isTrue();
        // Le clic recherche l'élément à chaque tentative : il aboutit même si un rafraîchissement survient
        click("#auto-select-3");
        assertThat(waitText("#auto-resultat", "Article 3 sélectionné")).isTrue();
    }
}
