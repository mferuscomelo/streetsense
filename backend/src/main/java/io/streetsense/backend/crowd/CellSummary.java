package io.streetsense.backend.crowd;

import io.streetsense.backend.domain.GridCell;

/**
 * What the crowd knows about one block, and how well it knows it.
 *
 * <p>The confidence fields are not decoration. Four hundred readings from one
 * person walking the same street every morning is not the same evidence as
 * four hundred from twelve people, and a map that renders both the same colour
 * is lying by omission. Every surface that shows a conclusion from this record
 * must show {@link #confidence()} alongside it.
 *
 * <p>{@code seededContributorCount} carries the same obligation one level
 * further: contributors generated to demonstrate the merge are counted, and
 * counted separately, exactly as {@code FLAG_MOCK_DATA} does for synthetic
 * readings. Nothing here can present generated evidence as measured.
 */
public record CellSummary(
        GridCell cell,
        int sampleCount,
        int contributorCount,
        int seededContributorCount,
        double meanPm2_5,
        double meanNoiseDb,
        int cleanestHour,
        int quietestHour,
        boolean mock) {

    /** Fewest readings before this cell is willing to claim it knows anything. */
    static final int MIN_SAMPLES = 5;

    public enum Confidence {
        /** Not enough readings to say anything. */
        NO_DATA,
        /** Enough readings, but all from one person — one routine, one route, one time of day. */
        SINGLE_CONTRIBUTOR,
        /** Independent agreement from more than one contributor. */
        CORROBORATED
    }

    public boolean hasEnoughEvidence() {
        return sampleCount >= MIN_SAMPLES;
    }

    public boolean hasSeededData() {
        return seededContributorCount > 0;
    }

    public Confidence confidence() {
        if (!hasEnoughEvidence()) {
            return Confidence.NO_DATA;
        }
        return contributorCount > 1 ? Confidence.CORROBORATED : Confidence.SINGLE_CONTRIBUTOR;
    }
}
