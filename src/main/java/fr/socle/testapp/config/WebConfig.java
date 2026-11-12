package fr.socle.testapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fait pointer /classic (avec ou sans slash final) vers la page d'accueil du front correspondant.
 * Les fronts React, Vue et Angular seront ajoutés à cette liste.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] FRONTS = {"classic"};

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String front : FRONTS) {
            registry.addViewController("/" + front).setViewName("forward:/" + front + "/index.html");
            registry.addViewController("/" + front + "/").setViewName("forward:/" + front + "/index.html");
        }
    }
}
