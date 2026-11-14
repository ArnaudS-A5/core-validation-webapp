package fr.socle.testapp.smoke;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.socle.testapp.service.ProfilReseau;
import fr.socle.testapp.service.Reseau;

/**
 * Socle des tests de latence (classes {@code *LT}) : exécutés à part du contrôle de fumée, sur demande
 * (attentes longues, réseau dégradé). Même fonctionnement que la fumée : un navigateur emprunté au groupe,
 * dans son propre espace de données, remis à zéro (profil réseau compris) au début de chaque classe.
 */
abstract class LatenceTestBase extends SmokeTestBase {

    /** Ligne du panneau des tirages, telle que la page l'affiche. */
    record LigneTirage(long numero, String chemin, int latenceMs, String incident) {
    }

    /** Lignes remplies du panneau des tirages, de la plus récente à la plus ancienne. */
    protected List<LigneTirage> lignesDuPanneau() {
        List<LigneTirage> lignes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String numero = attr("#tirage-ligne-" + i, "data-numero");
            if (numero == null) {
                break;
            }
            lignes.add(new LigneTirage(Long.parseLong(numero), text("#tirage-chemin-" + i),
                    Integer.parseInt(attr("#tirage-ligne-" + i, "data-latence")), attr("#tirage-ligne-" + i, "data-incident")));
        }
        return lignes;
    }

    /** Ligne la plus récente du panneau pour ce chemin d'API, s'il en affiche une. */
    protected Optional<LigneTirage> ligneDuPanneau(String chemin) {
        return lignesDuPanneau().stream().filter(l -> l.chemin().equals(chemin)).findFirst();
    }

    /** Le calcul que fera le serveur pour ce profil, cette graine et ce numéro de tirage. */
    protected static Reseau.Tirage tirageAttendu(ProfilReseau profil, long graine, long numero) {
        Reseau reseau = new Reseau();
        reseau.configurer(profil, graine);
        return reseau.calculer(numero);
    }

    /** Latence de base de l'espace (ms), qui s'ajoute à la latence tirée quand l'appel atteint l'API. */
    protected int latenceDeBase() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(APP + "/api/admin/settings"))
                        .header("Cookie", BrowserExtension.ESPACE_COOKIE + "=" + BrowserExtension.espace(d)).build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("\"defaultDelayMs\"\\s*:\\s*(\\d+)").matcher(response.body());
        if (response.statusCode() != 200 || !m.find()) {
            throw new IllegalStateException("Latence de base illisible : HTTP " + response.statusCode() + " " + response.body());
        }
        return Integer.parseInt(m.group(1));
    }
}
