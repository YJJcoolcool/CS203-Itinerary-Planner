# Multi-Country Support — Implementation Plan

Status: Phase 1 implemented
Branch: `feat/multi-country-phase1`
Scope: Phase 1 (detailed), Phase 2 (outline), Phase 3 (out of scope)

## 1. Goal and non-goals

**Phase 1 goal.** A trip in a non-SG country works end to end: geocoding, POI
discovery, planner with a non-empty candidate set (live-OSM backfill, D7), manual
itinerary, members, and suggestions behave correctly. Weather and turn-by-turn
routing degrade to *explicitly unavailable* rather than silently reporting the
wrong thing. Singapore behaviour is byte-for-byte unchanged, and **Singapore
remains the default country** for any trip that does not specify one.

**Scope contraction (product decision).** One trip has exactly **one** country.
The existing `countries[]` list is retired in favour of a single
`trips.country_code` (D0). Multi-country trips are explicitly out of scope for
Phases 1–3.

**Non-goals (Phase 1).** No new weather provider, no second routing provider, no
public-transport routing abroad, no per-country offline indexes, no UI redesign,
no full i18n/translation, no multi-country trips.

**Phase 2** adds real weather providers (Open-Meteo / OpenWeather) and a routing
provider seam with OSRM/ORS/Google. **Phase 3** (per-city GTFS public transport)
is deliberately out of scope — it is open-ended and priced per metro.

## 2. Current state (verified)

Already multi-country by accident, keep it:

| Asset | Location | Note |
|---|---|---|
| `WeatherSource` seam | `weather/WeatherSource.java:16` | `isRainy(lat, lon, from, to)` — already geo-neutral |
| `Trip.countries` | `trips/Trip.java:99` | `trip_countries` table, validated two-letter codes at `:185-205` |
| Country-aware geocoding | `geo/GeoController.java:85-105` | `countrycodes` param, derived from trip |
| Planner | `planner/CandidateFinder`, `ScoreCalculator` | pure bbox/Haversine, no SG assumptions |
| RAG ranking | `rag/*` | coordinate-general except default query text |
| Nominatim/Overpass | `geo/NominatimClient`, `geo/OverpassClient` | generic |

Singapore-coupled, must change:

| # | Coupling | Location |
|---|---|---|
| 1 | SG bbox constants (duplicated) | `weather/NeaWeatherSource.java:71-74`, `seed/OsmIndexImporter.java:45-48` |
| 2 | `Asia/Singapore` literals | `config/ClockConfig.java:18`, `weather/NeaWeatherSource.java:67`, `polling/WeatherPoller.java:53,74`, `routing/RoutingService.java:24`, frontend weather panels |
| 3 | "must contain SG" gate | `suggestion/SuggestionService.java:168-175,252` |
| 4 | NEA-specific feed semantics | `NeaWeatherSource` (2h area feed / national 4-day), `services/weather/NEAWeatherClient` |
| 5 | No routing seam | `routing/RoutingService` depends on `OneMapClient` directly |
| 6 | SG-only seed data | `resources/seed/sg_pois.json`, `scripts/build-sg-pois.sh`, `OsmIndexImporter:274` appends `"Singapore "` |
| 7 | `location` has no country | `db/migration/V1__init.sql:60-75` |
| 8 | SG default copy | `rag/QueryTextBuilder.java:34`, `resources/prompts/choose-alternative.txt:1`, frontend panel titles |
| 9 | Hardcoded OSM tile URL | `components/locations/ManualLocationModal.tsx:331`, `components/trips/ActivityRouteMap.tsx:79` |
| 10 | Planner reads DB only | `CandidateFinder.java:80` → `LocationRepository:32`; empty outside seeded regions |

## 3. Architecture decisions

### D0 — One trip, one country; Singapore is the default

`Trip.countries: List<String>` collapses to `Trip.countryCode: String`. The
`trip_countries` join table is retired.

- **Default:** a trip created without a country gets `sg`. This preserves every
  existing behaviour and matches what the client already does
  (`CreateTripModal.tsx:41` initialises `['sg']`).
- **Validation:** exactly one ISO-3166-1 alpha-2 code; reject empty on create,
  refuse to clear it on update.
- **Why now:** with one country per trip, region resolution is unambiguous, the
  weather gate becomes `trip.countryCode`, cross-border candidate bleed is
  solved by a single filter, and the UI loses a multi-select it never needed.

