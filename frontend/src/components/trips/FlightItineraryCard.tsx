import type { Activity } from '../../types/trip'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PILL =
  'inline-flex items-center rounded-full border border-border bg-surface-subtle px-2.5 py-0.5 font-mono text-[11px] text-muted'

interface FlightItineraryCardProps {
  activity: Activity
  onEdit?: () => void
  onDelete?: () => void
}

function formatTime(
  value: string | null,
  timeZone: string | null | undefined,
): string {
  if (!value || !timeZone) return 'Time TBD'

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return 'Time TBD'

  try {
    return new Intl.DateTimeFormat(undefined, {
      hour: 'numeric',
      minute: '2-digit',
      timeZone,
    }).format(date)
  } catch {
    return 'Time TBD'
  }
}

function formatDate(
  value: string | null,
  timeZone: string | null | undefined,
): string {
  if (!value || !timeZone) return ''

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return ''

  try {
    return new Intl.DateTimeFormat(undefined, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone,
    }).format(date)
  } catch {
    return ''
  }
}

/** Gets the UTC offset applicable at this instant in the given timezone. */
function formatUtcOffset(
  value: string | null,
  timeZone: string | null | undefined,
): string | null {
  if (!value || !timeZone) return null

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) return null

  try {
    const parts = new Intl.DateTimeFormat('en', {
      timeZone,
      timeZoneName: 'shortOffset',
    }).formatToParts(date)

    return parts.find((part) => part.type === 'timeZoneName')?.value ?? null
  } catch {
    return null
  }
}

/** Calculates actual elapsed flight time, accounting for UTC offsets. */
function formatDuration(
  start: string | null,
  end: string | null,
): string | null {
  if (!start || !end) return null

  const departure = new Date(start)
  const arrival = new Date(end)

  if (
    Number.isNaN(departure.getTime()) ||
    Number.isNaN(arrival.getTime()) ||
    arrival.getTime() <= departure.getTime()
  ) {
    return null
  }

  const totalMinutes = Math.round(
    (arrival.getTime() - departure.getTime()) / 60000,
  )

  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60

  if (hours === 0) return `${minutes}m`
  if (minutes === 0) return `${hours}h`

  return `${hours}h ${minutes}m`
}

function Airport({
  code,
  name,
  terminal,
  dateTime,
  timeZone,
  alignment = 'left',
}: {
  code: string | null | undefined
  name: string | null | undefined
  terminal: string | null | undefined
  dateTime: string | null
  timeZone: string | null | undefined
  alignment?: 'left' | 'right'
}) {
  const time = formatTime(dateTime, timeZone)
  const date = formatDate(dateTime, timeZone)
  const offset = formatUtcOffset(dateTime, timeZone)

  return (
    <div className={`min-w-0 ${alignment === 'right' ? 'text-right' : ''}`}>
      <p className="font-mono text-xs font-medium tracking-wide text-accent">
        {alignment === 'right' ? 'ARRIVAL' : 'DEPARTURE'}
      </p>

      <h3 className="mt-2 font-display text-3xl font-medium tracking-tight text-foreground sm:text-4xl">
        {code || '---'}
      </h3>

      <p className="mt-1 line-clamp-2 text-sm text-muted">
        {name || 'Airport information unavailable'}
      </p>

      {terminal && (
        <p className="mt-2 text-xs text-muted">
          Terminal {terminal}
        </p>
      )}

      <p className="mt-4 font-display text-xl font-medium text-foreground">
        {time}
      </p>

      {date && (
        <p className="mt-1 text-xs text-muted">
          {date}
          {offset && (
            <span className="ml-1.5 font-mono text-[10px] text-faint">
              ({offset})
            </span>
          )}
        </p>
      )}
    </div>
  )
}

export function FlightItineraryCard({
  activity,
  onEdit,
  onDelete,
}: FlightItineraryCardProps) {
  const duration = formatDuration(
    activity.startDateTime,
    activity.endDateTime,
  )

  const flightNumber = activity.flightNumber || activity.name

  return (
    <li className="animate-rise overflow-hidden rounded-2xl border border-border bg-surface-raised">
      {/* Flight header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border bg-surface-subtle px-5 py-4">
        <div className="flex min-w-0 items-center gap-3">
          <div
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/10 text-lg"
          >
            ✈
          </div>

          <div className="min-w-0">
            <h4 className="truncate text-sm font-semibold text-foreground">
              {activity.airline || 'Flight'}
            </h4>

            <p className="mt-0.5 font-mono text-xs text-muted">
              {flightNumber}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {activity.status && (
            <span className={PILL}>
              {activity.status}
            </span>
          )}

          {onEdit && (
            <button
              type="button"
              onClick={onEdit}
              aria-label={`Edit flight ${flightNumber}`}
              className={`rounded-lg border border-border px-3 py-1 text-xs text-muted transition-colors hover:border-border-strong hover:text-foreground ${FOCUS_RING}`}
            >
              Edit
            </button>
          )}

          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              aria-label={`Delete flight ${flightNumber}`}
              className={`rounded-lg border border-terracotta/40 px-3 py-1 text-xs text-terracotta transition-colors hover:bg-terracotta/10 ${FOCUS_RING}`}
            >
              Delete
            </button>
          )}
        </div>
      </div>

      {/* Flight route */}
      <div className="p-5">
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-6">
          <Airport
            code={activity.departureAirportCode}
            name={activity.departureAirportName}
            terminal={activity.departureTerminal}
            dateTime={activity.startDateTime}
            timeZone={activity.departureTimeZone}
            alignment="left"
          />

          {/* Flight duration indicator */}
          <div className="flex min-w-0 flex-col items-center gap-2 px-1">
            <span className="whitespace-nowrap text-xs font-medium text-muted">
              {duration || '—'}
            </span>

            <div className="flex w-full items-center">
              <div className="h-px flex-1 bg-border" />

              <span
                aria-hidden="true"
                className="mx-1 text-lg text-accent"
              >
                ✈
              </span>

              <div className="h-px flex-1 bg-border" />
            </div>

            <span className="whitespace-nowrap text-[10px] text-faint">
              Flight
            </span>
          </div>

          <Airport
            code={activity.arrivalAirportCode}
            name={activity.arrivalAirportName}
            terminal={activity.arrivalTerminal}
            dateTime={activity.endDateTime}
            timeZone={activity.arrivalTimeZone}
            alignment="right"
          />
        </div>
      </div>
    </li>
  )
}

export default FlightItineraryCard