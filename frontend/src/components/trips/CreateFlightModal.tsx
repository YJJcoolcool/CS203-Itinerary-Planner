import { useEffect, useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '@clerk/react'

import { ApiError, lookupFlight } from '../../lib/api'
import { useCreateActivity } from '../../lib/useTrips'
import { useBodyScrollLock } from '../../lib/useBodyScrollLock'
import type { FlightRecord } from '../../lib/api'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const PRIMARY_BUTTON =
  `inline-flex items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-colors hover:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const GHOST_BUTTON =
  `inline-flex items-center justify-center gap-2 rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-muted transition-colors hover:border-border-strong hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50 ${FOCUS_RING}`

const INPUT =
  'input-field w-full'

const UTC_OFFSETS = Array.from({ length: 57 }, (_, index) => {
  const minutes = -12 * 60 + index * 30
  const sign = minutes >= 0 ? '+' : '-'
  const absolute = Math.abs(minutes)
  const hours = String(Math.floor(absolute / 60)).padStart(2, '0')
  const mins = String(absolute % 60).padStart(2, '0')

  return `${sign}${hours}:${mins}`
})

type FlightFormMode = 'SEARCH' | 'REVIEW' | 'MANUAL'

interface FlightForm {
  flightNumber: string
  departureTimeZone: string
  arrivalTimeZone: string
  airline: string

  departureAirportCode: string
  departureAirportName: string
  departureTerminal: string
  departureTime: string
  departureOffset: string

  arrivalAirportCode: string
  arrivalAirportName: string
  arrivalTerminal: string
  arrivalTime: string
  arrivalOffset: string
}

const EMPTY_FORM: FlightForm = {
  flightNumber: '',
  departureTimeZone: '',
  arrivalTimeZone: '',
  airline: '',

  departureAirportCode: '',
  departureAirportName: '',
  departureTerminal: '',
  departureTime: '',
  departureOffset: '+08:00',

  arrivalAirportCode: '',
  arrivalAirportName: '',
  arrivalTerminal: '',
  arrivalTime: '',
  arrivalOffset: '+08:00',
}

function describeError(cause: unknown): string {
  if (cause instanceof Error && cause.message) return cause.message
  return 'Something went wrong. Please try again.'
}

/**
 * Extracts the wall-clock date and time from an ISO timestamp.
 *
 * Example:
 * 2026-12-20T23:55:00+08:00
 * becomes:
 * 2026-12-20T23:55
 *
 * This intentionally preserves the original offset's wall-clock time.
 */
function toLocalDateTime(iso: string | null | undefined): string {
  if (!iso) return ''

  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})/.exec(iso)
  return match?.[1] ?? ''
}

/** Extracts an ISO timestamp's UTC offset. */
function toUtcOffset(iso: string | null | undefined): string {
  if (!iso) return '+08:00'

  // Z means UTC+00:00
  if (/[Zz]$/.test(iso)) return '+00:00'

  // Match offsets such as +08:00 or -05:00.
  // Also supports Java ZonedDateTime strings with [Asia/Singapore].
  const match = /([+-]\d{2}:\d{2})(?:\[[^\]]+\])?$/.exec(iso)

  return match?.[1] ?? '+08:00'
}

/**
 * Builds an ISO-8601 timestamp using the explicitly selected UTC offset.
 */
function toIsoWithOffset(value: string, offset: string): string | null {
  if (!value) return null
  return `${value}:00${offset}`
}

function isValidDateTime(value: string): boolean {
  if (!value) return false

  const date = new Date(value)
  return !Number.isNaN(date.getTime())
}

function createFormFromFlight(flight: FlightRecord): FlightForm {
  return {
    flightNumber: flight.flightNumber ?? '',
    departureTimeZone: flight.departureTimeZone ?? '',
    arrivalTimeZone: flight.arrivalTimeZone ?? '',
    airline: flight.airline ?? '',

    departureAirportCode: flight.departureAirportCode ?? '',
    departureAirportName: flight.departureAirportName ?? '',
    departureTerminal: flight.departureTerminal ?? '',
    departureTime: toLocalDateTime(flight.departureTime),
    departureOffset: toUtcOffset(flight.departureTime),

    arrivalAirportCode: flight.arrivalAirportCode ?? '',
    arrivalAirportName: flight.arrivalAirportName ?? '',
    arrivalTerminal: flight.arrivalTerminal ?? '',
    arrivalTime: toLocalDateTime(flight.arrivalTime),
    arrivalOffset: toUtcOffset(flight.arrivalTime),
  }
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  required = false,
  disabled = false,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  type?: string
  required?: boolean
  disabled?: boolean
}) {
  const id = useId()

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-medium text-muted">
        {label}{required ? ' *' : ''}
      </label>

      <input
        id={id}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        className={INPUT}
      />
    </div>
  )
}

function OffsetField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  const id = useId()

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-medium text-muted">
        {label}
      </label>

      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={INPUT}
      >
        {UTC_OFFSETS.map((offset) => (
          <option key={offset} value={offset}>
            UTC{offset}
          </option>
        ))}
      </select>
    </div>
  )
}

function AirportFields({
  title,
  airportCode,
  airportName,
  terminal,
  dateTime,
  offset,
  onChange,
}: {
  title: string
  airportCode: string
  airportName: string
  terminal: string
  dateTime: string
  offset: string
  onChange: (field: string, value: string) => void
}) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-3 rounded-xl border border-border p-4">
      <legend className="px-1 text-sm font-semibold text-foreground">
        {title}
      </legend>

      <div className="grid grid-cols-2 gap-3">
        <Field
          label="Airport code"
          value={airportCode}
          onChange={(value) => onChange('airportCode', value.toUpperCase())}
          placeholder="SIN"
          required
        />

        <Field
          label="Terminal"
          value={terminal}
          onChange={(value) => onChange('terminal', value)}
          placeholder="e.g. 3"
        />
      </div>

      <Field
        label="Airport name"
        value={airportName}
        onChange={(value) => onChange('airportName', value)}
        placeholder="e.g. Singapore Changi Airport"
      />

      <Field
        label="Local date and time"
        type="datetime-local"
        value={dateTime}
        onChange={(value) => onChange('time', value)}
        required
      />

      <OffsetField
        label="Airport UTC offset"
        value={offset}
        onChange={(value) => onChange('offset', value)}
      />
    </fieldset>
  )
}

export function CreateFlightModal({
  tripId,
  onClose,
  onCreated,
}: {
  tripId: string
  onClose: () => void
  onCreated: () => void
}) {
  const { getToken } = useAuth()
  const { createActivity, isCreating } = useCreateActivity(tripId)

  const [mode, setMode] = useState<FlightFormMode>('SEARCH')
  const [form, setForm] = useState<FlightForm>(EMPTY_FORM)

  const [flightNumberInput, setFlightNumberInput] = useState('')
  const [flightDateInput, setFlightDateInput] = useState('')

  const [candidates, setCandidates] = useState<FlightRecord[]>([])
  const [error, setError] = useState<string | null>(null)

  const [isSearching, setIsSearching] = useState(false)
  const [isSaving, setIsSaving] = useState(false)

  const isBusy = isSearching || isCreating || isSaving

  const titleId = useId()
  const flightNumberRef = useRef<HTMLInputElement>(null)

  useBodyScrollLock()

  useEffect(() => {
    flightNumberRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !isBusy) {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isBusy, onClose])

  function updateForm(field: keyof FlightForm, value: string) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  function updateAirport(
    prefix: 'departure' | 'arrival',
    field: string,
    value: string,
  ) {
    const key = `${prefix}${field.charAt(0).toUpperCase()}${field.slice(1)}` as keyof FlightForm
    updateForm(key, value)
  }

  function handleFlightSelection(flight: FlightRecord) {
    setForm(createFormFromFlight(flight))
    setMode('REVIEW')
    setCandidates([])
    setError(null)
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const flightNumber = flightNumberInput.trim().toUpperCase()

    if (!flightNumber || !flightDateInput) {
      setError('Enter a flight number and departure date.')
      return
    }

    setIsSearching(true)
    setError(null)
    setCandidates([])

    try {
      const token = await getToken()

      if (!token) {
        throw new ApiError(401, 'No active session token is available.')
      }

      const result = await lookupFlight(
        token,
        tripId,
        flightNumber,
        flightDateInput,
      )

      if (result.status === 'NOT_FOUND') {
        setError(result.message || 'No matching flight was found.')
        return
      }

      if (result.status === 'AMBIGUOUS') {
        setCandidates(result.flights)
        return
      }

      if (result.status !== 'SUCCESS' || result.flights.length === 0) {
        setError('No usable flight information was returned.')
        return
      }

      handleFlightSelection(result.flights[0])
    } catch (cause) {
      setError(describeError(cause))
    } finally {
      setIsSearching(false)
    }
  }

  function handleManualEntry() {
    setForm({
      ...EMPTY_FORM,
      flightNumber: flightNumberInput.trim().toUpperCase(),
    })

    setCandidates([])
    setError(null)
    setMode('MANUAL')
  }

  function handleDiscard() {
    setForm(EMPTY_FORM)
    setCandidates([])
    setError(null)
    setMode('SEARCH')
  }

  function validate(): string | null {
    if (!form.flightNumber.trim()) {
      return 'Flight number is required.'
    }

    if (!form.departureAirportCode.trim() || !form.arrivalAirportCode.trim()) {
      return 'Departure and arrival airport codes are required.'
    }

    if (!isValidDateTime(form.departureTime) || !isValidDateTime(form.arrivalTime)) {
      return 'Valid departure and arrival times are required.'
    }

    const departure = new Date(
      `${form.departureTime}:00${form.departureOffset}`,
    )

    const arrival = new Date(
      `${form.arrivalTime}:00${form.arrivalOffset}`,
    )

    if (
      Number.isNaN(departure.getTime()) ||
      Number.isNaN(arrival.getTime())
    ) {
      return 'Please enter valid departure and arrival times.'
    }

    if (arrival.getTime() <= departure.getTime()) {
      return 'Arrival must be after departure.'
    }

    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const validationError = validate()

    if (validationError) {
      setError(validationError)
      return
    }

    setError(null)
    setIsSaving(true)

    try {
      const departureTime = toIsoWithOffset(
        form.departureTime,
        form.departureOffset,
      )

      const arrivalTime = toIsoWithOffset(
        form.arrivalTime,
        form.arrivalOffset,
      )

      await createActivity({
        name: `Flight ${form.flightNumber.trim().toUpperCase()}`,
        type: 'FLIGHT',

        flightNumber: form.flightNumber.trim().toUpperCase(),
        departureTimeZone: form.departureTimeZone || form.departureOffset,
        arrivalTimeZone: form.arrivalTimeZone || form.arrivalOffset,
        airline: form.airline.trim() || undefined,

        departureAirportCode: form.departureAirportCode.trim().toUpperCase(),
        departureAirportName: form.departureAirportName.trim() || undefined,
        departureTerminal: form.departureTerminal.trim() || undefined,

        arrivalAirportCode: form.arrivalAirportCode.trim().toUpperCase(),
        arrivalAirportName: form.arrivalAirportName.trim() || undefined,
        arrivalTerminal: form.arrivalTerminal.trim() || undefined,

        startDateTime: departureTime,
        endDateTime: arrivalTime,

        locations: [],
      })

      onCreated()
      onClose()
    } catch (cause) {
      setError(describeError(cause))
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isBusy) {
          onClose()
        }
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="animate-rise my-auto w-full max-w-2xl rounded-2xl border border-border bg-surface-raised p-6 shadow-2xl"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2
              id={titleId}
              className="font-display text-2xl font-medium tracking-tight text-foreground"
            >
              Add flight
            </h2>

            <p className="mt-2 text-sm text-muted">
              Add a flight to your trip using flight information lookup or manual entry.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isBusy}
            aria-label="Close"
            className={`rounded-lg p-2 text-muted transition-colors hover:bg-surface-subtle hover:text-foreground ${FOCUS_RING}`}
          >
            ✕
          </button>
        </div>

        {/* Mode selector */}
        <div className="mt-5 grid grid-cols-2 gap-2 rounded-xl bg-surface-subtle p-1">
          <button
            type="button"
            onClick={() => {
              setMode('SEARCH')
              setError(null)
            }}
            disabled={isBusy}
            aria-pressed={mode !== 'MANUAL'}
            className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
              mode !== 'MANUAL'
                ? 'bg-surface-raised text-foreground shadow-sm'
                : 'text-muted hover:text-foreground'
            }`}
          >
            Flight Lookup
          </button>

          <button
            type="button"
            onClick={handleManualEntry}
            disabled={isBusy}
            aria-pressed={mode === 'MANUAL'}
            className={`rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
              mode === 'MANUAL'
                ? 'bg-surface-raised text-foreground shadow-sm'
                : 'text-muted hover:text-foreground'
            }`}
          >
            Manual entry
          </button>
        </div>

        {/* AI search */}
        {mode === 'SEARCH' && (
          <div className="mt-5">
            <form onSubmit={handleLookup} className="flex flex-col gap-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <label
                    htmlFor={`${titleId}-flight-number`}
                    className="text-xs font-medium text-muted"
                  >
                    Flight number
                  </label>

                  <input
                    ref={flightNumberRef}
                    id={`${titleId}-flight-number`}
                    value={flightNumberInput}
                    onChange={(event) =>
                      setFlightNumberInput(event.target.value.toUpperCase())
                    }
                    placeholder="e.g. SQ321"
                    className={INPUT}
                    autoComplete="off"
                    required
                  />
                </div>

                <Field
                  label="Departure date"
                  type="date"
                  value={flightDateInput}
                  onChange={setFlightDateInput}
                  required
                />
              </div>

              <p className="text-xs text-muted">
                Enter your flight number and departure date. We'll look up the available flight information for you.
              </p>

              <button
                type="submit"
                disabled={isBusy || !flightNumberInput.trim() || !flightDateInput}
                className={PRIMARY_BUTTON}
              >
                {isSearching ? 'Searching…' : 'Search flight'}
              </button>
            </form>

            {candidates.length > 0 && (
              <div className="mt-5 flex flex-col gap-3">
                <h3 className="text-sm font-semibold text-foreground">
                  Multiple flights found
                </h3>

                <p className="text-xs text-muted">
                  Select the flight you want to add.
                </p>

                {candidates.map((flight, index) => (
                  <button
                    key={`${flight.flightNumber}-${flight.departureTime}-${index}`}
                    type="button"
                    onClick={() => handleFlightSelection(flight)}
                    className={`flex flex-col gap-2 rounded-xl border border-border p-4 text-left transition-colors hover:border-accent hover:bg-surface-subtle ${FOCUS_RING}`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-semibold text-foreground">
                        {flight.airline || 'Unknown airline'} · {flight.flightNumber}
                      </span>

                      <span className="text-xs text-accent">
                        Select flight →
                      </span>
                    </div>

                    <div className="flex items-center gap-3 text-sm text-muted">
                      <span className="font-mono">
                        {flight.departureAirportCode}
                      </span>
                      <span aria-hidden="true">→</span>
                      <span className="font-mono">
                        {flight.arrivalAirportCode}
                      </span>
                    </div>

                    <p className="text-xs text-muted">
                      {flight.departureTime || 'Departure time unavailable'}
                    </p>
                  </button>
                ))}
              </div>
            )}

            <button
              type="button"
              onClick={handleManualEntry}
              className={`mt-4 w-full ${GHOST_BUTTON}`}
              disabled={isBusy}
            >
              Enter flight manually instead
            </button>
          </div>
        )}

        {/* Review / manual entry */}
        {(mode === 'REVIEW' || mode === 'MANUAL') && (
          <form
            onSubmit={handleSubmit}
            className="mt-5 flex max-h-[65vh] flex-col gap-4 overflow-y-auto pr-1"
          >
            {mode === 'REVIEW' && (
              <div className="rounded-xl border border-accent/30 bg-accent/5 px-4 py-3">
                <p className="text-sm font-medium text-foreground">
                  Flight information retrieved
                </p>
                <p className="mt-1 text-xs text-muted">
                  Review the information below. You can edit any field before saving.
                </p>
              </div>
            )}

            <div className="grid gap-3 sm:grid-cols-2">
              <Field
                label="Flight number"
                value={form.flightNumber}
                onChange={(value) => updateForm('flightNumber', value.toUpperCase())}
                placeholder="SQ321"
                required
              />

              <Field
                label="Airline"
                value={form.airline}
                onChange={(value) => updateForm('airline', value)}
                placeholder="e.g. Singapore Airlines"
              />
            </div>

            <AirportFields
              title="Departure"
              airportCode={form.departureAirportCode}
              airportName={form.departureAirportName}
              terminal={form.departureTerminal}
              dateTime={form.departureTime}
              offset={form.departureOffset}
              onChange={(field, value) =>
                updateAirport('departure', field, value)
              }
            />

            <AirportFields
              title="Arrival"
              airportCode={form.arrivalAirportCode}
              airportName={form.arrivalAirportName}
              terminal={form.arrivalTerminal}
              dateTime={form.arrivalTime}
              offset={form.arrivalOffset}
              onChange={(field, value) =>
                updateAirport('arrival', field, value)
              }
            />

            <p className="text-xs text-muted">
              Times are entered in the respective airport's local time. Check the UTC offset for each airport before saving.
            </p>

            <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-between">
              <button
                type="button"
                onClick={handleDiscard}
                disabled={isBusy}
                className={GHOST_BUTTON}
              >
                Discard
              </button>

              <div className="flex flex-col-reverse gap-2 sm:flex-row">
                <button
                  type="button"
                  onClick={() => {
                    setMode('SEARCH')
                    setError(null)
                  }}
                  disabled={isBusy}
                  className={GHOST_BUTTON}
                >
                  Back
                </button>

                <button
                  type="submit"
                  disabled={isBusy}
                  className={PRIMARY_BUTTON}
                  aria-busy={isBusy}
                >
                  {isSaving || isCreating ? 'Saving…' : 'Accept and save flight'}
                </button>
              </div>
            </div>
          </form>
        )}

        {error && (
          <p
            role="alert"
            className="mt-4 rounded-xl border border-terracotta/40 bg-terracotta/10 px-3 py-2 text-sm text-terracotta"
          >
            {error}
          </p>
        )}
      </div>
    </div>
  )
}

export default CreateFlightModal