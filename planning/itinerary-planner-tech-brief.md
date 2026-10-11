# Itinerary Planner: Technical Approach

Team brief on how the adaptive itinerary feature could work under the hood. Nothing here is final. The open questions at the bottom need a team decision.

**The idea in one line:** we start with a set itinerary. The app watches weather, public transport alerts, the user's location and budget, then suggests changes to the plan or to how the user gets to the next stop.

**In practice:** Mei planned the Botanic Gardens for 2:30pm. At 1:15pm, NEA forecasts thunderstorms there. The app finds indoor alternatives that still get her to her 5:30pm dinner booking, checks they're actually open, and suggests the Asian Civilisations Museum. She taps accept, and her plan updates. (Full walkthrough in [Worked example](#worked-example-meis-rainy-afternoon).)

## High-level architecture

```
 EXTERNAL SOURCES                 OUR BACKEND                                   USER
 ────────────────                 ───────────                                   ────

 NEA weather ──┐
               ├──> [1] Pollers (cron) ──> events table
 LTA alerts ───┘                               │
                                               v
                   trips / stops / legs ──> [2] Trigger engine (rules)
                                               │  "this stop/leg is affected"
                                               v
 OSM (cached) ───────────────────────────> [3] Find candidates + filter
                                               │
 OneMap routing ─────────────────────────> [4] Simulate day + score ──> top 5
                                               │
 Google Places ──────────────────────────> [5] Verify hours + price ──> cut down
                                               │  shuffle, hide scores
                                               v
 LLM API ────────────────────────────────> [6] LLM picks + explains
                                               │  validate, else fall back
                                               v                                Web Push
                                          [7] Suggestion ─────────────────────> accept / reject
                                               │
                                               v
                                   accept: new plan_version + legs
                                   reject: logged
```

**Components**

- **Web app (frontend):** shows the itinerary, sends location while open, receives Web Push suggestions.
- **Backend API:** trips, stops, accepting/rejecting suggestions.
- **Workers:** pollers on a schedule, plus the trigger → replan → LLM pipeline.
- **Postgres + PostGIS:** all app data, places cache, events, suggestions.
- **External APIs:** NEA (data.gov.sg), LTA DataMall, OSM Overpass, OneMap, Google Places, an LLM provider.

**The flow in one line per step**

1. Poll NEA and LTA → normalise → store only new events.
2. Match events against users' upcoming stops and route legs.
3. Find alternative places from the OSM cache → filter out invalid ones.
4. Simulate the day with travel times (OneMap) → score → keep the top 5.
5. Check the unique new places with Google for opening hours and price → drop closed or over-budget ones.
6. Shuffle the survivors, send them without scores to the LLM → it picks one and explains → code validates, else falls back to the top score.
7. Push the suggestion → user accepts (new plan version) or rejects (logged).

## Worked example: Mei's rainy afternoon

Single-stop swap (the MVP case), end to end. **All numbers, hours and prices are made up for illustration.**

### Setup

Mei is visiting Singapore on a Saturday. Budget $120, $25 spent so far. Preferences: likes history, dislikes crowds.

| Time         | Stop                          | Indoor?      | Fixed?        |
| ------------ | ----------------------------- | ------------ | ------------- |
| 10:00        | Buddha Tooth Relic Temple     | Yes          | No            |
| 12:30        | Lunch at Maxwell Food Centre  | Yes          | No            |
| 14:30–16:30 | Singapore Botanic Gardens     | **No** | No            |
| 17:30        | Dinner booking at Clarke Quay | Yes          | **Yes** |

It's 13:15 and she's eating lunch at Maxwell.

### Step 1: Pollers

The NEA poller runs at 13:15. The 2-hour forecast for Tanglin changed from "Cloudy" to "Thundery Showers", so it saves a new event:

```json
{
  "source": "NEA",
  "type": "forecast",
  "area": "Tanglin",
  "condition": "Thundery Showers",
  "valid_from": "13:15",
  "valid_to": "15:15"
}
```

If the forecast had still said "Cloudy", nothing would be saved.

### Step 2: Trigger engine

