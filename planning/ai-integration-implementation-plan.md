# AI Integration — Implementation Plan

Status: draft for review. Do not start M1 until the decisions in §1 are signed off.
Design reference: `ai-integration-design.md`.
Branch: `feature/ai_integration` (based on `8af2e1b`; rebase onto `main` after the invite-role PR merges).

## 1. Decisions to confirm first

| # | Decision | Proposed default | Impact if changed |
| --- | --- | --- | --- |
| D1 | What our score is made of | Hand-curated quality for 30–50 places + `quality_score`, `popularity_score` in `place_scores` | Seed work is wasted if the source changes |
| D2 | Interest tags | `history, nature, food, art, shopping, nightlife` | Prompt, seed groups, preference UI |
| D3 | Scoring weights | `0.5 similarity, 0.3 preference, 0.2 quality`; penalties as in the design note | Only ranking order |
| D4 | Unknown indoor for rain swaps | Excluded from candidates | If allowed, rain swaps may send users outdoors |
| D5 | AI inference of indoor | Offline only, `source=AI`, hand-reviewed | Per-request inference is unsupported and unsafe |
| D6 | LLM provider | Any OpenAI-compatible endpoint via env (`OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`) | Prompt/structured-output wiring |
| D7 | Shortlist sizes | 8 to the AI, 3 for the fallback | Cost/latency vs. choice quality |
| D8 | Sequence behaviour | Per-stop, just-in-time; never a day-long AI sequence | Major redesign of trigger/UI |

## 2. Definition of done

A trip with an outdoor stop and a rainy fixture forecast:

1. produces a suggestion card with 2–3 alternatives and a reason,
2. accepting it replaces the activity's location,
3. rejecting it records the decision and suppresses repeats,
4. the flow still works with no LLM key (fallback ranking),
5. all unit/integration tests pass, and the browser demo runs from a clean DB.

Out of scope: multi-stop re-sequencing, PostGIS, routing engines, Google verification, push notifications, multi-day AI plans.

## 3. Work breakdown

Estimates are person-days, not calendar dates.

### M0 — Branch and config hygiene (0.5 d)

- Rebase/cherry-pick after `feature/invite-role-selection` merges (otherwise its changes ride along in the AI PR).
- Decide how the unmerged `origin/feature/weather-forecast` enters this branch: merge, rebase, or reimplement behind our interface.
- Make the LLM key env-driven:
  - `spring.ai.openai.api-key=${OPENAI_API_KEY:}`
  - `spring.ai.openai.base-url=${OPENAI_BASE_URL:https://api.openai.com}`
  - `spring.ai.openai.chat.options.model=${OPENAI_MODEL:...}`
  - add an AI block to `backend/.env.example`.
- Add a `local`/test switch so the app boots with no key.

**Done when:** backend starts with no key, `application.properties` has no literal key, and the branch builds.

### M1 — Offline seed pipeline (1–1.5 d)

- `scripts/build-sg-pois.sh`: reproducible extract → filter → export (documented in the design note).
- Curate 30–50 places into `backend/src/main/resources/seed/sg_pois.json` with `source`, `extract_date`, `license`, and whitelisted fields only.
- `seed/SgPoiSeed`, `seed/PlaceSeeder`:
  - validation on load (fail fast on malformed/missing required fields),
  - import idempotent by `(osmType, osmId)` and name+distance fallback,
  - derives `category_group` from a tag map,
  - never overwrites manual edits on re-run.
- Seeder runs on startup only when the seed table is empty, or behind an explicit profile.

**Done when:** empty DB + startup = N seeded locations; second startup creates 0 duplicates; a corrupt seed file fails with a clear error; tests cover import and idempotency.

### M2 — Schema and domain (0.5–1 d)

- `V3__place_seed_and_scores.sql`:
  - `location`: `category_group`, `indoor_class`, `indoor_source`, `indoor_confidence`, `est_cost`, `open_intervals`, `seeded`
  - `place_scores(location_id, source, quality_score, popularity_score, scored_at)`
- Entity updates + `LocationRepository` bounding-box query.
- Keep H2 test profile (`ddl-auto=create-drop`, Flyway disabled) in sync.

**Done when:** Flyway applies on Postgres, Hibernate `validate` passes, H2 tests pass, rollback path is a drop + reseed.

### M3 — Indoor classifier and our scorer (1 d)

