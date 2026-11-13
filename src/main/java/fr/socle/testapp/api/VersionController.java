package fr.socle.testapp.api;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Version de l'application déployée (affichée en permanence dans l'en-tête de chaque page).
 * Source : META-INF/build-info.properties produit par Maven ; « dev » si l'application n'a pas été construite par Maven.
 */
@RestController
@RequestMapping("/api/version")
public class VersionController {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Paris"));

    private final Map<String, Object> version;

    public VersionController(Optional<BuildProperties> build) {
        this.version = build.map(VersionController::describe)
                .orElseGet(() -> Json.of("version", "dev", "buildTime", "", "commit", "local"));
    }

    private static Map<String, Object> describe(BuildProperties build) {
        String commit = build.get("commit");
        if (commit == null || commit.isBlank() || commit.startsWith("${")) {
            commit = "local";
        } else if (commit.length() > 8) {
            commit = commit.substring(0, 8);
        }
        return Json.of(
                "version", build.getVersion(),
                "buildTime", build.getTime() == null ? "" : DISPLAY.format(build.getTime()),
                "commit", commit);
    }

    @GetMapping
    public Map<String, Object> version() {
        return version;
    }
}
