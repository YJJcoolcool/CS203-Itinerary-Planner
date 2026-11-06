import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import { publishableKey } from '../auth/authConfiguration'
import type {
  TripPreferences,
  UpdateTripPreferencesInput,
  UpdateUserPreferencesInput,
  UserPreferences,
} from '../types/preference'
import {
  ApiError,
  getMyPreferences,
  getTripPreferences,
  summariseMyPreferences,
  previewMyPreferencesSummary,
  summariseTripPreferences,
  updateMyPreferences,
  updateMySummary,
  updateTripPreferences,
} from './api'

const CLERK_CONFIGURED = Boolean(publishableKey)
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'

function describeError(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message) return cause.message
  return fallback
}

/** Result of `useMyPreferences()`. */
export interface UseMyPreferencesResult {
  preferences: UserPreferences | null
  isLoading: boolean
  /** Failure to load the preferences (the last good copy is kept). */
  error: string | null
  isSaving: boolean
  /** Failure to save; cleared when a new save starts. */
  saveError: string | null
  /** Regenerates the summary from the user's preferences and trips. */
  summarise: () => Promise<UserPreferences>
  /** Generates a summary preview without updating the saved preferences. */
  previewSummary: () => Promise<string>
  /** Saves and returns the server copy. Rejects with the API error. */
  save: (input: UpdateUserPreferencesInput) => Promise<UserPreferences>
  /** Saves the user's own wording of their summary; null clears it. */
  saveSummary: (summary: string | null) => Promise<UserPreferences>
  refresh: () => void
}

function useConfiguredMyPreferences(): UseMyPreferencesResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [preferences, setPreferences] = useState<UserPreferences | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  const refresh = useCallback(() => setReloadCount((count) => count + 1), [])

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      setIsLoading(true)
      setError(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const result = await getMyPreferences(token)
        if (!cancelled) setPreferences(result)
      } catch (cause) {
        if (!cancelled) {
          setError(describeError(cause, 'Could not load your travel preferences.'))
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [getToken, isLoaded, isSignedIn, reloadCount])

  // Shared by every write: one busy flag and one error, and the server copy replaces ours.
  const run = useCallback(
    async (action: (token: string) => Promise<UserPreferences>, fallback: string) => {
      setIsSaving(true)
      setSaveError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const updated = await action(token)
        setPreferences(updated)
        return updated
      } catch (cause) {
        setSaveError(describeError(cause, fallback))
        throw cause
      } finally {
        setIsSaving(false)
      }
    },
    [getToken],
  )

  const summarise = useCallback(
    () => run((token) => summariseMyPreferences(token), 'Could not generate your summary.'),
    [run],
  )

  const previewSummary = useCallback(async () => {
    setIsSaving(true)
    setSaveError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

      return await previewMyPreferencesSummary(token)
    } catch (cause) {
      setSaveError(
        describeError(cause, 'Could not generate your summary preview.'),
      )
      throw cause
    } finally {
      setIsSaving(false)
    }
  }, [getToken])

  const save = useCallback(
    (input: UpdateUserPreferencesInput) =>
      run((token) => updateMyPreferences(token, input), 'Could not save your travel preferences.'),
    [run],
  )

  const saveSummary = useCallback(
    (summary: string | null) =>
      run((token) => updateMySummary(token, summary), 'Could not save your summary.'),
    [run],
  )

  const actions = { summarise, previewSummary, save, saveSummary, refresh }

  if (!isLoaded) {
    return { preferences, isLoading: true, error: null, isSaving: false, saveError: null, ...actions }
  }

  if (!isSignedIn) {
    return { preferences: null, isLoading: false, error: null, isSaving: false, saveError: null, ...actions }
  }

  return { preferences, isLoading, error, isSaving, saveError, ...actions }
}

function useUnconfiguredMyPreferences(): UseMyPreferencesResult {
  return {
    preferences: null,
    isLoading: false,
    error: null,
    isSaving: false,
    saveError: null,
    summarise: () => Promise.reject(new ApiError(0, 'Authentication is not configured.')),
    previewSummary: () => Promise.reject(new ApiError(0, 'Authentication is not configured.')),
    save: () => Promise.reject(new ApiError(0, 'Authentication is not configured.')),
    saveSummary: () => Promise.reject(new ApiError(0, 'Authentication is not configured.')),
    refresh: () => {},
  }
}

export const useMyPreferences: () => UseMyPreferencesResult = CLERK_CONFIGURED
  ? useConfiguredMyPreferences
  : useUnconfiguredMyPreferences

/** Result of `useTripPreferences(tripId)`. */
export interface UseTripPreferencesResult {
  preferences: TripPreferences | null
  isLoading: boolean
  /** Failure to load the group preference (the last good copy is kept). */
  error: string | null
  isSaving: boolean
  isSummarising: boolean
  /** Failure from the last save or summary regeneration. */
  actionError: string | null
  /** Saves goals and returns the server copy. Rejects with the API error. */
  save: (input: UpdateTripPreferencesInput) => Promise<TripPreferences>
  /** Regenerates the group summary. Rejects with the API error. */
  summarise: () => Promise<TripPreferences>
  refresh: () => void
}

function useConfiguredTripPreferences(tripId: string): UseTripPreferencesResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [preferences, setPreferences] = useState<TripPreferences | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [isSummarising, setIsSummarising] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  const refresh = useCallback(() => setReloadCount((count) => count + 1), [])

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      setIsLoading(true)
      setError(null)

      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const result = await getTripPreferences(token, tripId)
        if (!cancelled) setPreferences(result)
      } catch (cause) {
        if (!cancelled) {
          setError(describeError(cause, 'Could not load this trip’s group preferences.'))
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

  const save = useCallback(
    async (input: UpdateTripPreferencesInput) => {
      setIsSaving(true)
      setActionError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const updated = await updateTripPreferences(token, tripId, input)
        setPreferences(updated)
        return updated
      } catch (cause) {
        setActionError(describeError(cause, 'Could not save the group goals.'))
        throw cause
      } finally {
        setIsSaving(false)
      }
    },
    [getToken, tripId],
  )

  const summarise = useCallback(async () => {
    setIsSummarising(true)
    setActionError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

      const updated = await summariseTripPreferences(token, tripId)
      setPreferences(updated)
      return updated
    } catch (cause) {
      setActionError(describeError(cause, 'Could not regenerate the group summary.'))
      throw cause
    } finally {
      setIsSummarising(false)
    }
  }, [getToken, tripId])

  if (!isLoaded) {
    return {
      preferences,
      isLoading: true,
      error: null,
      isSaving: false,
      isSummarising: false,
      actionError: null,
      save,
      summarise,
      refresh,
    }
  }

  if (!isSignedIn) {
    return {
      preferences: null,
      isLoading: false,
      error: null,
      isSaving: false,
      isSummarising: false,
      actionError: null,
      save,
      summarise,
      refresh,
    }
  }

  return {
    preferences,
    isLoading,
    error,
    isSaving,
    isSummarising,
    actionError,
    save,
    summarise,
    refresh,
  }
}

function useUnconfiguredTripPreferences(): UseTripPreferencesResult {
  const notConfigured = () =>
    Promise.reject(new ApiError(0, 'Authentication is not configured.'))

  return {
    preferences: null,
    isLoading: false,
    error: null,
    isSaving: false,
    isSummarising: false,
    actionError: null,
    save: notConfigured,
    summarise: notConfigured,
    refresh: () => {},
  }
}

export const useTripPreferences: (tripId: string) => UseTripPreferencesResult =
  CLERK_CONFIGURED ? useConfiguredTripPreferences : useUnconfiguredTripPreferences
