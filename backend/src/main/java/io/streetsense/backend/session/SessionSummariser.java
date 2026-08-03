package io.streetsense.backend.session;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.StoredReading;
import io.streetsense.backend.web.VerdictView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Gatherers;

/**
 * Turns a session's stored readings into the debrief the app shows afterwards.
 *
 * <p>Two Gatherer pipelines, because they genuinely want different shapes:
 * {@link DoseGatherer} is a stateful fold where each reading's contribution
 * depends on the gap to the one before it, while the worst-stretch search is
 * a sliding window — {@code Gatherers.windowSliding} literally is that. They
 * are deliberately not merged into one pass: a single gatherer doing both
 * would have to reimplement windowing internally, trading clear code for a
 * traversal that costs nothing here.
 */
@Component
public class SessionSummariser {

    /**
     * Readings per "worst stretch" window. At the node's 1 Hz that is half a
     * minute — long enough not to be one freak reading, short enough to point
     * at a specific junction rather than a neighbourhood.
     */
    static final int SEGMENT_READINGS = 30;

    public SessionDebrief summarise(List<StoredReading> stored) {
        if (stored.isEmpty()) {
            return SessionDebrief.empty();
        }

        List<DecodedReading> readings = stored.stream()
                .map(StoredReading::reading)
                .sorted(Comparator.comparing(DecodedReading::capturedAt))
                .toList();

        DecodedReading first = readings.getFirst();
        DecodedReading last = readings.getLast();

        double dose = readings.stream()
                .gather(DoseGatherer.micrograms())
                .reduce((running, next) -> next)
                .orElse(0.0);

        double meanPm = readings.stream().mapToDouble(DecodedReading::pm2_5).average().orElse(0);
        double meanNoise = readings.stream().mapToDouble(DecodedReading::noiseDb).average().orElse(0);

        return new SessionDebrief(
                first.sessionId(),
                first.contributorId(),
                first.activity(),
                first.capturedAt(),
                last.capturedAt(),
                Duration.between(first.capturedAt(), last.capturedAt()).toSeconds(),
                readings.size(),
                dose,
                meanPm,
                meanNoise,
                worstSegment(readings),
                events(stored));
    }

    private static SessionDebrief.Segment worstSegment(List<DecodedReading> readings) {
        int window = Math.min(SEGMENT_READINGS, readings.size());

        return readings.stream()
                .gather(Gatherers.windowSliding(window))
                .map(w -> new SessionDebrief.Segment(
                        w.getFirst().capturedAt(),
                        w.getLast().capturedAt(),
                        w.stream().mapToDouble(DecodedReading::pm2_5).average().orElse(0)))
                .max(Comparator.comparingDouble(SessionDebrief.Segment::meanPm2_5))
                .orElseGet(() -> SessionDebrief.Segment.none(readings.getFirst().capturedAt()));
    }

    /**
     * The classified events along the route, skipping the long stretches where
     * nothing happened. Reuses {@link VerdictView} so the sentence shown in the
     * debrief is the same one the live screen showed at the time — one place
     * decides how a verdict is worded.
     */
    private static List<SessionDebrief.EventSummary> events(List<StoredReading> stored) {
        return stored.stream()
                .filter(s -> s.verdict() != null && !(s.verdict() instanceof Verdict.Normal))
                .sorted(Comparator.comparing(s -> s.reading().capturedAt()))
                .map(s -> {
                    VerdictView view = VerdictView.of(s.verdict());
                    return new SessionDebrief.EventSummary(
                            s.reading().capturedAt(), view.type(), view.headline(), view.explanation());
                })
                .toList();
    }
}
