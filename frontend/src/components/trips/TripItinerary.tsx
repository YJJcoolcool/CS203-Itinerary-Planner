import { Fragment, useState } from 'react'
import { useAuth } from '@clerk/react'
import { ApiError, deleteActivity } from '../../lib/api'
import { useTripItinerary } from '../../lib/useTrips'
import { CreateActivityModal } from './CreateActivityModal'
import { ActivityRoute } from './ActivityRoute'
import { groupActivitiesByDay, UNSCHEDULED_KEY } from './itineraryDays'
import type { Activity, Location } from '../../types/trip'
import { CreateFlightModal } from './CreateFlightModal'
import { FlightItineraryCard } from './FlightItineraryCard'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PILL =
  'inline-flex items-center rounded-full border border-border bg-surface-subtle px-2.5 py-0.5 font-mono text-[11px] text-muted'

/**
 * "8:30 AM – 10:00 AM" in the viewer's locale; the day heading above shows the
 * date (CGT-171). An activity that ends on a later day shows the end date too.
 */
function formatTimeRange(start: string | null, end: string | null): string {
  const startDate = start ? new Date(start) : null
  const endDate = end ? new Date(end) : null
  if (!startDate || Number.isNaN(startDate.getTime())) return 'Time TBD'

  const time = (date: Date) => date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

  if (!endDate || Number.isNaN(endDate.getTime())) return time(startDate)
  if (endDate.toDateString() !== startDate.toDateString()) {
    const endDay = endDate.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
    return `${time(startDate)} – ${endDay}, ${time(endDate)}`
  }
  return `${time(startDate)} – ${time(endDate)}`
}

function LocationList({
  locations,
  isLoading,
}: {
  locations: Location[] | undefined
  isLoading: boolean
}) {
  if (locations === undefined) {
    return (
      <p className="text-xs text-faint italic">
        {isLoading ? 'Loading locations…' : 'Couldn’t load locations.'}
      </p>
    )
  }

  if (locations.length === 0) {
    return <p className="text-xs text-faint italic">No locations added yet.</p>
  }

  // With several places, number them so the visit order is visible (CGT-145).
  const showOrder = locations.length > 1

  return (
    <ol className="flex flex-col gap-1">
      {locations.map((location, index) => (
        <li key={location.id} className="flex flex-wrap items-center gap-2 text-sm text-muted">
          {showOrder ? (
            <span className="flex h-5 w-5 items-center justify-center rounded-full bg-accent/15 font-mono text-[11px] text-accent">
              {index + 1}
            </span>
          ) : (
            <span aria-hidden="true">📍</span>
          )}
          <span className="text-foreground">{location.name}</span>
          {location.category && <span className={PILL}>{location.category}</span>}
          {location.isIndoor !== null && (
            <span className={PILL}>{location.isIndoor ? 'Indoor' : 'Outdoor'}</span>
          )}
        </li>
      ))}
    </ol>
  )
}

function ActivityItem({
  activity,
  locations,
  isLoadingLocations,
  onEdit,
  onDelete,
}: {
  activity: Activity
  locations: Location[] | undefined
  isLoadingLocations: boolean
  /** Only passed for editors and the owner, so viewers never see the Edit button. */
  onEdit?: () => void
  /** Same rule as onEdit. */
  onDelete?: () => void
}) {
  return (
    <li className="animate-rise flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-mono text-xs text-accent">
            {formatTimeRange(activity.startDateTime, activity.endDateTime)}
          </p>
          <h4 className="mt-1 text-lg font-medium text-foreground">{activity.name}</h4>
        </div>
        <div className="flex items-center gap-2">
          {activity.status && <span className={PILL}>{activity.status}</span>}
          {onEdit && (
            <button
              type="button"
              onClick={onEdit}
              // The form needs the current locations, so wait until they have loaded.
              disabled={locations === undefined}
              aria-label={`Edit ${activity.name}`}
              className={`rounded-lg border border-border px-3 py-1 text-xs text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:opacity-50 ${FOCUS_RING}`}
            >
              Edit
            </button>
          )}

          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              aria-label={`Delete ${activity.name}`}
              className={`rounded-lg border border-terracotta/40 px-3 py-1 text-xs text-terracotta transition-colors hover:bg-terracotta/10 ${FOCUS_RING}`}
            >
              Delete
            </button>
          )}
        </div>
      </div>
      <LocationList locations={locations} isLoading={isLoadingLocations} />
    </li>
  )
}

/**
 * Itinerary for a trip, grouped by day (CGT-171). Loads the activities first,
 * then each activity's locations (see `useTripItinerary`).
 */
