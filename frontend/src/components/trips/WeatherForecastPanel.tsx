import { useAuth } from '@clerk/react'

import { useEffect, useState } from 'react'
import { getWeatherForecast } from '../../lib/api'
import type { WeatherForecast } from '../../types/trip'

interface WeatherForecastPanelProps {
  startDate: string
  endDate: string
}

function formatForecastDate(date: string): string {
  // Parse the date as a local calendar date, avoiding UTC date shifts.
  const [year, month, day] = date.split('-').map(Number)
  const localDate = new Date(year, month - 1, day)

  return localDate.toLocaleDateString('en-SG', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  })
}

function getTodayInSingapore(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Singapore',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

export function WeatherForecastPanel({
  startDate,
  endDate,
}: WeatherForecastPanelProps) {
    const { getToken } = useAuth()

    const [forecasts, setForecasts] = useState<WeatherForecast[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const today = getTodayInSingapore()
    const tripHasEnded = endDate < today

    useEffect(() => {
        if (tripHasEnded) {
            return
        }

        let cancelled = false

        async function loadForecast() {
            setIsLoading(true)
            setError(null)

            try {
            const token = await getToken()
            const result = await getWeatherForecast(token)

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
        }, [tripHasEnded, getToken])

    if (tripHasEnded) return null

    const tripForecasts = forecasts.filter((forecast) =>
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

    const expectedStartDate = startDate > tomorrow ? startDate : tomorrow

    const futureTripForecasts = tripForecasts.filter((forecast) => forecast.date >= tomorrow)

    const hasUncoveredDates =
        !isLoading &&
        !error &&
        expectedStartDate <= endDate &&
        (
            futureTripForecasts.length === 0 ||
            futureTripForecasts[0].date > expectedStartDate ||
            futureTripForecasts[futureTripForecasts.length - 1].date < endDate
        )

    return (
        <section
        aria-labelledby="weather-heading"
        className="mt-6 rounded-2xl border border-border bg-surface-raised p-6"
        >
        <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
            <h2
                id="weather-heading"
                className="font-display text-xl font-medium tracking-tight text-foreground"
            >
                Weather forecast
            </h2>
            <p className="mt-1 text-sm text-muted">
                Singapore · Daily rain outlook
            </p>
            </div>
            <span aria-hidden="true" className="text-2xl">🌦️</span>
        </div>

        {isLoading ? (
            <p role="status" className="mt-5 text-sm text-muted">
            Loading weather forecast…
            </p>
        ) : error ? (
            <p role="status" className="mt-5 text-sm text-muted">
            {error}
            </p>
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
        </section>
    )
}
