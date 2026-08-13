package io.streetsense.backend.web;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.ingest.IngestResult;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.wire.DecodedPacket;
import io.streetsense.backend.wire.PacketLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

    private static final Logger log = LoggerFactory.getLogger(ReadingController.class);

    private final IngestService ingestService;
    private final ReadingRepository repository;
    private final LiveFeedBroadcaster liveFeed;

    public ReadingController(IngestService ingestService, ReadingRepository repository,
                             LiveFeedBroadcaster liveFeed) {
        this.ingestService = ingestService;
        this.repository = repository;
        this.liveFeed = liveFeed;
    }

    @PostMapping
    public ResponseEntity<ReadingView> submit(@RequestBody Map<String, Object> body) throws Exception {
        IngestRequest request = IngestRequest.from(body);
        byte[] rawPacket = Base64.getDecoder().decode(request.rawPacketBase64());
        DecodedPacket packet = PacketLayout.decode(rawPacket);
        // No snapping here: the request already carries a cell, because the
        // phone snapped before it uploaded. There is no coordinate to lose.
        DecodedReading reading = new DecodedReading(
                packet, request.cell(), request.hourOfDay(),
                request.sessionId(), request.contributorId(), request.activity(),
                request.capturedAt());

        IngestResult result = ingestService.ingest(reading, request.contributorId());
        ReadingView view = ReadingView.of(result.stored(), result.verdict());
        log.debug("Reading submitted: cell={} verdict={}", reading.cell(), view.verdict().type());

        // Fire-and-forget: a dashboard tab being open (or not) has no
        // business affecting whether an ingest succeeds, so this can never
        // become a reason the POST fails.
        liveFeed.publish(view);

        return ResponseEntity.ok(view);
    }

    @GetMapping("/recent")
    public List<ReadingView> recent(@RequestParam(defaultValue = "50") int limit) {
        List<ReadingView> views = repository.recent(limit).stream().map(ReadingView::of).toList();
        log.debug("Returning {} recent readings (limit={})", views.size(), limit);
        return views;
    }

    /**
     * A malformed or out-of-date submission is the client's problem, not a
     * server fault. This matters most for app builds predating the privacy
     * split: they post {@code lat}/{@code lon} and no cell, and they need a
     * 400 that says so rather than a 500 that reads like the backend broke.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        log.warn("Rejected malformed submission: {}", e.getMessage());
        return Map.of("error", e.getMessage());
    }
}
