import { useId, useState } from 'react'
import type { ReactNode } from 'react'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

// Card with a clickable title that shows or hides its body.
// Starts collapsed unless defaultOpen is set.
export function CollapsibleSection({
  title,
  subtitle,
  aside,
  defaultOpen = false,
  children,
}: {
  title: string
  subtitle?: ReactNode
  aside?: ReactNode
  defaultOpen?: boolean
  children: ReactNode
}) {
  const [isOpen, setIsOpen] = useState(defaultOpen)
  const headingId = useId()
  const bodyId = useId()

  return (
    <section
      aria-labelledby={headingId}
      className="mt-6 rounded-2xl border border-border bg-surface-raised p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 id={headingId} className="sr-only">
          {title}
        </h2>

        <button
          type="button"
          onClick={() => setIsOpen((open) => !open)}
          aria-expanded={isOpen}
          aria-controls={bodyId}
          aria-labelledby={headingId}
          className={`flex w-full items-start justify-between gap-3 rounded-lg text-left ${FOCUS_RING}`}
        >
          <span className="min-w-0 flex-1">
            <span className="flex items-center gap-2 font-display text-xl font-medium tracking-tight text-foreground">
              <svg
                aria-hidden="true"
                viewBox="0 0 20 20"
                fill="currentColor"
                className={`h-5 w-5 shrink-0 text-muted transition-transform ${isOpen ? 'rotate-90' : ''}`}
              >
                <path
                  fillRule="evenodd"
                  d="M7.2 14.8a.75.75 0 0 1 0-1.06L10.94 10 7.2 6.26a.75.75 0 1 1 1.06-1.06l4.27 4.27a.75.75 0 0 1 0 1.06l-4.27 4.27a.75.75 0 0 1-1.06 0Z"
                  clipRule="evenodd"
                />
              </svg>

              {title}
            </span>

            {subtitle && (
              <span className="mt-1 block text-sm text-muted">
                {subtitle}
              </span>
            )}
          </span>

          {aside && (
            <span className="shrink-0">
              {aside}
            </span>
          )}
        </button>
      </div>

      {isOpen && <div id={bodyId}>{children}</div>}
    </section>
  )
}
