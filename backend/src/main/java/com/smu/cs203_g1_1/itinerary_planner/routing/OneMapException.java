package com.smu.cs203_g1_1.itinerary_planner.routing;

import org.springframework.http.HttpStatus;

public class OneMapException extends RuntimeException {

    private final HttpStatus status;

    public OneMapException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public OneMapException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}