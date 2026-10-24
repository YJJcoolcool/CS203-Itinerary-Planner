# AI Discovery (Singapore) — Implementation Plan

Status: implemented on `feature/ai-discovery`. Supersedes `ai-integration-implementation-plan.md` for discovery, preferences, and retrieval.
Branch: `feature/ai-discovery`, merged with `origin/main` (deploy lane: Docker/compose/CI) and pushed. Milestone state and remaining gaps are in §14.
Scope: Singapore only. Global weather, country, timezone, and currency are out of scope.
Retrieval approach: **lightweight semantic ranking (option B)** — no pgvector, no vector-store extension, no new artifact.

## 1. Decisions to confirm first

| # | Decision | Default | Impact |
| --- | --- | --- | --- |
| D1 | Discovery | Offline SG OSM index in Postgres + hybrid retrieval (SQL filter + in-Java cosine). No web search, no live Overpass in the request path | Web search cannot return `osm_id`; Overpass has a <100 automated queries/day budget |
| D2 | Role of the 49 seeds | Curated gold tier (`seeded=true`) + last-resort fallback | They stop being the candidate universe |
| D3 | Weather | **Closed: NEA only, Singapore only.** No global provider. `Country` on a trip is informational and never changes which weather source is used | Out-of-SG points return "weather unavailable" rather than a fallback forecast |
| D4 | Trigger window | **Resolved: keep 24 h.** Ignore CGT-105's "four days" | — |
| D5 | Preference visibility | Any authenticated user can read another user's preference: `tags`, constraints, `summary`. Never email or account fields | Restricting to trip co-members is a one-line `@PreAuthorize` change |
| D6 | Group preference | AI-summarised aggregate of all trip members' preferences + trip goal, stored on the trip, visible on the trips page | Regeneration policy affects staleness and cost |
| D7 | Trip goal | Multi-select of category groups (`MUSEUMS, NATURE, FOOD, SHOPPING, CULTURE, NIGHTLIFE, ATTRACTIONS`) stored per trip | Goal feeds both the retrieval query and the ranking prompt |
| D8 | Retrieval | **Lite semantic ranking.** Precomputed POI embeddings in a `place_embedding` table; cosine over the bbox-filtered subset in Java. No pgvector, no HNSW, no `CREATE EXTENSION` | Chosen as the "advanced AI capability" without the vector-DB infrastructure cost |
| D9 | AI call budget | 1 query embedding + 1 ranking call per replan. Summary regeneration only on preference/goal change, cached | 2 chat calls per replan breaks the p95 target |
| D10 | Candidate pool scope | One discovery per replan (trip bbox), filtered per activity | Per-activity discovery multiplies retrieval passes |
| D11 | Embedding dimensions | 512 (`text-embedding-3-small` supports custom dims) → 2 KB/POI, ~60 MB for 30 k places | 1536 also works (180 MB) if storage is not a concern |

## 2. Definition of done

A Singapore trip with an outdoor stop and rain forecast for the activity window:

1. `POST /api/trips/{tripId}/replan` detects the disruption from **real NEA** data.
2. Retrieval returns ≥10 candidates within 3 km, combining SQL category/bbox filtering with semantic ranking over preference text.
3. Each member's preference and the trip goal visibly influence the ranking (asserted in a deterministic test).
4. The group preference shown on the trips page is a cached AI summary of all members' preferences plus the goal, regenerable on demand.
5. The LLM re-ranks the shortlist and returns a reason; `PlanChoiceValidator` still rejects anything outside the supplied ids.
6. Accept replaces the location in place; reject records the decision and suppresses repeats.
7. With no API key, missing embeddings, no NEA coverage, or an empty index: SQL-only path, deterministic suggestion, or nothing. Never a false positive, never a crash.
8. `./mvnw test` passes on H2 with no network; the ablation harness prints AI-on vs AI-off numbers.
9. Every replan logs `ai_called`, `ai_pick_used`, `fallback_reason`, `discovery_source`, `retrieval_mode`, `latency_ms`.
10. Polling (M4b, owned by a group mate) produces a suggestion **without any user action**: the 24 h trip check uses the four-day outlook, the 2 h activity check uses the two-hour forecast, and repeated cycles do not create duplicates.

Out of scope: worldwide trips, web search, PostGIS, pgvector, routing, push notifications, multi-day planning, per-keystroke geocoding, free-text chat interface.

## 3. Current state — verified gaps

| Gap | Evidence | Consequence |
| --- | --- | --- |
| No NEA → `WeatherSource` adapter | only `NoRainWeatherSource` (always false) and `FixtureWeatherSource` | In prod the LLM is **never called**; the AI path is dead code |
| Candidates are seeded-only | `CandidateFinder:70` → `findSeededInBoundingBox` | No indexed place can be suggested |
| `interests` hardcoded | `SuggestionService:216` passes `Set.of()` | 0.3 preference weight and the prompt's preference signal are dead |
| No preference or goal model | grep for `Preference` in `backend/src/main` → nothing | Entirely greenfield (largest unbudgeted item) |
| No embedding storage | no embedding column/table, no vector code | Semantic ranking needs a table + offline embed job |
| Overpass whitelist too narrow | `PoiService:25-33`, 8 tags | No hawker, mall, park, zoo, historic, worship |
| Opening-hours penalty will misfire | `UNKNOWN_HOURS_PENALTY=0.25` applied to OSM strings | Indexed POIs systematically rank below seeds |
| Duplicate tickets | CGT-122 ≡ CGT-123 | Wasted sprint capacity |
| Branch drift | local `main` behind 19; `feature/ai_integration` 14 behind / 2 ahead | New work conflicts with merged activity-edit, invitation-role, member-role PRs |

