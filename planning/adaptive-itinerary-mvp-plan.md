# Adaptive Itinerary — Implementation Plan (uni-scoped)

This is the **buildable** version of `itinerary-planner-tech-brief.md`. It keeps the
core idea and the "code decides, AI explains" principle, but cuts the production
machinery that a CS203 module does not need. It is written against the **current
repo** (Spring Boot 4.1.1 / Java 21 backend, React 19 + Vite 8 frontend, Clerk auth,
JPA + H2, OSM Nominatim/Overpass clients, Spring AI OpenAI dependency present but
unused).

Read the brief for the *why*. Read this for the *what to build, in what order*.

---

## 1. Scope

### The one feature (MVP)

**Rain is forecast at an outdoor stop → offer to swap that single stop for a nearby
indoor alternative the user can accept or reject.**

One trigger. One kind of change. End to end: weather in → suggestion out → user
decides → plan updates.

### Explicitly NOT in this plan

These are **future work**; mention them in the report, don't build them:

- LTA / train-alert triggers.
- Re-sequencing the day (the orienteering problem).
- PostGIS / spatial indexes.
- OneMap routing and `route_legs` / `travel_cache`.
- Web Push / PWA / service workers.
- `plan_version` history and undo.
- Google Places verification (optional stretch — see §8.3).
- Background location / ETA / "running late" triggers.

### Why these cuts are safe

- A single swap doesn't re-order the day, so no routing graph, no travel cache,
  no re-sequencing.
- Weather is the demo-able trigger and the only one that runs server-side without
  location.
- 3 km and 47 forecast areas are tiny data; on-demand Haversine in Java is faster
  to build and debug than PostGIS.
- In-app polling already exists in this repo (invitations) and avoids the iOS Web
  Push home-screen requirement entirely.

---

## 2. How it maps onto the current codebase

Reuse (already exists):

| Need                     | Existing thing                                                                                              |
| ------------------------ | ----------------------------------------------------------------------------------------------------------- |
| Stop                     | `trips/Activity.java` (has `startDateTime`, `endDateTime`, ordered `locations`)                     |
| Place / OSM cache        | `trips/Location.java` (has `category`, `isIndoor`, `osmType`/`osmId`, `openingHours`, lat/long) |
| Trip                     | `trips/Trip.java` (`startDate`/`endDate`, members, roles)                                             |
| User                     | `user/User.java`                                                                                          |
| Auth                     | Clerk JWT resource server (`config/SecurityConfig.java`)                                                  |
| OSM access               | `geo/OverpassClient.java`, `geo/NominatimClient.java`, `geo/PoiService.java`                          |
| HTTP client              | Spring`RestClient` (`config/OsmClientConfig.java`)                                                      |
| Caching                  | Caffeine (`config/CacheConfig.java`)                                                                      |
| LLM                      | `spring-ai-starter-model-openai` in `pom.xml` (declared, unused)                                        |
| Frontend polling pattern | `lib/useInvitations.ts` (30 s poll + refetch on focus)                                                    |
| Frontend API wrapper     | `lib/api.ts` (single `request<T>` + `ApiError`)                                                       |
| 8-char string IDs        | `util/ID.java`                                                                                            |

Build new (~everything in §5–§7).

---

## 3. First decision: persistence

Your H2 config is `jdbc:h2:mem:itinerarydb` + `ddl-auto=create-drop`
(`application.properties:8-14`). **Every restart wipes all tables.** The moment you
seed a places cache or store weather events, that work disappears on reboot.

**Do the DB switch first, before building the places cache.** Ordering matters:
build the cache on a throwaway DB and you will re-seed and re-tag everything.

Recommended:

- **Postgres** (MySQL 8 is acceptable; Postgres preferred for JSONB and tooling).
- Dev: `spring.jpa.hibernate.ddl-auto=update`.
- Optional polish (nice for the report, not required): **Flyway** with
  `V1__baseline.sql` (existing entities) + `V2__adaptive_itinerary.sql`.
