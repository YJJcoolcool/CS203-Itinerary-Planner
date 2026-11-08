import { useState } from 'react'
import { useSuggestions } from '../../lib/useSuggestions'
import type { Suggestion, SuggestionOption } from '../../types/suggestion'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

type Decision = 'accept' | 'reject' | 'undo'

/** How many applied swaps stay listed with an Undo button (CGT-169). */
const RECENTLY_APPLIED_LIMIT = 5

/**
 * Older suggestion payloads do not carry `options`. Build a single-option view
 * from the compatibility fields so the card stays usable.
 */
function optionsFor(suggestion: Suggestion): SuggestionOption[] {
  if (suggestion.options?.length) return suggestion.options

  return [
    {
      location: suggestion.proposedLocation,
      rank: 1,
      reason: suggestion.explanation,
      description: '',
      aiPick: false,
    },
  ]
}

export interface SuggestionsPanelProps {
  tripId: string
  canEdit: boolean
  /** Called after the itinerary changes: a suggestion was applied or undone. */
  onItineraryChanged?: () => void
}

/**
 * Rain-driven alternatives for a trip. Editors can check for new suggestions,
 * compare the ranked options for each stop, and apply one or dismiss the whole
 * suggestion; everyone can see the pending list. Recently applied swaps are
 * listed underneath so editors can undo them (CGT-169).
 */