Note: `spring-ai-starter-model-openai` already provides `OpenAiEmbeddingModel`, so D8 needs **no new dependency**.

## 4. Pipeline

```
POST /api/trips/{id}/replan
  → [1] EDITOR check, max 3 suggestions, window now..now+24h
  → [2] per activity: first OUTDOOR stop → WeatherSource.isRainy(lat, lon, arrival, arrival+duration)
        NeaWeatherSource: nearest-area lookup → two-hr forecast (four-day region for longer windows)
  → [3] ONE retrieval pass per replan (trip bbox)
        query text = trip goal + group summary + traveller tags + disruption context
        embed(query text)  ── 1 API call, cached by query hash
        ├── SQL arm:     location WHERE bbox AND category_group IN (goal) AND seeded-first
        ├── semantic arm: cosine(queryVector, place_embedding.vector) over bbox rows (Java)
        └── union + dedupe by (osm_type, osm_id) → fallback: seeded-only → empty
  → [4] per activity: CandidateFinder.find(CandidateRequest)
        filters: ≤3km, indoor when required, opening hours, category similarity, exclusions
        score: 0.5 similarity + 0.3 interests + 0.2 quality − distance − cost − unknown-hours
  → [5] PlanChooser.choose(PlanChoiceRequest)  (preferences + goal + prev/next context)
        PlanChoiceValidator.pick(choice, candidateIds)
  → [6] persist suggestion (candidates snapshot, ai_choice_id, top_score_choice_id, discovery_source)
  → [7] accept (in-place swap) / reject
```

Why hybrid: OSM text is sparse, so pure semantic search is weak and pure category filtering misses intent ("quiet, not touristy, good for a toddler"). The SQL arm guarantees constraints; the semantic arm widens recall. Both land in the same deterministic scorer, so retrieval can only *add* candidates — it can never bypass distance, indoor, or opening-hours rules.

Why not pgvector: at ~30 k POIs a 3 km bbox contains a few hundred rows, so a linear cosine scan in Java is microseconds-to-low-milliseconds. HNSW buys nothing at this scale and costs an extension, a privileged migration, a container image change, and a test seam.

Do **not** use `QuestionAnswerAdvisor` or `RetrievalAugmentationAdvisor` — those produce free text, and this feature needs a structured choice validated against candidate ids. Retrieve explicitly, then call the existing `PlanChooser`.

## 5. Work breakdown

Estimates are person-days. Lanes marked ∥ can run in parallel.

### M0 — Branch and repo hygiene (0.5 d)

- `git switch main && git pull --ff-only`; rebase `feature/ai_integration`, open/merge its PR (2 commits). Cut `feature/ai-discovery` from updated `main`.
- Delete local branches with `[gone]` upstreams after confirming merge. Decide `feature/trip-country` (not needed for SG scope).
- Archive CGT-122 or CGT-123.
- Add embedding config to `backend/.env.example`; set `spring.ai.openai.embedding.dimensions=512` (must match D11).

**Done when:** `main` is current, the new branch builds, `./mvnw test` is green, and the app still boots with no API key.

### M1 — NEA weather adapter (2 d)

- `weather/NeaWeatherSource implements WeatherSource`.
- `resources/nea/area-centroids.json` (~45 two-hour areas) + nearest-centroid lookup with an out-of-SG guard.
- Two-hour forecast for windows ≤ 2 h; four-day region mapping (North/South/East/West/Central) otherwise; rain if any part of `[arrival, arrival+duration]` is wet.
- Unknown area / stale feed / HTTP failure → `false`. Never default to rain.
- Prod wiring is `@Primary`; `NoRainWeatherSource` and `FixtureWeatherSource` stay for tests/dev.
- Tests: area mapping, window overlap, feed down, out-of-SG, fixture path.

**Done when:** real credentials make `isRainy` true for a rainy area and false for a dry one; dev fixture still works; no test hits the network.

### M2 — Singapore OSM index (2 d) ∥

- Extend `scripts/build-sg-pois.sh` (source documented in `sg_pois.json` metadata) to export the full whitelisted POI set; keep the 49 as `seeded=true`.
- Expand tags: `amenity=food_court|restaurant|cafe|bar|fast_food|place_of_worship|museum`, `shop=mall|department_store`, `leisure=park|garden`, `tourism=attraction|museum|zoo|theme_park`, `historic=*`.
- Mapping: `categoryGroup` via `CategoryGroups`; `indoorClass` from `building`+shop/amenity → `INDOOR`, `leisure=park`/`tourism=attraction|zoo`/`historic` → `OUTDOOR`, else null; `opening_hours` verbatim with a small parser (`24/7`, `Mo-Fr HH:MM-HH:MM`, `;` rules), everything else `unknownHours=true`.
- Import idempotent by `(osm_type, osm_id)`, `seeded=false`, `place_scores` untouched (quality defaults 0.5).
- Skip unnamed/coordinate-less POIs; log counts per category and unmapped-tag counts.

