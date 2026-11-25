package org.example.game.arena;

import org.example.dtos.BeybladeStats;
import org.example.game.FinishType;
import org.example.game.Side;
import org.example.game.UltType;

import java.util.ArrayList;
import java.util.List;

/**
 * Physik des Arena-Modus: zwei Kreisel als Kreise in der Bowl, live gesteuert. Läuft auf dem Server, die
 * Browser schicken nur ihre Eingaben und zeigen den Stand an.
 *
 * <ul>
 *   <li>Die Bowl zieht zur Mitte, Luft und Boden bremsen.</li>
 *   <li>Schub (WASD) und Dash (Shift) kosten Spin, Spin ist Leben: bei 0 dreht der Kreisel aus.</li>
 *   <li>Zusammenstöße sind echte Impulse mit Rückprall. Den Spinverlust bestimmen Wucht, Angriff und
 *       Verteidigung, dazu Abwehr- und Burst-Chance. Wer kurz nach einem Treffer schnell durch eine Tasche
 *       fliegt, ist raus (Ring-Out).</li>
 *   <li>Die Ult lädt sich durch Treffer, Abwehr und Zeit.</li>
 * </ul>
 *
 * Fester Zeitschritt, eigener Zufall mit Seed: Gleiche Eingaben ergeben genau denselben Kampf.
 */
public final class ArenaPhysics {

    public static final int SIM_HZ = 120;
    static final double DT = 1.0 / SIM_HZ;
    /** Mittelpunktabstand im Kontakt (wie {@code CONTACT_DIST} im Frontend) */
    public static final double CONTACT_DIST = 1.3;
    public static final double BODY_R = CONTACT_DIST / 2;

    // ---------------------------------------------------------------- Tuning
    static final double GRAVITY = 9;
    static final double DRAG = 1.1;
    static final double THRUST = 9;
    static final double MAX_SPEED = 6.5;
    static final double RUSH_SPEED = 9.5;
    static final double DASH_IMPULSE = 6.5;
    public static final int DASH_COOLDOWN_MS = 1500;
    static final double DASH_COST = 0.004;
    static final double THRUST_COST = 0.004;
    /** Grundverlust an Spin pro Sekunde (×Arena-Reibung, Stats) */
    static final double SPIN_DECAY = 0.011;
    static final double RESTITUTION = 0.8;
    static final double WALL_RESTITUTION = 0.45;
    static final double HIT_DAMAGE = 0.04;
    /** Unter dieser Aufprallgeschwindigkeit zählt ein Kontakt nicht als Treffer */
    static final double MIN_IMPACT = 1.2;
    static final double IMPACT_SCALE = 8;
    /** Vor dieser Zeit gibt es keinen Burst, keinen Ring-Out und kein Ausdrehen */
    static final int GRACE_MS = 6000;
    static final double RING_OUT_SPEED = 2.6;
    /** Ring-Out nur, wenn man so kurz vorher getroffen wurde (kein Selbst-Aus beim Jagen) */
    static final int RING_OUT_AFTER_HIT_MS = 1500;
    static final double BURST_CHANCE = 0.045;
    public static final int TIME_LIMIT_MS = 45_000;
    static final double ULT_TIME = 0.03;
    static final double ULT_HIT = 0.12;
    static final double ULT_TAKEN = 0.08;
    static final double ULT_DEFLECT = 0.07;
    public static final int ULT_DURATION_MS = 4500;

    /** Eingabe einer Seite: Schubrichtung in Weltkoordinaten (Länge ≤ 1), Dash, Ult. */
    public record Input(double x, double z, boolean dash, boolean ult) {
        public static final Input NONE = new Input(0, 0, false, false);

        public Input withoutPresses() {
            return dash || ult ? new Input(x, z, false, false) : this;
        }
    }

    /** Startwerte einer Seite. @param launch Startqualität aus dem Anspinnen, 0–1 */
    public record FighterSetup(BeybladeStats stats, UltType ult, double launch) {}

    public enum EventType { CLASH, DASH, ULT, ULT_END, WALL, FINISH }

