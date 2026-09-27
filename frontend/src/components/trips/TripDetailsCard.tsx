import type { Trip } from '../../types/trip'
import {
  avatarTone,
  daysInclusive,
  formatDateRange,
  initials,
  uniqueMembers,
} from './tripUtils'

const PILL =
  'inline-flex items-center gap-1.5 rounded-full border border-border bg-surface-subtle px-3 py-1 font-mono text-xs text-muted'

/**
 * Header card for the trip details page: name, dates, length, budget and the
 * travelers on the trip. Purely presentational — the page loads the trip.
 */
export function TripDetailsCard({ trip }: { trip: Trip }) {
  const days = daysInclusive(trip.startDate, trip.endDate)
  const members = uniqueMembers(trip)

  return (
    <section className="animate-rise rounded-2xl border border-border bg-surface-raised p-6">
      <div className="flex flex-col items-start justify-between gap-6 md:flex-row md:items-center">
        <div>
          <h1 className="font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl">
            {trip.name}
          </h1>
          <p className="mt-1 text-sm text-muted">
            Hosted by {trip.owner.displayName ?? 'Unknown traveler'}
          </p>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className={PILL}>{formatDateRange(trip.startDate, trip.endDate)}</span>
            <span className={PILL}>
              {days === null ? 'Dates TBD' : `${days} ${days === 1 ? 'day' : 'days'}`}
            </span>
            {/* No budget field on the backend yet (FRONTEND_PAGES.md §8). */}
            <span className={`${PILL} text-sage`}>Budget: TBD</span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-sm text-muted">Travelers</span>
          <ul className="flex -space-x-2">
            {members.map((member) => (
              <li
                key={member.id}
                title={member.displayName ?? 'Traveler'}
                className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold text-foreground ring-2 ring-surface-raised ${avatarTone(member.id)}`}
              >
                {initials(member.displayName)}
              </li>
            ))}
          </ul>
          {/* Invites need a backend user lookup first (FRONTEND_PAGES.md §5.10). */}
          <button
            type="button"
            disabled
            title="Inviting travelers is coming soon"
            aria-label="Invite traveler (coming soon)"
            className="flex h-10 w-10 items-center justify-center rounded-full bg-accent text-lg text-accent-contrast disabled:cursor-not-allowed disabled:opacity-50"
          >
            +
          </button>
        </div>
      </div>
    </section>
  )
}