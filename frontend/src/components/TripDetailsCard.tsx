import React from 'react';

export interface User {
    id: number;
    displayName: string;
}

export interface Trip {
    id: number;
    name: string;
    startDate: string; 
    endDate: string; 
    owner?: User;
    users: User[];
}

interface TripDetailsCardProps {
    trip: Trip;
}

// Helper function to calculate the number of days between two dates
const calculateDays = (start: string, end: string) => {
    const startDate = new Date(start);
    const endDate = new Date(end);
    const diffTime = Math.abs(endDate.getTime() - startDate.getTime());
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1; // Inclusive of start day
};

// Helper function to format dates (e.g., "Oct 14, 2026")
const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
    });
};

export function TripDetailsCard({ trip }: TripDetailsCardProps) {
    const days = calculateDays(trip.startDate, trip.endDate);
    
    // Fallback initials for the avatar if displayName is missing
    const getInitials = (name: string) => {
        return name ? name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '??';
    };

    return (
        <div className="bg-[#162032] border border-white/10 rounded-2xl p-6 text-[#f5efe6] shadow-lg mb-6">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
                
                {/* Trip Name and Core Details */}
                <div>
                    <h1 className="text-3xl font-bold font-serif text-white tracking-tight mb-2">
                        {trip.name}
                    </h1>
                    <div className="flex flex-wrap items-center gap-3 text-sm font-mono text-[#d6ccbf]">
                        <span className="flex items-center gap-1 bg-[#1e2f45] px-3 py-1 rounded-full">
                            🗓️ {formatDate(trip.startDate)} - {formatDate(trip.endDate)}
                        </span>
                        <span className="flex items-center gap-1 bg-[#1e2f45] px-3 py-1 rounded-full">
                            ⏱️ {days} {days === 1 ? 'day' : 'days'}
                        </span>
                        {/* Placeholder for budget, as noted in FRONTEND_PAGES.md Gaps */}
                        <span className="flex items-center gap-1 bg-[#1e2f45] px-3 py-1 rounded-full text-[#5c8a6e]">
                            💰 Budget: TBD
                        </span>
                    </div>
                </div>

                {/* Travelers / Collaborators Stack */}
                <div className="flex items-center gap-3">
                    <div className="text-sm text-[#d6ccbf] font-medium">Travelers</div>
                    <div className="flex -space-x-3">
                        {trip.users.map((user, index) => (
                            <div 
                                key={user.id} 
                                className="w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold text-white border-2 border-[#162032] z-10 hover:z-20 transition-transform hover:scale-110 cursor-pointer"
                                style={{
                                    // Generate a stable background color based on the user's ID
                                    backgroundColor: `hsl(${(user.id * 137) % 360}, 60%, 45%)`
                                }}
                                title={user.displayName}
                            >
                                {getInitials(user.displayName)}
                            </div>
                        ))}
                    </div>
                    
                    {/* Add Member Button Placeholder */}
                    <button className="w-10 h-10 rounded-full flex items-center justify-center bg-[#d4882a] text-white hover:bg-[#e8a84c] border-2 border-[#162032] transition-colors shadow-sm ml-1" title="Invite Traveler">
                        +
                    </button>
                </div>

            </div>
        </div>
    );
}

