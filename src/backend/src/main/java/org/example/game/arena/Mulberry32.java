package org.example.game.arena;

/**
 * Kleiner Seeded-PRNG, bitgenau wie {@code mulberry32} im Frontend ({@code src/game/prng.ts}). Liefert
 * Zahlen in [0, 1).
 */
public final class Mulberry32 {

    private int s;

    public Mulberry32(long seed) {
        this.s = (int) seed;
    }

    public double next() {
        s += 0x6d2b79f5;
        int t = s;
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return Integer.toUnsignedLong(t ^ (t >>> 14)) / 4294967296.0;
    }
}
