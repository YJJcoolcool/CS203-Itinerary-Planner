import {
  Show,
  SignInButton,
  SignUpButton,
  UserButton,
} from '@clerk/react'
import { Link } from 'react-router'
import { useAuthConfiguration } from '../auth/authConfiguration'
import { clerkAppearance } from '../auth/clerkAppearance'

type NavbarProps = {
  /** Use the light treatment on the public landing page and dark elsewhere. */
  variant?: 'light' | 'dark'
  /** Optional label shown on the right for pages such as sign-in and 404. */
  pageLabel?: string
  /** Only show the signed-in account control; useful for protected pages. */
  accountOnly?: boolean
}

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

function AccountMenu({ variant }: { variant: NavbarProps['variant'] }) {
  return (
    <Show when="signed-in">
      <UserButton
        appearance={variant === 'dark' ? clerkAppearance : undefined}
        userProfileMode="modal"
      >
        <UserButton.MenuItems>
          <UserButton.Action label="manageAccount" />
          <UserButton.Link label="Trips" href="/trips" />
          <UserButton.Action label="signOut" />
        </UserButton.MenuItems>
      </UserButton>
    </Show>
  )
}

function AuthActions({ variant, accountOnly }: { variant: NavbarProps['variant']; accountOnly: boolean }) {
  if (accountOnly) {
    return <AccountMenu variant={variant} />
  }

  return (
    <>
      <Show when="signed-out">
        <div className="flex items-center gap-2">
          <SignInButton mode="modal">
            <button
              type="button"
              className={
                variant === 'dark'
                  ? 'rounded-lg px-3 py-2 text-sm font-medium text-cream-dim transition-colors hover:bg-white/[0.05] hover:text-cream'
                  : 'rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900'
              }
            >
              Sign in
            </button>
          </SignInButton>
          <SignUpButton mode="modal">
            <button
              type="button"
              className={
                variant === 'dark'
                  ? 'rounded-lg bg-amber px-3.5 py-2 text-sm font-semibold text-navy transition-colors hover:bg-amber-light'
                  : 'rounded-lg bg-gray-900 px-3.5 py-2 text-sm font-semibold text-white transition-colors hover:bg-gray-700'
              }
            >
              Create account
            </button>
          </SignUpButton>
        </div>
      </Show>

      <AccountMenu variant={variant} />
    </>
  )
}

export function Navbar({
  variant = 'dark',
  pageLabel,
  accountOnly = false,
}: NavbarProps) {
  const { isConfigured } = useAuthConfiguration()

  const isDark = variant === 'dark'

  return (
    <header
      className={
        isDark
          ? 'relative z-20 border-b border-white/[0.06] bg-navy/95'
          : 'relative z-20 border-b border-gray-200 bg-white'
      }
    >
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-6 sm:px-8">
        <Link
          to="/"
          className={
            isDark
              ? 'flex items-center gap-3 rounded-xl focus-visible:ring-2 focus-visible:ring-amber/70 focus-visible:ring-offset-2 focus-visible:ring-offset-navy focus-visible:outline-none'
              : 'flex items-center gap-3 rounded-xl focus-visible:ring-2 focus-visible:ring-gray-900 focus-visible:ring-offset-2 focus-visible:outline-none'
          }
          aria-label="Itinerary Planner home"
        >
          <span
            className={
              isDark
                ? 'flex h-9 w-9 items-center justify-center rounded-xl bg-amber/10 text-amber ring-1 ring-amber/20'
                : 'flex h-9 w-9 items-center justify-center rounded-xl bg-gray-100 text-gray-900 ring-1 ring-gray-200'
            }
            aria-hidden="true"
          >
            <CompassMark />
          </span>
          <span
            className={
              isDark
                ? 'font-display text-lg font-medium tracking-tight text-cream'
                : 'font-display text-lg font-medium tracking-tight text-gray-900'
            }
          >
            Itinerary Planner
          </span>
        </Link>

        <div className="flex items-center gap-3">
          {pageLabel && (
            <span
              className={
                isDark
                  ? 'font-mono text-[11px] tracking-[0.28em] text-cream-dim/45 uppercase'
                  : 'font-mono text-[11px] tracking-[0.28em] text-gray-500 uppercase'
              }
            >
              {pageLabel}
            </span>
          )}

          {isConfigured ? (
            <AuthActions variant={variant} accountOnly={accountOnly} />
          ) : (
            !accountOnly && pageLabel === undefined && (
              <span
                className={
                  isDark
                    ? 'text-xs text-cream-dim/45'
                    : 'text-xs text-gray-500'
                }
              >
                Authentication unavailable
              </span>
            )
          )}
        </div>
      </div>
    </header>
  )
}
