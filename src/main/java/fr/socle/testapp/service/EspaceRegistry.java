package fr.socle.testapp.service;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registre des espaces de données. L'espace d'une requête est désigné par le cookie {@value #COOKIE}
 * (posé par la session de test) ; sans cookie valide, la requête utilise l'espace commun « {@value #COMMON} ».
 * Les espaces inactifs depuis plus d'une heure sont supprimés ; leur nombre est plafonné.
 */
@Component
public class EspaceRegistry {

    public static final String COOKIE = "testapp-espace";
    public static final String COMMON = "commun";
    static final long IDLE_MS = 3_600_000L;
    static final long PURGE_EVERY_MS = 60_000L;
    static final int MAX_SPACES = 500;
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final int defaultDelayMs;
    private final int jitterMs;
    private final Map<String, Espace> spaces = new ConcurrentHashMap<>();
    private final Espace common;
    private LongSupplier clock = System::currentTimeMillis;
    private volatile long lastPurgeMs;

    public EspaceRegistry(@Value("${testapp.latency.default-ms:300}") int defaultDelayMs,
                          @Value("${testapp.latency.jitter-ms:0}") int jitterMs) {
        this.defaultDelayMs = defaultDelayMs;
        this.jitterMs = jitterMs;
        this.common = new Espace(COMMON, new Latency(defaultDelayMs, jitterMs), clock.getAsLong());
        this.lastPurgeMs = clock.getAsLong();
    }

    /** Espace désigné par le cookie de la requête (espace commun sans cookie valide). */
    public Espace fromRequest(HttpServletRequest request) {
        String id = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE.equals(cookie.getName())) {
                    id = cookie.getValue();
                }
            }
        }
        return get(id);
    }

    /** Espace d'identifiant donné, créé à la première utilisation (espace commun si l'identifiant est absent ou invalide). */
    public Espace get(String id) {
        long now = clock.getAsLong();
        if (id == null || !VALID_ID.matcher(id).matches() || COMMON.equals(id)) {
            common.touch(now);
            return common;
        }
        purgeIfDue(now);
        Espace space = spaces.computeIfAbsent(id, key -> new Espace(key, new Latency(defaultDelayMs, jitterMs), now));
        space.touch(now);
        return space;
    }

    /** Nombre d'espaces isolés actifs (hors espace commun). */
    public int count() {
        return spaces.size();
    }

    public Espace common() {
        return common;
    }

    private void purgeIfDue(long now) {
        if (now - lastPurgeMs < PURGE_EVERY_MS && spaces.size() < MAX_SPACES) {
            return;
        }
        lastPurgeMs = now;
        spaces.values().removeIf(space -> now - space.lastAccessMs() > IDLE_MS);
        while (spaces.size() >= MAX_SPACES) {
            spaces.values().stream().min(Comparator.comparingLong(Espace::lastAccessMs))
                    .ifPresent(oldest -> spaces.remove(oldest.id()));
        }
    }

    /** Horloge remplaçable pour les tests. */
    void setClock(LongSupplier clock) {
        this.clock = clock;
        this.lastPurgeMs = clock.getAsLong();
    }
}
