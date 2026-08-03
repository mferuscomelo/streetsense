package io.streetsense.backend.repository;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Verdict;

import java.time.Instant;

public record StoredReading(
        long id,
        DecodedReading reading,
        Verdict verdict,
        Instant storedAt) {
}
