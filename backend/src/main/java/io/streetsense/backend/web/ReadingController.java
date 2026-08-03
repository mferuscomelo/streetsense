package io.streetsense.backend.web;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.ingest.IngestResult;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.wire.DecodedPacket;
import io.streetsense.backend.wire.PacketLayout;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

    private static final String NODE_ID = "StreetSense-01";

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
        GridCell cell = GridCell.of(request.lat(), request.lon());
        DecodedReading reading = new DecodedReading(packet, cell, request.lat(), request.lon(), request.capturedAt());

        IngestResult result = ingestService.ingest(reading, NODE_ID);

        return ResponseEntity.ok(ReadingView.of(result.stored(), result.verdict()));
    }

    @GetMapping("/recent")
    public List<ReadingView> recent(@RequestParam(defaultValue = "50") int limit) {
        return repository.recent(limit).stream().map(ReadingView::of).toList();
    }
}
