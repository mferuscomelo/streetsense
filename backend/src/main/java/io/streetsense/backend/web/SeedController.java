package io.streetsense.backend.web;

import io.streetsense.backend.crowd.ContributorSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the dashboard re-seed the demo neighborhood around wherever the
 * visitor's browser resolves as "here", instead of shipping with one
 * hardcoded city baked in.
 *
 * <p>A no-op (403) unless {@code streetsense.seed.enabled} is explicitly
 * true — the same gate {@link ContributorSeeder}'s boot-time seeding uses,
 * so a real deployment can never be nudged into fabricating data by a stray
 * request. {@code /status} lets the dashboard check that gate before it
 * ever prompts a visitor for their location.
 */
@RestController
@RequestMapping("/api/v1/seed")
public class SeedController {

    private static final Logger log = LoggerFactory.getLogger(SeedController.class);

    private final ContributorSeeder seeder;
    private final ContributorSeeder.SeedSettings settings;

    public SeedController(ContributorSeeder seeder, ContributorSeeder.SeedSettings settings) {
        this.seeder = seeder;
        this.settings = settings;
    }

    @GetMapping("/status")
    public SeedStatus status() {
        return new SeedStatus(settings.isEnabled());
    }

    @PostMapping
    public ResponseEntity<SeedResult> reseed(@RequestParam double lat, @RequestParam double lon) throws Exception {
        if (!settings.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new SeedResult(0, "streetsense.seed.enabled is false — run with scripts/run-backend.sh --seed"));
        }
        int written = seeder.seedAround(lat, lon);
        log.info("Re-seeded via dashboard geolocation: lat={} lon={} written={}", lat, lon, written);
        return ResponseEntity.ok(new SeedResult(written, "ok"));
    }

    public record SeedStatus(boolean enabled) {}

    public record SeedResult(int written, String message) {}
}
