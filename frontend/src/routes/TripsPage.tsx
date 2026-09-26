import { SignOutButton } from '@clerk/react'
import { Link } from 'react-router'
import { useAuthConfiguration } from '../auth/authConfiguration'

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

function PlusIcon({ className = '' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      className={`h-4 w-4 ${className}`}
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function ArrowLeftIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path d="M19 12H5M11 18l-6-6 6-6" />
    </svg>
  )
}

function UserIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-3.5 w-3.5"
      aria-hidden="true"
    >
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 19.5a7 7 0 0 1 14 0" />
    </svg>
  )
}

function SignOutIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-3.5 w-3.5"
      aria-hidden="true"
    >
      <path d="M9 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  )
}

/**
 * Account control for signed-in, Clerk-configured sessions. Uses Clerk's
 * `SignOutButton` because, unlike `<UserButton>`, it exposes a `redirectUrl`
 * prop (Clerk v6.17) — so we can guarantee the post-sign-out landing on `/`
 * instead of bouncing back into the guarded `/trips` route.
 *
 * Only rendered when `isConfigured` is true, so no Clerk hook/context is
 * touched in the unconfigured fallback.
 */
function AccountControl() {
  return (
    <SignOutButton redirectUrl="/">
      <button
        type="button"
        aria-label="Sign out of your account"
        className="flex items-center gap-2.5 rounded-full border border-white/[0.08] bg-white/[0.03] py-1.5 pr-2.5 pl-1.5 text-cream-dim transition-colors hover:border-white/20 hover:bg-white/[0.06] hover:text-cream focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none"
      >
        <span
          className="flex h-7 w-7 items-center justify-center rounded-full bg-amber/15 text-amber ring-1 ring-amber/25"
          aria-hidden="true"
        >
          <UserIcon />
        </span>
        <span className="text-xs font-medium">Sign out</span>
        <span className="hidden text-cream-dim/60 sm:inline" aria-hidden="true">
          <SignOutIcon />
        </span>
      </button>
    </SignOutButton>
  )
}

/** Neutral, non-interactive fallback used when Clerk is not configured. */
function StaticAccountChip() {
  return (
    <div className="flex cursor-default items-center gap-2.5 rounded-full border border-white/[0.08] bg-white/[0.03] py-1.5 pr-2 pl-1.5 sm:pr-3.5">
      <span
        className="flex h-7 w-7 items-center justify-center rounded-full bg-amber/15 text-amber ring-1 ring-amber/25"
        aria-hidden="true"
      >
        <UserIcon />
      </span>
      <span className="hidden text-xs font-medium text-cream-dim sm:inline">Account</span>
    </div>
  )
}

function ItinerarySkeleton() {
  const rows = [
    { dot: 'bg-blue', bar: 'w-3/5' },
    { dot: 'bg-amber', bar: 'w-4/5' },
    { dot: 'bg-sage', bar: 'w-2/5' },
  ]

  return (
    <div
      aria-hidden="true"
      className="rounded-xl border border-white/[0.06] bg-white/[0.015] p-5"
    >
      <div className="flex items-center justify-between">
        <span className="font-mono text-[10px] uppercase tracking-[0.22em] text-cream-dim/45">
          Itinerary preview
        </span>
        <span className="font-mono text-[10px] text-cream-dim/35">0 stops</span>
      </div>

      <div className="mt-5 flex flex-col gap-4">
        {rows.map((row) => (
          <div key={row.bar} className="flex items-center gap-3">
            <span className={`h-2 w-2 flex-shrink-0 rounded-full opacity-70 ${row.dot}`} />
            <span className={`h-2 rounded-full bg-white/[0.06] ${row.bar}`} />
          </div>
        ))}
      </div>

      <div className="mt-5 flex items-center gap-2 rounded-lg border border-dashed border-white/[0.1] px-3 py-2.5">
        <PlusIcon className="text-cream-dim/30" />
        <span className="font-mono text-[10px] uppercase tracking-[0.2em] text-cream-dim/30">
          Add activity
        </span>
      </div>
    </div>
  )
}

export function TripsPage() {
  const { isConfigured } = useAuthConfiguration()

  return (
    <main className="page-dark relative flex min-h-screen flex-col overflow-hidden">
      {/* Ambient depth — soft accent glows, purely decorative. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-amber/[0.07] blur-[120px]" />
        <div className="absolute -right-16 -bottom-32 h-80 w-80 rounded-full bg-blue/[0.05] blur-[120px]" />
      </div>

      {/* Top bar */}
      <header className="relative z-10 border-b border-white/[0.06]">
        <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-6 sm:px-8">
          <div className="flex items-center gap-3">
            <span
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber/10 text-amber ring-1 ring-amber/20"
              aria-hidden="true"
            >
              <CompassMark />
            </span>
            <span className="font-display text-lg font-medium tracking-tight text-cream">
              Itinerary Planner
            </span>
          </div>

          {/* Account area. Real sign-out when Clerk is configured; a neutral,
              non-interactive chip otherwise (so no Clerk context is required). */}
          {isConfigured ? <AccountControl /> : <StaticAccountChip />}
        </div>
      </header>

      {/* Empty dashboard state */}
      <section className="relative z-10 flex flex-1 items-center justify-center px-6 py-16 sm:px-8">
        <div className="w-full max-w-3xl">
          <div className="animate-rise grid gap-10 rounded-2xl border border-white/[0.08] bg-white/[0.02] p-8 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.9)] sm:p-12 md:grid-cols-[1.15fr_0.85fr] md:items-center">
            <div>
              <span className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.28em] text-cream-dim/60">
                <span className="h-1.5 w-1.5 rounded-full bg-amber" aria-hidden="true" />
                My Trips
              </span>

              <h1 className="mt-5 font-display text-4xl leading-[1.05] font-medium tracking-tight text-cream sm:text-5xl">
                No trips <span className="text-amber-light italic">yet</span>.
              </h1>

              <p className="mt-5 max-w-md text-[15px] leading-relaxed text-cream-dim">
                This dashboard is a placeholder. Once trips can be created and loaded,
                they&apos;ll show up here — for now there is nothing to display.
              </p>

              <div className="mt-9 flex flex-col gap-3 sm:flex-row sm:items-center">
                <button
                  type="button"
                  disabled
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-amber px-5 py-3 text-sm font-semibold text-navy transition-colors disabled:cursor-not-allowed disabled:opacity-40 disabled:saturate-50"
                >
                  <PlusIcon />
                  Create your first trip
                </button>

                <Link
                  to="/"
                  className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/[0.12] px-5 py-3 text-sm font-medium text-cream-dim transition-colors hover:border-white/20 hover:bg-white/[0.05] hover:text-cream focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none"
                >
                  <ArrowLeftIcon />
                  Back to home
                </Link>
              </div>

              <p className="mt-4 font-mono text-[11px] text-cream-dim/45">
                Trip creation isn&apos;t wired up yet.
              </p>
            </div>

            <ItinerarySkeleton />
          </div>
        </div>
      </section>
    </main>
  )
}
