package io.streetsense.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans one ingested reading out to every browser tab watching the
 * dashboard's live feed.
 *
 * <p>A plain {@code CopyOnWriteArrayList<SseEmitter>} rather than a message
 * broker: this is one backend instance holding a handful of open browser
 * connections, not a multi-node fan-out problem. Sends happen on whichever
 * thread calls {@link #publish}, which is the request thread that just
 * finished an ingest — cheap because virtual threads (already enabled for
 * ingest's structured-concurrency fan-out) make blocking a send no costlier
 * than blocking anything else.
 */
@Component
public class LiveFeedBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveFeedBroadcaster.class);

    /** Long enough that a dashboard left open overnight doesn't need to reconnect hourly. */
    private static final Duration EMITTER_TIMEOUT = Duration.ofHours(4);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        emitters.add(emitter);
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("Live feed emitter completed, {} remaining", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("Live feed emitter timed out, {} remaining", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("Live feed emitter errored ({}), {} remaining", e.toString(), emitters.size());
        });
        return emitter;
    }

    public void publish(ReadingView reading) {
        log.debug("Broadcasting reading id={} to {} live feed subscribers", reading.id(), emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("reading")
                        .data(reading, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                // The client is gone; its own onError/onCompletion callback
                // will remove it from `emitters` — nothing to do here beyond
                // not letting one dead connection stop the others' sends.
                log.debug("Failed to send to a live feed emitter: {}", e.toString());
            }
        }
    }
}
