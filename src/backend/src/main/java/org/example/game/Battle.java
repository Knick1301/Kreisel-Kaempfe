package org.example.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.example.game.BattleTuning.*;

/**
 * Zeitbasierte, wahrscheinlichkeitsgesteuerte Kampfsimulation (20 Ticks/s).
 *
 * <p>Schere-Stein-Papier entsteht aus den Mechaniken, nicht aus festen Siegern:
 * <ul>
 *   <li>Verteidigung schlägt eher Angriff: Treffer prallen ab, der Angreifer verliert durch den Rückstoß Spin.</li>
 *   <li>Ausdauer schlägt eher Verteidigung: schwere, breite Kreisel haben mehr Reibung und drehen kürzer.</li>
 *   <li>Angriff schlägt eher Ausdauer: leichte, locker gebaute Kreisel fliegen raus oder bursten.</li>
 * </ul>
 *
 * <p>Gleicher Seed ergibt exakt das gleiche Ergebnis und die gleiche Timeline. Deshalb nutzt die Simulation
 * {@link Random} (plattformunabhängig spezifiziert) und {@link StrictMath} statt {@link Math}.
 */
public class Battle {

    private final Arena arena;
    private final Fighter a;
    private final Fighter b;
    private final long seed;
    private final Launch launchA;
    private final Launch launchB;

    /** Ohne Anspinnen: Startenergie wird gewürfelt (wie in Simulationsversion 1). */
    public Battle(Arena arena, Fighter a, Fighter b, long seed) {
        this(arena, a, b, seed, null, null);
    }

    /**
     * @param launchA Start von A, {@code null} = gewürfelt wie ohne Anspinnen
     * @param launchB Start von B, {@code null} = gewürfelt wie ohne Anspinnen
     */
    public Battle(Arena arena, Fighter a, Fighter b, long seed, Launch launchA, Launch launchB) {
        this.arena = arena;
        this.a = a;
        this.b = b;
        this.seed = seed;
        this.launchA = launchA;
        this.launchB = launchB;
    }

    public BattleOutcome run() {
        return new Run().simulate();
    }

    /** Normalisierte Werte und laufender Zustand eines Kämpfers. */
    private static final class State {
        final Side side;
        final Fighter fighter;
        final StyleVector style;
        final double attack;
        final double defense;
        final double stamina;
        final double dash;
        final double weight;
        final double mass;
        final double burstResistance;
        final double frictionPerSecond;
        /** Launch-Qualität für den Initiative-Bonus, 0 ohne Anspinnen */
        final double initiative;
        double energy;
        boolean wobbling;
        int hits;

        State(Side side, Fighter fighter, Launch launch, Arena arena, Random rng) {
            this.side = side;
            this.fighter = fighter;
            var s = fighter.stats();
            style = StyleVector.of(s);
            attack = norm(s.attack(), ATTACK_REF);
            defense = norm(s.defense(), DEFENSE_REF);
            stamina = norm(s.stamina(), STAMINA_REF);
            dash = norm(s.dash(), DASH_REF);
            weight = normWeight(s.weight());
            mass = Math.max(1, s.weight());
            burstResistance = norm(s.burstResistance(), BURST_REF);

            double roll = rng.nextDouble();
            if (launch == null) {
                energy = LAUNCH_MIN + (1 - LAUNCH_MIN) * roll;
                initiative = 0;
            } else {
                energy = LAUNCH_SKILL_MIN + LAUNCH_SKILL_RANGE * launch.quality()
                        + LAUNCH_NOISE * roll;
                initiative = launch.quality();
            }
            double jitter = 1 + FRICTION_JITTER * (2 * rng.nextDouble() - 1);
            frictionPerSecond = FRICTION_BASE * arena.friction() * jitter
                    * (1 + FRICTION_WEIGHT * weight + FRICTION_DEFENSE * defense)
                    * (1 - FRICTION_STAMINA * Math.min(stamina, 1.5));
        }

        double aggression() {
            return dash * (AGGRESSION_BASE + style.attack());
        }

        double attackWeight() {
            return ATTACKER_BASE + attack + ATTACKER_DASH * dash;
        }
    }

    private final class Run {
        private final Random rng = new Random(mix(seed));
        private final List<TimelineEvent> events = new ArrayList<>();
        private final List<Double> energyA = new ArrayList<>();
        private final List<Double> energyB = new ArrayList<>();
        private final State sa = new State(Side.A, a, launchA, arena, rng);
        private final State sb = new State(Side.B, b, launchB, arena, rng);
        private int lastClash = Integer.MIN_VALUE / 2;
        private int lastNear = Integer.MIN_VALUE / 2;
        private double orbitAngle = rng.nextDouble() * 2 * Math.PI;
        private int orbitTime = 0;

