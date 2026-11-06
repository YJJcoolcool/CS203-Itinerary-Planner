import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'

import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import { useTripInvitations } from '../../lib/useInvitations'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-xs text-terracotta'

function describeError(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message) return cause.message
  return fallback
}

function formatSentDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

/**
 * Owner-only popup opened from the trip card's + button: invite by email and manage
 * pending invites. The page hides it for non-owners; the backend enforces it (403) regardless.
 */
export function TripInviteModal({ tripId, onClose }: { tripId: string; onClose: () => void }) {
  const { invitations, isLoading, error, send, cancel } = useTripInvitations(tripId)
  const [email, setEmail] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; message: string } | null>(
    null,
  )
  const inputId = useId()
  const titleId = useId()
  const inputRef = useRef<HTMLInputElement>(null)

  // Prevent background scrolling
  useBodyScrollLock();

  // Focus the email field on open.
  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  // Escape closes the dialog.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = email.trim()
    if (!trimmed) return

    setIsSending(true)
    setFeedback(null)
    try {
      const created = await send(trimmed)
      setEmail('')
      setFeedback({
        kind: 'success',
        message: `Invite sent to ${created.receiverDisplayName ?? trimmed}`,
      })
    } catch (cause) {
      setFeedback({ kind: 'error', message: describeError(cause, 'Could not send the invite.') })
    } finally {
      setIsSending(false)
    }
  }

  async function handleCancel(invitationId: string) {
    setCancellingId(invitationId)
    setFeedback(null)
    try {
      await cancel(invitationId)
    } catch (cause) {
      setFeedback({ kind: 'error', message: describeError(cause, 'Could not cancel the invite.') })
    } finally {
      setCancellingId(null)
    }
  }

  // Portal to <body> so no ancestor's transform/blur traps the fixed overlay.
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
          Invite people
        </h2>
        <p className="mt-1 text-sm text-muted">
          They need an Agile Trips account with this email.
        </p>

        <form className="mt-4 flex flex-col gap-2 sm:flex-row" onSubmit={handleSubmit}>
          <label htmlFor={inputId} className="sr-only">
            Email address
          </label>
          <input
            ref={inputRef}
            id={inputId}
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="friend@email.com"
            autoComplete="off"
            className="input-field flex-1"
            disabled={isSending}
          />
          <button type="submit" className={PRIMARY_BUTTON} disabled={isSending} aria-busy={isSending}>
            {isSending ? 'Sending…' : 'Send invite'}
          </button>
        </form>

        {feedback && (
          <p
            role={feedback.kind === 'error' ? 'alert' : 'status'}
            className={
              feedback.kind === 'error'
                ? `mt-3 ${ERROR_ALERT}`
                : 'mt-3 rounded-xl border border-sage/40 bg-sage/10 px-3 py-2 text-xs text-sage'
            }
          >
            {feedback.message}
          </p>
        )}

        <h3 className="mt-6 text-xs font-medium tracking-wide text-muted uppercase">Pending</h3>
        {isLoading ? (
          <p className="mt-2 text-sm text-faint">Loading…</p>
        ) : error ? (
          <p role="alert" className={`mt-2 ${ERROR_ALERT}`}>
            {error}
          </p>
        ) : invitations.length === 0 ? (
          <p className="mt-2 text-sm text-faint">No pending invites</p>
        ) : (
          <ul className="mt-2 divide-y divide-border">
            {invitations.map((invitation) => (
              <li key={invitation.id} className="flex items-center justify-between gap-3 py-2.5">
                <span className="min-w-0 truncate text-sm text-foreground">
                  {invitation.receiverDisplayName ?? 'Unknown traveler'}
                  <span className="text-muted"> · sent {formatSentDate(invitation.createdAt)}</span>
                </span>
                <button
                  type="button"
                  onClick={() => void handleCancel(invitation.id)}
                  className={GHOST_BUTTON}
                  disabled={cancellingId === invitation.id}
                  aria-busy={cancellingId === invitation.id}
                >
                  {cancellingId === invitation.id ? 'Cancelling…' : 'Cancel'}
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="mt-6 flex justify-end">
          <button type="button" onClick={onClose} className={GHOST_BUTTON}>
            Done
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
