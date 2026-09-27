import { Show, SignInButton, SignUpButton, useAuth, useClerk } from '@clerk/react'
import { useEffect } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { Navbar } from '../components/Navbar'
import { useAuthConfiguration } from '../auth/authConfiguration'

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

function LandingContent() {
  return (
    <main className="relative flex min-h-screen flex-col bg-surface text-foreground">
      <Navbar />

      <section className="relative flex flex-1 items-center justify-center overflow-hidden px-6 py-20">
        {/* Ambient depth — soft accent glow, purely decorative. */}
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
          <div className="absolute -top-24 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-accent/10 blur-[130px]" />
        </div>

        <div className="animate-rise relative mx-auto flex w-full max-w-3xl flex-col items-center text-center">
          <span className="inline-flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-faint uppercase">
            <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
            Collaborative itinerary planner
          </span>

          <h1 className="mt-5 font-display text-5xl font-medium tracking-tight text-foreground sm:text-6xl">
            Agile Trips
          </h1>

          <p className="mt-6 max-w-2xl text-lg leading-8 text-muted">
            Plan your trips, organise your itinerary, and adapt your plans
            when things change.
          </p>

          <HomepageAuthActions />
        </div>
      </section>
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
