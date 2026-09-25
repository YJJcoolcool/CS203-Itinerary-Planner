# Frontend Pages — Flexible Plan

**Status:** Draft outline for the CS203 Itinerary Planner. **Not final design.**
**Owner:** _TBD (Frontend lead)_ · **Last updated:** 2026-09-25

This document is a starting map, not a spec. It lists the pages we expect to build, what
each one needs from the backend, and how the work can be split. Layout, naming, visual
detail, and even which pages get merged are all open until a real review happens. Treat
the tables as a shared vocabulary, not a contract.

**How to use this doc**

- Teams may change layout, spacing, copy, and component internals freely.
- Keep the *routes* and *data contracts* roughly aligned so parallel work merges cleanly.
- When you disagree with something here, change the doc in the same PR as the code.
- Anything marked **OPEN** is a decision the team still needs to make (see the last section).
- Indicative ASCII wireframe sketches for every page are in **§13 Wireframe sketches**.

**Related references**

- Reference design: `planning/Collaborative Trip Itinerary/src/App.tsx` (single-screen dark workspace).
- Design tokens: `planning/Collaborative Trip Itinerary/src/index.css`.
- Current frontend: `frontend/` (React 19 + Vite 8 + TS + Tailwind 4; landing page only).
- Backend: Spring Boot REST API under `backend/`.

---

## 1. Product snapshot

A collaborative trip itinerary planner. People sign in, create a trip, invite others, and
build a day-by-day itinerary of activities tied to real locations. The reference design
presents this as one dense dark workspace: top bar, collapsible traveler/stats sidebar,
a vertical column of collapsible day boxes, an add-activity modal, and a right-hand
AI chat drawer.

The current app has **no router, no auth wiring, and no API client** — only a static
landing page. All of the structure below still has to be created.

---

## 2. Aesthetic direction (carried over from the reference)

The reference is a dark, editorial travel journal: deep navy, warm amber accent,
serif display headings, monospace for anything numeric, generous rounded cards.
We should honour this direction unless the team deliberately pivots. Keep it as
**design tokens**, not hard-coded hex values scattered through components.

### 2.1 Color tokens

| Token (suggested name) | Value | Usage |
| --- | --- | --- |
| `--color-navy` | `#0d1b2a` | App background / AI panel |
| `--color-navy-deep` | `#0b1825` | Left sidebar |
| `--color-navy-mid` | `#162032` | Cards, modal, AI bubbles |
| `--color-navy-light` | `#1e2f45` | Controls, floating buttons |
| `--color-amber` | `#d4882a` | Primary accent, CTAs, times, stats |
| `--color-amber-light` | `#e8a84c` | Hover / highlight |
| `--color-terracotta` | `#c15a3f` | Food type, destructive tint |
| `--color-sage` | `#5c8a6e` | Costs, online status, success |
| `--color-purple` | `#a07bc4` | Transport type |
| `--color-blue` | `#7bb8d4` | Flight type |
| `--color-cream` | `#f5efe6` | Primary text |
| `--color-cream-dim` | `#d6ccbf` | Secondary text |

Notes:

- The reference defines these in `index.css` via Tailwind v4 `@theme` (names `--color-navy`,
  `--color-navy-mid`, `--color-navy-light`, `--color-amber`, `--color-amber-light`,
  `--color-terracotta`, `--color-cream`, `--color-cream-dim`, `--color-sage`).
- The current `frontend/src/index.css` only contains `@import "tailwindcss";`, so these
  tokens (and the activity type colors) need to be added during M1.
- Activity type colors are a small palette: flight `#7bb8d4`, hotel `#d4882a`,
  food `#c15a3f`, activity `#5c8a6e`, transport `#a07bc4`.

### 2.2 Typography

| Role | Font | Used for |
| --- | --- | --- |
| Display | **Fraunces** (serif) | Trip name, day city, section headings |
| Body | **Outfit** (sans) | Everything else |
| Mono | **JetBrains Mono** | Times, costs, stats, type badges, date pills |

Load via Google Fonts `@import` in `frontend/src/index.css`, then expose as
`--font-display`, `--font-body`, `--font-mono` so components use utility classes.

### 2.3 Shape & surface

- Corner radii: cards/panels ~`rounded-2xl`, controls ~`rounded-xl`, pills/badges `rounded-full`.
- Borders are low-opacity white (`rgba(255,255,255,0.06–0.1)`), not solid gray.
- Cards sit on translucent white (`rgba(255,255,255,0.02–0.03)`), never flat opaque fills.
- **OPEN:** whether to keep the "hidden scrollbars" look from the reference, or make
  scrollbars visible for accessibility.

---

## 3. Information architecture / navigation tree

```
Public
├─ /                      Landing (marketing; redirects to /trips if signed in)
├─ /sign-in/*             Clerk sign-in
├─ /sign-up/*             Clerk sign-up
└─ *                      404 / error page

Authenticated (wrapped in AppShell)
├─ /onboarding            Display-name step (first login only)
├─ /trips                 Trip Dashboard / My Trips  ← default landing after auth
│   └─ (create trip)      Modal or inline form on the dashboard
├─ /trips/:tripId         Trip Detail / Itinerary workspace (the reference screen)
│   ├─ itinerary tab      Default tab — days + activities
│   ├─ settings tab       Trip settings + members  (route /trips/:tripId/settings)
│   └─ (add/edit activity) Modal, with an optional deep-link route
├─ /trips/join/:token     Invite / join flow  (later; depends on backend)
└─ /profile               User Profile / Account
```

