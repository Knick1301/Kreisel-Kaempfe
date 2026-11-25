package org.example.entities.parts;

public interface StatSource {
        default int getAttack()          { return 0; }
        default int getDefense()         { return 0; }
        default int getStamina()         { return 0; }
        default int getDash()            { return 0; }
        default int getBurstResistance() { return 0; }
        default int getWeight()          { return 0; }
    }

