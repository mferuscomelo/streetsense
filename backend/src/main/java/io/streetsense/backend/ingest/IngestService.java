package io.streetsense.backend.ingest;

import io.streetsense.backend.anomaly.AnomalyDetector;
import io.streetsense.backend.baseline.RollingBaseline;
import io.streetsense.backend.domain.CellStats;
import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.repository.StoredReading;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * One ingested reading fans out into persist / update-rolling-baseline /
 * anomaly-check as a single unit of work with one lifetime (JEP 525): if
 * any subtask fails, the whole ingest fails together rather than leaving
 * the system in a half-updated state.
 *
 * The anomaly check is compared against the baseline as it existed
 * *before* this reading (fetched synchronously, cheaply, from the
 * in-memory cache) so a reading never gets judged against a baseline it
 * has already been folded into.
 */
@Service
public class IngestService {

    private final ReadingRepository repository;
    private final RollingBaseline baseline;
    private final AnomalyDetector detector;

    public IngestService(ReadingRepository repository, RollingBaseline baseline, AnomalyDetector detector) {
        this.repository = repository;
        this.baseline = baseline;
        this.detector = detector;
    }

    public IngestResult ingest(DecodedReading reading, String nodeId) throws Exception {
        IngestContext context = new IngestContext(nodeId, UUID.randomUUID().toString());
        CellStats previousBaseline = baseline.currentBaseline(reading.cell());

        return ScopedValue.where(IngestContext.CURRENT, context).call(() -> {
            try (var scope = StructuredTaskScope.open()) {
                Subtask<StoredReading> storedTask = scope.fork(() -> repository.save(reading));
                Subtask<CellStats> statsTask = scope.fork(() -> baseline.update(reading));
                Subtask<Verdict> verdictTask = scope.fork(() -> detector.check(reading, previousBaseline));

                scope.join();

                StoredReading stored = storedTask.get();
                CellStats newBaseline = statsTask.get();
                Verdict verdict = verdictTask.get();

                repository.attachVerdict(stored.id(), verdict);

                return new IngestResult(stored, newBaseline, verdict);
            }
        });
    }
}
