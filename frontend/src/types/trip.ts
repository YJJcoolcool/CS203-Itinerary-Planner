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
  /**
   * True for a place a user entered by hand (not seeded or from OSM). These
   * belong to one trip and can be deleted by its editors (CGT-142, CGT-172).
   */
  manual?: boolean
}

export type ActivityType = 'ACTIVITY' | 'FLIGHT'

/** An itinerary entry, as returned by `GET /api/trips/{tripId}/activities`. */
export interface Activity {
  id: string
  name: string
  type: ActivityType
  startDateTime: string | null
  endDateTime: string | null
  status: string | null

  flightNumber?: string | null
  flightDate?: string | null
  airline?: string | null

  departureAirportCode?: string | null
  departureAirportName?: string | null
  departureTerminal?: string | null

  arrivalAirportCode?: string | null
  arrivalAirportName?: string | null
  arrivalTerminal?: string | null
}

/** A member's access level on a trip. Mirrors backend TripRole. */
export type MemberRole = 'OWNER' | 'EDITOR' | 'VIEWER'

/** A trip as returned by `GET /api/trips` and `POST /api/trips`. */
export interface Trip {
  id: string
  name: string
  /** ISO date string, e.g. `2026-09-26`. */
  startDate: string
  /** ISO date string, e.g. `2026-10-03`. */
  endDate: string
  /** Lowercase ISO 3166-1 alpha-2 country code, e.g. `"sg"`. */
  countryCode: string
  /** Custom or predefined cover image URL. Null uses the default cover. */
  coverImageUrl: string | null
  owner: User
  users: User[]
  /** User id -> role. A member missing from this map is a VIEWER (see `roleOf`). */
  roles?: Record<string, MemberRole>
  itinerary: Activity[]
}

/** Client-supplied fields for creating a trip. The backend sets the owner from the login token. */
export interface CreateTripInput {
  name: string
  startDate: string
  endDate: string
  countryCode: string
  coverImageUrl: string | null
}

/** Client-supplied fields for creating an activity. */
export interface CreateActivityInput {
  name: string
  type?: ActivityType
  startDateTime: string | null
  endDateTime: string | null
  locations: Pick<Location, 'id'>[]

  flightNumber?: string
  airline?: string

  departureAirportCode?: string
  departureAirportName?: string
  departureTerminal?: string

  arrivalAirportCode?: string
  arrivalAirportName?: string
  arrivalTerminal?: string
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

/** A four-day weather forecast returned by the backend. */
export interface FourDayWeatherForecast {
  date: string
  forecast: string
  rainExpected: boolean
}

/**
 * One day of point-based weather for a non-NEA destination, returned by
 * `GET /api/trips/{tripId}/weather/outlook` (Open-Meteo backed).
 */
export interface WeatherOutlookDay {
  /** ISO date string, e.g. `2026-11-02`. */
  date: string
  rainy: boolean
  precipitationMm: number
  probabilityPercent?: number | null
  /** Null when no hour that day reported a WMO code. */
  weatherCode: number | null
}

/** A two-hour weather forecast returned by the backend. */
export interface TwoHourWeatherForecast {
  area: string
  forecast: string
  validFrom: string
  validUntil: string
  updatedAt: string | null
  rainExpected: boolean
}

/** Transport modes supported by the activity routing endpoint. */
export type RouteMode = 'PUBLIC_TRANSPORT' | 'DRIVING' | 'WALKING' | 'CYCLING'

/** Response from GET /api/trips/{tripId}/activities/{fromId}/route-to/{toId}. */
export interface RouteResponse {
  fromActivityId: string
  toActivityId: string
  fromLocationName: string
  toLocationName: string
  mode: RouteMode
  departureDateTime: string
  routeData: unknown
}

/** Route geometry */
export interface RouteMapData {
  coordinates: [number, number][]
}