Migration (two-step, so a rollback stays cheap):

1. `V11` adds `trips.country_code varchar(2)` and backfills:
   `UPDATE trips SET country_code = COALESCE((SELECT min(country_code) FROM trip_countries tc WHERE tc.trip_id = trips.id), 'sg') WHERE country_code IS NULL;`
   then `ALTER COLUMN ... SET NOT NULL`. `min()` is deterministic; with the ≤1
   row payload today it is simply "the" country.
2. Keep `trip_countries` **unused** for one release, then drop it in a later
   migration once the deploy is confirmed.

Rejected alternative: keep the list table and constrain length to 1. It leaves
every call site (`GeoController`, `TripController`, `TripService`, the frontend
types) still handling a list while the domain says otherwise.

### D1 — Region profiles are configuration

Introduce a bundled `regions.json` plus a `RegionRegistry`. A region is keyed by
ISO-3166-1 alpha-2 and carries: display name, tier, bounding boxes, IANA zone,
locale, currency, provider keys and basemap.

**Resolution is trip-first, coordinate-second.** With D0, `trip.countryCode`
identifies the region directly, so weather/routing/UI all resolve from the trip.
`byPoint(lat, lon)` is retained for the two places that genuinely have only a
coordinate: the seed importer's bounds check and the NEA `covers()` guard (a
MY-border point must never be answered by NEA just because it falls inside the
SG bbox).

Rejected alternatives:
- *JPA entity / admin-editable table* — premature; region config is release
  data, not user data.
- *Fetching boundaries from Nominatim at runtime* — network dependency on a hot
  path, and bbox is all we need.

### D2 — Missing provider is not "no rain"

Today every weather failure path returns `false`, conflating "no data" with
"dry". Abroad that is a silent lie. Phase 1 splits the two:

```java
public interface WeatherSourceResolver {
    Optional<WeatherSource> forPoint(double lat, double lon);
    Optional<WeatherSource> forCountry(String countryCode);
}
```

`WeatherSource` itself does **not** change. Absence is handled *before* the call:
no resolver match → skip with a reason, log it, and expose it as a capability so
the UI hides the panel. `false` keeps meaning "forecast says dry".

### D3 — Time zones are derived, never baked into the global clock

`ClockConfig.clock()` becomes `Clock.systemUTC()`. Instants written to the DB are
unaffected (an `Instant` is zone-free); only `LocalDate.now(clock)` and
`atStartOfDay` call sites need a zone, and those get it from the resolved region.

This is the lowest-risk way to remove the global Singapore pin: no persistence
change, no serde change, and a failing test points straight at the offending
call site. Every current `LocalDate.now(clock)` is audited in Lane B.

Known limitation: countries with multiple zones (US, AU, RU) resolve to the
region's `defaultZone`, correct for the primary travel area only. Phase 2 may add
per-bbox zones in `regions.json`; no code change required.

### D4 — No routing abstraction in Phase 1

OneMap stays behind `RoutingService` unchanged. Extracting a `RoutingProvider`
interface with exactly one implementation is speculative, and the expensive part
(OneMap-shaped response parsing in `frontend/src/lib/routingUtils.ts`, 400 lines,
including PT plan legs) does not get cheaper by adding the interface early.

Phase 1 only adds a **capability check**: routing outside a routing-capable
region returns a typed 422 instead of an upstream error, and the UI hides the
route affordance. The seam lands in Phase 2 alongside the second provider.

### D5 — Capabilities are served, not duplicated in the client

Add `GET /api/regions` returning the supported codes and their capabilities. The
frontend stops hardcoding `countries.includes('sg')` and asks what the trip can
actually do. One source of truth, and it also lets `CountryMultiSelect` mark
unsupported countries instead of accepting them silently.

### D6 — Providers are selected per region, not globally

`app.weather.source` is a single global selector today. That does not scale: SG
needs NEA, the rest of the world needs a global feed, and China may need a
different feed again. A region entry therefore names its providers:

```json
"providers": { "weather": "nea", "routing": "onemap" }
```

`app.weather.source` is retained as the **default** for regions that do not name
one, so `application-dev.properties` (`app.weather.source=none` → fixture) keeps
working untouched. Resolution order: region key → global default → none.

