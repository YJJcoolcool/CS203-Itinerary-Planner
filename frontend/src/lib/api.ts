import type { CreateTripInput, Trip, User } from '../types/trip'

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

/** Fetches every trip. The endpoint is not user-scoped — filter with `tripsForUser`. */
export function getTrips(token: string | null): Promise<Trip[]> {
  return request<Trip[]>('/api/trips', { token })
}

/** Creates a trip owned by `ownerId`. */
export function createTrip(
  token: string | null,
  input: CreateTripInput & { ownerId: number },
): Promise<Trip> {
  return request<Trip>('/api/trips', {
    method: 'POST',
    body: input,
    token,
  })
}

/** True when `userId` owns the trip or appears in its member list. */
export function isUserMemberOfTrip(trip: Trip, userId: number | undefined): boolean {
  if (userId === undefined) return false
  if (trip.owner.id === userId) return true
  return trip.users.some((member) => member.id === userId)
}

/**
 * Client-side filter for the current user. Required because `GET /api/trips`
 * returns all trips regardless of membership.
 */
export function tripsForUser(trips: Trip[], userId: number | undefined): Trip[] {
  if (userId === undefined) return []
  return trips.filter((trip) => isUserMemberOfTrip(trip, userId))
}

/** Fetches a single trip. Throws `ApiError` with status 404 when it does not exist. */
export function getTrip(token: string | null, tripId: number): Promise<Trip> {
  return request<Trip>(`/api/trips/${tripId}`, { token })
}