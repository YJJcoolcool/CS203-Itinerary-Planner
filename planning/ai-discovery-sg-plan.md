# AI Discovery (Singapore) — Implementation Plan

Status: draft for review. Supersedes `ai-integration-implementation-plan.md` for discovery, preferences, and retrieval.
Branch: new branch `feature/ai-discovery` cut from `main` after `feature/ai_integration` merges.
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

Depends on M1 (weather adapter) and M4 (shared discovery pool). Do **not** start before both land.

- `SuggestionService.replanForSystem(String tripId, ZonedDateTime at, TriggerSource trigger)` — the system path. It skips the EDITOR check but keeps every other rule (24 h window, PENDING suppression, reject cooldown, max 3). The existing EDITOR-gated `replan` is **not** relaxed.
- `polling/WeatherPoller`:
  - 24 h pass (`@Scheduled(cron = "0 0 * * * *")`): trips starting in `[now+20h, now+28h]`, four-day outlook (region-level).
  - 2 h pass (`@Scheduled(fixedDelay = 10 min)`): activities starting in `[now+90m, now+150m]`, two-hour forecast (area-level).
  - Gate on weather **first**: if `isRainy` is false, skip discovery and the LLM entirely. One NEA fetch per cycle (the client already caches for 15 min, so a 10-minute poll reuses the cache half the time).
- Idempotency: a `PENDING` suggestion suppresses repeats; `ACCEPTED`/`REJECTED` re-open the activity for the next check; the existing reject cooldown still applies.
- Add `Clock` injection to the time-dependent code — `ZonedDateTime.now(Asia/Singapore)` is currently hardcoded, which makes scheduler tests non-deterministic.
- Tests: window selection (in/out), no-rain → zero discovery calls, PENDING suppression across two cycles, ACCEPTED re-opens, cooldown honoured, clock fixed at a known instant.

**Done when:** with a fixed rainy clock, one scheduler tick creates exactly one suggestion; a second tick creates none; a dry tick creates none and makes no discovery/LLM call.

**Coordination contract (avoid lane collisions):**

| Touch point | Owner | Rule |
| --- | --- | --- |
| `SuggestionService.replan` restructure (M4) | plan lane | Lands **first**; polling builds on it |
| `SuggestionService.replanForSystem` | polling lane | Additive only — no edits to the manual path |
| `V5` migration | plan lane | Already contains `trigger_source` + `notified_at`; polling must **not** add a second `V5` |
| `Suggestion` entity | polling lane | Add fields only; no renaming |
| Frontend suggestion surface | plan lane | Polling shows up through the existing `GET /suggestions` |

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

`trigger` is a reserved word in Postgres — use `trigger_source`. Both polling columns live in `V5` (owned by the plan lane) so the polling lane does not create a competing `V5`.

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
| Polling | `WeatherPollerTest` — window in/out, dry tick makes no discovery/LLM call, two consecutive rainy ticks → one suggestion, ACCEPTED re-opens, cooldown honoured, fixed `Clock` |
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

| Milestone | State |
| --- | --- |
| M0 branch hygiene | not started |
| M1 NEA adapter | not started |
| M2 SG index | not started |
| M3 semantic ranking | not started |
| M4 discovery | not started |
| M4b polling | not started (group mate) |
| M5 preferences + goal | not started |
| M6 prompt | not started |
| M7 eval | not started |
| M8 frontend | not started |
| M9 stretch | not started |
