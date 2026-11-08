import { useEffect, useId, useState } from 'react'
import { createPortal } from 'react-dom'
import { useAuth } from '@clerk/react'

import {
  ApiError,
  getFriendship,
  getUserPreferences,
  respondToFriendRequest,
  sendFriendRequest,
} from '../../lib/api'
import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import type { Friendship } from '../../types/friend'
import { INDOOR_PREFERENCE_LABELS } from '../../types/preference'
import type { UserPreferences } from '../../types/preference'
import { PreferenceChip } from '../preferences/PreferenceChip'
import { avatarTone, initials } from '../trips/tripUtils'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

/**
 * Another traveler's profile: their public preferences (CGT-179) and the friend
 * action for the signed-in user's relation to them (CGT-180/181).
 */
export function UserProfileModal({
  userId,
  displayName,
  isSelf = false,
  onClose,
  onFriendshipChange,
}: {
  userId: string
  displayName: string | null
  /** Hides the friend action on the signed-in user's own profile. */
  isSelf?: boolean
  onClose: () => void
  /** Called after a friend request is sent, accepted or rejected. */
  onFriendshipChange?: () => void
}) {
  const { getToken } = useAuth()
  const titleId = useId()

  const [preferences, setPreferences] = useState<UserPreferences | null>(null)
  const [preferencesError, setPreferencesError] = useState<string | null>(null)
  const [friendship, setFriendship] = useState<Friendship | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useBodyScrollLock()

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  useEffect(() => {
    let cancelled = false
    void (async () => {
      const token = await getToken()
      const [prefs, relation] = await Promise.allSettled([
        getUserPreferences(token, userId),
        isSelf ? Promise.resolve(null) : getFriendship(token, userId),
      ])
      if (cancelled) return
      if (prefs.status === 'fulfilled') setPreferences(prefs.value)
      else
        setPreferencesError(
          prefs.reason instanceof ApiError && prefs.reason.status === 403
            ? 'Preferences are only visible to people you share a trip with.'
            : 'Could not load preferences.',
        )
      if (relation.status === 'fulfilled') setFriendship(relation.value)
    })()
    return () => {
      cancelled = true
    }
  }, [getToken, userId, isSelf])

  async function act(change: (token: string | null) => Promise<Friendship>) {
    setIsBusy(true)
    setActionError(null)
    try {
      setFriendship(await change(await getToken()))
      onFriendshipChange?.()
    } catch (cause) {
      setActionError(cause instanceof Error && cause.message ? cause.message : 'Something went wrong.')
    } finally {
      setIsBusy(false)
    }
  }

  const respond = (requestId: string, accept: boolean) =>
    act(async (token) => {
      await respondToFriendRequest(token, requestId, accept)
      return { relation: accept ? 'FRIENDS' : 'NONE', requestId: null }
    })

  const incomingId = friendship?.relation === 'INCOMING' ? friendship.requestId : null
  const name = displayName ?? 'Traveler'
  const details = preferences
    ? [
        preferences.indoorPreference && preferences.indoorPreference !== 'ANY'
          ? `Prefers ${INDOOR_PREFERENCE_LABELS[preferences.indoorPreference].toLowerCase()}`
          : null,
        preferences.maxWalkKm !== null ? `Walks up to ${preferences.maxWalkKm} km` : null,
        preferences.dietary ? `Dietary: ${preferences.dietary}` : null,
      ].filter((detail): detail is string => detail !== null)
    : []

  // Portal to <body> so the navbar's backdrop-blur can't trap the fixed overlay.
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="animate-rise w-full max-w-md rounded-2xl border border-border bg-surface-raised p-6 shadow-2xl"
      >
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-base font-semibold text-foreground ${avatarTone(userId)}`}
          >
            {initials(displayName)}
          </span>
          <h2 id={titleId} className="font-display text-2xl font-medium tracking-tight text-foreground">
            {name}
          </h2>
        </div>

        <div className="mt-5">
          {preferencesError ? (
            <p className="text-sm text-muted">{preferencesError}</p>
          ) : !preferences ? (
            <p className="text-sm text-muted">Loading profile…</p>
          ) : (
            <>
              {preferences.summary && <p className="text-sm text-foreground">{preferences.summary}</p>}

              <h3 className="mt-4 text-xs font-medium uppercase tracking-wide text-muted">Interests</h3>
              {preferences.tags.length === 0 ? (
                <p className="mt-2 text-sm text-faint">No interests set yet.</p>
              ) : (
                <div className="mt-2 flex flex-wrap gap-2">
                  {preferences.tags.map((tag) => (
                    <PreferenceChip key={tag} label={capitalize(tag)} selected />
                  ))}
                </div>
              )}

              {details.length > 0 && (
                <ul className="mt-4 space-y-1 text-sm text-muted">
                  {details.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>

        {actionError && (
          <p
            role="alert"
            className="mt-4 rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-xs text-terracotta"
          >
            {actionError}
          </p>
        )}

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className={GHOST_BUTTON}>
            Close
          </button>

          {friendship?.relation === 'NONE' && (
            <button
              type="button"
              disabled={isBusy}
              onClick={() => void act((token) => sendFriendRequest(token, userId))}
              className={PRIMARY_BUTTON}
            >
              {isBusy ? 'Sending…' : 'Add friend'}
            </button>
          )}
          {friendship?.relation === 'OUTGOING' && (
            <button type="button" disabled className={GHOST_BUTTON}>
              Request sent
            </button>
          )}
          {incomingId && (
            <>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => void respond(incomingId, false)}
                className={GHOST_BUTTON}
              >
                Reject
              </button>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => void respond(incomingId, true)}
                className={PRIMARY_BUTTON}
              >
                Accept friend request
              </button>
            </>
          )}
          {friendship?.relation === 'FRIENDS' && (
            <span className="inline-flex items-center justify-center rounded-xl border border-sage/40 bg-sage/10 px-4 py-2.5 text-sm font-medium text-sage">
              Friends
            </span>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}
