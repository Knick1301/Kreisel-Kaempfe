package org.example.repositories;

import org.example.entities.AuthToken;
import org.example.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AuthToken t where t.user = :user")
    void deleteByUser(User user);
}
