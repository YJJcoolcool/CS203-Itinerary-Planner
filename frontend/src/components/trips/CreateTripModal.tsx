import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useCreateTrip } from '../../lib/useTrips'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

interface FieldErrors {
  name?: string
  startDate?: string
  endDate?: string
  form?: string
}

function describeError(cause: unknown): string {
  if (cause instanceof Error && cause.message) return cause.message
  return 'Could not create the trip. Please try again.'
}

/**
 * Modal form for creating a trip. Validates locally before hitting the API and
 * uses `useCreateTrip`, which resolves the owner id internally.
 */
export function CreateTripModal({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: () => void
}) {
  const { createTrip, isCreating } = useCreateTrip()
  const [name, setName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const nameRef = useRef<HTMLInputElement>(null)
  const titleId = useId()

  // Focus the first field on open.
  useEffect(() => {
    nameRef.current?.focus()
  }, [])

  // Escape closes the dialog.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  function validate(): FieldErrors {
    const next: FieldErrors = {}
    if (!name.trim()) next.name = 'Give your trip a name.'
    if (!startDate) next.startDate = 'Pick a start date.'
    if (!endDate) next.endDate = 'Pick an end date.'
    if (startDate && endDate && endDate < startDate) {
      next.endDate = 'End date must be on or after the start date.'
    }
    return next
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const next = validate()
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }

    setErrors({})
    try {
      await createTrip({ name: name.trim(), startDate, endDate })
      onCreated()
      onClose()
    } catch (cause) {
      setErrors({ form: describeError(cause) })
    }
  }

  return (
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
          New trip
        </h2>
        <p className="mt-2 text-sm text-muted">Name it and set the dates. You can invite people later.</p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${titleId}-name`} className="text-xs font-medium text-muted">
              Trip name
            </label>
            <input
              ref={nameRef}
              id={`${titleId}-name`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="input-field"
              placeholder="e.g. Japan Autumn"
              autoComplete="off"
              aria-invalid={Boolean(errors.name)}
              aria-describedby={errors.name ? `${titleId}-name-error` : undefined}
            />
            {errors.name && (
              <p id={`${titleId}-name-error`} role="alert" className="text-xs text-terracotta">
                {errors.name}
              </p>
            )}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${titleId}-start`} className="text-xs font-medium text-muted">
                Start date
              </label>
              <input
                id={`${titleId}-start`}
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
                className="input-field"
                aria-invalid={Boolean(errors.startDate)}
                aria-describedby={errors.startDate ? `${titleId}-start-error` : undefined}
              />
              {errors.startDate && (
                <p id={`${titleId}-start-error`} role="alert" className="text-xs text-terracotta">
                  {errors.startDate}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${titleId}-end`} className="text-xs font-medium text-muted">
                End date
              </label>
              <input
                id={`${titleId}-end`}
                type="date"
                value={endDate}
                min={startDate || undefined}
                onChange={(event) => setEndDate(event.target.value)}
                className="input-field"
                aria-invalid={Boolean(errors.endDate)}
                aria-describedby={errors.endDate ? `${titleId}-end-error` : undefined}
              />
              {errors.endDate && (
                <p id={`${titleId}-end-error`} role="alert" className="text-xs text-terracotta">
                  {errors.endDate}
                </p>
              )}
            </div>
          </div>

          {errors.form && (
            <p
              role="alert"
              className="rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-xs text-terracotta"
            >
              {errors.form}
            </p>
          )}

          <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button type="button" onClick={onClose} className={GHOST_BUTTON} disabled={isCreating}>
              Cancel
            </button>
            <button
              type="submit"
              className={PRIMARY_BUTTON}
              disabled={isCreating}
              aria-busy={isCreating}
            >
              {isCreating ? 'Creating…' : 'Create trip'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
