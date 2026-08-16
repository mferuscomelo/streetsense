package io.streetsense.backend.repository;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.CellKey;
import io.streetsense.backend.domain.Verdict;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Bounded in-memory ring buffer. Simple `synchronized` methods rather than
 * lock-free structures — demo-scale throughput doesn't need more, and this
 * whole class is replaced by a Postgres/PostGIS-backed implementation
 * behind the same {@link ReadingRepository} interface in the next slice.
 */
@Repository
public final class InMemoryReadingRepository implements ReadingRepository {

    private static final int MAX_TOTAL = 10_000;
    private static final int MAX_PER_KEY = 500;

    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, StoredReading> byId = new HashMap<>();
    private final Deque<Long> insertionOrder = new ArrayDeque<>();
    private final Map<CellKey, Deque<DecodedReading>> byKey = new HashMap<>();
    // Insertion-ordered so recentSessionIds can report newest-first without
    // scanning every stored reading.
    private final Map<String, List<Long>> bySession = new LinkedHashMap<>();

    @Override
    public synchronized StoredReading save(DecodedReading reading) {
        long id = nextId.getAndIncrement();
        StoredReading stored = new StoredReading(id, reading, null, Instant.now());

        byId.put(id, stored);
        insertionOrder.addLast(id);
        if (insertionOrder.size() > MAX_TOTAL) {
            byId.remove(insertionOrder.removeFirst());
        }

        bySession.computeIfAbsent(reading.sessionId(), s -> new ArrayList<>()).add(id);

        Deque<DecodedReading> history = byKey.computeIfAbsent(reading.key(), k -> new ArrayDeque<>());
        history.addLast(reading);
        if (history.size() > MAX_PER_KEY) {
            history.removeFirst();
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
    public synchronized List<DecodedReading> recentForKey(CellKey key, int limit) {
        Deque<DecodedReading> history = byKey.get(key);
        if (history == null) return List.of();
        List<DecodedReading> all = new ArrayList<>(history);
        int from = Math.max(0, all.size() - limit);
        return List.copyOf(all.subList(from, all.size()));
    }

    @Override
    public synchronized java.util.Set<CellKey> knownKeys() {
        return java.util.Set.copyOf(byKey.keySet());
    }

    @Override
    public synchronized List<StoredReading> forSession(String sessionId) {
        List<Long> ids = bySession.get(sessionId);
        if (ids == null) return List.of();
        // Ids whose readings have already been evicted by the ring buffer are
        // skipped rather than returned as nulls — a long-running demo should
        // show a shorter session, not crash the debrief.
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public synchronized List<String> recentSessionIds(int limit) {
        List<String> all = new ArrayList<>(bySession.keySet());
        java.util.Collections.reverse(all);
        return List.copyOf(all.subList(0, Math.min(limit, all.size())));
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

    @Override
    public synchronized void evictWhere(Predicate<String> contributorIdMatches) {
        Set<Long> toRemove = byId.entrySet().stream()
                .filter(e -> contributorIdMatches.test(e.getValue().reading().contributorId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (toRemove.isEmpty()) {
            return;
        }

        toRemove.forEach(byId::remove);
        insertionOrder.removeIf(toRemove::contains);
        bySession.values().forEach(ids -> ids.removeIf(toRemove::contains));
        bySession.values().removeIf(List::isEmpty);
        byKey.values().forEach(history -> history.removeIf(r -> contributorIdMatches.test(r.contributorId())));
        byKey.values().removeIf(Deque::isEmpty);
    }
}
