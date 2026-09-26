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

  return <Navigate to="/sign-in" replace state={{ from: location }} />
}

export function RequireAuth({ children }: PropsWithChildren) {
  const { isConfigured } = useAuthConfiguration()

  if (!isConfigured) {
    return <>{children}</>
  }

  return <SignedInGuard>{children}</SignedInGuard>
}
