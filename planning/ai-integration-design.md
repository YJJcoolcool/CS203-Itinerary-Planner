# AI Integration Design — offline POI seed + AI re-ranking

Status: proposed, not implemented.
Scope: CGT-88 / CGT-89 (AI suggests an alternative when the original plan has a problem).
Companion docs: `adaptive-itinerary-mvp-plan.md` (product/architecture), `itinerary-planner-tech-brief.md` (rationale).

## 1. Core decision

Code decides what is *possible*; the AI decides what is *preferable* and explains why.

- Code filters candidates on hard facts (distance, open at arrival, indoor, category, budget).
- Our own score ranks the survivors and picks the shortlist.
- The AI re-ranks that shortlist **with our score hidden**, using user preferences and itinerary context.
- If the AI fails, times out, or returns an unknown id, the app falls back to our ranking.

The AI never discovers places, never checks opening hours, never decides whether it is raining,
and never plans a sequence of stops.

## 2. Data source

There is no Singapore-only Geofabrik extract. Geofabrik publishes
`asia/malaysia-singapore-brunei-latest.osm.pbf` (combined, ~240 MB).

| Source | Size | Use |
| --- | --- | --- |
| `https://download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf` | ~36 MB | **Primary.** Singapore-only |
| `https://download.bbbike.org/osm/bbbike/Singapore/` | ~37 MB PBF | Fallback |
| `https://download.geofabrik.de/asia/malaysia-singapore-brunei-latest.osm.pbf` | ~240 MB | Only if Geofabrik is required; must be clipped |

Accepted formats: `.osm.pbf` only. Do not use the Geofabrik `.shp.zip` / `.gpkg.zip`
bundles (480–494 MB, derived layers, less convenient).

### 2.1 One-time extract (not committed)

```bash
brew install osmium-tool

# Primary source; skip the extract step.
curl -O https://download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf

# Geofabrik alternative: clip first (bbox includes Johor/Batam slivers).
# osmium extract -b 103.6,1.15,104.1,1.48 \
#   malaysia-singapore-brunei-latest.osm.pbf -o singapore-rough.osm.pbf

# Keep only POI categories we use.
osmium tags-filter singapore-latest.osm.pbf \
  nwr/tourism=attraction,museum,gallery,viewpoint,zoo,theme_park \
  nwr/shop=mall,department_store \
  nwr/leisure=park,bowling_alley \
  nwr/amenity=restaurant,cafe,food_court,marketplace,cinema,library \
  -o sg-pois.osm.pbf

# Export to JSON for shaping (stable @type/@id for seed references).
osmium export -u type_id -a type,id sg-pois.osm.pbf -f geojson -o sg-pois.geojson
```

`scripts/build-sg-pois.sh` wraps these commands.

Commit the **curated seed JSON** (tens of KB), never the PBF/GeoJSON extract.

### 2.2 Attribution (required)

- Visible credit in the UI: `© OpenStreetMap contributors`.
- Link to https://www.openstreetmap.org/copyright stating ODbL 1.0.
- Seed file carries `source`, `extract_date`, and `license` metadata.
- A cached/extracted POI database is a Derivative Database; keep it ODbL.

## 3. Seed shape

`backend/src/main/resources/seed/sg_pois.json`:

```json
{
  "source": "OpenStreetMap via download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf",
  "extract_date": "2026-10-17",
  "license": "ODbL-1.0",
  "attribution": "© OpenStreetMap contributors",
  "places": [
    {
      "osm_type": "node",
      "osm_id": 123456,
      "name": "National Museum of Singapore",
      "category": "museum",
      "category_group": "culture",
      "latitude": 1.2966,
      "longitude": 103.8485,
      "indoor_class": "INDOOR",
      "indoor_source": "MANUAL",
      "opening_hours": "Mo-Su 10:00-19:00",
      "address": "93 Stamford Road Singapore",
      "est_cost": 15,
      "quality_score": 0.9,
      "popularity_score": 0.85,
      "tags": { "tourism": "museum", "wikidata": "Q1234" }
    }
  ]
}
```

Rules for the seed set:

- 30–50 places, hand-checked. This is a curated set, not "all Singapore POIs".
- One element per venue: dedupe by `(osm_type, osm_id)`, then `wikidata`, then
  normalised name + proximity (<50–100 m) to collapse node/way/relation duplicates.
- Keep only whitelisted tags; never store arbitrary `tags` blobs from OSM.
- `unknown` is a valid value for indoor/hours; do not guess to fill it.

## 4. Our score

Intrinsic facts stay on `locations`. Our score lives in `place_scores` so its source and
age are visible and it can be recomputed without touching the place record.

```sql
place_scores (
  location_id     varchar(8) not null references location(id),
  source          varchar(16) not null,   -- MANUAL | RULE
  quality_score   numeric(4,3) not null,  -- 0..1, curated notability/quality
  popularity_score numeric(4,3) not null, -- 0..1, Wikidata/manual proxy
  scored_at       timestamptz not null,
  primary key (location_id, source)
)
```

`Location` gains: `category_group`, `indoor_class`, `indoor_source`, `indoor_confidence`,
`est_cost`, `open_intervals` (parsed, JSON-encoded text), `seeded`.

Shortlist score (code only, never shown to the AI or the user):

```
score = 0.5 * similarity + 0.3 * preferenceMatch + 0.2 * quality
        - distancePenalty - costPenalty - unknownHoursPenalty
```

- `similarity`: hand table over `category_group` (museum → gallery, mall → shopping, …)
- `preferenceMatch`: 1 when the candidate group matches a user `interest_tag`
- `distancePenalty = min(1, km / 3)`, `costPenalty = min(0.3, est_cost / 100)`,
  `unknownHoursPenalty = 0.25`
- Keep the top 5–8 for the AI; keep the top 3 as the deterministic fallback.

Weights are tunable constants with tests, not magic numbers spread through the code.

## 5. Indoor classification

Rain swaps fail if an "indoor" recommendation is actually outdoors. Classify in this order:

| Priority | Signal (OSM tags) | Class |
| --- | --- | --- |
| 1 | `indoor=yes/room/corridor`, `covered=yes` | INDOOR |
| 2 | `shop=mall`, `tourism=museum`, `tourism=gallery` | INDOOR |
| 3 | `amenity=restaurant/cafe/fast_food/bar/cinema/library` | INDOOR |
| 4 | `leisure=park/garden/nature_reserve`, `tourism=viewpoint`, `natural=*` | OUTDOOR |
| 5 | `tourism=attraction`, `amenity=place_of_worship` (unless 1–3 apply) | UNKNOWN |
| 6 | anything else | UNKNOWN |

- Explicit tags beat category defaults; category defaults beat "unknown".
- Store `indoor_source`: `OSM_TAG | MANUAL | AI`.
- **The AI may classify UNKNOWN places only offline, as a one-time seed enrichment.** Results
  are stored with `source=AI` and reviewed by hand; they are never generated per request.
- At request time, UNKNOWN is excluded from rain swaps (or penalised heavily). The AI is not
  asked to guess indoor/outdoor as part of the ranking call.
- `opening_hours` parsing is three-state: open / closed / unknown. Parse failure is unknown,
  never "closed" and never "open".

## 6. Candidate filtering (request time)

For the affected stop only:

1. Bounding box around the stop, then Haversine ≤ 3 km.
2. `indoor_class` compatible with the weather problem.
3. Open at the expected arrival time.
4. Same-or-compatible `category_group` (a restaurant is not a replacement for a museum,
   unless the user preference says otherwise).
5. Exclude the current location and anything already in the itinerary.
6. Order by our score; take the top 5–8.

## 7. AI contract

One call per affected stop. Input:

```json
{
  "reason": "rain_expected",
  "at": "2026-11-02T15:00+08:00",
  "activity": { "name": "Gardens by the Bay", "category_group": "nature" },
  "preferences": { "interests": ["nature", "history"], "avoid_crowds": true },
  "context": {
    "next_activity": { "name": "Dinner at Lau Pa Sat", "start": "18:30" },
    "remaining_minutes": 120,
    "budget_remaining": 40
  },
  "candidates": [
    {
      "id": "node/123",
      "name": "National Museum of Singapore",
      "category": "museum",
      "distance_km": 0.8,
      "indoor": "INDOOR",
      "open_at_arrival": true,
      "est_cost": 10
    }
  ]
}
```

