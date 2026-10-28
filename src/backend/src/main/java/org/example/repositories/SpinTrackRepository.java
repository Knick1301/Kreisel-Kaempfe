package org.example.repositories;

import org.example.entities.parts.SpinTrack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpinTrackRepository extends JpaRepository<SpinTrack, Long> {
    Optional<SpinTrack> findByName(String name);
    List<SpinTrack> findByActiveTrue();
}
