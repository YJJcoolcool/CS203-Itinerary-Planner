/**
 * Shared domain types for the trips backend contract.
 *
 * These mirror the JSON shapes returned by the Spring API. Keep them free of
 * React and fetch concerns so both data and UI layers can depend on them.
 */

/** A backend user account. */
export interface User {
  id: string
  clerkUserId: string
  displayName: string | null
}

/** A place an activity happens at, as returned by the locations endpoints. */
export interface Location {
  id: string
  name: string
  category: string | null
  latitude: number
  longitude: number
  isIndoor: boolean | null
}

/** An itinerary entry, as returned by `GET /api/trips/{tripId}/activities`. */
export interface Activity {
  id: string
  name: string
  /** ISO-8601 date-time with offset, e.g. `2026-10-14T08:30:00+09:00`. */
  startDateTime: string | null
  endDateTime: string | null
  status: string | null
}

/** A trip as returned by `GET /api/trips` and `POST /api/trips`. */
export interface Trip {
  id: string
  name: string
  /** ISO date string, e.g. `2026-09-26`. */
  startDate: string
  /** ISO date string, e.g. `2026-10-03`. */
  endDate: string
  owner: User
  users: User[]
  itinerary: Activity[]
}

/** Client-supplied fields for creating a trip. `ownerId` is added by the data layer. */
export interface CreateTripInput {
  name: string
  startDate: string
  endDate: string
}