This is a registry lookup, not a plugin loader. Each provider id maps to a bean
implementing `WeatherSource` / (Phase 2) `RoutingProvider`; unknown id fails fast
at startup rather than silently degrading.

### D7 — Cold start is a first-class Phase 1 problem

`CandidateFinder` reads only the local `location` table
(`CandidateFinder:80` → `findTrustedInBoundingBox`, which requires
`seeded = true or osm_id is not null`). Outside SG that table is empty, so a
fresh Malaysia or China destination produces **zero candidates** — the planner
is mechanically correct and practically useless. Phase 1 therefore includes a
live-OSM fallback rather than treating seeding as an ops afterthought.

Fallback shape: when the trusted bbox yields fewer than `N` rows, query Overpass
around the point and persist the results through the existing
`LocationService.findOrCreateFromOsm` path (already idempotent by
`(osmType, osmId)`, already classifies indoor/category/hours). The planner is
then unchanged: it still reads the DB, which now has local rows.

Constraints: public Overpass allows ~25 s per query and is shared; the fallback
must be bounded (top-k, whitelisted categories), cached, and never on a
user-blocking replan path. Preferred trigger is activity creation / trip setup,
which also warms the RAG bucket.

### D8 — Basemap is region configuration

Tile URLs are hardcoded in two components
(`ManualLocationModal.tsx:331`, `ActivityRouteMap.tsx:79`), both pointing at
`https://{s}.tile.openstreetmap.org`. That domain is unreliable in mainland
China and a licensed-provider question there. Region entries carry
`tiles: { url, attribution }`; the components take them from the capability
payload.

Caveat to record in code: Chinese providers serve **GCJ-02**, while OSM and this
app are **WGS-84**. Mixing a GCJ-02 basemap with WGS-84 pins offsets markers by
100–500 m. A region must therefore declare its basemap *and* its coordinate
system together; Phase 1 only supports `wgs84` and rejects a GCJ-02 basemap
until a transform exists.

### D9 — "Supported country" is a tier, not a boolean

Coverage is bounded by OSM data quality and third-party terms, not by our code.
Regions declare a tier so the product can be honest:

| Tier | Meaning | Example | Behaviour |
|---|---|---|---|
| `full` | OSM-strong, weather + routing available | SG, JP, most of EU | all features |
| `partial` | OSM-decent, some providers missing | MY, TH, CN Tier-1 | planner works, degraded panels |
| `minimal` | OSM-thin | parts of Africa, Central Asia | geocoding only, planner sparse |

`GET /api/regions` exposes the tier; the UI uses it to explain *why* something
is unavailable instead of just hiding it.

## 4. Phase 1 work breakdown

Lanes are ordered by dependency. **A must merge before B, C, D, E.**
C is independent of B/D/E. D depends on E's response contract.

### Lane A — Region core (foundation, blocking)

Add:

- `backend/src/main/resources/regions.json`

  ```json
  {
    "regions": [
      {
        "code": "sg",
        "name": "Singapore",
        "tier": "full",
        "locale": "en-SG",
        "currency": "SGD",
        "defaultZone": "Asia/Singapore",
        "bboxes": [{ "minLat": 1.15, "maxLat": 1.48, "minLon": 103.60, "maxLon": 104.10 }],
        "providers": { "weather": "nea", "routing": "onemap" },
        "tiles": {
          "url": "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
          "attribution": "© OpenStreetMap contributors",
          "crs": "wgs84"
        }
      }
    ],
    "fallback": {
      "tier": "partial",
      "locale": "en",
      "currency": null,
      "defaultZone": "UTC",
      "providers": {},
      "tiles": { "crs": "wgs84" }
    }
  }
  ```

  The SG numbers are copied verbatim from the two existing constant blocks, so
  behaviour is preserved by construction. Non-SG regions are added one at a time
  as their data is verified — do not ship entries for countries nobody has
  smoke-tested.

- `config/region/RegionProfile.java` — record: `code`, `name`, `tier`, `locale`,
  `currency`, `defaultZone`, `List<BoundingBox>`, `providers`
  (`Map<Capability,String>`), `tiles` (`TileConfig`).
  `record BoundingBox(double minLat, double maxLat, double minLon, double maxLon)`
  with `boolean contains(double lat, double lon)`.
