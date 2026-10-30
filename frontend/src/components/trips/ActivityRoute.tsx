
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '@clerk/react'
import { ApiError, getActivityRoute } from '../../lib/api'
import type { Activity, Location, RouteMode } from '../../types/trip'
import ActivityRouteMap from './ActivityRouteMap'
import {
    formatDistance,
    formatDuration,
    formatRouteTimeRange,
    getDisplayedDuration,
    getMinutesLate,
    getRouteDisplayInfo,
    getRouteModeButtonLabel,
    getRouteModeLabel,
    getRouteMapSegments,
    getPublicTransportItineraries,
    getItineraryMapSegments,
    getItineraryDirections,
    getRouteInstructions,
} from '../../lib/routingUtils'

const FOCUS_RING = 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset'

const ALL_MODES: { mode: RouteMode; icon: string }[] = [
    { mode: 'PUBLIC_TRANSPORT', icon: '🚌' },
    { mode: 'DRIVING', icon: '🚗' },
    { mode: 'WALKING', icon: '🚶' },
    { mode: 'CYCLING', icon: '🚲' },
]

interface ActivityRouteProps {
    tripId: string
    fromActivity: Activity
    toActivity: Activity
    fromLocations: Location[] | undefined
    toLocations: Location[] | undefined
}

export function ActivityRoute({
    tripId,
    fromActivity,
    toActivity,
    fromLocations,
    toLocations,
}: ActivityRouteProps) {
    const { getToken } = useAuth()
    const menuRef = useRef<HTMLDivElement>(null)

    const [isLoading, setIsLoading] = useState(false)
    const [isMenuOpen, setIsMenuOpen] = useState(false)
    const [selectedMode, setSelectedMode] =
        useState<RouteMode>('PUBLIC_TRANSPORT')
    const [routeResponse, setRouteResponse] = useState<Awaited<
        ReturnType<typeof getActivityRoute>
    > | null>(null)
    const [selectedItineraryIndex, setSelectedItineraryIndex] = useState(0)
    const [isDirectionsExpanded, setIsDirectionsExpanded] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [isWarningDismissed, setIsWarningDismissed] = useState(false)
    
    // Close the dropdown when the user clicks outside it or presses Escape.
    useEffect(() => {
        if (!isMenuOpen) return

        function handlePointerDown(event: PointerEvent) {
        if (
            event.target instanceof Node &&
            !menuRef.current?.contains(event.target)
        ) {
            setIsMenuOpen(false)
        }
        }

        function handleKeyDown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            setIsMenuOpen(false)
        }
        }

        document.addEventListener('pointerdown', handlePointerDown)
        document.addEventListener('keydown', handleKeyDown)

        return () => {
        document.removeEventListener('pointerdown', handlePointerDown)
        document.removeEventListener('keydown', handleKeyDown)
        }
    }, [isMenuOpen])

    async function fetchRoute(mode: RouteMode) {
        setIsMenuOpen(false)
        setSelectedMode(mode)
        setError(null)
        setRouteResponse(null)
        setIsWarningDismissed(false)

        if (!fromActivity.endDateTime) {
        setError(
            `Add an end time to "${fromActivity.name}" before requesting directions.`,
        )
        return
        }

        if (fromLocations === undefined || toLocations === undefined) {
        setError('Locations are still loading. Please try again shortly.')
        return
        }

        if (fromLocations.length === 0 || toLocations.length === 0) {
        setError('Both activities need at least one location to calculate a route.')
        return
        }

        const departureDate = new Date(fromActivity.endDateTime)
        if (Number.isNaN(departureDate.getTime())) {
        setError('The previous activity has an invalid end time.')
        return
        }

        setIsLoading(true)

        try {
        const token = await getToken()
        if (!token) {
            throw new ApiError(401, 'No active session token is available.')
        }

        const response = await getActivityRoute(
            token,
            tripId,
            fromActivity.id,
            toActivity.id,
            mode,
            fromActivity.endDateTime,
        )

        console.log('Route data:', response.routeData)

        setRouteResponse(response)
        setSelectedItineraryIndex(0)
        setIsDirectionsExpanded(false)
        } catch (cause) {
        const reason =
            cause instanceof Error && cause.message
            ? cause.message
            : 'Please try again.'

        setError(`Couldn't get directions: ${reason}`)
        } finally {
        setIsLoading(false)
        }
    }

    const itineraries = routeResponse?.mode === 'PUBLIC_TRANSPORT'
        ? getPublicTransportItineraries(routeResponse)
        : []

    const selectedItinerary = itineraries[selectedItineraryIndex]

    const routeInfo = routeResponse?.mode === 'PUBLIC_TRANSPORT'
        ? selectedItinerary
            ? {
                durationSeconds: selectedItinerary.duration,
                walkingDistanceMetres: selectedItinerary.walkDistance,
                departureTime: new Date(selectedItinerary.startTime),
                arrivalTime: new Date(selectedItinerary.endTime),
            }
            : null
        : routeResponse
            ? getRouteDisplayInfo(routeResponse)
            : null

    const minutesLate =
        routeInfo && routeResponse
            ? getMinutesLate(routeInfo.arrivalTime, toActivity.startDateTime)
            : 0

    const selectedModeOption = ALL_MODES.find(
        ({ mode }) => mode === selectedMode,
    )!

    const otherModes = ALL_MODES.filter(
        ({ mode }) => mode !== selectedMode,
    )

    const routeMapSegments = routeResponse?.mode === 'PUBLIC_TRANSPORT'
        ? selectedItinerary
            ? getItineraryMapSegments(selectedItinerary)
            : []
        : routeResponse
            ? getRouteMapSegments(routeResponse)
            : []

    const directions = selectedItinerary
        ? getItineraryDirections(
            selectedItinerary,
            toLocations?.[0]?.name ?? routeResponse?.toLocationName ?? 'Destination',
        )
        : []
    
    const routeInstructions = routeResponse
        ? getRouteInstructions(routeResponse)
        : []

    return (
        <li className="flex flex-col items-center gap-2 py-1">
        <div ref={menuRef} className="relative">
            {/* Combined two-section directions control */}
            <div className="flex overflow-hidden rounded-full border border-border bg-surface-raised shadow-sm">
            <button
                type="button"
                onClick={() => void fetchRoute(selectedMode)}
                disabled={isLoading}
                className={`inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-surface-subtle hover:text-accent disabled:cursor-wait disabled:opacity-60 ${FOCUS_RING}`}
            >
                {isLoading ? (
                <>
                    <span
                    aria-hidden="true"
                    className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
                    />
                    Getting directions…
                </>
                ) : (
                <>
                    <span aria-hidden="true">{selectedModeOption.icon}</span>
                    Get Directions ({getRouteModeButtonLabel(selectedMode)})
                </>
                )}
            </button>

            <button
                type="button"
                aria-label="Choose another transport mode"
                aria-expanded={isMenuOpen}
                aria-haspopup="menu"
                onClick={() => setIsMenuOpen((open) => !open)}
                disabled={isLoading}
                className={`border-l border-border pl-3 pr-4 text-sm text-muted transition-colors hover:bg-surface-subtle hover:text-foreground disabled:opacity-60 ${FOCUS_RING}`}
            >
                <span aria-hidden="true">▼</span>
            </button>
            </div>

            {isMenuOpen && (
            <div
                role="menu"
                className="absolute right-0 top-full z-20 mt-2 min-w-48 overflow-hidden rounded-xl border border-border bg-surface-raised p-1 shadow-lg"
            >
                {otherModes.map(({ mode, icon }) => (
                <button
                    key={mode}
                    type="button"
                    role="menuitem"
                    onClick={() => void fetchRoute(mode)}
                    className={`flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-surface-subtle ${FOCUS_RING}`}
                >
                    <span aria-hidden="true">{icon}</span>
                    {getRouteModeLabel(mode)}
                </button>
                ))}
            </div>
            )}
        </div>

        {error && (
            <p role="alert" className="max-w-xl text-center text-xs text-terracotta">
            {error}
            </p>
        )}

        {routeResponse && !routeInfo && (
            <p role="alert" className="max-w-xl text-center text-xs text-terracotta">
            The route was returned, but its details could not be read.
            </p>
        )}

        {routeInfo && routeResponse && (
            <div className="w-full max-w-xl rounded-xl border border-border bg-surface-subtle px-4 py-3">
                {itineraries.length > 1 && (
                <div className="mb-3 flex flex-wrap justify-center gap-2">
                    {itineraries.map((itinerary, index) => (
                    <button
                        key={index}
                        type="button"
                        onClick={() => {
                        setSelectedItineraryIndex(index)
                        setIsWarningDismissed(false)
                        }}
                        className={`rounded-full border px-3 py-2 text-xs transition-colors ${
                        selectedItineraryIndex === index
                            ? 'border-primary bg-primary font-bold'
                            : 'border-border bg-background hover:text-white hover:bg-muted'
                        }`}
                    >
                        Route {index + 1} ({formatDuration(itinerary.duration)})
                    </button>
                    ))}
                </div>
                )}

                {/* Estimated duration and distance */}
                <p className="text-center text-lg font-bold text-foreground">
                    <span aria-hidden="true" className="mr-1">{selectedModeOption.icon}</span>
                    {getDisplayedDuration(
                        routeInfo.departureTime,
                        routeInfo.arrivalTime,
                    )}
                    {routeResponse.mode !== 'PUBLIC_TRANSPORT' && (
                    routeInfo.distanceMetres !== undefined && (
                        <span className="ml-2 font-normal text-muted">
                        ({formatDistance(routeInfo.distanceMetres)})
                        </span>
                    )
                    )}
                </p>

                {/* Departure and arrival times, including dates across midnight */}
                <p className="mt-1 text-center text-sm text-muted">
                    {formatRouteTimeRange(
                    routeInfo.departureTime,
                    routeInfo.arrivalTime,
                    )}
                </p>

                {minutesLate > 0 && !isWarningDismissed && (
                    <div
                    role="alert"
                    className="mt-3 flex items-start gap-2 rounded-lg border border-yellow-500/40 bg-yellow-500/10 px-3 py-2 text-yellow-800 dark:text-yellow-200"
                    >
                    <span
                        aria-hidden="true"
                        className="mt-0.5 shrink-0 text-base"
                    >
                        ⚠️
                    </span>

                    <p className="flex-1 text-xs">
                        Your expected arrival time exceeds the start time of your next
                        activity by {minutesLate} minute{minutesLate === 1 ? '' : 's'}.
                        Consider allowing more time for travel by leaving earlier or
                        adjusting your next activity.
                    </p>

                    <button
                        type="button"
                        aria-label="Dismiss late arrival warning"
                        onClick={() => setIsWarningDismissed(true)}
                        className={`shrink-0 text-sm opacity-70 hover:opacity-100 ${FOCUS_RING}`}
                    >
                        ✕
                    </button>
                    </div>
                )}
                {routeMapSegments.length > 0 && (
                    <ActivityRouteMap segments={routeMapSegments} />
                )}

                {(directions.length > 0 || routeInstructions.length > 0) && (
                    <div className="mt-3 overflow-hidden rounded-xl border border-border">
                        <button
                        type="button"
                        onClick={() => setIsDirectionsExpanded((current) => !current)}
                        aria-expanded={isDirectionsExpanded}
                        className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium"
                        >
                        <span>Directions</span>

                        <span className="flex items-center gap-2 text-muted">
                            {isDirectionsExpanded ? 'Hide' : 'Show'}
                            <span
                            className={`transition-transform ${
                                isDirectionsExpanded ? 'rotate-180' : ''
                            }`}
                            >
                            ▼
                            </span>
                        </span>
                        </button>

                        {/* Directions for public transport routes */}
                        {isDirectionsExpanded && routeResponse?.mode === 'PUBLIC_TRANSPORT' && (
                            <div className="border-t border-border px-4">
                                {directions.map((direction, index) => (
                                <div
                                    key={index}
                                    className="flex gap-3 border-b border-border py-3 last:border-b-0"
                                >
                                    <span className="mt-0.5">
                                    {direction.type === 'WALK' && '🚶'}
                                    {direction.type === 'SUBWAY' && '🚇'}
                                    {direction.type === 'BUS' && '🚌'}
                                    </span>

                                    <div className="min-w-0 flex-1">
                                    <p className="text-sm font-medium">
                                        {direction.title}
                                    </p>

                                    <p className="mt-0.5 text-xs text-muted">
                                        {direction.description}
                                    </p>
                                    </div>

                                    <span className="shrink-0 text-xs text-muted">
                                    {formatDuration(direction.durationSeconds)}
                                    </span>
                                </div>
                                ))}
                            </div>
                        )}

                        {/* Directions for non-public transport routes */}
                        {isDirectionsExpanded && routeResponse?.mode !== 'PUBLIC_TRANSPORT' && (
                            <div className="border-t border-border px-4">
                                <ol className="my-3 space-y-3">
                                    {routeInstructions.map((instruction, index) => (
                                        <li key={index} className="flex gap-3">
                                            <div className="shrink-0">
                                                {index + 1}.
                                            </div>

                                            <div>
                                                <p className="font-medium">
                                                    {instruction.instruction}
                                                </p>

                                                {(instruction.distanceMetres > 0 ||
                                                    instruction.roadName) && (
                                                    <p className="text-sm text-gray-500">
                                                        {instruction.roadName &&
                                                            `${instruction.roadName} · `}
                                                        {instruction.distanceMetres > 0 &&
                                                            formatDistance(instruction.distanceMetres)}
                                                    </p>
                                                )}
                                            </div>
                                        </li>
                                    ))}
                                </ol>
                            </div>
                        )}
                    </div>
                )}
            </div>
        )}
        </li>
    )
}
