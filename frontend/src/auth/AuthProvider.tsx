import { ClerkProvider } from '@clerk/react'
import type { PropsWithChildren } from 'react'
import { AuthConfigurationContext, publishableKey } from './authConfiguration'

/**
 * Owns Clerk configuration. Keep visual authentication components outside this
 * provider so their design can change without changing session handling.
 */
export function AuthProvider({ children }: PropsWithChildren) {
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
      <ClerkProvider
        publishableKey={publishableKey}
        localization={{
          userButton: {
            action__manageAccount: 'Settings',
          },
        }}
      >
        {children}
      </ClerkProvider>
    </AuthConfigurationContext.Provider>
  )
}
