package org.example.entities;

import jakarta.persistence.*;
import org.example.game.FinishType;

import java.time.Instant;

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

    @Enumerated(EnumType.STRING)
    private FinishType finish;

    private int rounds;

    private Instant playedAt = Instant.now();

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
}
