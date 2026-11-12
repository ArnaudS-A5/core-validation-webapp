package fr.socle.testapp.api;

import fr.socle.testapp.service.ItemStore;
import fr.socle.testapp.service.LatencyService;
import fr.socle.testapp.service.ScenarioState;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Contexte Spring complet, sans latence simulée, données remises à zéro avant chaque test. */
@SpringBootTest(properties = {"testapp.latency.default-ms=0", "testapp.latency.jitter-ms=0"})
@AutoConfigureMockMvc
abstract class AbstractApiTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ItemStore itemStore;

    @Autowired
    protected ScenarioState scenarioState;

    @Autowired
    protected LatencyService latencyService;

    @BeforeEach
    void resetState() {
        itemStore.reset();
        scenarioState.reset();
        latencyService.configure(0, 0);
    }
}
