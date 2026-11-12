
import { Show, SignInButton, SignUpButton, useAuth, useClerk } from '@clerk/react'
import { useEffect } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'

import { useDocumentTitle } from '../lib/useDocumentTitle'
import { Navbar } from '../components/Navbar'
import { useAuthConfiguration } from '../auth/authConfiguration'
import { TripCard } from '../components/trips/TripCard'
import type { Trip } from '../types/trip'

const FOCUS_RING =
  'focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface focus-visible:outline-none'

function HomepageAuthActions() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return (
      <p className="mt-10 text-sm text-faint" role="status">
        Authentication will be available after Clerk is configured.
      </p>
    )
  }

  return (
    <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row">
      <Show when="signed-out">
        <SignInButton mode="modal">
          <button
            type="button"
            className={`rounded-xl bg-accent px-6 py-3 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
          >
            Sign In
          </button>
        </SignInButton>
        <SignUpButton mode="modal">
          <button
            type="button"
            className={`rounded-xl border border-border bg-surface-raised px-6 py-3 text-sm font-semibold text-foreground transition-colors hover:border-border-strong ${FOCUS_RING}`}
          >
            Create Account
          </button>
        </SignUpButton>
      </Show>
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Landing page preview helpers                                               */
/* -------------------------------------------------------------------------- */

/** Returns a date in the next calendar month, using the local timezone. */
function getNextMonthDate(day: number): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth() + 1, day)
}

/** Converts a local date to the ISO date format expected by Trip. */
function toIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

/* -------------------------------------------------------------------------- */
/* Section 1: Trip planning                                                   */
/* -------------------------------------------------------------------------- */

function ActivityPreviewCard({
  title,
  startTime,
  endTime,
  locations,
}: {
  title: string
  startTime: string
  endTime: string
  locations: string[]
}) {
  return (
    <li className="animate-rise flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5">
      <div>
        <p className="font-mono text-xs text-accent">
          {startTime} – {endTime}
        </p>
        <h3 className="mt-1 text-lg font-medium text-foreground">
          {title}
        </h3>
      </div>

      <ol className="flex flex-col gap-1">
        {locations.map((location, index) => (
          <li
            key={location}
            className="flex flex-wrap items-center gap-2 text-sm text-muted"
          >
            {locations.length > 1 ? (
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-accent/15 font-mono text-[11px] text-accent">
                {index + 1}
              </span>
            ) : (
              <span aria-hidden="true">📍</span>
            )}

            <span className="text-foreground">{location}</span>
          </li>
        ))}
      </ol>
    </li>
  )
}

function TripPlanningSection() {
  return (
    <section
      className="w-full px-6 py-20 sm:px-8 bg-accent-soft"
      aria-labelledby="planning-heading"
    >
      <div className="mx-auto max-w-2xl text-center">
        <h2
          id="planning-heading"
          className="font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl"
        >
          Trip Planning Made Easy
        </h2>

        <p className="mt-3 text-base leading-7 text-muted sm:text-lg">
          An easy to use interface makes itinerary planning a breeze!
        </p>
      </div>

      <div className="mx-auto mt-10 max-w-3xl">
        <ol className="flex flex-col gap-3">
          <ActivityPreviewCard
            title="Exploring Marina Bay"
            startTime="9:00 AM"
            endTime="12:00 PM"
            locations={[
              'Gardens by the Bay',
              'Cloud Forest',
              'Flower Dome',
              'Marina Barrage',
            ]}
          />

          <ActivityPreviewCard
            title="Lunch in the City"
            startTime="12:30 PM"
            endTime="1:30 PM"
            locations={['Lau Pa Sat']}
          />

          <ActivityPreviewCard
            title="Shopping Spree"
            startTime="2:00 PM"
            endTime="5:00 PM"
            locations={[
              'ION Orchard',
              'Takashimaya',
              'Paragon',
            ]}
          />
        </ol>
      </div>
    </section>
  )
}

/* -------------------------------------------------------------------------- */
/* Section 2: Collaboration                                                   */
/* -------------------------------------------------------------------------- */

function CollaborationSection() {
  const startDate = getNextMonthDate(5)
  const endDate = getNextMonthDate(8)

  const mockTrip: Trip = {
    id: 'landing-preview-trip',
    name: 'Singapore Trip',
    startDate: toIsoDate(startDate),
    endDate: toIsoDate(endDate),
    countryCode: 'sg',
    coverImageUrl:
      '/images/trip_covers/pexels-julia-khalimova-826746-4140892.jpg',
    owner: {
      id: 'demo-owner',
      clerkUserId: 'demo-owner',
      displayName: 'Alex Tan',
    },
    users: [
      {
        id: 'demo-traveller-1',
        clerkUserId: 'demo-traveller-1',
        displayName: 'Jamie Lee',
      },
      {
        id: 'demo-traveller-2',
        clerkUserId: 'demo-traveller-2',
        displayName: 'Sam Wong',
      },
    ],
    itinerary: [
      {
        id: 'demo-activity-1',
        name: 'Exploring Marina Bay',
        type: 'ACTIVITY',
        startDateTime: null,
        endDateTime: null,
        status: null,
      },
      {
        id: 'demo-activity-2',
        name: 'Lunch in the City',
        type: 'ACTIVITY',
        startDateTime: null,
        endDateTime: null,
        status: null,
      },
      {
        id: 'demo-activity-3',
        name: 'Shopping Spree',
        type: 'ACTIVITY',
        startDateTime: null,
        endDateTime: null,
        status: null,
      },
    ],
  }

  return (
    <section
      className="border-y border-border bg-surface-subtle/50"
      aria-labelledby="collaboration-heading"
    >
      <div className="mx-auto grid w-full max-w-6xl items-center gap-12 px-6 py-20 sm:px-8 lg:grid-cols-2">
        <div className="mx-auto w-full max-w-md">
          <TripCard trip={mockTrip} />
        </div>

        <div>
          <h2
            id="collaboration-heading"
            className="font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl"
          >
            Collaborate With Others
          </h2>

          <p className="mt-4 text-base leading-7 text-muted sm:text-lg">
            Work together on your next trip by inviting others to contribute
            places!
          </p>

          <ul className="mt-7 flex flex-col gap-4">
            <li className="flex items-center gap-3 text-sm text-muted">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-accent/15">
                👥
              </span>
              Invite friends and travel companions.
            </li>

            <li className="flex items-center gap-3 text-sm text-muted">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-accent/15">
                🗺️
              </span>
              Plan activities and destinations together.
            </li>
          </ul>
        </div>
      </div>
    </section>
  )
}

/* -------------------------------------------------------------------------- */
/* Section 3: AI suggestions                                                  */
/* -------------------------------------------------------------------------- */

function AISection() {
  return (
    <section
      className="mx-auto grid w-full max-w-6xl items-center gap-12 px-6 py-20 sm:px-8 lg:grid-cols-2"
      aria-labelledby="ai-heading"
    >
      <div>
        <h2
          id="ai-heading"
          className="font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl"
        >
          AI that reacts to disruptions
        </h2>

        <p className="mt-4 text-base leading-7 text-muted sm:text-lg">
          Never worry about the weather ruining your plans!
        </p>
      </div>

      <div className="rounded-2xl border border-border bg-surface-raised p-5 sm:p-6">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent/15 text-accent">
              ✨
            </span>

            <div>
              <h3 className="font-medium text-foreground">
                AI suggestion
              </h3>
              <p className="text-xs text-muted">
                Weather disruption detected
              </p>
            </div>
          </div>

          <span className="rounded-full border border-amber/40 bg-amber/10 px-2.5 py-1 text-xs font-medium text-muted">
            Rain expected
          </span>
        </div>

        <div className="mt-6 rounded-xl border border-border bg-surface-subtle p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-faint">
            Current stop
          </p>

          <p className="mt-1 font-medium text-foreground">
            Gardens by the Bay
          </p>
        </div>

        <div className="my-3 flex justify-center text-muted" aria-hidden="true">
          ↓
        </div>

        <div className="rounded-xl border border-accent/40 bg-accent-soft p-4">
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-medium uppercase tracking-wide text-accent">
              Suggested alternative
            </p>

            <span className="rounded-full bg-accent/15 px-2 py-0.5 text-[11px] font-semibold text-accent">
              Indoor
            </span>
          </div>

          <p className="mt-1 text-lg font-medium text-foreground">
            ArtScience Museum
          </p>

          <p className="mt-2 text-sm leading-relaxed text-muted">
            Consider swapping your outdoor visit for an indoor attraction
            nearby, so you can continue exploring Marina Bay while staying
            sheltered from the rain.
          </p>
        </div>
      </div>
    </section>
  )
}

/* -------------------------------------------------------------------------- */
/* Section 4: Get started                                                    */
/* -------------------------------------------------------------------------- */

function HomepageSignUpAction() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return (
      <p className="mt-6 text-sm text-faint" role="status">
        Authentication will be available after Clerk is configured.
      </p>
    )
  }

  return (
    <Show when="signed-out">
      <SignUpButton mode="modal">
        <button
          type="button"
          className={`rounded-xl bg-accent px-7 py-3.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
        >
          Sign Up
        </button>
      </SignUpButton>
    </Show>
  )
}

