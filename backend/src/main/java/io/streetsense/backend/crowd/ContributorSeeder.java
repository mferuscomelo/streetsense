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

    @Bean
    @ConfigurationProperties("streetsense.seed")
    public SeedSettings seedSettings() {
        return new SeedSettings();
    }

    @Bean
    public ApplicationRunner seedRunner(SeedSettings settings, IngestService ingestService) {
        return args -> {
            if (!settings.isEnabled()) {
                return;
            }
            int written = seed(settings, ingestService);
            log.warn("Seeded {} synthetic readings from {} generated contributors. "
                            + "Every one is flagged mock and prefixed '{}' — see docs/honest-caveats.md.",
                    written, settings.getContributors(), CrowdService.SEEDED_PREFIX);
        };
    }

    private int seed(SeedSettings settings, IngestService ingestService) throws Exception {
        // Fixed seed: the demo should show the same city every time it is run,
        // so a recorded walkthrough matches what a judge sees on their own machine.
        Random random = new Random(20260816L);
        Instant base = Instant.now().minus(Duration.ofDays(7));
        int written = 0;

        for (int c = 0; c < settings.getContributors(); c++) {
            String contributorId = CrowdService.SEEDED_PREFIX + "contributor-" + c;

            // Contributors walk OVERLAPPING stretches of one shared street,
            // each starting a couple of blocks further along than the last.
            // The overlap is the entire point: it produces cells that several
            // people have independently sampled (CORROBORATED) next to cells
            // only one person has (SINGLE_CONTRIBUTOR), which is what makes
            // the confidence distinction visible instead of theoretical.
            //
            // An earlier version gave each contributor their own parallel
            // column, so no two ever shared a cell and every cell in the city
            // reported exactly one contributor — the crowd layer rendered
            // perfectly and demonstrated nothing.
            int startCell = c * settings.getRouteOffsetCells();

            for (int step = 0; step < settings.getCells(); step++) {
                int cellIndex = startCell + step;
                GridCell cell = GridCell.of(
                        settings.getCenterLat() + cellIndex * 0.001,
                        settings.getCenterLon());

                for (int hour : settings.getHours()) {
                    // Traffic-shaped, so the cleanest/quietest-hour
                    // recommendation has a real pattern to find rather than
                    // picking whichever hour noise happened to favour.
                    double rushFactor = settings.rushFactorFor(hour);

                    for (int sample = 0; sample < settings.getSamplesPerHour(); sample++) {
                        double pm25 = 9.0 * rushFactor + random.nextGaussian() * 2.0;
                        double voc = 110.0 * rushFactor + random.nextGaussian() * 12.0;
                        double noise = 48.0 + (rushFactor - 1) * 9.0 + random.nextGaussian() * 2.5;

                        DecodedPacket packet = new DecodedPacket(
                                1, true, sample,
                                pm25 * 0.6, Math.max(0, pm25), pm25 * 1.2, pm25 * 1.5,
                                Math.max(0, voc), 21.0, 52.0, Math.max(0, noise));

                        DecodedReading reading = new DecodedReading(
                                packet, cell, hour,
                                contributorId + "-session-" + cellIndex + "-" + hour,
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

    /** Bound from {@code streetsense.seed.*}. Disabled unless explicitly enabled. */
    public static class SeedSettings {
        private boolean enabled = false;
        private double centerLat = 49.0069;
        private double centerLon = 8.4037;
        private int contributors = 4;
        private int cells = 10;
        /** How far along the shared street each contributor starts. Smaller = more overlap. */
        private int routeOffsetCells = 2;
        private int samplesPerHour = 8;
        private int[] hours = {6, 7, 8, 12, 17, 18, 21};

        /**
         * A plausible diurnal traffic curve. Deliberately not flat outside
         * rush hour: if every non-rush hour shared one factor, "cleanest hour"
         * would be decided by random noise and the recommendation would be
         * meaningless even though it rendered fine.
         */
        double rushFactorFor(int hour) {
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

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getCenterLat() { return centerLat; }
        public void setCenterLat(double centerLat) { this.centerLat = centerLat; }
        public double getCenterLon() { return centerLon; }
        public void setCenterLon(double centerLon) { this.centerLon = centerLon; }
        public int getContributors() { return contributors; }
        public void setContributors(int contributors) { this.contributors = contributors; }
        public int getCells() { return cells; }
        public int getRouteOffsetCells() { return routeOffsetCells; }
        public void setRouteOffsetCells(int routeOffsetCells) { this.routeOffsetCells = routeOffsetCells; }
        public void setCells(int cells) { this.cells = cells; }
        public int getSamplesPerHour() { return samplesPerHour; }
        public void setSamplesPerHour(int samplesPerHour) { this.samplesPerHour = samplesPerHour; }
        public int[] getHours() { return hours; }
        public void setHours(int[] hours) { this.hours = hours; }
    }
}
