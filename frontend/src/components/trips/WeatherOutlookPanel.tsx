import { useAuth } from '@clerk/react'
import { useEffect, useState } from 'react'

import { getWeatherOutlook } from '../../lib/api'
import { useTripCapabilities } from '../../lib/useTripCapabilities'
import type { WeatherOutlookDay } from '../../types/trip'
import { CollapsibleSection } from '../CollapsibleSection'

interface WeatherOutlookPanelProps {
  tripId: string
  countryCode: string
}

function formatOutlookDate(date: string, locale: string, zone: string): string {
  const [year, month, day] = date.split('-').map(Number)
  // The API sends calendar dates without a time. Noon UTC anchors the display
  // so the day does not slip backwards when rendered in the trip's zone.
  const noonUtc = new Date(Date.UTC(year, month - 1, day, 12))

  return new Intl.DateTimeFormat(locale, {
    timeZone: zone,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(noonUtc)
}

function formatPrecipitation(value: number): string {
  return `${value.toFixed(1)} mm`
}

export function WeatherOutlookPanel({
  tripId,
  countryCode,
}: WeatherOutlookPanelProps) {
  const { getToken } = useAuth()
  const [token, setToken] = useState<string | null>(null)

  const [outlook, setOutlook] = useState<WeatherOutlookDay[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [hasError, setHasError] = useState(false)

  const { region, hasWeather, hasNeaForecast, locale, timeZone } =
    useTripCapabilities(token, countryCode)
  const regionName = region?.name ?? countryCode.toUpperCase()

  const isEnabled = hasWeather && !hasNeaForecast

  useEffect(() => {
    let cancelled = false

    void getToken().then((value) => {
      if (!cancelled) setToken(value)
    })

    return () => {
      cancelled = true
    }
  }, [getToken])

  useEffect(() => {
    if (!isEnabled || !token) return

    let cancelled = false

    async function loadOutlook() {
      setIsLoading(true)
      setHasError(false)
      setOutlook([])

      try {
        const result = await getWeatherOutlook(token, tripId)

        if (!cancelled) {
          setOutlook(
            [...result].sort((a, b) => a.date.localeCompare(b.date)),
          )
        }
      } catch {
        if (!cancelled) setHasError(true)
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    }

    void loadOutlook()

    return () => {
      cancelled = true
    }
  }, [isEnabled, token, tripId])

  if (!isEnabled || hasError || (!isLoading && outlook.length === 0)) {
    return null
  }

  return (
    <CollapsibleSection
      title="Weather outlook"
      subtitle={`${regionName} · Daily forecast`}
      aside={<span aria-hidden="true" className="text-2xl">🌦️</span>}
    >
      {isLoading ? (
        <p role="status" className="mt-5 text-sm text-muted">
          Loading weather outlook…
        </p>
      ) : (
        <>
          <ul className="mt-5 flex flex-col gap-3">
            {outlook.map((day) => {
              const dateLabel = formatOutlookDate(day.date, locale, timeZone)
              const rainLabel = day.rainy
                ? 'Rain expected'
                : 'No rain expected'
              const precipitationLabel = formatPrecipitation(
                day.precipitationMm,
              )
              const probabilityLabel =
                day.probabilityPercent != null
                  ? `${day.probabilityPercent}% chance`
                  : null

              return (
                <li
                  key={day.date}
                  aria-label={[
                    dateLabel,
                    rainLabel,
                    precipitationLabel,
                    probabilityLabel,
                  ]
                    .filter(Boolean)
                    .join('. ')}
                  className="rounded-xl border border-border bg-surface-subtle p-4"
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <p className="text-sm font-medium text-foreground">
                      <time dateTime={day.date}>{dateLabel}</time>
                    </p>

                    <p
                      className={`text-sm font-semibold ${
                        day.rainy ? 'text-sky-700' : 'text-sage'
                      }`}
                    >
                      {rainLabel}
                    </p>
                  </div>

                  <p className="mt-2 text-sm leading-relaxed text-muted">
                    {precipitationLabel}
                    {probabilityLabel && ` · ${probabilityLabel}`}
                  </p>
                </li>
              )
            })}
          </ul>

          <p className="mt-4 text-[11px] text-faint">
            Weather data by{' '}
            <a
              href="https://open-meteo.com/"
              target="_blank"
              rel="noreferrer"
              className="underline underline-offset-2 transition-colors hover:text-muted"
            >
              Open-Meteo.com
            </a>
          </p>
        </>
      )}
    </CollapsibleSection>
  )
}
