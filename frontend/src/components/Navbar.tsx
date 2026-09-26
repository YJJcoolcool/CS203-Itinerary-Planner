import { Show, SignInButton, SignUpButton, UserButton, useAuth } from '@clerk/react'
import { Link } from 'react-router'
import { useAuthConfiguration } from '../auth/authConfiguration'
import { getClerkAppearance } from '../auth/clerkAppearance'
import { useTheme } from '../lib/theme'

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

function SunIcon() {
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
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

function MoonIcon() {
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
      <path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5Z" />
    </svg>
  )
}

const FOCUS_RING =
  'focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface focus-visible:outline-none'

function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      className={`flex h-9 w-9 items-center justify-center rounded-full border border-border bg-surface-subtle text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`}
    >
      {isDark ? <SunIcon /> : <MoonIcon />}
    </button>
  )
}

/** Clerk-only: renders the "My Trips" link for signed-in users. */
function MyTripsLink() {
  const { isSignedIn } = useAuth()

  if (!isSignedIn) {
    return null
  }

  return (
    <Link
      to="/trips"
      className={`rounded-lg px-2 py-1.5 text-xs font-medium text-muted transition-colors hover:text-foreground sm:text-sm ${FOCUS_RING}`}
    >
      My Trips
    </Link>
  )
}

/** Clerk-only: sign-in / sign-up for signed-out users, avatar menu when signed in. */
function NavAuthControls() {
  const { theme } = useTheme()

  return (
    <div className="flex items-center gap-2 sm:gap-3">
      <Show when="signed-out">
        <SignInButton mode="modal">
          <button
            type="button"
            className={`inline-flex rounded-xl border border-border px-3 py-1.5 text-xs font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground sm:px-4 sm:py-2 sm:text-sm ${FOCUS_RING}`}
          >
            Sign in
          </button>
        </SignInButton>
        <SignUpButton mode="modal">
          <button
            type="button"
            className={`inline-flex rounded-xl bg-accent px-3 py-1.5 text-xs font-semibold text-accent-contrast transition-colors hover:bg-accent-strong sm:px-4 sm:py-2 sm:text-sm ${FOCUS_RING}`}
          >
            Get started
          </button>
        </SignUpButton>
      </Show>
      <Show when="signed-in">
        <div className="flex items-center rounded-full border border-border bg-surface-subtle p-0.5">
          <UserButton appearance={getClerkAppearance(theme)} />
        </div>
      </Show>
    </div>
  )
}

/**
 * Shared top navigation. Safe to render on public pages: the wordmark and theme
 * toggle always render, while any Clerk component is gated on `isConfigured` so
 * no Clerk context is required when auth is not set up.
 */
export function Navbar() {
  const { isConfigured } = useAuthConfiguration()

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface/80 backdrop-blur-md">
      <nav
        aria-label="Primary"
        className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-3 px-4 sm:px-8"
      >
        <div className="flex min-w-0 items-center gap-3 sm:gap-6">
          <Link
            to="/"
            className={`flex items-center gap-2.5 rounded-lg ${FOCUS_RING}`}
          >
            <span
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-accent-soft text-accent ring-1 ring-accent/25"
              aria-hidden="true"
            >
              <CompassMark />
            </span>
            <span className="font-display text-base font-medium tracking-tight text-foreground sm:text-lg">
              Agile Trips
            </span>
          </Link>

          {isConfigured ? <MyTripsLink /> : null}
        </div>

        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          <ThemeToggle />
          {isConfigured ? <NavAuthControls /> : null}
        </div>
      </nav>
    </header>
  )
}
