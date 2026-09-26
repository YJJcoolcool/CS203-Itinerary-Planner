import { Link } from 'react-router'
import { Navbar } from '../components/Navbar'

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
    <main className="relative flex min-h-screen flex-col overflow-hidden bg-surface text-foreground">
      {/* Ambient depth — soft accent glow, purely decorative. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-32 left-1/2 h-96 w-96 -translate-x-1/2 rounded-full bg-accent/10 blur-[130px]" />
      </div>

      <Navbar />

      <section className="relative z-10 flex flex-1 items-center justify-center px-6 py-16 sm:px-8">
        <div className="relative w-full max-w-xl text-center">
          {/* Oversized ghost numeral for editorial depth. */}
          <span
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-[60%] font-display text-[10rem] leading-none font-semibold text-foreground/[0.05] select-none sm:text-[14rem]"
          >
            404
          </span>

          <div className="animate-rise relative">
            <span className="inline-flex items-center gap-2 font-mono text-[11px] tracking-[0.28em] text-faint uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" aria-hidden="true" />
              Not found
            </span>

            <h1 className="mt-5 font-display text-4xl leading-tight font-medium tracking-tight text-foreground sm:text-5xl">
              This page wandered off.
            </h1>

            <p className="mt-5 text-[15px] leading-relaxed text-muted">
              The page you&apos;re looking for doesn&apos;t exist or may have moved.
            </p>

            <div className="mt-9 flex justify-center">
              <Link
                to="/"
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-5 py-3 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface focus-visible:outline-none"
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
