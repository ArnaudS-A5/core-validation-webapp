package fr.socle.testapp.api;

import java.util.Map;
import java.util.Optional;

import fr.socle.testapp.service.Espace;
import fr.socle.testapp.service.ItemStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Liste paginée, triable et filtrable, avec création, modification et suppression. */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    public record ItemRequest(String name, String category, Double price) {
    }

    @GetMapping
    public ItemStore.Page list(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String sort,
                               @RequestParam(required = false) String dir,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) Integer delay,
                                      Espace espace) {
        espace.latency().simulate(delay);
        return espace.items().query(q, sort, dir, page, size);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ItemRequest request,
                                                       @RequestParam(required = false) Integer delay,
                                      Espace espace) {
        espace.latency().simulate(delay);
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.unprocessableEntity()
                    .body(Json.of("errors", Json.of("name", "Le nom est obligatoire")));
        }
        ItemStore.Item item = espace.items().create(request.name().trim(),
                request.category() == null || request.category().isBlank() ? "Divers" : request.category(),
                request.price() == null ? 0.0 : request.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(Json.of("item", item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable int id,
                                                      @RequestBody ItemRequest request,
                                                      @RequestParam(required = false) Integer delay,
                                      Espace espace) {
        espace.latency().simulate(delay);
        Optional<ItemStore.Item> updated = espace.items().update(id, request.name(), request.category(), request.price());
        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Json.of("error", "Article introuvable"));
        }
        return ResponseEntity.ok(Json.of("item", updated.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int id,
                                                      @RequestParam(required = false) Integer delay,
                                      Espace espace) {
        espace.latency().simulate(delay);
        if (!espace.items().delete(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Json.of("error", "Article introuvable"));
        }
        return ResponseEntity.ok(Json.of("deleted", id));
    }
}
