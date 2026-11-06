package org.example.repositories;

import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.Tip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FusionWheelRepository extends JpaRepository<FusionWheel, Long> {
    Optional<FusionWheel> findByName(String name);
    List<FusionWheel> findByActiveTrue();
}
