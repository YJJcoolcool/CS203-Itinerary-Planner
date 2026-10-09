import { useAuth, useUser } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import { publishableKey } from '../auth/authConfiguration'
import type {
  Activity,
  CreateActivityInput,
  CreateTripInput,
  Location,
  Trip,
  User,
} from '../types/trip'
import {
  ApiError,
  createActivity as createActivityRequest,
  createBackendUser,
  createTrip as createTripRequest,
  getTrip,
  getTrips,
  getActivities,
  getActivityLocations,
} from './api'

/** Result of `useCurrentUser()`. */
export interface UseCurrentUserResult {
  user: User | null
  isLoading: boolean
  error: string | null
}

/** Result of `useTrips()`. */
export interface UseTripsResult {
  trips: Trip[]
  isLoading: boolean
  error: string | null
  refresh: () => void
}

/** Result of `useTrip(tripId)`. */
export interface UseTripResult {
  trip: Trip | null
  isLoading: boolean
  error: string | null
  /** HTTP status of the failed request (e.g. 404), or null. */
  errorStatus: number | null
  refresh: () => void
}

/** Result of `useTripItinerary(tripId)`. */
export interface UseTripItineraryResult {
  activities: Activity[]
  /**
   * Locations keyed by activity id. A missing key means that activity's
   * locations failed to load; an empty array means it has none.
   */
  locationsByActivity: Record<string, Location[]>
  isLoading: boolean
  isLoadingLocations: boolean
  error: string | null
  refresh: () => void
}

/** Result of `useCreateTrip()`. */
export interface UseCreateTripResult {
  createTrip: (input: CreateTripInput) => Promise<Trip>
  isCreating: boolean
  error: string | null
}

/** Result of useCreateActivity(). */
export interface UseCreateActivityResult {
  createActivity: (input: CreateActivityInput) => Promise<Activity>
  isCreating: boolean
  error: string | null
}

/**
 * Whether Clerk is configured. Mirrors `AuthProvider`: without a publishable
 * key there is no `<ClerkProvider>`, so Clerk hooks would throw if called.
 * This value is baked in at build time and cannot change while the app runs,
 * which is why selecting an implementation at module load is render-safe.
 */
const CLERK_CONFIGURED = Boolean(publishableKey)

const NOT_CONFIGURED_MESSAGE =
  'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'

const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'

/** Structural subset of Clerk's user, so this stays decoupled from its types. */
interface ClerkUserLike {
  fullName: string | null
  primaryEmailAddress: { emailAddress: string } | null
}

function deriveDisplayName(user: ClerkUserLike | null | undefined): string {
  return user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? 'New user'
}

function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong. Please try again.'
}

