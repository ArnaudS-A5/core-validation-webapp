package fr.socle.testapp.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * État en mémoire des scénarios (compteurs d'échecs, tâches longues). Réinitialisable via /api/admin/reset.
 * Une instance par espace de données ({@link Espace}).
 */
public class ScenarioState {

    public record Job(long startMs, int durationMs) {
    }

    private final Map<String, AtomicInteger> flakyCounters = new ConcurrentHashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    /** Incrémente et retourne le numéro de tentative pour une clé donnée (1 = première tentative). */
    public int nextAttempt(String key) {
        return flakyCounters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    public String startJob(int durationMs) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new Job(System.currentTimeMillis(), durationMs));
        return id;
    }

    public Job job(String id) {
        return jobs.get(id);
    }

    public void reset() {
        flakyCounters.clear();
        jobs.clear();
    }
}
