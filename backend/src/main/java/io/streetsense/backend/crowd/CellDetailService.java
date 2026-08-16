package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.repository.StoredReading;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Builds the cell click-through view: the same evidence {@link CrowdService}
 * pools for the map, unrolled into an hourly series and joined against the
 * sessions that passed through this one cell.
 *
 * <p>There is no {@code GridCell -> sessionId} index in the repository, and
 * this deliberately doesn't add one. The 24-hour {@code (cell, hour)} scan
 * this already needs returns raw readings with {@code sessionId} right on
 * them, so collecting distinct ids off that list is free; a dedicated index
 * would just be a second structure that can go stale the same way the
 * repository's own {@code byId} ring buffer already can. See
 * {@link ReadingRepository#forSession} for the eviction-safe read this
 * relies on.
 */
@Service
public class CellDetailService {

    private static final int SESSIONS_LIMIT = 20;

    private final ReadingRepository repository;
    private final CrowdService crowd;

    public CellDetailService(ReadingRepository repository, CrowdService crowd) {
        this.repository = repository;
        this.crowd = crowd;
    }

    public CellDetail detail(GridCell cell) {
        CellSummary summary = crowd.summarise(cell);
        Map<Integer, List<DecodedReading>> byHour = crowd.readingsByHour(cell);
        List<DecodedReading> all = byHour.values().stream().flatMap(List::stream).toList();

        List<CellDetail.HourlyBucket> hourly = IntStream.range(0, 24)
                .mapToObj(hour -> {
                    List<DecodedReading> readings = byHour.getOrDefault(hour, List.of());
                    int contributors = (int) readings.stream()
                            .map(DecodedReading::contributorId).distinct().count();
                    return new CellDetail.HourlyBucket(
                            hour, readings.size(), contributors, CellDetail.PollutantMeans.of(readings));
                })
                .toList();

        return new CellDetail(cell, summary, CellDetail.PollutantMeans.of(all), hourly, relatedSessions(cell, all));
    }

    private List<CellDetail.RelatedSession> relatedSessions(GridCell cell, List<DecodedReading> cellReadings) {
        List<String> sessionIds = cellReadings.stream()
                .sorted(Comparator.comparing(DecodedReading::capturedAt).reversed())
                .map(DecodedReading::sessionId)
                .distinct()
                .limit(SESSIONS_LIMIT)
                .toList();

        return sessionIds.stream()
                .map(id -> summariseForCell(id, cell))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * {@code null} when the session's readings for this cell have all been
     * evicted from the ring buffer, or (rarer) when every reading collected
     * above turns out to belong to a session that no longer resolves at all
     * — {@link ReadingRepository#forSession} already filters those out
     * rather than returning nulls, so this only has to handle "empty".
     */
    private CellDetail.RelatedSession summariseForCell(String sessionId, GridCell cell) {
        List<StoredReading> inCell = repository.forSession(sessionId).stream()
                .filter(s -> s.reading().cell().equals(cell))
                .sorted(Comparator.comparing(s -> s.reading().capturedAt()))
                .toList();
        if (inCell.isEmpty()) {
            return null;
        }
        DecodedReading first = inCell.get(0).reading();
        DecodedReading last = inCell.get(inCell.size() - 1).reading();
        int events = (int) inCell.stream()
                .filter(s -> s.verdict() != null && !(s.verdict() instanceof Verdict.Normal))
                .count();
        return new CellDetail.RelatedSession(
                sessionId, first.activity().name(), first.capturedAt(), last.capturedAt(),
                inCell.size(), events, first.mock());
    }
}
