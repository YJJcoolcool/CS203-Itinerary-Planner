import { useAuth } from '@clerk/react'
import { useCallback, useState } from 'react'
import { publishableKey } from '../../auth/authConfiguration'
import type { Activity, CreateActivityInput, CreateTripInput, Trip } from '../../types/trip'
import { ApiError, createActivity as createActivityRequest, createTrip as createTripRequest } from '../api'

export interface UseCreateTripResult { createTrip: (input: CreateTripInput) => Promise<Trip>; isCreating: boolean; error: string | null }
export interface UseCreateActivityResult { createActivity: (input: CreateActivityInput) => Promise<Activity>; isCreating: boolean; error: string | null }
const CLERK_CONFIGURED = Boolean(publishableKey)
const NOT_CONFIGURED_MESSAGE = 'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'
function describeError(error: unknown): string { if (error instanceof ApiError) return error.message; if (error instanceof Error && error.message) return error.message; return 'Something went wrong. Please try again.' }
function useConfiguredCreateTrip(): UseCreateTripResult {
  const { getToken } = useAuth()

  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useCallback(
    async (input: CreateTripInput): Promise<Trip> => {
      setIsCreating(true)
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        // Resolve the backend user id here so callers only supply the trip
        // fields. find-or-create is idempotent and covers a missing user row.

        return await createTripRequest(token, {
          ...input,
        })
      } catch (cause) {
        setError(describeError(cause))
        throw cause instanceof Error ? cause : new Error(describeError(cause))
      } finally {
        setIsCreating(false)
      }
    },
    [getToken],
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
 * Minimum pause between keystrokes and a search request. The Nominatim usage
 * policy forbids firing a request per keystroke, so this is intentionally
 * generous (>= 750ms).
 */

export const useCreateTrip: () => UseCreateTripResult = CLERK_CONFIGURED
  ? useConfiguredCreateTrip
  : useUnconfiguredCreateTrip

/** Creates an activity in the requested trip. */
export const useCreateActivity: (tripId: string) => UseCreateActivityResult = CLERK_CONFIGURED
  ? useConfiguredCreateActivity
  : useUnconfiguredCreateActivity

/**
 * Debounced OpenStreetMap place search for the activity location picker. Fires
 * at most one request per 750ms pause to respect the Nominatim usage policy.
 */

