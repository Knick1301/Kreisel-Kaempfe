package org.example.repositories;

import org.example.entities.Beyblade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeybladeRepository extends JpaRepository<Beyblade, Long> {
    List<Beyblade> findByOwnerId(Long id);
    Optional<Beyblade> findByOwnerIdAndName(Long id, String name);
}
