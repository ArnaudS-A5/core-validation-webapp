package fr.socle.testapp.smoke;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Enrichit le rapport Allure du contrôle de fumée :
 * navigateur en paramètre (un même test par navigateur), regroupement par navigateur,
 * capture d'écran et adresse de la page jointes à chaque test en échec,
 * fiche d'environnement (application, Grid, navigateur).
 */
public class AllureSupport implements BeforeAllCallback, BeforeEachCallback, AfterTestExecutionCallback {

    private static final String BROWSER = System.getProperty("browser", "chrome").trim().toLowerCase();

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        String dir = System.getProperty("allure.results.directory");
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path results = Path.of(dir);
        Files.createDirectories(results);
        Properties env = new Properties();
        env.setProperty("Navigateur", BROWSER);
        env.setProperty("Application", System.getProperty("app.url", ""));
        env.setProperty("Grid", System.getProperty("grid.url", "").isBlank() ? "navigateur local" : System.getProperty("grid.url"));
        try (Writer out = Files.newBufferedWriter(results.resolve("environment.properties"), StandardCharsets.UTF_8)) {
            env.store(out, "Contrôle de fumée socle-testapp");
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Allure.parameter("navigateur", BROWSER);
        Allure.label("parentSuite", "Fumée " + BROWSER);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        WebDriver driver = BrowserExtension.driver();
        if (driver == null) {
            return;
        }
        try {
            Allure.addAttachment("Page au moment de l'échec", "text/uri-list", driver.getCurrentUrl());
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Capture d'écran", "image/png", new ByteArrayInputStream(png), ".png");
        } catch (Exception e) {
            // alerte ouverte, fenêtre fermée… : la capture est impossible, le test reste en échec
            Allure.addAttachment("Capture impossible", e.getClass().getSimpleName() + " : " + e.getMessage());
        }
    }
}
