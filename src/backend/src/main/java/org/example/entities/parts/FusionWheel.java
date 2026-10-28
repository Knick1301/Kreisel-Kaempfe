package org.example.entities.parts;
import jakarta.persistence.*;

@Entity
public class FusionWheel extends Part {


    private int attack;
    private int burstResistance;
    private int defense;

    public void setAttack(int attack) {
        this.attack = attack;
    }

    public void setBurstResistance(int burstResistance) {
        this.burstResistance = burstResistance;
    }

    public void setDefense(int defense) {
        this.defense = defense;
    }

    public int getAttack(){
        return this.attack;
    }

    public int getBurstResistance() {
        return this.burstResistance;
    }


    public int getDefense() {
        return this.defense;
    }


}