- Botanic Gardens is outdoor ✓
- Its nearest NEA forecast area is Tanglin ✓
- Its window (14:30–16:30) overlaps the forecast (until 15:15) ✓
- Thundery showers is above the threshold (drizzle wouldn't be) ✓
- No suggestion sent for this stop in the last hour ✓

→ Botanic Gardens is flagged.

### Step 3: Find candidates and filter

Indoor attractions within ~3km of the Maxwell → Clarke Quay route, from the `places` cache:

| Candidate                  | Open at 14:30? (OSM)       | Est. cost | Result           |
| -------------------------- | -------------------------- | --------- | ---------------- |
| National Museum            | Open                       | $15       | Keep             |
| Asian Civilisations Museum | Open                       | $20       | Keep             |
| ArtScience Museum          | Open                       | $35       | Keep             |
| ION Orchard (mall)         | Open                       | $0        | Keep (low value) |
| Small heritage gallery     | **Unknown**          | $5        | Keep, penalised  |
| Some museum                | **Closed** Saturdays | —        | Dropped          |

Budget check: $95 left, so all survivors fit.

### Step 4: Simulate and score

Leave Maxwell 13:45 → travel → stay 2 hours → travel to Clarke Quay → must arrive by 17:30.

| Candidate           | Arrive | Leave | At dinner | Makes booking? |
| ------------------- | ------ | ----- | --------- | -------------- |
| National Museum     | 14:05  | 16:05 | 16:25     | ✓             |
| Asian Civilisations | 14:00  | 16:00 | 16:10     | ✓             |
| ArtScience          | 14:10  | 16:10 | 16:35     | ✓             |
| ION Orchard         | 14:15  | 16:15 | 16:45     | ✓             |
| Heritage gallery    | 14:20  | 16:20 | 16:40     | ✓             |

Replacement score = similarity to a garden + match with "likes history" + popularity − travel time − cost − unknown-hours penalty:

| Candidate           | Score |
| ------------------- | ----- |
| Asian Civilisations | 8.1   |
| National Museum     | 7.9   |
| ArtScience          | 6.8   |
| Heritage gallery    | 5.2   |
| ION Orchard         | 3.0   |

Keep the top 3. The gallery lost points for unknown hours, the mall for low similarity to a garden and no match with her interests.

### Step 5: Verify with Google

Three unique places → three Place Details calls (Google place IDs already stored, so no matching search).

| Candidate           | Google says                                  | Result     |
| ------------------- | -------------------------------------------- | ---------- |
| Asian Civilisations | Open until 19:00, price level moderate       | ✓         |
| National Museum     | Open until 19:00, price level moderate       | ✓         |
| ArtScience          | **Closes 15:30 today** (special event) | ✗ Dropped |

This is exactly the case OSM would have missed.

### Step 6: LLM

Survivors shuffled, scores hidden:

```
Reason: thundery showers expected at Botanic Gardens until 15:15.
Preferences: likes history, dislikes crowds.

plan_A: National Museum 14:05–16:05, ~$15, MRT, then dinner at 17:30
plan_B: Asian Civilisations Museum 14:00–16:00, ~$20, MRT, then dinner at 17:30
```

Reply:

```json
{
  "choice": "plan_B",
  "explanation": "Thunderstorms are expected at the Botanic Gardens this afternoon, so we've swapped it for the Asian Civilisations Museum. It's indoors, great for history, and a short walk to your dinner at Clarke Quay."
}
```

Code checks `plan_B` is a real ID and the JSON is valid ✓. It logs that the AI's pick and the top-score pick agree.

### Step 7: Suggestion

13:20: Mei gets the push notification and taps accept.

- Plan version 2 saved; version 1 kept for undo.
- Route legs recalculated (Maxwell → museum, museum → Clarke Quay).
- Suggestion logged as accepted.

Total: a few seconds, three Google calls, one LLM call.

### Variations

- **Mei is running late** (still at Maxwell at 14:20). The lateness rule in the trigger engine catches this, but only if the app knows her location. As a web app, that means the app is open or she tapped "I'm heading out". Otherwise we assume she's on schedule. If caught, step 4 re-simulates from 14:20 and some candidates may no longer make the 17:30 booking.
- **Rain at Clarke Quay at 17:30.** The dinner is indoor and fixed, so the stop itself is fine, but the walk from the MRT isn't. Checking route legs for rain (suggest a sheltered route or taxi) is out of scope for the MVP. See open questions.

## Plain-English summary

- **Helpers** keep checking the weather and train alerts and write everything into a notebook (pollers → `events` table).
- **A rule-follower** reads the notebook and asks "does this affect anyone's plan?", ignoring drizzle and repeat alerts (trigger engine).
- **A planner** finds backup places, throws out anything impossible, tries the possible orders, and scores what's left, preferring small changes (replanner).
- **A fact-checker** asks Google whether the top few places are actually open and affordable.
- **The AI** picks one of the safe plans and explains it in normal words. It can't invent plans.
- **The user** taps yes or no. Nothing changes behind their back.
- **Catch:** as a website, the app only knows your location while it's open on screen.
- **Start small:** rain → swap an outdoor stop for a nearby indoor one, end to end.

## Core principle: code decides, AI explains

Don't hand the LLM raw API data and ask it to "replan". It will suggest closed places, impossible timings and routes that don't exist.

Instead:

1. Code detects that something changed.
2. Code generates valid alternative plans and scores them.
3. The LLM picks the best of the top few and writes the message to the user.
4. Code checks that the LLM's pick is one of the options we gave it.

## Data sources

| Source                       | What we use it for                                                      | How often                                          |
| ---------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------- |
| NEA via data.gov.sg          | 2-hour forecast (by area), rainfall readings                            | Forecast ~every 30 min, rainfall every few min     |
| LTA DataMall                 | Train service alerts; bus arrivals                                      | Alerts every 1–2 min; bus arrivals on demand only |
| OpenStreetMap (Overpass API) | Places: location, category, indoor/outdoor tags, opening hours (patchy) | Sync occasionally, cache in our DB                 |
| OneMap (SLA) routing API     | Public transport routes, travel times, fares                            | On demand, cached                                  |
| Google Places API            | Confirm opening hours and price level for shortlisted places only       | On demand, ~5 places per replan                    |

OSM gives us places but does not plan public transport routes. That's what OneMap is for.

Check current endpoints, rate limits and pricing before building. data.gov.sg has changed API versions before.

## Pipeline in detail

### 1. Pollers

Background jobs fetch each source on a schedule. They normalise the data into one `events` shape (what, where, when) and store it. Keep the raw response too, for replay testing.

Don't save a new event if nothing changed since the last fetch. The forecast barely changes between 30-minute polls, and duplicate events make step 2 re-flag the same rain.

### 2. Trigger engine

Plain if-then rules, no AI. The rules don't check the data on its own. They check it **against users' plans**. "Rain in Jurong at 3pm" only matters if someone has an outdoor stop near Jurong around 3pm.

Examples:

- Outdoor stop + showers forecast in the nearest NEA area during its time window → flag it.
- Train alert on a line used by one of the trip's upcoming route legs → flag it.
- User's ETA to the next stop leaves less slack than a set threshold → flag it.

It needs two safeguards:

- **Thresholds**, so drizzle doesn't trigger a replan.
- **Cooldowns and de-duplication**, so the same storm doesn't send five notifications.

### 3. Find candidates and filter

We start with **single-stop swaps**. The goal is **re-sequencing the rest of the day**.

Find alternatives from our cached `places` table, then drop the ones that can't work. This is **not** a pile of if-else. There are only a few real constraints, and each one is a filter over data:

| Check                | How                                                                                                                     |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Right kind of place  | category +`is_indoor`, one SQL `WHERE`                                                                              |
| Close enough         | PostGIS distance query                                                                                                  |
| Open at arrival time | an opening-hours library (e.g.`opening_hours.js`) answers "open at 15:20 Sunday?". Don't parse OSM strings ourselves. |
| Within budget        | compare`est_cost` with remaining budget                                                                               |
| Fits the schedule    | simulate arrival times with the travel matrix (step 4)                                                                  |

In code: a list of check functions, run over every candidate; a candidate survives only if all pass. A new rule = one new function.

A swap also changes the travel legs into and out of the new place, so those routes must be recalculated.

If no valid replacement exists, decide what happens: keep the plan and warn the user, or stay silent.

#### Opening hours: the weakest data

OSM opening hours are often missing or out of date, because volunteers add them. Handle the uncertainty honestly:

- **Three answers, not two:** open, closed, or **unknown**. Never treat missing as open.
- **Unknown = score penalty, not elimination.** Only drop places marked closed. Confirmed hours beat unknown, all else equal.
- **Manually check the top ~50 places** users actually visit (big attractions, malls, museums) and store the hours in our `places` table.
- **"This place was closed" button**, so we mark it and don't send the next user there.
- **Public holidays:** OSM has `PH` syntax; give the library Singapore's holiday calendar. Easy to miss in testing.
- **Google check in step 5** resolves most unknowns for the places that matter.

#### Why not let an LLM agent (tools/MCP) find places and we verify?

We considered "LLM gathers data with tools, then we verify". It doesn't save work: the verify step needs exactly the same checks (open? within budget? reachable in time?). We'd write the same code and add an LLM in front of it, making things:

- **Slower:** several LLM round trips, seconds to tens of seconds per replan.
- **Costlier:** many more tokens per replan.
- **Harder to debug:** different tool calls each run.
- **Prone to retry loops:** what do we do when the LLM's plan keeps failing verification?

"We might miss cases" is true either way. With code, a missed case shows up in logs and gets fixed once. The LLM misses cases silently.

MCP is a protocol for plugging tools into LLM apps. Inside our own backend we don't need it; plain tool/function calling does the same job.

**Where an LLM does help:** open-ended requests like "somewhere chill with good local food". The LLM turns that into search terms or categories (`amenity=cafe`, `cuisine=local`), and our code searches and filters.

### 4. Simulate and score

#### MVP: scoring replacements (single-stop swap)

In a single swap, the other stops never change, so we **don't need user-rated stop value yet**. Stop value only matters when deciding which stops to drop or reorder (re-sequencing, below).

We do need to rank the **replacements**. Otherwise a mall and a museum look equally good, since both are indoor, open and nearby. This is calculated automatically, in code:

1. **Simulate each candidate:** travel from the previous stop, stay, travel to the next stop. Drop it if it misses a fixed booking or the next stop's window.
2. **Score it:**

```
replacement score = w1 × similarity + w2 × preference match + w3 × popularity
                    − travel time − cost − unknown-hours penalty
```

3. Keep the **top 5** for the Google check (step 5).

The weights (`w1`, `w2`, `w3`) are tuned by running test scenarios like Mei's and checking the ranking looks sensible. Open question: should similarity (`w1`) or preference match (`w2`) count more? E.g. Mei likes history and is losing a garden. Does a history museum (low similarity, preference boost) beat a nature-themed indoor exhibit (high similarity, no boost)?

#### Where each part of the score comes from

**Similarity, from OSM tags.** OSM tags are written by volunteers but aren't free text. They use an agreed list of values (`tourism=museum`, `leisure=park`, `shop=mall`), so reading them is exact matching, **no embeddings or AI needed**. We map tags to a few groups once, when syncing places, and store the group in `places`:

```python
TAG_TO_GROUP = {
    ("leisure", "park"): "Nature",
    ("leisure", "garden"): "Nature",
    ("leisure", "nature_reserve"): "Nature",
    ("tourism", "museum"): "History/culture",
    ("historic", "*"): "History/culture",
    ("tourism", "gallery"): "Art",
    ("shop", "mall"): "Shopping",
    ("amenity", "food_court"): "Food",
    ("tourism", "zoo"): "Entertainment",
}
```

Then a small hand-made similarity table between groups:

|                           | Nature | History/culture | Art | Shopping | Entertainment |
| ------------------------- | ------ | --------------- | --- | -------- | ------------- |
| **Nature**          | 1.0    | 0.4             | 0.4 | 0.1      | 0.3           |
| **History/culture** | 0.4    | 1.0             | 0.7 | 0.1      | 0.3           |
| **Art**             | 0.4    | 0.7             | 1.0 | 0.2      | 0.3           |
| **Shopping**        | 0.1    | 0.1             | 0.2 | 1.0      | 0.4           |
| **Entertainment**   | 0.3    | 0.3             | 0.3 | 0.4      | 1.0           |

(Numbers are a starting point for the team to adjust.)

Tag pitfalls:

- **`tourism=attraction` is a catch-all** (Gardens by the Bay and a statue both use it). Can't tell category or indoor/outdoor from it.
- **Multiple tags:** a historic building that's also a museum. Pick a rule (first match wins, or allow multiple groups).
- **OSM has no indoor/outdoor tag.** Infer it (museum/mall → indoor, park → outdoor, attraction → unclear).
- **Missing or wrong tags** → group "Unknown".
- **Fix:** automatic mapping for the bulk, plus a manual check of the top ~50–100 places users actually visit.

**Preference match, from fixed interest tags.** Users pick from a fixed list (history, nature, food, art, shopping, nightlife). Matching is code: the place's group matches a user interest → boost. If users type free text ("love old buildings, hate crowds"), the LLM converts it **once, when they save preferences**, into those tags plus flags like `avoid_crowds: true`. Not on every replan.

**Popularity, from manual flags and Google.** Tags can't tell quality: a one-room gallery and the National Gallery both have `tourism=gallery`. Google ratings only arrive in step 5, after shortlisting, so a great place could be cut before Google sees it. Fix: an `is_popular` flag on the manually checked top places, so they rank properly from the start. Google rating is then used when re-scoring in step 5.

**Anything tags can't capture** ("she's been walking all day", "hates crowds") is left to the LLM in step 6, where it picks between close options.

#### Alternatives we considered

| Alternative                                                           | Verdict                                                                                                                                                                                      |
| --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **User-rated stop value** ("must go / nice to have / optional") | **Deferred** to re-sequencing. Not needed for single swaps. When we need it: default from category/popularity, let users mark "must go", three levels mapped to numbers (e.g. 10/6/3). |
| **Inferred stop value only** (category, popularity)             | Fine as the default, but can't know what this user cares about.                                                                                                                              |
| **All stops equal**                                             | Easiest, but loses the "what to drop" decision entirely.                                                                                                                                     |
| **Embeddings** (vectors of place descriptions vs preferences)   | **Later, maybe.** More flexible for fuzzy matching, but needs good descriptions (OSM mostly lacks them) and is harder to debug. Tags are already clean labels.                         |
| **LLM ranks all candidates every replan**                       | **No.** Slower, costlier, less predictable. Code ranks broadly; LLM only picks between the final few.                                                                                  |
| **Google for everything**                                       | **No.** Cost and terms of use. Google only for the final check.                                                                                                                        |

#### Later: re-sequencing the rest of the day

Re-sequencing is a known problem: the orienteering problem with time windows. Choose which stops to keep, which to replace, and in what order, to get the most value within time, budget and opening hours. This is where **stop value** is needed.

Most of the pipeline stays the same (pollers, triggers, Google check, LLM, accept/reject). The extra difficulty is in steps 3–4 and the user experience:

1. **Combinations explode.** Orders alone: 4! = 24, 5! = 120, 6! = 720. Fine. But with 3 alternatives per stop for 5 stops: 4^5 × 5! ≈ 123,000 plans. Limits needed:
   - Only offer alternatives for flagged stops, not every stop.
   - Cap alternatives at 2–3 per slot.
   - Never move fixed stops.
   - Use straight-line estimates to cut most plans before calling OneMap; cache aggressively.
2. **Knock-on effects.** Change one stop and every arrival after it shifts. Re-simulate the whole rest of the day, not just neighbouring stops.
3. **User experience is the hardest part.** "Swap the park for the museum" is easy to accept. "Here's a new afternoon" isn't. Needs a clear before/after view, and the **stability penalty** matters much more: value − travel − cost − penalty for every change from the current plan. Without it, tiny forecast changes reshuffle the whole afternoon and users stop trusting the app.

If we support 10+ stops, switch to Google OR-Tools (routing with time windows). Don't start there.

**Should the LLM do multi-stop planning instead? No.** Re-sequencing is a puzzle with interlocking hard rules, which is exactly where LLMs are weakest. We'd still need the same simulation code to verify its plans, and multi-stop plans fail verification more often, causing retry loops.

**Middle ground worth trying:** let the LLM propose **one extra candidate** (e.g. "she said she's tired, drop the long walk"). It goes through the same simulation and checks as every other candidate. Passes → joins the shortlist. Fails → discarded, nothing breaks.

### 5. Verify with Google Places

OSM is used for discovery (free, cacheable). Google is used only for a final check on the shortlist (live, accurate).

1. **Collect the unique new places** across the top 5 plans. Plans often share places, so call **per place, not per plan**. For a single swap it's just the 3–5 replacement candidates.
2. **Match OSM → Google.** Google doesn't know OSM IDs. Do a text search with name + coordinates and accept the result only if it's within ~100m of the OSM location, since same-name places nearby cause wrong matches. **Store the Google place ID** in `places` (place IDs can be kept long term), so later lookups skip the search.
3. **Get Place Details:** opening hours and price level.
4. **Cut down:**
   - Closed at arrival time → drop.
   - Price level clearly over budget → drop.
   - Re-score the survivors with the better data.
   - If everything is dropped, go back for the next batch of candidates instead of giving up.

Notes:

- **Price is a level** (inexpensive → very expensive), not a dollar amount. Map it roughly to dollars for the budget check. Fine for filtering, but don't show it to users as exact.
- Google doesn't always have price or hours either, so the "unknown" handling still applies, just less often.
- **Cost:** since March 2025, Google uses per-SKU free monthly thresholds instead of the old $200 credit: 10,000 calls for Essentials, 5,000 for Pro, 1,000 for Enterprise. Hours and price are in the pricier tiers (confirm on Google's pricing page). At ~5 calls per replan, that's a couple of hundred free replans a month, enough for the project and demos.
- **No hard spending cap by default.** Budget alerts only notify. **Set per-API usage quotas on day one.**
- **Terms of use:** Google restricts long-term storage of its data (place IDs are the main exception) and has display/attribution rules, including restrictions on non-Google maps. Don't cache Google hours/prices in our tables. Read the terms before building.

### 6. LLM

- Input: the surviving plans (shuffled, without scores), the reason for the change, and the user's preferences.
- Output: chosen plan ID + a short, plain explanation ("Heavy rain expected at 3pm, so we swapped Botanic Gardens for the National Museum").
- Also useful for turning messy user input ("hate crowds, keep it under $50") into structured preferences.

#### Why the AI only picks, never plans

The API data (NEA, LTA, OneMap, Google) is reliable. The AI's output is not. An LLM is good with words but bad at being exactly right. Left to plan on its own, it might suggest a place that's closed, get travel-time maths wrong, go over budget, or invent a place or bus route. It sounds equally confident when it's wrong, so we can't tell.

So code does the strict checking and only hands the AI options that already passed. Worst case, the AI picks the second-best valid plan, never an impossible one.

#### Still validate the AI's pick

"Pick from this list" is an instruction, not a guarantee. The AI might tweak a plan, merge two, or name something not on the list. Even at temperature 0 it can still be wrong. So:

- Give each option an ID (`plan_A`, `plan_B`, ...).
- Require a fixed JSON reply: `{"choice": "plan_B", "explanation": "..."}`.
- In code, check `choice` is one of our IDs. If it isn't, or the JSON is broken, or the call times out, **fall back to the top-scored plan**.

The plan the user sees always comes from our code. The AI only contributes the choice and the wording.

#### Model settings

- **Temperature: low (0–0.3).** Same situation should give the same pick, so bugs are reproducible and refreshing doesn't change suggestions. Some newer/reasoning models ignore or don't allow temperature, so check the provider docs.
- **Structured outputs.** Use the provider's JSON schema / structured output / tool-calling feature to force the reply shape. Make `choice` an enum of the plan IDs so the API itself rejects anything else. Keep the code-side check as a backup.
- **Keep the system prompt fixed.** Put the plans and context in the user message, not the system prompt.
- **Turn off thinking/reasoning mode** if the model has one. It adds hidden tokens we pay for and slows replies; this task doesn't need it.

#### Which model

A small, cheap model is enough. Picking between a few already-valid plans and writing two sentences is easy work.

- **Suggested: Gemini Flash-Lite or Flash**, on Google AI Studio's free tier while building. Caveat: on the free tier Google may use inputs/outputs to improve its models and humans may review them, so **move to the paid tier once real user data goes through it**.
- **Equally fine:** Claude Haiku or OpenAI's mini-tier models. All support structured outputs. Pick whichever has free credits or student deals for the team.
- **Cost is negligible:** ~1,000 tokens in, ~100 out per call. At budget-tier prices (~$0.30/M in, ~$2.50/M out) that's ~$0.00055 per call, roughly 55 cents per 1,000 replans. Google Places will cost more than the LLM.
- **Hide the provider behind one function** (`choose_plan(options, prefs)`) so switching means changing one file.
- **Choose by testing:** run 15–20 scenarios like Mei's through two cheap models and keep the one that picks more sensibly.

Prices change often, so check each provider's pricing page before committing.

Draft system prompt:

```
You help users of a Singapore itinerary app decide between alternative plans for the rest of their day.

You will receive:
- The user's preferences
- The reason a change is needed (e.g. rain forecast, train disruption)
- 2–5 candidate plans, each with an ID, stops, timings, travel and cost

Rules:
- Choose exactly one plan by its ID. Never invent or modify a plan.
- Prefer plans that fit the user's preferences and change the original day the least, unless another plan is clearly better.
- Write a 1–2 sentence explanation in plain, friendly English that says what changed and why.
- Do not mention scores, IDs or internal details in the explanation.

Respond only in the required JSON format.
```

#### Scores: used by code, hidden from the AI

**Decision: don't show the AI the scores.** If it sees them, it will mostly pick the top score and we gain nothing from it. Hidden, it can weigh fuzzy preferences ("hates crowds") more freely.

We still need scores, for three reasons:

1. **Shortlisting.** Brute force can produce hundreds of valid plans. Sending them all to the AI is slow, costly, and makes its choices worse. The score cuts them to the top few.
2. **Fallback.** When the AI fails, the top-scored plan is the answer.
3. **Evaluating the AI.** Log both the score's top pick and the AI's pick, and compare which one users accept more. If the score's pick wins, we may not need the AI to choose at all.

**Shuffle the shortlist before sending it.** LLMs tend to favour the first option listed. If we always sort by score, we're leaking the ranking anyway.

### 7. User confirmation

Always suggest, never silently rewrite. Forecasts are wrong often enough that silent changes will annoy people.

- **Accept:** save as a new `plan_version` (old one kept for undo) with the recalculated route legs.
- **Reject:** log it. Acceptance and rejection rates tell us whether the system is any good.

## Location: important constraint for a web app

**Browsers cannot track location in the background.** `navigator.geolocation.watchPosition` only works while the tab is open and, on phones, on screen. Browsers have no geofencing. Making it a PWA doesn't change this.

Options:

- **A. Accept it (recommended for now).** Update location when the app is open. Otherwise assume position from the planned schedule. Add an "I'm heading out" button. Weather and train triggers still work because they run on our server. Notify via Web Push. On iPhones, Web Push only works if the user adds the PWA to their home screen (iOS 16.4+).
- **B. Wrap with Capacitor later** to get native background location and geofencing without rewriting the app.

Privacy (PDPA): get clear consent, store only the latest location rather than full trails, and don't keep it longer than needed.

## Database (Postgres + PostGIS)

| Table            | Purpose                   | Key fields                                                                                                                                                                                |
| ---------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `users`        | Account + preferences     | `preferences` (jsonb: interest tags + flags like `avoid_crowds`), `last_location`, `last_location_at`                                                                             |
| `trips`        | One planned day           | `user_id`, `trip_date`, `day_start`, `day_end`, `budget`, `plan_version`                                                                                                      |
| `stops`        | Stops in a trip           | `trip_id`, `place_id`, `seq`, `plan_version`, `planned_arrive`, `planned_depart`, `is_fixed`, `status`                                                                    |
| `places`       | Cache of OSM places       | `osm_id`, `name`, `osm_tags` (jsonb), `category_group`, `location`, `is_indoor`, `is_popular`, `opening_hours`, `est_cost`, `google_place_id`, `reported_closed_at` |
| `route_legs`   | How to get between stops  | `trip_id`, `from_stop`, `to_stop`, `mode`, `duration_min`, `fare`, `lines_used` (jsonb)                                                                                     |
| `events`       | Normalised NEA/LTA feed   | `source`, `type`, `area`, `affected_line`, `valid_from`, `valid_to`, `raw` (jsonb)                                                                                          |
| `suggestions`  | Proposed changes          | `trip_id`, `event_id`, `proposed_plan` (jsonb), `explanation`, `status`, `ai_choice`, `top_score_choice`                                                                    |
| `travel_cache` | Cached travel times/fares | `from_place`, `to_place`, `mode`, `hour_bucket`, `duration_min`, `fare`                                                                                                       |

Design decisions:

- **`places` is a cache.** Pull from Overpass once, add fields OSM doesn't give cleanly (`is_indoor`, `est_cost`), re-sync occasionally. Store `google_place_id` once matched, but not Google's hours or prices (terms of use).
- **`plan_version`.** Accepting a suggestion writes version N+1 instead of overwriting. That gives us history and undo for free.
- **`events.raw`** lets us replay a real rainstorm or MRT breakdown in demos and tests.
- **`route_legs.lines_used`** is how we check "does this train alert affect anyone?"
- **`suggestions`** stores both the AI's pick and the top-score pick, so we can measure whether the AI actually helps.
- **`travel_cache`** can move to Redis later if needed.

## Suggested MVP

Build one trigger end to end: **rain → swap an outdoor stop for a nearby indoor one.**

1. Data model for itineraries (with `is_indoor`, `is_fixed`, time windows).
2. NEA poller → `events`.
3. Rule: outdoor stop + rain in its window.
4. Find and filter indoor alternatives nearby from the `places` cache.
5. Verify the top few with Google Places.
6. LLM picks + explains; code validates.
7. User accepts → new `plan_version`.

Record real API responses so we can replay rain on demand instead of waiting for the weather.

Then add: train alerts → reroute legs, then full re-sequencing.

## Decisions so far

- Start with single-stop swaps; aim for full re-sequencing.
- It's a web app, so no background location for now.
- Constraint checks are plain code (filter functions), not an LLM agent.
- OSM for discovery; Google Places only to verify the shortlist (per unique place).
- Opening hours are three-state (open / closed / unknown); unknown is penalised, not dropped.
- The AI picks from code-validated options only; code validates its pick and falls back to the top score.
- Scores are hidden from the AI; the shortlist is shuffled before sending.
- Low temperature (0–0.3) + structured outputs; thinking mode off; a small, cheap model behind one function.
- MVP ranks replacements with an automatic score (tag similarity + preference match + popularity − travel − cost − unknown-hours penalty). User-rated stop value is deferred to re-sequencing.
- OSM tags are mapped to groups with a lookup table in code. No embeddings for now.
- Free-text preferences are converted to tags by the LLM once, at save time, not per replan.
- Multi-stop planning stays in code; the LLM may add one extra candidate that goes through the same checks.

## Open questions for the team

**Lead decisions for the meeting** (they change how things get built):

1. **Location:** go with option A (foreground only + "I'm heading out") or plan for Capacitor?
2. **LLM provider and model:** suggested Gemini Flash/Flash-Lite (free tier for dev), or Claude Haiku / OpenAI mini. Who sets up the account?
3. **Replacement scoring:** agree the groups, the similarity table, the interest tag list, and whether similarity or preference match counts more.

**Quick admin item:**

4. **Google costs:** who owns the billing account, and what usage quota do we set?

**Assign an owner, decide while building:**

5. **Event de-duplication** (poller owner): e.g. only save a forecast if the condition changed from the last stored one for that area.
6. **Budget:** how do we map Google's price levels (and missing prices) to dollar estimates?
7. **No valid replacement:** keep the plan and warn the user, or stay silent?
8. **Weather on route legs:** flag rain on walking legs (e.g. MRT → restaurant) and suggest a sheltered route or taxi? Proposed: not in the MVP.

**Decide later:**

9. **Notifications:** how many suggestions per day before it gets annoying?
10. **Stop value** (needed for re-sequencing): user-rated, inferred, or a mix? Proposed: default from category/popularity + user can mark "must go" (three levels).