- `config/region/RegionRegistry.java` — loads `regions.json` at startup
  (`@PostConstruct`, fail fast on bad data), exposes:

  ```java
  Optional<RegionProfile> byCode(String isoCode)
  Optional<RegionProfile> byPoint(double lat, double lon)
  List<RegionProfile> all()
  CapabilitySet capabilitiesForCountries(List<String> codes)
  ```

  `byPoint` = first region whose bbox contains the point; empty otherwise.
  Startup validation: every `providers` id resolves to a known bean, every
  `defaultZone` parses, `crs` is `wgs84` (see D8).
- `config/region/Capability.java` — enum `WEATHER`, `ROUTING`.
- `config/region/ProviderCatalog.java` — id → bean lookup used by the startup
  validation and by Lane B's resolver.
- `config/region/TileConfig.java` — `url`, `attribution`, `crs`.

Modify:

- `config/ClockConfig.java:18` → `Clock.systemUTC()`.

Acceptance: unit tests for `contains`, `byPoint` (inside/outside SG, on the
boundary), unknown code, and malformed-json fail-fast.

### Lane B — Weather and suggestions use regions

Modify `weather/NeaWeatherSource.java`:

- Delete the four bbox constants (`:71-74`). `isInSingapore` becomes
  `regionRegistry.byPoint(...).map(r -> r.code().equals("sg"))`.
  Keep `static boolean isInSingapore` as a `@Deprecated` shim only if tests
  outside this file need it; otherwise update the callers.
- Replace `SINGAPORE` (`:67`) with the resolved region zone; keep `AREA_RESOURCE`
  and the NEA-specific feed logic untouched — it is correct *for SG*.
- Keep `@ConditionalOnProperty(app.weather.source=nea)` and `@Primary` for the
  SG/dev wiring; Lane B's resolver is what actually selects per region.

Add `weather/WeatherSourceResolver` implementation backed by `RegionRegistry` +
`ProviderCatalog`:

- `forPoint(lat, lon)` → `byPoint` → region `providers.weather` → catalog bean.
  No region or no provider → empty.
- `forCountry(code)` → `byCode` → region `providers.weather` → catalog bean.
  No region or no provider → empty. Used by `SuggestionService`, which knows the
  trip's country but not necessarily a coordinate.
- **Coverage hook.** Add a default method to `WeatherSource` (non-breaking):

  ```java
  default boolean covers(double latitude, double longitude) { return true; }
  ```

  `NeaWeatherSource` overrides it with the SG bbox check (from `RegionRegistry`,
  not a constant). `NoRainWeatherSource` returns `false`. This makes "provider
  exists but not here" explicit without changing `isRainy`. The resolver treats
  `covers == false` as no provider.

Modify `application.properties`:

```properties
# Global default only; a region's providers.weather overrides it.
app.weather.source=${APP_WEATHER_SOURCE:nea}
```

Modify `suggestion/SuggestionService.java`:

- Replace `coversSingapore(trip)` (`:168-175`) with
  `weatherSourceResolver.forCountry(trip.getCountryCode()).isPresent()`.
- The "empty countries = legacy SG" branch disappears: `countryCode` is
  non-null and defaults to `sg` (D0), so there is exactly one path.
- At `:252` log the resolved reason (`no weather provider for country jp`)
  rather than naming SG.

Modify `polling/WeatherPoller.java`:

- `SG` constant (`:53`) → resolve per trip: build the day window from the
  region of the trip's activity centroid.
- `@Scheduled` cron zone cannot be per-trip. Keep `zone = "Asia/Singapore"` but
  document that the *tick* is scheduled in SG time while the *window* is
  region-local; hourly ticks make the scheduling zone immaterial. Add a comment
  so nobody "fixes" it later.
- Skip trips whose centroid has no weather-capable region.

Acceptance: `NeaWeatherSourceTest` still passes; new tests for non-SG centroid
(skip, logged) and for a JP trip (no suggestion, no exception).

### Lane C — Data model and seeding

- `db/migration/V11__trip_single_country.sql` — the D0 migration: add
  `trips.country_code`, backfill from `trip_countries` with `'sg'` fallback, set
  `NOT NULL`, index it. `trip_countries` is left in place but unused; its drop is
  a separate later migration.
- `db/migration/V12__location_country_code.sql`

  ```sql
  ALTER TABLE location ADD COLUMN country_code varchar(2);
  UPDATE location SET country_code = 'sg' WHERE country_code IS NULL;
  CREATE INDEX idx_location_country_code ON location (country_code);
  ```

  Backfill to `sg` is correct: every row today originates from the SG seed or SG
  geocoding. Leave the column nullable so rows created from ungeocodable input do
  not fail.

