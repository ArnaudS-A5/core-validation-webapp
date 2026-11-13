package fr.socle.testapp.config;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.EspaceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Fournit aux contrôleurs l'espace de données de la requête (paramètre de type {@link Espace}). */
public class EspaceArgumentResolver implements HandlerMethodArgumentResolver {

    private final EspaceRegistry registry;

    public EspaceArgumentResolver(EspaceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Espace.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return registry.fromRequest(webRequest.getNativeRequest(HttpServletRequest.class));
    }
}
