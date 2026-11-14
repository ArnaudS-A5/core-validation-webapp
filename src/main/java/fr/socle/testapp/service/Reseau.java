package fr.socle.testapp.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Réseau simulé d'un espace : profil actif et graine.
 * <p>
 * Chaque requête reçoit un tirage (latence, incident) entièrement déterminé par la graine et le numéro du
 * tirage : avec la même graine, la même séquence se rejoue à l'identique. Le numéro est celui que fournit la
 * requête (paramètre {@code tirage}), sinon le compteur de l'espace, remis à zéro à chaque application du profil.
 * Les {@value #TAILLE_JOURNAL} derniers tirages sont conservés dans un journal (chemin appelé, latence, incident),
 * vidé lui aussi à chaque application du profil.
 */
public class Reseau {

    public enum Incident {
        AUCUN, ERREUR, COUPURE
    }

    public record Tirage(long numero, int latenceMs, Incident incident) {
    }

    /** Tirage appliqué à une requête : numéro, chemin appelé, latence, incident et heure (ms depuis l'époque). */
    public record Entree(long numero, String chemin, int latenceMs, Incident incident, long horodatage) {
    }

    public static final int TAILLE_JOURNAL = 50;

    private static final long MELANGE = 0x9E3779B97F4A7C15L;

    private volatile ProfilReseau profil = ProfilReseau.AUCUN;
    private volatile long graine;
    private final AtomicLong compteur = new AtomicLong();
    private final Deque<Entree> journal = new ArrayDeque<>();

    /** Applique un profil ; sans graine, une graine aléatoire est choisie (et restituée par {@link #snapshot()}). */
    public synchronized void configurer(ProfilReseau nouveauProfil, Long nouvelleGraine) {
        this.profil = nouveauProfil;
        this.graine = nouvelleGraine != null ? nouvelleGraine : ThreadLocalRandom.current().nextLong(1, 1_000_000);
        compteur.set(0);
        synchronized (journal) {
            journal.clear();
        }
    }

    public boolean actif() {
        return profil != ProfilReseau.AUCUN;
    }

    public ProfilReseau profil() {
        return profil;
    }

    /** Tirage de la requête : numéro imposé, ou à défaut le suivant de la séquence de l'espace ; noté au journal. */
    public Tirage tirer(Long numeroImpose, String chemin) {
        Tirage tirage = calculer(numeroImpose != null ? numeroImpose : compteur.getAndIncrement());
        synchronized (journal) {
            journal.addFirst(new Entree(tirage.numero(), chemin, tirage.latenceMs(), tirage.incident(), System.currentTimeMillis()));
            if (journal.size() > TAILLE_JOURNAL) {
                journal.removeLast();
            }
        }
        return tirage;
    }

    /** Tirage portant ce numéro avec le profil et la graine actuels, sans le consommer ni le noter au journal. */
    public Tirage calculer(long numero) {
        ProfilReseau p = profil;
        SplittableRandom hasard = new SplittableRandom(graine ^ (numero * MELANGE));
        int latence = p.latenceMinMs() + hasard.nextInt(p.latenceMaxMs() - p.latenceMinMs() + 1);
        double u = hasard.nextDouble();
        Incident incident = u < p.tauxCoupure() ? Incident.COUPURE
                : u < p.tauxCoupure() + p.tauxErreur() ? Incident.ERREUR : Incident.AUCUN;
        return new Tirage(numero, Math.min(latence, Latency.MAX_DELAY_MS), incident);
    }

    /** Derniers tirages, du plus récent au plus ancien. */
    public List<Entree> journal() {
        synchronized (journal) {
            return new ArrayList<>(journal);
        }
    }

    public Map<String, Object> snapshot() {
        ProfilReseau p = profil;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profil", p.nom());
        map.put("graine", graine);
        map.put("latenceMinMs", p.latenceMinMs());
        map.put("latenceMaxMs", p.latenceMaxMs());
        map.put("tauxErreur", p.tauxErreur());
        map.put("tauxCoupure", p.tauxCoupure());
        map.put("tiragesEffectues", compteur.get());
        return map;
    }
}
