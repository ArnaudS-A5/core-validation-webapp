package fr.socle.testapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class VersionControllerTest extends AbstractApiTest {

    @Test
    void version_exposee_par_l_api() throws Exception {
        mvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", notNullValue()))
                .andExpect(jsonPath("$.commit", notNullValue()))
                .andExpect(jsonPath("$.buildTime", matchesPattern("^(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})?$")));
    }

    private static BuildProperties build(String commit) {
        Properties p = new Properties();
        p.setProperty("version", "1.0.42");
        p.setProperty("time", Instant.parse("2026-11-13T09:30:00Z").toString());
        if (commit != null) {
            p.setProperty("commit", commit);
        }
        return new BuildProperties(p);
    }

    @Test
    void commit_raccourci_et_date_lisible() {
        var v = new VersionController(Optional.of(build("0123456789abcdef"))).version();
        assertThat(v).containsEntry("version", "1.0.42")
                .containsEntry("commit", "01234567")
                .containsEntry("buildTime", "13/11/2026 10:30");
    }

    @Test
    void commit_absent_ou_non_resolu() {
        assertThat(new VersionController(Optional.of(build(null))).version()).containsEntry("commit", "local");
        assertThat(new VersionController(Optional.of(build("${env.GIT_COMMIT}"))).version()).containsEntry("commit", "local");
        assertThat(new VersionController(Optional.of(build("abc"))).version()).containsEntry("commit", "abc");
    }

    @Test
    void sans_informations_de_build() {
        assertThat(new VersionController(Optional.empty()).version())
                .containsEntry("version", "dev").containsEntry("commit", "local");
    }
}
