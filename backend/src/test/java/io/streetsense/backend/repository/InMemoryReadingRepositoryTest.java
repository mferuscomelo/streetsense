package io.streetsense.backend.repository;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.wire.DecodedPacket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryReadingRepositoryTest {

    private static final GridCell CELL = GridCell.of(49.0069, 8.4037);

    private static DecodedReading reading(String sessionId, int hourOfDay) {
        DecodedPacket packet = new DecodedPacket(1, true, 0, 10, 15, 20, 25, 120, 21.0, 50.0, 55.0);
        return new DecodedReading(packet, CELL, hourOfDay, sessionId, "contributor-a",
                Activity.RUN, Instant.now());
    }

    @Test
    void readingsAreRetrievableBySession() {
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        repository.save(reading("morning-run", 7));
        repository.save(reading("morning-run", 7));
        repository.save(reading("evening-walk", 18));

        assertEquals(2, repository.forSession("morning-run").size());
        assertEquals(1, repository.forSession("evening-walk").size());
        assertEquals(0, repository.forSession("never-happened").size());
    }

    @Test
    void recentSessionIdsAreNewestFirst() {
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        repository.save(reading("first", 7));
        repository.save(reading("second", 12));
        repository.save(reading("third", 18));

        assertEquals(List.of("third", "second", "first"), repository.recentSessionIds(10));
        assertEquals(List.of("third"), repository.recentSessionIds(1));
    }

    @Test
    void theSameCellAtDifferentHoursIsTrackedSeparately() {
        // The repository is what feeds the rolling baseline, so hour-keyed
        // history has to hold here too — not just in the baseline's own cache.
        InMemoryReadingRepository repository = new InMemoryReadingRepository();
        DecodedReading morning = reading("s", 7);
        DecodedReading evening = reading("s", 18);
        repository.save(morning);
        repository.save(evening);

        assertEquals(1, repository.recentForKey(morning.key(), 10).size());
        assertEquals(1, repository.recentForKey(evening.key(), 10).size());
    }
}
