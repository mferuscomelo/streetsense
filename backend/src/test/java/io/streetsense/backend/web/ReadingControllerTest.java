package io.streetsense.backend.web;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.ingest.IngestResult;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.repository.StoredReading;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingController.class)
class ReadingControllerTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);

    // Golden byte vector — see docs/golden-packet.md.
    private static final String GOLDEN_PACKET_B64 = Base64.getEncoder().encodeToString(new byte[]{
            0x01, 0x01, 0x2A, 0x00, 0x53, 0x00, (byte) 0x9D, 0x00, (byte) 0xC0, 0x00,
            (byte) 0xF6, 0x00, 0x41, 0x05, 0x59, 0x08, (byte) 0xA0, 0x14, 0x48, 0x02
    });

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestService ingestService;

    @MockitoBean
    private ReadingRepository repository;

    @Test
    void postReadingsReturnsDecodedVerdict() throws Exception {
        DecodedPacket packet = new DecodedPacket(1, true, 42, 8.3, 15.7, 19.2, 24.6, 134.5, 21.37, 52.80, 58.4);
        DecodedReading reading = new DecodedReading(packet, CELL, 49.0069, 8.4037, Instant.parse("2026-08-03T12:00:00Z"));
        StoredReading stored = new StoredReading(1L, reading, null, Instant.now());
        CellStats baseline = CellStats.of(List.of());
        Verdict verdict = new Verdict.Normal(baseline);

        when(ingestService.ingest(any(), anyString())).thenReturn(new IngestResult(stored, baseline, verdict));

        mockMvc.perform(post("/api/v1/readings")
                        .contentType("application/json")
                        .content("""
                                {"rawPacket":"%s","lat":49.0069,"lon":8.4037,"capturedAt":"2026-08-03T12:00:00Z"}
                                """.formatted(GOLDEN_PACKET_B64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.pm2_5").value(15.7))
                .andExpect(jsonPath("$.verdict.type").value("NORMAL"));
    }

    @Test
    void recentReturnsDecodedListing() throws Exception {
        DecodedPacket packet = new DecodedPacket(1, true, 42, 8.3, 15.7, 19.2, 24.6, 134.5, 21.37, 52.80, 58.4);
        DecodedReading reading = new DecodedReading(packet, CELL, 49.0069, 8.4037, Instant.now());
        Verdict verdict = new Verdict.Normal(CellStats.of(List.of()));
        StoredReading stored = new StoredReading(1L, reading, verdict, Instant.now());

        when(repository.recent(50)).thenReturn(List.of(stored));

        mockMvc.perform(get("/api/v1/readings/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].verdict.type").value("NORMAL"));
    }
}
