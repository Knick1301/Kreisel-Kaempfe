package org.example.game;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ein Ereignis der Kampf-Timeline. Felder, die für den Typ keine Rolle spielen, sind {@code null}
 * und werden im JSON weggelassen.
 *
 * @param t          Zeitpunkt in ms seit dem Launch
 * @param attacker   CLASH: wer angreift
 * @param defender   DEFLECT: wer abwehrt
 * @param victim     NEAR_RING_OUT, WOBBLE, BURST, RING_OUT, SPIN_OUT: wen es trifft
 * @param intensity  CLASH: Stärke 0–1; ULT: Stärke der Ult (rund 0,85–1,35)
 * @param angle      CLASH: Polarwinkel (rad) des Treffpunkts auf der Bowl;
 *                   NEAR_RING_OUT/RING_OUT: Richtung der Tasche
 * @param pocket     NEAR_RING_OUT/RING_OUT: Index der Tasche in {@link Arena#pockets()}
 * @param side       ULT, ULT_READY, ULT_END: wessen Ult
 * @param ult        ULT, ULT_END: welche Ult; CLASH: Treffer gehört zu dieser Ult des Angreifers
 *                   (Vernichtungsschlag, Ansturm) oder prallte am Schild ab
 * @param durationMs ULT: Wirkdauer bei Schild, Ansturm und Zweiter Wind
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineEvent(int t, EventType type, Side attacker, Side defender, Side victim,
                            Double intensity, Double angle, Integer pocket, Side side, UltType ult,
                            Integer durationMs) {

    static TimelineEvent at(int t, EventType type) {
        return new TimelineEvent(t, type, null, null, null, null, null, null, null, null, null);
    }

    static TimelineEvent clash(int t, Side attacker, double intensity, double angle) {
        return clash(t, attacker, intensity, angle, null);
    }

    static TimelineEvent clash(int t, Side attacker, double intensity, double angle, UltType ult) {
        return new TimelineEvent(t, EventType.CLASH, attacker, null, null, round(intensity), round(angle), null,
                null, ult, null);
    }

    static TimelineEvent deflect(int t, Side defender) {
        return new TimelineEvent(t, EventType.DEFLECT, null, defender, null, null, null, null, null, null, null);
    }

    static TimelineEvent victim(int t, EventType type, Side victim) {
        return new TimelineEvent(t, type, null, null, victim, null, null, null, null, null, null);
    }

    static TimelineEvent pocket(int t, EventType type, Side victim, double angle, int pocket) {
        return new TimelineEvent(t, type, null, null, victim, null, round(angle), pocket, null, null, null);
    }

    static TimelineEvent ult(int t, Side side, UltType ult, double power, Integer durationMs) {
        return new TimelineEvent(t, EventType.ULT, null, null, null, round(power), null, null, side, ult,
                durationMs);
    }

    static TimelineEvent ultReady(int t, Side side, UltType ult) {
        return new TimelineEvent(t, EventType.ULT_READY, null, null, null, null, null, null, side, ult, null);
    }

    static TimelineEvent ultEnd(int t, Side side, UltType ult) {
        return new TimelineEvent(t, EventType.ULT_END, null, null, null, null, null, null, side, ult, null);
    }

    static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
