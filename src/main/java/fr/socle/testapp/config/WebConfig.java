package fr.socle.testapp.config;

import java.util.List;

import fr.socle.testapp.service.EspaceRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fait pointer /classic (avec ou sans slash final) vers la page d'accueil du front correspondant,
 * fournit aux contrôleurs l'espace de données de chaque requête et applique le profil réseau de l'espace
 * aux appels d'API (hors administration et version, qui restent toujours fiables).
 * Les fronts React, Vue et Angular seront ajoutés à la liste FRONTS.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] FRONTS = {"classic"};

    private final EspaceRegistry espaces;

    public WebConfig(EspaceRegistry espaces) {
        this.espaces = espaces;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String front : FRONTS) {
            registry.addViewController("/" + front).setViewName("forward:/" + front + "/index.html");
            registry.addViewController("/" + front + "/").setViewName("forward:/" + front + "/index.html");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ReseauInterceptor(espaces))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**", "/api/version");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new EspaceArgumentResolver(espaces));
    }
}