Routing notes:

- Use **React Router 7** (`react-router`) as already declared.
- Clerk provides its own `<SignIn/>`, `<SignUp/>` components and `/sign-in/*`, `/sign-up/*`
  catch-all routes.
- Auth guard: a `<RequireAuth>` wrapper that redirects unauthenticated users to `/sign-in`.
- `GET /api/trips` returns **all** trips, not the user's. Until a user-scoped endpoint exists,
  the dashboard must filter client-side (see Gaps).

---

## 4. Page inventory (at a glance)

| # | Page | Suggested route | Auth | Priority | Primary user |
| --- | --- | --- | --- | --- | --- |
| 1 | Landing | `/` | Public | MVP | Visitor |
| 2 | Sign-in / Sign-up | `/sign-in/*`, `/sign-up/*` | Public | MVP | Returning / new user |
| 3 | Onboarding (display name) | `/onboarding` | Auth | MVP | First-time user |
| 4 | Trip Dashboard / My Trips | `/trips` | Auth | MVP | Organizer |
| 5 | Trip Detail / Itinerary | `/trips/:tripId` | Auth (member) | MVP | Organizer + traveler |
| 6 | Add / Edit Activity | modal on `/trips/:tripId` | Auth (member) | MVP | Organizer + traveler |
| 7 | Trip Settings / Members | `/trips/:tripId/settings` | Auth (owner) | MVP (basic) | Organizer |
| 8 | User Profile / Account | `/profile` | Auth | MVP (basic) | Any signed-in user |
| 9 | 404 / Error | `*` | Public | MVP | Anyone |
| 10 | Invite / Join trip | `/trips/join/:token` | Auth | Later | Invited traveler |
| 11 | Trip AI Assistant | drawer on `/trips/:tripId` | Auth (member) | Later (UI shell in MVP) | Organizer |
| 12 | Location picker / map explorer | modal / panel | Auth | Later | Organizer |
| 13 | Locations admin (CRUD) | `/locations` | Auth | Later | Power user / dev |

MVP = needed for a usable end-to-end demo. Later = valuable but not on the critical path.

---

## 5. Page details

Each page lists: purpose, key UI regions, backend data, and states to handle.
States to cover generally: **loading, empty, error, not-found, unauthorized.**

### 5.1 Landing — `/` (MVP, public)

- **Purpose:** Explain the product and route people into Clerk auth. The current
  `frontend/src/App.tsx` is this page; it needs to be restyled to match the new
  dark aesthetic (or the team decides landing stays light — **OPEN**).
- **Regions:** hero (name + tagline), short value props, primary CTA ("Get started"),
  secondary CTA ("Sign in"), optional screenshot of the workspace.
- **Data:** none.
- **States:** if the Clerk session is already active, redirect to `/trips`.
- **Notes:** wire the existing "Sign In" / "Create Account" buttons to Clerk instead of
  being dead buttons.

### 5.2 Sign-in / Sign-up — `/sign-in/*`, `/sign-up/*` (MVP)

- **Purpose:** Authentication via **Clerk** (`@clerk/react` 6).
- **Regions:** Clerk-hosted component, styled to blend with the dark theme; link to switch
  between sign-in and sign-up.
- **Data:** Clerk session. `VITE_CLERK_PUBLISHABLE_KEY` from env (`frontend/.env.example`).
- **States:** loading (Clerk initialising), error (invalid credentials), already signed in →
  redirect.
- **Notes:** No custom password UI — Clerk owns it. Our job is layout + redirects.

### 5.3 Onboarding / display name — `/onboarding` (MVP)

- **Purpose:** The backend `User` is created against the Clerk subject and needs a
  `displayName`. On first login there is no user row yet.
- **Flow:** after Clerk sign-in, call `POST /api/users/me` with `{ displayName }`.
  If the user already exists (200, not 201), skip straight to `/trips`.
- **Regions:** centered card, name input, avatar preview (initials), "Continue" button.
- **Endpoints:** `POST /api/users/me` `{displayName}`, `GET /api/users/me`.
- **States:** loading, validation error (blank/long name), server error, already-onboarded
  (redirect).
- **Notes:** The reference derives avatars from initials + a color; we can compute an avatar
  color from the user id so collaborators get stable colors.

### 5.4 Trip Dashboard / My Trips — `/trips` (MVP)

- **Purpose:** List the user's trips and create a new one.
- **Regions:** `AppShell` top bar; greeting; grid/list of `TripCard`s (name, date range,
  day count, member avatars, activity count); primary "New trip" button; empty state;
  account menu (profile, sign out).
- **Endpoints / data:**
  - `GET /api/trips` (returns **all** trips — filter to current user client-side until a
    scoped endpoint exists).
  - `GET /api/users/me` (to know our numeric id for filtering/membership).
  - `POST /api/trips` `{name, startDate, endDate, ownerId}` (ownerId = current user id).
- **States:**
  - loading: skeleton cards.
  - empty: friendly illustration + "Create your first trip".
  - error: retry banner.
  - unauthorized: redirect to sign-in.
- **Notes:** Create-trip can be a modal with name + start/end date pickers. Validate
  end >= start before calling the API (backend also validates).

