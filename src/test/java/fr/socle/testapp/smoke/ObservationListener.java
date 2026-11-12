package fr.socle.testapp.smoke;

import java.util.Collection;

import org.openqa.selenium.Alert;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.events.WebDriverListener;

/**
 * Mode observation ({@code -Dsmoke.ralenti=800}) : pause après chaque geste visible
 * (clic, saisie, action souris/clavier, navigation, changement de fenêtre, alerte)
 * pour suivre le déroulé à l'écran, par exemple dans la vue VNC du Selenium Grid.
 * Les lectures et les attentes ne sont pas ralenties.
 */
class ObservationListener implements WebDriverListener {

    private final long pauseMs;

    ObservationListener(long pauseMs) {
        this.pauseMs = pauseMs;
    }

    private void pause() {
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void afterGet(WebDriver driver, String url) {
        pause();
    }

    @Override
    public void afterClick(WebElement element) {
        pause();
    }

    @Override
    public void afterSendKeys(WebElement element, CharSequence... keysToSend) {
        pause();
    }

    @Override
    public void afterClear(WebElement element) {
        pause();
    }

    @Override
    public void afterPerform(WebDriver driver, Collection<Sequence> actions) {
        pause();
    }

    @Override
    public void afterWindow(WebDriver.TargetLocator targetLocator, String nameOrHandle, WebDriver driver) {
        pause();
    }

    @Override
    public void afterAccept(Alert alert) {
        pause();
    }

    @Override
    public void afterDismiss(Alert alert) {
        pause();
    }

    @Override
    public void afterSendKeys(Alert alert, String text) {
        pause();
    }
}
