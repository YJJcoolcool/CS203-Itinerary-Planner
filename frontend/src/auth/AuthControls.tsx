import {
  Show,
  SignInButton,
  SignOutButton,
  SignUpButton,
  UserButton,
  useUser,
} from '@clerk/react'
import { useState } from 'react'
import { useAuthConfiguration } from './authConfiguration'
import { useBackendProfile } from './useBackendProfile'

function CurrentUser() {
  const { user } = useUser()
  const name =
    user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? 'your account'

  return <span className="auth-user-name">Signed in as {name}</span>
}

function BackendProfileControl() {
  const { user } = useUser()
  const syncProfile = useBackendProfile()
  const [status, setStatus] = useState<string>()
  const [isLoading, setIsLoading] = useState(false)

  const handleConnect = async () => {
    setIsLoading(true)
    setStatus(undefined)

    try {
      const profile = await syncProfile(
        user?.fullName ?? user?.primaryEmailAddress?.emailAddress ?? 'New user',
      )
      setStatus(`Backend profile connected (internal ID ${profile.id}).`)
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Unable to connect profile.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <button
        type="button"
        className="auth-button"
        onClick={handleConnect}
        disabled={isLoading}
      >
        {isLoading ? 'Connecting…' : 'Connect backend profile'}
      </button>
      {status && <span className="auth-profile-status" role="status">{status}</span>}
    </>
  )
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
        <BackendProfileControl />
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
