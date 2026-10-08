import { Link, useParams } from 'react-router'
import { Navbar } from '../components/Navbar'
import { TripDetailsCard } from '../components/trips/TripDetailsCard'
import { useCurrentUser, useTrip } from '../lib/useTrips'
import { TripInvitePanel } from '../components/invitations/TripInvitePanel'
import { NotFoundPage } from './NotFoundPage'
import { TripItinerary } from '../components/trips/TripItinerary'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-5 py-3 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`

function TripDetailsSkeleton() {
  return (
    <div
      aria-hidden="true"
      className="h-40 animate-pulse rounded-2xl border border-border bg-surface-raised motion-reduce:animate-none"
    />
  )
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="animate-rise flex flex-col items-center rounded-2xl border border-terracotta/40 bg-terracotta/10 px-6 py-12 text-center"
    >
      <h2 className="font-display text-2xl font-medium tracking-tight text-foreground">
        Couldn&apos;t load this trip
      </h2>
      <p className="mt-3 max-w-md text-sm leading-relaxed text-muted">{message}</p>
      <button type="button" onClick={onRetry} className={`mt-7 ${PRIMARY_BUTTON}`}>
        Retry
      </button>
    </div>
  )
}

function TripDetailsContent({ tripId }: { tripId: number }) {
  const { trip, isLoading, error, errorStatus, refresh } = useTrip(tripId)
  const { user } = useCurrentUser()

  if (errorStatus === 404) return <NotFoundPage />

  return (
    <main className="relative flex min-h-screen flex-col bg-surface text-foreground">
      <Navbar />

      <section className="mx-auto w-full max-w-6xl flex-1 px-6 py-10 sm:px-8 sm:py-14">
        <Link
          to="/trips"
          className={`mb-6 inline-flex items-center gap-1 rounded-lg text-sm text-muted transition-colors hover:text-foreground ${FOCUS_RING}`}
        >
          ← My Trips
        </Link>

        {isLoading ? (
          <TripDetailsSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={refresh} />
        ) : trip ? (
          <>
            <TripDetailsCard trip={trip} />
            {/* UI-only gate; the backend rejects non-owners with 403 regardless. */}
            {user && trip.owner.id === user.id && <TripInvitePanel tripId={trip.id} />}
            <TripItinerary tripId={trip.id} />
          </>
        ) : null}
      </section>
    </main>
  )
}

/** `/trips/:tripId` — header card with the itinerary below it. */
export function TripDetailsPage() {
  const { tripId } = useParams()
  const id = Number(tripId)

  // Route params are strings; anything that isn't a positive integer can't be a trip.
  if (!Number.isInteger(id) || id <= 0) return <NotFoundPage />

  return <TripDetailsContent tripId={id} />
}