function useConfiguredCurrentUser(): UseCurrentUserResult {
  const { getToken } = useAuth()
  const { user: clerkUser, isLoaded: isUserLoaded } = useUser()

  const [resolved, setResolved] = useState<{ clerkUserId: string; user: User } | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const clerkUserId = clerkUser?.id ?? null
  const displayName = deriveDisplayName(clerkUser)

  useEffect(() => {
    if (!isUserLoaded || !clerkUserId) return

    // Capture the narrowed id: the guard above does not narrow `clerkUserId`
    // inside the async closure below.
    const currentClerkUserId = clerkUserId

    let cancelled = false

    async function sync() {
      setIsLoading(true)
      setError(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        // POST /api/users/me is find-or-create, so this also covers the 404 a
        // bare GET /api/users/me returns before the row exists.
        const user = await createBackendUser(token, displayName)
        if (!cancelled) setResolved({ clerkUserId: currentClerkUserId, user })
      } catch (cause) {
        if (!cancelled) setError(describeError(cause))
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void sync()

    return () => {
      cancelled = true
    }
  }, [clerkUserId, displayName, getToken, isUserLoaded])

  if (!isUserLoaded) return { user: null, isLoading: true, error: null }
  if (!clerkUserId) return { user: null, isLoading: false, error: null }

  // Only expose a user resolved for the currently signed-in Clerk account.
  const currentUser = resolved?.clerkUserId === clerkUserId ? resolved.user : null
  return { user: currentUser, isLoading, error }
}

function useConfiguredTrips(): UseTripsResult {
  const { getToken, isLoaded, isSignedIn, userId } = useAuth()

  const [trips, setTrips] = useState<Trip[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    if (!isLoaded || !isSignedIn || !userId) return

    let cancelled = false

    async function load() {
      setIsLoading(true)
      setError(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const result = await getTrips(token)
        if (!cancelled) setTrips(result)
      } catch (cause) {
        if (!cancelled) setError(describeError(cause))
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [getToken, isLoaded, isSignedIn, reloadCount, userId])

  const refresh = useCallback(() => {
    setReloadCount((count) => count + 1)
  }, [])

  if (!isLoaded) return { trips: [], isLoading: true, error: null, refresh }
  if (!isSignedIn || !userId) return { trips: [], isLoading: false, error: null, refresh }
  return { trips, isLoading, error, refresh }
}

function useConfiguredTrip(tripId: string): UseTripResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [trip, setTrip] = useState<Trip | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [errorStatus, setErrorStatus] = useState<number | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return

    let cancelled = false

    async function load() {
      setIsLoading(true)
      setError(null)
      setErrorStatus(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const result = await getTrip(token, tripId)
        if (!cancelled) setTrip(result)
      } catch (cause) {
        if (!cancelled) {
          setError(describeError(cause))
          setErrorStatus(cause instanceof ApiError ? cause.status : null)
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [getToken, isLoaded, isSignedIn, reloadCount, tripId])

  const refresh = useCallback(() => {
    setReloadCount((count) => count + 1)
  }, [])

  if (!isLoaded) return { trip: null, isLoading: true, error: null, errorStatus: null, refresh }
  // Only expose the trip that matches the requested id, never a stale one.
  const current = trip?.id === tripId ? trip : null
  return { trip: current, isLoading, error, errorStatus, refresh }
}

interface ItineraryState {
  tripId: string
  activities: Activity[]
  locationsByActivity: Record<string, Location[]>
}

function useConfiguredTripItinerary(tripId: string): UseTripItineraryResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [state, setState] = useState<ItineraryState | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingLocations, setIsLoadingLocations] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return

    let cancelled = false

    async function load() {
      setIsLoading(true)
      setIsLoadingLocations(false)
      setError(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        // Step 1: the activities themselves.
        const activities = await getActivities(token, tripId)
        if (cancelled) return
        setState({ tripId, activities, locationsByActivity: {} })
        setIsLoading(false)

        // Step 2: once the activities are in, fetch every activity's
        // locations in parallel. allSettled keeps one failure from hiding
        // the locations of all the other activities.
        if (activities.length === 0) return
        setIsLoadingLocations(true)

        const results = await Promise.allSettled(
          activities.map((activity) => getActivityLocations(token, tripId, activity.id)),
        )
        if (cancelled) return

        const locationsByActivity: Record<string, Location[]> = {}
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') {
            locationsByActivity[activities[index].id] = result.value
          }
        })
        setState({ tripId, activities, locationsByActivity })
      } catch (cause) {
        if (!cancelled) setError(describeError(cause))
      } finally {
        if (!cancelled) {
          setIsLoading(false)
          setIsLoadingLocations(false)
        }
      }
    }

    void load()

    return () => {
      cancelled = true
    }
  }, [getToken, isLoaded, isSignedIn, reloadCount, tripId])

  const refresh = useCallback(() => {
    setReloadCount((count) => count + 1)
  }, [])

  // Only expose data loaded for the requested trip, never a stale one.
  const current = state?.tripId === tripId ? state : null
  return {
    activities: current?.activities ?? EMPTY_ACTIVITIES,
    locationsByActivity: current?.locationsByActivity ?? EMPTY_LOCATIONS,
    isLoading: !isLoaded || isLoading,
    isLoadingLocations,
    error,
    refresh,
  }
}

function useConfiguredCreateTrip(): UseCreateTripResult {
  const { getToken } = useAuth()
  const { user: clerkUser } = useUser()

  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const displayName = deriveDisplayName(clerkUser)

  const create = useCallback(
    async (input: CreateTripInput): Promise<Trip> => {
      setIsCreating(true)
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        // Resolve the backend user id here so callers only supply the trip
        // fields. find-or-create is idempotent and covers a missing user row.
        const backendUser = await createBackendUser(token, displayName)

        return await createTripRequest(token, {
          ...input,
          ownerId: backendUser.id,
        })
      } catch (cause) {
        setError(describeError(cause))
        throw cause instanceof Error ? cause : new Error(describeError(cause))
      } finally {
        setIsCreating(false)
      }
    },
    [displayName, getToken],
  )

  return { createTrip: create, isCreating, error }
}

