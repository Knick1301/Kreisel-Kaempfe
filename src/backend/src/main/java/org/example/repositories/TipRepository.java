package org.example.repositories;

import org.example.entities.parts.Tip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipRepository extends JpaRepository<Tip, Long> {
    Optional<Tip> findByName(String name);
    List<Tip> findByActiveTrue();
}
