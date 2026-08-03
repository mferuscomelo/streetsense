package io.streetsense.backend.web;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.ingest.IngestResult;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.wire.DecodedPacket;
import io.streetsense.backend.wire.PacketLayout;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

    private final IngestService ingestService;
    private final ReadingRepository repository;

    public ReadingController(IngestService ingestService, ReadingRepository repository) {
        this.ingestService = ingestService;
        this.repository = repository;
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

        return ResponseEntity.ok(ReadingView.of(result.stored(), result.verdict()));
    }

    @GetMapping("/recent")
    public List<ReadingView> recent(@RequestParam(defaultValue = "50") int limit) {
        return repository.recent(limit).stream().map(ReadingView::of).toList();
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
        return Map.of("error", e.getMessage());
    }
}
