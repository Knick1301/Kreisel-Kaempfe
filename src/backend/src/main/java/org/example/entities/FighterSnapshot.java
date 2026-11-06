package org.example.entities;

import jakarta.persistence.Embeddable;
import org.example.dtos.BeybladeStats;
import org.example.game.Fighter;

/**
 * Stand eines Kämpfers zum Zeitpunkt des Kampfs. Kreisel können später geändert oder gelöscht werden,
 * das Replay braucht aber genau die Werte von damals. Teile werden nie gelöscht (nur deaktiviert),
 * deshalb reichen ihre IDs. Bei KI-Gegnern sind die Teile-IDs leer.
 */
@Embeddable
public class FighterSnapshot {

    private String owner;
    private String beyblade;
    private Integer attack;
    private Integer defense;
    private Integer stamina;
    private Integer dash;
    private Integer weight;
    private Integer burstResistance;
    private Long fusionWheelId;
    private Long spinTrackId;
    private Long tipId;
    private String accent;

    protected FighterSnapshot() {}

    private FighterSnapshot(String owner, String beyblade, BeybladeStats stats) {
        this.owner = owner;
        this.beyblade = beyblade;
        this.attack = stats.attack();
        this.defense = stats.defense();
        this.stamina = stats.stamina();
        this.dash = stats.dash();
        this.weight = stats.weight();
        this.burstResistance = stats.burstResistance();
    }

    public static FighterSnapshot of(User owner, Beyblade b) {
        FighterSnapshot s = new FighterSnapshot(owner.getUsername(), b.getName(), b.getStats());
        s.fusionWheelId = b.getFusionWheel().getId();
        s.spinTrackId = b.getSpinTrack().getId();
        s.tipId = b.getTip().getId();
        return s;
    }

    public static FighterSnapshot bot(String owner, String name, BeybladeStats stats, String accent) {
        FighterSnapshot s = new FighterSnapshot(owner, name, stats);
        s.accent = accent;
        return s;
    }

    public boolean isComplete() {
        return beyblade != null && attack != null && defense != null && stamina != null && dash != null
                && weight != null && burstResistance != null;
    }

    public BeybladeStats stats() {
        return new BeybladeStats(attack, defense, stamina, dash, weight, burstResistance);
    }

    public Fighter fighter() {
        return new Fighter(owner, beyblade, stats());
    }

    public String getOwner() { return owner; }
    public String getBeyblade() { return beyblade; }
    public Long getFusionWheelId() { return fusionWheelId; }
    public Long getSpinTrackId() { return spinTrackId; }
    public Long getTipId() { return tipId; }
    public String getAccent() { return accent; }
}
