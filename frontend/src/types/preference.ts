/**
 * Shared types for user and trip travel preferences.
 *
 * Mirrors the JSON returned by the backend preference endpoints. Kept free of
 * React and fetch concerns so both data and UI layers can depend on them.
 */

/** Mirrors backend indoor preference values. */
export const INDOOR_PREFERENCES = ['ANY', 'INDOOR', 'OUTDOOR'] as const

export type IndoorPreference = (typeof INDOOR_PREFERENCES)[number]

/** Display label for an indoor preference segment. */
export const INDOOR_PREFERENCE_LABELS: Record<IndoorPreference, string> = {
  ANY: 'Any',
  INDOOR: 'Indoor',
  OUTDOOR: 'Outdoor',
}

/** The interests offered as chips on the personal preference form. */
export const INTEREST_OPTIONS = [
  'art',
  'food',
  'nature',
  'shopping',
  'history',
  'nightlife',
  'family',
  'photography',
] as const

export type Interest = (typeof INTEREST_OPTIONS)[number]

/** Trip goals. Mirrors the backend category groups. */
export const TRIP_GOALS = [
  'MUSEUMS',
  'NATURE',
  'FOOD',
  'SHOPPING',
  'CULTURE',
  'NIGHTLIFE',
  'ATTRACTIONS',
] as const

export type TripGoal = (typeof TRIP_GOALS)[number]

/** Display label for a trip goal chip. */
export const TRIP_GOAL_LABELS: Record<TripGoal, string> = {
  MUSEUMS: 'Museums',
  NATURE: 'Nature',
  FOOD: 'Food',
  SHOPPING: 'Shopping',
  CULTURE: 'Culture',
  NIGHTLIFE: 'Nightlife',
  ATTRACTIONS: 'Attractions',
}

/**
 * One user's travel preferences. `notes` is always null on the public view
 * (`GET /api/users/{userId}/preferences`).
 */
export interface UserPreferences {
  userId: string
  tags: string[]
  maxWalkKm: number | null
  dietary: string | null
  indoorPreference: IndoorPreference | null
  summary: string | null
  notes: string | null
  /** ISO-8601 instant, e.g. `2026-10-21T08:12:45Z`. */
  updatedAt: string
}

/** Client-supplied fields for saving personal preferences. */
export interface UpdateUserPreferencesInput {
  tags: string[]
  maxWalkKm: number | null
  dietary: string | null
  indoorPreference: IndoorPreference | null
  notes: string | null
}

/** A trip's shared group preference, readable by every member. */
export interface TripPreferences {
  tripId: string
  goals: TripGoal[]
  tags: string[]
  summary: string | null
  summaryStale: boolean
  /** User id of the last editor, or null if it was never set. */
  updatedBy: string | null
  /** ISO-8601 instant, e.g. `2026-10-21T08:12:45Z`. */
  updatedAt: string
}

/** Client-supplied fields for saving a trip's group preference. */
export interface UpdateTripPreferencesInput {
  goals: TripGoal[]
  tags: string[]
}
