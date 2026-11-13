package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Keys;
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

    // ---------- Saisies avancées ----------

    @Test
    @Order(8)
    void lecture_seule_puis_deverrouillee() {
        assertThat($("#input-readonly").getDomProperty("readOnly")).as("référence en lecture seule").isEqualTo("true");
        assertThat($("#input-readonly").isEnabled()).as("lecture seule mais active").isTrue();
        assertThat($("#input-disabled").isEnabled()).as("champ désactivé").isFalse();
        click("#btn-unlock-readonly");
        assertThat(waitUntil(drv -> "false".equals($("#input-readonly").getDomProperty("readOnly")), 4))
                .as("référence déverrouillée après 1,5 s").isTrue();
        clearAndType("#input-readonly", "REF-2026-777");
        press(Keys.TAB);
        assertThat(waitText("#readonly-result", "Référence saisie : REF-2026-777")).as("référence modifiée").isTrue();
    }

    @Test
    @Order(9)
    void calendrier_javascript() {
        // Geste : ouvrir le calendrier, avancer jusqu'au mois visé, choisir le 15 (ou le premier jour ouvré suivant)
        LocalDate wanted = LocalDate.now().plusMonths(2).withDayOfMonth(15);
        while (wanted.getDayOfWeek() == DayOfWeek.SATURDAY || wanted.getDayOfWeek() == DayOfWeek.SUNDAY) {
            wanted = wanted.plusDays(1);
        }
        String monthLabel = wanted.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));

        click("#datepicker-input");
        assertThat(waitVisible("#datepicker", 2)).as("calendrier ouvert").isTrue();
        for (int i = 0; i < 24 && !monthLabel.equals(textContent("#datepicker-month")); i++) {
            click("#datepicker-next");
        }
        assertThat(textContent("#datepicker-month")).as("mois atteint").isEqualTo(monthLabel);

        LocalDate weekend = wanted.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        if (weekend.getMonth() == wanted.getMonth()) {
            assertThat($("#datepicker-day-" + weekend.getDayOfMonth()).isEnabled()).as("week-end indisponible").isFalse();
        }
        click("#datepicker-day-" + wanted.getDayOfMonth());
        String expected = wanted.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat($("#datepicker-input").getDomProperty("value")).as("date saisie par le calendrier").isEqualTo(expected);
        assertThat(waitText("#datepicker-result", "Date choisie : " + expected)).isTrue();
        assertThat(visible("#datepicker")).as("calendrier refermé").isFalse();
    }

    @Test
    @Order(10)
    void liste_a_saisie_au_clavier() {
        // Geste : taper quelques lettres, descendre au clavier jusqu'à « Espagne », valider par Entrée
        click("#combo-input");
        type("#combo-input", "a");
        assertThat(waitUntil(drv -> $$("#combo-list [role=option]").size() == 4, 4)).as("4 pays proposés").isTrue();
        for (int i = 0; i < 8; i++) {
            press(Keys.ARROW_DOWN);
            String activeId = attr("#combo-input", "aria-activedescendant");
            if (activeId != null && "Espagne".equals(textContent("#" + activeId))) {
                break;
            }
        }
        press(Keys.ENTER);
        assertThat(waitText("#combo-result", "Pays choisi : Espagne \\(ES\\)")).as("choix au clavier").isTrue();
        assertThat($("#combo-input").getDomProperty("value")).isEqualTo("Espagne");
        clearAndType("#combo-input", "e");
        assertThat(waitVisible("#combo-list", 3)).as("liste rouverte").isTrue();
        press(Keys.ESCAPE);
        assertThat(visible("#combo-list")).as("Échap ferme la liste").isFalse();
    }
}
