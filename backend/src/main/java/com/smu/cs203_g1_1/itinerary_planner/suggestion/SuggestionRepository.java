package com.smu.cs203_g1_1.itinerary_planner.suggestion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, String> {

    List<Suggestion> findByTripIdOrderByCreatedAtDesc(String tripId);

    boolean existsByActivityIdAndStatus(String activityId, SuggestionStatus status);
}
