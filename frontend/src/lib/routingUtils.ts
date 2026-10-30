
import polyline from '@mapbox/polyline'

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

export interface RouteMapSegment {
  mode: string
  coordinates: [number, number][]
}

export function getRouteMapSegments(
  response: RouteResponse,
): RouteMapSegment[] {
  const routeData = asObject(response.routeData)

  if (!routeData) return []

  // Driving, walking and cycling
  if (response.mode !== 'PUBLIC_TRANSPORT') {
    const geometry = routeData.route_geometry

    if (typeof geometry !== 'string' || !geometry) {
      return []
    }

    return [{
      mode: response.mode,
      coordinates: polyline.decode(geometry) as [number, number][],
    }]
  }

  // Public transport: use the same first itinerary as the duration display
  const plan = asObject(routeData.plan)
  const itineraries = plan?.itineraries

  if (!Array.isArray(itineraries) || itineraries.length === 0) {
    return []
  }

  const itinerary = asObject(itineraries[0])
  const legs = itinerary?.legs

  if (!Array.isArray(legs)) return []

  return legs.flatMap((leg) => {
    const legObject = asObject(leg)
    const geometry = asObject(legObject?.legGeometry)
    const points = geometry?.points

    if (typeof points !== 'string' || !points) {
      return []
    }

    return [{
      mode: typeof legObject?.mode === 'string'
        ? legObject.mode
        : 'WALK',
      coordinates: polyline.decode(points) as [number, number][],
    }]
  })
}

export interface PublicTransportItinerary {
  duration: number
  startTime: number
  endTime: number
  walkDistance: number
  legs: PublicTransportLeg[]
}

export interface PublicTransportLeg {
  mode: string
  route?: string
  routeShortName?: string
  routeLongName?: string
  duration: number
  from: {
    name: string
  }
  to: {
    name: string
  }
  legGeometry?: {
    points?: string
  }
}

export interface RouteDirection {
  type: 'WALK' | 'SUBWAY' | 'BUS'
  title: string
  description: string
  durationSeconds: number
}

export function getPublicTransportItineraries(
  response: RouteResponse,
): PublicTransportItinerary[] {
  const routeData = asObject(response.routeData)
  const plan = asObject(routeData?.plan)
  const itineraries = plan?.itineraries

  if (!Array.isArray(itineraries)) return []

  return itineraries.filter(
    (item): item is PublicTransportItinerary =>
      typeof item === 'object' &&
      item !== null &&
      Array.isArray((item as PublicTransportItinerary).legs),
  )
}

export function getItineraryMapSegments(
  itinerary: PublicTransportItinerary,
): RouteMapSegment[] {
  return itinerary.legs.flatMap((leg) => {
    const points = leg.legGeometry?.points

    if (!points) return []

    return [{
      mode: leg.mode,
      coordinates: polyline.decode(points) as [number, number][],
    }]
  })
}

export function getItineraryDirections(
    itinerary: PublicTransportItinerary,
    destinationName: string,
    ): RouteDirection[] {
    return itinerary.legs.flatMap(
        (leg, index): RouteDirection[] => {
        const durationMinutes = Math.round(leg.duration / 60)

        if (leg.mode === 'WALK') {
            const isLastLeg = index === itinerary.legs.length - 1

            return [{
            type: 'WALK',
            title: `Walk ${durationMinutes} min`,
            description: `To ${
                isLastLeg ? destinationName : leg.to.name
            }`,
            durationSeconds: leg.duration,
            }]
        }

        if (leg.mode === 'SUBWAY') {
            const lineName = leg.routeLongName
            ? `${leg.routeLongName} (${leg.routeShortName ?? ''})`
            : `Train ${leg.routeShortName ?? ''}`

            return [{
            type: 'SUBWAY',
            title: lineName,
            description: `${leg.from.name} → ${leg.to.name}`,
            durationSeconds: leg.duration,
            }]
        }

        if (leg.mode === 'BUS') {
            return [{
            type: 'BUS',
            title: `Bus ${leg.routeShortName ?? leg.route ?? ''}`,
            description: `${leg.from.name} → ${leg.to.name}`,
            durationSeconds: leg.duration,
            }]
        }

        return []
        },
    )
}

export interface RouteInstruction {
    turn: string
    distanceMetres: number
    durationSeconds: number
    roadName: string
    instruction: string
}

export function getRouteInstructions(
    response: RouteResponse,
): RouteInstruction[] {
    if (response.mode === 'PUBLIC_TRANSPORT') return []

    const routeData = asObject(response.routeData)
    const rawInstructions = routeData?.route_instructions

    if (!Array.isArray(rawInstructions)) return []

    return rawInstructions.flatMap((item): RouteInstruction[] => {
        if (!Array.isArray(item)) return []

        const distance = asNumber(item[2]) ?? 0
        const duration = asNumber(item[4]) ?? 0

        return [{
            turn: String(item[0] ?? ''),
            roadName: String(item[1] ?? ''),
            distanceMetres: distance,
            durationSeconds: duration,
            instruction: String(item[9] ?? ''),
        }]
    }).filter(item => item.instruction.length > 0)
}