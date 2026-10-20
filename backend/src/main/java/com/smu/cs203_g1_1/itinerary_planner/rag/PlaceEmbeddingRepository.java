package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceEmbeddingRepository extends JpaRepository<PlaceEmbedding, String> {

    List<PlaceEmbedding> findByLocationIdIn(Collection<String> locationIds);
}
