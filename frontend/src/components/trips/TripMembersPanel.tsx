import { useState } from 'react'
import { useAuth } from '@clerk/react'

import { ApiError, changeMemberRole, removeMember } from '../../lib/api'
import { roleLabel } from '../../types/invitation'
import type { TripRole } from '../../types/invitation'
import type { MemberRole, Trip } from '../../types/trip'
import { CollapsibleSection } from '../CollapsibleSection'
import { avatarTone, initials, roleOf, uniqueMembers } from './tripUtils'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-3 py-2 text-sm font-medium text-muted hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta'

/**
 * Everyone on the trip with their role. The owner gets a role dropdown and a remove button for
 * each collaborator; everyone else sees the role as text.
 * The owner's own row never has controls.
 */
export function TripMembersPanel({
  trip,
  myRole,
  onUpdated,
}: {
  trip: Trip
  myRole: MemberRole | null
  onUpdated: (trip: Trip) => void
}) {
  const { getToken } = useAuth()

  const [busyUserId, setBusyUserId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const canManage = myRole === 'OWNER'

  // Runs one member change and hands the updated trip back to the page.
  async function run(userId: string, action: (token: string) => Promise<Trip>, fallback: string) {
    setBusyUserId(userId)
    setError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active session token is available.')

      onUpdated(await action(token))
    } catch (cause) {
      setError(cause instanceof Error && cause.message ? cause.message : fallback)
    } finally {
      setBusyUserId(null)
    }
  }

  function handleRoleChange(userId: string, role: TripRole) {
    void run(userId, (token) => changeMemberRole(token, trip.id, userId, role), 'Could not change this role.')
  }
  
  function handleRemove(userId: string, name: string) {
    if (!window.confirm(`Remove ${name} from this trip?`)) return
    void run(userId, (token) => removeMember(token, trip.id, userId), 'Could not remove this member.')
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
          const showControls = canManage && role !== 'OWNER'
          const isBusy = busyUserId === member.id

          return (
            <li key={member.id} className="flex items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <span
                  aria-hidden="true"
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-foreground ${avatarTone(member.id)}`}
                >
                  {initials(member.displayName)}
                </span>
                <p className="truncate text-sm font-medium text-foreground">{name}</p>
              </div>

              {showControls ? (
                <div className="flex shrink-0 items-center gap-2">
                  <select
                    aria-label={`Role for ${name}`}
                    value={role}
                    disabled={isBusy}
                    onChange={(event) => handleRoleChange(member.id, event.target.value as TripRole)}
                    className="input-field w-28"
                  >
                    <option value="VIEWER">Viewer</option>
                    <option value="EDITOR">Editor</option>
                  </select>
                  <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => handleRemove(member.id, name)}
                      className={SECONDARY_BUTTON}
                  >
                      Remove
                  </button>
                </div>
              ) : (
                <span className="shrink-0 text-sm text-muted">{roleLabel(role)}</span>
              )}
            </li>
          )
        })}
      </ul>
    </CollapsibleSection>
  )
}