import { useAuth } from '@clerk/react'
import { useCallback } from 'react'

type BackendProfile = {
  id: number
  clerkUserId: string
  displayName: string | null
}

const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/** Sends the active Clerk session token to the backend profile endpoint. */
export function useBackendProfile() {
  const { getToken } = useAuth()

  return useCallback(
    async (displayName: string): Promise<BackendProfile> => {
      const token = await getToken()

      if (!token) {
        throw new Error('No active Clerk session token is available.')
      }

      const response = await fetch(`${apiUrl}/api/users/me`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ displayName }),
      })

      if (!response.ok) {
        throw new Error(`Backend profile request failed (${response.status}).`)
      }

      return response.json() as Promise<BackendProfile>
    },
    [getToken],
  )
}