- Keep **H2** as the `test` profile so existing tests and CI keep running without a
  live database. `application-test.properties` → H2 + `create-drop`.

`application.properties` change:

```properties
# Postgres (dev)
spring.datasource.url=jdbc:postgresql://localhost:5432/itinerary
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

Add `org.postgresql:postgresql` to `pom.xml` (runtime). No PostGIS, no spatial types.

`run.sh` already copies `backend/.env.example` → `backend/.env`; add `DB_USER` /
`DB_PASSWORD` there and in the example file.

---

## 4. The pipeline (end to end)

```
[1] NEA poller (@Scheduled)          → weather_events (only changed forecasts)
        │
        v
[2] Trigger scan (every N min)       → for each active trip, flag outdoor stops
        │                               whose nearest NEA area forecasts rain in
        │                               their time window
        v
[3] Candidate finder + filter        → indoor places within ~3km, open at arrival,
        │                               within budget
        v
[4] Score + shortlist (top 3)        → similarity + preference + popularity
        │                               − distance − unknown-hours − cost
        v
[5] LLM chooses + explains           → validated; falls back to top score
        │
        v
[6] Save suggestion + expose API     → frontend polls, shows a card
        │
        v
[7] accept → replace the stop's       reject → log status
    location(s); reject → log
```

Steps 1 and 2 are scheduled. Steps 3–6 run for a flagged trip. There is also a
**manual "check now" endpoint** (§6.4) so you can run steps 2–6 on demand for
demos and tests without waiting for real rain.

---

## 5. Data model

Minimal additions. Reuse `locations` as the places cache — do **not** create a
second places table.

### 5.1 `Location` (extend — this is the places cache)

Add columns:

| Field               | Type          | Purpose                                                                                              |
| ------------------- | ------------- | ---------------------------------------------------------------------------------------------------- |
| `category_group`  | String        | Coarse group for similarity:`Nature` / `History` / `Art` / `Shopping` / `Food` / `Other` |
| `is_popular`      | Boolean       | Hand-set for top places so quality ranks from the start                                              |
| `est_cost`        | Double        | Rough entry cost (SGD), nullable                                                                     |
| `open_intervals`  | String (JSON) | Normalised opening hours (see §8.2);`null` = unknown                                              |
| `google_place_id` | String        | Optional, only if you do the Google stretch                                                          |
| `seeded`          | Boolean       | `true` for bulk-seeded POIs vs user-created places                                                 |

Keep `openingHours` (raw OSM string) for display; `open_intervals` is the
machine-readable form used by the filter.

### 5.2 `User` (extend)

| Field             | Type         | Purpose                                                                 |
| ----------------- | ------------ | ----------------------------------------------------------------------- |
| `interest_tags` | String (CSV) | Chosen from a fixed list:`history,nature,food,art,shopping,nightlife` |
| `avoid_crowds`  | Boolean      | Preference flag passed to the LLM                                       |

Free-text preferences (stretch): an LLM call at save time converts prose → tags +
flags. One call, not per replan.

### 5.3 New entity `WeatherEvent`

The poller's output. Store only **changed** forecasts (see §8.1).

| Field                       | Notes                                                                         |
| --------------------------- | ----------------------------------------------------------------------------- |
| `id`                      | 8-char via`util/ID`                                                         |
| `source`                  | `"NEA"`                                                                     |
| `area`                    | NEA area name, e.g.`"Tanglin"`                                              |
| `condition`               | e.g.`"Thundery Showers"`                                                    |
| `validFrom` / `validTo` | `Instant` / `ZonedDateTime`                                               |
| `raw`                     | `@Lob`/`@Column(columnDefinition="text")` original JSON — enables replay |
| `fetchedAt`               | `Instant`                                                                   |

### 5.4 New entity `Suggestion`

| Field                         | Notes                                                      |
| ----------------------------- | ---------------------------------------------------------- |
| `id`                        | 8-char                                                     |
| `trip`                      | `@ManyToOne`                                             |
| `activity`                  | `@ManyToOne` — the stop being swapped                   |
| `weatherEvent`              | `@ManyToOne` — what caused it                           |
| `proposedLocation`          | `@ManyToOne` → `Location`                             |
| `currentLocation`           | `@ManyToOne` → `Location` (for the before/after view) |
| `reason`                    | Short code/text, e.g.`"RAIN_OUTDOOR_STOP"`               |
| `explanation`               | The LLM's sentence (or a code-generated fallback)          |
| `candidates`                | text/JSON: the shortlist (ids + scores) for debugging/eval |
| `aiChoice`                  | Plan id the LLM picked                                     |
| `topScoreChoice`            | Plan id code would have picked                             |
| `status`                    | enum`PENDING` / `ACCEPTED` / `REJECTED`              |
| `createdAt` / `decidedAt` | timestamps                                                 |

Enums: `SuggestionStatus`, `DisruptionReason`.

That is **two new tables** (`weather_events`, `suggestions`) plus columns. Not eight.

---

## 6. Backend implementation

New packages under `com.smu.cs203_g1_1.itinerary_planner`:

```
weather/     NEA client, poller, area mapping, WeatherEvent entity+repo+service
planner/     trigger scan, candidate finder/filter, scoring, suggestion builder
ai/          PlanChooser interface + OpenAI implementation
seed/        PlaceSeeder (bulk-load OSM POIs into Location)
suggestion/  Suggestion entity+repo, SuggestionService, SuggestionController
```

### 6.1 `weather/`

- `NeaForecastClient` — `RestClient` against
  `https://api-open.data.gov.sg/v2/real-time/api/two-hr-forecast`. No API key needed.
  Implements a `WeatherSource` interface.
