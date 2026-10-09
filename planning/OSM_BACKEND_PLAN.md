# OpenStreetMap Backend Integration — Plan & API Contract

**Status:** Proposal for review. No code yet.
**Branch:** `openstreetmaps`
**Owner:** Backend
**Last updated:** 2026-10-07

This document plans the **backend** half of the OpenStreetMap (OSM) integration only.
The navbar, map rendering, and user-facing frontend requests are owned by another
developer; §9 is the handoff contract they build against.

---

## 1. Objective

Let users find real places, see them on a map, and attach them to itinerary
activities — with all OSM access mediated by the backend so we comply with OSM's
usage policies and keep a stable internal `Location` catalog.

The backend answers questions like:

- "Where is 'Shibuya Crossing'?" → coordinates + address
- "What is at this clicked map point?" → reverse geocode
- "What restaurants are within 500 m?" → POI list (also feeds the future AI assistant)
- "Turn this search result into a real `Location` I can attach to an activity."

---

## 2. Scope decisions (agreed)

| Decision            | Choice                                                                                              |
| ------------------- | --------------------------------------------------------------------------------------------------- |
| Capabilities        | Forward geocoding/search, reverse geocoding, POI discovery (Overpass), routing (optional / phase 2) |
| Map tiles           | **Not** proxied by the backend. Browser loads tiles directly.                                 |
| Delivery this round | Plan + API contract only                                                                            |
| Storage             | Persist user-chosen places into the`Location` catalog + in-memory cache for lookups               |
| Future              | POI data is retained/normalized so the planned AI assistant can consume it                          |

> Interpretation note: the reply "We plan to integrate AI in the future. no need 5"
> was read as **exclude map-tile proxying (option 5)** and **include geocoding,
> reverse geocoding, POI/Overpass, and (optionally) routing**. Routing is marked
> Phase 2 because the public OSRM demo server is not production-grade. Correct this
> table if the intent differs.

### Why a backend proxy at all?

The public OSM services cannot be called safely from the browser:

