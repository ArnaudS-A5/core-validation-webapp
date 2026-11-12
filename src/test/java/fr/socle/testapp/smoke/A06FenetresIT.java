package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

@DisplayName("Fenêtres, pop-up et alertes")
class A06FenetresIT extends SmokeTestBase {

    private String main;

    private Alert alert(int seconds) {
        return new WebDriverWait(d, Duration.ofSeconds(seconds)).until(ExpectedConditions.alertIsPresent());
    }

    /** Attend l'ouverture d'une nouvelle fenêtre et bascule dessus. */
    private String switchToNewWindow(Set<String> before) {
        waitUntil(drv -> drv.getWindowHandles().size() > before.size(), 5);
        Set<String> after = new HashSet<>(d.getWindowHandles());
        after.removeAll(before);
        String handle = after.iterator().next();
        d.switchTo().window(handle);
        return handle;
    }

    @Test
    @Order(1)
    void alerte() {
        open("windows.html");
        main = d.getWindowHandle();
        click("#btn-alert");
        Alert alert = alert(3);
        assertThat(alert.getText()).as("texte de l'alerte").isEqualTo("Ceci est une alerte");
        alert.accept();
        assertThat(waitText("#alert-result", "fermée")).as("alert").isTrue();
    }

    @Test
    @Order(2)
    void confirmation_acceptee_puis_refusee() {
        click("#btn-confirm");
        alert(3).accept();
        assertThat(waitText("#confirm-result", "confirmée")).as("confirm accepté").isTrue();
        click("#btn-confirm");
        alert(3).dismiss();
        assertThat(waitText("#confirm-result", "annulée")).as("confirm refusé").isTrue();
    }

    @Test
    @Order(3)
    void saisie_acceptee_puis_annulee() {
        click("#btn-prompt");
        Alert prompt = alert(3);
        prompt.sendKeys("Albert");
        prompt.accept();
        assertThat(waitText("#prompt-result", "Bonjour Albert")).as("prompt").isTrue();
        click("#btn-prompt");
        alert(3).dismiss();
        assertThat(waitText("#prompt-result", "annulée")).as("prompt annulé").isTrue();
    }

    @Test
    @Order(4)
    void alerte_differee() {
        click("#btn-alert-delayed");
        alert(5).accept();
        assertThat(waitText("#alert-delayed-result", "fermée", 5)).as("alerte différée").isTrue();
    }

    @Test
    @Order(5)
    void pop_up_et_post_message() {
        Set<String> before = new HashSet<>(d.getWindowHandles());
        click("#btn-open-popup");
        switchToNewWindow(before);
        assertThat(waitText("#popup-origin", "^popup$")).as("pop-up ouvert").isTrue();
        click("#popup-send");
        d.switchTo().window(main);
        assertThat(waitText("#popup-message", "Message de la fenêtre « popup »")).as("postMessage vers la fenêtre d'origine").isTrue();

        d.switchTo().window(switchBack(before));
        try {
            click("#popup-close");
        } catch (WebDriverException closedDuringClick) {
            // la fenêtre se ferme pendant le clic
        }
        d.switchTo().window(main);
        assertThat(waitUntil(drv -> drv.getWindowHandles().size() == before.size(), 3)).as("pop-up fermé par script").isTrue();
    }

    private String switchBack(Set<String> before) {
        Set<String> handles = new HashSet<>(d.getWindowHandles());
        handles.removeAll(before);
        return handles.iterator().next();
    }

    @Test
    @Order(6)
    void nouvel_onglet() {
        Set<String> before = new HashSet<>(d.getWindowHandles());
        click("#link-new-tab");
        switchToNewWindow(before);
        assertThat(waitText("#popup-origin", "^onglet$")).as("nouvel onglet").isTrue();
        d.close();
        d.switchTo().window(main);
    }

    @Test
    @Order(7)
    void deux_pop_ups_par_nom_et_par_titre() {
        Set<String> before = new HashSet<>(d.getWindowHandles());
        click("#btn-open-two-popups");
        assertThat(waitUntil(drv -> drv.getWindowHandles().size() == before.size() + 2, 5)).as("deux fenêtres ouvertes").isTrue();

        // Bascule par nom de fenêtre (window.name donné à window.open)
        d.switchTo().window("popupA");
        assertThat(waitText("#popup-origin", "^popup-A$")).as("pop-up A trouvé par son nom").isTrue();
        String handleA = d.getWindowHandle();

        // Bascule par titre de page
        String handleB = null;
        for (String handle : d.getWindowHandles()) {
            if (!before.contains(handle) && d.switchTo().window(handle).getTitle().endsWith("popup-B")) {
                handleB = handle;
                break;
            }
        }
        assertThat(handleB).as("pop-up B trouvé par son titre").isNotNull();
        assertThat(textContent("#popup-origin")).as("pop-up B").isEqualTo("popup-B");

        d.close();
        d.switchTo().window(handleA).close();
        d.switchTo().window(main);
        assertThat(d.getWindowHandles()).as("retour à la seule fenêtre principale").hasSize(before.size());
    }

    @Test
    @Order(8)
    void pop_up_differe_de_2_s() {
        Set<String> before = new HashSet<>(d.getWindowHandles());
        click("#btn-open-delayed");
        assertThat(waitText("#popup-open-result", "Ouverture dans 2 secondes")).isTrue();
        assertThat(d.getWindowHandles()).as("pas encore de pop-up juste après le clic").hasSize(before.size());
        assertThat(waitUntil(drv -> drv.getWindowHandles().size() > before.size(), 5)).as("pop-up ouvert après 2 s").isTrue();
        switchToNewWindow(before);
        assertThat(waitText("#popup-origin", "^popup-differe$")).as("pop-up différé").isTrue();
        d.close();
        d.switchTo().window(main);
    }

    @Test
    @Order(9)
    void redirection_differee() {
        click("#btn-redirect");
        assertThat(waitUntil(drv -> drv.getCurrentUrl().contains("popup.html?from=redirection"), 4))
                .as("redirection différée").isTrue();
    }
}
