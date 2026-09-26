import { Link } from 'react-router'
import { Navbar } from '../components/Navbar'
import { useAuthConfiguration } from '../auth/authConfiguration'

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
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-amber/[0.07] blur-[120px]" />
        <div className="absolute -right-16 -bottom-32 h-80 w-80 rounded-full bg-blue/[0.05] blur-[120px]" />
      </div>

      <Navbar variant="dark" accountOnly={isConfigured} />

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
