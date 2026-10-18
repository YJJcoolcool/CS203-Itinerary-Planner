import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMyInvitations } from '../../lib/useInvitations'
import { roleLabel } from '../../types/invitation'
import type { TripInvitation } from '../../types/invitation'
import { formatDateRange } from '../trips/tripUtils'
import { InvitationModal } from './InvitationModal'

const FOCUS_RING =
  'focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface focus-visible:outline-none'

function InboxIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="M22 12h-6l-2 3h-4l-2-3H2" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11Z" />
    </svg>
  )
}

/** Navbar inbox: red dot while invites are pending; click an invite to accept/decline it. */
export function InvitationsInbox() {
  const { invitations, refresh, respond } = useMyInvitations()
  const [isOpen, setIsOpen] = useState(false)
  const [selected, setSelected] = useState<TripInvitation | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const count = invitations.length

  // Close the dropdown on outside click or Escape.
  useEffect(() => {
    if (!isOpen) return
    function onMouseDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }
    document.addEventListener('mousedown', onMouseDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onMouseDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [isOpen])

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-label={count > 0 ? `Invitations (${count} pending)` : 'Invitations'}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        title="Invitations"
        className={`relative flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface-subtle text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`}
      >
        <InboxIcon />
        {count > 0 && (
          <span
            aria-hidden="true"
            className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-red-500 ring-2 ring-surface"
          />
        )}
      </button>

      {isOpen && (
        <div
          role="menu"
          aria-label="Invitations"
          className="absolute right-0 z-50 mt-2 w-72 rounded-2xl border border-border bg-surface-raised p-2 shadow-2xl"
        >
          <p className="px-2 py-1.5 text-xs font-medium text-muted">Invitations</p>
          {count === 0 ? (
            <p className="px-2 py-4 text-center text-sm text-faint">No pending invitations</p>
          ) : (
            invitations.map((invitation) => (
              <button
                key={invitation.id}
                role="menuitem"
                type="button"
                onClick={() => {
                  setSelected(invitation)
                  setIsOpen(false)
                }}
                className={`w-full rounded-xl px-2 py-2 text-left transition-colors hover:bg-surface-subtle ${FOCUS_RING}`}
              >
                <span className="block text-sm font-medium text-foreground">
                  {invitation.tripName}
                </span>
                <span className="block text-xs text-muted">
                  from {invitation.senderDisplayName ?? 'Someone'} ·{' '}
                  {formatDateRange(invitation.tripStartDate, invitation.tripEndDate)}
                </span>
                <span className="mt-0.5 block text-xs text-faint">
                  Invited as {roleLabel(invitation.role)}
                </span>
              </button>
            ))
          )}
        </div>
      )}

      {selected && (
        <InvitationModal
          invitation={selected}
          respond={respond}
          onClose={() => setSelected(null)}
          onAccepted={(tripId) => navigate(`/trips/${tripId}`)}
          onStale={refresh}
        />
      )}
    </div>
  )
}
