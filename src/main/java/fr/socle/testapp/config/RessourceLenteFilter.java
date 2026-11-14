package fr.socle.testapp.config;

import java.io.IOException;

import fr.socle.testapp.service.Latency;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ressources statiques lentes : tout fichier d'un dossier {@code lent/} d'un front (ex. {@code /classic/lent/x.js})
 * n'est servi qu'après {@code delai} ms (paramètre de requête, {@value #DELAI_PAR_DEFAUT_MS} ms par défaut,
 * borné à {@link Latency#MAX_DELAY_MS}). Sert le piège « présent ≠ prêt » : page affichée, scripts pas encore là.
 */
@Component
public class RessourceLenteFilter extends OncePerRequestFilter {

    static final int DELAI_PAR_DEFAUT_MS = 3_000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/lent/") || request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Latency.pause(delai(request.getParameter("delai")));
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }

    static int delai(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return DELAI_PAR_DEFAUT_MS;
        }
        try {
            return Integer.parseInt(valeur.trim());
        } catch (NumberFormatException e) {
            return DELAI_PAR_DEFAUT_MS;
        }
    }
}