        BattleOutcome simulate() {
            events.add(TimelineEvent.at(0, EventType.LAUNCH));
            sample();
            int ticks = TIME_LIMIT_MS / TICK_MS;
            for (int tick = 1; tick <= ticks; tick++) {
                int t = tick * TICK_MS;
                if (t == TORNADO_MS) events.add(TimelineEvent.at(t, EventType.TORNADO));
                if (t == ENGAGE_MS) events.add(TimelineEvent.at(t, EventType.ENGAGE));

                friction(sa);
                friction(sb);

                BattleOutcome ko = clash(t);
                if (ko != null) return ko;

                if (t < MIN_KO_MS) {
                    sa.energy = Math.max(sa.energy, EARLY_ENERGY_FLOOR);
                    sb.energy = Math.max(sb.energy, EARLY_ENERGY_FLOOR);
                }
                wobble(sa, t);
                wobble(sb, t);

                BattleOutcome spinOut = spinOut(t);
                if (spinOut != null) return spinOut;

                if (t % SAMPLE_MS == 0) sample();
            }
            events.add(TimelineEvent.at(TIME_LIMIT_MS, EventType.TIMEOUT));
            State winner = sa.energy == sb.energy
                    ? (rng.nextBoolean() ? sa : sb)
                    : (sa.energy > sb.energy ? sa : sb);
            return finish(winner, FinishType.DECISION, TIME_LIMIT_MS);
        }

        private void friction(State s) {
            double wobble = 1 + WOBBLE_FRICTION * Math.max(0, WOBBLE_ENERGY - s.energy) / WOBBLE_ENERGY;
            s.energy -= s.frictionPerSecond * wobble * TICK_MS / 1000.0;
        }

        private BattleOutcome clash(int t) {
            if (t < TORNADO_MS || t - lastClash < CLASH_COOLDOWN_MS) return null;
            boolean tornado = t < ENGAGE_MS;
            double phase = tornado ? TORNADO_CLASH_FACTOR : 1;
            double speed = 0.3 + 0.7 * clamp((sa.energy + sb.energy) / 2, 0, 1);
            double lambda = CLASH_RATE * arena.clash() * phase * speed * (CLASH_RATE_BASE + sa.aggression() + sb.aggression());
            if (rng.nextDouble() >= 1 - StrictMath.exp(-lambda * TICK_MS / 1000.0)) return null;
            lastClash = t;

            double wA = sa.attackWeight();
            double wB = sb.attackWeight();
            if (t < INITIATIVE_MS) {
                // Wer sauberer angespinnt hat, setzt in der Anfangsphase eher den ersten Treffer.
                double edge = INITIATIVE * (sa.initiative - sb.initiative);
                wA *= 1 + edge;
                wB *= 1 - edge;
            }
            double pA = wA / (wA + wB);
            State att = rng.nextDouble() < pA ? sa : sb;
            State def = att == sa ? sb : sa;

            double intensity = clamp(INTENSITY_BASE
                    * (0.30 + 0.60 * Math.min(att.attack, 1.5) + INTENSITY_WEIGHT * Math.min(att.weight, 1.5))
                    * (0.75 + 0.25 * Math.min(att.dash, 1.5))
                    * StrictMath.sqrt(Math.max(att.energy, 0.05))
                    * StrictMath.sqrt(att.mass / def.mass)
                    * (0.65 + 0.7 * rng.nextDouble())
                    * (1 - EVASION * def.style.stamina())
                    * (tornado ? TORNADO_INTENSITY : 1)
                    * arena.impact(), 0, 1);
            double angle = nextClashAngle(t);

            double deflectChance = clamp(DEFLECT_BASE
                    + DEFLECT_DEFENSE * (Math.min(def.defense, 1.5) - DEFLECT_ATTACK * Math.min(att.attack, 1.5)),
                    DEFLECT_MIN, DEFLECT_MAX);
            double ko = clamp((t - MIN_KO_MS) / (double) KO_RAMP_MS, 0, 1);
            if (rng.nextDouble() < deflectChance) {
                att.energy -= RECOIL * intensity * (def.mass / att.mass) * (0.5 + 0.5 * Math.min(def.defense, 1));
                def.energy -= DEFLECT_DEFENDER_LOSS * HIT * intensity;
                // Harter Abpraller: Der Angreifer kann sich selbst aus dem Stadion schießen.
                double selfOut = SELF_RING_OUT * intensity * intensity * arena.ringOut() * (def.mass / att.mass)
                        * Math.max(0.1, RING_OUT_WEIGHT - att.weight) * lowEnergy(att);
                boolean out = rng.nextDouble() < selfOut * ko;
                double finalAngle = out ? pocketAngle(angle) : angle;
                events.add(TimelineEvent.clash(t, att.side, intensity, finalAngle));
                events.add(TimelineEvent.deflect(t, def.side));
                if (out) {
                    events.add(TimelineEvent.pocket(t, EventType.RING_OUT, att.side, finalAngle,
                            arena.nearestPocket(finalAngle)));
                    return finish(def, FinishType.RING_OUT, t);
                }
                return null;
            }

            def.energy -= HIT * intensity * (1 - ABSORB * Math.min(def.defense, 1));
            att.energy -= HIT_ATTACKER_LOSS * HIT * intensity;
            att.hits++;

            double lowEnergy = lowEnergy(def);
            double i2 = intensity * intensity;
            double burst = BURST_BASE * i2
                    * Math.max(0, 1 - BURST_RESIST * Math.min(def.burstResistance, 1.2))
                    * (1 + FRAGILITY * def.style.stamina())
                    * lowEnergy;
            double ringOut = RING_OUT_BASE * i2 * arena.ringOut()
                    * Math.max(0.1, RING_OUT_WEIGHT - def.weight)
                    * (1 - RING_OUT_DEFENSE * Math.min(def.defense, 1))
                    * lowEnergy;
            double burstRoll = rng.nextDouble();
            double ringRoll = rng.nextDouble();

            if (burstRoll < burst * ko) {
                events.add(TimelineEvent.clash(t, att.side, intensity, angle));
                events.add(TimelineEvent.victim(t, EventType.BURST, def.side));
                return finish(att, FinishType.BURST, t);
            }
            if (ringRoll < ringOut * ko) {
                double pocketAngle = pocketAngle(angle);
                events.add(TimelineEvent.clash(t, att.side, intensity, pocketAngle));
                events.add(TimelineEvent.pocket(t, EventType.RING_OUT, def.side, pocketAngle,
                        arena.nearestPocket(pocketAngle)));
                return finish(att, FinishType.RING_OUT, t);
            }

            events.add(TimelineEvent.clash(t, att.side, intensity, angle));
            if (ringRoll < NEAR_RING_OUT * ringOut && t - lastNear >= NEAR_COOLDOWN_MS) {
                lastNear = t;
                int pocket = arena.nearestPocket(angle);
                events.add(TimelineEvent.pocket(t, EventType.NEAR_RING_OUT, def.side,
                        arena.pockets().get(pocket), pocket));
            }
            return null;
        }

