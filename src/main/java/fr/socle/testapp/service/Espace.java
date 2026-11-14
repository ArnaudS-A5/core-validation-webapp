package fr.socle.testapp.service;

/**
 * Espace de données isolé : ses propres articles, compteurs de scénarios, tâches longues, latence et réseau simulé.
 * Plusieurs sessions de test peuvent ainsi travailler en parallèle sans se gêner.
 */
public final class Espace {

    private final String id;
    private final ItemStore items = new ItemStore();
    private final ScenarioState scenarios = new ScenarioState();
    private final Latency latency;
    private final Reseau reseau = new Reseau();
    private volatile long lastAccessMs;

    Espace(String id, Latency latency, long nowMs) {
        this.id = id;
        this.latency = latency;
        this.lastAccessMs = nowMs;
    }

    public String id() {
        return id;
    }

    public ItemStore items() {
        return items;
    }

    public ScenarioState scenarios() {
        return scenarios;
    }

    public Latency latency() {
        return latency;
    }

    public Reseau reseau() {
        return reseau;
    }

    /**
     * Remet les articles et les compteurs de scénarios à leur état initial, et le réseau sans profil
     * (un scénario ne doit pas hériter du réseau dégradé d'un autre). La latence est conservée.
     */
    public void reset() {
        items.reset();
        scenarios.reset();
        reseau.configurer(ProfilReseau.AUCUN, null);
    }

    long lastAccessMs() {
        return lastAccessMs;
    }

    void touch(long nowMs) {
        this.lastAccessMs = nowMs;
    }
}
