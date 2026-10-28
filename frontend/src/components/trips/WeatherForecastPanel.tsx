import { useAuth } from '@clerk/react'
import { useEffect, useState } from 'react'
import {
  getFourDayWeatherForecast,
  getTwoHourWeatherForecast,
} from '../../lib/api'
import type {
  FourDayWeatherForecast,
  TwoHourWeatherForecast,
} from '../../types/trip'
import { CollapsibleSection } from '../CollapsibleSection'

interface WeatherForecastPanelProps {
  startDate: string
  endDate: string
  countries: string[]
}

function formatForecastDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  const localDate = new Date(year, month - 1, day)

  return localDate.toLocaleDateString('en-SG', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })
}

function getTodayInSingapore(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Singapore',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())

  const values = Object.fromEntries(
    parts.map(({ type, value }) => [type, value]),
  )

  return `${values.year}-${values.month}-${values.day}`
}

function formatSingaporeTime(value: string): string {
  return new Intl.DateTimeFormat('en-SG', {
    timeZone: 'Asia/Singapore',
    hour: 'numeric',
    minute: '2-digit',
    day: 'numeric',
    month: 'short',
  }).format(new Date(value))
}

export function WeatherForecastPanel({
  startDate,
  endDate,
  countries,
}: WeatherForecastPanelProps) {
  const { getToken } = useAuth()

  const [forecasts, setForecasts] = useState<FourDayWeatherForecast[]>([])
  const [twoHourForecasts, setTwoHourForecasts] =
    useState<TwoHourWeatherForecast[]>([])
  const [selectedArea, setSelectedArea] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const today = getTodayInSingapore()
  const tripHasEnded = endDate < today
  const tripIncludesToday = startDate <= today && endDate >= today
  const includesSingapore = countries.some(
    (country) => country.toLowerCase() === 'sg',
  )

  useEffect(() => {
    if (tripHasEnded || !includesSingapore) return

    let cancelled = false

    async function loadForecast() {
      setIsLoading(true)
      setError(null)

      try {
        const token = await getToken()

        if (tripIncludesToday) {
          const result = await getTwoHourWeatherForecast(token)

          if (!cancelled) {
            setTwoHourForecasts(result)
          }
        } else {
          const result = await getFourDayWeatherForecast(token)

          if (!cancelled) {
            setForecasts(result)
          }
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
  }, [tripHasEnded, tripIncludesToday, includesSingapore, getToken])

  if (tripHasEnded || !includesSingapore) return null

  const tripForecasts = forecasts.filter(
    (forecast) =>
      forecast.date >= startDate && forecast.date <= endDate,
  )

  const tomorrow = (() => {
    const [year, month, day] = today.split('-').map(Number)
    const date = new Date(year, month - 1, day + 1)

    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-')
  })()

  const futureTripForecasts = tripForecasts.filter(
    (forecast) => forecast.date >= tomorrow,
  )

  const expectedStartDate =
    startDate > tomorrow ? startDate : tomorrow

  const hasUncoveredDates =
    !isLoading &&
    !error &&
    expectedStartDate <= endDate &&
    (
      futureTripForecasts.length === 0 ||
      futureTripForecasts[0].date > expectedStartDate ||
      futureTripForecasts[futureTripForecasts.length - 1].date < endDate
    )

  // Keep only the latest validity period for each area.
  const latestByArea = new Map<string, TwoHourWeatherForecast>()

  for (const forecast of twoHourForecasts) {
    const existing = latestByArea.get(forecast.area)

    if (
      !existing ||
      Date.parse(forecast.validFrom) >
        Date.parse(existing.validFrom)
    ) {
      latestByArea.set(forecast.area, forecast)
    }
  }

  const latestTwoHourForecasts = Array.from(latestByArea.values())
    .sort((a, b) => a.area.localeCompare(b.area))

  const displayedTwoHourForecast =
    latestByArea.get(selectedArea) ??
    latestTwoHourForecasts[0]

  return (
    <CollapsibleSection
      title="Weather forecast"
      subtitle={
        tripIncludesToday
          ? 'Singapore · Two-hour outlook'
          : 'Singapore · Daily rain outlook'
      }
      aside={<span aria-hidden="true" className="text-2xl">🌦️</span>}
    >

      {isLoading ? (
        <p role="status" className="mt-5 text-sm text-muted">
          Loading weather forecast…
        </p>
      ) : error ? (
        <p role="status" className="mt-5 text-sm text-muted">
          {error}
        </p>
      ) : tripIncludesToday ? (
        latestTwoHourForecasts.length > 0 && displayedTwoHourForecast ? (
          <div className="mt-5">
            <label
              htmlFor="weather-area"
              className="block text-sm font-medium text-foreground"
            >
              Forecast area
            </label>

            <select
              id="weather-area"
              value={displayedTwoHourForecast.area}
              onChange={(event) => setSelectedArea(event.target.value)}
              className="mt-2 w-full rounded-lg border border-border bg-surface-subtle px-3 py-2 text-sm text-foreground sm:max-w-xs"
            >
              {latestTwoHourForecasts.map((forecast) => (
                <option key={forecast.area} value={forecast.area}>
                  {forecast.area}
                </option>
              ))}
            </select>

            <div className="mt-4 rounded-xl border border-border bg-surface-subtle p-4">
              <p
                className={`text-sm font-semibold ${
                  displayedTwoHourForecast.rainExpected
                    ? 'text-sky-700'
                    : 'text-sage'
                }`}
              >
                {displayedTwoHourForecast.rainExpected
                  ? '🌧️ Rain possible'
                  : '☀️ No rain indicated'}
              </p>

              <p className="mt-2 text-sm leading-relaxed text-muted">
                {displayedTwoHourForecast.forecast}
              </p>

              <p className="mt-3 text-xs text-muted">
                Valid from{' '}
                {formatSingaporeTime(displayedTwoHourForecast.validFrom)}
                {' '}to{' '}
                {formatSingaporeTime(displayedTwoHourForecast.validUntil)}
                {' '}· Singapore time
              </p>
            </div>
          </div>
        ) : (
          <p className="mt-5 text-sm text-muted">
            A two-hour forecast is currently unavailable.
          </p>
        )
      ) : (
        <>
          {tripForecasts.length > 0 ? (
            <ul className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {tripForecasts.map((forecast) => (
                <li
                  key={forecast.date}
                  className="rounded-xl border border-border bg-surface-subtle p-4"
                >
                  <p className="text-sm font-medium text-foreground">
                    {formatForecastDate(forecast.date)}
                  </p>

                  <p
                    className={`mt-3 text-sm font-semibold ${
                      forecast.rainExpected
                        ? 'text-sky-700'
                        : 'text-sage'
                    }`}
                  >
                    {forecast.rainExpected
                      ? '🌧️ Rain expected'
                      : '☀️ No rain expected'}
                  </p>

                  <p className="mt-2 text-sm leading-relaxed text-muted">
                    {forecast.forecast}
                  </p>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-5 text-sm text-muted">
              A precise forecast is not available for your trip dates yet.
            </p>
          )}

          {hasUncoveredDates && (
            <p className="mt-4 text-sm leading-relaxed text-muted">
              The four-day forecast does not cover your entire trip.
              Check back closer to your travel dates for updated forecasts.
            </p>
          )}

          {startDate < today && endDate >= today && (
            <p className="mt-4 text-sm text-muted">
              Only available forecast dates are shown; past dates are not forecast.
            </p>
          )}
        </>
      )}
    </CollapsibleSection>
  )
}
