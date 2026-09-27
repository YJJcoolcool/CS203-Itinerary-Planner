import type { Trip } from '../../types/trip'
import {
  activityCount,
  avatarTone,
  daysInclusive,
  formatDateRange,
  initials,
  uniqueMembers,
} from './tripUtils'

const MAX_AVATARS = 4

/**
 * Summary card for a single trip. Purely presentational; the parent wraps it
 * in a link to the trip detail page
 */
export function TripCard({ trip }: { trip: Trip }) {
  const days = daysInclusive(trip.startDate, trip.endDate)
  const range = formatDateRange(trip.startDate, trip.endDate)
  const stops = activityCount(trip)
  const members = uniqueMembers(trip)
  const visibleMembers = members.slice(0, MAX_AVATARS)
  const overflow = members.length - visibleMembers.length

  return (
    <article className="animate-rise flex h-full flex-col gap-4 rounded-2xl border border-border bg-surface-raised p-5 transition-colors hover:border-border-strong">
      <div className="flex items-start justify-between gap-3">
        <h2 className="font-display text-xl leading-tight font-medium tracking-tight text-foreground">
          {trip.name}
        </h2>
        <span className="inline-flex shrink-0 items-center rounded-full border border-border bg-surface-subtle px-2.5 py-1 font-mono text-[11px] whitespace-nowrap text-muted">
          {days === null ? 'Dates TBD' : `${days} ${days === 1 ? 'day' : 'days'}`}
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs text-muted">
        <span className="font-mono">{range}</span>
        <span aria-hidden="true" className="text-faint">
          •
        </span>
        <span>
          {stops} {stops === 1 ? 'activity' : 'activities'}
        </span>
      </div>

      <div className="mt-auto flex items-center justify-between gap-3 pt-1">
        <div className="flex -space-x-2">
          {visibleMembers.map((member) => (
            <span
              key={member.id}
              title={member.displayName ?? 'Traveler'}
              className={`flex h-8 w-8 items-center justify-center rounded-full text-[11px] font-semibold text-foreground ring-2 ring-surface-raised ${avatarTone(member.id)}`}
            >
              {initials(member.displayName)}
            </span>
          ))}
          {overflow > 0 && (
            <span
              title={`${overflow} more`}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-subtle text-[11px] font-semibold text-muted ring-2 ring-surface-raised"
            >
              +{overflow}
            </span>
          )}
        </div>
        <span className="text-xs text-faint">
          {members.length} {members.length === 1 ? 'person' : 'people'}
        </span>
      </div>
    </article>
  )
}

/** Content-shaped placeholder shown while trips load. */
export function TripCardSkeleton() {
  return (
    <div
      aria-hidden="true"
      className="flex h-full animate-pulse flex-col gap-4 rounded-2xl border border-border bg-surface-raised p-5 motion-reduce:animate-none"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="h-5 w-1/2 rounded-full bg-surface-subtle" />
        <span className="h-6 w-14 rounded-full bg-surface-subtle" />
      </div>
      <span className="h-3 w-3/4 rounded-full bg-surface-subtle" />
      <div className="mt-auto flex items-center justify-between gap-3 pt-1">
        <div className="flex -space-x-2">
          <span className="h-8 w-8 rounded-full bg-surface-subtle ring-2 ring-surface-raised" />
          <span className="h-8 w-8 rounded-full bg-surface-subtle ring-2 ring-surface-raised" />
        </div>
        <span className="h-3 w-12 rounded-full bg-surface-subtle" />
      </div>
    </div>
  )
}
