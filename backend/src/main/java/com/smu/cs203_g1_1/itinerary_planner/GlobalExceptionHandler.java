package com.smu.cs203_g1_1.itinerary_planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smu.cs203_g1_1.itinerary_planner.trips.LocationNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<Void> handleLocationNotFound(LocationNotFoundException e) {
        log.warn("Location not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }
}