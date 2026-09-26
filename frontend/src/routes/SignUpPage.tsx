import { SignUp, useAuth } from '@clerk/react'
import { Link, Navigate } from 'react-router'
import { useAuthConfiguration } from '../auth/authConfiguration'
import { clerkAppearance } from '../auth/clerkAppearance'

function CompassMark() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="8.5" />
      <path d="M15 9l-1.9 4.6L8.5 15.5l1.9-4.6L15 9Z" strokeLinejoin="round" />
    </svg>
  )
}

function NotConfigured() {
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 text-center">
      <p className="text-sm leading-relaxed text-cream-dim">
        Authentication is not configured. Set{' '}
        <code className="font-mono text-xs text-cream">VITE_CLERK_PUBLISHABLE_KEY</code> to enable
        sign-up.
      </p>
    </div>
  )
}

/** Clerk-aware child: only rendered once we know Clerk is configured. */
function SignUpForm() {
  const { isLoaded, isSignedIn } = useAuth()

  if (!isLoaded) {
    return null
  }

  if (isSignedIn) {
    return <Navigate to="/trips" replace />
  }

  return (
    <SignUp
      routing="path"
      path="/sign-up"
      appearance={clerkAppearance}
      fallbackRedirectUrl="/trips"
      signInUrl="/sign-in"
    />
  )
}

export function SignUpPage() {
  const { isConfigured } = useAuthConfiguration()

  return (
    <main className="page-dark relative flex min-h-screen flex-col overflow-hidden">
      {/* Ambient depth — soft accent glow, purely decorative. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-32 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-amber/[0.06] blur-[130px]" />
      </div>

      {/* Top bar — matches the Trips/404 chrome so the flow feels like one product. */}
      <header className="relative z-10 border-b border-white/[0.06]">
        <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-6 sm:px-8">
          <Link to="/" className="flex items-center gap-3 rounded-xl focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none">
            <span
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber/10 text-amber ring-1 ring-amber/20"
              aria-hidden="true"
            >
              <CompassMark />
            </span>
            <span className="font-display text-lg font-medium tracking-tight text-cream">
              Itinerary Planner
            </span>
          </Link>

          <span className="font-mono text-[11px] tracking-[0.28em] text-cream-dim/45 uppercase">
            Sign up
          </span>
        </div>
      </header>

      <section className="animate-rise relative z-10 flex flex-1 items-center justify-center px-6 py-16 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 text-center">
            <span className="inline-flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-cream-dim/60 uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-amber" aria-hidden="true" />
              Get started
            </span>
            <h1 className="mt-4 font-display text-3xl leading-tight font-medium tracking-tight text-cream sm:text-4xl">
              Create your account.
            </h1>
            <p className="mt-3 text-sm leading-relaxed text-cream-dim">
              Plan trips, invite people, and build the itinerary together.
            </p>
          </div>

          {isConfigured ? <SignUpForm /> : <NotConfigured />}

          <p className="mt-6 text-center text-sm text-cream-dim">
            Already have an account?{' '}
            <Link
              to="/sign-in"
              className="font-medium text-amber-light underline-offset-4 transition-colors hover:text-cream hover:underline focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none"
            >
              Sign in
            </Link>
          </p>
        </div>
      </section>
    </main>
  )
}
