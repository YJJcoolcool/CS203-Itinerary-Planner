import type { MemberRole, Trip, User } from '../../types/trip'

/** Matches the ISO `YYYY-MM-DD` shape the backend uses for trip dates. */
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/
const MS_PER_DAY = 86_400_000

/**
 * Parses an ISO date as UTC midnight so day math is not shifted by the
 * browser's timezone. Returns null for missing/malformed input.
 */
export function parseIsoDate(value: string | null | undefined): Date | null {
  if (!value || !ISO_DATE.test(value)) return null
  const date = new Date(`${value}T00:00:00Z`)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * Number of calendar days from `startDate` to `endDate`, **inclusive** of both
 * ends. There is no Day entity on the backend, so days are derived from the
 * trip's date range. Returns null when either date is missing/malformed or the
 * range is inverted.
 */
export function daysInclusive(startDate: string, endDate: string): number | null {
  const start = parseIsoDate(startDate)
  const end = parseIsoDate(endDate)
  if (!start || !end) return null

  const diff = end.getTime() - start.getTime()
  if (diff < 0) return null

  return Math.round(diff / MS_PER_DAY) + 1
}

function format(date: Date, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', ...options }).format(date)
}

/** Human-readable date range, e.g. "Oct 14 – 21, 2026". Falls back gracefully. */
export function formatDateRange(startDate: string, endDate: string): string {
  const start = parseIsoDate(startDate)
  const end = parseIsoDate(endDate)
  if (!start || !end) return 'Dates unavailable'

  if (start.getTime() === end.getTime()) {
    return format(start, { month: 'short', day: 'numeric', year: 'numeric' })
  }

  if (start.getUTCFullYear() !== end.getUTCFullYear()) {
    return `${format(start, { month: 'short', day: 'numeric', year: 'numeric' })} – ${format(end, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })}`
  }

  const sameMonth = start.getUTCMonth() === end.getUTCMonth()
  const startLabel = format(start, { month: 'short', day: 'numeric' })
  const endLabel = sameMonth
    ? format(end, { day: 'numeric' })
    : format(end, { month: 'short', day: 'numeric' })

  return `${startLabel} – ${endLabel}, ${format(end, { year: 'numeric' })}`
}

/** Up to two initials derived from a display name; "?" when unavailable. */
export function initials(displayName: string | null | undefined): string {
  const parts = (displayName ?? '').trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase()
}

/**
 * Stable avatar tint derived from a user id. Only background tints are used;
 * initials stay in the theme foreground so contrast holds in both themes.
 */
const AVATAR_TONES = [
  'bg-amber/25',
  'bg-terracotta/25',
  'bg-sage/25',
  'bg-purple/25',
  'bg-blue/25',
] as const

export function avatarTone(id: string): string {
  let hash = 0

  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) | 0
  }

  const index = Math.abs(hash) % AVATAR_TONES.length
  return AVATAR_TONES[index]
}

/** Owner first, then members, de-duplicated by id. */
export function uniqueMembers(trip: Trip): User[] {
  const byId = new Map<string, User>()
  byId.set(trip.owner.id, trip.owner)
  for (const member of trip.users ?? []) {
    byId.set(member.id, member)
  }
  return [...byId.values()]
}

/** Activity count, tolerant of a missing/non-array `itinerary`. */
export function activityCount(trip: Trip): number {
  return Array.isArray(trip.itinerary) ? trip.itinerary.length : 0
}

/**
 * The user's role on the trip, or null if they aren't on it. Mirrors the
 * backend's Trip.findRole(): the owner is OWNER, and a member with no entry in
 * `roles` is a VIEWER.
 */
export function roleOf(trip: Trip, userId: string | null | undefined): MemberRole | null {
  if (!userId) return null
  if (trip.owner.id === userId) return 'OWNER'
  if (!trip.users.some((member) => member.id === userId)) return null
  return trip.roles?.[userId] ?? 'VIEWER'
}