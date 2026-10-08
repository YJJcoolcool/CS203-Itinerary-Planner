import { useTripItinerary } from '../../lib/useTrips'
import type { Activity, Location } from '../../types/trip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PILL =
  'inline-flex items-center rounded-full border border-border bg-surface-subtle px-2.5 py-0.5 font-mono text-[11px] text-muted'

/** "Oct 14, 8:30 AM – 10:00 AM" in the viewer's locale; tolerant of missing times. */
function formatTimeRange(start: string | null, end: string | null): string {
  const startDate = start ? new Date(start) : null
  const endDate = end ? new Date(end) : null
  if (!startDate || Number.isNaN(startDate.getTime())) return 'Time TBD'

  const day = startDate.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  const time = (date: Date) => date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })

  if (!endDate || Number.isNaN(endDate.getTime())) return `${day}, ${time(startDate)}`
  return `${day}, ${time(startDate)} – ${time(endDate)}`
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

  return (
    <ul className="flex flex-col gap-1">
      {locations.map((location) => (
        <li key={location.id} className="flex flex-wrap items-center gap-2 text-sm text-muted">
          <span aria-hidden="true">📍</span>
          <span className="text-foreground">{location.name}</span>
          {location.category && <span className={PILL}>{location.category}</span>}
          {location.isIndoor !== null && (
            <span className={PILL}>{location.isIndoor ? 'Indoor' : 'Outdoor'}</span>
          )}
        </li>
      ))}
    </ul>
  )
}

function ActivityItem({
  activity,
  locations,
  isLoadingLocations,
}: {
  activity: Activity
  locations: Location[] | undefined
  isLoadingLocations: boolean
}) {
  return (
    <li className="animate-rise flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-mono text-xs text-accent">
            {formatTimeRange(activity.startDateTime, activity.endDateTime)}
          </p>
          <h3 className="mt-1 text-lg font-medium text-foreground">{activity.name}</h3>
        </div>
        {activity.status && <span className={PILL}>{activity.status}</span>}
      </div>
      <LocationList locations={locations} isLoading={isLoadingLocations} />
    </li>
  )
}

/**
 * Itinerary for a trip. Loads the activities first, then each activity's
 * locations (see `useTripItinerary`).
 */
export function TripItinerary({ tripId }: { tripId: string }) {
  const { activities, locationsByActivity, isLoading, isLoadingLocations, error, refresh } =
    useTripItinerary(tripId)

  return (
    <section className="mt-10" aria-labelledby="itinerary-heading">
      <h2
        id="itinerary-heading"
        className="font-display text-2xl font-medium tracking-tight text-foreground"
      >
        Itinerary
      </h2>

      <div className="mt-4">
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
        ) : activities.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-border p-8 text-center text-sm text-muted">
            No activities yet.
          </p>
        ) : (
          <ol className="flex flex-col gap-3">
            {activities.map((activity) => (
              <ActivityItem
                key={activity.id}
                activity={activity}
                locations={locationsByActivity[activity.id]}
                isLoadingLocations={isLoadingLocations}
              />
            ))}
          </ol>
        )}
      </div>
    </section>
  )
}