- `seed/IndoorClassifier`: rule table from the design note; explicit tag > category default > unknown; stores source/confidence.
- `planner/ScoreCalculator`: constants + formula, no AI.
- `planner/CategorySimilarity`: 6×6 table.
- Unit tests: one per classifier rule, unknown handling, ordering fixtures, penalty bounds.

**Done when:** classifier table tests pass; a fixed candidate set always produces the same ordering; no LLM dependency in these classes.

### M4 — Weather source (1–1.5 d, parallelisable)

Preferred: build behind an interface first so M5/M6 are not blocked.

- `weather/WeatherSource` interface.
- `weather/FixtureWeatherSource` (profile/test): rain at a configured area/time.
- `weather/NeaTwoHourClient`: NEA 2-hour forecast (`data.gov.sg`), area mapping, "rainy" text rule.
- `weather/WeatherEvent` entity + `V4__weather_events.sql`; poller + change detection.
- **Note:** the unmerged weather branch is the 4-day daily outlook, not this 2-hour feed. Reuse its client structure only.

**Done when:** fixture source drives suggestions deterministically; the real client parses a recorded NEA payload; poller stores only changed forecasts; no network in tests.

### M5 — Candidate finding and suggestion flow (1.5–2 d)

- `planner/CandidateFinder`: bbox → Haversine ≤3 km → indoor requirement → open at arrival → category compatibility → exclude current/in-itinerary → top N by our score.
- `planner/TriggerScan`: active trips, outdoor stops, rain in window, cooldown.
- `suggestion/` entity + `V5__suggestions.sql`; service with accept/reject/replan; owner/editor checks.
- Endpoints: `GET /api/trips/{tripId}/suggestions`, `POST /api/suggestions/{id}/accept`, `POST /api/suggestions/{id}/reject`, `POST /api/trips/{tripId}/replan`.

**Done when:** fixture rain + replan returns a shortlist; accept updates the activity location in one transaction; reject is persisted and suppresses repeats; non-members are denied; tests cover all of it.

### M6 — LLM chooser (1–1.5 d)

- `ai/PlanChooser` + request/response records.
- `ai/OpenAiPlanChooser`: Spring AI `ChatClient`, temperature 0.2, structured output, timeout.
- Prompt at `resources/prompts/choose-alternative.txt`; whitelisted candidate fields; candidates shuffled.
- Validation: unknown/duplicate ids, bad ranks, timeout → retry once → `FallbackPlanChooser`.
- Cache by `(candidate ids, forecast, preferences, itinerary slice, prompt version)`; persist `ai_score`, `ai_reason`, `chooser`.
- No live LLM in CI: fake chooser for valid/invalid/error cases.

**Done when:** valid output updates the suggestion; unknown id falls back without failing the request; missing key still produces a suggestion; one manual call returns schema-valid JSON.

### M7 — Frontend (1.5–2 d)

- API client + hook: suggestions, replan, accept, reject, polling.
- Suggestion card in `TripItinerary` / `TripDetailsPage`: alternatives, reason, accept/reject, pending and error states.
- "Check now" for owner/editor.
- Show "© OpenStreetMap contributors" + ODbL link where seed data is displayed.
- Re-validate on open/accept; no client-side trust of stale suggestions.

**Done when:** browser demo works without devtools; empty/error/pending states are handled; no layout regression on mobile.

### M8 — Verification and demo (1 d)

- End-to-end test with fixture weather + fake chooser.
- Demo script in `planning/` (steps + expected screens).
- README/attribution updates; `.env.example`; seed provenance.
- Run backend suite and frontend build.

**Done when:** clean DB → seed → demo runs; tests green; no secrets in the repo.

## 4. Order and critical path

```
M0 → M1 → M2 → M3 ─┬→ M5 → M6 → M7 → M8
                    └→ M4 (fixture first; real NEA can land after M6)
```

M4 can run in parallel from the start if the fixture source is built first.
M7 can start against the M5 API shape before M6 is finished.

## 5. Test plan

