package fr.socle.testapp.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Profils réseau simulés : latence tirée au hasard dans une plage, et incidents (erreur HTTP 503,
 * connexion coupée en cours de réponse) selon une probabilité.
 */
public enum ProfilReseau {

    AUCUN(0, 0, 0.0, 0.0),
    LENT(2_000, 5_000, 0.0, 0.0),
    INSTABLE(200, 8_000, 0.10, 0.0),
    CATASTROPHIQUE(1_000, 15_000, 0.20, 0.05);

    private final int latenceMinMs;
    private final int latenceMaxMs;
    private final double tauxErreur;
    private final double tauxCoupure;

    ProfilReseau(int latenceMinMs, int latenceMaxMs, double tauxErreur, double tauxCoupure) {
        this.latenceMinMs = latenceMinMs;
        this.latenceMaxMs = latenceMaxMs;
        this.tauxErreur = tauxErreur;
        this.tauxCoupure = tauxCoupure;
    }

    public int latenceMinMs() {
        return latenceMinMs;
    }

    public int latenceMaxMs() {
        return latenceMaxMs;
    }

    public double tauxErreur() {
        return tauxErreur;
    }

    public double tauxCoupure() {
        return tauxCoupure;
    }

    /** Nom employé par l'API et les pages (minuscules : aucun, lent, instable, catastrophique). */
    public String nom() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ProfilReseau> parNom(String nom) {
        if (nom == null) {
            return Optional.empty();
        }
        String recherche = nom.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.name().equals(recherche)).findFirst();
    }
}
