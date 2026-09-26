import { SignIn, useAuth } from '@clerk/react'
import { Link, Navigate } from 'react-router'
import { Navbar } from '../components/Navbar'
import { useAuthConfiguration } from '../auth/authConfiguration'
import { clerkAppearance } from '../auth/clerkAppearance'

function NotConfigured() {
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 text-center">
      <p className="text-sm leading-relaxed text-cream-dim">
        Authentication is not configured. Set{' '}
        <code className="font-mono text-xs text-cream">VITE_CLERK_PUBLISHABLE_KEY</code> to enable
        sign-in.
      </p>
    </div>
  )
}

function SignInForm() {
  const { isLoaded, isSignedIn } = useAuth()

  if (!isLoaded) {
    return null
  }

  if (isSignedIn) {
    return <Navigate to="/trips" replace />
  }

  return (
    <SignIn
      routing="path"
      path="/sign-in"
      appearance={clerkAppearance}
      fallbackRedirectUrl="/trips"
      signUpUrl="/sign-up"
    />
  )
}

export function SignInPage() {
  const { isConfigured } = useAuthConfiguration()

  return (
    <main className="page-dark relative flex min-h-screen flex-col overflow-hidden">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-32 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-amber/[0.06] blur-[130px]" />
      </div>

      <Navbar variant="dark" pageLabel="Sign in" />

      <section className="animate-rise relative z-10 flex flex-1 items-center justify-center px-6 py-16 sm:px-8">
        <div className="w-full max-w-md">
          <div className="mb-8 text-center">
            <span className="inline-flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-cream-dim/60 uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-amber" aria-hidden="true" />
              Welcome back
            </span>
            <h1 className="mt-4 font-display text-3xl leading-tight font-medium tracking-tight text-cream sm:text-4xl">
              Sign in to your trips.
            </h1>
            <p className="mt-3 text-sm leading-relaxed text-cream-dim">
              Pick up your itineraries where you left off.
            </p>
          </div>

          {isConfigured ? <SignInForm /> : <NotConfigured />}

          <p className="mt-6 text-center text-sm text-cream-dim">
            New here?{' '}
            <Link
              to="/sign-up"
              className="font-medium text-amber-light underline-offset-4 transition-colors hover:text-cream hover:underline focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none"
            >
              Create an account
            </Link>
          </p>
        </div>
      </section>
    </main>
  )
}
