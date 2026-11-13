package fr.socle.testapp.smoke;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.Cookie;
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
 * Groupe de sessions navigateur du contrôle de fumée.
 * <p>
 * Jusqu'à {@code smoke.sessions} navigateurs sont ouverts (1 par défaut). Chaque classe de test (une page)
 * emprunte un navigateur libre et le rend à la fin ; il est réutilisé par la classe suivante sans être refermé
 * (sur le Grid, chaque fermeture redémarre le nœud). Chaque navigateur travaille dans son propre espace de données
 * (cookie {@value #ESPACE_COOKIE}) : les sessions parallèles ne se gênent pas. Tout est fermé en fin d'exécution.
 * <ul>
 *   <li>{@code browser} : chrome (défaut), firefox ou edge ;</li>
 *   <li>{@code grid.url} : URL du Selenium Grid ; vide = navigateur local (sans tête par défaut, {@code -Dheadless=false} pour le voir) ;</li>
 *   <li>{@code smoke.sessions} : nombre de navigateurs ouverts en parallèle ;</li>
 *   <li>{@code smoke.ralenti} : pause en ms après chaque geste, pour suivre le déroulé à l'écran (0 par défaut).</li>
 * </ul>
 */
public class BrowserExtension implements BeforeAllCallback {

    static final String ESPACE_COOKIE = "testapp-espace";
    static final int SESSIONS = Math.max(1, Integer.getInteger("smoke.sessions", 1));

    private static final Object LOCK = new Object();
    private static final BlockingQueue<WebDriver> AVAILABLE = new LinkedBlockingQueue<>();
    private static final List<WebDriver> ALL = new CopyOnWriteArrayList<>();
    // Clé par identité : un navigateur décoré (mode observation) ne délègue pas forcément equals/hashCode
    private static final Map<WebDriver, String> ESPACES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static int reserved;
    private static boolean closerRegistered;

    static boolean remote() {
        return !gridUrl().isBlank();
    }

    private static String gridUrl() {
        return System.getProperty("grid.url", "").trim();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        synchronized (LOCK) {
            if (closerRegistered) {
                return;
            }
            closerRegistered = true;
        }
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .put("socle-testapp-browsers", (ExtensionContext.Store.CloseableResource) () -> {
                    for (WebDriver driver : ALL) {
                        try {
                            driver.quit();
                        } catch (RuntimeException ignored) {
                            // session déjà perdue
                        }
                    }
                });
    }

    /** Emprunte un navigateur libre ; en ouvre un nouveau tant que le nombre de sessions n'est pas atteint. */
    static WebDriver borrow() throws InterruptedException {
        WebDriver driver = AVAILABLE.poll();
        if (driver != null) {
            return driver;
        }
        boolean mayCreate;
        synchronized (LOCK) {
            mayCreate = reserved < SESSIONS;
            if (mayCreate) {
                reserved++;
            }
        }
        if (!mayCreate) {
            return AVAILABLE.take();
        }
        try {
            driver = create();
        } catch (RuntimeException e) {
            synchronized (LOCK) {
                reserved--;
            }
            throw e;
        }
        ALL.add(driver);
        enterOwnSpace(driver);
        return driver;
    }

    /** Rend le navigateur au groupe pour la classe de test suivante. */
    static void release(WebDriver driver) {
        if (driver != null) {
            AVAILABLE.offer(driver);
        }
    }

    /** Espace de données du navigateur (identifiant du cookie). */
    static String espace(WebDriver driver) {
        return ESPACES.get(driver);
    }

    /** Pose le cookie d'espace : toutes les requêtes du navigateur (Ajax, SSE, téléchargements, iframes) le portent. */
    private static void enterOwnSpace(WebDriver driver) {
        String id = "fumee-" + System.getProperty("browser", "chrome").trim().toLowerCase() + "-" + UUID.randomUUID();
        driver.get(SmokeTestBase.APP + "/api/version");
        driver.manage().addCookie(new Cookie(ESPACE_COOKIE, id, "/"));
        ESPACES.put(driver, id);
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
