package org.example.repositories;

import org.example.entities.BattleResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BattleResultRepository extends JpaRepository<BattleResult, Long> {

    @Query("select r.winner.username, count(r) from BattleResult r group by r.winner.username")
    List<Object[]> countWinsByUser();

    @Query("select r.loser.username, count(r) from BattleResult r group by r.loser.username")
    List<Object[]> countLossesByUser();

    @Query("select r from BattleResult r where r.winner.username = :username or r.loser.username = :username "
            + "order by r.playedAt desc, r.id desc")
    List<BattleResult> findForUser(String username);
}