**Done when:** clean import yields ≥10 named POIs within 3 km of 10 sample SG coordinates; re-run inserts 0 duplicates; category report is printed.

### M3 — Semantic ranking (1 d)

- `V5` adds `place_embedding(location_id PK, model, dims, vector bytea, created_at)`, mapped as `@JdbcTypeCode(SqlTypes.VARBINARY) byte[]` (portable across Postgres and H2).
- `rag/PlaceDocumentBuilder`: text = name, category, group, address, opening hours, tag-derived descriptors, plus a short curated description for the 49. Coordinates and ids go in metadata only, never into the embedded text.
- `rag/PlaceEmbedder` (`@Profile("index")`): reads the imported POIs, embeds in batches of 100, skips rows already embedded with the same `model` + `dims`. One-off cost ≈ $0.12 for 30 k POIs.
- `rag/SemanticRanker`:
  1. reuse the bbox query (locations + embeddings, non-null only),
  2. cosine in Java over the vector subset,
  3. return top-K ids + scores.
- `rag/QueryEmbedder`: builds query text, caches vectors by SHA-256 of the text (Caffeine, 24 h, 1 000 entries).
- Degradation: no key, API error, missing/empty embeddings, or dimension mismatch → return empty list; discovery falls back to SQL-only. Log `retrieval_mode` = `SEMANTIC | SQL_ONLY`.
- Tests: cosine maths (known vectors), ranker ordering, empty embeddings, dim mismatch, embedder failure → SQL-only, document text excludes coordinates.

**Done when:** a preference query ("quiet museum, not touristy, good for toddlers") returns relevant SG places from seeded test vectors, and the whole path runs offline in tests.

### M4 — Discovery service and candidate wiring (1.5 d)

- `LocationRepository.findInBoundingBox(minLat, maxLat, minLon, maxLon, seededFirst)`.
- `discovery/PlaceDiscoveryService`: one call per replan; runs the SQL arm and the semantic arm, unions and dedupes, falls back to seeded-only when both are empty.
- Change `CandidateFinder:70` to the new query; keep 3 km, exclusions, indoor filter, ordering.
- Restructure `SuggestionService.replan` so discovery runs **before** the activity loop and the pool is passed in.
- Apply the unknown-hours penalty only to curated rows until parser coverage is measured.
- Tests: pool reuse across 3 activities (assert 1 discovery call), network-free operation, empty pool, dedupe, semantic-arm failure.

**Done when:** replan works end-to-end offline; `SuggestionServiceTest` and `CandidateFinderTest` updated and green.

### M4b — Proactive polling (1.5 d, owned by a group mate) ∥

Depends on M1 (weather adapter) and M4 (shared discovery pool) — both have landed. Re-verified against the code on 2026-10-22; **three assumptions in the original draft were wrong** and are corrected before the work breakdown.

**What the code actually has today**

| Original claim | Reality (verified) |
| --- | --- |
| `V5` already has `trigger_source` + `notified_at` | No. `V5__trip_countries.sql` only creates `trip_countries`; no migration defines either column and `Suggestion` has neither field. Polling needs a new **`V8__polling.sql`** (V6 = embeddings, V7 = preferences). |
| "the existing reject cooldown still applies" | No cooldown exists anywhere. `reject` only writes `REJECTED` (`SuggestionService.java:147-153`); the sole suppression is `existsByActivityIdAndStatus(..., PENDING)` at `:277-280`. Without one, a rejected suggestion returns on the very next tick. This must be built in M4b.1, not assumed. |
| `@Scheduled` infrastructure exists | No scheduler at all: no `@EnableScheduling`, no `@Scheduled`, no scheduling properties. This lane introduces them. |
| Clock injection needed | Partly present: `NeaWeatherSource` holds a `Clock` field (`:76`, `:95`) but never reads it; `SuggestionService.replan` hardcodes `ZonedDateTime.now(SG)` at `:223`. |

No frontend work: `useSuggestions.ts:14` already polls `GET /api/trips/{id}/suggestions` every 30 s, so a scheduler-created row appears by itself.

**M4b.1 — Migration, entity fields, cooldown predicate (0.25 d)**
- `V8__polling.sql`: `ALTER TABLE suggestions ADD COLUMN trigger_source varchar(16) NOT NULL DEFAULT 'MANUAL', ADD COLUMN notified_at timestamptz;` plus an index on `(activity_id, status, responded_at)` to serve the dedupe and cooldown queries.
- `Suggestion`: add `triggerSource` (`MANUAL | POLL_24H | POLL_2H`) and `notifiedAt` (`Instant`). Additive fields only, no renames.
- Repository: `boolean existsByActivityIdAndStatusAndRespondedAtAfter(String activityId, SuggestionStatus status, Instant after)`.
- Cooldown rule (new, and required for the feature to behave): skip an activity when a `REJECTED` suggestion exists in the last `REJECT_COOLDOWN = Duration.ofHours(6)`. Apply it in the shared rule body so the manual path and the poller agree.
- Expose `triggerSource` on `SuggestionResponse` so the card can badge automated suggestions. `discoverySource` stays **out of scope** — it does not exist in code today and belongs to M4/M8.

