import { useEffect } from 'react';
import { useTrips } from '../auth/useTrips';

// Helper function to format dates
const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
    });
};

export function TripList() {
    const { trips, fetchTrips, isLoading, error } = useTrips();

    // Fetch trips as soon as the component loads
    useEffect(() => {
        fetchTrips();
    }, [fetchTrips]);

    if (isLoading) return <div className="p-8 text-gray-600 font-medium">Loading your trips...</div>;
    if (error) return <div className="p-8 text-red-500">Error: {error}</div>;

    return (
        <div className="p-8">
            <h2 className="text-3xl font-bold font-serif text-gray-900 mb-6">My Trips</h2>
            
            {trips.length === 0 && !isLoading && (
                <div className="p-8 border-2 border-dashed border-gray-300 rounded-xl text-center text-gray-500">
                    No trips found. Time to start planning!
                </div>
            )}

            {/* Responsive grid: 1 column on mobile, 2 on tablets, 3 on large screens */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {trips.map((trip) => (
                    <div 
                        key={trip.id} 
                        className="p-6 bg-white border border-gray-200 rounded-2xl shadow-sm hover:shadow-md hover:border-gray-300 transition-all cursor-pointer"
                    >
                        <h3 className="text-xl font-bold text-gray-900 mb-2">{trip.name}</h3>
                        <p className="text-sm font-mono text-gray-600 mb-4">
                            🗓️ {formatDate(trip.startDate)} - {formatDate(trip.endDate)}
                        </p>
                        
                        <div className="flex items-center gap-2">
                            <span className="text-xs text-gray-500 font-medium uppercase tracking-wider">Travelers:</span>
                            <div className="flex -space-x-2">
                                {trip.users?.map((user) => (
                                    <div 
                                        key={user.id} 
                                        className="w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-white border-2 border-white"
                                        style={{ backgroundColor: `hsl(${(user.id * 137) % 360}, 60%, 45%)` }}
                                        title={user.displayName}
                                    >
                                        {user.displayName.charAt(0).toUpperCase()}
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}