export function SuggestionsPanel({ tripId, canEdit, onItineraryChanged }: SuggestionsPanelProps) {
  const { suggestions, isLoading, error, isReplanning, replan, accept, reject, undo, refresh } =
    useSuggestions(tripId)

  const [acting, setActing] = useState<{ id: string; decision: Decision } | null>(null)
  const [selectedBySuggestion, setSelectedBySuggestion] = useState<Record<string, string>>({})
  // Outcome of the last "Check now", so a check that finds nothing still says so.
  const [checkResult, setCheckResult] = useState<string | null>(null)

  const pending = suggestions.filter((suggestion) => suggestion.status === 'PENDING')

  // Newest first (the API orders by creation); only rows that know both places can be undone.
  const recentlyApplied = suggestions
    .filter((suggestion) =>
      suggestion.status === 'ACCEPTED' && suggestion.acceptedLocation && suggestion.currentLocation)
    .slice(0, RECENTLY_APPLIED_LIMIT)

  async function handleCheckNow() {
    setCheckResult(null)
    const created = await replan()
    if (created === null) return // the error banner explains what went wrong

    setCheckResult(
      created === 0
        ? 'Checked just now: no new changes needed for the next 24 hours.'
        : `Checked just now: found ${created} new ${created === 1 ? 'alternative' : 'alternatives'}.`,
    )
  }

  function handleSelect(suggestionId: string, locationId: string) {
    setSelectedBySuggestion((current) => ({ ...current, [suggestionId]: locationId }))
  }

  async function handleDecision(
    suggestion: Suggestion,
    decision: Decision,
    locationId?: string,
  ) {
    if (acting) return

    setActing({ id: suggestion.id, decision })

    try {
      if (decision === 'accept') {
        await accept(suggestion.id, locationId)
        onItineraryChanged?.()
      } else if (decision === 'undo') {
        await undo(suggestion.id)
        onItineraryChanged?.()
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
            onClick={() => void handleCheckNow()}
            disabled={isReplanning}
            aria-busy={isReplanning}
            className={SECONDARY_BUTTON}
          >
            {isReplanning ? 'Checking…' : 'Check now'}
          </button>
        )}
      </div>

      <div className="mt-4" aria-live="polite" aria-busy={isReplanning}>
        {checkResult && !isReplanning && (
          <p className="mb-3 text-sm text-muted">{checkResult}</p>
        )}

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
              const options = optionsFor(suggestion)
              const storedId = selectedBySuggestion[suggestion.id]
              const selectedOption =
                options.find((option) => option.location.id === storedId) ??
                options[0]
              const selectedId = selectedOption.location.id

              const isActing = acting?.id === suggestion.id
              const isBusy = acting !== null
              const isAccepting = isActing && acting.decision === 'accept'
              const isDismissing = isActing && acting.decision === 'reject'

              return (
                <li
                  key={suggestion.id}
                  aria-busy={isActing}
                  className="animate-rise flex flex-col gap-5 rounded-2xl border border-border bg-surface-raised p-5 sm:p-6"
                >
                  <div>
                    <h3 className="font-display text-xl font-medium tracking-tight text-foreground">
                      {suggestion.activityName}
                    </h3>
                    <p className="mt-1 text-sm text-muted">{suggestion.reason}</p>
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border border-border bg-surface-subtle px-4 py-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-faint">
                      Current stop
                    </p>
                    <p className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">
                      {suggestion.currentLocation?.name ?? 'No location set'}
                    </p>
                    {suggestion.currentLocation?.category && (
                      <span className="rounded-full border border-border px-2 py-0.5 text-xs text-muted">
                        {suggestion.currentLocation.category}
                      </span>
                    )}
                  </div>

                  <fieldset className="min-w-0" disabled={!canEdit || isBusy}>
                    <legend className="text-xs font-medium uppercase tracking-wide text-faint">
                      {options.length > 1
                        ? `Choose an alternative (${options.length})`
                        : 'Alternative'}
                    </legend>

                    <div className="mt-3 grid gap-3 lg:grid-cols-2">
                      {options.map((option) => {
                        const isSelected = option.location.id === selectedId

                        return (
                          <label
                            key={option.location.id}
                            className={`flex gap-3 rounded-xl border p-4 transition-colors has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-accent has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:ring-offset-surface-raised has-[:focus-visible]:outline-none ${
                              isSelected
                                ? 'border-accent bg-accent-soft'
                                : 'border-border bg-surface-subtle'
                            } ${
                              canEdit && !isBusy
                                ? 'cursor-pointer hover:border-border-strong'
                                : 'cursor-default'
                            }`}
                          >
                            <input
                              type="radio"
                              name={`suggestion-${suggestion.id}-option`}
                              value={option.location.id}
                              checked={isSelected}
                              onChange={() => handleSelect(suggestion.id, option.location.id)}
                              disabled={!canEdit || isBusy}
                              className="sr-only"
                            />

                            <span
                              aria-hidden="true"
                              className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-colors ${
                                isSelected ? 'border-accent' : 'border-border-strong'
                              }`}
                            >
                              {isSelected && (
                                <span className="h-2.5 w-2.5 rounded-full bg-accent" />
                              )}
                            </span>

                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                                <span className="font-medium text-foreground">
                                  {option.location.name}
                                </span>

                                {option.location.category && (
                                  <span className="rounded-full border border-border bg-surface-raised px-2 py-0.5 text-[11px] font-medium text-muted">
                                    {option.location.category}
                                  </span>
                                )}

                                {option.rank === 1 && (
                                  <span className="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-accent">
                                    Top pick
                                  </span>
                                )}
                              </div>

                              {option.reason && (
                                <p className="mt-2 text-sm font-medium leading-snug text-foreground">
                                  {option.reason}
                                </p>
                              )}

                              {option.description && option.description !== option.reason && (
                                <p className="mt-1 text-sm leading-relaxed text-muted">
                                  {option.description}
                                </p>
                              )}
                            </div>
                          </label>
                        )
                      })}
                    </div>
                  </fieldset>

                  {canEdit && (
                    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <button
                        type="button"
                        onClick={() => void handleDecision(suggestion, 'reject')}
                        disabled={isBusy}
                        aria-busy={isDismissing}
                        aria-label={`Dismiss suggestion for ${suggestion.activityName}`}
                        className={SECONDARY_BUTTON}
                      >
                        {isDismissing ? 'Dismissing…' : 'Dismiss'}
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          void handleDecision(suggestion, 'accept', selectedId)
                        }
                        disabled={isBusy}
                        aria-busy={isAccepting}
                        aria-label={`Apply ${selectedOption.location.name} for ${suggestion.activityName}`}
                        className={PRIMARY_BUTTON}
                      >
                        {isAccepting ? 'Applying…' : 'Apply this alternative'}
                      </button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}

        {!isLoading && recentlyApplied.length > 0 && (
          <div className="mt-6">
            <h3 className="text-xs font-medium uppercase tracking-wide text-muted">
              Recently applied
            </h3>
            <ul className="mt-2 divide-y divide-border rounded-2xl border border-border">
              {recentlyApplied.map((suggestion) => {
                const isUndoing = acting?.id === suggestion.id && acting.decision === 'undo'

                return (
                  <li
                    key={suggestion.id}
                    className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                  >
                    <p className="min-w-0 text-sm text-foreground">
                      <span className="font-medium">{suggestion.activityName}</span>
                      {': '}
                      <span className="text-muted line-through">{suggestion.currentLocation?.name}</span>
                      {' → '}
                      {suggestion.acceptedLocation?.name}
                    </p>

                    {canEdit && (
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm(
                            `Undo this change and put "${suggestion.currentLocation?.name}" back in "${suggestion.activityName}"?`,
                          )) {
                            void handleDecision(suggestion, 'undo')
                          }
                        }}
                        disabled={acting !== null}
                        aria-busy={isUndoing}
                        aria-label={`Undo the change to ${suggestion.activityName}`}
                        className={SECONDARY_BUTTON}
                      >
                        {isUndoing ? 'Undoing…' : 'Undo'}
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
        )}
      </div>
    </section>
  )
}