- `trips/Trip.java`: replace `countries: List<String>` + `@ElementCollection`
  with `countryCode: String` (`@Column(name = "country_code", nullable = false,
  length = 2)`). Move the existing two-letter validation (`Trip.java:185-205`)
  onto the single value, and default to `"sg"` when null. Update
  `TripController`, `TripService`, `TripResponse`, and the
  `getTripForMember` callers.
- `seed/OsmIndexImporter.java`: delete the duplicated bbox (`:45-48`), take the
  region from `RegionRegistry` by an injected `app.seed.country` property
  (default `sg`). Replace the hardcoded `"Singapore "` at `:274` with the region
  display name.
- `seed/SgPoiSeed` / `sg_pois.json`: leave as-is; rename only if the seed grows a
  second country (do not rename speculatively).
- `scripts/build-sg-pois.sh`: parameterize `COUNTRY=${1:-sg}` and derive the
  extract URL/filename from it. Keep the SG default so existing docs stay valid.
- `geo/PoiService` / `PlaceDiscoveryService`: set `country_code` on creation from
  the reverse-geocode `address.countryCode()` (already parsed at `PoiService:123`).
- `CandidateFinder`: filter candidates by the **trip's** `countryCode` alongside
  the bbox. With one country per trip this is now unconditional, which removes
  cross-border bleed (JB vs SG) instead of making it a heuristic.

Acceptance: V11 backfills every existing trip (nulls = 0), V12 every location
(nulls = 0); import of a non-SG fixture file lands rows with the right
`country_code`; a SG trip's candidate set is unchanged from `main`.

### Lane C2 — Cold start (live OSM backfill into the local table)

Without this, every non-SG destination has an empty candidate ring (D7).

Add `discovery/NearbyBackfillService`:

```java
int backfill(double lat, double lon, int radiusM, int limit)
```

- Calls the existing generic Overpass path (`PoiService.findPois` or
  `OverpassClient` directly) across the whitelisted categories.
- Persists each hit through `LocationService.findOrCreateFromOsm(osmType, osmId)`
  — reuses the idempotent import that already sets indoor class, category group
  and opening hours. Do **not** write `Location` rows by hand.
- Skips rows already present (the OSM-identity unique constraint handles races).
- Bounded: `limit` ≤ 200, `radius` ≤ 2000 m (same clamps as `/api/geo/pois`),
  one Overpass query per category at most.

Add `discovery/BackfillPolicy`:

- Triggers on activity creation and on trip country change, **not** inside the
  replan hot path.
- Skips when the trusted bbox already has ≥ `app.discovery.backfill-threshold`
  rows (default 40), so SG never pays for it.
- Runs off the request thread (`@Async` + a bounded executor); failures log and
  degrade, never block the write.
- Optional scheduled pre-warm per trip start date (Phase 2 candidate; not needed
  to ship).

Modify `planner/CandidateFinder`:

- After the trusted bbox query returns fewer than the threshold, call
  `NearbyBackfillService.backfill(...)` **once** and re-run the query. This keeps
  the planner DB-only (no network in the pure planning path) while making the
  first Malaysia/China trip non-empty.
- Guard with a per-(point, hour) memo so repeated replans cannot hammer Overpass.

Config:

```properties
app.discovery.backfill-enabled=${APP_DISCOVERY_BACKFILL:true}
app.discovery.backfill-threshold=${APP_DISCOVERY_BACKFILL_THRESHOLD:40}
app.discovery.backfill-radius-m=${APP_DISCOVERY_BACKFILL_RADIUS_M:1500}
app.discovery.backfill-limit=${APP_DISCOVERY_BACKFILL_LIMIT:120}
app.discovery.backfill-max-categories=${APP_DISCOVERY_BACKFILL_MAX_CATEGORIES:4}
app.discovery.backfill-queue-capacity=${APP_DISCOVERY_BACKFILL_QUEUE_CAPACITY:16}
```

**Implementation note (as built).** The first cut called Overpass synchronously
inside `CandidateFinder.find`, which could block a request thread for up to
`4 × app.osm.read-timeout`. That is not acceptable, so the shipped shape is:

- `NearbyBackfillService.backfillAsync(...)` submits to a dedicated 1-thread
  bounded pool (queue capacity above, daemon threads, `@PreDestroy` shutdown).
  A full queue returns `completedFuture(0)`; nothing is ever discarded silently.
- `CandidateFinder` records the memo **before** submitting, submits, and never
  joins. It re-runs the trusted query once immediately, so the very first call
  may still return few rows.
- `ActivityService.createActivity` pre-warms the same way after a successful
  save, which is what makes the first real replan non-empty.

Acceptance (as built): `CandidateFinderBackfillTest` proves one submission per
~100 m cell per policy window, record-before-submit ordering, no second
submission, and that `find` returns while the future is still pending. SG path:
threshold already met, zero Overpass submissions.

### Lane D — Frontend

- `lib/api.ts`: add `getRegions()` and the region/capability types.
- New `lib/useTripCapabilities.ts`: given a trip, returns
  `{ hasWeather, hasRouting, locale, timeZone }` from `/api/regions`.
- `components/trips/WeatherForecastPanel.tsx`:
  - Replace `includesSingapore` (`:72-77,117,119`) with `hasWeather`.
  - Replace `en-SG`/`Asia/Singapore` (`:23,32,46-47`) with the capability locale
    and zone. Keep a default of `en-SG` when the trip is SG so nothing changes
    visually.
  - Titles at `:181-182` and the "· Singapore time" suffix at `:240` become
    region-name driven; for SG they render identically.
- `components/trips/TwoHourWeatherPanel.tsx`: same treatment (`:7-9,87,114`).
- `components/trips/ActivityRoute.tsx`: hide/disable route controls when
  `!hasRouting`; show a plain "Directions aren't available for this destination"
  line rather than an error from the backend.
- `components/locations/ManualLocationModal.tsx:27-28`: default center from the
  trip's first activity (or the geocoded trip destination) instead of
  1.3521/103.8198; fall back to the current values only when the trip is SG.
- `components/locations/ManualLocationModal.tsx:330-331` and
  `components/trips/ActivityRouteMap.tsx:78-79`: take `tileUrl`/`attribution`
  from the trip capability payload (`D8`). Default to the current OSM URL so SG
  is unchanged. Reject a `crs` other than `wgs84` with a visible "map unavailable
  for this destination" state rather than rendering offset pins.
- `components/trips/CountryMultiSelect.tsx` → `CountrySelect.tsx`: single-select,
  pre-selected `sg` when creating a trip, no "Add another country" affordance.
  Keep the searchable list; mark codes whose tier is not `full` with a hint.
- Types and call sites follow D0: `types/trip.ts:53-54` (`countries: string[]` →
  `country: string`), `CreateTripModal.tsx:41,80-98,200-213`
  (`useState<string>('sg')`), `TripSettingsPanel.tsx:261-336`,
  `TripDetailsCard.tsx:34`, `WeatherForecastPanel.tsx:16,72-77`,
  `lib/api.ts:161-169` (`countries` → `country` in the update payload).
  Do this as one mechanical pass; do not leave both shapes alive.

Acceptance: an SG trip renders identically; a JP trip hides weather, hides route
actions, and shows JP-appropriate dates; trip creation defaults to Singapore and
requires exactly one country.

### Lane E — API surface

- `geo/RegionController.java` — `GET /api/regions`:

  ```json
  [{ "code": "sg", "name": "Singapore", "tier": "full", "locale": "en-SG",
     "currency": "SGD", "defaultZone": "Asia/Singapore",
     "capabilities": ["weather", "routing"],
     "tiles": { "url": "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
                "attribution": "© OpenStreetMap contributors", "crs": "wgs84" } }]
  ```

  Public (no auth) or authenticated — match the existing auth convention in
  `SecurityConfig`; prefer authenticated for consistency. Keep the payload small:
  this is fetched once and cached; do not include bboxes.
- `routing/RoutingController`: before invoking OneMap, check the trip's region
  supports `ROUTING`; otherwise throw a typed `RoutingUnavailableException`
  mapped to `422` with `{ "error": "routing_unavailable", "country": "jp" }`.
- `routing/RoutingService`: resolves the departure date/time zone from the
  trip's region (coupling #2), replacing the hardcoded Singapore zone.
- `geo/GeoController.java:100-105`: replace the `countries` list join with
  `trip.getCountryCode()` when `tripId` is supplied (the explicit
  `countrycodes` query param stays for ad-hoc search).
