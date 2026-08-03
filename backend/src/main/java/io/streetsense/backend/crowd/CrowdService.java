package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.CellKey;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.repository.ReadingRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pools every contributor's readings into a shared per-block picture.
 *
 * <p>This is the part of StreetSense that only works because more than one
 * person carries a node. A contributor gets a verdict on a street they have
 * personally never walked, because someone else walked it — and that, rather
 * than any claim about future scale, is the network effect. It is
 * demonstrable with two contributors.
 *
 * <p><b>On structured concurrency.</b> The implementation plan wanted a second
 * {@code StructuredTaskScope} site here, to sit alongside the genuine one in
 * {@code ingest/IngestService}. Written out, it was contrived: this merge is
 * a handful of in-memory map lookups, and forking them would be ceremony
 * around work that does not block. {@code docs/java26-jeps.md} sets the rule
 * that a strained feature should be dropped rather than defended, so it is
 * dropped. If the repository becomes Postgres-backed, the per-cell
 * aggregations below become independent queries and the case changes — see
 * {@code docs/future-work.md}.
 */
@Service
public class CrowdService {

    /**
     * Marks a contributor generated to demonstrate the merge rather than
     * measured. Prefix rather than a flag on the reading because it survives
     * every path a contributor id travels — repository, baseline, API — with
     * no way to accidentally drop it.
     */
    public static final String SEEDED_PREFIX = "seed:";

    private static final int PER_HOUR_LIMIT = 500;
    private static final int UNKNOWN_HOUR = -1;

    private final ReadingRepository repository;

    public CrowdService(ReadingRepository repository) {
        this.repository = repository;
    }

    /** Every block anyone has contributed to, for the city map. */
    public List<CellSummary> cityView() {
        Set<GridCell> cells = repository.knownKeys().stream()
                .map(CellKey::cell)
                .collect(Collectors.toSet());

        return cells.stream()
                .map(this::summarise)
                .sorted(Comparator.comparingInt(CellSummary::sampleCount).reversed())
                .toList();
    }

    public CellSummary summarise(GridCell cell) {
        Map<Integer, List<DecodedReading>> byHour = readingsByHour(cell);

        List<DecodedReading> all = byHour.values().stream().flatMap(List::stream).toList();
        if (all.isEmpty()) {
            return new CellSummary(cell, 0, 0, 0, 0, 0, UNKNOWN_HOUR, UNKNOWN_HOUR, false);
        }

        Set<String> contributors = all.stream()
                .map(DecodedReading::contributorId)
                .collect(Collectors.toSet());
        long seeded = contributors.stream().filter(CrowdService::isSeeded).count();

        return new CellSummary(
                cell,
                all.size(),
                contributors.size(),
                (int) seeded,
                all.stream().mapToDouble(DecodedReading::pm2_5).average().orElse(0),
                all.stream().mapToDouble(DecodedReading::noiseDb).average().orElse(0),
                bestHour(byHour, DecodedReading::pm2_5),
                bestHour(byHour, DecodedReading::noiseDb),
                all.stream().anyMatch(DecodedReading::mock));
    }

    public static boolean isSeeded(String contributorId) {
        return contributorId != null && contributorId.startsWith(SEEDED_PREFIX);
    }

    private Map<Integer, List<DecodedReading>> readingsByHour(GridCell cell) {
        Map<Integer, List<DecodedReading>> byHour = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            List<DecodedReading> readings = repository.recentForKey(new CellKey(cell, hour), PER_HOUR_LIMIT);
            if (!readings.isEmpty()) {
                byHour.put(hour, readings);
            }
        }
        return byHour;
    }

    /**
     * The hour with the lowest mean for the given measure — the actual
     * recommendation this whole hour-keyed model exists to produce. Not
     * "avoid this street", which people cannot always act on, but "go at
     * seven rather than six", which they can.
     */
    private static int bestHour(Map<Integer, List<DecodedReading>> byHour,
                                java.util.function.ToDoubleFunction<DecodedReading> measure) {
        return byHour.entrySet().stream()
                .min(Comparator.comparingDouble(e ->
                        e.getValue().stream().mapToDouble(measure).average().orElse(Double.MAX_VALUE)))
                .map(Map.Entry::getKey)
                .orElse(UNKNOWN_HOUR);
    }
}
