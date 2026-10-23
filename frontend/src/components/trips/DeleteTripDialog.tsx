import { useEffect, useId, useState } from 'react'
import { createPortal } from 'react-dom'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

export function DeleteTripDialog({
  tripId,
  tripName,
  isDeleting = false,
  error = null,
  onClose,
  onConfirm,
}: {
  tripId: string
  tripName: string
  /** Optional: the trip settings panel hands the delete to My Trips instead of waiting here. */
  isDeleting?: boolean
  error?: string | null
  onClose: () => void
  onConfirm: () => void
}) {
  const [confirmation, setConfirmation] = useState('')
  const titleId = useId()
  const inputId = useId()

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isDeleting) onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isDeleting, onClose])

  const canDelete = confirmation === tripId && !isDeleting

  return createPortal(
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center overflow-y-auto bg-black/60 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isDeleting) {
          onClose()
        }
      }}
    >
      <section
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md rounded-2xl border border-border bg-surface-raised p-6 shadow-2xl"
      >
        <h2
          id={titleId}
          className="font-display text-2xl font-medium text-foreground"
        >
          Delete trip?
        </h2>

        <p className="mt-3 text-sm leading-relaxed text-muted">
          You are about to permanently delete{' '}
          <strong className="text-foreground">{tripName}</strong>.
          This action cannot be undone.
        </p>

        <p className="mt-3 text-sm text-muted">
          Type this trip ID to confirm:
        </p>

        <code className="mt-2 block break-all rounded-lg bg-surface-subtle p-3 text-sm text-foreground">
          {tripId}
        </code>

        <label
          htmlFor={inputId}
          className="mt-4 block text-sm font-medium text-foreground"
        >
          Trip ID confirmation
        </label>

        <input
          id={inputId}
          autoFocus
          autoComplete="off"
          spellCheck={false}
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          placeholder="Enter the exact trip ID"
          disabled={isDeleting}
          className="input-field mt-2 w-full font-mono text-sm"
        />

        {error && (
          <p role="alert" className="mt-3 text-sm text-terracotta">
            {error}
          </p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            disabled={isDeleting}
            onClick={onClose}
            className={`rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`}
          >
            Cancel
          </button>

          <button
            type="button"
            disabled={!canDelete}
            onClick={onConfirm}
            className={`rounded-xl bg-terracotta px-4 py-2.5 text-sm font-semibold text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40 ${FOCUS_RING}`}
          >
            {isDeleting ? 'Deleting…' : 'Delete permanently'}
          </button>
        </div>
      </section>
    </div>,
    document.body,
  )
}