### 5.5 Trip Detail / Itinerary workspace — `/trips/:tripId` (MVP)

This is the reference screen. Highest-value page.

- **Purpose:** View and build the itinerary day by day.
- **Regions (all flexible):**
  - **Top bar:** trip name (display font), date-range pill, city route + day count, budget
    pill, collaborator avatar stack + count, "Trip AI" toggle, account menu.
  - **Left sidebar (collapsible):** Travelers list (name, avatar, online/away, host role),
    "+ Invite traveler", Trip Stats (Days / Activities / Budget / Cities).
  - **Main column:** vertical list of collapsible `DayBox`es; each contains `ActivityRow`s
    on a timeline and a dashed "+ Add activity" button; a "+ Add day" button at the bottom.
  - **Right AI drawer:** toggled by "Trip AI" (later milestone; see 5.11).
- **Endpoints / data:**
  - `GET /api/trips/{tripId}` → name, startDate, endDate, owner, users (members), itinerary.
  - `GET /api/trips/{tripId}/activities` → activities to group into days.
  - `GET /api/users/me` → current user for author/ownership checks.
- **Derived data:** there is **no Day entity**. Build days by iterating every date from
  `startDate` to `endDate` inclusive; assign each activity to the day of its
  `startDateTime`. Days with no activities still render (empty day box).
- **States:**
  - loading: skeleton day boxes.
  - empty: trip exists but no activities → day boxes with empty hints.
  - not-found: `404` from `GET /api/trips/{tripId}` → not-found view.
  - unauthorized: `401` → redirect to sign-in; `403` → "You don't have access" view.
  - error: retry.
- **Notes:** Hide destructive controls for non-owners/non-members once roles are real.
  The reference shows a delete ✕ on hover; gate it.

### 5.6 Add / Edit Activity — modal (MVP)

- **Purpose:** Create or edit an activity inside a day.
- **Regions:** start/end time, type, title, location, est. cost, notes; Cancel / Save.
  Reuse the same component for edit, prefilled.
- **Mapping reality (important — see §8):** The backend Activity only stores
  `name`, `startDateTime`, `endDateTime`, `status`, and `locations` (a list of existing
  Location refs, at least one required). It has **no** `type`, `cost`, `notes`, or `addedBy`.
  For MVP, decide how to handle each (drop, encode, or extend the backend — **OPEN**).
- **Endpoints / data:**
  - `POST /api/trips/{tripId}/activities` with `{name, startDateTime, endDateTime, status, locations:[{id}]}`.
  - `PUT /api/trips/{tripId}/activities/{activityId}` for edits.
  - `GET /api/locations` for the location picker (needs actual `Location` ids).
- **Date/time format:** `startDateTime` / `endDateTime` are `ZonedDateTime` (use ISO-8601
  with offset, e.g. `2026-10-14T08:30:00+09:00`). The day is derived from this value.
- **States:** validation (end before start, missing title, no location), submitting,
  server error, unauthorized.
- **Notes:** A custom "type" is purely presentational for now; if we keep it, we need a place
  to store it (status? location category? backend field?).

### 5.7 Trip Settings / Members — `/trips/:tripId/settings` (MVP basic)

- **Purpose:** Rename a trip, change dates, manage members, delete the trip.
- **Regions:** trip form (name, start, end), member list with remove buttons, add-member
  control, danger zone (delete trip).
- **Endpoints:**
  - `PUT /api/trips/{tripId}` `{name, startDate, endDate}`.
  - `POST /api/trips/{tripId}/members/{userId}`.
  - `DELETE /api/trips/{tripId}/members/{userId}`.
  - `DELETE /api/trips/{tripId}`.
- **States:** owner-only view vs read-only for members; saving; confirm-delete; errors.
- **Notes:** Adding a member requires a **numeric user id**. There's no "find user by email"
  endpoint yet, so a true invite flow is blocked (see §8 Gaps and §10 Q3).

### 5.8 User Profile / Account — `/profile` (MVP basic)

- **Purpose:** View/update display name, see Clerk account info, sign out.
- **Regions:** avatar + name, display-name edit form, account details (email via Clerk),
  sign-out button, delete-account (later).
- **Endpoints:** `GET /api/users/me`, `PATCH /api/users/me` `{displayName}`.
- **States:** loading, save success/error, unauthorized.

### 5.9 404 / Error — `*` (MVP)

- **Purpose:** Friendly dead-end for unknown routes and unexpected errors.
- **Regions:** message, illustration/icon, "Back to my trips" / "Sign in" actions.
- **States:** route-not-found (404) and render/API error boundary share this visual language.
- **Notes:** Add a React error boundary around the app so a crash doesn't blank the screen.

### 5.10 Invite / Join trip — `/trips/join/:token` (Later)

- **Purpose:** Let an invited person join a trip.
- **Blocker:** the backend has no invitation/token concept and no way to look up a user by
  email or Clerk id from the frontend. `POST /api/trips/{tripId}/members/{userId}` needs a
  numeric id the invitee can't currently discover.
- **Possible MVP-lite interim:** organizer adds an existing member by numeric id from Trip
  Settings (no email invite). Mark the pretty invite flow as Later.

### 5.11 Trip AI Assistant — drawer on `/trips/:tripId` (Later; UI shell in MVP)

