package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceScoreRepository extends JpaRepository<PlaceScore, PlaceScoreId> {
    List<PlaceScore> findByLocationIdIn(Collection<String> locationIds);
}
