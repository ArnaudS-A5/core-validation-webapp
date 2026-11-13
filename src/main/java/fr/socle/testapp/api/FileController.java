package fr.socle.testapp.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.Latency;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Téléchargements (txt, csv, json, zip, bin) et envoi de fichiers. */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final int MAX_BIN_SIZE = 50 * 1024 * 1024;

    /**
     * Télécharge un fichier généré. Paramètres optionnels : {@code delay} (ms avant la réponse),
     * {@code size} (octets, uniquement pour le type « bin »).
     */
    @GetMapping("/download/{kind}")
    public ResponseEntity<byte[]> download(@PathVariable String kind,
                                           @RequestParam(defaultValue = "0") int delay,
                                           @RequestParam(defaultValue = "1024") int size) throws IOException {
        Latency.pause(delay);

        String filename;
        MediaType mediaType;
        byte[] content;

        switch (kind) {
            case "txt" -> {
                filename = "exemple.txt";
                mediaType = new MediaType("text", "plain", StandardCharsets.UTF_8);
                content = "Fichier d'exemple généré par socle-testapp.\nLigne 2.\nLigne 3.\n"
                        .getBytes(StandardCharsets.UTF_8);
            }
            case "csv" -> {
                filename = "exemple.csv";
                mediaType = new MediaType("text", "csv", StandardCharsets.UTF_8);
                content = "id;nom;categorie\n1;Article 001;Informatique\n2;Article 002;Maison\n3;Article 003;Jardin\n"
                        .getBytes(StandardCharsets.UTF_8);
            }
            case "json" -> {
                filename = "exemple.json";
                mediaType = MediaType.APPLICATION_JSON;
                content = "{\"application\":\"socle-testapp\",\"items\":[1,2,3]}".getBytes(StandardCharsets.UTF_8);
            }
            case "zip" -> {
                filename = "exemple.zip";
                mediaType = new MediaType("application", "zip");
                content = buildZip();
            }
            case "bin" -> {
                filename = "exemple.bin";
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
                int bounded = Math.max(0, Math.min(size, MAX_BIN_SIZE));
                content = new byte[bounded];
                for (int i = 0; i < bounded; i++) {
                    content[i] = (byte) (i % 256);
                }
            }
            default -> {
                return ResponseEntity.notFound().build();
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    /** Reçoit un fichier (champ multipart « file ») et renvoie son nom, sa taille et son empreinte SHA-256. */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) Integer delay,
                                                      Espace espace)
            throws IOException, NoSuchAlgorithmException {
        espace.latency().simulate(delay);
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Json.of("error", "Fichier vide ou absent"));
        }
        byte[] bytes = file.getBytes();
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return ResponseEntity.ok(Json.of(
                "name", file.getOriginalFilename(),
                "size", bytes.length,
                "contentType", file.getContentType(),
                "sha256", sha256));
    }

    private static byte[] buildZip() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("a.txt"));
            zip.write("Contenu du fichier A\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("dossier/b.txt"));
            zip.write("Contenu du fichier B\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }
}
