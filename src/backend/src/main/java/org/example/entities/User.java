package org.example.entities;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    /** Liga-Punkte (Elo). {@code null} = noch nie gewertet gekämpft, zählt als Startwert. */
    private Integer rating;

    private Integer xp;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Beyblade> beybladeConfigurations = new ArrayList<>();


    public List<Beyblade> removeBeyblade(Beyblade beyblade) {
        if(beybladeConfigurations.contains(beyblade)) {
            beybladeConfigurations.remove(beyblade);
            beyblade.setOwner(null);
        }
        return beybladeConfigurations;

    }

    public List<Beyblade> addBeyblade(Beyblade beyblade) {
        if(!beybladeConfigurations.contains(beyblade)){
            beybladeConfigurations.add(beyblade);
            beyblade.setOwner(this);
        }
        return beybladeConfigurations;
    }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Beyblade> getBeybladeConfigurations() {
        return beybladeConfigurations;
    }


    public Long getId() {
        return id;
    }


    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    /** Arena-Modus (Physik, Steuerung): eigener Rang, getrennt vom klassischen. {@code null} = noch nie gespielt */
    private Integer arenaRating;
    private Integer arenaXp;
    private Integer arenaWins;
    private Integer arenaLosses;

    /** Gewonnene Solo-Cups (Turnier gegen die KI) */
    private Integer cupsWon;

    public Integer getArenaRating() { return arenaRating; }
    public void setArenaRating(Integer arenaRating) { this.arenaRating = arenaRating; }
    public Integer getArenaXp() { return arenaXp; }
    public void setArenaXp(Integer arenaXp) { this.arenaXp = arenaXp; }
    public Integer getArenaWins() { return arenaWins; }
    public void setArenaWins(Integer arenaWins) { this.arenaWins = arenaWins; }
    public Integer getArenaLosses() { return arenaLosses; }
    public void setArenaLosses(Integer arenaLosses) { this.arenaLosses = arenaLosses; }

    public int getCupsWon() { return cupsWon == null ? 0 : cupsWon; }
    public void setCupsWon(Integer cupsWon) { this.cupsWon = cupsWon; }

    public Integer getXp() {
        return xp;
    }

    public void setXp(Integer xp) {
        this.xp = xp;
    }
}