    /**
     * Ereignis für Effekte, Sound und Treffer-Anzeigen. Felder, die für den Typ nicht gelten, sind 0/null.
     *
     * @param side      CLASH: Angreifer, sonst die betroffene Seite; FINISH: Sieger
     * @param x         Ort (CLASH)
     * @param nx        Stoßrichtung (CLASH)
     * @param ult       Ult zum Ereignis (ULT, ULT_END; bei CLASH die beteiligte Ult)
     * @param finish    Finish (FINISH)
     */
    public record Event(EventType type, int t, Side side, double intensity, double x, double z, double nx,
                        double nz, boolean deflected, UltType ult, FinishType finish) {

        static Event of(EventType type, int t, Side side) {
            return new Event(type, t, side, 0, 0, 0, 0, 0, false, null, null);
        }
    }

    /** Ergebnis, sobald der Kampf entschieden ist. */
    public record Result(FinishType finish, Side winner, Side loser, int t) {}

    public static final class Body {
        public final Side side;
        public double x;
        public double z;
        public double vx;
        public double vz;
        /** Spin 0–1 (Leben) */
        public double spin;
        final double mass;
        final double atk;
        final double def;
        final double sta;
        final double dash;
        final double burstRes;
        public final UltType ult;
        public double meter;
        public int ultUntil = -1;
        /** Vernichtungsschlag: scharf bis zum nächsten Treffer oder Ablauf */
        public boolean smashArmed;
        public int dashReadyAt;
        /** Zeitpunkt des letzten erlittenen Treffers (für Ring-Out) */
        int hitAt = -1_000_000_000;
        public int hits;
        public int dashes;
        public boolean out;

        Body(Side side, FighterSetup f, double playRadius) {
            BeybladeStats s = f.stats();
            double start = playRadius * 0.62;
            int dir = side == Side.A ? -1 : 1;
            this.side = side;
            this.x = dir * start;
            this.z = 0;
            // tangential anlaufen wie aus dem Launcher
            this.vx = 0;
            this.vz = dir * (2 + 2.5 * f.launch());
            this.spin = 0.86 + 0.14 * f.launch();
            this.mass = Math.max(1, s.weight()) / 78.0;
            this.atk = norm(s.attack(), 7);
            this.def = norm(s.defense(), 12);
            this.sta = norm(s.stamina(), 16);
            this.dash = norm(s.dash(), 8);
            this.burstRes = norm(s.burstResistance(), 4);
            this.ult = f.ult();
            this.meter = 0.15 * f.launch();
        }
    }

    public final ArenaGeometry arena;
    public final Body a;
    public final Body b;
    /** Kampfzeit in Sekunden */
    public double t;
    private Result finished;
    private List<Event> pending = new ArrayList<>();
    private final Mulberry32 rng;
    private final double play;
    private int contactUntil = -1;

    public ArenaPhysics(ArenaGeometry arena, FighterSetup a, FighterSetup b, long seed) {
        this.arena = arena;
        this.rng = new Mulberry32(seed);
        this.play = arena.playRadius();
        this.a = new Body(Side.A, a, play);
        this.b = new Body(Side.B, b, play);
    }

    public Body body(Side side) {
        return side == Side.A ? a : b;
    }

    public Result finished() {
        return finished;
    }

    public int timeMs() {
        return (int) Math.round(t * 1000);
    }

    /** Ereignisse seit dem letzten Abholen. */
    public List<Event> drain() {
        List<Event> out = pending;
        pending = new ArrayList<>();
        return out;
    }

    /** Simuliert {@code ms} Millisekunden in festen Schritten. */
    public void advance(int ms, Input inA, Input inB) {
        int steps = Math.max(0, (int) Math.round(ms / 1000.0 * SIM_HZ));
        for (int i = 0; i < steps && finished == null; i++) step(inA, inB);
    }

    public boolean isUltReady(Side side) {
        Body x = body(side);
        return x.meter >= 1 && t * 1000 >= x.ultUntil && !x.smashArmed;
    }

