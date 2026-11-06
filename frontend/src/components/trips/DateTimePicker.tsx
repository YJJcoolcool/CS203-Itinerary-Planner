import { useEffect, useId, useMemo, useRef, useState } from 'react'

const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-surface'

const DEFAULT_TIME = '09:00'

/** One-tap times that cover the common cases without a second control. */
const TIME_PRESETS = [
  { label: 'Morning', time: '09:00' },
  { label: 'Noon', time: '12:00' },
  { label: 'Afternoon', time: '15:00' },
  { label: 'Evening', time: '19:00' },
] as const

const VALUE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?$/

const WEEKDAY_LABELS = (() => {
  const formatter = new Intl.DateTimeFormat(undefined, { weekday: 'short' })
  const monday = new Date(2024, 0, 1) // a Monday
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(monday)
    day.setDate(monday.getDate() + index)
    return formatter.format(day)
  })
})()

const MONTH_FORMATTER = new Intl.DateTimeFormat(undefined, {
  month: 'long',
  year: 'numeric',
})

const DAY_FORMATTER = new Intl.DateTimeFormat(undefined, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

const SHORT_DATE_FORMATTER = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
})

const SHORT_DATE_WITH_YEAR_FORMATTER = new Intl.DateTimeFormat(undefined, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit',
})

interface DateTimeParts {
  date: string
  time: string
}

/** Splits a `YYYY-MM-DDTHH:mm` value, rejecting impossible dates. */
function parseParts(value: string): DateTimeParts | null {
  const match = VALUE_PATTERN.exec(value)
  if (!match) return null

  const [, year, month, day, hours = '00', minutes = '00'] = match
  const date = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hours),
    Number(minutes),
  )

  if (
    date.getFullYear() !== Number(year) ||
    date.getMonth() !== Number(month) - 1 ||
    date.getDate() !== Number(day) ||
    Number(hours) > 23 ||
    Number(minutes) > 59
  ) {
    return null
  }

  return { date: `${year}-${month}-${day}`, time: `${hours}:${minutes}` }
}

function parseDateOnly(datePart: string): Date {
  const [year, month, day] = datePart.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function parseLocal(value: string): Date | null {
  const parts = parseParts(value)
  if (!parts) return null
  return parseDateOnly(parts.date)
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function toDatePart(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

function todayPart(): string {
  return toDatePart(new Date())
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function addMonths(month: Date, delta: number): Date {
  return new Date(month.getFullYear(), month.getMonth() + delta, 1)
}

function buildMonthGrid(viewMonth: Date): Date[] {
  const firstOfMonth = startOfMonth(viewMonth)
  // Monday-first grid: Sunday (0) becomes offset 6.
  const offset = (firstOfMonth.getDay() + 6) % 7
  const gridStart = new Date(firstOfMonth)
  gridStart.setDate(firstOfMonth.getDate() - offset)

  return Array.from({ length: 42 }, (_, index) => {
    const day = new Date(gridStart)
    day.setDate(gridStart.getDate() + index)
    return day
  })
}

function normalizeTime(value: string | undefined): string {
  if (value && /^\d{2}:\d{2}$/.test(value)) return value
  return DEFAULT_TIME
}

function formatTrigger(value: string): string {
  const date = parseLocal(value)
  const parts = parseParts(value)
  if (!date || !parts) return ''

  const [hours, minutes] = parts.time.split(':').map(Number)
  const withTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), hours, minutes)
  const sameYear = date.getFullYear() === new Date().getFullYear()
  const dateLabel = (sameYear ? SHORT_DATE_FORMATTER : SHORT_DATE_WITH_YEAR_FORMATTER).format(date)
  const timeLabel = TIME_FORMATTER.format(withTime)
  return `${dateLabel} · ${timeLabel}`
}

function formatTime(time: string): string {
  const [hours, minutes] = time.split(':').map(Number)
  const date = new Date(2000, 0, 1, hours, minutes)
  return TIME_FORMATTER.format(date)
}

function CalendarIcon({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      className={className}
    >
      <rect x="3" y="4.5" width="14" height="12" rx="2.5" />
      <path d="M3 8.25h14M6.5 3.5v3M13.5 3.5v3" />
    </svg>
  )
}

function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4 w-4"
    >
      <path d={direction === 'left' ? 'm11.5 5-5 5 5 5' : 'm8.5 5 5 5-5 5'} />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      className="h-3.5 w-3.5"
    >
      <path d="m5 5 10 10M15 5 5 15" />
    </svg>
  )
}

