import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import { publishableKey } from '../auth/authConfiguration'
import type { TripInvitation } from '../types/invitation'
import {
  ApiError,
  cancelInvitation,
  getMyInvitations,
  getTripInvitations,
  respondToInvitation,
  sendInvitation,
} from './api'

/** How often the navbar re-checks for new invitations. */
const POLL_INTERVAL_MS = 30_000

/** Result of `useMyInvitations()`. */
export interface UseMyInvitationsResult {
  invitations: TripInvitation[]
  isLoading: boolean
  error: string | null
  refresh: () => void
  /** Accepts/declines, then removes the invite from the list. Rejects with the API error. */
  respond: (invitationId: string, status: 'ACCEPTED' | 'DECLINED') => Promise<TripInvitation>
}

// Same reasoning as useTrips.ts: no ClerkProvider without a key, so pick the impl at load time.
const CLERK_CONFIGURED = Boolean(publishableKey)

function useConfiguredMyInvitations(): UseMyInvitationsResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [invitations, setInvitations] = useState<TripInvitation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadCount, setReloadCount] = useState(0)

  const refresh = useCallback(() => setReloadCount((count) => count + 1), [])

  // Fetch whenever signed in or refresh() is called.
  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      try {
        const token = await getToken()
        if (cancelled || !token) return
        const result = await getMyInvitations(token)
        if (!cancelled) {
          setInvitations(result)
          setError(null)
        }
      } catch (cause) {
        // Keep the last good list on a failed poll; just record the error.
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : 'Could not load invitations.')
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

  const respond = useCallback(
    async (invitationId: string, status: 'ACCEPTED' | 'DECLINED') => {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active Clerk session token is available.')
      const updated = await respondToInvitation(token, invitationId, status)
      setInvitations((current) => current.filter((inv) => inv.id !== invitationId))
      return updated
    },
    [getToken],
  )

  if (!isLoaded) return { invitations: [], isLoading: true, error: null, refresh, respond }
  if (!isSignedIn) return { invitations: [], isLoading: false, error: null, refresh, respond }
  return { invitations, isLoading, error, refresh, respond }
}

function useUnconfiguredMyInvitations(): UseMyInvitationsResult {
  return {
    invitations: [],
    isLoading: false,
    error: null,
    refresh: () => {},
    respond: () => Promise.reject(new ApiError(0, 'Authentication is not configured.')),
  }
}

export const useMyInvitations: () => UseMyInvitationsResult = CLERK_CONFIGURED
  ? useConfiguredMyInvitations
  : useUnconfiguredMyInvitations

/** Result of `useTripInvitations(tripId)` (owner side). */
export interface UseTripInvitationsResult {
  /** Pending invites for this trip. */
  invitations: TripInvitation[]
  isLoading: boolean
  error: string | null
  /** Sends an invite and appends it on success. Rejects with the API error. */
  send: (email: string) => Promise<TripInvitation>
  /** Cancels an invite and removes it on success. Rejects with the API error. */
  cancel: (invitationId: string) => Promise<void>
}

// No polling: the owner's own send/cancel update the list directly.
function useConfiguredTripInvitations(tripId: string): UseTripInvitationsResult {
  const { getToken, isLoaded, isSignedIn } = useAuth()

  const [invitations, setInvitations] = useState<TripInvitation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return
    let cancelled = false

    async function load() {
      setIsLoading(true)
      setError(null)
      try {
        const token = await getToken()
        if (cancelled) return
        if (!token) throw new ApiError(401, 'No active Clerk session token is available.')
        const result = await getTripInvitations(token, tripId)
        if (!cancelled) setInvitations(result)
      } catch (cause) {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : 'Could not load invitations.')
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [getToken, isLoaded, isSignedIn, tripId])

  const send = useCallback(
    async (email: string) => {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active Clerk session token is available.')
      const created = await sendInvitation(token, tripId, email)
      setInvitations((current) => [...current, created])
      return created
    },
    [getToken, tripId],
  )

  const cancel = useCallback(
    async (invitationId: string) => {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active Clerk session token is available.')
      await cancelInvitation(token, tripId, invitationId)
      setInvitations((current) => current.filter((inv) => inv.id !== invitationId))
    },
    [getToken, tripId],
  )

  if (!isLoaded) return { invitations: [], isLoading: true, error: null, send, cancel }
  return { invitations, isLoading, error, send, cancel }
}

function useUnconfiguredTripInvitations(): UseTripInvitationsResult {
  const notConfigured = () => Promise.reject(new ApiError(0, 'Authentication is not configured.'))
  return {
    invitations: [],
    isLoading: false,
    error: null,
    send: notConfigured,
    cancel: notConfigured,
  }
}

export const useTripInvitations: (tripId: string) => UseTripInvitationsResult = CLERK_CONFIGURED
  ? useConfiguredTripInvitations
  : useUnconfiguredTripInvitations
