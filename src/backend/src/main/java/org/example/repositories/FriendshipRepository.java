package org.example.repositories;

import org.example.entities.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("select f from Friendship f where f.requester.username = :u or f.addressee.username = :u")
    List<Friendship> involving(@Param("u") String username);

    @Query("select f from Friendship f where (f.requester.username = :a and f.addressee.username = :b)"
            + " or (f.requester.username = :b and f.addressee.username = :a)")
    Optional<Friendship> between(@Param("a") String a, @Param("b") String b);
}
