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
  /** OSM identity, only present on locations imported from OpenStreetMap. */
  osmType?: string | null
  osmId?: number | null
  /** Human-readable address / display name from OSM, when known. */
  address?: string | null
  /** Free-form OSM opening_hours value, when known. */
  openingHours?: string | null
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

/** Client-supplied fields for creating an activity. */
export interface CreateActivityInput {
  name: string
  startDateTime: string | null
  endDateTime: string | null
  locations: Pick<Location, 'id'>[]
}

/** Structured address components of a geocoded place. */
export interface GeoAddress {
  houseNumber: string | null
  road: string | null
  city: string | null
  state: string | null
  postcode: string | null
  country: string | null
  countryCode: string | null
}

/** A rectangular geographic area, as returned by `GET /api/geo/search`. */
export interface GeoBoundingBox {
  minLat: number
  minLon: number
  maxLat: number
  maxLon: number
}

/**
 * A place from OpenStreetMap, as returned by `GET /api/geo/search`. Distinct
 * from `Location`, which is a place the backend has already imported and stored.
 */
export interface GeoPlace {
  osmType: string
  osmId: number
  name: string | null
  displayName: string | null
  category: string | null
  type: string | null
  latitude: number
  longitude: number
  address: GeoAddress | null
  openingHours: string | null
  boundingBox: GeoBoundingBox | null
  importance: number | null
}

/**
 * Envelope for a geo search: the matching places plus the required OSM
 * attribution text that must be shown wherever the results are displayed.
 */
export interface GeoSearchResponse {
  results: GeoPlace[]
  attribution: string | null
}