- `FixtureWeatherSource` — reads a recorded JSON from `resources/weather/*.json`.
  Selected via a profile (e.g. `app.weather.source=fixture|live`). **This is what
  makes the system testable and demoable.**
- `NeaForecastPoller` — `@Scheduled(fixedDelayString = "${app.weather.poll-ms:1800000}")`.
  Requires `@EnableScheduling` (none exists today).
- `WeatherEventService` — normalises payload → events, de-duplicates (skip if the
  condition for an area is unchanged from the last stored event).
- `NeaAreaLocator` — Haversine to the nearest NEA area label; returns the area name.

### 6.2 `planner/`

- `TriggerService` — for each trip active today, for each activity with an outdoor
  primary location, find a `WeatherEvent` for the nearest area where the condition
  is "rainy" (see §8.1) and the event window overlaps the activity's window.
  Skip if a `PENDING`/recent suggestion already exists for that activity (cooldown).
- `CandidateFinder` — SQL bounding-box query on `location` around the flagged stop,
  `seeded=true`, `is_indoor=true`, `category_group` in the allowed set, exclude the
  current place. Then Haversine-refine to ≤ 3 km in Java.
- `CandidateFilter` — list of predicate functions run over each candidate; a
  candidate survives only if all pass:

  1. `isOpenAt(open_intervals, arrival)` — three-state; `unknown` passes with a
     penalty, `closed` fails.
  2. `withinBudget(est_cost, remainingBudget)`.
  3. `fitsWindow(arrival, activityDuration, nextActivityStart)`.
     Adding a rule = adding one function (this is the brief's best idea — keep it).
- `ReplacementScorer` — weights in config:

  ```
  score = w1*similarity + w2*preferenceMatch + w3*popularity
          − distancePenalty − costPenalty − unknownHoursPenalty
  ```

  Suggested starting values (tune by eyeballing the demo scenarios):
  `w1=0.5, w2=0.3, w3=0.2`, `distancePenalty = min(1.0, km/3)`,
  `costPenalty = min(0.3, est_cost/100)`, `unknownHoursPenalty = 0.25`.
- `SuggestionBuilder` — builds the shortlist (top 3), asks the `PlanChooser`,
  validates, persists a `Suggestion`.

### 6.3 `ai/`

- `interface PlanChooser { PlanChoice choose(PlanChoiceRequest req); }`
- `OpenAiPlanChooser` — Spring AI `ChatClient` (starter already in `pom.xml`),
  `temperature ≈ 0.2`, `.entity(PlanChoice.class)` for structured output.
- `PlanChoice` = `record PlanChoice(String choice, String explanation)`.
- `FallbackPlanChooser` — used when the API key is the placeholder or the call
  fails; returns the top-scored plan with a templated explanation.
- Code validates `choice` is one of the offered ids; anything else → fallback.
- Keep the provider behind the one interface so switching models is one file.

Draft system prompt (in `resources/prompts/choose-plan.txt`):

```
You help users of a Singapore itinerary app choose between alternative plans for
the rest of their day.

You receive: the user's preferences, the reason a change is needed, and 2–3
candidate plans, each with an ID, the place, timings and cost.

Rules:
- Choose exactly one plan by its ID. Never invent or modify a plan.
- Prefer plans that fit the user's preferences and change the original day the
  least, unless another plan is clearly better.
- Write a 1–2 sentence explanation in plain, friendly English.
- Do not mention scores, IDs or internal details.

Respond only in the required JSON format.
```

### 6.4 REST endpoints

| Method + path                           | Purpose                                                                                                 |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `GET /api/trips/{tripId}/suggestions` | Pending + recent suggestions (frontend polls this)                                                      |
| `POST /api/suggestions/{id}/accept`   | Apply the swap; update the activity's locations                                                         |
| `POST /api/suggestions/{id}/reject`   | Mark rejected (log)                                                                                     |
| `POST /api/trips/{tripId}/replan`     | **Manual "check now"** — runs the pipeline for one trip (demo/testing). Allowed for owner/editor |
| `GET /api/admin/demo-data`            | (optional) seed trigger for demos                                                                       |

Reuse the existing `TripRole` guard style for who may accept (owner/editor; viewers
cannot) — this is the shared-trip question the brief ignored.

---

## 7. Frontend implementation

### 7.1 Types + API (`lib/api.ts`)

Add a `Suggestion` type and:

- `getTripSuggestions(token, tripId)`
- `acceptSuggestion(token, id)`
- `rejectSuggestion(token, id)`
- `runReplanCheck(token, tripId)` — the manual trigger button

### 7.2 Hook (`lib/useSuggestions.ts`)

Mirror `useInvitations.ts`: poll every 30 s (skip when `document.hidden`), refetch on
window focus, expose `accept()` / `reject()` that update local state. Copy the
`CLERK_CONFIGURED` conditional-impl pattern so the app still runs without a Clerk key.

### 7.3 UI

- `components/suggestions/SuggestionCard.tsx` — "Rain expected at the Botanic
  Gardens — swap for the National Museum? [Accept] [Reject]", with a before → after
  line (current place → proposed place) and the explanation sentence.
- Render it on the trip page (`components/trips/TripItinerary.tsx`), and/or a navbar
  indicator like the invitations red dot.
- On accept, refresh the itinerary so the new place shows.
- A "Check now" button on the trip page that calls `runReplanCheck` — **essential
  for demos** (no waiting for rain).

Design quality note: the SuggestionCard is user-facing. Keep it consistent with the
existing token-based theme (`bg-surface-raised`, `text-foreground`, `bg-accent`,
`text-accent-contrast`) — see `index.css`. Do not hardcode light colours (that exact
bug already happened in `ManualLocationModal`).

---

## 8. External integrations

### 8.1 NEA weather (data.gov.sg v2)

- Endpoint: `https://api-open.data.gov.sg/v2/real-time/api/two-hr-forecast`
  (2-hour forecast, ~30 min cadence; `/rainfall` every ~5 min if you want it later).
- No API key for low volume.
- **Verify the exact JSON shape** when you wire it: the payload has area metadata
  (name + `label_location` lat/long) and per-area forecasts with a validity period.
  Map a stop to the **nearest area label** via Haversine — do not attempt
  point-in-polygon.
- "Rainy" = condition text contains `Shower`/`Rain`/`Thunder` (case-insensitive).
  Treat `Drizzle`/`Light` as below threshold so you don't replan on drizzle.
- De-duplicate: only store a `WeatherEvent` when the area's condition changes.

### 8.2 Opening hours (the weakest data)

OSM `opening_hours` strings are patchy. Two pragmatic choices:

- **Normalise at seed time.** For the ~30–50 places you actually demo, store a
  structured `open_intervals` JSON (`{"mon":[["09:00","19:00"]], ...}` or a daily
  `open`/`close` list) on `Location`. This avoids writing an OSM `opening_hours`
  parser in Java entirely, which is the real trap.
- Three states: **open / closed / unknown**. Missing = unknown = score penalty, never
  treated as open. Only confirmed-closed is eliminated.
- Public holidays: OSM `PH` rules need a holiday calendar; since you normalise
  manually for seeded places, just encode holiday closures where they matter. If you
  later parse raw OSM strings, remember Singapore is **not** in `opening_hours.js`'
  built-in country list.

### 8.3 Google Places (optional stretch)

Skip for the MVP. If added: it's a **Place Details (New)** call, and hours +
`priceLevel` are the **Enterprise** SKU. Only `place_id` may be stored long-term.
Since cost doesn't matter to you, the only real risk is integration time — do it
last, behind the same "verify the shortlist" seam.

### 8.4 OSM / Overpass (seeding)

- Runs **offline**, once, via a `PlaceSeeder`. The existing `PoiService` already
  whitelists categories and calls Overpass.
- Overpass is fine when you're running locally; note that OSM now blocks some cloud
  IP ranges, so if you deploy to AWS/Azure, prefer a **Geofabrik Singapore extract**
  processed offline instead. Keep the seed output in the DB, not live calls.
- After seeding, hand-fix `is_indoor`, `category_group`, `est_cost`, `is_popular`
  and `open_intervals` for your demo places.

### 8.5 LLM

- Use the OpenAI starter already present, pointed at any OpenAI-compatible endpoint
  via `spring.ai.openai.base-url` + `api-key` env (OpenAI, Gemini's OpenAI-compat
  endpoint, or OpenRouter). Free/cheap tier is fine.
- `temperature ≈ 0.2`, structured output, thinking off if the model supports the flag.
- Validate the returned `choice` against the offered ids; fall back to top score on
  any error/timeout. The plan the user sees always comes from code.

---

## 9. Scoring design (concrete)

Tag → group mapping (done once, at seed time):

```text
(leisure,park|garden|nature_reserve) → Nature
(tourism,museum)|(historic,*)        → History
(tourism,gallery)                    → Art
(shop,mall)                          → Shopping
(amenity,food_court)|(amenity,restaurant) → Food
else                                 → Other
```

Similarity table (starting point):

|          | Nature | History | Art | Shopping | Food | Other |
| -------- | ------ | ------- | --- | -------- | ---- | ----- |
| Nature   | 1.0    | 0.4     | 0.4 | 0.1      | 0.2  | 0.2   |
| History  | 0.4    | 1.0     | 0.7 | 0.1      | 0.2  | 0.2   |
| Art      | 0.4    | 0.7     | 1.0 | 0.2      | 0.2  | 0.2   |
| Shopping | 0.1    | 0.1     | 0.2 | 1.0      | 0.4  | 0.2   |
| Food     | 0.2    | 0.2     | 0.2 | 0.4      | 1.0  | 0.2   |
| Other    | 0.2    | 0.2     | 0.2 | 0.2      | 0.2  | 0.5   |

- `similarity` = table[current.stop.group][candidate.group].
- `preferenceMatch` = 1.0 if candidate.group matches one of the user's
  `interest_tags`, else 0.
- `popularity` = `is_popular ? 1 : 0`.
- Keep the **top 3** and pass them to the LLM.

Scores are used by code only; **never shown to the LLM** and never shown to the user.

---

## 10. Testing & demo strategy

This is the part that decides whether the project is presentable.

- **Fixture replay.** Record one or two real NEA responses into
  `resources/weather/`. `FixtureWeatherSource` lets tests and demos run
  deterministically. This is the single most valuable piece of infrastructure.
- **Unit tests** (JUnit, already in the repo):
  - `CandidateFilter` — open/closed/unknown, budget, window.
  - `ReplacementScorer` — a mall ranks below a museum for a "likes history" user.
  - `NeaAreaLocator` — known lat/long → expected area.
  - `PlanChoice` validation — bad id → fallback.
- **Integration test**: load fixture weather + seeded trips → run pipeline →
  assert a `Suggestion` is created for the outdoor stop and not for indoor ones.
- **Demo path**: a seeded "Mei" trip + a "Check now" button + a rain fixture. You can
  show the whole loop on demand instead of praying for a thunderstorm.
- Keep the existing test profile on **H2** so CI (`.github/workflows/ci.yml`) keeps
  passing without a database.

---

## 11. Build order / milestones

Each step ends in something you can demo or test.

| Week | Deliverable                                                                                                  |
| ---- | ------------------------------------------------------------------------------------------------------------ |
| 1    | Postgres switch + new columns/tables;`PlaceSeeder` loads and hand-tags ~30 SG POIs; H2 test profile intact |
| 2    | `NeaForecastClient` + fixture replay; `@Scheduled` poller; `WeatherEvent` de-dup; `NeaAreaLocator`   |
| 3    | `TriggerService` + `Suggestion` persistence; candidates found for a flagged stop (log only)              |
| 4    | `CandidateFilter` + `ReplacementScorer` → top 3; manual `/replan` endpoint                            |
| 5    | `PlanChooser` (OpenAI) + validation + fallback; explanation text                                           |
| 6    | Frontend:`useSuggestions`, `SuggestionCard`, accept/reject, "Check now"                                  |
| 7    | Polish, tests, demo script, report;**only then**, second trigger if time                               |

Definition of done for the MVP: with fixture rain loaded, tapping "Check now" on the
Mei trip produces a suggestion to swap the Botanic Gardens for an indoor museum;
accepting it updates the itinerary; rejecting logs it.

---

## 12. Risks & mitigations

| Risk                                   | Mitigation                                                                |
| -------------------------------------- | ------------------------------------------------------------------------- |
| H2`create-drop` wipes seed/events    | Switch to persistent Postgres**before** building the cache (Week 1) |
| Weather is unpredictable → can't demo | `FixtureWeatherSource` + manual `/replan` (Week 2)                    |
| OSM hours/indoor quality               | Normalise + hand-tag demo places; three-state unknown handling            |
| Overpass blocked on cloud deploy       | Seed offline / Geofabrik extract; never call Overpass live                |
| LLM returns junk or wrong id           | Validate against offered ids; fall back to top score                      |
| Shared trips: who accepts?             | Owner/editor only; reuse`TripRole` guard                                |
| Scope creep                            | §1 non-goals is a hard line; second trigger only after Week 7            |

**Biggest risk:** doing the DB switch last. It silently invalidates the cache work.

---

## 13. Decisions (log)

- Web app, weather-only trigger, foreground awareness only.
- Single-stop swap; re-sequencing is future work.
- Code decides, AI explains; AI picks from code-validated options; code validates the pick.
- `Location` is the places cache; no separate `places` table; no PostGIS.
- Persistence: Postgres (MySQL acceptable), H2 only in tests.
- Opening hours normalised at seed time; three-state open/closed/unknown.
- In-app polling for suggestions; no Web Push.
- Only owner/editor may accept; viewers cannot.

## 14. Open questions

1. Postgres or MySQL? (default: Postgres)
2. Interest-tag list — is `history,nature,food,art,shopping,nightlife` enough?
3. Scoring weights — confirm after the first demo scenario.
4. Seed scope — which ~30–50 SG places are "demo places"?
5. LLM provider/model + who holds the key.
6. On no valid replacement: keep the plan silently, or show "no indoor option nearby"?
   (default: silent, log only)
