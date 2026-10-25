import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { useImportLocationFromOsm, useLocationSearch } from '../../lib/useTrips'
import type { GeoPlace, Location } from '../../types/trip'
import ManualLocationModal from './ManualLocationModal'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const ADD_BUTTON = `inline-flex shrink-0 items-center justify-center rounded-lg border border-border bg-surface-subtle px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-accent hover:text-accent disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const DONE_BUTTON = `inline-flex shrink-0 items-center justify-center rounded-xl border border-border px-4 py-2 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`

const ICON_BUTTON = `flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border bg-surface-subtle text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const PILL =
  'inline-flex items-center rounded-full border border-border bg-surface-subtle px-2.5 py-0.5 font-mono text-[11px] text-muted'

/** Required OpenStreetMap licensing link shown wherever search results appear. */
const OSM_COPYRIGHT_URL = 'https://www.openstreetmap.org/copyright'
const OSM_ATTRIBUTION_FALLBACK = '© OpenStreetMap contributors'

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function getFocusable(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
}

function SearchIcon({ className = 'h-4 w-4' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}

function CloseIcon() {
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
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  )
}

/**
 * OpenStreetMap place picker.
 *
 * The search lives in its own dialog rather than inline: a full-width trigger
 * opens a popup (portalled to `<body>`, `z-[60]`) that stacks above the parent
 * "Add activity" modal. Keeping the input and its results inside a dedicated
 * dialog guarantees the debounced results can never overflow the parent's edge
 * at any viewport width.
 *
 * Interaction model: the popup STAYS OPEN after choosing a result. Adding a
 * place is a toggle-like action — the row then shows "Added" and is disabled —
 * so users can import several places from one search session. The dialog closes
 * via its Close/Done buttons, Escape, or a backdrop click.
 */
