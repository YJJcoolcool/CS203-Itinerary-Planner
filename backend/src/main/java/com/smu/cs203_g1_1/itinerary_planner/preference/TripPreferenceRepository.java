package com.smu.cs203_g1_1.itinerary_planner.preference;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripPreferenceRepository extends JpaRepository<TripPreference, String> {
}
