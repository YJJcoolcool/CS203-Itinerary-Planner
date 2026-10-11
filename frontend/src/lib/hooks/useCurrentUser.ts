import { useAuth, useUser } from '@clerk/react'
import { useEffect, useState } from 'react'
import { publishableKey } from '../../auth/authConfiguration'
import type { User } from '../../types/trip'
import { ApiError, createBackendUser } from '../api'

export interface UseCurrentUserResult {
  user: User | null
  isLoading: boolean
  error: string | null
}

const CLERK_CONFIGURED = Boolean(publishableKey)
const NOT_CONFIGURED_MESSAGE =
  'Authentication is not configured. Set VITE_CLERK_PUBLISHABLE_KEY to load your trips.'
const NO_SESSION_MESSAGE = 'No active Clerk session token is available.'

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


function useUnconfiguredCurrentUser(): UseCurrentUserResult {
  return { user: null, isLoading: false, error: NOT_CONFIGURED_MESSAGE }
}


export const useCurrentUser: () => UseCurrentUserResult = CLERK_CONFIGURED
  ? useConfiguredCurrentUser
  : useUnconfiguredCurrentUser