- **Purpose:** Chat suggestions, restaurant ideas, route optimisation, itinerary edits.
- **Regions:** header (status), "Try asking" suggestion chips, message bubbles, typing/
  loading indicator, input + send.
- **Blocker:** there is **no AI endpoint** in the current API. `spring.ai.openai.api-key` is a
  placeholder. The reference simulates responses with a `setTimeout`.
- **MVP option:** build the drawer as a UI shell with canned/echo responses, clearly marked
  as not-yet-connected. Real integration is a separate milestone.

### 5.12 Location picker / map explorer — (Later)

- **Purpose:** Pick or create the `Location`(s) an activity references; view them on a map.
- **Endpoints:** `GET /api/locations`, `GET /api/locations/{id}`, `POST /api/locations`,
  `DELETE /api/locations/{id}`. Fields: `name`, `category`, `latitude`, `longitude`,
  `isIndoor`, optional parent/sub-locations.
- **Notes:** MVP can use a plain searchable list of existing locations; map view is Later.

### 5.13 Locations admin — `/locations` (Later)

- **Purpose:** Manage the shared location catalog. Useful for dev/demo seeding.
- **Notes:** Low product priority; keep only if the team wants a data-authoring surface.

---

## 6. Shared component inventory

Build these once, reuse everywhere. Names are suggestions; internals are free.

| Component | Purpose | Notes |
| --- | --- | --- |
| `AppShell` | Page chrome: header + content slot + optional side panels | Used by all authed pages |
| `TopBar` | Page/trip header row | Trip variant vs dashboard variant |
| `SideNav` / `AccountMenu` | Global nav + sign-out | MVP can be a simple menu |
| `Button` | Primary / secondary / ghost / danger | Amber primary |
| `Avatar` | Initials avatar with color + online dot | Derive color from id |
| `AvatarStack` | Overlapping collaborator avatars + count | Top bar |
| `TypeBadge` | Activity type pill (icon + label) | Presentational; see §8 |
| `DayBox` | Collapsible day container with header stats | `open` state, stop count, day total |
| `ActivityRow` | Timeline row: time + duration, badge, title, location, cost, notes, author | Core list item |
| `Timeline` | Spine/dot rail wrapping `ActivityRow`s | Part of `DayBox` if simpler |
| `Modal` | Centered dialog | Reused by Add/Edit Activity, confirm-delete |
| `FormField` | Label + input/select/textarea wrapper | Shared `input-field` styling |
| `DateRangePicker` | Trip start/end selection | Validate end >= start |
| `TripCard` | Dashboard trip summary card | Name, dates, members, counts |
| `StatRow` | Label + mono value | Trip Stats sidebar |
| `EmptyState` | Icon + message + CTA | Dashboard, day, AI, members |
| `LoadingSkeleton` | Content-shaped placeholders | Days, cards, lists |
| `ErrorView` / `ErrorBoundary` | Error + retry | 404 page shares visuals |
| `Toast` | Transient success/error feedback | Optional in MVP |
| `AIChatDrawer` | AI message list + input | Shell in MVP, wired later |
| `ConfirmDialog` | Destructive-action confirmation | Delete trip/activity/member |

---

## 7. Responsive strategy

**Desktop-first**, because the workspace is a multi-panel layout.

- **Desktop (≥1024px):** sidebar + main + AI drawer all visible; this is the primary target.
- **Tablet (768–1023px):** sidebar collapses to an icon/overlay toggle; AI drawer overlays
  instead of pushing content; main column stays.
- **Mobile (<768px):** single column. Sidebar and AI become full-screen sheets/drawers.
  Day boxes stack; activity rows reflow (time above title). Top bar condenses to trip name +
  a menu; avatar stack shows a count only.
- **Daily drivers:** test at ~390px and ~1440px.
- **OPEN:** whether mobile is "usable" or "full-featured" in scope for MVP. Recommend usable
  read + add activity, defer heavy settings/AI.

---

## 8. Frontend types ↔ backend DTO mapping (and gaps)

This is the most important table in the doc. Figma's data model is richer than the current
backend. Treat the **Gaps** rows as explicit work items or decisions.

### 8.1 Trip

| Frontend field | Backend field | Status |
| --- | --- | --- |
| `id` | `id` (Long) | OK |
| `name` | `name` (String) | OK |
| `startDate` | `startDate` (LocalDate) | OK |
| `endDate` | `endDate` (LocalDate) | OK |
| `ownerId` | `owner.id` | OK (nested) |
| `members` | `users` (List<User>) | OK |
| `itinerary` | `itinerary` (List<Activity>) | OK |
| `city` / `country` (Figma day) | — | **GAP:** no field on Trip or Location |
| `budget` | — | **GAP:** no field; Figma derives from activity costs |

### 8.2 Activity

| Frontend field | Backend field | Status |
| --- | --- | --- |
| `id` | `id` (Long) | OK |
| `title` | `name` (String) | Rename mapping |
| `startTime` | `startDateTime` (ZonedDateTime) | OK; format differs |
| `endTime` | `endDateTime` (ZonedDateTime) | OK; format differs |
| `location` (free text) | `locations` (List<Location> by id) | **GAP:** backend needs real location ids |
| `type` (flight/hotel/food/activity/transport) | — | **GAP:** no field; presentation-only for now |
| `cost` | — | **GAP:** no field; Figma budget/totals can't persist |
| `notes` | — | **GAP:** no field; user notes would be lost |
| `addedBy` | — | **GAP:** no author field; `ActivityRow` author avatar can't resolve |
| — | `status` (String) | Backend-only; decide what it means (planned/confirmed/done?) |
| — | `trip` | `@JsonIgnore`; comes from the URL |

