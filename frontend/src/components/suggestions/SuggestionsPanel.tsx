import { useState } from 'react'
import { useSuggestions } from '../../lib/useSuggestions'
import type { Suggestion } from '../../types/suggestion'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

type Decision = 'accept' | 'reject'

function LocationColumn({
  label,
  name,
  category,
}: {
  label: string
  name: string
  category: string | null
}) {
  return (
    <div className="min-w-0 flex-1">
      <p className="text-xs font-medium uppercase tracking-wide text-faint">{label}</p>
      <p className="mt-0.5 truncate text-sm font-medium text-foreground">{name}</p>
      {category && <p className="mt-0.5 truncate text-xs text-muted">{category}</p>}
    </div>
  )
}

export interface SuggestionsPanelProps {
  tripId: string
  canEdit: boolean
  onAccepted?: () => void
}

/**
 * Rain-driven alternatives for a trip. Editors can check for new suggestions
 * and accept or dismiss each one; everyone can see the pending list.
 */
export function SuggestionsPanel({ tripId, canEdit, onAccepted }: SuggestionsPanelProps) {
  const { suggestions, isLoading, error, isReplanning, replan, accept, reject, refresh } =
    useSuggestions(tripId)

  const [acting, setActing] = useState<{ id: string; decision: Decision } | null>(null)

  const pending = suggestions.filter((suggestion) => suggestion.status === 'PENDING')

  async function handleDecision(suggestion: Suggestion, decision: Decision) {
    if (acting) return

    setActing({ id: suggestion.id, decision })

    try {
      if (decision === 'accept') {
        await accept(suggestion.id)
        onAccepted?.()
      } else {
        await reject(suggestion.id)
      }
    } catch {
      // The hook records a friendly message in `error`, shown below.
    } finally {
      setActing(null)
    }
  }

  return (
    <section className="mt-10" aria-labelledby="suggestions-heading">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2
            id="suggestions-heading"
            className="font-display text-2xl font-medium tracking-tight text-foreground"
          >
            Weather alternatives
          </h2>
          <p className="mt-1 text-sm text-muted">
            Indoor swaps for outdoor stops when rain is expected.
          </p>
        </div>

        {canEdit && (
          <button
            type="button"
            onClick={() => void replan()}
            disabled={isReplanning}
            aria-busy={isReplanning}
            className={SECONDARY_BUTTON}
          >
            {isReplanning ? 'Checking…' : 'Check now'}
          </button>
        )}
      </div>

      <div className="mt-4" aria-live="polite" aria-busy={isReplanning}>
        {error && (
          <div
            role="alert"
            className="mb-4 flex flex-col items-start gap-3 rounded-2xl border border-terracotta/40 bg-terracotta/10 p-5 sm:flex-row sm:items-center sm:justify-between"
          >
            <p className="text-sm text-muted">{error}</p>
            <button type="button" onClick={refresh} className={SECONDARY_BUTTON}>
              Retry
            </button>
          </div>
        )}

        {isLoading ? (
          <p className="text-sm text-muted">Loading weather alternatives…</p>
        ) : pending.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center">
            <p className="text-sm font-medium text-foreground">
              No weather-related changes right now.
            </p>
            <p className="mt-1 text-sm text-muted">
              When rain is expected at an outdoor stop, an indoor alternative will appear here.
            </p>
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {pending.map((suggestion) => {
              const isActing = acting?.id === suggestion.id
              const isAccepting = isActing && acting.decision === 'accept'
              const isDismissing = isActing && acting.decision === 'reject'

              return (
                <li
                  key={suggestion.id}
                  className="animate-rise flex flex-col gap-4 rounded-2xl border border-border bg-surface-raised p-5 sm:p-6"
                >
                  <div>
                    <h3 className="font-display text-xl font-medium tracking-tight text-foreground">
                      {suggestion.activityName}
                    </h3>
                    <p className="mt-1 text-sm text-muted">{suggestion.reason}</p>
                  </div>

                  <div className="flex flex-col gap-3 rounded-xl border border-border bg-surface-subtle px-4 py-3 sm:flex-row sm:items-center sm:gap-5">
                    <LocationColumn
                      label="Current stop"
                      name={suggestion.currentLocation?.name ?? 'No location set'}
                      category={suggestion.currentLocation?.category ?? null}
                    />

                    <span aria-hidden="true" className="hidden shrink-0 text-muted sm:block">
                      →
                    </span>

                    <LocationColumn
                      label="Indoor alternative"
                      name={suggestion.proposedLocation.name}
                      category={suggestion.proposedLocation.category}
                    />
                  </div>

                  <p className="text-sm leading-relaxed text-muted">{suggestion.explanation}</p>

                  {canEdit && (
                    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                      <button
                        type="button"
                        onClick={() => void handleDecision(suggestion, 'reject')}
                        disabled={acting !== null}
                        aria-busy={isDismissing}
                        aria-label={`Dismiss suggestion for ${suggestion.activityName}`}
                        className={SECONDARY_BUTTON}
                      >
                        {isDismissing ? 'Dismissing…' : 'Dismiss'}
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleDecision(suggestion, 'accept')}
                        disabled={acting !== null}
                        aria-busy={isAccepting}
                        aria-label={`Accept alternative for ${suggestion.activityName}`}
                        className={PRIMARY_BUTTON}
                      >
                        {isAccepting ? 'Accepting…' : 'Accept alternative'}
                      </button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </section>
  )
}
