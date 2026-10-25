
import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { useAuth } from '@clerk/react'
import { useNavigate } from 'react-router'

import { publishableKey } from '../../auth/authConfiguration'
import { ApiError, updateTrip } from '../../lib/api'
import { useTripInvitations } from '../../lib/useInvitations'
import { roleLabel } from '../../types/invitation'
import type { TripRole } from '../../types/invitation'
import type { Trip } from '../../types/trip'
import { CountryMultiSelect } from './CountryMultiSelect'
import { DeleteTripDialog } from './DeleteTripDialog'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta'

function describeError(cause: unknown, fallback: string): string {
  if (cause instanceof Error && cause.message) return cause.message
  return fallback
}

function formatSentDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''

  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function normalizeCountries(countries: string[] = []): string[] {
  return [...new Set(
    countries.map((country) => country.toLowerCase()),
  )].sort()
}

interface InvitationsSectionProps {
  tripId: string
}

function InvitationsSection({ tripId }: InvitationsSectionProps) {
  const {
    invitations,
    isLoading,
    error,
    send,
    cancel,
  } = useTripInvitations(tripId)

  const [email, setEmail] = useState('')
  const [role, setRole] = useState<TripRole>('VIEWER')
  const [isSending, setIsSending] = useState(false)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<{
    kind: 'success' | 'error'
    message: string
  } | null>(null)

  const inputId = useId()
  const roleId = useId()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmed = email.trim()
    if (!trimmed || isSending) return

    setIsSending(true)
    setFeedback(null)

    try {
      const created = await send(trimmed, role)
      setEmail('')
      setFeedback({
        kind: 'success',
        message: `Invite sent to ${created.receiverDisplayName ?? trimmed} as ${roleLabel(created.role)}.`,
      })
    } catch (cause) {
      setFeedback({
        kind: 'error',
        message: describeError(cause, 'Could not send the invitation.'),
      })
    } finally {
      setIsSending(false)
    }
  }

  async function handleCancel(invitationId: string) {
    setCancellingId(invitationId)
    setFeedback(null)

    try {
      await cancel(invitationId)
      setFeedback({
        kind: 'success',
        message: 'Invitation cancelled.',
      })
    } catch (cause) {
      setFeedback({
        kind: 'error',
        message: describeError(cause, 'Could not cancel the invitation.'),
      })
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <section>
      <h3 className="text-lg font-semibold text-foreground">
        Invitations
      </h3>
      <p className="mt-1 text-sm text-muted">
        Invite existing Agile Trips users by email and manage outstanding invitations.
      </p>

      <form
        onSubmit={handleSubmit}
        className="mt-4 flex flex-col gap-2 sm:flex-row"
      >
        <label htmlFor={inputId} className="sr-only">
          Email address
        </label>

        <input
          id={inputId}
          type="email"
          required
          autoComplete="off"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="friend@email.com"
          disabled={isSending}
          className="input-field min-w-0 flex-1"
        />

        <label htmlFor={roleId} className="sr-only">
          Role
        </label>
        <select
          id={roleId}
          value={role}
          onChange={(event) => setRole(event.target.value as TripRole)}
          disabled={isSending}
          className="input-field sm:w-32 sm:shrink-0"
        >
          <option value="VIEWER">Viewer</option>
          <option value="EDITOR">Editor</option>
        </select>

        <button
          type="submit"
          disabled={isSending || !email.trim()}
          aria-busy={isSending}
          className={PRIMARY_BUTTON}
        >
          {isSending ? 'Sending…' : 'Send invite'}
        </button>
      </form>

      {feedback && (
        <p
          role={feedback.kind === 'error' ? 'alert' : 'status'}
          className={`mt-3 ${
            feedback.kind === 'error'
              ? ERROR_ALERT
              : 'rounded-xl border border-sage/40 bg-sage/10 px-3 py-2 text-sm text-sage'
          }`}
        >
          {feedback.message}
        </p>
      )}

      <div className="mt-6">
        <h4 className="text-xs font-medium uppercase tracking-wide text-muted">
          Pending invitations
        </h4>

        {isLoading ? (
          <p className="mt-3 text-sm text-muted">Loading invitations…</p>
        ) : error ? (
          <p role="alert" className={`mt-3 ${ERROR_ALERT}`}>
            {error}
          </p>
        ) : invitations.length === 0 ? (
          <p className="mt-3 text-sm text-muted">
            No pending invitations.
          </p>
        ) : (
          <ul className="mt-2 divide-y divide-border">
            {invitations.map((invitation) => (
              <li
                key={invitation.id}
                className="flex items-center justify-between gap-3 py-3"
              >
                <div className="min-w-0">
                  <div className="flex min-w-0 items-center gap-2">
                    <p className="truncate text-sm font-medium text-foreground">
                      {invitation.receiverDisplayName ?? 'Unknown traveler'}
                    </p>
                    <span className="shrink-0 rounded-full border border-border bg-surface-subtle px-2 py-0.5 text-xs text-muted">
                      {roleLabel(invitation.role)}
                    </span>
                  </div>
                  <p className="text-xs text-muted">
                    Sent {formatSentDate(invitation.createdAt)}
                  </p>
                </div>

                <button
                  type="button"
                  disabled={cancellingId === invitation.id}
                  aria-busy={cancellingId === invitation.id}
                  onClick={() => void handleCancel(invitation.id)}
                  className={SECONDARY_BUTTON}
                >
                  {cancellingId === invitation.id ? 'Cancelling…' : 'Cancel'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

export function TripSettingsPanel({
  trip,
  isOwner,
  onClose,
  onUpdated,
  onDeleted,
}: {
  trip: Trip
  isOwner: boolean
  onClose: () => void
  onUpdated: (trip: Trip) => void
  onDeleted: () => void
}) {
  const { getToken } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState(trip.name)
  const [startDate, setStartDate] = useState(trip.startDate)
  const [endDate, setEndDate] = useState(trip.endDate)
  const [countries, setCountries] = useState<string[]>(
    trip.countries ?? [],
  )

  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  const [isDeleteOpen, setIsDeleteOpen] = useState(false)

  const titleId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)

  const countriesChanged =
    JSON.stringify(normalizeCountries(countries)) !==
    JSON.stringify(normalizeCountries(trip.countries ?? []))

  const dirty =
    name.trim() !== trip.name ||
    startDate !== trip.startDate ||
    endDate !== trip.endDate ||
    countriesChanged

  useEffect(() => {
    headingRef.current?.focus()

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isDeleteOpen && !isSaving) {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isDeleteOpen, isSaving, onClose])

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedName = name.trim()
    if (!trimmedName || !startDate || !endDate) {
      setSaveError('Please complete the trip name and both dates.')
      return
    }

    if (endDate < startDate) {
      setSaveError('The end date cannot be before the start date.')
      return
    }

    const countryCodes = [
      ...new Set(
        countries
          .map((country) => country.trim().toLowerCase())
          .filter(Boolean)
      ),
    ];

    if (countryCodes.length === 0) {
      setSaveError('Enter at least one country code.')
      return
    }

    setIsSaving(true)
    setSaveError(null)
    setSaveSuccess(false)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active session token is available.')

      const updated = await updateTrip(token, trip.id, {
        name: name.trim(),
        startDate,
        endDate,
        countries: normalizeCountries(countries),
      })

      onUpdated(updated)
      setSaveSuccess(true)
    } catch (cause) {
      setSaveError(describeError(cause, 'Could not update this trip.'))
    } finally {
      setIsSaving(false)
    }
  }

  /*
   * Deleting can be slow, so go back to My Trips straight away and let that
   * page hide the card and send the delete (CGT-136). The trip travels in the
   * navigation state, so My Trips knows which one to delete.
   */
  function handleDelete() {
    setIsDeleteOpen(false)
    onDeleted()
    navigate('/trips', {
      replace: true,
      state: { deletingTrip: { id: trip.id, name: trip.name } },
    })
  }

  const panel = (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 p-3 backdrop-blur-sm sm:p-6"
      onMouseDown={(event) => {
        if (
          event.target === event.currentTarget &&
          !isSaving &&
          !isDeleteOpen
        ) {
          onClose()
        }
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="my-auto flex max-h-[92vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-border bg-surface-raised shadow-2xl"
      >
        <header className="flex items-start justify-between gap-4 border-b border-border p-5 sm:p-6">
          <div>
            <h2
              id={titleId}
              ref={headingRef}
              tabIndex={-1}
              className="font-display text-2xl font-medium tracking-tight text-foreground"
            >
              Trip settings
            </h2>
            <p className="mt-1 text-sm text-muted">
              Manage {trip.name}
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSaving}
            aria-label="Close trip settings"
            className={`rounded-lg p-2 text-muted hover:bg-surface-subtle hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`}
          >
            ✕
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto p-5 sm:p-6">
          <form id="trip-settings-form" onSubmit={handleSave}>
            <section>
              <h3 className="text-lg font-semibold text-foreground">
                Trip details
              </h3>
              <p className="mt-1 text-sm text-muted">
                Change the trip name and travel dates.
              </p>

              <div className="mt-5 grid gap-4">
                <div>
                  <label
                    htmlFor="trip-settings-name"
                    className="mb-1.5 block text-sm font-medium text-foreground"
                  >
                    Trip name
                  </label>
                  <input
                    id="trip-settings-name"
                    required
                    maxLength={120}
                    value={name}
                    onChange={(event) => {
                      setName(event.target.value)
                      setSaveSuccess(false)
                      setSaveError(null)
                    }}
                    disabled={isSaving}
                    className="input-field w-full"
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label
                      htmlFor="trip-settings-start"
                      className="mb-1.5 block text-sm font-medium text-foreground"
                    >
                      Start date
                    </label>
                    <input
                      id="trip-settings-start"
                      type="date"
                      required
                      value={startDate}
                      onChange={(event) => {
                        setStartDate(event.target.value)
                        setSaveSuccess(false)
                        setSaveError(null)
                      }}
                      disabled={isSaving}
                      className="input-field w-full"
                    />
                  </div>

                  <div>
                    <label
                      htmlFor="trip-settings-end"
                      className="mb-1.5 block text-sm font-medium text-foreground"
                    >
                      End date
                    </label>
                    <input
                      id="trip-settings-end"
                      type="date"
                      required
                      min={startDate || undefined}
                      value={endDate}
                      onChange={(event) => {
                        setEndDate(event.target.value)
                        setSaveSuccess(false)
                        setSaveError(null)
                      }}
                      disabled={isSaving}
                      className="input-field w-full"
                    />
                  </div>
                </div>

                <CountryMultiSelect
                  value={countries}
                  onChange={(next) => {
                    setCountries(next)
                    setSaveSuccess(false)
                    setSaveError(null)
                  }}
                  disabled={isSaving}
                />
              </div>

              {saveError && (
                <p role="alert" className={`mt-4 ${ERROR_ALERT}`}>
                  {saveError}
                </p>
              )}

              {saveSuccess && (
                <p role="status" className="mt-4 text-sm text-sage">
                  Trip settings saved.
                </p>
              )}

              <div className="mt-5 flex justify-end">
                <button
                  type="submit"
                  disabled={isSaving || !dirty}
                  className={PRIMARY_BUTTON}
                >
                  {isSaving ? 'Saving…' : 'Save changes'}
                </button>
              </div>
            </section>
          </form>

          <div className="my-7 border-t border-border" />

          <InvitationsSection tripId={trip.id} />

          <div className="my-7 border-t border-border" />

          {isOwner && ( // Only show for Owner
            <section className="rounded-xl border border-terracotta/40 p-4 sm:p-5">
              <h3 className="font-semibold text-terracotta">
                Danger zone
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">
                Permanently delete this trip. This action cannot be undone.
              </p>
              <div className="mt-4 flex justify-end">
                <button
                  type="button"
                  disabled={isSaving}
                  onClick={() => setIsDeleteOpen(true)}
                  className={`rounded-xl bg-terracotta px-4 py-2.5 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50 ${FOCUS_RING}`}
                >
                  Delete trip
                </button>
              </div>
            </section>
          )}
        </div>

        <footer className="flex justify-end border-t border-border p-4 sm:px-6">
          <button
            type="button"
            onClick={onClose}
            disabled={isSaving}
            className={SECONDARY_BUTTON}
          >
            Done
          </button>
        </footer>
      </section>

      {isDeleteOpen && (
        <DeleteTripDialog
          tripId={trip.id}
          tripName={trip.name}
          onClose={() => setIsDeleteOpen(false)}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )

  if (!publishableKey) {
    return panel
  }

  return createPortal(panel, document.body)
}
