import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '@clerk/react';
import { useActivityLocations, Activity } from '../auth/useActivityLocations';

export function TripItinerary({ tripId }: { tripId: number }) {
    const [activities, setActivities] = useState<Activity[]>([]);
    
    // Define the variables needed for the API call
    const { getToken } = useAuth();
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  
    // Initialize custom hook
    const { locationsByActivity, fetchLocationsForActivities, isLoading } = useActivityLocations(tripId);

    const fetchActivities = useCallback(async (tripId: number) => {
        try {
            const token = await getToken();
            if (!token) throw new Error('No active Clerk session token is available.');

            // Calling the ActivityController endpoint directly
            const response = await fetch(`${apiUrl}/api/trips/${tripId}/activities`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error(`Failed to fetch activities: ${response.status}`);
            }

            const data: Activity[] = await response.json();
            setActivities(data);
        } catch (error) {
            console.error('Error fetching activities:', error);
        }
    }, [getToken, apiUrl]);

    // When the component mounts, fetch activities
    useEffect(() => {
        fetchActivities(tripId);
    }, [tripId, fetchActivities]);

    // Whenever 'activities' updates, fetch their locations
    useEffect(() => {
        if (activities.length > 0) {
            fetchLocationsForActivities(activities);
        }
    }, [activities, fetchLocationsForActivities]);

    // Handle loading state
    if (isLoading) return <div className="p-4 text-gray-600">Loading locations...</div>;

    return (
        <div className="flex flex-col gap-4 p-4">
            {activities.map((activity) => (
                <div key={activity.id} className="p-4 bg-white border border-gray-200 rounded-xl shadow-sm">
                    <h3 className="text-lg font-bold text-gray-900">{activity.name}</h3>
                    
                    {/* Lookup the locations for this specific activity from the state map */}
                    <ul className="mt-2 text-sm text-gray-600">
                        {locationsByActivity[activity.id]?.map((location) => (
                            <li key={location.id} className="flex items-center gap-2">
                                📍 {location.name} {location.isIndoor ? '(Indoor)' : '(Outdoor)'}
                            </li>
                        ))}

                        {/* Fallback if no locations exist for this activity yet */}
                        {!locationsByActivity[activity.id] && (
                            <li className="italic text-gray-400">No locations added yet.</li>
                        )}
                    </ul>
                </div>
            ))}
        </div>
    );
}