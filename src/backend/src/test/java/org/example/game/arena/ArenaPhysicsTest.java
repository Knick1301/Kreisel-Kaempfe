package org.example.game.arena;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Archetype;
import org.example.game.FinishType;
import org.example.game.Side;
import org.example.game.UltType;
import org.example.game.arena.ArenaPhysics.EventType;
import org.example.game.arena.ArenaPhysics.FighterSetup;
import org.example.game.arena.ArenaPhysics.Input;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arena-Physik auf dem Server: Bewegung, Stöße, Ring-Out, Dash, Determinismus, KI gegen KI. */
class ArenaPhysicsTest {

    static final BeybladeStats ATTACK = new BeybladeStats(7, 4, 7, 7, 76, 2);
    static final BeybladeStats DEFENSE = new BeybladeStats(2, 11, 8, 3, 84, 4);
    static final BeybladeStats STAMINA = new BeybladeStats(2, 4, 15, 4, 73, 2);
    static final ArenaGeometry STANDARD = ArenaGeometry.of(Arena.STANDARD);

    static FighterSetup setup(BeybladeStats s) {
        UltType ult = s == ATTACK ? UltType.SMASH : s == DEFENSE ? UltType.SHIELD : UltType.SURGE;
        return new FighterSetup(s, ult, 0.6);
    }

    static Archetype type(BeybladeStats s) {
        return s == ATTACK ? Archetype.ATTACK : s == DEFENSE ? Archetype.DEFENSE : Archetype.STAMINA;
    }

    static ArenaPhysics aiBattle(BeybladeStats a, BeybladeStats b, long seed) {
        ArenaPhysics sim = new ArenaPhysics(STANDARD, setup(a), setup(b), seed);
        ArenaBotAi ai = new ArenaBotAi(Side.A, type(a), seed + 99, ArenaBotAi.DEFAULT_REACTION_MS);
        ArenaBotAi bi = new ArenaBotAi(Side.B, type(b), seed + 199, ArenaBotAi.DEFAULT_REACTION_MS);
        while (sim.finished() == null && sim.t * 1000 < ArenaPhysics.TIME_LIMIT_MS + 1000) {
            sim.step(ai.think(sim), bi.think(sim));
        }
        return sim;
    }

    @Test
    void zufallIstBitgenauWieImFrontend() {
        Mulberry32 r = new Mulberry32(12345);
        assertEquals(0.9797282677609473, r.next(), 1e-15);
        assertEquals(0.3067522644996643, r.next(), 1e-15);
        assertEquals(0.484205421525985, r.next(), 1e-15);
        Mulberry32 q = new Mulberry32(4_000_000_000L);
        assertEquals(0.6919068221468478, q.next(), 1e-15);
        assertEquals(0.5511944761965424, q.next(), 1e-15);
    }

    @Test
    void ohneEingabeSinktDerSpinUndManBleibtInDerBowl() {
        ArenaPhysics sim = new ArenaPhysics(STANDARD, setup(ATTACK), setup(STAMINA), 1);
        double spin0 = sim.a.spin;
        sim.b.x = 3;
        sim.b.z = 3;
        sim.advance(8000, Input.NONE, Input.NONE);
        assertTrue(sim.a.spin < spin0);
        assertTrue(Math.hypot(sim.a.x, sim.a.z) <= STANDARD.playRadius() + 1e-9);
    }

    @Test
    void zusammenstossTrenntDieKreisel() {
        ArenaPhysics sim = new ArenaPhysics(STANDARD, setup(ATTACK), setup(DEFENSE), 2);
        sim.a.x = -0.7;
        sim.a.z = 0;
        sim.a.vx = 5;
        sim.a.vz = 0;
        sim.b.x = 0.7;
        sim.b.z = 0;
        sim.b.vx = 0;
        sim.b.vz = 0;
        double spinB = sim.b.spin;
        boolean clash = false;
        for (int i = 0; i < 30 && !clash; i++) {
            sim.step(Input.NONE, Input.NONE);
            clash = sim.drain().stream().anyMatch(e -> e.type() == EventType.CLASH);
        }
        assertTrue(clash);
        assertTrue(sim.b.vx > 0);
        assertTrue(sim.a.vx < 5);
        assertTrue(sim.b.spin <= spinB);
    }

    @Test
    void ringOutNurNachTreffer() {
        assertEquals(FinishType.RING_OUT, shoot(true).finished().finish());
        assertEquals(Side.B, shoot(true).finished().winner());
        assertNull(shoot(false).finished());
    }

    private static ArenaPhysics shoot(boolean hitBefore) {
        ArenaPhysics sim = new ArenaPhysics(STANDARD, setup(STAMINA), setup(ATTACK), 3);
        sim.t = 7;
        double p = Arena.STANDARD.pockets().getFirst();
        double r = STANDARD.playRadius() - 0.05;
        sim.a.x = Math.cos(p) * r;
        sim.a.z = Math.sin(p) * r;
        sim.a.vx = Math.cos(p) * 6;
        sim.a.vz = Math.sin(p) * 6;
        sim.a.hitAt = hitBefore ? 6800 : -1_000_000_000;
        sim.b.x = -2;
        for (int i = 0; i < 20 && sim.finished() == null; i++) sim.step(Input.NONE, Input.NONE);
        return sim;
    }

    @Test
    void dashHatAbklingzeitUndKostetSpin() {
        ArenaPhysics sim = new ArenaPhysics(STANDARD, setup(ATTACK), setup(STAMINA), 4);
        Input dash = new Input(1, 0, true, false);
        double spin0 = sim.a.spin;
        sim.step(dash, Input.NONE);
        sim.step(dash, Input.NONE);
        assertEquals(1, sim.a.dashes);
        assertTrue(sim.a.spin < spin0);
        sim.advance(ArenaPhysics.DASH_COOLDOWN_MS + 20, Input.NONE, Input.NONE);
        sim.step(dash, Input.NONE);
        assertEquals(2, sim.a.dashes);
    }

    @Test
    void gleicherSeedGleicherKampf() {
        ArenaPhysics x = aiBattle(ATTACK, STAMINA, 7);
        ArenaPhysics y = aiBattle(ATTACK, STAMINA, 7);
        assertEquals(x.finished(), y.finished());
        assertEquals(x.a.hits, y.a.hits);
    }

    /** Wie im Frontend abgestimmt: Kämpfe enden, dauern sinnvoll lang, alle Finish-Arten kommen vor. */
    @Test
    void kiGegenKi() {
        BeybladeStats[] kinds = {ATTACK, DEFENSE, STAMINA};
        Map<FinishType, Integer> finishes = new EnumMap<>(FinishType.class);
        long hits = 0;
        long ms = 0;
        int total = 0;
        for (BeybladeStats a : kinds) {
            for (BeybladeStats b : kinds) {
                for (int seed = 0; seed < 12; seed++) {
                    ArenaPhysics sim = aiBattle(a, b, seed * 31L + 5);
                    assertNotNull(sim.finished());
                    finishes.merge(sim.finished().finish(), 1, Integer::sum);
                    hits += sim.a.hits + sim.b.hits;
                    ms += sim.finished().t();
                    total++;
                }
            }
        }
        double avgSec = ms / (double) total / 1000;
        assertTrue(avgSec > 8, "Durchschnitt " + avgSec + " s");
        assertTrue(hits / (double) total > 3, "Treffer pro Kampf " + hits / (double) total);
        assertTrue(finishes.size() >= 3, "Finish-Arten " + finishes);
    }
}
