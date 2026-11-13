package fr.socle.testapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Cycle de vie des espaces : création, réutilisation, nettoyage des espaces inactifs, plafond. */
class EspaceRegistryTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private EspaceRegistry registry() {
        EspaceRegistry registry = new EspaceRegistry(7, 0);
        registry.setClock(now::get);
        return registry;
    }

    @Test
    void creation_et_reutilisation() {
        EspaceRegistry registry = registry();
        Espace a = registry.get("a");
        assertThat(registry.get("a")).isSameAs(a);
        assertThat(registry.get("commun")).isSameAs(registry.common());
        assertThat(registry.get(null)).isSameAs(registry.common());
        assertThat(a.latency().snapshot()).containsEntry("defaultDelayMs", 7);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void espaces_inactifs_supprimes_apres_une_heure() {
        EspaceRegistry registry = registry();
        Espace ancien = registry.get("ancien");
        now.addAndGet(EspaceRegistry.IDLE_MS - 1);
        registry.get("recent");
        now.addAndGet(EspaceRegistry.PURGE_EVERY_MS + 2);
        registry.get("declencheur");
        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.get("ancien")).as("recréé à neuf").isNotSameAs(ancien);
    }

    @Test
    void nombre_d_espaces_plafonne() {
        EspaceRegistry registry = registry();
        for (int i = 0; i < EspaceRegistry.MAX_SPACES + 10; i++) {
            now.incrementAndGet();
            registry.get("s" + i);
        }
        assertThat(registry.count()).isLessThanOrEqualTo(EspaceRegistry.MAX_SPACES);
        assertThat(registry.get("s" + (EspaceRegistry.MAX_SPACES + 9)).id()).isEqualTo("s" + (EspaceRegistry.MAX_SPACES + 9));
    }

    @Test
    void remise_a_zero_d_un_espace() {
        Espace espace = registry().get("r");
        espace.items().delete(1);
        espace.scenarios().nextAttempt("k");
        espace.reset();
        assertThat(espace.items().query(null, null, null, 0, 100).totalElements()).isEqualTo(57);
        assertThat(espace.scenarios().nextAttempt("k")).isEqualTo(1);
    }
}