**M4b.2 — Clock plumbing (0.25 d)**
- One `@Bean Clock clock()` (`Clock.system(SINGAPORE)`), injected into `SuggestionService` and `WeatherPoller`.
- Replace the hardcoded `now()` at `SuggestionService:223` with `ZonedDateTime.now(clock)`; use the same clock for `respondedAt` (`:138`, `:153`).
- Delete the dead `Clock` field in `NeaWeatherSource` or actually read it — one time source, not two.

**M4b.3 — `replanForSystem` (0.25 d)**
- `List<SuggestionResponse> replanForSystem(String tripId, String userId, ZonedDateTime at, TriggerSource trigger)`. `userId` may be null for scheduled runs — the trip's owner is not authenticated, so authorization must come from the caller's trust boundary (the poller), never from a fabricated user.
- Refactor `replan` (`:221-330`) by extracting the rule body into a private `replanInternal(..., boolean requireEditor)`; `replan` keeps its EDITOR check, `replanForSystem` skips only that check. Do **not** relax the manual path.
- All other rules unchanged: 24 h window (`:265-270`), max 3 (`:258-260`), PENDING suppression (`:277-280`), and the new cooldown from M4b.1.
- Stamps `triggerSource`; leaves `notifiedAt` null (nothing consumes it yet).

**M4b.4 — `WeatherPoller` (0.5 d)**
- New `polling/WeatherPoller`, annotation-gated: `@ConditionalOnProperty(prefix="app.polling", name="enabled", havingValue="true")`, with `@EnableScheduling` on a matching `@Configuration` so dev boots and the test suite never start a scheduler. Default `false` in `application.properties`.
- **24 h pass** — `@Scheduled(cron = "0 0 * * * *")`. Target trips whose `startDate` is tomorrow. Caveat: `Trip.startDate` is a `LocalDate` (`Trip.java:45`), so the original "`[now+20h, now+28h]`" band is not representable; "tomorrow in Asia/Singapore" is the honest equivalent. Record it as an accepted simplification rather than inventing a start time.
- **2 h pass** — `@Scheduled(fixedDelay = 10 min)`. Target activities with `startDateTime` in `[now+90m, now+150m]`.
- Weather gate **before** discovery and the LLM. Trip-based pass: derive the point from the trip's activity locations (the centroid, or the first activity if there is only one) because `Trip` carries no location; skip a trip with no activities. Activity pass: use that activity's `Location` lat/lon. Pass the matching window to `WeatherSource.isRainy(lat, lon, from, to)` — a >2 h window selects the four-day outlook, `<= 2 h` selects the two-hour feed (`NeaWeatherSource:114-117`), so the window choice *is* the feed choice. Do not add a new weather interface.
- Failure isolation: wrap each trip/activity in its own try/catch and log; one bad row must never abort the tick. NEA already swallows its own errors and returns `false` (`:120-123`).
- Repositories needed: `TripRepository.findByStartDate(LocalDate)` and `ActivityRepository.findByStartDateTimeBetween(ZonedDateTime, ZonedDateTime)`. Neither exists today.
- Accept that `replanForSystem` is trip-scoped: a 2 h tick can also propose for other eligible activities in that trip. Document it; do not add a per-activity filter unless the demo needs it.

**M4b.5 — Tests (0.25 d)**
- Fixed `Clock` at a known instant via a test `@Bean`; never rely on wall time.
- Window selection: activity at +100 min is picked, +80 min and +160 min are not; trip starting tomorrow is picked, today and +2 days are not.
- Dry weather → zero discovery calls and zero LLM calls (assert on mocks, not just on empty results).
- First rainy tick creates exactly one suggestion; second tick creates none (PENDING suppression).
- Reject → subsequent poller ticks never re-propose the stop; the manual `POST /replan` still can.
- Accept → activity re-opens for the next check.
- Clock injection test: `SuggestionService` produces the injected instant, not wall time.
- Scheduler disabled by default: context loads with no `@Scheduled` bean when `app.polling.enabled` is absent.

**Done when:** with a fixed rainy clock, one scheduler tick creates exactly one suggestion; an immediate second tick creates none; a dry tick creates none and makes no discovery/LLM call; a poller-triggered suggestion is not re-proposed after a rejection; a green `./mvnw test` on H2.

