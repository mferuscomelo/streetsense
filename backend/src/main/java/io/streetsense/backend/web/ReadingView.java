package io.streetsense.backend.web;

import io.streetsense.backend.domain.DecodedReading;
import io.streetsense.backend.domain.Verdict;
import io.streetsense.backend.repository.StoredReading;

import java.time.Instant;

public record ReadingView(
        long id,
        boolean mock,
        double pm1, double pm2_5, double pm4, double pm10,
        double vocIndex, double tempC, double humidity, double noiseDb,
        double lat, double lon,
        Instant capturedAt,
        VerdictView verdict) {

    public static ReadingView of(StoredReading stored, Verdict verdict) {
        DecodedReading r = stored.reading();
        return new ReadingView(
                stored.id(), r.mock(),
                r.pm1(), r.pm2_5(), r.pm4(), r.pm10(),
                r.vocIndex(), r.tempC(), r.humidity(), r.noiseDb(),
                r.lat(), r.lon(), r.capturedAt(),
                VerdictView.of(verdict));
    }

    public static ReadingView of(StoredReading stored) {
        return of(stored, stored.verdict());
    }
}