Not included: our score, our ranking order, our penalties, raw OSM tag blobs.
Candidates are shuffled so position does not imply preference.

Output (structured output / JSON schema):

```json
{
  "ranking": [
    { "id": "node/123", "rank": 1, "score": 0.92, "reason": "Indoor museum, matches history interest, 10 min away" }
  ],
  "no_suitable_option": false
}
```

Validation:

- every `id` must exist in the offered set; duplicates and unknown ids reject the response
- ranks must be unique; allow `no_suitable_option: true`
- `score` is advisory and never shown to the user
- one retry on invalid output, then fall back to our ranking
- persist `our_score`, `ai_score`, `ai_reason`, `chooser = LLM | FALLBACK`

Prompt rules:

- fixed system prompt in `resources/prompts/choose-alternative.txt`
- temperature 0.2, structured output, no tools, no browsing
- treat place names as untrusted data (prompt injection), send whitelisted fields only
- require the model to prefer preference match + weather suitability + convenience, and to
  say "no suitable option" rather than invent a place

## 8. When the AI runs

Trigger, not schedule:

```
weather poller (NEA 2-hour forecast, ~15 min)
  → active trips with an outdoor stop whose window intersects a rainy forecast
  → cooldown check (per stop + forecast version)
  → build candidates, call the AI asynchronously
  → persist suggestion; frontend polls / shows it
  → on open/accept: re-validate hard facts, then accept/reject
```

- Generate **just in time**, not a day ahead. NEA's 2-hour forecast is only meaningful for
  roughly that window; open/closed, delays, and edits invalidate anything older.
- Do not ask the AI for a timed sequence of stops. Accepting one swap changes travel time,
  budget, and the next stop's feasible window, so later entries would be stale immediately.
- Returning 2–3 ranked alternatives **for the same stop** in one call is fine.
- Cache key: `hash(candidate ids + forecast snapshot + preferences + itinerary slice + prompt version)`.
  Regenerate when the forecast changes, the cache is older than ~60 minutes, or the itinerary changes.
- Budget: ≤ 1 LLM call per affected stop per forecast window.
- Latency: run async; the UI shows a pending suggestion rather than blocking.
- Fallback is always available, so the feature works with no API key configured.

## 9. Module layout

```
weather/     NEA 2-hour client, poller, weather_events
seed/        PlaceSeeder, POI import, indoor classifier, score computation
planner/     CandidateFinder, Scorer, TriggerScan, SuggestionBuilder
ai/          PlanChooser interface, OpenAiPlanChooser, FallbackPlanChooser
suggestion/  Suggestion entity/repository/service/controller
```

Suggested migrations:

- `V3__place_seed_and_scores.sql` — Location columns, `place_scores`
- `V4__weather_events.sql`
- `V5__suggestions.sql`

Tests run against H2 with Flyway disabled (`ddl-auto=create-drop`), so entity changes must
stay in sync with the migrations manually.

## 10. Testing

- Scorer: pure unit tests over fixture candidates (ordering, penalties, unknown hours).
- Indoor classifier: one test per rule-table row, including ambiguous `tourism=attraction`.
- Seed import: idempotent by OSM identity; dedupe; malformed JSON fails fast.
- AI: fake `PlanChooser` returning valid output, unknown ids, duplicates, and errors; assert
  fallback. No live LLM calls in CI.
- Trigger: `FixtureWeatherSource` + fixed clock; assert cooldown and "no outdoor stop → no call".
- End-to-end: fixture rain + "Check now" produces a museum swap, accept updates the activity.

## 11. Open decisions

- Interest tag list and whether preferences are per-user or per-trip.
- Final scoring weights and the similarity table.
- Seed set ownership (who curates the 30–50 places).
- LLM provider/model and key ownership; `spring.ai.openai.api-key` must become env-driven.
- Whether UNKNOWN indoor is excluded or only penalised.
- No-valid-replacement behaviour (silent vs. explicit "nothing suitable").
