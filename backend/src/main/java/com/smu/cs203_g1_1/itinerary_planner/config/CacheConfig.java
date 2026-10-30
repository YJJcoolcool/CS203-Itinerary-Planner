package com.smu.cs203_g1_1.itinerary_planner.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Declares the Caffeine-backed caches used for OpenStreetMap lookups. Place caches are
 * long-lived (OSM data changes slowly); the POI cache is short-lived because Overpass
 * results are more volatile.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String GEOCODE_SEARCH = "geocodeSearch";
    public static final String GEOCODE_REVERSE = "geocodeReverse";
    public static final String OSM_LOOKUP = "osmLookup";
    public static final String POIS = "pois";
    public static final String ROUTES = "routes";

    /**
     * Builds the cache manager wiring each cache name to its Caffeine spec: the three
     * place caches live 30 days / 10k entries, the POI cache lives 1 hour / 5k entries.
     */
    @Bean
    public CacheManager cacheManager() {
        List<Cache> caches = List.of(
            new CaffeineCache(GEOCODE_SEARCH, placeCache()),
            new CaffeineCache(GEOCODE_REVERSE, placeCache()),
            new CaffeineCache(OSM_LOOKUP, placeCache()),
            new CaffeineCache(
                POIS,
                Caffeine.newBuilder()
                    .maximumSize(5_000)
                    .expireAfterWrite(Duration.ofHours(1))
                    .build()
            ),
            new CaffeineCache(
                ROUTES,
                Caffeine.newBuilder()
                    .maximumSize(2_000)
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .build()
            )
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        manager.afterPropertiesSet();
        return manager;
    }

    /**
     * Creates the shared Caffeine spec for cached place lookups (10k entries, 30-day write expiry).
     */
    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> placeCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofDays(30))
                .build();
    }
}
