package com.courier.shared.domain;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UUIDv7 generator: 48-bit Unix millisecond timestamp followed by random bits.
 *
 * <p>Random UUIDv4 primary keys scatter InnoDB inserts across the whole clustered
 * index, which fragments pages and destroys write locality on a table the size of
 * {@code shipments}. A time-ordered prefix keeps inserts appending to the right-hand
 * edge of the B-tree while remaining globally unique and non-guessable.
 *
 * <p>Within a single millisecond a monotonic counter is used for the next 12 bits so
 * that IDs generated in a tight loop stay strictly ordered.
 */
public final class TimeOrderedUuid {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(-1L);
    private static final AtomicLong SEQUENCE = new AtomicLong(0L);
    private static final long MAX_SEQUENCE = 0xFFFL; // 12 bits

    private TimeOrderedUuid() {
    }

    public static UUID generate() {
        long timestamp = System.currentTimeMillis();
        long sequence = nextSequence(timestamp);

        // 48 bits timestamp | 4 bits version (7) | 12 bits sequence
        long msb = (timestamp & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;
        msb |= (sequence & MAX_SEQUENCE);

        // 2 bits variant (10) | 62 bits randomness
        long lsb = RANDOM.nextLong();
        lsb &= 0x3FFFFFFFFFFFFFFFL;
        lsb |= 0x8000000000000000L;

        return new UUID(msb, lsb);
    }

    /**
     * Big-endian 16-byte form, matching how the JDBC driver stores a {@code BINARY(16)}
     * primary key.
     *
     * <p>Needed when a native query has to bind an id: JPQL binds a {@code UUID}
     * through the entity mapping, but native SQL has no mapping to consult and the
     * driver would otherwise send the 36-character string form, which never matches.
     *
     * @return null when {@code uuid} is null, so it can be passed straight to an
     *         optional query parameter
     */
    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * Reverse of {@link #toBytes}: turns a {@code BINARY(16)} column's raw bytes back
     * into a {@code UUID}.
     *
     * <p>Needed when a native query projects an id/FK column into an interface
     * projection — Spring Data has no {@code Converter<byte[], UUID>} registered, so a
     * projection method declared to return {@code UUID} throws {@code
     * UnsupportedOperationException} at read time instead of converting. Project the
     * column as {@code byte[]} and convert with this method explicitly.
     *
     * @return null when {@code bytes} is null, so it can be applied straight to a
     *         nullable projected column
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static long nextSequence(long timestamp) {
        long last = LAST_TIMESTAMP.get();
        if (timestamp > last && LAST_TIMESTAMP.compareAndSet(last, timestamp)) {
            SEQUENCE.set(0L);
            return 0L;
        }
        return SEQUENCE.incrementAndGet() & MAX_SEQUENCE;
    }
}
