import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import { publishableKey } from '../auth/authConfiguration'
import type { Suggestion } from '../types/suggestion'
import {
  ApiError,
  acceptSuggestion,
  getSuggestions,
  rejectSuggestion,
  replanTrip,
} from './api'

/** How often the open trip page re-checks for new suggestions. */
const POLL_INTERVAL_MS = 30_000
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'

/** Result of `useSuggestions(tripId)`. */
export interface UseSuggestionsResult {
  suggestions: Suggestion[]
  isLoading: boolean
  error: string | null
  isReplanning: boolean
  /** Checks the weather and reloads the list. Failures are reported via `error`. */
  replan: () => Promise<void>
  /** Accepts a suggestion, optionally with a chosen option id. Rejects with the API error after recording a friendly message. */
  accept: (suggestionId: string, locationId?: string) => Promise<void>
  /** Dismisses a suggestion. Rejects with the API error after recording a friendly message. */
  reject: (suggestionId: string) => Promise<void>
  /** Refetches the list without clearing the current one. */
  refresh: () => void
}

// Same reasoning as useTrips.ts: no ClerkProvider without a key, so pick the impl at load time.
const CLERK_CONFIGURED = Boolean(publishableKey)

function describeError(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message) return cause.message
  return fallback
}

function useConfiguredSuggestions(tripId: string): UseSuggestionsResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isReplanning, setIsReplanning] = useState(false)
  const [reloadCount, setReloadCount] = useState(0)

  const refresh = useCallback(() => setReloadCount((count) => count + 1), [])

  // Fetch whenever signed in or refresh() is called.
  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)
        const result = await getSuggestions(token, tripId)
        if (!cancelled) {
          setSuggestions(result)
          setError(null)
        }
      } catch (cause) {
        // Keep the last good list on a failed poll; just record the error.
        if (!cancelled) {
          setError(describeError(cause, 'Could not load weather alternatives.'))
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

  // Poll every 30s (skipped while the tab is hidden) and re-check when the window regains focus.
  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    const interval = window.setInterval(() => {
      if (!document.hidden) refresh()
    }, POLL_INTERVAL_MS)
    window.addEventListener('focus', refresh)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('focus', refresh)
    }
  }, [isLoaded, isSignedIn, refresh])

  const replan = useCallback(async () => {
    setIsReplanning(true)
    setError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

      await replanTrip(token, tripId)
      const result = await getSuggestions(token, tripId)
      setSuggestions(result)
    } catch (cause) {
      setError(describeError(cause, 'Could not check for weather alternatives. Please try again.'))
    } finally {
      setIsReplanning(false)
    }
  }, [getToken, tripId])

  const accept = useCallback(
    async (suggestionId: string, locationId?: string) => {
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const updated = await acceptSuggestion(token, suggestionId, locationId)
        setSuggestions((current) =>
          current.map((suggestion) =>
            suggestion.id === suggestionId ? updated : suggestion,
          ),
        )
        refresh()
      } catch (cause) {
        setError(describeError(cause, 'Could not accept this alternative. Please try again.'))
        throw cause
      }
    },
    [getToken, refresh],
  )

  const reject = useCallback(
    async (suggestionId: string) => {
      setError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const updated = await rejectSuggestion(token, suggestionId)
        setSuggestions((current) =>
          current.map((suggestion) =>
            suggestion.id === suggestionId ? updated : suggestion,
          ),
        )
        refresh()
      } catch (cause) {
        setError(describeError(cause, 'Could not dismiss this suggestion. Please try again.'))
        throw cause
      }
    },
    [getToken, refresh],
  )

  if (!isLoaded) {
    return {
      suggestions: [],
      isLoading: true,
      error: null,
      isReplanning: false,
      replan,
      accept,
      reject,
      refresh,
    }
  }

  if (!isSignedIn) {
    return {
      suggestions: [],
      isLoading: false,
      error: null,
      isReplanning: false,
      replan,
      accept,
      reject,
      refresh,
    }
  }

  return { suggestions, isLoading, error, isReplanning, replan, accept, reject, refresh }
}

function useUnconfiguredSuggestions(): UseSuggestionsResult {
  const notConfigured = () =>
    Promise.reject(new ApiError(0, 'Authentication is not configured.'))

  return {
    suggestions: [],
    isLoading: false,
    error: null,
    isReplanning: false,
    replan: notConfigured,
    accept: notConfigured,
    reject: notConfigured,
    refresh: () => {},
  }
}

export const useSuggestions: (tripId: string) => UseSuggestionsResult = CLERK_CONFIGURED
  ? useConfiguredSuggestions
  : useUnconfiguredSuggestions
