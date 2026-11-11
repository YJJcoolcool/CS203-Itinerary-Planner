import { useAuth } from '@clerk/react'
import { useCallback, useEffect, useState } from 'react'
import type { Friend, FriendRequest } from '../types/friend'
import { getFriendRequests, getFriends } from './api'

/** Same cadence as the invitations poll. */
const POLL_INTERVAL_MS = 30_000

/**
 * Incoming friend requests for the navbar inbox (CGT-181). Polls while the tab
 * is visible and re-checks on focus. Only used under `<Show when="signed-in">`.
 */
export function useFriendRequests() {
  const { getToken } = useAuth()
  const [requests, setRequests] = useState<FriendRequest[]>([])
  const [reloadCount, setReloadCount] = useState(0)

  const refresh = useCallback(() => setReloadCount((count) => count + 1), [])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const token = await getToken()
        if (!token) return
        const result = await getFriendRequests(token)
        if (!cancelled) setRequests(result)
      } catch {
        // Keep the last good list on a failed poll.
      }
    })()
    return () => {
      cancelled = true
    }
  }, [getToken, reloadCount])

  useEffect(() => {
    const interval = window.setInterval(() => {
      if (!document.hidden) refresh()
    }, POLL_INTERVAL_MS)
    window.addEventListener('focus', refresh)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('focus', refresh)
    }
  }, [refresh])

  return { requests, refresh }
}

/** The signed-in user's friends, loaded on mount (CGT-182). Empty on failure. */
export function useFriends() {
  const { getToken } = useAuth()
  const [friends, setFriends] = useState<Friend[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const token = await getToken()
        if (!token) return
        const result = await getFriends(token)
        if (!cancelled) setFriends(result)
      } catch {
        // Non-critical: callers show an empty list.
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [getToken])

  return { friends, isLoading }
}
