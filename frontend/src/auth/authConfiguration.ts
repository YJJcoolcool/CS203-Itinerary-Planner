import { createContext, useContext } from 'react'

type AuthConfiguration = {
  isConfigured: boolean
}

export const publishableKey = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

export const AuthConfigurationContext = createContext<AuthConfiguration>({
  isConfigured: Boolean(publishableKey),
})

export function useAuthConfiguration() {
  return useContext(AuthConfigurationContext)
}
