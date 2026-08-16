package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.ingest.IngestService;
import io.streetsense.backend.repository.ReadingRepository;
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

    private final ReadingRepository repository;
    private final IngestService ingestService;
    private final SeedSettings settings;

    public ContributorSeeder(ReadingRepository repository, IngestService ingestService, SeedSettings settings) {
        this.repository = repository;
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
            log.warn("Seeded {} synthetic readings from {} generated contributors. "
                            + "Every one is flagged mock and prefixed '{}' — see docs/honest-caveats.md.",
                    written, settings.getContributors(), CrowdService.SEEDED_PREFIX);
        };
    }

    /**
     * Re-seeds around a caller-supplied center — the dashboard's resolved
     * geolocation, typically — instead of the configured default. Evicts
     * whatever seeded data exists first, so repeated calls (page reloads,
     * geolocation resolving after an initial fallback paint) replace the
     * visible neighborhood rather than layering duplicates under the same
     * deterministic contributor ids, or leaving a stray cluster behind at
     * the old center. Never touches measured readings — eviction only
     * matches {@link CrowdService#isSeeded}.
     */
    public int seedAround(double lat, double lon) throws Exception {
        if (!settings.isEnabled()) {
            throw new IllegalStateException("streetsense.seed.enabled is false");
        }
        repository.evictWhere(CrowdService::isSeeded);
        int written = seed(settings.toParams().withCenter(lat, lon));
        log.info("Re-seeded {} synthetic readings around {},{}", written, lat, lon);
        return written;
    }

    private int seed(SeedParams params) throws Exception {
        // Fixed seed: the demo should show the same neighborhood shape every
        // time it is run, so a recorded walkthrough matches what a judge
        // sees on their own machine.
        Random random = new Random(20260816L);
        Instant base = Instant.now().minus(Duration.ofDays(7));
        int written = 0;

        for (int c = 0; c < params.contributors(); c++) {
            String contributorId = CrowdService.SEEDED_PREFIX + "contributor-" + c;

            // Contributors are laid out across a handful of parallel
            // "avenues" (longitude offset) as well as staggered starting
            // points along each one (latitude offset) — a 2-D block, not
            // one street. Overlap is still the entire point: it produces
            // cells that several people have independently sampled
            // (CORROBORATED) next to cells only one person has
            // (SINGLE_CONTRIBUTOR), which is what makes the confidence
            // distinction visible instead of theoretical.
            //
            // An earlier version gave each contributor their own parallel
            // column with zero overlap, so no two ever shared a cell and
            // every cell in the city reported exactly one contributor — the
            // crowd layer rendered perfectly and demonstrated nothing.
            int avenue = c % params.avenueCount();
            int progress = c / params.avenueCount();
            int startCell = progress * params.routeOffsetCells();

            for (int step = 0; step < params.cells(); step++) {
                int cellIndex = startCell + step;
                GridCell cell = GridCell.of(
                        params.centerLat() + cellIndex * 0.001,
                        params.centerLon() + avenue * params.avenueOffsetCells() * 0.001);

                for (int hour : params.hours()) {
                    // Traffic-shaped, so the cleanest/quietest-hour
                    // recommendation has a real pattern to find rather than
                    // picking whichever hour noise happened to favour.
                    double rushFactor = rushFactorFor(hour);

                    for (int sample = 0; sample < params.samplesPerHour(); sample++) {
                        double pm25 = 9.0 * rushFactor + random.nextGaussian() * 2.0;
                        double voc = 110.0 * rushFactor + random.nextGaussian() * 12.0;
                        double noise = 48.0 + (rushFactor - 1) * 9.0 + random.nextGaussian() * 2.5;

                        DecodedPacket packet = new DecodedPacket(
                                1, true, sample,
                                pm25 * 0.6, Math.max(0, pm25), pm25 * 1.2, pm25 * 1.5,
                                Math.max(0, voc), 21.0, 52.0, Math.max(0, noise));

                        DecodedReading reading = new DecodedReading(
                                packet, cell, hour,
                                contributorId + "-session-" + avenue + "-" + cellIndex + "-" + hour,
                                contributorId, Activity.WALK,
                                base.plus(Duration.ofSeconds(written)));

                        ingestService.ingest(reading, contributorId);
                        written++;
                    }
                }
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

    /** Immutable snapshot of one seeding run's parameters — see {@link SeedSettings#toParams()}. */
    public record SeedParams(
            double centerLat, double centerLon,
            int contributors, int cells, int routeOffsetCells,
            int avenueCount, int avenueOffsetCells,
            int samplesPerHour, int[] hours) {

        public SeedParams withCenter(double lat, double lon) {
            return new SeedParams(lat, lon, contributors, cells, routeOffsetCells,
                    avenueCount, avenueOffsetCells, samplesPerHour, hours);
        }
    }

    /** Bound from {@code streetsense.seed.*}. Disabled unless explicitly enabled. */
    public static class SeedSettings {
        private boolean enabled = false;
        private double centerLat = 49.0069;
        private double centerLon = 8.4037;
        private int contributors = 16;
        private int cells = 14;
        /** How far along a shared avenue each successive contributor on it starts. Smaller = more overlap. */
        private int routeOffsetCells = 2;
        /** How many parallel avenues contributors are spread across. */
        private int avenueCount = 4;
        /** Longitude spacing (in cells) between adjacent avenues. 1 keeps avenues
         *  in adjacent columns so the seeded grid reads as a gapless block rather
         *  than a striped one. */
        private int avenueOffsetCells = 1;
        private int samplesPerHour = 10;
        private int[] hours = {6, 7, 8, 9, 12, 15, 17, 18, 19, 21};

        SeedParams toParams() {
            return new SeedParams(centerLat, centerLon, contributors, cells, routeOffsetCells,
                    avenueCount, avenueOffsetCells, samplesPerHour, hours);
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getCenterLat() { return centerLat; }
        public void setCenterLat(double centerLat) { this.centerLat = centerLat; }
        public double getCenterLon() { return centerLon; }
        public void setCenterLon(double centerLon) { this.centerLon = centerLon; }
        public int getContributors() { return contributors; }
        public void setContributors(int contributors) { this.contributors = contributors; }
        public int getCells() { return cells; }
        public void setCells(int cells) { this.cells = cells; }
        public int getRouteOffsetCells() { return routeOffsetCells; }
        public void setRouteOffsetCells(int routeOffsetCells) { this.routeOffsetCells = routeOffsetCells; }
        public int getAvenueCount() { return avenueCount; }
        public void setAvenueCount(int avenueCount) { this.avenueCount = avenueCount; }
        public int getAvenueOffsetCells() { return avenueOffsetCells; }
        public void setAvenueOffsetCells(int avenueOffsetCells) { this.avenueOffsetCells = avenueOffsetCells; }
        public int getSamplesPerHour() { return samplesPerHour; }
        public void setSamplesPerHour(int samplesPerHour) { this.samplesPerHour = samplesPerHour; }
        public int[] getHours() { return hours; }
        public void setHours(int[] hours) { this.hours = hours; }
    }
}
