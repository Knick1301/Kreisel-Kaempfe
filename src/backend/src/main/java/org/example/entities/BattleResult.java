package org.example.entities;

import jakarta.persistence.*;
import org.example.game.BattleMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.example.game.FinishType;
import org.example.game.Side;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
public class BattleResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "winner_id", nullable = false)
    private User winner;

    @ManyToOne(optional = false)
    @JoinColumn(name = "loser_id", nullable = false)
    private User loser;

    private String winnerBeyblade;
    private String loserBeyblade;

    // Enums als Text statt als H2-Enum-Typ: Sonst lehnt eine bestehende Datenbank neue Werte (z. B. RING_OUT)
    // ab, weil ddl-auto=update die erlaubten Werte nie erweitert. Hibernate stellt bestehende
    // Spalten beim Start selbst auf VARCHAR um.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private FinishType finish;

    /** Nur noch für Altbestand, die neue Simulation rechnet in Zeit statt Runden. */
    private int rounds;

    // Neue Spalten sind nullable: ddl-auto=update ergänzt sie in bestehenden Datenbanken,
    // Kämpfe von vor dem Kampfsystem haben dort null (= PvP, kein Replay).

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "battle_mode", length = 20)
    private BattleMode mode;

    /** 53 Bit, damit der Seed im Frontend als Zahl exakt ankommt. */
    @Column(name = "sim_seed")
    private Long seed;

    private Integer simVersion;

    @Column(name = "arena_id")
    private String arena;

    /** Optische Variante der Arena, {@code null} bei Altbestand (dann die Vorgabe der Arena). */
    @Column(name = "arena_variant", length = 40)
    private String arenaVariant;

    private Integer durationMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Side winnerSide;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "owner", column = @Column(name = "a_owner")),
            @AttributeOverride(name = "beyblade", column = @Column(name = "a_beyblade")),
            @AttributeOverride(name = "attack", column = @Column(name = "a_attack")),
            @AttributeOverride(name = "defense", column = @Column(name = "a_defense")),
            @AttributeOverride(name = "stamina", column = @Column(name = "a_stamina")),
            @AttributeOverride(name = "dash", column = @Column(name = "a_dash")),
            @AttributeOverride(name = "weight", column = @Column(name = "a_weight")),
            @AttributeOverride(name = "burstResistance", column = @Column(name = "a_burst_resistance")),
            @AttributeOverride(name = "fusionWheelId", column = @Column(name = "a_fusion_wheel_id")),
            @AttributeOverride(name = "spinTrackId", column = @Column(name = "a_spin_track_id")),
            @AttributeOverride(name = "tipId", column = @Column(name = "a_tip_id")),
            @AttributeOverride(name = "accent", column = @Column(name = "a_accent")),
            @AttributeOverride(name = "launchQuality", column = @Column(name = "a_launch_quality")),
            @AttributeOverride(name = "launchHits", column = @Column(name = "a_launch_hits")),
            @AttributeOverride(name = "launchCores", column = @Column(name = "a_launch_cores")),
            @AttributeOverride(name = "launchLandingX", column = @Column(name = "a_launch_landing_x")),
            @AttributeOverride(name = "launchLandingY", column = @Column(name = "a_launch_landing_y"))
    })
    private FighterSnapshot fighterA;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "owner", column = @Column(name = "b_owner")),
            @AttributeOverride(name = "beyblade", column = @Column(name = "b_beyblade")),
            @AttributeOverride(name = "attack", column = @Column(name = "b_attack")),
            @AttributeOverride(name = "defense", column = @Column(name = "b_defense")),
            @AttributeOverride(name = "stamina", column = @Column(name = "b_stamina")),
            @AttributeOverride(name = "dash", column = @Column(name = "b_dash")),
            @AttributeOverride(name = "weight", column = @Column(name = "b_weight")),
            @AttributeOverride(name = "burstResistance", column = @Column(name = "b_burst_resistance")),
            @AttributeOverride(name = "fusionWheelId", column = @Column(name = "b_fusion_wheel_id")),
            @AttributeOverride(name = "spinTrackId", column = @Column(name = "b_spin_track_id")),
            @AttributeOverride(name = "tipId", column = @Column(name = "b_tip_id")),
            @AttributeOverride(name = "accent", column = @Column(name = "b_accent")),
            @AttributeOverride(name = "launchQuality", column = @Column(name = "b_launch_quality")),
            @AttributeOverride(name = "launchHits", column = @Column(name = "b_launch_hits")),
            @AttributeOverride(name = "launchCores", column = @Column(name = "b_launch_cores")),
            @AttributeOverride(name = "launchLandingX", column = @Column(name = "b_launch_landing_x")),
            @AttributeOverride(name = "launchLandingY", column = @Column(name = "b_launch_landing_y"))
    })
    private FighterSnapshot fighterB;

    /** Liga-Punkte-Änderung, {@code null} bei Altbestand oder für die KI-Seite. */
    private Integer ratingDeltaWinner;
    private Integer ratingDeltaLoser;

    private Instant playedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public Long getId() {
        return id;
    }

    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }
    public User getLoser() { return loser; }
    public void setLoser(User loser) { this.loser = loser; }
    public String getWinnerBeyblade() { return winnerBeyblade; }
    public void setWinnerBeyblade(String winnerBeyblade) { this.winnerBeyblade = winnerBeyblade; }
    public String getLoserBeyblade() { return loserBeyblade; }
    public void setLoserBeyblade(String loserBeyblade) { this.loserBeyblade = loserBeyblade; }
    public FinishType getFinish() { return finish; }
    public void setFinish(FinishType finish) { this.finish = finish; }
    public int getRounds() { return rounds; }
    public void setRounds(int rounds) { this.rounds = rounds; }
    public Instant getPlayedAt() { return playedAt; }
    public BattleMode getMode() { return mode; }
    public void setMode(BattleMode mode) { this.mode = mode; }
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
    public Integer getSimVersion() { return simVersion; }
    public void setSimVersion(Integer simVersion) { this.simVersion = simVersion; }
    public String getArena() { return arena; }
    public void setArena(String arena) { this.arena = arena; }
    public String getArenaVariant() { return arenaVariant; }
    public void setArenaVariant(String arenaVariant) { this.arenaVariant = arenaVariant; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Side getWinnerSide() { return winnerSide; }
    public void setWinnerSide(Side winnerSide) { this.winnerSide = winnerSide; }
    public FighterSnapshot getFighterA() { return fighterA; }
    public void setFighterA(FighterSnapshot fighterA) { this.fighterA = fighterA; }
    public FighterSnapshot getFighterB() { return fighterB; }
    public void setFighterB(FighterSnapshot fighterB) { this.fighterB = fighterB; }

    public Integer getRatingDeltaWinner() { return ratingDeltaWinner; }
    public void setRatingDeltaWinner(Integer ratingDeltaWinner) { this.ratingDeltaWinner = ratingDeltaWinner; }
    public Integer getRatingDeltaLoser() { return ratingDeltaLoser; }
    public void setRatingDeltaLoser(Integer ratingDeltaLoser) { this.ratingDeltaLoser = ratingDeltaLoser; }

    public FighterSnapshot fighter(Side side) {
        return side == Side.A ? fighterA : fighterB;
    }
}
