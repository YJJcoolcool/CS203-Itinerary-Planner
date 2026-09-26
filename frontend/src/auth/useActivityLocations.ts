import { useAuth } from '@clerk/react'
import { useState, useCallback } from 'react'

// Backend entities
export interface Location {
    id: number;
    name: string;
    category: string;
    latitude: number;
    longitude: number;
    isIndoor: boolean;
}

export interface Activity {
    id: number;
    name: string;
}

export function useActivityLocations(tripId: number) {
    const { getToken } = useAuth()
    // put locations in a dictionary keyed by activity id
    const[locationsByActivity, setLocationsByActivity] = useState<Record<number, Location[]>>({})
    const [isLoading, setIsLoading] = useState(false)

    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

    const fetchLocationsForActivities = useCallback(async (activities: Activity[]) => {
        setIsLoading(true)

        try {
            const token = await getToken()
            if(!token) throw new Error('No active Clerk session token is available.')
            
            // Map each activity to a fetch promise 
            const locationPromises = activities.map(async (activity) => {
                const response = await fetch(`${apiUrl}/api/trips/${tripId}/activities/${activity.id}/locations`, {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        'Content-Type': 'application/json',
                    },
                })

                if (!response.ok) {
                    throw new Error(`Failed to fetch locations for activity ${activity.id}`)
                }

                const data: Location[] = await response.json()
                return { activityId: activity.id, locations: data }
            })

            // Execute all requests concurrently 
            const results = await Promise.all(locationPromises)

            // Transform the results into a lookup object
            const newLocationsState: Record<number, Location[]> = {}
            results.forEach(({ activityId, locations }) => {
                newLocationsState[activityId] = locations
            })

            setLocationsByActivity(newLocationsState)
        } catch (error) {
            console.error('Error fetching locations:', error)
        } finally {
            setIsLoading(false)
        }
    }, [getToken, tripId, apiUrl])
    
    return { locationsByActivity, fetchLocationsForActivities, isLoading }
}


















