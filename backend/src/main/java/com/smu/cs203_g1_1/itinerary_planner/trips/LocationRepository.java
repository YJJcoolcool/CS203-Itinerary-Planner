package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, String> {
    // will provide basic crud like save findall findbyid etc....
    Optional<Location> findByOsmTypeAndOsmId(String osmType, Long osmId);

    List<Location> findBySeededTrue();

    @Query("select l from Location l where l.seeded = true and l.latitude between :minLat and :maxLat and l.longitude between :minLon and :maxLon")
    List<Location> findSeededInBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon);

    /** Curated or OSM-sourced places; the same trust rule as the bounding-box query below. */
    @Query("select l from Location l where l.seeded = true or l.osmId is not null")
    List<Location> findTrusted();

    /**
     * Curated or OSM-sourced places in the box; user-created rows (no OSM id) are never candidates.
     */
    @Query("select l from Location l where (l.seeded = true or l.osmId is not null) and l.latitude between :minLat and :maxLat and l.longitude between :minLon and :maxLon")
    List<Location> findTrustedInBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon);

    /**
     * Same trust and box rules as {@link #findTrustedInBoundingBox}, restricted
     * to one ISO country so a neighbouring country's rows cannot leak in.
     */
    @Query("select l from Location l where (l.seeded = true or l.osmId is not null) and l.latitude between :minLat and :maxLat and l.longitude between :minLon and :maxLon and l.countryCode = :countryCode")
    List<Location> findTrustedInBoundingBoxAndCountry(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon,
            @Param("countryCode") String countryCode);
}
