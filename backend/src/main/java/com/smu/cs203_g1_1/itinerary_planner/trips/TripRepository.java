package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    List<Trip> findDistinctByOwnerIdOrUsersId(String ownerId, String memberId);

    List<Trip> findByStartDate(LocalDate date);
}
