package fr.socle.testapp.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import fr.socle.testapp.service.LatencyService;
import fr.socle.testapp.service.ScenarioState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints factices dédiés aux scénarios asynchrones : latences, échecs, authentification,
 * formulaires, listes dépendantes, auto-complétion, tâches longues.
 * Tous acceptent le paramètre optionnel {@code delay} (ms) qui remplace la latence globale.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    public record LoginRequest(String username, String password) {
    }

    private static final Map<String, String> COUNTRY_NAMES = new LinkedHashMap<>();
    private static final Map<String, List<String>> CITIES = new LinkedHashMap<>();

    static {
        COUNTRY_NAMES.put("FR", "France");
        COUNTRY_NAMES.put("DE", "Allemagne");
        COUNTRY_NAMES.put("ES", "Espagne");
        COUNTRY_NAMES.put("IT", "Italie");
        CITIES.put("FR", List.of("Paris", "Lyon", "Marseille", "Toulouse"));
        CITIES.put("DE", List.of("Berlin", "Munich", "Hambourg"));
        CITIES.put("ES", List.of("Madrid", "Barcelone", "Valence"));
        CITIES.put("IT", List.of("Rome", "Milan", "Naples"));
    }

    private static final List<String> SUGGESTIONS = List.of(
            "Abricot", "Ananas", "Banane", "Cerise", "Citron", "Clémentine", "Fraise", "Framboise",
            "Kiwi", "Mangue", "Melon", "Orange", "Pamplemousse", "Pêche", "Poire", "Pomme", "Prune", "Raisin");

    private final LatencyService latency;
    private final ScenarioState state;

    public ApiController(LatencyService latency, ScenarioState state) {
        this.latency = latency;
        this.state = state;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping(@RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        return Json.of("status", "ok", "serverTime", Instant.now().toString());
    }

    @GetMapping("/clock")
    public Map<String, Object> clock(@RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        Instant now = Instant.now();
        return Json.of("time", now.toString(), "epochMs", now.toEpochMilli());
    }

    /** Répond après exactement {@code delay} ms (2000 par défaut). */
    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "2000") int delay) {
        latency.sleep(delay);
        return Json.of("message", "Réponse reçue après " + delay + " ms", "delayMs", delay);
    }

    /** Identifiants valides : test / test. Compte verrouillé : locked (HTTP 423). */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request,
                                                      @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        if ("locked".equals(request.username())) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Json.of("error", "Compte verrouillé"));
        }
        if ("test".equals(request.username()) && "test".equals(request.password())) {
            return ResponseEntity.ok(Json.of("token", UUID.randomUUID().toString(),
                    "displayName", "Utilisateur Test"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Json.of("error", "Identifiants invalides"));
    }

    @GetMapping("/countries")
    public List<Map<String, Object>> countries(@RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        List<Map<String, Object>> result = new ArrayList<>();
        COUNTRY_NAMES.forEach((code, name) -> result.add(Json.of("code", code, "name", name)));
        return result;
    }

    @GetMapping("/countries/{code}/cities")
    public ResponseEntity<List<String>> cities(@PathVariable String code,
                                               @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        List<String> cities = CITIES.get(code.toUpperCase(Locale.ROOT));
        if (cities == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(cities);
    }

    /** Auto-complétion : suggestions contenant la saisie (insensible à la casse). */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        if (!needle.isEmpty()) {
            for (String suggestion : SUGGESTIONS) {
                if (suggestion.toLowerCase(Locale.ROOT).contains(needle)) {
                    results.add(suggestion);
                }
            }
        }
        return Json.of("query", q, "results", results);
    }

    /** Échoue en HTTP 503 pour les {@code failures} premières tentatives d'une même {@code key}. */
    @GetMapping("/flaky")
    public ResponseEntity<Map<String, Object>> flaky(@RequestParam(defaultValue = "default") String key,
                                                      @RequestParam(defaultValue = "2") int failures,
                                                      @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        int attempt = state.nextAttempt(key);
        if (attempt <= failures) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Json.of("error", "Service temporairement indisponible", "attempt", attempt));
        }
        return ResponseEntity.ok(Json.of("message", "Succès à la tentative " + attempt, "attempt", attempt));
    }

    /** Répond avec le code HTTP demandé (200 à 599). */
    @GetMapping("/status/{code}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable int code,
                                                      @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        if (code < 200 || code > 599) {
            return ResponseEntity.badRequest().body(Json.of("error", "Code HTTP hors plage 200-599"));
        }
        return ResponseEntity.status(code).body(Json.of("status", code));
    }

    /** Validation côté serveur : « name » obligatoire, « email » doit contenir « @ ». */
    @PostMapping("/forms/submit")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, Object> payload,
                                                      @RequestParam(required = false) Integer delay) {
        latency.simulate(delay);
        Map<String, String> errors = new LinkedHashMap<>();
        if (text(payload.get("name")).isBlank()) {
            errors.put("name", "Le nom est obligatoire");
        }
        if (!text(payload.get("email")).contains("@")) {
            errors.put("email", "Adresse e-mail invalide");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Json.of("errors", errors));
        }
        return ResponseEntity.ok(Json.of("message", "Formulaire enregistré",
                "id", UUID.randomUUID().toString(), "received", payload));
    }

    /** Démarre une tâche longue ; sa progression se consulte via GET /api/jobs/{id}. */
    @PostMapping("/jobs")
    public Map<String, Object> startJob(@RequestParam(defaultValue = "5000") int duration) {
        int bounded = Math.max(500, Math.min(duration, 60_000));
        return Json.of("jobId", state.startJob(bounded), "durationMs", bounded);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> job(@PathVariable String id) {
        ScenarioState.Job job = state.job(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Json.of("error", "Tâche inconnue"));
        }
        long elapsed = System.currentTimeMillis() - job.startMs();
        int percent = (int) Math.min(100, elapsed * 100 / job.durationMs());
        return ResponseEntity.ok(Json.of("jobId", id, "percent", percent,
                "status", percent >= 100 ? "DONE" : "RUNNING"));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
