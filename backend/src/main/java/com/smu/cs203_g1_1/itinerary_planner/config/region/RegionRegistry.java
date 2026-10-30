package com.smu.cs203_g1_1.itinerary_planner.config.region;

import java.io.IOException;
import java.io.InputStream;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the bundled {@code regions.json} once at startup and resolves regions
 * by ISO-3166-1 alpha-2 code or by coordinate.
 *
 * <p>Region configuration is release data, not user data, so the JSON resource
 * is the single source of truth. Invalid configuration fails startup rather
 * than surfacing as wrong behaviour at request time. Provider ids are parsed but
 * deliberately not resolved here; that validation belongs to the provider
 * catalog.</p>
 */
@Component
public class RegionRegistry {

    private static final String RESOURCE = "regions.json";

    private final Map<String, RegionProfile> byCode;
    private final List<RegionProfile> all;
    private final RegionProfile fallback;

    public RegionRegistry() {
        this(openResource());
    }

    /**
     * Test seam: parse an arbitrary stream with the production validation rules.
     */
    RegionRegistry(InputStream input) {
        RegionFile file = parse(input);

        if (file.regions() == null || file.regions().isEmpty()) {
            throw new IllegalStateException("'" + RESOURCE + "' contains no regions");
        }
        if (file.fallback() == null) {
            throw new IllegalStateException("'" + RESOURCE + "' has no fallback region");
        }

        Map<String, RegionProfile> profiles = new LinkedHashMap<>();
        for (RegionDto region : file.regions()) {
            RegionProfile profile = toProfile(region);

            if (profiles.putIfAbsent(profile.code(), profile) != null) {
                throw new IllegalStateException(
                    "Duplicate region code in '" + RESOURCE + "': " + profile.code());
            }
        }

        this.byCode = Map.copyOf(profiles);
        this.all = List.copyOf(profiles.values());
        this.fallback = toFallback(file.fallback());
    }

    public Optional<RegionProfile> byCode(String isoCode) {
        if (isoCode == null || isoCode.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(byCode.get(isoCode.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * @return the first configured region whose bounding box contains the point
     */
    public Optional<RegionProfile> byPoint(double latitude, double longitude) {
        return all.stream()
            .filter(region -> region.bboxes().stream()
                .anyMatch(bbox -> bbox.contains(latitude, longitude)))
            .findFirst();
    }

    public List<RegionProfile> all() {
        return all;
    }

    public RegionProfile fallback() {
        return fallback;
    }

    private static InputStream openResource() {
        try {
            return new ClassPathResource(RESOURCE).getInputStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read '" + RESOURCE + "'", ex);
        }
    }

    private static RegionFile parse(InputStream input) {
        if (input == null) {
            throw new IllegalStateException("'" + RESOURCE + "' input stream is null");
        }

        try (InputStream in = input) {
            return new ObjectMapper().readValue(in, RegionFile.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse '" + RESOURCE + "'", ex);
        }
    }

    private static RegionProfile toProfile(RegionDto dto) {
        if (dto == null) {
            throw new IllegalStateException("'" + RESOURCE + "' contains a null region");
        }

        String code = normalizeCode(dto.code());
        ZoneId zone = parseZone(dto.defaultZone(), "region " + code);
        validateTiles(dto.tiles(), "region " + code);

        List<BoundingBox> bboxes = dto.bboxes() == null ? List.of() : dto.bboxes();
        try {
            bboxes.forEach(BoundingBox::validate);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "Invalid bounding box for region " + code, ex);
        }

        RegionTier tier;
        try {
            tier = RegionTier.fromJson(dto.tier());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "Invalid tier for region " + code + ": " + dto.tier(), ex);
        }

        return new RegionProfile(
            code,
            dto.name(),
            tier,
            dto.locale(),
            dto.currency(),
            zone,
            bboxes,
            dto.providers(),
            dto.tiles()
        );
    }

    private static RegionProfile toFallback(FallbackDto dto) {
        ZoneId zone = parseZone(dto.defaultZone(), "fallback region");
        validateTiles(dto.tiles(), "fallback region");

        RegionTier tier;
        try {
            tier = RegionTier.fromJson(dto.tier());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "Invalid tier for fallback region: " + dto.tier(), ex);
        }

        return new RegionProfile(
            null,
            null,
            tier,
            dto.locale(),
            dto.currency(),
            zone,
            List.of(),
            dto.providers(),
            dto.tiles()
        );
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Region code is required in '" + RESOURCE + "'");
        }

        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2}")) {
            throw new IllegalStateException(
                "Region code must be two letters in '" + RESOURCE + "': " + code);
        }

        return normalized;
    }

    private static ZoneId parseZone(String zoneId, String owner) {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalStateException("Missing defaultZone for " + owner);
        }

        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                "Invalid defaultZone '" + zoneId + "' for " + owner, ex);
        }
    }

    private static void validateTiles(TileConfig tiles, String owner) {
        if (tiles == null) {
            throw new IllegalStateException("Missing tiles for " + owner);
        }
        if (tiles.crs() == null || !tiles.crs().equalsIgnoreCase("wgs84")) {
            throw new IllegalStateException(
                "Unsupported tiles.crs '" + tiles.crs() + "' for " + owner
                    + "; only wgs84 is supported");
        }
    }

    private record RegionFile(List<RegionDto> regions, FallbackDto fallback) {
    }

    private record RegionDto(
        String code,
        String name,
        String tier,
        String locale,
        String currency,
        String defaultZone,
        List<BoundingBox> bboxes,
        Map<String, String> providers,
        TileConfig tiles
    ) {
    }

    private record FallbackDto(
        String tier,
        String locale,
        String currency,
        String defaultZone,
        Map<String, String> providers,
        TileConfig tiles
    ) {
    }
}
