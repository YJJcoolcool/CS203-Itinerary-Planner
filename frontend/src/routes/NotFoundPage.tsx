import { Link } from 'react-router'

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

export function NotFoundPage() {
  return (
    <main className="page-dark relative flex min-h-screen flex-col overflow-hidden">
      {/* Ambient depth — soft accent glow, purely decorative. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-32 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-amber/[0.06] blur-[130px]" />
      </div>

      {/* Top bar — matches the Trips dashboard so the 404 feels part of the product. */}
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

          <span className="font-mono text-[11px] tracking-[0.28em] text-cream-dim/45 uppercase">
            404
          </span>
        </div>
      </header>

      <section className="relative z-10 flex flex-1 items-center justify-center px-6 py-16 sm:px-8">
        <div className="relative w-full max-w-xl text-center">
          {/* Oversized ghost numeral for editorial depth. */}
          <span
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-[60%] font-display text-[10rem] leading-none font-semibold text-white/[0.03] select-none sm:text-[14rem]"
          >
            404
          </span>

          <div className="animate-rise relative">
            <span className="inline-flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-cream-dim/60 uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-amber" aria-hidden="true" />
              Not found
            </span>

            <h1 className="mt-5 font-display text-4xl leading-tight font-medium tracking-tight text-cream sm:text-5xl">
              This page wandered off.
            </h1>

            <p className="mt-5 text-[15px] leading-relaxed text-cream-dim">
              The page you&apos;re looking for doesn&apos;t exist or may have moved.
            </p>

            <div className="mt-9 flex justify-center">
              <Link
                to="/"
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-amber px-5 py-3 text-sm font-semibold text-navy transition-colors hover:bg-amber-light focus-visible:ring-2 focus-visible:ring-amber-light focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none"
              >
                <ArrowLeftIcon />
                Back to home
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}
