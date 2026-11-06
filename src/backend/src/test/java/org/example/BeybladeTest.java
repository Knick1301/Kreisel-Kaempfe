package org.example;

import org.example.entities.Beyblade;
import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.SpinTrack;
import org.example.entities.parts.Tip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeybladeTest {

    @Test
    void statsAreSumOfAllParts() {
        FusionWheel wheel = new FusionWheel();
        wheel.setWeight(30);
        wheel.setAttack(7);
        wheel.setDefense(1);
        wheel.setBurstResistance(2);

        SpinTrack track = new SpinTrack();
        track.setWeight(45);
        track.setDefense(7);
        track.setStamina(3);

        Tip tip = new Tip();
        tip.setWeight(4);
        tip.setStamina(2);
        tip.setDash(8);

        Beyblade b = new Beyblade();
        b.setFusionWheel(wheel);
        b.setSpinTrack(track);
        b.setTip(tip);

        assertEquals(7,  b.getAttack());
        assertEquals(8,  b.getDefense());
        assertEquals(5,  b.getStamina());
        assertEquals(8,  b.getDash());
        assertEquals(2,  b.getBurstResistance());
        assertEquals(79, b.getWeight());
    }
}