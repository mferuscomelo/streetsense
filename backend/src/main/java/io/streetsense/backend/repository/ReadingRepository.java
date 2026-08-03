package io.streetsense.backend.repository;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
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

    /** Most recent readings for a single grid cell, oldest first, capped at limit. */
    List<DecodedReading> recentForCell(GridCell cell, int limit);

    /** Most recent stored readings across all cells, newest first. */
    List<StoredReading> recent(int limit);
}
