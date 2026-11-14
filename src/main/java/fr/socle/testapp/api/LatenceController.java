package fr.socle.testapp.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import fr.socle.testapp.service.Latency;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Réponses au compte-gouttes : les entêtes partent tout de suite, le contenu arrive par morceaux
 * pendant plusieurs secondes. Durée totale bornée à {@link Latency#MAX_DELAY_MS}.
 */
@RestController
@RequestMapping("/api/latence")
public class LatenceController {

    static final int MAX_ELEMENTS = 50;
    static final int MAX_MORCEAUX = 100;
    static final int MAX_TAILLE = 4 * 1024 * 1024;

    /**
     * Liste de {@code nombre} éléments, un toutes les {@code intervalle} ms.
     * Format {@code ndjson} (un objet JSON par ligne, exploitable au fil de l'eau) ou {@code json}
     * (un tableau, exploitable seulement une fois complet).
     */
    @GetMapping("/goutte")
    public ResponseEntity<StreamingResponseBody> goutte(@RequestParam(defaultValue = "10") int nombre,
                                                        @RequestParam(defaultValue = "500") int intervalle,
                                                        @RequestParam(defaultValue = "ndjson") String format) {
        int elements = Math.max(1, Math.min(nombre, MAX_ELEMENTS));
        int pause = Math.max(0, Math.min(intervalle, Latency.MAX_DELAY_MS / elements));
        boolean ndjson = !"json".equalsIgnoreCase(format);
        StreamingResponseBody corps = out -> {
            if (!ndjson) {
                envoyer(out, "[");
            }
            for (int rang = 1; rang <= elements; rang++) {
                Latency.pause(pause);
                String element = "{\"rang\":" + rang + ",\"texte\":\"Élément " + rang + "\"}";
                if (ndjson) {
                    envoyer(out, element + "\n");
                } else {
                    envoyer(out, (rang > 1 ? "," : "") + element);
                }
            }
            if (!ndjson) {
                envoyer(out, "]");
            }
        };
        return ResponseEntity.ok()
                .contentType(ndjson ? MediaType.parseMediaType("application/x-ndjson;charset=UTF-8") : MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(corps);
    }

    /**
     * Fichier texte de {@code taille} octets téléchargé au compte-gouttes pendant {@code duree} ms
     * (taille annoncée d'emblée dans Content-Length, contenu envoyé en {@code morceaux} morceaux réguliers).
     */
    @GetMapping("/fichier")
    public ResponseEntity<StreamingResponseBody> fichier(@RequestParam(defaultValue = "10000") int duree,
                                                         @RequestParam(defaultValue = "524288") int taille,
                                                         @RequestParam(defaultValue = "20") int morceaux) {
        int octets = Math.max(1, Math.min(taille, MAX_TAILLE));
        int parts = Math.max(1, Math.min(morceaux, Math.min(MAX_MORCEAUX, octets)));
        int pause = Math.max(0, Math.min(duree, Latency.MAX_DELAY_MS)) / parts;
        byte[] contenu = contenuFichier(octets);
        StreamingResponseBody corps = out -> {
            for (int i = 0; i < parts; i++) {
                Latency.pause(pause);
                int debut = (int) ((long) octets * i / parts);
                int fin = (int) ((long) octets * (i + 1) / parts);
                out.write(contenu, debut, fin - debut);
                out.flush();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(octets)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fichier-goutte.txt\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(corps);
    }

    /** Contenu reproductible : lignes numérotées de 64 octets (ASCII), tronqué à la taille demandée. */
    static byte[] contenuFichier(int octets) {
        byte[] contenu = new byte[octets];
        int position = 0;
        for (int ligne = 1; position < octets; ligne++) {
            String texte = String.format("%-63s", "ligne " + String.format("%06d", ligne) + " - fichier au compte-gouttes")
                    .substring(0, 63) + "\n";
            byte[] octetsLigne = texte.getBytes(StandardCharsets.US_ASCII);
            int n = Math.min(octetsLigne.length, octets - position);
            System.arraycopy(octetsLigne, 0, contenu, position, n);
            position += n;
        }
        return contenu;
    }

    private static void envoyer(OutputStream out, String texte) throws IOException {
        out.write(texte.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
