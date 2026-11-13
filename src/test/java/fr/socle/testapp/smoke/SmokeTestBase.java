package fr.socle.testapp.smoke;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Socle commun du contrôle de fumée : une page par classe, des tests ordonnés qui s'enchaînent,
 * l'application remise à zéro ({@code POST /api/admin/reset}) au début de chaque classe.
 */
@ExtendWith({BrowserExtension.class, AllureSupport.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class SmokeTestBase {

    protected static final String APP = withoutTrailingSlashes(System.getProperty("app.url", "http://localhost:8080"));
    protected static final String BASE = APP + "/classic/";

    protected WebDriver d;

    @BeforeAll
    void prepareBrowser() throws Exception {
        d = BrowserExtension.driver();
        resetApp();
        // Repart d'une seule fenêtre, hors de toute iframe et sans alerte ouverte
        try {
            d.switchTo().alert().dismiss();
        } catch (Exception ignored) {
            // pas d'alerte
        }
        List<String> handles = new ArrayList<>(d.getWindowHandles());
        for (int i = 1; i < handles.size(); i++) {
            try {
                d.switchTo().window(handles.get(i));
                System.out.println("[fumée] fenêtre restée ouverte avant " + getClass().getSimpleName() + " : " + d.getCurrentUrl());
                d.close();
            } catch (Exception e) {
                System.out.println("[fumée] fermeture impossible (" + e.getClass().getSimpleName() + "), fenêtre ignorée");
            }
        }
        d.switchTo().window(handles.get(0));
        d.switchTo().defaultContent();
    }

    /**
     * Amène l'élément au centre de la zone visible. Nécessaire avant les actions souris
     * et avant d'entrer dans une iframe : Firefox ne fait pas défiler la page seul dans ces cas.
     */
    protected WebElement inView(WebElement element) {
        js("arguments[0].scrollIntoView({ block: 'center', inline: 'center' })", element);
        return element;
    }

    protected WebElement inView(String css) {
        return inView($(css));
    }

    private static String withoutTrailingSlashes(String url) {
        String result = url.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    protected static void resetApp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(APP + "/api/admin/reset")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Remise à zéro impossible : HTTP " + response.statusCode());
        }
    }

    // ---------- Navigation ----------

    protected void open(String page) {
        d.get(BASE + page);
        waitUntil(drv -> "complete".equals(js("return document.readyState")), 10);
    }

    // ---------- Recherche et lecture ----------

    protected WebElement $(String css) {
        return d.findElement(By.cssSelector(css));
    }

    protected List<WebElement> $$(String css) {
        return d.findElements(By.cssSelector(css));
    }

    protected boolean exists(String css) {
        return !$$(css).isEmpty();
    }

    protected String text(String css) {
        return $(css).getText().trim();
    }

    /** Texte brut du DOM (textContent), indépendant de la visibilité. */
    protected String textContent(String css) {
        return String.valueOf(js("return document.querySelector(arguments[0]).textContent", css)).trim();
    }

    protected boolean visible(String css) {
        try {
            List<WebElement> elements = $$(css);
            return !elements.isEmpty() && elements.get(0).isDisplayed();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    protected String attr(String css, String name) {
        return $(css).getDomAttribute(name);
    }

    // ---------- Attentes ----------

    protected boolean waitUntil(Function<WebDriver, Boolean> condition, int seconds) {
        try {
            new WebDriverWait(d, Duration.ofSeconds(seconds), Duration.ofMillis(100))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(condition);
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    protected boolean waitText(String css, String regex) {
        return waitText(css, regex, 6);
    }

    protected boolean waitText(String css, String regex, int seconds) {
        Pattern pattern = Pattern.compile(regex);
        return waitUntil(drv -> exists(css) && pattern.matcher(textContent(css)).find(), seconds);
    }

    protected boolean waitVisible(String css, int seconds) {
        return waitUntil(drv -> visible(css), seconds);
    }

    protected boolean waitAbsent(String css, int seconds) {
        return waitUntil(drv -> !exists(css), seconds);
    }

    protected static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- Actions ----------

    /**
     * Clic qui patiente tant que l'élément est recouvert (calque, animation) ; si un élément fixe
     * (bandeau, en-tête) le recouvre, l'élément est ramené au centre de l'écran avant de réessayer.
     */
    protected void click(String css) {
        click(css, 5);
    }

    protected void click(String css, int seconds) {
        boolean done = waitUntil(drv -> {
            try {
                $(css).click();
                return true;
            } catch (ElementClickInterceptedException e) {
                inView(css);
                return false;
            }
        }, seconds);
        if (!done) {
            $(css).click(); // relance pour obtenir l'erreur réelle
        }
    }

    /**
     * Geste humain sur un curseur : saisir la poignée et la déplacer jusqu'à ce que la valeur affichée
     * atteigne la cible, en avançant à grands pas puis pas à pas à l'approche, puis relâcher.
     *
     * @return la valeur affichée au relâchement
     */
    protected int dragSliderUntilDisplayed(String handleCss, String displayedValueCss, int target) {
        new Actions(d).clickAndHold(inView(handleCss)).perform();
        try {
            for (int i = 0; i < 300; i++) {
                int current = Integer.parseInt(textContent(displayedValueCss));
                if (current == target) {
                    break;
                }
                int gap = target - current;
                int step = Math.abs(gap) > 10 ? 15 : Math.abs(gap) > 3 ? 4 : 1;
                new Actions(d).moveByOffset(Integer.signum(gap) * step, 0).perform();
            }
        } finally {
            new Actions(d).release().perform();
        }
        return Integer.parseInt(textContent(displayedValueCss));
    }

    /** Navigateur local sous macOS (les raccourcis y utilisent Cmd et la disposition du clavier système). */
    protected static boolean localMac() {
        return !BrowserExtension.remote() && System.getProperty("os.name").toLowerCase().contains("mac");
    }

    /** Touche des raccourcis clavier (Ctrl, ou Cmd sur un navigateur local macOS). */
    protected static Keys shortcutKey() {
        return localMac() ? Keys.COMMAND : Keys.CONTROL;
    }

    /**
     * Raccourci clavier (Ctrl+touche, ou Cmd+touche en local sous macOS) : la touche de modification est
     * maintenue pendant l'appui. Attention : Actions.sendKeys(Keys.chord(...)) appuie et relâche chaque
     * touche séparément, ce qui ne produit pas de raccourci.
     */
    protected void shortcut(String key) {
        new Actions(d).keyDown(shortcutKey()).sendKeys(key).keyUp(shortcutKey()).perform();
    }

    /** Appuie sur une touche dans l'élément qui a le focus. */
    protected void press(CharSequence... keys) {
        new Actions(d).sendKeys(keys).perform();
    }

    /** Identifiant de l'élément qui a le focus. */
    protected String focusedId() {
        return String.valueOf(js("return document.activeElement && document.activeElement.id"));
    }

    /** Rectangle de l'élément (x, y, largeur, hauteur) dans la zone visible. */
    protected String rect(WebElement element) {
        return String.valueOf(js("var r = arguments[0].getBoundingClientRect(); return [r.x, r.y, r.width, r.height].join(',');", element));
    }

    /** Vrai si l'élément est entièrement visible dans la zone défilante qui le contient. */
    protected boolean visibleInside(String elementCss, String containerCss) {
        return Boolean.TRUE.equals(js("var e = document.querySelector(arguments[0]), c = document.querySelector(arguments[1]);"
                + "if (!e) return false; var r = e.getBoundingClientRect(), z = c.getBoundingClientRect();"
                + "return r.top >= z.top && r.bottom <= z.bottom && r.left >= z.left && r.right <= z.right;", elementCss, containerCss));
    }

    protected void type(String css, String value) {
        $(css).sendKeys(value);
    }

    protected void clearAndType(String css, String value) {
        WebElement element = $(css);
        element.clear();
        element.sendKeys(value);
    }

    protected void select(String css, String... values) {
        Select select = new Select($(css));
        for (String value : values) {
            select.selectByValue(value);
        }
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) d).executeScript(script, args);
    }

    protected Object asyncJs(String script, Object... args) {
        return ((JavascriptExecutor) d).executeAsyncScript(script, args);
    }
}
