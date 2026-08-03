package io.streetsense.backend.domain;

/**
 * A crude fixed-size lat/lon bucket (~0.01 degrees, ~1.1km at the equator).
 * Deliberately simple — real grid sophistication (geohash precision tuned
 * per density, PostGIS-backed) lands with the Postgres/PostGIS slice; this
 * is just enough to group readings "at roughly the same place."
 */
public record GridCell(int latBucket, int lonBucket) {

    private static final double CELL_SIZE_DEGREES = 0.01;

    public static GridCell of(double lat, double lon) {
        return new GridCell(
                (int) Math.floor(lat / CELL_SIZE_DEGREES),
                (int) Math.floor(lon / CELL_SIZE_DEGREES));
    }
}