- **Nominatim** caps the whole application at **1 request/second** and requires an
  identifying `User-Agent`/`Referer` + caching. A browser cannot set `User-Agent`,
  and per-end-user calls would blow the app-wide budget.
  [https://operations.osmfoundation.org/policies/nominatim/](https://operations.osmfoundation.org/policies/nominatim/)
- **Overpass** is IP rate-limited and expects an identifying client; the browser
  would share the user's IP budget and cannot cache.
  [https://wiki.openstreetmap.org/wiki/Overpass_API](https://wiki.openstreetmap.org/wiki/Overpass_API)
- **Tiles**, by contrast, are explicitly designed for direct browser consumption —
  proxying them is discouraged.
  [https://operations.osmfoundation.org/policies/tiles/](https://operations.osmfoundation.org/policies/tiles/)

---

## 3. Architecture

```
React (someone else)                Spring Boot backend (this plan)             OSM ecosystem
────────────────────                ──────────────────────────────             ─────────────
 map + navbar  ──fetch──►  /api/geo/search   ──► GeoService ──► NominatimClient ──► nominatim.openstreetmap.org
 search box                 /api/geo/reverse                              (rate limiting deferred)
 map click  ──fetch──►      /api/geo/pois      ──► OverpassClient  ─────────────► overpass-api.de
 add place  ──fetch──►      /api/locations/from-osm ─► LocationCatalog (JPA) ──► H2
 tiles <──────────────────────────────────────────────────────────── browser ──► tile.openstreetmap.org
```

Layering:

- **Controllers** thin, return normalized DTOs, never leak upstream payloads.
- **Clients** (`NominatimClient`, `OverpassClient`) own HTTP, URLs, headers, timeouts.
- **GeoService** orchestrates caching, normalization, mapping.
- **LocationService/Repository** handles persistence of chosen places.

Proposed package:

```
com.smu.cs203_g1_1.itinerary_planner.geo/
  GeoController.java
  GeoService.java
  NominatimClient.java
  OverpassClient.java
  OsmAttribution.java
  dto/ PlaceDto.java, PoiDto.java, AddressDto.java, BoundingBoxDto.java, RouteDto.java
  GeoUnavailableException.java
```

---

## 4. Data model changes

`Location` already has commented-out placeholders for `address`, `osmId`,
`openingHours` (`trips/Location.java:38-43`). Promote them, add OSM identity, and
keep everything nullable so existing POST `/api/locations` rows still work.

| New field        | Type       | Notes                                      |
| ---------------- | ---------- | ------------------------------------------ |
| `osmType`      | `String` | `node` / `way` / `relation`          |
| `osmId`        | `Long`   | Unique together with`osmType`            |
| `address`      | `String` | Human-readable (Nominatim`display_name`) |
| `openingHours` | `String` | Raw OSM string; no parsing in this phase   |

Rules:

- **Stable identity is `(osmType, osmId)`**, never Nominatim `place_id` (it is not
  persistent). [https://nominatim.org/release-docs/latest/api/Output/](https://nominatim.org/release-docs/latest/api/Output/)
- Add a composite unique constraint `(osm_type, osm_id)`. Rows created manually
  (null osm fields) remain allowed because SQL/H2 treats NULLs as distinct.
- `create-drop` DDL is in use (`application.properties`), so no migration is
  required for this phase. If a persistent profile is added later, add a Flyway
  migration then.
- Keep `rawTags`/`extratags` out of scope for now; add a JSON column only if the
  AI phase needs the full tag set.

Repository additions:

```java
Optional<Location> findByOsmTypeAndOsmId(String osmType, Long osmId);
```

---

## 5. API contract

Base path: `/api/geo` (new) + one new `/api/locations` endpoint. All endpoints
require the same Clerk bearer token as the rest of the API (they fall under
`anyRequest().authenticated()`); no new auth surface.

Common conventions:

- JSON, UTF-8. Coordinates are **WGS84 decimal degrees**, numbers not strings.
- Every geo response includes `"attribution": "© OpenStreetMap contributors"` and
  the frontend must render it.
- Errors use a small envelope (§5.5).

### 5.1 `GET /api/geo/search` — forward geocoding

Query params:

| Param            | Required | Default | Notes                                                             |
| ---------------- | -------- | ------- | ----------------------------------------------------------------- |
| `q`            | yes      | —      | Place/address text. Trimmed; blank → 400.                        |
| `limit`        | no       | 5       | Clamped 1–10. No autocomplete-per-keystroke; frontend debounces. |
| `countrycodes` | no       | —      | Comma-separated ISO 3166-1 alpha-2, e.g.`jp,sg`.                |
| `viewbox`      | no       | —      | `minLon,minLat,maxLon,maxLat` bias.                             |
| `bounded`      | no       | false   | Restrict to`viewbox`.                                           |

`200` body:

```json
{
  "results": [
    {
      "osmType": "node",
      "osmId": 123456789,
      "name": "Shibuya Crossing",
      "displayName": "Shibuya Crossing, Shibuya, Tokyo, Japan",
      "category": "highway",
      "type": "crossing",
      "latitude": 35.6595,
      "longitude": 139.7005,
      "address": {
        "houseNumber": null,
        "road": "Shibuya Scramble Crossing",
        "city": "Shibuya",
        "state": "Tokyo",
        "postcode": null,
        "country": "Japan",
        "countryCode": "jp"
      },
      "openingHours": null,
      "boundingBox": { "minLat": 35.659, "minLon": 139.700, "maxLat": 35.660, "maxLon": 139.701 },
      "importance": 0.72
    }
  ],
  "attribution": "© OpenStreetMap contributors"
}
```

Upstream: Nominatim `/search?format=jsonv2&addressdetails=1&extratags=1&namedetails=1`.
[https://nominatim.org/release-docs/latest/api/Search/](https://nominatim.org/release-docs/latest/api/Search/)

### 5.2 `GET /api/geo/reverse` — reverse geocoding

| Param    | Required | Default | Notes                |
| -------- | -------- | ------- | -------------------- |
| `lat`  | yes      | —      | −90..90, else 400   |
| `lon`  | yes      | —      | −180..180, else 400 |
| `zoom` | no       | 18      | Address detail level |

`200` body: a single place object (same shape as a `results[]` element) plus
`attribution`. `404` when nothing is found.

Upstream: Nominatim `/reverse?format=jsonv2&addressdetails=1&extratags=1`.
[https://nominatim.org/release-docs/latest/api/Reverse/](https://nominatim.org/release-docs/latest/api/Reverse/)

### 5.3 `GET /api/geo/pois` — POI discovery (Overpass)

| Param             | Required | Default        | Notes                                                                                                        |
| ----------------- | -------- | -------------- | ------------------------------------------------------------------------------------------------------------ |
| `lat` / `lon` | yes      | —             | Center point                                                                                                 |
| `radius`        | no       | 500            | Metres, clamped 50–2000                                                                                     |
| `category`      | no       | `restaurant` | Whitelisted`amenity`/`tourism` tag, e.g. `restaurant`, `cafe`, `hotel`, `attraction`, `museum` |
| `limit`         | no       | 50             | Clamped 1–200                                                                                               |

`200` body:

```json
{
  "results": [
    {
      "osmType": "node",
      "osmId": 987654321,
      "name": "Ichiran Shibuya",
      "displayName": "Ichiran, 1-22-7 Jinnan, Shibuya, Tokyo",
      "category": "amenity",
      "type": "restaurant",
      "latitude": 35.6617,
      "longitude": 139.6988,
      "address": { "road": "1-22-7 Jinnan", "city": "Shibuya", "country": "Japan", "countryCode": "jp" },
      "openingHours": "24/7"
    }
  ],
  "attribution": "© OpenStreetMap contributors"
}
```

Upstream (server-built, never client-supplied): Overpass QL

```
[out:json][timeout:25];
nwr["<key>"="<category>"](around:<radius>,<lat>,<lon>);
out center <limit>;
```

`category` must be mapped through a server-side whitelist to prevent arbitrary
tag injection. Use `out center` so ways/relations return a coordinate.
[https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL](https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL)

### 5.4 `POST /api/locations/from-osm` — persist a chosen place

Turns a transient search/POI result into a durable `Location` row.

Request:

```json
{ "osmType": "node", "osmId": 123456789 }
```

Server behavior:

1. Look up `(osmType, osmId)` in `LocationRepository`; return existing row if present.
2. Otherwise hydrate from Nominatim `/lookup?osm_ids=N123456789&format=jsonv2&addressdetails=1&extratags=1`.
   [https://nominatim.org/release-docs/latest/api/Lookup/](https://nominatim.org/release-docs/latest/api/Lookup/)
3. Map into a new `Location`, save, return `201` (new) or `200` (existing).

Response: the standard `Location` JSON (existing shape), now including the new
nullable fields. Security: the client sends only OSM identity, never the payload it
wants stored — the server re-fetches authoritative data.

### 5.5 Error envelope

Extend `GlobalExceptionHandler` with a geo-aware shape:

```json
{ "error": "UPSTREAM_UNAVAILABLE", "message": "Geocoding service is temporarily unavailable" }
```

| Status            | When                                                                                |
| ----------------- | ----------------------------------------------------------------------------------- |
| `400`           | Missing/blank/invalid params (bad`q`, out-of-range lat/lon, unknown `category`) |
| `401`           | Missing/invalid bearer token (existing security behavior)                           |
| `404`           | Reverse geocoding found nothing                                                     |
| `429`           | Our own inbound throttle, or upstream rate limit surfaced                           |
| `502` / `503` | Nominatim/Overpass timeout, 504, or connection failure                              |

### 5.6 Routing (Phase 2 — optional, not in this round)

`GET /api/geo/route?from=lat,lon&to=lat,lon&mode=driving|walking|cycling`
→ distance, duration, encoded polyline. Uses OSRM. Deferred because the public
demo server is explicitly not for production
([https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy](https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy)); we would
self-host or choose a host before exposing this.

---

## 6. Caching, rate limiting, resilience

**Caching** (`spring-boot-starter-cache` + Caffeine):

- `@Cacheable` on geocode search (key = normalized query + params), reverse
  (key = lat/lon rounded to ~5 decimals), lookup (key = osm identity), POIs
  (key = center + radius + category).
- TTL ~30 days for places, shorter (~1 h) for POIs (they change).
- **Cache negative results** too, so misses aren't re-requested.
- Bound with `maximumSize` (e.g. 10_000).
  [https://docs.spring.io/spring-boot/reference/io/caching.html](https://docs.spring.io/spring-boot/reference/io/caching.html)

**Outbound rate limiting (deferred — not implemented yet):**

- **Nominatim rate limiter: deferred.** A global token bucket at 1 req/s for the
  Nominatim leg would be app-wide, **not** per end user — that is what the policy
  actually caps. It is **not implemented yet** and must be re-added before any
  shared/public deployment to honour Nominatim's ~1 req/s policy.
- **Overpass concurrency cap: deferred.** Limiting to ≤2 concurrent calls (and
  backing off ≥30 s on 429/406) is **not implemented yet** and must be re-added
  before shared/public use.
- Inbound per-client throttle on `/api/geo/search` optional but recommended;
  rely primarily on frontend debounce + server cache.

**Resilience**:

- Spring Boot 4 `RestClient` + Jackson (no OSM Java library — see §10).
- Per-client timeouts and a descriptive `User-Agent` via
  `RestClient.Builder.defaultHeader(...)`. Validate exact Boot 4.1 property names
  (`spring.http.serviceclient.*` vs older `spring.http.client.*`) against the
  version's property reference during implementation.
- Retry only transient failures, exponential backoff + jitter, small cap; never
  blind-retry 429.
- On upstream failure return `502/503` — never a raw upstream error body.

---

## 7. Configuration

`application.properties` additions (values overridable by env; never hard-code
secrets):

```properties
# OSM integration
app.osm.user-agent=${APP_OSM_USER_AGENT:ItineraryPlanner/1.0 (+https://github.com/example; contact@example.com)}
app.osm.nominatim.base-url=${APP_OSM_NOMINATIM_BASE_URL:https://nominatim.openstreetmap.org}
app.osm.overpass.base-url=${APP_OSM_OVERPASS_BASE_URL:https://overpass-api.de/api/interpreter}

# Cache
spring.cache.cache-names=geocodeSearch,geocodeReverse,osmLookup,pois
spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=30d
```

> **Rate limiting deferred.** The Nominatim rate limiter (1 req/s token bucket) and
> the Overpass concurrency cap are **not implemented yet**. They must be re-added
> before any shared/public deployment to honour the Nominatim 1 req/s policy.

- The `User-Agent` **must** identify the app with a real contact — policies reject
  library defaults. Replace the placeholder contact before any shared/demo deploy.
- Add the same keys to `backend/.env.example`.

New dependencies (`pom.xml`): `spring-boot-starter-cache`,
`com.github.ben-manes.caffeine:caffeine`. No WebClient/WebFlux needed; no OSM Java
library. (A rate-limiting library would be added when the deferred rate limiter above
is implemented.)

---

## 8. Security & licensing

- All geo endpoints authenticated like the rest of `/api/**`; no anonymous geocoding.
- `category` (Overpass) and numeric params are server-validated/whitelisted — no
  raw Overpass QL from clients.
- **Attribution (ODbL):** responses carry the attribution string, and the UI must
  display **"© OpenStreetMap contributors"** linking to
  [https://www.openstreetmap.org/copyright](https://www.openstreetmap.org/copyright). This is a frontend requirement; the
  backend provides the string and the data.
  [https://osmfoundation.org/wiki/Licence/Attribution_Guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
- Keep OSM-derived fields clearly isolated on `Location` (provenance) to limit ODbL
  derivative-database concerns; prefer small per-place extracts over bulk imports.
  [https://osmfoundation.org/wiki/Licence/Community_Guidelines/Substantial_-_Guideline](https://osmfoundation.org/wiki/Licence/Community_Guidelines/Substantial_-_Guideline)

---

## 9. Frontend handoff (for the other developer)

Backend is the only thing that talks to Nominatim/Overpass. The frontend:

1. **Tiles:** use `react-leaflet` (Leaflet) with
   `https://tile.openstreetmap.org/{z}/{x}/{y}.png` directly. Do **not** route tiles
   through our backend. Include the required attribution control.
2. **Search box (navbar or map panel):** debounce **≥ 750 ms**, then
   `GET /api/geo/search?q=`. Show the top ~5 results. Never fire per keystroke.
3. **Map click:** `GET /api/geo/reverse?lat=&lon=` to label the point.
4. **"Add as location":** `POST /api/locations/from-osm` with `{osmType, osmId}`,
   then attach the returned `Location.id` to an activity
   (`POST /api/trips/{tripId}/activities` with `locations:[{id}]`).
5. **POI exploration / future AI:** `GET /api/geo/pois?lat=&lon=&category=`.
6. **Attribution:** render the returned `attribution` and a persistent OSM credit.
7. **Auth:** attach the Clerk bearer token exactly like existing calls
   (`docs/authenticated-endpoints.md`).

Coordinate order pitfall: this API and OSRM use `lat,lon`; GeoJSON/Leaflet use
`[lat, lon]` — agree on one convention and document it in the API client.

---

## 10. Why no OSM Java library

Current OSM Java libraries target file formats or the editing API, not these read
APIs: `osm4j` focuses on XML/PBF/TBO files; `westnordost/osmapi` targets the OSM
0.6 editing API. For three read-only JSON endpoints, Spring Boot 4's `RestClient` +
Jackson is simpler, and Spring Framework 7 supplies `@Retryable` for backoff.
JTS is only worth adding if real geometry operations are needed later.
[https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html)

---

## 11. Task breakdown (lanes)

Dependency-ordered; each lane has one writer to avoid conflicts.

| Lane                              | Scope                                                                                           | Files (indicative)                                                                                                                  | Depends on |
| --------------------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| **L1 — Foundation**        | Deps + cache config +`RestClient` beans (UA/timeouts) + config props + attribution constant   | `pom.xml`, `application.properties`, `config/HttpClientConfig.java`, `config/CacheConfig.java`, `geo/OsmAttribution.java` | —         |
| **L2 — Model**             | Extend`Location` (osmType/osmId/address/openingHours) + unique constraint + repository finder | `trips/Location.java`, `trips/LocationRepository.java`                                                                          | —         |
| **L3 — Geocoding**         | Nominatim client + cache + search & reverse endpoints + DTOs + errors (rate limiter deferred)   | `geo/NominatimClient.java`, `geo/GeoService.java`, `geo/GeoController.java`, `geo/dto/*`, `GlobalExceptionHandler.java`   | L1         |
| **L4 — POI**               | Overpass client + category whitelist + POI endpoint + cache                                     | `geo/OverpassClient.java`, `geo/GeoController.java`                                                                             | L1         |
| **L5 — Persist place**     | `/api/locations/from-osm` find-or-create + lookup hydration                                   | `geo/GeoService.java`, `trips/LocationService.java`, `trips/LocationController.java`                                          | L2, L3     |
| **L6 — Routing** (phase 2) | OSRM client + route endpoint                                                                    | `geo/OsrmClient.java`, `geo/dto/RouteDto.java`                                                                                  | L1         |
| **L7 — Tests & docs**      | Mocked-HTTP tests, service tests, Swagger annotations                                           | `src/test/...`, controller annotations                                                                                            | L3–L5     |

Parallelization: **L1 and L2 start together**; once L1 lands, **L3 and L4 run in
parallel**; L5 follows L2+L3; L6 is independent of everything but L1. L7 runs
continuously and is finalized last.

---

## 12. Verification plan

Unit / integration (no live network):

- Nominatim and Overpass clients tested with a mocked HTTP server (Spring
  `MockRestServiceServer` or WireMock): asserts URL/params, `User-Agent` header,
  timeout config, and DTO mapping.
- Rate limiter test (deferred): once the rate limiter is re-added, N rapid calls to
  `NominatimClient` must never exceed 1 req/s.
- Cache test: repeated identical search hits the client once; negative results cached.
- `GeoService` test: upstream 504/connection error → `502/503` envelope, logged.
- `POST /api/locations/from-osm` test: creates once, second call returns the same
  row (`(osmType, osmId)` dedupe); unknown osm id → `404`.
- Param validation: blank `q`, out-of-range `lat/lon`, bad `category` → `400`.
- Security: request without bearer token → `401` when Clerk is configured.

Manual smoke (one-off, live, respects policy):

- Single real `GET /api/geo/search?q=Shibuya` returns a normalized result with
  attribution; verify only one outbound request fires (check logs/metrics).
- Confirm endpoints appear in Swagger UI (`/swagger-ui.html`).

Acceptance criteria:

- A frontend client can search, reverse-geocode, list POIs, and persist a place
  using only the documented contract, with all upstream calls going through the
  backend and OSM attribution present.
- No path allows a client to send raw Overpass QL or arbitrary upstream params.
- Rate limiting is deferred; once re-added, the app must stay within Nominatim 1 req/s
  under the test load.

---

## 13. Open questions

1. **Routing** — is turn-by-turn/route distan	ce actually required, or can it be cut
   for now? If required, which host (self-hosted OSRM vs another provider)?
2. **Production provider** — for a real deployment Nominatim/Overpass public
   instances are demo-grade. Self-host (Photon/Nominatim), pay a provider, or cap
   usage and accept public limits for the course demo?
3. **Contact in User-Agent** — what URL/email should identify our app?
4. **POI persistence** — should POIs ever be persisted to `Location` (via
   `from-osm`) or only shown transiently on the map? The AI phase may want them
   cached server-side.
5. **Tile provider for production** — Leaflet + `tile.openstreetmap.org` is fine
   for dev/demo; confirm the production choice and its attribution line.
6. **`Location.rawTags`** — add a JSON column now for future AI use, or defer?

---

## 14. Source references

- Nominatim policy — https://operations.osmfoundation.org/policies/nominatim/
- Nominatim Search / Reverse / Lookup / Output — https://nominatim.org/release-docs/latest/api/Search/ · /Reverse/ · /Lookup/ · /Output/
- Overpass API / QL — https://wiki.openstreetmap.org/wiki/Overpass_API · /Overpass_API/Overpass_QL
- OSM Tile Usage Policy — https://operations.osmfoundation.org/policies/tiles/
- OSRM usage policy — https://github.com/Project-OSRM/osrm-backend/wiki/Api-usage-policy
- OSMF Attribution / Substantial guidelines — https://osmfoundation.org/wiki/Licence/Attribution_Guidelines · https://osmfoundation.org/wiki/Licence/Community_Guidelines/Substantial_-_Guideline
- Spring Boot 4 REST clients — https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html
- Spring Framework resilience — https://docs.spring.io/spring-framework/reference/core/resilience.html
- Spring Boot caching — https://docs.spring.io/spring-boot/reference/io/caching.html
