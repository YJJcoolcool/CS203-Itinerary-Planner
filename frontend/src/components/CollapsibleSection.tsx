import { useId, useState } from 'react'
import type { ReactNode } from 'react'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

// Card whose whole header row toggles the body.
// Starts collapsed unless defaultOpen is set.
// `aside` is rendered inside the toggle button, so keep it non-interactive.
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
      className="mt-6 rounded-2xl border border-border bg-surface-raised p-6 transition-[border-color,box-shadow] duration-200 ease-out has-[h2>button:focus-visible]:border-border-strong has-[h2>button:hover]:border-border-strong has-[h2>button:hover]:shadow-md has-[h2>button:hover]:shadow-black/10 has-[h2>button:active]:border-border-strong motion-reduce:transition-none dark:has-[h2>button:hover]:shadow-black/30"
    >
      <h2 id={headingId}>
        <button
          type="button"
          onClick={() => setIsOpen((open) => !open)}
          aria-expanded={isOpen}
          aria-controls={bodyId}
          className={`group/header -mx-4 -my-3 flex w-[calc(100%_+_2rem)] cursor-pointer flex-wrap items-start justify-between gap-3 rounded-xl px-4 py-3 text-left transition-[background-color,transform] duration-200 ease-out hover:bg-accent-soft active:translate-y-px active:bg-accent/20 active:duration-75 motion-reduce:transition-none ${FOCUS_RING}`}
        >
          <span className="min-w-0">
            <span className="flex items-center gap-2 font-display text-xl font-medium tracking-tight text-foreground">
              <svg
                aria-hidden="true"
                viewBox="0 0 20 20"
                fill="currentColor"
                className={`h-5 w-5 shrink-0 text-muted transition-[transform,color] duration-200 group-hover/header:text-accent group-active/header:text-accent-strong motion-reduce:transition-none ${isOpen ? 'rotate-90' : ''}`}
              >
                <path fillRule="evenodd" d="M7.2 14.8a.75.75 0 0 1 0-1.06L10.94 10 7.2 6.26a.75.75 0 1 1 1.06-1.06l4.27 4.27a.75.75 0 0 1 0 1.06l-4.27 4.27a.75.75 0 0 1-1.06 0Z" clipRule="evenodd" />
              </svg>
              {title}
            </span>
            {subtitle && (
              <span className="mt-1 block text-sm text-muted">{subtitle}</span>
            )}
          </span>
          {aside ? (
            <span className="flex shrink-0 items-center">{aside}</span>
          ) : null}
        </button>
      </h2>

      {isOpen && (
        <div id={bodyId} className="animate-rise">
          {children}
        </div>
      )}
    </section>
  )
}
