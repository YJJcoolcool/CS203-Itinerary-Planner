import { useAuth } from '@clerk/react'
import { useCallback, useState } from 'react'
import { publishableKey } from '../../auth/authConfiguration'
import type { GeoPlace, Location } from '../../types/trip'
import {
  ApiError,
  createLocation as createLocationRequest,
  importLocationFromOsm as importLocationFromOsmRequest,
  type CreateLocationInput,
} from '../api'

export interface UseImportLocationResult {
  importLocation: (place: Pick<GeoPlace, 'osmType' | 'osmId'>) => Promise<Location>
  importingKey: string | null
  error: string | null
}
export interface UseCreateLocationResult {
  createLocation: (input: CreateLocationInput) => Promise<Location>
  isCreating: boolean
  error: string | null
}

const CLERK_CONFIGURED = Boolean(publishableKey)
const NOT_CONFIGURED_MESSAGE =
  'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'

function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return 'Something went wrong. Please try again.'
}

function osmKey(osmType: string, osmId: number): string {
  return `${osmType}:${osmId}`
}

function useConfiguredImportLocation(): UseImportLocationResult {
  const { getToken } = useAuth()

  const [importingKey, setImportingKey] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const importLocation = useCallback(
    async (place: Pick<GeoPlace, 'osmType' | 'osmId'>): Promise<Location> => {
      const key = osmKey(place.osmType, place.osmId)
      setImportingKey(key)
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        return await importLocationFromOsmRequest(token, place)
      } catch (cause) {
        setError(describeError(cause))
        throw cause instanceof Error ? cause : new Error(describeError(cause))
      } finally {
        // Only clear if this import still owns the indicator, so a slower
        // concurrent import is not un-marked by a faster one finishing.
        setImportingKey((current) => (current === key ? null : current))
      }
    },
    [getToken],
  )

  return { importLocation, importingKey, error }
}


function rejectImportWithNotConfigured(): Promise<Location> {
  return Promise.reject(new ApiError(0, NOT_CONFIGURED_MESSAGE))
}

function useUnconfiguredImportLocation(): UseImportLocationResult {
  return {
    importLocation: rejectImportWithNotConfigured,
    importingKey: null,
    error: NOT_CONFIGURED_MESSAGE,
  }
}


function useConfiguredCreateLocation(): UseCreateLocationResult {
  const { getToken } = useAuth()
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useCallback(async (input: CreateLocationInput): Promise<Location> => {
    setIsCreating(true)
    setError(null)
    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)
      return await createLocationRequest(token, input)
    } catch (cause) {
      setError(describeError(cause))
      throw cause instanceof Error ? cause : new Error(describeError(cause))
    } finally {
      setIsCreating(false)
    }
  }, [getToken])

  return { createLocation: create, isCreating, error }
}

function rejectLocationWithNotConfigured(): Promise<Location> {
  return Promise.reject(new ApiError(0, NOT_CONFIGURED_MESSAGE))
}

function useUnconfiguredCreateLocation(): UseCreateLocationResult {
  return {
    createLocation: rejectLocationWithNotConfigured,
    isCreating: false,
    error: NOT_CONFIGURED_MESSAGE,
  }
}

export const useImportLocationFromOsm: () => UseImportLocationResult = CLERK_CONFIGURED
  ? useConfiguredImportLocation
  : useUnconfiguredImportLocation

export const useCreateLocation: () => UseCreateLocationResult = CLERK_CONFIGURED
  ? useConfiguredCreateLocation
  : useUnconfiguredCreateLocation
