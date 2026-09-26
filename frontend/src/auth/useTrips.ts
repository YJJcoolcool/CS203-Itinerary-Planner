import { useAuth } from '@clerk/react'
import { useState, useCallback } from 'react'
import { Trip } from '../components/TripDetailsCard'

export function useTrips() {
    const { getToken } = useAuth()
    const [trips, setTrips] = useState<Trip[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

    const fetchTrips = useCallback(async () => {
        setIsLoading(true)
        setError(null)

        try {
            const token = await getToken()
            if (!token) throw new Error('No active Clerk session token is available.')

            const response = await fetch(`${apiUrl}/api/trips`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            })

            if (!response.ok) {
                throw new Error(`Failed to fetch trips: ${response.status}`)
            }

            const data: Trip[] = await response.json()
            setTrips(data)
        } catch (err) {
            console.error('Error fetching trips:', err)
            setError(err instanceof Error ? err.message : 'An unknown error occurred')
        } finally {
            setIsLoading(false)
        }
    }, [getToken, apiUrl])

    return { trips, fetchTrips, isLoading, error }
}