export function LocationSearch({
  tripId,
  isAdded,
  onAdded,
}: {
  tripId: string
  isAdded: (place: GeoPlace) => boolean
  onAdded: (location: Location) => void
}) {
  const { query, setQuery, results, attribution, isSearching, error, hasSearched } =
    useLocationSearch(tripId)
  const { importLocation, importingKey, error: importError } = useImportLocationFromOsm()

  const [open, setOpen] = useState(false)
  const [isManualLocationOpen, setIsManualLocationOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const wasOpen = useRef(false)

  const inputId = useId()
  const titleId = `${inputId}-title`
  const errorId = `${inputId}-error`
  const listLabelId = `${inputId}-results-label`
  const attributionId = `${inputId}-attribution`

  const trimmed = query.trim()
  const isIdle = trimmed.length === 0
  const showResults = !isIdle && !isSearching && !error && results.length > 0
  const showEmpty = !isIdle && !isSearching && !error && hasSearched && results.length === 0
  const isImporting = importingKey !== null

  const status = isSearching
    ? 'Searching…'
    : showEmpty
      ? `No matches for ${trimmed}.`
      : showResults
        ? `${results.length} ${results.length === 1 ? 'result' : 'results'} for ${trimmed}.`
        : ''

  // Move focus into the dialog when it opens.
  useEffect(() => {
    if (open) inputRef.current?.focus()
  }, [open])

  // Restore focus to the trigger when the dialog closes.
  useEffect(() => {
    if (open) {
      wasOpen.current = true
      return
    }
    if (wasOpen.current) {
      wasOpen.current = false
      triggerRef.current?.focus()
    }
  }, [open])

  // Lock body scroll while the dialog is open.
  useEffect(() => {
    if (!open) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [open])

  // Own Escape in the capture phase so it never also reaches (and closes) the
  // parent "Add activity" modal. Escape is ignored mid-import.
  useEffect(() => {
    if (!open) return

    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.stopPropagation()
      event.preventDefault()
      if (!isImporting) setOpen(false)
    }

    document.addEventListener('keydown', onKeyDown, true)
    return () => document.removeEventListener('keydown', onKeyDown, true)
  }, [open, isImporting])

  /** Keeps Tab focus cycling inside the dialog while it is open. */
  function handleDialogKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return

    const focusable = getFocusable(dialogRef.current)
    if (focusable.length === 0) {
      event.preventDefault()
      return
    }

    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    const active = document.activeElement
    const inside = dialogRef.current?.contains(active) ?? false

    if (event.shiftKey) {
      if (!inside || active === first) {
        event.preventDefault()
        last.focus()
      }
    } else if (!inside || active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  async function handleAdd(place: GeoPlace) {
    try {
      const location = await importLocation(place)
      onAdded(location)
      // The row's Add button becomes disabled once added, which would otherwise
      // drop focus to the document; return it to the search field so the user
      // can keep browsing results.
      inputRef.current?.focus()
    } catch {
      // The hook records the message in `importError`; nothing else to do here.
    }
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        aria-expanded={open}
        className={`input-field flex items-center gap-2 text-left text-faint transition-colors hover:border-border-strong hover:text-muted ${FOCUS_RING}`}
      >
        <SearchIcon className="h-4 w-4 shrink-0" />
        <span className="truncate">Search for a place…</span>
      </button>

      {open &&
        createPortal(
          <div
            className="fixed inset-0 z-60 flex items-end justify-center bg-black/60 backdrop-blur-sm sm:items-center sm:p-4"
            onMouseDown={(event) => {
              if (event.target === event.currentTarget) setOpen(false)
            }}
          >
            <div
              ref={dialogRef}
              role="dialog"
              aria-modal="true"
              aria-labelledby={titleId}
              onKeyDown={handleDialogKeyDown}
              className="animate-rise flex max-h-[90vh] w-full flex-col overflow-hidden rounded-t-2xl border border-border bg-surface-raised shadow-2xl motion-reduce:animate-none sm:max-w-lg sm:rounded-2xl"
            >
              <div className="flex items-start justify-between gap-4 border-b border-border p-4 sm:p-6">
                <div className="min-w-0">
                  <h2
                    id={titleId}
                    className="font-display text-xl font-medium tracking-tight text-foreground"
                  >
                    Add a place
                  </h2>
                  <p className="mt-1 text-xs text-muted">
                    Search OpenStreetMap and add a location to this activity.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  aria-label="Close search"
                  className={ICON_BUTTON}
                >
                  <CloseIcon />
                </button>
              </div>

              <div className="flex flex-col gap-2 px-4 sm:px-6 sm:flex-row">
                <div className="min-w-0 flex-1">
                  <label htmlFor={inputId} className="text-xs font-medium text-muted">
                    Search for a place
                  </label>

                  <input
                    ref={inputRef}
                    id={inputId}
                    type="search"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    className="input-field"
                    placeholder="e.g. Marina Bay Sands"
                    autoComplete="off"
                    spellCheck={false}
                    aria-invalid={Boolean(error)}
                    aria-describedby={error ? errorId : attributionId}
                  />

                  {error && (
                    <p id={errorId} role="alert" className="text-xs text-terracotta">
                      {error}
                    </p>
                  )}
                </div>

                <div className="flex items-end sm:items-end">
                  <button
                    type="button"
                    onClick={() => setIsManualLocationOpen(true)}
                    className={`${GHOST_BUTTON} h-10 whitespace-nowrap`}
                  >
                    Add Manually
                  </button>
                </div>
              </div>

              <div aria-busy={isSearching} className="min-h-0 flex-1 overflow-y-auto p-4 sm:px-6">
                <p role="status" className="sr-only">
                  {status}
                </p>

                {isIdle && !error && (
                  <div className="flex flex-col items-center gap-1 py-10 text-center">
                    <SearchIcon className="h-6 w-6 text-faint" />
                    <p className="text-sm text-muted">Start typing to find a place.</p>
                    <p className="text-xs text-faint">Results come from OpenStreetMap.</p>
                  </div>
                )}

                {isSearching && (
                  <ul aria-hidden="true" className="flex flex-col gap-2">
                    {[0, 1, 2].map((row) => (
                      <li
                        key={row}
                        className="h-14 animate-pulse rounded-xl border border-border bg-surface-subtle motion-reduce:animate-none"
                      />
                    ))}
                  </ul>
                )}

                {showEmpty && <p className="py-6 text-center text-sm text-muted">No matches for “{trimmed}”.</p>}

                {isManualLocationOpen && (
                  <ManualLocationModal
                    onClose={() => setIsManualLocationOpen(false)}
                    onCreated={(location) => {
                      onAdded(location)
                    }}
                  />
                )}

                {showResults && (
                  <div>
                    <p id={listLabelId} className="sr-only">
                      Search results
                    </p>
                    <ul
                      aria-labelledby={listLabelId}
                      aria-describedby={attributionId}
                      className="flex flex-col divide-y divide-border overflow-hidden rounded-xl border border-border"
                    >
                      {results.map((place) => {
                        const key = `${place.osmType}:${place.osmId}`
                        const added = isAdded(place)
                        const importing = importingKey === key
                        const label = place.name ?? place.displayName ?? 'this place'

                        return (
                          <li key={key} className="flex items-start justify-between gap-3 p-3">
                            <div className="min-w-0">
                              <p className="truncate text-sm text-foreground">{label}</p>
                              {place.displayName && place.displayName !== label && (
                                <p className="truncate text-xs text-muted">{place.displayName}</p>
                              )}
                              {place.category && (
                                <span className={`${PILL} mt-1.5`}>{place.category}</span>
                              )}
                            </div>
                            <button
                              type="button"
                              onClick={() => void handleAdd(place)}
                              disabled={added || importing}
                              aria-label={added ? `${label} added` : `Add ${label}`}
                              className={ADD_BUTTON}
                            >
                              {added ? 'Added' : importing ? 'Adding…' : 'Add'}
                            </button>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                )}

                {importError && (
                  <p role="alert" className="mt-3 text-xs text-terracotta">
                    {importError}
                  </p>
                )}
              </div>

              <div className="flex items-center justify-between gap-3 border-t border-border p-4 sm:px-6">
                <p id={attributionId} className="min-w-0 truncate text-[11px] text-faint">
                  <a
                    href={OSM_COPYRIGHT_URL}
                    target="_blank"
                    rel="noreferrer"
                    className={`rounded underline underline-offset-2 transition-colors hover:text-foreground ${FOCUS_RING}`}
                  >
                    {attribution ?? OSM_ATTRIBUTION_FALLBACK}
                  </a>
                </p>
                <button type="button" onClick={() => setOpen(false)} className={DONE_BUTTON}>
                  Done
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  )
}
