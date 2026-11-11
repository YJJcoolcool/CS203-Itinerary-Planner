import { useEffect, useId } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'

import { useBodyScrollLock } from '../lib/useBodyScrollLock'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

/** In-app replacement for `window.confirm`, styled like DeleteTripDialog. */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onClose: () => void
}) {
  const titleId = useId()

  useBodyScrollLock()

  // Captured so Escape closes only this dialog, not a modal underneath it.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.stopPropagation()
      onClose()
    }
    document.addEventListener('keydown', onKeyDown, true)
    return () => document.removeEventListener('keydown', onKeyDown, true)
  }, [onClose])

  return createPortal(
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center overflow-y-auto bg-black/60 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md rounded-2xl border border-border bg-surface-raised p-6 shadow-2xl"
      >
        <h2 id={titleId} className="font-display text-2xl font-medium text-foreground">
          {title}
        </h2>

        <p className="mt-3 text-sm leading-relaxed text-muted">{children}</p>

        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className={`rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted hover:text-foreground ${FOCUS_RING}`}
          >
            Cancel
          </button>
          <button
            type="button"
            autoFocus
            onClick={onConfirm}
            className={`rounded-xl bg-terracotta px-4 py-2.5 text-sm font-semibold text-white hover:opacity-90 ${FOCUS_RING}`}
          >
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>,
    document.body,
  )
}