**Post-review amendments (2026-10-24):**
- `NeaWeatherSource` chooses the feed by **lead time**: two-hour feed when the window starts within 2 h, four-day outlook otherwise. A 1–3 h stop tomorrow now gets a forecast instead of a silent `false`.
- Both poller passes gate on the first **OUTDOOR** stop; the 24 h centroid averages outdoor stops only. Indoor-only activities make no weather call.
- A null `endDateTime` uses `start + 2 h` for the weather window (previously `now + 2 h`, which could invert the window).
- Rejection policy: poller triggers skip any activity that already has a `REJECTED` suggestion; the manual `POST /replan` is not suppressed. The 6 h cooldown was removed (it was bypassable via the client-supplied `at` and caused a re-propose loop).
- `TriggerSource` values are `MANUAL | POLL_TOMORROW | POLL_IMMINENT`.
- `createdAt` comes from the injected `Clock`; the client's `at` only drives window math.
- `notified_at` was dropped (V9); V10 adds the Postgres-only partial unique index on PENDING activity ids.
- `NeaWeatherSource` now reads the shared `Clock` bean instead of constructing its own; the NEA client's 15-minute cache still uses wall time.

**Risks / limits**
- Multi-instance duplication: fine on the single EC2 container; if the stack is ever scaled, add ShedLock or a DB advisory lock before enabling the poller on both.
- `NEAWeatherClient` caches for 15 min using `System.currentTimeMillis()`, so a 10-minute poll reuses the cache roughly every other tick. Harmless, but the cache ignores the injected `Clock` — do not assert cache TTL behaviour in tests.
- The poller writes suggestions without a human in the loop; if the demo expects only user-triggered suggestions, keep `app.polling.enabled=false` and show the manual `POST /replan` instead.

**Coordination contract (avoid lane collisions):**

| Touch point | Owner | Rule |
| --- | --- | --- |
| `SuggestionService.replan` restructure (M4) | plan lane | Already landed; polling extracts from it, keeps behaviour |
| `SuggestionService.replanForSystem` + `triggerSource` | polling lane | Additive; no relaxation of the manual EDITOR path |
| Migrations `V1`–`V7` | plan lane | Frozen. Polling adds `V8__polling.sql` + `V9__polling_cleanup.sql` (common) and `V10__suggestions_pending_unique.sql` (Postgres-only, `db/migration-postgresql`) |
| `Suggestion` entity | polling lane | Add fields only; no renames |
| Rejection policy | polling lane | Pollers never re-propose a rejected stop; the manual path is not suppressed — announce before editing `SuggestionService` |
| Frontend suggestion surface | plan lane | No change; existing `GET /suggestions` polling surfaces new rows |

### M5 — Preferences, group summary, and trip goal (3 d) ∥

- `preference/UserPreference`: `user_id`, `tags` CSV, `max_walk_km`, `dietary`, `indoor_preference`, `notes`, `updated_at`.
- `preference/TripPreference`: `trip_id`, `goals` CSV, `tags` CSV, `summary`, `summary_stale`, `updated_by`, `updated_at`.
- `GET /api/users/{id}/preferences` (public read per D5, structured fields only), `GET/PUT /api/users/me/preferences`.
- `GET/PUT /api/trips/{id}/preferences` (owner/editor writes; all members read), `POST /api/trips/{id}/preferences/summarise` to force regeneration.
- Summarisation: one LLM call over all members' preferences + goal → `summary`; deterministic fallback is comma-joined tags. Mark stale on any member preference change or goal change; regenerate lazily on read or explicitly.
- Wire `SuggestionService:216` to real interests = trip tags ∪ current user tags.
- Tests: converter round-trip, visibility rules, editor-only writes, summary fallback with no key, stale invalidation.

**Done when:** a preference change alters the deterministic score order in a test, and the summary appears in the prompt payload.

### M6 — Prompt upgrade (1 d)

- CGT-120: include previous and next activity (name, category, start time) in `PlanChoiceRequest`.
- Add `tripGoal`, `groupSummary`, `travellerNotes`, and the arrival weather line.
- Keep existing guarantees: shuffled candidates, no internal scores/tags, JSON-only, ids from the supplied set.
- Bump `max-tokens` only if explanations truncate (currently 800).

**Done when:** `OpenAiPlanChooserTest` asserts goal/summary/prev/next in the payload; validator tests unchanged and green.

### M7 — Observability and ablation (1 d)

- Persist/log `ai_called`, `ai_pick_used`, `fallback_reason`, `discovery_source`, `retrieval_mode`, `latency_ms`.
- `scripts/eval/` golden set: 10 SG scenarios (rainy afternoon at Marina Bay, hawker lunch, family with toddler, museum-focused goal…).
- Harness runs each scenario three ways — full pipeline, no AI, semantic arm disabled — and prints valid-OSM rate, indoor violations, recall@20, AI pick rate, p95 latency, manual relevance 1–5.

**Done when:** one command prints the comparison from a clean DB. This table is the report evidence.

### M8 — Frontend (2 d)

- Suggestion card: proposed place, reason, distance, opening status, provenance badge (`Curated` vs `OpenStreetMap`), attribution.
- Trip page: group preference summary + trip goal chips + "regenerate summary" (the not-yet-implemented trips-page surface).
- Personal preference settings, with a visible note that other users can see the structured fields.
- Manual "Check for disruptions" button → `POST /replan` (CGT-116).
- No typeahead against Nominatim.

### M9 — Day-level re-sequencing (2 d, stretch)

