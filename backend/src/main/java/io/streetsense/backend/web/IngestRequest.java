package io.streetsense.backend.web;

import io.streetsense.backend.domain.Activity;
import io.streetsense.backend.domain.GridCell;

import java.time.Instant;
import java.util.Map;

/**
 * Parsed, normalized form of the raw JSON body posted to /api/v1/readings.
 *
 * <p>Takes grid buckets, not coordinates. The phone does the snapping — see
 * {@link io.streetsense.backend.domain.DecodedReading} for why the precise
 * fix never crosses the wire. A client posting {@code lat}/{@code lon} is an
 * old build; those keys are ignored rather than honoured, so an out-of-date
 * app fails to submit instead of quietly reinstating trace upload.
 */
record IngestRequest(
        int latBucket,
        int lonBucket,
        int hourOfDay,
        String sessionId,
        String contributorId,
        Activity activity,
        Instant capturedAt,
        String rawPacketBase64) {

    static IngestRequest from(Map<String, Object> body) {
        return new IngestRequest(
                LenientJson.asInt(body.get("latBucket")),
                LenientJson.asInt(body.get("lonBucket")),
                LenientJson.asInt(body.get("hourOfDay")),
                LenientJson.asString(body.get("sessionId")),
                LenientJson.asString(body.get("contributorId")),
                LenientJson.asEnum(body.get("activity"), Activity.class),
                LenientJson.asInstant(body.get("capturedAt")),
                LenientJson.asString(body.get("rawPacket")));
    }

    GridCell cell() {
        return new GridCell(latBucket, lonBucket);
    }
}
