package fr.socle.testapp.api;

import java.util.Map;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.EspaceRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pilotage de l'application par les tests, toujours limité à l'espace de données de l'appelant
 * (cookie {@value EspaceRegistry#COOKIE}, espace commun sans cookie) : latence et remise à zéro.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record SettingsRequest(Integer defaultDelayMs, Integer jitterMs) {
    }

    private final EspaceRegistry registry;

    public AdminController(EspaceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings(Espace espace) {
        return espace.latency().snapshot();
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody SettingsRequest request, Espace espace) {
        espace.latency().configure(request.defaultDelayMs(), request.jitterMs());
        return espace.latency().snapshot();
    }

    /** Remet les articles et les compteurs de scénarios de l'espace de l'appelant à leur état initial. */
    @PostMapping("/reset")
    public Map<String, Object> reset(Espace espace) {
        espace.reset();
        return Json.of("status", "reset", "espace", espace.id());
    }

    /** Espace de l'appelant et nombre d'espaces isolés actifs. */
    @GetMapping("/espace")
    public Map<String, Object> espace(Espace espace) {
        return Json.of("espace", espace.id(), "espacesIsoles", registry.count());
    }
}
