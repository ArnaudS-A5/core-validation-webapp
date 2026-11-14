package fr.socle.testapp.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.EspaceRegistry;
import fr.socle.testapp.service.ProfilReseau;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pilotage de l'application par les tests, toujours limité à l'espace de données de l'appelant
 * (cookie {@value EspaceRegistry#COOKIE}, espace commun sans cookie) : latence, profil réseau et remise à zéro.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record SettingsRequest(Integer defaultDelayMs, Integer jitterMs) {
    }

    public record ProfilRequest(String profil, Long graine) {
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

    @GetMapping("/profil")
    public Map<String, Object> profil(Espace espace) {
        return espace.reseau().snapshot();
    }

    /**
     * Applique un profil réseau (aucun, lent, instable, catastrophique) aux appels d'API de l'espace.
     * Même graine = même séquence de latences et d'incidents ; sans graine, une graine aléatoire est choisie.
     */
    @PutMapping("/profil")
    public ResponseEntity<Map<String, Object>> updateProfil(@RequestBody ProfilRequest request, Espace espace) {
        return ProfilReseau.parNom(request.profil())
                .<ResponseEntity<Map<String, Object>>>map(profil -> {
                    espace.reseau().configurer(profil, request.graine());
                    return ResponseEntity.ok(espace.reseau().snapshot());
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(Json.of("error", "Profil inconnu : " + request.profil(),
                        "profils", Arrays.stream(ProfilReseau.values()).map(ProfilReseau::nom).toList())));
    }

    /**
     * Journal des derniers tirages du profil réseau de l'espace (du plus récent au plus ancien), pour le panneau
     * des tirages affiché dans les pages. Lire le journal ne consomme aucun tirage.
     */
    @GetMapping("/tirages")
    public Map<String, Object> tirages(Espace espace) {
        Map<String, Object> reponse = new LinkedHashMap<>(espace.reseau().snapshot());
        reponse.put("tirages", espace.reseau().journal().stream().map(e -> Json.of(
                "numero", e.numero(),
                "chemin", e.chemin(),
                "latenceMs", e.latenceMs(),
                "incident", e.incident().name().toLowerCase(Locale.ROOT),
                "horodatage", e.horodatage())).toList());
        return reponse;
    }

    /** Remet les articles, les compteurs de scénarios et le réseau (sans profil) de l'espace de l'appelant à leur état initial. */
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
