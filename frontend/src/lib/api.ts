import type { TripInvitation } from '../types/invitation'
import type {
  Activity,
  CreateActivityInput,
  CreateTripInput,
  FourDayWeatherForecast,
  GeoPlace,
  GeoSearchResponse,
  Location,
  Trip,
  TwoHourWeatherForecast,
  User,
} from '../types/trip'

/**
 * Base URL for the Spring backend. Falls back to local development so the
 * app still works when `VITE_API_URL` is not set.
 */
const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/**
 * Error thrown for any non-2xx response (or a network failure, reported with
 * `status === 0`). Carries the HTTP status so callers can branch on 401/404.
 */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  token?: string | null
}

/** Extract a human-readable message from an error response body. */
async function readErrorMessage(response: Response): Promise<string> {
  const fallback = `Request failed with status ${response.status}.`

  try {
    const raw = await response.text()
    if (!raw) return fallback

    try {
      const parsed: unknown = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        const record = parsed as Record<string, unknown>
        for (const key of ['message', 'error', 'detail']) {
          const value = record[key]
          if (typeof value === 'string' && value.trim()) return value
        }
      }
    } catch {
      // Body was not JSON; fall through to the plain-text fallback below.
    }

    const text = raw.trim()
    if (text && text.length <= 200 && !text.startsWith('<')) return text
    return fallback
  } catch {
    return fallback
  }
}

/**
 * Single fetch wrapper for the backend. Attaches the Clerk bearer token when
 * present, serialises JSON bodies, and normalises failures to `ApiError`.
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, token } = options

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (cause) {
    const message =
      cause instanceof Error && cause.message
        ? cause.message
        : 'Network request failed. Please check your connection.'
    throw new ApiError(0, message)
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) {
    return undefined as T
  }

  const parsed: unknown = JSON.parse(text)
  return parsed as T
}

/**
 * Fetches the current backend user. Returns 404 via `ApiError` when the user
 * row has not been created yet — prefer `createBackendUser` to find-or-create.
 */
export function getCurrentUser(token: string | null): Promise<User> {
  return request<User>('/api/users/me', { token })
}

/**
 * Find-or-create for the current backend user. Safe to call even when the row
 * already exists, which makes it the reliable way to resolve the user id.
 */
export function createBackendUser(token: string | null, displayName: string): Promise<User> {
  return request<User>('/api/users/me', {
    method: 'POST',
    body: { displayName },
    token,
  })
}

/** Fetches the logged-in user's trips (the ones they own or are a member of). */
export function getTrips(token: string | null): Promise<Trip[]> {
  return request<Trip[]>('/api/trips', { token })
}

/** Creates a trip. The backend makes the logged-in user (from the token) its owner. */
export function createTrip(token: string | null, input: CreateTripInput): Promise<Trip> {
  return request<Trip>('/api/trips', {
    method: 'POST',
    body: input,
    token,
  })
}

/** Fetches a single trip. Throws `ApiError` with status 404 when it does not exist. */
export function getTrip(token: string | null, tripId: string): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}`, { token })
}

/** Updates a trip's name and dates. Owner-only. */
export function updateTrip(
  token: string | null,
  tripId: string,
  input: {
    name: string
    startDate: string
    endDate: string
  },
): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}`, {
    method: 'PUT',
    body: input,
    token,
  })
}

/** Deletes a trip. Owner-only. */
export function deleteTrip(
  token: string | null,
  tripId: string,
): Promise<void> {
  return request<void>(`/api/trips/${tripId}`, {
    method: 'DELETE',
    token,
  })
}

/** Fetches a trip's activities, ordered by start time. */
export function getActivities(token: string | null, tripId: string): Promise<Activity[]> {
  return request<Activity[]>(`/api/trips/${tripId}/activities`, { token })
}

/** Creates an activity within a trip. Locations must reference existing IDs. */
export function createActivity(
  token: string | null,
  tripId: string,
  input: CreateActivityInput,
): Promise<Activity> {
  return request<Activity>(`/api/trips/${tripId}/activities`, {
    method: 'POST',
    body: input,
    token,
  })
}

/** Fetches the locations attached to one activity of a trip. */
export function getActivityLocations(
  token: string | null,
  tripId: string,
  activityId: string,
): Promise<Location[]> {
  return request<Location[]>(`/api/trips/${tripId}/activities/${activityId}/locations`, { token })
}
/** Owner invites an existing user (by email) to a trip. */
export function sendInvitation(
  token: string | null,
  tripId: string,
  email: string,
): Promise<TripInvitation> {
  return request<TripInvitation>(`/api/trips/${tripId}/invitations`, {
    method: 'POST',
    body: { email },
    token,
  })
}

/** Owner-only: pending invitations for a trip. */
export function getTripInvitations(token: string | null, tripId: string): Promise<TripInvitation[]> {
  return request<TripInvitation[]>(`/api/trips/${tripId}/invitations`, { token })
}

/** Owner-only: withdraws a pending invitation. */
export function cancelInvitation(
  token: string | null,
  tripId: string,
  invitationId: string,
): Promise<void> {
  return request<void>(`/api/trips/${tripId}/invitations/${invitationId}`, {
    method: 'DELETE',
    token,
  })
}

/** Current user's pending invitations (drives the navbar red dot). */
export function getMyInvitations(token: string | null): Promise<TripInvitation[]> {
  return request<TripInvitation[]>('/api/users/me/invitations', { token })
}

/** Accept or decline an invitation addressed to the current user. */
export function respondToInvitation(
  token: string | null,
  invitationId: string,
  status: 'ACCEPTED' | 'DECLINED',
): Promise<TripInvitation> {
  return request<TripInvitation>(`/api/users/me/invitations/${invitationId}`, {
    method: 'PATCH',
    body: { status },
    token,
  })
}

/**
 * Searches OpenStreetMap places by free text. `limit` is clamped by the backend
 * to 1..10. Callers should debounce to respect the Nominatim usage policy.
 */
export function searchPlaces(
  token: string | null,
  query: string,
  limit = 5,
): Promise<GeoSearchResponse> {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return request<GeoSearchResponse>(`/api/geo/search?${params.toString()}`, { token })
}

/**
 * Imports an OpenStreetMap place as a stored {@link Location}. Idempotent per
 * `(osmType, osmId)`, so importing a place twice returns the same location.
 */
export function importLocationFromOsm(
  token: string | null,
  input: Pick<GeoPlace, 'osmType' | 'osmId'>,
): Promise<Location> {
  return request<Location>('/api/locations/from-osm', {
    method: 'POST',
    body: { osmType: input.osmType, osmId: input.osmId },
    token,
  })
}

export interface CreateLocationInput {
  name: string
  latitude: number
  longitude: number
}

/**
 * For manual entry of Locations
 */
export function createLocation(
  token: string | null,
  input: CreateLocationInput,
): Promise<Location> {
  return request<Location>('/api/locations', {
    method: 'POST',
    body: input,
    token,
  })
}

/** Fetches the four-day weather forecast from the backend. */
export function getFourDayWeatherForecast(
  token: string | null,
): Promise<FourDayWeatherForecast[]> {
  return request<FourDayWeatherForecast[]>('/api/weather/four-day', { token })
}

/** Fetches the two-hour weather forecast for Singapore. */
export function getTwoHourWeatherForecast(
  token: string | null,
): Promise<TwoHourWeatherForecast[]> {
  return request<TwoHourWeatherForecast[]>('/api/weather/two-hour', { token })
}