- Ask the LLM to reorder a day around a disruption; validate against the same candidate and opening-hours rules.
- Only start after M1–M8.

## 6. Data migrations

> **As built (2026-10-24):** the single `V5` draft below did not land as written. The schema lives in `V5__trip_countries.sql`, `V6__place_embeddings.sql`, and `V7__preferences.sql`. Polling added:
> - `V8__polling.sql` (common): `trigger_source` (NOT NULL DEFAULT `MANUAL`), `notified_at`, and the wider `(activity_id, status, responded_at)` index.
> - `V9__polling_cleanup.sql` (common): drops the superseded V4 index, drops `notified_at`, and renames `POLL_2H`/`POLL_24H` history rows.
> - `V10__suggestions_pending_unique.sql` (Postgres-only, `db/migration-postgresql`): dedupes PENDING rows per activity and adds a partial unique index. H2 cannot express partial indexes.
>
> `V3` was moved to `db/migration-postgresql` with byte-identical content (checksums preserved); the H2 migration test uses a syntax-only split copy in `src/test/resources/db/migration-h2`. Treat the block below as the original design intent, not current DDL.

`V5__preferences_and_embeddings.sql`:

```sql
CREATE TABLE user_preference (
  user_id varchar(8) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  tags varchar(255), max_walk_km numeric(4,2), dietary varchar(64),
  indoor_preference varchar(16), notes varchar(1024),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE trip_preference (
  trip_id varchar(8) PRIMARY KEY REFERENCES trips(id) ON DELETE CASCADE,
  goals varchar(255), tags varchar(255), summary varchar(1024),
  summary_stale boolean NOT NULL DEFAULT true,
  updated_by varchar(8) REFERENCES users(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE place_embedding (
  location_id varchar(8) PRIMARY KEY REFERENCES location(id) ON DELETE CASCADE,
  model varchar(64) NOT NULL,
  dims int NOT NULL,
  vector bytea NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE suggestions
  ADD COLUMN discovery_source varchar(16),   -- SEEDED | OSM_INDEX | SEMANTIC
  ADD COLUMN trigger_source varchar(16) NOT NULL DEFAULT 'MANUAL',  -- MANUAL | POLL_24H | POLL_2H
  ADD COLUMN notified_at timestamptz;
```

`trigger` is a reserved word in Postgres — use `trigger_source`. The polling columns land in `V8__polling.sql`; `V9__polling_cleanup.sql` removes `notified_at` and renames the trigger values, and Postgres-only `V10` adds the partial unique index.

No extension, no vector type, no HNSW index. `bytea` maps to `VARBINARY` on H2, so the test profile needs no special handling. Store floats as little-endian `float32`; validate `dims * 4 == vector.length` on read.

## 7. API changes

| Method | Path | Change |
| --- | --- | --- |
| POST | `/api/trips/{tripId}/replan` | unchanged body; now index + NEA + semantic backed |
| GET | `/api/trips/{tripId}/suggestions` | `proposedLocation` gains `discoverySource`, `osmType`, `osmId`; row gains `triggerSource`, `createdAt` |
| GET | `/api/users/{userId}/preferences` | new, public read (structured fields only) |
| GET/PUT | `/api/users/me/preferences` | new |
| GET/PUT | `/api/trips/{tripId}/preferences` | new; body includes `goals[]` |
| POST | `/api/trips/{tripId}/preferences/summarise` | new; regenerates the group summary |
| GET | `/api/trips/{tripId}/days/{date}/proposal` | M9 only |

## 8. Test plan

| Layer | Tests |
| --- | --- |
| Weather | `NeaWeatherSourceTest` — area mapping, window overlap, feed failure, out-of-SG, four-day fallback |
| Index | `SgPoiMapperTest` (tags → group/indoor/hours), `IndexImportTest` (idempotency) |
| Semantic | `CosineTest` (known vectors), `SemanticRankerTest` (order, empty, dim mismatch), `QueryEmbedderTest` (cache, API failure → empty), `PlaceDocumentBuilderTest` (no coordinates in text) |
| Discovery | `PlaceDiscoveryServiceTest` — union/dedupe, semantic-arm failure → SQL-only, empty, pool reuse |
| Planner | `CandidateFinderTest` (+non-seeded rows), `ScoreCalculatorTest` (hours exemption) |
| Suggestion | `SuggestionServiceTest` — fallback ladder, real interests, one discovery per replan |
| Polling | `WeatherPollerTest` + `PollingSimulationTest` — window in/out, outdoor-only gate, dry tick makes no discovery/LLM call, two consecutive rainy ticks → one suggestion, ACCEPTED re-opens, poller rejection suppression (manual still re-proposes), fixed `Clock` |
| AI | `OpenAiPlanChooserTest` (goal/summary/prev-next in payload), `PlanChoiceValidatorTest` (unchanged) |
| Preferences | converter round-trip, visibility, editor-only writes, summarisation fallback |
| Eval | golden set, three-way comparison |

Test seams: mock `EmbeddingModel` (deterministic vectors), fake `SemanticRanker`, `@MockitoBean` (not `@MockBean`). Everything runs on H2 with no network and no key.

