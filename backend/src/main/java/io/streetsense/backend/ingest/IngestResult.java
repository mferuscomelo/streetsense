package io.streetsense.backend.ingest;

import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.StoredReading;

public record IngestResult(StoredReading stored, CellStats baseline, Verdict verdict) {
}
