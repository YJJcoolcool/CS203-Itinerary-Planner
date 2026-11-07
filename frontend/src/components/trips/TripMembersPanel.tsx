import { useState } from 'react'
import { useAuth } from '@clerk/react'

import { ApiError, changeMemberRole, removeMember, transferOwnership } from '../../lib/api'
import { roleLabel } from '../../types/invitation'
import type { TripRole } from '../../types/invitation'
import type { MemberRole, Trip } from '../../types/trip'
import { CollapsibleSection } from '../CollapsibleSection'
import { avatarTone, initials, roleOf, uniqueMembers } from './tripUtils'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-3 py-2 text-sm font-medium text-muted hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`

const DANGER_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-terracotta/40 px-3 py-2 text-sm font-medium text-terracotta hover:bg-terracotta/10 disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta'

/**
 * Everyone on the trip with their role. The owner gets a role dropdown, a
 * "Make owner" button (CGT-168) and a remove button for each collaborator;
 * everyone else sees the role as text. Any member except the owner can leave
 * the trip from their own row (CGT-167).
 */
export function TripMembersPanel({
  trip,
  myRole,
  currentUserId,
  onUpdated,
  onLeft,
}: {
  trip: Trip
  myRole: MemberRole | null
  /** The signed-in user's id, used to find their own row. */
  currentUserId: string | null
  onUpdated: (trip: Trip) => void
  /** Called after the signed-in user leaves; the page should navigate away. */
  onLeft: () => void
}) {
  const { getToken } = useAuth()

  const [busyUserId, setBusyUserId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const canManage = myRole === 'OWNER'

  // Runs one member change and returns the updated trip, or null if it failed.
  async function run(userId: string, action: (token: string) => Promise<Trip>, fallback: string) {
    setBusyUserId(userId)
    setError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active session token is available.')

      return await action(token)
    } catch (cause) {
      setError(cause instanceof Error && cause.message ? cause.message : fallback)
      return null
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleRoleChange(userId: string, role: TripRole) {
    const updated = await run(userId, (token) => changeMemberRole(token, trip.id, userId, role), 'Could not change this role.')
    if (updated) onUpdated(updated)
  }

  async function handleRemove(userId: string, name: string) {
    if (!window.confirm(`Remove ${name} from this trip?`)) return
    const updated = await run(userId, (token) => removeMember(token, trip.id, userId), 'Could not remove this member.')
    if (updated) onUpdated(updated)
  }

  async function handleMakeOwner(userId: string, name: string) {
    if (!window.confirm(`Make ${name} the owner of this trip? You'll stay on as an editor.`)) return
    const updated = await run(userId, (token) => transferOwnership(token, trip.id, userId), 'Could not transfer ownership.')
    if (updated) onUpdated(updated)
  }

  async function handleLeave(userId: string) {
    if (!window.confirm(`Leave "${trip.name}"? You'll lose access unless someone invites you again.`)) return
    const updated = await run(userId, (token) => removeMember(token, trip.id, userId), 'Could not leave this trip.')
    // The page would now get a 403 for this trip, so don't hand it the update.
    if (updated) onLeft()
  }

  const members = uniqueMembers(trip)

  return (
    <CollapsibleSection
      title="Members"
      subtitle={`${members.length} ${members.length === 1 ? 'traveler' : 'travelers'}`}
    >
      {error && (
        <p role="alert" className={`mt-3 ${ERROR_ALERT}`}>
          {error}
        </p>
      )}

      <ul className="mt-4 divide-y divide-border">
        {members.map((member) => {
          const name = member.displayName ?? 'Traveler'
          const role = roleOf(trip, member.id) ?? 'VIEWER'
          const isMe = member.id === currentUserId
          const showControls = canManage && role !== 'OWNER'
          const canLeave = isMe && role !== 'OWNER'
          const isBusy = busyUserId === member.id

          return (
            <li key={member.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <span
                  aria-hidden="true"
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-foreground ${avatarTone(member.id)}`}
                >
                  {initials(member.displayName)}
                </span>
                <p className="truncate text-sm font-medium text-foreground">
                  {name}
                  {isMe && <span className="ml-1 font-normal text-muted">(you)</span>}
                </p>
              </div>

              {showControls ? (
                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <select
                    aria-label={`Role for ${name}`}
                    value={role}
                    disabled={isBusy}
                    onChange={(event) => void handleRoleChange(member.id, event.target.value as TripRole)}
                    className="input-field w-28"
                  >
                    <option value="VIEWER">Viewer</option>
                    <option value="EDITOR">Editor</option>
                  </select>
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => void handleMakeOwner(member.id, name)}
                    className={SECONDARY_BUTTON}
                  >
                    Make owner
                  </button>
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => void handleRemove(member.id, name)}
                    className={SECONDARY_BUTTON}
                  >
                    Remove
                  </button>
                </div>
              ) : canLeave ? (
                <div className="flex shrink-0 items-center gap-3">
                  <span className="text-sm text-muted">{roleLabel(role)}</span>
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() => void handleLeave(member.id)}
                    className={DANGER_BUTTON}
                  >
                    Leave trip
                  </button>
                </div>
              ) : (
                <span
                  className="shrink-0 text-sm text-muted"
                  title={isMe && role === 'OWNER' ? 'To leave, make another member the owner first' : undefined}
                >
                  {roleLabel(role)}
                </span>
              )}
            </li>
          )
        })}
      </ul>
    </CollapsibleSection>
  )
}