| Layer | Tests | Runs in CI |
| --- | --- | --- |
| Seed | parse, idempotency, dedupe, malformed input | yes (H2) |
| Indoor classifier | rule table, ambiguous `tourism=attraction`, unknown | yes |
| Scorer | ordering, penalties, bounds, deterministic ties | yes |
| Candidate finder | distance, open/closed/unknown, category, exclusions | yes |
| Weather | fixture source, recorded NEA payload, change detection | yes |
| Suggestions | accept updates activity, reject, permissions, replay suppression | yes |
| LLM | fake chooser: valid, unknown id, duplicate, timeout, malformed | yes |
| Manual | browser demo per M8 script | no |

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| Weather branch is unmerged and a different forecast product | Build behind `WeatherSource`; fixture first; merge explicitly in M0 |
| Seed curation takes longer than expected | 30 places is enough for the demo; pick 5 per category |
| `tourism=attraction` indoor data is ambiguous | Exclude unknown from rain swaps; manual flags for demo places |
| LLM hallucinates ids or places | Closed set, structured output, id validation, retry once, fallback |
| LLM latency/cost during demo | Async + cache + "Check now"; one call per stop |
| NEA/OSM rate limits | Offline seed; NEA poll ≤15 min; cache; never bulk Overpass/Nominatim |
| Prompt injection via OSM names | Whitelisted fields, no raw tags, fixed system prompt |
| ODbL violation | Attribution UI + seed metadata + license link |
| Shared dev Postgres schema drift | Flyway-only changes; `validate`; test profile H2 |

## 7. Deliverables

- `planning/ai-integration-design.md` (done)
- this plan
- `scripts/build-sg-pois.sh`, `backend/src/main/resources/seed/sg_pois.json`
- migrations V3–V5
- `seed/`, `planner/`, `ai/`, `weather/`, `suggestion/` packages
- frontend suggestion UI
- tests listed in §5
- demo script

## 8. First PR (suggested)

Keep it reviewable: **M0 + M1 + M2 + M3** only — config, seed pipeline, schema, classifier, scorer, tests. No LLM, no frontend. That proves the data foundation and ranking before anyone depends on the AI call.

## 9. Progress

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0 — config | **Done** | `application.properties` key/base-url/model env-driven; `backend/.env.example` documented; app boots with no key |
| M1 — seed | **Done** | `scripts/build-sg-pois.sh`; `seed/sg_pois.json` (49 places, 5 groups); `PlaceSeeder` + `PlaceSeedRunner`; first run `inserted=49, scoresInserted=49`, second run `inserted=0` |
| M2 — schema | **Done** | `V3__place_seed_and_scores.sql`; Location fields; `PlaceScore` entity/repository; bbox query |
| M3 — classifier + scorer | **Done** | `IndoorClassifier`, `CategorySimilarity`, `ScoreCalculator` with 29 unit tests |
| M4 — weather | **Seam ready; feed owned by teammate** | `WeatherSource` + default `NoRainWeatherSource` + dev `FixtureWeatherSource`; the NEA 2-hour implementation plugs in here |
| M5 — candidates + suggestions | **Done (backend)** | `OpeningHours`, `CandidateFinder`, `CategoryGroups`; V4 `suggestions`; `GET /api/trips/{id}/suggestions`, accept/reject, `POST /api/trips/{id}/replan`; imported OSM places now get category/indoor fields |
| M6 — LLM chooser | **Done** | `PlanChooser` + `OpenAiPlanChooser` + `choose-alternative.txt`; closed set, shuffled candidates, no internal scores, id/rank validation, fallback on no key/error/invalid output |
| M7 — frontend | **Done** | `SuggestionsPanel` + `useSuggestions` (30s poll, focus refresh); accept/dismiss; "Check now"; editor gating via `trip.roles`; itinerary remounts after accept; `npm run build` passes |
| M8 — verification/demo | Not started | Merge weather PR, live LLM check, end-to-end demo script, attribution |

Verification: backend `./mvnw test` → 194 tests, 0 failures; frontend `npm run build` passes. The LLM path is tested with mocked `ChatClient` only; no live call has been made (no key configured).

Operational notes:
- Seed import runs automatically only with the `dev` profile (`app.seed.enabled=true`); tests and the default profile do not seed.
- `V4__suggestions.sql` is taken; the weather teammate's `weather_events` migration should use **V5**.
- The dev profile enables `app.weather.fixture-enabled=true` with fixture rain, so `replan` is demoable before the NEA feed lands. When the real `WeatherSource` is added, mark it `@Primary` or remove the fixture/no-rain beans — otherwise Spring will see multiple `WeatherSource` beans.
- Replan scans only the **next 24 hours** and creates at most 3 suggestions per call; a pending suggestion suppresses new ones for that activity.
