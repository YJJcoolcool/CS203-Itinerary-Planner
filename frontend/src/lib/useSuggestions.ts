import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { publishableKey } from '../auth/authConfiguration'
import type { Suggestion } from '../types/suggestion'
import {
  ApiError,
  acceptSuggestion,
  getSuggestions,
  rejectSuggestion,
  replanTrip,
  undoSuggestion,
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
  /**
   * Checks the weather and reloads the list. Resolves to how many new
   * suggestions were created, or null if the check failed (reported via `error`).
   */
  replan: () => Promise<number | null>
  /** Accepts a suggestion, optionally with a chosen option id. Rejects with the API error after recording a friendly message. */
  accept: (suggestionId: string, locationId?: string) => Promise<void>
  /** Dismisses a suggestion. Rejects with the API error after recording a friendly message. */
  reject: (suggestionId: string) => Promise<void>
  /** Reverses an applied suggestion (CGT-169). Rejects with the API error after recording a friendly message. */
  undo: (suggestionId: string) => Promise<void>
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
  // Kept apart so a successful poll can't wipe a message from something the user just did.
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [isReplanning, setIsReplanning] = useState(false)
  const [reloadCount, setReloadCount] = useState(0)
  const hasLoaded = useRef(false)
  // Each fetch takes a ticket; only the newest may write the list, so a slow
  // poll that started before "Check now" can't overwrite its fresher result.
  const latestFetch = useRef(0)

  const reload = useCallback(() => setReloadCount((count) => count + 1), [])
  const refresh = useCallback(() => {
    setActionError(null)
    reload()
  }, [reload])

  // Fetch whenever signed in or refresh() is called.
  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)
        const ticket = ++latestFetch.current
        const result = await getSuggestions(token, tripId)
        if (!cancelled && ticket === latestFetch.current) {
          setSuggestions(result)
          setLoadError(null)
          hasLoaded.current = true
        }
      } catch (cause) {
        // A rate-limited poll retries by itself, so don't alarm anyone over a list that's still fine.
        const isQuietPoll =
          cause instanceof ApiError && cause.status === 429 && hasLoaded.current
        // Keep the last good list on a failed poll; just record the error.
        if (!cancelled && !isQuietPoll) {
          setLoadError(describeError(cause, 'Could not load weather alternatives.'))
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
      if (!document.hidden) reload()
    }, POLL_INTERVAL_MS)
    window.addEventListener('focus', reload)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('focus', reload)
    }
  }, [isLoaded, isSignedIn, reload])

  const replan = useCallback(async () => {
    setIsReplanning(true)
    setActionError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

      const created = await replanTrip(token, tripId)
      const ticket = ++latestFetch.current
      const result = await getSuggestions(token, tripId)
      if (ticket === latestFetch.current) {
        // Belt and braces: whatever the list says, show what was just created.
        const known = new Set(result.map((suggestion) => suggestion.id))
        setSuggestions([...created.filter((suggestion) => !known.has(suggestion.id)), ...result])
      }
      return created.length
    } catch (cause) {
      setActionError(describeError(cause, 'Could not check for weather alternatives. Please try again.'))
      return null
    } finally {
      setIsReplanning(false)
    }
  }, [getToken, tripId])

  const accept = useCallback(
    async (suggestionId: string, locationId?: string) => {
      setActionError(null)

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
        setActionError(describeError(cause, 'Could not accept this alternative. Please try again.'))
        throw cause
      }
    },
    [getToken, refresh],
  )

  const reject = useCallback(
    async (suggestionId: string) => {
      setActionError(null)

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
        setActionError(describeError(cause, 'Could not dismiss this suggestion. Please try again.'))
        throw cause
      }
    },
    [getToken, refresh],
  )

  const undo = useCallback(
    async (suggestionId: string) => {
      setActionError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

        const updated = await undoSuggestion(token, suggestionId)
        setSuggestions((current) =>
          current.map((suggestion) =>
            suggestion.id === suggestionId ? updated : suggestion,
          ),
        )
        refresh()
      } catch (cause) {
        setActionError(describeError(cause, 'Could not undo this change. Please try again.'))
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
      undo,
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
      undo,
      refresh,
    }
  }

  return {
    suggestions,
    isLoading,
    error: actionError ?? loadError,
    isReplanning,
    replan,
    accept,
    reject,
    undo,
    refresh,
  }
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
    undo: notConfigured,
    refresh: () => {},
  }
}

export const useSuggestions: (tripId: string) => UseSuggestionsResult = CLERK_CONFIGURED
  ? useConfiguredSuggestions
  : useUnconfiguredSuggestions
