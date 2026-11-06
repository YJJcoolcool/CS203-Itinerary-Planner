import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router'

import { useDocumentTitle } from '../lib/useDocumentTitle'
import { Navbar } from '../components/Navbar'
import { TripPreferencesPanel } from '../components/preferences/TripPreferencesPanel'
import { SuggestionsPanel } from '../components/suggestions/SuggestionsPanel'
import { TripDetailsCard } from '../components/trips/TripDetailsCard'
import { TripItinerary } from '../components/trips/TripItinerary'
import { TripMembersPanel } from '../components/trips/TripMembersPanel'
import { TripSettingsPanel } from '../components/trips/TripSettingsPanel'
import { roleOf } from '../components/trips/tripUtils'
import { WeatherForecastPanel } from '../components/trips/WeatherForecastPanel'
import { WeatherOutlookPanel } from '../components/trips/WeatherOutlookPanel'
import { useCurrentUser, useTrip } from '../lib/useTrips'
import type { Trip } from '../types/trip'
import { NotFoundPage } from './NotFoundPage'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-5 py-3 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`

function TripDetailsSkeleton() {
  return (
    <div
      aria-hidden="true"
      className="h-40 animate-pulse rounded-2xl border border-border bg-surface-raised motion-reduce:animate-none"
    />
  )
}

function ErrorState({
  message,
  onRetry,
}: {
  message: string
  onRetry: () => void
}) {
  return (
    <div
      role="alert"
      className="animate-rise flex flex-col items-center rounded-2xl border border-terracotta/40 bg-terracotta/10 px-6 py-12 text-center"
    >
      <h2 className="font-display text-2xl font-medium tracking-tight text-foreground">
        Couldn't load this trip
      </h2>
      <p className="mt-3 max-w-md text-sm leading-relaxed text-muted">
        {message}
      </p>
      <button
        type="button"
        onClick={onRetry}
        className={`mt-7 ${PRIMARY_BUTTON}`}
      >
        Retry
      </button>
    </div>
  )
}

function TripDetailsContent({ tripId }: { tripId: string }) {
  const { trip, isLoading, error, errorStatus, refresh } = useTrip(tripId)

  useDocumentTitle(
    errorStatus === 404
      ? 'Not Found | Agile Trips'
      : trip
        ? `${trip.name} | Agile Trips`
        : 'Trip Details | Agile Trips',
  )
  
  const { user } = useCurrentUser()

  const [isSettingsOpen, setIsSettingsOpen] = useState(false)
  const [scrollToInvitations, setScrollToInvitations] = useState(false)
  const [updatedTrip, setUpdatedTrip] = useState<Trip | null>(null)
  const [itineraryRefreshKey, setItineraryRefreshKey] = useState(0)

  const closeSettings = useCallback(() => {
    setIsSettingsOpen(false)
  }, [])

  const openSettings = useCallback(() => {
    setScrollToInvitations(false)
    setIsSettingsOpen(true)
  }, [])

  const openSettingsForInvite = useCallback(() => {
    setScrollToInvitations(true)
    setIsSettingsOpen(true)
  }, [])

  const handleUpdated = useCallback((updated: Trip) => {
    setUpdatedTrip(updated)
    // Refresh the server-backed trip too, so the page stays in sync.
    refresh()
  }, [refresh])

  const isOwner = Boolean(user && trip && trip.owner.id === user.id)
  const displayedTrip = trip && updatedTrip?.id === trip.id
    ? updatedTrip
    : trip
  const myRole = displayedTrip ? roleOf(displayedTrip, user?.id) : null
  const canEdit = myRole === 'OWNER' || myRole === 'EDITOR'

  if (errorStatus === 404) return <NotFoundPage />

  return (
    <main className="relative flex min-h-screen flex-col bg-surface text-foreground">
      <Navbar />

      <section className="mx-auto w-full max-w-6xl flex-1 px-6 py-10 sm:px-8 sm:py-14">
        <div className="mb-6 flex items-center justify-between gap-4">
          <Link
            to="/trips"
            className={`inline-flex items-center gap-1 rounded-lg text-sm text-muted transition-colors hover:text-foreground ${FOCUS_RING}`}
          >
            ← My Trips
          </Link>

          {(myRole === 'OWNER' || myRole === 'EDITOR') && (
            <button
              type="button"
              onClick={openSettings}
              className={`inline-flex items-center gap-2 rounded-xl border border-border bg-surface-subtle px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-raised ${FOCUS_RING}`}
            >
              <span aria-hidden="true">⚙</span>
              Trip settings
            </button>
          )}
        </div>

        {isLoading ? (
          <TripDetailsSkeleton />
        ) : error ? (
          <ErrorState message={error} onRetry={refresh} />
        ) : displayedTrip ? (
          <>
            <TripDetailsCard
              trip={displayedTrip}
              myRole={myRole}
              onInvite={isOwner ? openSettingsForInvite : undefined}
            />

            <TripMembersPanel
              trip={displayedTrip}
              myRole={myRole}
              onUpdated={handleUpdated}
            />

            <TripPreferencesPanel trip={displayedTrip} canEdit={canEdit} />

            <WeatherForecastPanel
              startDate={displayedTrip.startDate}
              endDate={displayedTrip.endDate}
              countryCode={displayedTrip.countryCode}
            />

            <WeatherOutlookPanel
              tripId={displayedTrip.id}
              countryCode={displayedTrip.countryCode}
            />

            <TripItinerary
              key={itineraryRefreshKey}
              tripId={displayedTrip.id}
              countryCode={displayedTrip.countryCode}
              canEdit={canEdit}
            />

            <SuggestionsPanel
              key={displayedTrip.id}
              tripId={displayedTrip.id}
              canEdit={canEdit}
              onAccepted={() => setItineraryRefreshKey((key) => key + 1)}
            />
          </>
        ) : null}
      </section>

      {isSettingsOpen && (myRole === 'OWNER' || myRole === 'EDITOR') && displayedTrip && (
        <TripSettingsPanel
          trip={displayedTrip}
          isOwner={isOwner}
          onClose={closeSettings}
          onUpdated={handleUpdated}
          onDeleted={() => {
            setIsSettingsOpen(false)
            setUpdatedTrip(null)
          }}
          scrollToInvitations={scrollToInvitations}
        />
      )}
    </main>
  )
}

/** `/trips/:tripId` — header card with the itinerary below it. */
export function TripDetailsPage() {
  const { tripId } = useParams()

  if (!tripId) return <NotFoundPage />

  return <TripDetailsContent tripId={tripId} />
}