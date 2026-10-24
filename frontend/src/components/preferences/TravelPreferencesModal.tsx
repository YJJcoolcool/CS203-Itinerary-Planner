import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'

import { useMyPreferences } from '../../lib/usePreferences'
import {
  INDOOR_PREFERENCES,
  INDOOR_PREFERENCE_LABELS,
  INTEREST_OPTIONS,
} from '../../types/preference'
import type { IndoorPreference } from '../../types/preference'
import { PreferenceChip } from './PreferenceChip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const SECONDARY_BUTTON =
  `inline-flex items-center justify-center rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const ERROR_ALERT =
  'rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta'

const PRESET_INTERESTS: readonly string[] = INTEREST_OPTIONS

/** Lowercase, trim, drop blanks and duplicates while keeping the entered order. */
function normalizeTags(tags: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []

  for (const tag of tags) {
    const value = tag.trim().toLowerCase()
    if (!value || seen.has(value)) continue
    seen.add(value)
    result.push(value)
  }

  return result
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

function interestLabel(tag: string): string {
  return tag.charAt(0).toUpperCase() + tag.slice(1)
}

/** Treat a missing or unrecognised stored value as "no preference". */
function normalizeIndoor(value: string | null | undefined): IndoorPreference {
  return value === 'INDOOR' || value === 'OUTDOOR' ? value : 'ANY'
}

/** Modal for the signed-in user's own travel preferences. */
export function TravelPreferencesModal({ onClose }: { onClose: () => void }) {
  const {
    preferences,
    isLoading,
    error,
    isSaving,
    saveError,
    save,
    refresh,
  } = useMyPreferences()

  const [interests, setInterests] = useState<string[]>([])
  const [maxWalk, setMaxWalk] = useState('')
  const [dietary, setDietary] = useState('')
  const [indoor, setIndoor] = useState<IndoorPreference>('ANY')
  const [notes, setNotes] = useState('')
  const [customInterest, setCustomInterest] = useState('')
  const [walkError, setWalkError] = useState<string | null>(null)
  const [isSaved, setIsSaved] = useState(false)

  const titleId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)

  // Fill the form once the server copy arrives (and after a save). Copying an
  // external server value into editable form state is intentional; the rule
  // cannot tell it apart from a cascading render, so suppress it for this block.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!preferences) return

    setInterests(preferences.tags ?? [])
    setMaxWalk(
      preferences.maxWalkKm === null || preferences.maxWalkKm === undefined
        ? ''
        : String(preferences.maxWalkKm),
    )
    setDietary(preferences.dietary ?? '')
    setIndoor(normalizeIndoor(preferences.indoorPreference))
    setNotes(preferences.notes ?? '')
  }, [preferences])
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    headingRef.current?.focus()

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isSaving) onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isSaving, onClose])

  function toggleInterest(tag: string) {
    setIsSaved(false)
    setInterests((current) => {
      const exists = current.some((item) => item.toLowerCase() === tag.toLowerCase())
      return exists ? current.filter((item) => item.toLowerCase() !== tag.toLowerCase()) : [...current, tag]
    })
  }

  function addCustomInterest() {
    const value = customInterest.trim().toLowerCase()
    if (!value) return

    setIsSaved(false)
    setInterests((current) => normalizeTags([...current, value]))
    setCustomInterest('')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (isSaving) return

    const walkValue = maxWalk.trim() === '' ? null : Number(maxWalk)

    if (
      walkValue !== null &&
      (!Number.isFinite(walkValue) || walkValue < 0 || walkValue > 10)
    ) {
      setWalkError('Enter a distance between 0 and 10 km, or leave it blank.')
      return
    }

    setWalkError(null)
    setIsSaved(false)

    try {
      await save({
        tags: normalizeTags(interests),
        maxWalkKm: walkValue,
        dietary: dietary.trim() || null,
        indoorPreference: indoor,
        notes: notes.trim() || null,
      })
      setIsSaved(true)
    } catch {
      // The hook records a friendly message in `saveError`, shown below.
    }
  }

  const normalizedTags = normalizeTags(interests)
  const storedTags = normalizeTags(preferences?.tags ?? [])
  const tagsDirty =
    JSON.stringify([...normalizedTags].sort()) !== JSON.stringify([...storedTags].sort())

  const walkValue = maxWalk.trim() === '' ? null : Number(maxWalk)
  const walkDirty = walkValue !== (preferences?.maxWalkKm ?? null)
  const dietaryDirty =
    (dietary.trim() || null) !== (preferences?.dietary?.trim() || null)
  const indoorDirty = indoor !== normalizeIndoor(preferences?.indoorPreference)
  const notesDirty = (notes.trim() || null) !== (preferences?.notes?.trim() || null)
  const dirty = tagsDirty || walkDirty || dietaryDirty || indoorDirty || notesDirty

  const extraInterests = normalizedTags.filter(
    (tag) => !PRESET_INTERESTS.includes(tag),
  )
  const interestChoices = [...INTEREST_OPTIONS, ...extraInterests]

  const dialog = (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 p-3 backdrop-blur-sm sm:p-6"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isSaving) onClose()
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
              Travel preferences
            </h2>
            <p className="mt-1 text-sm text-muted">
              Used when suggesting places and indoor alternatives.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isSaving}
            aria-label="Close travel preferences"
            className={`rounded-lg p-2 text-muted hover:bg-surface-subtle hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`}
          >
            ✕
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto p-5 sm:p-6">
          {isLoading ? (
            <p role="status" className="text-sm text-muted">
              Loading your preferences…
            </p>
          ) : error ? (
            <div
              role="alert"
              className="flex flex-col items-start gap-3 rounded-2xl border border-terracotta/40 bg-terracotta/10 p-4"
            >
              <p className="text-sm text-muted">{error}</p>
              <button type="button" onClick={refresh} className={SECONDARY_BUTTON}>
                Retry
              </button>
            </div>
          ) : preferences ? (
            <form id="travel-preferences-form" onSubmit={handleSubmit} noValidate>
              <p className="rounded-xl border border-border bg-surface-subtle px-3 py-2 text-xs text-muted">
                Other signed-in travelers can see these fields. Your notes stay private.
              </p>

              <fieldset className="mt-6">
                <legend className="text-sm font-medium text-foreground">
                  Interests
                </legend>
                <p className="mt-1 text-sm text-muted">
                  Pick what you enjoy. Add your own if it is missing.
                </p>

                <div className="mt-3 flex flex-wrap gap-2">
                  {interestChoices.map((tag) => (
                    <PreferenceChip
                      key={tag}
                      label={interestLabel(tag)}
                      selected={normalizedTags.includes(tag)}
                      onToggle={() => toggleInterest(tag)}
                      disabled={isSaving}
                    />
                  ))}
                </div>

                <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center">
                  <label htmlFor={`${titleId}-custom-interest`} className="sr-only">
                    Add another interest
                  </label>
                  <input
                    id={`${titleId}-custom-interest`}
                    value={customInterest}
                    maxLength={32}
                    onChange={(event) => setCustomInterest(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' && customInterest.trim()) {
                        event.preventDefault()
                        addCustomInterest()
                      }
                    }}
                    placeholder="Add another interest"
                    autoComplete="off"
                    disabled={isSaving}
                    className="input-field sm:max-w-xs"
                  />
                  <button
                    type="button"
                    onClick={addCustomInterest}
                    disabled={isSaving || !customInterest.trim()}
                    className={SECONDARY_BUTTON}
                  >
                    Add
                  </button>
                </div>
              </fieldset>

              <div className="mt-6 grid gap-4 sm:grid-cols-2">
                <div>
                  <label
                    htmlFor={`${titleId}-max-walk`}
                    className="mb-1.5 block text-sm font-medium text-foreground"
                  >
                    Maximum walk (km)
                  </label>
                  <input
                    id={`${titleId}-max-walk`}
                    type="number"
                    min={0}
                    max={10}
                    step={0.5}
                    inputMode="decimal"
                    value={maxWalk}
                    onChange={(event) => {
                      setMaxWalk(event.target.value)
                      setWalkError(null)
                      setIsSaved(false)
                    }}
                    placeholder="No limit"
                    disabled={isSaving}
                    aria-invalid={Boolean(walkError)}
                    aria-describedby={
                      walkError
                        ? `${titleId}-max-walk-error`
                        : `${titleId}-max-walk-help`
                    }
                    className="input-field"
                  />
                  {walkError ? (
                    <p id={`${titleId}-max-walk-error`} role="alert" className="mt-1.5 text-xs text-terracotta">
                      {walkError}
                    </p>
                  ) : (
                    <p id={`${titleId}-max-walk-help`} className="mt-1.5 text-xs text-muted">
                      Optional, 0–10. Leave blank if distance is not a concern.
                    </p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor={`${titleId}-dietary`}
                    className="mb-1.5 block text-sm font-medium text-foreground"
                  >
                    Dietary needs
                  </label>
                  <input
                    id={`${titleId}-dietary`}
                    value={dietary}
                    maxLength={64}
                    onChange={(event) => {
                      setDietary(event.target.value)
                      setIsSaved(false)
                    }}
                    placeholder="e.g. vegetarian, no shellfish"
                    autoComplete="off"
                    disabled={isSaving}
                    className="input-field"
                  />
                </div>
              </div>

              <fieldset className="mt-6">
                <legend className="text-sm font-medium text-foreground">
                  Indoor or outdoor
                </legend>
                <p className="mt-1 text-sm text-muted">
                  Used when the planner chooses between a stop inside and a similar one outside.
                </p>

                <div className="mt-3 inline-flex rounded-xl border border-border bg-surface-subtle p-1">
                  {INDOOR_PREFERENCES.map((option) => {
                    const isSelected = indoor === option

                    return (
                      <button
                        key={option}
                        type="button"
                        aria-pressed={isSelected}
                        disabled={isSaving}
                        onClick={() => {
                          setIndoor(option)
                          setIsSaved(false)
                        }}
                        className={`rounded-lg px-3 py-1.5 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
                          isSelected
                            ? 'bg-accent text-accent-contrast'
                            : 'text-muted hover:text-foreground'
                        } ${FOCUS_RING}`}
                      >
                        {INDOOR_PREFERENCE_LABELS[option]}
                      </button>
                    )
                  })}
                </div>
              </fieldset>

              <div className="mt-6">
                <label
                  htmlFor={`${titleId}-notes`}
                  className="mb-1.5 block text-sm font-medium text-foreground"
                >
                  Notes
                </label>
                <textarea
                  id={`${titleId}-notes`}
                  rows={3}
                  maxLength={1024}
                  value={notes}
                  onChange={(event) => {
                    setNotes(event.target.value)
                    setIsSaved(false)
                  }}
                  placeholder="Anything else the group should know?"
                  disabled={isSaving}
                  className="input-field resize-y"
                />
                <p className="mt-1.5 text-xs text-muted">Only you can see this.</p>
              </div>

              {preferences.summary && (
                <section className="mt-6 rounded-xl border border-border bg-surface-subtle p-4">
                  <h3 className="text-xs font-medium uppercase tracking-wide text-muted">
                    AI summary
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-foreground">
                    {preferences.summary}
                  </p>
                  <p className="mt-3 text-xs text-faint">
                    Updated {formatUpdatedAt(preferences.updatedAt)}
                  </p>
                </section>
              )}

              {saveError && (
                <p role="alert" className={`mt-4 ${ERROR_ALERT}`}>
                  {saveError}
                </p>
              )}
              {isSaved && (
                <p role="status" className="mt-4 text-sm text-sage">
                  Preferences saved.
                </p>
              )}
            </form>
          ) : (
            <p className="text-sm text-muted">
              Your preferences are unavailable right now.
            </p>
          )}
        </div>

        <footer className="flex justify-end gap-2 border-t border-border p-4 sm:px-6">
          <button
            type="button"
            onClick={onClose}
            disabled={isSaving}
            className={SECONDARY_BUTTON}
          >
            Cancel
          </button>
          <button
            type="submit"
            form="travel-preferences-form"
            disabled={isSaving || !preferences || !dirty}
            aria-busy={isSaving}
            className={PRIMARY_BUTTON}
          >
            {isSaving ? 'Saving…' : 'Save changes'}
          </button>
        </footer>
      </section>
    </div>
  )

  return createPortal(dialog, document.body)
}
