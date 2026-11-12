package fr.socle.testapp.smoke;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.events.EventFiringDecorator;

/**
 * Une seule session navigateur pour tout le contrôle de fumée, fermée à la fin de l'exécution.
 * <ul>
 *   <li>{@code browser} : chrome (défaut), firefox ou edge ;</li>
 *   <li>{@code grid.url} : URL du Selenium Grid ; vide = navigateur local (sans tête par défaut, {@code -Dheadless=false} pour le voir) ;</li>
 *   <li>{@code smoke.ralenti} : pause en ms après chaque geste, pour suivre le déroulé à l'écran (0 par défaut).</li>
 * </ul>
 */
public class BrowserExtension implements BeforeAllCallback {

    private static WebDriver driver;

    static WebDriver driver() {
        return driver;
    }

    static boolean remote() {
        return !gridUrl().isBlank();
    }

    private static String gridUrl() {
        return System.getProperty("grid.url", "").trim();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (driver != null) {
            return;
        }
        driver = create();
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .put("socle-testapp-driver", (ExtensionContext.Store.CloseableResource) () -> {
                    driver.quit();
                    driver = null;
                });
    }

    private static WebDriver create() {
        String browser = System.getProperty("browser", "chrome").trim().toLowerCase();
        boolean headless = !remote() && Boolean.parseBoolean(System.getProperty("headless", "true"));

        AbstractDriverOptions<?> options = switch (browser) {
            case "firefox" -> {
                FirefoxOptions o = new FirefoxOptions();
                if (headless) {
                    o.addArguments("-headless");
                }
                yield o;
            }
            case "edge" -> {
                EdgeOptions o = new EdgeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                yield o;
            }
            default -> {
                ChromeOptions o = new ChromeOptions();
                if (headless) {
                    o.addArguments("--headless=new");
                }
                yield o;
            }
        };
        // Les alertes restent ouvertes : ce sont les tests qui les traitent
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);

        WebDriver created;
        if (remote()) {
            // Téléchargements gérés par le Grid : les fichiers téléchargés sur le nœud sont consultables
            options.setEnableDownloads(true);
            RemoteWebDriver remoteDriver;
            try {
                remoteDriver = new RemoteWebDriver(URI.create(gridUrl()).toURL(), options);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("grid.url invalide : " + gridUrl(), e);
            }
            // Envoi de fichiers : le fichier local est transféré au nœud
            remoteDriver.setFileDetector(new LocalFileDetector());
            created = remoteDriver;
        } else {
            created = switch (browser) {
                case "firefox" -> new FirefoxDriver((FirefoxOptions) options);
                case "edge" -> new EdgeDriver((EdgeOptions) options);
                default -> new ChromeDriver((ChromeOptions) options);
            };
        }
        created.manage().window().setSize(new Dimension(1440, 900));
        created.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

        int ralenti = Integer.getInteger("smoke.ralenti", 0);
        if (ralenti > 0) {
            created = new EventFiringDecorator<>(new ObservationListener(ralenti)).decorate(created);
        }
        return created;
    }
}
