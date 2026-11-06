package org.example.entities;


import jakarta.persistence.*;
import org.example.dtos.BeybladeStats;
import org.example.entities.parts.StatSource;
import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.SpinTrack;
import org.example.entities.parts.Tip;

import java.util.List;
import java.util.function.ToIntFunction;


@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "name"}))
public class Beyblade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "spintrack_id", nullable = false)
    private SpinTrack spinTrack;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fusion_wheel_id", nullable = false)
    private FusionWheel fusionWheel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tip_id", nullable = false)
    private Tip tip;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }


    private List<StatSource> parts() {
        return List.of(fusionWheel, spinTrack, tip);
    }

    public Tip getTip() {
        return tip;
    }

    public void setTip(Tip tip) {
        this.tip = tip;
    }

    public Long getId() {
        return id;
    }


    public SpinTrack getSpinTrack() {
        return spinTrack;
    }

    public void setSpinTrack(SpinTrack spinTrack) {
        this.spinTrack = spinTrack;
    }

    public FusionWheel getFusionWheel() {
        return fusionWheel;
    }

    public void setFusionWheel(FusionWheel fusionWheel) {
        this.fusionWheel = fusionWheel;
    }


    private int sum(ToIntFunction<StatSource> stats) {
       return parts().stream().mapToInt(stats).sum();
    }

    public int getAttack(){ return sum(StatSource::getAttack);}
    public int getDefense(){ return sum(StatSource::getDefense);}
    public int getStamina(){ return sum(StatSource::getStamina);}
    public int getDash(){ return sum(StatSource::getDash);}
    public int getWeight(){ return sum(StatSource::getWeight);}
    public int getBurstResistance(){ return sum(StatSource::getBurstResistance);}

    public BeybladeStats getStats() {
        return new BeybladeStats(getAttack(), getDefense(), getStamina(), getDash(), getWeight(), getBurstResistance());
    }




}
