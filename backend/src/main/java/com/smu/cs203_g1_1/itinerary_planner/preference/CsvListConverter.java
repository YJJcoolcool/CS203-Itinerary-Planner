package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a small string list as a comma-separated column.
 *
 * <p>CSV rather than a Postgres array keeps the mapping portable to the H2 test
 * profile, and preferences are short enough (a dozen tags at most) that a
 * normalised join table would buy nothing.</p>
 */
@Converter
public class CsvListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        String joined = attribute.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .collect(Collectors.joining(SEPARATOR));

        return joined.isEmpty() ? null : joined;
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(dbData.split(SEPARATOR))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
