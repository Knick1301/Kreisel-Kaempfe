package org.example.entities.parts;

import jakarta.persistence.*;

@Entity
public class Tip extends Part {

    private int stamina;



    private int dash;

    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    public void setDash(int dash) {
        this.dash = dash;
    }

    public int getStamina() {
        return this.stamina;
    }


    public int getDash() {
        return this.dash;
    }
}
