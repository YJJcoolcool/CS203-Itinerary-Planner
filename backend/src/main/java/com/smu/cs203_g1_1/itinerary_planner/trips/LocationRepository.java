package com.smu.cs203_g1_1.itinerary_planner.trips;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    // will provide basic crud like save findall findbyid etc....
}
