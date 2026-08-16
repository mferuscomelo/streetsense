package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.wire.DecodedPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Generates additional contributors so the crowd effect can be demonstrated
 * with one physical node.
 *
 * <p>The beat this exists to make possible: a block that <em>your</em> node
 * has never visited still has an answer, because someone else walked it. That
 * is the actual product working, not a claim about future scale.
 *
 * <p><b>Everything it generates is flagged, twice over.</b> Contributor ids
 * carry {@link CrowdService#SEEDED_PREFIX}, so {@code CellSummary} reports
 * them separately from measured contributors; and every packet sets the mock
 * flag, so {@code FLAG_MOCK_DATA} propagates through the API exactly as it
 * does for the mock firmware. There is no path by which this data can present
 * itself as measured.
 *
 * <p>Off unless {@code streetsense.seed.enabled} is explicitly true. Seeded
 * data that appears without being asked for is how a demo turns into a
 * misrepresentation.
 */
@Configuration
public class ContributorSeeder {

    private static final Logger log = LoggerFactory.getLogger(ContributorSeeder.class);

    private final IngestService ingestService;
    private final SeedSettings settings;

    public ContributorSeeder(IngestService ingestService, SeedSettings settings) {
        this.ingestService = ingestService;
        this.settings = settings;
    }

    // Static so Spring can create this bean without first constructing
    // ContributorSeeder itself — which now takes a SeedSettings in its own
    // constructor. An instance @Bean method here would be circular.
    @Bean
    @ConfigurationProperties("streetsense.seed")
    public static SeedSettings seedSettings() {
        return new SeedSettings();
    }

    @Bean
    public ApplicationRunner seedRunner() {
        return args -> {
            if (!settings.isEnabled()) {
                return;
            }
            int written = seed(settings.toParams());
            log.warn("Seeded {} synthetic readings. "
                            + "Every one is flagged mock and prefixed '{}' — see docs/honest-caveats.md.",
                    written, CrowdService.SEEDED_PREFIX);
        };
    }

    // A dense, near-total fill across the whole viewport rather than a
    // handful of narrow road corridors — echoing how the reference photo
    // actually reads: almost every block downtown has *some* color, coverage
    // thins and cleans up toward the outskirts, and a scatter of missed
    // blocks and isolated outlier cells break up what would otherwise be too
    // clean a gradient. Every offset here is relative to the seeded center
    // (dLat/dLon cells, not degrees), so the exact same mask reproduces
    // regardless of where it's centered — this is a fixed shape, not a fresh
    // random layout each run.
    private static final int INNER_SOLID_RADIUS_CELLS = 4;   // guaranteed no gaps this close to center
    private static final int DENSE_RADIUS_CELLS = 26;        // near-total coverage inside this radius
    private static final int FRAY_RADIUS_CELLS = 40;         // coverage probability decays to 0 by here
    private static final int CORROBORATED_RADIUS_CELLS = 12; // cells this close get 2 independent contributors
    private static final double CORE_PM25 = 19.0;            // mean level right at the center
    private static final double EDGE_PM25 = 3.0;             // mean level out at the fray radius
    private static final double GAP_PROBABILITY = 0.05;      // even the dense core misses a few blocks

    // Isolated saturated squares and isolated clean pockets that don't
    // connect back to the main coverage — the reference photo has both:
    // stray hot readings far from downtown, and a couple of noticeably clean
    // patches breaking up the otherwise-uniform haze. {dLat, dLon} from the
    // seeded center.
    private static final int[][] HOT_SPOTS = {
            {-14, 6}, {9, -18}, {2, 24}, {-24, -5}, {18, 13}, {-7, -26},
    };
    private static final int[][] COOL_SPOTS = {
            {11, -23}, {-20, 17}, {25, 4},
    };

    private int seed(SeedParams params) throws Exception {
        // Fixed seed: the demo should show the same neighborhood shape every
        // time it is run, so a recorded walkthrough matches what a judge
        // sees on their own machine.
        Random random = new Random(20260816L);
        Instant base = Instant.now().minus(Duration.ofDays(7));
        int written = 0;

        // Bucket arithmetic, not degree arithmetic: longitude's degrees-per-
        // cell varies with latBucket (see GridCell's cos(latitude)
        // correction), so an offset in cells has to be added as buckets,
        // not degrees — degrees would drift off the true bucket boundary
        // away from the equator.
        GridCell origin = GridCell.of(params.centerLat(), params.centerLon());

        // rushFactorFor() swings well above 1.0 at peak hours, so averaged
        // across a whole day it would drag every cell's mean up past
        // whatever basePm25 was aimed at. Normalizing by the mean the
        // configured hour list actually produces keeps basePm25 meaning
        // what it says: the cell's mean level, not its peak-weighted one.
        double avgRushFactor = averageRushFactor(params.hours());

        for (int dLat = -FRAY_RADIUS_CELLS; dLat <= FRAY_RADIUS_CELLS; dLat++) {
            for (int dLon = -FRAY_RADIUS_CELLS; dLon <= FRAY_RADIUS_CELLS; dLon++) {
                double dist = Math.hypot(dLat, dLon);
                if (dist > FRAY_RADIUS_CELLS) {
                    continue; // a circle, not a square
                }

                double fillProbability;
                if (dist <= INNER_SOLID_RADIUS_CELLS) {
                    fillProbability = 1.0;
                } else if (dist <= DENSE_RADIUS_CELLS) {
                    fillProbability = 1.0 - GAP_PROBABILITY;
                } else {
                    double frac = (dist - DENSE_RADIUS_CELLS) / (FRAY_RADIUS_CELLS - DENSE_RADIUS_CELLS);
                    fillProbability = (1.0 - GAP_PROBABILITY) * (1.0 - frac);
                }
                if (random.nextDouble() > fillProbability) {
                    continue; // a missed block — a crowd never covers everything
                }

                // Radial falloff from a hot core to a cleaner edge, layered
                // with two octaves of position-based (not draw-order-based)
                // block bias so nearby cells drift together into patches —
                // the reference photo reads as blotches of consistent color
                // spanning several blocks, not independent per-cell static —
                // plus fine per-cell noise on top for texture.
                double falloff = Math.pow(Math.max(0, 1 - dist / FRAY_RADIUS_CELLS), 1.3);
                double basePm25 = EDGE_PM25 + (CORE_PM25 - EDGE_PM25) * falloff
                        + blockBias(dLat, dLon, 8, 1L) * 4.0
                        + blockBias(dLat, dLon, 3, 2L) * 2.0
                        + random.nextGaussian() * 1.2;

                GridCell cell = new GridCell(origin.latBucket() + dLat, origin.lonBucket() + dLon);
                int contributorCount = dist <= CORROBORATED_RADIUS_CELLS ? 2 : 1;
                for (int i = 0; i < contributorCount; i++) {
                    String contributorId = CrowdService.SEEDED_PREFIX + "cov-" + dLat + "_" + dLon + "-" + i;
                    written = writeCellReadings(cell, contributorId,
                            contributorId + "-session", random, base, params, written, basePm25, avgRushFactor);
                }
            }
        }

        for (int i = 0; i < HOT_SPOTS.length; i++) {
            GridCell center = new GridCell(origin.latBucket() + HOT_SPOTS[i][0], origin.lonBucket() + HOT_SPOTS[i][1]);
            written = fillSolidDisc(center, 1, 2, "hot-" + i, random, base, params, written, 21.0, avgRushFactor);
        }
        for (int i = 0; i < COOL_SPOTS.length; i++) {
            GridCell center = new GridCell(origin.latBucket() + COOL_SPOTS[i][0], origin.lonBucket() + COOL_SPOTS[i][1]);
            written = fillSolidDisc(center, 1, 2, "cool-" + i, random, base, params, written, 1.5, avgRushFactor);
        }

        return written;
    }

    /** Forces every cell within {@code radius} of {@code center} to have {@code contributorCount} independent readings at {@code basePm25}. */
    private int fillSolidDisc(GridCell center, int radius, int contributorCount, String idPrefix,
            Random random, Instant base, SeedParams params, int written, double basePm25, double avgRushFactor) throws Exception {
        for (int dLat = -radius; dLat <= radius; dLat++) {
            for (int dLon = -radius; dLon <= radius; dLon++) {
                if (dLat * dLat + dLon * dLon > radius * radius) {
                    continue; // a circle, not a square
                }
                GridCell cell = new GridCell(center.latBucket() + dLat, center.lonBucket() + dLon);
                for (int i = 0; i < contributorCount; i++) {
                    String contributorId = CrowdService.SEEDED_PREFIX + idPrefix + "-" + i;
                    written = writeCellReadings(cell, contributorId,
                            contributorId + "-session-" + dLat + "_" + dLon,
                            random, base, params, written, basePm25, avgRushFactor);
                }
            }
        }
        return written;
    }

    /** Writes one contributor's readings for one cell across every configured hour. Returns the updated running count. */
    private int writeCellReadings(GridCell cell, String contributorId, String sessionPrefix,
            Random random, Instant base, SeedParams params, int written, double basePm25, double avgRushFactor) throws Exception {
        for (int hour : params.hours()) {
            // Traffic-shaped, so the cleanest/quietest-hour recommendation
            // has a real pattern to find rather than picking whichever hour
            // noise happened to favour. Normalized against the hour list's
            // own average so basePm25 stays the cell's actual mean level.
            double rushFactor = rushFactorFor(hour) / avgRushFactor;

            for (int sample = 0; sample < params.samplesPerHour(); sample++) {
                double pm25 = basePm25 * rushFactor + random.nextGaussian() * 2.0;
                double voc = 110.0 * rushFactor + random.nextGaussian() * 12.0;
                double noise = 48.0 + (rushFactor - 1) * 9.0 + random.nextGaussian() * 2.5;

                DecodedPacket packet = new DecodedPacket(
                        1, true, sample,
                        pm25 * 0.6, Math.max(0, pm25), pm25 * 1.2, pm25 * 1.5,
                        Math.max(0, voc), 21.0, 52.0, Math.max(0, noise));

                DecodedReading reading = new DecodedReading(
                        packet, cell, hour, sessionPrefix + "-" + hour,
                        contributorId, Activity.WALK,
                        base.plus(Duration.ofSeconds(written)));

                ingestService.ingest(reading, contributorId);
                written++;
            }
        }
        return written;
    }

    /**
     * A plausible diurnal traffic curve. Deliberately not flat outside rush
     * hour: if every non-rush hour shared one factor, "cleanest hour" would
     * be decided by random noise and the recommendation would be
     * meaningless even though it rendered fine.
     */
    private static double rushFactorFor(int hour) {
        return switch (hour) {
            case 6 -> 0.7;   // clearly the cleanest, and the answer the demo should give
            case 7 -> 2.3;
            case 8 -> 2.6;   // morning peak
            case 12 -> 1.2;
            case 17 -> 2.4;
            case 18 -> 2.6;  // evening peak
            case 21 -> 0.9;
            default -> 1.0;
        };
    }

    /**
     * A fixed, position-keyed pseudo-random bias in roughly [-1, 1] — same
     * value every time for the same block, regardless of draw order. Cells
     * are grouped into {@code blockSize}-cell squares so neighbors share a
     * bias, producing coherent patches instead of independent per-cell
     * static; two calls at different block sizes and salts (see call sites)
     * layer coarse and fine patchiness, a cheap stand-in for value noise.
     */
    private static double blockBias(int dLat, int dLon, int blockSize, long salt) {
        long bLat = Math.floorDiv(dLat, blockSize);
        long bLon = Math.floorDiv(dLon, blockSize);
        long h = bLat * 374761393L + bLon * 668265263L + salt * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= (h >>> 16);
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2 - 1;
    }

    private static double averageRushFactor(int[] hours) {
        double sum = 0;
        for (int hour : hours) {
            sum += rushFactorFor(hour);
        }
        return sum / hours.length;
    }

    /** Immutable snapshot of one seeding run's parameters — see {@link SeedSettings#toParams()}. */
    public record SeedParams(double centerLat, double centerLon, int samplesPerHour, int[] hours) {}

    /** Bound from {@code streetsense.seed.*}. Disabled unless explicitly enabled. */
    public static class SeedSettings {
        private boolean enabled = false;
        private double centerLat = 49.0069;
        private double centerLon = 8.4037;
        private int samplesPerHour = 6;
        private int[] hours = {6, 7, 8, 9, 12, 15, 17, 18, 19, 21};

        SeedParams toParams() {
            return new SeedParams(centerLat, centerLon, samplesPerHour, hours);
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getCenterLat() { return centerLat; }
        public void setCenterLat(double centerLat) { this.centerLat = centerLat; }
        public double getCenterLon() { return centerLon; }
        public void setCenterLon(double centerLon) { this.centerLon = centerLon; }
        public int getSamplesPerHour() { return samplesPerHour; }
        public void setSamplesPerHour(int samplesPerHour) { this.samplesPerHour = samplesPerHour; }
        public int[] getHours() { return hours; }
        public void setHours(int[] hours) { this.hours = hours; }
    }
}