        private double lowEnergy(State s) {
            return 1 + LOW_ENERGY_KO * (1 - clamp(s.energy, 0, 1));
        }

        /** Ring-Out-Richtung: nächste Tasche zum Treffpunkt, leicht gestreut. */
        private double pocketAngle(double angle) {
            int pocket = arena.nearestPocket(angle);
            return wrap2pi(arena.pockets().get(pocket)
                    + (2 * rng.nextDouble() - 1) * arena.pocketHalfWidth() * POCKET_SPREAD);
        }

        /** Treffpunkt wandert plausibel um die Bowl, damit die Animation keine Sprünge braucht. */
        private double nextClashAngle(int t) {
            double dt = (t - orbitTime) / 1000.0;
            orbitAngle = wrap2pi(orbitAngle + ORBIT_OMEGA * dt + rng.nextGaussian() * ORBIT_JITTER);
            orbitTime = t;
            return orbitAngle;
        }

        private void wobble(State s, int t) {
            if (!s.wobbling && s.energy < WOBBLE_ENERGY) {
                s.wobbling = true;
                events.add(TimelineEvent.victim(t, EventType.WOBBLE, s.side));
            }
        }

        private BattleOutcome spinOut(int t) {
            boolean outA = sa.energy <= 0;
            boolean outB = sb.energy <= 0;
            if (!outA && !outB) return null;
            State loser;
            if (outA && outB) {
                loser = sa.energy == sb.energy ? (rng.nextBoolean() ? sa : sb) : (sa.energy < sb.energy ? sa : sb);
            } else {
                loser = outA ? sa : sb;
            }
            events.add(TimelineEvent.victim(t, EventType.SPIN_OUT, loser.side));
            return finish(loser == sa ? sb : sa, FinishType.SPIN_OUT, t);
        }

        private void sample() {
            energyA.add(TimelineEvent.round(clamp(sa.energy, 0, 1)));
            energyB.add(TimelineEvent.round(clamp(sb.energy, 0, 1)));
        }

        private BattleOutcome finish(State winner, FinishType finish, int t) {
            if (t % SAMPLE_MS == 0 && energyA.size() == t / SAMPLE_MS) sample();
            State loser = winner == sa ? sb : sa;
            BattleTimeline timeline = new BattleTimeline(SAMPLE_MS, List.copyOf(energyA), List.copyOf(energyB),
                    List.copyOf(events), sa.hits, sb.hits);
            return new BattleOutcome(winner.fighter, loser.fighter, winner.side, finish, t, timeline);
        }
    }

    /** SplitMix64-Finalizer: verteilt auch aufeinanderfolgende Seeds (0, 1, 2 …) gleichmäßig. */
    static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static double wrap2pi(double angle) {
        double a = angle % (2 * Math.PI);
        return a < 0 ? a + 2 * Math.PI : a;
    }
}
