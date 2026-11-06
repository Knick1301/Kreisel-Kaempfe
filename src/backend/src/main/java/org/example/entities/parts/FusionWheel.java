package org.example.entities.parts;
import jakarta.persistence.*;
import org.example.game.UltType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class FusionWheel extends Part {


    private int attack;
    private int burstResistance;
    private int defense;
    /** Ult des Kreisels, {@code null} = passend zum Stil */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16)
    private UltType ult;

    public UltType getUlt() { return ult; }
    public void setUlt(UltType ult) { this.ult = ult; }

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
