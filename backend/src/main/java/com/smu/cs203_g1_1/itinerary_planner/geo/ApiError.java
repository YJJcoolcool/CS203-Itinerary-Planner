package com.smu.cs203_g1_1.itinerary_planner.geo;

/** Uniform error body ({@code error}, {@code message}) produced by the geo exception handlers. */
public record ApiError(String error, String message) {
}
