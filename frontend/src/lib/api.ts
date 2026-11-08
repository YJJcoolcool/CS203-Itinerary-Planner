import type { Friend, FriendRequest, Friendship } from '../types/friend'
import type { TripInvitation, TripRole } from '../types/invitation'
import type {
  TripPreferences,
  UpdateTripPreferencesInput,
  UpdateUserPreferencesInput,
  UserPreferences,
} from '../types/preference'
import type { Suggestion } from '../types/suggestion'
import type {
  Activity,
  CreateActivityInput,
  CreateTripInput,
  FourDayWeatherForecast,
  GeoPlace,
  GeoSearchResponse,
  Location,
  RouteMode,
  RouteResponse,
  Trip,
  TwoHourWeatherForecast,
  User,
  WeatherOutlookDay,
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

/** Basemap configuration for a region, as returned by `GET /api/regions`. */
export interface RegionTiles {
  url: string
  attribution: string
  crs: string
}

/** A destination region and the capabilities the backend supports there. */
export interface RegionInfo {
  code: string
  name: string
  tier: string
  locale: string
  currency: string | null
  defaultZone: string
  capabilities: string[]
  tiles: RegionTiles | null
}

/** Extract a human-readable message from an error response body. */
async function readErrorMessage(response: Response): Promise<string> {
  const fallback =
    response.status === 429
      ? 'Too many requests. Please wait a moment and try again.'
      : `Request failed with status ${response.status}.`

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

/** Fetches the configured destination regions and their capabilities. */
export function getRegions(token: string | null): Promise<RegionInfo[]> {
  return request<RegionInfo[]>('/api/regions', { token })
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

/** Updates a trip's name, dates, and country. Editor or owner only. */
export function updateTrip(
  token: string | null,
  tripId: string,
  input: {
    name: string
    startDate: string
    endDate: string
    countryCode: string
    coverImageUrl: string | null
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

/** Changes a member's role to Editor or Viewer. Owner-only. Returns the updated trip. */
export function changeMemberRole(
  token: string | null,
  tripId: string,
  userId: string,
  role: TripRole,
): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}/members/${userId}`, {
    method: 'PUT',
    body: { role },
    token,
  })
}

/**
 * Removes a member from a trip. The owner can remove anyone else; any member
 * can pass their own id to leave the trip (CGT-167). The owner can't be
 * removed or leave. Returns the updated trip.
 */
export function removeMember(
  token: string | null,
  tripId: string,
  userId: string,
): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}/members/${userId}`, {
    method: 'DELETE',
    token,
  })
}

/**
 * Hands the trip to another member (CGT-168). Owner-only; the previous owner
 * becomes an editor. Returns the updated trip.
 */
export function transferOwnership(
  token: string | null,
  tripId: string,
  userId: string,
): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}/owner`, {
    method: 'PUT',
    body: { userId },
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

/** Updates an activity's name, times and locations. Editor or owner only. */
export function updateActivity(
  token: string | null,
  tripId: string,
  activityId: string,
  input: CreateActivityInput,
): Promise<Activity> {
  return request<Activity>(`/api/trips/${tripId}/activities/${activityId}`, {
    method: 'PUT',
    body: input,
    token,
  })
}

/** Deletes an activity from a trip. Editor or owner only. */
export function deleteActivity(
  token: string | null,
  tripId: string,
  activityId: string,
): Promise<void> {
  return request<void>(`/api/trips/${tripId}/activities/${activityId}`, {
    method: 'DELETE',
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

/** Fetches a route between two consecutive activities. */
export function getActivityRoute(
  token: string | null,
  tripId: string,
  fromActivityId: string,
  toActivityId: string,
  mode: RouteMode,
  departureDateTime: string,
): Promise<RouteResponse> {
  const params = new URLSearchParams({
    mode,
    departureDateTime,
  })

  return request<RouteResponse>(
    `/api/trips/${encodeURIComponent(tripId)}/activities/${encodeURIComponent(fromActivityId)}/route-to/${encodeURIComponent(toActivityId)}?${params.toString()}`,
    { token },
  )
}

/** Owner invites an existing user (by email) to a trip. */
export function sendInvitation(
  token: string | null,
  tripId: string,
  email: string,
  role: TripRole,
): Promise<TripInvitation> {
  return request<TripInvitation>(`/api/trips/${tripId}/invitations`, {
    method: 'POST',
    body: { email, role },
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

/** Current user's accepted friends, sorted by name. */
export function getFriends(token: string | null): Promise<Friend[]> {
  return request<Friend[]>('/api/users/me/friends', { token })
}

/** Pending friend requests addressed to the current user, newest first. */
export function getFriendRequests(token: string | null): Promise<FriendRequest[]> {
  return request<FriendRequest[]>('/api/users/me/friend-requests', { token })
}

/** Sends a friend request; accepts theirs instead if they already asked. */
export function sendFriendRequest(token: string | null, userId: string): Promise<Friendship> {
  return request<Friendship>('/api/users/me/friend-requests', {
    method: 'POST',
    body: { userId },
    token,
  })
}

/** Accepts or rejects a friend request addressed to the current user. */
export function respondToFriendRequest(
  token: string | null,
  requestId: string,
  accept: boolean,
): Promise<void> {
  return request<void>(`/api/users/me/friend-requests/${requestId}`, {
    method: 'PATCH',
    body: { accept },
    token,
  })
}

/** How the current user relates to another user (drives the profile's friend button). */
export function getFriendship(token: string | null, userId: string): Promise<Friendship> {
  return request<Friendship>(`/api/users/${userId}/friendship`, { token })
}

/**
 * Searches OpenStreetMap places by free text. `limit` is clamped by the backend
 * to 1..10. Callers should debounce to respect the Nominatim usage policy.
 */
export function searchPlaces(
  token: string | null,
  query: string,
  tripId: string,
  limit = 5,
): Promise<GeoSearchResponse> {
  const params = new URLSearchParams({
    q: query,
    limit: String(limit),
    tripId,
  })

  return request<GeoSearchResponse>(
    `/api/geo/search?${params.toString()}`,
    { token },
  )
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

/** Indoor/outdoor choice for a manual place; null means "not sure" (CGT-141). */
export type ManualIndoorClass = 'INDOOR' | 'OUTDOOR' | null

export interface CreateLocationInput {
  name: string
  latitude: number
  longitude: number
  indoorClass: ManualIndoorClass
}

/**
 * For manual entry of Locations. The place is private to `tripId`: only that
 * trip's members can see or use it (CGT-142). Editor or owner only.
 */
export function createLocation(
  token: string | null,
  tripId: string,
  input: CreateLocationInput,
): Promise<Location> {
  return request<Location>(`/api/trips/${tripId}/locations`, {
    method: 'POST',
    body: input,
    token,
  })
}

/**
 * Deletes a place added by hand (CGT-172). Editor or owner of its trip only.
 * Fails with 409 while an activity still uses the place, and 403 for shared places.
 */
export function deleteLocation(token: string | null, locationId: string): Promise<void> {
  return request<void>(`/api/locations/${locationId}`, {
    method: 'DELETE',
    token,
  })
}

/** Fetches all suggestions for a trip, including already answered ones. */
export function getSuggestions(token: string | null, tripId: string): Promise<Suggestion[]> {
  return request<Suggestion[]>(`/api/trips/${tripId}/suggestions`, { token })
}

/** Asks the backend to check the weather and propose new alternatives. */
export function replanTrip(token: string | null, tripId: string): Promise<Suggestion[]> {
  return request<Suggestion[]>(`/api/trips/${tripId}/replan`, {
    method: 'POST',
    token,
  })
}

/**
 * Accepts one suggestion and applies the chosen location to the activity.
 * Omitting `locationId` keeps the backend's default of using the top option.
 */
export function acceptSuggestion(
  token: string | null,
  suggestionId: string,
  locationId?: string,
): Promise<Suggestion> {
  return request<Suggestion>(`/api/suggestions/${suggestionId}/accept`, {
    method: 'POST',
    body: locationId === undefined ? undefined : { locationId },
    token,
  })
}

/** Dismisses one suggestion without changing the itinerary. */
export function rejectSuggestion(
  token: string | null,
  suggestionId: string,
): Promise<Suggestion> {
  return request<Suggestion>(`/api/suggestions/${suggestionId}/reject`, {
    method: 'POST',
    token,
  })
}

/**
 * Reverses an applied suggestion, putting the original place back (CGT-169).
 * Editor or owner only; 409 once the activity has changed since it was applied.
 */
export function undoSuggestion(
  token: string | null,
  suggestionId: string,
): Promise<Suggestion> {
  return request<Suggestion>(`/api/suggestions/${suggestionId}/undo`, {
    method: 'POST',
    token,
  })
}

/** Fetches the signed-in user's travel preferences. */
export function getMyPreferences(token: string | null): Promise<UserPreferences> {
  return request<UserPreferences>('/api/users/me/preferences', { token })
}

/** Saves the signed-in user's travel preferences. */
export function updateMyPreferences(
  token: string | null,
  input: UpdateUserPreferencesInput,
): Promise<UserPreferences> {
  return request<UserPreferences>('/api/users/me/preferences', {
    method: 'PUT',
    body: input,
    token,
  })
}

/** Regenerates the signed-in user's summary from their preferences and trips. */
export function summariseMyPreferences(token: string | null): Promise<UserPreferences> {
  return request<UserPreferences>('/api/users/me/preferences/summarise', {
    method: 'POST',
    token,
  })
}

/** Generates a summary preview without saving it. */
export async function previewMyPreferencesSummary(
  token: string | null,
): Promise<string> {
  const response = await request<{ summary: string }>(
    '/api/users/me/preferences/summarise/preview',
    {
      method: 'POST',
      token,
    },
  )

  return response.summary
}

/** Saves the signed-in user's own edit of their summary; null clears it. */
export function updateMySummary(
  token: string | null,
  summary: string | null,
): Promise<UserPreferences> {
  return request<UserPreferences>('/api/users/me/preferences/summary', {
    method: 'PUT',
    body: { summary },
    token,
  })
}

/**
 * Fetches another user's preferences. Structured fields only: the backend
 * always returns `notes: null` on this view.
 */
export function getUserPreferences(
  token: string | null,
  userId: string,
): Promise<UserPreferences> {
  return request<UserPreferences>(`/api/users/${userId}/preferences`, { token })
}

/** Fetches a trip's shared group preference. Every member can read it. */
export function getTripPreferences(
  token: string | null,
  tripId: string,
): Promise<TripPreferences> {
  return request<TripPreferences>(`/api/trips/${tripId}/preferences`, { token })
}

/** Saves a trip's group goals. Owner or editor only. */
export function updateTripPreferences(
  token: string | null,
  tripId: string,
  input: UpdateTripPreferencesInput,
): Promise<TripPreferences> {
  return request<TripPreferences>(`/api/trips/${tripId}/preferences`, {
    method: 'PUT',
    body: input,
    token,
  })
}

/** Regenerates a trip's group summary from its goals and members. Owner or editor only. */
export function summariseTripPreferences(
  token: string | null,
  tripId: string,
): Promise<TripPreferences> {
  return request<TripPreferences>(`/api/trips/${tripId}/preferences/summarise`, {
    method: 'POST',
    token,
  })
}

/** Generates a group summary preview without saving it. */
export async function previewTripSummary(
  token: string | null,
  tripId: string,
): Promise<string> {
  const response = await request<{ summary: string }>(
    `/api/trips/${tripId}/preferences/summarise/preview`,
    {
      method: 'POST',
      token,
    },
  )

  return response.summary
}

/** Saves an edited group summary. Owner or editor only. */
export function updateTripSummary(
  token: string | null,
  tripId: string,
  summary: string | null,
): Promise<TripPreferences> {
  return request<TripPreferences>(
    `/api/trips/${tripId}/preferences/summary`,
    {
      method: 'PUT',
      body: { summary },
      token,
    },
  )
}

/** Fetches the four-day weather forecast from the backend. */
export function getFourDayWeatherForecast(
  token: string | null,
): Promise<FourDayWeatherForecast[]> {
  return request<FourDayWeatherForecast[]>('/api/weather/four-day', { token })
}

/** Fetches the two-hour weather forecast. */
export function getTwoHourWeatherForecast(
  token: string | null,
): Promise<TwoHourWeatherForecast[]> {
  return request<TwoHourWeatherForecast[]>('/api/weather/two-hour', { token })
}

/**
 * Fetches the day-by-day point forecast for a trip. Empty when the destination
 * has no weather data; callers should hide the panel in that case.
 */
export function getWeatherOutlook(
  token: string | null,
  tripId: string,
): Promise<WeatherOutlookDay[]> {
  return request<WeatherOutlookDay[]>(
    `/api/trips/${encodeURIComponent(tripId)}/weather/outlook`,
    { token },
  )
}
