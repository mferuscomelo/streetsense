package io.streetsense.backend.wire;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Decodes the raw 20-byte StreetSensePacket via the Foreign Function &amp;
 * Memory API. A packed little-endian C struct crossing into Java is the
 * textbook FFM use case: this layout mirrors
 * firmware/lib/streetsense_packet/packet.h field-for-field, so the two stay
 * legible against each other, and there are no hand-computed byte offsets
 * to get wrong.
 *
 * The app forwards this packet verbatim rather than re-deriving it (see
 * app/.../ble/SensorPacket.java), which makes this class the single
 * version-authority for the wire format.
 */
public final class PacketLayout {

    public static final int PACKET_LENGTH = 20;
    private static final int FLAG_MOCK_DATA = 0x01;

    // Each multi-byte field gets its own withByteAlignment(1) override — a
    // struct-level withByteAlignment(1) alone is rejected (IllegalArgumentException:
    // "Invalid alignment constraint") because a group layout's alignment can't be
    // set below the natural alignment of its strictest member. Overriding every
    // field individually is what actually reproduces the firmware's #pragma pack(1).
    private static final ValueLayout.OfShort PACKED_SHORT = JAVA_SHORT.withByteAlignment(1);

    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            JAVA_BYTE.withName("version"),
            JAVA_BYTE.withName("flags"),
            PACKED_SHORT.withName("seq"),
            PACKED_SHORT.withName("pm1"),
            PACKED_SHORT.withName("pm2_5"),
            PACKED_SHORT.withName("pm4"),
            PACKED_SHORT.withName("pm10"),
            PACKED_SHORT.withName("voc_index"),
            PACKED_SHORT.withName("temp_c"),
            PACKED_SHORT.withName("humidity"),
            PACKED_SHORT.withName("noise_db")
    );

    private static final VarHandle VERSION = LAYOUT.varHandle(PathElement.groupElement("version"));
    private static final VarHandle FLAGS = LAYOUT.varHandle(PathElement.groupElement("flags"));
    private static final VarHandle SEQ = LAYOUT.varHandle(PathElement.groupElement("seq"));
    private static final VarHandle PM1 = LAYOUT.varHandle(PathElement.groupElement("pm1"));
    private static final VarHandle PM2_5 = LAYOUT.varHandle(PathElement.groupElement("pm2_5"));
    private static final VarHandle PM4 = LAYOUT.varHandle(PathElement.groupElement("pm4"));
    private static final VarHandle PM10 = LAYOUT.varHandle(PathElement.groupElement("pm10"));
    private static final VarHandle VOC_INDEX = LAYOUT.varHandle(PathElement.groupElement("voc_index"));
    private static final VarHandle TEMP_C = LAYOUT.varHandle(PathElement.groupElement("temp_c"));
    private static final VarHandle HUMIDITY = LAYOUT.varHandle(PathElement.groupElement("humidity"));
    private static final VarHandle NOISE_DB = LAYOUT.varHandle(PathElement.groupElement("noise_db"));

    private PacketLayout() {}

    public static DecodedPacket decode(byte[] raw) {
        if (raw.length != PACKET_LENGTH) {
            throw new IllegalArgumentException(
                    "expected " + PACKET_LENGTH + "-byte packet, got " + raw.length);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(LAYOUT);
            MemorySegment.copy(raw, 0, segment, JAVA_BYTE, 0, raw.length);

            int version = Byte.toUnsignedInt((byte) VERSION.get(segment, 0L));
            int flags = Byte.toUnsignedInt((byte) FLAGS.get(segment, 0L));
            int seq = Short.toUnsignedInt((short) SEQ.get(segment, 0L));
            int pm1Raw = Short.toUnsignedInt((short) PM1.get(segment, 0L));
            int pm2_5Raw = Short.toUnsignedInt((short) PM2_5.get(segment, 0L));
            int pm4Raw = Short.toUnsignedInt((short) PM4.get(segment, 0L));
            int pm10Raw = Short.toUnsignedInt((short) PM10.get(segment, 0L));
            int vocRaw = Short.toUnsignedInt((short) VOC_INDEX.get(segment, 0L));
            short tempRaw = (short) TEMP_C.get(segment, 0L); // signed
            int humidityRaw = Short.toUnsignedInt((short) HUMIDITY.get(segment, 0L));
            int noiseRaw = Short.toUnsignedInt((short) NOISE_DB.get(segment, 0L));

            return new DecodedPacket(
                    version,
                    (flags & FLAG_MOCK_DATA) != 0,
                    seq,
                    pm1Raw / 10.0,
                    pm2_5Raw / 10.0,
                    pm4Raw / 10.0,
                    pm10Raw / 10.0,
                    vocRaw / 10.0,
                    tempRaw / 100.0,
                    humidityRaw / 100.0,
                    noiseRaw / 10.0);
        }
    }
}
