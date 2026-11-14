package fr.socle.testapp.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.EspaceRegistry;
import fr.socle.testapp.service.Latency;
import fr.socle.testapp.service.Reseau;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applique le profil réseau de l'espace aux appels d'API : latence tirée au hasard, puis éventuellement
 * une erreur HTTP 503 ou une connexion coupée en cours de réponse. Sans profil, la requête passe telle quelle.
 * <p>
 * Le paramètre de requête {@code tirage} impose le numéro du tirage (séquence rejouable à l'identique).
 * L'en-tête {@code X-Reseau-Tirage} indique le profil, le numéro et la latence appliqués.
 */
public class ReseauInterceptor implements HandlerInterceptor {

    static final String ENTETE = "X-Reseau-Tirage";

    /** Taille annoncée d'une réponse coupée : le client attend des octets qui n'arriveront jamais. */
    static final int TAILLE_ANNONCEE_COUPURE = 4096;

    private final EspaceRegistry registry;

    public ReseauInterceptor(EspaceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Espace espace = registry.fromRequest(request);
        Reseau reseau = espace.reseau();
        if (!reseau.actif()) {
            return true;
        }
        Reseau.Tirage tirage = reseau.tirer(numeroImpose(request), request.getRequestURI());
        response.setHeader(ENTETE, reseau.profil().nom() + ";numero=" + tirage.numero() + ";latence=" + tirage.latenceMs());
        Latency.pause(tirage.latenceMs());
        return switch (tirage.incident()) {
            case AUCUN -> true;
            case ERREUR -> {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"Incident réseau simulé (profil " + reseau.profil().nom()
                        + ", tirage " + tirage.numero() + ")\"}");
                yield false;
            }
            case COUPURE -> {
                couper(response);
                yield false;
            }
        };
    }

    /**
     * Connexion coupée en cours de réponse : entêtes et début du corps envoyés, puis la connexion est fermée
     * avant la fin annoncée (Content-Length). Le navigateur obtient une erreur réseau (statut 0), pas un code HTTP.
     */
    private static void couper(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Connection", "close");
        response.setContentLength(TAILLE_ANNONCEE_COUPURE);
        response.getOutputStream().write("{\"coupure\":".getBytes(StandardCharsets.UTF_8));
        // Le serveur ferme la connexion (Connection: close) sans avoir envoyé la taille annoncée
        response.flushBuffer();
    }

    private static Long numeroImpose(HttpServletRequest request) {
        String valeur = request.getParameter("tirage");
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valeur.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
