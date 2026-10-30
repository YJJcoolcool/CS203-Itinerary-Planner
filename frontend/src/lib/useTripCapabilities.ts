import { useEffect, useState } from 'react'

import { getRegions } from './api'
import type { RegionInfo, RegionTiles } from './api'

/** Keep SG's current basemap as the fallback for unknown or unavailable regions. */
const DEFAULT_TILES: RegionTiles = {
  url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  crs: 'wgs84',
}

let regionsPromise: Promise<RegionInfo[]> | null = null

function loadRegions(token: string): Promise<RegionInfo[]> {
  if (!regionsPromise) {
    regionsPromise = getRegions(token).catch((error: unknown) => {
      // Allow a later mount to retry after a transient failure.
      regionsPromise = null
      throw error
    })
  }

  return regionsPromise
}

export interface TripCapabilities {
  region: RegionInfo | null
  hasWeather: boolean
  hasNeaForecast: boolean
  hasRouting: boolean
  locale: string
  timeZone: string
  tiles: RegionTiles
  loading: boolean
}

/**
 * Resolves a trip's region capabilities from `/api/regions`. The catalog is
 * fetched once per session and shared by every caller.
 */
export function useTripCapabilities(
  token: string | null,
  countryCode: string,
): TripCapabilities {
  const [regions, setRegions] = useState<RegionInfo[] | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    if (!token) {
      return () => {
        cancelled = true
      }
    }

    loadRegions(token).then(
      (result) => {
        if (!cancelled) {
          setRegions(result)
          setLoading(false)
        }
      },
      () => {
        if (!cancelled) {
          setRegions(null)
          setLoading(false)
        }
      },
    )

    return () => {
      cancelled = true
    }
  }, [token])

  const normalizedCode = countryCode.trim().toLowerCase()
  const region =
    regions?.find(
      (candidate) => candidate.code.toLowerCase() === normalizedCode,
    ) ??
    regions?.find((candidate) => candidate.code === '*') ??
    null

  const capabilities = region?.capabilities ?? []
  const regionTiles = region?.tiles

  return {
    region,
    hasWeather: capabilities.includes('weather'),
    hasNeaForecast: region?.code === 'sg',
    hasRouting: capabilities.includes('routing'),
    locale: region?.locale || 'en',
    timeZone: region?.defaultZone || 'UTC',
    tiles:
      regionTiles?.url && regionTiles.attribution
        ? regionTiles
        : DEFAULT_TILES,
    loading,
  }
}
