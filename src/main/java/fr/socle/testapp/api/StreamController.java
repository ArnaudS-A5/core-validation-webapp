package fr.socle.testapp.api;

import java.io.IOException;
import java.time.Instant;

import fr.socle.testapp.service.Latency;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Flux temps réel poussé par le serveur (Server-Sent Events) : {@code count} événements,
 * un toutes les {@code interval} ms, puis un événement « end ».
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    static final int MAX_COUNT = 200;
    static final int MIN_INTERVAL_MS = 20;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestParam(defaultValue = "10") int count,
                             @RequestParam(defaultValue = "1000") int interval) {
        int events = Math.max(1, Math.min(count, MAX_COUNT));
        int pause = Math.max(MIN_INTERVAL_MS, Math.min(interval, Latency.MAX_DELAY_MS));
        SseEmitter emitter = new SseEmitter((long) events * pause + 30_000L);
        Thread.ofVirtual().name("sse-events").start(() -> push(emitter, events, pause));
        return emitter;
    }

    private void push(SseEmitter emitter, int events, int pause) {
        try {
            for (int seq = 1; seq <= events; seq++) {
                Latency.pause(pause);
                emitter.send(SseEmitter.event().id(String.valueOf(seq)).name("message")
                        .data(Json.of("seq", seq, "text", "Événement " + seq, "time", Instant.now().toString()),
                                MediaType.APPLICATION_JSON));
            }
            emitter.send(SseEmitter.event().name("end").data(Json.of("count", events), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // client parti : rien à faire
            emitter.completeWithError(e);
        }
    }
}
