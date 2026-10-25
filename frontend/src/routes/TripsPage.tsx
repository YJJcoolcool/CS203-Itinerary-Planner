import { useEffect, useRef, useState } from 'react'
import { useAuth } from '@clerk/react'
import { Navbar } from '../components/Navbar'
import { TravelPreferencesModal } from '../components/preferences/TravelPreferencesModal'
import { CreateTripModal } from '../components/trips/CreateTripModal'
import { TripCard, TripCardSkeleton } from '../components/trips/TripCard'
import { ApiError, deleteTrip } from '../lib/api'
import { useCurrentUser, useTrips } from '../lib/useTrips'
import { Link, useLocation, useNavigate } from 'react-router'

/** Sent by the trip settings panel when the owner deletes a trip (see handleDelete there). */
interface DeletingTrip {
  id: string
  name: string
}

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-5 py-3 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`

const SECONDARY_BUTTON = `inline-flex items-center justify-center gap-2 rounded-xl border border-border bg-surface-subtle px-5 py-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-raised ${FOCUS_RING}`

const SHOWCASE_SKELETONS = [0, 1, 2]

function PlusIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function SlidersIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="M4 7h8M18 7h2M4 17h4M14 17h6" />
      <circle cx="15" cy="7" r="2.25" />
      <circle cx="11" cy="17" r="2.25" />
    </svg>
  )
}

function CompassIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      className="h-6 w-6"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="8.5" />
      <path d="M15 9l-1.9 4.6L8.5 15.5l1.9-4.6L15 9Z" strokeLinejoin="round" />
    </svg>
  )
}

function AlertIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-6 w-6"
      aria-hidden="true"
    >
      <path d="M12 3.5 21 20H3L12 3.5Z" />
      <path d="M12 10v4M12 17.2v.1" />
    </svg>
  )
}

function TripGridSkeleton() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {SHOWCASE_SKELETONS.map((index) => (
        <TripCardSkeleton key={index} />
      ))}
    </div>
  )
}

function EmptyState({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="animate-rise mx-auto flex max-w-xl flex-col items-center rounded-2xl border border-border bg-surface-raised px-6 py-12 text-center">
      <span
        className="flex h-12 w-12 items-center justify-center rounded-2xl bg-accent-soft text-accent ring-1 ring-accent/25"
        aria-hidden="true"
      >
        <CompassIcon />
      </span>
      <h2 className="mt-5 font-display text-2xl font-medium tracking-tight text-foreground">
        No trips yet
      </h2>
      <p className="mt-3 max-w-sm text-sm leading-relaxed text-muted">
        Create your first trip and it will show up here.
      </p>
      <button type="button" onClick={onCreate} className={`mt-7 ${PRIMARY_BUTTON}`}>
        <PlusIcon />
        Create your first trip
      </button>
    </div>
  )
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="animate-rise mx-auto flex max-w-xl flex-col items-center rounded-2xl border border-terracotta/40 bg-terracotta/10 px-6 py-12 text-center"
    >
      <span
        className="flex h-12 w-12 items-center justify-center rounded-2xl bg-terracotta/15 text-terracotta"
        aria-hidden="true"
      >
        <AlertIcon />
      </span>
      <h2 className="mt-5 font-display text-2xl font-medium tracking-tight text-foreground">
        Couldn&apos;t load your trips
      </h2>
      <p className="mt-3 max-w-md text-sm leading-relaxed text-muted">{message}</p>
      <button type="button" onClick={onRetry} className={`mt-7 ${PRIMARY_BUTTON}`}>
        Retry
      </button>
    </div>
  )
}

export function TripsPage() {
  // Creates the backend profile on first sign-in; the result itself is not needed here.
  const { isLoading: isUserLoading } = useCurrentUser()
  const { trips: allTrips, isLoading: isTripsLoading, error, refresh } = useTrips()
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isPreferencesOpen, setIsPreferencesOpen] = useState(false)

  const { getToken } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  // Trips hidden while (or after) their delete request runs.
  const [hiddenIds, setHiddenIds] = useState<string[]>([])
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const startedDeleteRef = useRef<string | null>(null)

  const myTrips = allTrips.filter((trip) => !hiddenIds.includes(trip.id))
  const deletingTrip = (location.state as { deletingTrip?: DeletingTrip } | null)?.deletingTrip

  /*
   * CGT-136: hide the deleted trip straight away and delete it in the
   * background. On success it stays hidden; on failure it comes back with an
   * error message.
   */
  useEffect(() => {
    // The ref stops React's development double-run from sending the delete twice.
    if (!deletingTrip || startedDeleteRef.current === deletingTrip.id) return
    startedDeleteRef.current = deletingTrip.id
    const { id, name } = deletingTrip

    // Clear the navigation state so reloading the page doesn't delete again.
    navigate(location.pathname, { replace: true, state: null })

    async function runDelete() {
      setHiddenIds((ids) => [...ids, id])
      setDeleteError(null)

      try {
        const token = await getToken()
        if (!token) throw new ApiError(401, 'No active session token is available.')

        await deleteTrip(token, id)
      } catch (cause) {
        setHiddenIds((ids) => ids.filter((hiddenId) => hiddenId !== id))
        const reason = cause instanceof Error && cause.message ? cause.message : 'Please try again.'
        setDeleteError(`Couldn't delete "${name}": ${reason}`)
      }
    }

    void runDelete()
  }, [deletingTrip, getToken, location.pathname, navigate])

  const isLoading = isTripsLoading || isUserLoading
  const hasTrips = !isLoading && !error && myTrips.length > 0

  let subtitle: string
  if (isLoading) {
    subtitle = 'Loading your trips…'
  } else if (error) {
    subtitle = 'We couldn\u2019t load your trips.'
  } else if (myTrips.length === 0) {
    subtitle = 'Trips you create or join will appear here.'
  } else {
    subtitle = `${myTrips.length} ${myTrips.length === 1 ? 'trip' : 'trips'}`
  }

  return (
    <main className="relative flex min-h-screen flex-col bg-surface text-foreground">
      <Navbar />

      <section className="mx-auto w-full max-w-6xl flex-1 px-6 py-10 sm:px-8 sm:py-14">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span className="flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-faint uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
              Trips
            </span>
            <h1 className="mt-3 font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl">
              My Trips
            </h1>
            <p className="mt-2 text-sm text-muted">{subtitle}</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setIsPreferencesOpen(true)}
              className={SECONDARY_BUTTON}
            >
              <SlidersIcon />
              Travel preferences
            </button>

            {hasTrips && (
              <button type="button" onClick={() => setIsCreateOpen(true)} className={PRIMARY_BUTTON}>
                <PlusIcon />
                New trip
              </button>
            )}
          </div>
        </header>

        <div className="mt-8">
          {deleteError && (
            <p
              role="alert"
              className="mb-4 rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta"
            >
              {deleteError}
            </p>
          )}
          {isLoading ? (
            <TripGridSkeleton />
          ) : error ? (
            <ErrorState message={error} onRetry={refresh} />
          ) : myTrips.length === 0 ? (
            <EmptyState onCreate={() => setIsCreateOpen(true)} />
          ) : (
            <ul className="grid list-none gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {myTrips.map((trip) => (
                <li key={trip.id}>
                  <Link
                    to={`/trips/${trip.id}`}
                    className={`block h-full rounded-2xl ${FOCUS_RING}`}
                    aria-label={`Open ${trip.name}`}
                  >
                    <TripCard trip={trip} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {isCreateOpen && (
        <CreateTripModal onClose={() => setIsCreateOpen(false)} onCreated={refresh} />
      )}

      {isPreferencesOpen && (
        <TravelPreferencesModal onClose={() => setIsPreferencesOpen(false)} />
      )}
    </main>
  )
}
