package io.streetsense.backend.domain;

/**
 * The key everything in the crowd layer is grouped by: a place and a time of
 * day.
 *
 * <p>A cell alone is not enough. The same street is a different place at
 * 07:00 and at 18:00, and averaging the two produces a baseline that
 * describes neither — a reading that is perfectly normal for rush hour looks
 * like an anomaly against an all-day mean, and a genuinely quiet morning
 * looks unremarkable. Keying by hour is also the only thing that makes
 * "the quietest hour on this block" answerable at all.
 *
 * <p>{@code hourOfDay} is the contributor's <em>local</em> hour, sent by the
 * phone rather than derived from the capture instant on the server. A UTC
 * hour would smear rush hour across timezones the moment there is more than
 * one contributor city.
 */
public record CellKey(GridCell cell, int hourOfDay) {

    public CellKey {
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay must be 0-23, got " + hourOfDay);
        }
    }

    public static CellKey of(DecodedReading reading) {
        return new CellKey(reading.cell(), reading.hourOfDay());
    }
}
