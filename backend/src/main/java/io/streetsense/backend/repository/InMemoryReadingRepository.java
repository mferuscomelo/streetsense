package io.streetsense.backend.repository;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.GridCell;
import io.streetsense.backend.domain.Verdict;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded in-memory ring buffer. Simple `synchronized` methods rather than
 * lock-free structures — demo-scale throughput doesn't need more, and this
 * whole class is replaced by a Postgres/PostGIS-backed implementation
 * behind the same {@link ReadingRepository} interface in the next slice.
 */
@Repository
public final class InMemoryReadingRepository implements ReadingRepository {

    private static final int MAX_TOTAL = 10_000;
    private static final int MAX_PER_CELL = 500;

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, StoredReading> byId = new HashMap<>();
    private final Deque<Long> insertionOrder = new ArrayDeque<>();
    private final Map<GridCell, Deque<DecodedReading>> byCell = new HashMap<>();

    @Override
    public synchronized StoredReading save(DecodedReading reading) {
        long id = nextId.getAndIncrement();
        StoredReading stored = new StoredReading(id, reading, null, Instant.now());

        byId.put(id, stored);
        insertionOrder.addLast(id);
        if (insertionOrder.size() > MAX_TOTAL) {
            byId.remove(insertionOrder.removeFirst());
        }

        Deque<DecodedReading> cellHistory = byCell.computeIfAbsent(reading.cell(), c -> new ArrayDeque<>());
        cellHistory.addLast(reading);
        if (cellHistory.size() > MAX_PER_CELL) {
            cellHistory.removeFirst();
        }

        return stored;
    }

    @Override
    public synchronized void attachVerdict(long id, Verdict verdict) {
        StoredReading existing = byId.get(id);
        if (existing != null) {
            byId.put(id, new StoredReading(existing.id(), existing.reading(), verdict, existing.storedAt()));
        }
    }

    @Override
    public synchronized List<DecodedReading> recentForCell(GridCell cell, int limit) {
        Deque<DecodedReading> history = byCell.get(cell);
        if (history == null) return List.of();
        List<DecodedReading> all = new ArrayList<>(history);
        int from = Math.max(0, all.size() - limit);
        return List.copyOf(all.subList(from, all.size()));
    }

    @Override
    public synchronized List<StoredReading> recent(int limit) {
        List<StoredReading> result = new ArrayList<>(Math.min(limit, insertionOrder.size()));
        var iterator = insertionOrder.descendingIterator();
        while (iterator.hasNext() && result.size() < limit) {
            StoredReading r = byId.get(iterator.next());
            if (r != null) result.add(r);
        }
        return List.copyOf(result);
    }
}
