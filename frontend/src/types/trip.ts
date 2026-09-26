/**
 * Shared domain types for the trips backend contract.
 *
 * These mirror the JSON shapes returned by the Spring API. Keep them free of
 * React and fetch concerns so both data and UI layers can depend on them.
 */

/** A backend user account. */
export interface User {
  id: number
  clerkUserId: string
  displayName: string | null
}

/** A trip as returned by `GET /api/trips` and `POST /api/trips`. */
export interface Trip {
  id: number
  name: string
  /** ISO date string, e.g. `2026-09-26`. */
  startDate: string
  /** ISO date string, e.g. `2026-10-03`. */
  endDate: string
  owner: User
  users: User[]
  itinerary: unknown[]
}

/** Client-supplied fields for creating a trip. `ownerId` is added by the data layer. */
export interface CreateTripInput {
  name: string
  startDate: string
  endDate: string
}
