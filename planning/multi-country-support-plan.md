# Multi-Country Support — Implementation Plan

Status: proposed
Branch: `plan/multi-country-support`
Scope: Phase 1 (detailed), Phase 2 (outline), Phase 3 (out of scope)

## 1. Goal and non-goals

**Phase 1 goal.** A trip in a non-SG country works end to end: geocoding, POI
discovery, planner, manual itinerary, members, and suggestions behave correctly.
Weather and turn-by-turn routing degrade to *explicitly unavailable* rather than
silently reporting the wrong thing. Singapore behaviour is byte-for-byte
unchanged.

**Non-goals (Phase 1).** No new weather provider, no second routing provider, no
public-transport routing abroad, no UI redesign, no full i18n/translation.

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

## 3. Architecture decisions

### D1 — Region profiles are configuration, resolved by coordinate

Introduce a bundled `regions.json` plus a `RegionRegistry`. A region is keyed by
ISO-3166-1 alpha-2 and carries: display name, bounding boxes, IANA zone, locale,
currency, and capability flags (`weather`, `routing`).

**Why coordinate-keyed, not trip-keyed.** Trips carry `countries[]` and may span
several (SG + MY is the obvious case). Weather is asked as
`isRainy(lat, lon, …)`, so resolving at the *point* is unambiguous and needs no
trip-level arbitration. Trip country codes remain the coarse gate for UI and
seeding.

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
    Optional<WeatherSource> forCountries(List<String> countryCodes);
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
        "locale": "en-SG",
        "currency": "SGD",
        "defaultZone": "Asia/Singapore",
        "bboxes": [{ "minLat": 1.15, "maxLat": 1.48, "minLon": 103.60, "maxLon": 104.10 }],
        "capabilities": ["weather", "routing"]
      }
    ],
    "fallback": { "locale": "en", "currency": null, "defaultZone": "UTC", "capabilities": [] }
  }
  ```

  The SG numbers are copied verbatim from the two existing constant blocks, so
  behaviour is preserved by construction.

- `config/region/RegionProfile.java` — record: `code`, `name`, `locale`,
  `currency`, `defaultZone`, `List<BoundingBox>`, `Set<Capability>`.
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
- `config/region/Capability.java` — enum `WEATHER`, `ROUTING`.

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
- Keep `@ConditionalOnProperty(app.weather.source=nea)` and `@Primary`.

Add `weather/WeatherSourceResolver` implementation backed by `RegionRegistry` +
`Clock`:

- `forPoint` → region has `WEATHER` → delegate to the active `WeatherSource` and
  verify it accepts the point (NEA returns `false` outside SG, which would still
  be wrong for a region that claims weather — so the NEa implementation must
  expose its own coverage; see below).
- **Coverage hook.** Add a default method to `WeatherSource` (non-breaking):

  ```java
  default boolean covers(double latitude, double longitude) { return true; }
  ```

  `NeaWeatherSource` overrides it with the SG bbox check. `NoRainWeatherSource`
  returns `false`. This makes "provider exists but not here" explicit without
  changing `isRainy`.

Modify `suggestion/SuggestionService.java`:

- Replace `coversSingapore(trip)` (`:168-175`) with
  `weatherSourceResolver.forCountries(trip.getCountries()).isPresent()`.
- Keep the legacy rule: empty `countries` → treated as legacy → SG behaviour.
  Preserve this explicitly and test it, because it is the main compatibility
  risk.
- At `:252` log the resolved reason (`no weather provider for countries [jp]`)
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

- `db/migration/V6__location_country_code.sql`

  ```sql
  ALTER TABLE location ADD COLUMN country_code varchar(2);
  UPDATE location SET country_code = 'sg' WHERE country_code IS NULL;
  CREATE INDEX idx_location_country_code ON location (country_code);
  ```

  Backfill to `sg` is correct: every row today originates from the SG seed or SG
  geocoding. Leave the column nullable so rows created from ungeocodable input do
  not fail.

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
- `CandidateFinder`: optional `countryCode` filter alongside the bbox, to avoid
  cross-border bleed (JB vs SG). Off by default; enabled when the trip has a
  single country.

Acceptance: migration runs on a copy of prod-shaped data; import of a
non-SG fixture file lands rows with the right `country_code`.

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
- `components/trips/CountryMultiSelect.tsx`: mark codes without capabilities
  ("no weather data yet") — informational only, do not block selection.

Acceptance: an SG trip renders identically; a JP trip hides weather, hides route
actions, and shows JP-appropriate dates.

### Lane E — API surface

- `geo/RegionController.java` — `GET /api/regions`:

  ```json
  [{ "code": "sg", "name": "Singapore", "locale": "en-SG", "currency": "SGD",
     "capabilities": ["weather", "routing"] }]
  ```

  Public (no auth) or authenticated — match the existing auth convention in
  `SecurityConfig`; prefer authenticated for consistency.
- `routing/RoutingController`: before invoking OneMap, check the region supports
  `ROUTING`; otherwise throw a typed `RoutingUnavailableException` mapped to
  `422` with `{ "error": "routing_unavailable", "countries": [...] }`.
- `services/weather/WeatherController`: unchanged signatures. When no provider
  covers the window, return `200` with `[]` (already tolerated by the client) and
  let capabilities drive visibility.

Acceptance: contract tests for `/api/regions` and for 422 on a JP trip.

### Lane F — Tests

- Keep every existing test green. The only expected edits are the ones that
  reference the moved bbox constants.
- New: `RegionRegistryTest`, `WeatherSourceResolverTest`,
  `NeaWeatherSourceRegionTest` (point outside SG but inside another region must
  not be answered by NEA), `SuggestionServiceScopeTest` (empty countries = legacy
  SG; `["jp"]` = skipped with reason), `RoutingUnavailableTest`.
- Fixtures: no network. `FixtureWeatherSource` already exists; add a
  `regions-test.json` only if the production file cannot express a case.

## 5. Write-scope map (for parallel execution)

| Lane | Owns | Conflicts with |
|---|---|---|
| A | `config/region/**`, `resources/regions.json`, `config/ClockConfig.java` | — blocks all |
| B | `weather/**`, `services/weather/**`, `suggestion/SuggestionService.java`, `polling/WeatherPoller.java` | A |
| C | `db/migration/V6*`, `seed/**`, `scripts/build-sg-pois.sh`, `geo/PoiService.java` | A |
| D | `frontend/src/**` | E (contract) |
| E | `geo/RegionController.java`, `routing/RoutingController.java` | A |
| F | `backend/src/test/**` | all, but additive |

Sequence: **A → (B ∥ C ∥ E) → D → F**. F runs continuously as each lane lands.

## 6. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Global clock change alters stored timestamps | Data corruption | `Clock.systemUTC()` changes only `LocalDate`/`atStartOfDay` derivation; audit all `clock` usages with grep before merge; DB stores instants |
| Existing tests assume SG local date | CI red | Grep `LocalDate.now` + fixed-clock tests in `SuggestionService`/poller; adjust expectations, not the clock |
| "No provider" silently suppressing suggestions | Missed feature, invisible | Resolver + explicit log + capability flag in UI; test asserts the log path |
| Multi-zone countries resolve to one zone | Wrong day boundaries for US/AU | Documented limitation; `regions.json` can carry per-bbox zones later |
| `location.country_code` backfill wrong for any non-SG row | Misclassification | Verify against prod row counts first; backfill is a data assertion, run it as a query before the migration |
| NEA claiming coverage outside SG | Wrong weather abroad | `covers()` override + explicit cross-region test |

## 7. Verification plan

**Automated.**

1. `./mvnw test` green with SG defaults unchanged.
2. New tests listed in Lane F.
3. Migration test: apply V6 to a dump-shaped DB; assert null count = 0 and the
   index exists.
4. Contract test for `GET /api/regions` and the 422 routing response.

**Manual smoke (no network beyond local services).**

1. SG trip: create, add activity, weather panel shows NEA two-hour forecast,
   route button works. Must be pixel-identical to `main`.
2. JP trip (`countries=["jp"]`): search "Shibuya" (Nominatim), add activity,
   planner suggests nearby candidates, weather panel absent, route action absent.
3. Legacy trip with `countries=[]`: behaves like SG (backward-compat check).
4. SG+MY trip: weather panel shows only for SG-coordinate activities; MY
   activities produce a "no weather data" path, not "no rain".
5. Dev profile: `app.weather.source=none` still selects the fixture and nothing
   regresses.

## 8. Phase 2 outline (not scheduled)

- `OpenMeteoWeatherSource` / `OpenWeatherSource` behind the unchanged
  `WeatherSource`; hourly precipitation maps to `isRainy` directly.
- Extract `RoutingProvider` (`id()`, `supports(RouteMode)`, `route(RouteRequest)`)
  only when the second implementation exists; generalize
  `frontend/src/lib/routingUtils.ts` away from OneMap's `plan`/`route_summary`
  shapes.
- `regions.json` gains per-bbox zones and a `weatherProvider` key so a region can
  select its implementation without a code change.
- Seed pipeline: per-country extracts, same script with an argument.

## 9. Phase 3 (explicitly out of scope)

Public transport abroad needs GTFS feeds plus a journey planner (OTP, Navitia, or
a paid API) per city, with ongoing feed maintenance. Budget 1–2 weeks per metro;
there is no global drop-in replacement for OneMap's PT routing. Do not let Phase
1 or 2 designs assume a single global PT provider.