function GetStartedSection() {
  return (
    <section className="border-t border-border bg-surface-subtle/50 px-6 py-20 text-center">
      <div className="mx-auto max-w-2xl">
        <h2 className="font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl">
          Get Started with Agile Trips
        </h2>

        <p className="mt-4 text-base leading-7 text-muted">
          Your next adventure starts with a plan. Bring your friends and start
          organising your next trip together.
        </p>

        <div className="mt-8">
          <HomepageSignUpAction />
        </div>
      </div>
    </section>
  )
}

/* -------------------------------------------------------------------------- */
/* Landing page                                                               */
/* -------------------------------------------------------------------------- */

function LandingContent() {
  useDocumentTitle('Agile Trips - AI Powered Itinerary Planner')

  return (
    <main className="relative flex min-h-screen flex-col bg-surface text-foreground">
      <Navbar />

      {/* Existing hero section */}
      <section className="relative flex flex-1 items-center justify-center overflow-hidden bg-[url('/images/background.png')] dark:bg-[url('/images/background-dark.png')] bg-cover bg-center bg-no-repeat px-6 py-20">
        <div className="animate-rise relative mx-auto flex w-full max-w-3xl flex-col items-center text-center p-8 bg-white/90 dark:bg-accent-contrast/90 rounded-xl shadow-2xl">
          <h1 className="mt-5 font-display text-5xl font-medium tracking-tight text-foreground sm:text-6xl">
            Agile Trips
          </h1>

          <p className="mt-6 max-w-2xl text-lg leading-8 text-muted">
            Plan your trips with others, organise your itinerary, and adapt when things change.
          </p>

          <HomepageAuthActions />

          <p className="mt-6 max-w-2xl text-xs leading-8 text-muted">
            A project as part of SMU&apos;s CS203 Human-AI Collaborative Software Development course.
          </p>
        </div>
      </section>

      {/* New landing page sections */}
      <TripPlanningSection />
      <CollaborationSection />
      <AISection />
      <GetStartedSection />
    </main>
  )
}

/**
 * Clerk-aware landing. Redirects signed-in users, and opens the sign-in modal
 * exactly once when the user was bounced here from a guarded route — then
 * clears the navigation state so a normal visit or a post-sign-out landing
 * never auto-opens the modal.
 */
function ConfiguredLanding() {
  const { isLoaded, isSignedIn } = useAuth()
  const clerk = useClerk()
  const location = useLocation()
  const navigate = useNavigate()
  const cameFromGuard = Boolean((location.state as { from?: unknown } | null)?.from)

  useEffect(() => {
    if (!isLoaded || isSignedIn || !cameFromGuard) {
      return
    }

    clerk.openSignIn()
    navigate('/', { replace: true, state: null })
  }, [clerk, isLoaded, isSignedIn, cameFromGuard, navigate])

  if (!isLoaded) {
    return null
  }

  if (isSignedIn) {
    return <Navigate to="/trips" replace />
  }

  return <LandingContent />
}

export function LandingPage() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return <LandingContent />
  }

  return <ConfiguredLanding />
}