- `services/weather/WeatherController`: unchanged signatures. When no provider
  covers the window, return `200` with `[]` (already tolerated by the client) and
  let capabilities drive visibility.

Acceptance: contract tests for `/api/regions` and for 422 on a JP trip.

### Lane F — Tests

- Keep every existing test green. The only expected edits are the ones that
  reference the moved bbox constants.
- New: `RegionRegistryTest`, `WeatherSourceResolverTest`,
  `NeaWeatherSourceRegionTest` (point outside SG but inside another region must
  not be answered by NEA), `SuggestionServiceScopeTest` (`countryCode = "jp"` =
  skipped with reason; `"sg"` = runs), `TripCountryCodeTest` (default `sg`, two-
  letter validation, cannot be cleared), `RoutingUnavailableTest`.
- Fixtures: no network. `FixtureWeatherSource` already exists; add a
  `regions-test.json` only if the production file cannot express a case.

## 5. Write-scope map (for parallel execution)

| Lane | Owns | Conflicts with |
|---|---|---|
| A | `config/region/**`, `resources/regions.json`, `config/ClockConfig.java` | — blocks all |
| B | `weather/**`, `services/weather/**`, `suggestion/SuggestionService.java`, `polling/WeatherPoller.java` | A |
| C | `db/migration/V11*, V12*`, `trips/Trip.java`, `trips/TripService.java`, `trips/TripController.java`, `seed/**`, `scripts/build-sg-pois.sh`, `geo/PoiService.java` | A, E (`GeoController`) |
| C2 | `discovery/NearbyBackfillService.java`, `discovery/BackfillPolicy.java`, `planner/CandidateFinder.java` | C (`PoiService`), A |
| D | `frontend/src/**` | E (contract) |
| E | `geo/RegionController.java`, `routing/RoutingController.java`, `geo/GeoController.java` | A, C (`Trip` accessors) |
| F | `backend/src/test/**` | all, but additive |

Sequence: **A → (B ∥ C ∥ E) → (C2 ∥ D) → F**. C's `Trip.countryCode` rename must
land before E reads it and before D's types change; C2 needs C's `country_code`
plumbing and B's resolver types. F runs continuously as each lane lands.

## 6. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Global clock change alters stored timestamps | Data corruption | `Clock.systemUTC()` changes only `LocalDate`/`atStartOfDay` derivation; audit all `clock` usages with grep before merge; DB stores instants |
| Existing tests assume SG local date | CI red | Grep `LocalDate.now` + fixed-clock tests in `SuggestionService`/poller; adjust expectations, not the clock |
| "No provider" silently suppressing suggestions | Missed feature, invisible | Resolver + explicit log + capability flag in UI; test asserts the log path |
| Multi-zone countries resolve to one zone | Wrong day boundaries for US/AU | Documented limitation; `regions.json` can carry per-bbox zones later |
| `trips.country_code` backfill picks the wrong row for a multi-country trip | Misclassified trip | `min(country_code)` is deterministic; list trips with >1 row beforehand and spot-check the result |
| `location.country_code` backfill wrong for any non-SG row | Misclassification | Verify against prod row counts first; backfill is a data assertion, run it as a query before the migration |
| Dropping `trip_countries` too early | Irreversible if rollback needed | Two-step: V11 only adds/backfills; the drop ships in a later migration after one confirmed release |
| NEA claiming coverage outside SG | Wrong weather abroad | `covers()` override + explicit cross-region test |
| Overpass backfill hammers the public instance | 429/timeouts, slow writes | Trigger at activity creation, not replan; threshold gate; per-point memo; bounded radius/limit; configurable to disable |
| Nominatim public ToS (~1 req/s, no bulk) | Multi-country traffic gets blocked | Base URLs already configurable (`app.osm.*-base-url`); plan self-hosted Nominatim + Overpass before any load test |
| OSM coverage varies wildly by country | Planner looks broken in thin regions | Region `tier` exposed via API; UI explains "limited data" instead of showing an empty list |
| Chinese providers use GCJ-02 vs our WGS-84 | Pins offset 100–500 m | A region declares basemap and CRS together; Phase 1 accepts `wgs84` only and refuses a GCJ-02 basemap |
| Adding regions nobody smoke-tested | Silent quality regressions | Ship `regions.json` entries one country at a time, gated by the Lane F tests + manual smoke |

