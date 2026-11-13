package fr.socle.testapp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class StreamControllerTest extends AbstractApiTest {

    private String awaitEnd(MvcResult result) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String body = "";
        while (System.currentTimeMillis() < deadline) {
            body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains("event:end")) {
                return body;
            }
            Thread.sleep(20);
        }
        return body;
    }

    @Test
    void flux_d_evenements_puis_fin() throws Exception {
        MvcResult result = mvc.perform(get("/api/stream/events").param("count", "3").param("interval", "20"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitEnd(result);
        assertThat(body).contains("id:1", "id:3", "Événement 3", "event:end", "\"count\":3");
        assertThat(body).doesNotContain("id:4");
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void parametres_bornes() throws Exception {
        MvcResult result = mvc.perform(get("/api/stream/events").param("count", "0").param("interval", "1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = awaitEnd(result);
        assertThat(body).contains("id:1", "\"count\":1").doesNotContain("id:2");
    }
}
