package org.example.repositories;

import org.example.entities.BattleResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BattleResultRepository extends JpaRepository<BattleResult, Long> {

    // Kämpfe gegen KI-Gegner zählen nicht für die Rangliste. mode = null ist Altbestand und damit PvP.

    @Query("select r.winner.username, count(r) from BattleResult r "
            + "where r.mode is null or r.mode <> org.example.game.BattleMode.BOT group by r.winner.username")
    List<Object[]> countWinsByUser();

    @Query("select r.loser.username, count(r) from BattleResult r "
            + "where r.mode is null or r.mode <> org.example.game.BattleMode.BOT group by r.loser.username")
    List<Object[]> countLossesByUser();

    @Query("select r.winner.username, count(r) from BattleResult r group by r.winner.username")
    List<Object[]> countAllWinsByUser();

    @Query("select r.loser.username, count(r) from BattleResult r group by r.loser.username")
    List<Object[]> countAllLossesByUser();

    @Query("select count(r) from BattleResult r "
            + "where r.mode = org.example.game.BattleMode.BOT and r.winner.username = :username")
    long countBotWins(String username);

    @Query("select count(r) from BattleResult r "
            + "where r.mode = org.example.game.BattleMode.BOT and r.loser.username = :username")
    long countBotLosses(String username);

    @Query("select r from BattleResult r where r.winner.username = :username or r.loser.username = :username "
            + "order by r.playedAt desc, r.id desc")
    List<BattleResult> findForUser(String username);
}
