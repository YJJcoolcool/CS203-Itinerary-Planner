import { useAuth } from '@clerk/react'
import { Navigate, useLocation } from 'react-router'
import type { PropsWithChildren } from 'react'
import { useAuthConfiguration } from './authConfiguration'

function SignedInGuard({ children }: PropsWithChildren) {
  const { isLoaded, isSignedIn } = useAuth()
  const location = useLocation()

  if (!isLoaded) {
    return null
  }

  if (isSignedIn) {
    return <>{children}</>
  }

  // Bounce to the landing page with the origin recorded; LandingPage opens the
  // sign-in modal once from that state. We deliberately do NOT open the modal
  // here, so signing out never triggers an immediate re-login prompt.
  return <Navigate to="/" replace state={{ from: location }} />
}

export function RequireAuth({ children }: PropsWithChildren) {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return <>{children}</>
  }

  return <SignedInGuard>{children}</SignedInGuard>
}