    public boolean isUltActive(Side side) {
        Body x = body(side);
        return t * 1000 < x.ultUntil || x.smashArmed;
    }

    public void step(Input inA, Input inB) {
        if (finished != null) return;
        int tMs = (int) Math.round(t * 1000);
        control(a, inA, tMs);
        control(b, inB, tMs);
        move(a, tMs);
        move(b, tMs);
        collide(tMs);
        walls(a, tMs);
        walls(b, tMs);
        t += DT;
        checkEnd(tMs);
    }

    // ---------------------------------------------------------------- Steuerung

    private void control(Body x, Input in, int tMs) {
        if (in == null) in = Input.NONE;
        boolean rush = x.ult == UltType.RUSH && tMs < x.ultUntil;
        double len = Math.hypot(in.x(), in.z());
        double ix = len > 1 ? in.x() / len : in.x();
        double iz = len > 1 ? in.z() / len : in.z();
        double thrust = THRUST * (0.55 + 0.45 * x.spin) * (0.8 + 0.4 * x.dash) * (rush ? 1.7 : 1);
        x.vx += ix * thrust * DT;
        x.vz += iz * thrust * DT;
        x.spin -= THRUST_COST * Math.min(1, len) * DT * (rush ? 2 : 1);

        if (in.dash() && tMs >= x.dashReadyAt) {
            // ohne Richtung: Dash auf den Gegner zu
            double dx = ix;
            double dz = iz;
            if (Math.hypot(dx, dz) < 0.1) {
                Body o = other(x);
                dx = o.x - x.x;
                dz = o.z - x.z;
            }
            double d = nonZero(Math.hypot(dx, dz));
            double imp = DASH_IMPULSE * (0.8 + 0.4 * x.dash);
            x.vx += dx / d * imp;
            x.vz += dz / d * imp;
            x.spin -= DASH_COST;
            x.dashReadyAt = tMs + DASH_COOLDOWN_MS;
            x.dashes++;
            pending.add(Event.of(EventType.DASH, tMs, x.side));
        }

        if (in.ult() && isUltReady(x.side)) fireUlt(x, tMs);
    }

    private void fireUlt(Body x, int tMs) {
        x.meter = 0;
        pending.add(new Event(EventType.ULT, tMs, x.side, 0, 0, 0, 0, 0, false, x.ult, null));
        switch (x.ult) {
            case SMASH -> {
                // scharf bis zum nächsten Treffer, dazu ein Satz auf den Gegner
                x.smashArmed = true;
                x.ultUntil = tMs + ULT_DURATION_MS;
                Body o = other(x);
                double dx = o.x - x.x;
                double dz = o.z - x.z;
                double d = nonZero(Math.hypot(dx, dz));
                x.vx += dx / d * DASH_IMPULSE * 1.4;
                x.vz += dz / d * DASH_IMPULSE * 1.4;
            }
            case SURGE -> {
                x.spin = Math.min(1, x.spin + 0.09);
                x.ultUntil = tMs + ULT_DURATION_MS;
            }
            default -> x.ultUntil = tMs + ULT_DURATION_MS;
        }
    }

    // ---------------------------------------------------------------- Bewegung

    private void move(Body x, int tMs) {
        if (x.out) return;
        double r = Math.hypot(x.x, x.z);
        if (r > 1e-4) {
            // Hangabtrieb zur Mitte
            double g = GRAVITY * arena.slope(r);
            x.vx -= x.x / r * g * DT;
            x.vz -= x.z / r * g * DT;
        }
        double drag = Math.exp(-DRAG * arena.arena().friction() * DT);
        x.vx *= drag;
        x.vz *= drag;
        double max = x.ult == UltType.RUSH && tMs < x.ultUntil ? RUSH_SPEED : MAX_SPEED;
        double sp = Math.hypot(x.vx, x.vz);
        if (sp > max) {
            x.vx *= max / sp;
            x.vz *= max / sp;
        }
        x.x += x.vx * DT;
        x.z += x.vz * DT;

        // Spinverlust: Reibung, Ausdauer senkt, Schild und Zweiter Wind ebenfalls
        double decay = SPIN_DECAY * arena.arena().friction() * (1 + 0.04 * x.def) * (1 - 0.18 * Math.min(1.5, x.sta));
        if (tMs < x.ultUntil && (x.ult == UltType.SHIELD || x.ult == UltType.SURGE)) decay *= 0.5;
        // taumelnd verliert man schneller
        if (x.spin < 0.3) decay *= 1 + 1.2 * (0.3 - x.spin) / 0.3;
        x.spin -= decay * DT;
        x.meter = Math.min(1, x.meter + (isUltActive(x.side) ? 0 : ULT_TIME * DT));

        if (x.ultUntil >= 0 && tMs >= x.ultUntil) {
            pending.add(new Event(EventType.ULT_END, tMs, x.side, 0, 0, 0, 0, 0, false, x.ult, null));
            x.ultUntil = -1;
            x.smashArmed = false;
        }
    }

