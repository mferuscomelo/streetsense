package io.streetsense.backend.ingest;

/**
 * Bound once per request in {@link IngestService} and read by every forked
 * subtask (persist / baseline-update / anomaly-check) without threading it
 * through each method signature. JEP 525 specifies that structured
 * concurrency subtasks inherit ScopedValue bindings from the thread that
 * forked them — that inheritance is what makes this pairing deliberate
 * rather than two unrelated features used side by side.
 */
public record IngestContext(String nodeId, String correlationId) {
    public static final ScopedValue<IngestContext> CURRENT = ScopedValue.newInstance();
}
