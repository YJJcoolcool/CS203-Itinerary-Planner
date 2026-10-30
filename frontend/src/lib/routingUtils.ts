
import type { RouteMode, RouteResponse } from '../types/trip'

type JsonObject = Record<string, unknown>

export interface RouteDisplayInfo {
  durationSeconds: number
  distanceMetres?: number
  walkingDistanceMetres?: number
  departureTime: Date
  arrivalTime: Date
}

function asObject(value: unknown): JsonObject | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return null
  }

  return value as JsonObject
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

/** Formats seconds as whole hours and remaining minutes, rounded to the nearest minute. */
export function formatDuration(seconds: number): string {
  const totalMinutes = Math.round(seconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (hours === 0) return `${minutes}m`
  return `${hours}h ${minutes}m`
}

/** Formats a distance in metres using metres below 1 km and kilometres otherwise. */
export function formatDistance(metres: number): string {
  if (metres < 1000) return `${Math.round(metres)}m`
  return `${(metres / 1000).toFixed(1)}km`
}

/** Formats a date as a local time, e.g. "11:45 PM". */
export function formatClockTime(date: Date): string {
  return date.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })
}

/**
 * Formats a journey's departure and arrival times.
 * Includes the date on both times when the journey crosses midnight.
 */
export function formatRouteTimeRange(
  departureTime: Date,
  arrivalTime: Date,
): string {
  const departureDate = departureTime.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
  })

  const arrivalDate = arrivalTime.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
  })

  const crossesDate =
    departureTime.getFullYear() !== arrivalTime.getFullYear() ||
    departureTime.getMonth() !== arrivalTime.getMonth() ||
    departureTime.getDate() !== arrivalTime.getDate()

  if (crossesDate) {
    return `${formatClockTime(departureTime)} (${departureDate}) – ${formatClockTime(arrivalTime)} (${arrivalDate})`
  }

  return `${formatClockTime(departureTime)} – ${formatClockTime(arrivalTime)}`
}

/**
 * Extracts the display information from a route response.
 *
 * Public transport uses the first returned itinerary. Its walkDistance is
 * walking distance only, not the total journey distance.
 *
 * Other modes use route_summary.total_time and total_distance.
 */
export function getRouteDisplayInfo(
  response: RouteResponse,
): RouteDisplayInfo | null {
  const routeData = asObject(response.routeData)
  if (!routeData) return null

  if (response.mode === 'PUBLIC_TRANSPORT') {
    const plan = asObject(routeData.plan)
    const itineraries = plan?.itineraries

    if (!Array.isArray(itineraries) || itineraries.length === 0) return null

    const itinerary = asObject(itineraries[0])
    if (!itinerary) return null

    const durationSeconds = asNumber(itinerary.duration)
    const startTime = asNumber(itinerary.startTime)
    const endTime = asNumber(itinerary.endTime)
    const walkingDistanceMetres = asNumber(itinerary.walkDistance)

    if (durationSeconds === null || startTime === null || endTime === null) {
      return null
    }

    return {
      durationSeconds,
      walkingDistanceMetres: walkingDistanceMetres ?? undefined,
      departureTime: new Date(startTime),
      arrivalTime: new Date(endTime),
    }
  }

  const summary = asObject(routeData.route_summary)
  if (!summary) return null

  const durationSeconds = asNumber(summary.total_time)
  const distanceMetres = asNumber(summary.total_distance)

  if (durationSeconds === null || distanceMetres === null) return null

  const departureTime = response.departureDateTime
    ? new Date(response.departureDateTime)
    : null

  if (!departureTime || Number.isNaN(departureTime.getTime())) return null

  return {
    durationSeconds,
    distanceMetres,
    departureTime,
    arrivalTime: new Date(departureTime.getTime() + durationSeconds * 1000),
  }
}

/** Calculates the displayed duration from the actual departure and arrival times. */
export function getDisplayedDuration(
  departureTime: Date,
  arrivalTime: Date,
): string {
  const elapsedSeconds =
    (arrivalTime.getTime() - departureTime.getTime()) / 1000

  return formatDuration(Math.max(0, elapsedSeconds))
}

/** Returns whole minutes late, ignoring differences of less than one minute. */
export function getMinutesLate(
  arrivalTime: Date,
  nextActivityStart: string | null,
): number {
  if (!nextActivityStart) return 0

  const nextStart = new Date(nextActivityStart)
  if (Number.isNaN(nextStart.getTime())) return 0

  const difference = arrivalTime.getTime() - nextStart.getTime()

  if (difference < 60_000) return 0

  return Math.floor(difference / 60_000)
}

/** Human-readable label for a transport mode. */
export function getRouteModeLabel(mode: RouteMode): string {
  switch (mode) {
    case 'PUBLIC_TRANSPORT':
      return 'Public transport'
    case 'DRIVING':
      return 'Driving'
    case 'WALKING':
      return 'Walking'
    case 'CYCLING':
      return 'Cycling'
  }
}

/** Short label used on the main directions button. */
export function getRouteModeButtonLabel(mode: RouteMode): string {
  switch (mode) {
    case 'PUBLIC_TRANSPORT':
      return 'Public Transport'
    case 'DRIVING':
      return 'Car'
    case 'WALKING':
      return 'Walking'
    case 'CYCLING':
      return 'Cycling'
  }
}