    private void walls(Body x, int tMs) {
        if (x.out) return;
        double r = Math.hypot(x.x, x.z);
        if (r <= play) return;
        double nx = x.x / r;
        double nz = x.z / r;
        double vr = x.vx * nx + x.vz * nz;
        double angle = Math.atan2(x.z, x.x);
        if (tMs >= GRACE_MS && vr > RING_OUT_SPEED && tMs - x.hitAt < RING_OUT_AFTER_HIT_MS
                && arena.pocketAt(angle) >= 0) {
            x.out = true;
            finish(FinishType.RING_OUT, other(x).side, tMs);
            return;
        }
        // Rand: zurückprallen
        x.x = nx * play;
        x.z = nz * play;
        if (vr > 0) {
            x.vx -= (1 + WALL_RESTITUTION) * vr * nx;
            x.vz -= (1 + WALL_RESTITUTION) * vr * nz;
            if (vr > 2) {
                pending.add(new Event(EventType.WALL, tMs, x.side, clamp(vr / IMPACT_SCALE, 0, 1), x.x, x.z, nx, nz,
                        false, null, null));
            }
        }
    }

    // ---------------------------------------------------------------- Zusammenstöße

    private void collide(int tMs) {
        if (a.out || b.out) return;
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double dist = Math.hypot(dx, dz);
        if (dist >= 2 * BODY_R || dist < 1e-6) return;
        double nx = dx / dist;
        double nz = dz / dist;
        // auseinanderschieben
        double overlap = 2 * BODY_R - dist;
        double total = a.mass + b.mass;
        a.x -= nx * overlap * (b.mass / total);
        a.z -= nz * overlap * (b.mass / total);
        b.x += nx * overlap * (a.mass / total);
        b.z += nz * overlap * (a.mass / total);

        double rvn = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz;
        if (rvn >= 0) return;
        double impact = -rvn;
        boolean shieldA = a.ult == UltType.SHIELD && tMs < a.ultUntil;
        boolean shieldB = b.ult == UltType.SHIELD && tMs < b.ultUntil;
        double e = RESTITUTION + (shieldA || shieldB ? 0.6 : 0);
        double j = (1 + e) * impact / (1 / a.mass + 1 / b.mass);
        a.vx -= j / a.mass * nx;
        a.vz -= j / a.mass * nz;
        b.vx += j / b.mass * nx;
        b.vz += j / b.mass * nz;

        if (impact < MIN_IMPACT || tMs < contactUntil) return;
        contactUntil = tMs + 300;

        // Angreifer: wer schneller auf den anderen zufuhr
        double pushA = a.vx * nx + a.vz * nz + j / a.mass;
        double pushB = -(b.vx * nx + b.vz * nz) + j / b.mass;
        Body att = pushA >= pushB ? a : b;
        Body def = att == a ? b : a;
        boolean smash = att.smashArmed;
        double intensity = clamp(impact / IMPACT_SCALE * (0.8 + 0.4 * att.atk) * arena.arena().impact(), 0, 1);
        if (smash) intensity = clamp(intensity * 1.8 + 0.25, 0, 1);
        boolean rush = att.ult == UltType.RUSH && tMs < att.ultUntil;
        if (rush) intensity = clamp(intensity * 1.25, 0, 1);
        boolean shieldDef = def.ult == UltType.SHIELD && tMs < def.ultUntil;

        double deflect = clamp(0.2 + 0.7 * (def.def - 0.6 * att.atk), 0.05, 0.8);
        if (shieldDef) deflect = Math.min(0.95, deflect + 0.4);
        if (smash) deflect = 0;
        boolean deflected = rng.next() < deflect;
        double cx = a.x + nx * BODY_R;
        double cz = a.z + nz * BODY_R;
        double dirX = att == a ? nx : -nx;
        double dirZ = att == a ? nz : -nz;
        UltType tag = smash ? UltType.SMASH : rush ? UltType.RUSH : shieldDef ? UltType.SHIELD : null;

        if (deflected) {
            att.spin -= HIT_DAMAGE * 0.5 * intensity * (0.6 + 0.6 * def.def);
            if (!shieldDef) def.spin -= HIT_DAMAGE * 0.15 * intensity;
            charge(def, ULT_DEFLECT);
        } else {
            double damage = HIT_DAMAGE * intensity * ((0.6 + att.atk) / (0.6 + def.def)) * (smash ? 2.2 : 1);
            def.spin -= damage;
            att.spin -= damage * 0.15;
            def.hitAt = tMs;
            att.hits++;
            charge(att, ULT_HIT);
            charge(def, ULT_TAKEN);
            // Burst: harte Treffer, wenig Spin, schwache Burst-Resistenz
            if (tMs >= GRACE_MS && intensity > 0.35) {
                double chance = BURST_CHANCE * intensity * intensity * Math.max(0.1, 1 - 0.6 * def.burstRes)
                        * (1 + 1.5 * (1 - clamp(def.spin, 0, 1)));
                if (rng.next() < chance) {
                    pending.add(new Event(EventType.CLASH, tMs, att.side, intensity, cx, cz, dirX, dirZ, false, tag,
                            null));
                    finish(FinishType.BURST, att.side, tMs);
                    return;
                }
            }
        }
        if (smash) {
            att.smashArmed = false;
            att.ultUntil = tMs;
        }
        pending.add(new Event(EventType.CLASH, tMs, att.side, intensity, cx, cz, dirX, dirZ, deflected, tag, null));
    }

