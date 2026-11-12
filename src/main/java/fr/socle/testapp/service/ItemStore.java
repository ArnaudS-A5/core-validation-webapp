package fr.socle.testapp.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Jeu de données factice (57 articles) en mémoire, modifiable (ajout, modification, suppression)
 * et réinitialisable via /api/admin/reset.
 */
@Component
public class ItemStore {

    public record Item(int id, String name, String category, double price, boolean inStock) {
    }

    public record Page(List<Item> items, int page, int size, long totalElements, int totalPages) {
    }

    private static final String[] CATEGORIES = {"Informatique", "Maison", "Jardin", "Sport", "Livres"};
    private static final int INITIAL_COUNT = 57;

    private final List<Item> items = new ArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public ItemStore() {
        reset();
    }

    public synchronized void reset() {
        items.clear();
        sequence.set(0);
        for (int i = 1; i <= INITIAL_COUNT; i++) {
            double price = Math.round((5 + (i * 7.37) % 95) * 100) / 100.0;
            items.add(new Item(sequence.incrementAndGet(),
                    String.format(Locale.ROOT, "Article %03d", i),
                    CATEGORIES[(i - 1) % CATEGORIES.length],
                    price,
                    i % 4 != 0));
        }
    }

    public synchronized Page query(String q, String sort, String dir, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);

        List<Item> filtered = new ArrayList<>();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        for (Item item : items) {
            if (needle.isEmpty()
                    || item.name().toLowerCase(Locale.ROOT).contains(needle)
                    || item.category().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(item);
            }
        }

        Comparator<Item> comparator;
        String sortKey = sort == null ? "id" : sort;
        switch (sortKey) {
            case "name" -> comparator = Comparator.comparing(Item::name);
            case "category" -> comparator = Comparator.comparing(Item::category);
            case "price" -> comparator = Comparator.comparingDouble(Item::price);
            default -> comparator = Comparator.comparingInt(Item::id);
        }
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }
        filtered.sort(comparator);

        long total = filtered.size();
        int totalPages = (int) Math.max(1, (total + safeSize - 1) / safeSize);
        int from = (int) Math.min((long) safePage * safeSize, total);
        int to = (int) Math.min((long) from + safeSize, total);
        return new Page(new ArrayList<>(filtered.subList(from, to)), safePage, safeSize, total, totalPages);
    }

    public synchronized Item create(String name, String category, double price) {
        Item item = new Item(sequence.incrementAndGet(), name, category, price, true);
        items.add(item);
        return item;
    }

    public synchronized Optional<Item> update(int id, String name, String category, Double price) {
        for (int i = 0; i < items.size(); i++) {
            Item current = items.get(i);
            if (current.id() == id) {
                Item updated = new Item(id,
                        name != null && !name.isBlank() ? name : current.name(),
                        category != null && !category.isBlank() ? category : current.category(),
                        price != null ? price : current.price(),
                        current.inStock());
                items.set(i, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean delete(int id) {
        return items.removeIf(item -> item.id() == id);
    }
}