export function TripItinerary({
  tripId,
  countryCode,
  tripStartDate,
  canEdit,
}: {
  tripId: string
  countryCode: string
  /** `YYYY-MM-DD`; day headings count from it ("Day 1", "Day 2", ...). */
  tripStartDate?: string | null
  canEdit: boolean
}) {
  const { activities, locationsByActivity, isLoading, isLoadingLocations, error, refresh } =
    useTripItinerary(tripId)
  
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [editingActivity, setEditingActivity] = useState<Activity | null>(null)
  const { getToken } = useAuth()
  // Activities hidden while (or after) their delete request runs.
  const [hiddenIds, setHiddenIds] = useState<string[]>([])
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const [isCreateFlightModalOpen, setIsCreateFlightModalOpen] = useState(false)

  const visibleActivities = activities.filter((activity) => !hiddenIds.includes(activity.id))
  const days = groupActivitiesByDay(visibleActivities, tripStartDate)

  /*
   * Hides the card straight away, then deletes in the background (CGT-136).
   * On success the card stays hidden, so there is no need to reload the list.
   * On failure it comes back with an error message.
   */
  async function handleDelete(activity: Activity) {
    if (!window.confirm(`Delete "${activity.name}" from the itinerary?`)) return

    setHiddenIds((ids) => [...ids, activity.id])
    setDeleteError(null)

    try {
      const token = await getToken()
      if (!token) throw new ApiError(401, 'No active session token is available.')

      await deleteActivity(token, tripId, activity.id)
    } catch (cause) {
      setHiddenIds((ids) => ids.filter((id) => id !== activity.id))
      const reason = cause instanceof Error && cause.message ? cause.message : 'Please try again.'
      setDeleteError(`Couldn't delete "${activity.name}": ${reason}`)
    }
  }

  return (
    <section className="mt-10" aria-labelledby="itinerary-heading">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2
          id="itinerary-heading"
          className="font-display text-2xl font-medium tracking-tight text-foreground"
        >
          Itinerary
        </h2>

        {canEdit && (
          <div className='flex gap-3'>
            <button
              type="button"
              onClick={() => setIsCreateModalOpen(true)}
              className={`rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
            >
              + Add activity
            </button>
            <button
              type="button"
              onClick={() => setIsCreateFlightModalOpen(true)}
              className={`rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
            >
              + Add flight
            </button>
          </div>
        )}
      </div>

      <div className="mt-4">
        {deleteError && (
          <p
            role="alert"
            className="mb-3 rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta"
          >
            {deleteError}
          </p>
        )}

        {isLoading ? (
          <div
            aria-hidden="true"
            className="h-28 animate-pulse rounded-2xl border border-border bg-surface-raised motion-reduce:animate-none"
          />
        ) : error ? (
          <div
            role="alert"
            className="flex flex-col items-start gap-3 rounded-2xl border border-terracotta/40 bg-terracotta/10 p-5"
          >
            <p className="text-sm text-muted">Couldn&apos;t load the itinerary: {error}</p>
            <button
              type="button"
              onClick={refresh}
              className={`rounded-xl border border-border px-4 py-2 text-sm text-foreground transition-colors hover:border-border-strong ${FOCUS_RING}`}
            >
              Retry
            </button>
          </div>
        ) : visibleActivities.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted">
            No activities yet.
          </p>
        ) : (
          <div className="flex flex-col gap-8">
            {days.map((day) => (
              <section key={day.key} aria-labelledby={`itinerary-day-${day.key}`}>
                <h3
                  id={`itinerary-day-${day.key}`}
                  className="mb-3 flex flex-wrap items-baseline gap-x-3 gap-y-1 font-display text-lg font-medium tracking-tight text-foreground"
                >
                  {day.label}
                  <span className="font-mono text-xs font-normal text-muted">
                    {day.activities.length} {day.activities.length === 1 ? 'activity' : 'activities'}
                  </span>
                </h3>

                <ol className="flex flex-col gap-3">
                  {day.activities.map((activity, index) => {
                    // Directions only between stops on the same day; unscheduled ones have no times to route.
                    const nextActivity = day.key === UNSCHEDULED_KEY ? undefined : day.activities[index + 1]

                    return (
                      <Fragment key={activity.id}>
                        {activity.type === 'FLIGHT' ? (
                          <FlightItineraryCard
                            activity={activity}
                            onDelete={canEdit ? () => void handleDelete(activity) : undefined}
                          />
                        ) : (
                          <ActivityItem
                            activity={activity}
                            locations={locationsByActivity[activity.id]}
                            isLoadingLocations={isLoadingLocations}
                            onEdit={canEdit ? () => setEditingActivity(activity) : undefined}
                            onDelete={canEdit ? () => void handleDelete(activity) : undefined}
                          />
                        )}

                        {nextActivity && (
                          <ActivityRoute
                            tripId={tripId}
                            countryCode={countryCode}
                            fromActivity={activity}
                            toActivity={nextActivity}
                            fromLocations={locationsByActivity[activity.id]}
                            toLocations={locationsByActivity[nextActivity.id]}
                          />
                        )}
                      </Fragment>
                    )
                  })}
                </ol>
              </section>
            ))}
          </div>
        )}
      </div>

      {isCreateModalOpen && (
        <CreateActivityModal
          tripId={tripId}
          onClose={() => setIsCreateModalOpen(false)}
          onCreated={refresh}
        />
      )}

      {editingActivity && (
        <CreateActivityModal
          tripId={tripId}
          activity={editingActivity}
          activityLocations={locationsByActivity[editingActivity.id]}
          onClose={() => setEditingActivity(null)}
          onCreated={refresh}
        />
      )}

      {isCreateFlightModalOpen && (
        <CreateFlightModal
          tripId={tripId}
          onClose={() => setIsCreateFlightModalOpen(false)}
          onCreated={refresh}
        />
      )}
    </section>
  )
}