## 7. Verification plan

**Automated.**

1. `./mvnw test` green with SG defaults unchanged.
2. New tests listed in Lane F.
3. Migration test: apply V11 and V12 to a dump-shaped DB; assert
   `trips.country_code` nulls = 0, all legacy rows became `sg`,
   `location.country_code` nulls = 0, and both indexes exist.
4. Contract test for `GET /api/regions` and the 422 routing response.
5. API contract test: create a trip without `country` → response is `sg`;
   create/update with `"JP"` → stored `jp`; attempt to clear → 400.

**Manual smoke (no network beyond local services).**

1. SG trip: create, add activity, weather panel shows NEA two-hour forecast,
   route button works. Must be pixel-identical to `main`.
2. JP trip (`country="jp"`): search "Shibuya" (Nominatim), add activity,
   planner suggests nearby candidates **after backfill**, weather panel absent,
   route action absent.
3. Trip with no country supplied: created as `sg` and behaves like SG
   (backward-compat check).
4. Cross-border check: SG trip with an activity at a JB coordinate → the
   `country_code` filter excludes MY candidates; no NEA answer outside the SG
   bbox.
5. Dev profile: `app.weather.source=none` still selects the fixture and nothing
   regresses.
6. Cold start: fresh DB, KL/Beijing coordinate, `app.discovery.backfill-enabled=true`
   → first `CandidateFinder` call persists rows and returns candidates; second
   call makes no Overpass request; SG trip makes zero Overpass requests.
7. Backfill disabled (`APP_DISCOVERY_BACKFILL=false`) → request succeeds with an
   empty candidate list, no error.

## 8. Country coverage notes

Coverage is bounded by OSM data quality and provider terms, not by this design.
Use these tiers when deciding whether to add a `regions.json` entry:

| Tier | Meaning | Examples | What works |
|---|---|---|---|
| `full` | OSM-strong, weather + routing wired | SG, JP, most of EU, KR, TW | everything |
| `partial` | OSM-decent or provider gaps | MY, TH, VN, ID, CN Tier-1 | planner + backfill; weather/routing may be absent |
| `minimal` | OSM-thin | much of Africa, Central Asia, rural anywhere | geocoding only; planner sparse even after backfill |

Country-specific notes worth keeping:

- **Malaysia**: OSM decent around KL/Penang/JB. Cross-border bleed from SG is
  handled by the unconditional `country_code` candidate filter (D0 makes it
  correct, not heuristic), but the SG bbox still overlaps JB — so `byPoint` is
  only used for coverage, never for trip-level resolution.
- **China**: OSM dense in Tier-1, thin elsewhere; names usually Chinese-only, so
  search input must be Chinese. OSM tile hosts are unreliable there and Chinese
  providers are GCJ-02. Treat as `partial` until a basemap/CRS story exists.
- **US / AU / RU**: multiple IANA zones; `defaultZone` is a known approximation
  until per-bbox zones land.
- Anywhere: geocoding still resolves via Nominatim even in `minimal` regions —
  the user can always add a place manually; they just won't get suggestions.

## 9. Phase 2 outline (not scheduled)

- `OpenMeteoWeatherSource` / `OpenWeatherSource` behind the unchanged
  `WeatherSource`; hourly precipitation maps to `isRainy` directly. Register the
  id in `regions.json` (`providers.weather`) — no selector code change.
- Extract `RoutingProvider` (`id()`, `supports(RouteMode)`, `route(RouteRequest)`)
  only when the second implementation exists; generalize
  `frontend/src/lib/routingUtils.ts` away from OneMap's `plan`/`route_summary`
  shapes.
- Per-bbox zones in `regions.json`, removing the multi-zone approximation.
- Commercial geocoder fallback (Google/Mapbox/HERE) for `minimal` regions, behind
  the existing base-URL config pattern.
- Seed pipeline: per-country extracts, same script with an argument, plus a
  scheduled pre-warm of backfill for upcoming trips.

## 10. Phase 3 (explicitly out of scope)

Public transport abroad needs GTFS feeds plus a journey planner (OTP, Navitia, or
a paid API) per city, with ongoing feed maintenance. Budget 1–2 weeks per metro;
there is no global drop-in replacement for OneMap's PT routing. Do not let Phase
1 or 2 designs assume a single global PT provider.
