package fr.socle.testapp.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Petit utilitaire pour construire des réponses JSON ordonnées : {@code Json.of("cle", valeur, ...)}. */
final class Json {

    private Json() {
    }

    static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
