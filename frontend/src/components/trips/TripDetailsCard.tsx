import { roleLabel } from '../../types/invitation'
import type { MemberRole, Trip } from '../../types/trip'
import { getName } from 'country-list'
import {
  avatarTone,
  daysInclusive,
  formatDateRange,
  initials,
  uniqueMembers,
} from './tripUtils'
import {
  DEFAULT_COVER_IMAGE,
  BROKEN_URL_IMAGE,
} from './tripCoverImages'

const PILL =
  'inline-flex items-center gap-1.5 rounded-full border border-border bg-surface-subtle px-3 py-1 font-mono text-xs text-muted'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

/**
 * Header card for the trip details page: name, dates, length, budget and the
 * travelers on the trip. Purely presentational — the page loads the trip.
 */
export function TripDetailsCard({
  trip,
  myRole,
  onInvite,
}: {
  trip: Trip
  /** The signed-in user's role on this trip; the badge is hidden when null. */
  myRole?: MemberRole | null
  onInvite?: () => void
}) {
  const days = daysInclusive(trip.startDate, trip.endDate)
  const members = uniqueMembers(trip)
  const countryName = getName(trip.countryCode.toUpperCase())

  return (
    <section className="animate-rise rounded-2xl border border-border bg-surface-raised overflow-hidden">
      <div className="relative">
        <img
          src={trip.coverImageUrl || DEFAULT_COVER_IMAGE}
          alt={`${trip.name} cover`}
          onError={(event) => {
            event.currentTarget.onerror = null
            event.currentTarget.src = BROKEN_URL_IMAGE
          }}
          className="h-32 w-full object-cover sm:h-64"
        />
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-white to-transparent dark:from-slate-900" />
      </div>
      <div className="flex flex-col items-start justify-between p-6 gap-6 md:flex-row md:items-center">
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
            {countryName && (
              <span className={PILL}>
                {countryName}
              </span>
            )}
            {/* No budget field on the backend yet (FRONTEND_PAGES.md §8). */}
            <span className={`${PILL} text-sage`}>Budget: TBD</span>
            {myRole && (
              <span className={`${PILL} text-foreground`}>You: {roleLabel(myRole)}</span>
            )}
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
          {/* Only passed for the owner, so non-owners never see the + button. */}
          {onInvite && (
            <button
              type="button"
              onClick={onInvite}
              title="Invite travelers"
              aria-label="Invite travelers"
              className={`flex h-10 w-10 items-center justify-center rounded-full bg-accent text-lg text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
            >
              +
            </button>
          )}
        </div>
      </div>
    </section>
  )
}