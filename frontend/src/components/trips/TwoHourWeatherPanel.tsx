import { useAuth } from '@clerk/react'
import { useEffect, useState } from 'react'
import { getTwoHourWeatherForecast } from '../../lib/api'
import { useTripCapabilities } from '../../lib/useTripCapabilities'
import type { TwoHourWeatherForecast } from '../../types/trip'

function formatTime(value: string, locale: string, zone: string): string {
  return new Intl.DateTimeFormat(locale, {
    timeZone: zone,
    hour: 'numeric',
    minute: '2-digit',
    day: 'numeric',
    month: 'short',
  }).format(new Date(value))
}

export function TwoHourWeatherPanel({
  countryCode,
}: {
  countryCode: string
}) {
  const { getToken } = useAuth()
  const [token, setToken] = useState<string | null>(null)

  const [forecasts, setForecasts] =
    useState<TwoHourWeatherForecast[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const { region, hasNeaForecast, locale, timeZone } = useTripCapabilities(
    token,
    countryCode,
  )
  const regionName = region?.name ?? countryCode.toUpperCase()

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
    if (!hasNeaForecast) return

    let cancelled = false

    async function loadForecast() {
      setError(null)

      try {
        const token = await getToken()
        const result = await getTwoHourWeatherForecast(token)

        if (!cancelled) {
          setForecasts(result)
        }
      } catch {
        if (!cancelled) {
          setError('Weather information is temporarily unavailable.')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadForecast()

    return () => {
      cancelled = true
    }
  }, [hasNeaForecast, getToken])

  if (!hasNeaForecast) return null

  // Keep the most recent validity period for each area.
  const latestByArea = new Map<string, TwoHourWeatherForecast>()

  for (const forecast of forecasts) {
    const existing = latestByArea.get(forecast.area)

    if (
      !existing ||
      Date.parse(forecast.validFrom) > Date.parse(existing.validFrom)
    ) {
      latestByArea.set(forecast.area, forecast)
    }
  }

  const latestForecasts = Array.from(latestByArea.values())
    .sort((a, b) => a.area.localeCompare(b.area))

  return (
    <section
      aria-labelledby="two-hour-weather-heading"
      className="mt-6 rounded-2xl border border-border bg-surface-raised p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2
            id="two-hour-weather-heading"
            className="font-display text-xl font-medium tracking-tight text-foreground"
          >
            Current weather outlook
          </h2>
          <p className="mt-1 text-sm text-muted">
            {regionName} · Two-hour forecast by area
          </p>
        </div>
        <span aria-hidden="true" className="text-2xl">🌦️</span>
      </div>

      {isLoading ? (
        <p role="status" className="mt-5 text-sm text-muted">
          Loading two-hour forecast…
        </p>
      ) : error ? (
        <p role="status" className="mt-5 text-sm text-muted">
          {error}
        </p>
      ) : latestForecasts.length === 0 ? (
        <p className="mt-5 text-sm text-muted">
          No two-hour forecast is currently available.
        </p>
      ) : (
        <>
          <p className="mt-4 text-sm text-muted">
            Forecast validity:
            {' '}
            {formatTime(latestForecasts[0].validFrom, locale, timeZone)}
            {' – '}
            {formatTime(latestForecasts[0].validUntil, locale, timeZone)}
            {' '}
            ({regionName} time)
          </p>

          <ul className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {latestForecasts.map((forecast) => (
              <li
                key={forecast.area}
                className="rounded-xl border border-border bg-surface-subtle p-4"
              >
                <p className="text-sm font-medium text-foreground">
                  {forecast.area}
                </p>

                <p
                  className={`mt-3 text-sm font-semibold ${
                    forecast.rainExpected
                      ? 'text-sky-700'
                      : 'text-sage'
                  }`}
                >
                  {forecast.rainExpected
                    ? '🌧️ Rain possible'
                    : '☀️ No rain indicated'}
                </p>

                <p className="mt-2 text-sm leading-relaxed text-muted">
                  {forecast.forecast}
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
