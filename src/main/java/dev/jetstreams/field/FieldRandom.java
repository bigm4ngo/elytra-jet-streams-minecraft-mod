package dev.jetstreams.field;

/**
 * Stateless deterministic hashing helpers. The entire stream layout is derived from
 * {@code seed + salt + index} hashes, so the field needs zero disk storage and is
 * identical on server and client (given the same world seed).
 */
public final class FieldRandom {
    private FieldRandom() {}

    private static final long A = 0x9E3779B97F4A7C15L;
    private static final long B = 0xBF58476D1CE4E5B9L;
    private static final long C = 0x94D049BB133111EBL;

    /** splitmix64 finalizer. */
    public static long mix(long x) {
        long z = x + A;
        z = (z ^ (z >>> 30)) * B;
        z = (z ^ (z >>> 27)) * C;
        return z ^ (z >>> 31);
    }

    /** Three-input combination hash. */
    public static long hash(long seed, long salt, long index) {
        return mix(mix(seed ^ salt) ^ mix(index + A));
    }

    /** Hash mapped to [0, 1). */
    public static double hash01(long seed, long salt, long index) {
        return (hash(seed, salt, index) >>> 11) * 0x1.0p-53;
    }

    /** Hash mapped to [min, max). */
    public static double range01(long seed, long salt, long index, double min, double max) {
        return min + hash01(seed, salt, index) * (max - min);
    }
}
