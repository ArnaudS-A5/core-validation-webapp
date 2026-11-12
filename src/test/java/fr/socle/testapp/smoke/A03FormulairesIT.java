package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

@DisplayName("Formulaires")
class A03FormulairesIT extends SmokeTestBase {

    @Test
    @Order(1)
    void listes_dependantes() {
        open("forms.html");
        assertThat(waitText("#select-country", "Allemagne")).as("liste pays chargée en asynchrone").isTrue();
        assertThat($("#select-city").isEnabled()).as("ville désactivée").isFalse();
        select("#select-country", "FR");
        assertThat(waitUntil(drv -> $("#select-city").isEnabled(), 4)).isTrue();
        assertThat($$("#select-city option").stream().map(WebElement::getText)).as("villes de France").contains("Lyon");
        select("#select-city", "Lyon");
    }

    @Test
    @Order(2)
    void cases_a_cocher() {
        click("#chk-all");
        assertThat($$(".chk-option")).as("tout cocher").allMatch(WebElement::isSelected);
        click("#chk-sms");
        assertThat(js("return document.getElementById('chk-all').indeterminate")).as("indéterminé").isEqualTo(true);
    }

    @Test
    @Order(3)
    void boutons_radio_dont_personnalise() {
        click("#radio-mrs");
        click("#plan-premium-label");
        Object values = js("return document.querySelector('input[name=civility]:checked').value + '/'"
                + " + document.querySelector('input[name=plan]:checked').value");
        assertThat(values).as("radios (dont personnalisé)").isEqualTo("Mme/premium");
    }

    @Test
    @Order(4)
    void listes_et_curseur() {
        select("#select-static", "weekly");
        select("#select-multiple", "java", "diving");
        js("var e = document.getElementById('input-range'); e.value = 80; e.dispatchEvent(new Event('input', { bubbles: true }));");
        assertThat(text("#range-value")).as("curseur").isEqualTo("80");
    }

    @Test
    @Order(5)
    void validation_serveur_422() {
        click("#form-submit");
        assertThat(waitText("#error-name", "obligatoire")).as("validation 422 (nom)").isTrue();
        assertThat(textContent("#error-email")).as("validation 422 (e-mail)").contains("invalide");
    }

    @Test
    @Order(6)
    void envoi_reussi() throws Exception {
        type("#input-name", "Albert");
        type("#input-email", "a@b.fr");
        type("#textarea-comment", "Bonjour");
        click("#form-submit");
        assertThat(waitText("#form-result", "enregistré")).as("succès").isTrue();
        JsonNode received = new ObjectMapper().readTree(textContent("#form-received"));
        assertThat(received.path("city").asText()).as("données reçues : ville").isEqualTo("Lyon");
        assertThat(received.path("interests").size()).as("données reçues : centres d'intérêt").isEqualTo(2);
        assertThat(received.path("options").size()).as("données reçues : options").isEqualTo(2);
        assertThat(received.path("plan").asText()).as("données reçues : formule").isEqualTo("premium");
    }

    @Test
    @Order(7)
    void reinitialisation() {
        click("#form-reset");
        assertThat(waitUntil(drv -> textContent("#form-result").isEmpty(), 2)).as("réinitialisation").isTrue();
    }
}