### 8.3 Day

| Frontend field | Backend field | Status |
| --- | --- | --- |
| `id` / `label` / `date` | — | **GAP:** no Day entity. Derive from `trip.startDate..endDate` |
| `city` / `country` | — | **GAP:** nothing to derive from |
| `activities[]` | grouped from trip activities | Derived client-side |

### 8.4 User

| Frontend field | Backend field | Status |
| --- | --- | --- |
| `id` | `id` (Long) | OK |
| `displayName` | `displayName` | OK |
| `clerkUserId` | `clerkUserId` | Backend-only; don't expose needlessly |
| `avatar` initials | — | Derive client-side from name |
| `online` / `away` | — | **GAP:** no presence concept. Fake/cosmetic in MVP |
| `role` (organizer/traveler) | `owner.id === userId` | Derive from ownership |

### 8.5 Cross-cutting gaps

1. **No user-scoped trips endpoint.** `GET /api/trips` returns all trips. The dashboard must
   filter by membership client-side, or the backend adds `GET /api/users/me/trips`
   (already sketched as a TODO in `UserController`).
2. **No CORS configuration.** There is no CORS/security config in `backend/src/main`, so a
   browser on the Vite origin (`http://localhost:5173`) cannot call the API
   (`http://localhost:8080`). This blocks **all** frontend↔backend work until fixed.
   Interim option: Vite dev proxy for `/api` → `localhost:8080`.
3. **No way to find a user to invite.** Member add/remove needs a numeric `userId`; there is
   no lookup by email/Clerk id. Blocks the invite flow.
4. **No AI endpoint.** `Trip AI` can only be a shell.
5. **No persisted activity cost/type/notes/author.** Budget pill, type badges, notes, and
   author avatars cannot survive a refresh unless the backend grows fields or we accept
   a reduced model for MVP.
6. **IDs are numeric `Long`**, not strings. Type them as `number` and parse route params.
7. **No `Day` identity.** Day grouping is a pure frontend concern and must be centralised in
   one helper (e.g. `groupActivitiesByDate(trip, activities)`).

> Recommendation: freeze a decision on gap #5 before M4 (create/edit) so the API client and
> form don't get rewritten. See Q1–Q4.

---

## 9. API client & auth approach (suggested)

- **Single API module** (e.g. `frontend/src/lib/api.ts`) that reads `VITE_API_URL` and wraps
  `fetch`. One place for base URL, JSON handling, and error normalisation.
- **Auth header:** attach `Authorization: Bearer <Clerk token>` per request. In
  `@clerk/react`, use the session token getter (e.g. `useAuth().getToken()`); outside React,
  expose a token accessor set once at app start.
- **Typed responses:** hand-write TypeScript interfaces matching §8. No schema generation in
  MVP.
- **Error envelope:** normalise non-2xx responses into a small `ApiError { status, message }`
  so pages can distinguish 401 (sign-in) / 403 (no access) / 404 (not-found) / 5xx (retry).
- **File layout (suggested, not required):**
  ```
  frontend/src/
  ├─ main.tsx              # providers: Clerk, Router
  ├─ App.tsx               # route table
  ├─ lib/api.ts            # fetch wrapper + endpoints
  ├─ lib/logger.ts         # already present
  ├─ types/                # Trip, Activity, Location, User
  ├─ routes/               # one folder per page
  └─ components/           # shared components (§6)
  ```
- **Env:** `VITE_API_URL` (already in `.env.example`), `VITE_CLERK_PUBLISHABLE_KEY`
  (currently empty — needs a real key).

---

## 10. Parallel work breakdown (lanes)

Split so people rarely touch the same files. Put names in the Owner column.

| Lane | Scope | Owner | Depends on |
| --- | --- | --- | --- |
| **A — Foundation** | Router setup, `AppShell`, theme tokens in `index.css`, fonts, API client, auth context, Clerk provider | _TBD_ | — |
| **B — Auth & Account** | Sign-in/up wiring, onboarding/display-name, profile, account menu, `RequireAuth` guard | _TBD_ | A |
| **C — Dashboard** | `/trips` list, `TripCard`, create-trip modal, empty/loading states | _TBD_ | A, CORS fix |
| **D — Trip Workspace** | `/trips/:tripId`, top bar, `DayBox`, `ActivityRow`, day-derivation helper, stats sidebar | _TBD_ | A, CORS fix |
| **E — Activity CRUD** | Add/Edit modal, form validation, time/type mapping, delete | _TBD_ | D, backend decision on §8 Gaps |
| **F — Collaboration** | Members list, Trip Settings, add/remove member, invite flow | _TBD_ | D, user-lookup gap resolved |
| **G — AI Panel** | Drawer shell, suggestion chips, message list, loading, input | _TBD_ | D |
| **H — Locations** | Location picker list, optional map, location admin | _TBD_ | E |
| **I — Backend enablement** | CORS config, user-scoped trips, activity fields (type/cost/notes/author), user lookup, invite | _TBD (backend)_ | — |

