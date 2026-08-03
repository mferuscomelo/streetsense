package io.streetsense.backend.repository;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.CellKey;
import io.streetsense.backend.domain.Verdict;

import java.util.List;

/**
 * In-memory now; Postgres/PostGIS lands next slice behind this same
 * interface — no API/controller change required when it does.
 */
public interface ReadingRepository {

    StoredReading save(DecodedReading reading);

    /** Attaches a verdict computed after save() returned (see IngestService). */
    void attachVerdict(long id, Verdict verdict);

    /**
     * Most recent readings for one cell at one hour of day, oldest first,
     * capped at limit. Keyed by hour, not just place: see {@link CellKey}.
     */
    List<DecodedReading> recentForKey(CellKey key, int limit);

    /** Most recent stored readings across all cells, newest first. */
    List<StoredReading> recent(int limit);

    /** Every stored reading belonging to one session, oldest first. */
    List<StoredReading> forSession(String sessionId);

    /** Ids of the most recently active sessions, newest first. */
    List<String> recentSessionIds(int limit);

    /** Every (cell, hour) key anyone has contributed a reading to. */
    java.util.Set<CellKey> knownKeys();
}
