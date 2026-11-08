package com.smu.cs203_g1_1.itinerary_planner.friends;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    // The pair's row in either direction.
    @Query("select f from Friendship f where (f.requester.id = :a and f.addressee.id = :b)"
            + " or (f.requester.id = :b and f.addressee.id = :a)")
    Optional<Friendship> findBetween(@Param("a") String a, @Param("b") String b);

    @Query("select f from Friendship f where f.status = :status"
            + " and (f.requester.id = :userId or f.addressee.id = :userId)")
    List<Friendship> findInvolving(@Param("userId") String userId, @Param("status") Friendship.Status status);

    // Incoming requests, newest first.
    List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(String addresseeId, Friendship.Status status);
}
