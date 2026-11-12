package fr.socle.testapp.api;

import java.util.Map;

import fr.socle.testapp.service.ItemStore;
import fr.socle.testapp.service.LatencyService;
import fr.socle.testapp.service.ScenarioState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pilotage de l'application par les tests : latence globale et remise à zéro des données. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record SettingsRequest(Integer defaultDelayMs, Integer jitterMs) {
    }

    private final LatencyService latency;
    private final ItemStore itemStore;
    private final ScenarioState state;

    public AdminController(LatencyService latency, ItemStore itemStore, ScenarioState state) {
        this.latency = latency;
        this.itemStore = itemStore;
        this.state = state;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return latency.snapshot();
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody SettingsRequest request) {
        latency.configure(request.defaultDelayMs(), request.jitterMs());
        return latency.snapshot();
    }

    /** Remet les données (articles) et les compteurs de scénarios à leur état initial. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        itemStore.reset();
        state.reset();
        return Json.of("status", "reset");
    }
}
