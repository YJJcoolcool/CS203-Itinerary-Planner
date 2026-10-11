import { useAuth } from '@clerk/react'
import { useEffect, useState } from 'react'
import { publishableKey } from '../../auth/authConfiguration'
import type { GeoPlace } from '../../types/trip'
import { ApiError, searchPlaces } from '../api'

export interface UseLocationSearchResult { query: string; setQuery: (value: string) => void; results: GeoPlace[]; attribution: string | null; isSearching: boolean; error: string | null; hasSearched: boolean }
const CLERK_CONFIGURED = Boolean(publishableKey)
const NOT_CONFIGURED_MESSAGE = 'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'
function describeError(error: unknown): string { if (error instanceof ApiError) return error.message; if (error instanceof Error && error.message) return error.message; return 'Something went wrong. Please try again.' }
const SEARCH_DEBOUNCE_MS = 750

const EMPTY_PLACES: GeoPlace[] = []

/** Identity used to dedupe an OSM place against already-imported locations. */
function osmKey(osmType: string, osmId: number): string {
  return `${osmType}:${osmId}`
}

/** State of one resolved search, tagged with the query it belongs to. */
interface LocationSearchState {
  query: string
  results: GeoPlace[]
  attribution: string | null
  error: string | null
}

function useConfiguredLocationSearch(): UseLocationSearchResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [query, setQuery] = useState('')
  const [state, setState] = useState<LocationSearchState | null>(null)
  const [isFetching, setIsFetching] = useState(false)

  const trimmed = query.trim()
  const isActive = isLoaded && Boolean(isSignedIn) && trimmed.length > 0

  useEffect(() => {
    if (!isActive) return

    let cancelled = false

    // Debounce: only the last keystroke inside the window triggers a request.
    const timer = window.setTimeout(() => {
      const requestQuery = trimmed

      async function run() {
        setIsFetching(true)

        try {
          const token = await getToken()
          if (cancelled) return
          if (!token) throw new ApiError(401, NO_SESSION_MESSAGE)

          const response = await searchPlaces(token, requestQuery)
          if (cancelled) return
          setState({
            query: requestQuery,
            results: response.results,
            attribution: response.attribution,
            error: null,
          })
        } catch (cause) {
          if (!cancelled) {
            setState({
              query: requestQuery,
              results: [],
              attribution: null,
              error: describeError(cause),
            })
          }
        } finally {
          if (!cancelled) setIsFetching(false)
        }
      }

      void run()
    }, SEARCH_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [getToken, isActive, trimmed])

  // Results are only exposed once they match the current query, so a stale
  // response from a previous query can never be shown for new text.
  const current = state?.query === trimmed ? state : null

  return {
    query,
    setQuery,
    results: current?.results ?? EMPTY_PLACES,
    attribution: current?.attribution ?? null,
    isSearching: isActive && (isFetching || current === null),
    error: current?.error ?? null,
    hasSearched: isActive && current !== null && !isFetching,
  }
}


function useUnconfiguredLocationSearch(): UseLocationSearchResult {
  const [query, setQuery] = useState('')
  return {
    query,
    setQuery,
    results: EMPTY_PLACES,
    attribution: null,
    isSearching: false,
    error: NOT_CONFIGURED_MESSAGE,
    hasSearched: false,
  }
}


export const useLocationSearch: () => UseLocationSearchResult = CLERK_CONFIGURED
  ? useConfiguredLocationSearch
  : useUnconfiguredLocationSearch

/**
 * Imports an OpenStreetMap place as a stored location, tracking the in-flight
 * `osmType:osmId` so the UI can show a per-item importing state.
 */

