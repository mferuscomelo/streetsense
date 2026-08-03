package io.streetsense.backend.web;

import java.time.Instant;
import java.util.Map;

/** Parsed, normalized form of the raw JSON body posted to /api/v1/readings. */
record IngestRequest(double lat, double lon, Instant capturedAt, String rawPacketBase64) {

    static IngestRequest from(Map<String, Object> body) {
        return new IngestRequest(
                LenientJson.asDouble(body.get("lat")),
                LenientJson.asDouble(body.get("lon")),
                LenientJson.asInstant(body.get("capturedAt")),
                LenientJson.asString(body.get("rawPacket")));
    }
}
