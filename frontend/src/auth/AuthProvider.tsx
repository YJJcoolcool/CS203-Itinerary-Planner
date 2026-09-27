import { ClerkProvider } from '@clerk/react'
import type { PropsWithChildren } from 'react'
import { useTheme } from '../lib/theme'
import { AuthConfigurationContext, publishableKey } from './authConfiguration'
import { getClerkAppearance } from './clerkAppearance'

/**
 * Owns Clerk configuration and keeps Clerk's own components (including the
 * sign-in/sign-up modals) in sync with the active light/dark theme. Requires a
 * `ThemeProvider` above it (set up in `main.tsx`).
 */
export function AuthProvider({ children }: PropsWithChildren) {
  const { theme } = useTheme()
  const configuration = { isConfigured: Boolean(publishableKey) }

  if (!publishableKey) {
    return (
      <AuthConfigurationContext.Provider value={configuration}>
        {children}
      </AuthConfigurationContext.Provider>
    )
  }

  return (
    <AuthConfigurationContext.Provider value={configuration}>
      <ClerkProvider publishableKey={publishableKey} appearance={getClerkAppearance(theme)}>
        {children}
      </ClerkProvider>
    </AuthConfigurationContext.Provider>
  )
}
