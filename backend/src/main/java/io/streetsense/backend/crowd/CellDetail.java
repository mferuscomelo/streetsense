package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;

import java.time.Instant;
import java.util.List;

/**
 * Everything the dashboard's cell click-through panel needs in one round
 * trip: the same {@link CellSummary} the map already shows, plus the full
 * pollutant breakdown, hourly time series, and related sessions that
 * {@link CrowdService#summarise} computes and then throws away.
 */
public record CellDetail(
        GridCell cell,
        CellSummary summary,
        PollutantMeans means,
        List<HourlyBucket> hourly,
        List<RelatedSession> sessions) {

    /** All eight pollutant channels a reading carries, meaned over some set of readings. */
    public record PollutantMeans(
            double pm1, double pm2_5, double pm4, double pm10,
            double vocIndex, double tempC, double humidity, double noiseDb) {

        static final PollutantMeans EMPTY = new PollutantMeans(0, 0, 0, 0, 0, 0, 0, 0);

        static PollutantMeans of(List<DecodedReading> readings) {
            if (readings.isEmpty()) {
                return EMPTY;
            }
            return new PollutantMeans(
                    readings.stream().mapToDouble(DecodedReading::pm1).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::pm2_5).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::pm4).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::pm10).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::vocIndex).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::tempC).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::humidity).average().orElse(0),
                    readings.stream().mapToDouble(DecodedReading::noiseDb).average().orElse(0));
        }
    }

    /** One hour-of-day slice of the cell's history — the chart's x-axis unit. */
    public record HourlyBucket(int hourOfDay, int sampleCount, int contributorCount, PollutantMeans means) {}

    /**
     * A session that passed through this specific cell. Counts and time
     * range are scoped to readings taken *in this cell*, not the whole
     * session — a session may cross many blocks, and "1 flagged event"
     * here should mean it happened here, not somewhere else on the walk.
     */
    public record RelatedSession(
            String sessionId, String activity, Instant startedAt, Instant endedAt,
            int readingCountInCell, int eventCount, boolean mock) {}
}
