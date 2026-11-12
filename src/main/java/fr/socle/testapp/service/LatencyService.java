package fr.socle.testapp.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Simule la latence réseau/serveur afin de mettre à l'épreuve les mécanismes de synchronisation.
 * <p>
 * Chaque endpoint accepte un paramètre optionnel {@code delay} (ms) qui prime sur la latence globale
 * (délai par défaut + gigue aléatoire), modifiable à chaud via {@code /api/admin/settings}.
 */
@Service
public class LatencyService {

    public static final int MAX_DELAY_MS = 30_000;

    private volatile int defaultDelayMs;
    private volatile int jitterMs;

    public LatencyService(@Value("${testapp.latency.default-ms:300}") int defaultDelayMs,
                          @Value("${testapp.latency.jitter-ms:0}") int jitterMs) {
        this.defaultDelayMs = defaultDelayMs;
        this.jitterMs = jitterMs;
    }

    /** Attend le délai demandé, ou à défaut la latence globale (délai par défaut + gigue). */
    public void simulate(Integer requestedDelayMs) {
        if (requestedDelayMs != null) {
            sleep(requestedDelayMs);
            return;
        }
        int jitter = jitterMs > 0 ? ThreadLocalRandom.current().nextInt(jitterMs + 1) : 0;
        sleep(defaultDelayMs + jitter);
    }

    /** Attend exactement le délai demandé (borné entre 0 et {@link #MAX_DELAY_MS}). */
    public void sleep(int delayMs) {
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
