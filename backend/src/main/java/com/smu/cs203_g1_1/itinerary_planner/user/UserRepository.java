package com.smu.cs203_g1_1.itinerary_planner.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByClerkUserId(String clerkUserId);
    Optional<User> findById(Long id);

}