function useConfiguredCreateActivity(
  tripId: string,
): UseCreateActivityResult {
  const { getToken } = useAuth()

  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useCallback(
    async (input: CreateActivityInput): Promise<Activity> => {
      setIsCreating(true)
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        return await createActivityRequest(token, tripId, input)
      } catch (cause) {
        setError(describeError(cause))
        throw cause instanceof Error
          ? cause
          : new Error(describeError(cause))
      } finally {
        setIsCreating(false)
      }
    },
    [getToken, tripId],
  )

  return { createActivity: create, isCreating, error }
}

function useUnconfiguredCurrentUser(): UseCurrentUserResult {
  return { user: null, isLoading: false, error: NOT_CONFIGURED_MESSAGE }
}

const EMPTY_TRIPS: Trip[] = []
const EMPTY_ACTIVITIES: Activity[] = []
const EMPTY_LOCATIONS: Record<string, Location[]> = {}

function useUnconfiguredTrips(): UseTripsResult {
  const refresh = useCallback(() => {
    // Nothing to refresh while authentication is unconfigured.
  }, [])

  return {
    trips: EMPTY_TRIPS,
    isLoading: false,
    error: NOT_CONFIGURED_MESSAGE,
    refresh,
  }
}

function useUnconfiguredTrip(): UseTripResult {
  const refresh = useCallback(() => {
    // Nothing to refresh while authentication is unconfigured.
  }, [])

  return { trip: null, isLoading: false, error: NOT_CONFIGURED_MESSAGE, errorStatus: null, refresh }
}

function useUnconfiguredTripItinerary(): UseTripItineraryResult {
  const refresh = useCallback(() => {
    // Nothing to refresh while authentication is unconfigured.
  }, [])

  return {
    activities: EMPTY_ACTIVITIES,
    locationsByActivity: EMPTY_LOCATIONS,
    isLoading: false,
    isLoadingLocations: false,
    error: NOT_CONFIGURED_MESSAGE,
    refresh,
  }
}

function rejectWithNotConfigured(): Promise<Trip> {
  return Promise.reject(new ApiError(0, NOT_CONFIGURED_MESSAGE))
}

function useUnconfiguredCreateTrip(): UseCreateTripResult {
  return {
    createTrip: rejectWithNotConfigured,
    isCreating: false,
    error: NOT_CONFIGURED_MESSAGE,
  }
}

function rejectActivityWithNotConfigured(): Promise<Activity> {
  return Promise.reject(new ApiError(0, NOT_CONFIGURED_MESSAGE))
}

function useUnconfiguredCreateActivity(): UseCreateActivityResult {
  return {
    createActivity: rejectActivityWithNotConfigured,
    isCreating: false,
    error: NOT_CONFIGURED_MESSAGE,
  }
}

/**
 * Resolves the current backend user, creating the row on first sign-in.
 *
 * The configured/unconfigured implementation is selected once at module load
 * (the publishable key is a build-time constant), so no component ever changes
 * its hook order between renders.
 */
export const useCurrentUser: () => UseCurrentUserResult = CLERK_CONFIGURED
  ? useConfiguredCurrentUser
  : useUnconfiguredCurrentUser

/**
 * Loads every trip from the backend. The list is intentionally NOT filtered
 * here — the page filters with `tripsForUser` using the current user id.
 */
export const useTrips: () => UseTripsResult = CLERK_CONFIGURED
  ? useConfiguredTrips
  : useUnconfiguredTrips

/** Loads a single trip by id for the trip details page. */
export const useTrip: (tripId: string) => UseTripResult = CLERK_CONFIGURED
  ? useConfiguredTrip
  : useUnconfiguredTrip

/**
 * Loads a trip's activities, then each activity's locations, for the
 * itinerary section of the trip details page.
 */
export const useTripItinerary: (tripId: string) => UseTripItineraryResult = CLERK_CONFIGURED
  ? useConfiguredTripItinerary
  : useUnconfiguredTripItinerary
  
/**
 * Creates a trip, resolving the owner via `POST /api/users/me` internally.
 */
export const useCreateTrip: () => UseCreateTripResult = CLERK_CONFIGURED
  ? useConfiguredCreateTrip
  : useUnconfiguredCreateTrip

/** Creates an activity in the requested trip. */
export const useCreateActivity: (tripId: string) => UseCreateActivityResult = CLERK_CONFIGURED
  ? useConfiguredCreateActivity
  : useUnconfiguredCreateActivity