    private void charge(Body x, double amount) {
        if (!isUltActive(x.side)) x.meter = Math.min(1, x.meter + amount);
    }

    // ---------------------------------------------------------------- Ende

    private void checkEnd(int tMs) {
        if (finished != null) return;
        if (tMs >= GRACE_MS && (a.spin <= 0 || b.spin <= 0)) {
            Side loser = a.spin <= b.spin ? Side.A : Side.B;
            finish(FinishType.SPIN_OUT, loser == Side.A ? Side.B : Side.A, tMs);
            return;
        }
        a.spin = Math.max(a.spin, tMs < GRACE_MS ? 0.05 : 0);
        b.spin = Math.max(b.spin, tMs < GRACE_MS ? 0.05 : 0);
        if (tMs >= TIME_LIMIT_MS) finish(FinishType.DECISION, a.spin >= b.spin ? Side.A : Side.B, tMs);
    }

    /** Aufgabe: Wer die Verbindung verliert oder aufgibt, verliert. */
    public void forfeit(Side loser) {
        if (finished != null) return;
        finish(FinishType.SPIN_OUT, loser == Side.A ? Side.B : Side.A, timeMs());
    }

    private void finish(FinishType finish, Side winner, int tMs) {
        if (finished != null) return;
        Side loser = winner == Side.A ? Side.B : Side.A;
        finished = new Result(finish, winner, loser, tMs);
        pending.add(new Event(EventType.FINISH, tMs, winner, 0, 0, 0, 0, 0, false, null, finish));
    }

    private Body other(Body x) {
        return x == a ? b : a;
    }

    private static double norm(double v, double ref) {
        return Math.max(0, Math.min(2, v / ref));
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double nonZero(double v) {
        return v == 0 ? 1 : v;
    }
}