Regression base: 196 tests / 28 classes. `CandidateFinder` and `SuggestionService` changes touch 19 of them — expect to update, not just add.

## 9. Metrics for the demo

| Metric | Target |
| --- | --- |
| Candidates within 3 km (median) | ≥ 10 |
| Valid-OSM rate | 100% by construction |
| Recall@20, semantic arm vs SQL-only | report; expected lift on vague preferences |
| AI pick rate vs deterministic top-1 | report actual; expect 60–85% |
| Fallback rate | < 20%, always logged |
| p95 replan, no LLM | < 1.5 s |
| p95 replan, full pipeline | < 3 s |
| Indoor violations on rainy swaps | 0 |
| One-off embedding cost for ~30 k POIs | ~$0.12 |
| $ per replan | < $0.001 |

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| Sparse OSM text → weak semantic recall | Descriptor text from tags + curated descriptions; SQL arm is the recall floor; measure recall@20 |
| Embedding dimension/model drift between index and query | Persist `model` + `dims` per row; skip/re-embed mismatches; validate on read |
| Missing embeddings (import partly run) | Semantic arm returns empty → SQL-only, logged as `retrieval_mode=SQL_ONLY` |
| OOM from loading vectors | Load only the bbox subset; 512 dims ≈ 2 KB/row |
| OpenAI key absent at startup | Retrieve via a lazily-created `EmbeddingModel` null-checked like `OpenAiPlanChooser`; no new starter, so startup behaviour is unchanged |
| Summaries go stale or drift from members' prefs | `summary_stale` flag, regenerate lazily, show "updated <time>" in UI |
| Group summary leaks a member's private notes | Summarise tags/constraints only; never include free-text `notes` verbatim |
| NEA area mapping wrong → false negatives | Unit-test every area; out-of-area → false |
| Opening-hours parser coverage low | Measure; exempt indexed rows from the penalty until coverage is acceptable |
| Graded on AI substance | Semantic ranking + preference summary are the substantive artifacts; M7 supplies the numbers |
| Branch drift | M0 merge first; separate branch |
| Poller and manual trigger race → duplicate suggestions | PENDING suppression is check-then-act; wrap in `@Transactional` with a pessimistic lock on the activity row (or catch the unique violation) |
| `@Scheduled` runs on every instance | Fine for one container; if scaled, add a DB advisory lock or run the poller as a single worker |
| Polling creates suggestions nobody sees | Surface them: badge on the trip page + `notified_at`; without a visible cue the feature looks broken |
| Polling burns LLM calls | Weather gate first — a dry tick makes zero discovery and zero LLM calls; cap suggestions per cycle |
| Poller tests non-deterministic | Inject `Clock`; never call `ZonedDateTime.now()` in scheduled code |
| **CGT-128 (country selection) implies non-SG weather** | **Closed: SG-only.** Country is stored as informational metadata; `WeatherSource` returns "unavailable outside SG". Amend the ticket text — do not add a global provider |
| Per-user summary (CGT-118) exposes inferred travel behaviour | Show the generated summary, never the source activities; restrict to trip co-members if the team agrees |
| Index and embeddings only exist on a developer laptop | Deployment ticket must run the import + embed job once per environment; prod needs `OPENAI_API_KEY` before first replan |
| Work with no Jira ticket (adapter, index, embeddings, polling) | Process is graded; create tickets before starting or the demo looks unplanned |

## 11. Order and critical path

```
M0 ──► M1 (weather) ──────────────┬──► M4b (polling)
   ├─► M2 (index) ──► M3 (semantic) ──► M4 (discovery) ──► M6 (prompt) ──► M7 (eval) ──► M9?
   └─► M5 (prefs+goal) ──────────────────────────────────┘        │
                                                                  └──► M8 (frontend)
```

M1, M2→M3, and M5 can run in parallel. M4 needs M2 and M3. M4b needs M1 and M4. M6 needs M4 and M5. Total ≈ 15 person-days (13.5 plan lane + 1.5 polling lane) + 2 stretch.

Suggested split: lane A M1 (weather/NEA); lane B M2→M3→M4 (index/semantic/discovery); lane C M5 (preferences/goal); **lane D M4b (polling, group mate)** starts once M1 and M4 land; frontend picks up M8 once M4/M5 shapes settle.

## 12. Ticket mapping

