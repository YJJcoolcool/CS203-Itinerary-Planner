package com.smu.cs203_g1_1.itinerary_planner;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smu.cs203_g1_1.itinerary_planner.trips.LocationNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<Void> handleLocationNotFound(LocationNotFoundException e) {
        return ResponseEntity.notFound().build();
    }
}