export interface DateTimePickerProps {
  /** `YYYY-MM-DDTHH:mm`, or `''` for no selection. */
  value: string
  onChange: (value: string) => void
  label: string
  placeholder: string
  id?: string
  /** Earliest allowed value, in the same `YYYY-MM-DDTHH:mm` format. */
  min?: string
  /** Time applied when a day is picked before a time has been chosen. */
  defaultTime?: string
  disabled?: boolean
  invalid?: boolean
  describedBy?: string
}

/**
 * Combined day + time popover. The outer trigger looks like a text field, and
 * the panel keeps the calendar and time presets together so a single day click
 * is enough to produce a complete value.
 */
export function DateTimePicker({
  value,
  onChange,
  label,
  placeholder,
  id,
  min,
  defaultTime = DEFAULT_TIME,
  disabled = false,
  invalid = false,
  describedBy,
}: DateTimePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [viewMonth, setViewMonth] = useState<Date>(() => currentReferenceMonth())
  const triggerRef = useRef<HTMLButtonElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const panelId = useId()

  const valueParts = value ? parseParts(value) : null
  const minParts = min ? parseParts(min) : null
  const fallbackTime = normalizeTime(defaultTime)
  const today = todayPart()

  /** Day used when a time is picked before a day has been chosen. */
  const quickDay =
    minParts && minParts.date > today ? minParts.date : today

  function currentReferenceMonth(): Date {
    const selectedDate = value ? parseParts(value)?.date : undefined
    const minDate = min ? parseParts(min)?.date : undefined
    const fallback = minDate && minDate > todayPart() ? minDate : todayPart()
    return startOfMonth(parseDateOnly(selectedDate ?? fallback))
  }

  useEffect(() => {
    if (!isOpen) return

    // Escape belongs to the popover. Capture it before the activity modal's
    // document-level listener so only this panel closes.
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.preventDefault()
      event.stopPropagation()
      setIsOpen(false)
      triggerRef.current?.focus()
    }

    document.addEventListener('keydown', onKeyDown, true)
    return () => document.removeEventListener('keydown', onKeyDown, true)
  }, [isOpen])

  useEffect(() => {
    if (!isOpen) return

    function onPointerDown(event: PointerEvent) {
      if (containerRef.current?.contains(event.target as Node)) return

      setIsOpen(false)
      // Return focus to the trigger unless the click landed on another control
      // that should keep focus (an input, button, link…).
      window.setTimeout(() => {
        if (document.activeElement === document.body) triggerRef.current?.focus()
      }, 0)
    }

    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [isOpen])

  const gridDays = useMemo(() => buildMonthGrid(viewMonth), [viewMonth])

  /** The time a day click should use, clamped to `min` on its first day. */
  function effectiveTimeForDay(datePart: string): string {
    const base = valueParts?.time ?? fallbackTime
    if (minParts && datePart === minParts.date && base < minParts.time) {
      return minParts.time
    }
    return base
  }

  function selectDay(day: Date) {
    const datePart = toDatePart(day)
    onChange(`${datePart}T${effectiveTimeForDay(datePart)}`)

    if (day.getMonth() !== viewMonth.getMonth() || day.getFullYear() !== viewMonth.getFullYear()) {
      setViewMonth(startOfMonth(day))
    }
  }

  function selectTime(time: string) {
    if (!/^\d{2}:\d{2}$/.test(time)) return

    let datePart = valueParts?.date ?? quickDay
    if (minParts && datePart < minParts.date) datePart = minParts.date

    const nextTime =
      minParts && datePart === minParts.date && time < minParts.time
        ? minParts.time
        : time

    onChange(`${datePart}T${nextTime}`)
  }

  function selectToday() {
    if (minParts && today < minParts.date) return
    onChange(`${today}T${effectiveTimeForDay(today)}`)
    setViewMonth(startOfMonth(new Date()))
  }

  function openPicker() {
    setViewMonth(currentReferenceMonth())
    setIsOpen(true)
  }

  function closePicker(returnFocus: boolean) {
    setIsOpen(false)
    if (returnFocus) triggerRef.current?.focus()
  }

  function clearValue() {
    onChange('')
    closePicker(true)
  }

  const fieldLabel = label.replace(' (optional)', '')
  const triggerText = valueParts ? formatTrigger(value) : ''
  const timeAnchorDay = valueParts?.date ?? quickDay
  const selectedTime =
    valueParts?.time ??
    (minParts && timeAnchorDay === minParts.date && fallbackTime < minParts.time
      ? minParts.time
      : fallbackTime)
  const timeMin =
    minParts && timeAnchorDay === minParts.date ? minParts.time : undefined
  const presetDisabled = (time: string) => {
    if (!minParts) return false
    const day = valueParts?.date ?? quickDay
    if (day < minParts.date) return true
    return day === minParts.date && time < minParts.time
  }
  const minMonth = minParts ? startOfMonth(parseDateOnly(minParts.date)) : null
  const previousDisabled = minMonth ? viewMonth.getTime() <= minMonth.getTime() : false
  const todayDisabled = Boolean(minParts && today < minParts.date)

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={triggerRef}
        id={id}
        type="button"
        disabled={disabled}
        onClick={() => {
          if (isOpen) closePicker(true)
          else openPicker()
        }}
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-controls={isOpen ? panelId : undefined}
        aria-label={valueParts ? `${fieldLabel}: ${triggerText}` : fieldLabel}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        className={`input-field flex w-full cursor-pointer items-center gap-2 text-left transition-colors hover:border-border-strong aria-[invalid=true]:border-terracotta disabled:cursor-not-allowed disabled:opacity-50 ${valueParts ? 'pr-9' : ''} ${isOpen ? 'border-accent' : ''} ${FOCUS_RING}`}
      >
        <CalendarIcon className="h-4 w-4 shrink-0 text-muted" />
        <span className={`truncate ${valueParts ? 'text-foreground' : 'text-faint'}`}>
          {valueParts ? triggerText : placeholder}
        </span>
      </button>

      {valueParts && !disabled && (
        <button
          type="button"
          onClick={clearValue}
          aria-label={`Clear ${fieldLabel.toLowerCase()}`}
          className={`absolute top-1/2 right-1.5 -translate-y-1/2 rounded-md p-1 text-faint transition-colors hover:bg-surface-subtle hover:text-foreground ${FOCUS_RING}`}
        >
          <CloseIcon />
        </button>
      )}

      {isOpen && (
        <div className="absolute top-full left-0 z-30 mt-2 w-full max-w-[calc(100vw-2rem)] sm:left-1/2 sm:w-[19rem] sm:-translate-x-1/2">
          <div
            id={panelId}
            role="dialog"
            aria-label={`${fieldLabel} picker`}
            className="animate-rise rounded-2xl border border-border bg-surface-raised p-3 shadow-2xl shadow-black/20 [animation-duration:180ms] motion-reduce:animate-none dark:shadow-black/50"
          >
            <div className="flex items-center justify-between gap-2 pb-2">
              <button
                type="button"
                onClick={() => setViewMonth(addMonths(viewMonth, -1))}
                disabled={previousDisabled}
                aria-label="Previous month"
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-subtle hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30 ${FOCUS_RING}`}
              >
                <ChevronIcon direction="left" />
              </button>

              <span
                aria-live="polite"
                className="font-display text-sm font-medium tracking-tight text-foreground"
              >
                {MONTH_FORMATTER.format(viewMonth)}
              </span>

              <button
                type="button"
                onClick={() => setViewMonth(addMonths(viewMonth, 1))}
                aria-label="Next month"
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-subtle hover:text-foreground ${FOCUS_RING}`}
              >
                <ChevronIcon direction="right" />
              </button>
            </div>

            <div className="grid grid-cols-7 gap-0.5" aria-hidden="true">
              {WEEKDAY_LABELS.map((weekday, index) => (
                <span
                  key={`${weekday}-${index}`}
                  className="pb-1 text-center text-[0.625rem] font-medium tracking-[0.08em] text-faint uppercase"
                >
                  {weekday}
                </span>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-0.5" role="group" aria-label="Choose a day">
              {gridDays.map((day) => {
                const datePart = toDatePart(day)
                const isSelected = datePart === valueParts?.date
                const isToday = datePart === today
                const isDisabled = Boolean(minParts && datePart < minParts.date)
                const isOutsideMonth = day.getMonth() !== viewMonth.getMonth()

                const tone = isSelected
                  ? 'bg-accent font-semibold text-accent-contrast shadow-sm'
                  : isDisabled
                    ? 'cursor-not-allowed text-faint opacity-40'
                    : isToday
                      ? 'font-semibold text-accent ring-1 ring-accent/50 ring-inset hover:bg-accent-soft'
                      : isOutsideMonth
                        ? 'text-faint hover:bg-surface-subtle hover:text-foreground'
                        : 'text-foreground hover:bg-surface-subtle'

                return (
                  <button
                    key={datePart}
                    type="button"
                    onClick={() => selectDay(day)}
                    disabled={isDisabled}
                    aria-pressed={isSelected}
                    aria-current={isToday ? 'date' : undefined}
                    aria-label={DAY_FORMATTER.format(day)}
                    className={`relative flex h-9 items-center justify-center rounded-lg text-sm transition-colors ${tone} ${FOCUS_RING}`}
                  >
                    {day.getDate()}
                    {isSelected && isToday && (
                      <span
                        aria-hidden="true"
                        className="absolute bottom-1 h-1 w-1 rounded-full bg-accent-contrast"
                      />
                    )}
                  </button>
                )
              })}
            </div>

            <div className="mt-2 border-t border-border pt-3">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium text-muted">Time</span>
                <input
                  type="time"
                  value={selectedTime}
                  min={timeMin}
                  onChange={(event) => selectTime(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') event.preventDefault()
                  }}
                  aria-label="Exact time"
                  className="input-field w-[7.5rem] px-2 py-1 text-xs tabular-nums"
                />
              </div>

              <div className="mt-2 grid grid-cols-2 gap-1.5">
                {TIME_PRESETS.map((preset) => {
                  const isSelected = valueParts?.time === preset.time
                  const isDisabled = presetDisabled(preset.time)

                  return (
                    <button
                      key={preset.time}
                      type="button"
                      onClick={() => selectTime(preset.time)}
                      disabled={isDisabled}
                      aria-pressed={isSelected}
                      className={`rounded-lg border px-2.5 py-1.5 text-left text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
                        isSelected
                          ? 'border-accent/60 bg-accent-soft text-accent'
                          : 'border-border text-muted hover:border-border-strong hover:text-foreground'
                      } ${FOCUS_RING}`}
                    >
                      <span className="block font-medium">{preset.label}</span>
                      <span className="block text-[0.625rem] tabular-nums text-faint">
                        {formatTime(preset.time)}
                      </span>
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="mt-2 flex items-center justify-between gap-2 border-t border-border pt-2">
              <button
                type="button"
                onClick={selectToday}
                disabled={todayDisabled}
                className={`rounded-lg px-2 py-1 text-xs font-medium text-muted transition-colors hover:bg-surface-subtle hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30 ${FOCUS_RING}`}
              >
                Today
              </button>

              <button
                type="button"
                onClick={() => closePicker(true)}
                className={`rounded-lg bg-accent px-3 py-1.5 text-xs font-semibold text-accent-contrast transition-colors hover:bg-accent-strong ${FOCUS_RING}`}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
