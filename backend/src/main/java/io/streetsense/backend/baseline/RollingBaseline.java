package io.streetsense.backend.baseline;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.repository.ReadingRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Gatherers;

/**
 * The rolling per-cell baseline — the feature the entire anomaly-detection
 * "AI" claim rests on. A sliding window over recent readings is literally
 * what {@code Gatherers.windowSliding(n)} is for; {@link EwmaGatherer}
 * folds those windows into one running smoothed value per cell.
 *
 * {@link #currentBaseline} returns the baseline as of *before* the reading
 * currently being ingested, so {@link io.streetsense.backend.anomaly.AnomalyDetector}
 * always judges a reading against history, never against itself.
 */
@Component
public class RollingBaseline {

    private static final int WINDOW = 5;
    private static final double EWMA_ALPHA = 0.3;
    private static final int HISTORY_LIMIT = 50;

    private final ReadingRepository repository;
    private final Map<GridCell, CellStats> cache = new ConcurrentHashMap<>();

    public RollingBaseline(ReadingRepository repository) {
        this.repository = repository;
    }

    public CellStats currentBaseline(GridCell cell) {
        return cache.getOrDefault(cell, CellStats.of(List.of()));
    }

    /** Recomputes and caches the baseline for {@code reading}'s cell, including it. */
    public CellStats update(DecodedReading reading) {
        List<DecodedReading> history = new ArrayList<>(repository.recentForCell(reading.cell(), HISTORY_LIMIT));
        history.add(reading);

        CellStats result;
        if (history.size() < WINDOW) {
            result = CellStats.of(history);
        } else {
            result = history.stream()
                    .gather(Gatherers.windowSliding(WINDOW))
                    .map(CellStats::of)
                    .gather(EwmaGatherer.of(EWMA_ALPHA))
                    .reduce((first, second) -> second)
                    .orElseGet(() -> CellStats.of(history));
        }

        cache.put(reading.cell(), result);
        return result;
    }
}