Coordination rules:

- Lane A lands `index.css` tokens early; others consume tokens, not hex codes.
- Lane D owns the day-grouping helper; other lanes import it — don't duplicate.
- Lanes E/F negotiate the shared `Modal`, `FormField`, `Button` with Lane A to avoid forks.
- Lane I is the hard dependency; CORS should be unblocked first.

### Milestones

| Milestone | Goal | Lanes | Exit criteria |
| --- | --- | --- | --- |
| **M1** | Auth + shell | A, B | Sign in with Clerk, token sent to a health check, `AppShell` renders, tokens/fonts live |
| **M2** | Dashboard | C | `/trips` lists trips and creates one (create may be blocked on CORS/owner id) |
| **M3** | Trip detail (read-only) | D, I (CORS) | Open a trip, see derived days + activities, stats, members |
| **M4** | Create/edit | E | Add/edit/delete activity with a real location; reload keeps data |
| **M5** | Collaboration / invites | F, I (lookup+invite) | Manage members; (if backend ready) join via invite |
| **M6** | AI panel | G, I (AI endpoint) | Real or clearly-labelled simulated AI conversation in the drawer |

Suggested sequencing: unblock **Lane I CORS** during M1; start M2/M3 UI in parallel against
mocked data while the API decision on §8 Gaps is pending.

---

## 11. Open questions / decisions needed

These are genuine TBDs, not oversights. Record the decision and date when resolved.

1. **Activity extras (`type`, `cost`, `notes`, `addedBy`)** — do we add fields to the backend
   Activity entity, or cut them from MVP? Cutting keeps scope small but loses most of the
   reference design's richness (budget pill, type badges, author avatars, notes).
2. **Location model in the form** — require the user to pick a pre-existing `Location`, or add
   "create location inline" during activity creation? The latter needs lat/long, which users
   can't easily supply without a map/search.
3. **Invites** — do we build a real invite flow (needs an invite token/email lookup on the
   backend), or ship "add existing member by id" only for MVP?
4. **`status` field meaning** — the backend has a free-text `status` on Activity. Is it a
   workflow state (planned/confirmed/done), a type substitute, or unused?
5. **Days with no date coverage** — if an activity's `startDateTime` falls outside the trip's
   `startDate..endDate`, do we hide it, clamp it, or show an "outside trip" section?
6. **Timezone handling** — `LocalDate` for trips vs `ZonedDateTime` for activities. Do we
   store a trip timezone, or use the browser's? Affects "Day 1" boundaries.
7. **Cities/countries** — the reference shows a city route and city count, but there's no city
   field anywhere. Derive from location data (needs a city on Location), add a trip field, or
   drop the feature?
8. **Budget semantics** — is the budget a sum of activity costs, a user-set cap, or both?
   With no `cost` field, the budget pill is currently unfillable from the backend.
9. **Clerk keys & provisioning** — who provides `VITE_CLERK_PUBLISHABLE_KEY` and the backend
   `CLERK_ISSUER_URI`? Local dev is blocked without them.
10. **Landing page direction** — keep it light as-is, or restyle to the dark travel aesthetic?
11. **Online/away presence** — fake it cosmetically, cut it, or plan a real presence channel
    (WebSocket) later?
12. **Mobile depth** — read-only + add activity, or full feature parity?
13. **Auth scoping** — since `GET /api/trips` returns all trips, is that a bug to fix in the
    backend, or do we accept client-side filtering for the course demo?

---

## 12. Verification checklist (for whoever builds this)

- Every route in §3 resolves (no dead links) and unknown routes hit the 404 page.
- Signed-out users hitting authed routes are redirected to sign-in and returned after login.
- Each page has visibly distinct loading / empty / error / not-found / unauthorized states.
- Activities added then refreshed still appear (proves persistence, not local state).
- Days render from trip dates, including an empty day with zero activities.
- Tokens are defined once in `index.css`; components use them, not raw hex.
- CORS (or Vite proxy) works from the dev origin before M2/M3 integration is called done.

---

## 13. Wireframe sketches (indicative)

These are rough layout sketches, not pixel specs. Proportions are approximate, alignment is
loose, and every region here may change — same "not final design" framing as the rest of the
doc. They exist only to show which regions exist relative to each other and roughly what
state they carry. Boxes are labelled for recognition, not for exact content.

Ordering matches the §4 page inventory.

### 13.1 Landing

**Landing — `/` (public, desktop)**

```text
+----------------------------------------------------------------+
|  Agile Trips                                    [ Sign in ]     |
|                                                                 |
|        Plan your trips, organise your itinerary,                |
|        and adapt when things change.                            |
|                                                                 |
|                     [ Get started ]                             |
|                                                                 |
|     ( optional workspace preview / product screenshot )         |
|                                                                 |
|  How it works   |   Built for groups   |   Works offline-first  |
+----------------------------------------------------------------+
```

- Two CTAs map to Clerk: "Sign in" and "Get started" (sign-up).
- If a session already exists, redirect to `/trips` instead of rendering.

### 13.2 Sign-in / Sign-up

**Sign-in / Sign-up — `/sign-in/*`, `/sign-up/*` (public, desktop)**

