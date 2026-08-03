package io.streetsense.backend.web;

import io.streetsense.backend.repository.ReadingRepository;
import io.streetsense.backend.session.SessionDebrief;
import io.streetsense.backend.session.SessionSummariser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The debrief the app opens when you stop recording, and the history it
 * compares against.
 *
 * <p>Note what is <em>not</em> here: any route geometry. The backend has never
 * held a coordinate (see {@code domain/DecodedReading}), so the session map is
 * drawn on the phone from its own local trace, and this endpoint supplies only
 * what the phone cannot work out for itself — dose, the worst stretch, and the
 * classified events.
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final ReadingRepository repository;
    private final SessionSummariser summariser;

    public SessionController(ReadingRepository repository, SessionSummariser summariser) {
        this.repository = repository;
        this.summariser = summariser;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDebrief> debrief(@PathVariable String sessionId) {
        var readings = repository.forSession(sessionId);
        if (readings.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(summariser.summarise(readings));
    }

    @GetMapping
    public List<SessionDebrief> recent(@RequestParam(defaultValue = "20") int limit) {
        return repository.recentSessionIds(limit).stream()
                .map(repository::forSession)
                .filter(readings -> !readings.isEmpty())
                .map(summariser::summarise)
                .toList();
    }
}
