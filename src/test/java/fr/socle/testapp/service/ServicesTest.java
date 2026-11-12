package fr.socle.testapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests unitaires purs (sans Spring) des services. */
class ServicesTest {

    @Test
    void latence_bornee_et_configurable() {
        LatencyService latency = new LatencyService(0, 0);
        latency.configure(-10, 99_999_999);
        assertThat(latency.snapshot()).containsEntry("defaultDelayMs", 0).containsEntry("jitterMs", LatencyService.MAX_DELAY_MS);
        latency.configure(null, null);
        assertThat(latency.snapshot()).containsEntry("defaultDelayMs", 0);
        latency.configure(1, 1);
        long t0 = System.nanoTime();
        latency.simulate(null);
        latency.simulate(0);
        latency.sleep(-5);
        assertThat(System.nanoTime() - t0).isLessThan(2_000_000_000L);
    }

    @Test
    void latence_interrompue_conserve_le_drapeau() {
        LatencyService latency = new LatencyService(0, 0);
        Thread.currentThread().interrupt();
        latency.sleep(50);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void etat_des_scenarios() {
        ScenarioState state = new ScenarioState();
        assertThat(state.nextAttempt("a")).isEqualTo(1);
        assertThat(state.nextAttempt("a")).isEqualTo(2);
        String id = state.startJob(1000);
        assertThat(state.job(id).durationMs()).isEqualTo(1000);
        state.reset();
        assertThat(state.job(id)).isNull();
        assertThat(state.nextAttempt("a")).isEqualTo(1);
    }

    @Test
    void jeu_d_articles() {
        ItemStore store = new ItemStore();
        assertThat(store.query(null, null, null, 0, 100).totalElements()).isEqualTo(57);
        assertThat(store.query("  ", "price", "asc", 0, 10).items()).hasSize(10);
        assertThat(store.update(1, null, null, null)).get().extracting(ItemStore.Item::name).isEqualTo("Article 001");
        assertThat(store.delete(1)).isTrue();
        assertThat(store.update(1, "x", "y", 1.0)).isEmpty();
        store.reset();
        assertThat(store.query(null, null, null, 0, 10).totalElements()).isEqualTo(57);
    }
}
