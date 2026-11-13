package fr.socle.testapp.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Latence simulée d'un espace : l'API attend réellement côté serveur avant de répondre, afin de
 * mettre à l'épreuve les mécanismes de synchronisation.
 * <p>
 * Chaque endpoint accepte un paramètre optionnel {@code delay} (ms) qui prime sur la latence de l'espace
 * (délai par défaut + gigue aléatoire), modifiable à chaud via {@code /api/admin/settings}.
 */
public class Latency {

    public static final int MAX_DELAY_MS = 30_000;

    private volatile int defaultDelayMs;
    private volatile int jitterMs;

    public Latency(int defaultDelayMs, int jitterMs) {
        this.defaultDelayMs = defaultDelayMs;
        this.jitterMs = jitterMs;
    }

    /** Attend le délai demandé, ou à défaut la latence de l'espace (délai par défaut + gigue). */
    public void simulate(Integer requestedDelayMs) {
        if (requestedDelayMs != null) {
            pause(requestedDelayMs);
            return;
        }
        int jitter = jitterMs > 0 ? ThreadLocalRandom.current().nextInt(jitterMs + 1) : 0;
        pause(defaultDelayMs + jitter);
    }

    /** Attend exactement le délai demandé (borné entre 0 et {@link #MAX_DELAY_MS}). */
    public static void pause(int delayMs) {
        int bounded = Math.max(0, Math.min(delayMs, MAX_DELAY_MS));
        if (bounded == 0) {
            return;
        }
        try {
            Thread.sleep(bounded);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void configure(Integer newDefaultDelayMs, Integer newJitterMs) {
        if (newDefaultDelayMs != null) {
            this.defaultDelayMs = Math.max(0, Math.min(newDefaultDelayMs, MAX_DELAY_MS));
        }
        if (newJitterMs != null) {
            this.jitterMs = Math.max(0, Math.min(newJitterMs, MAX_DELAY_MS));
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("defaultDelayMs", defaultDelayMs);
        map.put("jitterMs", jitterMs);
        return map;
    }
}
