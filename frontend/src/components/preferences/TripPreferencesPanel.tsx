import { useState } from 'react'

import { useTripPreferences } from '../../lib/usePreferences'
import { TRIP_GOALS, TRIP_GOAL_LABELS } from '../../types/preference'
import type { TripGoal } from '../../types/preference'
import type { Trip } from '../../types/trip'
import { uniqueMembers } from '../trips/tripUtils'
import { CollapsibleSection } from '../CollapsibleSection'
import { PreferenceChip } from './PreferenceChip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const SMALL_SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-3 py-2 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta'

function sortedUnique(values: string[]): string[] {
  return [...new Set(values)].sort()
}

function formatUpdatedAt(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''

  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/**
 * The trip's shared group preference: goal chips plus the cached AI summary.
 * Everyone on the trip can read it; only the owner and editors can change it.
 */
export function TripPreferencesPanel({
  trip,
  canEdit,
}: {
  trip: Trip
  /** True for OWNER and EDITOR (from `roleOf`). Viewers get a read-only panel. */
  canEdit: boolean
}) {
  const {
    preferences,
    isLoading,
    error,
    isSaving,
    isSummarising,
    actionError,
    save,
    summarise,
    refresh,
  } = useTripPreferences(trip.id)

  // Null until the user edits, so the loaded goals are shown without an extra
  // render and unsaved edits survive a background refresh.
  const [editedGoals, setEditedGoals] = useState<TripGoal[] | null>(null)
  const [status, setStatus] = useState<string | null>(null)

  const goals = editedGoals ?? preferences?.goals ?? []
  const goalsDirty =
    JSON.stringify(sortedUnique(goals)) !==
    JSON.stringify(sortedUnique(preferences?.goals ?? []))

  const updatedBy = preferences?.updatedBy
    ? uniqueMembers(trip).find((member) => member.id === preferences.updatedBy)
    : undefined

  function toggleGoal(goal: TripGoal) {
    setStatus(null)
    setEditedGoals((current) => {
      const base = current ?? preferences?.goals ?? []
      return base.includes(goal)
        ? base.filter((item) => item !== goal)
        : [...base, goal]
    })
  }

  async function handleSave() {
    if (!canEdit || isSaving) return

    setStatus(null)
    try {
      await save({ goals, tags: preferences?.tags ?? [] })
      // Re-sync with the saved copy so the panel and server cannot drift.
      setEditedGoals(null)
      setStatus('Group goals saved.')
    } catch {
      // The hook records a friendly message in `actionError`, shown below.
    }
  }

  async function handleSummarise() {
    if (!canEdit || isSaving || isSummarising || goalsDirty) return

    setStatus(null)
    try {
      await summarise()
      setStatus('Summary regenerated.')
    } catch {
      // The hook records a friendly message in `actionError`, shown below.
    }
  }

  return (
    <CollapsibleSection
      title="Group preferences"
      subtitle="Goals for this trip and a shared summary of what everyone likes."
      aside={
        preferences?.summaryStale && (
          <span className="inline-flex items-center gap-1.5 rounded-full border border-amber/40 bg-amber/10 px-2.5 py-1 text-xs font-medium text-foreground">
            <span className="h-1.5 w-1.5 rounded-full bg-amber" aria-hidden="true" />
            Needs update
          </span>
        )
      }
    >

      {isLoading ? (
        <p role="status" className="mt-5 text-sm text-muted">
          Loading group preferences…
        </p>
      ) : error ? (
        <div
          role="alert"
          className="mt-5 flex flex-col items-start gap-3 rounded-2xl border border-terracotta/40 bg-terracotta/10 p-4"
        >
          <p className="text-sm text-muted">{error}</p>
          <button type="button" onClick={refresh} className={SMALL_SECONDARY_BUTTON}>
            Retry
          </button>
        </div>
      ) : preferences ? (
        <>
          <div className="mt-5">
            <h3 className="text-sm font-medium text-foreground">Trip goals</h3>
            <p className="mt-1 text-sm text-muted">
              Pick the kinds of places the group wants to visit.
            </p>

            {canEdit ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {TRIP_GOALS.map((goal) => (
                  <PreferenceChip
                    key={goal}
                    label={TRIP_GOAL_LABELS[goal]}
                    selected={goals.includes(goal)}
                    onToggle={() => toggleGoal(goal)}
                    disabled={isSaving}
                  />
                ))}
              </div>
            ) : goals.length > 0 ? (
              <div className="mt-3 flex flex-wrap gap-2">
                {goals.map((goal) => (
                  <PreferenceChip
                    key={goal}
                    label={TRIP_GOAL_LABELS[goal] ?? goal}
                    selected
                  />
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-muted">No trip goals set yet.</p>
            )}

            {canEdit && (
              <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <button
                  type="button"
                  onClick={() => void handleSave()}
                  disabled={!goalsDirty || isSaving}
                  aria-busy={isSaving}
                  className={PRIMARY_BUTTON}
                >
                  {isSaving ? 'Saving…' : 'Save goals'}
                </button>

                {goalsDirty && (
                  <p className="text-xs text-muted">
                    Save your goal changes before regenerating the summary.
                  </p>
                )}
              </div>
            )}
          </div>

          {actionError && (
            <p role="alert" className={`mt-4 ${ERROR_ALERT}`}>
              {actionError}
            </p>
          )}

          {status && (
            <p role="status" className="mt-4 text-sm text-sage">
              {status}
            </p>
          )}

          <div className="mt-5 rounded-xl border border-border bg-surface-subtle p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <h3 className="text-xs font-medium uppercase tracking-wide text-muted">
                Group summary
              </h3>

              {canEdit && (
                <button
                  type="button"
                  onClick={() => void handleSummarise()}
                  disabled={isSummarising || isSaving || goalsDirty}
                  aria-busy={isSummarising}
                  title={goalsDirty ? 'Save goal changes first' : undefined}
                  className={SMALL_SECONDARY_BUTTON}
                >
                  {isSummarising ? 'Regenerating…' : 'Regenerate summary'}
                </button>
              )}
            </div>

            {preferences.summary ? (
              <p className="mt-3 text-sm leading-relaxed text-foreground">
                {preferences.summary}
              </p>
            ) : (
              <p className="mt-3 text-sm text-muted">
                No summary yet. An owner or editor can generate one.
              </p>
            )}

            {preferences.summary && preferences.summaryStale && (
              <p className="mt-3 text-xs text-terracotta">
                This summary no longer reflects the latest goals or member preferences.
              </p>
            )}

            <p className="mt-3 text-xs text-faint">
              Updated {formatUpdatedAt(preferences.updatedAt)}
              {updatedBy
                ? ` by ${updatedBy.displayName ?? 'a traveler'}`
                : ''}
            </p>
          </div>

          {!canEdit && (
            <p className="mt-4 text-xs text-muted">
              Only the owner and editors can change the goals or regenerate the summary.
            </p>
          )}
        </>
      ) : (
        <p className="mt-5 text-sm text-muted">
          Group preferences are unavailable right now.
        </p>
      )}
    </CollapsibleSection>
  )
}