```text
+----------------------------------------------------------------+
|                    [ LOGO / APP NAME ]                          |
|                                                                 |
|          +-----------------------------------------+            |
|          |  Welcome back                           |            |
|          |  ( Clerk-hosted sign-in component )     |            |
|          |  [ email                    ]            |            |
|          |  [ password                 ]            |            |
|          |  [ Continue ]                           |            |
|          |  New here?  [ Sign up ]                 |            |
|          +-----------------------------------------+            |
+----------------------------------------------------------------+
```

- Clerk owns the form fields; we own the surrounding frame and theme.
- Loading / invalid-credentials states render inside the Clerk card.

### 13.3 Onboarding / display name

**Onboarding — `/onboarding` (auth, first login only)**

```text
+----------------------------------------------------------------+
|                    Set up your profile                          |
|                                                                 |
|            +---------------------------------------+            |
|            |  ( MC )   avatar preview (initials)   |            |
|            |                                       |            |
|            |  Display name                         |            |
|            |  [ Your name                 ]        |            |
|            |  [ Continue ]                         |            |
|            +---------------------------------------+            |
|                                                                 |
|   Shown next to activities and in the travelers list.           |
+----------------------------------------------------------------+
```

- Calls `POST /api/users/me { displayName }`; responses are find-or-create.
- If the user already exists, skip straight to `/trips`.

### 13.4 Trip Dashboard / My Trips

**Trip Dashboard — `/trips` (auth, desktop; cards stack on mobile)**

```text
+----------------------------------------------------------------+
|  My Trips                            [ Search ]   [ Avatar v ]  |
+----------------------------------------------------------------+
|  +------------------+  +------------------+  +--------------+   |
|  | Japan Autumn     |  | Bali 2027        |  | + New trip   |   |
|  | Oct 14 - 21      |  | Mar 2 - 9        |  |              |   |
|  | 8 days - 4 ppl   |  | 7 days - 2 ppl   |  | (modal form) |   |
|  | (MC)(LF)(PN)     |  | (MC)             |  |              |   |
|  +------------------+  +------------------+  +--------------+   |
|                                                                 |
|  ( empty state: illustration + "Create your first trip" here )   |
+----------------------------------------------------------------+
```

- Cards show name, date range, day count, member avatars.
- List is filtered client-side until a user-scoped trips endpoint exists.

### 13.5 Trip Detail / Itinerary (desktop)

**Trip Detail — `/trips/:tripId` (desktop, three panels)**

```text
+---------------------------------------------------------------------------------------+
| Japan Autumn  [Oct 14-21]   Tokyo -> Kyoto   8 days   [$2,040]   (MC LF PN)  [Trip AI] |
+-------------+---------------------------------------------------+---------------------+
| Travelers   | [ Day 1 - Oct 14 - Tokyo  v ]        3 stops      | Trip AI      * Active|
| (MC) Maya   |  o 08:30-15:50 [flight] Flight SQ639    $1,240    |---------------------|
| (LF) Luca   |  o 17:00-18:00 [hotel]  Check-in         $480     | Try asking          |
| (PN) Priya  |  o 19:30-21:30 [food]   Dinner Saito      $95     | [ day trip ]        |
| [+ Invite]  |  [ + Add activity ]                                | [ veg food ]        |
|             | [ Day 2 - Oct 15 - Tokyo  >]          3 stops      |                     |
| Trip Stats  | [ + Add day ]                                     | (chat messages)     |
| Days      8 |                                                    |                     |
| Activities 9|                                                    | [ ask... ]      [->]|
| Budget $2040|                                                    |                     |
| Cities    2 |                                                    |                     |
+-------------+---------------------------------------------------+---------------------+
```

- Left sidebar collapses to zero width via an edge toggle; AI drawer is toggled, hidden by default.
- Day boxes collapse/expand; empty days still render with a "no activities yet" hint.
- Activity delete (hover ✕) should be hidden from non-owners once roles are real.

### 13.6 Trip Detail / Itinerary (mobile)

**Trip Detail — `/trips/:tripId` (mobile, single column)**

```text
+---------------------------+
| <  Japan Autumn   [=] [AI]|
| Oct 14-21  Tokyo->Kyoto   |
| 8 days  $2,040  4 ppl     |
+---------------------------+
| [ Day 1 - Oct 14 - Tokyo v]|
|  o 08:30 [flight] Flight  |
|  o 17:00 [hotel]  Check-in|
|  o 19:30 [food]   Dinner  |
|  [ + Add activity ]       |
| [ Day 2 - Oct 15 - Tokyo >]|
| [ + Add day ]             |
+---------------------------+
| sheet [ Travelers + Stats ]|  <- opened via [=]
| sheet [ Trip AI chat ]     |  <- opened via [AI]
+---------------------------+
```

- Sidebar and AI drawer become full-screen sheets on small screens.
- Read + add activity is the mobile MVP target; heavy settings can stay desktop-first.

### 13.7 Add / Edit Activity modal

**Add / Edit Activity — modal over `/trips/:tripId` (overlay)**

```text
+----------------------------------------------------------------+
|  ########## dimmed itinerary background ##########             |
|      +-----------------------------------------------+         |
|      |  Add Activity                                 |         |
|      |  Start [10:00]  End [12:00]  Type [ activity v]|        |
|      |  Title      [                               ] |        |
|      |  Location   [ search / pick a location      ] |         |
|      |  Est. Cost  [ 0 ]                             |         |
|      |  Notes      [                               ] |         |
|      |             [ Cancel ]   [ Add to itinerary ]  |        |
|      +-----------------------------------------------+         |
+----------------------------------------------------------------+
```

