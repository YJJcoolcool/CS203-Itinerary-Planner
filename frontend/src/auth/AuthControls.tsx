import {
  Show,
  SignInButton,
  SignOutButton,
  SignUpButton,
  UserButton,
  useUser,
} from '@clerk/react'
import { useAuthConfiguration } from './authConfiguration'

function CurrentUser() {
  const { user } = useUser()
  const name =
    user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? 'your account'

  return <span className="auth-user-name">Signed in as {name}</span>
}

/**
 * Clerk-aware actions only. Replace this component's markup/styles to redesign
 * authentication UI without touching Clerk configuration or app session state.
 */
export function AuthControls() {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return (
      <p className="auth-setup-notice" role="status">
        Authentication will be available after Clerk is configured.
      </p>
    )
  }

  return (
    <div className="auth-controls" aria-label="Account controls">
      <Show when="signed-out">
        <SignInButton mode="modal">
          <button type="button" className="auth-button">
            Sign in
          </button>
        </SignInButton>
        <SignUpButton mode="modal">
          <button type="button" className="auth-button auth-button-primary">
            Sign up
          </button>
        </SignUpButton>
      </Show>
      <Show when="signed-in">
        <CurrentUser />
        <UserButton />
        <SignOutButton>
          <button type="button" className="auth-button">
            Sign out
          </button>
        </SignOutButton>
      </Show>
    </div>
  )
}