| CGT | Plan | Note |
| --- | --- | --- |
| CGT-104 | M1 + M7 | Cannot be evaluated until the NEA adapter exists |
| CGT-105 | M1 | 24 h window confirmed; drop the "four days" text |
| CGT-106 | M1 + M4 | Outdoor filter + per-activity rain window |
| CGT-116 | done | `POST /replan {"at"}` exists |
| CGT-120 | M6 | Cheapest high-value ticket; do early to de-risk the prompt |
| CGT-121 | M2 + M3 | Satisfied by construction: candidates are OSM objects with ids |
| CGT-122 / CGT-123 | delete one | Duplicates; query-text building replaces "AI writes Overpass QL" |
| CGT-117 | M5 | Trip category tags = trip goal. Wording says "when creating a trip" — confirm settings-only vs onboarding |
| CGT-124 | M5 | Personal category tags = `user_preference.tags`. Wording says "when creating my account" — same question |
| CGT-118 | M5 (+1 d) | Per-user AI summary **derived from past activity data**, not just selected tags. Not previously planned; privacy-sensitive because preferences are publicly readable |
| CGT-128 | informational only | **Resolved: SG-only.** Country is stored for display/filtering but never selects a weather provider. Amend the ticket's "relevant weather data" wording |
| CGT-109 / 111 / 112 / 114 | deployment lane | Index + embeddings must be generated in prod, not just dev; prod needs `OPENAI_API_KEY` |
| CGT-136 | none | Deletion latency: new cascading FKs (`place_embedding`, `user_preference`, `trip_preference`) add delete work — verify it does not regress this ticket |
| new | M1 | NEA adapter + area mapping (implied by CGT-104/105/106 but has no ticket of its own) |
| new | M2 | SG index expansion from 49 to the full whitelisted set |
| new | M3 | Embedding table + semantic ranking (advanced-capability requirement) |
| new | M4b | Proactive polling: 24 h trip check + 2 h activity check (owned by group mate; also has no ticket) |

## 13. Open questions

1. Do personal preferences need per-trip visibility, or is app-wide public read intended? (D5 default is public read.)
2. Should the group summary regenerate automatically on every member change, or only on demand? (Default: mark stale, regenerate lazily.)
3. Is the index rebuilt at build time, on startup, or by an admin script? (Recommendation: build script; import + embed via `@Profile("index")`.)
4. Does the module's rubric reward a specific retrieval technique (reranker, citations)? If so, add a reranker stage — still possible without pgvector.
5. Acceptable cost ceiling if the demo is run repeatedly.
6. Trip goal: fixed enum only, or free-text additions? (Default: fixed enum, keeps filtering deterministic.)
7. Polling: is 10 minutes the right activity cadence (NEA's two-hour feed refreshes roughly every 30 minutes), and is there an in-app notification surface, or only the suggestion list?

## 14. Progress

Last updated: 2026-10-24, after the P0/P1/P2 review of the polling lane (feed selection, poller gate, rejection policy, migrations V9/V10, Flyway smoke test).

Previous update: 2026-10-22, after merging `origin/main` into `feature/ai-discovery`.

| Milestone | State | Evidence / remaining work |
| --- | --- | --- |
| M0 branch hygiene | partial | Branch merged with `origin/main` (deploy lane) and pushed. Still open: `backend/.env.example` has no `APP_RAG_MODEL`/`APP_RAG_DIMENSIONS` entry; five local branches keep `[gone]` upstreams (`bugfixes`, `feature/activity-creation`, `feature/ai_integration`, `feature/invite-role-selection`, `planning-docs`); CGT-122/123 still both open. |
| M1 NEA adapter | done | `weather/NeaWeatherSource` + `NeaWeatherSourceTest`, `nea/area-centroids.json`; feed chosen by lead time (two-hour within 2 h, four-day beyond it), no assumed rain. |
| M2 SG index | done | `seed/OsmIndexImporter`, `OsmIndexRunner` + test, `scripts/build-sg-pois.sh` made practical. |
| M3 semantic ranking | done | `rag/` package (document builder, embedder, codec, ranker, runner), `V6__place_embeddings.sql`; degrades to SQL-only on bad keys or corrupt vectors. |
| M4 discovery | done | `discovery/PlaceDiscoveryService` + test; `CandidateFinder`/`CandidateRequest` rewired; `CategoryGoalMapper` maps goals to categories. |
| M4b polling | done | `polling/WeatherPoller` + `PollingConfig` gated by `app.polling.enabled` (false in dev/tests, true in the prod profile); `V8`/`V9`/`V10` migrations; NEA feed selected by lead time; both passes gate on the first OUTDOOR stop; `replanForSystem` rejects `MANUAL` and requires a user; pollers never re-propose a rejected stop while the manual path stays free; `createdAt` comes from the Clock, not the client's `at`. `PollingSimulationTest` + `WeatherPollerTest` + `FlywayMigrationTest` cover the path offline. 331 backend tests green (2026-10-24). |
| M5 preferences + goal | done | `preference/` package (entities, repositories, service, controller, summaries), `V7__preferences.sql`; trip summaries marked stale on member change. |
| M6 prompt | done | `prompts/choose-alternative.txt`; chooser receives trip context and emits decision metrics. |
| M7 eval | done | `scripts/eval/run-eval.sh`, `scripts/eval/scenarios.json`; replan harness added. Not yet run against a live key. |
| M8 frontend | done | `PreferenceChip`, `TravelPreferencesModal`, `TripPreferencesPanel`, `usePreferences`, wired into trip pages. |
| M9 stretch | not started | No day-level re-sequencing code. |

Verification: backend suite green on this branch — `./mvnw test` ran 331 tests, 0 failures, 0 errors, 0 skipped (2026-10-24), including `PollingSimulationTest`, `WeatherPollerTest`, and `FlywayMigrationTest` (V1–V9 on H2 with `ddl-auto=validate`). The frontend build and the eval harness against a live key were not run.
