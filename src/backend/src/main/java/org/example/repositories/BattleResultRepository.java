package org.example.repositories;

import org.example.entities.BattleResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BattleResultRepository extends JpaRepository<BattleResult, Long> {
}
