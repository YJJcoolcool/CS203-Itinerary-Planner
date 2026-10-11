import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import { publishableKey } from '../../auth/authConfiguration'
import type { Activity, Location, Trip } from '../../types/trip'
import { ApiError, getActivities, getActivityLocations, getTrip, getTrips } from '../api'

export interface UseTripsResult { trips: Trip[]; isLoading: boolean; error: string | null; refresh: () => void }
export interface UseTripResult { trip: Trip | null; isLoading: boolean; error: string | null; errorStatus: number | null; refresh: () => void }
export interface UseTripItineraryResult { activities: Activity[]; locationsByActivity: Record<string, Location[]>; isLoading: boolean; isLoadingLocations: boolean; error: string | null; refresh: () => void }
const CLERK_CONFIGURED = Boolean(publishableKey)
const NOT_CONFIGURED_MESSAGE = 'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'
function describeError(error: unknown): string { if (error instanceof ApiError) return error.message; if (error instanceof Error && error.message) return error.message; return 'Something went wrong. Please try again.' }
const EMPTY_TRIPS: Trip[] = []
const EMPTY_ACTIVITIES: Activity[] = []
const EMPTY_LOCATIONS: Record<string, Location[]> = {}
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

