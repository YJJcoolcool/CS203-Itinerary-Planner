import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import { ApiError } from '../../lib/api'
import type { UseMyInvitationsResult } from '../../lib/useInvitations'
import { roleLabel } from '../../types/invitation'
import type { TripInvitation } from '../../types/invitation'
import { formatDateRange } from '../trips/tripUtils'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

type Decision = 'ACCEPTED' | 'DECLINED'

/**
 * Accept/decline popup for one invitation. Closing (Escape, backdrop) leaves the
 * invite pending so the user can decide later.
 */
export function InvitationModal({
  invitation,
  respond,
  onClose,
  onAccepted,
  onStale,
}: {
  invitation: TripInvitation
  respond: UseMyInvitationsResult['respond']
  onClose: () => void
  onAccepted: (tripId: string) => void
  onStale: () => void
}) {
  const [pending, setPending] = useState<Decision | null>(null)
  const [error, setError] = useState<string | null>(null)
  const acceptRef = useRef<HTMLButtonElement>(null)
  const titleId = useId()

  // Prevent background scrolling
  useBodyScrollLock();

  // Focus the primary action on open.
  useEffect(() => {
    acceptRef.current?.focus()
  }, [])

  // Escape closes the dialog.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function handle(status: Decision) {
    setPending(status)
    setError(null)
    try {
      await respond(invitation.id, status)
      onClose()
      if (status === 'ACCEPTED') onAccepted(invitation.tripId)
    } catch (cause) {
      setError(cause instanceof Error && cause.message ? cause.message : 'Something went wrong.')
      // 403/404/409 = invite is no longer actionable (e.g. owner cancelled): refresh the inbox.
      if (cause instanceof ApiError && [403, 404, 409].includes(cause.status)) onStale()
    } finally {
      setPending(null)
    }
  }

  // Portal to <body>: the navbar's backdrop-blur makes it the containing block for
  // position:fixed, which would trap this overlay inside the 64px header.
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
        <h2
          id={titleId}
          className="font-display text-2xl font-medium tracking-tight text-foreground"
        >
          Trip invitation
        </h2>
        <p className="mt-2 text-sm text-muted">
          <strong className="font-semibold text-foreground">
            {invitation.senderDisplayName ?? 'Someone'}
          </strong>{' '}
          invited you to join as {roleLabel(invitation.role)}
        </p>

        <div className="mt-4 rounded-xl border border-border bg-surface-subtle px-4 py-3">
          <p className="font-display text-xl text-foreground">{invitation.tripName}</p>
          <p className="mt-1 text-sm text-muted">
            {formatDateRange(invitation.tripStartDate, invitation.tripEndDate)}
          </p>
        </div>

        {error && (
          <p
            role="alert"
            className="mt-4 rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-xs text-terracotta"
          >
            {error}
          </p>
        )}

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            type="button"
            onClick={() => void handle('DECLINED')}
            className={GHOST_BUTTON}
            disabled={pending !== null}
            aria-busy={pending === 'DECLINED'}
          >
            {pending === 'DECLINED' ? 'Declining…' : 'Decline'}
          </button>
          <button
            ref={acceptRef}
            type="button"
            onClick={() => void handle('ACCEPTED')}
            className={PRIMARY_BUTTON}
            disabled={pending !== null}
            aria-busy={pending === 'ACCEPTED'}
          >
            {pending === 'ACCEPTED' ? 'Joining…' : 'Accept & join trip'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
