
import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '@clerk/react'

import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import { ApiError, updateActivity } from '../../lib/api'
import { useCreateActivity } from '../../lib/useTrips'
import { LocationSearch } from '../locations/LocationSearch'
import type { Activity, GeoPlace, Location } from '../../types/trip'
import ManualLocationModal from '../locations/ManualLocationModal'
import { LocationOrderList } from './LocationOrderList'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

interface FieldErrors {
  name?: string
  startDateTime?: string
  endDateTime?: string
  locations?: string
  form?: string
}

function describeError(cause: unknown, isEditing: boolean): string {
  if (cause instanceof Error && cause.message) return cause.message
  return isEditing
    ? 'Could not save the activity. Please try again.'
    : 'Could not create the activity. Please try again.'
}

/** ISO date-time -> the "YYYY-MM-DDTHH:mm" local value a datetime-local input expects. */
function toDateTimeLocal(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * Add-activity form. Pass `activity` (and its current `activityLocations`) to
 * use it as the edit form instead: fields start filled in and Save sends a PUT.
 */
export function CreateActivityModal({
  tripId,
  activity,
  activityLocations,
  onClose,
  onCreated,
}: {
  tripId: string
  activity?: Activity
  activityLocations?: Location[]
  onClose: () => void
  onCreated: () => void
}) {
  const isEditing = Boolean(activity)
  const { getToken } = useAuth()
  const { createActivity, isCreating } = useCreateActivity(tripId)
  const [isSaving, setIsSaving] = useState(false)
  const isBusy = isCreating || isSaving
  const [name, setName] = useState(activity?.name ?? '')
  const [startDateTime, setStartDateTime] = useState(toDateTimeLocal(activity?.startDateTime ?? null))
  const [endDateTime, setEndDateTime] = useState(toDateTimeLocal(activity?.endDateTime ?? null))
  const [locations, setLocations] = useState<Location[]>(activityLocations ?? [])
  const [selectedLocationIds, setSelectedLocationIds] = useState<string[]>((activityLocations ?? []).map((location) => location.id),)
  const [isManualLocationOpen, setIsManualLocationOpen] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const nameRef = useRef<HTMLInputElement>(null)
  const titleId = useId()

  // Prevent background scrolling
  useBodyScrollLock();

  useEffect(() => {
    nameRef.current?.focus()
  }, [])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isBusy) onClose()
    }

    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [isBusy, onClose])

  function toggleLocation(id: string) {
    setSelectedLocationIds((current) =>
      current.includes(id)
        ? current.filter((selectedId) => selectedId !== id)
        : [...current, id],
    )
  }

  /** Adds an imported place to the list and selects it straight away. */
  function handleLocationAdded(location: Location) {
    setLocations((current) =>
      current.some((existing) => existing.id === location.id) ? current : [...current, location],
    )
    setSelectedLocationIds((current) =>
      current.includes(location.id) ? current : [...current, location.id],
    )
  }

  /** Whether a search result has already been imported (matched on OSM identity). */
  function isPlaceAdded(place: GeoPlace) {
    return locations.some(
      (location) => location.osmType === place.osmType && location.osmId === place.osmId,
    )
  }

  /** The ticked places in visit order, which is the order they are saved in (CGT-152). */
  const orderedLocations = selectedLocationIds
    .map((id) => locations.find((location) => location.id === id))
    .filter((location): location is Location => location !== undefined)

  /** Moves a ticked place from one position in the visit order to another. */
  function moveLocation(from: number, to: number) {
    if (to < 0 || to >= selectedLocationIds.length) return

    setSelectedLocationIds((current) => {
      const next = [...current]
      const [moved] = next.splice(from, 1)
      next.splice(to, 0, moved)
      return next
    })
  }

  function validate(): FieldErrors {
    const next: FieldErrors = {}

    if (!name.trim()) next.name = 'Give your activity a name.'
    if (
      startDateTime &&
      endDateTime &&
      new Date(endDateTime).getTime() < new Date(startDateTime).getTime()
    ) {
      next.endDateTime = 'End time must be on or after the start time.'
    }
    if (selectedLocationIds.length === 0) {
      next.locations = 'Select at least one location.'
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

        const input = {
      name: name.trim(),
      startDateTime: startDateTime
        ? new Date(startDateTime).toISOString()
        : null,
      endDateTime: endDateTime
        ? new Date(endDateTime).toISOString()
        : null,
      locations: selectedLocationIds.map((id) => ({ id })),
    }

    try {
      if (activity) {
        setIsSaving(true)
        const token = await getToken()
        if (!token) throw new ApiError(401, 'No active session token is available.')
        await updateActivity(token, tripId, activity.id, input)
      } else {
        await createActivity(input)
      }

      onCreated()
      onClose()
    } catch (cause) {
      setErrors({ form: describeError(cause, isEditing) })
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isBusy) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="animate-rise w-full max-w-lg rounded-2xl border border-border bg-surface-raised p-6 shadow-2xl"
      >
        <h2
          id={titleId}
          className="font-display text-2xl font-medium tracking-tight text-foreground"
        >
          {isEditing ? 'Edit activity' : 'Add activity'}
        </h2>
        <p className="mt-2 text-sm text-muted">
          {isEditing
            ? 'Change the name, times or locations. Keep at least one location selected.'
            : 'Add an activity to your itinerary and select at least one location.'}
        </p>

        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${titleId}-name`} className="text-xs font-medium text-muted">
              Activity name
            </label>
            <input
              ref={nameRef}
              id={`${titleId}-name`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className="input-field"
              placeholder="e.g. Explore Marina Bay"
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
                Start date and time (optional)
              </label>
              <input
                id={`${titleId}-start`}
                type="datetime-local"
                value={startDateTime}
                onChange={(event) => setStartDateTime(event.target.value)}
                className="input-field"
                aria-invalid={Boolean(errors.startDateTime)}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${titleId}-end`} className="text-xs font-medium text-muted">
                End date and time (optional)
              </label>
              <input
                id={`${titleId}-end`}
                type="datetime-local"
                min={startDateTime || undefined}
                value={endDateTime}
                onChange={(event) => setEndDateTime(event.target.value)}
                className="input-field"
                aria-invalid={Boolean(errors.endDateTime)}
                aria-describedby={errors.endDateTime ? `${titleId}-end-error` : undefined}
              />
              {errors.endDateTime && (
                <p id={`${titleId}-end-error`} role="alert" className="text-xs text-terracotta">
                  {errors.endDateTime}
                </p>
              )}
            </div>
          </div>

          <fieldset className="flex min-w-0 flex-col gap-2">
            <legend className="text-xs font-medium text-muted">
              Locations (at least one required)
            </legend>

            <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
              <div className="min-w-0 flex-1">
                <LocationSearch tripId={tripId} onAdded={handleLocationAdded} isAdded={isPlaceAdded} />
              </div>

              <button
                type="button"
                onClick={() => setIsManualLocationOpen(true)}
                className={GHOST_BUTTON}
              >
                Add Manually
              </button>

              {isManualLocationOpen && (
                <ManualLocationModal
                  tripId={tripId}
                  onClose={() => setIsManualLocationOpen(false)}
                  onCreated={(location) => {
                    handleLocationAdded(location)
                  }}
                />
              )}
            </div>

            {locations.length > 0 && (
              <ul className="flex max-h-40 flex-col gap-2 overflow-y-auto rounded-xl border border-border p-3">
                {locations.map((location) => (
                  <li key={location.id}>
                    <label className="flex cursor-pointer items-start gap-3 rounded-lg p-2 hover:bg-surface-subtle">
                      <input
                        type="checkbox"
                        checked={selectedLocationIds.includes(location.id)}
                        onChange={() => toggleLocation(location.id)}
                        className="mt-1 accent-current"
                      />
                      <span className="min-w-0">
                        <span className="block text-sm text-foreground">{location.name}</span>
                        {location.address && (
                          <span className="block text-xs text-muted">{location.address}</span>
                        )}
                        {location.category && (
                          <span className="block text-xs text-muted">{location.category}</span>
                        )}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}

            {/* Order only matters once there are two or more places to visit. */}
            {orderedLocations.length > 1 && (
              <LocationOrderList
                locations={orderedLocations}
                onMove={moveLocation}
                disabled={isBusy}
              />
            )}

            {errors.locations && (
              <p role="alert" className="text-xs text-terracotta">
                {errors.locations}
              </p>
            )}
          </fieldset>

          {errors.form && (
            <p
              role="alert"
              className="rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-xs text-terracotta"
            >
              {errors.form}
            </p>
          )}

          <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
              className={GHOST_BUTTON}
              disabled={isBusy}
            >
              Cancel
            </button>
            <button
              type="submit"
              className={PRIMARY_BUTTON}
              disabled={isBusy || selectedLocationIds.length === 0}
              aria-busy={isBusy}
            >
              {isEditing
                ? (isBusy ? 'Saving…' : 'Save changes')
                : (isBusy ? 'Creating…' : 'Add activity')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
