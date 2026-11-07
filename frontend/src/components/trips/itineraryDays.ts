import type { Activity } from '../../types/trip'

/** One day of the itinerary (CGT-171). */
export interface ItineraryDay {
  /** `YYYY-MM-DD` in the viewer's time zone, or `unscheduled`. */
  key: string
  /** e.g. "Day 1 · Mon, 3 Nov", or "Unscheduled". */
  label: string
  activities: Activity[]
}

export const UNSCHEDULED_KEY = 'unscheduled'

const MS_PER_DAY = 86_400_000

const pad = (value: number) => String(value).padStart(2, '0')

function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** A `YYYY-MM-DD` trip date as local midnight, or null when missing or malformed. */
function parseLocalDate(value: string | null | undefined): Date | null {
  const match = value ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(value) : null
  if (!match) return null
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function startTime(activity: Activity): number {
  return activity.startDateTime ? new Date(activity.startDateTime).getTime() : Number.NaN
}

/**
 * "Day 3 · Wed, 5 Nov". The day number counts from the trip's start date;
 * it's left out for dates before the trip starts or when the trip has no start date.
 */
function dayLabel(date: Date, tripStart: Date | null): string {
  const dateLabel = date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
  if (!tripStart) return dateLabel

  const midnight = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  // Rounding absorbs the hour a daylight-saving change adds or removes.
  const dayNumber = Math.round((midnight.getTime() - tripStart.getTime()) / MS_PER_DAY) + 1
  return dayNumber >= 1 ? `Day ${dayNumber} · ${dateLabel}` : dateLabel
}

/**
 * Splits activities into days by their start time, earliest day first, with
 * activities in start order inside each day. Activities with no start time
 * go into a final "Unscheduled" group.
 */
export function groupActivitiesByDay(
  activities: Activity[],
  tripStartDate?: string | null,
): ItineraryDay[] {
  const tripStart = parseLocalDate(tripStartDate)
  const days = new Map<string, ItineraryDay>()
  const unscheduled: Activity[] = []

  for (const activity of activities) {
    const start = activity.startDateTime ? new Date(activity.startDateTime) : null
    if (!start || Number.isNaN(start.getTime())) {
      unscheduled.push(activity)
      continue
    }

    const key = localDateKey(start)
    let day = days.get(key)
    if (!day) {
      day = { key, label: dayLabel(start, tripStart), activities: [] }
      days.set(key, day)
    }
    day.activities.push(activity)
  }

  const scheduled = [...days.values()].sort((a, b) => a.key.localeCompare(b.key))
  for (const day of scheduled) {
    day.activities.sort((a, b) => startTime(a) - startTime(b))
  }

  return unscheduled.length > 0
    ? [...scheduled, { key: UNSCHEDULED_KEY, label: 'Unscheduled', activities: unscheduled }]
    : scheduled
}
