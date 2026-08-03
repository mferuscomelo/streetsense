package io.streetsense.backend.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Lenient coercion for ingest JSON fields that legitimately arrive as
 * different JSON types across phone app versions — a JSON number with no
 * fractional part deserializes as Integer, one with a fraction as Double,
 * and some client code stringifies numbers to dodge float-precision
 * quirks. JEP 530 primitive type patterns in switch make this one
 * expression instead of a chain of instanceof checks.
 */
final class LenientJson {

    private LenientJson() {}

    static double asDouble(Object raw) {
        return switch (raw) {
            case null -> throw new IllegalArgumentException("missing numeric value");
            case int i -> i;
            case double d -> d;
            case String s -> Double.parseDouble(s);
            default -> throw new IllegalArgumentException("unsupported numeric value: " + raw);
        };
    }

    static String asString(Object raw) {
        return switch (raw) {
            case null -> throw new IllegalArgumentException("missing string value");
            case String s -> s;
            default -> throw new IllegalArgumentException("expected string, got: " + raw);
        };
    }

    static Instant asInstant(Object raw) {
        String s = asString(raw);
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid timestamp: " + s, e);
        }
    }
}
