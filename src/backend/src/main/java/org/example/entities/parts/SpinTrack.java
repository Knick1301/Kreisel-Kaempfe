package org.example.entities.parts;

import jakarta.persistence.*;

@Entity
public class SpinTrack extends Part {

    private int stamina;

    private int defense;

    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    public void setDefense(int defense) {
        this.defense = defense;
    }

    public int getStamina() {
        return this.stamina;
    }

    public int getDefense(){
        return this.defense;
    }
}
