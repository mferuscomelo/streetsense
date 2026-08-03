package io.streetsense.backend.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The dashboard's live feed, over Server-Sent Events on the virtual threads
 * already enabled for ingest (see {@code application.yml}) — an open SSE
 * connection is one blocked thread for as long as a browser tab stays open,
 * which is exactly what virtual threads make cheap.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class LiveFeedController {

    private final LiveFeedBroadcaster broadcaster;

    public LiveFeedController(LiveFeedBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
