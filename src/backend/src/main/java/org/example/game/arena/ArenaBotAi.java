package org.example.game.arena;

import org.example.game.Archetype;
import org.example.game.Side;
import org.example.game.arena.ArenaPhysics.Body;
import org.example.game.arena.ArenaPhysics.Input;

/**
 * KI-Gegner im Arena-Modus, je nach Archetyp:
 * <ul>
 *   <li>Angriff jagt den Gegner und dasht aus kurzer Distanz.</li>
 *   <li>Verteidigung hält die Mitte und kontert, wenn der Gegner anrauscht.</li>
 *   <li>Ausdauer kreist außen, weicht aus und spart Spin.</li>
 *   <li>Balance wechselt zwischen Jagen und Kreisen.</li>
 * </ul>
 * Wer beim Spin hinten liegt, greift an. Ult, sobald sie bereit ist und die Lage passt.
 */
public final class ArenaBotAi {

    /** Reaktionszeit in ms für normale KI-Gegner */
    public static final int DEFAULT_REACTION_MS = 160;

    private final Side side;
    private final Archetype archetype;
    private final Mulberry32 rng;
    /** höher = langsamer, menschlicher */
    private final int reactionMs;
    private double nextThink;
    private double wobble;
    private Input input = Input.NONE;

    public ArenaBotAi(Side side, Archetype archetype, long seed, int reactionMs) {
        this.side = side;
        this.archetype = archetype;
        this.rng = new Mulberry32(seed);
        this.reactionMs = reactionMs;
    }

    public Input think(ArenaPhysics sim) {
        double tMs = sim.t * 1000;
        if (tMs < nextThink) return input.withoutPresses();
        nextThink = tMs + reactionMs * (0.7 + 0.6 * rng.next());
        wobble += (rng.next() - 0.5) * 0.6;

        Body me = sim.body(side);
        Body them = sim.body(side == Side.A ? Side.B : Side.A);
        double dx = them.x - me.x;
        double dz = them.z - me.z;
        double dist = nonZero(Math.hypot(dx, dz));
        double tx = dx / dist;
        double tz = dz / dist;
        double play = sim.arena.playRadius();
        double r = nonZero(Math.hypot(me.x, me.z));
        // Tangente um die Mitte (für Kreisen)
        double cx = -me.z / r;
        double cz = me.x / r;
        // Gegner kommt auf mich zu?
        double closing = -((them.vx - me.vx) * tx + (them.vz - me.vz) * tz);

        double x = 0;
        double z = 0;
        boolean dash = false;
        Archetype mode = archetype;
        if (mode == Archetype.BALANCE) mode = Math.sin(tMs / 3000 + wobble) > 0 ? Archetype.ATTACK : Archetype.STAMINA;
        // wer beim Spin hinten liegt, muss angreifen, sonst verliert er nach Zeit
        if (mode != Archetype.ATTACK && me.spin < them.spin - 0.08 && tMs > 8000) mode = Archetype.ATTACK;

        switch (mode) {
            case ATTACK -> {
                // Lenken statt Ziehen: Wunschgeschwindigkeit auf den vorausberechneten Treffpunkt minus eigene
                // Geschwindigkeit. Sonst wirkt der Schub wie eine Fliehkraft und man kreist endlos umeinander.
                double lead = Math.min(0.6, dist / 8);
                double px = them.x + them.vx * lead - me.x;
                double pz = them.z + them.vz * lead - me.z;
                double pd = nonZero(Math.hypot(px, pz));
                x = px / pd * 6 - me.vx;
                z = pz / pd * 6 - me.vz;
                double aim = (me.vx * tx + me.vz * tz) / nonZero(Math.hypot(me.vx, me.vz));
                dash = dist < 2.2 && closing > 0.5 && aim > 0.8 && rng.next() < 0.7;
                if (dash) {
                    x = px;
                    z = pz;
                }
            }
            case DEFENSE -> {
                // zur Mitte, leicht dem Gegner zugewandt; anrauschenden Gegner kontern
                x = -me.x / play + 0.3 * tx;
                z = -me.z / play + 0.3 * tz;
                dash = closing > 3 && dist < 2.6 && rng.next() < 0.6;
            }
            default -> {
                // außen kreisen, Abstand halten
                double want = play * 0.6;
                double radial = (want - r) / play;
                x = cx + me.x / r * radial * 2 - tx * (dist < 2.5 ? 0.8 : 0);
                z = cz + me.z / r * radial * 2 - tz * (dist < 2.5 ? 0.8 : 0);
                dash = dist < 1.8 && closing > 2 && rng.next() < 0.4;
                if (dash) {
                    // weg vom Gegner
                    x = -tx;
                    z = -tz;
                }
            }
        }
        // nicht selbst in eine Tasche rauschen
        if (r > play * 0.85) {
            x -= me.x / r * 0.8;
            z -= me.z / r * 0.8;
        }
        double len = nonZero(Math.hypot(x, z));
        boolean ult = sim.isUltReady(side) && (archetype != Archetype.ATTACK || dist < 4);
        input = new Input(x / len, z / len, dash, ult);
        return input;
    }

    private static double nonZero(double v) {
        return v == 0 ? 1 : v;
    }
}