- Same component prefilled for Edit; title becomes "Edit Activity".
- Location field requires choosing an existing `Location` (see §8 and §11 Q2).
- End-before-start and missing-location are inline validation, not server round-trips.

### 13.8 Trip Settings / Members

**Trip Settings / Members — `/trips/:tripId/settings` (auth, owner)**

```text
+----------------------------------------------------------------+
| <  Japan Autumn   /   Settings                                  |
+----------------------------------------------------------------+
|  Trip details                                                   |
|  Name    [ Japan Autumn              ]                          |
|  Start   [ 2026-10-14 ]   End [ 2026-10-21 ]                    |
|                                        [ Save changes ]         |
+----------------------------------------------------------------+
|  Members (4)                              [ + Add member ]      |
|  (MC) Maya Chen        host      [ owner ]                      |
|  (LF) Luca Ferri                 [ Remove ]                     |
|  (PN) Priya Nair                 [ Remove ]                     |
+----------------------------------------------------------------+
|  Danger zone:  [ Delete trip ]                                  |
+----------------------------------------------------------------+
```

- Owner-only editing; members see a read-only variant.
- Delete trip and remove member go through `ConfirmDialog`.

### 13.9 User Profile / Account

**Profile — `/profile` (auth)**

```text
+----------------------------------------------------------------+
|  Profile                                        [ Account v ]   |
+----------------------------------------------------------------+
|         ( MC )   Maya Chen                                      |
|                  maya@example.com   (from Clerk)                |
|                                                                 |
|  Display name   [ Maya Chen             ]   [ Save ]            |
|                                                                 |
|  [ Sign out ]                                                   |
+----------------------------------------------------------------+
```

- Display name uses `GET` / `PATCH /api/users/me`.
- Account email comes from the Clerk session, not our API.

### 13.10 404 / Error

**404 / Error — `*` (public)**

```text
+----------------------------------------------------------------+
|                                                                 |
|                            404                                  |
|                    This page took a wrong turn.                 |
|                                                                 |
|              [ Back to my trips ]    [ Sign in ]                |
|                                                                 |
+----------------------------------------------------------------+
```

- Shared visual language with the API error boundary and empty states.
- Signed-out users see "Sign in" only; signed-in users see "Back to my trips".

### 13.11 Invite / Join trip

**Invite / Join trip — `/trips/join/:token` (auth, later)**

```text
+----------------------------------------------------------------+
|                                                                 |
|              You've been invited to join                        |
|                     " Japan Autumn "                            |
|                                                                 |
|        Maya Chen invited you  ·  8 days  ·  4 travelers         |
|                                                                 |
|                  [ Accept & join trip ]                         |
|                  [ Decline ]                                    |
|                                                                 |
+----------------------------------------------------------------+
```

- **Blocked:** no invite/token or user-lookup endpoint exists yet (see §8 Gaps, §11 Q3).
- Interim MVP: add members by numeric id from Trip Settings instead of this page.

### 13.12 Trip AI Assistant drawer

**Trip AI Assistant — drawer on `/trips/:tripId` (auth, later / shell now)**

```text
+------------------------------------------+
| Trip AI          * Active            [x] |
+------------------------------------------+
| TRY ASKING                               |
| [ Suggest a day trip from Kyoto ]        |
| [ Vegetarian restaurants in Tokyo ]      |
| [ Optimize Day 2 by travel time ]        |
+------------------------------------------+
| * Hi! I can suggest activities...        |
|                        [ user question ] |
| * Thinking...                            |
| * Here are three options: ...            |
+------------------------------------------+
| [ Ask anything about the trip... ]  [->] |
+------------------------------------------+
```

- Toggled from the top bar "Trip AI" button; hidden by default.
- No AI endpoint yet — MVP is a clearly-labelled canned/echo shell (§11 gap).

### 13.13 Location picker / map explorer

**Location picker — modal / panel (later)**

```text
+----------------------------------------------------------------+
|  Pick a location                                                |
|  [ search locations...                        ]                 |
+----------------------------------------------------------------+
|  * Tsukiji Outer Market      food       outdoor                 |
|  * Senso-ji Temple           activity   outdoor                 |
|  * The Prince Gallery        hotel      indoor                  |
|  * + Create new location                                        |
+----------------------------------------------------------------+
|  [ Cancel ]                              [ Use selected ]       |
+----------------------------------------------------------------+
```

- Sources from `GET /api/locations`; selected id goes into the activity `locations` list.
- Map view is a later enhancement over this list.

### 13.14 Locations admin

**Locations admin — `/locations` (later)**

```text
+----------------------------------------------------------------+
|  Locations                          [ + New location ]          |
+----------------------------------------------------------------+
|  Name                  Category    Indoor   Lat / Lng    [del]  |
|  Tsukiji Outer Market  food        no       35.6, 139.7  [ x ]  |
|  Senso-ji Temple       activity    no       35.7, 139.8  [ x ]  |
|  The Prince Gallery    hotel       yes      35.6, 139.8  [ x ]  |
+----------------------------------------------------------------+
```

- Optional data-authoring surface for seeding/demoing locations.
- Uses `POST /api/locations` and `DELETE /api/locations/{id}`.
